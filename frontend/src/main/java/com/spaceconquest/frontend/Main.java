package com.spaceconquest.frontend;

import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.entity.Entity;
import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.GalaxyGenerator;
import com.spaceconquest.engine.GameClock;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.GameStartScenario;
import com.spaceconquest.engine.SolarSystem;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.paint.Color;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
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
    private final com.spaceconquest.engine.SpaceConquestEngine engine = new com.spaceconquest.engine.SpaceConquestEngine();
    private GameHud hud;
    private GalaxyGenerator generator;
    private List<SolarSystem> currentSolarSystems;
    private GameStartScenario currentScenario = GameStartScenario.PRE_SPACE_FLIGHT;

    public com.spaceconquest.engine.SpaceConquestEngine getEngine() {
        return engine;
    }

    public List<SolarSystem> getSolarSystems() {
        return currentSolarSystems;
    }

    public GameStartScenario getCurrentScenario() {
        return currentScenario;
    }

    @Override
    protected void initSettings(GameSettings settings) {
        ScreenSettings screenSettings = ScreenSettingsManager.getInstance().getSettings();
        settings.setWidth(screenSettings.getWidth());
        settings.setHeight(screenSettings.getHeight());
        if (screenSettings.isFullscreen()) {
            settings.setFullScreenAllowed(true);
            settings.setFullScreenFromStart(true);
        }
        settings.setTitle("Space conquest game");
        settings.setVersion("0.1");
        settings.setGameMenuEnabled(false);
        settings.setMainMenuEnabled(false);
    }

    @Override
    protected void initGame() {
        getGameScene().setBackgroundColor(Color.BLACK);

        try {
            generator = new GalaxyGenerator();
            currentSolarSystems = DataModelLoader.loadSolarSystems();
            registry.clear();
            for (SolarSystem solarSystem : currentSolarSystems) {
                new SolarSystemRenderer(solarSystem, registry, engine.getMegastructures()).render();
            }

            // Start centered on Sol at the requested close-up zoom level.
            camera.gotoEntity("Sol", 5);
            camera.updateZoom();
        } catch (IOException e) {
            logger.error("Failed to load initial galaxy data", e);
        }
    }

    public void createNewGalaxy(int numSystems) {
        createNewGalaxy(numSystems, 0, GameStartScenario.PRE_SPACE_FLIGHT);
    }

    public void createNewGalaxy(int numSystems, GameStartScenario scenario) {
        createNewGalaxy(numSystems, 0, scenario);
    }

    public void createNewGalaxy(int numSystems, int numAIEmpires, GameStartScenario scenario) {
        this.currentScenario = scenario != null ? scenario : GameStartScenario.PRE_SPACE_FLIGHT;
        GameStartScenario selectedScenario = this.currentScenario;
        if (hud != null && hud.getMenubar() != null) hud.getMenubar().invalidateWorldRefreshes();
        runSimulationTask(() -> {
            GameState newGameState = generator.generateGameState(numSystems, numAIEmpires, selectedScenario);
            GameState activeState;
            LocalDateTime time;
            synchronized (engine) {
                engine.getGameClock().startNewCampaign(selectedScenario.startTime());
                engine.applyGameState(newGameState);
                engine.getGameClock().alignTurn(newGameState.turn());
                // Process staged empire-creation commands against the new world.
                if (hud != null && hud.getMenubar() != null && hud.getMenubar().getHumanController() != null) {
                    hud.getMenubar().getHumanController().getCommandQueue().processCommands(engine);
                }
                activeState = engine.getGameState();
                time = engine.getGameClock().getGameTime();
            }
            Platform.runLater(() -> displayWorld(activeState, time, 1));
        });
    }

    public void loadGame(String saveName) {
        if (hud != null && hud.getMenubar() != null) hud.getMenubar().invalidateWorldRefreshes();
        runSimulationTask(() -> {
            try {
                com.spaceconquest.engine.SaveGameManager mgr = new com.spaceconquest.engine.SaveGameManager();
                java.io.File file = mgr.getSaveDirectory().resolve(saveName.endsWith(".scsave") ? saveName : saveName + ".scsave").toFile();
                if (file.exists()) {
                    com.spaceconquest.engine.SaveGame save = mgr.load(file);
                    LocalDateTime savedTime;
                    try {
                        savedTime = LocalDateTime.parse(save.gameTime());
                    } catch (DateTimeParseException | NullPointerException ignored) {
                        savedTime = save.resolvedCampaignStartTime();
                    }
                    if (savedTime.isBefore(save.resolvedCampaignStartTime())) {
                        savedTime = save.resolvedCampaignStartTime();
                    }
                    GameState activeState;
                    synchronized (engine) {
                        engine.getGameClock().startNewCampaign(save.resolvedCampaignStartTime());
                        engine.getGameClock().restore(savedTime, MenubarClockController.speedForIndex(save.gameSpeed()));
                        engine.applyGameState(save.toGameState(engine.getGameClock().getCurrentTurn(), "RUNNING"));
                        activeState = engine.getGameState();
                    }
                    LocalDateTime restoredTime = savedTime;
                    Platform.runLater(() -> displayWorld(activeState, restoredTime, save.gameSpeed()));
                }
            } catch (IOException e) {
                logger.error("Failed to load save " + saveName, e);
            }
        });
    }

    public void saveGame(String saveName) {
        int speed = hud != null && hud.getMenubar() != null ? hud.getMenubar().getSpeedIndex() : 1;
        runSimulationTask(() -> {
            try {
                com.spaceconquest.engine.SaveGameManager mgr = new com.spaceconquest.engine.SaveGameManager();
                synchronized (engine) {
                    mgr.save(saveName, engine.getGameState(), speed,
                            engine.getGameClock().getGameTime().toString(),
                            engine.getGameClock().getCampaignStartTime().toString());
                }
                logger.info("Saved game successfully as: " + saveName);
            } catch (IOException e) {
                logger.error("Failed to save game as " + saveName, e);
            }
        });
    }

    public void quickSave() {
        int speed = hud != null && hud.getMenubar() != null ? hud.getMenubar().getSpeedIndex() : 1;
        runSimulationTask(() -> {
            try {
                com.spaceconquest.engine.SaveGameManager mgr = new com.spaceconquest.engine.SaveGameManager();
                synchronized (engine) {
                    mgr.quickSave(engine.getGameState(), speed,
                            engine.getGameClock().getGameTime().toString(),
                            engine.getGameClock().getCampaignStartTime().toString());
                }
                logger.info("Quick saved game successfully.");
            } catch (IOException e) {
                logger.error("Failed to quick save game", e);
            }
        });
    }

    private void runSimulationTask(Runnable task) {
        if (hud != null && hud.getMenubar() != null) {
            hud.getMenubar().submitSimulationTask(task);
        } else {
            task.run();
        }
    }

    private void displayWorld(GameState state, LocalDateTime time, int speed) {
        registry.clear();
        getGameWorld().getEntitiesCopy().forEach(Entity::removeFromWorld);
        currentSolarSystems = state.solarSystems();
        for (SolarSystem system : currentSolarSystems) {
            new SolarSystemRenderer(system, registry, state.megastructures()).render();
        }
        if (hud != null && hud.getMenubar() != null) {
            hud.getMenubar().restoreTime(time, speed);
            hud.getMenubar().updateAllViews(state);
        }
        if (!currentSolarSystems.isEmpty()) {
            camera.gotoEntity(currentSolarSystems.getFirst().name(), 5);
        }
        camera.updateZoom();
    }

    @Override
    protected void initInput() {
        // Pressing Escape opens/closes the same Game Menu as the menubar button.
        onKeyDown(KeyCode.ESCAPE, () -> {
            if (hud != null && hud.getMenubar() != null) {
                hud.getMenubar().toggleGameMenu();
            }
        });

        // A single click on the map focuses on that point; a double-click on a
        // celestial body focuses on it (centered) at zoom level 1.
        getGameScene().getContentRoot().setOnMouseClicked(e -> {
            if (e.isConsumed() || e.getButton() != MouseButton.PRIMARY) {
                return;
            }
            if (hud != null && hud.getMenubar() != null && hud.getMenubar().isAnyOverlayVisible()) {
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
            if (e.isConsumed()) {
                return;
            }
            if (hud != null && hud.getMenubar() != null && hud.getMenubar().isAnyOverlayVisible()) {
                return;
            }
            if (e.getDeltaY() > 0) {
                camera.zoomIn();
            } else if (e.getDeltaY() < 0) {
                camera.zoomOut();
            }
        });
    }

    @Override
    protected void initUI() {
        engine.start();
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
