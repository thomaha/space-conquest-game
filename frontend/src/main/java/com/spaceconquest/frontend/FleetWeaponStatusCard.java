package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;

/** Snapshot-only weapon diagnostics and paid resupply actions, quoted away from the JavaFX thread. */
final class FleetWeaponStatusCard {
    record Entry(ShipWeaponReadiness readiness, List<AmmunitionResupplyQuote> resupply) {
        Entry { resupply = List.copyOf(resupply); }
    }
    private FleetWeaponStatusCard() {}
    static TitledPane create(GameState state, Fleet fleet, String owner, HumanController controller,
                            Label feedback, Supplier<GameState> current, Set<String> expanded) {
        var box = new VBox(5); var pane = new TitledPane("Weapons and ammunition", box);
        pane.setId("weapon-status-" + fleet.id()); pane.setExpanded(false); pane.setDisable(state == null);
        pane.expandedProperty().addListener((observable, old, open) -> {
            if (!open) { expanded.remove(fleet.id()); box.setUserData(new Object()); return; }
            expanded.add(fleet.id());
            refresh(box, state, current, () -> CompletableFuture.supplyAsync(() -> inspect(state, fleet, owner)),
                    controller == null ? null : controller::stageTrackedCommand, feedback);
        });
        if (state != null) pane.setExpanded(expanded.contains(fleet.id()));
        return pane;
    }
    static List<Entry> inspect(GameState state, Fleet fleet, String owner) {
        if (!fleet.ownerEntityId().equals(owner)) return List.of();
        var entries = new ArrayList<Entry>();
        boolean firing = !com.spaceconquest.engine.combat.TacticalCombatProcessor.STANCE_EVASIVE_RETREAT.equalsIgnoreCase(fleet.fleetStance());
        for (var ship : fleet.ships()) {
            var design = state.shipDesigns().stream().filter(d -> d.id().equals(ship.designId())).findFirst().orElse(null);
            var readiness = ShipWeaponReadiness.inspect(design, ship, firing);
            var quotes = readiness.weapons().stream().filter(w -> !w.ammunitionId().isBlank())
                    .map(w -> AmmunitionResupplyQuote.inspect(state, owner, ship.id(), w.moduleId())).toList();
            entries.add(new Entry(readiness, quotes));
        }
        return List.copyOf(entries);
    }
    static void refresh(VBox box, GameState state, Supplier<GameState> current,
                        Supplier<CompletableFuture<List<Entry>>> calculation,
                        Function<GameCommand, CompletableFuture<CommandOutcome>> submit, Label feedback) {
        var request = new Object(); box.setUserData(request);
        box.getChildren().setAll(line("Calculating weapon readiness and local resupply...", "weapon-loading"));
        calculation.get().whenComplete((entries, failure) -> Platform.runLater(() -> {
            if (box.getUserData() != request || current.get() != state) return;
            if (failure != null) box.getChildren().setAll(line("Weapon status unavailable. Reopen after the next update.", "weapon-error"));
            else render(box, entries, state, current, submit, feedback);
        }));
    }
    static void render(VBox box, List<Entry> entries, GameState state, Supplier<GameState> current,
                       Function<GameCommand, CompletableFuture<CommandOutcome>> submit, Label feedback) {
        box.getChildren().clear();
        for (var entry : entries) {
            var readiness = entry.readiness();
            box.getChildren().add(line("Ship " + readiness.shipId(), "weapon-ship-" + readiness.shipId()));
            if (readiness.weapons().isEmpty()) box.getChildren().add(line("No installed weapons shown. " + readiness.note(), "weapon-empty-" + readiness.shipId()));
            for (var weapon : readiness.weapons()) {
                String key = readiness.shipId() + "-" + weapon.moduleId();
                String mounts = String.join(", ", weapon.mounts().stream().map(m -> String.format(Locale.ROOT,
                        "#%d (%.0f%% condition%s)", m.number(), m.condition() * 100, m.condition() <= 0 ? "; disabled" : "")).toList());
                box.getChildren().add(line(weapon.name() + " | Mounts " + mounts + " | " + weapon.status()
                        + " | " + weapon.fundedShots() + " shots in quoted round", "weapon-ready-" + key));
                if (!weapon.ammunitionId().isBlank()) {
                    box.getChildren().add(line(String.format(Locale.ROOT,
                            "%s: %d whole round%s (%.2f kg each); %.2f kg aboard. Shared by mounts %s; counts use the same cargo.",
                            resource(weapon.ammunitionId()), weapon.wholeRounds(), weapon.wholeRounds() == 1 ? "" : "s", weapon.roundMassKg(), weapon.cargoKg(),
                            weapon.sharedMounts()), "weapon-ammo-" + key));
                    entry.resupply().stream().filter(q -> q.moduleId().equals(weapon.moduleId())).findFirst()
                            .ifPresent(q -> resupply(box, q, key, state, current, submit, feedback));
                }
            }
            if (!readiness.weapons().isEmpty()) box.getChildren().add(line(readiness.note(), "weapon-note-" + readiness.shipId()));
        }
        box.getChildren().add(line("Purchase quotes are independent and use current stock and funds. Queued loading is checked again next tick. "
                + "Use the supplies controls for larger quantities.", "weapon-resupply-note"));
    }
    private static void resupply(VBox box, AmmunitionResupplyQuote quote, String key, GameState state,
                                 Supplier<GameState> current, Function<GameCommand, CompletableFuture<CommandOutcome>> submit, Label feedback) {
        String purchase = quote.purchaseCredits() == null ? "price unavailable" : String.format(Locale.ROOT, "purchase %.2f credits", quote.purchaseCredits());
        String lift = quote.liftCredits() == null ? "lift unquoted" : String.format(Locale.ROOT, "loading %.2f credits", quote.liftCredits());
        box.getChildren().add(line(String.format(Locale.ROOT, "Local hub %s: %.2f kg %s available. One %.2f kg round: %s; %s. %s",
                quote.bodyId().isBlank() ? "unavailable" : quote.bodyId(), quote.localStockKg(), resource(quote.ammunitionId()),
                quote.quantityKg(), purchase, lift, quote.reason()), "weapon-resupply-" + key));
        var buy = new Button("Buy and load 1 round"); buy.setId("weapon-load-" + key);
        buy.setDisable(!quote.available() || submit == null);
        buy.setOnAction(event -> {
            if (current.get() != state) { feedback.setText("Snapshot changed; refresh the ammunition quote."); buy.setDisable(true); return; }
            buy.setDisable(true);
            feedback.setText("Queued one " + resource(quote.ammunitionId()) + " round for " + quote.shipId());
            submit.apply(quote.command()).whenComplete((outcome, failure) -> Platform.runLater(() -> {
                feedback.setText(failure != null ? "Ammunition loading failed; refresh the quote."
                        : outcome == CommandOutcome.EXECUTED ? "Ammunition purchase and loading completed."
                        : "Ammunition loading was not applied; stock, funds or ship access may have changed.");
                feedback.setTextFill(failure == null && outcome == CommandOutcome.EXECUTED ? Color.LIGHTGREEN : Color.SALMON);
            }));
        });
        box.getChildren().add(buy);
    }
    private static String resource(String id) { return id.replace('_', ' '); }
    private static Label line(String text, String id) {
        var label = new Label(text); label.setId(id); label.setWrapText(true); label.setTextFill(Color.LIGHTCYAN); return label;
    }
}
