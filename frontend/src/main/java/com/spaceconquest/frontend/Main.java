package com.spaceconquest.frontend;

import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.entity.Entity;
import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.GalaxyGenerator;
import com.spaceconquest.engine.SolarSystem;
import javafx.geometry.Point2D;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.paint.Color;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.List;
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
    private GameHud hud;
    private GalaxyGenerator generator;
    private List<SolarSystem> currentSolarSystems;

    public List<SolarSystem> getSolarSystems() {
        return currentSolarSystems;
    }

    @Override
    protected void initSettings(GameSettings settings) {
        settings.setWidth(1920);
        settings.setHeight(1080);
        settings.setTitle("Space conquest game");
        settings.setVersion("0.1");
    }

    @Override
    protected void initGame() {
        getGameScene().setBackgroundColor(Color.BLACK);

        try {
            generator = new GalaxyGenerator();
            currentSolarSystems = DataModelLoader.loadSolarSystems();
            registry.clear();
            for (SolarSystem solarSystem : currentSolarSystems) {
                new SolarSystemRenderer(solarSystem, registry).render();
            }

            // Start centered on Sol at the requested close-up zoom level.
            camera.gotoEntity("Sol", 5);
            camera.updateZoom();
        } catch (IOException e) {
            logger.error("Failed to load initial galaxy data", e);
        }
    }

    public void createNewGalaxy(int numSystems) {
        registry.clear();
        getGameWorld().getEntitiesCopy().forEach(Entity::removeFromWorld);

        currentSolarSystems = generator.generate(numSystems);
        for (SolarSystem ss : currentSolarSystems) {
            new SolarSystemRenderer(ss, registry).render();
        }

        if (!currentSolarSystems.isEmpty()) {
            camera.gotoEntity(currentSolarSystems.get(0).name(), 5);
        }
        camera.updateZoom();
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

        getGameScene().getContentRoot().setOnScroll(e -> {
            if (e.getDeltaY() > 0) {
                camera.zoomIn();
            } else if (e.getDeltaY() < 0) {
                camera.zoomOut();
            }
        });
    }

    @Override
    protected void initUI() {
        hud = new GameHud(camera, registry);
        hud.build(this);
    }

    @Override
    protected void initGameVars(Map<String, Object> vars) {
        vars.put("zoomLevel", camera.getZoomLevel());
    }

    static void main(String[] args) {
        launch(args);
    }
}
