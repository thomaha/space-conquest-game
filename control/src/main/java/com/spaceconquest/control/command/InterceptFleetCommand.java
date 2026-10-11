package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.*;

/** Stages an explicit pursuit of a live detected wartime contact. */
public record InterceptFleetCommand(String ownerEntityId, String fleetId, String targetFleetId) implements GameCommand {
    @Override public boolean validate(GameState state) {
        if (state == null || ownerEntityId == null || fleetId == null || targetFleetId == null) return false;
        var fleet = FleetOrganization.find(state, fleetId);
        return fleet != null && ownerEntityId.equals(fleet.ownerEntityId())
                && (!fleet.location().inTransit() || BodyFleetRendezvous.ballisticAvailable(fleet)) && !fleet.hasInterstellarOrder() && !fleet.isInWarp()
                && fleet.flightMotion() == null && fleet.interstellarMode().isBlank()
                && fleet.location().current().kind() != FleetLocation.Kind.SURFACE
                && FleetInterceptProcessor.militaryAvailable(state, fleet)
                && FleetInterceptProcessor.contact(state, fleet, FleetOrganization.find(state, targetFleetId));
    }

    @Override public GameState apply(GameState state) {
        if (!validate(state)) return state;
        return state.withFleets(state.fleets().stream().map(fleet -> fleetId.equals(fleet.id())
                ? fleet.withInterceptOrder(new FleetInterceptOrder(ownerEntityId, targetFleetId, fleet.currentSystemId()))
                : fleet).toList());
    }
}
