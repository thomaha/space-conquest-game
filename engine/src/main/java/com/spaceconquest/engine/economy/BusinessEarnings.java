package com.spaceconquest.engine.economy;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.*;
import java.util.stream.Collectors;

/** Shared realized operating receipts before profit tax and capital transfers. */
public final class BusinessEarnings {
    private BusinessEarnings() {}
    public static double result(GameState state, String owner) {
        var ids = state.industrialFacilities().stream().filter(f -> f.ownerEntityId().equals(owner)
                && !IndustrialFacility.HIVE_GRID.equals(f.ownershipType())).map(IndustrialFacility::id).collect(Collectors.toSet());
        double result = state.industryAccounts().stream().filter(a -> ids.contains(a.facilityId()))
                .mapToDouble(IndustryAccount::realizedResultCredits).sum();
        result += state.tradeRoutes().stream().filter(r -> owner.equals(r.ownerEntityId())).mapToDouble(r -> r.dailyOperatingResultCredits()).sum();
        result += state.launchActivities().stream().filter(a -> owner.equals(a.providerOwnerId())
                && !ids.contains(a.providerId())
                && state.spaceElevators().stream().anyMatch(e -> e.id().equals(a.providerId())))
                .mapToDouble(a -> a.feeCredits() - a.powerCostCredits()).sum();
        double marker = state.corporateTaxAccounts().stream().filter(a -> a.corporationId().equals(owner))
                .mapToDouble(CorporateTaxAccount::settledShipyardProfitCredits).findFirst()
                .orElse(state.businessLedger().account(owner).settledShipyardProfitCredits());
        result += state.shipyardLedger().cumulativeProfit().getOrDefault(owner, 0.0) - marker;
        return result - state.businessLedger().account(owner).externalServicesPaidToday();
    }
}
