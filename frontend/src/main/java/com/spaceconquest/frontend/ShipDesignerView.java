package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.DesignShipCommand;
import com.spaceconquest.control.command.GameCommand;
import com.spaceconquest.control.command.CommandOutcome;
import com.spaceconquest.control.command.UpdateShipDesignCommand;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.habitation.PassengerStasis;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipDesignSpecification;
import com.spaceconquest.engine.ship.ShipBlueprintFactory;
import com.spaceconquest.engine.ship.ShipComponentCatalog;
import com.spaceconquest.engine.ship.ShipDesignValidator;
import com.spaceconquest.engine.ship.ShipConstructionRequirements;
import com.spaceconquest.engine.ship.ShipModule;
import com.spaceconquest.engine.ship.PropulsionCatalog;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
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
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
    private final List<ShipDesign> registeredDesigns = new ArrayList<>();
    private final Map<String, PendingDesign> pendingDesigns = new LinkedHashMap<>();
    private GameState snapshot;
    private String editingDesignId;

    private record PendingDesign(ShipDesign design, CompletableFuture<CommandOutcome> completion,
                                 boolean updating) {}

    public ShipDesignerView(Menubar menubar) {
        this.menubar = menubar;
    }

    public void setHumanController(HumanController controller) {
        this.humanController = controller;
    }

    public void setPlayerEmpireId(String empireId) {
        if (empireId != null && !empireId.isEmpty()) {
            this.playerEmpireId = empireId;
        }
    }

    public void initializeAfterConstruction() {
        build();
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

    public void updateData(GameState state) {
        snapshot = state;
        if (state != null) {
            List<PendingDesign> confirmed = new ArrayList<>();
            List<PendingDesign> rejected = new ArrayList<>();
            pendingDesigns.values().forEach(pending -> {
                if (!pending.completion().isDone()) return;
                if (pending.completion().isCompletedExceptionally()) rejected.add(pending);
                else if (pending.completion().getNow(null) == CommandOutcome.EXECUTED) confirmed.add(pending);
                else rejected.add(pending);
            });
            confirmed.forEach(pending -> pendingDesigns.remove(pending.design().id()));
            rejected.forEach(pending -> pendingDesigns.remove(pending.design().id()));
            if (!confirmed.isEmpty()) {
                PendingDesign latest = confirmed.getLast();
                feedbackLabel.setText(latest.updating()
                        ? "Blueprint " + latest.design().name() + " was updated."
                        : "Blueprint " + latest.design().name() + " is registered and ready to build.");
                feedbackLabel.setTextFill(Color.LIGHTGREEN);
            }
            if (!rejected.isEmpty()) {
                PendingDesign latest = rejected.getLast();
                feedbackLabel.setText(failedSubmissionMessage(latest));
                feedbackLabel.setTextFill(Color.SALMON);
            }
        }
        updateDesigns(state == null ? List.of() : state.shipDesigns());
    }

    private String failedSubmissionMessage(PendingDesign pending) {
        if (pending.completion().isCompletedExceptionally())
            return "Blueprint submission failed. " + pending.design().name() + " was not confirmed.";
        if (pending.completion().getNow(null) == CommandOutcome.CANCELLED)
            return "Blueprint submission for " + pending.design().name() + " was cancelled.";
        return pending.updating()
                ? "Changes to " + pending.design().name() + " were rejected. Check research, yard capacity and active use."
                : "Blueprint " + pending.design().name() + " was rejected. Check its technology and design settings.";
    }

    private void renderContent() {
        content.getChildren().clear();

        content.getChildren().add(createDesignerWorkbenchSection());
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
        ComboBox<ShipModule> engineCombo = moduleCombo();
        List<String> unlocked = snapshot == null ? List.of() : snapshot.empires().stream()
                .filter(empire -> empire.id().equals(playerEmpireId))
                .map(empire -> empire.unlockedTechIds()).findFirst().orElse(List.of());
        for (String moduleId : PropulsionCatalog.MAIN_DRIVE_IDS) {
            if (PropulsionCatalog.researched(List.of(moduleId), unlocked))
                engineCombo.getItems().add(PropulsionCatalog.module(moduleId));
        }
        if (!engineCombo.getItems().isEmpty()) engineCombo.setValue(engineCombo.getItems().getFirst());
        ComboBox<ShipModule> powerCombo = moduleCombo();
        ShipComponentCatalog.POWER_MODULE_IDS.stream()
                .filter(id -> ShipComponentCatalog.powerResearched(List.of(id), unlocked))
                .map(ShipComponentCatalog::module).forEach(powerCombo.getItems()::add);
        powerCombo.setValue(powerCombo.getItems().getFirst());
        boolean stasisResearched = snapshot != null && snapshot.empires().stream()
                .anyMatch(empire -> empire.id().equals(playerEmpireId)
                        && empire.unlockedTechIds().contains(PassengerStasis.TECHNOLOGY_ID));
        CheckBox stasisPod = new CheckBox("Cryogenic stasis pod");
        stasisPod.setDisable(!stasisResearched);
        stasisPod.setTextFill(Color.LIGHTCYAN);
        if (!stasisResearched) stasisPod.setText("Cryogenic stasis pod (research required)");
        ShipDesign editingDesign = registeredDesigns.stream()
                .filter(design -> design.id().equals(editingDesignId)
                        && playerEmpireId.equals(design.ownerEntityId())
                        && !design.isProprietaryCorporateDesign())
                .findFirst().orElse(null);
        if (editingDesign != null) {
            nameField.setText(editingDesign.name());
            roleCombo.setValue(editingDesign.role());
            matCombo.setValue(editingDesign.hullMaterialId());
            armorCombo.setValue(editingDesign.armorMaterialId());
            armorSpinner.getValueFactory().setValue(Math.clamp(editingDesign.armorThicknessCm(),
                    0.5, 10.0));
            PropulsionCatalog.MAIN_DRIVE_IDS.stream()
                    .filter(editingDesign.equippedModuleIds()::contains)
                    .map(PropulsionCatalog::module).findFirst().ifPresent(engineCombo::setValue);
            powerCombo.getItems().stream().filter(module -> editingDesign.equippedModuleIds().contains(module.id()))
                    .findFirst().ifPresent(powerCombo::setValue);
            stasisPod.setSelected(editingDesign.equippedModuleIds()
                    .contains(PassengerStasis.MODULE_ID));
        }

        GridPane grid = createWorkbenchForm(nameField, roleCombo, matCombo, armorCombo,
                armorSpinner, engineCombo, powerCombo);

        VBox statsBox = new VBox(6);
        VBox estimateBox = new VBox(4);
        Runnable refreshStats = () -> refreshWorkbenchStats(statsBox, estimateBox, roleCombo.getValue(),
                matCombo.getValue(), armorCombo.getValue(), armorSpinner.getValue(),
                engineCombo.getValue(), powerCombo.getValue(), stasisPod.isSelected());
        refreshStats.run();
        engineCombo.valueProperty().addListener((observable, old, selected) -> refreshStats.run());
        powerCombo.valueProperty().addListener((observable, old, selected) -> refreshStats.run());
        roleCombo.valueProperty().addListener((observable, old, selected) -> refreshStats.run());
        matCombo.valueProperty().addListener((observable, old, selected) -> refreshStats.run());
        armorCombo.valueProperty().addListener((observable, old, selected) -> refreshStats.run());
        armorSpinner.valueProperty().addListener((observable, old, selected) -> refreshStats.run());
        stasisPod.selectedProperty().addListener((observable, old, selected) -> refreshStats.run());
        Button saveBlueprintBtn = createSaveBlueprintButton(nameField, roleCombo, matCombo,
                armorCombo, armorSpinner, stasisPod, engineCombo, powerCombo, editingDesign);
        boolean inUse = editingDesign != null && snapshot != null
                && (snapshot.shipConstructionOrders().stream().anyMatch(order -> editingDesign.id().equals(order.designId()))
                || snapshot.fleets().stream().flatMap(fleet -> fleet.ships().stream())
                        .anyMatch(ship -> editingDesign.id().equals(ship.designId())));
        saveBlueprintBtn.setDisable(engineCombo.getItems().isEmpty() || inUse);
        if (inUse) section.getChildren().add(new Label("This blueprint is in use. Register a new design to change its specifications."));

        if (editingDesign != null) {
            Button cancelEdit = new Button("Cancel editing");
            cancelEdit.setOnAction(event -> {
                editingDesignId = null;
                renderContent();
            });
            section.getChildren().addAll(header, new Label("Editing: " + editingDesign.name()),
                    grid, stasisPod, statsBox, estimateBox,
                    new HBox(8, saveBlueprintBtn, cancelEdit));
        } else {
            section.getChildren().addAll(header, grid, stasisPod, statsBox, estimateBox,
                    saveBlueprintBtn);
        }
        return section;
    }

    private static ComboBox<ShipModule> moduleCombo() {
        ComboBox<ShipModule> combo = new ComboBox<>();
        combo.setConverter(new StringConverter<>() {
            @Override public String toString(ShipModule module) {
                return module == null ? "" : module.name();
            }
            @Override public ShipModule fromString(String value) { return null; }
        });
        return combo;
    }

    private GridPane createWorkbenchForm(TextField nameField, ComboBox<String> roleCombo,
                                        ComboBox<String> matCombo, ComboBox<String> armorCombo,
                                        Spinner<Double> armorSpinner,
                                        ComboBox<ShipModule> engineCombo, ComboBox<ShipModule> powerCombo) {
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
        Label engineLbl = new Label("Propulsion drive:");
        engineLbl.setTextFill(Color.LIGHTCYAN);

        grid.add(nameLbl, 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(roleLbl, 2, 0);
        grid.add(roleCombo, 3, 0);
        grid.add(matLbl, 0, 1);
        grid.add(matCombo, 1, 1);
        grid.add(armorLbl, 2, 1);
        grid.add(new HBox(5, armorCombo, armorSpinner), 3, 1);
        grid.add(engineLbl, 0, 2);
        grid.add(engineCombo, 1, 2);
        Label powerLbl = new Label("Electrical power:");
        powerLbl.setTextFill(Color.LIGHTCYAN);
        grid.add(powerLbl, 2, 2);
        grid.add(powerCombo, 3, 2);
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

    private void refreshWorkbenchStats(VBox statsBox, VBox estimateBox, String role,
                                       String hullMaterialId,
                                       String armorMaterialId, double armorThickness,
                                       ShipModule selectedDrive, ShipModule selectedPower, boolean stasisSelected) {
        statsBox.getChildren().clear();
        estimateBox.getChildren().clear();
        if (selectedDrive == null) {
            estimateBox.getChildren().add(new Label("Research a propulsion drive to design a ship."));
            return;
        }
        var specification = new ShipDesignSpecification("preview", "Preview", playerEmpireId,
                role, hullMaterialId, ShipComponentCatalog.workbenchModules(selectedDrive.id(), stasisSelected, selectedPower.id()),
                armorMaterialId, armorThickness);
        var evaluation = ShipBlueprintFactory.evaluate(snapshot, specification);
        if (evaluation.physics() != null)
            statsBox.getChildren().setAll(createWorkbenchStatsBox(evaluation.physics()).getChildren());
        if (!evaluation.valid()) {
            estimateBox.getChildren().add(new Label(String.join("; ", evaluation.errors())));
            return;
        }
        var manufacturing = evaluation.design().manufacturingProfile();
        ShipConstructionRequirements.Estimate estimate = ShipConstructionRequirements.estimate(evaluation.design());
        long minimumTurns = (long) Math.ceil(estimate.workUnits() / 100.0);
        List<javafx.scene.Node> estimateLines = new ArrayList<>();
        Text estimateHeader = new Text("Estimated production requirements");
        estimateHeader.setFill(Color.LIGHTBLUE);
        estimateHeader.setFont(Font.font("Verdana", FontWeight.BOLD, 12));
        estimateLines.add(estimateHeader);
        Text work = new Text(String.format("Production work: %.0f standardized shipyard labor-hours (~%d turns minimum)",
                estimate.workUnits(), minimumTurns));
        work.setFill(Color.LIGHTCYAN);
        estimateLines.add(work);
        if (manufacturing.stasisCapacity() != null && manufacturing.stasisCapacity() > 0) {
            Text stasis = new Text("Stasis capacity: " + manufacturing.stasisCapacity() + " passengers");
            stasis.setFill(Color.LIGHTCYAN);
            estimateLines.add(stasis);
        }
        estimate.materialsKg().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    Text material = new Text(String.format("%s: %.0f kg",
                            entry.getKey().replace('_', ' '), entry.getValue()));
                    material.setFill(Color.GAINSBORO);
                    estimateLines.add(material);
                });
        Text caveat = new Text("Actual completion can take longer if the yard lacks materials or work capacity.");
        caveat.setFill(Color.LIGHTGRAY);
        estimateLines.add(caveat);
        estimateBox.getChildren().setAll(estimateLines);
    }

    private Button createSaveBlueprintButton(TextField nameField, ComboBox<String> roleCombo,
                                            ComboBox<String> matCombo, ComboBox<String> armorCombo,
                                            Spinner<Double> armorSpinner, CheckBox stasisPod,
                                            ComboBox<ShipModule> engineCombo, ComboBox<ShipModule> powerCombo,
                                            ShipDesign editingDesign) {
        Button saveBlueprintBtn = new Button(editingDesign == null
                ? "Register blueprint design" : "Save blueprint changes");
        saveBlueprintBtn.setStyle("-fx-background-color: #00cec9; -fx-text-fill: black; -fx-font-weight: bold;");
        saveBlueprintBtn.setOnAction(e -> {
            if (engineCombo.getValue() == null) return;
            String id = editingDesign == null ? "design_" + UUID.randomUUID().toString().substring(0, 8)
                    : editingDesign.id();
            var specification = new ShipDesignSpecification(id, nameField.getText(), playerEmpireId,
                    roleCombo.getValue(), matCombo.getValue(),
                    ShipComponentCatalog.workbenchModules(engineCombo.getValue().id(), stasisPod.isSelected(), powerCombo.getValue().id()),
                    armorCombo.getValue(), armorSpinner.getValue());
            var evaluation = ShipBlueprintFactory.evaluate(snapshot, specification);
            if (!evaluation.valid()) {
                feedbackLabel.setText("Blueprint invalid: " + String.join(", ", evaluation.errors()));
                feedbackLabel.setTextFill(Color.SALMON);
                return;
            }
            ShipDesign newDesign = evaluation.design();

            if (humanController != null) {
                GameCommand command;
                boolean updating = editingDesign != null;
                if (!updating) {
                    command = new DesignShipCommand(specification);
                } else {
                    command = new UpdateShipDesignCommand(specification);
                    editingDesignId = null;
                }
                feedbackLabel.setText(updating
                        ? "Blueprint changes queued for the next simulation tick."
                        : "Blueprint queued for the next simulation tick.");
                pendingDesigns.put(newDesign.id(), new PendingDesign(newDesign,
                        humanController.stageTrackedCommand(command), updating));
                feedbackLabel.setTextFill(Color.LIGHTGREEN);
                renderContent();
            } else {
                feedbackLabel.setText("The command queue is unavailable. Blueprint changes were not submitted.");
                feedbackLabel.setTextFill(Color.SALMON);
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

        if (!pendingDesigns.isEmpty()) {
            Text pendingHeader = new Text("Pending changes");
            pendingHeader.setFill(Color.LIGHTYELLOW);
            pendingHeader.setFont(Font.font("Verdana", FontWeight.BOLD, 12));
            section.getChildren().add(pendingHeader);
            for (PendingDesign pending : pendingDesigns.values()) {
                Text pendingText = new Text(pending.design().name() + " ["
                        + pending.design().role() + "] — pending registration");
                pendingText.setFill(Color.LIGHTYELLOW);
                pendingText.setFont(Font.font("Verdana", 11));
                section.getChildren().add(pendingText);
            }
        }

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
                if (playerEmpireId.equals(design.ownerEntityId())
                        && !design.isProprietaryCorporateDesign()) {
                    Button editButton = new Button("Edit design");
                    editButton.setOnAction(event -> {
                        editingDesignId = design.id();
                        renderContent();
                        scrollPane.setVvalue(0.0);
                    });
                    card.getChildren().add(editButton);
                }
                section.getChildren().add(card);
            }
        }

        return section;
    }
}
