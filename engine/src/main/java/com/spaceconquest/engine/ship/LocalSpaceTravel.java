package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;

import java.util.HashMap;
import java.util.Map;

/** Bounded physical local planning from loaded acceleration and funded velocity change. */
public final class LocalSpaceTravel {
    private static final double EPSILON_KG = .000001;

    public record Plan(LocalSiteGeometry.Leg geometry, FlightMotion.Trajectory trajectory,
                       Map<String, Double> propellantKg,
                       Map<String, InterstellarTravel.ReactorFuelUse> reactorFuelKg,
                       Map<String, JourneyPropulsion> propulsion) {
        public Plan {
            propellantKg = Map.copyOf(propellantKg);
            reactorFuelKg = Map.copyOf(reactorFuelKg);
            propulsion = Map.copyOf(propulsion);
        }
        public double days() { return trajectory.totalSeconds() / InterstellarTravel.SECONDS_PER_DAY; }
        public double scheduledDays() { return Math.ceil(days()); }
    }

    private record Capability(ShipInstance ship, PropulsionCatalog.Drive drive,
                              PropulsionCatalog.ReactorFuel reactor, double massKg, double exhaustMps,
                              double deltaVMps, double accelerationMps2) {}

    private LocalSpaceTravel() {}

    public static Plan plan(GameState state, Fleet fleet, FleetLocation.Site destination) {
        return plan(state, fleet, destination, Map.of());
    }

    public static Plan plan(GameState state, Fleet fleet, FleetLocation.Site destination, Map<String, Double> reserveKg) {
        if (state == null || fleet == null || destination == null || fleet.ships().isEmpty()
                || fleet.location().inTransit() || fleet.hasInterstellarOrder()
                || fleet.ships().stream().map(ShipInstance::id).distinct().count() != fleet.ships().size()) return null;
        var geometry = LocalSiteGeometry.resolve(state, fleet.currentSystemId(), fleet.location().current(), destination);
        if (geometry == null) return null;
        Map<String, Double> protectedKg = new HashMap<>(reserveKg);
        fleet.ships().forEach(ship -> protectedKg.merge(ship.id(), fleet.fuelPolicy().reserveKg(state, fleet, ship), Math::max));
        return solve(state, fleet, destination, geometry, 0, 0, protectedKg, true);
    }

    public static Plan recovery(GameState state, Fleet fleet) {
        if (state == null || fleet == null || fleet.location().localFlight() == null
                || !fleet.location().localFlight().interrupted()) return null;
        var flight = fleet.location().localFlight();
        Map<String, Double> protectedKg = new HashMap<>(fleet.interstellarFuelBudgetKg());
        fleet.ships().forEach(ship -> protectedKg.merge(ship.id(), fleet.fuelPolicy().reserveKg(state, fleet, ship), Double::sum));
        return solve(state, fleet, fleet.location().destination(), flight.geometry(),
                flight.motion().positionMeters(), flight.motion().velocityMps(), protectedKg, false);
    }

    private static Plan solve(GameState state, Fleet fleet, FleetLocation.Site destination,
                              LocalSiteGeometry.Leg geometry, double position, double velocity,
                              Map<String, Double> reserveKg, boolean conserveDepartureFuel) {
        double remaining = geometry.distanceMeters() - position;
        if (remaining <= 0 || fleet.ships().isEmpty()) return null;
        Map<String, Capability> ships = new HashMap<>();
        double acceleration = Double.POSITIVE_INFINITY, deltaV = Double.POSITIVE_INFINITY;
        for (var ship : fleet.ships()) {
            var capability = capability(state, fleet, ship, destination, reserveKg.getOrDefault(ship.id(), 0.0));
            if (capability == null) return null;
            ships.put(ship.id(), capability);
            acceleration = Math.min(acceleration, capability.accelerationMps2());
            deltaV = Math.min(deltaV, capability.deltaVMps());
        }
        if (deltaV < velocity || velocity * velocity / (2 * acceleration) > remaining) return null;
        double peak = Math.min(InterstellarTravel.MAX_CRUISE_MPS,
                Math.min((deltaV + velocity) / 2, Math.sqrt(acceleration * remaining + velocity * velocity / 2)));
        if (!Double.isFinite(peak) || peak <= 0 || peak < velocity) return null;
        if (conserveDepartureFuel) peak = economicalPeak(remaining, acceleration, peak);
        double accelerate = (peak - velocity) / acceleration, brake = peak / acceleration;
        double coast = Math.max(0, (remaining - (2 * peak * peak - velocity * velocity) / (2 * acceleration)) / peak);
        if (!Double.isFinite(accelerate + brake + coast)) return null;
        var trajectory = new FlightMotion.Trajectory(position, velocity, acceleration, peak, accelerate, coast, brake);
        Map<String, Double> propellant = new HashMap<>();
        Map<String, InterstellarTravel.ReactorFuelUse> reactor = new HashMap<>();
        Map<String, JourneyPropulsion> propulsion = new HashMap<>();
        for (var capability : ships.values()) {
            double kg = capability.massKg() * -Math.expm1(-(2 * peak - velocity) / capability.exhaustMps());
            if (!Double.isFinite(kg) || kg <= 0 || kg > capability.ship().currentFuelKg() + EPSILON_KG) return null;
            propellant.put(capability.ship().id(), kg);
            var feed = capability.reactor();
            if (feed != null && feed.kgPerPropellantKg() > 0) {
                double required = kg * feed.kgPerPropellantKg();
                if (required > capability.ship().storedCargoKg().getOrDefault(feed.materialId(), 0.0) + EPSILON_KG) return null;
                reactor.put(capability.ship().id(), new InterstellarTravel.ReactorFuelUse(feed.materialId(), required));
            }
            propulsion.put(capability.ship().id(), JourneyPropulsion.capture(
                    capability.ship(), capability.drive(), feed, kg).withProtectedPropellant(fleet.fuelPolicy().reserveKg(state, fleet, capability.ship())));
        }
        return new Plan(geometry, trajectory, propellant, reactor, propulsion);
    }

