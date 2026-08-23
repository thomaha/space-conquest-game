package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.ai.CorporationAIController;
import com.spaceconquest.control.ai.EmpireAIController;
import com.spaceconquest.control.ai.ShadowSyndicateAIController;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.scenario.VictoryConditionChecker;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static com.almasb.fxgl.dsl.FXGL.*;

/** The top-level navigation, command pipeline and real-time game-clock loop for the space conquest game. */
public class Menubar {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String[] SPEED_NAMES = {"1 min/s", "1 hour/s", "6 hours/s", "12 hours/s", "1 day/s"};
    private static final int[] MINUTES_PER_TICK = {1, 60, 360, 720, 1440};

    private VBox root;
    private Label clockLabel;
    private Label speedLabel;
    private VBox detailPanel;
    private Label detailTitle;
    private Label detailText;
    private final Timeline clock = new Timeline();
    private final List<VBox> overlayNodes = new ArrayList<>();

    private final HumanController humanController = new HumanController();
    private EmpireAIController empireAIController;
    private CorporationAIController corporationAIController;
    private ShadowSyndicateAIController shadowSyndicateAIController;
    private String playerEmpireId = "terran_confederation";

    private TechnologyView techView;
    private GameMenuView gameMenuView;
    private GalaxyListView galaxyListView;
    private EmpireView empireView;
    private CorporateView corporateView;
    private DiplomacyView diplomacyView;
    private CommercialHubView commercialHubView;
    private ColonyManagementView colonyManagementView;
    private CampaignManagerView campaignManagerView;
    private OrbitalStationView orbitalStationView;
    private EspionageView espionageView;
    private RefinementView refinementView;
    private TacticalBattlePlaybackView battlePlaybackView;
    private TerraformingView terraformingView;
    private MegastructureView megastructureView;
    private GalacticSenateView galacticSenateView;
    private GalaxyCanvasView galaxyCanvasView;
    private ScenarioEditorView scenarioEditorView;
    private IndustryView industryView;
    private ShipDesignerView shipDesignerView;
    private FleetManagementView fleetManagementView;
    private PlanetDetailView planetDetailView;
    private VictoryDefeatView victoryDefeatView;
    private TutorialOnboardingView tutorialView;
    private TacticalCombatArenaView combatArenaView;
    private EmpireCreationWizardView empireWizardView;
    private AudioSettingsView audioSettingsView;
    private ScreenSettingsView screenSettingsView;
    private AudioPlaybackManager audioPlaybackManager;

    private LocalDateTime gameTime = LocalDateTime.of(2200, 1, 1, 8, 0);
    private int speedIndex = 1;
    private int savedSpeedIndex = 1;
    private boolean paused;
    private boolean manuallyPaused;
    private Main mainApp;

    public HumanController getHumanController() {
        return humanController;
    }

    public Main getMainApp() {
        return mainApp;
    }

    public String getPlayerEmpireId() {
        return playerEmpireId;
    }

    public void setPlayerEmpireId(String empireId) {
        if (empireId != null && !empireId.isEmpty()) {
            this.playerEmpireId = empireId;
            if (empireView != null) empireView.setPlayerEmpireId(empireId);
            if (techView != null) techView.setPlayerEmpireId(empireId);
            if (industryView != null) industryView.setPlayerEmpireId(empireId);
            if (shipDesignerView != null) shipDesignerView.setPlayerEmpireId(empireId);
            if (fleetManagementView != null) fleetManagementView.setPlayerEmpireId(empireId);
            if (colonyManagementView != null) colonyManagementView.setPlayerEmpireId(empireId);
            if (planetDetailView != null) planetDetailView.setPlayerEmpireId(empireId);
            if (commercialHubView != null) commercialHubView.setPlayerEmpireId(empireId);
            if (terraformingView != null) terraformingView.setPlayerEmpireId(empireId);
            if (megastructureView != null) megastructureView.setPlayerEmpireId(empireId);
            if (galacticSenateView != null) galacticSenateView.setPlayerEmpireId(empireId);
            if (galaxyCanvasView != null) galaxyCanvasView.setPlayerEmpireId(empireId);
        }
    }

