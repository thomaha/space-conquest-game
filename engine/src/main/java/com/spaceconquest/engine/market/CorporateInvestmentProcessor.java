package com.spaceconquest.engine.market;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.FacilityExpansionProject;
import com.spaceconquest.engine.industry.ConstructionMaterialCatalog;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.industry.FacilityManufacturingCapacity;
import com.spaceconquest.engine.industry.IndustryRecipeCatalog;
import com.spaceconquest.engine.ship.*;
import com.spaceconquest.engine.technology.ApplicationProduction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Converts funded corporate investments into physical game-state assets. */
public class CorporateInvestmentProcessor {
    public static final double INFRASTRUCTURE_COST = 8000.0;
    public static final double SHIP_PROCUREMENT_COST = 12000.0;
    public static final double SHORTCOMING_INFRASTRUCTURE_THRESHOLD = 0.50;
    public static final double SHORTCOMING_FLEET_THRESHOLD = 0.40;
    public static final double FACTORY_CONSTRUCTION_WORK_HOURS = 500.0;

    public GameState processCorporateInvestments(GameState state, Map<String, Double> trustPenalties) {
        GameState current = state;
        for (Corporation corporation : state.corporations()) {
            if (trustPenalties != null && trustPenalties.getOrDefault(corporation.empireId(), 0.0) < -20.0) continue;
            if ("TRANSPORT".equalsIgnoreCase(corporation.marketOrientation())) continue;
            GameState snapshot = current;
            CommercialHub target = current.commercialHubs().stream()
                    .filter(hub -> controlsBody(snapshot, corporation, hub.entityId()))
                    .filter(hub -> bestOrder(corporation, hub) != null)
                    .max(Comparator.comparingDouble(hub ->
                            opportunityScore(corporation, hub, bestOrder(corporation, hub))))
                    .orElse(null);
            if (target == null) continue;
            MarketOrder order = bestOrder(corporation, target);
            if (order.shortcomingScore() >= SHORTCOMING_INFRASTRUCTURE_THRESHOLD
                    && opportunityScore(corporation, target, order) > 0.0) {
                current = invest(current, corporation.id(), target.entityId(), "INFRASTRUCTURE", INFRASTRUCTURE_COST);
            }
        }
        return current;
    }

    public boolean canInvest(GameState state, String corporationId, String bodyId, String type, double credits) {
        if (state == null || corporationId == null || bodyId == null || type == null || !Double.isFinite(credits)) return false;
        Corporation corporation = state.corporations().stream()
                .filter(item -> corporationId.equals(item.id())).findFirst().orElse(null);
        if (corporation == null || !controlsBody(state, corporation, bodyId)) return false;
        CommercialHub hub = state.commercialHubs().stream()
                .filter(item -> bodyId.equals(item.entityId())).findFirst().orElse(null);
        if (hub == null) return false;
        Empire empire = state.empires().stream().filter(item -> corporation.empireId().equals(item.id()))
                .findFirst().orElse(null);
        if ("INFRASTRUCTURE".equalsIgnoreCase(type)) {
            MarketOrder order = bestOrder(corporation, hub);
            String application = order == null ? null
                    : applicationForBody(state, corporation, bodyId, order.resourceId());
            if (application == null) return false;
            boolean alreadyBuilding = state.expansionProjects().stream().anyMatch(project ->
                    state.industrialFacilities().stream().anyMatch(facility ->
                            project.facilityId().equals(facility.id())
                                    && corporationId.equals(facility.ownerEntityId())
                                    && bodyId.equals(facility.planetId())
                                    && application.equals(facility.applicationId())));
            if (alreadyBuilding) return false;
            IndustryRecipeCatalog.Recipe recipe = IndustryRecipeCatalog.find(application);
            return credits == INFRASTRUCTURE_COST && corporation.liquidCapitalReserves() >= credits
                    && IndustryRecipeCatalog.isUnlocked(recipe, empire)
                    && (!"surface_water_treatment".equals(application)
                    || hasLiquidWater(state, bodyId))
                    && (!recipe.extractsDeposit() || state.geologicalDeposits().stream().anyMatch(deposit ->
                    bodyId.equals(deposit.planetId()) && deposit.isDiscovered()
                            && deposit.remainingVolumeKg() > 0.0
                            && recipe.outputsKg().containsKey(deposit.materialId())));
        }
        if ("FLEET".equalsIgnoreCase(type) || "SHIP".equalsIgnoreCase(type)) {
            ShipDesign existingDesign = ownedFleetDesign(state, corporation);
            return credits == SHIP_PROCUREMENT_COST && corporation.liquidCapitalReserves() >= credits
                    && empire != null && empire.unlockedTechIds().contains("rocketry")
                    && empire.unlockedTechIds().contains("computers")
                    && (existingDesign == null || PropulsionCatalog.researched(
                    existingDesign.equippedModuleIds(), empire.unlockedTechIds()))
                    && state.shipConstructionOrders().stream()
                    .noneMatch(order -> corporationId.equals(order.ownerEntityId()));
        }
        return false;
    }

