package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.SolarSystem;
import java.util.HashMap;
import java.util.Map;

/** Computes a fleet itinerary using loaded ship mass and an accelerate-brake profile. */
public final class InterstellarTravel {
    public static final double METERS_PER_LIGHT_YEAR = 9_460_730_472_580_800.0;
    public static final double SECONDS_PER_DAY = 86_400.0;
    public static final double MAX_CRUISE_MPS = 0.99 * 299_792_458.0;
    public static final double WARP_DAYS = 4.0;

    public record Plan(String mode, double days, double distanceMeters,
                       double accelerationMps2, double peakSpeedMps,
                       Map<String, Double> fuelBudgetKg,
                       Map<String, ReactorFuelUse> reactorFuelBudgetKg) {
        public Plan {
            fuelBudgetKg = fuelBudgetKg == null ? Map.of() : Map.copyOf(fuelBudgetKg);
            reactorFuelBudgetKg = reactorFuelBudgetKg == null ? Map.of()
                    : Map.copyOf(reactorFuelBudgetKg);
        }
    }

    public record ReactorFuelUse(String materialId, double quantityKg) {}

    private InterstellarTravel() {}

    public static Plan plan(GameState state, Fleet fleet, String targetSystemId) {
        SolarSystem origin = state.solarSystems().stream()
                .filter(system -> system.id().equals(fleet.currentSystemId()))
                .findFirst().orElseThrow();
        SolarSystem destination = state.solarSystems().stream()
                .filter(system -> system.id().equals(targetSystemId))
                .findFirst().orElseThrow();
        String empireId = state.corporations().stream()
                .filter(corp -> corp.id().equals(fleet.ownerEntityId()))
                .map(Corporation::empireId).findFirst().orElse(fleet.ownerEntityId());
        boolean warp = state.empires().stream().anyMatch(empire ->
                empire.id().equals(empireId) && empire.unlockedTechIds().contains("warp"));
        if (warp) return new Plan(Fleet.MODE_WARP, WARP_DAYS, 0.0, 0.0, 0.0,
                Map.of(), Map.of());
        double dx = destination.x() - origin.x();
        double dy = destination.y() - origin.y();
        double dz = destination.z() - origin.z();
        double distance = Math.max(1.0, Math.sqrt(dx * dx + dy * dy + dz * dz)
                * METERS_PER_LIGHT_YEAR);
        if (!Double.isFinite(distance)) return null;
        double fleetAcceleration = Double.POSITIVE_INFINITY;
        double fuelLimitedPeak = MAX_CRUISE_MPS;
        Map<String, Double> wetMasses = new HashMap<>();
        Map<String, PropulsionCatalog.Drive> drives = new HashMap<>();
        Map<String, PropulsionCatalog.ReactorFuel> reactorFuels = new HashMap<>();
        if (fleet.ships().isEmpty()) return null;
        for (ShipInstance ship : fleet.ships()) {
            ShipDesign design = state.shipDesigns().stream()
                    .filter(item -> item.id().equals(ship.designId())).findFirst().orElse(null);
            if (design == null || !Double.isFinite(design.totalThrustN())
                    || design.totalThrustN() <= 0.0
                    || !Double.isFinite(design.totalDryMassKg())
                    || design.totalDryMassKg() <= 0.0
                    || !Double.isFinite(ship.currentFuelKg())
                    || ship.currentFuelKg() < 0.0
                    || ship.currentFuelKg() > design.fuelCapacityKg() + 0.000001) return null;
            double cargo = ship.storedCargoKg().values().stream()
                    .filter(value -> Double.isFinite(value) && value > 0.0)
                    .mapToDouble(Double::doubleValue).sum();
            double mass = Math.max(1.0, design.totalDryMassKg()
                    + Math.max(0.0, ship.currentFuelKg()) + cargo
                    + Math.max(0, ship.passengerCount()) * 80.0);
            if (!Double.isFinite(mass)) return null;
            fleetAcceleration = Math.min(fleetAcceleration, design.totalThrustN() / mass);
            PropulsionCatalog.Drive drive = PropulsionCatalog.mainDrive(design.equippedModuleIds());
            if (drive != null) {
                if (ship.currentFuelKg() <= 0.0 || mass <= ship.currentFuelKg()) return null;
                PropulsionCatalog.ReactorFuel reactorFuel =
                        PropulsionCatalog.availableReactorFuel(drive, ship);
                if (!PropulsionCatalog.reactorFuels(drive.moduleId()).isEmpty()
                        && reactorFuel == null) return null;
                double exhaust = drive.exhaustVelocityMps()
                        * (reactorFuel == null ? 1.0 : reactorFuel.exhaustMultiplier());
                double deltaV = exhaust
                        * Math.log(mass / (mass - ship.currentFuelKg()));
                fuelLimitedPeak = Math.min(fuelLimitedPeak, deltaV / 2.0);
                wetMasses.put(ship.id(), mass);
                drives.put(ship.id(), drive);
                if (reactorFuel != null) reactorFuels.put(ship.id(), reactorFuel);
            }
        }
        if (!Double.isFinite(fleetAcceleration) || fleetAcceleration <= 0.0) return null;
        double peak = Math.min(fuelLimitedPeak, Math.sqrt(distance * fleetAcceleration));
        if (!Double.isFinite(peak) || peak <= 0.0) return null;
        double seconds = travelSeconds(distance, fleetAcceleration, peak);
        if (!Double.isFinite(seconds)) return null;
        Map<String, Double> fuelBudget = new HashMap<>();
        Map<String, ReactorFuelUse> reactorFuelBudget = new HashMap<>();
        for (ShipInstance ship : fleet.ships()) {
            PropulsionCatalog.Drive drive = drives.get(ship.id());
            if (drive == null) continue;
            double mass = wetMasses.get(ship.id());
            PropulsionCatalog.ReactorFuel reactorFuel = reactorFuels.get(ship.id());
            double exhaust = drive.exhaustVelocityMps()
                    * (reactorFuel == null ? 1.0 : reactorFuel.exhaustMultiplier());
            double propellantKg = Math.min(ship.currentFuelKg(),
                    mass * -Math.expm1(-2.0 * peak / exhaust));
            fuelBudget.put(ship.id(), propellantKg);
            if (reactorFuel != null && reactorFuel.kgPerPropellantKg() > 0.0)
                reactorFuelBudget.put(ship.id(), new ReactorFuelUse(
                        reactorFuel.materialId(), propellantKg * reactorFuel.kgPerPropellantKg()));
        }
        return new Plan(Fleet.MODE_SUBLIGHT,
                Math.max(1.0, seconds / SECONDS_PER_DAY),
                distance, fleetAcceleration, peak, fuelBudget, reactorFuelBudget);
    }

