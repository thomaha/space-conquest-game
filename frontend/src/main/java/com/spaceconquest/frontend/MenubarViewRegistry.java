package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.ai.CorporationAIController;
import com.spaceconquest.control.ai.EmpireAIController;
import com.spaceconquest.control.ai.ShadowSyndicateAIController;
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

    public void initViews(Menubar menubar, Main mainApp, HumanController humanController, String playerEmpireId) {
        techView = new TechnologyView(menubar);
        techView.setHumanController(humanController);
        techView.setPlayerEmpireId(playerEmpireId);

        industryView = new IndustryView(menubar);
        industryView.setHumanController(humanController);
        industryView.setPlayerEmpireId(playerEmpireId);

        shipDesignerView = new ShipDesignerView(menubar);
        shipDesignerView.setHumanController(humanController);
        shipDesignerView.setPlayerEmpireId(playerEmpireId);

        fleetManagementView = new FleetManagementView(menubar);
        fleetManagementView.setHumanController(humanController);
        fleetManagementView.setPlayerEmpireId(playerEmpireId);

        colonyManagementView = new ColonyManagementView(menubar);
        colonyManagementView.setHumanController(humanController);
        colonyManagementView.setPlayerEmpireId(playerEmpireId);

        planetDetailView = new PlanetDetailView(menubar);
        planetDetailView.setHumanController(humanController);
        planetDetailView.setPlayerEmpireId(playerEmpireId);

        gameMenuView = new GameMenuView(menubar);
        galaxyListView = new GalaxyListView(menubar, mainApp);
        empireView = new EmpireView(menubar);
        empireView.setHumanController(humanController);
        empireView.setPlayerEmpireId(playerEmpireId);
        corporateView = new CorporateView(menubar);
        diplomacyView = new DiplomacyView(menubar);
        commercialHubView = new CommercialHubView(menubar);
        commercialHubView.setHumanController(humanController);
        commercialHubView.setPlayerEmpireId(playerEmpireId);

        campaignManagerView = new CampaignManagerView(menubar);
        campaignManagerView.setMainApp(mainApp);

        orbitalStationView = new OrbitalStationView(menubar);
        espionageView = new EspionageView(menubar);
        refinementView = new RefinementView(menubar);
        battlePlaybackView = new TacticalBattlePlaybackView(menubar);

        terraformingView = new TerraformingView(menubar);
        terraformingView.setHumanController(humanController);
        terraformingView.setPlayerEmpireId(playerEmpireId);

        megastructureView = new MegastructureView(menubar);
        megastructureView.setHumanController(humanController);
        megastructureView.setPlayerEmpireId(playerEmpireId);

        galacticSenateView = new GalacticSenateView(menubar);
        galacticSenateView.setHumanController(humanController);
        galacticSenateView.setPlayerEmpireId(playerEmpireId);

        galaxyCanvasView = new GalaxyCanvasView(menubar);
        galaxyCanvasView.setHumanController(humanController);
        galaxyCanvasView.setPlayerEmpireId(playerEmpireId);

        AudioSynthesizer audioSynth = mainApp != null && mainApp.getEngine() != null 
                ? mainApp.getEngine().getAudioSynthesizer() 
                : new AudioSynthesizer();
        audioPlaybackManager = new AudioPlaybackManager(audioSynth);

        scenarioEditorView = new ScenarioEditorView(menubar, audioSynth);
        victoryDefeatView = new VictoryDefeatView(menubar);

        combatArenaView = new TacticalCombatArenaView(menubar, audioSynth);
        combatArenaView.setHumanController(humanController);

        tutorialView = new TutorialOnboardingView(menubar, combatArenaView);
        tutorialView.setHumanController(humanController);

        empireWizardView = new EmpireCreationWizardView(menubar, audioSynth);
        empireWizardView.setHumanController(humanController);

        audioSettingsView = new AudioSettingsView(menubar, audioSynth);
        screenSettingsView = new ScreenSettingsView(menubar, ScreenSettingsManager.getInstance());
    }

    public void setPlayerEmpireId(String empireId) {
        if (empireId == null || empireId.isEmpty()) return;
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

    public void updateAllViews(GameState state, Main mainApp, HumanController humanController,
                               EmpireAIController empireAIController, CorporationAIController corporationAIController,
                               ShadowSyndicateAIController shadowSyndicateAIController, String playerEmpireId) {
        if (state == null) return;
        if (empireAIController != null) empireAIController.onGameStateUpdate(state);
        if (corporationAIController != null) corporationAIController.onGameStateUpdate(state);
        if (shadowSyndicateAIController != null) shadowSyndicateAIController.onGameStateUpdate(state);
        if (humanController != null) humanController.onGameStateUpdate(state);

        if (empireView != null) empireView.updateData(state);
        if (techView != null) {
            Empire playerEmpire = state.empires().stream()
                    .filter(e -> e.id().equals(playerEmpireId))
                    .findFirst().orElse(null);
            techView.updateData(state.researchProjects(), state.technologyExchangeRoutes(), state.systemEconomies(), playerEmpire);
        }
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

    public void refreshOnTick(GameState state, Main mainApp) {
        if (state == null) return;
        if (techView != null) techView.setResearchProjects(state.researchProjects());
        if (industryView != null) industryView.updateData(state.industrialFacilities(), state.expansionProjects());
        if (shipDesignerView != null) shipDesignerView.updateDesigns(state.shipDesigns());
        if (fleetManagementView != null) fleetManagementView.updateFleets(state.fleets());
        if (galacticSenateView != null) galacticSenateView.updateData(state.galacticCommunity());
        if (megastructureView != null) megastructureView.updateData(state.megastructures());
        if (terraformingView != null && mainApp != null && mainApp.getEngine() != null) {
            terraformingView.updateData(mainApp.getEngine().getAtmospheres(), state.terraformingProjects());
        }
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
