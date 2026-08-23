package com.spaceconquest.frontend;

import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.MinistryAssignment;
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
 * UI panel displaying imperial ministries, governor assignments, treasury, and macro-policies.
 */
public class EmpireView {
    private static final Logger logger = LogManager.getLogger(EmpireView.class);

    private VBox root;
    private VBox content;
    private final Menubar menubar;

    public EmpireView(Menubar menubar) {
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

        Text title = new Text("Imperial cabinet and governance");
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
                content.getChildren().add(createEmpireBox(empire));
            }
        } catch (IOException e) {
            logger.error("Failed to load empire data for visualization", e);
            Text errorText = new Text("Error loading imperial cabinet data.");
            errorText.setFill(Color.RED);
            content.getChildren().add(errorText);
        }
    }

    private VBox createEmpireBox(Empire empire) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: rgba(40, 60, 100, 0.6); -fx-background-radius: 6;");

        Text nameText = new Text(empire.name() + " (" + empire.societyStructure() + ")");
        nameText.setFill(Color.LIGHTBLUE);
        nameText.setFont(Font.font("Verdana", FontWeight.BOLD, 16));

        Text metricsText = new Text(String.format("Treasury: %,.0f credits | Tax rate: %.1f%% | Systems: %s",
                empire.treasuryCredits(), empire.corporateTaxRate() * 100.0,
                empire.controlledSystemIds().isEmpty() ? "None" : String.join(", ", empire.controlledSystemIds())));
        metricsText.setFill(Color.GAINSBORO);
        metricsText.setFont(Font.font("Verdana", 12));

        box.getChildren().addAll(nameText, metricsText);

        if (!empire.ministries().isEmpty()) {
            Text minHeader = new Text("Ministerial portfolios:");
            minHeader.setFill(Color.GOLD);
            minHeader.setFont(Font.font("Verdana", FontWeight.BOLD, 13));
            box.getChildren().add(minHeader);

            for (MinistryAssignment assignment : empire.ministries()) {
                Text item = new Text(String.format("  • %s: %s (Synergy: %.2fx)",
                        assignment.portfolioId(), assignment.assignedCitizenProfessionId(), assignment.calculatedEfficiencyModifier()));
                item.setFill(Color.LIGHTGREEN);
                item.setFont(Font.font("Verdana", 11));
                box.getChildren().add(item);
            }
        }

        return box;
    }
}
