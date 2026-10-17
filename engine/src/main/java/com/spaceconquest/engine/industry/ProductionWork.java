package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.technology.ApplicationProduction;
import java.util.List;
import java.util.Map;

/** Pure shared quotes for typed task work, installed machinery, staffing and process electricity. */
public final class ProductionWork {
    public record Definition(String applicationId, String processType, double workPerBatch,
                             double energyKwhPerBatch, double minimumProcessingDays,
                             ProductionEquipment equipment) {
        public Definition {
            if (applicationId == null || applicationId.isBlank() || processType == null || processType.isBlank()
                    || !Double.isFinite(workPerBatch) || workPerBatch <= 0
                    || !Double.isFinite(energyKwhPerBatch) || energyKwhPerBatch <= 0
                    || !Double.isFinite(minimumProcessingDays) || minimumProcessingDays < 0
                    || equipment == null || !processType.equals(equipment.processType()))
                throw new IllegalArgumentException("Invalid production work definition");
        }
    }
    public record Quote(String processType, double requiredWorkPerBatch, double workUnitsPerDay,
                        double batchesPerDay, double energyKwh, String blocker) {}
    public record EnergyBudget(double availableKwh, double pricedKwh, double tariffPerKwh) {
        public EnergyBudget(double availableKwh, double pricedKwh) { this(availableKwh, pricedKwh, PowerBillingProcessor.PRICE_PER_KWH); }
        public EnergyBudget {
            if (!Double.isFinite(availableKwh) || availableKwh < 0 || !Double.isFinite(pricedKwh)
                    || pricedKwh < 0 || pricedKwh > availableKwh || !Double.isFinite(tariffPerKwh) || tariffPerKwh < 0)
                throw new IllegalArgumentException("Invalid production energy budget");
        }
        public double pricePerKwh() {
            return availableKwh <= 0 ? 0 : pricedKwh / availableKwh * tariffPerKwh;
        }
    }

