package com.spaceconquest.frontend;

import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.governance.DiplomacyProcessor;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
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
import java.util.List;

/**
 * UI panel displaying galactic diplomatic relations, treaties, influence values, and bilateral stances.
 */
public class DiplomacyView {
    private static final Logger logger = LogManager.getLogger(DiplomacyView.class);

    private VBox root;
    private VBox content;
    private final Menubar menubar;
    private final DiplomacyProcessor diplomacyProcessor = new DiplomacyProcessor();

    public DiplomacyView(Menubar menubar) {
        this.menubar = menubar;
        build();
    }

    private void build() {
        root = new VBox(20);
        content = new VBox(15);
        ScrollPane scrollPane = new ScrollPane(content);

        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: rgba(12, 20, 42, 0.95); " +
                "-fx-border-color: #78aaff; -fx-border-width: 2; " +
                "-fx-border-radius: 10; -fx-background-radius: 10;");
        root.setPrefSize(750, 550);

        Text title = new Text("Galactic diplomacy matrix");
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

        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.setPadding(new Insets(10));

        root.getChildren().addAll(header, scrollPane);
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

    private void loadData() {
        content.getChildren().clear();
        try {
            List<Empire> empires = DataModelLoader.loadEmpires();
            for (Empire empire : empires) {
                content.getChildren().add(createDiplomacyBox(empire, empires));
            }
        } catch (IOException e) {
            logger.error("Failed to load diplomacy data", e);
            Text errorText = new Text("Error loading galactic diplomacy data.");
            errorText.setFill(Color.RED);
            content.getChildren().add(errorText);
        }
    }

    private VBox createDiplomacyBox(Empire empire, List<Empire> allEmpires) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: rgba(40, 60, 100, 0.6); -fx-background-radius: 6;");

        Text nameText = new Text(empire.name() + " (" + empire.raceId() + ")");
        nameText.setFill(Color.LIGHTBLUE);
        nameText.setFont(Font.font("Verdana", FontWeight.BOLD, 16));

        Text postureText = new Text("Ideology: " + empire.societyStructure() + " | Foreign relations:");
        postureText.setFill(Color.GAINSBORO);
        postureText.setFont(Font.font("Verdana", 12));

        box.getChildren().addAll(nameText, postureText);

        for (Empire other : allEmpires) {
            if (!other.id().equals(empire.id())) {
                String tier = diplomacyProcessor.getDiplomaticTier(empire.id(), other.id(), List.of());
                double discount = diplomacyProcessor.getDefaultTariffDiscount(tier);
                
                HBox row = new HBox(10);
                row.setAlignment(Pos.CENTER_LEFT);

                Text rel = new Text(String.format("• vs %s: %s (Tariff discount: %.0f%%)",
                        other.name(), tier, discount * 100.0));
                rel.setFill(Color.LIGHTSKYBLUE);
                rel.setFont(Font.font("Verdana", 11));

                Button proposeTradeBtn = new Button("Propose trade");
                proposeTradeBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-size: 10px;");
                proposeTradeBtn.setOnAction(e -> {
                    // Update tier to commercial alliance
                    rel.setText(String.format("• vs %s: COMMERCIAL_ALLIANCE (Tariff discount: 50%%)", other.name()));
                });

                Button declareWarBtn = new Button("Declare war");
                declareWarBtn.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-size: 10px;");
                declareWarBtn.setOnAction(e -> {
                    rel.setText(String.format("• vs %s: TOTAL_WAR (Tariff discount: 0%%)", other.name()));
                });

                row.getChildren().addAll(rel, proposeTradeBtn, declareWarBtn);
                box.getChildren().add(row);
            }
        }

        return box;
    }
}
