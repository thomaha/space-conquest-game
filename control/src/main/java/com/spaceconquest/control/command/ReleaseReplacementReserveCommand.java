package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.economy.*;

/** Explicit owner authorization to return earmarked capital to spendable cash; this is not income. */
public record ReleaseReplacementReserveCommand(String empireId, String ownerId, double credits) implements GameCommand {
    @Override public boolean validate(GameState state) {
        if (state == null || !Double.isFinite(credits) || credits <= 0) return false;
        var empire = EconomicOwnership.empireFor(state, ownerId);
        return empire != null && empire.id().equals(empireId)
                && state.businessLedger().account(ownerId).replacementReserveCredits() >= credits;
    }
    @Override public GameState apply(GameState state) {
        if (!validate(state)) return state;
        var account = state.businessLedger().account(ownerId);
        return BusinessCash.change(state, ownerId, credits).toBuilder().businessLedger(state.businessLedger()
                .withAccount(ownerId, account.withReserve(account.replacementReserveCredits() - credits))).build();
    }
}
