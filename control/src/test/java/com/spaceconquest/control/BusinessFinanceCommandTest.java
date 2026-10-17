package com.spaceconquest.control;

import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.*;
import com.spaceconquest.engine.market.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BusinessFinanceCommandTest {
    private GameState funded() throws Exception {
        var state = new AIFacilityLogisticsTest().world(); var hub = state.commercialHubs().getLast();
        var orders = new HashMap<>(hub.activeOrders()); var steel = orders.get("steel");
        orders.put("steel", new MarketOrder("steel", 12, steel.demandKg(), steel.pricePerKg(), 1));
        return state.withCommercialHubs(List.of(state.commercialHubs().getFirst(), hub.withInventory(state.turn(), hub.currentStoredWeightKg() + 12, orders)));
    }
    @Test void queuedContractHoldsBuyerCashOnceAndCancellationRefundsItWithoutGoods() throws Exception {
        var state = funded(); var create = new CreateSupplyContractCommand("owner", "order", "factory", "destination", "cannon_round", 1, 10000, state.turn() + 30);
        assertTrue(create.validate(state));
        assertFalse(new CreateSupplyContractCommand("foreign", "order", "factory", "destination", "cannon_round", 1, 10000, state.turn() + 30).validate(state));
        var queue = new CommandQueue(); queue.submit(create); queue.submit(create);
        var held = queue.drainAndExecute(state);
        assertEquals(1, held.businessLedger().contracts().size()); assertEquals(10000, held.businessLedger().escrowCredits());
        assertEquals(state.commercialHubs(), held.commercialHubs()); assertEquals(state.empires(), held.empires());
        assertEquals(state.marketAccounts().stream().mapToDouble(MarketAccount::unsettledSalesCredits).sum(), held.businessLedger().escrowCredits()
                + held.marketAccounts().stream().mapToDouble(MarketAccount::unsettledSalesCredits).sum());
        assertFalse(new CancelSupplyContractCommand("foreign", "order").validate(held));
        queue.submit(new CancelSupplyContractCommand("owner", "order")); queue.submit(new CancelSupplyContractCommand("owner", "order"));
        var cancelled = queue.drainAndExecute(held); assertEquals(state.marketAccounts(), cancelled.marketAccounts());
        assertEquals(state.commercialHubs(), cancelled.commercialHubs()); assertEquals(0, cancelled.businessLedger().escrowCredits());
    }
    @Test void reserveReleaseIsAnOwnedCashTransferRatherThanIncomeOrMoneyCreation() throws Exception {
        var state = funded(); var book = BusinessAccount.empty().withReserve(100);
        state = state.toBuilder().businessLedger(state.businessLedger().withAccount("owner", book)).build();
        var release = new ReleaseReplacementReserveCommand("owner", "owner", 60);
        assertFalse(new ReleaseReplacementReserveCommand("foreign", "owner", 60).validate(state));
        assertFalse(new ReleaseReplacementReserveCommand("owner", "owner", 101).validate(state));
        var end = release.apply(state);
        assertEquals(state.empires().getFirst().treasuryCredits() + 60, end.empires().getFirst().treasuryCredits());
        assertEquals(40, end.businessLedger().account("owner").replacementReserveCredits());
        assertEquals(0, end.businessLedger().account("owner").cumulativeOperatingResultCredits());
    }
    @Test void committedCarrierServicesDebitRealOwnerCashAndCreditTheServiceAccount() throws Exception {
        var state = funded(); var fleet = state.fleets().getFirst();
        var route = new com.spaceconquest.engine.logistics.TradeRoute("service", "Service", "owner", "source", "destination", "steel", 1, 0, 10,
                List.of(fleet.ships().getFirst().id()), 0, true);
        state = state.withTradeRoutes(List.of(route));
        double value = BusinessAssets.shipValue(state, fleet, fleet.ships().getFirst());
        double expected = com.spaceconquest.engine.market.SupplyPricingPolicy.current().transportOperatingCreditsPerDay()
                + value * com.spaceconquest.engine.market.SupplyPricingPolicy.current().transportMaintenanceDailyFraction();
        var result = BusinessServices.settle(state).state();
        assertEquals(expected, state.empires().getFirst().treasuryCredits() - result.empires().getFirst().treasuryCredits(), 1e-8);
        assertEquals(expected, result.businessLedger().account("owner").externalServicesPaidToday(), 1e-8);
        assertTrue(result.businessLedger().serviceClearingCredits() >= expected);
    }
}
