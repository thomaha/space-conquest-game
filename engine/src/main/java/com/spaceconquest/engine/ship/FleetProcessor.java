package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.DiplomaticRelation;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.macrostructure.StationModule;
import java.util.ArrayList;
import java.util.List;

/**
 * Simulates sub-light maneuvering, FTL warp bubble transits and fleet stances.
 */
public class FleetProcessor {

    public static final double BASE_WARP_SPEED_PER_TURN = 0.25;

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
        double elapsedDays = fleet.interstellarElapsedDays();
        String currentSys = fleet.currentSystemId();
        String targetSys = fleet.targetSystemId();
        double posX = fleet.coordinateX();
        double posY = fleet.coordinateY();
        FleetLocation location = fleet.location();
        boolean wasLocalTransit = location.inTransit();

        List<ShipInstance> updatedShips = repairDockedShips(fleet, stations, relations);

        if (!inWarp && location.inTransit()) location = location.advanceDay();
        else if (!inWarp && !fleet.isInterstellarTransit()
                && targetSys != null && !targetSys.isEmpty()
                && !targetSys.equals(currentSys)) {
            FleetLocation.Site departure = FleetLocation.Site.deepSpace();
            if (location.isAt(departure)) {
                inWarp = Fleet.MODE_WARP.equals(fleet.interstellarMode());
                progress = 0.0;
                elapsedDays = 0.0;
            }
        }

        boolean crossing = targetSys != null && !targetSys.isEmpty()
                && !wasLocalTransit
                && location.isAt(FleetLocation.Site.deepSpace())
                && (inWarp || Fleet.MODE_SUBLIGHT.equals(fleet.interstellarMode()));
        if (crossing) {
            double days = fleet.interstellarTravelDays() > 0.0
                    ? fleet.interstellarTravelDays() : 1.0 / BASE_WARP_SPEED_PER_TURN;
            elapsedDays += 1.0;
            progress = Fleet.MODE_SUBLIGHT.equals(fleet.interstellarMode())
                    && fleet.interstellarDistanceMeters() > 0.0
                    && fleet.interstellarAccelerationMps2() > 0.0
                    ? InterstellarTravel.progress(fleet.interstellarDistanceMeters(),
                    fleet.interstellarAccelerationMps2(),
                    fleet.interstellarPeakSpeedMps() > 0.0
                            ? fleet.interstellarPeakSpeedMps() : InterstellarTravel.MAX_CRUISE_MPS,
                    elapsedDays * InterstellarTravel.SECONDS_PER_DAY)
                    : elapsedDays / days;

            updatedShips = burnSublightFuel(fleet, updatedShips, elapsedDays);

            // Check if arrived at destination
            if (progress + 0.000000001 >= 1.0) {
                inWarp = false;
                progress = 0.0;
                elapsedDays = 0.0;
                currentSys = targetSys;
                targetSys = "";
                location = FleetLocation.at(FleetLocation.Site.deepSpace());
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
                updatedShips,
                location,
                targetSys.isEmpty() ? "" : fleet.interstellarMode(),
                targetSys.isEmpty() ? 0.0 : fleet.interstellarTravelDays(),
                targetSys.isEmpty() ? 0.0 : fleet.interstellarDistanceMeters(),
                targetSys.isEmpty() ? 0.0 : fleet.interstellarAccelerationMps2(),
                elapsedDays,
                targetSys.isEmpty() ? 0.0 : fleet.interstellarPeakSpeedMps(),
                targetSys.isEmpty() ? java.util.Map.of() : fleet.interstellarFuelBudgetKg()
        );
    }

    private List<ShipInstance> burnSublightFuel(Fleet fleet, List<ShipInstance> ships,
                                                double elapsedDays) {
        if (!Fleet.MODE_SUBLIGHT.equals(fleet.interstellarMode())
                || fleet.interstellarPeakSpeedMps() <= 0.0
                || fleet.interstellarFuelBudgetKg().isEmpty()) return ships;
        double previousFraction = InterstellarTravel.fuelBurnFraction(
                fleet.interstellarDistanceMeters(), fleet.interstellarAccelerationMps2(),
                fleet.interstellarPeakSpeedMps(),
                fleet.interstellarElapsedDays() * InterstellarTravel.SECONDS_PER_DAY);
        double currentFraction = InterstellarTravel.fuelBurnFraction(
                fleet.interstellarDistanceMeters(), fleet.interstellarAccelerationMps2(),
                fleet.interstellarPeakSpeedMps(), elapsedDays * InterstellarTravel.SECONDS_PER_DAY);
        double deltaFraction = Math.max(0.0, currentFraction - previousFraction);
        return ships.stream().map(ship -> {
            double budget = fleet.interstellarFuelBudgetKg().getOrDefault(ship.id(), 0.0);
            if (budget <= 0.0) return ship;
            return new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(),
                    ship.currentHullHealth(), ship.currentShieldHealth(),
                    Math.max(0.0, ship.currentFuelKg() - budget * deltaFraction),
                    ship.storedCargoKg(), ship.passengerCount(),
                    ship.passengerRaceId(), ship.transitMode());
        }).toList();
    }

    private List<ShipInstance> repairDockedShips(Fleet fleet, List<OrbitalStation> stations,
                                                 List<DiplomaticRelation> relations) {
        if (fleet.isInWarp() || fleet.location().inTransit() || stations == null)
            return fleet.ships();
        for (OrbitalStation station : stations) {
            if (!station.systemId().equals(fleet.currentSystemId())
                    || !fleet.location().isAt(FleetLocation.Site.docked(station.id()))
                    || !station.hasModuleType(StationModule.TYPE_MILITARY_HANGAR)) continue;
            boolean allied = fleet.ownerEntityId().equals(station.ownerEntityId());
            if (!allied && relations != null) allied = relations.stream().anyMatch(relation ->
                    (relation.tier().contains("ALLIANCE")
                            || relation.tier().contains("FEDERATION"))
                            && ((relation.empireAId().equals(fleet.ownerEntityId())
                            && relation.empireBId().equals(station.ownerEntityId()))
                            || (relation.empireBId().equals(fleet.ownerEntityId())
                            && relation.empireAId().equals(station.ownerEntityId()))));
            if (!allied) continue;
            return fleet.ships().stream().map(ship -> new ShipInstance(ship.id(),
                    ship.designId(), ship.ownerEntityId(),
                    Math.min(100.0, ship.currentHullHealth() + 5.0),
                    Math.min(100.0, ship.currentShieldHealth() + 10.0),
                    ship.currentFuelKg(), ship.storedCargoKg(), ship.passengerCount(),
                    ship.passengerRaceId(), ship.transitMode())).toList();
        }
        return fleet.ships();
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
