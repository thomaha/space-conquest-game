package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.*;

/** Carried dwell insurance for port departures with supply shortages or longer market exposure. */
final class TradeArrivalReadiness {
    private TradeArrivalReadiness() {}

    static TradeLegReadiness.Report inspect(GameState state, Fleet departure, Fleet arrival,
                                             CommercialHub destination, double journeyDays) {
        // Reassess at ports only. A shortage discovered underway must not prevent the funded approach.
        if (state.commercialHubs().stream().noneMatch(hub -> FleetPositioning.atHub(state, departure, hub))
                || journeyDays <= ShipPowerProcessor.ARRIVAL_RESERVE_HOURS / 24
                && FleetPortReadiness.inspect(state, arrival, destination).available())
            return new TradeLegReadiness.Report(true, "Next port is reachable with an electrical arrival reserve.");
        var site = FleetPositioning.hubSite(state, destination);
        var environment = ShipArrivalReserve.environment(state, arrival, site, false);
        for (var ship : arrival.ships()) {
            var design = FleetSupplySimulation.design(state, ship);
            if (design == null) return new TradeLegReadiness.Report(false, "Waiting: unknown arrival equipment.");
            var profile = design.powerProfile();
            if (profile == null) continue; // Preserve explicit legacy electrical compatibility.
            var reserve = ShipArrivalReserve.check(profile, ShipPowerProcessor.reserves(ship),
                    profile.essentialKw(ship, design), ShipPowerProcessor.cargoKw(profile, ship, design),
                    environment, TradePortMaintenance.DWELL_RESERVE_HOURS);
            if (!reserve.ready()) return new TradeLegReadiness.Report(false,
                    "Waiting: destination shortages or a journey over two days require carried electricity for a 60-day port wait.");
        }
        return new TradeLegReadiness.Report(true,
                "Next port is reachable with carried electricity for a 60-day wait.");
    }
}
