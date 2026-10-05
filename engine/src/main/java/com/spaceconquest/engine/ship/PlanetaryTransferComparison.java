package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.SolarSystem;

/** Read-only circular, coplanar Hohmann benchmark. Never authorizes or executes a journey. */
public final class PlanetaryTransferComparison {
    public static final double GRAVITATIONAL_CONSTANT = 6.67430e-11;
    public static final double PARKING_ALTITUDE_KM = 500;
    public static final double MAX_BURN_PERIOD_FRACTION = .1;
    private static final double TWO_PI = 2 * Math.PI;
    private static final double DAY_SECONDS = 86400;

    public record Orbit(String targetId, double coastDays, double waitDays, double requiredPhaseRadians,
                        double departureExcessMps, double arrivalExcessMps,
                        double departureDeltaVMps, double captureDeltaVMps,
                        double departureParkingPeriodSeconds, double arrivalParkingPeriodSeconds) {
        public double totalDeltaVMps() { return departureDeltaVMps + captureDeltaVMps; }
    }

    public record Budget(double wetMassKg, double usablePropellantKg, double protectedPropellantKg,
                         double availableDeltaVMps, double requiredPropellantKg,
                         double departureBurnPeriodFraction, double captureBurnPeriodFraction,
                         boolean propellantFits, boolean reactorFeedFits, boolean impulseFits) {
        public String explanation() {
            var blockers = new java.util.ArrayList<String>();
            if (!propellantFits) blockers.add("Main propellant cannot fund departure and capture with protected reserve");
            if (!reactorFeedFits) blockers.add("Drive reactor feed cannot fund the required propellant");
            if (!impulseFits) blockers.add("Burn exceeds the provisional 10% parking-period impulse limit");
            return blockers.isEmpty() ? "Benchmark maneuver checks pass; full trajectory and electrical readiness are not evaluated"
                    : String.join("; ", blockers);
        }
    }

    private PlanetaryTransferComparison() {}

    public static Orbit orbit(SolarSystem system, Planet source, Planet target, double relativePhaseRadians) {
        return orbit(system, source, target, relativePhaseRadians, PARKING_ALTITUDE_KM, PARKING_ALTITUDE_KM);
    }

    public static Orbit orbit(SolarSystem system, Planet source, Planet target, double relativePhaseRadians,
                              double sourceAltitudeKm, double targetAltitudeKm) {
        if (system == null || source == null || target == null || source.id().equals(target.id())
                || !system.planets().contains(source) || !system.planets().contains(target)
                || !Double.isFinite(relativePhaseRadians)) throw new IllegalArgumentException("Known distinct planets and a finite phase are required");
        positive(system.sunMass()); positive(source.distance()); positive(target.distance());
        nonnegative(sourceAltitudeKm); nonnegative(targetAltitudeKm);
        withinSphere(system, source, sourceAltitudeKm); withinSphere(system, target, targetAltitudeKm);
        double mu = GRAVITATIONAL_CONSTANT * system.sunMass();
        double r1 = source.distance() * 1000, r2 = target.distance() * 1000;
        if (r1 == r2) throw new IllegalArgumentException("Equal-radius planets require a different transfer model");
        double axis = (r1 + r2) / 2;
        double coast = Math.PI * Math.sqrt(axis * axis * axis / mu);
        double n1 = Math.sqrt(mu / (r1 * r1 * r1)), n2 = Math.sqrt(mu / (r2 * r2 * r2));
        double phase = angle(Math.PI - n2 * coast);
        double rate = n2 - n1;
        double phaseGap = angle(rate > 0 ? phase - angle(relativePhaseRadians) : angle(relativePhaseRadians) - phase);
        if (phaseGap < 1e-12 || TWO_PI - phaseGap < 1e-12) phaseGap = 0;
        double wait = phaseGap / Math.abs(rate);
        double departureExcess = Math.abs(Math.sqrt(mu * (2 / r1 - 1 / axis)) - Math.sqrt(mu / r1));
        double arrivalExcess = Math.abs(Math.sqrt(mu * (2 / r2 - 1 / axis)) - Math.sqrt(mu / r2));
        double departure = parkingDeltaV(source, sourceAltitudeKm, departureExcess);
        double capture = parkingDeltaV(target, targetAltitudeKm, arrivalExcess);
        positive(coast); positive(departure); positive(capture);
        if (!Double.isFinite(wait)) throw new IllegalArgumentException("Nonfinite transfer wait");
        return new Orbit(target.id(), coast / DAY_SECONDS, wait / DAY_SECONDS, phase, departureExcess, arrivalExcess,
                departure, capture, parkingPeriod(source, sourceAltitudeKm), parkingPeriod(target, targetAltitudeKm));
    }

