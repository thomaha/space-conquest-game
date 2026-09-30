package com.spaceconquest.engine.macrostructure;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.industry.ConstructionMaterials;
import com.spaceconquest.engine.industry.ConstructionProgress;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simulates micro-simulation cycles for orbital habitats, defense stations,
 * space elevators and macro-structure construction deployment projects.
 */
public class MacroStructureProcessor {

    public record StationTurnResult(
            OrbitalStation updatedStation,
            double collectedTariffCredits,
            double generatedResearchPoints,
            Map<String, Double> producedMaterialsKg
    ) {}

    public record ConstructionTurnResult(
            List<ConstructionDeploymentProject> remainingProjects,
            List<OrbitalStation> newlyCompletedStations,
            List<SpaceElevator> newlyCompletedElevators
    ) {}

    private record ModulePowerStateResult(List<StationModule> updatedModules, double activeGenKw, double activeDemandKw) {}

    /**
     * Executes turn update calculations for an orbital space station.
     */
    public StationTurnResult processOrbitalStation(OrbitalStation station, double stateTariffRate) {
        if (station == null) {
            return new StationTurnResult(null, 0.0, 0.0, Map.of());
        }

        List<StationModule> modules = new ArrayList<>(station.modules());
        boolean hasControlOnline = modules.stream()
                .anyMatch(m -> StationModule.TYPE_CONTROL.equalsIgnoreCase(m.type()) && m.isOnline());

        if (!hasControlOnline) {
            OrbitalStation unpowered = new OrbitalStation(
                    station.id(), station.name(), station.systemId(), station.planetOrbitId(),
                    station.ownerEntityId(), station.ownershipType(), station.totalSlots(),
                    modules, station.storedCargoKg(),
                    0.0, 0.0,
                    0.0, station.maxShieldHealth(),
                    station.currentHullHealth(), station.maxHullHealth(),
                    station.armorMaterialId(), station.armorThicknessCm(),
                    false
            );
            return new StationTurnResult(unpowered, 0.0, 0.0, Map.of());
        }

        double totalGenKw = modules.stream().filter(StationModule::isOnline).mapToDouble(StationModule::powerOutputKw).sum();
        double totalDemandKw = modules.stream().filter(StationModule::isOnline).mapToDouble(StationModule::powerDrawKw).sum();
        boolean isPowerDeficit = totalGenKw < totalDemandKw;

        ModulePowerStateResult powerState = resolveModulePowerStates(modules, isPowerDeficit);
        List<StationModule> updatedModules = powerState.updatedModules();

        double collectedTariffs = calculateCommerceTariffs(updatedModules, stateTariffRate);
        double researchPoints = calculateResearchPoints(updatedModules);
        Map<String, Double> producedMaterials = calculateProducedMaterials(updatedModules);
        double currentShield = calculateShieldHealth(station, updatedModules);

        OrbitalStation updatedStation = new OrbitalStation(
                station.id(), station.name(), station.systemId(), station.planetOrbitId(),
                station.ownerEntityId(), station.ownershipType(), station.totalSlots(),
                updatedModules, station.storedCargoKg(),
                powerState.activeGenKw(), powerState.activeDemandKw(),
                currentShield, station.maxShieldHealth(),
                station.currentHullHealth(), station.maxHullHealth(),
                station.armorMaterialId(), station.armorThicknessCm(),
                true
        );

        return new StationTurnResult(updatedStation, collectedTariffs, researchPoints, producedMaterials);
    }

    private ModulePowerStateResult resolveModulePowerStates(List<StationModule> modules, boolean isPowerDeficit) {
        List<StationModule> updatedModules = new ArrayList<>();
        double activeGenKw = 0.0;
        double activeDemandKw = 0.0;

        for (StationModule mod : modules) {
            boolean stayOnline = mod.isOnline();
            if (isPowerDeficit && isNonEssentialModule(mod.type())) {
                stayOnline = false;
            }

            StationModule updatedMod = new StationModule(
                    mod.id(), mod.name(), mod.type(), mod.slotSize(),
                    mod.dryMassKg(), mod.powerDrawKw(), mod.powerOutputKw(),
                    mod.materialInputs(), mod.workforceProfessionId(),
                    mod.requiredWorkers(), stayOnline
            );
            updatedModules.add(updatedMod);

            if (stayOnline) {
                activeGenKw += mod.powerOutputKw();
                activeDemandKw += mod.powerDrawKw();
            }
        }
        return new ModulePowerStateResult(updatedModules, activeGenKw, activeDemandKw);
    }

    private boolean isNonEssentialModule(String type) {
        return StationModule.TYPE_METALLURGY_FOUNDRY.equalsIgnoreCase(type)
                || StationModule.TYPE_CONSUMER_GOODS_FACTORY.equalsIgnoreCase(type)
                || StationModule.TYPE_COMMERCE.equalsIgnoreCase(type)
                || StationModule.TYPE_SHIPYARD_GRID.equalsIgnoreCase(type)
                || StationModule.TYPE_CAPITAL_SLIPWAY.equalsIgnoreCase(type);
    }

