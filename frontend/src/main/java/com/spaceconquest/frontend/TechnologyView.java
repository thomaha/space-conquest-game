package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.ReverseEngineerSalvageCommand;
import com.spaceconquest.control.command.SelectOptimizationPathCommand;
import com.spaceconquest.control.command.StartResearchCommand;
import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.TechnicalApplication;
import com.spaceconquest.engine.Technology;
import com.spaceconquest.engine.technology.ResearchProject;
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

        Text title = new Text("Imperial Technology & Research Laboratory");
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

    private void loadData() {
        content.getChildren().clear();

        // 1. Active Research Section
        content.getChildren().add(createActiveResearchSection());

        // 2. Reverse Engineering Salvage Section
        content.getChildren().add(createReverseEngineeringSection());

        // 3. Foundational Tech Tree Section
        try {
            List<Technology> technologies = DataModelLoader.loadTechnologies();
            Text techTreeHeader = new Text("Available Technologies & Practical Applications");
            techTreeHeader.setFill(Color.LIGHTBLUE);
            techTreeHeader.setFont(Font.font("Verdana", FontWeight.BOLD, 16));
            content.getChildren().add(techTreeHeader);

            for (Technology tech : technologies) {
                content.getChildren().add(createTechBox(tech));
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

        Text sectionTitle = new Text("Active Research Projects & Progress Vectors");
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
                VBox projBox = new VBox(6);
                projBox.setPadding(new Insets(8));
                projBox.setStyle("-fx-background-color: rgba(20, 30, 50, 0.6); -fx-background-radius: 5;");

                HBox header = new HBox(10);
                header.setAlignment(Pos.CENTER_LEFT);
                Text name = new Text("Target: " + project.targetTechOrAppId() + (project.isApplication() ? " (Application)" : " (Foundational)"));
                name.setFill(Color.LIGHTCYAN);
                name.setFont(Font.font("Verdana", FontWeight.BOLD, 13));

                Text scientists = new Text("Assigned: " + project.assignedScientists() + " Scientists");
                scientists.setFill(Color.LIGHTGREEN);
                scientists.setFont(Font.font("Verdana", 11));
                header.getChildren().addAll(name, scientists);

                ProgressBar bar = new ProgressBar(project.accumulatedPoints() / Math.max(1.0, project.requiredPoints()));
                bar.setPrefWidth(400);

                Text progressText = new Text(String.format("Progress: %.1f / %.1f pts (%.1f%%)",
                        project.accumulatedPoints(), project.requiredPoints(), project.getProgressPercentage()));
                progressText.setFill(Color.WHITE);
                progressText.setFont(Font.font("Verdana", 11));

                HBox barRow = new HBox(10, bar, progressText);
                barRow.setAlignment(Pos.CENTER_LEFT);

                projBox.getChildren().addAll(header, barRow);
                section.getChildren().add(projBox);
            }
        }

        return section;
    }

    private VBox createReverseEngineeringSection() {
        VBox section = new VBox(8);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: rgba(25, 40, 65, 0.65); -fx-background-radius: 8; -fx-border-color: #9b59b6; -fx-border-width: 1; -fx-border-radius: 8;");

        Text title = new Text("Xeno-Debris & Reverse Engineering Salvage");
        title.setFill(Color.VIOLET);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 14));

        HBox controls = new HBox(10);
        controls.setAlignment(Pos.CENTER_LEFT);

        Button deconstructBtn = new Button("Analyze Wreckage Debris (+Progress Vector)");
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
        section.getChildren().addAll(title, controls);
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

        Text techComplexity = new Text("(Complexity: " + tech.complexity() + ")");
        techComplexity.setFill(Color.LIGHTSKYBLUE);
        techComplexity.setFont(Font.font("Verdana", 12));

        Spinner<Integer> scientistSpinner = new Spinner<>(1, 50, 5);
        scientistSpinner.setPrefWidth(70);

        Button researchBtn = new Button("Start Research");
        researchBtn.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px;");
        researchBtn.setOnAction(e -> {
            int count = scientistSpinner.getValue();
            if (humanController != null) {
                humanController.stageCommand(new StartResearchCommand(
                        playerEmpireId, tech.id(), false, count
                ));
                feedbackLabel.setText("Research initiated for foundational technology: " + tech.name() + " (" + count + " scientists assigned)");
                feedbackLabel.setTextFill(Color.LIGHTGREEN);
            }
        });

        titleRow.getChildren().addAll(techName, techComplexity, scientistSpinner, researchBtn);

        Text techDesc = new Text(tech.description());
        techDesc.setFill(Color.WHITE);
        techDesc.setWrappingWidth(740);

        techBox.getChildren().addAll(titleRow, techDesc);

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
        VBox appBox = new VBox(4);
        appBox.setPadding(new Insets(6));
        appBox.setStyle("-fx-background-color: rgba(30, 45, 65, 0.4); -fx-background-radius: 4;");

        HBox appHeader = new HBox(10);
        appHeader.setAlignment(Pos.CENTER_LEFT);

        Text appName = new Text("• " + app.name() + " (Cost: " + (int) app.costToBuildPerUnit() + " hrs, Complexity: " + app.complexity() + ")");
        appName.setFill(Color.LIGHTGREEN);
        appName.setFont(Font.font("Verdana", FontWeight.BOLD, 13));

        Button appResearchBtn = new Button("Research App");
        appResearchBtn.setStyle("-fx-background-color: #16a085; -fx-text-fill: white; -fx-font-size: 10px; -fx-font-weight: bold;");
        appResearchBtn.setOnAction(e -> {
            if (humanController != null) {
                humanController.stageCommand(new StartResearchCommand(
                        playerEmpireId, app.id(), true, 5
                ));
                feedbackLabel.setText("Research initiated for application: " + app.name());
                feedbackLabel.setTextFill(Color.LIGHTGREEN);
            }
        });

        appHeader.getChildren().addAll(appName, appResearchBtn);

        Text appDesc = new Text(app.description());
        appDesc.setFill(Color.GAINSBORO);
        appDesc.setFont(Font.font("Verdana", 11));
        appDesc.setWrappingWidth(700);

        // Optimization Controls
        HBox optControls = new HBox(10);
        optControls.setAlignment(Pos.CENTER_LEFT);

        Button pathABtn = new Button("Path A: Performance (+15% output, +20% cost)");
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

        Button pathBBtn = new Button("Path B: Miniaturize (-15% cost, -1 complexity)");
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

        appBox.getChildren().add(optControls);

        return appBox;
    }
}
