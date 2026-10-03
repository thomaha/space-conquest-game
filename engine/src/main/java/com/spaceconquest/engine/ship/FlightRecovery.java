package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Pure recovery planning and tick-owned motion. No rescue resources are invented. */
public final class FlightRecovery {
    public record Plan(FlightMotion.Trajectory trajectory, Map<String, Double> fuelKg,
                       Map<String, InterstellarTravel.ReactorFuelUse> reactorKg) {
        public Plan { fuelKg = Map.copyOf(fuelKg); reactorKg = Map.copyOf(reactorKg); }
    }
    private FlightRecovery() {}

    public static Plan plan(GameState state, Fleet fleet) {
        if (fleet == null || !Fleet.MODE_POWER_INTERRUPTED.equals(fleet.interstellarMode())
                || fleet.location().inTransit() || !fleet.hasInterstellarOrder()
                || fleet.interstellarDistanceMeters() <= 0 || fleet.ships().isEmpty()) return null;
        if (state.solarSystems().stream().noneMatch(system -> system.id().equals(fleet.targetSystemId()))) return null;
        FlightMotion motion = motion(fleet);
        double remaining = fleet.interstellarDistanceMeters() - motion.positionMeters();
        double acceleration = Double.POSITIVE_INFINITY, deltaV = Double.POSITIVE_INFINITY;
        Map<String, Double> masses = new HashMap<>(), exhausts = new HashMap<>();
        Map<String, PropulsionCatalog.ReactorFuel> reactors = new HashMap<>();
        for (ShipInstance ship : fleet.ships()) {
            ShipDesign design = state.shipDesigns().stream().filter(item -> item.id().equals(ship.designId()))
                    .findFirst().orElse(null);
            if (design == null || !ship.ownerEntityId().equals(fleet.ownerEntityId())) return null;
            if (!Double.isFinite(design.totalDryMassKg()) || design.totalDryMassKg() <= 0
                    || !Double.isFinite(ship.currentFuelKg()) || ship.currentFuelKg() > design.fuelCapacityKg() + .000001
                    || ship.storedCargoKg().values().stream().anyMatch(value -> !Double.isFinite(value) || value < 0)) return null;
            double mass = design.totalDryMassKg() + ship.currentFuelKg() + ship.generatorFuelMassKg()
                    + ship.storedCargoKg().values().stream().mapToDouble(Double::doubleValue).sum()
                    + ship.passengerCount() * 80.0;
            if (!Double.isFinite(mass) || mass <= ship.currentFuelKg() || ship.currentFuelKg() < 0) return null;
            acceleration = Math.min(acceleration, ShipPowerProcessor.poweredThrust(design, ship, ShipSolarEnvironment.DARK) / mass);
            var drive = PropulsionCatalog.mainDrive(design.equippedModuleIds());
            if (drive == null) return null; // Recovery must have an explicit physical propellant model.
            var reactor = PropulsionCatalog.availableReactorFuel(drive, ship);
            if (!PropulsionCatalog.reactorFuels(drive.moduleId()).isEmpty() && reactor == null) return null;
            double exhaust = drive.exhaustVelocityMps() * (reactor == null ? 1 : reactor.exhaustMultiplier());
            deltaV = Math.min(deltaV, exhaust * Math.log(mass / (mass - ship.currentFuelKg())));
            masses.put(ship.id(), mass); exhausts.put(ship.id(), exhaust);
            if (reactor != null) reactors.put(ship.id(), reactor);
        }
        double velocity = motion.velocityMps();
        if (!Double.isFinite(acceleration) || acceleration <= 0 || remaining <= 0
                || deltaV + .000001 < velocity || velocity * velocity / (2 * acceleration) > remaining) return null;
        double peak = Math.min(InterstellarTravel.MAX_CRUISE_MPS,
                Math.min((deltaV + velocity) / 2, Math.sqrt(acceleration * remaining + velocity * velocity / 2)));
        if (peak < velocity || peak <= 0) return null;
        double accelerate = (peak - velocity) / acceleration, brake = peak / acceleration;
        double coast = Math.max(0, (remaining - (peak * peak - velocity * velocity) / (2 * acceleration)
                - peak * peak / (2 * acceleration)) / peak);
        var trajectory = new FlightMotion.Trajectory(motion.positionMeters(), velocity, acceleration, peak, accelerate, coast, brake);
        Map<String, Double> fuel = new HashMap<>();
        Map<String, InterstellarTravel.ReactorFuelUse> reactorFuel = new HashMap<>();
        for (ShipInstance ship : fleet.ships()) {
            double quantity = masses.get(ship.id()) * -Math.expm1(-(2 * peak - velocity) / exhausts.get(ship.id()));
            if (quantity > ship.currentFuelKg() + .000001) return null;
            fuel.put(ship.id(), Math.min(quantity, ship.currentFuelKg()));
            var reactor = reactors.get(ship.id());
            if (reactor != null && reactor.kgPerPropellantKg() > 0)
                reactorFuel.put(ship.id(), new InterstellarTravel.ReactorFuelUse(reactor.materialId(), quantity * reactor.kgPerPropellantKg()));
        }
        return new Plan(trajectory, fuel, reactorFuel);
    }

