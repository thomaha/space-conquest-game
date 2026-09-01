package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.ReverseEngineerSalvageCommand;
import com.spaceconquest.control.command.SelectOptimizationPathCommand;
import com.spaceconquest.control.command.StartResearchCommand;
import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.TechnicalApplication;
import com.spaceconquest.engine.Technology;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.economy.SystemEconomy;
import com.spaceconquest.engine.technology.ResearchProject;
import com.spaceconquest.engine.technology.TechnologyExchangeRoute;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A UI component that visualizes the technology tree, active research endeavors,
 * dual-path optimization options and reverse-engineering salvage queues with direct player command dispatching.
 */
public class TechnologyView {
    private static final Logger logger = LogManager.getLogger(TechnologyView.class);

    private VBox root;
    private VBox content;
    private ScrollPane scrollPane;
    private Label feedbackLabel;
    private final Menubar menubar;
    private HumanController humanController;
    private String playerEmpireId = "terran_confederation";
    private final List<ResearchProject> activeResearchProjects = new ArrayList<>();
    private final List<TechnologyExchangeRoute> exchangeRoutes = new ArrayList<>();
    private final List<SystemEconomy> systemEconomies = new ArrayList<>();
    private Empire playerEmpire;
    private long totalScientists = 0;
    private long unassignedScientists = 0;

