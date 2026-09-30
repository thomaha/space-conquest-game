package com.spaceconquest.engine.market;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.MarketOrder;
import com.spaceconquest.engine.Population;
import com.spaceconquest.engine.PopulationProcessor;
import com.spaceconquest.engine.Race;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.industry.IndustryRecipeCatalog;
import com.spaceconquest.engine.industry.PowerPlantCatalog;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FleetLocation;
import com.spaceconquest.engine.ship.FleetPositioning;
import com.spaceconquest.engine.ship.PropulsionCatalog;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipInstance;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Refreshes local desired demand from current residents and staffed production capacity. */
public class MarketDemandProcessor {
    private record Residents(List<Population> populations, String atmosphere, boolean hive) {}

    private final PopulationProcessor populationProcessor = new PopulationProcessor();

    public List<CommercialHub> refresh(GameState state, List<Race> races) {
        Map<String, Race> raceById = new HashMap<>();
        for (Race race : races) raceById.put(race.id(), race);
        Map<String, Residents> residents = residentsByBody(state);
        Set<String> managedGoods = managedGoods(races);
        Map<String, Map<String, Double>> constructionDemand = constructionDemand(state);
        Map<String, Corporation> corporations = new HashMap<>();
        for (Corporation corporation : state.corporations()) corporations.put(corporation.id(), corporation);
        Map<String, Empire> empires = new HashMap<>();
        for (Empire empire : state.empires()) empires.put(empire.id(), empire);

        return state.commercialHubs().stream().map(hub -> {
            Map<String, Double> demand = new HashMap<>();
            Residents local = residents.get(hub.entityId());
            if (local != null && !local.hive()) {
                for (Population population : local.populations()) {
                    Race race = raceById.get(population.raceId());
                    Map<String, Double> basics = race == null
                            ? Map.of("food_matrix", population.totalCount() * 0.1)
                            : populationProcessor.calculateDailyMarketRequirements(
                                    population.totalCount(), race, local.atmosphere());
                    basics.forEach((resource, kg) -> demand.merge(resource, kg, Double::sum));
                    demand.merge("consumer_goods", population.totalCount() * 0.005, Double::sum);
                    demand.merge("luxury_goods", population.totalCount() * 0.001, Double::sum);
                }
            }
            for (IndustrialFacility facility : state.industrialFacilities()) {
                if (!hub.entityId().equals(facility.planetId())) continue;
                Empire owner = technologyOwner(facility, empires, corporations);
                if ("cargo_terminal".equals(facility.applicationId()) && facility.tier() > 0) {
                    PropulsionCatalog.drive("mod_chemical_rocket")
                            .propellantMaterials(1_000.0 * facility.tier()).forEach(
                                    (material, quantity) -> demand.merge(material, quantity,
                                            Double::sum));
                    continue;
                }
                PowerPlantCatalog.Plant plant = PowerPlantCatalog.find(facility.applicationId());
                if (plant != null) {
                    if (owner != null && facility.tier() > 0 && facility.allocatedWorkers() > 0
                            && owner.unlockedTechIds().contains("electricity")
                            && owner.unlockedTechIds().contains(plant.requiredTechnology())
                            && plant.fuelMaterialId() != null) {
                        double staffing = Math.min(1.0,
                                (double) facility.allocatedWorkers() / plant.requiredWorkers());
                        double scale = facility.tier() * staffing
                                * facility.getEffectiveThroughputMultiplier()
                                / Math.pow(1.5, Math.max(0, facility.tier() - 1));
                        demand.merge(plant.fuelMaterialId(),
                                plant.fuelKgPerTierDay() * scale, Double::sum);
                    }
                    continue;
                }
                IndustryRecipeCatalog.Recipe recipe = IndustryRecipeCatalog.find(facility.applicationId());
                if (!IndustryRecipeCatalog.isUnlocked(recipe, owner)
                        || facility.tier() < recipe.minTier()) continue;
                double batches = Math.max(0, facility.allocatedWorkers())
                        * facility.getEffectiveThroughputMultiplier()
                        * recipe.technologyMultiplier(owner) / recipe.workersPerBatch();
                recipe.inputsKg().forEach((resource, kg) ->
                        demand.merge(resource, batches * kg, Double::sum));
            }
            constructionDemand.getOrDefault(hub.entityId(), Map.of()).forEach((resource, kg) ->
                    demand.merge(resource, kg, Double::sum));
            shipFuelDemand(state, hub, demand);
            Map<String, MarketOrder> orders = new HashMap<>(hub.activeOrders());
            Set<String> allGoods = new HashSet<>(managedGoods);
            allGoods.addAll(demand.keySet());
            for (String resource : allGoods) {
                MarketOrder old = orders.get(resource);
                double desired = Math.max(0.0, demand.getOrDefault(resource, 0.0));
                if (old == null && desired <= 0.0) continue;
                orders.put(resource, new MarketOrder(resource, old == null ? 0.0 : old.supplyKg(),
                        desired, old == null ? 2.0 : old.pricePerKg(),
                        old == null ? 0.0 : old.shortcomingScore()));
            }
            return new CommercialHub(hub.id(), hub.entityId(), hub.transactionTariffRate(),
                    hub.storageCapacityKg(), hub.currentStoredWeightKg(), hub.logisticsRangeUnits(),
                    Map.copyOf(orders));
        }).toList();
    }

