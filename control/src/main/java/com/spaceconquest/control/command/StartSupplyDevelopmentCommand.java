package com.spaceconquest.control.command;

import com.spaceconquest.control.ai.CrossWorldSupplyPlanner;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.*;
import com.spaceconquest.engine.logistics.*;
import java.util.*;

/** Requotes source selection, then buys source-market purchasing power with real treasury money once. */
public record StartSupplyDevelopmentCommand(String empireId, String sourceHubId, String destinationHubId, String materialId) implements GameCommand {
    private CrossWorldSupplyPlanner.Candidate quote(GameState state) {
        if (state == null || empireId == null) return null;
        var owner = state.empires().stream().filter(e -> e.id().equals(empireId)).findFirst().orElse(null);
        var q = owner == null ? null : CrossWorldSupplyPlanner.next(state, owner);
        return q != null && q.sourceHubId().equals(sourceHubId) && q.destinationHubId().equals(destinationHubId) && q.materialId().equals(materialId) ? q : null;
    }
    @Override public boolean validate(GameState state) { return quote(state) != null; }
    @Override public GameState apply(GameState state) {
        var q = quote(state); if (q == null) return state;
        var owner = state.empires().stream().filter(e -> e.id().equals(empireId)).findFirst().orElseThrow();
        String fleet = state.fleets().stream().filter(f -> empireId.equals(f.ownerEntityId())
                && com.spaceconquest.control.ai.FacilitySupplyShipment.available(state, owner, f))
                .sorted(Comparator.comparing(com.spaceconquest.engine.ship.Fleet::id)).map(com.spaceconquest.engine.ship.Fleet::id).findFirst().orElse("");
        var plans = new ArrayList<>(state.supplyDevelopmentPlans());
        while (plans.size() >= 32) {
            var oldest = plans.stream().filter(SupplyDevelopmentPlan::closed).findFirst().orElse(null);
            if (oldest == null) break; plans.remove(oldest);
        }
        plans.add(new SupplyDevelopmentPlan(UUID.randomUUID().toString(), empireId, sourceHubId, destinationHubId, materialId,
                q.quantityKg(), q.buyerFundingCredits(), state.turn(), fleet, false));
        var accounts = new ArrayList<>(state.marketAccounts());
        double old = accounts.stream().filter(a -> a.hubId().equals(sourceHubId)).mapToDouble(MarketAccount::unsettledSalesCredits).findFirst().orElse(0);
        accounts.removeIf(a -> a.hubId().equals(sourceHubId)); accounts.add(new MarketAccount(sourceHubId, old + q.buyerFundingCredits()));
        return SupplyDevelopmentDemand.refresh(BusinessCash.change(state, empireId, -q.buyerFundingCredits()).toBuilder()
                .supplyDevelopmentPlans(plans).marketAccounts(accounts).build(), state);
    }
}