    /**
     * Updates all child views and AI controllers with the newly active game state.
     *
     * @param state the latest game state from the engine
     */
    public void updateAllViews(GameState state) {
        if (state == null) return;
        if (empireAIController != null) empireAIController.onGameStateUpdate(state);
        if (corporationAIController != null) corporationAIController.onGameStateUpdate(state);
        if (shadowSyndicateAIController != null) shadowSyndicateAIController.onGameStateUpdate(state);
        if (humanController != null) humanController.onGameStateUpdate(state);

        if (empireView != null) empireView.updateData(state);
        if (techView != null) techView.setResearchProjects(state.researchProjects());
        if (industryView != null) industryView.updateData(state.industrialFacilities(), state.expansionProjects());
        if (shipDesignerView != null) shipDesignerView.updateDesigns(state.shipDesigns());
        if (fleetManagementView != null) fleetManagementView.updateFleets(state.fleets());
        if (galacticSenateView != null) galacticSenateView.updateData(state.galacticCommunity());
        if (megastructureView != null) megastructureView.updateData(state.megastructures());
        if (terraformingView != null && mainApp != null && mainApp.getEngine() != null) {
            terraformingView.updateData(mainApp.getEngine().getAtmospheres(), state.terraformingProjects());
        }
        if (planetDetailView != null) planetDetailView.updateData(state.geologicalDeposits(), state.powerGrids(), state.megastructures());
        if (colonyManagementView != null && mainApp != null && mainApp.getEngine() != null) {
            colonyManagementView.updateData(mainApp.getEngine().getAllPlanets(), List.of());
        }
        if (espionageView != null) espionageView.updateData(state.sleeperAgents(), state.espionageOperations(), state.pirateBases());
        if (orbitalStationView != null) orbitalStationView.updateData(state.orbitalStations(), state.spaceElevators());
        if (galaxyCanvasView != null && mainApp != null && mainApp.getSolarSystems() != null) {
            galaxyCanvasView.updateData(mainApp.getSolarSystems(), state.fleets(), state.megastructures(), state.fogOfWarStates());
        }
    }

    public TechnologyView getTechView() {
        return techView;
    }

    public GameMenuView getGameMenuView() {
        return gameMenuView;
    }

    public GalaxyListView getGalaxyListView() {
        return galaxyListView;
    }

    public EmpireView getEmpireView() {
        return empireView;
    }

    public CorporateView getCorporateView() {
        return corporateView;
    }

    public DiplomacyView getDiplomacyView() {
        return diplomacyView;
    }

    public CommercialHubView getCommercialHubView() {
        return commercialHubView;
    }

    public ColonyManagementView getColonyManagementView() {
        return colonyManagementView;
    }

    public CampaignManagerView getCampaignManagerView() {
        return campaignManagerView;
    }

    public OrbitalStationView getOrbitalStationView() {
        return orbitalStationView;
    }

    public EspionageView getEspionageView() {
        return espionageView;
    }

    public RefinementView getRefinementView() {
        return refinementView;
    }

    public TacticalBattlePlaybackView getBattlePlaybackView() {
        return battlePlaybackView;
    }

    public TerraformingView getTerraformingView() {
        return terraformingView;
    }

    public MegastructureView getMegastructureView() {
        return megastructureView;
    }

    public GalacticSenateView getGalacticSenateView() {
        return galacticSenateView;
    }

    public GalaxyCanvasView getGalaxyCanvasView() {
        return galaxyCanvasView;
    }

    public ScenarioEditorView getScenarioEditorView() {
        return scenarioEditorView;
    }

    public IndustryView getIndustryView() {
        return industryView;
    }

    public ShipDesignerView getShipDesignerView() {
        return shipDesignerView;
    }

    public FleetManagementView getFleetManagementView() {
        return fleetManagementView;
    }

    public PlanetDetailView getPlanetDetailView() {
        return planetDetailView;
    }

    public VictoryDefeatView getVictoryDefeatView() {
        return victoryDefeatView;
    }

    public TutorialOnboardingView getTutorialView() {
        return tutorialView;
    }

    /** The current speed setting index, used when saving the game. */
    public int getSpeedIndex() {
        return speedIndex;
    }

    /** The current in-game date and time. */
    public LocalDateTime getGameTime() {
        return gameTime;
    }

    /** Restores the game clock from a loaded save. */
    public void restoreTime(LocalDateTime time, int speed) {
        if (time != null) {
            gameTime = time;
        }
        speedIndex = Math.max(0, Math.min(SPEED_NAMES.length - 1, speed));
        savedSpeedIndex = speedIndex;
        updateClockLabels();
        restartClock();
    }

    /** Opens the campaign setup dialogue for configuring a new galaxy, starting era, and custom empire. */
    public void showGameStartDialog() {
        startNewGameSetup();
    }

    /** Initiates the multi-step new game creation workflow starting with campaign scenario setup. */
    public void startNewGameSetup() {
        hideAllPanels();
        openPage();
        if (scenarioEditorView != null) {
            scenarioEditorView.show();
        }
    }