    private Map<String, Map<String, Double>> constructionDemand(GameState state) {
        Map<String, Map<String, Double>> byBody = new HashMap<>();
        for (var project : state.expansionProjects()) {
            IndustrialFacility facility = state.industrialFacilities().stream()
                    .filter(item -> project.facilityId().equals(item.id())).findFirst().orElse(null);
            if (facility == null) continue;
            Map<String, Double> demand = byBody.computeIfAbsent(facility.planetId(),
                    ignored -> new HashMap<>());
            project.requiredMaterialsKg().forEach((resource, total) -> {
                double remaining = Math.max(0.0,
                        total - project.consumedMaterialsKg().getOrDefault(resource, 0.0));
                demand.merge(resource, remaining / 30.0, Double::sum);
            });
        }
        for (var order : state.shipConstructionOrders()) {
            Map<String, Double> demand = byBody.computeIfAbsent(order.yardBodyId(),
                    ignored -> new HashMap<>());
            order.requiredMaterialsKg().forEach((resource, total) -> {
                double remaining = Math.max(0.0,
                        total - order.consumedMaterialsKg().getOrDefault(resource, 0.0));
                demand.merge(resource, remaining / 30.0, Double::sum);
            });
        }
        for (var project : state.constructionProjects()) {
            String body = com.spaceconquest.engine.macrostructure.ConstructionDeploymentProject
                    .TYPE_SPACE_ELEVATOR.equalsIgnoreCase(project.targetStructureType())
                    ? com.spaceconquest.engine.industry.ConstructionMaterials.bodyForSystem(
                    state, project.targetSystemId(), project.targetCelestialId())
                    : com.spaceconquest.engine.industry.ConstructionMaterials.orbitalHubEntity(
                    state, project.targetSystemId(), project.targetStationId() == null
                            ? project.targetCelestialId() : project.targetStationId());
            if (body == null) continue;
            Map<String, Double> demand = byBody.computeIfAbsent(body, ignored -> new HashMap<>());
            project.requiredMaterialsKg().forEach((resource, total) -> demand.merge(resource,
                    Math.max(0.0, total - project.consumedMaterialsKg()
                            .getOrDefault(resource, 0.0)) / 30.0, Double::sum));
        }
        for (var mega : state.megastructures()) {
            if (mega.isFullyConstructed()) continue;
            String body = com.spaceconquest.engine.industry.ConstructionMaterials.orbitalHubEntity(
                    state, mega.systemId(), mega.targetCelestialId());
            if (body == null) continue;
            Map<String, Double> demand = byBody.computeIfAbsent(body, ignored -> new HashMap<>());
            mega.requiredMaterialsKg().forEach((resource, total) -> demand.merge(resource,
                    Math.max(0.0, total - mega.consumedMaterialsKg()
                            .getOrDefault(resource, 0.0)) / 30.0, Double::sum));
        }
        for (var project : state.terraformingProjects()) {
            if (project.isCompleted()) continue;
            String systemId = com.spaceconquest.engine.industry.ConstructionMaterials.systemForBody(
                    state, project.planetId());
            String body = com.spaceconquest.engine.industry.ConstructionMaterials.bodyForSystem(
                    state, systemId, project.planetId());
            if (body == null) continue;
            Map<String, Double> demand = byBody.computeIfAbsent(body, ignored -> new HashMap<>());
            project.requiredMaterialsKg().forEach((resource, total) -> demand.merge(resource,
                    Math.max(0.0, total - project.consumedMaterialsKg()
                            .getOrDefault(resource, 0.0)) / 30.0, Double::sum));
        }
        return byBody;
    }

