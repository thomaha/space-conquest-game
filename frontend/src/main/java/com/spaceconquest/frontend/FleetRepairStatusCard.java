package com.spaceconquest.frontend;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Read-only repair explanations from an immutable snapshot, evaluated away from the FX thread. */
final class FleetRepairStatusCard {
    private FleetRepairStatusCard() {}
    static TitledPane create(GameState state, Fleet fleet, Supplier<GameState> currentSnapshot, Set<String> expandedFleets) {
        var box = new VBox(4); var pane = new TitledPane("Repair status", box);
        pane.setId("repair-status-" + fleet.id()); pane.setExpanded(false);
        pane.expandedProperty().addListener((observable, old, expanded) -> {
            if (expanded) {
                expandedFleets.add(fleet.id());
                refresh(box, state, currentSnapshot,
                        () -> CompletableFuture.supplyAsync(() -> FleetRepairStatus.inspect(state, fleet)));
            } else expandedFleets.remove(fleet.id());
        });
        pane.setDisable(state == null);
        if (state != null) pane.setExpanded(expandedFleets.contains(fleet.id()));
        return pane;
    }

    static void refresh(VBox box, GameState state, Supplier<GameState> currentSnapshot,
                        Supplier<CompletableFuture<FleetRepairStatus>> calculation) {
        var request = new Object(); box.setUserData(request);
        box.getChildren().setAll(line("Calculating repair status...", "repair-loading"));
        calculation.get().whenComplete((status, failure) -> Platform.runLater(() -> {
            if (box.getUserData() != request || currentSnapshot.get() != state) return;
            if (failure != null) box.getChildren().setAll(line("Repair status unavailable. Reopen after the next update.", "repair-error"));
            else render(box, status);
        }));
    }

    static void render(VBox box, FleetRepairStatus status) {
        box.getChildren().clear();
        var priority = status.priority();
        String combat = priority.threat() == FleetThreatAssessment.Action.NO_CONTACT ? "quiet blueprint order"
                : priority.withdrawing() || priority.threat() == FleetThreatAssessment.Action.ESCAPE
                ? "shields before weapons" : "weapons before shields";
        box.getChildren().add(line("Priority: disabled workshops first; useful power restoration next; funded mobility restoration next; "
                + (priority.missingCoverage() ? "restore missing sensor coverage; " : "shared sensor coverage available; ")
                + combat + ". Applies within each recipient; hull follows mounts.", "repair-priority"));
        double ready = status.providers().stream().mapToDouble(FleetRepairStatus.Provider::availablePoints).sum();
        box.getChildren().add(line(String.format(Locale.ROOT,
                "Onboard: %.1f points/day ready before materials; current supplies fund %.1f points across this fleet.",
                ready, status.fundedOnboardPoints()), "repair-capacity"));
        if (status.providers().isEmpty()) box.getChildren().add(line("No known onboard repair workshop installed.", "repair-no-workshop"));
        for (var provider : status.providers()) {
            box.getChildren().add(line(String.format(Locale.ROOT, "%s: %.1f points/day from current workshop condition; %.1f ready.",
                    provider.shipId(), provider.installedPoints(), provider.availablePoints()), "repair-provider-" + provider.shipId()));
            if (!provider.blockers().isEmpty()) box.getChildren().add(line("Blocked: " + String.join("; ", provider.blockers()), "repair-blocker-" + provider.shipId()));
            if (!provider.firstRecipient().isEmpty()) box.getChildren().add(line("First priority: " + provider.firstRecipient(), "repair-first-" + provider.shipId()));
            if (!provider.firstRecipeShortages().isEmpty()) box.getChildren().add(line("First-priority recipe short by "
                    + materials(provider.firstRecipeShortages()) + ". Other affordable work can proceed.", "repair-materials-" + provider.shipId()));
        }
        for (var ship : status.ships()) {
            if (!ship.alive()) { box.getChildren().add(line(ship.id() + ": destroyed; cannot be repaired.", "repair-ship-" + ship.id())); continue; }
            if (!ship.knownDesign()) { box.getChildren().add(line(ship.id() + ": blueprint unavailable; repair needs unknown.", "repair-ship-" + ship.id())); continue; }
            var damage = new ArrayList<String>();
            for (var mount : ship.mounts()) damage.add(String.format(Locale.ROOT, "%s #%d: %.1f damage points", mount.name(), mount.index() + 1, mount.damagePoints()));
            if (ship.hullPoints() > 0) damage.add(String.format(Locale.ROOT, "hull: %.1f points to commissioning health", ship.hullPoints()));
            box.getChildren().add(line(ship.id() + ": " + (damage.isEmpty() ? "no repairable damage" : String.join("; ", damage)), "repair-ship-" + ship.id()));
        }
        if (status.base() != null) {
            var base = status.base();
            box.getChildren().add(line(String.format(Locale.ROOT, "%s: %.1f base points/day shared by docked ships.", base.name(), base.sharedPoints()), "repair-base"));
            if (!base.firstRecipient().isEmpty()) box.getChildren().add(line(String.format(Locale.ROOT,
                    "First-priority quote for %s / %s: %.1f points for %.2f credits, including materials and labor.",
                    base.firstRecipient(), base.firstJob(), base.quotedPoints(), base.quotedCredits()), "repair-base-quote"));
            if (!base.blockers().isEmpty()) box.getChildren().add(line("Base limits: " + String.join("; ", base.blockers()), "repair-base-blocker"));
            if (!base.shortages().isEmpty()) box.getChildren().add(line("Local recipe short by " + materials(base.shortages()), "repair-base-materials"));
            box.getChildren().add(line("Quote uses current prices and funds before other docked ships receive service. Internal allocation can have zero monetary cost.", "repair-base-note"));
        } else box.getChildren().add(line("Base repairs require physical docking at an accessible equipped station.", "repair-base"));
        if (!status.savedMission().isEmpty()) box.getChildren().add(line("Saved mission: " + status.savedMission()
                + ". Resumes after safe funded return if the assignment remains valid.", "repair-mission"));
        box.getChildren().add(line("Current-snapshot preview. Next-tick power, stock and orders may change work. Onboard hosts repair themselves first using their own cargo.", "repair-note"));
    }

    private static String materials(Map<String, Double> materials) {
        return new TreeMap<>(materials).entrySet().stream().map(entry -> String.format(Locale.ROOT,
                "%.2f kg %s", entry.getValue(), entry.getKey().replace('_', ' '))).collect(java.util.stream.Collectors.joining(", "));
    }
    private static Label line(String text, String id) {
        var label = new Label(text); label.setId(id); label.setWrapText(true); label.setTextFill(Color.LIGHTCYAN); return label;
    }
}