    public void build(Main mainApp) {
        this.mainApp = mainApp;
        root = new VBox(8);

        // Initialize AI controllers
        empireAIController = new EmpireAIController("vulkan_forge", humanController.getCommandQueue());
        corporationAIController = new CorporationAIController("corp_sol_extraction", humanController.getCommandQueue());
        shadowSyndicateAIController = new ShadowSyndicateAIController("shadow_syndicate_sol", humanController.getCommandQueue());

        // Initialize all frontend views and pass controller
        techView = new TechnologyView(this);
        techView.setHumanController(humanController);
        techView.setPlayerEmpireId(playerEmpireId);

        industryView = new IndustryView(this);
        industryView.setHumanController(humanController);
        industryView.setPlayerEmpireId(playerEmpireId);

        shipDesignerView = new ShipDesignerView(this);
        shipDesignerView.setHumanController(humanController);
        shipDesignerView.setPlayerEmpireId(playerEmpireId);

        fleetManagementView = new FleetManagementView(this);
        fleetManagementView.setHumanController(humanController);
        fleetManagementView.setPlayerEmpireId(playerEmpireId);

        colonyManagementView = new ColonyManagementView(this);
        colonyManagementView.setHumanController(humanController);
        colonyManagementView.setPlayerEmpireId(playerEmpireId);

        planetDetailView = new PlanetDetailView(this);
        planetDetailView.setHumanController(humanController);
        planetDetailView.setPlayerEmpireId(playerEmpireId);

        gameMenuView = new GameMenuView(this);
        galaxyListView = new GalaxyListView(this, mainApp);
        empireView = new EmpireView(this);
        empireView.setHumanController(humanController);
        empireView.setPlayerEmpireId(playerEmpireId);
        corporateView = new CorporateView(this);
        diplomacyView = new DiplomacyView(this);
        commercialHubView = new CommercialHubView(this);
        commercialHubView.setHumanController(humanController);
        commercialHubView.setPlayerEmpireId(playerEmpireId);

        campaignManagerView = new CampaignManagerView(this);
        campaignManagerView.setMainApp(mainApp);

        orbitalStationView = new OrbitalStationView(this);
        espionageView = new EspionageView(this);
        refinementView = new RefinementView(this);
        battlePlaybackView = new TacticalBattlePlaybackView(this);

        terraformingView = new TerraformingView(this);
        terraformingView.setHumanController(humanController);
        terraformingView.setPlayerEmpireId(playerEmpireId);

        megastructureView = new MegastructureView(this);
        megastructureView.setHumanController(humanController);
        megastructureView.setPlayerEmpireId(playerEmpireId);

        galacticSenateView = new GalacticSenateView(this);
        galacticSenateView.setHumanController(humanController);
        galacticSenateView.setPlayerEmpireId(playerEmpireId);

        galaxyCanvasView = new GalaxyCanvasView(this);
        galaxyCanvasView.setHumanController(humanController);
        galaxyCanvasView.setPlayerEmpireId(playerEmpireId);
        scenarioEditorView = new ScenarioEditorView(this, mainApp != null ? mainApp.getEngine().getAudioSynthesizer() : null);
        victoryDefeatView = new VictoryDefeatView(this);

        com.spaceconquest.engine.audio.AudioSynthesizer audioSynth = mainApp != null && mainApp.getEngine() != null ? mainApp.getEngine().getAudioSynthesizer() : new com.spaceconquest.engine.audio.AudioSynthesizer();
        audioPlaybackManager = new AudioPlaybackManager(audioSynth);

        combatArenaView = new TacticalCombatArenaView(this, audioSynth);
        combatArenaView.setHumanController(humanController);

        tutorialView = new TutorialOnboardingView(this, combatArenaView);
        tutorialView.setHumanController(humanController);

        empireWizardView = new EmpireCreationWizardView(this, audioSynth);
        empireWizardView.setHumanController(humanController);

        audioSettingsView = new AudioSettingsView(this, audioSynth);
        screenSettingsView = new ScreenSettingsView(this, ScreenSettingsManager.getInstance());

        clockLabel = new Label();
        speedLabel = new Label();
        detailPanel = new VBox(8);
        detailTitle = new Label();
        detailText = new Label();

        double scale = ScreenSettingsManager.getInstance().getUiScale();
        root.getTransforms().setAll(new javafx.scene.transform.Scale(scale, scale, 0, 0));
        root.setPadding(new Insets(10));
        root.setAlignment(Pos.CENTER_LEFT);
        root.setStyle("-fx-background-color: rgba(12, 20, 42, 0.92); -fx-background-radius: 6;"
                + " -fx-border-color: rgba(120, 170, 255, 0.55); -fx-border-radius: 6;");
        root.setPrefWidth((getAppWidth() - 20) / scale);

        HBox navigation = new HBox(6);
        navigation.setAlignment(Pos.CENTER_LEFT);
        navigation.getChildren().addAll(
                empireButton(),
                diplomacyButton(),
                techButton(),
                shipyardButton(),
                fleetsButton(),
                commercialHubButton(),
                espionageButton(),
                senateButton(),
                canvasButton(),
                combatButton(),
                tutorialButton(),
                galaxyButton());

        VBox timeView = new VBox(2, clockLabel, speedLabel);
        timeView.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(timeView, Priority.ALWAYS);

        Button slower = smallButton("−");
        slower.setTooltip(new javafx.scene.control.Tooltip("Decrease game speed"));
        slower.setOnAction(e -> changeSpeed(-1));
        Button pause = smallButton("Ⅱ");
        pause.setTooltip(new javafx.scene.control.Tooltip("Pause or resume game time"));
        pause.setOnAction(e -> togglePaused(pause));
        Button faster = smallButton("+");
        faster.setTooltip(new javafx.scene.control.Tooltip("Increase game speed"));
        faster.setOnAction(e -> changeSpeed(1));

        Button gameMenu = new Button("Game menu");
        gameMenu.setStyle(buttonStyle());
        gameMenu.setPrefHeight(52);
        gameMenu.setCursor(javafx.scene.Cursor.HAND);
        gameMenu.setOnAction(e -> toggleGameMenu());

        HBox timeControls = new HBox(4, timeView, slower, pause, faster, gameMenu);
        timeControls.setAlignment(Pos.CENTER_RIGHT);
        navigation.getChildren().add(timeControls);
        root.getChildren().add(navigation);

        detailTitle.setTextFill(Color.WHITE);
        detailTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        detailText.setTextFill(Color.LIGHTGRAY);
        detailText.setWrapText(true);
        Button close = smallButton("X");
        close.setCursor(javafx.scene.Cursor.HAND);
        close.setOnAction(e -> {
            detailPanel.setVisible(false);
            closePage();
        });
        HBox detailHeader = new HBox(detailTitle, close);
        detailHeader.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(detailTitle, Priority.ALWAYS);
        detailPanel.getTransforms().setAll(new javafx.scene.transform.Scale(scale, scale, 0, 0));
        detailPanel.getChildren().addAll(detailHeader, detailText);
        detailPanel.setPadding(new Insets(14));
        detailPanel.setPrefWidth(300);
        detailPanel.setTranslateX(20 * scale);
        detailPanel.setTranslateY(95 * scale);
        detailPanel.setVisible(false);
        detailPanel.setStyle("-fx-background-color: rgba(25, 38, 75, 0.94); -fx-background-radius: 6;"
                + " -fx-border-color: rgba(120, 170, 255, 0.7); -fx-border-radius: 6;");

        setupOverlayEventInterception(root);
        setupOverlayEventInterception(detailPanel);

        if (root.getParent() == null) {
            root.setTranslateX(10);
            root.setTranslateY(10);
            addUINode(root);
        }
        if (detailPanel.getParent() == null) {
            addUINode(detailPanel);
        }

        overlayNodes.clear();
        centerAndAddOverlay(techView.getRoot(), 440, 340);
        centerAndAddOverlay(gameMenuView.getRoot(), 200, 250);
        centerAndAddOverlay(galaxyListView.getRoot(), 500, 400);
        centerAndAddOverlay(empireView.getRoot(), 480, 360);
        centerAndAddOverlay(corporateView.getRoot(), 375, 275);
        centerAndAddOverlay(diplomacyView.getRoot(), 375, 275);
        centerAndAddOverlay(commercialHubView.getRoot(), 375, 275);
        centerAndAddOverlay(colonyManagementView.getRoot(), 460, 350);
        centerAndAddOverlay(campaignManagerView.getRoot(), 450, 340);
        centerAndAddOverlay(orbitalStationView.getRoot(), 460, 350);
        centerAndAddOverlay(espionageView.getRoot(), 460, 350);
        centerAndAddOverlay(refinementView.getRoot(), 460, 350);
        centerAndAddOverlay(battlePlaybackView.getRoot(), 470, 360);
        centerAndAddOverlay(industryView.getRoot(), 460, 350);
        centerAndAddOverlay(shipDesignerView.getRoot(), 460, 350);
        centerAndAddOverlay(fleetManagementView.getRoot(), 460, 350);
        centerAndAddOverlay(planetDetailView.getRoot(), 460, 350);
        centerAndAddOverlay(terraformingView.getRoot(), 460, 350);
        centerAndAddOverlay(megastructureView.getRoot(), 460, 350);
        centerAndAddOverlay(galacticSenateView.getRoot(), 460, 350);
        centerAndAddOverlay(galaxyCanvasView.getRoot(), 480, 360);
        centerAndAddOverlay(scenarioEditorView.getRoot(), 390, 300);
        centerAndAddOverlay(victoryDefeatView.getRoot(), 375, 260);
        centerAndAddOverlay(tutorialView.getRoot(), 425, 310);
        centerAndAddOverlay(combatArenaView.getRoot(), 490, 360);
        centerAndAddOverlay(empireWizardView.getRoot(), 480, 370);
        centerAndAddOverlay(audioSettingsView.getRoot(), 300, 240);
        centerAndAddOverlay(screenSettingsView.getRoot(), 300, 240);

        updateClockLabels();
        restartClock();
    }