    public GameState invest(GameState state, String corporationId, String bodyId, String type, double credits) {
        if (!canInvest(state, corporationId, bodyId, type, credits)) return state;
        Corporation corporation = state.corporations().stream()
                .filter(item -> corporationId.equals(item.id())).findFirst().orElseThrow();
        List<String> facilityIds = new ArrayList<>(corporation.ownedFacilityIds());
        List<String> shipIds = new ArrayList<>(corporation.ownedShipIds());
        GameState.Builder builder = state.toBuilder();
        if ("INFRASTRUCTURE".equalsIgnoreCase(type)) {
            CommercialHub hub = state.commercialHubs().stream()
                    .filter(item -> bodyId.equals(item.entityId())).findFirst().orElseThrow();
            String application = applicationForBody(state, corporation, bodyId,
                    bestOrder(corporation, hub).resourceId());
            IndustryRecipeCatalog.Recipe recipe = IndustryRecipeCatalog.find(application);
            String id = "facility_corp_" + UUID.randomUUID();
            int targetTier = Math.max(recipe.minTier(),
                    FacilityManufacturingCapacity.minimumTier(state, corporationId, application));
            String profession = switch (application) {
                case "mining_outpost" -> "miner";
                case "industrial_soil_cultivation" -> "farmer";
                default -> "industrial_worker";
            };
            List<IndustrialFacility> facilities = new ArrayList<>(state.industrialFacilities());
            facilities.add(new IndustrialFacility(id, bodyId, application, corporationId,
                    IndustrialFacility.PRIVATE_CORPORATE, 0,
                    Math.max(1, recipe.workersPerBatch()), profession, true, 0.0));
            builder.industrialFacilities(facilities);
            List<FacilityExpansionProject> projects = new ArrayList<>(state.expansionProjects());
            projects.add(new FacilityExpansionProject("project_corp_" + UUID.randomUUID(), id,
                    targetTier, 0.0, ApplicationProduction.constructionWorkHours(
                    state, corporationId, application, FACTORY_CONSTRUCTION_WORK_HOURS * targetTier), credits,
                    ConstructionMaterialCatalog.facility(state, corporationId, application, targetTier), Map.of()));
            builder.expansionProjects(projects);
            facilityIds.add(id);
        } else {
            ShipDesign design = ownedFleetDesign(state, corporation);
            List<ShipDesign> designs = new ArrayList<>(state.shipDesigns());
            if (design == null) {
                design = createFleetDesign(state, corporation);
                designs.add(design);
                builder.shipDesigns(designs);
            }
            ShipConstructionRequirements.Estimate quote = ShipConstructionRequirements.estimate(design);
            List<ShipConstructionOrder> orders = new ArrayList<>(state.shipConstructionOrders());
            orders.add(new ShipConstructionOrder("corp_" + UUID.randomUUID(), corporationId,
                    design.id(), systemForBody(state, bodyId), bodyId, 0.0,
                    quote.workUnits(), quote.materialsKg(), Map.of()));
            builder.shipConstructionOrders(orders);
        }
        List<Corporation> corporations = new ArrayList<>();
        for (Corporation item : state.corporations()) {
            corporations.add(item.id().equals(corporationId) ? new Corporation(item.id(), item.name(),
                    item.empireId(), item.headquartersEntityId(), item.marketOrientation(),
                    item.liquidCapitalReserves() - credits, facilityIds, shipIds, item.claimedVeinIds()) : item);
        }
        return builder.corporations(corporations).build();
    }

