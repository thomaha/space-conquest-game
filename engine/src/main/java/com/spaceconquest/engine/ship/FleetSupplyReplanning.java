package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import java.util.HashMap;
import java.util.Map;

/** A refill retains current velocity and replaces the remaining coast and brake using real loaded mass. */
public final class FleetSupplyReplanning {
    private FleetSupplyReplanning() {}

    public static Fleet plan(GameState state, Fleet fleet) {
        var motion = fleet.flightMotion();
        if (motion == null || motion.trajectory() == null || motion.trajectory().rescueOrder() != null
                || motion.velocityMps() <= 0 || fleet.ships().isEmpty()) return null;
        double remaining = fleet.interstellarDistanceMeters() - motion.positionMeters();
        double acceleration = Double.POSITIVE_INFINITY;
        Map<String, Double> masses = new HashMap<>();
        for (var ship : fleet.ships()) {
            var d = FleetSupplySimulation.design(state, ship);
            var saved = fleet.journeyPropulsion().get(ship.id());
            var drive = d == null ? null : PropulsionCatalog.mainDrive(d.equippedModuleIds());
            if (drive == null || saved == null || !saved.designId().equals(ship.designId())
                    || !saved.driveModuleId().equals(drive.moduleId()) || ship.currentHullHealth() <= 0
                    || !Double.isFinite(ship.currentFuelKg()) || ship.currentFuelKg() < 0
                    || ship.currentFuelKg() > d.fuelCapacityKg() + 1e-6
                    || !Double.isFinite(d.totalDryMassKg()) || d.totalDryMassKg() <= 0
                    || ship.storedCargoKg().values().stream().anyMatch(value -> !Double.isFinite(value) || value < 0)
                    || !ship.ownerEntityId().equals(fleet.ownerEntityId())) return null;
            double mass = d.totalDryMassKg() + ship.currentFuelKg() + ship.generatorFuelMassKg() + ship.supplyFuelMassKg()
                    + ship.passengerCount() * 80 + ship.storedCargoKg().values().stream().mapToDouble(Double::doubleValue).sum();
            if (!Double.isFinite(mass) || mass <= ship.currentFuelKg()) return null;
            masses.put(ship.id(), mass);
            acceleration = Math.min(acceleration, ShipPowerProcessor.poweredThrust(d, ship, ShipSolarEnvironment.DARK) / mass);
        }
        double velocity = motion.velocityMps(), brakingDistance = velocity * velocity / (2 * acceleration);
        if (!Double.isFinite(acceleration) || acceleration <= 0 || !Double.isFinite(brakingDistance)
                || remaining <= 0 || brakingDistance > remaining + 1e-6) return null;
        var trajectory = new FlightMotion.Trajectory(motion.positionMeters(), velocity, acceleration, velocity, 0,
                Math.max(0, (remaining - brakingDistance) / velocity), velocity / acceleration);
        Map<String, Double> fuel = new HashMap<>();
        Map<String, JourneyPropulsion> propulsion = new HashMap<>();
        Map<String, InterstellarTravel.ReactorFuelUse> additionalFeed = new HashMap<>();
        double oldFraction = motion.trajectory().impulse(motion.elapsedSeconds())
                / motion.trajectory().impulse(motion.trajectory().totalSeconds());
        for (var ship : fleet.ships()) {
            var saved = fleet.journeyPropulsion().get(ship.id());
            double kg = masses.get(ship.id()) * -Math.expm1(-velocity / saved.exhaustVelocityMps());
            if (!Double.isFinite(kg) || kg + saved.protectedPropellantKg() > ship.currentFuelKg() + 1e-6) return null;
            fuel.put(ship.id(), Math.min(kg, ship.currentFuelKg()));
            var feed = saved.reactorFeedId() == null ? null : PropulsionCatalog.reactorFuel(saved.driveModuleId(), saved.reactorFeedId());
            if (!PropulsionCatalog.reactorFuels(saved.driveModuleId()).isEmpty() && feed == null) return null;
            double needed = feed == null ? 0 : kg * feed.kgPerPropellantKg();
            double extra = Math.max(0, needed - saved.committedReactorKg() * (1 - oldFraction));
            if (extra > 0) {
                if (ship.storedCargoKg().getOrDefault(feed.materialId(), 0.0) + 1e-6
                        < extra + saved.protectedPropellantKg() * feed.kgPerPropellantKg()) return null;
                additionalFeed.put(ship.id(), new InterstellarTravel.ReactorFuelUse(feed.materialId(), extra));
            }
            propulsion.put(ship.id(), new JourneyPropulsion(saved.designId(), saved.driveModuleId(),
                    saved.exhaustVelocityMps(), saved.reactorFeedId(), needed, saved.protectedPropellantKg()));
        }
        var paid = InterstellarTravel.commitReactorFuel(fleet, new InterstellarTravel.Plan(Fleet.MODE_RECOVERY,
                trajectory.totalSeconds() / 86400, fleet.interstellarDistanceMeters(), acceleration, velocity,
                fuel, additionalFeed, propulsion));
        return FleetPropulsionSupply.motion(paid, Fleet.MODE_RECOVERY, trajectory.at(0), fuel, propulsion);
    }

    public static boolean electricallyReady(GameState state, Fleet fleet, double deliveryHour) {
        var trajectory = fleet.flightMotion().trajectory();
        double arrival = deliveryHour + trajectory.totalSeconds() / 3600;
        double padding = Math.ceil(arrival / 24) * 24 - arrival;
        for (var ship : fleet.ships()) {
            var design = FleetSupplySimulation.design(state, ship);
            var p = design.powerProfile();
            if (p == null) return false;
            var power = ShipPowerProcessor.reserves(ship);
            for (double[] phase : new double[][]{{trajectory.coastSeconds() / 3600, 0},
                    {trajectory.brakingSeconds() / 3600, p.driveKw()}, {padding + 48, 0}}) {
                var interval = ShipPowerProcessor.interval(p, power, phase[0], 0, p.essentialKw(ship, design),
                        ShipPowerProcessor.cargoKw(p, ship, design), phase[1]);
                if (!interval.supplied()) return false;
                power = interval.state();
            }
        }
        return true;
    }
}
