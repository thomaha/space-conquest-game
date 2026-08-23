package com.spaceconquest.frontend;

import com.spaceconquest.engine.GameStartScenario;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

/**
 * Dialogue for starting a new game and selecting starting conditions.
 */
public class GameStartDialog extends Dialog<GameStartDialog.GameStartResult> {

    public record GameStartResult(int numSolarSystems, GameStartScenario scenario) {}

    public GameStartDialog(GameStartScenario defaultScenario, int defaultSystems) {
        setTitle("Game start setup");
        setHeaderText("Configure galaxy generation and starting conditions");

        ButtonType startButtonType = new ButtonType("Start game", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(startButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(14);
        grid.setPadding(new Insets(20, 20, 10, 20));

        TextField systemsField = new TextField(String.valueOf(defaultSystems > 0 ? defaultSystems : 10));
        systemsField.setPromptText("10");

        ComboBox<GameStartScenario> scenarioBox = new ComboBox<>();
        scenarioBox.getItems().addAll(GameStartScenario.values());
        scenarioBox.setValue(defaultScenario != null ? defaultScenario : GameStartScenario.PRE_SPACE_FLIGHT);
        scenarioBox.setPrefWidth(350);

        scenarioBox.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(GameStartScenario item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.displayName());
            }
        });
        scenarioBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(GameStartScenario item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.displayName());
            }
        });

        Label descriptionLabel = new Label(scenarioBox.getValue().description());
        descriptionLabel.setWrapText(true);
        descriptionLabel.setMaxWidth(350);
        descriptionLabel.setStyle("-fx-text-fill: #555555; -fx-font-style: italic;");

        scenarioBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                descriptionLabel.setText(newVal.description());
            }
        });

        grid.add(new Label("Number of stars:"), 0, 0);
        grid.add(systemsField, 1, 0);
        grid.add(new Label("Starting era:"), 0, 1);
        grid.add(scenarioBox, 1, 1);
        grid.add(new Label("Details:"), 0, 2);
        grid.add(descriptionLabel, 1, 2);

        getDialogPane().setContent(grid);

        setResultConverter(dialogButton -> {
            if (dialogButton == startButtonType) {
                int systems;
                try {
                    systems = Integer.parseInt(systemsField.getText().trim());
                    if (systems <= 0) systems = 10;
                } catch (NumberFormatException e) {
                    systems = 10;
                }
                return new GameStartResult(systems, scenarioBox.getValue());
            }
            return null;
        });
    }
}
