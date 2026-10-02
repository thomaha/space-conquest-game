package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.audio.AudioSynthesizer;
import com.spaceconquest.frontend.empire.EmpireView;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages the instantiation, lifecycle, and data updates for all frontend view panels.
 */
public class MenubarViewRegistry {

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
    private String playerEmpireId = "terran_confederation";

    public void initViews(Menubar menubar, Main mainApp, HumanController humanController, String playerEmpireId) {
        if (playerEmpireId != null && !playerEmpireId.isEmpty()) {
            this.playerEmpireId = playerEmpireId;
        }
        techView = new TechnologyView(menubar);
        techView.setHumanController(humanController);
        techView.setPlayerEmpireId(playerEmpireId);

        industryView = PostConstructionInitializer.initialize(
                new IndustryView(menubar), IndustryView::initializeAfterConstruction);
        industryView.setHumanController(humanController);
        industryView.setPlayerEmpireId(playerEmpireId);

        shipDesignerView = PostConstructionInitializer.initialize(
                new ShipDesignerView(menubar), ShipDesignerView::initializeAfterConstruction);
        shipDesignerView.setHumanController(humanController);
        shipDesignerView.setPlayerEmpireId(playerEmpireId);

        fleetManagementView = PostConstructionInitializer.initialize(
                new FleetManagementView(menubar), FleetManagementView::initializeAfterConstruction);
        fleetManagementView.setHumanController(humanController);
        fleetManagementView.setPlayerEmpireId(playerEmpireId);

        colonyManagementView = PostConstructionInitializer.initialize(
                new ColonyManagementView(menubar), ColonyManagementView::initializeAfterConstruction);
        colonyManagementView.setHumanController(humanController);
        colonyManagementView.setPlayerEmpireId(playerEmpireId);

        planetDetailView = PostConstructionInitializer.initialize(
                new PlanetDetailView(menubar), PlanetDetailView::initializeAfterConstruction);
        planetDetailView.setHumanController(humanController);
        planetDetailView.setPlayerEmpireId(playerEmpireId);

        gameMenuView = PostConstructionInitializer.initialize(
                new GameMenuView(menubar), GameMenuView::initializeAfterConstruction);
        galaxyListView = PostConstructionInitializer.initialize(
                new GalaxyListView(menubar, mainApp), GalaxyListView::initializeAfterConstruction);
        empireView = PostConstructionInitializer.initialize(
                new EmpireView(menubar), EmpireView::initializeAfterConstruction);
        empireView.setHumanController(humanController);
        empireView.setPlayerEmpireId(playerEmpireId);
        corporateView = PostConstructionInitializer.initialize(
                new CorporateView(menubar), CorporateView::initializeAfterConstruction);
        diplomacyView = PostConstructionInitializer.initialize(
                new DiplomacyView(menubar), DiplomacyView::initializeAfterConstruction);
        diplomacyView.setHumanController(humanController);
        commercialHubView = PostConstructionInitializer.initialize(
                new CommercialHubView(menubar), CommercialHubView::initializeAfterConstruction);
        commercialHubView.setHumanController(humanController);
        commercialHubView.setPlayerEmpireId(playerEmpireId);

        campaignManagerView = PostConstructionInitializer.initialize(
                new CampaignManagerView(menubar), CampaignManagerView::initializeAfterConstruction);
        campaignManagerView.setMainApp(mainApp);

        orbitalStationView = PostConstructionInitializer.initialize(
                new OrbitalStationView(menubar), OrbitalStationView::initializeAfterConstruction);
        espionageView = PostConstructionInitializer.initialize(
                new EspionageView(menubar), EspionageView::initializeAfterConstruction);
        refinementView = PostConstructionInitializer.initialize(
                new RefinementView(menubar), RefinementView::initializeAfterConstruction);
        battlePlaybackView = PostConstructionInitializer.initialize(
                new TacticalBattlePlaybackView(menubar), TacticalBattlePlaybackView::initializeAfterConstruction);

        terraformingView = PostConstructionInitializer.initialize(
                new TerraformingView(menubar), TerraformingView::initializeAfterConstruction);
        terraformingView.setHumanController(humanController);
        terraformingView.setPlayerEmpireId(playerEmpireId);

        megastructureView = PostConstructionInitializer.initialize(
                new MegastructureView(menubar), MegastructureView::initializeAfterConstruction);
        megastructureView.setHumanController(humanController);
        megastructureView.setPlayerEmpireId(playerEmpireId);

        galacticSenateView = PostConstructionInitializer.initialize(
                new GalacticSenateView(menubar), GalacticSenateView::initializeAfterConstruction);
        galacticSenateView.setHumanController(humanController);
        galacticSenateView.setPlayerEmpireId(playerEmpireId);

        galaxyCanvasView = PostConstructionInitializer.initialize(
                new GalaxyCanvasView(menubar), GalaxyCanvasView::initializeAfterConstruction);
        galaxyCanvasView.setHumanController(humanController);
        galaxyCanvasView.setPlayerEmpireId(playerEmpireId);

        AudioSynthesizer audioSynth = mainApp != null && mainApp.getEngine() != null 
                ? mainApp.getEngine().getAudioSynthesizer() 
                : new AudioSynthesizer();
        audioPlaybackManager = PostConstructionInitializer.initialize(
                new AudioPlaybackManager(audioSynth), AudioPlaybackManager::initializeAfterConstruction);

        scenarioEditorView = PostConstructionInitializer.initialize(
                new ScenarioEditorView(menubar, audioSynth), ScenarioEditorView::initializeAfterConstruction);
        victoryDefeatView = PostConstructionInitializer.initialize(
                new VictoryDefeatView(menubar), VictoryDefeatView::initializeAfterConstruction);

        combatArenaView = PostConstructionInitializer.initialize(
                new TacticalCombatArenaView(menubar, audioSynth), TacticalCombatArenaView::initializeAfterConstruction);
        combatArenaView.setHumanController(humanController);

        tutorialView = PostConstructionInitializer.initialize(
                new TutorialOnboardingView(menubar, combatArenaView), TutorialOnboardingView::initializeAfterConstruction);
        tutorialView.setHumanController(humanController);

        empireWizardView = PostConstructionInitializer.initialize(
                new EmpireCreationWizardView(menubar, audioSynth), EmpireCreationWizardView::initializeAfterConstruction);
        empireWizardView.setHumanController(humanController);

        audioSettingsView = PostConstructionInitializer.initialize(
                new AudioSettingsView(menubar, audioSynth), AudioSettingsView::initializeAfterConstruction);
        screenSettingsView = PostConstructionInitializer.initialize(
                new ScreenSettingsView(menubar, ScreenSettingsManager.getInstance()), ScreenSettingsView::initializeAfterConstruction);
    }

