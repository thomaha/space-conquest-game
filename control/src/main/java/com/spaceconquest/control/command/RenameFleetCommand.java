package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.FleetOrganization;

public record RenameFleetCommand(String ownerEntityId, String fleetId, String name) implements GameCommand {
    public String problem(GameState state) { return FleetOrganization.renameProblem(state, ownerEntityId, fleetId, name); }
    @Override public boolean validate(GameState state) { return problem(state) == null; }
    @Override public GameState apply(GameState state) { return FleetOrganization.rename(state, ownerEntityId, fleetId, name); }
}
