package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.ai.CorporationAIController;
import com.spaceconquest.control.ai.EmpireAIController;
import com.spaceconquest.control.ai.ShadowSyndicateAIController;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.scenario.VictoryConditionChecker;
import com.spaceconquest.frontend.empire.EmpireView;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.almasb.fxgl.dsl.FXGL.*;

/**
 * The top-level navigation, command pipeline and real-time game-clock loop for the space conquest game.
 */
public class Menubar {

    private VBox root;
    private VBox detailPanel;
    private Label detailTitle;
    private Label detailText;
    private final List<VBox> overlayNodes = new ArrayList<>();

    private final HumanController humanController = new HumanController();
    private EmpireAIController empireAIController;
    private CorporationAIController corporationAIController;
    private ShadowSyndicateAIController shadowSyndicateAIController;
    private String playerEmpireId = "terran_confederation";

    private final MenubarViewRegistry viewRegistry = new MenubarViewRegistry();
    private final MenubarClockController clockController = new MenubarClockController();
    private final MenubarNavigation navigation = new MenubarNavigation();

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
            viewRegistry.setPlayerEmpireId(empireId);
        }
    }

    public void updateAllViews(GameState state) {
        viewRegistry.updateAllViews(state, mainApp, humanController,
                empireAIController, corporationAIController, shadowSyndicateAIController, playerEmpireId);
    }

    public TechnologyView getTechView() { return viewRegistry.getTechView(); }
    public GameMenuView getGameMenuView() { return viewRegistry.getGameMenuView(); }
    public GalaxyListView getGalaxyListView() { return viewRegistry.getGalaxyListView(); }
    public EmpireView getEmpireView() { return viewRegistry.getEmpireView(); }
    public CorporateView getCorporateView() { return viewRegistry.getCorporateView(); }
    public DiplomacyView getDiplomacyView() { return viewRegistry.getDiplomacyView(); }
    public CommercialHubView getCommercialHubView() { return viewRegistry.getCommercialHubView(); }
    public ColonyManagementView getColonyManagementView() { return viewRegistry.getColonyManagementView(); }
    public CampaignManagerView getCampaignManagerView() { return viewRegistry.getCampaignManagerView(); }
    public OrbitalStationView getOrbitalStationView() { return viewRegistry.getOrbitalStationView(); }
    public EspionageView getEspionageView() { return viewRegistry.getEspionageView(); }
    public RefinementView getRefinementView() { return viewRegistry.getRefinementView(); }
    public TacticalBattlePlaybackView getBattlePlaybackView() { return viewRegistry.getBattlePlaybackView(); }
    public TerraformingView getTerraformingView() { return viewRegistry.getTerraformingView(); }
    public MegastructureView getMegastructureView() { return viewRegistry.getMegastructureView(); }
    public GalacticSenateView getGalacticSenateView() { return viewRegistry.getGalacticSenateView(); }
    public GalaxyCanvasView getGalaxyCanvasView() { return viewRegistry.getGalaxyCanvasView(); }
    public ScenarioEditorView getScenarioEditorView() { return viewRegistry.getScenarioEditorView(); }
    public IndustryView getIndustryView() { return viewRegistry.getIndustryView(); }
    public ShipDesignerView getShipDesignerView() { return viewRegistry.getShipDesignerView(); }
    public FleetManagementView getFleetManagementView() { return viewRegistry.getFleetManagementView(); }
    public PlanetDetailView getPlanetDetailView() { return viewRegistry.getPlanetDetailView(); }
    public VictoryDefeatView getVictoryDefeatView() { return viewRegistry.getVictoryDefeatView(); }
    public TutorialOnboardingView getTutorialView() { return viewRegistry.getTutorialView(); }
    public TacticalCombatArenaView getCombatArenaView() { return viewRegistry.getCombatArenaView(); }
    public EmpireCreationWizardView getEmpireWizardView() { return viewRegistry.getEmpireWizardView(); }
    public AudioSettingsView getAudioSettingsView() { return viewRegistry.getAudioSettingsView(); }
    public ScreenSettingsView getScreenSettingsView() { return viewRegistry.getScreenSettingsView(); }
    public AudioPlaybackManager getAudioPlaybackManager() { return viewRegistry.getAudioPlaybackManager(); }

    public int getSpeedIndex() {
        return clockController.getSpeedIndex();
    }

    public LocalDateTime getGameTime() {
        return clockController.getGameTime();
    }

    public void restoreTime(LocalDateTime time, int speed) {
        clockController.restoreTime(time, speed);
    }

    public void showGameStartDialog() {
        startNewGameSetup();
    }

    public void startNewGameSetup() {
        hideAllPanels();
        openPage();
        if (getScenarioEditorView() != null) {
            getScenarioEditorView().show();
        }
    }

    public void build(Main mainApp) {
        this.mainApp = mainApp;
        root = new VBox(8);

        empireAIController = new EmpireAIController("vulkan_forge", humanController.getCommandQueue());
        corporationAIController = new CorporationAIController("corp_sol_extraction", humanController.getCommandQueue());
        shadowSyndicateAIController = new ShadowSyndicateAIController("shadow_syndicate_sol", humanController.getCommandQueue());

        viewRegistry.initViews(this, mainApp, humanController, playerEmpireId);

        double scale = ScreenSettingsManager.getInstance().getUiScale();
        root.getTransforms().setAll(new javafx.scene.transform.Scale(scale, scale, 0, 0));
        root.setPadding(new Insets(10));
        root.setAlignment(Pos.CENTER_LEFT);
        root.setStyle("-fx-background-color: rgba(12, 20, 42, 0.92); -fx-background-radius: 6;"
                + " -fx-border-color: rgba(120, 170, 255, 0.55); -fx-border-radius: 6;");
        root.setPrefWidth((getAppWidth() - 20) / scale);

        HBox navigationBox = buildToolbar(scale);
        root.getChildren().add(navigationBox);

        buildDetailPanel(scale);

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

        registerAllOverlays();
        clockController.init(this::handleSimulationTick);
    }

    private HBox buildToolbar(double scale) {
        HBox toolbar = new HBox(6);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        toolbar.getChildren().addAll(
                buildNavButton("Empire\nview", this::showEmpireView),
                buildNavButton("Diplomacy\npacts", () -> getDiplomacyView().show()),
                buildNavButton("Technology\nresearch", this::showTechView),
                buildNavButton("Shipyard\ndesigner", this::showShipyardView),
                buildNavButton("Fleets\nnaval hub", this::showFleetsView),
                buildNavButton("Trade\nlogistics", this::showCommercialHubView),
                buildNavButton("Espionage\nintelligence", this::showEspionageView),
                buildNavButton("Galactic\nSenate", this::showSenateView),
                buildNavButton("Tactical\ncanvas", this::showCanvasView),
                buildNavButton("Combat\nplayback", () -> getBattlePlaybackView().show()),
                navigation.createSpecialButton("Tutorial", MenubarNavigation.STYLE_TUTORIAL_NORMAL, this::showTutorialView),
                navigation.createNavButton("Galaxy\nmap view", this::showGalaxyView)
        );

        VBox timeView = new VBox(2, clockController.getClockLabel(), clockController.getSpeedLabel());
        timeView.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(timeView, Priority.ALWAYS);

        Button slower = MenubarNavigation.createSmallButton("−");
        slower.setTooltip(new Tooltip("Decrease game speed"));
        slower.setOnAction(e -> clockController.changeSpeed(-1));

        Button pause = MenubarNavigation.createSmallButton("Ⅱ");
        pause.setTooltip(new Tooltip("Pause or resume game time"));
        pause.setOnAction(e -> clockController.togglePaused(pause));

        Button faster = MenubarNavigation.createSmallButton("+");
        faster.setTooltip(new Tooltip("Increase game speed"));
        faster.setOnAction(e -> clockController.changeSpeed(1));

        Button gameMenu = new Button("Game menu");
        gameMenu.setStyle(MenubarNavigation.STYLE_NORMAL);
        gameMenu.setPrefHeight(52);
        gameMenu.setCursor(javafx.scene.Cursor.HAND);
        gameMenu.setOnAction(e -> toggleGameMenu());

        HBox timeControls = new HBox(4, timeView, slower, pause, faster, gameMenu);
        timeControls.setAlignment(Pos.CENTER_RIGHT);
        toolbar.getChildren().add(timeControls);

        return toolbar;
    }

    private Button buildNavButton(String text, Runnable action) {
        return navigation.createNavButton(text, () -> {
            hideAllPanels();
            openPage();
            action.run();
        });
    }

    private void showTutorialView() {
        hideAllPanels();
        openPage();
        getTutorialView().show();
    }

    private void showGalaxyView() {
        hideAllPanels();
        if (mainApp != null) {
            getGalaxyListView().show(mainApp.getSolarSystems());
        }
    }

    private void showEmpireView() {
        if (mainApp != null && mainApp.getEngine() != null) {
            getEmpireView().updateData(mainApp.getEngine().getGameState());
        }
        getEmpireView().show();
    }

    private void showTechView() {
        if (mainApp != null && mainApp.getEngine() != null) {
            getTechView().setResearchProjects(mainApp.getEngine().getGameState().researchProjects());
        }
        getTechView().show();
    }

    private void showShipyardView() {
        if (mainApp != null && mainApp.getEngine() != null) {
            getShipDesignerView().updateDesigns(mainApp.getEngine().getGameState().shipDesigns());
        }
        getShipDesignerView().show();
    }

    private void showFleetsView() {
        if (mainApp != null && mainApp.getEngine() != null) {
            getFleetManagementView().updateFleets(mainApp.getEngine().getGameState().fleets());
        }
        getFleetManagementView().show();
    }

    private void showCommercialHubView() {
        if (mainApp != null && mainApp.getEngine() != null) {
            GameState st = mainApp.getEngine().getGameState();
            getCommercialHubView().show(st.commercialHubs(), st.tradeRoutes());
        } else {
            getCommercialHubView().show(List.of(), List.of());
        }
    }

    private void showEspionageView() {
        if (mainApp != null && mainApp.getEngine() != null) {
            GameState st = mainApp.getEngine().getGameState();
            getEspionageView().updateData(st.sleeperAgents(), st.espionageOperations(), st.pirateBases());
        }
        getEspionageView().show();
    }

    private void showSenateView() {
        if (mainApp != null && mainApp.getEngine() != null) {
            getGalacticSenateView().updateData(mainApp.getEngine().getGalacticCommunity());
        }
        getGalacticSenateView().show();
    }

    private void showCanvasView() {
        if (mainApp != null && mainApp.getSolarSystems() != null && mainApp.getEngine() != null) {
            GameState gs = mainApp.getEngine().getGameState();
            getGalaxyCanvasView().updateData(mainApp.getSolarSystems(), gs.fleets(), gs.megastructures(), gs.fogOfWarStates());
        } else if (mainApp != null && mainApp.getSolarSystems() != null) {
            getGalaxyCanvasView().updateData(mainApp.getSolarSystems(), List.of(), List.of(), List.of());
        }
        getGalaxyCanvasView().show();
    }

    private void buildDetailPanel(double scale) {
        detailPanel = new VBox(8);
        detailTitle = new Label();
        detailText = new Label();

        detailTitle.setTextFill(Color.WHITE);
        detailTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        detailText.setTextFill(Color.LIGHTGRAY);
        detailText.setWrapText(true);

        Button close = MenubarNavigation.createSmallButton("X");
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
    }

    private void registerAllOverlays() {
        overlayNodes.clear();
        for (VBox node : viewRegistry.getAllOverlayRoots()) {
            centerAndAddOverlay(node);
        }
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

    private void centerAndAddOverlay(VBox node) {
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
        if (detailPanel != null) {
            detailPanel.setVisible(false);
        }
        navigation.setActiveButton(null);
        viewRegistry.hideAll();
    }

    public void toggleGameMenu() {
        if (getGameMenuView() != null) {
            if (getGameMenuView().getRoot().isVisible()) {
                getGameMenuView().hide();
            } else {
                hideAllPanels();
                openPage();
                getGameMenuView().show();
            }
        }
    }

    public void openPage() {
        clockController.openPage();
    }

    public void closePage() {
        clockController.closePage();
        navigation.setActiveButton(null);
    }

    private void handleSimulationTick() {
        if (mainApp != null && mainApp.getEngine() != null) {
            GameState currentState = mainApp.getEngine().getGameState();

            if (empireAIController != null) empireAIController.onGameStateUpdate(currentState);
            if (corporationAIController != null) corporationAIController.onGameStateUpdate(currentState);
            if (shadowSyndicateAIController != null) shadowSyndicateAIController.onGameStateUpdate(currentState);

            humanController.getCommandQueue().processCommands(mainApp.getEngine());
            mainApp.getEngine().stepTurn();
            GameState state = mainApp.getEngine().getGameState();
            humanController.onGameStateUpdate(state);

            viewRegistry.refreshOnTick(state, mainApp);

            if (getVictoryDefeatView() != null && !getVictoryDefeatView().isSandboxModeActive() 
                    && mainApp.getEngine().getVictoryConditionChecker() != null) {
                VictoryConditionChecker.VictoryCheckResult vRes = mainApp.getEngine().getVictoryConditionChecker().evaluateVictory(
                        state, mainApp.getEngine().getCampaignSetup(), state.galacticCommunity(), state.megastructures()
                );
                if (vRes.isVictoryAchieved()) {
                    getVictoryDefeatView().showVictory(vRes, playerEmpireId);
                }
            }
        }
    }
}
