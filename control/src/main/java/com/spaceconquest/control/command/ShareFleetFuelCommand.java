package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.FleetFuelSharing;
import java.util.List;

/** Executes only the reviewed allocation after rebuilding it against live tick state. */
public record ShareFleetFuelCommand(FleetFuelSharing.Request request,
                                   List<FleetFuelSharing.Transfer> approvedTransfers) implements GameCommand {
    public ShareFleetFuelCommand { approvedTransfers = approvedTransfers == null ? List.of() : List.copyOf(approvedTransfers); }
    @Override public boolean validate(GameState state) { return apply(state) != state; }
    @Override public GameState apply(GameState state) { return FleetFuelSharing.apply(state, request, approvedTransfers); }
}
