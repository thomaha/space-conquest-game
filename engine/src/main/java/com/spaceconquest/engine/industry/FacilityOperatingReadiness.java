package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.technology.ApplicationProduction;
import java.util.*;

/** Snapshot-only viability and cash recovery quote. It buys no goods and hires no workers. */
public record FacilityOperatingReadiness(String facilityId, Status status, int potentialWorkers,
                                        double dailyCostCredits, double targetCashCredits, double transferCredits) {
    public enum Status { NO_POLICY, NOT_PUBLIC, NO_CONTROL, NOT_OPERATIONAL, NO_RESEARCH, NO_CAPACITY,
        NO_WORKERS, NO_HUB, NO_INPUTS, NO_ENVIRONMENT, NO_DEPOSIT, NO_POWER, NO_DEMAND,
        RESERVE_TOO_SMALL, TREASURY_LOW, FUNDED, CASH_LOW }
    private record Operation(Status blocker, double cost) {}
    private static FacilityOperatingReadiness blocked(String id, Status status) {
        return new FacilityOperatingReadiness(id, status, 0, 0, 0, 0);
    }
    public static FacilityOperatingReadiness inspect(GameState state, String empireId, String facilityId) {
        var facility = state.industrialFacilities().stream().filter(value -> value.id().equals(facilityId)).findFirst().orElse(null);
        return inspect(state, empireId, facilityId, facility == null ? null : OperatingReservePolicy.forApplication(facility.applicationId()));
    }
    public static FacilityOperatingReadiness inspect(GameState state, String empireId, String facilityId, OperatingReservePolicy policy) {
        var facility = state.industrialFacilities().stream().filter(value -> value.id().equals(facilityId)).findFirst().orElse(null);
        if (facility == null || !empireId.equals(facility.ownerEntityId()) || !IndustrialFacility.PUBLIC_STATE.equals(facility.ownershipType()))
            return blocked(facilityId, Status.NOT_PUBLIC);
        var empire = state.empires().stream().filter(value -> value.id().equals(empireId)).findFirst().orElse(null);
        var people = populations(state, empire, facility.planetId());
        if (people == null || (empire.societyStructure() != null && empire.societyStructure().toLowerCase(Locale.ROOT).contains("hive"))) return blocked(facilityId, Status.NO_CONTROL);
        if (policy == null) return blocked(facilityId, Status.NO_POLICY);
        if (facility.tier() <= 0 || facility.isUndergoingExpansion() || state.expansionProjects().stream()
                .anyMatch(project -> facilityId.equals(project.facilityId()))) return blocked(facilityId, Status.NOT_OPERATIONAL);
        var plant = PowerPlantCatalog.find(facility.applicationId()); var recipe = IndustryRecipeCatalog.forFacility(state, facility);
        var service = com.spaceconquest.engine.logistics.InfrastructureService.find(facility.applicationId());
        if ((plant == null && recipe == null && service == null) || !ApplicationProduction.isAvailable(empire, ApplicationProduction.capabilityApplication(facility.applicationId()))
                || (recipe != null && !IndustryRecipeCatalog.isUnlocked(recipe, empire))
                || (plant != null && (!empire.unlockedTechIds().contains("electricity") || !empire.unlockedTechIds().contains(plant.requiredTechnology()))))
            return blocked(facilityId, Status.NO_RESEARCH);
        if (!FacilityManufacturingCapacity.canOperate(state, facility)) return blocked(facilityId, Status.NO_CAPACITY);
        var account = state.industryAccounts().stream().filter(value -> value.facilityId().equals(facilityId)).findFirst().orElse(IndustryAccount.empty(facilityId));
        long trained = state.householdAccounts().stream().filter(value -> facility.planetId().equals(value.bodyId())
                && empireId.equals(value.empireId()) && facility.workerProfessionId().equals(value.professionId()))
                .mapToLong(value -> value.employment().unemployedWorkers()).sum() + account.paidWorkers();
        long adults = people.stream().flatMap(value -> value.ageGroups().entrySet().stream())
                .filter(age -> age.getKey() >= 18 && age.getKey() < 65).mapToLong(Map.Entry::getValue).sum();
        int workers = (int) Math.min(Math.min(Math.max(0, facility.allocatedWorkers()), Math.min(trained, adults)),
                plant == null ? service == null ? Integer.MAX_VALUE : facility.productionEquipment().referenceCrew() : GenerationCapacity.workers(facility, plant));
        if (workers <= 0 && (facility.productionEquipment() == null || facility.productionEquipment().autonomousFraction() <= 0))
            return blocked(facilityId, Status.NO_WORKERS);
        var hub = state.commercialHubs().stream().filter(value -> value.entityId().equals(facility.planetId())).findFirst().orElse(null);
        if (hub == null && (plant == null || plant.fuelMaterialId() != null)) return blocked(facilityId, Status.NO_HUB);
        var operation = service != null ? new Operation(null, 0)
                : plant == null ? factory(state, empire, facility, recipe, hub, workers, policy.maximumCashCredits()) : generation(state, empire, facility, plant, hub, workers);
        if (operation.blocker() != null) return blocked(facilityId, operation.blocker());
        int units = plant == null ? 1 : GenerationCapacity.units(facility);
        double daily = operation.cost() + workers * Profession.getBaseWageForProfession(facility.workerProfessionId())
                + policy.maintenanceCreditsPerTier() * facility.tier() * units;
        double target = Math.min(policy.maximumCashCredits() * units, daily * policy.targetDays());
        if (!Double.isFinite(daily) || daily <= 0 || target < daily) return blocked(facilityId, Status.RESERVE_TOO_SMALL);
        if (account.operatingCashCredits() >= Math.min(target, daily * policy.refillBelowDays()))
            return new FacilityOperatingReadiness(facilityId, Status.FUNDED, workers, daily, target, 0);
        double transfer = target - account.operatingCashCredits();
        var status = empire.treasuryCredits() - transfer >= policy.treasuryReserveCredits() ? Status.CASH_LOW : Status.TREASURY_LOW;
        return new FacilityOperatingReadiness(facilityId, status, workers, daily, target, status == Status.CASH_LOW ? transfer : 0);
    }
    private static Operation generation(GameState state, Empire empire, IndustrialFacility facility, PowerPlantCatalog.Plant plant,
                                        CommercialHub hub, int workers) {
        double environment = PowerGenerationProcessor.environmentFactor(state.solarSystems(), facility);
        if (environment <= 0) return new Operation(Status.NO_ENVIRONMENT, 0);
        double scale = GenerationCapacity.scale(facility, plant, workers);
        double output = plant.kwPerTier() * scale * environment * ApplicationProduction.modifiersForOwner(state, empire.id(), facility.applicationId()).effectMultiplier();
        double ownKw = state.industryAccounts().stream().filter(account -> facility.id().equals(account.facilityId()))
                .mapToDouble(account -> account.generatedKwh() / 24).findFirst().orElse(0);
        if (output <= 0 || state.powerGrids().stream().noneMatch(grid -> facility.planetId().equals(grid.entityId())
                && (grid.isDeficitBrownoutActive() || grid.totalDemandKw() > Math.max(0, grid.totalGenerationKw() - ownKw))))
            return new Operation(Status.NO_DEMAND, 0);
        return plant.fuelMaterialId() == null ? new Operation(null, 0)
                : inputs(hub, Map.of(plant.fuelMaterialId(), plant.fuelKgPerTierDay() * scale));
    }
    private static Operation factory(GameState state, Empire empire, IndustrialFacility facility, IndustryRecipeCatalog.Recipe recipe,
                                     CommercialHub hub, int workers, double workingCash) {
        if (!ProcessInput.environmentAvailable(state, facility.planetId(), recipe.inputSources())) return new Operation(Status.NO_ENVIRONMENT, 0);
        double legacyBatches = workers * facility.getEffectiveThroughputMultiplier()
                * ApplicationProduction.modifiersForOwner(state, empire.id(), facility.applicationId()).effectMultiplier()
                * recipe.technologyMultiplier(empire) / recipe.workersPerBatch();
        var work = ProductionWork.definition(facility.applicationId()) == null ? null : ProductionWork.quote(state, facility, workers);
        if (work != null && !work.blocker().isEmpty()) return new Operation(Status.NO_CAPACITY, 0);
        double batches = work == null ? legacyBatches : work.batchesPerDay();
        if (facility.tier() < recipe.minTier()) return new Operation(Status.NO_CAPACITY, 0);
        var required = new HashMap<String, Double>(); recipe.inputsKg().forEach((id, kg) -> required.put(id, kg * batches));
        var input = inputs(hub, required); if (input.blocker() != null) return input;
        if (ProcessInput.maximumBatches(state, facility.planetId(), recipe.inputSources(), state.geologicalDeposits()) + 1e-8 < batches)
            return new Operation(Status.NO_DEPOSIT, 0);
        double kw = work == null ? PowerProcessor.requestedIndustryKw(facility, workers) : work.energyKwh() / 24;
        var grid = state.powerGrids().stream().filter(value -> facility.planetId().equals(value.entityId())).findFirst().orElse(null);
        var account = state.industryAccounts().stream().filter(value -> facility.id().equals(value.facilityId())).findFirst().orElse(IndustryAccount.empty(facility.id()));
        double tariff = GridSupplyPricing.priceWithOperatingCash(state, facility, workingCash);
        double previousLoad = Math.min(PowerProcessor.requestedIndustryKw(facility, account.paidWorkers()),
                account.powerCostsCredits() / (24 * tariff));
        if (kw > 0 && (grid == null || grid.totalGenerationKw() + grid.currentStoredKwh() / 24
                - Math.max(0, grid.totalDemandKw() - previousLoad) < kw)) return new Operation(Status.NO_POWER, 0);
        boolean wanted = recipe.outputsKg().keySet().stream().anyMatch(id -> {
            var order = hub.activeOrders().get(id);
            return order != null && order.demandKg() > 0 && order.supplyKg() < order.demandKg() * 30;
        });
        double hubCash = state.marketAccounts().stream().filter(accountValue -> hub.id().equals(accountValue.hubId()))
                .mapToDouble(accountValue -> accountValue.unsettledSalesCredits()).findFirst().orElse(0);
        if (!wanted || hub.storageCapacityKg() - hub.currentStoredWeightKg() + required.values().stream().mapToDouble(Double::doubleValue).sum() <= 0
                || hubCash + input.cost() <= 0) return new Operation(Status.NO_DEMAND, 0);
        return new Operation(null, input.cost() + kw * 24 * tariff);
    }
    private static Operation inputs(CommercialHub hub, Map<String, Double> requirements) {
        double cost = 0;
        for (var input : requirements.entrySet()) {
            var order = hub.activeOrders().get(input.getKey());
            if (order == null || order.supplyKg() < input.getValue()) return new Operation(Status.NO_INPUTS, 0);
            cost += input.getValue() * Math.max(0, order.pricePerKg());
        }
        return new Operation(null, cost);
    }
    private static List<Population> populations(GameState state, Empire empire, String body) {
        if (empire == null) return null;
        for (var system : state.solarSystems()) if (empire.controlledSystemIds().contains(system.id())) for (var planet : system.planets()) {
            if (planet.id().equals(body)) return planet.populations();
            for (var moon : planet.moons()) if (moon.id().equals(body)) return moon.populations();
        }
        return null;
    }
}
