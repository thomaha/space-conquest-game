package com.spaceconquest.frontend;

import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.TechnicalApplication;
import com.spaceconquest.engine.Technology;
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
 * A UI component that visualizes the technology tree.
 */
public class TechnologyView {
    private static final Logger logger = LogManager.getLogger(TechnologyView.class);

    private VBox root;
    private VBox content;
    private ScrollPane scrollPane;
    private final Menubar menubar;

    public TechnologyView(Menubar menubar) {
        this.menubar = menubar;
        // JavaFX nodes moved to initUI() logic via build() pattern if needed, 
        // but here we just ensure TechnologyView is instantiated in Menubar.build()
        build();
    }

    private void build() {
        root = new VBox(20);
        content = new VBox(15);
        scrollPane = new ScrollPane(content);
        
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: rgba(12, 20, 42, 0.95); " +
                      "-fx-border-color: #78aaff; -fx-border-width: 2; " +
                      "-fx-border-radius: 10; -fx-background-radius: 10;");
        root.setPrefSize(800, 600);

        Text title = new Text("Technology Tree");
        title.setFill(Color.WHITE);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 24));

        Button closeButton = new Button("Close");
        closeButton.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-weight: bold;");
        closeButton.setOnAction(e -> {
            root.setVisible(false);
            menubar.closePage();
        });

        HBox header = new HBox(title);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(title, Priority.ALWAYS);
        
        // Add some space and the close button
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

    private void loadData() {
        content.getChildren().clear();
        try {
            List<Technology> technologies = DataModelLoader.loadTechnologies();
            for (Technology tech : technologies) {
                content.getChildren().add(createTechBox(tech));
            }
        } catch (IOException e) {
            logger.error("Failed to load technologies for visualization", e);
            Text errorText = new Text("Error loading technology data.");
            errorText.setFill(Color.RED);
            content.getChildren().add(errorText);
        }
    }

    private VBox createTechBox(Technology tech) {
        VBox techBox = new VBox(5);
        techBox.setPadding(new Insets(10));
        techBox.setStyle("-fx-background-color: rgba(60, 80, 120, 0.5); -fx-background-radius: 5;");

        Text techName = new Text(tech.name());
        techName.setFill(Color.LIGHTBLUE);
        techName.setFont(Font.font("Verdana", FontWeight.BOLD, 18));

        Text techDesc = new Text(tech.description());
        techDesc.setFill(Color.WHITE);
        techDesc.setWrappingWidth(740);

        techBox.getChildren().addAll(techName, techDesc);

        if (!tech.requiredTechnologies().isEmpty()) {
            Text reqs = new Text("Requires: " + String.join(", ", tech.requiredTechnologies()));
            reqs.setFill(Color.GOLD);
            reqs.setFont(Font.font("Verdana", 12));
            techBox.getChildren().add(reqs);
        }

        if (!tech.applications().isEmpty()) {
            VBox appsBox = new VBox(5);
            appsBox.setPadding(new Insets(5, 0, 0, 20));
            for (TechnicalApplication app : tech.applications()) {
                appsBox.getChildren().add(createAppBox(app));
            }
            techBox.getChildren().add(appsBox);
        }

        return techBox;
    }

    private VBox createAppBox(TechnicalApplication app) {
        VBox appBox = new VBox(2);
        
        Text appName = new Text("• " + app.name());
        appName.setFill(Color.LIGHTGREEN);
        appName.setFont(Font.font("Verdana", FontWeight.BOLD, 14));

        Text appDesc = new Text(app.description());
        appDesc.setFill(Color.GAINSBORO);
        appDesc.setFont(Font.font("Verdana", 12));
        appDesc.setWrappingWidth(700);

        appBox.getChildren().addAll(appName, appDesc);
        
        if (!app.requiredTechnologies().isEmpty()) {
             Text reqs = new Text("  Requires: " + String.join(", ", app.requiredTechnologies()));
             reqs.setFill(Color.ORANGE);
             reqs.setFont(Font.font("Verdana", 10));
             appBox.getChildren().add(reqs);
        }

        return appBox;
    }
}