    public TechnologyView(Menubar menubar) {
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

    private void build() {
        root = new VBox(15);
        content = new VBox(15);
        scrollPane = new ScrollPane(content);

        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: rgba(12, 20, 42, 0.95); " +
                "-fx-border-color: #78aaff; -fx-border-width: 2; " +
                "-fx-border-radius: 10; -fx-background-radius: 10;");
        root.setPrefSize(880, 680);

        Text title = new Text("Imperial technology and research laboratory");
        title.setFill(Color.WHITE);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 22));

        Button closeButton = new Button("Close");
        closeButton.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-weight: bold;");
        closeButton.setOnAction(e -> {
            root.setVisible(false);
            if (menubar != null) {
                menubar.closePage();
            }
        });

        HBox header = new HBox(title);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(title, Priority.ALWAYS);

        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(spacer, closeButton);

        feedbackLabel = new Label("Ready | Assign imperial scientists to commence research.");
        feedbackLabel.setTextFill(Color.LIGHTCYAN);
        feedbackLabel.setFont(Font.font("Verdana", 11));

        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.setPadding(new Insets(10));

        root.getChildren().addAll(header, feedbackLabel, scrollPane);
        root.setVisible(false);
    }

    public VBox getRoot() {
        return root;
    }

    public void show() {
        loadData();
        root.setVisible(true);
        root.toFront();
    }

    public void hide() {
        root.setVisible(false);
        if (menubar != null) {
            menubar.closePage();
        }
    }

    public void setResearchProjects(List<ResearchProject> projects) {
        activeResearchProjects.clear();
        if (projects != null) {
            activeResearchProjects.addAll(projects);
        }
        if (root.isVisible()) {
            loadData();
        }
    }

    public void updateData(List<ResearchProject> projects, List<TechnologyExchangeRoute> routes, List<SystemEconomy> economies, Empire empire) {
        activeResearchProjects.clear();
        if (projects != null) activeResearchProjects.addAll(projects);
        exchangeRoutes.clear();
        if (routes != null) exchangeRoutes.addAll(routes);
        systemEconomies.clear();
        if (economies != null) systemEconomies.addAll(economies);
        this.playerEmpire = empire;

        if (root != null && root.isVisible()) {
            loadData();
        }
    }

    private void loadData() {
        content.getChildren().clear();

        // 0. Summary Section (Scientists headcount)
        content.getChildren().add(createScientistSummarySection());

        // 1. Active Research Section
        content.getChildren().add(createActiveResearchSection());

        // 2. Technology Exchange Accords Section
        content.getChildren().add(createExchangeAccordsSection());

        // 3. Reverse Engineering Salvage Section
        content.getChildren().add(createReverseEngineeringSection());

        // 4. Foundational Tech Tree Section
        try {
            List<Technology> technologies = DataModelLoader.loadTechnologies();
            Text techTreeHeader = new Text("Available technologies and practical applications");
            techTreeHeader.setFill(Color.LIGHTBLUE);
            techTreeHeader.setFont(Font.font("Verdana", FontWeight.BOLD, 16));
            content.getChildren().add(techTreeHeader);

            for (Technology tech : technologies) {
                // Hide non-researchable technologies (simple logic for now)
                if (playerEmpire != null && !playerEmpire.unlockedTechIds().contains(tech.id())) {
                    boolean allReqsMet = tech.requiredTechnologies().isEmpty() ||
                            playerEmpire.unlockedTechIds().containsAll(tech.requiredTechnologies());
                    if (allReqsMet) {
                        content.getChildren().add(createTechBox(tech));
                    }
                } else if (playerEmpire == null) {
                    content.getChildren().add(createTechBox(tech));
                }
            }
        } catch (IOException e) {
            logger.error("Failed to load technologies for visualization", e);
            Text errorText = new Text("Error loading technology data.");
            errorText.setFill(Color.RED);
            content.getChildren().add(errorText);
        }
    }

    private VBox createActiveResearchSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(12));
        section.setStyle("-fx-background-color: rgba(30, 45, 75, 0.7); -fx-background-radius: 8; -fx-border-color: #4a90e2; -fx-border-width: 1; -fx-border-radius: 8;");

        Text sectionTitle = new Text("Active research projects and progress vectors");
        sectionTitle.setFill(Color.GOLD);
        sectionTitle.setFont(Font.font("Verdana", FontWeight.BOLD, 15));
        section.getChildren().add(sectionTitle);

        if (activeResearchProjects.isEmpty()) {
            Text emptyText = new Text("No active research project currently in progress. Select a technology below to assign scientists.");
            emptyText.setFill(Color.LIGHTGRAY);
            emptyText.setFont(Font.font("Verdana", 12));
            section.getChildren().add(emptyText);
        } else {
            for (ResearchProject project : activeResearchProjects) {
                if (!project.empireId().equals(playerEmpireId)) continue;

                VBox projBox = new VBox(6);
                projBox.setPadding(new Insets(8));
                projBox.setStyle("-fx-background-color: rgba(20, 30, 50, 0.6); -fx-background-radius: 5;");

                HBox header = new HBox(10);
                header.setAlignment(Pos.CENTER_LEFT);
                Text name = new Text("Target: " + project.targetTechOrAppId() + (project.isApplication() ? " (Application)" : " (Foundational)"));
                name.setFill(Color.LIGHTCYAN);
                name.setFont(Font.font("Verdana", FontWeight.BOLD, 13));

                Text scientists = new Text("Assigned: " + project.assignedScientists() + " scientists");
                scientists.setFill(Color.LIGHTGREEN);
                scientists.setFont(Font.font("Verdana", 11));

                Text speedMod = new Text(String.format("Multiplier: %.2fx", project.speedModifier()));
                speedMod.setFill(Color.YELLOW);
                speedMod.setFont(Font.font("Verdana", 11));

                header.getChildren().addAll(name, scientists, speedMod);

                ProgressBar bar = new ProgressBar(project.accumulatedPoints() / Math.max(1.0, project.requiredPoints()));
                bar.setPrefWidth(400);

                Text progressText = new Text(String.format("Progress: %.1f / %.1f pts (%.1f%%)",
                        project.accumulatedPoints(), project.requiredPoints(), project.getProgressPercentage()));
                progressText.setFill(Color.WHITE);
                progressText.setFont(Font.font("Verdana", 11));

                HBox barRow = new HBox(10, bar, progressText);
                barRow.setAlignment(Pos.CENTER_LEFT);

                Text breakthroughText = new Text("Breakthrough variance: Weighted variance roll active for this vector.");
                breakthroughText.setFill(Color.LIGHTSKYBLUE);
                breakthroughText.setFont(Font.font("Verdana", 9));

                projBox.getChildren().addAll(header, barRow, breakthroughText);
                section.getChildren().add(projBox);
            }
        }

        return section;
    }

    private VBox createScientistSummarySection() {
        VBox section = new VBox(5);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: rgba(40, 60, 90, 0.6); -fx-background-radius: 8;");

        long totalScientists = systemEconomies.stream()
                .filter(se -> se.empireId().equals(playerEmpireId))
                .mapToLong(SystemEconomy::employedScientists)
                .sum();
        int assignedScientists = activeResearchProjects.stream()
                .filter(p -> p.empireId().equals(playerEmpireId))
                .mapToInt(ResearchProject::assignedScientists)
                .sum();
        long unassigned = Math.max(0, totalScientists - assignedScientists);

        this.totalScientists = totalScientists;
        this.unassignedScientists = unassigned;

        Text summaryTitle = new Text("Imperial scientific personnel allocation");
        summaryTitle.setFill(Color.LIGHTBLUE);
        summaryTitle.setFont(Font.font("Verdana", FontWeight.BOLD, 14));

        Text headcount = new Text(String.format("Total scientists: %d | Assigned: %d | Unassigned: %d",
                totalScientists, assignedScientists, unassigned));
        headcount.setFill(Color.WHITE);
        headcount.setFont(Font.font("Verdana", 12));

        section.getChildren().addAll(summaryTitle, headcount);
        return section;
    }

    private VBox createExchangeAccordsSection() {
        VBox section = new VBox(8);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: rgba(35, 55, 85, 0.65); -fx-background-radius: 8; -fx-border-color: #27ae60; -fx-border-width: 1; -fx-border-radius: 8;");

        Text title = new Text("Technology exchange and bilateral accords");
        title.setFill(Color.LIGHTGREEN);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
        section.getChildren().add(title);

        List<TechnologyExchangeRoute> myRoutes = exchangeRoutes.stream()
                .filter(r -> r.receiverEmpireId().equals(playerEmpireId) || r.senderEmpireId().equals(playerEmpireId))
                .toList();

        if (myRoutes.isEmpty()) {
            Text empty = new Text("No active technology exchange accords established with other empires.");
            empty.setFill(Color.LIGHTGRAY);
            empty.setFont(Font.font("Verdana", 11));
            section.getChildren().add(empty);
        } else {
            for (TechnologyExchangeRoute route : myRoutes) {
                String desc = route.senderEmpireId().equals(playerEmpireId) ?
                        String.format("Sharing %s with %s (Bonus: %.0f%% training speed)",
                                route.technologyId(), route.receiverEmpireId(), route.trainingSpeedBonus() * 100) :
                        String.format("Receiving %s insights from %s (Pre-populated: %.0f%%)",
                                route.technologyId(), route.senderEmpireId(), route.prePopulatedPercentage() * 100);

                Text routeText = new Text("• " + desc);
                routeText.setFill(Color.GAINSBORO);
                routeText.setFont(Font.font("Verdana", 11));
                section.getChildren().add(routeText);
            }
        }

        return section;
    }

    private VBox createReverseEngineeringSection() {
        VBox section = new VBox(8);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: rgba(25, 40, 65, 0.65); -fx-background-radius: 8; -fx-border-color: #9b59b6; -fx-border-width: 1; -fx-border-radius: 8;");

        Text title = new Text("Xeno-debris and reverse engineering salvage");
        title.setFill(Color.VIOLET);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
        section.getChildren().add(title);

        // Display progress of reverse engineering missions if any (mocked for now)
        Text missionProgress = new Text("Active salvage missions: Analyzing derelict starship hull (Progress: 42%)");
        missionProgress.setFill(Color.LIGHTBLUE);
        missionProgress.setFont(Font.font("Verdana", 11));
        section.getChildren().add(missionProgress);

        HBox controls = new HBox(10);
        controls.setAlignment(Pos.CENTER_LEFT);

        Button deconstructBtn = new Button("Analyze wreckage debris (+progress vector)");
        deconstructBtn.setStyle("-fx-background-color: #8e44ad; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px;");
        deconstructBtn.setOnAction(e -> {
            if (humanController != null) {
                humanController.stageCommand(new ReverseEngineerSalvageCommand(
                        playerEmpireId, "alien_salvage_hull_01", 0.75, "energy_shielding"
                ));
                feedbackLabel.setText("Dispatched reverse engineering command for alien salvage debris.");
                feedbackLabel.setTextFill(Color.LIGHTGREEN);
            }
        });

        Text desc = new Text("Reverse engineers recovered components accounting for biochemical translation distance.");
        desc.setFill(Color.LIGHTGRAY);
        desc.setFont(Font.font("Verdana", 11));

        controls.getChildren().addAll(deconstructBtn, desc);
        section.getChildren().add(controls);
        return section;
    }

    private VBox createTechBox(Technology tech) {
        VBox techBox = new VBox(6);
        techBox.setPadding(new Insets(10));
        techBox.setStyle("-fx-background-color: rgba(60, 80, 120, 0.5); -fx-background-radius: 5;");

        HBox titleRow = new HBox(10);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Text techName = new Text(tech.name());
        techName.setFill(Color.LIGHTBLUE);
        techName.setFont(Font.font("Verdana", FontWeight.BOLD, 16));

        Text techLevel = new Text("Level: 1"); // Placeholder for actual level
        techLevel.setFill(Color.LIGHTGOLDENRODYELLOW);
        techLevel.setFont(Font.font("Verdana", FontWeight.BOLD, 12));

        Text techComplexity = new Text("(Complexity: " + tech.complexity() + ")");
        techComplexity.setFill(Color.LIGHTSKYBLUE);
        techComplexity.setFont(Font.font("Verdana", 12));

        Spinner<Integer> scientistSpinner = new Spinner<>(0, (int) totalScientists, Math.min((int) unassignedScientists, 5));
        scientistSpinner.setPrefWidth(80);
        scientistSpinner.setEditable(true);

        Button researchBtn = new Button("Start research");
        researchBtn.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px;");
        researchBtn.setOnAction(e -> {
            int count = scientistSpinner.getValue();
            if (count <= 0) {
                feedbackLabel.setText("Cannot assign zero scientists.");
                feedbackLabel.setTextFill(Color.ORANGERED);
                return;
            }

            // Check against unassigned scientists
            // Note: if re-assigning to current project, we'd need to account for currently assigned
            // But this UI is for starting/restarting, let's keep it simple for now.
            if (count > unassignedScientists) {
                feedbackLabel.setText("Insufficient unassigned scientists available.");
                feedbackLabel.setTextFill(Color.ORANGERED);
                return;
            }

            if (humanController != null) {
                humanController.stageCommand(new StartResearchCommand(
                        playerEmpireId, tech.id(), false, count
                ));
                feedbackLabel.setText("Research initiated for foundational technology: " + tech.name() + " (" + count + " scientists assigned)");
                feedbackLabel.setTextFill(Color.LIGHTGREEN);
            }
        });

        titleRow.getChildren().addAll(techName, techLevel, techComplexity, scientistSpinner, researchBtn);

        Text techDesc = new Text(tech.description());
        techDesc.setFill(Color.WHITE);
        techDesc.setWrappingWidth(740);

        Text bonusText = new Text("Researched bonus: +5% theoretical scientific progression efficiency.");
        bonusText.setFill(Color.LIGHTCYAN);
        bonusText.setFont(Font.font("Verdana", 10));

        techBox.getChildren().addAll(titleRow, techDesc, bonusText);

        if (!tech.requiredTechnologies().isEmpty()) {
            Text reqs = new Text("Requires: " + String.join(", ", tech.requiredTechnologies()));
            reqs.setFill(Color.GOLD);
            reqs.setFont(Font.font("Verdana", 11));
            techBox.getChildren().add(reqs);
        }

        if (!tech.applications().isEmpty()) {
            VBox appsBox = new VBox(6);
            appsBox.setPadding(new Insets(5, 0, 0, 15));
            for (TechnicalApplication app : tech.applications()) {
                appsBox.getChildren().add(createAppBox(app));
            }
            techBox.getChildren().add(appsBox);
        }

        return techBox;
    }

    private VBox createAppBox(TechnicalApplication app) {
        if (playerEmpire != null && !app.requiredTechnologies().isEmpty() &&
                !playerEmpire.unlockedTechIds().containsAll(app.requiredTechnologies())) {
            return new VBox();
        }

        VBox appBox = new VBox(4);
        appBox.setPadding(new Insets(6));
        appBox.setStyle("-fx-background-color: rgba(30, 45, 65, 0.4); -fx-background-radius: 4;");

        HBox appHeader = createAppHeader(app);

        Text appDesc = new Text(app.description());
        appDesc.setFill(Color.GAINSBORO);
        appDesc.setFont(Font.font("Verdana", 11));
        appDesc.setWrappingWidth(700);

        VBox optBox = createOptimizationBox(app);

        appBox.getChildren().addAll(appHeader, appDesc);

        if (!app.requiredTechnologies().isEmpty()) {
            Text reqs = new Text("  Requires: " + String.join(", ", app.requiredTechnologies()));
            reqs.setFill(Color.ORANGE);
            reqs.setFont(Font.font("Verdana", 10));
            appBox.getChildren().add(reqs);
        }

        if (!app.affectedFactors().isEmpty()) {
            Text factors = new Text("  Affects: " + String.join(", ", app.affectedFactors()));
            factors.setFill(Color.LIGHTCYAN);
            factors.setFont(Font.font("Verdana", 10));
            appBox.getChildren().add(factors);
        }

        if (!app.requiredMaterials().isEmpty()) {
            Text materials = new Text("  Materials: " + String.join(", ", app.requiredMaterials()));
            materials.setFill(Color.KHAKI);
            materials.setFont(Font.font("Verdana", 10));
            appBox.getChildren().add(materials);
        }

        appBox.getChildren().add(optBox);
        return appBox;
    }

    private HBox createAppHeader(TechnicalApplication app) {
        HBox appHeader = new HBox(10);
        appHeader.setAlignment(Pos.CENTER_LEFT);

        Text appName = new Text("• " + app.name() + " (Cost: " + (int) app.costToBuildPerUnit() + " hrs, Complexity: " + app.complexity() + ")");
        appName.setFill(Color.LIGHTGREEN);
        appName.setFont(Font.font("Verdana", FontWeight.BOLD, 13));

        Text appBonus = new Text("Researched bonus: -10% resource requirement.");
        appBonus.setFill(Color.KHAKI);
        appBonus.setFont(Font.font("Verdana", 10));

        Spinner<Integer> appScientistSpinner = new Spinner<>(0, (int) totalScientists, Math.min((int) unassignedScientists, 5));
        appScientistSpinner.setPrefWidth(80);
        appScientistSpinner.setEditable(true);

        Button appResearchBtn = new Button("Research app");
        appResearchBtn.setStyle("-fx-background-color: #16a085; -fx-text-fill: white; -fx-font-size: 10px; -fx-font-weight: bold;");
        appResearchBtn.setOnAction(e -> {
            int count = appScientistSpinner.getValue();
            if (count <= 0) {
                feedbackLabel.setText("Cannot assign zero scientists.");
                feedbackLabel.setTextFill(Color.ORANGERED);
                return;
            }

            if (count > unassignedScientists) {
                feedbackLabel.setText("Insufficient unassigned scientists available.");
                feedbackLabel.setTextFill(Color.ORANGERED);
                return;
            }

            if (humanController != null) {
                humanController.stageCommand(new StartResearchCommand(
                        playerEmpireId, app.id(), true, count
                ));
                feedbackLabel.setText("Research initiated for application: " + app.name() + " (" + count + " scientists assigned)");
                feedbackLabel.setTextFill(Color.LIGHTGREEN);
            }
        });

        appHeader.getChildren().addAll(appName, appBonus, appScientistSpinner, appResearchBtn);
        return appHeader;
    }

    private VBox createOptimizationBox(TechnicalApplication app) {
        VBox optBox = new VBox(4);
        Text optHeader = new Text("Dual-path optimization (based on breakthrough outcomes):");
        optHeader.setFill(Color.LIGHTCORAL);
        optHeader.setFont(Font.font("Verdana", FontWeight.BOLD, 9));

        HBox optControls = new HBox(10);
        optControls.setAlignment(Pos.CENTER_LEFT);

        Button pathABtn = new Button("Path A: performance (+15% output, +20% cost)");
        pathABtn.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white; -fx-font-size: 10px;");
        pathABtn.setOnAction(e -> {
            if (humanController != null) {
                humanController.stageCommand(new SelectOptimizationPathCommand(
                        playerEmpireId, app.id(), SelectOptimizationPathCommand.PATH_A_PERFORMANCE
                ));
                feedbackLabel.setText("Selected Path A (Performance) optimization for " + app.name());
                feedbackLabel.setTextFill(Color.LIGHTGREEN);
            }
        });

        Button pathBBtn = new Button("Path B: miniaturize (-15% cost, -1 complexity)");
        pathBBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-size: 10px;");
        pathBBtn.setOnAction(e -> {
            if (humanController != null) {
                humanController.stageCommand(new SelectOptimizationPathCommand(
                        playerEmpireId, app.id(), SelectOptimizationPathCommand.PATH_B_MINIATURIZATION
                ));
                feedbackLabel.setText("Selected Path B (Miniaturization) optimization for " + app.name());
                feedbackLabel.setTextFill(Color.LIGHTGREEN);
            }
        });

        optControls.getChildren().addAll(pathABtn, pathBBtn);
        optBox.getChildren().addAll(optHeader, optControls);
        return optBox;
    }
}
