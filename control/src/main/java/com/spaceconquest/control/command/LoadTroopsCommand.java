package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.habitation.PassengerTransitProcessor;

/** Boards available public soldiers onto an owned troop transport for a declared-war invasion. */
public record LoadTroopsCommand(String fleetId, String shipId, String raceId,
                                int troopCount, String destinationPlanetId) implements GameCommand {
    @Override
    public boolean validate(GameState state) {
        return PassengerTransitProcessor.canBoardTroops(state, fleetId, shipId,
                raceId, troopCount, destinationPlanetId);
    }

    @Override
    public GameState apply(GameState state) {
        return validate(state) ? PassengerTransitProcessor.boardTroops(state, fleetId, shipId,
                raceId, troopCount, destinationPlanetId) : state;
    }
}
