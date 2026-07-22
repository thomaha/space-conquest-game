package com.spaceconquest.frontend;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;

import java.util.List;
import java.util.stream.Collectors;

import static com.almasb.fxgl.dsl.FXGL.*;

/**
 * Builds and manages the on-screen HUD: zoom controls, the goto search box and
 * the info panel for the currently focused celestial body.
 */
public class GameHud {
    private final CameraController camera;
    private final GalaxyRegistry registry;

    private Label infoLabel;
    private VBox infoPanel;

    public GameHud(CameraController camera, GalaxyRegistry registry) {
        this.camera = camera;
        this.registry = registry;
    }

    public void build() {
        addZoomControls();
        addSearchControls();
        addInfoPanel();
        camera.setInfoUpdater(this::updateInfoPanel);
        camera.updateZoom();
    }

    private void addZoomControls() {
        Button btnPlus = new Button("+");
        btnPlus.setOnAction(e -> camera.zoomIn());

        Button btnMinus = new Button("-");
        btnMinus.setOnAction(e -> camera.zoomOut());

        Text zoomLabel = getUIFactoryService().newText("zoom: ", Color.WHITE, 18.0);
        Text zoomValue = getUIFactoryService().newText("", Color.WHITE, 18.0);
        zoomValue.textProperty().bind(getip("zoomLevel").asString());

        HBox zoomControls = new HBox(10, zoomLabel, zoomValue, btnPlus, btnMinus);
        zoomControls.setAlignment(Pos.CENTER);
        zoomControls.setTranslateX(50);
        zoomControls.setTranslateY(50);
        addUINode(zoomControls);
    }

    private void addSearchControls() {
        TextField searchField = new TextField();
        searchField.setPromptText("goto star, planet or moon...");
        searchField.setPrefWidth(200);

        ListView<String> suggestions = new ListView<>();
        suggestions.setPrefSize(200, 150);
        suggestions.setVisible(false);

        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.isEmpty()) {
                suggestions.setVisible(false);
            } else {
                List<String> matches = registry.names().stream()
                        .filter(name -> name.startsWith(newVal.toLowerCase()))
                        .sorted()
                        .map(name -> name + " (" + registry.getType(name) + ")")
                        .collect(Collectors.toList());

                if (matches.isEmpty()) {
                    suggestions.setVisible(false);
                } else {
                    suggestions.getItems().setAll(matches);
                    suggestions.setVisible(true);
                }
            }
        });

        suggestions.setOnMouseClicked(e -> {
            String selected = suggestions.getSelectionModel().getSelectedItem();
            if (selected != null) {
                // Strip the trailing " (type)" indicator to recover the entity name.
                int idx = selected.lastIndexOf(" (");
                String name = idx >= 0 ? selected.substring(0, idx) : selected;
                camera.gotoEntity(name);
                searchField.clear();
                suggestions.setVisible(false);
            }
        });

        VBox searchControls = new VBox(0, searchField, suggestions);
        searchControls.setTranslateX(getAppWidth() - 250);
        searchControls.setTranslateY(50);
        addUINode(searchControls);
    }

    private void addInfoPanel() {
        Text infoTitle = getUIFactoryService().newText("info", Color.WHITE, 16.0);
        infoLabel = new Label();
        infoLabel.setTextFill(Color.WHITE);
        infoLabel.setWrapText(true);
        infoLabel.setPrefWidth(220);
        infoPanel = new VBox(5, infoTitle, infoLabel);
        infoPanel.setPrefWidth(230);
        infoPanel.setTranslateX(getAppWidth() - 250);
        infoPanel.setTranslateY(230);
        infoPanel.setVisible(false);
        addUINode(infoPanel);
    }

    private void updateInfoPanel(String info) {
        if (infoPanel == null || infoLabel == null) {
            return;
        }
        if (info == null || info.isEmpty()) {
            infoPanel.setVisible(false);
            infoLabel.setText("");
        } else {
            infoLabel.setText(info);
            infoPanel.setVisible(true);
        }
    }
}
