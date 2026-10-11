package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;

/** Uses bounded illumination cycles across the actual local burn and coast phases. */
public final class LocalSpacePowerForecast {
    public record Budget(ShipPowerState state, boolean ready, double journeyKwh, ShipInstance projectedShip) {
        public Budget(ShipPowerState state, boolean ready, double journeyKwh) { this(state, ready, journeyKwh, null); }
    }
    private LocalSpacePowerForecast() {}

    public static Budget check(GameState state, Fleet fleet, ShipInstance ship, ShipDesign design,
                               FleetLocation.Site destination, LocalTravel.Plan plan) {
        var profile = design.powerProfile(ship);
        var current = ShipPowerProcessor.reserves(ship);
        if (profile == null) return new Budget(current, true, 0);
        var activity = ShipServicePower.activity(state, fleet);
        if (plan.orbital() != null) return OrbitalPowerAccounting.forecast(ship, design, plan.orbital(),
                !fleet.hasInterstellarOrder() && FleetShieldPolicy.recharge(state, fleet, true), activity);
        var environment = ShipSolarEnvironment.journey(state, fleet, destination);
        var result = ShipShieldPower.forecast(ship, design, ShipPowerPhases.withActivity(ShipPowerPhases.local(profile, environment, plan), activity),
                ShipArrivalReserve.environment(state, fleet, destination, false), FleetShieldPolicy.recharge(state, fleet, true));
        return new Budget(result.ship().powerState(), result.ready(), result.journeyKwh(), result.ship());
    }

    static Budget physical(ShipInstance ship, ShipDesign design, ShipSolarEnvironment environment, LocalSpaceTravel.Plan plan) {
        return physical(ship, design, environment, plan, false);
    }

    static Budget physical(ShipInstance ship, ShipDesign design, ShipSolarEnvironment environment, LocalSpaceTravel.Plan plan, boolean recharge) {
        return physical(ship, design, environment, plan, recharge, ShipServicePower.Activity.CRUISE);
    }
    static Budget physical(ShipInstance ship, ShipDesign design, ShipSolarEnvironment environment, LocalSpaceTravel.Plan plan,
                           boolean recharge, ShipServicePower.Activity activity) {
        var local = new LocalTravel.Plan(plan.days(), plan.propellantKg(), plan.reactorFuelKg(), plan);
        var result = ShipShieldPower.forecast(ship, design, ShipPowerPhases.withActivity(ShipPowerPhases.local(design.powerProfile(), environment, local), activity), environment, recharge);
        return new Budget(result.ship().powerState(), result.ready(), result.journeyKwh(), result.ship());
    }

    public static Fleet consume(GameState state, Fleet fleet, FleetLocation.Site destination, LocalTravel.Plan plan) {
        return fleet.withShips(fleet.ships().stream().map(ship -> {
            var design = FleetSupplySimulation.design(state, ship);
            if (design == null) return ship;
            var budget = check(state, fleet, ship, design, destination, plan);
            return budget.projectedShip() == null ? ship.withPowerState(budget.state()) : budget.projectedShip();
        }).toList());
    }

    static boolean remainingReady(GameState state, Fleet fleet, double seconds) {
        var activity = ShipServicePower.activity(state, fleet);
        var orbital = fleet.location().orbitalFlight(); var local = fleet.location().localFlight();
        if (local != null && LocalFlightProcessor.availableHours(fleet, seconds / 3600) + 1e-9 < seconds / 3600) return false;
        for (var ship : fleet.ships()) {
            var design = FleetSupplySimulation.design(state, ship);
            if (design == null) return false;
            if (orbital != null) {
                if (!OrbitalPowerAccounting.projection(ship, design, orbital.itinerary(), orbital.elapsedSeconds(), orbital.nextManeuver(), false, activity).ready()) return false;
                continue;
            }
            var profile = design.powerProfile(ship);
            double reserve = fleet.fuelPolicy().reserveKg(state, fleet, ship);
            var commitment = local.propulsion().get(ship.id());
            if (commitment != null) reserve = Math.max(reserve, commitment.protectedPropellantKg());
            if (ship.currentFuelKg() - LocalFlightProcessor.remainingPropellantKg(fleet, ship.id(), seconds) + 1e-6 < reserve) return false;
            if (profile == null) continue;
            var current = ShipPowerProcessor.reserves(ship); var trajectory = local.motion().trajectory();
            double start = local.motion().elapsedSeconds(), end = start + seconds;
            double[] boundaries = {0, trajectory.accelerationSeconds(), trajectory.brakingStart(), trajectory.totalSeconds()};
            var environment = ShipSolarEnvironment.journey(state, fleet, fleet.location().destination());
            for (int phase = 0; phase < 3; phase++) {
                double from = Math.max(start, boundaries[phase]), to = Math.min(end, boundaries[phase + 1]);
                if (to <= from) continue;
                var budget = ShipLocalPowerForecast.check(profile, current, (to - from) / 3600, environment,
                        profile.essentialKw(ship, design), ShipPowerProcessor.cargoKw(profile, ship, design),
                        phase == 1 ? 0 : profile.driveKw(), (from - start) / 3600, activity);
                if (!budget.ready()) return false;
                current = budget.state();
            }
        }
        return true;
    }
}