    public void setPlayerEmpireId(String empireId) {
        if (empireId == null || empireId.isEmpty()) return;
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

    public void hideAll() {
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

    public void updateAllViews(GameState state, HumanController humanController, String playerEmpireId) {
        if (state == null) return;
        if (humanController != null) humanController.onGameStateUpdate(state);
        setPlayerEmpireId(playerEmpireId);
        refreshSnapshotViews(state);
    }

    public void refreshOnTick(GameState state) {
        if (state == null) return;
        refreshSnapshotViews(state);
    }

    private void refreshSnapshotViews(GameState state) {
        if (empireView != null) empireView.updateData(state);
        if (corporateView != null) corporateView.updateData(state);
        if (diplomacyView != null) diplomacyView.updateData(state);
        if (techView != null) {
            Empire playerEmpire = state.empires().stream()
                    .filter(e -> e.id().equals(playerEmpireId))
                    .findFirst().orElse(null);
            techView.updateData(state.researchProjects(), state.technologyExchangeRoutes(), state.systemEconomies(), playerEmpire);
        }
        if (industryView != null) industryView.updateData(state);
        if (shipDesignerView != null) shipDesignerView.updateData(state);
        if (fleetManagementView != null) fleetManagementView.updateData(state);
        if (commercialHubView != null) commercialHubView.updateData(state);
        if (galacticSenateView != null) galacticSenateView.updateData(state.galacticCommunity());
        if (megastructureView != null) megastructureView.updateData(state.megastructures());
        if (terraformingView != null) terraformingView.updateData(state.atmosphericCompositions(), state.terraformingProjects());
        if (planetDetailView != null) planetDetailView.updateData(state);
        if (colonyManagementView != null) colonyManagementView.updateData(state);
        if (espionageView != null) espionageView.updateData(state.sleeperAgents(), state.espionageOperations(), state.pirateBases());
        if (orbitalStationView != null) orbitalStationView.updateData(state, playerEmpireId);
        if (galaxyCanvasView != null) galaxyCanvasView.updateData(state);
    }

    public List<VBox> getAllOverlayRoots() {
        List<VBox> roots = new ArrayList<>();
        if (techView != null) roots.add(techView.getRoot());
        if (gameMenuView != null) roots.add(gameMenuView.getRoot());
        if (galaxyListView != null) roots.add(galaxyListView.getRoot());
        if (empireView != null) roots.add(empireView.getRoot());
        if (corporateView != null) roots.add(corporateView.getRoot());
        if (diplomacyView != null) roots.add(diplomacyView.getRoot());
        if (commercialHubView != null) roots.add(commercialHubView.getRoot());
        if (colonyManagementView != null) roots.add(colonyManagementView.getRoot());
        if (campaignManagerView != null) roots.add(campaignManagerView.getRoot());
        if (orbitalStationView != null) roots.add(orbitalStationView.getRoot());
        if (espionageView != null) roots.add(espionageView.getRoot());
        if (refinementView != null) roots.add(refinementView.getRoot());
        if (battlePlaybackView != null) roots.add(battlePlaybackView.getRoot());
        if (industryView != null) roots.add(industryView.getRoot());
        if (shipDesignerView != null) roots.add(shipDesignerView.getRoot());
        if (fleetManagementView != null) roots.add(fleetManagementView.getRoot());
        if (planetDetailView != null) roots.add(planetDetailView.getRoot());
        if (terraformingView != null) roots.add(terraformingView.getRoot());
        if (megastructureView != null) roots.add(megastructureView.getRoot());
        if (galacticSenateView != null) roots.add(galacticSenateView.getRoot());
        if (galaxyCanvasView != null) roots.add(galaxyCanvasView.getRoot());
        if (scenarioEditorView != null) roots.add(scenarioEditorView.getRoot());
        if (victoryDefeatView != null) roots.add(victoryDefeatView.getRoot());
        if (tutorialView != null) roots.add(tutorialView.getRoot());
        if (combatArenaView != null) roots.add(combatArenaView.getRoot());
        if (empireWizardView != null) roots.add(empireWizardView.getRoot());
        if (audioSettingsView != null) roots.add(audioSettingsView.getRoot());
        if (screenSettingsView != null) roots.add(screenSettingsView.getRoot());
        return roots;
    }

    public TechnologyView getTechView() { return techView; }
    public GameMenuView getGameMenuView() { return gameMenuView; }
    public GalaxyListView getGalaxyListView() { return galaxyListView; }
    public EmpireView getEmpireView() { return empireView; }
    public CorporateView getCorporateView() { return corporateView; }
    public DiplomacyView getDiplomacyView() { return diplomacyView; }
    public CommercialHubView getCommercialHubView() { return commercialHubView; }
    public ColonyManagementView getColonyManagementView() { return colonyManagementView; }
    public CampaignManagerView getCampaignManagerView() { return campaignManagerView; }
    public OrbitalStationView getOrbitalStationView() { return orbitalStationView; }
    public EspionageView getEspionageView() { return espionageView; }
    public RefinementView getRefinementView() { return refinementView; }
    public TacticalBattlePlaybackView getBattlePlaybackView() { return battlePlaybackView; }
    public TerraformingView getTerraformingView() { return terraformingView; }
    public MegastructureView getMegastructureView() { return megastructureView; }
    public GalacticSenateView getGalacticSenateView() { return galacticSenateView; }
    public GalaxyCanvasView getGalaxyCanvasView() { return galaxyCanvasView; }
    public ScenarioEditorView getScenarioEditorView() { return scenarioEditorView; }
    public IndustryView getIndustryView() { return industryView; }
    public ShipDesignerView getShipDesignerView() { return shipDesignerView; }
    public FleetManagementView getFleetManagementView() { return fleetManagementView; }
    public PlanetDetailView getPlanetDetailView() { return planetDetailView; }
    public VictoryDefeatView getVictoryDefeatView() { return victoryDefeatView; }
    public TutorialOnboardingView getTutorialView() { return tutorialView; }
    public TacticalCombatArenaView getCombatArenaView() { return combatArenaView; }
    public EmpireCreationWizardView getEmpireWizardView() { return empireWizardView; }
    public AudioSettingsView getAudioSettingsView() { return audioSettingsView; }
    public ScreenSettingsView getScreenSettingsView() { return screenSettingsView; }
    public AudioPlaybackManager getAudioPlaybackManager() { return audioPlaybackManager; }
}
