package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import java.util.List;

/** Immediate direct return using predicted arrival working stores; no purchases, transfers or dwell are assumed. */
public final class FleetReturnReserve {
    public record ShipReserve(String shipId, double propellantKg, double electricalFuelKg, double bulkSupplyKg) {}
    public record Preview(List<ShipReserve> destination, boolean ready, double returnDays, String explanation) {
        public Preview { destination = List.copyOf(destination); }
    }
    private FleetReturnReserve() {}

    public static Preview preview(GameState state, Fleet departed, String destinationId, FleetPropulsionSupply.Forecast outbound) {
        if (outbound == null || outbound.arrival() == null || !ShipPowerForecast.ready(outbound.electrical()))
            return new Preview(List.of(), false, 0, "Return reserve unavailable until the outbound journey is ready.");
        Fleet arrival = outbound.arrival();
        var reserves = arrival.ships().stream().map(ship -> new ShipReserve(ship.id(), ship.currentFuelKg(),
                ship.generatorFuelMassKg(), ship.supplyFuelMassKg())).toList();
        var returned = new Fleet(arrival.id(), arrival.name(), arrival.ownerEntityId(), destinationId, "", 0, 0, 0,
                false, arrival.fleetStance(), arrival.ships().stream().map(ship -> ship.withSupplyState(ship.supplyState()
                .withOrder(null, ship.supplyState().outcome()).withTimeline(null))).toList(), FleetLocation.at(FleetLocation.Site.deepSpace())).withFuelPolicy(arrival.fuelPolicy());
        if (state.solarSystems().stream().noneMatch(system -> system.id().equals(destinationId)))
            return new Preview(reserves, false, 0, "The return destination is unknown.");
        var plan = InterstellarTravel.plan(state, returned, departed.currentSystemId());
        if (plan == null) return new Preview(reserves, false, 0, "Arrival working propellant or carried drive reactor feed cannot fund a return trajectory.");
        var power = ShipPowerForecast.departure(state, returned, FleetLocation.Site.deepSpace(), null, plan);
        boolean ready = ShipPowerForecast.ready(power);
        return new Preview(reserves, ready, Math.ceil(plan.days()), ready
                ? "Immediate return is funded by arrival working stores, including 48 hours of electrical arrival reserve."
                : "Arrival electrical stores cannot fund the return burn, journey and 48-hour arrival reserve.");
    }
}
