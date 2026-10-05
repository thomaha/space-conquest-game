package com.spaceconquest.frontend;

import com.spaceconquest.engine.ship.CircularOrbitalEphemeris;
import com.spaceconquest.engine.ship.OrbitalFlight;
import com.spaceconquest.engine.ship.OrbitalJourneySnapshot;
import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/** Canvas rendering of an immutable tick snapshot; all trajectory sampling runs on a worker. */
final class OrbitalJourneyDiagram {
    private OrbitalJourneyDiagram() {}

    static VBox create(OrbitalFlight flight) {
        var box = new VBox(4);
        box.setMinWidth(0);
        box.setId("orbital-journey-diagram");
        var description = new Label("Loading orbital trajectory...");
        description.setWrapText(true); description.setTextFill(Color.LIGHTCYAN);
        var canvas = new Canvas(520, 280);
        canvas.setId("orbital-journey-canvas");
        var maneuvers = new Label();
        maneuvers.setId("orbital-maneuver-timing");
        maneuvers.setWrapText(true); maneuvers.setTextFill(Color.LIGHTGRAY);
        var legend = new Label("White/red ring: fleet | Orange: planned coast | Gray: ballistic orbit. "
                + (flight.itinerary().bodyCentered() ? flight.itinerary().lunarTransfer()
                ? "Planet-centered lunar transfer; local parking motion and exact station phasing are omitted."
                : flight.itinerary().frame().kind() == OrbitalFlight.CenterKind.LUNAR
                ? "Moon-centered parking approximation; exact station phasing is omitted."
                : "Planet-centered parking approximation; exact station phasing is omitted."
                : "Star-centered orbital envelopes; parking motion and exact station phasing are omitted."));
        legend.setWrapText(true); legend.setTextFill(Color.LIGHTGRAY);
        box.getChildren().addAll(description, canvas, maneuvers, legend);
        box.widthProperty().addListener((observable, oldWidth, width) -> {
            canvas.setWidth(Math.max(1, width.doubleValue()));
            if (canvas.getUserData() instanceof OrbitalJourneySnapshot snapshot) draw(canvas, snapshot);
        });
        CompletableFuture.supplyAsync(() -> OrbitalJourneySnapshot.from(flight)).whenComplete((snapshot, failure) -> Platform.runLater(() -> {
            if (failure != null) { description.setText("Orbital trajectory unavailable."); return; }
            canvas.setUserData(snapshot); draw(canvas, snapshot);
            description.setText(String.format(Locale.ROOT,
                    "%s (%.0f km parking) → %s (%.0f km parking) | %s",
                    snapshot.sourceBodyId(), snapshot.sourceAltitudeKm(), snapshot.targetBodyId(), snapshot.targetAltitudeKm(), snapshot.status()));
            maneuvers.setText(snapshot.maneuvers().stream().map(event -> event.name() + ": " + (event.completed() ? "completed"
                    : event.abandoned() ? "not executed" : countdown(event.daysUntil())))
                    .reduce((first, next) -> first + " | " + next).orElse(""));
            canvas.setAccessibleText(description.getText() + ". " + maneuvers.getText() + ". " + snapshot.message());
        }));
        return box;
    }

    private static String countdown(double days) {
        return days < 1 ? String.format(Locale.ROOT, "in %.1f hours", Math.max(0, days * 24))
                : String.format(Locale.ROOT, "in %.1f days", days);
    }

    private static void draw(Canvas canvas, OrbitalJourneySnapshot snapshot) {
        var g = canvas.getGraphicsContext2D();
        double width = canvas.getWidth(), height = canvas.getHeight();
        g.setFill(Color.rgb(9, 19, 33)); g.fillRect(0, 0, width, height);
        double cx = width / 2, cy = height / 2;
        double scale = Math.max(1, Math.min(width - 36, height - 36)) / 2
                / (Math.max(snapshot.sourceRadiusMeters(), snapshot.targetRadiusMeters()) * 1.08);
        g.setStroke(Color.rgb(58, 76, 99)); g.setLineWidth(1);
        for (double radius : List.of(snapshot.sourceRadiusMeters(), snapshot.targetRadiusMeters()))
            g.strokeOval(cx - radius * scale, cy - radius * scale, radius * scale * 2, radius * scale * 2);
        path(g, snapshot.ballisticOrbit(), Color.rgb(88, 101, 116), cx, cy, scale);
        path(g, snapshot.transferArc(), Color.ORANGE, cx, cy, scale);
        g.setFill(snapshot.bodyCentered() ? Color.CORNFLOWERBLUE : Color.GOLD); g.fillOval(cx - 5, cy - 5, 10, 10);
        if (snapshot.bodyCentered()) g.fillText(snapshot.frame().centerBodyId(), cx + 9, cy - 5);
        marker(g, snapshot.source(), snapshot.parkingTransfer() ? "Departure orbit" : snapshot.sourceBodyId(), Color.LIGHTBLUE, cx, cy, scale);
        marker(g, snapshot.target(), snapshot.parkingTransfer() ? "Arrival orbit" : snapshot.targetBodyId(), Color.LIGHTGREEN, cx, cy, scale);
        double x = cx + snapshot.ship().xMeters() * scale, y = cy - snapshot.ship().yMeters() * scale;
        g.setStroke(snapshot.status() == OrbitalFlight.Status.MISSED || snapshot.status() == OrbitalFlight.Status.APPROACH_FAILED
                || snapshot.status() == OrbitalFlight.Status.WAITING_FAILED ? Color.SALMON : Color.WHITE);
        g.setLineWidth(2); g.strokeOval(x - 7, y - 7, 14, 14);
        double speed = snapshot.ship().speedMps();
        if (speed > 0) g.strokeLine(x, y, x + snapshot.ship().vxMps() / speed * 20, y - snapshot.ship().vyMps() / speed * 20);
    }

    private static void path(GraphicsContext g, List<OrbitalJourneySnapshot.Point> points, Color color,
                             double cx, double cy, double scale) {
        g.setStroke(color); g.setLineWidth(1); g.beginPath();
        var first = points.getFirst(); g.moveTo(cx + first.xMeters() * scale, cy - first.yMeters() * scale);
        for (var point : points) g.lineTo(cx + point.xMeters() * scale, cy - point.yMeters() * scale);
        g.stroke();
    }

    private static void marker(GraphicsContext g, CircularOrbitalEphemeris.State position, String name, Color color,
                               double cx, double cy, double scale) {
        double x = cx + position.xMeters() * scale, y = cy - position.yMeters() * scale;
        g.setFill(color); g.fillOval(x - 4, y - 4, 8, 8); g.fillText(name, x + 9, y - 5);
    }
}
