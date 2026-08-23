package com.spaceconquest.engine.macrostructure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simulates turn-by-turn operations of orbital space stations, modular facilities,
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

    /**
     * Executes turn update calculations for an orbital space station.
     */
    public StationTurnResult processOrbitalStation(OrbitalStation station, double stateTariffRate) {
        if (station == null) {
            return new StationTurnResult(null, 0.0, 0.0, Map.of());
        }

        List<StationModule> modules = new ArrayList<>(station.modules());

        // 1. Check for active Control Module
        boolean hasControlOnline = modules.stream()
                .anyMatch(m -> StationModule.TYPE_CONTROL.equalsIgnoreCase(m.type()) && m.isOnline());

        if (!hasControlOnline) {
            // Station cannot process automated functions without an active control module
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

        // 2. Power Balance ($E_net$)
        double totalGenKw = modules.stream()
                .filter(StationModule::isOnline)
                .mapToDouble(StationModule::powerOutputKw)
                .sum();
        double totalDemandKw = modules.stream()
                .filter(StationModule::isOnline)
                .mapToDouble(StationModule::powerDrawKw)
                .sum();

        boolean isPowerDeficit = totalGenKw < totalDemandKw;

        List<StationModule> updatedModules = new ArrayList<>();
        double activeGenKw = 0.0;
        double activeDemandKw = 0.0;

        for (StationModule mod : modules) {
            boolean stayOnline = mod.isOnline();
            if (isPowerDeficit) {
                // Shed heavy non-essential loads (foundries, labs, commerce) during brownouts
                if (StationModule.TYPE_METALLURGY_FOUNDRY.equalsIgnoreCase(mod.type())
                        || StationModule.TYPE_CONSUMER_GOODS_FACTORY.equalsIgnoreCase(mod.type())
                        || StationModule.TYPE_COMMERCE.equalsIgnoreCase(mod.type())
                        || StationModule.TYPE_SHIPYARD_GRID.equalsIgnoreCase(mod.type())
                        || StationModule.TYPE_CAPITAL_SLIPWAY.equalsIgnoreCase(mod.type())) {
                    stayOnline = false;
                }
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

        // 3. Commerce & Tariff collection (requires both Commerce Module and Civilian Hangar)
        boolean hasCommerce = updatedModules.stream()
                .anyMatch(m -> StationModule.TYPE_COMMERCE.equalsIgnoreCase(m.type()) && m.isOnline());
        boolean hasCivilianHangar = updatedModules.stream()
                .anyMatch(m -> StationModule.TYPE_CIVILIAN_HANGAR.equalsIgnoreCase(m.type()) && m.isOnline());

        double collectedTariffs = 0.0;
        if (hasCommerce && hasCivilianHangar) {
            double grossTransactionVolume = 5000.0;
            collectedTariffs = grossTransactionVolume * Math.max(0.01, stateTariffRate);
        }

        // 4. Science Research generation
        double researchPoints = 0.0;
        for (StationModule mod : updatedModules) {
            if (mod.isOnline()) {
                if (StationModule.TYPE_THEORETICAL_PHYSICS_LAB.equalsIgnoreCase(mod.type())
                        || StationModule.TYPE_MATERIAL_SCIENCE_LAB.equalsIgnoreCase(mod.type())
                        || StationModule.TYPE_XENOBIOLOGY_LAB.equalsIgnoreCase(mod.type())) {
                    researchPoints += 15.0;
                }
            }
        }

        // 5. Material production yields
        Map<String, Double> producedMaterials = new HashMap<>();
        for (StationModule mod : updatedModules) {
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

        // 6. Shield regeneration if power is online
        double shieldGen = updatedModules.stream()
                .anyMatch(m -> StationModule.TYPE_SHIELD_GENERATOR.equalsIgnoreCase(m.type()) && m.isOnline()) ? 100.0 : 0.0;
        double currentShield = Math.min(station.maxShieldHealth(), station.currentShieldHealth() + shieldGen);

        OrbitalStation updatedStation = new OrbitalStation(
                station.id(), station.name(), station.systemId(), station.planetOrbitId(),
                station.ownerEntityId(), station.ownershipType(), station.totalSlots(),
                updatedModules, station.storedCargoKg(),
                activeGenKw, activeDemandKw,
                currentShield, station.maxShieldHealth(),
                station.currentHullHealth(), station.maxHullHealth(),
                station.armorMaterialId(), station.armorThicknessCm(),
                true
        );

        return new StationTurnResult(updatedStation, collectedTariffs, researchPoints, producedMaterials);
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
        if (projects == null) {
            return new ConstructionTurnResult(List.of(), List.of(), List.of());
        }

        List<ConstructionDeploymentProject> remaining = new ArrayList<>();
        List<OrbitalStation> newStations = new ArrayList<>();
        List<SpaceElevator> newElevators = new ArrayList<>();

        for (ConstructionDeploymentProject proj : projects) {
            double nextProgress = proj.accumulatedProgressTurns() + 1.0;
            if (nextProgress >= proj.requiredProgressTurns()) {
                if (ConstructionDeploymentProject.TYPE_ORBITAL_STATION.equalsIgnoreCase(proj.targetStructureType())) {
                    // Spawn newly completed station with a default control module and power module
                    StationModule controlMod = new StationModule(
                            "mod_ctrl_" + proj.projectId(), "Command Core", StationModule.TYPE_CONTROL,
                            6, 12000.0, 50.0, 0.0, Map.of(), "bureaucrat", 5, true
                    );
                    StationModule powerMod = new StationModule(
                            "mod_pwr_" + proj.projectId(), "Fission Reactor Hub", StationModule.TYPE_POWER,
                            10, 22000.0, 0.0, 250.0, Map.of(), "technician", 4, true
                    );
                    OrbitalStation newStation = new OrbitalStation(
                            "station_" + proj.projectId(),
                            "Orbital Station " + proj.targetCelestialId(),
                            proj.targetSystemId(),
                            proj.targetCelestialId(),
                            ownerEntityId,
                            OrbitalStation.OWNERSHIP_PUBLIC_STATE,
                            50,
                            List.of(controlMod, powerMod),
                            Map.of(),
                            250.0, 50.0,
                            500.0, 500.0,
                            1000.0, 1000.0,
                            "steel", 5.0,
                            true
                    );
                    newStations.add(newStation);
                } else if (ConstructionDeploymentProject.TYPE_SPACE_ELEVATOR.equalsIgnoreCase(proj.targetStructureType())) {
                    SpaceElevator elevator = new SpaceElevator(
                            "elevator_" + proj.targetCelestialId(),
                            proj.targetCelestialId(),
                            ownerEntityId,
                            100000.0,
                            0.95,
                            100.0,
                            true
                    );
                    newElevators.add(elevator);
                }
            } else {
                remaining.add(new ConstructionDeploymentProject(
                        proj.projectId(), proj.constructionShipId(),
                        proj.targetSystemId(), proj.targetCelestialId(),
                        proj.targetStructureType(), nextProgress,
                        proj.requiredProgressTurns(), proj.consumedMaterialsKg(),
                        false
                ));
            }
        }

        return new ConstructionTurnResult(remaining, newStations, newElevators);
    }
}