    public static void setupOverlayEventInterception(Region node) {
        if (node == null) return;
        node.setPickOnBounds(true);
        node.addEventHandler(MouseEvent.MOUSE_PRESSED, Event::consume);
        node.addEventHandler(MouseEvent.MOUSE_RELEASED, Event::consume);
        node.addEventHandler(MouseEvent.MOUSE_CLICKED, Event::consume);
        node.addEventHandler(MouseEvent.MOUSE_DRAGGED, Event::consume);
        node.addEventHandler(ScrollEvent.SCROLL, Event::consume);
    }

    private void centerAndAddOverlay(VBox node, double halfW, double halfH) {
        if (node != null) {
            setupOverlayEventInterception(node);
            if (!overlayNodes.contains(node)) {
                overlayNodes.add(node);
            }
            if (node.getParent() == null) {
                double scale = ScreenSettingsManager.getInstance().getUiScale();
                double targetWidth = Math.max(300, (getAppWidth() / scale) - 20);
                double targetHeight = Math.max(200, (getAppHeight() / scale) - 80);
                node.setPrefSize(targetWidth, targetHeight);
                node.setMinSize(targetWidth, targetHeight);
                node.setMaxSize(targetWidth, targetHeight);
                node.getTransforms().setAll(new javafx.scene.transform.Scale(scale, scale, 0, 0));
                node.setTranslateX(10 * scale);
                node.setTranslateY(70 * scale);
                addUINode(node);
            }
        }
    }

