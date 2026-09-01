package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.CreateCustomEmpireCommand;
import com.spaceconquest.engine.audio.AudioSynthesizer;
import com.spaceconquest.engine.scenario.CustomEmpireProfile;
import com.spaceconquest.engine.scenario.IdeologicalEthics;
import com.spaceconquest.engine.scenario.SpeciesTrait;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Interactive UI wizard for designing custom sovereign empires, species traits,
 * biochemical archetypes and ideological ethics alignments.
 */
public class EmpireCreationWizardView {

    private VBox root;
    private final Menubar menubar;
    private final AudioSynthesizer audioSynthesizer;
    private HumanController humanController;

    private TextField empireNameField;
    private TextField empireIdField;
    private ComboBox<String> colorCombo;
    private ComboBox<String> govCombo;
    private TextField speciesNameField;
    private ComboBox<String> bioCombo;
    private Slider tempSlider;
    private Slider pressureSlider;
    private Slider gravitySlider;

    private Slider authDemoSlider;
    private Slider milPacSlider;
    private Slider matSpirSlider;
    private Slider xenPhilPhobeSlider;

    private Label traitPointsLabel;
    private Label ethicsPointsLabel;
    private Label statusLabel;

    private final Map<String, CheckBox> traitCheckboxes = new HashMap<>();

    public EmpireCreationWizardView(Menubar menubar, AudioSynthesizer audioSynthesizer) {
        this.menubar = menubar;
        this.audioSynthesizer = audioSynthesizer != null ? audioSynthesizer : new AudioSynthesizer();
        build();
    }

    public void setHumanController(HumanController controller) {
        this.humanController = controller;
    }

