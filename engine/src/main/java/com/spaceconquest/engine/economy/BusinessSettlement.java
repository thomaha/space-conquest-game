package com.spaceconquest.engine.economy;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.market.SupplyPricingPolicy;
import java.util.*;

/** Tick-owned service payment, ordinary tax assessment and after-tax replacement funding, in that order. */
public final class BusinessSettlement {
    public record Result(GameState state, Map<String, Double> collectedByBody) {}
    private BusinessSettlement() {}
    public static Result closeDay(GameState state) {
        if (state.businessLedger().lastClosedTurn() >= state.turn()) return new Result(state, Map.of());
        var service = BusinessServices.settle(state); state = service.state();
        var earnings = new HashMap<String, Double>();
        for (String owner : state.businessLedger().accounts().keySet()) earnings.put(owner, BusinessEarnings.result(state, owner));
        var tax = new CorporateProfitTaxProcessor().process(state);
        state = state.toBuilder().corporations(tax.corporations()).corporateTaxAccounts(tax.accounts()).build();
        var books = new HashMap<>(state.businessLedger().accounts());
        for (String owner : new TreeSet<>(books.keySet())) {
            var current = state;
            var facilities = state.industrialFacilities().stream().filter(f -> f.ownerEntityId().equals(owner)
                    && !IndustrialFacility.HIVE_GRID.equals(f.ownershipType())).toList();
            var ids = facilities.stream().map(IndustrialFacility::id).collect(java.util.stream.Collectors.toSet());
            double result = earnings.getOrDefault(owner, 0.0);
            double paidTax = tax.accounts().stream().filter(a -> a.corporationId().equals(owner)).mapToDouble(CorporateTaxAccount::paidTaxCredits).sum();
            double value = BusinessAssets.ownerValue(state, owner);
            double fundingTarget = Math.min(value, books.get(owner).replacementFundingTargetCredits() + value / SupplyPricingPolicy.current().capitalRecoveryDays());
            double target = Math.max(0, fundingTarget - books.get(owner).replacementReserveCredits());
            boolean indebted = current.businessLedger().unpaidExpenses().keySet().stream().anyMatch(key -> key.startsWith(BusinessServices.ownerPrefix(owner)));
            double reserve = indebted ? 0 : Math.min(target, Math.max(0, result - paidTax));
            boolean privateOwner = state.corporations().stream().anyMatch(c -> c.id().equals(owner));
            if (privateOwner) {
                reserve = Math.min(reserve, Math.max(0, EconomicOwnership.cash(state, owner)));
                state = BusinessCash.change(state, owner, -reserve);
            } else {
                double remaining = reserve;
                var accounts = new ArrayList<IndustryAccount>();
                for (var account : state.industryAccounts()) {
                    double contribution = ids.contains(account.facilityId()) ? Math.min(remaining,
                            Math.min(Math.max(0, account.operatingCashCredits()), Math.max(0, account.realizedResultCredits()))) : 0;
                    accounts.add(account.withOperatingCash(account.operatingCashCredits() - contribution)); remaining -= contribution;
                }
                double treasuryPart = Math.min(remaining, Math.max(0, EconomicOwnership.cash(state, owner)));
                // Treasury cash funds only the owner's actual profitable carrier activity.
                double shippingProfit = Math.max(0, state.tradeRoutes().stream().filter(r -> r.ownerEntityId().equals(owner))
                        .mapToDouble(r -> r.dailyOperatingResultCredits()).sum() - books.get(owner).externalServicesPaidToday());
                treasuryPart = Math.min(treasuryPart, shippingProfit);
                state = BusinessCash.change(state.toBuilder().industryAccounts(accounts).build(), owner, -treasuryPart);
                reserve = reserve - remaining + treasuryPart;
            }
            books.put(owner, books.get(owner).close(result, paidTax, service.paidByOwner().getOrDefault(owner, 0.0), reserve,
                    state.shipyardLedger().cumulativeProfit().getOrDefault(owner, 0.0), fundingTarget));
        }
        return new Result(state.toBuilder().businessLedger(new BusinessLedger(books, state.businessLedger().unpaidExpenses(),
                state.businessLedger().serviceClearingCredits(), state.businessLedger().contracts(), state.turn(), state.businessLedger().operatingDecisions())).build(), tax.collectedByBody());
    }
}
