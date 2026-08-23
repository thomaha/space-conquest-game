package com.spaceconquest.frontend;

import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.DataModelLoader;
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
 * UI panel displaying registered private corporations, market orientations, liquid reserves, and fleets.
 */
public class CorporateView {
    private static final Logger logger = LogManager.getLogger(CorporateView.class);

    private VBox root;
    private VBox content;
    private final Menubar menubar;

    public CorporateView(Menubar menubar) {
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

        Text title = new Text("Private corporations registry");
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
            List<Corporation> corporations = DataModelLoader.loadCorporations();
            for (Corporation corp : corporations) {
                content.getChildren().add(createCorpBox(corp));
            }
        } catch (IOException e) {
            logger.error("Failed to load corporate registry", e);
            Text errorText = new Text("Error loading corporate data.");
            errorText.setFill(Color.RED);
            content.getChildren().add(errorText);
        }
    }

    private VBox createCorpBox(Corporation corp) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: rgba(40, 60, 100, 0.6); -fx-background-radius: 6;");

        Text nameText = new Text(corp.name() + " (" + corp.marketOrientation() + ")");
        nameText.setFill(Color.LIGHTBLUE);
        nameText.setFont(Font.font("Verdana", FontWeight.BOLD, 16));

        Text metricsText = new Text(String.format("HQ: %s | Liquid reserves: %,.0f credits | Facilities: %d | Ships: %d",
                corp.headquartersEntityId(), corp.liquidCapitalReserves(),
                corp.ownedFacilityIds().size(), corp.ownedShipIds().size()));
        metricsText.setFill(Color.GAINSBORO);
        metricsText.setFont(Font.font("Verdana", 12));

        box.getChildren().addAll(nameText, metricsText);
        return box;
    }
}
