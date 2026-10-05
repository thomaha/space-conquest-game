package com.spaceconquest.engine.ship;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Frozen local geometry, actual motion and per-ship paid propulsion commitments. */
public record LocalFlight(LocalSiteGeometry.Leg geometry, FlightMotion motion,
                          Map<String, Double> propellantKg, Map<String, JourneyPropulsion> propulsion,
                          boolean interrupted, boolean advancedInPowerTick) {
    public LocalFlight {
        if (geometry == null || motion == null || motion.trajectory() == null)
            throw new IllegalArgumentException("Local flight requires geometry and a trajectory");
        propellantKg = Map.copyOf(propellantKg);
        propulsion = Map.copyOf(propulsion);
        if (!Double.isFinite(geometry.distanceMeters()) || geometry.distanceMeters() <= 0
                || !propellantKg.keySet().equals(propulsion.keySet()))
            throw new IllegalArgumentException("Local flight requires finite geometry and complete propulsion budgets");
        if (propellantKg.values().stream().anyMatch(kg -> !Double.isFinite(kg) || kg < 0))
            throw new IllegalArgumentException("Invalid local propellant budget");
    }

    public LocalFlight retain(List<ShipInstance> ships) {
        Map<String, Double> fuel = new HashMap<>();
        Map<String, JourneyPropulsion> drives = new HashMap<>();
        ships.forEach(ship -> {
            if (propellantKg.containsKey(ship.id())) fuel.put(ship.id(), propellantKg.get(ship.id()));
            if (propulsion.containsKey(ship.id())) drives.put(ship.id(), propulsion.get(ship.id()));
        });
        return new LocalFlight(geometry, motion, fuel, drives, interrupted, advancedInPowerTick);
    }

    public boolean together(LocalFlight other) {
        return other != null && geometry.equals(other.geometry) && motion.equals(other.motion)
                && interrupted == other.interrupted && advancedInPowerTick == other.advancedInPowerTick;
    }

    public LocalFlight join(LocalFlight other) {
        if (!together(other)) throw new IllegalArgumentException("Local motion differs");
        var fuel = new HashMap<>(propellantKg); fuel.putAll(other.propellantKg);
        var drives = new HashMap<>(propulsion); drives.putAll(other.propulsion);
        return new LocalFlight(geometry, motion, fuel, drives, interrupted, advancedInPowerTick);
    }
}
