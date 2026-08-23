package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.BuildFacilityCommand;
import com.spaceconquest.control.command.BuildMegastructureCommand;
import com.spaceconquest.control.command.ColonizePlanetCommand;
import com.spaceconquest.control.command.EnactMartialLawCommand;
import com.spaceconquest.control.command.PlaceFacilityOnTileCommand;
import com.spaceconquest.control.command.SetSystemEconomyBudgetCommand;
import com.spaceconquest.control.command.StartProspectingMissionCommand;
import com.spaceconquest.control.command.StartTerraformingProjectCommand;
import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.MinistryAssignment;
import com.spaceconquest.engine.Moon;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.Population;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.SystemGovernor;
import com.spaceconquest.engine.biome.BiomeAdjacencyProcessor;
import com.spaceconquest.engine.biome.PlanetBiomeGrid;
import com.spaceconquest.engine.biome.SurfaceTile;
import com.spaceconquest.engine.economy.SystemEconomy;
import com.spaceconquest.engine.economy.SystemEconomyProcessor;
import com.spaceconquest.engine.industry.FacilityExpansionProject;
import com.spaceconquest.engine.industry.GeologicalDeposit;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.industry.PowerGridState;
import com.spaceconquest.engine.macrostructure.ConstructionDeploymentProject;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.macrostructure.SpaceElevator;
import com.spaceconquest.engine.macrostructure.StationModule;
import com.spaceconquest.engine.megastructure.Megastructure;
import com.spaceconquest.engine.technology.ResearchProject;
import com.spaceconquest.engine.terraforming.GeoengineeringProject;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Interactive imperial administration and planetary management hub.
 * Features a modular tabbed interface supporting the Imperial Cabinet overview,
 * planetary bodies exploration across all star systems, multi-attribute filtering/sorting
 * and technology-gated contextual operations.
 */
public class EmpireView {
    private static final Logger logger = LogManager.getLogger(EmpireView.class);

    public enum Tab {
        ECONOMY("Imperial economy"),
        CABINET("Empire cabinet"),
        PLANETS("Planets"),
        STATIONS("Orbital stations and shipyards"),
        CORPORATIONS("Corporation registry"),
        MEGASTRUCTURES("Megastructures");

        private final String displayName;

        Tab(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public record EmpireEconomyReport(
            double treasuryCredits,
            double corporateTaxRate,
            long totalPopulation,
            int colonizedWorldCount,
            int controlledSystemCount,
            double colonialTaxIncome,
            double stateIndustryIncome,
            double corporateTariffIncome,
            double spaceElevatorIncome,
            double miningRoyaltiesIncome,
            double totalIncome,
            double governanceExpenses,
            double ministryBudgets,
            double infrastructureMaintenance,
            double orbitalStationMaintenance,
            double researchSubsidies,
            double terraformingSubsidies,
            double totalCosts,
            double netBudgetBalance,
            List<ColonyEconomyEntry> colonyEntries,
            List<CorporateEconomyEntry> corporateEntries
    ) {}

    public record ColonyEconomyEntry(
            String bodyId,
            String bodyName,
            String systemName,
            boolean isMoon,
            long population,
            double grossOutputCredits,
            double taxCollectedCredits,
            double localGovernanceCostCredits,
            double netContributionCredits
    ) {}

    public record CorporateEconomyEntry(
            String corporationId,
            String corporationName,
            String hqEntityId,
            String marketOrientation,
            double liquidCapital,
            int ownedFacilitiesCount,
            double estimatedTariffPaid
    ) {}

    public enum EconomySubView {
        IMPERIAL("Imperial economy"),
        SYSTEM("System economy");

        private final String displayName;

        EconomySubView(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public record SystemEconomyReport(
            String systemId,
            String systemName,
            String empireId,
            long systemPopulation,
            int colonizedBodiesCount,
            double grossSystemOutput,
            double colonialTaxes,
            double corporateTariffs,
            double spaceElevatorFees,
            double miningRoyalties,
            double stateIndustryIncome,
            double totalRevenues,
            double publicSectorFunding,
            double governorAdministration,
            double stationMaintenance,
            double totalExpenditures,
            double netSystemBalance,
            SystemEconomy economy
    ) {}

    public enum FilterCategory {
        COLONIZED("Colonized"),
        UNCOLONIZED("Uncolonized"),
        COLONIZABLE("Colonizable"),
        ALL_BODIES("All planetary bodies"),
        ONLY_PLANETS("Only planets"),
        ONLY_MOONS("Only moons");

        private final String label;

        FilterCategory(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum SortOption {
        NAME_AZ("Name (A-Z)"),
        SYSTEM_NAME("Star system name"),
        POPULATION_DESC("Population (High to Low)"),
        SIZE_DESC("Diameter / Size"),
        GRAVITY_DESC("Surface gravity"),
        RESOURCES_DESC("Resource vein count");

        private final String label;

        SortOption(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private VBox root;
    private HBox tabHeaderBar;
    private final Map<Tab, Button> tabButtons = new HashMap<>();
    private VBox tabContentContainer;
    private Label feedbackLabel;
    private final Menubar menubar;

    private Tab currentTab = Tab.ECONOMY;
    private EconomySubView currentEconomySubView = EconomySubView.IMPERIAL;
    private String selectedEconomySystemId = null;
    private FilterCategory currentFilter = FilterCategory.COLONIZED;
    private SortOption currentSort = SortOption.NAME_AZ;
    private PlanetaryBodyEntry selectedBody = null;

    private HumanController humanController;
    private String playerEmpireId = "terran_confederation";

    private final List<SolarSystem> solarSystems = new ArrayList<>();
    private final List<Empire> empires = new ArrayList<>();
    private final List<SystemGovernor> systemGovernors = new ArrayList<>();
    private final List<ResearchProject> researchProjects = new ArrayList<>();
    private final List<GeologicalDeposit> deposits = new ArrayList<>();
    private final List<PowerGridState> powerGrids = new ArrayList<>();
    private final List<IndustrialFacility> facilities = new ArrayList<>();
    private final List<FacilityExpansionProject> expansionProjects = new ArrayList<>();
    private final List<GeoengineeringProject> terraformingProjects = new ArrayList<>();
    private final List<OrbitalStation> orbitalStations = new ArrayList<>();
    private final List<SpaceElevator> spaceElevators = new ArrayList<>();
    private final List<ConstructionDeploymentProject> constructionProjects = new ArrayList<>();
    private final List<Corporation> corporations = new ArrayList<>();
    private final List<Megastructure> megastructures = new ArrayList<>();
    private final List<SystemEconomy> systemEconomies = new ArrayList<>();

    private final BiomeAdjacencyProcessor biomeProcessor = new BiomeAdjacencyProcessor();
    private final SystemEconomyProcessor systemEconomyProcessor = new SystemEconomyProcessor();

    public EmpireView(Menubar menubar) {
        this.menubar = menubar;
        build();
    }

    public void setHumanController(HumanController controller) {
        this.humanController = controller;
    }

    public void setPlayerEmpireId(String empireId) {
        if (empireId != null && !empireId.isEmpty()) {
            this.playerEmpireId = empireId;
        }
    }

    public String getPlayerEmpireId() {
        return playerEmpireId;
    }

    public Tab getCurrentTab() {
        return currentTab;
    }

    public EconomySubView getCurrentEconomySubView() {
        return currentEconomySubView;
    }

    public void setCurrentEconomySubView(EconomySubView subView) {
        if (subView != null) {
            this.currentEconomySubView = subView;
        }
    }

    public String getSelectedEconomySystemId() {
        return selectedEconomySystemId;
    }

    public void setSelectedEconomySystemId(String systemId) {
        this.selectedEconomySystemId = systemId;
    }

    public List<SystemEconomy> getSystemEconomies() {
        return systemEconomies;
    }

    public FilterCategory getCurrentFilter() {
        return currentFilter;
    }

    public void setCurrentFilter(FilterCategory filter) {
        if (filter != null) {
            this.currentFilter = filter;
        }
    }

    public SortOption getCurrentSort() {
        return currentSort;
    }

    public void setCurrentSort(SortOption sort) {
        if (sort != null) {
            this.currentSort = sort;
        }
    }

    public PlanetaryBodyEntry getSelectedBody() {
        return selectedBody;
    }

    public void setSelectedBody(PlanetaryBodyEntry entry) {
        this.selectedBody = entry;
    }

    private void build() {
        root = new VBox(12);
        root.setPadding(new Insets(16));
        root.setStyle("-fx-background-color: rgba(10, 18, 38, 0.97); " +
                "-fx-border-color: #78aaff; -fx-border-width: 2; " +
                "-fx-border-radius: 10; -fx-background-radius: 10;");
        root.setPrefSize(960, 720);

        // Header with title, tab buttons, spacer, and close button
        HBox topBar = new HBox(12);
        topBar.setAlignment(Pos.CENTER_LEFT);

        Text title = new Text("Empire management");
        title.setFill(Color.WHITE);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 20));

        tabHeaderBar = new HBox(8);
        tabHeaderBar.setAlignment(Pos.CENTER_LEFT);

        for (Tab tab : Tab.values()) {
            Button tabBtn = new Button(tab.getDisplayName());
            tabBtn.setFont(Font.font("Verdana", FontWeight.BOLD, 12));
            tabBtn.setPrefHeight(32);
            tabBtn.setCursor(javafx.scene.Cursor.HAND);
            tabBtn.setOnAction(e -> selectTab(tab));
            tabButtons.put(tab, tabBtn);
            tabHeaderBar.getChildren().add(tabBtn);
        }

        updateTabButtonStyles();

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeButton = new Button("Close");
        closeButton.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 4; -fx-cursor: hand;");
        closeButton.setCursor(javafx.scene.Cursor.HAND);
        closeButton.setOnAction(e -> hide());

        topBar.getChildren().addAll(title, tabHeaderBar, spacer, closeButton);

        feedbackLabel = new Label("Ready | Manage imperial administration and celestial planetary assets.");
        feedbackLabel.setTextFill(Color.LIGHTCYAN);
        feedbackLabel.setFont(Font.font("Verdana", 11));

        tabContentContainer = new VBox(10);
        VBox.setVgrow(tabContentContainer, Priority.ALWAYS);

        root.getChildren().addAll(topBar, feedbackLabel, tabContentContainer);
        root.setVisible(false);

        renderCurrentTab();
    }

    public VBox getRoot() {
        return root;
    }

    public void selectTab(Tab tab) {
        if (tab == null) return;
        this.currentTab = tab;
        updateTabButtonStyles();
        renderCurrentTab();
    }

    private void updateTabButtonStyles() {
        for (Map.Entry<Tab, Button> entry : tabButtons.entrySet()) {
            Button btn = entry.getValue();
            boolean isSelected = (entry.getKey() == currentTab);
            String bg = isSelected ? "#2980b9" : "rgba(25, 45, 80, 0.7)";
            String border = isSelected ? "#78aaff" : "rgba(120, 170, 255, 0.4)";
            String textFill = isSelected ? "white" : "#b0c4de";
            double borderWidth = isSelected ? 1.5 : 1.0;

            String baseStyle = String.format(
                    "-fx-background-color: %s; -fx-text-fill: %s; -fx-font-family: 'Verdana'; -fx-font-size: 12px; " +
                    "-fx-font-weight: bold; -fx-border-color: %s; -fx-border-width: %.1f; " +
                    "-fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 6 14 6 14; -fx-cursor: hand;",
                    bg, textFill, border, borderWidth
            );
            btn.setStyle(baseStyle);
            btn.setCursor(javafx.scene.Cursor.HAND);

            btn.setOnMouseEntered(e -> {
                if (entry.getKey() != currentTab) {
                    btn.setStyle(
                            "-fx-background-color: rgba(45, 75, 120, 0.9); -fx-text-fill: white; -fx-font-family: 'Verdana'; " +
                            "-fx-font-size: 12px; -fx-font-weight: bold; -fx-border-color: #78aaff; -fx-border-width: 1.0; " +
                            "-fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 6 14 6 14; -fx-cursor: hand;"
                    );
                }
            });
            btn.setOnMouseExited(e -> {
                if (entry.getKey() != currentTab) {
                    btn.setStyle(baseStyle);
                }
            });
        }
    }

    public void updateData(GameState gameState) {
        if (gameState == null) return;
        updateData(
                gameState.solarSystems(),
                gameState.empires(),
                gameState.systemGovernors(),
                gameState.researchProjects(),
                gameState.geologicalDeposits(),
                gameState.powerGrids(),
                gameState.industrialFacilities(),
                gameState.expansionProjects(),
                gameState.terraformingProjects(),
                gameState.orbitalStations(),
                gameState.spaceElevators(),
                gameState.constructionProjects(),
                gameState.corporations(),
                gameState.megastructures(),
                gameState.systemEconomies()
        );
    }

    public void updateData(
            List<SolarSystem> systems,
            List<Empire> newEmpires,
            List<SystemGovernor> governors,
            List<ResearchProject> research,
            List<GeologicalDeposit> newDeposits,
            List<PowerGridState> grids,
            List<IndustrialFacility> newFacilities,
            List<FacilityExpansionProject> expansions,
            List<GeoengineeringProject> terraform
    ) {
        updateData(systems, newEmpires, governors, research, newDeposits, grids, newFacilities, expansions, terraform, null, null, null, null, null, null);
    }

    public void updateData(
            List<SolarSystem> systems,
            List<Empire> newEmpires,
            List<SystemGovernor> governors,
            List<ResearchProject> research,
            List<GeologicalDeposit> newDeposits,
            List<PowerGridState> grids,
            List<IndustrialFacility> newFacilities,
            List<FacilityExpansionProject> expansions,
            List<GeoengineeringProject> terraform,
            List<OrbitalStation> newStations,
            List<SpaceElevator> newElevators,
            List<ConstructionDeploymentProject> newProjects,
            List<Corporation> newCorps
    ) {
        updateData(systems, newEmpires, governors, research, newDeposits, grids, newFacilities, expansions, terraform, newStations, newElevators, newProjects, newCorps, null, null);
    }

    public void updateData(
            List<SolarSystem> systems,
            List<Empire> newEmpires,
            List<SystemGovernor> governors,
            List<ResearchProject> research,
            List<GeologicalDeposit> newDeposits,
            List<PowerGridState> grids,
            List<IndustrialFacility> newFacilities,
            List<FacilityExpansionProject> expansions,
            List<GeoengineeringProject> terraform,
            List<OrbitalStation> newStations,
            List<SpaceElevator> newElevators,
            List<ConstructionDeploymentProject> newProjects,
            List<Corporation> newCorps,
            List<Megastructure> newMegastructures
    ) {
        updateData(systems, newEmpires, governors, research, newDeposits, grids, newFacilities, expansions, terraform, newStations, newElevators, newProjects, newCorps, newMegastructures, null);
    }

    public void updateData(
            List<SolarSystem> systems,
            List<Empire> newEmpires,
            List<SystemGovernor> governors,
            List<ResearchProject> research,
            List<GeologicalDeposit> newDeposits,
            List<PowerGridState> grids,
            List<IndustrialFacility> newFacilities,
            List<FacilityExpansionProject> expansions,
            List<GeoengineeringProject> terraform,
            List<OrbitalStation> newStations,
            List<SpaceElevator> newElevators,
            List<ConstructionDeploymentProject> newProjects,
            List<Corporation> newCorps,
            List<Megastructure> newMegastructures,
            List<SystemEconomy> newEconomies
    ) {
        solarSystems.clear();
        if (systems != null) solarSystems.addAll(systems);

        empires.clear();
        if (newEmpires != null) empires.addAll(newEmpires);

        systemGovernors.clear();
        if (governors != null) systemGovernors.addAll(governors);

        researchProjects.clear();
        if (research != null) researchProjects.addAll(research);

        deposits.clear();
        if (newDeposits != null) deposits.addAll(newDeposits);

        powerGrids.clear();
        if (grids != null) powerGrids.addAll(grids);

        facilities.clear();
        if (newFacilities != null) facilities.addAll(newFacilities);

        expansionProjects.clear();
        if (expansions != null) expansionProjects.addAll(expansions);

        terraformingProjects.clear();
        if (terraform != null) terraformingProjects.addAll(terraform);

        orbitalStations.clear();
        if (newStations != null) orbitalStations.addAll(newStations);

        spaceElevators.clear();
        if (newElevators != null) spaceElevators.addAll(newElevators);

        constructionProjects.clear();
        if (newProjects != null) constructionProjects.addAll(newProjects);

        corporations.clear();
        if (newCorps != null) corporations.addAll(newCorps);

        megastructures.clear();
        if (newMegastructures != null) megastructures.addAll(newMegastructures);

        systemEconomies.clear();
        if (newEconomies != null) systemEconomies.addAll(newEconomies);

        selectedBody = null;

        if (root != null && root.isVisible()) {
            renderCurrentTab();
        }
    }

    public void show() {
        show(currentTab);
    }

    public void show(Tab tab) {
        if (tab != null) {
            this.currentTab = tab;
            updateTabButtonStyles();
        }
        ensureDataLoaded();
        renderCurrentTab();
        if (root != null) {
            root.setVisible(true);
            root.toFront();
        }
    }

    public void hide() {
        if (root != null) {
            root.setVisible(false);
        }
        if (menubar != null) {
            menubar.closePage();
        }
    }

    private void ensureDataLoaded() {
        if (menubar != null && menubar.getMainApp() != null && menubar.getMainApp().getEngine() != null) {
            updateData(menubar.getMainApp().getEngine().getGameState());
            return;
        }
        if (empires.isEmpty()) {
            try {
                empires.addAll(DataModelLoader.loadEmpires());
            } catch (IOException e) {
                logger.error("Failed to load empires fallback data", e);
            }
        }
        if (corporations.isEmpty()) {
            try {
                corporations.addAll(DataModelLoader.loadCorporations());
            } catch (IOException e) {
                logger.error("Failed to load corporate registry fallback data", e);
            }
        }
    }

    private void renderCurrentTab() {
        if (tabContentContainer == null) return;
        tabContentContainer.getChildren().clear();

        switch (currentTab) {
            case ECONOMY -> tabContentContainer.getChildren().add(buildEconomyTabContent());
            case CABINET -> tabContentContainer.getChildren().add(buildCabinetTabContent());
            case PLANETS -> tabContentContainer.getChildren().add(buildPlanetsTabContent());
            case STATIONS -> tabContentContainer.getChildren().add(buildStationsTabContent());
            case CORPORATIONS -> tabContentContainer.getChildren().add(buildCorporationsTabContent());
            case MEGASTRUCTURES -> tabContentContainer.getChildren().add(buildMegastructuresTabContent());
        }
    }

    // ==========================================
    // TAB 1: EMPIRE ECONOMY (IMPERIAL & SYSTEM)
    // ==========================================

    private VBox buildEconomyTabContent() {
        VBox container = new VBox(10);
        container.setPadding(new Insets(4));
        VBox.setVgrow(container, Priority.ALWAYS);

        // Sub-navigation view switcher bar
        HBox subNavBar = new HBox(8);
        subNavBar.setAlignment(Pos.CENTER_LEFT);
        subNavBar.setPadding(new Insets(2, 6, 2, 6));

        for (EconomySubView subView : EconomySubView.values()) {
            Button btn = new Button(subView.getDisplayName());
            btn.setFont(Font.font("Verdana", FontWeight.BOLD, 12));
            btn.setPrefHeight(30);
            btn.setCursor(javafx.scene.Cursor.HAND);
            boolean isSelected = (subView == currentEconomySubView);
            String bg = isSelected ? "#f39c12" : "rgba(30, 45, 75, 0.8)";
            String textFill = isSelected ? "black" : "#dfe6e9";
            btn.setStyle(String.format(
                    "-fx-background-color: %s; -fx-text-fill: %s; -fx-font-family: 'Verdana'; -fx-font-size: 12px; " +
                    "-fx-font-weight: bold; -fx-border-color: #f39c12; -fx-border-width: 1; " +
                    "-fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 4 14 4 14; -fx-cursor: hand;",
                    bg, textFill
            ));
            btn.setOnAction(e -> {
                currentEconomySubView = subView;
                renderCurrentTab();
            });
            subNavBar.getChildren().add(btn);
        }

        container.getChildren().add(subNavBar);

        if (currentEconomySubView == EconomySubView.IMPERIAL) {
            container.getChildren().add(buildImperialMacroEconomyContent());
        } else {
            container.getChildren().add(buildSystemEconomyWorkbenchContent());
        }

        return container;
    }

    private VBox buildImperialMacroEconomyContent() {
        VBox container = new VBox(12);
        container.setPadding(new Insets(4));
        VBox.setVgrow(container, Priority.ALWAYS);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.setPadding(new Insets(6));
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        VBox content = new VBox(14);
        scrollPane.setContent(content);

        Empire playerEmpire = getPlayerEmpire();
        EmpireEconomyReport report = calculateEmpireEconomyReport();

        if (playerEmpire != null) {
            content.getChildren().add(createEconomyOverviewCard(report, playerEmpire));

            HBox columnsBox = new HBox(12);
            columnsBox.setAlignment(Pos.TOP_LEFT);

            VBox incomesCard = createIncomesAndRevenuesCard(report);
            HBox.setHgrow(incomesCard, Priority.ALWAYS);

            VBox costsCard = createExpensesAndCostsCard(report);
            HBox.setHgrow(costsCard, Priority.ALWAYS);

            columnsBox.getChildren().addAll(incomesCard, costsCard);
            content.getChildren().add(columnsBox);

            content.getChildren().add(createColonyLedgerCard(report));
            content.getChildren().add(createCorporateEconomyCard(report));
        } else {
            Text noEmpireText = new Text("No sovereign empire economy data available.");
            noEmpireText.setFill(Color.LIGHTCORAL);
            content.getChildren().add(noEmpireText);
        }

        container.getChildren().add(scrollPane);
        return container;
    }

    private VBox buildSystemEconomyWorkbenchContent() {
        VBox container = new VBox(10);
        VBox.setVgrow(container, Priority.ALWAYS);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.setPadding(new Insets(6));
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        VBox content = new VBox(12);
        scrollPane.setContent(content);

        Empire playerEmpire = getPlayerEmpire();
        if (playerEmpire == null) {
            Text noEmpireText = new Text("No sovereign empire data available.");
            noEmpireText.setFill(Color.LIGHTCORAL);
            content.getChildren().add(noEmpireText);
            container.getChildren().add(scrollPane);
            return container;
        }

        List<String> controlledSystemIds = playerEmpire.controlledSystemIds() != null && !playerEmpire.controlledSystemIds().isEmpty()
                ? playerEmpire.controlledSystemIds()
                : solarSystems.stream().map(SolarSystem::id).toList();

        List<SolarSystem> availableSystems = solarSystems.stream()
                .filter(s -> controlledSystemIds.contains(s.id()))
                .toList();

        if (availableSystems.isEmpty()) {
            availableSystems = solarSystems;
        }

        if (selectedEconomySystemId == null || availableSystems.stream().noneMatch(s -> s.id().equals(selectedEconomySystemId))) {
            if (!availableSystems.isEmpty()) {
                selectedEconomySystemId = availableSystems.get(0).id();
            }
        }

        // Star System Selector Header Bar
        HBox systemSelectorBar = new HBox(12);
        systemSelectorBar.setAlignment(Pos.CENTER_LEFT);
        systemSelectorBar.setPadding(new Insets(8, 12, 8, 12));
        systemSelectorBar.setStyle("-fx-background-color: rgba(20, 35, 65, 0.8); -fx-background-radius: 6; -fx-border-color: #3498db; -fx-border-width: 1; -fx-border-radius: 6;");

        Label selectLabel = new Label("Controlled star system:");
        selectLabel.setTextFill(Color.WHITE);
        selectLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 12));

        ComboBox<String> systemComboBox = new ComboBox<>();
        systemComboBox.setCursor(javafx.scene.Cursor.HAND);
        systemComboBox.setStyle("-fx-background-color: #2c3e50; -fx-text-fill: white; -fx-font-weight: bold;");

        Map<String, String> displayToIdMap = new HashMap<>();
        String currentSelectionDisplay = null;
        for (SolarSystem sys : availableSystems) {
            String display = sys.name() + " (" + sys.id() + ")";
            systemComboBox.getItems().add(display);
            displayToIdMap.put(display, sys.id());
            if (sys.id().equals(selectedEconomySystemId)) {
                currentSelectionDisplay = display;
            }
        }

        if (currentSelectionDisplay != null) {
            systemComboBox.setValue(currentSelectionDisplay);
        } else if (!systemComboBox.getItems().isEmpty()) {
            systemComboBox.setValue(systemComboBox.getItems().get(0));
            selectedEconomySystemId = displayToIdMap.get(systemComboBox.getItems().get(0));
        }

        systemComboBox.setOnAction(e -> {
            String val = systemComboBox.getValue();
            if (val != null && displayToIdMap.containsKey(val)) {
                selectedEconomySystemId = displayToIdMap.get(val);
                renderCurrentTab();
            }
        });

        systemSelectorBar.getChildren().addAll(selectLabel, systemComboBox);
        content.getChildren().add(systemSelectorBar);

        if (selectedEconomySystemId != null) {
            SystemEconomyReport report = calculateSystemEconomyReport(selectedEconomySystemId);
            content.getChildren().add(createSystemEconomyOverviewCard(report));
            content.getChildren().add(createSystemEconomyWorkbenchCard(report));

            HBox breakdownRow = new HBox(12);
            breakdownRow.setAlignment(Pos.TOP_LEFT);

            VBox revenueCard = createSystemRevenuesCard(report);
            HBox.setHgrow(revenueCard, Priority.ALWAYS);

            VBox expenseCard = createSystemExpensesCard(report);
            HBox.setHgrow(expenseCard, Priority.ALWAYS);

            breakdownRow.getChildren().addAll(revenueCard, expenseCard);
            content.getChildren().add(breakdownRow);

            content.getChildren().add(createSystemCelestialBodiesLedgerCard(report));
        }

        container.getChildren().add(scrollPane);
        return container;
    }

    private VBox createEconomyOverviewCard(EmpireEconomyReport report, Empire empire) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(14));
        box.setStyle("-fx-background-color: rgba(30, 50, 90, 0.7); -fx-background-radius: 8; " +
                "-fx-border-color: #f39c12; -fx-border-width: 1.5; -fx-border-radius: 8;");

        HBox topRow = new HBox(12);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Text empireName = new Text(empire.name() + " — Imperial economy overview");
        empireName.setFill(Color.GOLD);
        empireName.setFont(Font.font("Verdana", FontWeight.BOLD, 18));

        Label structureBadge = new Label(empire.societyStructure());
        structureBadge.setStyle("-fx-background-color: #2c3e50; -fx-text-fill: #00cec9; " +
                "-fx-font-weight: bold; -fx-padding: 3 8 3 8; -fx-background-radius: 4; -fx-font-size: 11;");

        Label raceBadge = new Label("Primary race: " + empire.raceId());
        raceBadge.setStyle("-fx-background-color: #34495e; -fx-text-fill: #dfe6e9; " +
                "-fx-padding: 3 8 3 8; -fx-background-radius: 4; -fx-font-size: 11;");

        topRow.getChildren().addAll(empireName, structureBadge, raceBadge);

        GridPane metricsGrid = new GridPane();
        metricsGrid.setHgap(20);
        metricsGrid.setVgap(8);

        Label treasuryLbl = new Label(String.format("Liquid treasury: %,.0f ₵", report.treasuryCredits()));
        treasuryLbl.setTextFill(Color.GOLD);
        treasuryLbl.setFont(Font.font("Verdana", FontWeight.BOLD, 13));

        boolean positiveNet = report.netBudgetBalance() >= 0;
        Label netLbl = new Label(String.format("Net budget balance: %+,.0f ₵/turn", report.netBudgetBalance()));
        netLbl.setTextFill(positiveNet ? Color.LIGHTGREEN : Color.LIGHTCORAL);
        netLbl.setFont(Font.font("Verdana", FontWeight.BOLD, 13));

        Label incomeLbl = new Label(String.format("Projected revenues: +%,.0f ₵/turn", report.totalIncome()));
        incomeLbl.setTextFill(Color.LIGHTGREEN);
        incomeLbl.setFont(Font.font("Verdana", FontWeight.BOLD, 12));

        Label costLbl = new Label(String.format("Projected expenditures: -%,.0f ₵/turn", report.totalCosts()));
        costLbl.setTextFill(Color.LIGHTCORAL);
        costLbl.setFont(Font.font("Verdana", FontWeight.BOLD, 12));

        Label systemsLbl = new Label(String.format("Controlled star systems: %d", report.controlledSystemCount()));
        systemsLbl.setTextFill(Color.WHITE);
        systemsLbl.setFont(Font.font("Verdana", 11));

        Label coloniesLbl = new Label(String.format("Colonized worlds: %d", report.colonizedWorldCount()));
        coloniesLbl.setTextFill(Color.LIGHTCYAN);
        coloniesLbl.setFont(Font.font("Verdana", 11));

        Label popLbl = new Label(String.format("Imperial citizen population: %,d", report.totalPopulation()));
        popLbl.setTextFill(Color.LIGHTYELLOW);
        popLbl.setFont(Font.font("Verdana", 11));

        Label taxLbl = new Label(String.format("Corporate tax tariff rate: %.1f%%", report.corporateTaxRate() * 100.0));
        taxLbl.setTextFill(Color.LIGHTSKYBLUE);
        taxLbl.setFont(Font.font("Verdana", 11));

        metricsGrid.add(treasuryLbl, 0, 0);
        metricsGrid.add(netLbl, 1, 0);
        metricsGrid.add(incomeLbl, 2, 0);
        metricsGrid.add(costLbl, 3, 0);

        metricsGrid.add(systemsLbl, 0, 1);
        metricsGrid.add(coloniesLbl, 1, 1);
        metricsGrid.add(popLbl, 2, 1);
        metricsGrid.add(taxLbl, 3, 1);

        box.getChildren().addAll(topRow, metricsGrid);
        return box;
    }

    private VBox createIncomesAndRevenuesCard(EmpireEconomyReport report) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: rgba(20, 45, 35, 0.7); -fx-background-radius: 8; " +
                "-fx-border-color: #2ecc71; -fx-border-width: 1.5; -fx-border-radius: 8;");

