package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;

/** Reserved compatibility command until physical salvage ownership and consumption exist. */
public record ReverseEngineerSalvageCommand(String empireId, String targetTechOrAppId, String sourceRaceId,
                                          boolean isIntact, int componentCount) implements GameCommand {
    public ReverseEngineerSalvageCommand(String empireId, String salvageDebrisId, double salvageQuality, String targetTechId) {
        this(empireId, targetTechId, null, salvageQuality >= .5, 1);
    }
    @Override public boolean validate(GameState state) { return false; }
    @Override public GameState apply(GameState state) { return state; }
}