    public static boolean electricallyReady(GameState state, Fleet fleet, Plan plan) {
        if (plan == null) return false;
        for (ShipInstance ship : fleet.ships()) {
            var design = state.shipDesigns().stream().filter(item -> item.id().equals(ship.designId())).findFirst().orElseThrow();
            var profile = design.powerProfile();
            if (profile == null) continue;
            var power = ShipPowerProcessor.reserves(ship);
            double essential = profile.essentialKw(ship, design), cargo = ShipPowerProcessor.cargoKw(profile, ship);
            var trajectory = plan.trajectory();
            for (double[] phase : new double[][]{{trajectory.accelerationSeconds(), profile.driveKw(), cargo},
                    {trajectory.coastSeconds(), 0, cargo}, {trajectory.brakingSeconds(), profile.driveKw(), cargo},
                    {Math.ceil(trajectory.totalSeconds() / InterstellarTravel.SECONDS_PER_DAY)
                            * InterstellarTravel.SECONDS_PER_DAY - trajectory.totalSeconds(), 0, cargo},
                    {ShipPowerProcessor.ARRIVAL_RESERVE_HOURS * 3600, 0, 0}}) {
                var interval = ShipPowerProcessor.interval(profile, power, phase[0] / 3600, 0, essential, phase[2], phase[1]);
                if (!interval.supplied()) return false;
                power = interval.state();
            }
        }
        return true;
    }

    public static Fleet depart(Fleet fleet, Plan plan) {
        var trajectory = plan.trajectory();
        var fueled = InterstellarTravel.commitReactorFuel(fleet, new InterstellarTravel.Plan(Fleet.MODE_RECOVERY,
                trajectory.totalSeconds() / InterstellarTravel.SECONDS_PER_DAY, fleet.interstellarDistanceMeters(),
                trajectory.accelerationMps2(), trajectory.peakMps(), plan.fuelKg(), plan.reactorKg()));
        return copy(fueled, Fleet.MODE_RECOVERY, trajectory.at(0), plan.fuelKg());
    }

    public static FlightMotion motion(Fleet fleet) {
        if (fleet.flightMotion() != null) return fleet.flightMotion();
        double seconds = fleet.interstellarElapsedDays() * InterstellarTravel.SECONDS_PER_DAY;
        return new FlightMotion(fleet.transitProgress() * fleet.interstellarDistanceMeters(),
                InterstellarTravel.velocityMps(fleet.interstellarDistanceMeters(), fleet.interstellarAccelerationMps2(),
                        fleet.interstellarPeakSpeedMps(), seconds), null, 0);
    }

