package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.governance.DiplomacyProcessor;

/** Ends an active bilateral war and returns the relation to neutrality. */
public record EndWarCommand(String empireAId, String empireBId) implements GameCommand {
    @Override
    public boolean validate(GameState state) {
        if (state == null || empireAId == null || empireBId == null || empireAId.equals(empireBId)) {
            return false;
        }
        boolean empireAExists = state.empires().stream().anyMatch(empire -> empireAId.equals(empire.id()));
        boolean empireBExists = state.empires().stream().anyMatch(empire -> empireBId.equals(empire.id()));
        return empireAExists && empireBExists
                && DiplomacyProcessor.TOTAL_WAR.equalsIgnoreCase(
                new DiplomacyProcessor().getDiplomaticTier(empireAId, empireBId, state.diplomaticRelations()));
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) return state;
        return new SetDiplomaticTierCommand(empireAId, empireBId, DiplomacyProcessor.NEUTRAL).apply(state);
    }
}