    private String fleetRole(Corporation corporation) {
        return "EXTRACTION".equalsIgnoreCase(corporation.marketOrientation())
                ? ShipRole.MINING_SHIP : ShipRole.CARGO_TRANSPORT;
    }

    private ShipDesign ownedFleetDesign(GameState state, Corporation corporation) {
        String role = fleetRole(corporation);
        return state.shipDesigns().stream()
                .filter(design -> corporation.id().equals(design.ownerEntityId()) && role.equals(design.role()))
                .findFirst().orElse(null);
    }

    /** Keeps the provisional corporate hull while using researched drive stats and frozen production values. */
    private ShipDesign createFleetDesign(GameState state, Corporation corporation) {
        Empire empire = state.empires().stream().filter(item -> corporation.empireId().equals(item.id()))
                .findFirst().orElseThrow();
        String driveId = empire.unlockedTechIds().contains("nuclear_fission")
                ? "mod_fission_thruster" : "mod_chemical_rocket";
        ShipModule baseDrive = PropulsionCatalog.module(driveId);
        ShipModule drive = ShipApplicationProduction.optimize(state, corporation.id(), baseDrive);
        ShipModule cargo = ShipApplicationProduction.optimize(state, corporation.id(),
                ShipComponentCatalog.module("mod_cargo_hold_large"));
        List<ShipModule> modules = List.of(drive, cargo, PropulsionCatalog.fuelTankModule());
        double cargoCapacity = cargo.operationalStats().get("cargoCapacityKg");
        double minimumLaunchThrust = 300_000.0 * (25_000.0 + cargoCapacity + 15_000.0) / 90_000.0;
        String role = fleetRole(corporation);
        return new ShipDesign("design_corp_" + UUID.randomUUID(), corporation.name() + " " + role,
                corporation.id(), role, "refined_aluminum",
                List.of("mod_cargo_hold_large", drive.id(), PropulsionCatalog.FUEL_TANK_MODULE_ID),
                "steel", 2.0, 25_000.0, cargoCapacity, 15_000.0,
                150.0, 1.20, minimumLaunchThrust, drive.thrustOutputN(), drive.thrustOutputN() >= minimumLaunchThrust, true,
                ShipApplicationProduction.profile(state, corporation.id(), modules, 25_000.0));
    }

    public double findMaxShortcoming(Corporation corporation, List<CommercialHub> hubs) {
        return hubs == null ? 0.0 : hubs.stream().map(hub -> bestOrder(corporation, hub))
                .filter(order -> order != null).mapToDouble(MarketOrder::shortcomingScore).max().orElse(0.0);
    }

    private MarketOrder bestOrder(Corporation corporation, CommercialHub hub) {
        return hub.activeOrders().values().stream()
                .filter(order -> "TRANSPORT".equalsIgnoreCase(corporation.marketOrientation())
                        || applicationFor(corporation, order.resourceId()) != null)
                .max(Comparator.<MarketOrder>comparingDouble(order ->
                        opportunityScore(corporation, hub, order))
                        .thenComparingDouble(MarketOrder::shortcomingScore)).orElse(null);
    }

