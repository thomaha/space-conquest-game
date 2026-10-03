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
                        && (!StationModule.TYPE_SOLAR_ARRAY.equalsIgnoreCase(moduleType)
                        || empire.unlockedTechIds().contains("electricity") && empire.unlockedTechIds().contains("solar_power"))
                        && (empire.id().equals(station.ownerEntityId())
                        || state.corporations().stream().anyMatch(corporation ->
                        corporation.id().equals(station.ownerEntityId())
                                && corporation.empireId().equals(empire.id()))));
        if (!researched) return false;
        int reserved = state.constructionProjects().stream()
                .filter(project -> stationId.equals(project.targetStationId())
                        && project.plannedModule() != null)
                .mapToInt(project -> project.plannedModule().slotSize()).sum();
        return station.hasAvailableSlots(reserved + Math.max(1, slotSize));
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
        ConstructionDeploymentProject project = new ConstructionDeploymentProject(
                "module_" + UUID.randomUUID(), "", station.systemId(),
                station.planetOrbitId(), ConstructionDeploymentProject.TYPE_STATION_MODULE,
                0.0, Math.max(2.0, Math.ceil(mass / 10_000.0)), station.ownerEntityId(),
                ConstructionMaterialCatalog.stationModule(mass), Map.of(),
                module.name(), station.ownershipType(), station.id(), module,
                0, station.armorMaterialId(), station.armorThicknessCm(), 0.0, false);
        List<ConstructionDeploymentProject> projects = new ArrayList<>(state.constructionProjects());
        projects.add(project);
        return state.withConstructionProjects(projects);
    }
}
