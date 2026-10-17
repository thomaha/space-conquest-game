package com.spaceconquest.control.command;

import com.spaceconquest.control.ai.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.BusinessCash;
import java.util.*;

/** Rechecks the preferred supplier expansion and transfers only its additional operating cash. */
public record RequestAISupplyExpansionCommand(String empireId, String facilityId, int targetTier) implements GameCommand {
    private SupplyExpansionQuote quote(GameState state) {
        if (state == null || empireId == null || facilityId == null) return null;
        var empire = state.empires().stream().filter(e -> e.id().equals(empireId)).findFirst().orElse(null);
        var quote = empire == null ? null : SupplyChainInvestmentPlanner.expansion(state, empire);
        return quote != null && facilityId.equals(quote.command().facilityId()) && targetTier == quote.command().targetTier() ? quote : null;
    }
    @Override public boolean validate(GameState state) { return quote(state) != null; }
    @Override public GameState apply(GameState state) {
        var quote = quote(state); if (quote == null) return state;
        var built = quote.command().apply(state);
        var account = built.industryAccounts().stream().filter(a -> a.facilityId().equals(facilityId)).findFirst()
                .orElse(com.spaceconquest.engine.industry.IndustryAccount.empty(facilityId));
        double extra = Math.max(0, quote.operatingCapitalCredits() - account.operatingCashCredits());
        built = BusinessCash.change(built, empireId, -extra);
        var accounts = new ArrayList<>(built.industryAccounts()); accounts.removeIf(a -> a.facilityId().equals(facilityId));
        accounts.add(account.withOperatingCash(account.operatingCashCredits() + extra));
        return built.toBuilder().industryAccounts(accounts).build();
    }
}