    /** Use the slowest cruise that preserves the fastest funded trajectory's arrival tick. */
    private static double economicalPeak(double distance, double acceleration, double fastestPeak) {
        double fastestSeconds = distance / fastestPeak + fastestPeak / acceleration;
        double arrivalDay = Math.ceil(fastestSeconds / InterstellarTravel.SECONDS_PER_DAY);
        double deadline = arrivalDay * InterstellarTravel.SECONDS_PER_DAY - .001;
        if (!Double.isFinite(deadline) || deadline <= fastestSeconds) return fastestPeak;
        double discriminant = deadline * deadline - 4 * distance / acceleration;
        if (!Double.isFinite(discriminant) || discriminant < 0) return fastestPeak;
        double peak = 2 * distance / (deadline + Math.sqrt(discriminant));
        if (!Double.isFinite(peak) || peak <= 0 || peak > fastestPeak) return fastestPeak;
        double days = (distance / peak + peak / acceleration) / InterstellarTravel.SECONDS_PER_DAY;
        return Math.ceil(days) == arrivalDay ? peak : fastestPeak;
    }

    public static Fleet resume(Fleet fleet, Plan plan) {
        var ships = fleet.ships().stream().map(ship -> {
            var feed = plan.reactorFuelKg().get(ship.id());
            if (feed == null) return ship;
            var cargo = new HashMap<>(ship.storedCargoKg());
            cargo.put(feed.materialId(), Math.max(0, cargo.getOrDefault(feed.materialId(), 0.0) - feed.quantityKg()));
            return new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), ship.currentHullHealth(),
                    ship.currentShieldHealth(), ship.currentFuelKg(), cargo, ship.passengerCount(), ship.passengerRaceId(),
                    ship.transitMode(), ship.powerState(), ship.supplyState());
        }).toList();
        var prior = fleet.location();
        var flight = new LocalFlight(plan.geometry(), plan.trajectory().at(0), plan.propellantKg(), plan.propulsion(), false, false);
        return fleet.withShips(ships).withLocation(new FleetLocation(prior.current(), prior.destination(),
                Math.clamp(flight.motion().positionMeters() / flight.geometry().distanceMeters(), 0, 1), plan.days(), flight));
    }

    private static Capability capability(GameState state, Fleet fleet, ShipInstance ship, FleetLocation.Site destination, double reserveKg) {
        var design = FleetSupplySimulation.design(state, ship);
        if (design == null || !fleet.ownerEntityId().equals(ship.ownerEntityId())
                || !Double.isFinite(design.totalDryMassKg()) || design.totalDryMassKg() <= 0
                || !Double.isFinite(ship.currentFuelKg()) || ship.currentFuelKg() <= EPSILON_KG
                || ship.currentFuelKg() > design.fuelCapacityKg() + EPSILON_KG || ship.passengerCount() < 0
                || ship.storedCargoKg().values().stream().anyMatch(kg -> !Double.isFinite(kg) || kg < 0)) return null;
        var drive = PropulsionCatalog.mainDrive(design.equippedModuleIds());
        if (drive == null) return null;
        double mass = design.totalDryMassKg() + ship.currentFuelKg() + ship.generatorFuelMassKg() + ship.supplyFuelMassKg()
                + ship.passengerCount() * 80.0 + ship.storedCargoKg().values().stream().mapToDouble(Double::doubleValue).sum();
        double thrust = ShipPowerProcessor.poweredThrust(design, ship, ShipSolarEnvironment.journey(state, fleet, destination));
        if (!Double.isFinite(mass) || mass <= ship.currentFuelKg() || !Double.isFinite(thrust) || thrust <= 0) return null;
        var feeds = PropulsionCatalog.reactorFuels(drive.moduleId());
        double contingencyKg = fleet.fuelPolicy().reserveKg(state, fleet, ship);
        if (feeds.isEmpty()) return supported(ship, drive, null, mass, thrust, reserveKg, contingencyKg);
        Capability best = null;
        for (var feed : feeds) {
            var candidate = supported(ship, drive, feed, mass, thrust, reserveKg, contingencyKg);
            if (candidate != null && (best == null || candidate.deltaVMps() > best.deltaVMps())) best = candidate;
        }
        return best;
    }

    private static Capability supported(ShipInstance ship, PropulsionCatalog.Drive drive,
                                        PropulsionCatalog.ReactorFuel reactor, double mass, double thrust,
                                        double reserveKg, double contingencyKg) {
        if (!Double.isFinite(reserveKg) || reserveKg < 0) return null;
        double usable = Math.max(0, ship.currentFuelKg() - reserveKg);
        if (reactor != null && reactor.kgPerPropellantKg() > 0)
            usable = Math.min(usable, ship.storedCargoKg().getOrDefault(reactor.materialId(), 0.0)
                    / reactor.kgPerPropellantKg() - contingencyKg);
        usable = Math.max(0, usable - Math.max(EPSILON_KG, usable * 1e-9));
        double exhaust = drive.exhaustVelocityMps() * (reactor == null ? 1 : reactor.exhaustMultiplier());
        double deltaV = -exhaust * Math.log1p(-usable / mass);
        return !Double.isFinite(deltaV) || deltaV <= 0 ? null
                : new Capability(ship, drive, reactor, mass, exhaust, deltaV, thrust / mass);
    }
}
