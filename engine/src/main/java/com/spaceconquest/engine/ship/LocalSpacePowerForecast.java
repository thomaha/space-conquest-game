package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;

/** Uses bounded illumination cycles across the actual local burn and coast phases. */
public final class LocalSpacePowerForecast {
    public record Budget(ShipPowerState state, boolean ready, double journeyKwh) {}
    private LocalSpacePowerForecast() {}

    public static Budget check(GameState state, Fleet fleet, ShipInstance ship, ShipDesign design,
                               FleetLocation.Site destination, LocalTravel.Plan plan) {
        var profile = design.powerProfile();
        var current = ShipPowerProcessor.reserves(ship);
        if (profile == null) return new Budget(current, true, 0);
        if (plan.orbital() != null) return OrbitalPowerAccounting.forecast(ship, design, plan.orbital());
        double essential = profile.essentialKw(ship, design), cargo = ShipPowerProcessor.cargoKw(profile, ship, design);
        var environment = ShipSolarEnvironment.journey(state, fleet, destination);
        double hours = Math.ceil(plan.days()) * 24;
        if (plan.physical() == null) {
            var result = ShipLocalPowerForecast.check(profile, current, hours, environment, essential, cargo, profile.driveKw());
            return new Budget(result.state(), result.ready(), (essential + cargo + profile.driveKw()) * hours);
        }
        var trajectory = plan.physical().trajectory();
        double elapsed = 0, energy = 0;
        boolean ready = true;
        double[] lengths = {trajectory.accelerationSeconds() / 3600, trajectory.coastSeconds() / 3600,
                trajectory.brakingSeconds() / 3600, Math.max(0, hours - trajectory.totalSeconds() / 3600)};
        for (int phase = 0; phase < lengths.length; phase++) {
            double drive = phase == 0 || phase == 2 ? profile.driveKw() : 0;
            var result = ShipLocalPowerForecast.check(profile, current, lengths[phase], environment, essential, cargo, drive, elapsed);
            ready &= result.ready(); current = result.state();
            energy += (essential + cargo + drive) * lengths[phase]; elapsed += lengths[phase];
        }
        return new Budget(current, ready, energy);
    }

    public static Fleet consume(GameState state, Fleet fleet, FleetLocation.Site destination, LocalTravel.Plan plan) {
        return fleet.withShips(fleet.ships().stream().map(ship -> {
            var design = FleetSupplySimulation.design(state, ship);
            return design == null ? ship : ship.withPowerState(check(state, fleet, ship, design, destination, plan).state());
        }).toList());
    }
}
