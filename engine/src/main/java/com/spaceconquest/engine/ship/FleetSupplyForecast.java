package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import java.util.List;

/** Departure readiness includes explicitly reserved deliveries at analytic coasting events. */
public final class FleetSupplyForecast {
    private FleetSupplyForecast() {}
    public static List<ShipPowerForecast.Readiness> departure(GameState state, Fleet fleet, LocalTravel.Plan local, InterstellarTravel.Plan crossing) {
        if (local != null || crossing == null || !Fleet.MODE_SUBLIGHT.equals(crossing.mode()))
            return List.of(new ShipPowerForecast.Readiness("fleet", true, false, 0, 0, 0, 0,
                    "Scheduled supply requires direct sublight departure from system space; cancel it for local travel or warp."));
        if (fleet.ships().stream().anyMatch(ship -> {
            var design = FleetSupplySimulation.design(state, ship);
            var drive = design == null ? null : PropulsionCatalog.mainDrive(design.equippedModuleIds());
            return drive == null || !crossing.propulsion().containsKey(ship.id());
        })) return List.of(new ShipPowerForecast.Readiness("fleet", true, false, 0, 0, 0, 0,
                "Scheduled electrical supply requires saved propulsion data for every main drive."));
        return FleetPropulsionSupply.forecast(state, fleet, crossing).electrical();
    }
}
