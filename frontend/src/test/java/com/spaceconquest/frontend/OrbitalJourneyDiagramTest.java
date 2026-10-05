package com.spaceconquest.frontend;

import com.spaceconquest.engine.ship.HohmannCoast;
import com.spaceconquest.engine.ship.OrbitalFlight;
import com.spaceconquest.engine.ship.OrbitalJourneySnapshot;
import com.spaceconquest.engine.ship.ShipSolarEnvironment;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.image.PixelFormat;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class OrbitalJourneyDiagramTest {
    @BeforeAll static void startFx() {
        try { Platform.startup(() -> {}); } catch (IllegalStateException ignored) { /* Already started. */ }
    }

    private <T> T fx(Callable<T> work) throws Exception {
        var task = new FutureTask<>(work); Platform.runLater(task); return task.get(10, TimeUnit.SECONDS);
    }

    @Test void lunarJourneyAndLunarParkingLabelTheirActualFrames() throws Exception {
        for (boolean parking : List.of(false, true)) {
            var coast = new HohmannCoast(parking ? 4.9e12 : 3.986e14, parking ? 2.2e6 : 8.371e6,
                    parking ? 2.7e6 : 384.4e6, .7);
            var frame = new OrbitalFlight.Frame(parking ? "moon" : "earth",
                    parking ? OrbitalFlight.CenterKind.LUNAR : OrbitalFlight.CenterKind.PLANETARY);
            var itinerary = new OrbitalFlight.Itinerary(parking ? "moon" : "earth", "moon", 500, 1000, 0, 0,
                    coast, ShipSolarEnvironment.DARK, List.of(new OrbitalFlight.Maneuver(0, 1, Map.of()),
                    new OrbitalFlight.Maneuver(coast.coastSeconds(), 1, Map.of()),
                    new OrbitalFlight.Maneuver(coast.coastSeconds() + 3600, 1, Map.of())), frame);
            var flight = new OrbitalFlight(itinerary, 1000, 1, OrbitalFlight.Status.COAST, "Coasting");
            var snapshot = OrbitalJourneySnapshot.from(flight);
            assertTrue(snapshot.bodyCentered()); assertEquals(frame, snapshot.frame());
            fx(() -> {
                var diagram = OrbitalJourneyDiagram.create(flight);
                var legend = (Label) diagram.getChildren().getLast();
                assertTrue(legend.getText().contains(parking ? "Moon-centered" : "Planet-centered lunar"));
                return null;
            });
        }
    }

    @Test void asynchronousDiagramShowsFailedCaptureWithoutClaimingArrivalAndRendersAtNarrowWidth() throws Exception {
        var coast = new HohmannCoast(1e20, 1e11, 2e11, .7);
        var itinerary = new OrbitalFlight.Itinerary("earth", "mars", 100000, 8000, 0, 10, coast, ShipSolarEnvironment.DARK,
                List.of(new OrbitalFlight.Maneuver(10, 1, Map.of()),
                        new OrbitalFlight.Maneuver(10 + coast.coastSeconds(), 1, Map.of()),
                        new OrbitalFlight.Maneuver(3610 + coast.coastSeconds(), 1, Map.of())));
        var flight = new OrbitalFlight(itinerary, 10 + coast.coastSeconds() + 20 * 86400, 1,
                OrbitalFlight.Status.MISSED, "Missing capture fuel; ballistic coast continues");
        var ready = new CompletableFuture<OrbitalJourneySnapshot>();
        var diagram = fx(() -> {
            var box = OrbitalJourneyDiagram.create(flight);
            var canvas = (Canvas) box.lookup("#orbital-journey-canvas");
            canvas.accessibleTextProperty().addListener((observable, old, text) ->
                    ready.complete((OrbitalJourneySnapshot) canvas.getUserData()));
            var root = new VBox(box); root.setStyle("-fx-background-color: #091321; -fx-padding: 12;");
            new Scene(root, 620, 420); root.applyCss(); root.layout();
            return box;
        });
        var snapshot = ready.get(10, TimeUnit.SECONDS);
        assertEquals(flight.position(), snapshot.ship());
        var image = fx(() -> {
            var canvas = (Canvas) diagram.lookup("#orbital-journey-canvas");
            assertTrue(((Label) diagram.lookup("#orbital-maneuver-timing")).getText().contains("Capture: not executed"));
            assertTrue(canvas.getAccessibleText().contains("MISSED"));
            assertTrue(canvas.getAccessibleText().contains("ballistic coast continues"));
            var parent = (VBox) diagram.getParent();
            parent.resize(320, 420); parent.layout();
            assertTrue(canvas.getWidth() <= 320);
            assertTrue(diagram.getChildren().getLast() instanceof Label legend && legend.isWrapText());
            parent.resize(620, 420); parent.layout();
            return parent.snapshot(null, null);
        });
        // Image IO runs outside the JavaFX application thread.
        int width = (int) image.getWidth(), height = (int) image.getHeight();
        var pixels = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
        var output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        output.setRGB(0, 0, width, height, pixels, 0, width);
        var file = Path.of("target/orbital-journey-diagram.png"); Files.createDirectories(file.toAbsolutePath().getParent());
        ImageIO.write(output, "png", file.toFile());
    }
}