    private ProductionWork() {}
    public static Definition definition(String applicationId) {
        var recipe = IndustryRecipeCatalog.find(applicationId);
        if (recipe == null) return null;
        return recipe.work() != null ? recipe.work()
                : recipe.refineryProcess() == null ? null : recipe.refineryProcess().profiles().getFirst().work();
    }
    public static ProductionEquipment initialEquipment(String applicationId) {
        var definition = definition(applicationId);
        if (definition != null) return definition.equipment();
        var service = com.spaceconquest.engine.logistics.InfrastructureService.find(applicationId);
        return service == null ? null : service.capability().work().equipment();
    }
    public static Definition definition(GameState state, IndustrialFacility facility) {
        var recipe = IndustryRecipeCatalog.find(facility.applicationId());
        return recipe == null || recipe.refineryProcess() == null ? definition(facility.applicationId())
                : recipe.refineryProcess().profile(state, facility.ownerEntityId()).work();
    }
    public static boolean supports(IndustrialFacility facility, String target) {
        var definition = definition(target);
        if (definition == null) return facility.productionEquipment() == null;
        var equipment = facility.productionEquipment();
        return equipment != null && definition.processType().equals(equipment.processType())
                && equipment.operatorProfession().equals(facility.workerProfessionId());
    }
    public static Quote quote(GameState state, IndustrialFacility facility, int paidOperators) {
        var recipe = IndustryRecipeCatalog.forFacility(state, facility);
        var definition = definition(state, facility);
        var equipment = facility.productionEquipment();
        if (definition == null) return new Quote("", 0, 0, 0, 0, "Legacy production model");
        if (!supports(facility, facility.applicationId())) return blocked(definition, "Incompatible installed equipment or profession");
        if (recipe.farming() != null && facility.isUndergoingExpansion())
            return blocked(definition, "Cultivation waits for construction or expansion");
        if (recipe.farming() != null && !recipe.farming().permits(state, facility))
            return blocked(definition, "Cultivation site or land unavailable");
        if (recipe.infrastructure() != null && !recipe.infrastructure().permitsSite(state, facility.planetId()))
            return blocked(definition, "Process environment unavailable");
        if (!ProcessInput.environmentAvailable(state, facility.planetId(), recipe.inputSources()))
            return blocked(definition, "Process input source unavailable");
        var refining = RefineryProcess.plan(state, facility, RefineryProcess.inventory(state, facility.id()));
        if (refining != null && !refining.blocker().isEmpty()) return blocked(definition, refining.blocker());
        var owner = owner(state, facility.ownerEntityId());
        if (facility.tier() < recipe.minTier() || !IndustryRecipeCatalog.isUnlocked(recipe, owner)
                || !FacilityManufacturingCapacity.canOperate(state, facility))
            return blocked(definition, "Research or manufacturing capacity unavailable");
        double effect = ApplicationProduction.modifiersForOwner(state, facility.ownerEntityId(), facility.applicationId()).effectMultiplier()
                * recipe.technologyMultiplier(owner);
        double required = definition.workPerBatch() / Math.max(0.000001, effect);
        int operators = Math.min(Math.max(0, paidOperators), Math.max(0, facility.allocatedWorkers()));
        double scale = facility.getEffectiveThroughputMultiplier();
        double work = equipment.ratedWorkPerDay() * scale * equipment.staffedFraction(operators);
        double batches = work / required;
        if (definition.minimumProcessingDays() > 0) batches = Math.min(batches, scale * equipment.parallelLines() / definition.minimumProcessingDays());
        if (recipe.farming() != null) batches = Math.min(batches, scale * equipment.parallelLines() / recipe.farming().growingDays());
        if (refining != null) batches = Math.min(batches, refining.maximumBatches());
        return new Quote(definition.processType(), required, batches * required, batches,
                batches * definition.energyKwhPerBatch(), batches > 0 ? "" : "No effective operating capacity");
    }
    /** Rated baseline load for catalogs and opening assets without a world snapshot. */
    public static double nominalKw(IndustrialFacility facility, int operators) {
        var definition = definition(facility.applicationId());
        if (definition == null || facility.tier() <= 0 || !supports(facility, facility.applicationId())) return 0;
        double scale = facility.getEffectiveThroughputMultiplier();
        double batches = facility.productionEquipment().ratedWorkPerDay() * scale
                * facility.productionEquipment().staffedFraction(Math.min(Math.max(0, operators), Math.max(0, facility.allocatedWorkers())))
                / definition.workPerBatch();
        if (definition.minimumProcessingDays() > 0) batches = Math.min(batches, scale * facility.productionEquipment().parallelLines() / definition.minimumProcessingDays());
        var recipe = IndustryRecipeCatalog.find(facility.applicationId());
        if (recipe.farming() != null) batches = Math.min(batches, scale * facility.productionEquipment().parallelLines() / recipe.farming().growingDays());
        return batches * definition.energyKwhPerBatch() / 24;
    }
    private static Quote blocked(Definition definition, String reason) {
        return new Quote(definition.processType(), definition.workPerBatch(), 0, 0, 0, reason);
    }
    private static Empire owner(GameState state, String id) {
        String empire = state.corporations().stream().filter(c -> c.id().equals(id)).map(Corporation::empireId).findFirst().orElse(id);
        return state.empires().stream().filter(e -> e.id().equals(empire)).findFirst().orElse(null);
    }
    public static double requestedKw(GameState state, IndustrialFacility facility, int operators) {
        var quote = quote(state, facility, operators);
        var recipe = IndustryRecipeCatalog.forFacility(state, facility);
        var hub = state.commercialHubs().stream().filter(h -> h.entityId().equals(facility.planetId())).findFirst().orElse(null);
        if (hub == null || quote.batchesPerDay() <= 0) return 0;
        double batches = quote.batchesPerDay();
        for (var input : recipe.inputsKg().entrySet()) {
            var stock = hub.activeOrders().get(input.getKey());
            batches = Math.min(batches, stock == null ? 0 : Math.max(0, stock.supplyKg()) / input.getValue());
        }
        batches = Math.min(batches, ProcessInput.maximumBatches(state, facility.planetId(), recipe.inputSources(), state.geologicalDeposits()));
        var refining = RefineryProcess.plan(state, facility, RefineryProcess.inventory(state, facility.id()));
        if (refining != null) batches = Math.min(batches, refining.maximumBatches());
        return batches * definition(state, facility).energyKwhPerBatch() / 24;
    }
    public static List<PowerGridState> settleGrids(GameState beforeBilling, List<PowerGridState> legacy,
                                                 Map<String, Double> workEnergy) {
        return legacy.stream().map(grid -> {
            double battery = beforeBilling.powerGrids().stream().filter(g -> g.entityId().equals(grid.entityId()))
                    .mapToDouble(PowerGridState::currentStoredKwh).findFirst().orElse(0);
            var settled = new PowerProcessor().balanceGrid(grid.entityId(), grid.totalGenerationKw(),
                    grid.totalDemandKw() + workEnergy.getOrDefault(grid.entityId(), 0.0) / 24,
                    battery, grid.batteryCapacityKwh());
            return new PowerGridState(settled.entityId(), settled.totalGenerationKw(), settled.totalDemandKw(),
                    settled.netBalanceKw(), settled.batteryCapacityKwh(), settled.currentStoredKwh(), grid.isDeficitBrownoutActive());
        }).toList();
    }
}
