package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.governance.PlanetaryInvasionProcessor;

/** Launches a ground invasion using troops aboard an arrived transport ship. */
public record InvadePlanetCommand(String attackerEmpireId, String targetSystemId,
                                  String targetPlanetId, String fleetId, String shipId) implements GameCommand {
    private static final PlanetaryInvasionProcessor PROCESSOR = new PlanetaryInvasionProcessor();

    @Override
    public boolean validate(GameState state) {
        return PROCESSOR.canInvade(state, attackerEmpireId, targetSystemId,
                targetPlanetId, fleetId, shipId);
    }

    @Override
    public GameState apply(GameState state) {
        return validate(state) ? PROCESSOR.resolve(state, attackerEmpireId, targetSystemId,
                targetPlanetId, fleetId, shipId) : state;
    }
}
