package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.market.CorporateInvestmentProcessor;

/** Stages a funded corporate asset purchase for the next simulation tick. */
public record CorporateInvestCommand(
        String corporationId, String entityId, String investmentType, double credits
) implements GameCommand {
    private static final CorporateInvestmentProcessor INVESTMENTS = new CorporateInvestmentProcessor();

    @Override
    public boolean validate(GameState state) {
        return INVESTMENTS.canInvest(state, corporationId, entityId, investmentType, credits);
    }

    @Override
    public GameState apply(GameState state) {
        return INVESTMENTS.invest(state, corporationId, entityId, investmentType, credits);
    }
}
