package com.spaceconquest.frontend.empire;

import com.spaceconquest.engine.megastructure.Megastructure;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.util.List;

public class MegastructuresTab {
    private final EmpireView parent;

    public MegastructuresTab(EmpireView parent) {
        this.parent = parent;
    }

    public VBox buildMegastructuresTabContent() {
        VBox container = new VBox(10);
        VBox.setVgrow(container, Priority.ALWAYS);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.setPadding(new Insets(6));
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        VBox content = new VBox(14);
        scrollPane.setContent(content);

        List<Megastructure> megastructures = parent.getMegastructuresForPlayerEmpire();

        Text title = new Text("Imperial megastructures and stellar engineering projects");
        title.setFill(Color.AQUA);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 16));
        content.getChildren().add(title);

        if (megastructures.isEmpty()) {
            Text empty = new Text("No active megastructures detected. Mastery of the stars requires monumental engineering.");
            empty.setFill(Color.LIGHTGRAY);
            content.getChildren().add(empty);
        } else {
            for (Megastructure mega : megastructures) {
                content.getChildren().add(createMegastructureCard(mega));
            }
        }

        container.getChildren().add(scrollPane);
        return container;
    }

    private VBox createMegastructureCard(Megastructure mega) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(12));
        card.setStyle("-fx-background-color: rgba(60, 40, 80, 0.7); -fx-background-radius: 8; -fx-border-color: #9b59b6; -fx-border-width: 1.5;");
        
        Text name = new Text(mega.name() + " (" + mega.type() + ")");
        name.setFill(Color.GOLD);
        name.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
        
        double progress = (mega.totalStages() > 0) ? (mega.currentStage() * 100.0 / mega.totalStages()) : 0.0;
        Text details = new Text("System: " + mega.systemId() + " | Progress: " + String.format("%.1f%%", progress));
        details.setFill(Color.WHITE);
        
        card.getChildren().addAll(name, details);
        return card;
    }
}
