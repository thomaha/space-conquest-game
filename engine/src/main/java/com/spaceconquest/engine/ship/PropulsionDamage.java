package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;

/** Live main-drive condition and the first saved burn its remaining thrust cannot support. */
public final class PropulsionDamage {
    private PropulsionDamage() {}
    public static double condition(ShipDesign design, ShipInstance ship) {
        if (design == null || ship.currentHullHealth() <= 0) return 0;
        double rated = 0, working = 0;
        for (int mount = 0; mount < design.equippedModuleIds().size(); mount++) {
            var module = PropulsionCatalog.module(design.equippedModuleIds().get(mount));
            if (module == null) continue;
            rated += module.thrustOutputN(); working += module.thrustOutputN() * ship.moduleDamage().condition(mount);
        }
        return rated > 0 ? working / rated : 1; // Preserve geometry-free legacy propulsion compatibility.
    }
    public static double thrust(ShipDesign design, ShipInstance ship) {
        return design == null ? 0 : design.totalThrustN() * condition(design, ship);
    }
    public static boolean damaged(GameState state, Fleet fleet) {
        return fleet.ships().stream().anyMatch(ship -> {
            var design = FleetSupplySimulation.design(state, ship);
            return design != null && PropulsionCatalog.mainDrive(design.equippedModuleIds()) != null && condition(design, ship) < 1;
        });
    }
    private static double mass(ShipDesign design, ShipInstance ship) {
        return design.totalDryMassKg() + ship.currentFuelKg() + ship.generatorFuelMassKg() + ship.supplyFuelMassKg()
                + ship.passengerCount() * 80.0 + ship.storedCargoKg().values().stream().mapToDouble(Double::doubleValue).sum();
    }
    public static boolean canBurn(ShipDesign design, ShipInstance ship, double impulse, double hours) {
        if (condition(design, ship) >= 1) return true;
        return hours > 0 && thrust(design, ship) > 0 && thrust(design, ship) * hours * 3600 + 1e-6 >= impulse * mass(design, ship);
    }
    public static double availableHours(GameState state, Fleet fleet, double hours) {
        double start, firstEnd, lastStart, end, firstAcceleration, lastAcceleration;
        var local = fleet.location().localFlight();
        if (local != null) {
            if (local.interrupted()) return hours;
            var t = local.motion().trajectory(); start = local.motion().elapsedSeconds();
            firstEnd = t.accelerationSeconds(); lastStart = t.brakingStart(); end = t.totalSeconds();
            firstAcceleration = lastAcceleration = t.accelerationMps2();
            if (local.vectorFlight() != null) {
                var v = local.vectorFlight().trajectory();
                firstAcceleration = VectorFlight.norm(v.firstAcceleration()); lastAcceleration = VectorFlight.norm(v.lastAcceleration());
            }
        } else if (fleet.hasInterstellarOrder() && (Fleet.MODE_SUBLIGHT.equals(fleet.interstellarMode()) || Fleet.MODE_RECOVERY.equals(fleet.interstellarMode()))) {
            if (fleet.flightMotion() != null) {
                var t = fleet.flightMotion().trajectory(); start = fleet.flightMotion().elapsedSeconds();
                firstEnd = t.accelerationSeconds(); lastStart = t.brakingStart(); end = t.totalSeconds();
                firstAcceleration = lastAcceleration = t.accelerationMps2();
            } else {
                start = fleet.interstellarElapsedDays() * 86400;
                firstEnd = fleet.interstellarPeakSpeedMps() / fleet.interstellarAccelerationMps2();
                end = InterstellarTravel.travelSeconds(fleet.interstellarDistanceMeters(), fleet.interstellarAccelerationMps2(), fleet.interstellarPeakSpeedMps());
                lastStart = end - firstEnd; firstAcceleration = lastAcceleration = fleet.interstellarAccelerationMps2();
            }
        } else return hours;
        double limit = hours;
        for (var ship : fleet.ships()) {
            var design = FleetSupplySimulation.design(state, ship);
            if (design == null || condition(design, ship) >= 1) continue;
            var environment = local == null ? ShipSolarEnvironment.DARK : ShipSolarEnvironment.journey(state, fleet, fleet.location().destination());
            double available = ShipPowerProcessor.poweredThrust(design, ship, environment) / Math.max(1, mass(design, ship));
            if (start < firstEnd - 1e-8 && available + 1e-9 < firstAcceleration) limit = 0;
            if (start < end - 1e-8 && available + 1e-9 < lastAcceleration)
                limit = Math.min(limit, Math.max(0, lastStart - start) / 3600);
        }
        return limit;
    }
}
