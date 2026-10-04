package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.FleetOrganization;
import com.spaceconquest.engine.ship.ShipInstance;
import java.util.List;

/** The destination keeps its identity, name and stance; the emptied source fleet is removed. */
public record MergeFleetsCommand(String ownerEntityId, String sourceFleetId, String targetFleetId) implements GameCommand {
    private List<String> ships(GameState state) {
        var fleet = state == null ? null : FleetOrganization.find(state, sourceFleetId);
        return fleet == null ? List.of() : fleet.ships().stream().map(ShipInstance::id).toList();
    }
    public String problem(GameState state) { return FleetOrganization.transferProblem(state, ownerEntityId, sourceFleetId, targetFleetId, ships(state)); }
    @Override public boolean validate(GameState state) { return problem(state) == null; }
    @Override public GameState apply(GameState state) {
        return FleetOrganization.transfer(state, ownerEntityId, sourceFleetId, targetFleetId, ships(state));
    }
}
