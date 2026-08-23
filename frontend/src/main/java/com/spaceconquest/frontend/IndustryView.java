package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.BuildFacilityCommand;
import com.spaceconquest.control.command.ExpandFacilityCommand;
import com.spaceconquest.control.command.SetFacilityRecipeCommand;
import com.spaceconquest.control.command.StartProspectingMissionCommand;
import com.spaceconquest.engine.industry.FacilityExpansionProject;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.industry.refinement.RefinementProcessor;
import com.spaceconquest.engine.industry.refinement.RefinementRecipe;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Interactive UI panel displaying planetary industrial processing facilities,
 * tier expansion pipelines, prospecting missions and public vs corporate ownership routing with direct command execution.
 */
public class IndustryView {
    private VBox root;
    private VBox content;
    private ScrollPane scrollPane;
    private Label feedbackLabel;
    private final Menubar menubar;
    private HumanController humanController;
    private String playerEmpireId = "terran_confederation";
    private final List<IndustrialFacility> facilities = new ArrayList<>();
    private final List<FacilityExpansionProject> expansionProjects = new ArrayList<>();

    public IndustryView(Menubar menubar) {
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
        content = new VBox(12);
        scrollPane = new ScrollPane(content);

        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: rgba(10, 18, 38, 0.96); " +
                "-fx-border-color: #a29bfe; -fx-border-width: 2; " +
                "-fx-border-radius: 10; -fx-background-radius: 10;");
        root.setPrefSize(920, 700);