    /** Advance powered motion up to the first failure, then coast for the rest of this day. */
    public static Fleet advance(Fleet fleet, double poweredHours) {
        if (fleet.location().inTransit() || Fleet.MODE_WARP.equals(fleet.interstellarMode())
                || Fleet.MODE_POWER_INTERRUPTED.equals(fleet.interstellarMode()) && fleet.interstellarDistanceMeters() <= 0)
            return PausedTravelRecovery.interrupt(fleet, poweredHours);
        double poweredSeconds = poweredHours * 3600, day = InterstellarTravel.SECONDS_PER_DAY;
        FlightMotion next;
        double fuelFraction = 0;
        if (Fleet.MODE_RECOVERY.equals(fleet.interstellarMode())) {
            var previous = fleet.flightMotion();
            var trajectory = previous.trajectory();
            double elapsed = previous.elapsedSeconds() + poweredSeconds;
            next = trajectory.at(elapsed);
            fuelFraction = (trajectory.impulse(elapsed) - trajectory.impulse(previous.elapsedSeconds()))
                    / trajectory.impulse(trajectory.totalSeconds());
            if (elapsed + .000001 >= trajectory.totalSeconds()) {
                return arrive(fleet.withShips(burn(fleet, fuelFraction)));
            }
        } else if (Fleet.MODE_SUBLIGHT.equals(fleet.interstellarMode()) && !fleet.location().inTransit()) {
            double prior = fleet.interstellarElapsedDays() * day, elapsed = prior + poweredSeconds;
            double distance = fleet.interstellarDistanceMeters(), acceleration = fleet.interstellarAccelerationMps2();
            double peak = fleet.interstellarPeakSpeedMps();
            next = new FlightMotion(InterstellarTravel.progress(distance, acceleration, peak, elapsed) * distance,
                    InterstellarTravel.velocityMps(distance, acceleration, peak, elapsed), null, 0);
            fuelFraction = InterstellarTravel.fuelBurnFraction(distance, acceleration, peak, elapsed)
                    - InterstellarTravel.fuelBurnFraction(distance, acceleration, peak, prior);
            if (elapsed >= InterstellarTravel.travelSeconds(distance, acceleration, peak))
                return arrive(fleet.withShips(burn(fleet, fuelFraction)));
        } else next = motion(fleet);
        if (Fleet.MODE_POWER_INTERRUPTED.equals(fleet.interstellarMode())) poweredSeconds = 0;
        boolean interrupted = poweredHours < 24 || Fleet.MODE_POWER_INTERRUPTED.equals(fleet.interstellarMode());
        if (interrupted) next = new FlightMotion(next.positionMeters() + next.velocityMps() * (day - poweredSeconds),
                next.velocityMps(), null, 0);
        return copy(fleet.withShips(burn(fleet, fuelFraction)), interrupted ? Fleet.MODE_POWER_INTERRUPTED : Fleet.MODE_RECOVERY,
                next, fleet.interstellarFuelBudgetKg());
    }

    private static List<ShipInstance> burn(Fleet fleet, double fraction) {
        return fleet.ships().stream().map(ship -> new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(),
                ship.currentHullHealth(), ship.currentShieldHealth(), Math.max(0, ship.currentFuelKg()
                - fleet.interstellarFuelBudgetKg().getOrDefault(ship.id(), 0.0) * fraction), ship.storedCargoKg(),
                ship.passengerCount(), ship.passengerRaceId(), ship.transitMode(), ship.powerState())).toList();
    }

    private static Fleet arrive(Fleet fleet) {
        return new Fleet(fleet.id(), fleet.name(), fleet.ownerEntityId(), fleet.targetSystemId(), "",
                fleet.coordinateX(), fleet.coordinateY(), 0, false, fleet.fleetStance(), fleet.ships(),
                FleetLocation.at(FleetLocation.Site.deepSpace()), "", 0, 0, 0, 0, 0, Map.of(), null);
    }

    private static Fleet copy(Fleet fleet, String mode, FlightMotion motion, Map<String, Double> budget) {
        return new Fleet(fleet.id(), fleet.name(), fleet.ownerEntityId(), fleet.currentSystemId(), fleet.targetSystemId(),
                fleet.coordinateX(), fleet.coordinateY(), fleet.interstellarDistanceMeters() <= 0 ? fleet.transitProgress()
                : Math.clamp(motion.positionMeters() / fleet.interstellarDistanceMeters(), 0, 1), false,
                fleet.fleetStance(), fleet.ships(), fleet.location(), mode, fleet.interstellarTravelDays(),
                fleet.interstellarDistanceMeters(), fleet.interstellarAccelerationMps2(), fleet.interstellarElapsedDays(),
                fleet.interstellarPeakSpeedMps(), budget, motion);
    }
}