    public static Budget budget(GameState state, Fleet fleet, ShipInstance ship, Orbit orbit) {
        if (state == null || fleet == null || ship == null || orbit == null || !fleet.ships().contains(ship))
            throw new IllegalArgumentException("A snapshot, fleet member and orbital benchmark are required");
        var design = FleetSupplySimulation.design(state, ship);
        if (design == null || !fleet.ownerEntityId().equals(ship.ownerEntityId()))
            throw new IllegalArgumentException("An owned recognized design is required");
        var drive = PropulsionCatalog.mainDrive(design.equippedModuleIds());
        if (drive == null) throw new IllegalArgumentException("A recognized drive is required");
        positive(design.totalDryMassKg());
        nonnegative(ship.currentFuelKg()); nonnegative(ship.generatorFuelMassKg()); nonnegative(ship.supplyFuelMassKg());
        if (ship.currentFuelKg() > design.fuelCapacityKg() || ship.passengerCount() < 0)
            throw new IllegalArgumentException("Invalid ship load");
        ship.storedCargoKg().values().forEach(PlanetaryTransferComparison::nonnegative);
        double mass = design.totalDryMassKg() + ship.currentFuelKg() + ship.generatorFuelMassKg() + ship.supplyFuelMassKg()
                + ship.passengerCount() * 80.0 + ship.storedCargoKg().values().stream().mapToDouble(Double::doubleValue).sum();
        positive(mass);
        double reserve = fleet.fuelPolicy().reserveKg(state, fleet, ship);
        double usable = Math.max(0, ship.currentFuelKg() - reserve);
        var feeds = PropulsionCatalog.reactorFuels(drive.moduleId());
        var feed = feeds.stream().filter(item -> ship.storedCargoKg().getOrDefault(item.materialId(), 0.0) > 0)
                .max(java.util.Comparator.comparingDouble(PropulsionCatalog.ReactorFuel::exhaustMultiplier)).orElse(null);
        double exhaust = drive.exhaustVelocityMps() * (feed == null ? 1 : feed.exhaustMultiplier());
        double required = mass * -Math.expm1(-orbit.totalDeltaVMps() / exhaust);
        boolean feedFits = feeds.isEmpty() || feed != null && (feed.kgPerPropellantKg() == 0
                || (required + reserve) * feed.kgPerPropellantKg() <= ship.storedCargoKg().getOrDefault(feed.materialId(), 0.0));
        double thrust = ShipPowerProcessor.poweredThrust(design, ship,
                ShipSolarEnvironment.journey(state, fleet, FleetLocation.Site.orbit(orbit.targetId())));
        double acceleration = thrust / mass;
        // Initial wet mass deliberately overestimates burn time; changing mass needs a later finite-burn solution.
        double departureFraction = acceleration > 0 ? orbit.departureDeltaVMps() / acceleration / orbit.departureParkingPeriodSeconds()
                : Double.POSITIVE_INFINITY;
        double captureFraction = acceleration > 0 ? orbit.captureDeltaVMps() / acceleration / orbit.arrivalParkingPeriodSeconds()
                : Double.POSITIVE_INFINITY;
        return new Budget(mass, usable, reserve, -exhaust * Math.log1p(-usable / mass), required,
                departureFraction, captureFraction, required <= usable + .000001, feedFits,
                departureFraction <= MAX_BURN_PERIOD_FRACTION && captureFraction <= MAX_BURN_PERIOD_FRACTION);
    }

    private static double parkingDeltaV(Planet planet, double altitudeKm, double excessMps) {
        double radius = parkingRadius(planet, altitudeKm), mu = GRAVITATIONAL_CONSTANT * planet.mass();
        return Math.sqrt(excessMps * excessMps + 2 * mu / radius) - Math.sqrt(mu / radius);
    }

    private static double parkingPeriod(Planet planet, double altitudeKm) {
        double radius = parkingRadius(planet, altitudeKm);
        double period = TWO_PI * Math.sqrt(radius * radius * radius / (GRAVITATIONAL_CONSTANT * planet.mass()));
        positive(period);
        return period;
    }

    private static double parkingRadius(Planet planet, double altitudeKm) {
        positive(planet.mass()); positive(planet.diameter());
        return (planet.diameter() / 2 + altitudeKm) * 1000;
    }

    private static void withinSphere(SolarSystem system, Planet planet, double altitudeKm) {
        double sphereMeters = planet.distance() * 1000 * Math.pow(planet.mass() / system.sunMass(), .4);
        if (parkingRadius(planet, altitudeKm) >= sphereMeters)
            throw new IllegalArgumentException("Parking orbit is outside the planetary sphere-of-influence approximation");
    }

    private static double angle(double radians) { return (radians % TWO_PI + TWO_PI) % TWO_PI; }
    private static void positive(double value) {
        if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException("Positive finite physics values are required");
    }
    private static void nonnegative(double value) {
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Finite nonnegative stores are required");
    }
}
