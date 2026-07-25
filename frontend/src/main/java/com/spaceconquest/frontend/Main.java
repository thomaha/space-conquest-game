package com.spaceconquest.frontend;

import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.entity.Entity;
import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.SolarSystem;
import javafx.geometry.Point2D;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Map;

import static com.almasb.fxgl.dsl.FXGL.*;

/**
 * FXGL entry point. Wires together the galaxy registry, renderer, camera
 * controller and HUD, delegating all behaviour to those collaborators.
 */
public class Main extends GameApplication {
    private static final Logger logger = LogManager.getLogger(Main.class);

    private final GalaxyRegistry registry = new GalaxyRegistry();
    private final CameraController camera = new CameraController(registry);
    private final GameHud hud = new GameHud(camera, registry);

    @Override
    protected void initSettings(GameSettings settings) {
        settings.setWidth(1200);
        settings.setHeight(800);
        settings.setTitle("Space conquest game");
        settings.setVersion("0.1");
    }

    @Override
    protected void initGame() {
        getGameScene().setBackgroundColor(Color.BLACK);

        try {
            java.util.List<SolarSystem> solarSystems = DataModelLoader.loadSolarSystems();
            registry.clear();
            for (SolarSystem solarSystem : solarSystems) {
                new SolarSystemRenderer(solarSystem, registry).render();
            }

            // Initial focus on Sol at zoom 5, then zoom out to galaxy view at zoom 10
            camera.gotoEntity("Sol", 10);
            camera.updateZoom();
        } catch (IOException e) {
            logger.error("Failed to load solar systems", e);
        }
    }

    @Override
    protected void initInput() {
        // A single click on the map focuses on that point; a double-click on a
        // celestial body focuses on it (centered) at zoom level 1.
        getGameScene().getContentRoot().setOnMouseClicked(e -> {
            if (e.getButton() != MouseButton.PRIMARY) {
                return;
            }
            Point2D world = getInput().getMousePositionWorld();
            if (e.getClickCount() >= 2) {
                Entity hit = camera.findEntityAt(world);
                if (hit != null) {
                    camera.focusOnEntity(hit, 1);
                    return;
                }
            }
            camera.focusOnPoint(world);
        });
    }

    @Override
    protected void initUI() {
        hud.build();
    }

    @Override
    protected void initGameVars(Map<String, Object> vars) {
        vars.put("zoomLevel", camera.getZoomLevel());
    }

    static void main(String[] args) {
        launch(args);
    }
}
