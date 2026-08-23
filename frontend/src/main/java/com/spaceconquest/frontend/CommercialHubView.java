package com.spaceconquest.frontend;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.CancelTradeRouteCommand;
import com.spaceconquest.control.command.CreateTradeRouteCommand;
import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.MarketOrder;
import com.spaceconquest.engine.logistics.TradeRoute;
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
import java.util.Map;

/**
 * UI panel displaying active commercial hubs, commodity spot prices, supply-demand bars,
 * shortcoming scores and automated civilian trade route logistics.
 */
public class CommercialHubView {

    private VBox root;
    private VBox content;
    private Label feedbackLabel;
    private final Menubar menubar;
    private HumanController humanController;
    private String playerEmpireId = "terran_confederation";
    private final List<CommercialHub> activeHubs = new ArrayList<>();
    private final List<TradeRoute> activeTradeRoutes = new ArrayList<>();

    public CommercialHubView(Menubar menubar) {
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
        ScrollPane scrollPane = new ScrollPane(content);

        root.setPadding(new Insets(15));
        root.setStyle("-fx-background-color: rgba(12, 20, 42, 0.96); " +
                "-fx-border-color: #78aaff; -fx-border-width: 2; " +
                "-fx-border-radius: 10; -fx-background-radius: 10;");
        root.setPrefSize(880, 680);

        Text title = new Text("Commercial hubs and automated trade routes");
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

        feedbackLabel = new Label("Ready | Manage commodity orders and recurring freighter logistics.");
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

    public void show(List<CommercialHub> hubs) {
        show(hubs, List.of());
    }

    public void show(List<CommercialHub> hubs, List<TradeRoute> routes) {
        activeHubs.clear();
        if (hubs != null) activeHubs.addAll(hubs);

        activeTradeRoutes.clear();
        if (routes != null) activeTradeRoutes.addAll(routes);

        renderView();
        root.setVisible(true);
        root.toFront();
    }

    public void hide() {
        root.setVisible(false);
        if (menubar != null) {
            menubar.closePage();
        }
    }

    private void renderView() {
        content.getChildren().clear();

        // 1. Interactive Trade Route Establishment Workbench
        content.getChildren().add(createTradeRouteWorkbench());

        // 2. Active Trade Routes List
        content.getChildren().add(createActiveRoutesSection());

        // 3. Commercial Hubs List & Spot Prices
        content.getChildren().add(createHubsSection());
    }

    private VBox createTradeRouteWorkbench() {
        VBox section = new VBox(8);
        section.setPadding(new Insets(12));
        section.setStyle("-fx-background-color: rgba(25, 45, 80, 0.75); -fx-background-radius: 8; -fx-border-color: #f39c12; -fx-border-width: 1; -fx-border-radius: 8;");

        Text title = new Text("Establish automated cargo logistics route");
        title.setFill(Color.ORANGE);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 15));

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);

        Label originLbl = new Label("Origin hub:");
        originLbl.setTextFill(Color.LIGHTCYAN);
        ComboBox<String> originCombo = new ComboBox<>();
        for (CommercialHub hub : activeHubs) {
            originCombo.getItems().add(hub.id());
        }
        if (originCombo.getItems().isEmpty()) {
            originCombo.getItems().addAll("hub_earth", "hub_mars", "hub_luna", "hub_ceres");
        }
        originCombo.setValue(originCombo.getItems().get(0));

        Label destLbl = new Label("Destination hub:");
        destLbl.setTextFill(Color.LIGHTCYAN);
        ComboBox<String> destCombo = new ComboBox<>();
        for (CommercialHub hub : activeHubs) {
            destCombo.getItems().add(hub.id());
        }
        if (destCombo.getItems().isEmpty()) {
            destCombo.getItems().addAll("hub_mars", "hub_earth", "hub_ceres", "hub_titan");
        }
        destCombo.setValue(destCombo.getItems().size() > 1 ? destCombo.getItems().get(1) : destCombo.getItems().get(0));

        Label matLbl = new Label("Cargo material:");
        matLbl.setTextFill(Color.LIGHTCYAN);
        ComboBox<String> matCombo = new ComboBox<>();
        matCombo.getItems().addAll("refined_iron", "refined_copper", "refined_aluminum", "consumer_goods", "fusion_fuel_pellets", "inconel_alloy", "food_rations");
        matCombo.setValue("refined_iron");

        Label amountLbl = new Label("Transfer / turn (kg):");
        amountLbl.setTextFill(Color.LIGHTCYAN);
        Spinner<Integer> amountSpinner = new Spinner<>(100, 20000, 1500, 100);
        amountSpinner.setPrefWidth(90);

        Label thresholdLbl = new Label("Min source stock (kg):");
        thresholdLbl.setTextFill(Color.LIGHTCYAN);
        Spinner<Integer> thresholdSpinner = new Spinner<>(0, 50000, 1000, 500);
        thresholdSpinner.setPrefWidth(90);

        Button establishBtn = new Button("Commission trade route");
        establishBtn.setStyle("-fx-background-color: #e67e22; -fx-text-fill: white; -fx-font-weight: bold;");
        establishBtn.setOnAction(e -> {
            if (humanController != null) {
                humanController.stageCommand(new CreateTradeRouteCommand(
                        playerEmpireId,
                        originCombo.getValue() + " -> " + destCombo.getValue() + " (" + matCombo.getValue() + ")",
                        originCombo.getValue(),
                        destCombo.getValue(),
                        matCombo.getValue(),
                        amountSpinner.getValue(),
                        thresholdSpinner.getValue(),
                        50000.0,
                        List.of("freighter_01")
                ));
                feedbackLabel.setText("Commissioned trade route: " + matCombo.getValue() + " from " + originCombo.getValue() + " to " + destCombo.getValue());
                feedbackLabel.setTextFill(Color.LIGHTGREEN);
            }
        });

        grid.add(originLbl, 0, 0);
        grid.add(originCombo, 1, 0);
        grid.add(destLbl, 2, 0);
        grid.add(destCombo, 3, 0);

        grid.add(matLbl, 0, 1);
        grid.add(matCombo, 1, 1);
        grid.add(amountLbl, 2, 1);
        grid.add(amountSpinner, 3, 1);

        grid.add(thresholdLbl, 0, 2);
        grid.add(thresholdSpinner, 1, 2);
        grid.add(establishBtn, 2, 2, 2, 1);

        section.getChildren().addAll(title, grid);
        return section;
    }

    private VBox createActiveRoutesSection() {
        VBox section = new VBox(6);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: rgba(20, 35, 60, 0.7); -fx-background-radius: 6;");

        Text header = new Text("Active trade and supply routes");
        header.setFill(Color.GOLD);
        header.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
        section.getChildren().add(header);

        if (activeTradeRoutes.isEmpty()) {
            Text empty = new Text("No recurring trade routes currently commissioned.");
            empty.setFill(Color.LIGHTGRAY);
            section.getChildren().add(empty);
        } else {
            for (TradeRoute r : activeTradeRoutes) {
                HBox row = new HBox(10);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(6));
                row.setStyle("-fx-background-color: rgba(30, 45, 70, 0.6); -fx-background-radius: 4;");

                Text info = new Text(String.format("• [%s] %s | Moving %,.0f kg/turn %s (Total moved: %,.0f kg) | Status: %s",
                        r.id(), r.name(), r.transferAmountPerTurnKg(), r.materialId(), r.totalVolumeMovedKg(),
                        r.isActive() ? "ACTIVE" : "INACTIVE"));
                info.setFill(r.isActive() ? Color.LIGHTGREEN : Color.GRAY);
                info.setFont(Font.font("Verdana", 11));
                HBox.setHgrow(info, Priority.ALWAYS);

                if (r.isActive()) {
                    Button cancelBtn = new Button("Cancel");
                    cancelBtn.setStyle("-fx-background-color: #d63031; -fx-text-fill: white; -fx-font-size: 10px;");
                    cancelBtn.setOnAction(e -> {
                        if (humanController != null) {
                            humanController.stageCommand(new CancelTradeRouteCommand(r.id(), playerEmpireId));
                            feedbackLabel.setText("Decommissioned trade route: " + r.name());
                            feedbackLabel.setTextFill(Color.LIGHTGREEN);
                        }
                    });
                    row.getChildren().addAll(info, cancelBtn);
                } else {
                    row.getChildren().add(info);
                }
                section.getChildren().add(row);
            }
        }
        return section;
    }

    private VBox createHubsSection() {
        VBox section = new VBox(8);
        if (activeHubs.isEmpty()) {
            Text emptyText = new Text("No active commercial hubs currently registered.");
            emptyText.setFill(Color.LIGHTGRAY);
            section.getChildren().add(emptyText);
        } else {
            for (CommercialHub hub : activeHubs) {
                section.getChildren().add(createHubBox(hub));
            }
        }
        return section;
    }

    private VBox createHubBox(CommercialHub hub) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: rgba(40, 60, 100, 0.6); -fx-background-radius: 6;");

        Text nameText = new Text("Hub: " + hub.id() + " (" + hub.entityId() + ")");
        nameText.setFill(Color.LIGHTBLUE);
        nameText.setFont(Font.font("Verdana", FontWeight.BOLD, 16));

        Text metricsText = new Text(String.format("Tariff: %.1f%% | Storage: %,.0f / %,.0f kg | Logistics range: %.0f units",
                hub.transactionTariffRate() * 100.0, hub.currentStoredWeightKg(), hub.storageCapacityKg(), hub.logisticsRangeUnits()));
        metricsText.setFill(Color.GAINSBORO);
        metricsText.setFont(Font.font("Verdana", 12));

        box.getChildren().addAll(nameText, metricsText);

        if (!hub.activeOrders().isEmpty()) {
            Text orderHeader = new Text("Commodity markets and deficit scores:");
            orderHeader.setFill(Color.GOLD);
            orderHeader.setFont(Font.font("Verdana", FontWeight.BOLD, 13));
            box.getChildren().add(orderHeader);

            for (Map.Entry<String, MarketOrder> entry : hub.activeOrders().entrySet()) {
                MarketOrder o = entry.getValue();
                Text orderText = new Text(String.format("  • %s: Price %,.2f cr/kg | Supply: %,.0f kg | Demand: %,.0f kg | Deficit (S_m): %.2f",
                        entry.getKey(), o.pricePerKg(), o.supplyKg(), o.demandKg(), o.shortcomingScore()));
                orderText.setFill(o.shortcomingScore() > 0.40 ? Color.ORANGERED : Color.LIGHTGREEN);
                orderText.setFont(Font.font("Verdana", 11));
                box.getChildren().add(orderText);
            }
        }

        return box;
    }
}