    /** Commits reactor fuel for a leg at departure; propellant continues to burn daily. */
    public static Fleet commitReactorFuel(Fleet fleet, Plan plan) {
        if (plan.reactorFuelBudgetKg().isEmpty()) return fleet;
        return fleet.withShips(fleet.ships().stream().map(ship -> {
            ReactorFuelUse use = plan.reactorFuelBudgetKg().get(ship.id());
            if (use == null || use.quantityKg() <= 0.0) return ship;
            Map<String, Double> cargo = new HashMap<>(ship.storedCargoKg());
            double stocked = cargo.getOrDefault(use.materialId(), 0.0);
            if (stocked + 0.000001 < use.quantityKg())
                throw new IllegalStateException("Insufficient committed reactor fuel");
            double remainder = Math.max(0.0, stocked - use.quantityKg());
            if (remainder <= 0.000001) cargo.remove(use.materialId());
            else cargo.put(use.materialId(), remainder);
            return new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(),
                    ship.currentHullHealth(), ship.currentShieldHealth(),
                    ship.currentFuelKg(), Map.copyOf(cargo), ship.passengerCount(),
                    ship.passengerRaceId(), ship.transitMode());
        }).toList());
    }

    public static double travelSeconds(double distanceMeters, double accelerationMps2) {
        return travelSeconds(distanceMeters, accelerationMps2, MAX_CRUISE_MPS);
    }

    public static double travelSeconds(double distanceMeters, double accelerationMps2,
                                       double peakSpeedMps) {
        if (distanceMeters <= 0.0 || accelerationMps2 <= 0.0) return 0.0;
        if (peakSpeedMps <= 0.0) return 0.0;
        double accelerationTime = peakSpeedMps / accelerationMps2;
        double accelerationDistance = peakSpeedMps * accelerationTime / 2.0;
        if (distanceMeters <= 2.0 * accelerationDistance)
            return 2.0 * Math.sqrt(distanceMeters / accelerationMps2);
        return 2.0 * accelerationTime
                + (distanceMeters - 2.0 * accelerationDistance) / peakSpeedMps;
    }

    /** Fraction of the journey covered after elapsed seconds, with a symmetric braking phase. */
    public static double progress(double distanceMeters, double accelerationMps2,
                                  double elapsedSeconds) {
        return progress(distanceMeters, accelerationMps2, MAX_CRUISE_MPS, elapsedSeconds);
    }

    public static double progress(double distanceMeters, double accelerationMps2,
                                  double peakSpeedMps, double elapsedSeconds) {
        if (distanceMeters <= 0.0 || accelerationMps2 <= 0.0) return 0.0;
        double total = travelSeconds(distanceMeters, accelerationMps2, peakSpeedMps);
        if (elapsedSeconds >= total) return 1.0;
        double accelerationTime = peakSpeedMps / accelerationMps2;
        double accelerationDistance = peakSpeedMps * accelerationTime / 2.0;
        if (distanceMeters <= 2.0 * accelerationDistance) {
            double half = total / 2.0;
            if (elapsedSeconds <= half)
                return 0.5 * accelerationMps2 * elapsedSeconds * elapsedSeconds / distanceMeters;
            double remaining = total - elapsedSeconds;
            return 1.0 - 0.5 * accelerationMps2 * remaining * remaining / distanceMeters;
        }
        double cruiseTime = (distanceMeters - 2.0 * accelerationDistance) / peakSpeedMps;
        if (elapsedSeconds <= accelerationTime)
            return 0.5 * accelerationMps2 * elapsedSeconds * elapsedSeconds / distanceMeters;
        if (elapsedSeconds <= accelerationTime + cruiseTime)
            return (accelerationDistance
                    + peakSpeedMps * (elapsedSeconds - accelerationTime)) / distanceMeters;
        double remaining = total - elapsedSeconds;
        return 1.0 - 0.5 * accelerationMps2 * remaining * remaining / distanceMeters;
    }

    public static double velocityMps(double distanceMeters, double accelerationMps2,
                                     double elapsedSeconds) {
        return velocityMps(distanceMeters, accelerationMps2, MAX_CRUISE_MPS, elapsedSeconds);
    }

    public static double velocityMps(double distanceMeters, double accelerationMps2,
                                     double peakSpeedMps, double elapsedSeconds) {
        if (distanceMeters <= 0.0 || accelerationMps2 <= 0.0 || elapsedSeconds <= 0.0)
            return 0.0;
        double total = travelSeconds(distanceMeters, accelerationMps2, peakSpeedMps);
        if (elapsedSeconds >= total) return 0.0;
        double accelerationTime = Math.min(total / 2.0,
                peakSpeedMps / accelerationMps2);
        if (elapsedSeconds <= accelerationTime)
            return accelerationMps2 * elapsedSeconds;
        if (elapsedSeconds >= total - accelerationTime)
            return accelerationMps2 * (total - elapsedSeconds);
        return peakSpeedMps;
    }

    /** Cumulative share of the planned fuel burned during acceleration and braking. */
    public static double fuelBurnFraction(double distanceMeters, double accelerationMps2,
                                          double peakSpeedMps, double elapsedSeconds) {
        if (peakSpeedMps <= 0.0 || accelerationMps2 <= 0.0) return 0.0;
        double total = travelSeconds(distanceMeters, accelerationMps2, peakSpeedMps);
        double burnSeconds = Math.min(total / 2.0, peakSpeedMps / accelerationMps2);
        if (burnSeconds <= 0.0) return 0.0;
        double first = Math.min(Math.max(0.0, elapsedSeconds), burnSeconds);
        double last = Math.max(0.0, Math.min(burnSeconds, elapsedSeconds - (total - burnSeconds)));
        return Math.clamp((first + last) / (2.0 * burnSeconds), 0.0, 1.0);
    }
}