        Text title = new Text("Galactic industrial production and facility construction");
        title.setFill(Color.WHITE);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 22));

        Button closeButton = new Button("Close");
        closeButton.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-weight: bold;");
        closeButton.setOnAction(e -> hide());

        HBox header = new HBox(title);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(title, Priority.ALWAYS);

        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(spacer, closeButton);

        feedbackLabel = new Label("Ready | Commission facilities or initiate prospecting surveys.");
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
        renderContent();
        root.setVisible(true);
        root.toFront();
    }

    public void hide() {
        root.setVisible(false);
        if (menubar != null) {
            menubar.closePage();
        }
    }

    public void updateData(List<IndustrialFacility> newFacilities, List<FacilityExpansionProject> newProjects) {
        facilities.clear();
        if (newFacilities != null) facilities.addAll(newFacilities);

        expansionProjects.clear();
        if (newProjects != null) expansionProjects.addAll(newProjects);

        if (root.isVisible()) {
            renderContent();
        }
    }

    private void renderContent() {
        content.getChildren().clear();

        // 1. Interactive Construction & Prospecting Workbench
        content.getChildren().add(createConstructionWorkbench());

        // 2. Facilities List
        content.getChildren().add(createFacilitiesSection());

        // 3. Expansion Projects Section
        content.getChildren().add(createExpansionSection());

        // 4. Refinement & Metallurgical Alloy Recipes
        content.getChildren().add(createRefinementAlloysSection());
    }

    private VBox createConstructionWorkbench() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(12));
        section.setStyle("-fx-background-color: rgba(25, 45, 75, 0.75); -fx-background-radius: 8; -fx-border-color: #00cec9; -fx-border-width: 1; -fx-border-radius: 8;");

        Text title = new Text("Commission surface facilities and geological prospecting");
        title.setFill(Color.AQUA);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 15));

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);

        Label planetLbl = new Label("Target planet:");
        planetLbl.setTextFill(Color.LIGHTCYAN);
        ComboBox<String> planetCombo = new ComboBox<>();
        planetCombo.getItems().addAll("earth", "mars", "luna", "ceres", "titan", "venus", "mercury");
        planetCombo.setValue("earth");

        Label appLbl = new Label("Facility application:");
        appLbl.setTextFill(Color.LIGHTCYAN);
        ComboBox<String> appCombo = new ComboBox<>();
        appCombo.getItems().addAll("smelter", "foundry", "hydroponics_dome", "consumer_factory", "fission_reactor", "fusion_reactor", "mining_outpost", "mass_driver");
        appCombo.setValue("smelter");

        Label profLbl = new Label("Worker profession:");
        profLbl.setTextFill(Color.LIGHTCYAN);
        ComboBox<String> profCombo = new ComboBox<>();
        profCombo.getItems().addAll("miner", "smelter_operator", "hydroponics_farmer", "technician", "engineer");
        profCombo.setValue("smelter_operator");

        Label tierLbl = new Label("Facility tier:");
        tierLbl.setTextFill(Color.LIGHTCYAN);
        Spinner<Integer> tierSpinner = new Spinner<>(1, 4, 1);
        tierSpinner.setPrefWidth(70);

        Button buildBtn = new Button("Order facility construction");
        buildBtn.setStyle("-fx-background-color: #0984e3; -fx-text-fill: white; -fx-font-weight: bold;");
        buildBtn.setOnAction(e -> {
            if (humanController != null) {
                humanController.stageCommand(new BuildFacilityCommand(
                        playerEmpireId, planetCombo.getValue(), appCombo.getValue(), profCombo.getValue(), tierSpinner.getValue()
                ));
                feedbackLabel.setText("Dispatched construction order for " + appCombo.getValue() + " on " + planetCombo.getValue().toUpperCase());
                feedbackLabel.setTextFill(Color.LIGHTGREEN);
            }
        });

        Button prospectBtn = new Button("Launch geological survey (5 staff)");
        prospectBtn.setStyle("-fx-background-color: #6c5ce7; -fx-text-fill: white; -fx-font-weight: bold;");
        prospectBtn.setOnAction(e -> {
            if (humanController != null) {
                humanController.stageCommand(new StartProspectingMissionCommand(
                        playerEmpireId, planetCombo.getValue(), 5
                ));
                feedbackLabel.setText("Dispatched geological prospecting survey team to " + planetCombo.getValue().toUpperCase());
                feedbackLabel.setTextFill(Color.LIGHTGREEN);
            }
        });

        grid.add(planetLbl, 0, 0);
        grid.add(planetCombo, 1, 0);
        grid.add(appLbl, 2, 0);
        grid.add(appCombo, 3, 0);

        grid.add(profLbl, 0, 1);
        grid.add(profCombo, 1, 1);
        grid.add(tierLbl, 2, 1);
        grid.add(tierSpinner, 3, 1);

        HBox actions = new HBox(12, buildBtn, prospectBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        section.getChildren().addAll(title, grid, actions);
        return section;
    }

    private VBox createFacilitiesSection() {
        VBox section = new VBox(8);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: rgba(20, 35, 60, 0.7); -fx-background-radius: 8; -fx-border-color: #a29bfe; -fx-border-width: 1; -fx-border-radius: 8;");

        Text header = new Text("Active industrial facilities (" + facilities.size() + ")");
        header.setFill(Color.MEDIUMPURPLE);
        header.setFont(Font.font("Verdana", FontWeight.BOLD, 15));
        section.getChildren().add(header);

        if (facilities.isEmpty()) {
            Text empty = new Text("No industrial manufacturing or refining facilities constructed.");
            empty.setFill(Color.LIGHTGRAY);
            section.getChildren().add(empty);
        } else {
            for (IndustrialFacility fac : facilities) {
                VBox card = new VBox(6);
                card.setPadding(new Insets(8));
                card.setStyle("-fx-background-color: rgba(15, 25, 45, 0.6); -fx-background-radius: 6;");

                HBox topRow = new HBox(10);
                topRow.setAlignment(Pos.CENTER_LEFT);

                Text facTitle = new Text(String.format("• Facility [%s] Planet: %s | Application: %s | Tier: %d | Workers: %d %s | Ownership: %s",
                        fac.id(), fac.planetId().toUpperCase(), fac.applicationId(), fac.tier(), fac.allocatedWorkers(),
                        fac.workerProfessionId(), fac.ownershipType()));
                facTitle.setFill(Color.WHITE);
                facTitle.setFont(Font.font("Verdana", FontWeight.BOLD, 12));
                HBox.setHgrow(facTitle, Priority.ALWAYS);

                Button upgradeBtn = new Button("Upgrade tier (+1)");
                upgradeBtn.setStyle("-fx-background-color: #fdcb6e; -fx-text-fill: black; -fx-font-weight: bold; -fx-font-size: 10px;");
                upgradeBtn.setOnAction(e -> {
                    if (humanController != null) {
                        humanController.stageCommand(new ExpandFacilityCommand(
                                fac.id(), fac.tier() + 1, 5000.0
                        ));
                        feedbackLabel.setText("Dispatched upgrade order for facility " + fac.id() + " to Tier " + (fac.tier() + 1));
                        feedbackLabel.setTextFill(Color.LIGHTGREEN);
                    }
                });

                ComboBox<String> recipeCombo = new ComboBox<>();
                for (RefinementRecipe rec : RefinementProcessor.STANDARD_RECIPES) {
                    recipeCombo.getItems().add(rec.id());
                }
                recipeCombo.setValue(RefinementProcessor.STANDARD_RECIPES.get(0).id());
                recipeCombo.setStyle("-fx-font-size: 10px;");

                Button recipeBtn = new Button("Set recipe");
                recipeBtn.setStyle("-fx-background-color: #00b894; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 10px;");
                recipeBtn.setOnAction(e -> {
                    if (humanController != null) {
                        String selectedRecipe = recipeCombo.getValue();
                        humanController.stageCommand(new SetFacilityRecipeCommand(
                                fac.id(), selectedRecipe
                        ));
                        feedbackLabel.setText("Updated active manufacturing recipe for facility " + fac.id() + " to " + selectedRecipe);
                        feedbackLabel.setTextFill(Color.LIGHTGREEN);
                    }
                });

                topRow.getChildren().addAll(facTitle, recipeCombo, recipeBtn, upgradeBtn);

                Text facStatus = new Text(String.format("  Throughput multiplier: %.2fx | Expansion status: %s",
                        fac.getEffectiveThroughputMultiplier(),
                        fac.isUndergoingExpansion() ? "UPGRADING (-50% Output Penalty Active)" : "NOMINAL CAPACITY"));
                facStatus.setFill(fac.isUndergoingExpansion() ? Color.ORANGE : Color.LIGHTGREEN);
                facStatus.setFont(Font.font("Verdana", 11));

                card.getChildren().addAll(topRow, facStatus);
                section.getChildren().add(card);
            }
        }

        return section;
    }

    private VBox createExpansionSection() {
        VBox section = new VBox(8);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: rgba(20, 35, 60, 0.7); -fx-background-radius: 8; -fx-border-color: #fdcb6e; -fx-border-width: 1; -fx-border-radius: 8;");

        Text header = new Text("Active facility tier scaling projects (" + expansionProjects.size() + ")");
        header.setFill(Color.GOLD);
        header.setFont(Font.font("Verdana", FontWeight.BOLD, 15));
        section.getChildren().add(header);

        if (expansionProjects.isEmpty()) {
            Text empty = new Text("No facilities currently undergoing expansion.");
            empty.setFill(Color.LIGHTGRAY);
            section.getChildren().add(empty);
        } else {
            for (FacilityExpansionProject proj : expansionProjects) {
                VBox card = new VBox(4);
                card.setPadding(new Insets(6));
                card.setStyle("-fx-background-color: rgba(15, 25, 45, 0.6); -fx-background-radius: 6;");

                Text projInfo = new Text(String.format("Project [%s] Facility: %s -> Upgrading to tier %d | Cost: %.0f credits",
                        proj.projectId(), proj.facilityId(), proj.targetTier(), proj.costCredits()));
                projInfo.setFill(Color.LIGHTCYAN);
                projInfo.setFont(Font.font("Verdana", 12));

                ProgressBar bar = new ProgressBar(proj.accumulatedWorkHours() / Math.max(1.0, proj.requiredWorkHours()));
                bar.setPrefWidth(400);

                Text progText = new Text(String.format("Work hours: %.0f / %.0f (%.1f%%)",
                        proj.accumulatedWorkHours(), proj.requiredWorkHours(), proj.getProgressPercentage()));
                progText.setFill(Color.WHITE);
                progText.setFont(Font.font("Verdana", 11));

                HBox barRow = new HBox(10, bar, progText);
                barRow.setAlignment(Pos.CENTER_LEFT);

                card.getChildren().addAll(projInfo, barRow);
                section.getChildren().add(card);
            }
        }

        return section;
    }

    private VBox createRefinementAlloysSection() {
        VBox section = new VBox(8);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: rgba(20, 35, 60, 0.7); -fx-background-radius: 8; -fx-border-color: #55efc4; -fx-border-width: 1; -fx-border-radius: 8;");

        Text header = new Text("Chemical refinement and metallurgical alloy recipes catalog (" + RefinementProcessor.STANDARD_RECIPES.size() + ")");
        header.setFill(Color.PALEGREEN);
        header.setFont(Font.font("Verdana", FontWeight.BOLD, 15));
        section.getChildren().add(header);

        for (RefinementRecipe recipe : RefinementProcessor.STANDARD_RECIPES) {
            VBox card = new VBox(4);
            card.setPadding(new Insets(8));
            card.setStyle("-fx-background-color: rgba(15, 25, 45, 0.6); -fx-background-radius: 6;");

            HBox row1 = new HBox(10);
            row1.setAlignment(Pos.CENTER_LEFT);

            Text recipeName = new Text(recipe.name() + " (" + recipe.id() + ")");
            recipeName.setFill(Color.WHITE);
            recipeName.setFont(Font.font("Verdana", FontWeight.BOLD, 12));

            Label catBadge = new Label(recipe.category());
            catBadge.setStyle("-fx-background-color: #2c3e50; -fx-text-fill: #00cec9; -fx-font-weight: bold; -fx-padding: 2 6 2 6; -fx-background-radius: 4; -fx-font-size: 10;");

            Label envBadge = new Label("Env: " + recipe.operationalEnvironment());
            envBadge.setStyle("-fx-background-color: #34495e; -fx-text-fill: #dfe6e9; -fx-padding: 2 6 2 6; -fx-background-radius: 4; -fx-font-size: 10;");

            Label tierBadge = new Label("Tier " + recipe.minFacilityTier() + "+ (" + recipe.primaryProfessionId() + ")");
            tierBadge.setStyle("-fx-background-color: #6c5ce7; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 2 6 2 6; -fx-background-radius: 4; -fx-font-size: 10;");

            row1.getChildren().addAll(recipeName, catBadge, envBadge, tierBadge);

            Text inputsText = new Text("  Inputs: " + recipe.inputMaterialsKg() + " | Energy: " + recipe.powerDrawKw() + " kW");
            inputsText.setFill(Color.LIGHTCYAN);
            inputsText.setFont(Font.font("Verdana", 11));

            Text outputsText = new Text("  Outputs: " + recipe.outputMaterialsKg() + (recipe.byproductMaterialsKg().isEmpty() ? "" : " | Byproducts: " + recipe.byproductMaterialsKg()));
            outputsText.setFill(Color.LIGHTGREEN);
            outputsText.setFont(Font.font("Verdana", 11));

            card.getChildren().addAll(row1, inputsText, outputsText);
            section.getChildren().add(card);
        }

        return section;
    }
}
