package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.DesignShipCommand;
import com.spaceconquest.engine.Material;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipDesignValidator;
import com.spaceconquest.engine.ship.ShipHullFrame;
import com.spaceconquest.engine.ship.ShipModule;
import com.spaceconquest.engine.ship.ShipRole;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
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
import java.util.Map;
import java.util.UUID;

/**
 * Interactive UI panel for custom starframe engineering, real-time physics validation and blueprint dispatching.
 */
public class ShipDesignerView {
    private VBox root;
    private VBox content;
    private ScrollPane scrollPane;
    private Label feedbackLabel;
    private final Menubar menubar;
    private HumanController humanController;
    private String playerEmpireId = "terran_confederation";
    private final ShipDesignValidator validator = new ShipDesignValidator();
    private final List<ShipDesign> registeredDesigns = new ArrayList<>();

    public ShipDesignerView(Menubar menubar) {
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
                "-fx-border-color: #00d2d3; -fx-border-width: 2; " +
                "-fx-border-radius: 10; -fx-background-radius: 10;");
        root.setPrefSize(920, 700);

        Text title = new Text("Modular starframe engineering and shipyard designer");
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

        feedbackLabel = new Label("Ready | Engineer custom starframes and validate physics constraints.");
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

    public void updateDesigns(List<ShipDesign> designs) {
        registeredDesigns.clear();
        if (designs != null) {
            registeredDesigns.addAll(designs);
        }
        if (root.isVisible()) {
            renderContent();
        }
    }

    private void renderContent() {
        content.getChildren().clear();

        // 1. Interactive Designer Preview & Form
        content.getChildren().add(createDesignerWorkbenchSection());

        // 2. Registered Blueprints Section
        content.getChildren().add(createRegisteredBlueprintsSection());
    }