    private double calculateCommerceTariffs(List<StationModule> modules, double stateTariffRate) {
        boolean hasCommerce = modules.stream().anyMatch(m -> StationModule.TYPE_COMMERCE.equalsIgnoreCase(m.type()) && m.isOnline());
        boolean hasCivilianHangar = modules.stream().anyMatch(m -> StationModule.TYPE_CIVILIAN_HANGAR.equalsIgnoreCase(m.type()) && m.isOnline());
        if (hasCommerce && hasCivilianHangar) {
            double grossTransactionVolume = 5000.0;
            return grossTransactionVolume * Math.max(0.01, stateTariffRate);
        }
        return 0.0;
    }

    private double calculateResearchPoints(List<StationModule> modules) {
        double researchPoints = 0.0;
        for (StationModule mod : modules) {
            if (mod.isOnline()) {
                if (StationModule.TYPE_THEORETICAL_PHYSICS_LAB.equalsIgnoreCase(mod.type())
                        || StationModule.TYPE_MATERIAL_SCIENCE_LAB.equalsIgnoreCase(mod.type())
                        || StationModule.TYPE_XENOBIOLOGY_LAB.equalsIgnoreCase(mod.type())) {
                    researchPoints += 15.0;
                }
            }
        }
        return researchPoints;
    }

    private Map<String, Double> calculateProducedMaterials(List<StationModule> modules) {
        Map<String, Double> producedMaterials = new HashMap<>();
        for (StationModule mod : modules) {
            if (mod.isOnline()) {
                if (StationModule.TYPE_HYDROPONIC_FOOD.equalsIgnoreCase(mod.type())) {
                    producedMaterials.merge("food_matrix", 800.0, Double::sum);
                    producedMaterials.merge("oxygen_gas", 10.0, Double::sum);
                } else if (StationModule.TYPE_METALLURGY_FOUNDRY.equalsIgnoreCase(mod.type())) {
                    producedMaterials.merge("steel", 500.0, Double::sum);
                } else if (StationModule.TYPE_CONSUMER_GOODS_FACTORY.equalsIgnoreCase(mod.type())) {
                    producedMaterials.merge("consumer_goods", 300.0, Double::sum);
                }
            }
        }
        return producedMaterials;
    }

    private double calculateShieldHealth(OrbitalStation station, List<StationModule> modules) {
        double shieldGen = modules.stream()
                .anyMatch(m -> StationModule.TYPE_SHIELD_GENERATOR.equalsIgnoreCase(m.type()) && m.isOnline()) ? 100.0 : 0.0;
        return Math.min(station.maxShieldHealth(), station.currentShieldHealth() + shieldGen);
    }

    /**
     * Calculates surface-to-orbit material launch costs utilizing a Space Elevator.
     */
    public double calculateDiscountedLaunchCost(SpaceElevator elevator, double standardGravityCost) {
        if (elevator == null || !elevator.isOperational()) {
            return standardGravityCost;
        }
        return standardGravityCost * (1.0 - elevator.surfaceToOrbitCostDiscount());
    }

    /**
     * Advances construction deployment projects for orbital stations and space elevators.
     */
    public ConstructionTurnResult advanceConstructionProjects(
            List<ConstructionDeploymentProject> projects,
            String ownerEntityId
    ) {
        List<ConstructionDeploymentProject> owned = projects == null ? List.of()
                : projects.stream().map(project -> new ConstructionDeploymentProject(
                project.projectId(), project.constructionShipId(), project.targetSystemId(),
                project.targetCelestialId(), project.targetStructureType(),
                project.accumulatedProgressTurns(), project.requiredProgressTurns(),
                project.ownerEntityId() == null ? ownerEntityId : project.ownerEntityId(),
                project.requiredMaterialsKg(), project.consumedMaterialsKg(),
                project.structureName(), project.ownershipType(), project.targetStationId(),
                project.plannedModule(), project.totalSlots(),
                project.armorMaterialId(),
                project.armorThicknessCm(), project.throughputCapacityKgPerTurn(),
                project.isCompleted())).toList();
        GameState updated = advanceConstructionProjects(GameState.builder()
                .constructionProjects(owned).build());
        return new ConstructionTurnResult(updated.constructionProjects(),
                updated.orbitalStations(), updated.spaceElevators());
    }

