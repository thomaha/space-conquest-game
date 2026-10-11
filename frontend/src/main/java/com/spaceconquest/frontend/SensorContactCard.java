package com.spaceconquest.frontend;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.galaxy.*;
import com.spaceconquest.engine.ship.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Immutable contact presentation; physical coverage and safety queries run off the FX thread. */
final class SensorContactCard {
    record Row(String id, String text, boolean live) {}
    record Status(List<Row> rows, List<String> explanations) {
        Status { rows = List.copyOf(rows); explanations = List.copyOf(explanations); }
    }
    private SensorContactCard() {}

    static TitledPane create(GameState state, String viewer, String system, Fleet fleet,
                            Supplier<GameState> currentSnapshot, Set<String> expanded, String key) {
        var box = new VBox(5); var scroll = new ScrollPane(box); scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(130); scroll.setMaxHeight(180);
        var pane = new TitledPane("Sensor contacts", scroll); pane.setId("sensor-contacts"); pane.setExpanded(false);
        pane.expandedProperty().addListener((observable, old, open) -> {
            if (open) {
                expanded.add(key);
                refresh(box, state, currentSnapshot,
                        () -> CompletableFuture.supplyAsync(() -> inspect(state, viewer, system, fleet)));
            } else expanded.remove(key);
        });
        pane.setDisable(state == null);
        if (state != null) pane.setExpanded(expanded.contains(key));
        return pane;
    }

    static void refresh(VBox box, GameState state, Supplier<GameState> currentSnapshot,
                        Supplier<CompletableFuture<Status>> calculation) {
        var request = new Object(); box.setUserData(request);
        box.getChildren().setAll(line("Loading sensor contacts...", "sensor-loading", Color.LIGHTGRAY));
        calculation.get().whenComplete((status, failure) -> Platform.runLater(() -> {
            if (box.getUserData() != request || currentSnapshot.get() != state) return;
            if (failure != null) box.getChildren().setAll(line("Contacts unavailable. Reopen after the next update.", "sensor-error", Color.SALMON));
            else render(box, status);
        }));
    }

    static Status inspect(GameState state, String viewer, String system, Fleet fleet) {
        var rows = new TreeMap<String, Row>(); var view = SensorSharing.view(state, viewer);
        if (view != null) for (var contact : view.contacts()) {
            if (!system.equals(contact.systemId()) || !contact.recent(state.turn())) continue;
            rows.put(contact.fleetId(), new Row(contact.fleetId(), contact.fleetId() + " (owner " + contact.ownerId()
                    + ") | Lost contact | Last seen " + age(state.turn() - contact.observedTurn()) + " ago (day "
                    + contact.observedTurn() + ") | " + position(contact.position())
                    + String.format(Locale.ROOT, " | Position uncertainty: %.0f km radius", contact.uncertaintyMeters(state.turn()) / 1000), false));
        }
        for (var target : state.fleets()) {
            if (!system.equals(target.currentSystemId()) || viewer.equals(target.ownerEntityId())
                    || !SensorCoverage.detects(state, viewer, target)) continue;
            var p = SensorPosition.at(state, system, target.location());
            rows.put(target.id(), new Row(target.id(), target.name() + " (" + target.id() + ", owner " + target.ownerEntityId()
                    + ") | Live detection | Observed now (day " + state.turn() + ") | "
                    + (p == null ? "Position unavailable" : position(p)) + " | Position uncertainty: 0 km", true));
        }
        var explanations = new ArrayList<String>();
        if (fleet != null && viewer.equals(fleet.ownerEntityId())) {
            boolean remembered = !SensorContactMemory.threats(state, fleet, FleetThreatAssessment.THREAT_RADIUS_METERS).isEmpty();
            if (remembered) explanations.add("Recent last-known threats contribute to "
                    + ShipServicePower.activity(state, fleet).name().toLowerCase(Locale.ROOT) + " power and repair priorities.");
            if (FleetShieldPolicy.travelling(fleet) && fleet.shieldPolicy() == FleetShieldPolicy.Mode.AUTOMATIC
                    && !SensorContactMemory.threats(state, fleet, Double.POSITIVE_INFINITY).isEmpty())
                explanations.add("Recent last-known threats keep travel shield recharge eligible; actual power and reserves still limit charging.");
            var order = fleet.interceptOrder(); var assignment = order == null ? null : order.protection();
            if (assignment != null && assignment.escaped()) {
                var destination = FleetProtectionReturn.destination(state, fleet);
                if (destination != null && !FleetEscapeReturn.clearToReturn(state, fleet, destination))
                    explanations.add("Escape return waits: live or recent threats remain near the refuge or return destination.");
            }
        }
        explanations.add("Lost contacts cannot authorize attack or interception. Memory expires after three days; uncertainty grows until reacquisition.");
        return new Status(new ArrayList<>(rows.values()), explanations);
    }

    private static String age(long days) { return days == 0 ? "less than one day" : days + (days == 1 ? " day" : " days"); }
    private static String position(LocalSiteGeometry.Point p) {
        return String.format(Locale.ROOT, "System coordinates: x %.0f, y %.0f, z %.0f km", p.xMeters() / 1000, p.yMeters() / 1000, p.zMeters() / 1000);
    }
    static void render(VBox box, Status status) {
        box.getChildren().clear();
        if (status.rows().isEmpty()) box.getChildren().add(line("No live or recent last-known contacts in this system.", "sensor-empty", Color.LIGHTGRAY));
        for (var row : status.rows()) box.getChildren().add(line(row.text(), "sensor-contact-" + row.id(), row.live() ? Color.LIGHTGREEN : Color.KHAKI));
        for (int i = 0; i < status.explanations().size(); i++)
            box.getChildren().add(line(status.explanations().get(i), "sensor-note-" + i, Color.LIGHTCYAN));
    }
    private static Label line(String text, String id, Color color) {
        var label = new Label(text); label.setId(id); label.setWrapText(true); label.setTextFill(color); return label;
    }
}
