package com.spaceconquest.frontend.empire;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.CorporateTaxAccount;
import com.spaceconquest.engine.ship.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShipyardEconomyCardTest {
    private GameState state() {
        var owner = new Empire("owner", "Home", "human", "Individualist", 10000, .2, List.of("a"), List.of(), Map.of(), List.of(), List.of());
        var foreign = new Empire("foreign", "Foreign", "human", "Individualist", 10000, .2, List.of("b"), List.of(), Map.of(), List.of(), List.of());
        var company = new Corporation("corp", "Yard company", "owner", "yard", "METALLURGY", 10000, List.of(), List.of(), List.of());
        var receipt = new ShipyardReceipt(1, "order", "owner", "corp", "a", "yard", 20, 40, 10, true, "Commissioned");
        var outsiders = new ShipyardReceipt(1, "other-order", "foreign", "foreign", "b", "secret-yard", 10, 100, 0, true, "Commissioned");
        return GameState.builder().empires(List.of(owner, foreign)).corporations(List.of(company))
                .shipyardLedger(ShipyardLedger.empty().append(receipt).append(outsiders))
                .corporateTaxAccounts(List.of(new CorporateTaxAccount("corp", 0, 0, 30, 6, 6, 30))).build();
    }
    @Test void completedPaymentsRemainVisibleWithTaxAndForeignHistoryIsFiltered() {
        var state = state(); var before = state.shipyardLedger(); var text = String.join("\n", ShipyardEconomyCard.rows(state, "owner"));
        assertTrue(text.contains("Yard company")); assertTrue(text.contains("40.0 fees earned")); assertTrue(text.contains("Commissioned")); assertTrue(text.contains("tax assessed 6.0"));
        assertFalse(text.contains("secret-yard")); assertEquals(before, state.shipyardLedger()); assertTrue(state.shipConstructionOrders().isEmpty());
    }
    @Test void aBlockedQueueShowsProgressFeesAndWorkforceWithoutMutation() {
        var state = state().withShipConstructionOrders(List.of(new ShipConstructionOrder("pending", "corp", "design", "a", "yard", 25, 100,
                Map.of("steel", 100.0), Map.of("steel", 25.0), new ShipyardContract("owner", 2, 50))));
        var text = String.join("\n", ShipyardEconomyCard.rows(state, "owner"));
        assertTrue(text.contains("25% complete")); assertTrue(text.contains("fees paid 50.0")); assertTrue(text.contains("workforce"));
        assertEquals(25, state.shipConstructionOrders().getFirst().accumulatedWorkHours());
    }
    @Test void pendingDeploymentUsesOnlyItsSavedSnapshotAndFiltersForeignIntents() {
        var state = state(); var ledger = state.shipyardLedger().deployment("new-ship", new ShipDeploymentIntent("corp", ShipDeploymentIntent.Kind.SUPPLY, "destination", "steel", 1, "Waiting for funded supply departure"))
                .deployment("foreign-ship", new ShipDeploymentIntent("foreign", ShipDeploymentIntent.Kind.DEFENSE, "secret-site", "", 1, "Preparing"));
        state = state.toBuilder().shipyardLedger(ledger).build(); var text = String.join("\n", ShipyardEconomyCard.rows(state, "owner"));
        assertTrue(text.contains("Pending deployment: new-ship")); assertTrue(text.contains("Waiting for funded supply departure"));
        assertFalse(text.contains("secret-site")); assertEquals(ledger, state.shipyardLedger());
    }
}