    public boolean isAnyOverlayVisible() {
        if (detailPanel != null && detailPanel.isVisible()) {
            return true;
        }
        for (VBox overlay : overlayNodes) {
            if (overlay != null && overlay.isVisible()) {
                return true;
            }
        }
        return false;
    }

    public void hideAllPanels() {
        detailPanel.setVisible(false);
        if (techView != null) techView.hide();
        if (industryView != null) industryView.hide();
        if (shipDesignerView != null) shipDesignerView.hide();
        if (fleetManagementView != null) fleetManagementView.hide();
        if (planetDetailView != null) planetDetailView.hide();
        if (galaxyListView != null) galaxyListView.hide();
        if (gameMenuView != null) gameMenuView.hide();
        if (empireView != null) empireView.hide();
        if (corporateView != null) corporateView.hide();
        if (diplomacyView != null) diplomacyView.hide();
        if (commercialHubView != null) commercialHubView.hide();
        if (colonyManagementView != null) colonyManagementView.hide();
        if (campaignManagerView != null) campaignManagerView.hide();
        if (orbitalStationView != null) orbitalStationView.hide();
        if (espionageView != null) espionageView.hide();
        if (refinementView != null) refinementView.hide();
        if (battlePlaybackView != null) battlePlaybackView.hide();
        if (terraformingView != null) terraformingView.hide();
        if (megastructureView != null) megastructureView.hide();
        if (galacticSenateView != null) galacticSenateView.hide();
        if (galaxyCanvasView != null) galaxyCanvasView.hide();
        if (scenarioEditorView != null) scenarioEditorView.hide();
        if (victoryDefeatView != null) victoryDefeatView.hide();
        if (tutorialView != null) tutorialView.hide();
        if (combatArenaView != null) combatArenaView.hide();
        if (empireWizardView != null) empireWizardView.hide();
        if (audioSettingsView != null) audioSettingsView.hide();
        if (screenSettingsView != null) screenSettingsView.hide();
    }

    public void toggleGameMenu() {
        if (gameMenuView != null) {
            if (gameMenuView.getRoot().isVisible()) {
                gameMenuView.hide();
            } else {
                hideAllPanels();
                openPage();
                gameMenuView.show();
            }
        }
    }

    private Button empireButton() {
        Button button = new Button("Empire\nview");
        button.setPrefWidth(105);
        button.setPrefHeight(52);
        button.setWrapText(true);
        button.setAlignment(Pos.CENTER);
        button.setStyle(buttonStyle());
        button.setOnAction(e -> {
            hideAllPanels();
            openPage();
            if (mainApp != null && mainApp.getEngine() != null) {
                empireView.updateData(mainApp.getEngine().getGameState());
            }
            empireView.show();
        });
        return button;
    }

    private Button diplomacyButton() {
        Button button = new Button("Diplomacy\npacts");
        button.setPrefWidth(105);
        button.setPrefHeight(52);
        button.setWrapText(true);
        button.setAlignment(Pos.CENTER);
        button.setStyle(buttonStyle());
        button.setOnAction(e -> {
            hideAllPanels();
            openPage();
            diplomacyView.show();
        });
        return button;
    }

    private Button techButton() {
        Button button = new Button("Technology\nresearch");
        button.setPrefWidth(105);
        button.setPrefHeight(52);
        button.setWrapText(true);
        button.setAlignment(Pos.CENTER);
        button.setStyle(buttonStyle());
        button.setOnAction(e -> {
            hideAllPanels();
            openPage();
            if (mainApp != null && mainApp.getEngine() != null) {
                techView.setResearchProjects(mainApp.getEngine().getGameState().researchProjects());
            }
            techView.show();
        });
        return button;
    }

