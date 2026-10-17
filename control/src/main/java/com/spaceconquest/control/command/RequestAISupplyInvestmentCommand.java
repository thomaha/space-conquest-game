package com.spaceconquest.control.command;

import com.spaceconquest.control.ai.*;
import com.spaceconquest.engine.*;
import java.util.*;

/** Requotes dependency need, physical resources and cash at execution on the simulation tick. */
public record RequestAISupplyInvestmentCommand(String empireId, String bodyId, String applicationId) implements GameCommand {
    private SupplyInvestmentQuote quote(GameState state) {
        if (state == null || empireId == null || bodyId == null || applicationId == null) return null;
        var empire = state.empires().stream().filter(e -> e.id().equals(empireId)).findFirst().orElse(null);
        if (empire == null) return null;
        var quote = SupplyChainInvestmentPlanner.next(state, empire);
        return quote != null && bodyId.equals(quote.command().planetId()) && applicationId.equals(quote.command().applicationId()) ? quote : null;
    }
    @Override public boolean validate(GameState state) { return quote(state) != null; }
    @Override public GameState apply(GameState state) {
        var quote = quote(state);
        if (quote == null) return state;
        var built = quote.command().apply(state);
        var facility = built.industrialFacilities().stream().filter(f -> state.industrialFacilities().stream().noneMatch(old -> old.id().equals(f.id()))).findFirst().orElseThrow();
        var account = built.industryAccounts().stream().filter(a -> a.facilityId().equals(facility.id())).findFirst().orElseThrow();
        double extra = Math.max(0, quote.operatingCapitalCredits() - account.operatingCashCredits());
        built = com.spaceconquest.engine.economy.BusinessCash.change(built, empireId, -extra);
        var accounts = new ArrayList<>(built.industryAccounts()); accounts.removeIf(a -> a.facilityId().equals(facility.id()));
        accounts.add(account.withOperatingCash(account.operatingCashCredits() + extra));
        return built.toBuilder().industryAccounts(accounts).build();
    }
}
