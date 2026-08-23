package com.spaceconquest.frontend;

import com.spaceconquest.engine.industry.refinement.RefinementProcessor;
import com.spaceconquest.engine.industry.refinement.RefinementRecipe;
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

import java.util.List;
import java.util.Map;

/**
 * Interactive UI panel displaying multi-stage chemical and metallurgical refinement recipes,
 * composite alloy synthesis and consumer goods living standards.
 */
public class RefinementView {

    private VBox root;
    private VBox content;
    private final Menubar menubar;
    private final List<RefinementRecipe> recipes = RefinementProcessor.STANDARD_RECIPES;

    public RefinementView(Menubar menubar) {
        this.menubar = menubar;
        build();
    }

    private void build() {
        root = new VBox(15);
        content = new VBox(12);
        ScrollPane scrollPane = new ScrollPane(content);

        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: rgba(14, 22, 45, 0.96); " +
                "-fx-border-color: #27ae60; -fx-border-width: 2; " +
                "-fx-border-radius: 10; -fx-background-radius: 10;");
        root.setPrefSize(920, 700);

        Text title = new Text("Industrial refinement chains, composite alloys and consumer goods");
        title.setFill(Color.WHITE);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 20));

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

    private void renderContent() {
        content.getChildren().clear();

        VBox section = new VBox(10);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: rgba(25, 40, 70, 0.7); -fx-background-radius: 8; -fx-border-color: #2ecc71; -fx-border-width: 1; -fx-border-radius: 8;");

        Text header = new Text("Standard metallurgical and chemical refinement recipes");
        header.setFill(Color.LIGHTGREEN);
        header.setFont(Font.font("Verdana", FontWeight.BOLD, 15));
        section.getChildren().add(header);

        for (RefinementRecipe recipe : recipes) {
            VBox rBox = new VBox(5);
            rBox.setPadding(new Insets(8));
            rBox.setStyle("-fx-background-color: rgba(15, 30, 55, 0.6); -fx-background-radius: 6;");

            Text rTitle = new Text(String.format("Recipe: %s [%s] | Category: %s | Environment: %s | Tier %d+ (%s)",
                    recipe.name(), recipe.id(), recipe.category(), recipe.operationalEnvironment(),
                    recipe.minFacilityTier(), recipe.primaryProfessionId()));
            rTitle.setFill(Color.AQUAMARINE);
            rTitle.setFont(Font.font("Verdana", FontWeight.BOLD, 12));

            StringBuilder inputsStr = new StringBuilder("  • Inputs: ");
            for (Map.Entry<String, Double> in : recipe.inputMaterialsKg().entrySet()) {
                inputsStr.append(String.format("%.0fkg %s, ", in.getValue(), in.getKey()));
            }
            inputsStr.append(String.format("Power: %.0f kW", recipe.powerDrawKw()));

            StringBuilder outputsStr = new StringBuilder("  • Outputs: ");
            for (Map.Entry<String, Double> out : recipe.outputMaterialsKg().entrySet()) {
                outputsStr.append(String.format("%.0fkg %s, ", out.getValue(), out.getKey()));
            }
            if (!recipe.byproductMaterialsKg().isEmpty()) {
                outputsStr.append(" | Byproducts: ");
                for (Map.Entry<String, Double> by : recipe.byproductMaterialsKg().entrySet()) {
                    outputsStr.append(String.format("%.0fkg %s, ", by.getValue(), by.getKey()));
                }
            }

            Text inText = new Text(inputsStr.toString());
            inText.setFill(Color.LIGHTGRAY);
            inText.setFont(Font.font("Verdana", 10));

            Text outText = new Text(outputsStr.toString());
            outText.setFill(Color.LIGHTYELLOW);
            outText.setFont(Font.font("Verdana", 10));

            rBox.getChildren().addAll(rTitle, inText, outText);
            section.getChildren().add(rBox);
        }

        content.getChildren().add(section);
    }
}
