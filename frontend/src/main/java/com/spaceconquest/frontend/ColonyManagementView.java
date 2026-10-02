package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.ColonizePlanetCommand;
import com.spaceconquest.control.command.EnactMartialLawCommand;
import com.spaceconquest.control.command.LaunchMassDriverPayloadCommand;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.Population;
import com.spaceconquest.engine.PopulationProcessor;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FleetLocation;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
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
 * Interactive UI panel displaying planetary colony demographics, biochemical metabolism meters,
 * workforce retirement burdens, surface mass drivers and colonization commands.
 */
public class ColonyManagementView {

    private VBox root;
    private VBox content;
    private Label feedbackLabel;
    private final Menubar menubar;
    private HumanController humanController;
    private String playerEmpireId = "terran_confederation";
    private final PopulationProcessor populationProcessor = new PopulationProcessor();
    private final List<Planet> planets = new ArrayList<>();
    private GameState snapshot;

    public ColonyManagementView(Menubar menubar) {
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
        ScrollPane scrollPane = new ScrollPane(content);

        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: rgba(14, 22, 45, 0.96); " +
                "-fx-border-color: #38ada9; -fx-border-width: 2; " +
                "-fx-border-radius: 10; -fx-background-radius: 10;");
        root.setPrefSize(920, 700);

        Text title = new Text("Colony demographics, habitation and colonization operations");
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

        feedbackLabel = new Label("Ready | Manage colonial governance or deploy colonization missions.");
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

    public void updateData(GameState state) {
        snapshot = state;
        planets.clear();
        if (state != null) state.solarSystems().forEach(system -> planets.addAll(system.planets()));

        if (root.isVisible()) {
            renderContent();
        }
    }

    private void renderContent() {
        content.getChildren().clear();

        // 1. Colonization & Martial Governance Action Workbench
        content.getChildren().add(createColonialActionWorkbench());

        // 2. Planetary Colony Demographics Section
        content.getChildren().add(createDemographicsSection());

        // 3. Surface-to-Orbit Mass Driver Section
        content.getChildren().add(createMassDriversSection());
    }