    private Button industryButton() {
        Button button = new Button("Industries\nproduction");
        button.setPrefWidth(105);
        button.setPrefHeight(52);
        button.setWrapText(true);
        button.setAlignment(Pos.CENTER);
        button.setStyle(buttonStyle());
        button.setOnAction(e -> {
            hideAllPanels();
            openPage();
            if (mainApp != null && mainApp.getEngine() != null) {
                GameState st = mainApp.getEngine().getGameState();
                industryView.updateData(st.industrialFacilities(), st.expansionProjects());
            }
            industryView.show();
        });
        return button;
    }

    private Button shipyardButton() {
        Button button = new Button("Shipyard\ndesigner");
        button.setPrefWidth(105);
        button.setPrefHeight(52);
        button.setWrapText(true);
        button.setAlignment(Pos.CENTER);
        button.setStyle(buttonStyle());
        button.setOnAction(e -> {
            hideAllPanels();
            openPage();
            if (mainApp != null && mainApp.getEngine() != null) {
                shipDesignerView.updateDesigns(mainApp.getEngine().getGameState().shipDesigns());
            }
            shipDesignerView.show();
        });
        return button;
    }

    private Button fleetsButton() {
        Button button = new Button("Fleets\nnaval hub");
        button.setPrefWidth(105);
        button.setPrefHeight(52);
        button.setWrapText(true);
        button.setAlignment(Pos.CENTER);
        button.setStyle(buttonStyle());
        button.setOnAction(e -> {
            hideAllPanels();
            openPage();
            if (mainApp != null && mainApp.getEngine() != null) {
                fleetManagementView.updateFleets(mainApp.getEngine().getGameState().fleets());
            }
            fleetManagementView.show();
        });
        return button;
    }

    private Button corporateButton() {
        Button button = new Button("Corporations\nregistry");
        button.setPrefWidth(105);
        button.setPrefHeight(52);
        button.setWrapText(true);
        button.setAlignment(Pos.CENTER);
        button.setStyle(buttonStyle());
        button.setOnAction(e -> {
            hideAllPanels();
            openPage();
            if (mainApp != null && mainApp.getEngine() != null) {
                empireView.updateData(mainApp.getEngine().getGameState());
            }
            empireView.show(EmpireView.Tab.CORPORATIONS);
        });
        return button;
    }

    private Button commercialHubButton() {
        Button button = new Button("Trade\nlogistics");
        button.setPrefWidth(105);
        button.setPrefHeight(52);
        button.setWrapText(true);
        button.setAlignment(Pos.CENTER);
        button.setStyle(buttonStyle());
        button.setOnAction(e -> {
            hideAllPanels();
            openPage();
            if (mainApp != null && mainApp.getEngine() != null) {
                GameState st = mainApp.getEngine().getGameState();
                commercialHubView.show(st.commercialHubs(), st.tradeRoutes());
            } else {
                commercialHubView.show(List.of(), List.of());
            }
        });
        return button;
    }

    private Button colonyButton() {
        Button button = new Button("Colonies\nhabitation");
        button.setPrefWidth(105);
        button.setPrefHeight(52);
        button.setWrapText(true);
        button.setAlignment(Pos.CENTER);
        button.setStyle(buttonStyle());
        button.setOnAction(e -> {
            hideAllPanels();
            openPage();
            if (mainApp != null && mainApp.getEngine() != null) {
                colonyManagementView.updateData(mainApp.getEngine().getAllPlanets(), List.of());
            }
            colonyManagementView.show();
        });
        return button;
    }

    private Button planetDetailButton() {
        Button button = new Button("Planet\nsurvey");
        button.setPrefWidth(105);
        button.setPrefHeight(52);
        button.setWrapText(true);
        button.setAlignment(Pos.CENTER);
        button.setStyle(buttonStyle());
        button.setOnAction(e -> {
            hideAllPanels();
            openPage();
            if (mainApp != null && mainApp.getEngine() != null) {
                GameState st = mainApp.getEngine().getGameState();
                planetDetailView.updateData(st.geologicalDeposits(), st.powerGrids());
            }
            planetDetailView.show();
        });
        return button;
    }

    private Button stationsButton() {
        Button button = new Button("Stations\norbital hubs");
        button.setPrefWidth(105);
        button.setPrefHeight(52);
        button.setWrapText(true);
        button.setAlignment(Pos.CENTER);
        button.setStyle(buttonStyle());
        button.setOnAction(e -> {
            hideAllPanels();
            openPage();
            if (mainApp != null && mainApp.getEngine() != null) {
                empireView.updateData(mainApp.getEngine().getGameState());
            }
            empireView.show(EmpireView.Tab.STATIONS);
        });
        return button;
    }

