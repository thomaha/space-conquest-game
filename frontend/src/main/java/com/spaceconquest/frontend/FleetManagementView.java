package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.MoveFleetCommand;
import com.spaceconquest.control.command.MoveFleetLocalCommand;
import com.spaceconquest.control.command.QueueShipBuildCommand;
import com.spaceconquest.control.command.LoadOrbitalCargoCommand;
import com.spaceconquest.control.command.LoadSurfaceCargoCommand;
import com.spaceconquest.control.command.LoadPassengersCommand;
import com.spaceconquest.control.command.RefuelShipCommand;
import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.Population;
import com.spaceconquest.engine.industry.ConstructionMaterials;
import com.spaceconquest.engine.habitation.PassengerStasis;
import com.spaceconquest.control.command.SetFleetStanceCommand;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.ShipConstructionOrder;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FleetLocation;
import com.spaceconquest.engine.ship.ShipInstance;
import com.spaceconquest.engine.ship.InterstellarTravel;
import com.spaceconquest.engine.ship.PropulsionCatalog;
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
 * Interactive UI panel for monitoring fleet dispositions, warp transits, fuel reserves, shipyard commissioning and fleet stances.
 */
public class FleetManagementView {
    private VBox root;
    private VBox content;
    private ScrollPane scrollPane;
    private Label feedbackLabel;
    private final Menubar menubar;
    private HumanController humanController;
    private String playerEmpireId = "terran_confederation";
    private final List<Fleet> activeFleets = new ArrayList<>();
    private final List<ShipConstructionOrder> constructionOrders = new ArrayList<>();
    private final List<String> buildSystemIds = new ArrayList<>();
    private final List<String> buildDesignIds = new ArrayList<>();
    private GameState snapshot;

    public FleetManagementView(Menubar menubar) {
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
                "-fx-border-color: #e17055; -fx-border-width: 2; " +
                "-fx-border-radius: 10; -fx-background-radius: 10;");
        root.setPrefSize(920, 700);

        Text title = new Text("Imperial fleet command and naval operations");
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

        feedbackLabel = new Label("Ready | Dispatch fleet movement orders or queue starship construction.");
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

    public void updateFleets(List<Fleet> fleets) {
        activeFleets.clear();
        if (fleets != null) {
            activeFleets.addAll(fleets);
        }
        if (root.isVisible()) {
            renderContent();
        }
    }

    public void updateData(GameState state) {
        if (state == null) return;
        snapshot = state;
        activeFleets.clear();
        activeFleets.addAll(state.fleets());
        constructionOrders.clear();
        constructionOrders.addAll(state.shipConstructionOrders().stream()
                .filter(order -> playerEmpireId.equals(order.ownerEntityId())).toList());
        buildSystemIds.clear();
        state.empires().stream().filter(empire -> playerEmpireId.equals(empire.id()))
                .findFirst().ifPresent(empire -> buildSystemIds.addAll(empire.controlledSystemIds()));
        buildDesignIds.clear();
        buildDesignIds.addAll(state.shipDesigns().stream()
                .filter(design -> playerEmpireId.equals(design.ownerEntityId())
                        && !design.isProprietaryCorporateDesign())
                .map(ShipDesign::id).toList());
        if (root.isVisible()) renderContent();
    }

    private void renderContent() {
        content.getChildren().clear();

        // 1. Shipyard Construction Queue Section
        content.getChildren().add(createShipyardQueueSection());
        content.getChildren().add(createOrbitalCargoSection());

        // 2. Active Fleets Section
        content.getChildren().add(createActiveFleetsSection());
    }