    private double opportunityScore(Corporation corporation, CommercialHub hub, MarketOrder order) {
        if ("TRANSPORT".equalsIgnoreCase(corporation.marketOrientation())) {
            return order.shortcomingScore();
        }
        String application = applicationFor(corporation, order.resourceId());
        IndustryRecipeCatalog.Recipe recipe = IndustryRecipeCatalog.find(application);
        if (recipe == null || order.demandKg() <= 0.0 || order.shortcomingScore() <= 0.0) return 0.0;
        double revenue = recipe.outputsKg().entrySet().stream().mapToDouble(output -> {
            MarketOrder buyer = hub.activeOrders().get(output.getKey());
            return buyer == null || buyer.demandKg() <= 0.0 ? 0.0
                    : Math.min(output.getValue(), buyer.demandKg())
                    * buyer.pricePerKg() * 0.80;
        }).sum();
        double inputs = recipe.inputsKg().entrySet().stream().mapToDouble(input -> {
            MarketOrder seller = hub.activeOrders().get(input.getKey());
            return input.getValue() * (seller == null
                    ? MarketProcessor.basePricePerKg(input.getKey()) : seller.pricePerKg());
        }).sum();
        String profession = application.endsWith("_mining") || "mining_outpost".equals(application)
                ? "miner" : "industrial_soil_cultivation".equals(application)
                ? "farmer" : "industrial_worker";
        double payroll = recipe.workersPerBatch() * Profession.getBaseWageForProfession(profession);
        double power = recipe.powerDrawKw() * 24.0 * 0.02;
        double profit = revenue - inputs - payroll - power;
        double construction = ConstructionMaterialCatalog.facility(application, recipe.minTier())
                .entrySet().stream().mapToDouble(material ->
                        material.getValue() * MarketProcessor.basePricePerKg(material.getKey())).sum();
        return Math.max(0.0, profit) * order.shortcomingScore()
                / (INFRASTRUCTURE_COST + construction);
    }

    private String applicationFor(Corporation corporation, String resourceId) {
        return switch (corporation.marketOrientation().toUpperCase()) {
            case "EXTRACTION" -> switch (resourceId) {
                case "iron_ore" -> "mining_outpost";
                case "aluminum_ore", "copper_ore", "silicates", "nitrates", "phosphates",
                        "potash", "carbon", "water_ice", "gold", "silver_ore",
                        "rare_earth_fluorides", "uranium_ore", "lithium_ore",
                        "nickel_ore" -> resourceId + "_mining";
                default -> null;
            };
            case "METALLURGY" -> switch (resourceId) {
                case "refined_iron" -> "pyro_iron_smelting";
                case "steel" -> "alloy_steel";
                default -> null;
            };
            case "AGRICULTURE" -> switch (resourceId) {
                case "food_matrix" -> "industrial_soil_cultivation";
                case "purified_water" -> "surface_water_treatment";
                default -> null;
            };
            case "CONSUMER" -> switch (resourceId) {
                case "refined_iron" -> "pyro_iron_smelting";
                case "steel" -> "alloy_steel";
                case "bio_polymers" -> "biomass_processing";
                case "refined_aluminum" -> "aluminum_refining";
                case "refined_copper" -> "copper_refining";
                case "silicon" -> "silicon_refining";
                case "refined_rare_earths" -> "rare_earth_refining";
                case "consumer_goods" -> "consumer_goods_mfg";
                case "luxury_goods" -> "luxury_goods_mfg";
                default -> null;
            };
            default -> null;
        };
    }

    private String applicationForBody(GameState state, Corporation corporation, String bodyId,
                                      String resourceId) {
        String application = applicationFor(corporation, resourceId);
        if ("surface_water_treatment".equals(application) && !hasLiquidWater(state, bodyId)) {
            return "ice_water_treatment";
        }
        return application;
    }

    private boolean hasLiquidWater(GameState state, String bodyId) {
        for (SolarSystem system : state.solarSystems()) {
            for (Planet planet : system.planets()) {
                if (bodyId.equals(planet.id())) return planet.hasLiquidWater();
                for (Moon moon : planet.moons()) {
                    if (bodyId.equals(moon.id())) return moon.hasLiquidWater();
                }
            }
        }
        return false;
    }

    private boolean controlsBody(GameState state, Corporation corporation, String bodyId) {
        String systemId = systemForBody(state, bodyId);
        return state.empires().stream().anyMatch(empire -> corporation.empireId().equals(empire.id())
                && empire.controlledSystemIds().contains(systemId));
    }

    private String systemForBody(GameState state, String bodyId) {
        for (SolarSystem system : state.solarSystems()) {
            for (Planet planet : system.planets()) {
                if (planet.id().equals(bodyId)) return system.id();
                for (Moon moon : planet.moons()) if (moon.id().equals(bodyId)) return system.id();
            }
        }
        return "";
    }
}