    private VBox createColonialActionWorkbench() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(12));
        section.setStyle("-fx-background-color: rgba(20, 45, 65, 0.75); -fx-background-radius: 8; -fx-border-color: #38ada9; -fx-border-width: 1; -fx-border-radius: 8;");

        Text title = new Text("Colonial operations and planetary deployment");
        title.setFill(Color.LIGHTCYAN);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 15));

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);

        Label planetLbl = new Label("Target world:");
        planetLbl.setTextFill(Color.LIGHTGREEN);
        ComboBox<String> planetCombo = new ComboBox<>();
        planetCombo.getItems().addAll("mars", "ceres", "titan", "venus", "luna", "proxima_b");
        planetCombo.setValue("mars");

        Label popLbl = new Label("Initial colonists:");
        popLbl.setTextFill(Color.LIGHTGREEN);
        Spinner<Integer> popSpinner = new Spinner<>(100, 10000, 1000, 500);
        popSpinner.setPrefWidth(100);

        Label raceLbl = new Label("Colonist species:");
        raceLbl.setTextFill(Color.LIGHTGREEN);
        ComboBox<String> raceCombo = new ComboBox<>();
        raceCombo.getItems().addAll("human", "vulkan", "silicon_core", "zephyr");
        raceCombo.setValue("human");

        Button colonizeBtn = new Button("Deploy colony ship");
        colonizeBtn.setStyle("-fx-background-color: #16a085; -fx-text-fill: white; -fx-font-weight: bold;");
        colonizeBtn.setOnAction(e -> {
            if (humanController != null) {
                humanController.stageCommand(new ColonizePlanetCommand(
                        playerEmpireId, planetCombo.getValue(), "colony_ship_alpha", popSpinner.getValue(), raceCombo.getValue()
                ));
                feedbackLabel.setText("Dispatched colonization fleet to seed " + planetCombo.getValue().toUpperCase() + " with " + popSpinner.getValue() + " " + raceCombo.getValue() + " citizens.");
                feedbackLabel.setTextFill(Color.LIGHTGREEN);
            }
        });

        Button martialLawBtn = new Button("Enact martial law (5 turns)");
        martialLawBtn.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-weight: bold;");
        martialLawBtn.setOnAction(e -> {
            if (humanController != null) {
                humanController.stageCommand(new EnactMartialLawCommand(
                        planetCombo.getValue(), playerEmpireId, 5
                ));
                feedbackLabel.setText("Enacted martial law on " + planetCombo.getValue().toUpperCase() + " to restore order.");
                feedbackLabel.setTextFill(Color.GOLD);
            }
        });

        grid.add(planetLbl, 0, 0);
        grid.add(planetCombo, 1, 0);
        grid.add(popLbl, 2, 0);
        grid.add(popSpinner, 3, 0);

        grid.add(raceLbl, 0, 1);
        grid.add(raceCombo, 1, 1);

        HBox actions = new HBox(12, colonizeBtn, martialLawBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        section.getChildren().addAll(title, grid, actions);
        return section;
    }

    private VBox createDemographicsSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: rgba(25, 40, 70, 0.7); -fx-background-radius: 8; -fx-border-color: #38ada9; -fx-border-width: 1; -fx-border-radius: 8;");

        Text header = new Text("Planetary habitation and species metabolism balances");
        header.setFill(Color.LIGHTCYAN);
        header.setFont(Font.font("Verdana", FontWeight.BOLD, 15));
        section.getChildren().add(header);

        if (planets.isEmpty()) {
            Text empty = new Text("No populated planetary colonies currently detected.");
            empty.setFill(Color.LIGHTGRAY);
            section.getChildren().add(empty);
        } else {
            for (Planet planet : planets) {
                if (planet.populations() != null && !planet.populations().isEmpty()) {
                    VBox pBox = new VBox(6);
                    pBox.setPadding(new Insets(8));
                    pBox.setStyle("-fx-background-color: rgba(15, 30, 55, 0.6); -fx-background-radius: 6;");

                    Text pTitle = new Text("Planet: " + planet.name() + " (" + planet.type() + ", Atmosphere: " + planet.atmosphere() + ")");
                    pTitle.setFill(Color.LIGHTGREEN);
                    pTitle.setFont(Font.font("Verdana", FontWeight.BOLD, 13));
                    pBox.getChildren().add(pTitle);

                    for (Population pop : planet.populations()) {
                        long totalCount = pop.ageGroups().values().stream().mapToLong(Long::longValue).sum();
                        Text popInfo = new Text(String.format("  • Species: %s | Total population: %,d citizens",
                                pop.raceId().toUpperCase(), totalCount));
                        popInfo.setFill(Color.WHITE);
                        popInfo.setFont(Font.font("Verdana", 11));
                        pBox.getChildren().add(popInfo);
                    }
                    section.getChildren().add(pBox);
                }
            }
        }

        return section;
    }

    private VBox createMassDriversSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: rgba(25, 40, 70, 0.7); -fx-background-radius: 8; -fx-border-color: #ffa502; -fx-border-width: 1; -fx-border-radius: 8;");

        Text header = new Text("Surface mass driver catapult arrays (zero-g orbital freight)");
        header.setFill(Color.GOLD);
        header.setFont(Font.font("Verdana", FontWeight.BOLD, 15));
        section.getChildren().add(header);

        List<IndustrialFacility> drivers = snapshot == null ? List.of()
                : snapshot.industrialFacilities().stream()
                .filter(facility -> "mass_driver".equals(facility.applicationId())
                        && facility.tier() > 0).toList();
        if (drivers.isEmpty()) {
            Text empty = new Text("No operational mass-driver facilities are built.");
            empty.setFill(Color.LIGHTGRAY);
            section.getChildren().add(empty);
        } else {
            for (IndustrialFacility driver : drivers) {
                VBox card = new VBox(6);
                card.setPadding(new Insets(8));
                card.setStyle("-fx-background-color: rgba(15, 30, 55, 0.6); -fx-background-radius: 6;");

                Text dInfo = new Text(String.format("%s on %s | %.0f kg/day | %.0f kW",
                        driver.id(), driver.planetId(), driver.tier() * 1_000.0,
                        driver.tier() * 250.0));
                dInfo.setFill(Color.LIGHTGREEN);
                dInfo.setFont(Font.font("Verdana", 11));
                CommercialHub hub = snapshot.commercialHubs().stream()
                        .filter(item -> driver.planetId().equals(item.entityId()))
                        .findFirst().orElse(null);
                ComboBox<String> material = new ComboBox<>();
                if (hub != null) hub.activeOrders().forEach((id, order) -> {
                    if (order.supplyKg() > 0.0) material.getItems().add(id);
                });
                material.setPromptText("Ground material");
                ComboBox<String> ship = new ComboBox<>();
                for (Fleet fleet : snapshot.fleets()) {
                    if (fleet.location().isAt(FleetLocation.Site.orbit(driver.planetId())))
                        fleet.ships().stream().filter(item -> playerEmpireId.equals(item.ownerEntityId())
                                || snapshot.corporations().stream().anyMatch(corporation ->
                                playerEmpireId.equals(corporation.empireId())
                                        && corporation.id().equals(item.ownerEntityId())))
                                .forEach(item -> ship.getItems().add(item.id()));
                }
                ship.setPromptText("Ship in orbit");
                Spinner<Integer> kilograms = new Spinner<>(1, Math.max(1, driver.tier() * 1_000), 100, 10);
                kilograms.setEditable(true);
                Button launchBtn = new Button("Launch freight");
                launchBtn.setStyle("-fx-background-color: #e67e22; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 10px;");
                launchBtn.disableProperty().bind(material.valueProperty().isNull()
                        .or(ship.valueProperty().isNull()));
                launchBtn.setOnAction(e -> {
                    if (humanController != null) {
                        humanController.stageCommand(new LaunchMassDriverPayloadCommand(
                                driver.id(), material.getValue(), kilograms.getValue() / 1000.0,
                                ship.getValue()
                        ));
                        feedbackLabel.setText("Launch queued; it will run on the next tick if stock, power and funds remain available.");
                        feedbackLabel.setTextFill(Color.LIGHTGREEN);
                    }
                });
                card.getChildren().addAll(dInfo, new HBox(8, material, ship, kilograms, launchBtn));
                section.getChildren().add(card);
            }
        }

        return section;
    }
}
