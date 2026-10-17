package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.ConstructionMaterialCatalog;
import com.spaceconquest.engine.macrostructure.ConstructionDeploymentProject;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.macrostructure.StationModule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Queues station module assembly against the station's available slots. */
public record AddStationModuleCommand(
        String stationId, String moduleName, String moduleType, int slotSize,
        double dryMassKg, double powerDrawKw, double powerOutputKw,
        String professionId, int requiredWorkers
) implements GameCommand {
    @Override
    public boolean validate(GameState state) {
        if (state == null || stationId == null || moduleType == null) return false;
        OrbitalStation station = state.orbitalStations().stream()
                .filter(item -> stationId.equals(item.id())).findFirst().orElse(null);
        if (station == null) return false;
        boolean researched = state.empires().stream().anyMatch(empire ->
                empire.controlledSystemIds().contains(station.systemId())
                        && empire.unlockedTechIds().contains("space_stations")
                        && com.spaceconquest.engine.ship.SensorModuleCatalog.researched(List.of(moduleType), empire.unlockedTechIds())
                        && com.spaceconquest.engine.ship.BaseRepairCatalog.researched(moduleType, empire.unlockedTechIds())
                        && (!StationModule.TYPE_SOLAR_ARRAY.equalsIgnoreCase(moduleType)
                        || empire.unlockedTechIds().contains("electricity") && empire.unlockedTechIds().contains("solar_power"))
                        && (empire.id().equals(station.ownerEntityId())
                        || state.corporations().stream().anyMatch(corporation ->
                        corporation.id().equals(station.ownerEntityId())
                                && corporation.empireId().equals(empire.id()))));
        if (!researched) return false;
        var yard = com.spaceconquest.engine.ship.ShipyardTechnology.yard(moduleType);
        if (yard != null && !state.empires().stream().anyMatch(empire ->
                empire.controlledSystemIds().contains(station.systemId())
                        && empire.unlockedTechIds().contains(com.spaceconquest.engine.ship.ShipyardTechnology.application(moduleType)))) return false;
        long reserved = com.spaceconquest.engine.industry.CultivationSpace.farmSlots(state, stationId, "")
                + com.spaceconquest.engine.industry.CultivationSpace.moduleSlots(state, station);
        var sensor = com.spaceconquest.engine.ship.SensorModuleCatalog.module(moduleType);
        if (StationModule.TYPE_REPAIR_WORKSHOP.equals(moduleType))
            return reserved + com.spaceconquest.engine.ship.BaseRepairCatalog.module().slotSize() <= station.totalSlots();
        return reserved + (yard != null ? yard.slots() : sensor == null ? Math.max(1, slotSize) : sensor.slotCost()) <= station.totalSlots();
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) return state;
        OrbitalStation station = state.orbitalStations().stream()
                .filter(item -> stationId.equals(item.id())).findFirst().orElseThrow();
        double mass = dryMassKg > 0.0 ? dryMassKg : 10_000.0;
        StationModule module = new StationModule("mod_" + UUID.randomUUID(),
                moduleName == null ? moduleType : moduleName, moduleType,
                Math.max(1, slotSize), mass, powerDrawKw, powerOutputKw, Map.of(),
                professionId == null ? "technician" : professionId,
                Math.max(0, requiredWorkers), true);
        var sensor = com.spaceconquest.engine.ship.SensorModuleCatalog.stationModule(moduleType);
        if (StationModule.TYPE_REPAIR_WORKSHOP.equals(moduleType)) sensor = com.spaceconquest.engine.ship.BaseRepairCatalog.module();
        if (sensor != null) {
            module = new StationModule("mod_" + UUID.randomUUID(), sensor.name(), sensor.type(), sensor.slotSize(),
                    sensor.dryMassKg(), sensor.powerDrawKw(), 0, sensor.materialInputs(),
                    sensor.workforceProfessionId(), sensor.requiredWorkers(), true);
            mass = sensor.dryMassKg();
        }
        var yard = com.spaceconquest.engine.ship.ShipyardTechnology.yard(moduleType);
        if (yard != null) { module = yard.module(moduleType); mass = yard.dryMassKg(); }
        ConstructionDeploymentProject project = new ConstructionDeploymentProject(
                "module_" + UUID.randomUUID(), "", station.systemId(),
                station.planetOrbitId(), ConstructionDeploymentProject.TYPE_STATION_MODULE,
                0.0, yard == null ? Math.max(2.0, Math.ceil(mass / 10_000.0)) : yard.constructionWork(), station.ownerEntityId(),
                yard == null ? ConstructionMaterialCatalog.stationModule(mass) : yard.materialsKg(), Map.of(),
                module.name(), station.ownershipType(), station.id(), module,
                0, station.armorMaterialId(), station.armorThicknessCm(), 0.0, false);
        List<ConstructionDeploymentProject> projects = new ArrayList<>(state.constructionProjects());
        projects.add(project);
        return state.withConstructionProjects(projects);
    }
}
