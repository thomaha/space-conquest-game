package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.*;

/** Detaches surviving escort ships through the ordinary paid-itinerary-preserving split. */
public record CancelEscortFleetCommand(String ownerEntityId, String convoyFleetId) implements GameCommand {
    private GameState prepared(GameState state) {
        if (state == null) return null;
        var fleet = FleetOrganization.find(state, convoyFleetId);
        if (fleet == null || fleet.escortOrder() != null || fleet.interceptOrder() == null
                || fleet.interceptOrder().protection() == null || fleet.interceptOrder().protection().repairIntent() == null) return state;
        var escort = fleet.interceptOrder().protection().repairIntent().escort();
        return escort == null ? state : state.withFleets(state.fleets().stream().map(item -> convoyFleetId.equals(item.id())
                ? item.withInterceptOrder(null).withEscortOrder(escort) : item).toList());
    }
    private SplitFleetCommand split(GameState state) {
        if (state == null || ownerEntityId == null || convoyFleetId == null) return null;
        state = prepared(state);
        var fleet = FleetOrganization.find(state, convoyFleetId);
        var order = fleet == null ? null : fleet.escortOrder();
        if (order == null || !ownerEntityId.equals(fleet.ownerEntityId())) return null;
        return new SplitFleetCommand(ownerEntityId, convoyFleetId, order.shipIds(), order.escortFleetId(), order.escortFleetName());
    }

    @Override public boolean validate(GameState state) {
        var split = split(state);
        return split != null && split.validate(prepared(state));
    }

    @Override public GameState apply(GameState state) {
        if (!validate(state)) return state;
        state = prepared(state);
        var order = FleetOrganization.find(state, convoyFleetId).escortOrder();
        boolean restoreStance = "ESCORT".equals(FleetOrganization.find(state, convoyFleetId).fleetStance());
        var separated = split(state).apply(state);
        separated = separated.withFleets(separated.fleets().stream().map(fleet -> convoyFleetId.equals(fleet.id())
                || order.escortFleetId().equals(fleet.id()) ? fleet.withEscortOrder(null) : fleet).toList());
        separated = new SetFleetStanceCommand(order.escortFleetId(), "PASSIVE").apply(separated);
        return restoreStance
                ? new SetFleetStanceCommand(convoyFleetId, order.previousStance()).apply(separated) : separated;
    }
}