    private void build() {
        root = new VBox(12);
        root.setPadding(new Insets(18));
        root.setStyle("-fx-background-color: rgba(12, 18, 38, 0.98); " +
                "-fx-border-color: #9b59b6; -fx-border-width: 2; " +
                "-fx-border-radius: 10; -fx-background-radius: 10;");
        root.setPrefSize(960, 740);

        Text title = new Text("Custom empire and species bio-architect creator");
        title.setFill(Color.WHITE);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 20));

        Button closeButton = new Button("Close");
        closeButton.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-weight: bold;");
        closeButton.setOnAction(e -> hide());

        HBox header = new HBox(title);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(title, Priority.ALWAYS);
        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(spacer, closeButton);

        VBox content = new VBox(12);
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        // 1. Empire Identity Section
        content.getChildren().add(createEmpireIdentitySection());

        // 2. Species Bio-Architecture Section
        content.getChildren().add(createSpeciesSection());

        // 3. Species Traits Selection Section
        content.getChildren().add(createTraitsSection());

        // 4. Ideological Ethics Section
        content.getChildren().add(createEthicsSection());

        // 5. Action Footer
        HBox footer = createActionFooter();

        root.getChildren().addAll(header, scrollPane, footer);
        root.setVisible(false);
    }

    private VBox createEmpireIdentitySection() {
        VBox sec = new VBox(8);
        sec.setPadding(new Insets(10));
        sec.setStyle("-fx-background-color: rgba(25, 35, 65, 0.7); -fx-background-radius: 6;");

        Text secTitle = new Text("1. Sovereign empire identity and governance");
        secTitle.setFill(Color.GOLD);
        secTitle.setFont(Font.font("Verdana", FontWeight.BOLD, 13));

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(8);

        Label nameLbl = new Label("Empire name:");
        nameLbl.setTextFill(Color.LIGHTCYAN);
        empireNameField = new TextField("Solar Ascendancy");

        Label idLbl = new Label("Empire ID:");
        idLbl.setTextFill(Color.LIGHTCYAN);
        empireIdField = new TextField("solar_ascendancy");

        Label colorLbl = new Label("Primary color:");
        colorLbl.setTextFill(Color.LIGHTCYAN);
        colorCombo = new ComboBox<>();
        colorCombo.getItems().addAll("#3498db (Sapphire blue)", "#e74c3c (Imperial crimson)", "#2ecc71 (Emerald green)", "#f39c12 (Solar amber)", "#9b59b6 (Psionic violet)");
        colorCombo.setValue("#3498db (Sapphire blue)");

        Label govLbl = new Label("Government form:");
        govLbl.setTextFill(Color.LIGHTCYAN);
        govCombo = new ComboBox<>();
        govCombo.getItems().addAll(CustomEmpireProfile.GOV_DEMOCRACY, CustomEmpireProfile.GOV_AUTOCRACY, CustomEmpireProfile.GOV_CORPORATE_OLIGARCHY, CustomEmpireProfile.GOV_HIVE_MIND);
        govCombo.setValue(CustomEmpireProfile.GOV_DEMOCRACY);

        grid.add(nameLbl, 0, 0);
        grid.add(empireNameField, 1, 0);
        grid.add(idLbl, 2, 0);
        grid.add(empireIdField, 3, 0);

        grid.add(colorLbl, 0, 1);
        grid.add(colorCombo, 1, 1);
        grid.add(govLbl, 2, 1);
        grid.add(govCombo, 3, 1);

        sec.getChildren().addAll(secTitle, grid);
        return sec;
    }

    private VBox createSpeciesSection() {
        VBox sec = new VBox(8);
        sec.setPadding(new Insets(10));
        sec.setStyle("-fx-background-color: rgba(25, 35, 65, 0.7); -fx-background-radius: 6;");

        Text secTitle = new Text("2. Species bio-architecture and environmental tolerances");
        secTitle.setFill(Color.LIGHTGREEN);
        secTitle.setFont(Font.font("Verdana", FontWeight.BOLD, 13));

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(8);

        Label sNameLbl = new Label("Species name:");
        sNameLbl.setTextFill(Color.LIGHTCYAN);
        speciesNameField = new TextField("Homo Stellaris");

        Label bioLbl = new Label("Biochemical type:");
        bioLbl.setTextFill(Color.LIGHTCYAN);
        bioCombo = new ComboBox<>();
        bioCombo.getItems().addAll(CustomEmpireProfile.BIO_CARBON_HUMANOID, CustomEmpireProfile.BIO_CARBON_AVIAN, CustomEmpireProfile.BIO_CARBON_REPTILIAN, CustomEmpireProfile.BIO_SILICON_LITHOVORE, CustomEmpireProfile.BIO_GASEOUS_BREATHER);
        bioCombo.setValue(CustomEmpireProfile.BIO_CARBON_HUMANOID);

        grid.add(sNameLbl, 0, 0);
        grid.add(speciesNameField, 1, 0);
        grid.add(bioLbl, 2, 0);
        grid.add(bioCombo, 3, 0);

        Label tempLbl = new Label("Optimal temperature (K):");
        tempLbl.setTextFill(Color.LIGHTCYAN);
        tempSlider = new Slider(150, 450, 288.15);
        tempSlider.setShowTickLabels(true);

        Label pressLbl = new Label("Optimal pressure (atm):");
        pressLbl.setTextFill(Color.LIGHTCYAN);
        pressureSlider = new Slider(0.1, 5.0, 1.0);
        pressureSlider.setShowTickLabels(true);

        Label gravLbl = new Label("Optimal gravity (m/s²):");
        gravLbl.setTextFill(Color.LIGHTCYAN);
        gravitySlider = new Slider(0.5, 30.0, 9.81);
        gravitySlider.setShowTickLabels(true);

        grid.add(tempLbl, 0, 1);
        grid.add(tempSlider, 1, 1);
        grid.add(pressLbl, 2, 1);
        grid.add(pressureSlider, 3, 1);
        grid.add(gravLbl, 0, 2);
        grid.add(gravitySlider, 1, 2);

        sec.getChildren().addAll(secTitle, grid);
        return sec;
    }

    private VBox createTraitsSection() {
        VBox sec = new VBox(8);
        sec.setPadding(new Insets(10));
        sec.setStyle("-fx-background-color: rgba(25, 35, 65, 0.7); -fx-background-radius: 6;");

        HBox top = new HBox(15);
        Text secTitle = new Text("3. Genetic traits (max 5 points)");
        secTitle.setFill(Color.AQUA);
        secTitle.setFont(Font.font("Verdana", FontWeight.BOLD, 13));

        traitPointsLabel = new Label("Trait points budget: 0 / 5 spent");
        traitPointsLabel.setTextFill(Color.LIGHTGREEN);
        traitPointsLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 11));

        top.getChildren().addAll(secTitle, traitPointsLabel);
        sec.getChildren().add(top);

        GridPane traitGrid = new GridPane();
        traitGrid.setHgap(15);
        traitGrid.setVgap(6);

        List<SpeciesTrait> traits = SpeciesTrait.getStandardTraits();
        for (int i = 0; i < traits.size(); i++) {
            SpeciesTrait t = traits.get(i);
            CheckBox cb = new CheckBox(String.format("%s (%+d pt): %s", t.name(), t.pointCost(), t.description()));
            cb.setTextFill(Color.LIGHTGRAY);
            cb.setFont(Font.font("Verdana", 10));
            cb.setOnAction(e -> updateTraitBudget());
            traitCheckboxes.put(t.id(), cb);

            int row = i / 2;
            int col = i % 2;
            traitGrid.add(cb, col, row);
        }

        sec.getChildren().add(traitGrid);
        return sec;
    }

    private VBox createEthicsSection() {
        VBox sec = new VBox(8);
        sec.setPadding(new Insets(10));
        sec.setStyle("-fx-background-color: rgba(25, 35, 65, 0.7); -fx-background-radius: 6;");

        HBox top = new HBox(15);
        Text secTitle = new Text("4. Ideological ethics alignment (max 3 points)");
        secTitle.setFill(Color.MAGENTA);
        secTitle.setFont(Font.font("Verdana", FontWeight.BOLD, 13));

        ethicsPointsLabel = new Label("Ethics points spent: 0 / 3");
        ethicsPointsLabel.setTextFill(Color.LIGHTGREEN);
        ethicsPointsLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 11));

        top.getChildren().addAll(secTitle, ethicsPointsLabel);
        sec.getChildren().add(top);

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(8);

        authDemoSlider = createEthicsSlider(grid, 0, "Authoritarian (-2)", "Democratic (+2)");
        milPacSlider = createEthicsSlider(grid, 1, "Militarist (-2)", "Pacifist (+2)");
        matSpirSlider = createEthicsSlider(grid, 2, "Materialist (-2)", "Spiritualist (+2)");
        xenPhilPhobeSlider = createEthicsSlider(grid, 3, "Xenophobe (-2)", "Xenophile (+2)");

        sec.getChildren().add(grid);
        return sec;
    }

    private Slider createEthicsSlider(GridPane grid, int row, String leftLabel, String rightLabel) {
        Label l = new Label(leftLabel);
        l.setTextFill(Color.LIGHTCYAN);
        l.setFont(Font.font("Verdana", 10));

        Slider s = new Slider(-2, 2, 0);
        s.setBlockIncrement(1);
        s.setMajorTickUnit(1);
        s.setSnapToTicks(true);
        s.setShowTickLabels(true);
        s.valueProperty().addListener((obs, oldV, newV) -> updateEthicsBudget());

        Label r = new Label(rightLabel);
        r.setTextFill(Color.LIGHTCYAN);
        r.setFont(Font.font("Verdana", 10));

        grid.add(l, 0, row);
        grid.add(s, 1, row);
        grid.add(r, 2, row);
        return s;
    }

    private void updateTraitBudget() {
        int spent = 0;
        List<SpeciesTrait> traits = SpeciesTrait.getStandardTraits();
        for (SpeciesTrait t : traits) {
            CheckBox cb = traitCheckboxes.get(t.id());
            if (cb != null && cb.isSelected()) {
                spent += t.pointCost();
            }
        }
        traitPointsLabel.setText(String.format("Trait points budget: %d / 5 spent", spent));
        traitPointsLabel.setTextFill(spent <= 5 ? Color.LIGHTGREEN : Color.RED);
    }

    private void updateEthicsBudget() {
        int spent = (int) (Math.abs(authDemoSlider.getValue()) + Math.abs(milPacSlider.getValue()) +
                Math.abs(matSpirSlider.getValue()) + Math.abs(xenPhilPhobeSlider.getValue()));
        ethicsPointsLabel.setText(String.format("Ethics points spent: %d / 3", spent));
        ethicsPointsLabel.setTextFill(spent <= 3 ? Color.LIGHTGREEN : Color.RED);
    }

    private HBox createActionFooter() {
        HBox footer = new HBox(12);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(8));

        statusLabel = new Label("Ready to configure sovereign empire.");
        statusLabel.setTextFill(Color.LIGHTCYAN);
        statusLabel.setFont(Font.font("Verdana", 11));

        Button backBtn = new Button("← Back to campaign setup");
        backBtn.setStyle("-fx-background-color: #34495e; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px;");
        backBtn.setOnAction(e -> {
            hide();
            if (menubar != null && menubar.getScenarioEditorView() != null) {
                menubar.getScenarioEditorView().show();
            }
        });

        Button createBtn = new Button("Establish sovereign empire and launch campaign");
        createBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px;");
        createBtn.setOnAction(e -> handleEstablishEmpire());

        footer.getChildren().addAll(statusLabel, new javafx.scene.layout.Region(), backBtn, createBtn);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        return footer;
    }

    private void handleEstablishEmpire() {
        List<String> selectedTraits = new ArrayList<>();
        for (Map.Entry<String, CheckBox> entry : traitCheckboxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                selectedTraits.add(entry.getKey());
            }
        }

        IdeologicalEthics ethics = new IdeologicalEthics(
                (int) authDemoSlider.getValue(),
                (int) milPacSlider.getValue(),
                (int) matSpirSlider.getValue(),
                (int) xenPhilPhobeSlider.getValue()
        );

        String colorHex = colorCombo.getValue().split(" ")[0];

        CustomEmpireProfile profile = new CustomEmpireProfile(
                empireIdField.getText().trim(),
                empireNameField.getText().trim(),
                colorHex,
                "INSIGNIA_CUSTOM",
                govCombo.getValue(),
                "species_" + empireIdField.getText().trim(),
                speciesNameField.getText().trim(),
                bioCombo.getValue(),
                tempSlider.getValue(),
                pressureSlider.getValue(),
                gravitySlider.getValue(),
                selectedTraits,
                ethics,
                50000.0
        );

        if (humanController != null) {
            humanController.stageCommand(new CreateCustomEmpireCommand(profile));
            audioSynthesizer.triggerCue(AudioSynthesizer.EVENT_VICTORY_FANFARE);
            statusLabel.setText("Successfully established empire: " + profile.empireName() + " (" + ethics.getPrimaryIdeologySummary() + ")");
            statusLabel.setTextFill(Color.LIGHTGREEN);
        }

        if (menubar != null) {
            if (menubar.getMainApp() != null) {
                int starCount = 10;
                int aiCount = 0;
                com.spaceconquest.engine.GameStartScenario scenario = com.spaceconquest.engine.GameStartScenario.PRE_SPACE_FLIGHT;
                if (menubar.getScenarioEditorView() != null) {
                    starCount = menubar.getScenarioEditorView().getCurrentSetup().starSystemCount();
                    aiCount = menubar.getScenarioEditorView().getCurrentSetup().aiEmpireCount();
                    scenario = menubar.getScenarioEditorView().getSelectedScenario();
                }
                menubar.getMainApp().createNewGalaxy(starCount, aiCount, scenario);
            }
            menubar.setPlayerEmpireId(profile.empireId());
        }
        hide();
    }

    public VBox getRoot() {
        return root;
    }

    public void show() {
        root.setVisible(true);
        root.toFront();
        if (menubar != null) {
            menubar.openPage();
        }
    }

    public void hide() {
        root.setVisible(false);
        if (menubar != null) {
            menubar.closePage();
        }
    }
}
