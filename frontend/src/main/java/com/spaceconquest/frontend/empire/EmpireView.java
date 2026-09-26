package com.spaceconquest.frontend.empire;

import com.spaceconquest.frontend.Menubar;
import com.spaceconquest.frontend.PlanetaryBodyEntry;
import com.spaceconquest.frontend.components.SurfaceBiomeGridView;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.PlaceFacilityOnTileCommand;
import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.Moon;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.SystemGovernor;
import com.spaceconquest.engine.economy.PlanetaryBalanceSheet;
import com.spaceconquest.engine.economy.ImperialBalanceSheet;
import com.spaceconquest.engine.economy.SystemEconomy;
import com.spaceconquest.engine.economy.CorporateTaxAccount;
import com.spaceconquest.engine.industry.FacilityExpansionProject;
import com.spaceconquest.engine.industry.GeologicalDeposit;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.industry.IndustryAccount;
import com.spaceconquest.engine.industry.IndustryRecipeCatalog;
import com.spaceconquest.engine.industry.PowerBillingProcessor;
import com.spaceconquest.engine.industry.PowerGridState;
import com.spaceconquest.engine.industry.PowerPlantCatalog;
import com.spaceconquest.engine.industry.refinement.RefinementProcessor;
import com.spaceconquest.engine.industry.refinement.RefinementRecipe;
import com.spaceconquest.engine.macrostructure.ConstructionDeploymentProject;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.macrostructure.SpaceElevator;
import com.spaceconquest.engine.megastructure.Megastructure;
import com.spaceconquest.engine.technology.ResearchProject;
import com.spaceconquest.engine.terraforming.GeoengineeringProject;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
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
    private final List<GeologicalDeposit> deposits = new ArrayList<>();
    private final List<IndustrialFacility> facilities = new ArrayList<>();
    private final List<IndustryAccount> industryAccounts = new ArrayList<>();
    private final List<CorporateTaxAccount> corporateTaxAccounts = new ArrayList<>();
    private final List<OrbitalStation> orbitalStations = new ArrayList<>();
    private final List<SpaceElevator> spaceElevators = new ArrayList<>();
    private final List<Corporation> corporations = new ArrayList<>();
    private final List<ResearchProject> researchProjects = new ArrayList<>();
    private final List<Megastructure> megastructures = new ArrayList<>();
    private final List<SystemEconomy> systemEconomies = new ArrayList<>();
    private final List<PlanetaryBalanceSheet> planetaryBalanceSheets = new ArrayList<>();
    private final List<ImperialBalanceSheet> imperialBalanceSheets = new ArrayList<>();
    private final List<PowerGridState> powerGrids = new ArrayList<>();
    private final List<FacilityExpansionProject> expansionProjects = new ArrayList<>();
    private final List<GeoengineeringProject> terraformingProjects = new ArrayList<>();
    private final List<ConstructionDeploymentProject> constructionProjects = new ArrayList<>();

    private final EconomyTab economyTab = new EconomyTab(this);
    private final CabinetTab cabinetTab = new CabinetTab(this);
    private final PlanetsTab planetsTab = new PlanetsTab(this);
    private final StationsTab stationsTab = new StationsTab(this);
    private final CorporationsTab corporationsTab = new CorporationsTab(this);
    private final MegastructuresTab megastructuresTab = new MegastructuresTab(this);

    public EmpireView(Menubar menubar) {
        this.menubar = menubar;
        build();
    }

    public List<SystemGovernor> getSystemGovernors() { return systemGovernors; }
    public List<GeologicalDeposit> getDeposits() { return deposits; }
    public List<IndustrialFacility> getFacilities() { return facilities; }
    public List<OrbitalStation> getOrbitalStations() { return orbitalStations; }
    public List<SpaceElevator> getSpaceElevators() { return spaceElevators; }
    public List<Corporation> getCorporations() { return corporations; }
    public List<ResearchProject> getResearchProjects() { return researchProjects; }
    public List<Megastructure> getMegastructures() { return megastructures; }
    public List<SolarSystem> getSystems() { return solarSystems; }
    public List<Empire> getEmpires() { return empires; }
    public List<PowerGridState> getPowerGrids() { return powerGrids; }
    public List<FacilityExpansionProject> getExpansionProjects() { return expansionProjects; }
    public List<GeoengineeringProject> getTerraformingProjects() { return terraformingProjects; }
    public List<ConstructionDeploymentProject> getConstructionProjects() { return constructionProjects; }
    public List<SystemEconomy> getSystemEconomies() { return systemEconomies; }
    public List<SystemEconomy> getSystemEconomiesList() { return systemEconomies; }
    public List<PlanetaryBalanceSheet> getPlanetaryBalanceSheets() { return planetaryBalanceSheets; }
    public List<ImperialBalanceSheet> getImperialBalanceSheets() { return imperialBalanceSheets; }
    public List<CorporateTaxAccount> getCorporateTaxAccounts() { return corporateTaxAccounts; }

    public String getPlayerEmpireId() { return playerEmpireId; }
    public Menubar getMenubar() { return menubar; }
    public HumanController getHumanController() { return humanController; }

    public void setHumanController(HumanController controller) {
        this.humanController = controller;
    }

    public void setPlayerEmpireId(String empireId) {
        if (empireId != null && !empireId.isEmpty()) {
            this.playerEmpireId = empireId;
        }
    }

    public Tab getCurrentTab() { return currentTab; }
    public EconomySubView getEconomySubView() { return currentEconomySubView; }
    public void setEconomySubView(EconomySubView subView) { this.currentEconomySubView = subView; }
    public String getSelectedEconomySystemId() { return selectedEconomySystemId; }
    public void setSelectedEconomySystemId(String systemId) {
        setSelectedEconomySystemId(systemId, true);
    }

    public void setSelectedEconomySystemId(String systemId, boolean shouldRender) {
        if (systemId != null && (this.selectedEconomySystemId == null || !this.selectedEconomySystemId.equals(systemId))) {
            this.selectedEconomySystemId = systemId;
            if (shouldRender) {
                renderCurrentTab();
            }
        }
    }
    public FilterCategory getCurrentFilter() { return currentFilter; }
    public void setCurrentFilter(FilterCategory filter) {
        if (filter != null && this.currentFilter != filter) {
            this.currentFilter = filter;
            this.selectedBody = null;
            renderCurrentTab();
        }
    }
    public SortOption getCurrentSort() { return currentSort; }
    public void setCurrentSort(SortOption sort) {
        if (sort != null && this.currentSort != sort) {
            this.currentSort = sort;
            this.selectedBody = null;
            renderCurrentTab();
        }
    }
    public PlanetaryBodyEntry getSelectedBody() { return selectedBody; }
    public void setSelectedBody(PlanetaryBodyEntry entry) {
        setSelectedBody(entry, true);
    }

    public void setSelectedBody(PlanetaryBodyEntry entry, boolean shouldRender) {
        if (entry != null && (this.selectedBody == null || !this.selectedBody.id().equals(entry.id()))) {
            this.selectedBody = entry;
            if (shouldRender) {
                renderCurrentTab();
            }
        }
    }

    public VBox createBodyCard(PlanetaryBodyEntry entry) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(12));
        card.setStyle("-fx-background-color: rgba(30, 50, 90, 0.7); -fx-background-radius: 8; -fx-border-color: #3498db; -fx-border-width: 1; -fx-border-radius: 8;");
        Label name = new Label(entry.name() + " (" + (entry.isMoon() ? "Moon" : entry.getBodyType()) + ")");
        name.setTextFill(Color.GOLD);
        name.setFont(Font.font("Verdana", FontWeight.BOLD, 16));
        name.setWrapText(true);
        Label details = new Label("System: " + entry.systemName() + " | Population: " + String.format("%,d", entry.totalPopulation()));
        details.setTextFill(Color.WHITE);
        details.setWrapText(true);
        card.getChildren().addAll(name, details);
        return card;
    }

    public VBox createPopulationDemographicsSection(PlanetaryBodyEntry body) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-background-color: rgba(20, 35, 65, 0.6); -fx-background-radius: 6;");
        Text title = new Text("Population & demographics");
        title.setFill(Color.AQUA);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 12));
        box.getChildren().add(title);

        if (body.isColonized()) {
            box.getChildren().add(createDetailRow("Total population:", String.format("%,d", body.totalPopulation())));
        } else {
            Label empty = new Label("No colonial presence detected.");
            empty.setTextFill(Color.LIGHTGRAY);
            empty.setFont(Font.font("Verdana", 11));
            box.getChildren().add(empty);
        }
        return box;
    }

    public VBox createIndustrySection(PlanetaryBodyEntry body) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-background-color: rgba(20, 35, 65, 0.6); -fx-background-radius: 6;");
        List<IndustrialFacility> localFacilities = facilities.stream()
                .filter(facility -> body.id().equals(facility.planetId()))
                .toList();
        Text title = new Text("Industries and facilities (" + localFacilities.size() + ")");
        title.setFill(Color.AQUA);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 12));
        box.getChildren().add(title);

        if (localFacilities.isEmpty()) {
            Label empty = new Label("No industrial facilities on this body.");
            empty.setTextFill(Color.LIGHTGRAY);
            empty.setFont(Font.font("Verdana", 11));
            box.getChildren().add(empty);
            return box;
        }

        Label note = new Label("Production, sales and profit/loss show the last processed day. Unsold goods remain with their facility.");
        note.setTextFill(Color.LIGHTGRAY);
        note.setWrapText(true);
        box.getChildren().add(note);
        localFacilities.forEach(facility -> box.getChildren().add(createIndustryCard(facility)));
        return box;
    }

    private VBox createIndustryCard(IndustrialFacility facility) {
        VBox card = new VBox(5);
        card.setPadding(new Insets(8));
        card.setStyle("-fx-background-color: rgba(30, 50, 90, 0.7); -fx-background-radius: 5;");

        Text name = new Text(readableIndustryName(facility.applicationId()) + " [" + facility.id() + "]");
        name.setFill(Color.GOLD);
        name.setFont(Font.font("Verdana", FontWeight.BOLD, 12));
        card.getChildren().add(name);

        String size = String.format("Tier %d; %.2fx effective throughput%s", facility.tier(),
                facility.getEffectiveThroughputMultiplier(),
                facility.isUndergoingExpansion() ? " during expansion" : "");
        card.getChildren().add(createIndustryDetailRow("Size:", size));
        card.getChildren().add(createIndustryDetailRow("Workers:", String.format("%,d assigned %s", facility.allocatedWorkers(),
                readableIndustryName(facility.workerProfessionId()))));
        card.getChildren().add(createIndustryDetailRow("Owner:", readableIndustryName(facility.ownerEntityId())));
        card.getChildren().add(createIndustryDetailRow("Configured products:", configuredProducts(facility.applicationId())));
        IndustryRecipeCatalog.Recipe recipe = IndustryRecipeCatalog.find(facility.applicationId());
        PowerPlantCatalog.Plant plant = PowerPlantCatalog.find(facility.applicationId());
        card.getChildren().add(createIndustryDetailRow("Required technology:", recipe == null
                ? (plant == null ? "No production recipe configured" : plant.requiredTechnology())
                : String.join(", ", recipe.requiredTechnologies())));
        if (recipe != null) {
            String ownerEmpireId = facility.ownerEntityId();
            if (IndustrialFacility.PRIVATE_CORPORATE.equals(facility.ownershipType())) {
                ownerEmpireId = corporations.stream().filter(corp -> corp.id().equals(facility.ownerEntityId()))
                        .map(Corporation::empireId).findFirst().orElse(ownerEmpireId);
            }
            String empireId = ownerEmpireId;
            empires.stream().filter(empire -> empire.id().equals(empireId)).findFirst().ifPresent(empire ->
                    card.getChildren().add(createIndustryDetailRow("Technology output:",
                            String.format("%.0f%% (including researched improvements)",
                                    recipe.technologyMultiplier(empire) * 100.0))));
        }
        IndustryAccount account = industryAccounts.stream()
                .filter(item -> item.facilityId().equals(facility.id())).findFirst().orElse(null);
        if (account != null) {
            if (PowerPlantCatalog.find(facility.applicationId()) != null) {
                card.getChildren().add(createIndustryDetailRow("Generated:",
                        String.format("%,.0f kWh", account.generatedKwh())));
            }
            card.getChildren().add(createIndustryDetailRow("Produced:", formatIndustryQuantities(account.producedKg())));
            card.getChildren().add(createIndustryDetailRow("Sold:", plant == null
                    ? formatIndustryQuantities(account.soldKg())
                    : String.format("%,.0f kWh", account.salesCredits() / PowerBillingProcessor.PRICE_PER_KWH)));
            card.getChildren().add(createIndustryDetailRow("Unsold stock:", formatIndustryQuantities(account.unsoldStockKg())));
            card.getChildren().add(createIndustryDetailRow("Input costs:", String.format("%,.2f credits", account.inputCostsCredits())));
            card.getChildren().add(createIndustryDetailRow("Electricity:", String.format("%,.2f credits", account.powerCostsCredits())));
            card.getChildren().add(createIndustryDetailRow("Wages:", String.format("%,.2f credits", account.wageCostsCredits())));
            card.getChildren().add(createIndustryDetailRow("Sales:", String.format("%,.2f credits", account.salesCredits())));
            card.getChildren().add(createIndustryDetailRow("Pretax profit/loss:", String.format("%,.2f credits", account.realizedResultCredits())));
        } else {
            card.getChildren().add(createIndustryDetailRow("Sales:", "Unavailable before the first simulation day"));
            card.getChildren().add(createIndustryDetailRow("Pretax profit/loss:", "Unavailable before the first simulation day"));
        }
        return card;
    }

    private String formatIndustryQuantities(Map<String, Double> quantities) {
        if (quantities.isEmpty()) return "None";
        return quantities.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> String.format("%s (%,.1f kg)", readableIndustryName(entry.getKey()), entry.getValue()))
                .reduce((left, right) -> left + ", " + right).orElse("None");
    }

    private HBox createIndustryDetailRow(String heading, String value) {
        HBox row = new HBox(8);
        Label label = new Label(heading);
        label.setTextFill(Color.LIGHTBLUE);
        label.setMinWidth(130);
        Label detail = new Label(value);
        detail.setTextFill(Color.WHITE);
        detail.setWrapText(true);
        HBox.setHgrow(detail, Priority.ALWAYS);
        row.getChildren().addAll(label, detail);
        return row;
    }

    private String configuredProducts(String applicationId) {
        if (PowerPlantCatalog.find(applicationId) != null) return "Electricity (kWh)";
        return RefinementProcessor.STANDARD_RECIPES.stream()
                .filter(recipe -> recipe.id().equals(applicationId))
                .findFirst()
                .map(RefinementRecipe::outputMaterialsKg)
                .filter(outputs -> !outputs.isEmpty())
                .map(outputs -> outputs.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(output -> String.format("%s (%,.0f kg/recipe)",
                                readableIndustryName(output.getKey()), output.getValue()))
                        .reduce((first, next) -> first + ", " + next)
                        .orElse("No product defined"))
                .orElseGet(() -> {
                    IndustryRecipeCatalog.Recipe recipe = IndustryRecipeCatalog.find(applicationId);
                    return recipe == null ? "No product defined for this application"
                            : formatIndustryQuantities(recipe.outputsKg());
                });
    }

    private String readableIndustryName(String id) {
        if (id == null || id.isBlank()) return "Unknown";
        String words = id.replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    public VBox createSurfaceBiomeSection(PlanetaryBodyEntry body) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-background-color: rgba(20, 35, 65, 0.6); -fx-background-radius: 6;");
        Text title = new Text("Surface biome & environmental telemetry");
        title.setFill(Color.AQUA);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 12));
        box.getChildren().add(title);

        box.getChildren().add(createDetailRow("Body type:", body.getBodyType()));
        box.getChildren().add(createDetailRow("Surface gravity:", String.format("%.2f m/s²", body.gravity())));
        box.getChildren().add(createDetailRow("Diameter:", String.format("%,.0f km", body.diameter())));
        box.getChildren().add(createDetailRow("Atmosphere:", body.getAtmosphere()));
        box.getChildren().add(createDetailRow("Liquid water:", body.hasLiquidWater() ? "Present" : "None"));

        SurfaceBiomeGridView biomeGrid = new SurfaceBiomeGridView(body, humanController, playerEmpireId, (id, tileIdx) -> {
            if (humanController != null) {
                humanController.stageCommand(new PlaceFacilityOnTileCommand(
                        id, tileIdx, "solar_power_array", playerEmpireId, "PUBLIC_STATE", 50, "technician"
                ));
                setFeedback("Commissioned facility on surface tile #" + tileIdx + " of " + body.name(), true);
            }
        });
        biomeGrid.setPrefHeight(400); // Increase height for better visibility in the main view
        box.getChildren().add(biomeGrid);

        return box;
    }

    public VBox createPowerAndDepositsSection(PlanetaryBodyEntry body) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-background-color: rgba(20, 35, 65, 0.6); -fx-background-radius: 6;");
        Text title = new Text("Power grid & geological resources");
        title.setFill(Color.AQUA);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 12));
        box.getChildren().add(title);

        box.getChildren().add(createDetailRow("Geological deposits:", String.valueOf(body.resourceCount())));
        if (!body.getResources().isEmpty()) {
            box.getChildren().add(createDetailRow("Detected resources:", String.join(", ", body.getResources())));
        }
        
        return box;
    }

    public VBox createTechnologyGatedOperationsSection(PlanetaryBodyEntry body) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-background-color: rgba(45, 55, 80, 0.7); -fx-background-radius: 8;");
        Text title = new Text("Technology-gated contextual operations");
        title.setFill(Color.GOLD);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 12));
        box.getChildren().add(title);

        Label info = new Label("Administrative and engineering actions available based on colonial status and technology.");
        info.setTextFill(Color.LIGHTGRAY);
        info.setFont(Font.font("Verdana", 10));
        info.setWrapText(true);
        info.setMaxWidth(Double.MAX_VALUE);
        box.getChildren().add(info);
        
        return box;
    }

    private HBox createDetailRow(String label, String value) {
        HBox row = new HBox(10);
        Label lbl = new Label(label);
        lbl.setTextFill(Color.LIGHTBLUE);
        lbl.setFont(Font.font("Verdana", 11));
        lbl.setPrefWidth(140);

        Label val = new Label(value);
        val.setTextFill(Color.WHITE);
        val.setFont(Font.font("Verdana", FontWeight.BOLD, 11));
        val.setWrapText(true);
        HBox.setHgrow(val, Priority.ALWAYS);

        row.getChildren().addAll(lbl, val);
        return row;
    }

    private void build() {
        root = new VBox(12);
        root.setPadding(new Insets(16));
        root.setStyle("-fx-background-color: rgba(10, 18, 38, 0.97); " +
                "-fx-border-color: #78aaff; -fx-border-width: 2; " +
                "-fx-border-radius: 10; -fx-background-radius: 10;");
        root.setPrefSize(960, 720);

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
        industryAccounts.clear();
        industryAccounts.addAll(gameState.industryAccounts());
        corporateTaxAccounts.clear();
        corporateTaxAccounts.addAll(gameState.corporateTaxAccounts());
        if (gameState.planetaryBalanceSheets() != null) {
            planetaryBalanceSheets.clear();
            planetaryBalanceSheets.addAll(gameState.planetaryBalanceSheets());
        }
        imperialBalanceSheets.clear();
        imperialBalanceSheets.addAll(gameState.imperialBalanceSheets());
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

        // Keep selection if it's still valid
        if (selectedBody != null) {
            String currentId = selectedBody.id();
            this.selectedBody = getAllPlanetaryBodies().stream()
                    .filter(b -> b.id().equals(currentId))
                    .findFirst()
                    .orElse(null);
        }

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

    public void renderCurrentTab() {
        if (tabContentContainer == null) return;
        tabContentContainer.getChildren().clear();

        switch (currentTab) {
            case ECONOMY -> {
                VBox content = economyTab.buildEconomyTabContent();
                if (content != null) tabContentContainer.getChildren().add(content);
            }
            case CABINET -> {
                VBox content = cabinetTab.buildCabinetTabContent();
                if (content != null) tabContentContainer.getChildren().add(content);
            }
            case PLANETS -> {
                HBox content = planetsTab.buildPlanetsTabContent();
                if (content != null) tabContentContainer.getChildren().add(content);
            }
            case STATIONS -> {
                VBox content = stationsTab.buildStationsTabContent();
                if (content != null) tabContentContainer.getChildren().add(content);
            }
            case CORPORATIONS -> {
                VBox content = corporationsTab.buildCorporationsTabContent();
                if (content != null) tabContentContainer.getChildren().add(content);
            }
            case MEGASTRUCTURES -> {
                VBox content = megastructuresTab.buildMegastructuresTabContent();
                if (content != null) tabContentContainer.getChildren().add(content);
            }
        }
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
        if (corporations.isEmpty()) {
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

    public List<PlanetaryBodyEntry> getAllPlanetaryBodies() {
        List<PlanetaryBodyEntry> list = new ArrayList<>();
        Empire playerEmpire = getPlayerEmpire();
        List<String> controlledSystemIds = playerEmpire != null ? playerEmpire.controlledSystemIds() : List.of();

        for (SolarSystem system : solarSystems) {
            boolean isSystemControlled = controlledSystemIds.isEmpty() || controlledSystemIds.contains(system.id());
            if (system.planets() == null) continue;

            for (Planet p : system.planets()) {
                boolean playerHasAssets = false;
                if (facilities != null) {
                    playerHasAssets = facilities.stream()
                            .anyMatch(f -> f.planetId().equals(p.id()) && f.ownerEntityId().equalsIgnoreCase(playerEmpireId));
                }
                if (!playerHasAssets && orbitalStations != null) {
                    playerHasAssets = orbitalStations.stream()
                            .anyMatch(s -> s.planetOrbitId() != null && s.planetOrbitId().equals(p.id()) && s.ownerEntityId().equalsIgnoreCase(playerEmpireId));
                }

                if (isSystemControlled || playerHasAssets) {
                    PlanetaryBodyEntry planetEntry = PlanetaryBodyEntry.fromPlanet(p, system, deposits);
                    if (planetEntry != null) {
                        list.add(planetEntry);
                    }
                    if (p.moons() != null) {
                        for (Moon m : p.moons()) {
                            PlanetaryBodyEntry moonEntry = PlanetaryBodyEntry.fromMoon(m, p, system, deposits);
                            if (moonEntry != null) {
                                list.add(moonEntry);
                            }
                        }
                    }
                }
            }
        }
        return list;
    }

    public List<PlanetaryBodyEntry> getFilteredAndSortedPlanetaryBodies() {
        List<PlanetaryBodyEntry> all = getAllPlanetaryBodies();

        List<PlanetaryBodyEntry> filtered = all.stream().filter(entry -> switch (currentFilter) {
            case COLONIZED -> entry.isColonized();
            case UNCOLONIZED -> !entry.isColonized();
            case COLONIZABLE -> entry.isColonizable();
            case ALL_BODIES -> true;
            case ONLY_PLANETS -> !entry.isMoon();
            case ONLY_MOONS -> entry.isMoon();
        }).toList();

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

        Empire playerEmpire = empires.stream()
                .filter(e -> e.id().equalsIgnoreCase(playerEmpireId))
                .findFirst()
                .orElse(null);

        if (playerEmpire != null && playerEmpire.unlockedTechIds() != null && !playerEmpire.unlockedTechIds().isEmpty()) {
            if (playerEmpire.unlockedTechIds().contains(techId)) {
                return true;
            }
        }

        for (ResearchProject rp : researchProjects) {
            if (rp.empireId().equalsIgnoreCase(playerEmpireId) &&
                    rp.targetTechOrAppId().equalsIgnoreCase(techId) &&
                    rp.isComplete()) {
                return true;
            }
        }

        if (playerEmpire == null || playerEmpire.unlockedTechIds().isEmpty()) {
            Set<String> defaultStarters = Set.of("electricity", "industrial_production", "rocketry", "geological_prospecting");
            return defaultStarters.contains(techId);
        }

        return false;
    }

    public EmpireEconomyReport calculateEmpireEconomyReport() {
        return EmpireEconomyCalculator.calculateEmpireEconomyReport(this);
    }

    public SystemEconomyReport calculateSystemEconomyReport(String systemId) {
        return EmpireEconomyCalculator.calculateSystemEconomyReport(this, systemId);
    }

    public void setFeedback(String message, boolean success) {
        if (feedbackLabel != null) {
            feedbackLabel.setText(message);
            feedbackLabel.setTextFill(success ? Color.LIGHTGREEN : Color.LIGHTCORAL);
        }
    }

    public static String formatTitle(String id) {
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
