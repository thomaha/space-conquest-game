package com.spaceconquest.frontend;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.logistics.TradeLegReadiness;
import com.spaceconquest.engine.logistics.TradeRoute;
import com.spaceconquest.engine.ship.FleetPortReadiness;
import javafx.application.Platform;
import javafx.scene.control.Label;
import java.util.concurrent.CompletableFuture;

/** Cosmetic route diagnostics from an injected snapshot, calculated away from the FX thread. */
final class TradeLegStatusCard {
    private TradeLegStatusCard() {}

    static Label create(GameState state, TradeRoute route) {
        var label = new Label("Checking next-leg supplies...");
        label.setTextFill(javafx.scene.paint.Color.LIGHTCYAN);
        label.setWrapText(true);
        CompletableFuture.supplyAsync(() -> status(state, route)).whenComplete((message, failure) ->
                Platform.runLater(() -> label.setText(failure == null ? message : "Supply status unavailable. Refresh the port.")));
        return label;
    }

    static String status(GameState state, TradeRoute route) {
        if (route.roaming() && !route.status().isBlank()) return route.status();
        var fleet = state.fleets().stream().filter(item -> item.ships().stream()
                .anyMatch(ship -> route.assignedFreighterIds().contains(ship.id()))).findFirst().orElse(null);
        if (fleet == null) return "Waiting: no assigned carrier is available.";
        if (fleet.hasInterstellarOrder() || fleet.location().inTransit())
            return "Journey in progress. The next leg will be checked after arrival and trading.";
        String hubId = TradeRoute.RETURNING.equals(route.phase()) ? route.originEntityId() : route.destinationEntityId();
        var hub = state.commercialHubs().stream().filter(item -> item.id().equals(hubId)).findFirst().orElse(null);
        var readiness = TradeLegReadiness.inspect(state, fleet, hub);
        if (!readiness.ready() && hub != null) {
            var supplied = TradeLegReadiness.prepare(state, fleet, hub);
            var prepared = supplied.fleets().stream().filter(item -> item.id().equals(fleet.id())).findFirst().orElseThrow();
            if (TradeLegReadiness.inspect(supplied, prepared, hub).ready())
                return "Next leg can depart after paid local resupply. " + FleetPortReadiness.inspect(state, fleet, hub).explanation();
        }
        return readiness.explanation() + " " + FleetPortReadiness.inspect(state, fleet, hub).explanation();
    }
}
