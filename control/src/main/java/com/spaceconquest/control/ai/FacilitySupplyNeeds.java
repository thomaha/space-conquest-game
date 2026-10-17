package com.spaceconquest.control.ai;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.logistics.HubReplenishment;
import com.spaceconquest.engine.ship.FleetPositioning;
import com.spaceconquest.engine.technology.ApplicationProduction;
import java.util.*;

/** Daily input and fuel requirements from supported owned facilities, including trained idle workers. */
public final class FacilitySupplyNeeds {
    private FacilitySupplyNeeds() {}
    public record Need(String hubId, String materialId, double dailyKg, double positionKg, double targetKg, boolean generationFuel) {
        public String key() { return hubId + ":" + materialId; }
    }
    public static List<Need> shortages(GameState state, Empire empire, LogisticsPolicy policy) {
        if ("Hive Mind".equalsIgnoreCase(empire.societyStructure())) return List.of();
        var owners = new HashSet<String>(); owners.add(empire.id());
        state.corporations().stream().filter(corp -> empire.id().equals(corp.empireId())).forEach(corp -> owners.add(corp.id()));
        var requirements = new HashMap<String, Map<String, Double>>(); var fuels = new HashSet<String>();
        for (var facility : state.industrialFacilities()) {
            if (!owners.contains(facility.ownerEntityId()) || facility.tier() <= 0 || facility.isUndergoingExpansion()
                    || !ApplicationProduction.isAvailable(empire, facility.applicationId()) || !FacilityManufacturingCapacity.canOperate(state, facility)) continue;
            var hub = state.commercialHubs().stream().filter(value -> facility.planetId().equals(value.entityId())
                    && empire.controlledSystemIds().contains(FleetPositioning.systemForHub(state, value))).findFirst().orElse(null);
            if (hub == null) continue;
            var account = state.industryAccounts().stream().filter(value -> facility.id().equals(value.facilityId())).findFirst().orElse(IndustryAccount.empty(facility.id()));
            long workers = account.paidWorkers() + state.householdAccounts().stream().filter(value -> facility.planetId().equals(value.bodyId())
                    && empire.id().equals(value.empireId()) && facility.workerProfessionId().equals(value.professionId()))
                    .mapToLong(value -> value.employment().unemployedWorkers()).sum();
            workers = Math.min(Math.min(Math.max(0, facility.allocatedWorkers()), workers), population(state, facility.planetId()));
            if (workers <= 0 && (facility.productionEquipment() == null || facility.productionEquipment().autonomousFraction() <= 0)) continue;
            var plant = PowerPlantCatalog.find(facility.applicationId()); var recipe = IndustryRecipeCatalog.forFacility(state, facility);
            var inputs = requirements.computeIfAbsent(hub.id(), key -> new HashMap<>());
            if (plant != null) {
                if (plant.fuelMaterialId() == null || !empire.unlockedTechIds().contains("electricity")
                        || !empire.unlockedTechIds().contains(plant.requiredTechnology())
                        || PowerGenerationProcessor.environmentFactor(state.solarSystems(), facility) <= 0) continue;
                double daily = plant.fuelKgPerTierDay() * facility.tier() * Math.min(1, (double) workers / plant.requiredWorkers());
                inputs.merge(plant.fuelMaterialId(), daily, Double::sum); fuels.add(hub.id() + ":" + plant.fuelMaterialId());
            } else if (IndustryRecipeCatalog.isUnlocked(recipe, empire) && facility.tier() >= recipe.minTier()
                    && ProcessInput.environmentAvailable(state, facility.planetId(), recipe.inputSources())
                    && recipe.outputsKg().keySet().stream().anyMatch(id -> hub.activeOrders().containsKey(id)
                    && hub.activeOrders().get(id).demandKg() > 0 && hub.activeOrders().get(id).supplyKg() < HubReplenishment.targetStockKg(hub, id))) {
                double legacyBatches = workers * facility.getEffectiveThroughputMultiplier() * recipe.technologyMultiplier(empire)
                        * ApplicationProduction.modifiersForOwner(state, facility.ownerEntityId(), facility.applicationId()).effectMultiplier() / recipe.workersPerBatch();
                double batches = ProductionWork.definition(facility.applicationId()) == null ? legacyBatches
                        : ProductionWork.quote(state, facility, (int) workers).batchesPerDay();
                recipe.inputsKg().forEach((id, kg) -> inputs.merge(id, kg * batches, Double::sum));
            }
        }
        var needs = new ArrayList<Need>();
        requirements.forEach((hubId, inputs) -> {
            var hub = state.commercialHubs().stream().filter(value -> value.id().equals(hubId)).findFirst().orElseThrow();
            inputs.forEach((material, daily) -> {
                var order = hub.activeOrders().get(material); if (order == null || !Double.isFinite(daily) || daily <= 0) return;
                double position = order.supplyKg() + HubReplenishment.inboundKg(state, hub, material, null)
                        + com.spaceconquest.engine.logistics.FacilitySupplyStaging.claimedDestinationKg(state, hubId, material);
                var configured = hub.stockTargets().get(material);
                double reorder = configured == null ? daily * policy.reorderDays() : configured.reorderThresholdKg();
                double target = configured == null ? daily * policy.targetDays() : configured.targetStockKg();
                if (position < reorder && target > position) needs.add(new Need(hubId, material, daily, position, target, fuels.contains(hubId + ":" + material)));
            });
        });
        for (var plan : state.supplyDevelopmentPlans()) if (plan.empireId().equals(empire.id()) && com.spaceconquest.engine.logistics.SupplyDevelopmentDemand.valid(state, plan)) {
            var hub = state.commercialHubs().stream().filter(h -> h.id().equals(plan.destinationHubId())).findFirst().orElse(null);
            if (hub == null || needs.stream().anyMatch(n -> n.hubId().equals(hub.id()) && n.materialId().equals(plan.materialId()))) continue;
            var order = hub.activeOrders().get(plan.materialId());
            double position = order == null ? 0 : order.supplyKg();
            position += HubReplenishment.inboundKg(state, hub, plan.materialId(), null)
                    + com.spaceconquest.engine.logistics.FacilitySupplyStaging.claimedDestinationKg(state, hub.id(), plan.materialId());
            double target = order == null ? plan.quantityKg() : Math.max(plan.quantityKg(), order.demandKg());
            if (position < target) needs.add(new Need(hub.id(), plan.materialId(), plan.quantityKg() / policy.targetDays(), position, target, false));
        }
        return needs.stream().sorted(Comparator.comparing(Need::generationFuel).reversed()
                .thenComparingDouble(need -> need.positionKg() / need.dailyKg()).thenComparing(Need::key)).toList();
    }
    private static long population(GameState state, String body) {
        for (var system : state.solarSystems()) for (var planet : system.planets()) {
            if (body.equals(planet.id())) return planet.populations().stream().mapToLong(Population::totalCount).sum();
            for (var moon : planet.moons()) if (body.equals(moon.id())) return moon.populations().stream().mapToLong(Population::totalCount).sum();
        }
        return state.orbitalStations().stream().filter(station -> body.equals(station.id()) && station.isOperational())
                .mapToLong(station -> station.residentCount()).sum();
    }
    private static boolean liquidWater(GameState state, String body) {
        for (var system : state.solarSystems()) for (var planet : system.planets()) {
            if (body.equals(planet.id())) return planet.hasLiquidWater();
            for (var moon : planet.moons()) if (body.equals(moon.id())) return moon.hasLiquidWater();
        }
        return false;
    }
}