    private VBox createShipyardQueueSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(12));
        section.setStyle("-fx-background-color: rgba(25, 45, 75, 0.75); -fx-background-radius: 8; -fx-border-color: #e17055; -fx-border-width: 1; -fx-border-radius: 8;");

        Text title = new Text("Ship construction");
        title.setFill(Color.LIGHTCORAL);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 15));

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);

        Label sysLbl = new Label("Shipyard system:");
        sysLbl.setTextFill(Color.LIGHTCYAN);
        ComboBox<String> sysCombo = new ComboBox<>();
        sysCombo.getItems().addAll(buildSystemIds);
        if (!buildSystemIds.isEmpty()) sysCombo.setValue(buildSystemIds.getFirst());

        Label designLbl = new Label("Blueprint design:");
        designLbl.setTextFill(Color.LIGHTCYAN);
        ComboBox<String> designCombo = new ComboBox<>();
        designCombo.getItems().addAll(buildDesignIds);
        if (!buildDesignIds.isEmpty()) designCombo.setValue(buildDesignIds.getFirst());

        Button queueBtn = new Button("Queue ship construction");
        queueBtn.setDisable(buildSystemIds.isEmpty() || buildDesignIds.isEmpty());
        queueBtn.setStyle("-fx-background-color: #e17055; -fx-text-fill: white; -fx-font-weight: bold;");
        queueBtn.setOnAction(e -> {
            if (humanController != null) {
                humanController.stageCommand(new QueueShipBuildCommand(
                        playerEmpireId, designCombo.getValue(), sysCombo.getValue()
                ));
                feedbackLabel.setText("Queued ship construction for " + designCombo.getValue() + " in " + sysCombo.getValue().toUpperCase());
                feedbackLabel.setTextFill(Color.LIGHTGREEN);
            }
        });

        grid.add(sysLbl, 0, 0);
        grid.add(sysCombo, 1, 0);
        grid.add(designLbl, 2, 0);
        grid.add(designCombo, 3, 0);

        section.getChildren().addAll(title, grid, queueBtn);
        if (constructionOrders.isEmpty()) {
            section.getChildren().add(new Label("No ship construction orders in progress."));
        } else {
            for (ShipConstructionOrder order : constructionOrders) {
                double work = order.requiredWorkHours() <= 0.0 ? 100.0
                        : 100.0 * order.accumulatedWorkHours() / order.requiredWorkHours();
                double required = order.requiredMaterialsKg().values().stream()
                        .mapToDouble(Double::doubleValue).sum();
                double consumed = order.consumedMaterialsKg().values().stream()
                        .mapToDouble(Double::doubleValue).sum();
                double materials = required <= 0.0 ? 100.0 : 100.0 * consumed / required;
                Label progress = new Label(String.format("%s in %s — work %.0f%%, materials %.0f%%",
                        order.designId(), order.systemId(), work, materials));
                progress.setTextFill(Color.LIGHTCYAN);
                section.getChildren().add(progress);
            }
        }
        return section;
    }

    private VBox createOrbitalCargoSection() {
        VBox section = new VBox(8);
        section.setPadding(new Insets(12));
        section.setStyle("-fx-background-color: rgba(25, 45, 75, 0.75); -fx-background-radius: 8;");
        Label title = new Label("Buy supplies and construction materials for a ship");
        title.setTextFill(Color.LIGHTCYAN);
        ComboBox<String> shipCombo = new ComboBox<>();
        ComboBox<String> bodyCombo = new ComboBox<>();
        ComboBox<String> materialCombo = new ComboBox<>();
        ComboBox<String> reactorFuelCombo = new ComboBox<>();
        Spinner<Integer> quantity = new Spinner<>(1, 100_000, 100, 100);
        quantity.setEditable(true);
        quantity.setPrefWidth(110);
        if (snapshot != null) {
            snapshot.fleets().stream().filter(fleet -> playerEmpireId.equals(fleet.ownerEntityId())
                    && !fleet.hasInterstellarOrder() && !fleet.location().inTransit())
                    .flatMap(fleet -> fleet.ships().stream())
                    .map(ShipInstance::id).forEach(shipCombo.getItems()::add);
        }
        bodyCombo.valueProperty().addListener((observable, old, body) ->
                populateMaterialChoices(body, materialCombo));
        shipCombo.valueProperty().addListener((observable, old, ship) -> {
            populateBodyChoices(ship, bodyCombo);
            populateReactorFuelChoices(ship, reactorFuelCombo);
        });
        if (!shipCombo.getItems().isEmpty()) shipCombo.setValue(shipCombo.getItems().getFirst());
        Button load = new Button("Buy and load cargo");
        load.setDisable(shipCombo.getItems().isEmpty());
        load.setOnAction(event -> {
            if (humanController == null || snapshot == null || materialCombo.getValue() == null) return;
            boolean surface = snapshot.fleets().stream().anyMatch(fleet ->
                    fleet.location().isAt(FleetLocation.Site.surface(bodyCombo.getValue()))
                            && fleet.ships().stream().anyMatch(ship ->
                            ship.id().equals(shipCombo.getValue())));
            com.spaceconquest.control.command.GameCommand command = surface
                    ? new LoadSurfaceCargoCommand(shipCombo.getValue(), bodyCombo.getValue(),
                    materialCombo.getValue(), quantity.getValue())
                    : new LoadOrbitalCargoCommand(shipCombo.getValue(), bodyCombo.getValue(),
                    materialCombo.getValue(), quantity.getValue());
            if (!command.validate(snapshot)) {
                feedbackLabel.setText("Cargo unavailable, unaffordable or over transport capacity.");
                feedbackLabel.setTextFill(Color.SALMON);
                return;
            }
            humanController.stageCommand(command);
            feedbackLabel.setText("Queued loading of " + quantity.getValue() + " kg "
                    + materialCombo.getValue() + " to " + shipCombo.getValue());
            feedbackLabel.setTextFill(Color.LIGHTGREEN);
        });
        Button refuel = new Button("Buy and refuel");
        refuel.setDisable(shipCombo.getItems().isEmpty());
        refuel.setOnAction(event -> refuelShip(shipCombo.getValue(), bodyCombo.getValue(),
                quantity.getValue(), reactorFuelCombo.getValue()));
        section.getChildren().addAll(title, new HBox(8, new Label("Ship:"), shipCombo,
                new Label("Surface hub:"), bodyCombo, new Label("Material:"), materialCombo,
                new Label("kg:"), quantity, load),
                new HBox(8, new Label("Reactor fuel:"), reactorFuelCombo, refuel));
        return section;
    }

    private void populateReactorFuelChoices(String shipId, ComboBox<String> choices) {
        choices.getItems().clear();
        if (snapshot == null || shipId == null) return;
        snapshot.fleets().stream().flatMap(fleet -> fleet.ships().stream())
                .filter(ship -> shipId.equals(ship.id())).findFirst()
                .flatMap(ship -> snapshot.shipDesigns().stream()
                        .filter(design -> design.id().equals(ship.designId())).findFirst())
                .map(design -> PropulsionCatalog.mainDrive(design.equippedModuleIds()))
                .ifPresent(drive -> PropulsionCatalog.reactorFuels(drive.moduleId()).stream()
                        .map(PropulsionCatalog.ReactorFuel::materialId)
                        .forEach(choices.getItems()::add));
        if (!choices.getItems().isEmpty()) choices.setValue(choices.getItems().getFirst());
    }

    private void refuelShip(String shipId, String bodyId, double quantityKg,
                            String reactorFuelId) {
        if (humanController == null || snapshot == null) return;
        RefuelShipCommand command = new RefuelShipCommand(shipId, bodyId,
                quantityKg, reactorFuelId);
        if (!command.validate(snapshot)) {
            String stationHub = snapshot.fleets().stream()
                    .filter(fleet -> fleet.ships().stream().anyMatch(ship -> shipId.equals(ship.id())))
                    .map(fleet -> fleet.location().current())
                    .filter(site -> site.kind() == FleetLocation.Kind.DOCKED)
                    .map(FleetLocation.Site::entityId)
                    .filter(id -> snapshot.commercialHubs().stream()
                            .anyMatch(hub -> id.equals(hub.entityId())))
                    .findFirst().orElse(null);
            if (stationHub != null)
                command = new RefuelShipCommand(shipId, stationHub,
                        quantityKg, reactorFuelId);
        }
        if (!command.validate(snapshot)) {
            feedbackLabel.setText("Fuel unavailable, unaffordable or beyond tank capacity.");
            feedbackLabel.setTextFill(Color.SALMON);
            return;
        }
        humanController.stageCommand(command);
        feedbackLabel.setText(String.format("Queued %.0f kg of propellant for %s.",
                quantityKg, shipId));
        feedbackLabel.setTextFill(Color.LIGHTGREEN);
    }

    private void populateMaterialChoices(String body, ComboBox<String> materialCombo) {
        materialCombo.getItems().clear();
        if (snapshot == null || body == null) return;
        snapshot.commercialHubs().stream().filter(hub -> body.equals(hub.entityId()))
                .findFirst().ifPresent(hub -> hub.activeOrders().values().stream()
                .filter(order -> order.supplyKg() > 0.0).map(order -> order.resourceId())
                .sorted().forEach(materialCombo.getItems()::add));
        if (!materialCombo.getItems().isEmpty())
            materialCombo.setValue(materialCombo.getItems().getFirst());
    }

    private void populateBodyChoices(String shipId, ComboBox<String> bodyCombo) {
        bodyCombo.getItems().clear();
        if (snapshot == null || shipId == null) return;
        Fleet fleet = snapshot.fleets().stream().filter(item -> item.ships().stream()
                .anyMatch(ship -> shipId.equals(ship.id()))).findFirst().orElse(null);
        String systemId = fleet == null ? null : fleet.currentSystemId();
        snapshot.commercialHubs().stream().map(CommercialHub::entityId)
                .filter(body -> systemId != null && systemId.equals(
                        ConstructionMaterials.systemForBody(snapshot, body)))
                .filter(body -> fleet.location().isAt(FleetLocation.Site.surface(body))
                        || fleet.location().isAt(FleetLocation.Site.orbit(body))
                        || snapshot.orbitalStations().stream().anyMatch(station ->
                        body.equals(station.planetOrbitId())
                                && fleet.location().isAt(FleetLocation.Site.docked(station.id()))))
                .distinct().forEach(bodyCombo.getItems()::add);
        if (!bodyCombo.getItems().isEmpty()) bodyCombo.setValue(bodyCombo.getItems().getFirst());
    }

    private VBox createActiveFleetsSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: rgba(20, 35, 60, 0.7); -fx-background-radius: 8;");

        Text summary = new Text("Active fleets under command: " + activeFleets.size());
        summary.setFill(Color.LIGHTSKYBLUE);
        summary.setFont(Font.font("Verdana", FontWeight.BOLD, 15));
        section.getChildren().add(summary);

        if (activeFleets.isEmpty()) {
            Text empty = new Text("No active military, cargo or mining fleets currently deployed.");
            empty.setFill(Color.LIGHTGRAY);
            section.getChildren().add(empty);
        } else {
            for (Fleet fleet : activeFleets) {
                section.getChildren().add(createFleetCard(fleet));
            }
        }

        return section;
    }

    private VBox createFleetCard(Fleet fleet) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(10));
        card.setStyle("-fx-background-color: rgba(30, 45, 75, 0.7); -fx-background-radius: 8; -fx-border-color: #fab1a0; -fx-border-width: 1; -fx-border-radius: 8;");

        HBox topRow = new HBox(12);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Text nameText = new Text(fleet.name() + " (" + fleet.ships().size() + " ships)");
        nameText.setFill(Color.WHITE);
        nameText.setFont(Font.font("Verdana", FontWeight.BOLD, 14));

        Text locationText = new Text(locationLabel(fleet));
        locationText.setFill(fleet.isInterstellarTransit() ? Color.GOLD : Color.LIGHTGREEN);
        locationText.setFont(Font.font("Verdana", 12));

        topRow.getChildren().addAll(nameText, locationText);

        HBox orderRow = new HBox(10);
        orderRow.setAlignment(Pos.CENTER_LEFT);

        ComboBox<String> targetCombo = new ComboBox<>();
        if (snapshot != null) snapshot.solarSystems().stream().map(system -> system.id())
                .filter(id -> !id.equals(fleet.currentSystemId()))
                .forEach(targetCombo.getItems()::add);
        if (!targetCombo.getItems().isEmpty()) targetCombo.setValue(targetCombo.getItems().getFirst());

        Button moveBtn = new Button("Move fleet");
        moveBtn.setDisable(targetCombo.getItems().isEmpty() || fleet.hasInterstellarOrder()
                || fleet.location().inTransit());
        moveBtn.setStyle("-fx-background-color: #0984e3; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px;");
        moveBtn.setOnAction(e -> {
            if (humanController != null && snapshot != null) {
                MoveFleetCommand command = new MoveFleetCommand(fleet.id(), targetCombo.getValue());
                if (!command.validate(snapshot)) {
                    feedbackLabel.setText("Departure unavailable. Check the fleet order and onboard passenger supplies.");
                    feedbackLabel.setTextFill(Color.SALMON);
                    return;
                }
                humanController.stageCommand(command);
                feedbackLabel.setText("Queued interstellar departure of " + fleet.name()
                        + " to " + targetCombo.getValue().toUpperCase());
                feedbackLabel.setTextFill(Color.LIGHTGREEN);
            }
        });

        ComboBox<String> stanceCombo = new ComboBox<>();
        stanceCombo.getItems().addAll("PASSIVE", "AGGRESSIVE", "PATROL", "ESCORT");
        stanceCombo.setValue(fleet.fleetStance() != null ? fleet.fleetStance() : "PASSIVE");

        Button stanceBtn = new Button("Set stance");
        stanceBtn.setStyle("-fx-background-color: #00b894; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px;");
        stanceBtn.setOnAction(e -> {
            if (humanController != null) {
                humanController.stageCommand(new SetFleetStanceCommand(
                        fleet.id(), stanceCombo.getValue()
                ));
                feedbackLabel.setText("Updated operational stance for fleet " + fleet.name() + " to " + stanceCombo.getValue());
                feedbackLabel.setTextFill(Color.LIGHTGREEN);
            }
        });

        orderRow.getChildren().addAll(new Label("Travel to:"), targetCombo, moveBtn, new Label("Stance:"), stanceCombo, stanceBtn);

        VBox shipList = new VBox(4);
        shipList.setPadding(new Insets(4, 0, 0, 10));
        for (ShipInstance ship : fleet.ships()) {
            Text shipText = new Text(String.format("• Ship [%s] Design: %s | Hull: %.0f HP | Shield: %.0f HP | Fuel: %.1f kg",
                    ship.id(), ship.designId(), ship.currentHullHealth(), ship.currentShieldHealth(), ship.currentFuelKg()));
            shipText.setFill(Color.GAINSBORO);
            shipText.setFont(Font.font("Verdana", 11));
            shipList.getChildren().add(shipText);
            ShipDesign design = snapshot == null ? null : snapshot.shipDesigns().stream()
                    .filter(item -> item.id().equals(ship.designId())).findFirst().orElse(null);
            PropulsionCatalog.Drive drive = design == null ? null
                    : PropulsionCatalog.mainDrive(design.equippedModuleIds());
            if (drive != null) {
                Label fuelDetail = new Label(String.format("Tank: %.0f/%.0f kg %s",
                        ship.currentFuelKg(), design.fuelCapacityKg(), drive.propellantLabel()));
                fuelDetail.setTextFill(Color.LIGHTCYAN);
                shipList.getChildren().add(fuelDetail);
                for (PropulsionCatalog.ReactorFuel reactorFuel :
                        PropulsionCatalog.reactorFuels(drive.moduleId())) {
                    if (reactorFuel.kgPerPropellantKg() <= 0.0) continue;
                    double stocked = ship.storedCargoKg().getOrDefault(
                            reactorFuel.materialId(), 0.0);
                    if (stocked <= 0.0) continue;
                    Label reserve = new Label(String.format("Reactor fuel: %.3f kg %s",
                            stocked, reactorFuel.materialId()));
                    reserve.setTextFill(Color.LIGHTCYAN);
                    shipList.getChildren().add(reserve);
                }
            }
            if (ship.passengerCount() > 0) {
                Label passengers = new Label("Passengers: " + ship.passengerCount() + " "
                        + ship.passengerRaceId() + " | " + ship.transitMode()
                        + " | Supplies: " + ship.storedCargoKg());
                passengers.setTextFill(Color.LIGHTCYAN);
                passengers.setWrapText(true);
                shipList.getChildren().add(passengers);
            }
        }

        card.getChildren().addAll(topRow, orderRow, createLocalOrders(fleet),
                createPassengerOrders(fleet), shipList);
        return card;
    }

    private String locationLabel(Fleet fleet) {
        if (fleet.isInterstellarTransit()) {
            if (Fleet.MODE_SUBLIGHT.equals(fleet.interstellarMode())
                    && fleet.interstellarDistanceMeters() > 0.0) {
                double speedKmS = InterstellarTravel.velocityMps(
                        fleet.interstellarDistanceMeters(), fleet.interstellarAccelerationMps2(),
                        fleet.interstellarPeakSpeedMps() > 0.0
                                ? fleet.interstellarPeakSpeedMps() : InterstellarTravel.MAX_CRUISE_MPS,
                        fleet.interstellarElapsedDays() * InterstellarTravel.SECONDS_PER_DAY) / 1000.0;
                return String.format("Sublight to %s: %.1f%% | %.0f km/s | %.0f days remaining",
                        fleet.targetSystemId(), fleet.transitProgress() * 100.0,
                        speedKmS, Math.max(0.0,
                                Math.ceil(fleet.interstellarTravelDays() - fleet.interstellarElapsedDays())));
            }
            return String.format("Warp to %s: %.0f%% (%.0f days total)",
                    fleet.targetSystemId(), fleet.transitProgress() * 100.0,
                    fleet.interstellarTravelDays());
        }
        FleetLocation location = fleet.location();
        String current = location.current().kind() + " " + location.current().entityId();
        if (location.inTransit()) return String.format("%s → %s %s: %.0f%%",
                current, location.destination().kind(), location.destination().entityId(),
                location.progress() * 100.0);
        if (fleet.targetSystemId() != null && !fleet.targetSystemId().isBlank())
            return current + " → departure for " + fleet.targetSystemId();
        return fleet.currentSystemId() + ": " + current;
    }

    private HBox createLocalOrders(Fleet fleet) {
        ComboBox<FleetLocation.Site> sites = new ComboBox<>();
        if (snapshot != null) {
            snapshot.solarSystems().stream().filter(system ->
                    system.id().equals(fleet.currentSystemId())).findFirst().ifPresent(system ->
                    system.planets().forEach(planet -> {
                        sites.getItems().add(FleetLocation.Site.surface(planet.id()));
                        sites.getItems().add(FleetLocation.Site.orbit(planet.id()));
                        planet.moons().forEach(moon -> {
                            sites.getItems().add(FleetLocation.Site.surface(moon.id()));
                            sites.getItems().add(FleetLocation.Site.orbit(moon.id()));
                        });
                    }));
            snapshot.orbitalStations().stream().filter(station ->
                    station.systemId().equals(fleet.currentSystemId()))
                    .forEach(station -> sites.getItems().add(FleetLocation.Site.docked(station.id())));
        }
        sites.getItems().add(FleetLocation.Site.deepSpace());
        sites.getItems().add(FleetLocation.Site.deepSpace(fleet.currentSystemId() + "_star"));
        if (snapshot != null) snapshot.megastructures().stream().filter(mega ->
                fleet.currentSystemId().equals(mega.systemId()))
                .map(mega -> FleetLocation.Site.deepSpace(mega.targetCelestialId()))
                .filter(site -> !sites.getItems().contains(site))
                .forEach(sites.getItems()::add);
        if (!sites.getItems().isEmpty()) sites.setValue(sites.getItems().getFirst());
        Button move = new Button("Move locally");
        move.setDisable(fleet.isInWarp() || fleet.location().inTransit()
                || fleet.targetSystemId() != null && !fleet.targetSystemId().isBlank());
        move.setOnAction(event -> {
            if (humanController == null || sites.getValue() == null) return;
            FleetLocation.Site site = sites.getValue();
            humanController.stageCommand(new MoveFleetLocalCommand(fleet.id(), site.kind(),
                    site.entityId()));
            feedbackLabel.setText("Queued local movement to " + site.kind() + " " + site.entityId());
            feedbackLabel.setTextFill(Color.LIGHTGREEN);
        });
        return new HBox(8, new Label("Local destination:"), sites, move);
    }

    private HBox createPassengerOrders(Fleet fleet) {
        HBox row = new HBox(8);
        if (snapshot == null || fleet.location().inTransit() || fleet.hasInterstellarOrder()
                || fleet.location().current().kind() != FleetLocation.Kind.SURFACE) return row;
        String source = fleet.location().current().entityId();
        ComboBox<String> ships = new ComboBox<>();
        fleet.ships().stream().filter(ship -> ship.passengerCount() == 0)
                .forEach(ship -> ships.getItems().add(ship.id()));
        ships.setPromptText("Passenger ship");
        ComboBox<String> races = new ComboBox<>();
        snapshot.solarSystems().forEach(system -> system.planets().forEach(planet -> {
            List<Population> sourceGroups = source.equals(planet.id()) ? planet.populations()
                    : planet.moons().stream().filter(moon -> source.equals(moon.id()))
                    .flatMap(moon -> moon.populations().stream()).toList();
            sourceGroups.stream().filter(group -> group.totalCount() > 0)
                    .forEach(group -> races.getItems().add(group.raceId()));
        }));
        races.setPromptText("Residents");
        ComboBox<String> destinations = new ComboBox<>();
        snapshot.solarSystems().forEach(system -> system.planets().forEach(planet -> {
            if (!source.equals(planet.id())) destinations.getItems().add(planet.id());
            planet.moons().forEach(moon -> {
                if (!source.equals(moon.id())) destinations.getItems().add(moon.id());
            });
        }));
        destinations.setPromptText("Offworld destination");
        Spinner<Integer> count = new Spinner<>(1, 100_000, 1, 1);
        count.setEditable(true);
        ComboBox<String> mode = new ComboBox<>();
        mode.getItems().add(ShipInstance.MODE_CONSCIOUS);
        mode.setValue(ShipInstance.MODE_CONSCIOUS);
        ships.valueProperty().addListener((observable, old, selected) -> {
            mode.getItems().setAll(ShipInstance.MODE_CONSCIOUS);
            ShipInstance ship = fleet.ships().stream().filter(item -> item.id().equals(selected))
                    .findFirst().orElse(null);
            if (PassengerStasis.available(snapshot, ship))
                mode.getItems().add(ShipInstance.MODE_CRYOGENIC_STASIS);
            mode.setValue(ShipInstance.MODE_CONSCIOUS);
        });
        Button book = new Button("Book passengers");
        book.disableProperty().bind(ships.valueProperty().isNull()
                .or(races.valueProperty().isNull()).or(destinations.valueProperty().isNull()));
        book.setOnAction(event -> {
            if (humanController == null) return;
            humanController.stageCommand(new LoadPassengersCommand(fleet.id(),
                    ships.getValue(), races.getValue(), count.getValue(),
                    mode.getValue(), destinations.getValue()));
            feedbackLabel.setText("Passenger booking queued for " + destinations.getValue()
                    + "; residents board only if capacity remains available on the next tick.");
            feedbackLabel.setTextFill(Color.LIGHTGREEN);
        });
        row.getChildren().addAll(new Label("Offworld passengers:"), ships, races,
                destinations, count, mode, book);
        return row;
    }
}
