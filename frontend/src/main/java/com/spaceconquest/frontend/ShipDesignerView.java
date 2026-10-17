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
    private ComboBox<String> sensorChoice;
    private Spinner<Integer> hullCapacityChoice;
    private final java.util.Map<String, Spinner<Integer>> combatChoices = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, Spinner<Integer>> equipmentChoices = new java.util.LinkedHashMap<>();

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
        content.getChildren().add(TankerDesignCard.create(snapshot, playerEmpireId, humanController, feedbackLabel));
        content.getChildren().add(ChemicalFreighterDesignCard.create(snapshot, playerEmpireId, humanController, feedbackLabel));
        content.getChildren().add(createRegisteredBlueprintsSection());
    }

    private VBox createDesignerWorkbenchSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(12));
        section.setStyle("-fx-background-color: rgba(20, 35, 60, 0.75); -fx-background-radius: 8; -fx-border-color: #00cec9; -fx-border-width: 1; -fx-border-radius: 8;");

        Text header = new Text("Interactive starframe engineering workbench");
        header.setFill(Color.LIGHTCYAN);
        header.setFont(Font.font("Verdana", FontWeight.BOLD, 16));

        TextField nameField = new TextField("Custom ship design");
        ComboBox<String> roleCombo = new ComboBox<>();
        roleCombo.getItems().addAll(ShipComponentCatalog.ROLES.stream().sorted().toList());
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
        ComboBox<ShipPowerOption> powerCombo = new ComboBox<>();
        powerCombo.getItems().addAll(ShipPowerOption.available(unlocked));
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
        restoreEditingChoices(editingDesign, nameField, roleCombo, matCombo, armorCombo, armorSpinner, engineCombo, powerCombo, stasisPod);

        var sensorRow = createSensorChoice(editingDesign);
        var hullRow = createHullCapacityChoice(editingDesign);
        var combatRows = createCombatChoices(editingDesign);
        var equipmentRows = createEquipmentChoices(editingDesign);
        GridPane grid = createWorkbenchForm(nameField, roleCombo, matCombo, armorCombo,
                armorSpinner, engineCombo, powerCombo);

        VBox statsBox = new VBox(6);
        VBox estimateBox = new VBox(4);
        Runnable refreshStats = () -> refreshWorkbenchStats(statsBox, estimateBox, roleCombo.getValue(),
                matCombo.getValue(), armorCombo.getValue(), armorSpinner.getValue(),
                engineCombo.getValue(), powerCombo.getValue(), stasisPod.isSelected());
        refreshStats.run();
        hullCapacityChoice.valueProperty().addListener((observable, old, selected) -> refreshStats.run());
        sensorChoice.valueProperty().addListener((observable, old, selected) -> refreshStats.run());
        combatChoices.values().forEach(choice -> choice.valueProperty().addListener((observable, old, selected) -> refreshStats.run()));
        equipmentChoices.values().forEach(choice -> choice.valueProperty().addListener((observable, old, selected) -> refreshStats.run()));
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
                    grid, hullRow, stasisPod, sensorRow, equipmentRows, combatRows, statsBox, estimateBox,
                    new HBox(8, saveBlueprintBtn, cancelEdit));
        } else {
            section.getChildren().addAll(header, grid, hullRow, stasisPod, sensorRow, equipmentRows, combatRows, statsBox, estimateBox,
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
                                        ComboBox<ShipModule> engineCombo, ComboBox<ShipPowerOption> powerCombo) {
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
                                       ShipModule selectedDrive, ShipPowerOption selectedPower, boolean stasisSelected) {
        statsBox.getChildren().clear();
        estimateBox.getChildren().clear();
        if (selectedDrive == null) {
            estimateBox.getChildren().add(new Label("Research a propulsion drive to design a ship."));
            return;
        }
        var moduleIds = workbenchModules(selectedDrive.id(), stasisSelected, selectedPower);
        var specification = new ShipDesignSpecification("preview", "Preview", playerEmpireId,
                role, hullMaterialId, moduleIds, armorMaterialId, armorThickness, hullCapacityChoice.getValue(), installationIdentities(moduleIds, "preview"));
        var evaluation = ShipBlueprintFactory.evaluate(snapshot, specification);
        if (evaluation.physics() != null)
            statsBox.getChildren().setAll(createWorkbenchStatsBox(evaluation.physics()).getChildren());
        if (evaluation.physics() != null) {
            var modules = specification.moduleIds().stream().map(ShipComponentCatalog::module)
                    .filter(java.util.Objects::nonNull).map(module -> com.spaceconquest.engine.ship.ShipApplicationProduction
                            .optimize(snapshot, playerEmpireId, module)).toList();
            var power = evaluation.design() == null ? com.spaceconquest.engine.ship.ShipPowerProfile.capture(modules)
                    : evaluation.design().powerProfile();
            statsBox.getChildren().add(createPowerDemandBox(power, modules, evaluation.physics().maxCargoMassKg()));
        }
        if (!evaluation.valid()) {
            estimateBox.getChildren().add(new Label(String.join("; ", evaluation.errors())));
            return;
        }
        var manufacturing = evaluation.design().manufacturingProfile();
        ShipConstructionRequirements.Estimate estimate = ShipConstructionRequirements.estimate(evaluation.design());
        var assembly = evaluation.design().assembly();
        if (assembly != null) estimateBox.getChildren().add(new Label(String.format("Work hours — frame: %.1f; armor: %.1f; equipment: %.1f; installation: %.1f",
                assembly.frame().workHours(), assembly.armor().workHours(), assembly.installations().stream().mapToDouble(item -> item.production().workHours()).sum(), assembly.integration().workHours())));
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

    private VBox createPowerDemandBox(com.spaceconquest.engine.ship.ShipPowerProfile power,
                                     List<ShipModule> modules, double cargoCapacity) {
        double normal = power.hotelKw() + power.cargoKw() * (cargoCapacity > 0 ? 1 : .1);
        double salvo = modules.stream().mapToDouble(module -> com.spaceconquest.engine.ship.WeaponSalvo.of(module).energyKwh()
                + module.operationalStats().getOrDefault("pointDefenseCapacity", 0.0) * module.operationalStats().getOrDefault("pointDefenseShotKwh", 0.0)).sum();
        double shield = modules.stream().mapToDouble(module -> module.operationalStats().getOrDefault("shieldRechargePointsPerHour", 0.0)
                * module.operationalStats().getOrDefault("shieldRechargeKwhPerPoint", 0.0)).sum();
        double hours = com.spaceconquest.engine.combat.TacticalCombatProcessor.SECONDS_PER_ROUND / 3600;
        double combat = normal + salvo / hours + shield;
        var box = new VBox(4);
        var normalLine = new Text(String.format("Normal operations: %.1f kW (%.1f kWh/day)", normal, normal * 24));
        normalLine.setId("designer-normal-power"); normalLine.setFill(Color.LIGHTCYAN);
        var travelLine = new Text(String.format("Powered travel: %.1f kW including propulsion", normal + power.driveKw()));
        travelLine.setId("designer-travel-power"); travelLine.setFill(Color.LIGHTCYAN);
        var combatLine = new Text(String.format("Full combat: %.1f kW (%.2f kWh per %.0f-second round)", combat,
                combat * hours, com.spaceconquest.engine.combat.TacticalCombatProcessor.SECONDS_PER_ROUND));
        combatLine.setId("designer-combat-power"); combatLine.setFill(Color.LIGHTCYAN);
        var breakdown = new Text(String.format("Combat adds %.2f kWh of salvos per round and up to %.1f kW of shield recharge.", salvo, shield));
        breakdown.setFill(Color.LIGHTGRAY);
        var propulsion = new Text(String.format("Full combat with propulsion: %.1f kW", combat + power.driveKw()));
        propulsion.setFill(Color.LIGHTCYAN);
        var supply = new Text(String.format("Rated supply: %.1f kW fueled generation + %.1f kW solar at Earth flux; battery discharge up to %.1f kW.",
                power.chemicalKw() + power.fissionKw(), power.solarKw(), power.dischargeKw()));
        supply.setFill(Color.LIGHTGRAY);
        var assumptions = new Text("Healthy hardware, full preserved cargo and no passengers. Full combat includes all salvos and maximum shield recharge.");
        assumptions.setFill(Color.LIGHTGRAY); assumptions.setWrappingWidth(620);
        var limits = new Text("Passenger upkeep is additional. Sunlight and carried energy limit supply; full shields stop recharge.");
        limits.setFill(Color.LIGHTGRAY); limits.setWrappingWidth(620);
        breakdown.setWrappingWidth(620); supply.setWrappingWidth(620);
        box.getChildren().addAll(normalLine, travelLine, combatLine, breakdown, propulsion, supply, assumptions, limits);
        return box;
    }

    private Button createSaveBlueprintButton(TextField nameField, ComboBox<String> roleCombo,
                                            ComboBox<String> matCombo, ComboBox<String> armorCombo,
                                            Spinner<Double> armorSpinner, CheckBox stasisPod,
                                            ComboBox<ShipModule> engineCombo, ComboBox<ShipPowerOption> powerCombo,
                                            ShipDesign editingDesign) {
        Button saveBlueprintBtn = new Button(editingDesign == null
                ? "Register blueprint design" : "Save blueprint changes");
        saveBlueprintBtn.setStyle("-fx-background-color: #00cec9; -fx-text-fill: black; -fx-font-weight: bold;");
        saveBlueprintBtn.setOnAction(e -> {
            if (engineCombo.getValue() == null) return;
            String id = editingDesign == null ? "design_" + UUID.randomUUID().toString().substring(0, 8)
                    : editingDesign.id();
            var moduleIds = workbenchModules(engineCombo.getValue().id(), stasisPod.isSelected(), powerCombo.getValue());
            var specification = new ShipDesignSpecification(id, nameField.getText(), playerEmpireId,
                    roleCombo.getValue(), matCombo.getValue(), moduleIds,
                    armorCombo.getValue(), armorSpinner.getValue(), hullCapacityChoice.getValue(), installationIdentities(moduleIds, id));
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

    private HBox createSensorChoice(ShipDesign editing) {
        sensorChoice = new ComboBox<>();
        sensorChoice.getItems().add("none");
        var technologies = snapshot == null ? List.<String>of() : snapshot.empires().stream()
                .filter(empire -> playerEmpireId.equals(empire.id())).map(com.spaceconquest.engine.Empire::unlockedTechIds)
                .findFirst().orElse(List.of());
        com.spaceconquest.engine.ship.SensorModuleCatalog.IDS.stream()
                .filter(id -> com.spaceconquest.engine.ship.SensorModuleCatalog.researched(List.of(id), technologies))
                .forEach(sensorChoice.getItems()::add);
        sensorChoice.setConverter(new StringConverter<>() {
            @Override public String toString(String id) {
                var module = com.spaceconquest.engine.ship.SensorModuleCatalog.module(id);
                return module == null ? "No sensor module" : module.name();
            }
            @Override public String fromString(String value) { return value; }
        });
        String selected = editing == null ? "none" : editing.equippedModuleIds().stream()
                .filter(id -> com.spaceconquest.engine.ship.SensorModuleCatalog.module(id) != null).findFirst().orElse("none");
        if (!sensorChoice.getItems().contains(selected)) sensorChoice.getItems().add(selected);
        sensorChoice.setValue(selected);
        return new HBox(8, new Label("Sensors:"), sensorChoice);
    }

    private VBox createCombatChoices(ShipDesign editing) {
        combatChoices.clear(); var rows = new VBox(6);
        var technologies = snapshot == null ? List.<String>of() : snapshot.empires().stream()
                .filter(empire -> playerEmpireId.equals(empire.id())).map(com.spaceconquest.engine.Empire::unlockedTechIds)
                .findFirst().orElse(List.of());
        var hardwareIds = new ArrayList<>(com.spaceconquest.engine.ship.CombatModuleCatalog.IDS);
        hardwareIds.add(com.spaceconquest.engine.ship.ShipRepairModuleCatalog.WORKSHOP);
        hardwareIds.addAll(com.spaceconquest.engine.ship.ShipMiningModuleCatalog.ids());
        for (String id : hardwareIds) {
            int installed = editing == null ? 0 : (int) editing.equippedModuleIds().stream().filter(id::equals).count();
            if (installed == 0 && (!com.spaceconquest.engine.ship.CombatModuleCatalog.researched(List.of(id), technologies)
                    || !com.spaceconquest.engine.ship.ShipRepairModuleCatalog.researched(List.of(id), technologies)
                    || !com.spaceconquest.engine.ship.ShipMiningModuleCatalog.researched(List.of(id), technologies))) continue;
            var module = ShipComponentCatalog.module(id);
            int capacity = com.spaceconquest.engine.ship.HullTechnology.maximumCapacity(snapshot, playerEmpireId);
            var count = new Spinner<Integer>(0, Math.max(capacity / module.slotCost(), installed), installed); count.setPrefWidth(90);
            if (com.spaceconquest.engine.ship.ShipRepairModuleCatalog.WORKSHOP.equals(id))
                count.setTooltip(new javafx.scene.control.Tooltip("Repairs its host then other ships in the same fleet using host cargo. "
                        + "20 repair points per day per healthy powered workshop. Trade-assigned hosts retain their freight."));
            if (com.spaceconquest.engine.ship.CombatModuleCatalog.application(id) != null)
                count.setTooltip(new javafx.scene.control.Tooltip(com.spaceconquest.engine.ship.CombatModuleCatalog.description(
                        com.spaceconquest.engine.ship.ShipApplicationProduction.optimize(snapshot, playerEmpireId, module))));
            combatChoices.put(id, count); rows.getChildren().add(new HBox(8, new Label(module.name() + ":"), count));
        }
        return rows;
    }
    private void restoreEditingChoices(ShipDesign editing, TextField name, ComboBox<String> role, ComboBox<String> material,
                                       ComboBox<String> armor, Spinner<Double> thickness, ComboBox<ShipModule> engine,
                                       ComboBox<ShipPowerOption> power, CheckBox stasis) {
        if (editing == null) return;
        name.setText(editing.name()); role.setValue(editing.role()); material.setValue(editing.hullMaterialId()); armor.setValue(editing.armorMaterialId());
        thickness.getValueFactory().setValue(Math.clamp(editing.armorThicknessCm(), .5, 10));
        PropulsionCatalog.MAIN_DRIVE_IDS.stream().filter(editing.equippedModuleIds()::contains).map(PropulsionCatalog::module).findFirst().ifPresent(engine::setValue);
        power.getItems().stream().filter(option -> option.matches(editing.equippedModuleIds())).findFirst().ifPresent(power::setValue);
        stasis.setSelected(editing.equippedModuleIds().contains(PassengerStasis.MODULE_ID));
    }
    private VBox createEquipmentChoices(ShipDesign editing) {
        equipmentChoices.clear(); var rows = new VBox(6);
        int capacity = com.spaceconquest.engine.ship.HullTechnology.maximumCapacity(snapshot, playerEmpireId);
        for (String id : List.of("mod_cargo_vault", PropulsionCatalog.FUEL_TANK_MODULE_ID, ShipComponentCatalog.BATTERY_ID)) {
            var module = ShipComponentCatalog.module(id);
            int installed = editing == null ? 1 : (int) editing.equippedModuleIds().stream().filter(id::equals).count();
            var count = new Spinner<Integer>(0, Math.max(installed, capacity / module.slotCost()), installed); count.setPrefWidth(90);
            equipmentChoices.put(id, count); rows.getChildren().add(new HBox(8, new Label(module.name() + ":"), count));
        }
        return rows;
    }
    private HBox createHullCapacityChoice(ShipDesign editing) {
        int maximum = Math.max(1, com.spaceconquest.engine.ship.HullTechnology.maximumCapacity(snapshot, playerEmpireId));
        int selected = editing == null || editing.hullFrame() == null ? com.spaceconquest.engine.ship.HullTechnology.initialCapacity() : editing.hullFrame().totalSlots();
        hullCapacityChoice = new Spinner<Integer>(1, Math.max(maximum, selected), selected);
        hullCapacityChoice.setPrefWidth(100);
        return new HBox(8, new Label("Hull capacity:"), hullCapacityChoice, new Label("Research limit: " + maximum + " capacity units; choose any module mix that fits"));
    }
    private List<String> installationIdentities(List<String> modules, String designId) {
        var previous = registeredDesigns.stream().filter(design -> design.id().equals(editingDesignId)).findFirst().orElse(null);
        return com.spaceconquest.engine.ship.ShipBlueprintAssembly.identitiesFor(previous, modules, designId);
    }
    private List<String> workbenchModules(String drive, boolean stasis, ShipPowerOption power) {
        List<String> modules = new ArrayList<>(ShipComponentCatalog.workbenchModules(drive, stasis, power.primaryId(), power.solarSupport()));
        for (var entry : equipmentChoices.entrySet()) {
            modules.removeIf(entry.getKey()::equals);
            for (int count = 0; count < entry.getValue().getValue(); count++) modules.add(entry.getKey());
        }
        if (sensorChoice != null && com.spaceconquest.engine.ship.SensorModuleCatalog.module(sensorChoice.getValue()) != null)
            modules.add(sensorChoice.getValue());
        for (var entry : combatChoices.entrySet())
            for (int i = 0; i < entry.getValue().getValue(); i++) modules.add(entry.getKey());
        ShipDesign editing = registeredDesigns.stream().filter(design -> design.id().equals(editingDesignId)).findFirst().orElse(null);
        if (editing != null && editing.equippedModuleIds().contains(com.spaceconquest.engine.ship.ChemicalFreighterCatalog.MAIN_TANK)) {
            modules.remove("mod_cargo_vault");
            modules.remove(PropulsionCatalog.FUEL_TANK_MODULE_ID);
            modules.add(com.spaceconquest.engine.ship.ChemicalFreighterCatalog.CARGO_HOLD);
              modules.add(com.spaceconquest.engine.ship.ChemicalFreighterCatalog.MAIN_TANK);
              var ordered = new ArrayList<String>();
              editing.equippedModuleIds().stream().filter(modules::contains).forEach(ordered::add);
              modules.stream().filter(id -> !ordered.contains(id)).forEach(ordered::add);
              modules = ordered;
        }
        if (editing != null && com.spaceconquest.engine.ship.ShipSupplyCatalog.totalCapacity(editing) > 0) {
            modules.remove("mod_cargo_vault");
            editing.equippedModuleIds().stream().filter(id -> com.spaceconquest.engine.ship.ShipSupplyCatalog.STORAGE_IDS.contains(id)
                    || com.spaceconquest.engine.ship.ShipSupplyCatalog.TRANSFER_PUMP.equals(id)
                    || com.spaceconquest.engine.ship.ShipSupplyCatalog.SERVICE_HOLD.equals(id)).forEach(modules::add);
        }
        return List.copyOf(modules);
    }
}
