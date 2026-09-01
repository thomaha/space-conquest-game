package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.DiplomaticRelation;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.macrostructure.StationModule;
import java.util.ArrayList;
import java.util.List;

/**
 * Simulates sub-light maneuvering, FTL warp bubble transits, fuel depletion and fleet stances.
 */
public class FleetProcessor {

    public static final double BASE_WARP_SPEED_PER_TURN = 0.25;
    public static final double FTL_FUEL_CONSUMPTION_KG = 5.0;

    /**
     * Updates all active fleets for one turn cycle.
     */
    public List<Fleet> processFleetMovements(
            List<Fleet> fleets,
            List<OrbitalStation> stations,
            List<DiplomaticRelation> relations
    ) {
        if (fleets == null || fleets.isEmpty()) {
            return List.of();
        }

        List<Fleet> updatedFleets = new ArrayList<>();
        for (Fleet fleet : fleets) {
            updatedFleets.add(processSingleFleet(fleet, stations, relations));
        }
        return updatedFleets;
    }

    private Fleet processSingleFleet(
            Fleet fleet,
            List<OrbitalStation> stations,
            List<DiplomaticRelation> relations
    ) {
        if (fleet == null) return null;

        boolean inWarp = fleet.isInWarp();
        double progress = fleet.transitProgress();
        String currentSys = fleet.currentSystemId();
        String targetSys = fleet.targetSystemId();
        double posX = fleet.coordinateX();
        double posY = fleet.coordinateY();

        List<ShipInstance> updatedShips = new ArrayList<>(fleet.ships());

        // Allied Repairs (Point 1): Repair ships if fleet is at an allied Military Hangar
        if (!inWarp && stations != null) {
            for (OrbitalStation station : stations) {
                if (station.systemId().equals(currentSys)) {
                    // Check if owner is ally
                    boolean isAlly = fleet.ownerEntityId().equals(station.ownerEntityId());
                    if (!isAlly && relations != null) {
                        for (DiplomaticRelation rel : relations) {
                            if (rel.tier().contains("ALLIANCE") || rel.tier().contains("FEDERATION")) {
                                if ((rel.empireAId().equals(fleet.ownerEntityId()) && rel.empireBId().equals(station.ownerEntityId()))
                                        || (rel.empireBId().equals(fleet.ownerEntityId()) && rel.empireAId().equals(station.ownerEntityId()))) {
                                    isAlly = true;
                                    break;
                                }
                            }
                        }
                    }

                    if (isAlly && station.hasModuleType(StationModule.TYPE_MILITARY_HANGAR)) {
                        updatedShips = updatedShips.stream().map(ship -> {
                            double newHull = Math.min(100.0, ship.currentHullHealth() + 5.0); // Simple 5% repair
                            double newShield = Math.min(100.0, ship.currentShieldHealth() + 10.0);
                            return new ShipInstance(
                                    ship.id(), ship.designId(), ship.ownerEntityId(),
                                    newHull, newShield,
                                    ship.currentFuelKg(), ship.storedCargoKg(),
                                    ship.passengerCount(), ship.passengerRaceId(), ship.transitMode()
                            );
                        }).toList();
                        break;
                    }
                }
            }
        }

        // 1. Check if initiating interstellar warp transit
        if (!inWarp && targetSys != null && !targetSys.isEmpty() && !targetSys.equals(currentSys)) {
            inWarp = true;
            progress = 0.0;
        }

        // 2. Advance warp transit
        if (inWarp) {
            progress += BASE_WARP_SPEED_PER_TURN;

            // Deplete fuel across fleet ships
            updatedShips = updatedShips.stream().map(ship -> {
                double newFuel = Math.max(0.0, ship.currentFuelKg() - FTL_FUEL_CONSUMPTION_KG);
                return new ShipInstance(
                        ship.id(), ship.designId(), ship.ownerEntityId(),
                        ship.currentHullHealth(), ship.currentShieldHealth(),
                        newFuel, ship.storedCargoKg()
                );
            }).toList();

            // Check if arrived at destination
            if (progress >= 1.0) {
                inWarp = false;
                progress = 0.0;
                currentSys = targetSys;
                targetSys = "";
            }
        } else {
            // Sub-light localized positioning or patrol stance
            if ("PATROL".equalsIgnoreCase(fleet.fleetStance())) {
                posX = (posX + 1.0) % 100.0;
                posY = (posY + 1.0) % 100.0;
            }
        }

        return new Fleet(
                fleet.id(),
                fleet.name(),
                fleet.ownerEntityId(),
                currentSys,
                targetSys,
                posX,
                posY,
                progress,
                inWarp,
                fleet.fleetStance(),
                updatedShips
        );
    }

    /**
     * Calculates the aggregate sensor / scanner range of a fleet.
     */
    public double calculateFleetScannerRange(Fleet fleet, List<ShipDesign> designs) {
        if (fleet == null || fleet.ships().isEmpty()) {
            return 5.0; // Baseline sensor range
        }

        double maxRange = 5.0;
        for (ShipInstance ship : fleet.ships()) {
            if (designs != null) {
                for (ShipDesign design : designs) {
                    if (design.id().equals(ship.designId())) {
                        if (ShipRole.EXPLORER.equalsIgnoreCase(design.role())) {
                            maxRange = Math.max(maxRange, 25.0);
                        } else if (ShipRole.COMBAT_SHIP.equalsIgnoreCase(design.role())
                                || ShipRole.CARRIER_SHIP.equalsIgnoreCase(design.role())) {
                            maxRange = Math.max(maxRange, 15.0);
                        }
                    }
                }
            }
        }
        return maxRange;
    }
}
