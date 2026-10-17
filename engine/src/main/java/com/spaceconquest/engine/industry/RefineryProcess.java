package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.technology.ApplicationProduction;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;

/** Application-defined chemistry with bounded recovery, paid reagents and retained elemental residue. */
public record RefineryProcess(List<String> feedstocks, String defaultFeedstock, double feedstockKgPerBatch,
                             List<String> products, List<Profile> profiles, double tailingsCapacityKg) {
    public record Profile(String requiredApplicationId, Map<String, Double> recoveryFractions,
                          Map<String, Double> reagentsKg, ProductionWork.Definition work) {
        public Profile {
            recoveryFractions = Map.copyOf(recoveryFractions); reagentsKg = Map.copyOf(reagentsKg);
            if (requiredApplicationId == null || work == null || recoveryFractions.isEmpty()
                    || recoveryFractions.values().stream().anyMatch(v -> !Double.isFinite(v) || v < 0 || v > 1)
                    || reagentsKg.values().stream().anyMatch(v -> !Double.isFinite(v) || v <= 0))
                throw new IllegalArgumentException("Invalid refinery recovery profile");
        }
    }
    public RefineryProcess {
        feedstocks = List.copyOf(feedstocks); products = List.copyOf(products); profiles = List.copyOf(profiles);
        if (!feedstocks.contains(defaultFeedstock) || products.isEmpty() || profiles.isEmpty()
                || feedstocks.stream().distinct().count() != feedstocks.size() || products.stream().distinct().count() != products.size()
                || profiles.stream().map(Profile::requiredApplicationId).distinct().count() != profiles.size()
                || !Double.isFinite(feedstockKgPerBatch) || feedstockKgPerBatch <= 0
                || !Double.isFinite(tailingsCapacityKg) || tailingsCapacityKg < feedstockKgPerBatch)
            throw new IllegalArgumentException("Invalid refinery process");
        for (var profile : profiles) if (!products.containsAll(profile.recoveryFractions().keySet())
                || !profile.work().applicationId().equals(profiles.getFirst().work().applicationId())
                || !profile.work().processType().equals(profiles.getFirst().work().processType())
                || !profile.work().equipment().equals(profiles.getFirst().work().equipment()))
            throw new IllegalArgumentException("Unknown refinery recovery product");
    }
    public Profile profile(GameState state, String owner) {
        String empireId = state.corporations().stream().filter(c -> c.id().equals(owner)).map(Corporation::empireId).findFirst().orElse(owner);
        var empire = state.empires().stream().filter(e -> e.id().equals(empireId)).findFirst().orElse(null);
        Profile selected = profiles.getFirst();
        for (var profile : profiles) if (empire != null && empire.unlockedTechIds().contains(profile.requiredApplicationId())
                && ApplicationProduction.isAvailable(empire, profile.requiredApplicationId())) selected = profile;
        return selected;
    }
    public double storageCapacityKg(IndustrialFacility facility) {
        return tailingsCapacityKg * facility.productionEquipment().parallelLines();
    }
    public record Plan(IndustryRecipeCatalog.Recipe recipe, Profile profile, RefineryState before,
                       RefineryLot intakePerBatch, RefineryLot residuePerBatch, double maximumBatches, String blocker) {
        public RefineryState complete(double batches, long turn) {
            if (!Double.isFinite(batches) || batches <= 0 || batches > maximumBatches + 1e-8 || !blocker.isEmpty())
                throw new IllegalArgumentException("Invalid refinery execution");
            var lots = new ArrayList<RefineryLot>();
            for (var lot : before.tailings()) {
                if (!lot.id().equals(before.selectedTailingsId())) lots.add(lot);
                else {
                    double remaining = lot.massKg() - recipe.refineryProcess().feedstockKgPerBatch() * batches;
                    if (remaining > 1e-8) lots.add(lot.scale(remaining / lot.massKg()));
                }
            }
            String id = "refinery_" + UUID.randomUUID();
            var intake = new RefineryLot(id + "/intake", intakePerBatch.source(), intakePerBatch.scale(batches).elementsKg());
            var residue = new RefineryLot(id + "/tailings", id, residuePerBatch.scale(batches).elementsKg());
            if (residue.massKg() > 1e-8) lots.add(residue);
            var inputs = scaled(recipe.inputsKg(), batches); var outputs = scaled(recipe.outputsKg(), batches);
            return new RefineryState(before.feedstockMaterialId(), before.selectedTailingsId(), lots,
                    new RefineryState.Receipt(turn, profile.requiredApplicationId(), intake, inputs, outputs, residue, batches));
        }
    }
    public static RefineryState inventory(GameState state, String facilityId) {
        return state.industryAccounts().stream().filter(a -> a.facilityId().equals(facilityId))
                .map(IndustryAccount::refineryState).findFirst().orElse(RefineryState.empty());
    }
    public static Plan plan(GameState state, IndustrialFacility facility, RefineryState inventory) {
        var template = IndustryRecipeCatalog.find(facility.applicationId());
        if (template == null || template.refineryProcess() == null) return null;
        var process = template.refineryProcess(); var profile = process.profile(state, facility.ownerEntityId());
        var inputs = new TreeMap<>(profile.reagentsKg()); var elements = new TreeMap<String, Double>();
        var source = inventory.selectedTailingsId() == null ? null : inventory.tailings().stream()
                .filter(lot -> lot.id().equals(inventory.selectedTailingsId())).findFirst().orElse(null);
        String material = inventory.feedstockMaterialId() == null ? process.defaultFeedstock() : inventory.feedstockMaterialId();
        String blocker = ""; double maximum = Double.MAX_VALUE;
        if (inventory.selectedTailingsId() != null) {
            if (source == null || source.massKg() <= 0) { blocker = "Selected tailings depleted or unavailable"; maximum = 0; }
            else { elements.putAll(source.scale(process.feedstockKgPerBatch() / source.massKg()).elementsKg()); maximum = source.massKg() / process.feedstockKgPerBatch(); }
        } else if (!process.feedstocks().contains(material)) { blocker = "Unsupported feedstock"; maximum = 0; }
        else { inputs.merge(material, process.feedstockKgPerBatch(), Double::sum); }
        for (var input : inputs.entrySet()) elemental(input.getKey(), input.getValue()).forEach((e, kg) -> elements.merge(e, kg, Double::sum));
        var intake = new RefineryLot("preview", source == null ? material : source.id(), elements);
        var remaining = new TreeMap<>(elements); var outputs = new TreeMap<String, Double>();
        for (String product : process.products()) {
            var fractions = elemental(product, 1);
            double possible = fractions.entrySet().stream().mapToDouble(e -> remaining.getOrDefault(e.getKey(), 0.0) / e.getValue()).min().orElse(0);
            double recovered = possible * profile.recoveryFractions().getOrDefault(product, 0.0);
            if (recovered <= 0) continue;
            outputs.put(product, recovered);
            fractions.forEach((e, fraction) -> remaining.put(e, Math.max(0, remaining.getOrDefault(e, 0.0) - fraction * recovered)));
        }
        var residue = new RefineryLot("preview", "residue", remaining);
        double growth = residue.massKg() - (source == null ? 0 : process.feedstockKgPerBatch());
        if (growth > 0) maximum = Math.min(maximum, Math.max(0, process.storageCapacityKg(facility) - inventory.storedKg()) / growth);
        if (outputs.isEmpty()) { if (blocker.isEmpty()) blocker = "No recoverable products"; maximum = 0; }
        if (maximum <= 0 && blocker.isEmpty()) blocker = "Tailings storage full";
        var recipe = new IndustryRecipeCatalog.Recipe(template.applicationId(), template.requiredTechnologies(), template.improvements(),
                inputs, outputs, template.workersPerBatch(), template.minTier(), profile.work().energyKwhPerBatch() / 24, false, process,
                profile.work(), template.factory());
        return new Plan(recipe, profile, inventory, intake, residue, maximum, blocker);
    }
    private static Map<String, Double> elemental(String materialId, double mass) {
        try {
            var material = DataModelLoader.loadMaterials().stream().filter(m -> m.id().equals(materialId)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown refinery material: " + materialId));
            double total = material.composition().values().stream().mapToDouble(Double::doubleValue).sum();
            if (!Double.isFinite(total) || Math.abs(total - 100) > 1e-6 || material.composition().values().stream().anyMatch(v -> !Double.isFinite(v) || v <= 0))
                throw new IllegalArgumentException("Refinery material needs mass-percent composition: " + materialId);
            var elements = new TreeMap<String, Double>(); material.composition().forEach((e, fraction) -> elements.put(e, mass * fraction / 100));
            return elements;
        } catch (IOException exception) { throw new UncheckedIOException(exception); }
    }
    private static Map<String, Double> scaled(Map<String, Double> amounts, double factor) {
        var result = new TreeMap<String, Double>(); amounts.forEach((id, kg) -> result.put(id, kg * factor)); return Map.copyOf(result);
    }
}
