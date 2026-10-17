package com.spaceconquest.engine.economy;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.market.FacilitySupplyPricing;
import java.util.*;

/** Measured pre-hiring constraints against allocated capacity, not mutually exclusive failure causes. */
public final class ProductionConstraints {
    public record Constraint(String kind, String resource, double required, double available) {
        public Constraint {
            if (kind == null || resource == null || !Double.isFinite(required) || required < 0
                    || !Double.isFinite(available) || available < 0) throw new IllegalArgumentException("Invalid production constraint");
        }
    }
    private ProductionConstraints() {}
    public static List<Constraint> inspect(GameState state, IndustrialFacility facility, GridSupplyPricing.Forecast forecast) {
        var result = new ArrayList<Constraint>();
        var owner = EconomicOwnership.empireFor(state, facility.ownerEntityId());
        var recipe = IndustryRecipeCatalog.forFacility(state, facility);
        if (recipe == null) return generator(state, facility);
        if (!IndustryRecipeCatalog.isUnlocked(recipe, owner)) result.add(new Constraint("Research", facility.applicationId(), 1, 0));
        if (!FacilityManufacturingCapacity.canOperate(state, facility) || facility.tier() < recipe.minTier())
            result.add(new Constraint("Manufacturing capacity", facility.applicationId(), recipe.minTier(), Math.max(0, facility.tier())));
        var hub = state.commercialHubs().stream().filter(h -> h.entityId().equals(facility.planetId())).findFirst().orElse(null);
        if (hub == null) return List.of(new Constraint("Local marketplace", facility.planetId(), 1, 0));
        double batches;
        double energy;
        if (ProductionWork.definition(facility.applicationId()) != null) {
            var quote = ProductionWork.quote(state, facility, facility.allocatedWorkers());
            batches = quote.batchesPerDay(); energy = quote.energyKwh();
            if (!quote.blocker().isEmpty()) result.add(new Constraint("Work or site", quote.blocker(), 1, 0));
        } else {
            batches = Math.max(0, facility.allocatedWorkers()) * facility.getEffectiveThroughputMultiplier()
                    * (owner == null ? 1 : recipe.technologyMultiplier(owner)) / recipe.workersPerBatch();
            energy = PowerProcessor.requestedIndustryKw(facility, facility.allocatedWorkers()) * 24;
        }
        double inputs = 0, supportedBatches = batches;
        for (var input : recipe.inputsKg().entrySet()) {
            var order = hub.activeOrders().get(input.getKey());
            double required = batches * input.getValue(); double available = order == null ? 0 : order.supplyKg();
            add(result, "Material", input.getKey(), required, available);
            supportedBatches = Math.min(supportedBatches, available / input.getValue());
            inputs += required * (order == null ? 0 : Math.max(0, order.pricePerKg()));
        }
        for (var input : recipe.inputSources()) if (input.source() == ProcessInput.Source.DEPOSIT) {
            double available = state.geologicalDeposits().stream().filter(d -> d.planetId().equals(facility.planetId())
                    && d.materialId().equals(input.materialId()) && d.isDiscovered()).mapToDouble(GeologicalDeposit::remainingVolumeKg).sum();
            add(result, "Deposit", input.materialId(), batches * input.kgPerBatch(), available);
            supportedBatches = Math.min(supportedBatches, available / input.kgPerBatch());
        }
        // Input-aware grid requests shrink before dispatch; absent inputs are not evidence of a power shortage.
        add(result, "Electricity", "kWh", batches <= 0 ? 0 : energy * supportedBatches / batches,
                forecast.productionEnergyKwh().getOrDefault(facility.id(), 0.0));
        double payroll = facility.allocatedWorkers() * Profession.getBaseWageForProfession(facility.workerProfessionId());
        double cost = inputs + payroll + FacilitySupplyPricing.declaredMaintenance(facility)
                + energy * forecast.tariffs().getOrDefault(facility.planetId(), .02);
        add(result, "Working capital", "credits", cost, FacilitySupplyPricing.cash(state, facility));
        double output = recipe.outputsKg().values().stream().mapToDouble(Double::doubleValue).sum() * batches;
        add(result, "Market storage", "kg", output, Math.max(0, hub.storageCapacityKg() - hub.currentStoredWeightKg()));
        return List.copyOf(result);
    }
    private static void add(List<Constraint> result, String kind, String resource, double required, double available) {
        if (required > 0 && available + required * 1e-9 < required)
            result.add(new Constraint(kind, resource, required, Math.max(0, available)));
    }
    private static List<Constraint> generator(GameState state, IndustrialFacility facility) {
        var plant = PowerPlantCatalog.find(facility.applicationId());
        if (plant == null) return List.of();
        var result = new ArrayList<Constraint>();
        var owner = EconomicOwnership.empireFor(state, facility.ownerEntityId());
        if (owner == null || !owner.unlockedTechIds().contains("electricity") || !owner.unlockedTechIds().contains(plant.requiredTechnology())
                || !com.spaceconquest.engine.technology.ApplicationProduction.isAvailable(owner, facility.applicationId()))
            result.add(new Constraint("Research", facility.applicationId(), 1, 0));
        double environment = PowerGenerationProcessor.environmentFactor(state.solarSystems(), facility);
        if (environment <= 0) result.add(new Constraint("Generator environment", facility.applicationId(), 1, 0));
        var hub = state.commercialHubs().stream().filter(h -> h.entityId().equals(facility.planetId())).findFirst().orElse(null);
        double scale = GenerationCapacity.scale(facility, plant, facility.allocatedWorkers());
        var fuel = hub == null || plant.fuelMaterialId() == null ? null : hub.activeOrders().get(plant.fuelMaterialId());
        double fuelKg = plant.fuelKgPerTierDay() * scale;
        if (plant.fuelMaterialId() != null) add(result, "Rated generator fuel", plant.fuelMaterialId(), fuelKg, fuel == null ? 0 : fuel.supplyKg());
        double cost = facility.allocatedWorkers() * Profession.getBaseWageForProfession(facility.workerProfessionId())
                + FacilitySupplyPricing.declaredMaintenance(facility) + fuelKg * (fuel == null ? 0 : fuel.pricePerKg());
        add(result, "Rated generator funding", "credits", cost, FacilitySupplyPricing.cash(state, facility));
        return List.copyOf(result);
    }
}
