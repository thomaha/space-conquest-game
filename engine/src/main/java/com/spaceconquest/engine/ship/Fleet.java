package com.spaceconquest.engine.ship;

import java.util.List;
import java.util.Map;

/**
 * Operational grouping of starships maneuvering across solar networks.
 *
 * @param id              unique fleet identifier
 * @param name            display name of the fleet
 * @param ownerEntityId   empire or corporate owner identifier
 * @param currentSystemId current solar system location ID
 * @param targetSystemId  destination solar system ID (if in transit)
 * @param coordinateX     local system or galactic X coordinate
 * @param coordinateY     local system or galactic Y coordinate
 * @param transitProgress normalized movement progress (0.0 to 1.0)
 * @param isInWarp        true if fleet is traversing FTL spacetime warp bubble
 * @param fleetStance     operational stance (PASSIVE, AGGRESSIVE, PATROL, ESCORT)
 * @param ships           list of constituent ShipInstance platforms
 * @param location        shared physical site of every ship in this fleet and local journey progress
 * @param interstellarMode WARP or SUBLIGHT for an active interstellar order
 * @param interstellarTravelDays duration in simulation days for that order
 * @param interstellarDistanceMeters distance of a sublight crossing
 * @param interstellarAccelerationMps2 slowest loaded ship's initial acceleration
 * @param interstellarElapsedDays time spent in the interstellar crossing
 * @param interstellarPeakSpeedMps fuel-limited peak speed for the current itinerary
 * @param interstellarFuelBudgetKg planned fuel consumption by ship ID
 * @param journeyPropulsion frozen propulsion by ship ID for the current physical itinerary
 * @param flightMotion actual sublight drift or replacement trajectory after a power interruption
 */
