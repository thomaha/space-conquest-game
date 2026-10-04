package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.FleetOrganization;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Adds selected ships to another fleet without altering their physical state. */
public record TransferFleetShipsCommand(String ownerEntityId, String sourceFleetId, String targetFleetId,
                                       List<String> shipIds) implements GameCommand {
    public TransferFleetShipsCommand { shipIds = shipIds == null ? null : Collections.unmodifiableList(new ArrayList<>(shipIds)); }
    public String problem(GameState state) { return FleetOrganization.transferProblem(state, ownerEntityId, sourceFleetId, targetFleetId, shipIds); }
    @Override public boolean validate(GameState state) { return problem(state) == null; }
    @Override public GameState apply(GameState state) { return FleetOrganization.transfer(state, ownerEntityId, sourceFleetId, targetFleetId, shipIds); }
}
