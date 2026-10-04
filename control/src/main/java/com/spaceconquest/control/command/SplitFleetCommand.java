package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.FleetOrganization;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Removes a selected proper subset into a named fleet at the same position and progress. */
public record SplitFleetCommand(String ownerEntityId, String sourceFleetId, List<String> shipIds,
                                String newFleetId, String newFleetName) implements GameCommand {
    public SplitFleetCommand { shipIds = shipIds == null ? null : Collections.unmodifiableList(new ArrayList<>(shipIds)); }
    public String problem(GameState state) { return FleetOrganization.splitProblem(state, ownerEntityId, sourceFleetId, shipIds, newFleetId, newFleetName); }
    @Override public boolean validate(GameState state) { return problem(state) == null; }
    @Override public GameState apply(GameState state) { return FleetOrganization.split(state, ownerEntityId, sourceFleetId, shipIds, newFleetId, newFleetName); }
}