    private void shipFuelDemand(GameState state, CommercialHub hub,
                                Map<String, Double> demand) {
        FleetLocation.Site hubSite = FleetPositioning.hubSite(state, hub);
        if (hubSite == null) return;
        for (Fleet fleet : state.fleets()) {
            if (fleet.hasInterstellarOrder() || fleet.location().inTransit()
                    || !(fleet.location().isAt(hubSite)
                    || hubSite.kind() == FleetLocation.Kind.SURFACE
                    && fleet.location().isAt(FleetLocation.Site.orbit(hub.entityId()))))
                continue;
            for (ShipInstance ship : fleet.ships()) {
                ShipDesign design = state.shipDesigns().stream()
                        .filter(item -> ship.designId().equals(item.id()))
                        .findFirst().orElse(null);
                if (design == null) continue;
                PropulsionCatalog.Drive drive = PropulsionCatalog.mainDrive(
                        design.equippedModuleIds());
                if (drive == null) continue;
                double missingPropellant = Math.max(0.0,
                        design.fuelCapacityKg() - ship.currentFuelKg());
                if (missingPropellant > 0.0)
                    drive.propellantMaterials(missingPropellant / 30.0).forEach(
                            (material, quantity) -> demand.merge(material, quantity, Double::sum));
                PropulsionCatalog.ReactorFuel reactor =
                        PropulsionCatalog.preferredReactorFuel(drive, ship);
                if (reactor == null || reactor.kgPerPropellantKg() <= 0.0) continue;
                double missingReactor = Math.max(0.0,
                        design.fuelCapacityKg() * reactor.kgPerPropellantKg()
                                - ship.storedCargoKg().getOrDefault(reactor.materialId(), 0.0));
                if (missingReactor > 0.0)
                    demand.merge(reactor.materialId(), missingReactor / 30.0, Double::sum);
            }
        }
    }

    private Map<String, Residents> residentsByBody(GameState state) {
        Map<String, Residents> bodies = new HashMap<>();
        for (var system : state.solarSystems()) {
            Empire owner = state.empires().stream()
                    .filter(empire -> empire.controlledSystemIds().contains(system.id()))
                    .findFirst().orElse(null);
            boolean hive = owner != null && owner.societyStructure() != null
                    && owner.societyStructure().toLowerCase().contains("hive");
            for (var planet : system.planets()) {
                bodies.put(planet.id(), new Residents(planet.populations(), planet.atmosphere(), hive));
                for (var moon : planet.moons()) {
                    bodies.put(moon.id(), new Residents(moon.populations(), moon.atmosphere(), hive));
                }
            }
        }
        return bodies;
    }

    private Set<String> managedGoods(List<Race> races) {
        Set<String> resources = new HashSet<>(Set.of("food_matrix", "consumer_goods", "luxury_goods"));
        for (Race race : races) {
            resources.addAll(populationProcessor.calculateDailyMarketRequirements(1_000, race, "none").keySet());
        }
        for (IndustryRecipeCatalog.Recipe recipe : IndustryRecipeCatalog.all()) {
            resources.addAll(recipe.inputsKg().keySet());
        }
        return resources;
    }

    private Empire technologyOwner(IndustrialFacility facility, Map<String, Empire> empires,
                                   Map<String, Corporation> corporations) {
        if (!IndustrialFacility.PRIVATE_CORPORATE.equals(facility.ownershipType())) {
            return empires.get(facility.ownerEntityId());
        }
        Corporation corporation = corporations.get(facility.ownerEntityId());
        return corporation == null ? null : empires.get(corporation.empireId());
    }
}
