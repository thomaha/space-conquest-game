package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import java.util.ArrayList;
import java.util.List;

/** Bounded departure calculation with optional shield work and analytic required-load tails. */
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
        var activity = ShipServicePower.activity(state, fleet);
        for (ShipInstance ship : fleet.ships()) {
            ShipDesign design = state.shipDesigns().stream().filter(item -> item.id().equals(ship.designId()))
                    .findFirst().orElse(null);
            if (design == null) return List.of(new Readiness(ship.id(), false, false, 0, 0, 0, 0, "Missing blueprint"));
            ShipPowerProfile profile = design.powerProfile(ship);
            if (profile == null) {
                results.add(new Readiness(ship.id(), false, true, 0, 0, 0, 0,
                        "Legacy electrical compatibility mode; endurance is unknown"));
                continue;
            }
            ShipPowerState initial = ShipPowerProcessor.reserves(ship), current = initial;
            double essential = profile.essentialKw(ship, design), cargo = ShipPowerProcessor.cargoKw(profile, ship, design);
            double energy = 0;
            boolean ready = true;
            if (local == null || local.orbital() == null) {
                var phases = new ArrayList<ShipShieldPower.Phase>();
                if (local != null) phases.addAll(ShipPowerPhases.local(profile, ShipSolarEnvironment.journey(state, fleet, destination), local));
                if (crossing != null) phases.addAll(ShipPowerPhases.crossing(profile, crossing));
                var budget = ShipShieldPower.forecast(ship, design, ShipPowerPhases.withActivity(phases, activity), ShipArrivalReserve.environment(state, fleet, destination, crossing != null),
                        FleetShieldPolicy.recharge(state, fleet, true));
                energy = budget.journeyKwh(); current = budget.ship().powerState(); ready = budget.ready();
            } else {
                var budget = OrbitalPowerAccounting.forecast(ship, design, local.orbital(),
                        crossing == null && FleetShieldPolicy.recharge(state, fleet, true), activity);
                energy += budget.journeyKwh();
                ready &= budget.ready(); current = budget.state();
                if (crossing != null) {
                    var actual = budget.projectedShip() == null ? ship.withPowerState(current) : budget.projectedShip();
                    var rest = ShipShieldPower.forecast(actual, design, ShipPowerPhases.withActivity(ShipPowerPhases.crossing(profile, crossing), activity),
                            ShipSolarEnvironment.DARK, FleetShieldPolicy.recharge(state, fleet, true));
                    energy += rest.journeyKwh(); ready &= rest.ready(); current = rest.ship().powerState();
                }
            }
            var arrival = ShipArrivalReserve.environment(state, fleet, destination, crossing != null);
            var reserveCheck = ShipArrivalReserve.check(profile, current, essential, cargo, arrival, ShipPowerProcessor.ARRIVAL_RESERVE_HOURS, activity);
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
