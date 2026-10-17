package com.spaceconquest.control.ai;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.ship.*;
import com.spaceconquest.engine.technology.*;
import java.util.*;

/** Reads current owned assets and shortages; research does not supply missing stock or retrofit ships. */
public final class EmpireResearchNeeds {
    private final GameState state;
    private final Empire empire;
    private final Set<String> owners = new HashSet<>(), deficitBodies = new HashSet<>(), materials = new HashSet<>(), fleetApps = new HashSet<>();
    private final List<IndustrialFacility> facilities;
    private final boolean atWar;
    private final boolean fleetShortage;
    private final String fleetReason;
    private final Set<String> supplyGoals;
    public EmpireResearchNeeds(GameState state, Empire empire) {
        this.state = state; this.empire = empire; owners.add(empire.id());
        supplyGoals = SupplyChainInvestmentPlanner.researchGoals(state, empire);
        var fleetNeeds = EmpireFleetNeeds.assess(state, empire);
        fleetShortage = !fleetNeeds.isEmpty(); fleetReason = fleetShortage ? fleetNeeds.getFirst().reason() : "";
        state.corporations().stream().filter(corporation -> empire.id().equals(corporation.empireId())).forEach(corporation -> owners.add(corporation.id()));
        facilities = state.industrialFacilities().stream().filter(facility -> owners.contains(facility.ownerEntityId()) && facility.tier() > 0).toList();
        atWar = state.diplomaticRelations().stream().anyMatch(relation -> "TOTAL_WAR".equals(relation.tier())
                && (empire.id().equals(relation.empireAId()) || empire.id().equals(relation.empireBId())));
        for (var system : state.solarSystems()) if (empire.controlledSystemIds().contains(system.id())) for (var planet : system.planets()) {
            deficit(planet.id()); planet.moons().forEach(moon -> deficit(moon.id()));
        }
        state.orbitalStations().stream().filter(station -> owners.contains(station.ownerEntityId())).forEach(station -> deficit(station.id()));
        for (var facility : facilities) {
            var account = state.industryAccounts().stream().filter(value -> facility.id().equals(value.facilityId())).findFirst().orElse(null);
            var recipe = IndustryRecipeCatalog.find(facility.applicationId());
            if (recipe != null && account != null && account.paidWorkers() > 0)
                recipe.inputsKg().keySet().stream().filter(id -> !stock(facility.planetId(), id)).forEach(materials::add);
            var plant = PowerPlantCatalog.find(facility.applicationId());
            if (plant != null && plant.fuelMaterialId() != null && !stock(facility.planetId(), plant.fuelMaterialId())) materials.add(plant.fuelMaterialId());
        }
        for (var fleet : state.fleets()) if (owners.contains(fleet.ownerEntityId())) for (var ship : fleet.ships()) {
            if (ship.currentHullHealth() <= 0) continue;
            var design = state.shipDesigns().stream().filter(value -> ship.designId().equals(value.id())).findFirst().orElse(null);
            if (design == null) continue;
            for (String id : design.equippedModuleIds()) {
                String app = ShipApplicationProduction.applicationId(id); if (app != null) fleetApps.add(app);
                var weapon = CombatModuleCatalog.module(design, id);
                if (weapon != null && !weapon.ammunitionId().isBlank() && ShipWeaponAmmunition.rounds(ship.storedCargoKg(), weapon) == 0)
                    materials.add(weapon.ammunitionId());
            }
        }
    }
    private void deficit(String body) {
        if (state.powerGrids().stream().anyMatch(grid -> body.equals(grid.entityId()) && (grid.isDeficitBrownoutActive() || grid.netBalanceKw() < 0))) deficitBodies.add(body);
    }
    private boolean stock(String body, String material) {
        return state.commercialHubs().stream().filter(hub -> body.equals(hub.entityId())).anyMatch(hub ->
                hub.activeOrders().containsKey(material) && hub.activeOrders().get(material).supplyKg() > 0);
    }
    public int priority(TechnicalApplication app) {
        if (!empire.unlockedTechIds().contains(app.id()) && facilities.stream().anyMatch(facility -> app.id().equals(facility.applicationId()))) return 0;
        var plant = PowerPlantCatalog.find(app.id());
        if (plant != null && !deficitBodies.isEmpty()) {
            if (facilities.stream().anyMatch(facility -> app.id().equals(facility.applicationId()) && deficitBodies.contains(facility.planetId())
                    && PowerGenerationProcessor.environmentFactor(state.solarSystems(), facility) > 0
                    && (plant.fuelMaterialId() == null || stock(facility.planetId(), plant.fuelMaterialId())))) return 1;
            if (!empire.unlockedTechIds().contains(app.id()) && plant.fuelMaterialId() == null && deficitBodies.stream().anyMatch(body ->
                    PowerGenerationProcessor.environmentFactor(state.solarSystems(), new IndustrialFacility("preview", body, app.id(), empire.id(),
                            IndustrialFacility.PUBLIC_STATE, 1, 0, "technician", false, 0)) > 0)) return 2;
        }
        var recipe = IndustryRecipeCatalog.find(app.id());
        if (supplyGoals.contains(app.id()) && !empire.unlockedTechIds().contains(app.id())) return 3;
        if (recipe != null && recipe.outputsKg().keySet().stream().anyMatch(materials::contains)) return 3;
        if (fleetShortage && EmpireFleetNeeds.urgentApplication(state, empire, app) || atWar && !fleetShortage && app.combatModule() != null) return 4;
        if (fleetApps.contains(app.id())) return 5;
        if (app.surveyCapability() != null && state.surveyTeams().stream().noneMatch(t -> t.empireId().equals(empire.id()))
                && SurveyCapability.all().stream().anyMatch(d -> d.applicationId().equals(app.id()) && empire.unlockedTechIds().contains(d.technologyId())))
            return app.surveyCapability().mode() == SurveyCapability.Mode.SURFACE ? 5 : 6;
        if (recipe != null || plant != null) return 6;
        if (app.combatModule() != null || app.auxiliaryHardware() != null) return 7;
        return 8;
    }
    public String optimizationPath(TechnicalApplication app) {
        if (priority(app) > 4) return "PATH_B";
        return canApply(app, ResearchPolicy.current().performance().complexityShift()) ? "PATH_A" : "PATH_B";
    }
    public String reason(TechnicalApplication app) {
        if (app.surveyCapability() != null) return "Maintain continuous geological fieldwork as the empire expands.";
        return switch (priority(app)) {
            case 0 -> "Existing public or corporate facility lacks this application discovery.";
            case 1 -> "Local power deficit; improve feasible installed generation.";
            case 2 -> "Local power deficit; discover feasible fuel-free generation.";
            case 3 -> {
                if (supplyGoals.contains(app.id())) yield "Funded local demand needs this supply-chain production, power or launch capability.";
                var recipe = IndustryRecipeCatalog.find(app.id());
                yield "Empty factory inputs, generation fuel or fleet magazines need: " + String.join(", ", recipe.outputsKg().keySet().stream()
                        .filter(materials::contains).sorted().map(id -> id.replace('_', ' ')).toList()) + ".";
            }
            case 4 -> fleetShortage ? fleetReason + "; this application supports new ship construction." : "Active war raises the need for combat equipment.";
            case 5 -> "Supports components installed in a living owned fleet; no urgent shortage identified.";
            case 6 -> "Usable factory or generation development; no urgent shortage identified.";
            case 7 -> "Usable combat or auxiliary hardware development; no urgent shortage identified.";
            default -> "Usable application development; no urgent shortage identified.";
        };
    }
    public boolean canApply(TechnicalApplication app, int shift) {
        boolean yards = state.shipDesigns().stream().filter(design -> empire.id().equals(design.ownerEntityId()) && EmpireFleetPlanner.combat(design.role()))
                .flatMap(design -> design.equippedModuleIds().stream()).filter(module -> app.id().equals(ShipApplicationProduction.applicationId(module)))
                .allMatch(module -> {
                    var hardware = ShipComponentCatalog.module(module);
                    return hardware == null || hardware.complexityLevel() + ApplicationProduction.modifiersForOwner(state, empire.id(), app.id()).complexityShift() + shift
                            <= ShipManufacturingCapacity.forOwner(state, empire.id());
                });
        if (app.combatModule() != null && fleetShortage) yards &= app.combatModule().complexityLevel()
                + ApplicationProduction.modifiersForOwner(state, empire.id(), app.id()).complexityShift() + shift <= ShipManufacturingCapacity.forOwner(state, empire.id());
        return yards && facilities.stream().filter(facility -> app.id().equals(facility.applicationId())).allMatch(facility ->
                FacilityManufacturingCapacity.available(app.id(), facility.tier()) >= FacilityManufacturingCapacity.required(state, facility.ownerEntityId(), app.id()) + shift);
    }
}