    private Button espionageButton() {
        Button button = new Button("Espionage\nintelligence");
        button.setPrefWidth(105);
        button.setPrefHeight(52);
        button.setWrapText(true);
        button.setAlignment(Pos.CENTER);
        button.setStyle(buttonStyle());
        button.setOnAction(e -> {
            hideAllPanels();
            openPage();
            if (mainApp != null && mainApp.getEngine() != null) {
                GameState st = mainApp.getEngine().getGameState();
                espionageView.updateData(st.sleeperAgents(), st.espionageOperations(), st.pirateBases());
            }
            espionageView.show();
        });
        return button;
    }

    private Button refinementButton() {
        Button button = new Button("Refinement\nalloys");
        button.setPrefWidth(105);
        button.setPrefHeight(52);
        button.setWrapText(true);
        button.setAlignment(Pos.CENTER);
        button.setStyle(buttonStyle());
        button.setOnAction(e -> {
            hideAllPanels();
            openPage();
            refinementView.show();
        });
        return button;
    }

    private Button terraformingButton() {
        Button button = new Button("Terraform\natmosphere");
        button.setPrefWidth(105);
        button.setPrefHeight(52);
        button.setWrapText(true);
        button.setAlignment(Pos.CENTER);
        button.setStyle(buttonStyle());
        button.setOnAction(e -> {
            hideAllPanels();
            openPage();
            if (mainApp != null && mainApp.getEngine() != null) {
                terraformingView.updateData(mainApp.getEngine().getAtmospheres(), mainApp.getEngine().getTerraformingProjects());
            }
            terraformingView.show();
        });
        return button;
    }

    private Button megastructureButton() {
        Button button = new Button("Megastructures\ndysons");
        button.setPrefWidth(105);
        button.setPrefHeight(52);
        button.setWrapText(true);
        button.setAlignment(Pos.CENTER);
        button.setStyle(buttonStyle());
        button.setOnAction(e -> {
            hideAllPanels();
            openPage();
            if (mainApp != null && mainApp.getEngine() != null) {
                megastructureView.updateData(mainApp.getEngine().getMegastructures());
            }
            megastructureView.show();
        });
        return button;
    }

    private Button senateButton() {
        Button button = new Button("Galactic\nSenate");
        button.setPrefWidth(105);
        button.setPrefHeight(52);
        button.setWrapText(true);
        button.setAlignment(Pos.CENTER);
        button.setStyle(buttonStyle());
        button.setOnAction(e -> {
            hideAllPanels();
            openPage();
            if (mainApp != null && mainApp.getEngine() != null) {
                galacticSenateView.updateData(mainApp.getEngine().getGalacticCommunity());
            }
            galacticSenateView.show();
        });
        return button;
    }

    private Button canvasButton() {
        Button button = new Button("Tactical\ncanvas");
        button.setPrefWidth(105);
        button.setPrefHeight(52);
        button.setWrapText(true);
        button.setAlignment(Pos.CENTER);
        button.setStyle(buttonStyle());
        button.setOnAction(e -> {
            hideAllPanels();
            openPage();
            if (mainApp != null && mainApp.getSolarSystems() != null && mainApp.getEngine() != null) {
                GameState gs = mainApp.getEngine().getGameState();
                galaxyCanvasView.updateData(mainApp.getSolarSystems(), gs.fleets(), gs.megastructures(), gs.fogOfWarStates());
            } else if (mainApp != null && mainApp.getSolarSystems() != null) {
                galaxyCanvasView.updateData(mainApp.getSolarSystems(), List.of(), List.of(), List.of());
            }
            galaxyCanvasView.show();
        });
        return button;
    }

    private Button combatButton() {
        Button button = new Button("Combat\nplayback");
        button.setPrefWidth(105);
        button.setPrefHeight(52);
        button.setWrapText(true);
        button.setAlignment(Pos.CENTER);
        button.setStyle(buttonStyle());
        button.setOnAction(e -> {
            hideAllPanels();
            openPage();
            battlePlaybackView.show();
        });
        return button;
    }

    private Button tutorialButton() {
        Button button = new Button("Tutorial");
        button.setPrefWidth(105);
        button.setPrefHeight(52);
        button.setWrapText(true);
        button.setAlignment(Pos.CENTER);
        button.setStyle("-fx-background-color: #00cec9; -fx-text-fill: black; -fx-font-weight: bold; -fx-background-radius: 4; -fx-cursor: hand;");
        button.setCursor(javafx.scene.Cursor.HAND);
        button.setOnAction(e -> {
            hideAllPanels();
            openPage();
            tutorialView.show();
        });
        return button;
    }

    private Button galaxyButton() {
        Button button = new Button("Galaxy\nmap view");
        button.setPrefWidth(105);
        button.setPrefHeight(52);
        button.setWrapText(true);
        button.setAlignment(Pos.CENTER);
        button.setStyle(buttonStyle());
        button.setOnAction(e -> {
            hideAllPanels();
            galaxyListView.show(mainApp.getSolarSystems());
        });
        return button;
    }

    public TacticalCombatArenaView getCombatArenaView() {
        return combatArenaView;
    }