public record Fleet(
        String id,
        String name,
        String ownerEntityId,
        String currentSystemId,
        String targetSystemId,
        double coordinateX,
        double coordinateY,
        double transitProgress,
        boolean isInWarp,
        String fleetStance,
        List<ShipInstance> ships,
        FleetLocation location,
        String interstellarMode,
        double interstellarTravelDays,
        double interstellarDistanceMeters,
        double interstellarAccelerationMps2,
        double interstellarElapsedDays,
        double interstellarPeakSpeedMps,
        Map<String, Double> interstellarFuelBudgetKg,
        FlightMotion flightMotion,
        Map<String, JourneyPropulsion> journeyPropulsion
) {
    public static final String MODE_WARP = "WARP";
    public static final String MODE_SUBLIGHT = "SUBLIGHT";
    public static final String MODE_POWER_INTERRUPTED = "POWER_INTERRUPTED";
    public static final String MODE_RECOVERY = "RECOVERY";

    public Fleet {
        ships = ships == null ? List.of() : List.copyOf(ships);
        journeyPropulsion = journeyPropulsion == null ? Map.of() : Map.copyOf(journeyPropulsion);
        if (location == null) location = FleetLocation.at(FleetLocation.Site.deepSpace());
        if (interstellarMode == null) interstellarMode = "";
        if (interstellarFuelBudgetKg == null) interstellarFuelBudgetKg = Map.of();
        else interstellarFuelBudgetKg = Map.copyOf(interstellarFuelBudgetKg);
        if (MODE_RECOVERY.equals(interstellarMode)
                && (flightMotion == null || flightMotion.trajectory() == null))
            throw new IllegalArgumentException("Recovery requires a physical trajectory");
        if (!Double.isFinite(interstellarTravelDays) || interstellarTravelDays < 0.0
                || !Double.isFinite(interstellarDistanceMeters) || interstellarDistanceMeters < 0.0
                || !Double.isFinite(interstellarAccelerationMps2) || interstellarAccelerationMps2 < 0.0
                || !Double.isFinite(interstellarElapsedDays) || interstellarElapsedDays < 0.0
                || !Double.isFinite(interstellarPeakSpeedMps) || interstellarPeakSpeedMps < 0.0)
            throw new IllegalArgumentException("Invalid interstellar travel profile");
    }

    public Fleet(String id, String name, String ownerEntityId, String currentSystemId,
                 String targetSystemId, double coordinateX, double coordinateY,
                 double transitProgress, boolean isInWarp, String fleetStance,
                 List<ShipInstance> ships, FleetLocation location, String interstellarMode,
                 double interstellarTravelDays, double interstellarDistanceMeters,
                 double interstellarAccelerationMps2, double interstellarElapsedDays,
                 double interstellarPeakSpeedMps, Map<String, Double> interstellarFuelBudgetKg,
                 FlightMotion flightMotion) {
        this(id, name, ownerEntityId, currentSystemId, targetSystemId, coordinateX, coordinateY,
                transitProgress, isInWarp, fleetStance, ships, location, interstellarMode,
                interstellarTravelDays, interstellarDistanceMeters, interstellarAccelerationMps2,
                interstellarElapsedDays, interstellarPeakSpeedMps, interstellarFuelBudgetKg, flightMotion, Map.of());
    }
    public Fleet(String id, String name, String ownerEntityId, String currentSystemId,
                 String targetSystemId, double coordinateX, double coordinateY,
                 double transitProgress, boolean isInWarp, String fleetStance,
                 List<ShipInstance> ships, FleetLocation location, String interstellarMode,
                 double interstellarTravelDays, double interstellarDistanceMeters,
                 double interstellarAccelerationMps2, double interstellarElapsedDays,
                 double interstellarPeakSpeedMps, Map<String, Double> interstellarFuelBudgetKg) {
        this(id, name, ownerEntityId, currentSystemId, targetSystemId, coordinateX, coordinateY,
                transitProgress, isInWarp, fleetStance, ships, location, interstellarMode,
                interstellarTravelDays, interstellarDistanceMeters, interstellarAccelerationMps2,
                interstellarElapsedDays, interstellarPeakSpeedMps, interstellarFuelBudgetKg, null);
    }

    public Fleet(String id, String name, String ownerEntityId, String currentSystemId,
                 String targetSystemId, double coordinateX, double coordinateY,
                 double transitProgress, boolean isInWarp, String fleetStance,
                 List<ShipInstance> ships, FleetLocation location,
                 String interstellarMode, double interstellarTravelDays,
                 double interstellarDistanceMeters, double interstellarAccelerationMps2,
                 double interstellarElapsedDays) {
        this(id, name, ownerEntityId, currentSystemId, targetSystemId, coordinateX,
                coordinateY, transitProgress, isInWarp, fleetStance, ships, location,
                interstellarMode, interstellarTravelDays, interstellarDistanceMeters,
                interstellarAccelerationMps2, interstellarElapsedDays, 0.0, Map.of());
    }

    public Fleet(String id, String name, String ownerEntityId, String currentSystemId,
                 String targetSystemId, double coordinateX, double coordinateY,
                 double transitProgress, boolean isInWarp, String fleetStance,
                 List<ShipInstance> ships, FleetLocation location,
                 String interstellarMode, double interstellarTravelDays) {
        this(id, name, ownerEntityId, currentSystemId, targetSystemId, coordinateX,
                coordinateY, transitProgress, isInWarp, fleetStance, ships, location,
                interstellarMode, interstellarTravelDays, 0.0, 0.0, 0.0);
    }

    public Fleet(String id, String name, String ownerEntityId, String currentSystemId,
                 String targetSystemId, double coordinateX, double coordinateY,
                 double transitProgress, boolean isInWarp, String fleetStance,
                 List<ShipInstance> ships, FleetLocation location) {
        this(id, name, ownerEntityId, currentSystemId, targetSystemId, coordinateX,
                coordinateY, transitProgress, isInWarp, fleetStance, ships, location,
                targetSystemId != null && !targetSystemId.isBlank() ? MODE_WARP : "",
                targetSystemId != null && !targetSystemId.isBlank() ? 4.0 : 0.0);
    }

    public Fleet(String id, String name, String ownerEntityId, String currentSystemId,
                 String targetSystemId, double coordinateX, double coordinateY,
                 double transitProgress, boolean isInWarp, String fleetStance,
                 List<ShipInstance> ships) {
        this(id, name, ownerEntityId, currentSystemId, targetSystemId, coordinateX,
                coordinateY, transitProgress, isInWarp, fleetStance, ships,
                FleetLocation.at(FleetLocation.Site.deepSpace()));
    }

    public Fleet withJourneyPropulsion(Map<String, JourneyPropulsion> value) {
        return new Fleet(id, name, ownerEntityId, currentSystemId, targetSystemId,
                coordinateX, coordinateY, transitProgress, isInWarp, fleetStance, ships, location,
                interstellarMode, interstellarTravelDays, interstellarDistanceMeters,
                interstellarAccelerationMps2, interstellarElapsedDays,
                interstellarPeakSpeedMps, interstellarFuelBudgetKg, flightMotion, value);
    }

    public Fleet withLocation(FleetLocation value) {
        return new Fleet(id, name, ownerEntityId, currentSystemId, targetSystemId,
                coordinateX, coordinateY, transitProgress, isInWarp, fleetStance, ships, value,
                interstellarMode, interstellarTravelDays, interstellarDistanceMeters,
                interstellarAccelerationMps2, interstellarElapsedDays,
                interstellarPeakSpeedMps, interstellarFuelBudgetKg, flightMotion, journeyPropulsion);
    }

    public Fleet withShips(List<ShipInstance> value) {
        return new Fleet(id, name, ownerEntityId, currentSystemId, targetSystemId,
                coordinateX, coordinateY, transitProgress, isInWarp, fleetStance, value, location,
                interstellarMode, interstellarTravelDays, interstellarDistanceMeters,
                interstellarAccelerationMps2, interstellarElapsedDays,
                interstellarPeakSpeedMps, interstellarFuelBudgetKg, flightMotion, journeyPropulsion);
    }

    /** Retains last known progress and consumed reserves while invalidating the controlled arrival plan. */
    public Fleet withInterruptedTravel() {
        return new Fleet(id, name, ownerEntityId, currentSystemId, targetSystemId,
                coordinateX, coordinateY, transitProgress, false, fleetStance, ships, location,
                MODE_POWER_INTERRUPTED, interstellarTravelDays, interstellarDistanceMeters,
                interstellarAccelerationMps2, interstellarElapsedDays,
                interstellarPeakSpeedMps, interstellarFuelBudgetKg, flightMotion, journeyPropulsion);
    }

    public boolean isInterstellarTransit() {
        return hasInterstellarOrder()
                && location.isAt(FleetLocation.Site.deepSpace())
                && transitProgress > 0.0;
    }

    public boolean hasInterstellarOrder() {
        return targetSystemId != null && !targetSystemId.isBlank();
    }
}
