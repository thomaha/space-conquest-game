package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import java.util.ArrayList;
import java.util.List;

/** Bounded departure calculation: local cycles and three analytic interstellar phases, never a daily simulation. */
public final class ShipPowerForecast {
    public record Readiness(String shipId, boolean modeled, boolean ready, double journeyKwh,
                            double arrivalReserveKwh, double remainingBatteryKwh,
                            double generatorFuelUsedKg, String explanation) {}
    private ShipPowerForecast() {}

    public static List<Readiness> departure(GameState state, Fleet fleet, FleetLocation.Site destination,
                                           LocalTravel.Plan local, InterstellarTravel.Plan crossing) {
        if (FleetSupplySimulation.hasOrders(fleet)) return FleetSupplyForecast.departure(state, fleet, local, crossing);
        if (local != null && (!Double.isFinite(local.days() * 24) || local.days() <= 0))
            return List.of(new Readiness("fleet", true, false, 0, 0, 0, 0, "Unsupported local journey duration"));
        List<Readiness> results = new ArrayList<>();
        for (ShipInstance ship : fleet.ships()) {
            ShipDesign design = state.shipDesigns().stream().filter(item -> item.id().equals(ship.designId()))
                    .findFirst().orElse(null);
            if (design == null) return List.of(new Readiness(ship.id(), false, false, 0, 0, 0, 0, "Missing blueprint"));
            ShipPowerProfile profile = design.powerProfile();
            if (profile == null) {
                results.add(new Readiness(ship.id(), false, true, 0, 0, 0, 0,
                        "Legacy electrical compatibility mode; endurance is unknown"));
                continue;
            }
            ShipPowerState initial = ShipPowerProcessor.reserves(ship), current = initial;
            double essential = profile.essentialKw(ship, design), cargo = ShipPowerProcessor.cargoKw(profile, ship, design);
            double energy = 0;
            boolean ready = true;
            if (local != null) {
                var budget = LocalSpacePowerForecast.check(state, fleet, ship, design, destination, local);
                energy += budget.journeyKwh();
                ready &= budget.ready(); current = budget.state();
            }
            if (crossing != null) {
                double hours = Math.ceil(crossing.days()) * 24;
                double burn = Fleet.MODE_SUBLIGHT.equals(crossing.mode())
                        ? Math.min(hours / 2, crossing.peakSpeedMps() / crossing.accelerationMps2() / 3600)
                        : hours / 2;
                for (double[] phase : new double[][]{{burn, profile.driveKw()}, {hours - 2 * burn, 0},
                        {burn, profile.driveKw()}}) {
                    energy += (essential + cargo + phase[1]) * phase[0];
                    var step = ShipPowerProcessor.interval(profile, current, phase[0], 0, essential, cargo, phase[1]);
                    ready &= step.supplied(); current = step.state();
                }
            }
            var arrival = ShipArrivalReserve.environment(state, fleet, destination, crossing != null);
            var reserveCheck = ShipArrivalReserve.check(profile, current, essential, cargo, arrival);
            ready &= reserveCheck.ready();
            results.add(new Readiness(ship.id(), true, ready, energy, reserveCheck.requiredKwh(), current.batteryChargeKwh(),
                    initial.fuelMassKg() - current.fuelMassKg(), ready
                    ? "Electrical supply includes a 48-hour essential and cargo arrival reserve with destination eclipses"
                    : "Insufficient electrical energy, peak output or eclipse storage; resupply or change equipment"));
        }
        return List.copyOf(results);
    }

    public static boolean ready(List<Readiness> checks) { return checks.stream().allMatch(Readiness::ready); }
}