    public EmpireCreationWizardView getEmpireWizardView() {
        return empireWizardView;
    }

    public AudioSettingsView getAudioSettingsView() {
        return audioSettingsView;
    }

    public ScreenSettingsView getScreenSettingsView() {
        return screenSettingsView;
    }

    public AudioPlaybackManager getAudioPlaybackManager() {
        return audioPlaybackManager;
    }

    private Button arenaButton() {
        Button button = new Button("Tactical\narena");
        button.setPrefWidth(105);
        button.setPrefHeight(52);
        button.setWrapText(true);
        button.setAlignment(Pos.CENTER);
        button.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 4; -fx-cursor: hand;");
        button.setCursor(javafx.scene.Cursor.HAND);
        button.setOnAction(e -> {
            hideAllPanels();
            openPage();
            combatArenaView.show();
        });
        return button;
    }

    private Button smallButton(String text) {
        Button button = new Button(text);
        button.setMinWidth(30);
        button.setStyle(buttonStyle());
        button.setCursor(javafx.scene.Cursor.HAND);
        return button;
    }

    private String buttonStyle() {
        return "-fx-background-color: #263d69; -fx-text-fill: white; -fx-font-weight: bold;"
                + " -fx-background-radius: 4; -fx-cursor: hand;";
    }

    private void changeSpeed(int change) {
        speedIndex = Math.max(0, Math.min(SPEED_NAMES.length - 1, speedIndex + change));
        updateClockLabels();
        restartClock();
    }

    private void togglePaused(Button pause) {
        paused = !paused;
        manuallyPaused = paused;
        pause.setText(paused ? "▶" : "Ⅱ");
        restartClock();
    }

    public void openPage() {
        if (!paused) {
            savedSpeedIndex = speedIndex;
            paused = true;
            restartClock();
            updateClockLabels();
        }
    }

    public void closePage() {
        if (!manuallyPaused) {
            paused = false;
            speedIndex = savedSpeedIndex;
            restartClock();
            updateClockLabels();
        }
    }

    private void restartClock() {
        clock.stop();
        if (!paused) {
            clock.getKeyFrames().setAll(new KeyFrame(Duration.seconds(1), e -> {
                gameTime = gameTime.plusMinutes(MINUTES_PER_TICK[speedIndex]);
                updateClockLabels();

                if (mainApp != null && mainApp.getEngine() != null) {
                    GameState currentState = mainApp.getEngine().getGameState();

                    // 1. Trigger autonomous AI decisions
                    if (empireAIController != null) empireAIController.onGameStateUpdate(currentState);
                    if (corporationAIController != null) corporationAIController.onGameStateUpdate(currentState);
                    if (shadowSyndicateAIController != null) shadowSyndicateAIController.onGameStateUpdate(currentState);

                    // 2. Drain all staged commands and step turn
                    humanController.getCommandQueue().processCommands(mainApp.getEngine());
                    mainApp.getEngine().stepTurn();
                    GameState state = mainApp.getEngine().getGameState();
                    humanController.onGameStateUpdate(state);

                    // 3. Refresh active view panels
                    if (techView != null) techView.setResearchProjects(state.researchProjects());
                    if (industryView != null) industryView.updateData(state.industrialFacilities(), state.expansionProjects());
                    if (shipDesignerView != null) shipDesignerView.updateDesigns(state.shipDesigns());
                    if (fleetManagementView != null) fleetManagementView.updateFleets(state.fleets());
                    if (galacticSenateView != null) galacticSenateView.updateData(state.galacticCommunity());
                    if (megastructureView != null) megastructureView.updateData(state.megastructures());
                    if (terraformingView != null) terraformingView.updateData(mainApp.getEngine().getAtmospheres(), state.terraformingProjects());

                    // 4. Check for Victory / Defeat conditions
                    if (victoryDefeatView != null && !victoryDefeatView.isSandboxModeActive() && mainApp.getEngine().getVictoryConditionChecker() != null) {
                        VictoryConditionChecker.VictoryCheckResult vRes = mainApp.getEngine().getVictoryConditionChecker().evaluateVictory(
                                state, mainApp.getEngine().getCampaignSetup(), state.galacticCommunity(), state.megastructures()
                        );
                        if (vRes.isVictoryAchieved()) {
                            victoryDefeatView.showVictory(vRes, playerEmpireId);
                        }
                    }
                }
            }));
            clock.setCycleCount(Timeline.INDEFINITE);
            clock.play();
        }
    }

    private void updateClockLabels() {
        clockLabel.setText(gameTime.format(TIME_FORMAT));
        clockLabel.setTextFill(Color.WHITE);
        clockLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        speedLabel.setText(paused ? "PAUSED" : SPEED_NAMES[speedIndex]);
        speedLabel.setTextFill(paused ? Color.SALMON : Color.LIGHTGRAY);
        speedLabel.setStyle("-fx-font-size: 11px;");
    }
}