    public GameState advanceConstructionProjects(GameState state) {
        GameState current = state;
        List<ConstructionDeploymentProject> remaining = new ArrayList<>();
        List<OrbitalStation> stations = new ArrayList<>(state.orbitalStations());
        List<SpaceElevator> elevators = new ArrayList<>(state.spaceElevators());
        for (ConstructionDeploymentProject project : state.constructionProjects()) {
            boolean ground = ConstructionDeploymentProject.TYPE_SPACE_ELEVATOR.equalsIgnoreCase(
                    project.targetStructureType());
            ConstructionProgress.Step step = ground
                    ? ConstructionProgress.advance(current, project.targetCelestialId(),
                    project.ownerEntityId(), project.requiredMaterialsKg(),
                    project.consumedMaterialsKg(), project.accumulatedProgressTurns(),
                    project.requiredProgressTurns(), 1.0)
                    : ConstructionProgress.advanceOrbital(current, project.targetSystemId(),
                    project.targetStationId() == null ? project.targetCelestialId()
                            : project.targetStationId(), project.ownerEntityId(),
                    project.requiredMaterialsKg(), project.consumedMaterialsKg(),
                    project.accumulatedProgressTurns(), project.requiredProgressTurns(), 1.0);
            current = step.state();
            if (step.complete() && (project.targetStationId() == null || stations.stream()
                    .anyMatch(station -> station.id().equals(project.targetStationId())
                            && project.plannedModule() != null
                            && station.hasAvailableSlots(project.plannedModule().slotSize())))) {
                if (ConstructionDeploymentProject.TYPE_ORBITAL_STATION.equalsIgnoreCase(
                        project.targetStructureType())) {
                    stations.add(createCompletedStation(project, project.ownerEntityId()));
                } else if (ConstructionDeploymentProject.TYPE_SPACE_ELEVATOR.equalsIgnoreCase(
                        project.targetStructureType())) {
                    elevators.add(new SpaceElevator("elevator_" + project.targetCelestialId(),
                            project.targetCelestialId(), project.ownerEntityId(),
                            project.throughputCapacityKgPerTurn(), 0.95, 100.0, true));
                } else if (ConstructionDeploymentProject.TYPE_STATION_MODULE.equalsIgnoreCase(
                        project.targetStructureType())) {
                    for (int index = 0; index < stations.size(); index++) {
                        OrbitalStation station = stations.get(index);
                        if (!station.id().equals(project.targetStationId())) continue;
                        List<StationModule> modules = new ArrayList<>(station.modules());
                        modules.add(project.plannedModule());
                        stations.set(index, new OrbitalStation(station.id(), station.name(),
                                station.systemId(), station.planetOrbitId(), station.ownerEntityId(),
                                station.ownershipType(), station.totalSlots(), modules,
                                station.storedCargoKg(), station.currentPowerGenerationKw()
                                + project.plannedModule().powerOutputKw(),
                                station.currentPowerDemandKw() + project.plannedModule().powerDrawKw(),
                                station.currentShieldHealth(), station.maxShieldHealth(),
                                station.currentHullHealth(), station.maxHullHealth(),
                                station.armorMaterialId(), station.armorThicknessCm(),
                                station.isOperational()));
                        if (StationModule.TYPE_COMMERCE.equalsIgnoreCase(
                                project.plannedModule().type()) && current.commercialHubs().stream()
                                .noneMatch(hub -> station.id().equals(hub.entityId()))) {
                            List<CommercialHub> hubs = new ArrayList<>(current.commercialHubs());
                            hubs.add(new CommercialHub("hub_" + station.id(), station.id(),
                                    0.0, 500_000.0, 0.0, 10.0, Map.of()));
                            current = current.withCommercialHubs(hubs);
                        }
                        break;
                    }
                }
            } else {
                remaining.add(new ConstructionDeploymentProject(project.projectId(),
                        project.constructionShipId(), project.targetSystemId(),
                        project.targetCelestialId(), project.targetStructureType(),
                        step.workHours(), project.requiredProgressTurns(),
                        project.ownerEntityId(), project.requiredMaterialsKg(),
                        step.consumedKg(), project.structureName(), project.ownershipType(),
                        project.targetStationId(), project.plannedModule(), project.totalSlots(),
                        project.armorMaterialId(), project.armorThicknessCm(),
                        project.throughputCapacityKgPerTurn(), false));
            }
        }
        return current.toBuilder().constructionProjects(remaining).orbitalStations(stations)
                .spaceElevators(elevators).build();
    }

    private OrbitalStation createCompletedStation(ConstructionDeploymentProject proj, String ownerEntityId) {
        StationModule controlMod = new StationModule(
                "mod_ctrl_" + proj.projectId(), "Command Core", StationModule.TYPE_CONTROL,
                6, 12000.0, 50.0, 0.0, Map.of(), "bureaucrat", 5, true
        );
        StationModule powerMod = new StationModule(
                "mod_pwr_" + proj.projectId(), "Fission Reactor Hub", StationModule.TYPE_POWER,
                10, 22000.0, 0.0, 250.0, Map.of(), "technician", 4, true
        );
        return new OrbitalStation(
                "station_" + proj.projectId(),
                proj.structureName() == null ? "Orbital Station " + proj.targetCelestialId() : proj.structureName(),
                proj.targetSystemId(),
                proj.targetCelestialId(),
                ownerEntityId,
                proj.ownershipType() == null ? OrbitalStation.OWNERSHIP_PUBLIC_STATE : proj.ownershipType(),
                proj.totalSlots(),
                List.of(controlMod, powerMod),
                Map.of(),
                250.0, 50.0,
                500.0, 500.0,
                1000.0, 1000.0,
                proj.armorMaterialId(), proj.armorThicknessCm(),
                true
        );
    }
}