    private VBox createDesignerWorkbenchSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(12));
        section.setStyle("-fx-background-color: rgba(20, 35, 60, 0.75); -fx-background-radius: 8; -fx-border-color: #00cec9; -fx-border-width: 1; -fx-border-radius: 8;");

        Text header = new Text("Interactive starframe engineering workbench");
        header.setFill(Color.LIGHTCYAN);
        header.setFont(Font.font("Verdana", FontWeight.BOLD, 16));

        TextField nameField = new TextField("Vanguard class cruiser");
        ComboBox<String> roleCombo = new ComboBox<>();
        roleCombo.getItems().addAll("COMBAT_SHIP", "CARGO_TRANSPORT", "COLONY_SHIP", "EXPLORER", "MINING_SHIP", "TROOP_TRANSPORT", "CARRIER_SHIP", "CONSTRUCTION_SHIP");
        roleCombo.setValue("COMBAT_SHIP");

        ComboBox<String> matCombo = new ComboBox<>();
        matCombo.getItems().addAll("steel", "refined_aluminum", "carbon_nanotubes", "silicon_carbide");
        matCombo.setValue("steel");

        ComboBox<String> armorCombo = new ComboBox<>();
        armorCombo.getItems().addAll("steel", "inconel_alloy", "titanium_aluminide");
        armorCombo.setValue("inconel_alloy");

        Spinner<Double> armorSpinner = new Spinner<>(0.5, 10.0, 2.0, 0.5);
        armorSpinner.setPrefWidth(80);

        GridPane grid = createWorkbenchForm(nameField, roleCombo, matCombo, armorCombo, armorSpinner);

        ShipHullFrame demoFrame = new ShipHullFrame("frame_medium", "Medium hull starframe", 20, matCombo.getValue(), 15000.0, 60.0);
        ShipModule reactor = new ShipModule("mod_fission_reactor", "Fission reactor tier 2", "MEDIUM", 4, 3000.0, 0.0, 500.0, 0.0, 2, Map.of(), Map.of());
        ShipModule thruster = new ShipModule("mod_ion_drive", "High-impulse ion drive", "MEDIUM", 6, 4500.0, 120.0, 0.0, 450000.0, 2, Map.of(), Map.of());
        ShipModule cargoVault = new ShipModule("mod_cargo_vault", "Pressurized cargo vault", "LARGE", 8, 2000.0, 30.0, 0.0, 0.0, 1, Map.of(), Map.of("cargoCapacityKg", 30000.0));
        List<ShipModule> sampleModules = List.of(reactor, thruster, cargoVault);
        Material hullMat = new Material("steel", "Steel", "Structural alloy", true, Map.of(), 7800.0, 60.0, 1);

        ShipDesignValidator.ValidationResult valRes = validator.validate(
                roleCombo.getValue(), demoFrame, sampleModules, hullMat, hullMat, armorSpinner.getValue(), 1.0, 1.0, 5
        );

        VBox statsBox = createWorkbenchStatsBox(valRes);
        Button saveBlueprintBtn = createSaveBlueprintButton(nameField, roleCombo, matCombo, armorCombo, armorSpinner, valRes);

        section.getChildren().addAll(header, grid, statsBox, saveBlueprintBtn);
        return section;
    }

    private GridPane createWorkbenchForm(TextField nameField, ComboBox<String> roleCombo,
                                        ComboBox<String> matCombo, ComboBox<String> armorCombo,
                                        Spinner<Double> armorSpinner) {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);

        Label nameLbl = new Label("Design name:");
        nameLbl.setTextFill(Color.LIGHTCYAN);
        Label roleLbl = new Label("Ship role:");
        roleLbl.setTextFill(Color.LIGHTCYAN);
        Label matLbl = new Label("Hull material:");
        matLbl.setTextFill(Color.LIGHTCYAN);
        Label armorLbl = new Label("Armor material and thickness:");
        armorLbl.setTextFill(Color.LIGHTCYAN);

        grid.add(nameLbl, 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(roleLbl, 2, 0);
        grid.add(roleCombo, 3, 0);
        grid.add(matLbl, 0, 1);
        grid.add(matCombo, 1, 1);
        grid.add(armorLbl, 2, 1);
        grid.add(new HBox(5, armorCombo, armorSpinner), 3, 1);
        return grid;
    }

    private VBox createWorkbenchStatsBox(ShipDesignValidator.ValidationResult valRes) {
        VBox statsBox = new VBox(6);
        statsBox.setPadding(new Insets(8));
        statsBox.setStyle("-fx-background-color: rgba(10, 20, 40, 0.6); -fx-background-radius: 6;");

        Text integrityLabel = new Text(String.format("Structural integrity (SI_c): %.2f (Threshold: %.2f) - %s",
                valRes.structuralIntegrity(), ShipDesignValidator.MIN_STRUCTURAL_INTEGRITY_THRESHOLD,
                valRes.structuralIntegrity() >= ShipDesignValidator.MIN_STRUCTURAL_INTEGRITY_THRESHOLD ? "PASSED" : "FAILED"));
        integrityLabel.setFill(valRes.structuralIntegrity() >= ShipDesignValidator.MIN_STRUCTURAL_INTEGRITY_THRESHOLD ? Color.LIGHTGREEN : Color.RED);

        Text powerLabel = new Text(String.format("Power balance: %.1f kW net generation", valRes.powerBalanceKw()));
        powerLabel.setFill(valRes.powerBalanceKw() >= 0 ? Color.LIGHTGREEN : Color.RED);

        Text thrustLabel = new Text(String.format("Launch propulsion: %.1f kN (Required: %.1f kN) - %s",
                valRes.totalThrustN() / 1000.0, valRes.minLaunchThrustRequiredN() / 1000.0,
                valRes.isLaunchCapable() ? "SURFACE LAUNCH READY" : "ORBITAL ONLY"));
        thrustLabel.setFill(valRes.isLaunchCapable() ? Color.LIGHTGREEN : Color.ORANGE);

        statsBox.getChildren().addAll(integrityLabel, powerLabel, thrustLabel);
        return statsBox;
    }

    private Button createSaveBlueprintButton(TextField nameField, ComboBox<String> roleCombo,
                                            ComboBox<String> matCombo, ComboBox<String> armorCombo,
                                            Spinner<Double> armorSpinner, ShipDesignValidator.ValidationResult valRes) {
        Button saveBlueprintBtn = new Button("Register blueprint design");
        saveBlueprintBtn.setStyle("-fx-background-color: #00cec9; -fx-text-fill: black; -fx-font-weight: bold;");
        saveBlueprintBtn.setOnAction(e -> {
            String id = "design_" + UUID.randomUUID().toString().substring(0, 8);
            ShipDesign newDesign = new ShipDesign(
                    id, nameField.getText(), playerEmpireId, roleCombo.getValue(), matCombo.getValue(),
                    List.of("mod_fission_reactor", "mod_ion_drive", "mod_cargo_vault"),
                    armorCombo.getValue(), armorSpinner.getValue(),
                    valRes.totalDryMassKg(), 30000.0, valRes.powerBalanceKw(),
                    valRes.structuralIntegrity(), valRes.minLaunchThrustRequiredN(),
                    valRes.totalThrustN(), valRes.isLaunchCapable(), false
            );

            if (humanController != null) {
                humanController.stageCommand(new DesignShipCommand(newDesign));
                feedbackLabel.setText("Registered new starframe blueprint: " + nameField.getText() + " [" + roleCombo.getValue() + "]");
                feedbackLabel.setTextFill(Color.LIGHTGREEN);
            }
        });
        return saveBlueprintBtn;
    }

    private VBox createRegisteredBlueprintsSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(12));
        section.setStyle("-fx-background-color: rgba(20, 35, 60, 0.7); -fx-background-radius: 8; -fx-border-color: #74b9ff; -fx-border-width: 1; -fx-border-radius: 8;");

        Text header = new Text("Galactic blueprint registry (" + registeredDesigns.size() + " blueprints)");
        header.setFill(Color.LIGHTBLUE);
        header.setFont(Font.font("Verdana", FontWeight.BOLD, 15));
        section.getChildren().add(header);

        if (registeredDesigns.isEmpty()) {
            Text emptyText = new Text("No custom or proprietary blueprints currently registered.");
            emptyText.setFill(Color.LIGHTGRAY);
            section.getChildren().add(emptyText);
        } else {
            for (ShipDesign design : registeredDesigns) {
                VBox card = new VBox(4);
                card.setPadding(new Insets(8));
                card.setStyle("-fx-background-color: rgba(15, 25, 45, 0.6); -fx-background-radius: 6;");

                HBox cardHeader = new HBox(10);
                Text nameText = new Text(design.name() + " [" + design.role() + "]");
                nameText.setFill(Color.WHITE);
                nameText.setFont(Font.font("Verdana", FontWeight.BOLD, 13));

                Text propTag = new Text(design.isProprietaryCorporateDesign() ? "(Corporate proprietary)" : "(Public blueprint)");
                propTag.setFill(design.isProprietaryCorporateDesign() ? Color.GOLD : Color.AQUA);
                propTag.setFont(Font.font("Verdana", 11));

                cardHeader.getChildren().addAll(nameText, propTag);

                Text specs = new Text(String.format("Dry mass: %.0f kg | Cargo: %.0f kg | Power: %.1f kW | SI_c: %.2f | Launch capable: %s",
                        design.totalDryMassKg(), design.maxCargoMassKg(), design.powerBalanceKw(),
                        design.calculatedStructuralIntegrity(), design.isValidForLaunch() ? "YES" : "NO"));
                specs.setFill(Color.GAINSBORO);
                specs.setFont(Font.font("Verdana", 11));

                card.getChildren().addAll(cardHeader, specs);
                section.getChildren().add(card);
            }
        }

        return section;
    }
}
