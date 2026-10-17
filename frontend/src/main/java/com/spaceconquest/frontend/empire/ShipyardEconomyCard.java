package com.spaceconquest.frontend.empire;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.economy.EconomicOwnership;
import com.spaceconquest.engine.ship.ShipyardDiagnostics;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import java.util.*;

/** Bounded read-only construction and payment history from the injected snapshot. */
public final class ShipyardEconomyCard {
    private ShipyardEconomyCard() {}
    public static List<String> rows(GameState state, String empireId) {
        if (state == null) return List.of("No shipyard data available.");
        var rows = new ArrayList<String>();
        var orders = ShipyardDiagnostics.orders(state, empireId);
        rows.add("Shipyard queue: " + orders.size() + " orders");
        orders.stream().limit(10).forEach(order -> rows.add(String.format(Locale.ROOT, "%s at %s — %.0f%% complete; provider %s; fees paid %.1f credits. %s",
                order.customer(), order.yard(), order.progressPercent(), order.provider(), order.paidFees(), order.status())));
        rows.add("Recent work and payments");
        state.shipyardLedger().history().reversed().stream().filter(receipt -> {
            var provider = EconomicOwnership.empireFor(state, receipt.providerId()); var customer = EconomicOwnership.empireFor(state, receipt.customerId());
            return provider != null && empireId.equals(provider.id()) || customer != null && empireId.equals(customer.id());
        }).limit(12).forEach(receipt -> rows.add(String.format(Locale.ROOT, "Day %d · %s · %s%s: %.1f work, %.1f fees earned, %.1f paid wages. %s",
                receipt.turn(), receipt.yardId(), ShipyardDiagnostics.name(state, receipt.providerId()),
                receipt.customerId().isBlank() ? "" : " for " + ShipyardDiagnostics.name(state, receipt.customerId()), receipt.workUnits(), receipt.feeCredits(), receipt.wageCostsCredits(), receipt.status())));
        if (rows.size() == 2) rows.add("No shipyard activity recorded.");
        state.shipyardLedger().deployments().entrySet().stream().filter(entry -> {
            var home = EconomicOwnership.empireFor(state, entry.getValue().ownerId()); return home != null && empireId.equals(home.id());
        }).sorted(java.util.Map.Entry.comparingByKey()).limit(8).forEach(entry -> rows.add("Pending deployment: " + entry.getKey() + " - "
                + entry.getValue().kind().name().toLowerCase(Locale.ROOT) + " at " + entry.getValue().targetId() + ". " + entry.getValue().status()));
        state.corporateTaxAccounts().stream().filter(account -> {
            var home = EconomicOwnership.empireFor(state, account.corporationId());
            return home != null && empireId.equals(home.id()) && state.shipyardLedger().cumulativeProfit().containsKey(account.corporationId());
        }).limit(6).forEach(account -> rows.add(String.format(Locale.ROOT, "%s · profit tax assessed %.1f, paid %.1f; carried losses %.1f credits",
                ShipyardDiagnostics.name(state, account.corporationId()), account.assessedTaxCredits(), account.paidTaxCredits(), account.lossCarryforwardCredits())));
        return List.copyOf(rows);
    }
    public static VBox create(GameState state, String empireId) {
        var card = new VBox(8); card.setStyle("-fx-background-color: #1a2744; -fx-padding: 14; -fx-background-radius: 8;");
        var title = new Label("Shipyard services and construction"); title.setTextFill(Color.GOLD); card.getChildren().add(title);
        rows(state, empireId).forEach(text -> { var label = new Label(text); label.setWrapText(true); label.setTextFill(Color.LIGHTCYAN); card.getChildren().add(label); });
        return card;
    }
}