        Text title = new Text("Imperial revenues and incomes (per turn)");
        title.setFill(Color.LIGHTGREEN);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 14));

        Text subtitle = new Text("Revenue streams from colonial taxes, state industry, corporate tariffs and mining royalties.");
        subtitle.setFill(Color.LIGHTGRAY);
        subtitle.setFont(Font.font("Verdana", 10));

        VBox itemsBox = new VBox(8);

        itemsBox.getChildren().add(createEconomyLineItem(
                "Colonial population taxes",
                String.format("+%,.0f ₵", report.colonialTaxIncome()),
                "Aggregated tax yields from all planetary colonies. Tax rates are set individually per colony.",
                Color.LIGHTGREEN
        ));

        itemsBox.getChildren().add(createEconomyLineItem(
                "Public state industrial output",
                String.format("+%,.0f ₵", report.stateIndustryIncome()),
                "Net operating profits from sovereign state-owned manufacturing and refining facilities.",
                Color.LIGHTGREEN
        ));

        itemsBox.getChildren().add(createEconomyLineItem(
                "Corporate business tariffs",
                String.format("+%,.0f ₵", report.corporateTariffIncome()),
                String.format("Tariffs collected on private corporate commerce (%.1f%% corporate tax rate).", report.corporateTaxRate() * 100.0),
                Color.LIGHTGREEN
        ));

        itemsBox.getChildren().add(createEconomyLineItem(
                "Space elevator & logistics transit",
                String.format("+%,.0f ₵", report.spaceElevatorIncome()),
                "Commercial throughput launch fees and surface-to-orbit cargo transit tariffs.",
                Color.LIGHTGREEN
        ));

        itemsBox.getChildren().add(createEconomyLineItem(
                "Mining & resource extraction royalties",
                String.format("+%,.0f ₵", report.miningRoyaltiesIncome()),
                "State concession royalties from surveyed geological mineral veins.",
                Color.LIGHTGREEN
        ));

        Region divider = new Region();
        divider.setPrefHeight(1);
        divider.setStyle("-fx-background-color: rgba(46, 204, 113, 0.4);");

        HBox totalRow = new HBox(8);
        totalRow.setAlignment(Pos.CENTER_LEFT);
        Text totalLabel = new Text("Total projected revenues:");
        totalLabel.setFill(Color.WHITE);
        totalLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 12));
        Region totalSpacer = new Region();
        HBox.setHgrow(totalSpacer, Priority.ALWAYS);
        Text totalVal = new Text(String.format("+%,.0f ₵/turn", report.totalIncome()));
        totalVal.setFill(Color.LIGHTGREEN);
        totalVal.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
        totalRow.getChildren().addAll(totalLabel, totalSpacer, totalVal);

        box.getChildren().addAll(title, subtitle, itemsBox, divider, totalRow);
        return box;
    }

    private VBox createExpensesAndCostsCard(EmpireEconomyReport report) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: rgba(45, 25, 30, 0.7); -fx-background-radius: 8; " +
                "-fx-border-color: #e74c3c; -fx-border-width: 1.5; -fx-border-radius: 8;");

        Text title = new Text("Imperial expenditures and costs (per turn)");
        title.setFill(Color.LIGHTCORAL);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 14));

        Text subtitle = new Text("Operational upkeep for colonial governance, ministerial portfolios, infrastructure and science.");
        subtitle.setFill(Color.LIGHTGRAY);
        subtitle.setFont(Font.font("Verdana", 10));

        VBox itemsBox = new VBox(8);

        itemsBox.getChildren().add(createEconomyLineItem(
                "Colonial administration & governance",
                String.format("-%,.0f ₵", report.governanceExpenses()),
                "Overhead for system governors, municipal administration and planetary public services.",
                Color.LIGHTCORAL
        ));

        itemsBox.getChildren().add(createEconomyLineItem(
                "Imperial cabinet ministries budget",
                String.format("-%,.0f ₵", report.ministryBudgets()),
                "Operational funding and ministerial administration across government portfolios.",
                Color.LIGHTCORAL
        ));

        itemsBox.getChildren().add(createEconomyLineItem(
                "Public infrastructure & power grids",
                String.format("-%,.0f ₵", report.infrastructureMaintenance()),
                "Base maintenance and repairs for state industrial complexes and electrical grids.",
                Color.LIGHTCORAL
        ));

        itemsBox.getChildren().add(createEconomyLineItem(
                "Orbital stations & naval shipyards",
                String.format("-%,.0f ₵", report.orbitalStationMaintenance()),
                "Upkeep for orbital starbases, attached modules, defense platforms and slipways.",
                Color.LIGHTCORAL
        ));

        itemsBox.getChildren().add(createEconomyLineItem(
                "Scientific research grants",
                String.format("-%,.0f ₵", report.researchSubsidies()),
                "Government research subsidies allocated to active technological research projects.",
                Color.LIGHTCORAL
        ));

        itemsBox.getChildren().add(createEconomyLineItem(
                "Geoengineering & terraforming subsidies",
                String.format("-%,.0f ₵", report.terraformingSubsidies()),
                "State funding for ongoing planetary biosphere and atmospheric alteration projects.",
                Color.LIGHTCORAL
        ));

        Region divider = new Region();
        divider.setPrefHeight(1);
        divider.setStyle("-fx-background-color: rgba(231, 76, 60, 0.4);");

        HBox totalRow = new HBox(8);
        totalRow.setAlignment(Pos.CENTER_LEFT);
        Text totalLabel = new Text("Total projected expenditures:");
        totalLabel.setFill(Color.WHITE);
        totalLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 12));
        Region totalSpacer = new Region();
        HBox.setHgrow(totalSpacer, Priority.ALWAYS);
        Text totalVal = new Text(String.format("-%,.0f ₵/turn", report.totalCosts()));
        totalVal.setFill(Color.LIGHTCORAL);
        totalVal.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
        totalRow.getChildren().addAll(totalLabel, totalSpacer, totalVal);

        box.getChildren().addAll(title, subtitle, itemsBox, divider, totalRow);
        return box;
    }

    private VBox createEconomyLineItem(String title, String amount, String description, Color amountColor) {
        VBox item = new VBox(2);
        item.setPadding(new Insets(4, 6, 4, 6));
        item.setStyle("-fx-background-color: rgba(15, 25, 45, 0.5); -fx-background-radius: 4;");

        HBox top = new HBox(8);
        top.setAlignment(Pos.CENTER_LEFT);

        Text titleText = new Text("• " + title);
        titleText.setFill(Color.WHITE);
        titleText.setFont(Font.font("Verdana", FontWeight.BOLD, 11));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Text amountText = new Text(amount);
        amountText.setFill(amountColor);
        amountText.setFont(Font.font("Verdana", FontWeight.BOLD, 11));

        top.getChildren().addAll(titleText, spacer, amountText);

        Text descText = new Text(description);
        descText.setFill(Color.GAINSBORO);
        descText.setFont(Font.font("Verdana", 9));

        item.getChildren().addAll(top, descText);
        return item;
    }

    private VBox createColonyLedgerCard(EmpireEconomyReport report) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: rgba(25, 40, 70, 0.7); -fx-background-radius: 8; " +
                "-fx-border-color: #3498db; -fx-border-width: 1.5; -fx-border-radius: 8;");

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        Text title = new Text("Colonial economic ledger & tax contributions");
        title.setFill(Color.LIGHTCYAN);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 14));

        Label noteBadge = new Label("Colony tax rates governed individually per colony");
        noteBadge.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white; " +
                "-fx-padding: 2 6 2 6; -fx-background-radius: 4; -fx-font-size: 10;");

        header.getChildren().addAll(title, noteBadge);

        Text desc = new Text("Overview of population, gross colonial output, taxes collected and administrative costs per inhabited world. Note: Tax rates are not set here as local taxation policy is configured per colony in colonial administration.");
        desc.setFill(Color.LIGHTGRAY);
        desc.setFont(Font.font("Verdana", 10));

        box.getChildren().addAll(header, desc);

        if (report.colonyEntries().isEmpty()) {
            Text empty = new Text("No colonized worlds currently established under imperial administration.");
            empty.setFill(Color.LIGHTGRAY);
            empty.setFont(Font.font("Verdana", 11));
            box.getChildren().add(empty);
        } else {
            VBox list = new VBox(6);
            for (ColonyEconomyEntry c : report.colonyEntries()) {
                HBox row = new HBox(10);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(6, 10, 6, 10));
                row.setStyle("-fx-background-color: rgba(18, 30, 55, 0.7); -fx-background-radius: 4; -fx-border-color: rgba(120, 170, 255, 0.2); -fx-border-radius: 4;");

                Text name = new Text(String.format("%s (%s%s)", c.bodyName(), c.isMoon() ? "Moon of " : "Planet in ", c.systemName()));
                name.setFill(Color.GOLD);
                name.setFont(Font.font("Verdana", FontWeight.BOLD, 11));
                name.setWrappingWidth(200);

                Text pop = new Text(String.format("Pop: %,d", c.population()));
                pop.setFill(Color.LIGHTYELLOW);
                pop.setFont(Font.font("Verdana", 10));
                pop.setWrappingWidth(110);

                Text gdp = new Text(String.format("Gross: %,.0f ₵", c.grossOutputCredits()));
                gdp.setFill(Color.WHITE);
                gdp.setFont(Font.font("Verdana", 10));
                gdp.setWrappingWidth(110);

                Text tax = new Text(String.format("Tax: +%,.0f ₵", c.taxCollectedCredits()));
                tax.setFill(Color.LIGHTGREEN);
                tax.setFont(Font.font("Verdana", FontWeight.BOLD, 10));
                tax.setWrappingWidth(100);

                Text cost = new Text(String.format("Admin: -%,.0f ₵", c.localGovernanceCostCredits()));
                cost.setFill(Color.LIGHTCORAL);
                cost.setFont(Font.font("Verdana", 10));
                cost.setWrappingWidth(100);

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                Text net = new Text(String.format("Net: %+,.0f ₵", c.netContributionCredits()));
                net.setFill(c.netContributionCredits() >= 0 ? Color.LIGHTGREEN : Color.LIGHTCORAL);
                net.setFont(Font.font("Verdana", FontWeight.BOLD, 11));

                row.getChildren().addAll(name, pop, gdp, tax, cost, spacer, net);
                list.getChildren().add(row);
            }
            box.getChildren().add(list);
        }

        return box;
    }

    private VBox createCorporateEconomyCard(EmpireEconomyReport report) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: rgba(35, 25, 55, 0.7); -fx-background-radius: 8; " +
                "-fx-border-color: #9b59b6; -fx-border-width: 1.5; -fx-border-radius: 8;");

        Text title = new Text("Corporate commercial sector & private tariff contributions");
        title.setFill(Color.LIGHTBLUE);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 14));

        Text desc = new Text("Summary of chartered private corporations registered under sovereign imperial jurisdiction and their tariff yields.");
        desc.setFill(Color.LIGHTGRAY);
        desc.setFont(Font.font("Verdana", 10));

        box.getChildren().addAll(title, desc);

        if (report.corporateEntries().isEmpty()) {
            Text empty = new Text("No private corporations currently registered under the sovereign empire.");
            empty.setFill(Color.LIGHTGRAY);
            empty.setFont(Font.font("Verdana", 11));
            box.getChildren().add(empty);
        } else {
            VBox list = new VBox(6);
            for (CorporateEconomyEntry corp : report.corporateEntries()) {
                HBox row = new HBox(10);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(6, 10, 6, 10));
                row.setStyle("-fx-background-color: rgba(22, 18, 42, 0.7); -fx-background-radius: 4; -fx-border-color: rgba(155, 89, 182, 0.3); -fx-border-radius: 4;");

                Text name = new Text(corp.corporationName());
                name.setFill(Color.LIGHTCYAN);
                name.setFont(Font.font("Verdana", FontWeight.BOLD, 11));
                name.setWrappingWidth(180);

                Label orientationBadge = new Label(corp.marketOrientation());
                orientationBadge.setStyle("-fx-background-color: #2c3e50; -fx-text-fill: #00cec9; " +
                        "-fx-padding: 1 5 1 5; -fx-background-radius: 3; -fx-font-size: 9;");

                Text hq = new Text("HQ: " + corp.hqEntityId().toUpperCase());
                hq.setFill(Color.WHITE);
                hq.setFont(Font.font("Verdana", 10));
                hq.setWrappingWidth(100);

                Text cap = new Text(String.format("Reserves: %,.0f ₵", corp.liquidCapital()));
                cap.setFill(Color.GOLD);
                cap.setFont(Font.font("Verdana", 10));
                cap.setWrappingWidth(130);

                Text fac = new Text(String.format("Facilities: %d", corp.ownedFacilitiesCount()));
                fac.setFill(Color.LIGHTYELLOW);
                fac.setFont(Font.font("Verdana", 10));
                fac.setWrappingWidth(80);

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                Text tariff = new Text(String.format("Tariff yield: +%,.0f ₵/turn", corp.estimatedTariffPaid()));
                tariff.setFill(Color.LIGHTGREEN);
                tariff.setFont(Font.font("Verdana", FontWeight.BOLD, 11));

                row.getChildren().addAll(name, orientationBadge, hq, cap, fac, spacer, tariff);
                list.getChildren().add(row);
            }
            box.getChildren().add(list);
        }

        return box;
    }

    public EmpireEconomyReport calculateEmpireEconomyReport() {
        if (empires.isEmpty()) {
            try {
                empires.addAll(DataModelLoader.loadEmpires());
            } catch (IOException e) {
                logger.error("Failed to load empires fallback data", e);
            }
        }
        if (corporations.isEmpty()) {
            try {
                corporations.addAll(DataModelLoader.loadCorporations());
            } catch (IOException e) {
                logger.error("Failed to load corporate registry fallback data", e);
            }
        }
        Empire playerEmpire = getPlayerEmpire();
        double treasury = playerEmpire != null ? playerEmpire.treasuryCredits() : 100_000.0;
        double corpTaxRate = playerEmpire != null ? playerEmpire.corporateTaxRate() : 0.15;
        List<String> controlledSystems = playerEmpire != null ? playerEmpire.controlledSystemIds() : List.of();

        List<PlanetaryBodyEntry> allBodies = getAllPlanetaryBodies();
        List<PlanetaryBodyEntry> playerColonies = allBodies.stream()
                .filter(PlanetaryBodyEntry::isColonized)
                .filter(b -> controlledSystems.isEmpty() || controlledSystems.contains(b.systemId()))
                .toList();

        long totalPop = playerColonies.stream().mapToLong(PlanetaryBodyEntry::totalPopulation).sum();
        int colonizedCount = playerColonies.size();
        int controlledSysCount = (int) playerColonies.stream().map(PlanetaryBodyEntry::systemId).distinct().count();
        if (playerEmpire != null && !playerEmpire.controlledSystemIds().isEmpty()) {
            controlledSysCount = Math.max(controlledSysCount, playerEmpire.controlledSystemIds().size());
        }

        List<ColonyEconomyEntry> colonyLedger = new ArrayList<>();
        double totalColonialTax = 0.0;
        double totalLocalGovCost = 0.0;

        for (PlanetaryBodyEntry colony : playerColonies) {
            long pop = colony.totalPopulation();
            // Gross colonial economic output: ~0.005 credits per citizen baseline
            double grossOutput = pop * 0.005;
            // Local colony tax collected (estimated default baseline 10% effective tax rate per colony)
            double taxCollected = grossOutput * 0.10;
            // Local municipal & planetary governance cost: 200 credits base + 0.0005 per citizen
            double localGov = 200.0 + (pop * 0.0005);
            double netContrib = taxCollected - localGov;

            totalColonialTax += taxCollected;
            totalLocalGovCost += localGov;

            colonyLedger.add(new ColonyEconomyEntry(
                    colony.id(),
                    colony.name(),
                    colony.systemName(),
                    colony.isMoon(),
                    pop,
                    grossOutput,
                    taxCollected,
                    localGov,
                    netContrib
            ));
        }

        // State Industrial output revenue
        List<IndustrialFacility> playerFacilities = facilities.stream()
                .filter(f -> f.ownerEntityId().equalsIgnoreCase(playerEmpireId) && "PUBLIC_STATE".equalsIgnoreCase(f.ownershipType()))
                .toList();
        double stateIndustryIncome = playerFacilities.size() * 350.0;

        // Corporate tariffs
        List<Corporation> playerCorps = getCorporationsForPlayerEmpire();
        List<CorporateEconomyEntry> corporateLedger = new ArrayList<>();
        double totalCorporateTariffs = 0.0;

        for (Corporation corp : playerCorps) {
            double estimatedTariff = (corp.liquidCapitalReserves() * 0.001) + (corp.ownedFacilityIds().size() * 120.0 * corpTaxRate);
            totalCorporateTariffs += estimatedTariff;
            corporateLedger.add(new CorporateEconomyEntry(
                    corp.id(),
                    corp.name(),
                    corp.headquartersEntityId(),
                    corp.marketOrientation(),
                    corp.liquidCapitalReserves(),
                    corp.ownedFacilityIds().size(),
                    estimatedTariff
            ));
        }

        // Space elevators transit tariffs
        List<SpaceElevator> playerElevators = spaceElevators.stream()
                .filter(e -> e.ownerEntityId().equalsIgnoreCase(playerEmpireId))
                .toList();
        double spaceElevatorIncome = playerElevators.stream()
                .mapToDouble(e -> e.isOperational() ? (e.transitThroughputCapacityKgPerTurn() * 0.00005) : 0.0)
                .sum();

        // Mining & resource royalties
        List<GeologicalDeposit> playerDeposits = deposits.stream()
                .filter(d -> playerColonies.stream().anyMatch(c -> c.id().equalsIgnoreCase(d.planetId())))
                .toList();
        double miningRoyalties = playerDeposits.size() * 150.0;

        double totalIncome = totalColonialTax + stateIndustryIncome + totalCorporateTariffs + spaceElevatorIncome + miningRoyalties;

        // Expenditures:
        // 1. Colonial governance overhead (aggregated local municipal and governor overhead)
        double governanceExpenses = totalLocalGovCost + (controlledSysCount * 300.0);

        // 2. Imperial cabinet ministries budget
        int ministryCount = (playerEmpire != null && playerEmpire.ministries() != null) ? playerEmpire.ministries().size() : 5;
        double ministryBudgets = ministryCount * 250.0;

        // 3. Infrastructure & power grids maintenance
        double infraMaintenance = (playerFacilities.size() * 80.0) + (powerGrids.size() * 50.0);

        // 4. Orbital stations & shipyard maintenance
        List<OrbitalStation> playerStations = orbitalStations.stream()
                .filter(s -> s.ownerEntityId().equalsIgnoreCase(playerEmpireId))
                .toList();
        double stationMaintenance = playerStations.size() * 400.0;

        // 5. Scientific research subsidies
        double researchSubsidies = researchProjects.size() * 200.0;

        // 6. Terraforming subsidies
        double terraformingSubsidies = terraformingProjects.size() * 300.0;

        double totalCosts = governanceExpenses + ministryBudgets + infraMaintenance + stationMaintenance + researchSubsidies + terraformingSubsidies;
        double netBalance = totalIncome - totalCosts;

        return new EmpireEconomyReport(
                treasury,
                corpTaxRate,
                totalPop,
                colonizedCount,
                controlledSysCount,
                totalColonialTax,
                stateIndustryIncome,
                totalCorporateTariffs,
                spaceElevatorIncome,
                miningRoyalties,
                totalIncome,
                governanceExpenses,
                ministryBudgets,
                infraMaintenance,
                stationMaintenance,
                researchSubsidies,
                terraformingSubsidies,
                totalCosts,
                netBalance,
                colonyLedger,
                corporateLedger
        );
    }

    public SystemEconomyReport calculateSystemEconomyReport(String systemId) {
        if (empires.isEmpty()) {
            try {
                empires.addAll(DataModelLoader.loadEmpires());
            } catch (IOException e) {
                logger.error("Failed to load empires fallback data", e);
            }
        }
        if (corporations.isEmpty()) {
            try {
                corporations.addAll(DataModelLoader.loadCorporations());
            } catch (IOException e) {
                logger.error("Failed to load corporate registry fallback data", e);
            }
        }

        SolarSystem system = solarSystems.stream()
                .filter(s -> s.id().equals(systemId))
                .findFirst()
                .orElse(null);

        String systemName = system != null ? system.name() : (systemId != null ? systemId : "Unknown");
        Empire playerEmpire = getPlayerEmpire();
        String empireId = playerEmpire != null ? playerEmpire.id() : playerEmpireId;

        long systemPop = 0;
        int colonizedCount = 0;
        List<String> systemBodyIds = new ArrayList<>();
        if (system != null && system.planets() != null) {
            for (Planet p : system.planets()) {
                systemBodyIds.add(p.id());
                long pPop = 0;
                if (p.populations() != null) {
                    for (Population pop : p.populations()) {
                        pPop += pop.totalCount();
                    }
                }
                if (pPop > 0) {
                    systemPop += pPop;
                    colonizedCount++;
                }
                if (p.moons() != null) {
                    for (Moon m : p.moons()) {
                        systemBodyIds.add(m.id());
                        long mPop = 0;
                        if (m.populations() != null) {
                            for (Population pop : m.populations()) {
                                mPop += pop.totalCount();
                            }
                        }
                        if (mPop > 0) {
                            systemPop += mPop;
                            colonizedCount++;
                        }
                    }
                }
            }
        }

        double grossOutput = systemPop * 0.005;
        double colonialTaxes = grossOutput * 0.10;

        double corporateTariffs = 0.0;
        for (Corporation corp : getCorporationsForPlayerEmpire()) {
            if (systemBodyIds.contains(corp.headquartersEntityId())) {
                corporateTariffs += (corp.liquidCapitalReserves() * 0.0005) + (corp.ownedFacilityIds().size() * 60.0);
            }
        }

        long elevatorsInSystem = spaceElevators.stream()
                .filter(se -> systemBodyIds.contains(se.planetId()))
                .count();
        double elevatorFees = elevatorsInSystem * 300.0;

        long depositsInSystem = deposits.stream()
                .filter(d -> systemBodyIds.contains(d.planetId()))
                .count();
        double miningRoyalties = depositsInSystem * 50.0;

        long stateFacilities = facilities.stream()
                .filter(f -> systemBodyIds.contains(f.planetId()) && "PUBLIC_STATE".equalsIgnoreCase(f.ownershipType()))
                .count();
        double stateIndustryIncome = stateFacilities * 350.0;

        double totalRevenues = colonialTaxes + corporateTariffs + elevatorFees + miningRoyalties + stateIndustryIncome;

        SystemEconomy economy = systemEconomies.stream()
                .filter(se -> se.systemId().equals(systemId))
                .findFirst()
                .orElse(null);

        if (economy == null) {
            economy = SystemEconomy.createDefault(systemId, empireId, systemPop);
        }

        double publicSectorFunding = economy.totalBudgetCredits();
        double governorAdmin = systemGovernors.stream().anyMatch(g -> g.solarSystemId().equals(systemId)) ? 100.0 : 0.0;
        long stationsInSystem = orbitalStations.stream().filter(st -> systemId.equals(st.systemId()) || systemBodyIds.contains(st.planetOrbitId())).count();
        double stationMaint = stationsInSystem * 200.0;

        double totalExpenditures = publicSectorFunding + governorAdmin + stationMaint;
        double netBalance = totalRevenues - totalExpenditures;

        return new SystemEconomyReport(
                systemId,
                systemName,
                empireId,
                systemPop,
                colonizedCount,
                grossOutput,
                colonialTaxes,
                corporateTariffs,
                elevatorFees,
                miningRoyalties,
                stateIndustryIncome,
                totalRevenues,
                publicSectorFunding,
                governorAdmin,
                stationMaint,
                totalExpenditures,
                netBalance,
                economy
        );
    }

    private VBox createSystemEconomyOverviewCard(SystemEconomyReport report) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(14));
        box.setStyle("-fx-background-color: rgba(30, 50, 90, 0.7); -fx-background-radius: 8; " +
                "-fx-border-color: #3498db; -fx-border-width: 1.5; -fx-border-radius: 8;");

        HBox topRow = new HBox(12);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Text sysName = new Text(report.systemName() + " — System economy overview");
        sysName.setFill(Color.GOLD);
        sysName.setFont(Font.font("Verdana", FontWeight.BOLD, 18));

        Label ownerBadge = new Label("Empire: " + report.empireId());
        ownerBadge.setStyle("-fx-background-color: #2c3e50; -fx-text-fill: #00cec9; " +
                "-fx-font-weight: bold; -fx-padding: 3 8 3 8; -fx-background-radius: 4; -fx-font-size: 11;");

        Label popBadge = new Label(String.format("Population: %,d", report.systemPopulation()));
        popBadge.setStyle("-fx-background-color: #34495e; -fx-text-fill: #dfe6e9; " +
                "-fx-padding: 3 8 3 8; -fx-background-radius: 4; -fx-font-size: 11;");

        Label colonyBadge = new Label(String.format("Colonized bodies: %d", report.colonizedBodiesCount()));
        colonyBadge.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; " +
                "-fx-font-weight: bold; -fx-padding: 3 8 3 8; -fx-background-radius: 4; -fx-font-size: 11;");

        topRow.getChildren().addAll(sysName, ownerBadge, popBadge, colonyBadge);

        GridPane metricsGrid = new GridPane();
        metricsGrid.setHgap(20);
        metricsGrid.setVgap(8);

        Label grossLbl = new Label(String.format("Gross system output: %,.0f ₵/turn", report.grossSystemOutput()));
        grossLbl.setTextFill(Color.LIGHTCYAN);
        grossLbl.setFont(Font.font("Verdana", FontWeight.BOLD, 12));

        Label revLbl = new Label(String.format("Total system revenues: +%,.0f ₵/turn", report.totalRevenues()));
        revLbl.setTextFill(Color.LIGHTGREEN);
        revLbl.setFont(Font.font("Verdana", FontWeight.BOLD, 12));

        Label expLbl = new Label(String.format("Total system expenditures: -%,.0f ₵/turn", report.totalExpenditures()));
        expLbl.setTextFill(Color.LIGHTCORAL);
        expLbl.setFont(Font.font("Verdana", FontWeight.BOLD, 12));

        boolean positiveNet = report.netSystemBalance() >= 0;
        Label netLbl = new Label(String.format("Net system balance: %+,.0f ₵/turn", report.netSystemBalance()));
        netLbl.setTextFill(positiveNet ? Color.LIGHTGREEN : Color.LIGHTCORAL);
        netLbl.setFont(Font.font("Verdana", FontWeight.BOLD, 13));

        Label militiaInvestLbl = new Label(String.format("Accumulated militia investment: %,.0f ₵", report.economy().accumulatedMilitiaInvestment()));
        militiaInvestLbl.setTextFill(Color.GOLD);
        militiaInvestLbl.setFont(Font.font("Verdana", 11));

        double militiaCombatFactor = systemEconomyProcessor.calculateMilitiaCombatEfficiency(report.economy().accumulatedMilitiaInvestment());
        Label militiaPowerLbl = new Label(String.format("Conscripted militia combat readiness: %.0f%% power", militiaCombatFactor * 100.0));
        militiaPowerLbl.setTextFill(Color.LIGHTYELLOW);
        militiaPowerLbl.setFont(Font.font("Verdana", 11));

        metricsGrid.add(grossLbl, 0, 0);
        metricsGrid.add(revLbl, 1, 0);
        metricsGrid.add(expLbl, 2, 0);
        metricsGrid.add(netLbl, 3, 0);

        metricsGrid.add(militiaInvestLbl, 0, 1);
        metricsGrid.add(militiaPowerLbl, 1, 1);

        box.getChildren().addAll(topRow, metricsGrid);
        return box;
    }

    private VBox createSystemEconomyWorkbenchCard(SystemEconomyReport report) {
        VBox box = new VBox(12);
        box.setPadding(new Insets(14));
        box.setStyle("-fx-background-color: rgba(18, 30, 55, 0.85); -fx-background-radius: 8; " +
                "-fx-border-color: #00cec9; -fx-border-width: 1.5; -fx-border-radius: 8;");

        Text header = new Text("Public sector budget allocation workbench");
        header.setFill(Color.LIGHTCYAN);
        header.setFont(Font.font("Verdana", FontWeight.BOLD, 16));

        SystemEconomy economy = report.economy();
        long pop = report.systemPopulation();
        double basePopBudget = Math.max(1000.0, pop * 0.002);

        // Budget Level Controls
        HBox budgetRow = new HBox(12);
        budgetRow.setAlignment(Pos.CENTER_LEFT);

        Label budgetLbl = new Label("Total system budget (₵/turn):");
        budgetLbl.setTextFill(Color.WHITE);
        budgetLbl.setFont(Font.font("Verdana", FontWeight.BOLD, 12));

        TextField budgetField = new TextField(String.format("%.0f", economy.totalBudgetCredits()));
        budgetField.setPrefWidth(120);
        budgetField.setStyle("-fx-background-color: #2c3e50; -fx-text-fill: gold; -fx-font-weight: bold;");

        Button rate50Btn = createRateButton("Austerity (50%)", budgetField, basePopBudget * 0.5);
        Button rate100Btn = createRateButton("Standard (100%)", budgetField, basePopBudget * 1.0);
        Button rate150Btn = createRateButton("High investment (150%)", budgetField, basePopBudget * 1.5);
        Button rate200Btn = createRateButton("Maximum (200%)", budgetField, basePopBudget * 2.0);

        budgetRow.getChildren().addAll(budgetLbl, budgetField, rate50Btn, rate100Btn, rate150Btn, rate200Btn);

        // Sector allocation sliders
        GridPane slidersGrid = new GridPane();
        slidersGrid.setHgap(16);
        slidersGrid.setVgap(10);

        Slider eduSlider = createSectorSlider(economy.educationAllocation() * 100.0);
        Slider lawSlider = createSectorSlider(economy.lawAndOrderAllocation() * 100.0);
        Slider healthSlider = createSectorSlider(economy.healthAndWelfareAllocation() * 100.0);
        Slider infraSlider = createSectorSlider(economy.infrastructureAllocation() * 100.0);
        Slider militiaSlider = createSectorSlider(economy.planetaryMilitiasAllocation() * 100.0);

        Label eduValLbl = createPercentLabel(eduSlider.getValue());
        Label lawValLbl = createPercentLabel(lawSlider.getValue());
        Label healthValLbl = createPercentLabel(healthSlider.getValue());
        Label infraValLbl = createPercentLabel(infraSlider.getValue());
        Label militiaValLbl = createPercentLabel(militiaSlider.getValue());

        addSliderRow(slidersGrid, 0, "Education (Teachers & Science):", eduSlider, eduValLbl, Color.LIGHTSKYBLUE);
        addSliderRow(slidersGrid, 1, "Law and order (Police & Security):", lawSlider, lawValLbl, Color.LIGHTCORAL);
        addSliderRow(slidersGrid, 2, "Health and welfare (Medics & Morale):", healthSlider, healthValLbl, Color.LIGHTGREEN);
        addSliderRow(slidersGrid, 3, "Infrastructure (Engineers & Output):", infraSlider, infraValLbl, Color.GOLD);
        addSliderRow(slidersGrid, 4, "Planetary militias (Soldiers & Defense):", militiaSlider, militiaValLbl, Color.ORANGERED);

        // Preset Buttons
        HBox presetRow = new HBox(8);
        presetRow.setAlignment(Pos.CENTER_LEFT);

        Label presetLabel = new Label("Sector presets:");
        presetLabel.setTextFill(Color.LIGHTGRAY);
        presetLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 11));

        Button balPreset = createPresetButton("Balanced 20/20/20/20/20", eduSlider, lawSlider, healthSlider, infraSlider, militiaSlider, 20, 20, 20, 20, 20);
        Button sciPreset = createPresetButton("Science & education focus", eduSlider, lawSlider, healthSlider, infraSlider, militiaSlider, 40, 15, 15, 15, 15);
        Button lawPreset = createPresetButton("Security & defense focus", eduSlider, lawSlider, healthSlider, infraSlider, militiaSlider, 15, 35, 15, 15, 20);
        Button infraPreset = createPresetButton("Infrastructure focus", eduSlider, lawSlider, healthSlider, infraSlider, militiaSlider, 15, 15, 15, 40, 15);
        Button healthPreset = createPresetButton("Health & welfare focus", eduSlider, lawSlider, healthSlider, infraSlider, militiaSlider, 15, 15, 40, 15, 15);

        presetRow.getChildren().addAll(presetLabel, balPreset, sciPreset, lawPreset, infraPreset, healthPreset);

        // Live Indicators Box
        VBox liveBox = new VBox(8);
        liveBox.setPadding(new Insets(10));
        liveBox.setStyle("-fx-background-color: rgba(12, 20, 40, 0.9); -fx-background-radius: 6; -fx-border-color: #27ae60; -fx-border-width: 1; -fx-border-radius: 6;");

        Text liveTitle = new Text("Projected live societal & economic metrics");
        liveTitle.setFill(Color.LIGHTGREEN);
        liveTitle.setFont(Font.font("Verdana", FontWeight.BOLD, 13));

        GridPane liveGrid = new GridPane();
        liveGrid.setHgap(20);
        liveGrid.setVgap(6);

        Label liveEduLbl = new Label();
        Label liveLawLbl = new Label();
        Label liveHealthLbl = new Label();
        Label liveInfraLbl = new Label();
        Label liveMilitiaLbl = new Label();

        Label liveProf1 = new Label();
        Label liveProf2 = new Label();
        Label liveProf3 = new Label();
        Label liveHappiness = new Label();
        Label liveMilitiaCombat = new Label();

        liveGrid.add(liveEduLbl, 0, 0);
        liveGrid.add(liveLawLbl, 1, 0);
        liveGrid.add(liveHealthLbl, 2, 0);
        liveGrid.add(liveInfraLbl, 3, 0);
        liveGrid.add(liveMilitiaLbl, 4, 0);

        liveGrid.add(liveProf1, 0, 1, 2, 1);
        liveGrid.add(liveProf2, 2, 1, 2, 1);
        liveGrid.add(liveProf3, 4, 1);

        liveGrid.add(liveHappiness, 0, 2, 2, 1);
        liveGrid.add(liveMilitiaCombat, 2, 2, 3, 1);

        liveBox.getChildren().addAll(liveTitle, liveGrid);

        // Listener updater
        Runnable updateMetrics = () -> {
            double eduV = eduSlider.getValue();
            double lawV = lawSlider.getValue();
            double healthV = healthSlider.getValue();
            double infraV = infraSlider.getValue();
            double militiaV = militiaSlider.getValue();

            eduValLbl.setText(String.format("%.1f%%", eduV));
            lawValLbl.setText(String.format("%.1f%%", lawV));
            healthValLbl.setText(String.format("%.1f%%", healthV));
            infraValLbl.setText(String.format("%.1f%%", infraV));
            militiaValLbl.setText(String.format("%.1f%%", militiaV));

            double sum = eduV + lawV + healthV + infraV + militiaV;
            double nEdu = sum > 0 ? eduV / sum : 0.20;
            double nLaw = sum > 0 ? lawV / sum : 0.20;
            double nHealth = sum > 0 ? healthV / sum : 0.20;
            double nInfra = sum > 0 ? infraV / sum : 0.20;
            double nMilitia = sum > 0 ? militiaV / sum : 0.20;

            double bCredits = basePopBudget;
            try {
                bCredits = Double.parseDouble(budgetField.getText().trim());
            } catch (Exception ignored) {}

            double eduIdx = systemEconomyProcessor.calculateSectorEfficiency(bCredits, nEdu, pop);
            double lawIdx = systemEconomyProcessor.calculateSectorEfficiency(bCredits, nLaw, pop);
            double healthIdx = systemEconomyProcessor.calculateSectorEfficiency(bCredits, nHealth, pop);
            double infraIdx = systemEconomyProcessor.calculateSectorEfficiency(bCredits, nInfra, pop);
            double militiaIdx = systemEconomyProcessor.calculateSectorEfficiency(bCredits, nMilitia, pop);

            liveEduLbl.setText(String.format("Education: %.0f₵ (Idx: %.2f)", bCredits * nEdu, eduIdx));
            liveEduLbl.setTextFill(eduIdx >= 1.0 ? Color.LIGHTGREEN : Color.LIGHTCORAL);

            liveLawLbl.setText(String.format("Law: %.0f₵ (Idx: %.2f)", bCredits * nLaw, lawIdx));
            liveLawLbl.setTextFill(lawIdx >= 1.0 ? Color.LIGHTGREEN : Color.LIGHTCORAL);

            liveHealthLbl.setText(String.format("Health: %.0f₵ (Idx: %.2f)", bCredits * nHealth, healthIdx));
            liveHealthLbl.setTextFill(healthIdx >= 1.0 ? Color.LIGHTGREEN : Color.LIGHTCORAL);

            liveInfraLbl.setText(String.format("Infra: %.0f₵ (Idx: %.2f)", bCredits * nInfra, infraIdx));
            liveInfraLbl.setTextFill(infraIdx >= 1.0 ? Color.LIGHTGREEN : Color.LIGHTCORAL);

            liveMilitiaLbl.setText(String.format("Militia: %.0f₵ (Idx: %.2f)", bCredits * nMilitia, militiaIdx));
            liveMilitiaLbl.setTextFill(militiaIdx >= 1.0 ? Color.LIGHTGREEN : Color.LIGHTCORAL);

            long teachers = Math.max(10, (long) (pop * 0.0005 * eduIdx));
            long scientists = Math.max(10, (long) (pop * 0.0003 * eduIdx));
            long police = Math.max(15, (long) (pop * 0.0008 * lawIdx));
            long medics = Math.max(10, (long) (pop * 0.0004 * healthIdx));
            long engineers = Math.max(20, (long) (pop * 0.0010 * infraIdx));
            long technicians = Math.max(30, (long) (pop * 0.0015 * infraIdx));
            long soldiers = Math.max(25, (long) (pop * 0.0012 * militiaIdx));
            long recruitable = Math.max(100, (long) (pop * 0.0050 * militiaIdx));

            liveProf1.setText(String.format("Teachers: %,d | Scientists: %,d | Police: %,d", teachers, scientists, police));
            liveProf1.setTextFill(Color.WHITE);

            liveProf2.setText(String.format("Medics: %,d | Engineers: %,d | Technicians: %,d", medics, engineers, technicians));
            liveProf2.setTextFill(Color.WHITE);

            liveProf3.setText(String.format("Soldiers: %,d (Recruits: %,d)", soldiers, recruitable));
            liveProf3.setTextFill(Color.LIGHTYELLOW);

            double happyMod = ((eduIdx - 1.0) + (lawIdx - 1.0) + (healthIdx - 1.0) + (infraIdx - 1.0) + (militiaIdx - 1.0)) * 0.05;
            liveHappiness.setText(String.format("System happiness modifier: %+,.1f%%", happyMod * 100.0));
            liveHappiness.setTextFill(happyMod >= 0 ? Color.LIGHTGREEN : Color.LIGHTCORAL);

            double projectedInvestment = (economy.accumulatedMilitiaInvestment() * 0.95) + (bCredits * nMilitia);
            double projectedMilitiaPower = systemEconomyProcessor.calculateMilitiaCombatEfficiency(projectedInvestment);
            liveMilitiaCombat.setText(String.format("Projected militia siege power: %.0f%% (Accumulated: %,.0f ₵)", projectedMilitiaPower * 100.0, projectedInvestment));
            liveMilitiaCombat.setTextFill(Color.GOLD);
        };

        eduSlider.valueProperty().addListener((obs, o, n) -> updateMetrics.run());
        lawSlider.valueProperty().addListener((obs, o, n) -> updateMetrics.run());
        healthSlider.valueProperty().addListener((obs, o, n) -> updateMetrics.run());
        infraSlider.valueProperty().addListener((obs, o, n) -> updateMetrics.run());
        militiaSlider.valueProperty().addListener((obs, o, n) -> updateMetrics.run());
        budgetField.textProperty().addListener((obs, o, n) -> updateMetrics.run());

        updateMetrics.run();

        // Action Row
        HBox actionRow = new HBox(12);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        Button applyBtn = new Button("Apply and save system budget");
        applyBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 8 18 8 18; -fx-background-radius: 6;");
        applyBtn.setCursor(javafx.scene.Cursor.HAND);

        applyBtn.setOnAction(e -> {
            double eduV = eduSlider.getValue();
            double lawV = lawSlider.getValue();
            double healthV = healthSlider.getValue();
            double infraV = infraSlider.getValue();
            double militiaV = militiaSlider.getValue();

            double sum = eduV + lawV + healthV + infraV + militiaV;
            double nEdu = sum > 0 ? eduV / sum : 0.20;
            double nLaw = sum > 0 ? lawV / sum : 0.20;
            double nHealth = sum > 0 ? healthV / sum : 0.20;
            double nInfra = sum > 0 ? infraV / sum : 0.20;
            double nMilitia = sum > 0 ? militiaV / sum : 0.20;

            double bCredits = basePopBudget;
            try {
                bCredits = Double.parseDouble(budgetField.getText().trim());
            } catch (Exception ignored) {}

            SetSystemEconomyBudgetCommand cmd = new SetSystemEconomyBudgetCommand(
                    report.empireId(),
                    report.systemId(),
                    nEdu, nLaw, nHealth, nInfra, nMilitia, bCredits
            );

            HumanController controller = humanController != null ? humanController
                    : (menubar != null ? menubar.getHumanController() : null);

            if (controller != null) {
                controller.stageCommand(cmd);
            }

            // Also update local copy
            SystemEconomy updated = new SystemEconomy(
                    report.systemId(),
                    report.empireId(),
                    nEdu, nLaw, nHealth, nInfra, nMilitia,
                    bCredits,
                    economy.accumulatedMilitiaInvestment(),
                    systemEconomyProcessor.calculateSectorEfficiency(bCredits, nEdu, pop),
                    systemEconomyProcessor.calculateSectorEfficiency(bCredits, nLaw, pop),
                    systemEconomyProcessor.calculateSectorEfficiency(bCredits, nHealth, pop),
                    systemEconomyProcessor.calculateSectorEfficiency(bCredits, nInfra, pop),
                    systemEconomyProcessor.calculateSectorEfficiency(bCredits, nMilitia, pop),
                    economy.employedTeachers(),
                    economy.employedScientists(),
                    economy.employedPolice(),
                    economy.employedMedics(),
                    economy.employedEngineers(),
                    economy.employedTechnicians(),
                    economy.employedSoldiers(),
                    economy.recruitableSoldiers()
            );
            systemEconomies.removeIf(se -> se.systemId().equals(report.systemId()));
            systemEconomies.add(updated);

            if (feedbackLabel != null) {
                feedbackLabel.setText("System budget policy updated and staged for " + report.systemName() + ".");
            }
            renderCurrentTab();
        });

        actionRow.getChildren().add(applyBtn);

        box.getChildren().addAll(header, budgetRow, slidersGrid, presetRow, liveBox, actionRow);
        return box;
    }

    private Slider createSectorSlider(double initialVal) {
        Slider slider = new Slider(0, 100, initialVal);
        slider.setPrefWidth(260);
        slider.setShowTickLabels(false);
        slider.setShowTickMarks(true);
        slider.setMajorTickUnit(25);
        slider.setMinorTickCount(4);
        slider.setBlockIncrement(5);
        slider.setCursor(javafx.scene.Cursor.HAND);
        return slider;
    }

    private Label createPercentLabel(double val) {
        Label lbl = new Label(String.format("%.1f%%", val));
        lbl.setTextFill(Color.GOLD);
        lbl.setFont(Font.font("Verdana", FontWeight.BOLD, 12));
        lbl.setPrefWidth(55);
        return lbl;
    }

    private void addSliderRow(GridPane grid, int row, String name, Slider slider, Label valLbl, Color labelColor) {
        Label nameLbl = new Label(name);
        nameLbl.setTextFill(labelColor);
        nameLbl.setFont(Font.font("Verdana", FontWeight.BOLD, 11));
        nameLbl.setPrefWidth(280);

        grid.add(nameLbl, 0, row);
        grid.add(slider, 1, row);
        grid.add(valLbl, 2, row);
    }

    private Button createRateButton(String title, TextField budgetField, double value) {
        Button btn = new Button(title);
        btn.setFont(Font.font("Verdana", 10));
        btn.setCursor(javafx.scene.Cursor.HAND);
        btn.setStyle("-fx-background-color: #34495e; -fx-text-fill: white; -fx-padding: 3 8 3 8; -fx-background-radius: 4;");
        btn.setOnAction(e -> budgetField.setText(String.format("%.0f", value)));
        return btn;
    }

    private Button createPresetButton(String title, Slider s1, Slider s2, Slider s3, Slider s4, Slider s5,
                                      double v1, double v2, double v3, double v4, double v5) {
        Button btn = new Button(title);
        btn.setFont(Font.font("Verdana", 10));
        btn.setCursor(javafx.scene.Cursor.HAND);
        btn.setStyle("-fx-background-color: rgba(40, 60, 95, 0.8); -fx-text-fill: #dfe6e9; -fx-padding: 3 8 3 8; " +
                "-fx-background-radius: 4; -fx-border-color: #3498db; -fx-border-width: 1; -fx-border-radius: 4;");
        btn.setOnAction(e -> {
            s1.setValue(v1);
            s2.setValue(v2);
            s3.setValue(v3);
            s4.setValue(v4);
            s5.setValue(v5);
        });
        return btn;
    }

    private VBox createSystemRevenuesCard(SystemEconomyReport report) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: rgba(20, 35, 60, 0.7); -fx-background-radius: 8; -fx-border-color: #27ae60; -fx-border-width: 1; -fx-border-radius: 8;");

        Text header = new Text(String.format("System revenue streams (+%,.0f ₵/turn)", report.totalRevenues()));
        header.setFill(Color.LIGHTGREEN);
        header.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
        box.getChildren().add(header);

        VBox itemsBox = new VBox(6);
        itemsBox.getChildren().add(createEconomyLineItem("Colonial population taxes (10% effective rate)", String.format("+%,.0f ₵", report.colonialTaxes()), "Taxes collected across colonized bodies in this system.", Color.LIGHTGREEN));
        itemsBox.getChildren().add(createEconomyLineItem("Local corporate tariffs", String.format("+%,.0f ₵", report.corporateTariffs()), "Tariffs on corporations headquartered in this star system.", Color.LIGHTGREEN));
        itemsBox.getChildren().add(createEconomyLineItem("Space elevator transit fees", String.format("+%,.0f ₵", report.spaceElevatorFees()), "Surface-to-orbit cargo and passenger transit fees.", Color.LIGHTGREEN));
        itemsBox.getChildren().add(createEconomyLineItem("Geological mining royalties", String.format("+%,.0f ₵", report.miningRoyalties()), "Mining royalties from surveyed mineral veins in this system.", Color.LIGHTGREEN));
        itemsBox.getChildren().add(createEconomyLineItem("State industry facilities", String.format("+%,.0f ₵", report.stateIndustryIncome()), "Output profits from state manufacturing plants in this system.", Color.LIGHTGREEN));

        box.getChildren().add(itemsBox);
        return box;
    }

    private VBox createSystemExpensesCard(SystemEconomyReport report) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: rgba(35, 20, 40, 0.7); -fx-background-radius: 8; -fx-border-color: #e74c3c; -fx-border-width: 1; -fx-border-radius: 8;");

        Text header = new Text(String.format("System expenditure line items (-%,.0f ₵/turn)", report.totalExpenditures()));
        header.setFill(Color.LIGHTCORAL);
        header.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
        box.getChildren().add(header);

        VBox itemsBox = new VBox(6);
        itemsBox.getChildren().add(createEconomyLineItem("Public sector funding budget", String.format("-%,.0f ₵", report.publicSectorFunding()), "Allocated budget distributed across Education, Law, Health, Infrastructure and Militias.", Color.LIGHTCORAL));
        itemsBox.getChildren().add(createEconomyLineItem("Governor & municipal administration", String.format("-%,.0f ₵", report.governorAdministration()), "Local administrative salaries and system governance expenses.", Color.LIGHTCORAL));
        itemsBox.getChildren().add(createEconomyLineItem("Orbital station maintenance", String.format("-%,.0f ₵", report.stationMaintenance()), "Upkeep and repairs for orbital starbases in this system.", Color.LIGHTCORAL));

        box.getChildren().add(itemsBox);
        return box;
    }

    private VBox createSystemCelestialBodiesLedgerCard(SystemEconomyReport report) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: rgba(20, 30, 50, 0.7); -fx-background-radius: 8; -fx-border-color: #34495e; -fx-border-width: 1; -fx-border-radius: 8;");

        Text header = new Text("Celestial bodies in " + report.systemName() + " system");
        header.setFill(Color.LIGHTSKYBLUE);
        header.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
        box.getChildren().add(header);

        SolarSystem system = solarSystems.stream()
                .filter(s -> s.id().equals(report.systemId()))
                .findFirst()
                .orElse(null);

        if (system == null || system.planets() == null || system.planets().isEmpty()) {
            Text emptyText = new Text("No celestial bodies mapped in this star system.");
            emptyText.setFill(Color.LIGHTGRAY);
            box.getChildren().add(emptyText);
            return box;
        }

        VBox list = new VBox(4);
        for (Planet p : system.planets()) {
            long pPop = 0;
            if (p.populations() != null) {
                for (Population pop : p.populations()) {
                    pPop += pop.totalCount();
                }
            }
            list.getChildren().add(createBodyRow(p.name(), p.type(), false, pPop));

            if (p.moons() != null) {
                for (Moon m : p.moons()) {
                    long mPop = 0;
                    if (m.populations() != null) {
                        for (Population pop : m.populations()) {
                            mPop += pop.totalCount();
                        }
                    }
                    list.getChildren().add(createBodyRow(m.name() + " (Moon of " + p.name() + ")", "Moon", true, mPop));
                }
            }
        }

        box.getChildren().add(list);
        return box;
    }

    private HBox createBodyRow(String name, String type, boolean isMoon, long population) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(4, 8, 4, 8));
        row.setStyle("-fx-background-color: " + (isMoon ? "rgba(15, 25, 45, 0.5)" : "rgba(25, 40, 70, 0.6)") + "; -fx-background-radius: 4;");

        Text nameText = new Text((isMoon ? "  ↳ " : "● ") + name);
        nameText.setFill(isMoon ? Color.LIGHTCYAN : Color.WHITE);
        nameText.setFont(Font.font("Verdana", FontWeight.BOLD, 11));
        nameText.setWrappingWidth(240);

        Label typeBadge = new Label(type != null ? type : "Terrestrial");
        typeBadge.setStyle("-fx-background-color: #2c3e50; -fx-text-fill: #00cec9; -fx-padding: 1 6 1 6; -fx-background-radius: 3; -fx-font-size: 9;");

        Text popText = new Text(population > 0 ? String.format("Population: %,d", population) : "Uncolonized");
        popText.setFill(population > 0 ? Color.LIGHTYELLOW : Color.GRAY);
        popText.setFont(Font.font("Verdana", 10));
        popText.setWrappingWidth(160);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        double gross = population * 0.005;
        double tax = gross * 0.10;
        Text taxText = new Text(population > 0 ? String.format("Tax yield: +%,.0f ₵/turn", tax) : "—");
        taxText.setFill(population > 0 ? Color.LIGHTGREEN : Color.GRAY);
        taxText.setFont(Font.font("Verdana", FontWeight.BOLD, 11));

        row.getChildren().addAll(nameText, typeBadge, popText, spacer, taxText);
        return row;
    }

    // ==========================================
    // TAB 2: EMPIRE CABINET
    // ==========================================

    private VBox buildCabinetTabContent() {
        VBox container = new VBox(12);
        container.setPadding(new Insets(4));
        VBox.setVgrow(container, Priority.ALWAYS);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.setPadding(new Insets(6));
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        VBox content = new VBox(14);
        scrollPane.setContent(content);

        // Find current player empire
        Empire playerEmpire = empires.stream()
                .filter(e -> e.id().equalsIgnoreCase(playerEmpireId))
                .findFirst()
                .orElse(empires.isEmpty() ? null : empires.get(0));

        if (playerEmpire != null) {
            content.getChildren().add(createSovereignEmpireCard(playerEmpire));
            content.getChildren().add(createMinistriesSection(playerEmpire));
            content.getChildren().add(createGovernorsSection(playerEmpire));
        } else {
            Text noEmpireText = new Text("No sovereign empire data available.");
            noEmpireText.setFill(Color.LIGHTCORAL);
            content.getChildren().add(noEmpireText);
        }

        // Other empires in galaxy
        if (empires.size() > 1) {
            Text otherTitle = new Text("Other sovereign galactic empires:");
            otherTitle.setFill(Color.LIGHTSKYBLUE);
            otherTitle.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
            content.getChildren().add(otherTitle);

            for (Empire other : empires) {
                if (playerEmpire != null && other.id().equals(playerEmpire.id())) continue;
                content.getChildren().add(createOtherEmpireSummaryCard(other));
            }
        }

        container.getChildren().add(scrollPane);
        return container;
    }

    private VBox createSovereignEmpireCard(Empire empire) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(14));
        box.setStyle("-fx-background-color: rgba(30, 50, 90, 0.7); -fx-background-radius: 8; " +
                "-fx-border-color: #3498db; -fx-border-width: 1.5; -fx-border-radius: 8;");

        HBox topRow = new HBox(12);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Text empireName = new Text(empire.name());
        empireName.setFill(Color.GOLD);
        empireName.setFont(Font.font("Verdana", FontWeight.BOLD, 18));

        Label structureBadge = new Label(empire.societyStructure());
        structureBadge.setStyle("-fx-background-color: #2c3e50; -fx-text-fill: #00cec9; " +
                "-fx-font-weight: bold; -fx-padding: 3 8 3 8; -fx-background-radius: 4; -fx-font-size: 11;");

        Label raceBadge = new Label("Primary race: " + empire.raceId());
        raceBadge.setStyle("-fx-background-color: #34495e; -fx-text-fill: #dfe6e9; " +
                "-fx-padding: 3 8 3 8; -fx-background-radius: 4; -fx-font-size: 11;");

        topRow.getChildren().addAll(empireName, structureBadge, raceBadge);

        GridPane metricsGrid = new GridPane();
        metricsGrid.setHgap(20);
        metricsGrid.setVgap(8);

        Label treasuryLbl = new Label(String.format("Treasury credits: %,.0f ₵", empire.treasuryCredits()));
        treasuryLbl.setTextFill(Color.LIGHTGREEN);
        treasuryLbl.setFont(Font.font("Verdana", FontWeight.BOLD, 12));

        Label taxLbl = new Label(String.format("Corporate tax rate: %.1f%%", empire.corporateTaxRate() * 100.0));
        taxLbl.setTextFill(Color.LIGHTSKYBLUE);
        taxLbl.setFont(Font.font("Verdana", FontWeight.BOLD, 12));

        String controlledStr = empire.controlledSystemIds().isEmpty() ? "None" : String.join(", ", empire.controlledSystemIds());
        Label systemsLbl = new Label("Controlled star systems: " + controlledStr);
        systemsLbl.setTextFill(Color.WHITE);
        systemsLbl.setFont(Font.font("Verdana", 11));

        int unlockedCount = empire.unlockedTechIds().size();
        Label techLbl = new Label("Researched technologies: " + unlockedCount + " unlocked");
        techLbl.setTextFill(Color.LIGHTYELLOW);
        techLbl.setFont(Font.font("Verdana", 11));

        metricsGrid.add(treasuryLbl, 0, 0);
        metricsGrid.add(taxLbl, 1, 0);
        metricsGrid.add(systemsLbl, 0, 1);
        metricsGrid.add(techLbl, 1, 1);

        box.getChildren().addAll(topRow, metricsGrid);
        return box;
    }

    private VBox createMinistriesSection(Empire empire) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: rgba(20, 35, 65, 0.65); -fx-background-radius: 8; " +
                "-fx-border-color: rgba(120, 170, 255, 0.4); -fx-border-width: 1; -fx-border-radius: 8;");

        Text title = new Text("Imperial cabinet ministries and ministerial portfolios");
        title.setFill(Color.AQUA);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
        box.getChildren().add(title);

        if (empire.ministries().isEmpty()) {
            Text empty = new Text("No active ministerial portfolio appointments.");
            empty.setFill(Color.LIGHTGRAY);
            box.getChildren().add(empty);
        } else {
            GridPane grid = new GridPane();
            grid.setHgap(16);
            grid.setVgap(8);

            int row = 0;
            for (MinistryAssignment assignment : empire.ministries()) {
                Label portLabel = new Label("• " + formatTitle(assignment.portfolioId()) + ":");
                portLabel.setTextFill(Color.GOLD);
                portLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 12));

                Label appointeeLabel = new Label(formatTitle(assignment.assignedCitizenProfessionId()));
                appointeeLabel.setTextFill(Color.WHITE);
                appointeeLabel.setFont(Font.font("Verdana", 12));

                Label synergyLabel = new Label(String.format("Efficiency multiplier: %.2fx", assignment.calculatedEfficiencyModifier()));
                synergyLabel.setTextFill(Color.LIGHTGREEN);
                synergyLabel.setFont(Font.font("Verdana", 11));

                grid.add(portLabel, 0, row);
                grid.add(appointeeLabel, 1, row);
                grid.add(synergyLabel, 2, row);
                row++;
            }
            box.getChildren().add(grid);
        }

        return box;
    }

    private VBox createGovernorsSection(Empire empire) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: rgba(20, 35, 65, 0.65); -fx-background-radius: 8; " +
                "-fx-border-color: rgba(120, 170, 255, 0.4); -fx-border-width: 1; -fx-border-radius: 8;");

        Text title = new Text("System governors and regional jurisdiction assignments");
        title.setFill(Color.AQUA);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
        box.getChildren().add(title);

        if (systemGovernors.isEmpty()) {
            Text empty = new Text("No regional system governors assigned.");
            empty.setFill(Color.LIGHTGRAY);
            box.getChildren().add(empty);
        } else {
            GridPane grid = new GridPane();
            grid.setHgap(16);
            grid.setVgap(8);

            int row = 0;
            for (SystemGovernor gov : systemGovernors) {
                Label nameLbl = new Label("• " + gov.name());
                nameLbl.setTextFill(Color.WHITE);
                nameLbl.setFont(Font.font("Verdana", FontWeight.BOLD, 12));

                Label sysLbl = new Label("System: " + gov.solarSystemId());
                sysLbl.setTextFill(Color.LIGHTBLUE);
                sysLbl.setFont(Font.font("Verdana", 11));

                Label profLbl = new Label("Profession: " + formatTitle(gov.professionId()));
                profLbl.setTextFill(Color.LIGHTGRAY);
                profLbl.setFont(Font.font("Verdana", 11));

                Label effLbl = new Label(String.format("Efficiency: +%.0f%% | Crime reduction: -%.0f%%",
                        gov.efficiencyBonus() * 100.0, gov.crimeReductionBonus() * 100.0));
                effLbl.setTextFill(Color.LIGHTGREEN);
                effLbl.setFont(Font.font("Verdana", 11));

                grid.add(nameLbl, 0, row);
                grid.add(sysLbl, 1, row);
                grid.add(profLbl, 2, row);
                grid.add(effLbl, 3, row);
                row++;
            }
            box.getChildren().add(grid);
        }

        return box;
    }

    private VBox createOtherEmpireSummaryCard(Empire other) {
        VBox box = new VBox(6);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-background-color: rgba(30, 45, 75, 0.5); -fx-background-radius: 6;");

        Text nameText = new Text(other.name() + " (" + other.societyStructure() + ")");
        nameText.setFill(Color.LIGHTSKYBLUE);
        nameText.setFont(Font.font("Verdana", FontWeight.BOLD, 13));

        Text detail = new Text(String.format("Treasury: %,.0f credits | Tax rate: %.1f%% | Systems: %s",
                other.treasuryCredits(), other.corporateTaxRate() * 100.0,
                other.controlledSystemIds().isEmpty() ? "None" : String.join(", ", other.controlledSystemIds())));
        detail.setFill(Color.GAINSBORO);
        detail.setFont(Font.font("Verdana", 11));

        box.getChildren().addAll(nameText, detail);
        return box;
    }

    // ==========================================
    // TAB 2: PLANETARY BODIES & OPERATIONS
    // ==========================================

    private HBox buildPlanetsTabContent() {
        HBox mainSplit = new HBox(12);
        HBox.setHgrow(mainSplit, Priority.ALWAYS);
        VBox.setVgrow(mainSplit, Priority.ALWAYS);

        // Left Panel: Filter, Sort, and Body List (40% width)
        VBox leftPanel = new VBox(8);
        leftPanel.setPrefWidth(380);
        leftPanel.setMinWidth(350);
        VBox.setVgrow(leftPanel, Priority.ALWAYS);

        // Filter and Sort controls
        HBox filterSortBar = new HBox(8);
        filterSortBar.setAlignment(Pos.CENTER_LEFT);

        ComboBox<FilterCategory> filterCombo = new ComboBox<>();
        filterCombo.getItems().addAll(FilterCategory.values());
        filterCombo.setValue(currentFilter);
        filterCombo.setPrefWidth(180);
        filterCombo.setStyle("-fx-font-size: 11px; -fx-cursor: hand;");
        filterCombo.setCursor(javafx.scene.Cursor.HAND);
        filterCombo.setOnAction(e -> {
            FilterCategory selected = filterCombo.getValue();
            if (selected != null && selected != currentFilter) {
                currentFilter = selected;
                renderCurrentTab();
            }
        });

        ComboBox<SortOption> sortCombo = new ComboBox<>();
        sortCombo.getItems().addAll(SortOption.values());
        sortCombo.setValue(currentSort);
        sortCombo.setPrefWidth(170);
        sortCombo.setStyle("-fx-font-size: 11px; -fx-cursor: hand;");
        sortCombo.setCursor(javafx.scene.Cursor.HAND);
        sortCombo.setOnAction(e -> {
            SortOption selected = sortCombo.getValue();
            if (selected != null && selected != currentSort) {
                currentSort = selected;
                renderCurrentTab();
            }
        });

        filterSortBar.getChildren().addAll(filterCombo, sortCombo);

        // Body List
        ScrollPane listScroll = new ScrollPane();
        listScroll.setFitToWidth(true);
        listScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(listScroll, Priority.ALWAYS);

        VBox bodyListBox = new VBox(6);
        listScroll.setContent(bodyListBox);

        List<PlanetaryBodyEntry> entries = getFilteredAndSortedPlanetaryBodies();

        if (entries.isEmpty()) {
            VBox emptyBox = new VBox(10);
            emptyBox.setPadding(new Insets(20));
            emptyBox.setAlignment(Pos.CENTER);
            Text emptyText = new Text("No planetary bodies match the selected filter.");
            emptyText.setFill(Color.LIGHTGRAY);
            emptyText.setFont(Font.font("Verdana", 12));
            emptyBox.getChildren().add(emptyText);
            bodyListBox.getChildren().add(emptyBox);
        } else {
            // If current selected body is not in the filtered list, default to first
            if (selectedBody == null || entries.stream().noneMatch(e -> e.id().equals(selectedBody.id()))) {
                selectedBody = entries.get(0);
            }

            for (PlanetaryBodyEntry entry : entries) {
                bodyListBox.getChildren().add(createBodyCard(entry));
            }
        }

        leftPanel.getChildren().addAll(filterSortBar, listScroll);

        // Right Panel: Selected Planetary Body Details and Technology-gated Operations
        VBox rightPanel = new VBox(8);
        HBox.setHgrow(rightPanel, Priority.ALWAYS);
        VBox.setVgrow(rightPanel, Priority.ALWAYS);

        ScrollPane rightScroll = new ScrollPane();
        rightScroll.setFitToWidth(true);
        rightScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        rightScroll.setPadding(new Insets(4));
        VBox.setVgrow(rightScroll, Priority.ALWAYS);

        VBox rightContent = new VBox(12);
        rightScroll.setContent(rightContent);

        if (selectedBody != null) {
            rightContent.getChildren().add(createBodyOverviewSection(selectedBody));
            rightContent.getChildren().add(createSurfaceBiomeSection(selectedBody));
            rightContent.getChildren().add(createPowerAndDepositsSection(selectedBody));
            rightContent.getChildren().add(createBodyMegastructuresSection(selectedBody));
            rightContent.getChildren().add(createTechnologyGatedOperationsSection(selectedBody));
        } else {
            VBox placeholder = new VBox(20);
            placeholder.setAlignment(Pos.CENTER);
            placeholder.setPadding(new Insets(40));
            Text msg = new Text("Select a planetary body from the list to view attributes and permissible operations.");
            msg.setFill(Color.LIGHTGRAY);
            placeholder.getChildren().add(msg);
            rightContent.getChildren().add(placeholder);
        }

        rightPanel.getChildren().add(rightScroll);

        mainSplit.getChildren().addAll(leftPanel, rightPanel);
        return mainSplit;
    }

    private VBox createBodyCard(PlanetaryBodyEntry entry) {
        VBox card = new VBox(4);
        card.setPadding(new Insets(8, 10, 8, 10));

        boolean isSelected = selectedBody != null && selectedBody.id().equals(entry.id());
        String background = isSelected ? "rgba(41, 128, 185, 0.55)" : "rgba(25, 45, 75, 0.6)";
        String border = isSelected ? "#3498db" : "rgba(120, 170, 255, 0.3)";

        card.setStyle(String.format("-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 1.5; " +
                "-fx-border-radius: 6; -fx-background-radius: 6; -fx-cursor: hand;", background, border));
        card.setCursor(javafx.scene.Cursor.HAND);

        HBox topRow = new HBox(6);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Text nameText = new Text(entry.name());
        nameText.setFill(isSelected ? Color.WHITE : Color.LIGHTCYAN);
        nameText.setFont(Font.font("Verdana", FontWeight.BOLD, 13));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label badge = new Label(entry.isMoon() ? "Moon" : entry.getBodyType());
        badge.setStyle("-fx-background-color: rgba(45, 52, 54, 0.8); -fx-text-fill: #74b9ff; " +
                "-fx-font-size: 9px; -fx-padding: 2 5 2 5; -fx-background-radius: 3;");

        topRow.getChildren().addAll(nameText, spacer, badge);

        HBox metaRow = new HBox(8);
        metaRow.setAlignment(Pos.CENTER_LEFT);

        Text sysText = new Text("System: " + entry.systemName());
        sysText.setFill(Color.GAINSBORO);
        sysText.setFont(Font.font("Verdana", 10));

        Text popText = new Text(entry.isColonized() ? String.format("Pop: %,d", entry.totalPopulation()) : "Uncolonized");
        popText.setFill(entry.isColonized() ? Color.LIGHTGREEN : Color.LIGHTGRAY);
        popText.setFont(Font.font("Verdana", 10));

        Text gravText = new Text(String.format("%.2f m/s²", entry.gravity()));
        gravText.setFill(Color.LIGHTGOLDENRODYELLOW);
        gravText.setFont(Font.font("Verdana", 10));

        metaRow.getChildren().addAll(sysText, popText, gravText);

        card.getChildren().addAll(topRow, metaRow);

        card.setOnMouseClicked(e -> {
            selectedBody = entry;
            renderCurrentTab();
        });

        return card;
    }

    private VBox createBodyOverviewSection(PlanetaryBodyEntry body) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(10, 12, 10, 12));
        box.setStyle("-fx-background-color: rgba(20, 38, 70, 0.75); -fx-background-radius: 8; " +
                "-fx-border-color: #3498db; -fx-border-width: 1; -fx-border-radius: 8;");

        HBox titleRow = new HBox(10);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Text name = new Text(body.name() + (body.isMoon() ? " (Moon of " + body.parentPlanetName() + ")" : ""));
        name.setFill(Color.WHITE);
        name.setFont(Font.font("Verdana", FontWeight.BOLD, 16));

        Label colonizedBadge = new Label(body.isColonized() ? "Colonized" : (body.isColonizable() ? "Colonizable" : "Uninhabitable"));
        String badgeColor = body.isColonized() ? "#2ecc71" : (body.isColonizable() ? "#f39c12" : "#95a5a6");
        colonizedBadge.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: white; " +
                "-fx-font-weight: bold; -fx-font-size: 10px; -fx-padding: 3 7 3 7; -fx-background-radius: 4;", badgeColor));

        titleRow.getChildren().addAll(name, colonizedBadge);

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(6);

        grid.add(createAttrLabel("System:", body.systemName()), 0, 0);
        grid.add(createAttrLabel("Celestial type:", body.getBodyType()), 1, 0);
        grid.add(createAttrLabel("Surface gravity:", String.format("%.2f m/s²", body.gravity())), 2, 0);

        grid.add(createAttrLabel("Diameter:", String.format("%,.0f km", body.diameter())), 0, 1);
        grid.add(createAttrLabel("Atmosphere:", body.getAtmosphere()), 1, 1);
        grid.add(createAttrLabel("Liquid water:", body.hasLiquidWater() ? "Present" : "None"), 2, 1);

        grid.add(createAttrLabel("Total population:", String.format("%,d citizens", body.totalPopulation())), 0, 2);
        grid.add(createAttrLabel("Resource deposits:", body.resourceCount() + " veins"), 1, 2);

        box.getChildren().addAll(titleRow, grid);
        return box;
    }

    private HBox createAttrLabel(String title, String val) {
        HBox row = new HBox(4);
        row.setAlignment(Pos.CENTER_LEFT);
        Text t = new Text(title);
        t.setFill(Color.LIGHTGRAY);
        t.setFont(Font.font("Verdana", 11));
        Text v = new Text(val);
        v.setFill(Color.WHITE);
        v.setFont(Font.font("Verdana", FontWeight.BOLD, 11));
        row.getChildren().addAll(t, v);
        return row;
    }

    private VBox createSurfaceBiomeSection(PlanetaryBodyEntry body) {
        VBox section = new VBox(6);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: rgba(18, 32, 58, 0.7); -fx-background-radius: 8; " +
                "-fx-border-color: rgba(120, 170, 255, 0.4); -fx-border-width: 1; -fx-border-radius: 8;");

        Text title = new Text("Surface biome grid and tile facility deployment");
        title.setFill(Color.AQUA);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 13));
        section.getChildren().add(title);

        Planet targetPlanet = body.planet();
        if (targetPlanet == null) {
            targetPlanet = new Planet(body.id(), body.name(), "Celestial moon body",
                    1.0e23, body.gravity(), 1.0, 0, body.diameter(), "MOON",
                    body.getAtmosphere(), body.hasLiquidWater(), 0.0, body.getResources(), List.of(), List.of());
        }

        PlanetBiomeGrid grid = biomeProcessor.generateDefaultGrid(targetPlanet, deposits);

        boolean isGas = grid.totalTiles() == 0 || (targetPlanet.type() != null &&
                (targetPlanet.type().toUpperCase().contains("GAS") || targetPlanet.type().toUpperCase().contains("ICE_GIANT")));

        if (isGas) {
            VBox gasCard = new VBox(6);
            gasCard.setPadding(new Insets(12));
            gasCard.setStyle("-fx-background-color: rgba(30, 45, 75, 0.6); -fx-background-radius: 6; " +
                    "-fx-border-color: rgba(120, 170, 255, 0.3); -fx-border-width: 1; -fx-border-radius: 6;");

            Label gasTitle = new Label("Gas giant celestial body");
            gasTitle.setTextFill(Color.GOLD);
            gasTitle.setFont(Font.font("Verdana", FontWeight.BOLD, 11));

            Label gasDesc = new Label("Gas giant celestial body — Gaseous atmosphere with no solid surface crust for ground facilities. Surface tile development unavailable.");
            gasDesc.setTextFill(Color.LIGHTGRAY);
            gasDesc.setFont(Font.font("Verdana", 10));
            gasDesc.setWrapText(true);

            gasCard.getChildren().addAll(gasTitle, gasDesc);
            section.getChildren().add(gasCard);
            return section;
        }

        VBox surfaceGridContainer = new VBox(6);
        surfaceGridContainer.setAlignment(Pos.CENTER);
        surfaceGridContainer.setPadding(new Insets(4));

        boolean canBuildTile = isTechnologyUnlocked("electricity") || isTechnologyUnlocked("industrial_production");

        for (int r = 0; r < grid.rows(); r++) {
            HBox rowBox = new HBox(6);
            rowBox.setAlignment(Pos.CENTER);
            int colsInRow = grid.columnsInRow(r);

            for (int c = 0; c < colsInRow; c++) {
                SurfaceTile tile = grid.getTile(r, c);
                if (tile == null) continue;

                VBox tileCard = new VBox(2);
                tileCard.setPadding(new Insets(4, 6, 4, 6));
                tileCard.setPrefSize(120, 58);

                String colorStyle = switch (tile.biomeType()) {
                    case SurfaceTile.BIOME_EQUATORIAL_DESERT -> "-fx-background-color: rgba(180, 130, 40, 0.5); -fx-border-color: #f1c40f;";
                    case SurfaceTile.BIOME_VOLCANIC_RIDGE -> "-fx-background-color: rgba(180, 50, 30, 0.5); -fx-border-color: #e74c3c;";
                    case SurfaceTile.BIOME_POLAR_ICE -> "-fx-background-color: rgba(60, 140, 200, 0.5); -fx-border-color: #3498db;";
                    case SurfaceTile.BIOME_OCEANIC_SHELF -> "-fx-background-color: rgba(30, 80, 160, 0.5); -fx-border-color: #2980b9;";
                    case SurfaceTile.BIOME_MOUNTAIN_RANGE -> "-fx-background-color: rgba(100, 100, 110, 0.5); -fx-border-color: #95a5a6;";
                    case SurfaceTile.BIOME_RADIOACTIVE_CRATER -> "-fx-background-color: rgba(120, 80, 140, 0.5); -fx-border-color: #9b59b6;";
                    case SurfaceTile.BIOME_BARREN_ROCK -> "-fx-background-color: rgba(90, 90, 90, 0.5); -fx-border-color: #7f8c8d;";
                    default -> "-fx-background-color: rgba(40, 140, 60, 0.5); -fx-border-color: #2ecc71;";
                };
                tileCard.setStyle(colorStyle + " -fx-background-radius: 4; -fx-border-width: 1; -fx-border-radius: 4;");

                Text tName = new Text(String.format("#%d %s", tile.tileIndex(), formatTitle(tile.biomeType())));
                tName.setFill(Color.WHITE);
                tName.setFont(Font.font("Verdana", FontWeight.BOLD, 9));

                Text depTxt = new Text(tile.hasDeposit() ? "Mineral vein" : "Clear terrain");
                depTxt.setFill(tile.hasDeposit() ? Color.GOLD : Color.LIGHTGRAY);
                depTxt.setFont(Font.font("Verdana", 8));

                Button buildBtn = new Button("Build");
                buildBtn.setStyle("-fx-background-color: #2c3e50; -fx-text-fill: white; -fx-font-size: 8px; -fx-padding: 1 4 1 4; -fx-cursor: hand;");
                buildBtn.setCursor(javafx.scene.Cursor.HAND);
                buildBtn.setDisable(!canBuildTile);

                final int tIdx = tile.tileIndex();
                final String bType = tile.biomeType();
                buildBtn.setOnAction(e -> {
                    if (humanController != null) {
                        humanController.stageCommand(new PlaceFacilityOnTileCommand(
                                body.id(), tIdx, "solar_power_array", playerEmpireId, "PUBLIC_STATE", 50, "technician"
                        ));
                        setFeedback("Commissioned solar power facility on tile #" + tIdx + " (" + bType + ")", true);
                    }
                });

                tileCard.getChildren().addAll(tName, depTxt, buildBtn);
                rowBox.getChildren().add(tileCard);
            }
            surfaceGridContainer.getChildren().add(rowBox);
        }

        ScrollPane tileScroll = new ScrollPane(surfaceGridContainer);
        tileScroll.setFitToWidth(true);
        tileScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        section.getChildren().add(tileScroll);
        return section;
    }

    private VBox createPowerAndDepositsSection(PlanetaryBodyEntry body) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-background-color: rgba(18, 32, 58, 0.7); -fx-background-radius: 8; " +
                "-fx-border-color: rgba(120, 170, 255, 0.4); -fx-border-width: 1; -fx-border-radius: 8;");

        HBox row = new HBox(16);
        row.setAlignment(Pos.TOP_LEFT);

        // Power Grid summary
        VBox powerBox = new VBox(4);
        HBox.setHgrow(powerBox, Priority.ALWAYS);

        Text powerTitle = new Text("Connected power grid");
        powerTitle.setFill(Color.AQUA);
        powerTitle.setFont(Font.font("Verdana", FontWeight.BOLD, 12));

        PowerGridState grid = powerGrids.stream()
                .filter(g -> g.entityId().equalsIgnoreCase(body.id()) || g.entityId().equalsIgnoreCase(body.systemId()))
                .findFirst()
                .orElse(null);

        if (grid != null) {
            Label genLbl = new Label(String.format("Generation: %.0f kW | Demand: %.0f kW", grid.totalGenerationKw(), grid.totalDemandKw()));
            genLbl.setTextFill(Color.WHITE);
            genLbl.setFont(Font.font("Verdana", 10));

            Label netLbl = new Label(String.format("Net: %s%.0f kW | Storage: %.0f / %.0f kWh",
                    grid.netBalanceKw() >= 0 ? "+" : "", grid.netBalanceKw(),
                    grid.currentStoredKwh(), grid.batteryCapacityKwh()));
            netLbl.setTextFill(grid.netBalanceKw() >= 0 ? Color.LIGHTGREEN : Color.LIGHTCORAL);
            netLbl.setFont(Font.font("Verdana", 10));

            powerBox.getChildren().addAll(powerTitle, genLbl, netLbl);
        } else {
            Label noGrid = new Label("No local power grid active. Build generation facilities to activate.");
            noGrid.setTextFill(Color.LIGHTGRAY);
            noGrid.setFont(Font.font("Verdana", 10));
            powerBox.getChildren().addAll(powerTitle, noGrid);
        }

        // Mineral Deposits summary
        VBox mineralBox = new VBox(4);
        HBox.setHgrow(mineralBox, Priority.ALWAYS);

        Text mineralTitle = new Text("Geological mineral deposits");
        mineralTitle.setFill(Color.GOLD);
        mineralTitle.setFont(Font.font("Verdana", FontWeight.BOLD, 12));

        List<GeologicalDeposit> bodyDeposits = deposits.stream()
                .filter(d -> d.planetId().equalsIgnoreCase(body.id()))
                .toList();

        if (bodyDeposits.isEmpty()) {
            Label noDep = new Label(body.getResources().isEmpty() ?
                    "No mineral deposits surveyed. Launch prospecting mission." :
                    "Surface resources: " + String.join(", ", body.getResources()));
            noDep.setTextFill(Color.LIGHTGRAY);
            noDep.setFont(Font.font("Verdana", 10));
            mineralBox.getChildren().addAll(mineralTitle, noDep);
        } else {
            for (GeologicalDeposit dep : bodyDeposits) {
                Label depLbl = new Label(String.format("• %s: %,.0f kg (Modifier: %.1fx)",
                        formatTitle(dep.materialId()), dep.remainingVolumeKg(), dep.concentrationModifier()));
                depLbl.setTextFill(Color.WHITE);
                depLbl.setFont(Font.font("Verdana", 10));
                mineralBox.getChildren().add(depLbl);
            }
        }

        row.getChildren().addAll(powerBox, mineralBox);
        box.getChildren().add(row);
        return box;
    }

    private VBox createBodyMegastructuresSection(PlanetaryBodyEntry body) {
        VBox section = new VBox(6);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: rgba(20, 35, 60, 0.7); -fx-background-radius: 8; " +
                "-fx-border-color: #f1c40f; -fx-border-width: 1; -fx-border-radius: 8;");

        Text title = new Text("Megastructures and orbital super-engineering");
        title.setFill(Color.GOLD);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 13));
        section.getChildren().add(title);

        List<Megastructure> bodyMegas = megastructures.stream()
                .filter(m -> m.targetCelestialId().equalsIgnoreCase(body.id()) ||
                        m.targetCelestialId().equalsIgnoreCase(body.name()) ||
                        m.systemId().equalsIgnoreCase(body.systemId()) ||
                        m.systemId().equalsIgnoreCase(body.systemName()))
                .toList();

        if (bodyMegas.isEmpty()) {
            Label emptyLbl = new Label("No megastructures constructed or in progress in this planetary system.");
            emptyLbl.setTextFill(Color.LIGHTGRAY);
            emptyLbl.setFont(Font.font("Verdana", 10));
            section.getChildren().add(emptyLbl);
        } else {
            for (Megastructure mega : bodyMegas) {
                VBox card = new VBox(4);
                card.setPadding(new Insets(6));
                card.setStyle("-fx-background-color: rgba(15, 25, 45, 0.6); -fx-background-radius: 6; " +
                        "-fx-border-color: " + (mega.isOperational() ? "#2ecc71" : "#e67e22") + "; -fx-border-width: 1; -fx-border-radius: 6;");

                HBox cardHeader = new HBox(8);
                cardHeader.setAlignment(Pos.CENTER_LEFT);

                Text mName = new Text(mega.name());
                mName.setFill(Color.WHITE);
                mName.setFont(Font.font("Verdana", FontWeight.BOLD, 12));

                Label typeBadge = new Label(mega.type());
                typeBadge.setStyle("-fx-background-color: #2c3e50; -fx-text-fill: #f1c40f; -fx-font-weight: bold; -fx-padding: 2 6 2 6; -fx-background-radius: 4; -fx-font-size: 9;");

                Label statusBadge = new Label(mega.isOperational() ? "Operational" : "Under construction");
                statusBadge.setStyle("-fx-background-color: " + (mega.isOperational() ? "#27ae60" : "#d35400") + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 2 6 2 6; -fx-background-radius: 4; -fx-font-size: 9;");

                cardHeader.getChildren().addAll(mName, typeBadge, statusBadge);

                Text specText = new Text(String.format("Stage: %d / %d | Power: %,.0f kW | Habitation: %,d citizens%s",
                        mega.currentStage(), mega.totalStages(), mega.energyYieldKw(), mega.habitableCapacity(),
                        mega.isOperational() ? "" : String.format(" | Turn progress: %.0f / %.0f", mega.currentStageProgress(), mega.requiredStageProgress())));
                specText.setFill(Color.LIGHTCYAN);
                specText.setFont(Font.font("Verdana", 10));

                card.getChildren().addAll(cardHeader, specText);
                section.getChildren().add(card);
            }
        }
        return section;
    }

    private VBox createTechnologyGatedOperationsSection(PlanetaryBodyEntry body) {
        VBox section = new VBox(8);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: rgba(20, 40, 75, 0.85); -fx-background-radius: 8; " +
                "-fx-border-color: #2ed573; -fx-border-width: 1.5; -fx-border-radius: 8;");

        Text title = new Text("Technology-gated contextual operations matrix");
        title.setFill(Color.LIGHTGREEN);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
        section.getChildren().add(title);

        GridPane opsGrid = new GridPane();
        opsGrid.setHgap(10);
        opsGrid.setVgap(8);

        // 1. Survey and prospecting
        boolean surveyUnlocked = isTechnologyUnlocked("geological_prospecting") ||
                isTechnologyUnlocked("remote_sensing") ||
                isTechnologyUnlocked("electricity");
        opsGrid.add(createOperationCard(
                "Planetary survey and prospecting",
                "Deploy orbital sensors and seismic prospecting probes to locate deep mineral veins.",
                surveyUnlocked,
                "Requires Geological prospecting tech",
                "Launch geological survey",
                e -> {
                    if (humanController != null) {
                        humanController.stageCommand(new StartProspectingMissionCommand(
                                body.id(), playerEmpireId
                        ));
                        setFeedback("Dispatched geological prospecting survey mission to " + body.name(), true);
                    }
                }
        ), 0, 0);

        // 2. Surface infrastructure and power
        boolean powerUnlocked = isTechnologyUnlocked("electricity") ||
                isTechnologyUnlocked("industrial_production");
        opsGrid.add(createOperationCard(
                "Surface infrastructure and power",
                "Construct solar collector arrays, industrial factories and connect local power grids.",
                powerUnlocked,
                "Requires Electricity / Industrial tech",
                "Build solar facility",
                e -> {
                    if (humanController != null) {
                        humanController.stageCommand(new BuildFacilityCommand(
                                body.id(), "solar_power_array", playerEmpireId, "PUBLIC_STATE", 50, "technician"
                        ));
                        setFeedback("Commissioned solar power generator array on " + body.name(), true);
                    }
                }
        ), 1, 0);

        // 3. Colony and habitation governance
        boolean colonyUnlocked = isTechnologyUnlocked("rocketry") ||
                isTechnologyUnlocked("surface_to_orbit_infrastructure");
        String colonyBtnText = body.isColonized() ? "Enact martial law" : "Deploy colony mission";
        opsGrid.add(createOperationCard(
                "Colony and habitation governance",
                body.isColonized() ?
                        "Govern colonial civil order, enforce emergency decrees and balance standard of living." :
                        "Deploy colony transport ship to establish permanent colonial settlements.",
                colonyUnlocked,
                "Requires Rocketry / Colonization tech",
                colonyBtnText,
                e -> {
                    if (humanController != null) {
                        if (body.isColonized()) {
                            humanController.stageCommand(new EnactMartialLawCommand(body.id(), playerEmpireId, 5));
                            setFeedback("Enacted emergency martial law decree on " + body.name(), true);
                        } else {
                            humanController.stageCommand(new ColonizePlanetCommand(playerEmpireId, body.systemId(), body.id(), "fleet_colony_alpha"));
                            setFeedback("Staged colony expedition transport command targeting " + body.name(), true);
                        }
                    }
                }
        ), 0, 1);

        // 4. Terraforming and geoengineering
        boolean terraformUnlocked = isTechnologyUnlocked("waste_management") ||
                isTechnologyUnlocked("gene_technology") ||
                isTechnologyUnlocked("industrial_production");
        opsGrid.add(createOperationCard(
                "Terraforming and geoengineering",
                "Initiate atmospheric cyanobacteria seeding, greenhouse alterations and planetary shields.",
                terraformUnlocked,
                "Requires Waste management / Gene tech",
                "Initiate bio-seeding project",
                e -> {
                    if (humanController != null) {
                        humanController.stageCommand(new StartTerraformingProjectCommand(
                                playerEmpireId, body.id(), "cyanobacteria_seeding", 1.0, 288.0, Map.of("nitrogen", 0.78, "oxygen", 0.21)
                        ));
                        setFeedback("Staged atmospheric geoengineering project on " + body.name(), true);
                    }
                }
        ), 1, 1);

        section.getChildren().add(opsGrid);
        return section;
    }

    private VBox createOperationCard(
            String cardTitle,
            String description,
            boolean isUnlocked,
            String lockReason,
            String actionButtonLabel,
            javafx.event.EventHandler<javafx.event.ActionEvent> actionHandler
    ) {
        VBox card = new VBox(5);
        card.setPadding(new Insets(8));
        card.setPrefWidth(260);

        String bg = isUnlocked ? "rgba(25, 55, 80, 0.7)" : "rgba(40, 40, 45, 0.7)";
        String border = isUnlocked ? "#2ed573" : "#e74c3c";
        card.setStyle(String.format("-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 1; " +
                "-fx-border-radius: 6; -fx-background-radius: 6;", bg, border));

        HBox top = new HBox(6);
        top.setAlignment(Pos.CENTER_LEFT);

        Text titleText = new Text(cardTitle);
        titleText.setFill(Color.WHITE);
        titleText.setFont(Font.font("Verdana", FontWeight.BOLD, 11));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label statusBadge = new Label(isUnlocked ? "UNLOCKED" : "LOCKED");
        statusBadge.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: white; " +
                "-fx-font-size: 8px; -fx-font-weight: bold; -fx-padding: 2 4 2 4; -fx-background-radius: 3;",
                isUnlocked ? "#27ae60" : "#c0392b"));

        top.getChildren().addAll(titleText, spacer, statusBadge);

        Text descText = new Text(description);
        descText.setFill(Color.LIGHTGRAY);
        descText.setFont(Font.font("Verdana", 9));
        descText.setWrappingWidth(240);

        Button actionBtn = new Button(isUnlocked ? actionButtonLabel : lockReason);
        actionBtn.setPrefWidth(245);
        actionBtn.setFont(Font.font("Verdana", FontWeight.BOLD, 10));
        if (isUnlocked) {
            actionBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-background-radius: 4; -fx-cursor: hand;");
            actionBtn.setCursor(javafx.scene.Cursor.HAND);
            actionBtn.setOnAction(actionHandler);
        } else {
            actionBtn.setStyle("-fx-background-color: #576574; -fx-text-fill: #c8d6e5; -fx-background-radius: 4;");
            actionBtn.setDisable(true);
        }

        card.getChildren().addAll(top, descText, actionBtn);
        return card;
    }

    private void setFeedback(String message, boolean success) {
        if (feedbackLabel != null) {
            feedbackLabel.setText(message);
            feedbackLabel.setTextFill(success ? Color.LIGHTGREEN : Color.LIGHTCORAL);
        }
    }

    // ==========================================
    // TAB 3: ORBITAL STATIONS & SHIPYARDS
    // ==========================================

    private VBox buildStationsTabContent() {
        VBox container = new VBox(12);
        container.setPadding(new Insets(4));
        VBox.setVgrow(container, Priority.ALWAYS);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.setPadding(new Insets(6));
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        VBox content = new VBox(14);
        scrollPane.setContent(content);

        Empire playerEmpire = getPlayerEmpire();
        String empireDisplayName = playerEmpire != null ? playerEmpire.name() : playerEmpireId;

        // Header
        HBox headerBox = new HBox(10);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        Text headerText = new Text("Orbital stations, shipyard slipways and space elevators");
        headerText.setFill(Color.GOLD);
        headerText.setFont(Font.font("Verdana", FontWeight.BOLD, 16));

        Label empBadge = new Label("Sovereign fleet infrastructure: " + empireDisplayName);
        empBadge.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white; " +
                "-fx-padding: 3 8 3 8; -fx-background-radius: 4; -fx-font-size: 11;");
        headerBox.getChildren().addAll(headerText, empBadge);
        content.getChildren().add(headerBox);

        // Filter stations for player empire
        List<OrbitalStation> playerStations = orbitalStations.stream()
                .filter(s -> s.ownerEntityId().equalsIgnoreCase(playerEmpireId))
                .toList();

        // 1. Orbital space stations section
        VBox stationsSection = new VBox(10);
        stationsSection.setPadding(new Insets(12));
        stationsSection.setStyle("-fx-background-color: rgba(25, 40, 70, 0.7); -fx-background-radius: 8; " +
                "-fx-border-color: #3498db; -fx-border-width: 1; -fx-border-radius: 8;");

        Text stationsTitle = new Text("Orbital space stations and starframe module arrays");
        stationsTitle.setFill(Color.LIGHTCYAN);
        stationsTitle.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
        stationsSection.getChildren().add(stationsTitle);

        if (playerStations.isEmpty()) {
            Text empty = new Text("No orbital space stations currently deployed for " + empireDisplayName +
                    ". Deploy construction ships or commission stations to establish orbital naval infrastructure.");
            empty.setFill(Color.LIGHTGRAY);
            empty.setFont(Font.font("Verdana", 12));
            stationsSection.getChildren().add(empty);
        } else {
            for (OrbitalStation station : playerStations) {
                stationsSection.getChildren().add(createStationCard(station));
            }
        }
        content.getChildren().add(stationsSection);

        // Filter space elevators for player empire
        List<SpaceElevator> playerElevators = spaceElevators.stream()
                .filter(e -> e.ownerEntityId().equalsIgnoreCase(playerEmpireId))
                .toList();

        // 2. Space elevators section
        VBox elevatorsSection = new VBox(10);
        elevatorsSection.setPadding(new Insets(12));
        elevatorsSection.setStyle("-fx-background-color: rgba(25, 40, 70, 0.7); -fx-background-radius: 8; " +
                "-fx-border-color: #f1c40f; -fx-border-width: 1; -fx-border-radius: 8;");

        Text elevatorsTitle = new Text("Planetary space elevators and geostationary tethers");
        elevatorsTitle.setFill(Color.GOLD);
        elevatorsTitle.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
        elevatorsSection.getChildren().add(elevatorsTitle);

        if (playerElevators.isEmpty()) {
            Text empty = new Text("No planetary space elevators currently constructed for " + empireDisplayName +
                    ". Construct space elevator tethers to reduce surface launch costs to near-zero.");
            empty.setFill(Color.LIGHTGRAY);
            empty.setFont(Font.font("Verdana", 12));
            elevatorsSection.getChildren().add(empty);
        } else {
            for (SpaceElevator elevator : playerElevators) {
                elevatorsSection.getChildren().add(createElevatorCard(elevator));
            }
        }
        content.getChildren().add(elevatorsSection);

        // 3. Macrostructure construction deployment projects
        List<ConstructionDeploymentProject> playerProjects = constructionProjects.stream()
                .filter(p -> playerEmpire != null && playerEmpire.controlledSystemIds().contains(p.targetSystemId()))
                .toList();

        if (!playerProjects.isEmpty()) {
            VBox projectsSection = new VBox(10);
            projectsSection.setPadding(new Insets(12));
            projectsSection.setStyle("-fx-background-color: rgba(25, 40, 70, 0.7); -fx-background-radius: 8; " +
                    "-fx-border-color: #e67e22; -fx-border-width: 1; -fx-border-radius: 8;");

            Text projTitle = new Text("Active macrostructure deployment projects");
            projTitle.setFill(Color.ORANGE);
            projTitle.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
            projectsSection.getChildren().add(projTitle);

            for (ConstructionDeploymentProject proj : playerProjects) {
                VBox projCard = new VBox(4);
                projCard.setPadding(new Insets(8));
                projCard.setStyle("-fx-background-color: rgba(15, 30, 55, 0.6); -fx-background-radius: 6;");

                Text pInfo = new Text(String.format("• Project [%s] Type: %s | Target: %s (System: %s) | Progress: %.0f/%.0f turns | Ship: %s | Completed: %s",
                        proj.projectId(), proj.targetStructureType(), proj.targetCelestialId(),
                        proj.targetSystemId().toUpperCase(), proj.accumulatedProgressTurns(), proj.requiredProgressTurns(),
                        proj.constructionShipId(), proj.isCompleted() ? "YES" : "NO"));
                pInfo.setFill(Color.LIGHTGREEN);
                pInfo.setFont(Font.font("Verdana", 11));
                projCard.getChildren().add(pInfo);
                projectsSection.getChildren().add(projCard);
            }
            content.getChildren().add(projectsSection);
        }

        container.getChildren().add(scrollPane);
        return container;
    }

    private VBox createStationCard(OrbitalStation station) {
        VBox sBox = new VBox(6);
        sBox.setPadding(new Insets(10));
        sBox.setStyle("-fx-background-color: rgba(15, 30, 55, 0.7); -fx-background-radius: 6; " +
                "-fx-border-color: rgba(52, 152, 219, 0.4); -fx-border-width: 1; -fx-border-radius: 6;");

        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Text sTitle = new Text(String.format("Station: %s [%s] (System: %s, Orbit: %s)",
                station.name(), station.id(), station.systemId().toUpperCase(), station.planetOrbitId()));
        sTitle.setFill(Color.LIGHTGREEN);
        sTitle.setFont(Font.font("Verdana", FontWeight.BOLD, 13));

        Label statusBadge = new Label(station.isOperational() ? "OPERATIONAL" : "OFFLINE");
        statusBadge.setStyle(station.isOperational()
                ? "-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 2 6 2 6; -fx-background-radius: 3; -fx-font-size: 10;"
                : "-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 2 6 2 6; -fx-background-radius: 3; -fx-font-size: 10;");

        topRow.getChildren().addAll(sTitle, statusBadge);

        Text stats = new Text(String.format("Slots: %d/%d | Power: %.1f kW (Demand: %.1f kW) | Shield: %.0f/%.0f | Hull: %.0f/%.0f | Armor: %s (%.1f cm)",
                station.getAllocatedSlots(), station.totalSlots(),
                station.currentPowerGenerationKw(), station.currentPowerDemandKw(),
                station.currentShieldHealth(), station.maxShieldHealth(),
                station.currentHullHealth(), station.maxHullHealth(),
                station.armorMaterialId(), station.armorThicknessCm()));
        stats.setFill(Color.WHITE);
        stats.setFont(Font.font("Verdana", 11));

        sBox.getChildren().addAll(topRow, stats);

        if (!station.modules().isEmpty()) {
            VBox moduleList = new VBox(3);
            moduleList.setPadding(new Insets(4, 0, 0, 10));

            for (StationModule mod : station.modules()) {
                boolean isShipyard = mod.type().equalsIgnoreCase(StationModule.TYPE_SHIPYARD_GRID) ||
                        mod.type().equalsIgnoreCase(StationModule.TYPE_CAPITAL_SLIPWAY) ||
                        mod.type().equalsIgnoreCase(StationModule.TYPE_CIVILIAN_HANGAR) ||
                        mod.type().equalsIgnoreCase(StationModule.TYPE_MILITARY_HANGAR);

                Text mText = new Text(String.format("  • [%s] %s (%d slots) | Power: -%.1f kW / +%.1f kW | %s (%d workers) | %s%s",
                        mod.type(), mod.name(), mod.slotSize(),
                        mod.powerDrawKw(), mod.powerOutputKw(),
                        mod.workforceProfessionId(), mod.requiredWorkers(),
                        mod.isOnline() ? "ONLINE" : "OFFLINE",
                        isShipyard ? " ★ [SHIPYARD FACILITY]" : ""));

                if (isShipyard) {
                    mText.setFill(mod.isOnline() ? Color.GOLD : Color.SALMON);
                    mText.setFont(Font.font("Verdana", FontWeight.BOLD, 10));
                } else {
                    mText.setFill(mod.isOnline() ? Color.LIGHTGRAY : Color.SALMON);
                    mText.setFont(Font.font("Verdana", 10));
                }
                moduleList.getChildren().add(mText);
            }
            sBox.getChildren().add(moduleList);
        }

        return sBox;
    }

    private VBox createElevatorCard(SpaceElevator elevator) {
        VBox card = new VBox(4);
        card.setPadding(new Insets(8));
        card.setStyle("-fx-background-color: rgba(15, 30, 55, 0.7); -fx-background-radius: 6; " +
                "-fx-border-color: rgba(241, 196, 15, 0.4); -fx-border-width: 1; -fx-border-radius: 6;");

        Text eInfo = new Text(String.format("• Space elevator [%s] Host planet: %s | Owner: %s | Throughput capacity: %,.0f kg/turn | Launch discount: %.0f%% | Integrity: %.1f%% | Status: %s",
                elevator.id(), elevator.planetId().toUpperCase(), elevator.ownerEntityId(),
                elevator.transitThroughputCapacityKgPerTurn(), elevator.surfaceToOrbitCostDiscount() * 100.0,
                elevator.structuralIntegrityPercent(), elevator.isOperational() ? "OPERATIONAL" : "OFFLINE"));
        eInfo.setFill(Color.LIGHTGREEN);
        eInfo.setFont(Font.font("Verdana", 11));

        card.getChildren().add(eInfo);
        return card;
    }

    // ==========================================
    // TAB 4: PRIVATE CORPORATIONS REGISTRY
    // ==========================================

    private VBox buildCorporationsTabContent() {
        VBox container = new VBox(12);
        container.setPadding(new Insets(4));
        VBox.setVgrow(container, Priority.ALWAYS);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.setPadding(new Insets(6));
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        VBox content = new VBox(14);
        scrollPane.setContent(content);

        Empire playerEmpire = getPlayerEmpire();
        String empireDisplayName = playerEmpire != null ? playerEmpire.name() : playerEmpireId;

        // Header
        HBox headerBox = new HBox(10);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        Text headerText = new Text("Private corporations registry");
        headerText.setFill(Color.GOLD);
        headerText.setFont(Font.font("Verdana", FontWeight.BOLD, 16));

        Label empBadge = new Label("Registered imperial enterprises: " + empireDisplayName);
        empBadge.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white; " +
                "-fx-padding: 3 8 3 8; -fx-background-radius: 4; -fx-font-size: 11;");
        headerBox.getChildren().addAll(headerText, empBadge);
        content.getChildren().add(headerBox);

        if (corporations.isEmpty()) {
            try {
                corporations.addAll(DataModelLoader.loadCorporations());
            } catch (IOException e) {
                logger.error("Failed to load corporate registry", e);
            }
        }

        // Filter corporations for player empire
        List<Corporation> playerCorporations = corporations.stream()
                .filter(c -> c.empireId().equalsIgnoreCase(playerEmpireId))
                .toList();

        if (playerCorporations.isEmpty()) {
            VBox emptyBox = new VBox(8);
            emptyBox.setPadding(new Insets(14));
            emptyBox.setStyle("-fx-background-color: rgba(25, 40, 70, 0.7); -fx-background-radius: 8;");
            Text emptyText = new Text("No private corporations currently registered under the " + empireDisplayName + ".");
            emptyText.setFill(Color.LIGHTGRAY);
            emptyText.setFont(Font.font("Verdana", 13));
            emptyBox.getChildren().add(emptyText);
            content.getChildren().add(emptyBox);
        } else {
            for (Corporation corp : playerCorporations) {
                content.getChildren().add(createCorporateCard(corp));
            }
        }

        container.getChildren().add(scrollPane);
        return container;
    }

    private VBox createCorporateCard(Corporation corp) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(14));
        box.setStyle("-fx-background-color: rgba(30, 50, 90, 0.7); -fx-background-radius: 8; " +
                "-fx-border-color: #3498db; -fx-border-width: 1.5; -fx-border-radius: 8;");

        HBox topRow = new HBox(12);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Text nameText = new Text(corp.name());
        nameText.setFill(Color.LIGHTBLUE);
        nameText.setFont(Font.font("Verdana", FontWeight.BOLD, 16));

        Label orientationBadge = new Label(corp.marketOrientation());
        orientationBadge.setStyle("-fx-background-color: #2c3e50; -fx-text-fill: #00cec9; " +
                "-fx-font-weight: bold; -fx-padding: 3 8 3 8; -fx-background-radius: 4; -fx-font-size: 11;");

        topRow.getChildren().addAll(nameText, orientationBadge);

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(6);

        grid.add(createMetricItem("Headquarters", corp.headquartersEntityId().toUpperCase(), Color.WHITE), 0, 0);
        grid.add(createMetricItem("Liquid reserves", String.format("%,.0f credits", corp.liquidCapitalReserves()), Color.GOLD), 1, 0);
        grid.add(createMetricItem("Owned facilities", String.valueOf(corp.ownedFacilityIds().size()), Color.LIGHTCYAN), 0, 1);
        grid.add(createMetricItem("Commercial ships", String.valueOf(corp.ownedShipIds().size()), Color.LIGHTGREEN), 1, 1);

        box.getChildren().addAll(topRow, grid);

        if (!corp.ownedFacilityIds().isEmpty()) {
            Text facText = new Text("  • Industrial facilities: " + String.join(", ", corp.ownedFacilityIds()));
            facText.setFill(Color.GAINSBORO);
            facText.setFont(Font.font("Verdana", 11));
            box.getChildren().add(facText);
        }

        if (!corp.ownedShipIds().isEmpty()) {
            Text shipText = new Text("  • Fleet ships: " + String.join(", ", corp.ownedShipIds()));
            shipText.setFill(Color.GAINSBORO);
            shipText.setFont(Font.font("Verdana", 11));
            box.getChildren().add(shipText);
        }

        if (!corp.claimedVeinIds().isEmpty()) {
            Text veinText = new Text("  • Claimed resource veins: " + String.join(", ", corp.claimedVeinIds()));
            veinText.setFill(Color.GAINSBORO);
            veinText.setFont(Font.font("Verdana", 11));
            box.getChildren().add(veinText);
        }

        return box;
    }

    private VBox createMetricItem(String label, String value, Color valueColor) {
        VBox box = new VBox(2);
        Label lbl = new Label(label);
        lbl.setTextFill(Color.LIGHTGRAY);
        lbl.setFont(Font.font("Verdana", 10));

        Label val = new Label(value);
        val.setTextFill(valueColor);
        val.setFont(Font.font("Verdana", FontWeight.BOLD, 12));

        box.getChildren().addAll(lbl, val);
        return box;
    }

    public Empire getPlayerEmpire() {
        return empires.stream()
                .filter(e -> e.id().equalsIgnoreCase(playerEmpireId))
                .findFirst()
                .orElse(empires.isEmpty() ? null : empires.get(0));
    }

    public List<OrbitalStation> getOrbitalStationsForPlayerEmpire() {
        return orbitalStations.stream()
                .filter(s -> s.ownerEntityId().equalsIgnoreCase(playerEmpireId))
                .toList();
    }

    public List<SpaceElevator> getSpaceElevatorsForPlayerEmpire() {
        return spaceElevators.stream()
                .filter(e -> e.ownerEntityId().equalsIgnoreCase(playerEmpireId))
                .toList();
    }

    public List<Corporation> getCorporationsForPlayerEmpire() {
        if (corporations.isEmpty() && (menubar == null || menubar.getMainApp() == null || menubar.getMainApp().getEngine() == null)) {
            try {
                corporations.addAll(DataModelLoader.loadCorporations());
            } catch (IOException e) {
                logger.error("Failed to load corporate registry", e);
            }
        }
        return corporations.stream()
                .filter(c -> c.empireId().equalsIgnoreCase(playerEmpireId))
                .toList();
    }

    public List<Megastructure> getMegastructuresForPlayerEmpire() {
        return megastructures.stream()
                .filter(m -> m.ownerEmpireId().equalsIgnoreCase(playerEmpireId))
                .toList();
    }

    // ==========================================
    // TAB 6: MEGASTRUCTURES
    // ==========================================

    private VBox buildMegastructuresTabContent() {
        VBox container = new VBox(12);
        container.setPadding(new Insets(4));
        VBox.setVgrow(container, Priority.ALWAYS);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.setPadding(new Insets(6));
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        VBox content = new VBox(14);
        scrollPane.setContent(content);

        List<Megastructure> playerMegas = getMegastructuresForPlayerEmpire();
        long operationalCount = playerMegas.stream().filter(Megastructure::isOperational).count();
        long underConstructionCount = playerMegas.size() - operationalCount;
        double totalPowerKw = playerMegas.stream().mapToDouble(Megastructure::energyYieldKw).sum();
        long totalHabitation = playerMegas.stream().mapToLong(Megastructure::habitableCapacity).sum();

        // 1. Overview and summary metrics card
        VBox summaryCard = new VBox(10);
        summaryCard.setPadding(new Insets(14));
        summaryCard.setStyle("-fx-background-color: rgba(30, 50, 90, 0.7); -fx-background-radius: 8; " +
                "-fx-border-color: #f1c40f; -fx-border-width: 1.5; -fx-border-radius: 8;");

        Text title = new Text("Imperial megastructures and stellar engineering");
        title.setFill(Color.GOLD);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 17));

        Text desc = new Text("Grand mega-engineering undertakings harnessing entire stellar outputs, orbital super-habitats and interstellar transit conduits.");
        desc.setFill(Color.LIGHTCYAN);
        desc.setFont(Font.font("Verdana", 11));

        GridPane statsGrid = new GridPane();
        statsGrid.setHgap(20);
        statsGrid.setVgap(8);

        statsGrid.add(createAttrLabel("Total megastructures:", String.valueOf(playerMegas.size())), 0, 0);
        statsGrid.add(createAttrLabel("Operational facilities:", String.valueOf(operationalCount)), 1, 0);
        statsGrid.add(createAttrLabel("Under construction:", String.valueOf(underConstructionCount)), 2, 0);
        statsGrid.add(createAttrLabel("Total energy output:", String.format("%,.0f kW", totalPowerKw)), 0, 1);
        statsGrid.add(createAttrLabel("Habitation capacity:", String.format("%,d citizens", totalHabitation)), 1, 1);

        summaryCard.getChildren().addAll(title, desc, statsGrid);
        content.getChildren().add(summaryCard);

        // 2. Commissioning workbench (Gated by 'stellar_megastructures' technology)
        VBox workbench = new VBox(10);
        workbench.setPadding(new Insets(12));
        workbench.setStyle("-fx-background-color: rgba(25, 45, 75, 0.75); -fx-background-radius: 8; " +
                "-fx-border-color: #00cec9; -fx-border-width: 1; -fx-border-radius: 8;");

        Text wbTitle = new Text("Commission grand stellar megastructure");
        wbTitle.setFill(Color.AQUA);
        wbTitle.setFont(Font.font("Verdana", FontWeight.BOLD, 14));

        boolean isTechUnlocked = isTechnologyUnlocked("stellar_megastructures");

        GridPane wbGrid = new GridPane();
        wbGrid.setHgap(12);
        wbGrid.setVgap(8);

        Label sysLbl = new Label("Target solar system:");
        sysLbl.setTextFill(Color.LIGHTCYAN);
        ComboBox<String> sysCombo = new ComboBox<>();
        for (SolarSystem ss : solarSystems) {
            sysCombo.getItems().add(ss.name());
        }
        if (sysCombo.getItems().isEmpty()) {
            sysCombo.getItems().addAll("Sol", "Alpha Centauri", "Sirius", "Vega");
        }
        sysCombo.setValue(sysCombo.getItems().get(0));

        Label typeLbl = new Label("Megastructure type:");
        typeLbl.setTextFill(Color.LIGHTCYAN);
        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll(
                Megastructure.TYPE_DYSON_SWARM,
                Megastructure.TYPE_DYSON_SPHERE,
                Megastructure.TYPE_STAR_LIFTER,
                Megastructure.TYPE_RINGWORLD,
                Megastructure.TYPE_ORBITAL_HABITAT,
                Megastructure.TYPE_HYPERLANE_GATEWAY
        );
        typeCombo.setValue(Megastructure.TYPE_DYSON_SWARM);

        Label targetLbl = new Label("Celestial target:");
        targetLbl.setTextFill(Color.LIGHTCYAN);
        ComboBox<String> targetCombo = new ComboBox<>();
        targetCombo.getItems().addAll("Star / Primary Sun", "Planet Orbit", "Deep Space Waypoint");
        targetCombo.setValue("Star / Primary Sun");

        Label nameLbl = new Label("Custom name:");
        nameLbl.setTextFill(Color.LIGHTCYAN);
        TextField nameField = new TextField();
        nameField.setPromptText("Enter custom name (optional)");
        nameField.setPrefWidth(180);

        Button buildBtn = new Button("Authorize megastructure construction (50,000 ₵)");
        buildBtn.setCursor(javafx.scene.Cursor.HAND);
        if (isTechUnlocked) {
            buildBtn.setStyle("-fx-background-color: #00cec9; -fx-text-fill: black; -fx-font-weight: bold;");
            buildBtn.setOnAction(e -> {
                if (humanController != null) {
                    String chosenSys = sysCombo.getValue().toLowerCase().replace(" ", "_");
                    String customName = nameField.getText() != null && !nameField.getText().isBlank()
                            ? nameField.getText()
                            : sysCombo.getValue() + " " + formatTitle(typeCombo.getValue());
                    humanController.stageCommand(new BuildMegastructureCommand(
                            playerEmpireId, typeCombo.getValue(), chosenSys, chosenSys + "_star", customName
                    ));
                    feedbackLabel.setText("Dispatched megastructure commissioning order: " + customName);
                    feedbackLabel.setTextFill(Color.LIGHTGREEN);
                }
            });
        } else {
            buildBtn.setStyle("-fx-background-color: #555555; -fx-text-fill: #aaaaaa; -fx-font-weight: bold;");
            buildBtn.setDisable(true);
        }

        wbGrid.add(sysLbl, 0, 0);
        wbGrid.add(sysCombo, 1, 0);
        wbGrid.add(typeLbl, 2, 0);
        wbGrid.add(typeCombo, 3, 0);

        wbGrid.add(targetLbl, 0, 1);
        wbGrid.add(targetCombo, 1, 1);
        wbGrid.add(nameLbl, 2, 1);
        wbGrid.add(nameField, 3, 1);

        VBox wbBox = new VBox(8);
        wbBox.getChildren().addAll(wbTitle, wbGrid);

        if (!isTechUnlocked) {
            Label lockedNotice = new Label("⚠ Technology locked: Building megastructures requires researching 'Stellar megastructures' (Complexity 9, unavailable at game start).");
            lockedNotice.setTextFill(Color.rgb(231, 76, 60));
            lockedNotice.setFont(Font.font("Verdana", FontWeight.BOLD, 11));
            wbBox.getChildren().add(lockedNotice);
        }

        wbBox.getChildren().add(buildBtn);
        workbench.getChildren().add(wbBox);
        content.getChildren().add(workbench);

        // 3. Active megastructures list
        VBox listSection = new VBox(8);
        listSection.setPadding(new Insets(10));
        listSection.setStyle("-fx-background-color: rgba(20, 35, 60, 0.7); -fx-background-radius: 8; " +
                "-fx-border-color: #f1c40f; -fx-border-width: 1; -fx-border-radius: 8;");

        Text listHeader = new Text("Active imperial megastructure installations (" + playerMegas.size() + ")");
        listHeader.setFill(Color.GOLD);
        listHeader.setFont(Font.font("Verdana", FontWeight.BOLD, 15));
        listSection.getChildren().add(listHeader);

        if (playerMegas.isEmpty()) {
            Text emptyText = new Text("No sovereign megastructures commissioned. Research Stellar megastructures to begin construction.");
            emptyText.setFill(Color.LIGHTGRAY);
            listSection.getChildren().add(emptyText);
        } else {
            for (Megastructure mega : playerMegas) {
                VBox card = new VBox(6);
                card.setPadding(new Insets(10));
                card.setStyle("-fx-background-color: rgba(15, 25, 45, 0.65); -fx-background-radius: 6; " +
                        "-fx-border-color: " + (mega.isOperational() ? "#2ecc71" : "#e67e22") + "; -fx-border-width: 1; -fx-border-radius: 6;");

                HBox cardHeader = new HBox(10);
                cardHeader.setAlignment(Pos.CENTER_LEFT);

                Text mName = new Text(mega.name() + " (" + mega.id() + ")");
                mName.setFill(Color.WHITE);
                mName.setFont(Font.font("Verdana", FontWeight.BOLD, 13));

                Label typeBadge = new Label(mega.type());
                typeBadge.setStyle("-fx-background-color: #2c3e50; -fx-text-fill: #f1c40f; -fx-font-weight: bold; -fx-padding: 2 6 2 6; -fx-background-radius: 4; -fx-font-size: 10;");

                Label statusBadge = new Label(mega.isOperational() ? "Operational" : "Under construction");
                statusBadge.setStyle("-fx-background-color: " + (mega.isOperational() ? "#27ae60" : "#d35400") + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 2 6 2 6; -fx-background-radius: 4; -fx-font-size: 10;");

                cardHeader.getChildren().addAll(mName, typeBadge, statusBadge);

                Text locText = new Text(String.format("  System: %s | Target: %s | Stage: %d / %d",
                        mega.systemId().toUpperCase(), mega.targetCelestialId(), mega.currentStage(), mega.totalStages()));
                locText.setFill(Color.LIGHTCYAN);
                locText.setFont(Font.font("Verdana", 11));

                if (!mega.isOperational()) {
                    ProgressBar bar = new ProgressBar(mega.currentStageProgress() / Math.max(1.0, mega.requiredStageProgress()));
                    bar.setPrefWidth(350);
                    Text progText = new Text(String.format("Stage progress: %.0f / %.0f turns (%.1f%%)",
                            mega.currentStageProgress(), mega.requiredStageProgress(),
                            (mega.currentStageProgress() / Math.max(1.0, mega.requiredStageProgress())) * 100.0));
                    progText.setFill(Color.LIGHTGRAY);
                    progText.setFont(Font.font("Verdana", 10));
                    HBox barBox = new HBox(8, bar, progText);
                    barBox.setAlignment(Pos.CENTER_LEFT);
                    card.getChildren().add(barBox);
                }

                Text specText = new Text(String.format("  Power output: %,.0f kW | Habitable capacity: %,d citizens%s",
                        mega.energyYieldKw(), mega.habitableCapacity(),
                        mega.materialHarvestYieldKgPerTurn().isEmpty() ? "" : " | Siphon yields: " + mega.materialHarvestYieldKgPerTurn()));
                specText.setFill(Color.LIGHTGREEN);
                specText.setFont(Font.font("Verdana", 11));

                card.getChildren().addAll(cardHeader, locText, specText);
                listSection.getChildren().add(card);
            }
        }

        content.getChildren().add(listSection);
        container.getChildren().add(scrollPane);
        return container;
    }

    // ==========================================
    // DATA QUERY & TECHNOLOGY VALIDATION
    // ==========================================

    public List<PlanetaryBodyEntry> getAllPlanetaryBodies() {
        List<PlanetaryBodyEntry> list = new ArrayList<>();
        for (SolarSystem system : solarSystems) {
            if (system.planets() == null) continue;
            for (Planet p : system.planets()) {
                PlanetaryBodyEntry planetEntry = PlanetaryBodyEntry.fromPlanet(p, system);
                if (planetEntry != null) {
                    list.add(planetEntry);
                }
                if (p.moons() != null) {
                    for (Moon m : p.moons()) {
                        PlanetaryBodyEntry moonEntry = PlanetaryBodyEntry.fromMoon(m, p, system);
                        if (moonEntry != null) {
                            list.add(moonEntry);
                        }
                    }
                }
            }
        }
        return list;
    }

    public List<PlanetaryBodyEntry> getFilteredAndSortedPlanetaryBodies() {
        List<PlanetaryBodyEntry> all = getAllPlanetaryBodies();

        // 1. Filter
        List<PlanetaryBodyEntry> filtered = all.stream().filter(entry -> switch (currentFilter) {
            case COLONIZED -> entry.isColonized();
            case UNCOLONIZED -> !entry.isColonized();
            case COLONIZABLE -> entry.isColonizable();
            case ALL_BODIES -> true;
            case ONLY_PLANETS -> !entry.isMoon();
            case ONLY_MOONS -> entry.isMoon();
        }).toList();

        // 2. Sort
        List<PlanetaryBodyEntry> sorted = new ArrayList<>(filtered);
        Comparator<PlanetaryBodyEntry> comparator = switch (currentSort) {
            case NAME_AZ -> Comparator.comparing(PlanetaryBodyEntry::name, String.CASE_INSENSITIVE_ORDER);
            case SYSTEM_NAME -> Comparator.comparing(PlanetaryBodyEntry::systemName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(PlanetaryBodyEntry::name, String.CASE_INSENSITIVE_ORDER);
            case POPULATION_DESC -> Comparator.comparingLong(PlanetaryBodyEntry::totalPopulation).reversed()
                    .thenComparing(PlanetaryBodyEntry::name, String.CASE_INSENSITIVE_ORDER);
            case SIZE_DESC -> Comparator.comparingDouble(PlanetaryBodyEntry::diameter).reversed()
                    .thenComparing(PlanetaryBodyEntry::name, String.CASE_INSENSITIVE_ORDER);
            case GRAVITY_DESC -> Comparator.comparingDouble(PlanetaryBodyEntry::gravity).reversed()
                    .thenComparing(PlanetaryBodyEntry::name, String.CASE_INSENSITIVE_ORDER);
            case RESOURCES_DESC -> Comparator.comparingInt(PlanetaryBodyEntry::resourceCount).reversed()
                    .thenComparing(PlanetaryBodyEntry::name, String.CASE_INSENSITIVE_ORDER);
        };

        sorted.sort(comparator);
        return sorted;
    }

    public boolean isTechnologyUnlocked(String techId) {
        if (techId == null || techId.isEmpty()) return true;

        // Check player empire's unlockedTechIds
        Empire playerEmpire = empires.stream()
                .filter(e -> e.id().equalsIgnoreCase(playerEmpireId))
                .findFirst()
                .orElse(null);

        if (playerEmpire != null && playerEmpire.unlockedTechIds() != null && !playerEmpire.unlockedTechIds().isEmpty()) {
            if (playerEmpire.unlockedTechIds().contains(techId)) {
                return true;
            }
        }

        // Check completed research projects
        for (ResearchProject rp : researchProjects) {
            if (rp.empireId().equalsIgnoreCase(playerEmpireId) &&
                    rp.targetTechOrAppId().equalsIgnoreCase(techId) &&
                    rp.isComplete()) {
                return true;
            }
        }

        // Default unlocked starter technologies for default empires if unlocked list not populated
        if (playerEmpire == null || playerEmpire.unlockedTechIds().isEmpty()) {
            Set<String> defaultStarters = Set.of("electricity", "industrial_production", "rocketry", "geological_prospecting");
            return defaultStarters.contains(techId);
        }

        return false;
    }

    private static String formatTitle(String id) {
        if (id == null || id.isEmpty()) return "";
        String[] parts = id.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            if (i > 0) sb.append(" ");
            sb.append(Character.toUpperCase(parts[i].charAt(0)))
                    .append(parts[i].substring(1).toLowerCase());
        }
        return sb.toString();
    }
}
