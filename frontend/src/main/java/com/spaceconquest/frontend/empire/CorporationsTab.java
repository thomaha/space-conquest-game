package com.spaceconquest.frontend.empire;

import com.spaceconquest.engine.Corporation;
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

public class CorporationsTab {
    private final EmpireView parent;

    public CorporationsTab(EmpireView parent) {
        this.parent = parent;
    }

    public VBox buildCorporationsTabContent() {
        VBox container = new VBox(10);
        VBox.setVgrow(container, Priority.ALWAYS);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.setPadding(new Insets(6));
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        VBox content = new VBox(14);
        scrollPane.setContent(content);

        List<Corporation> corporations = parent.getCorporationsForPlayerEmpire();

        Text title = new Text("Corporate commercial registry and private sector enterprise");
        title.setFill(Color.AQUA);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 16));
        content.getChildren().add(title);

        if (corporations.isEmpty()) {
            Text empty = new Text("No private corporations registered in sovereign space. Economic development is purely state-driven.");
            empty.setFill(Color.LIGHTGRAY);
            content.getChildren().add(empty);
        } else {
            for (Corporation corp : corporations) {
                content.getChildren().add(createCorporationCard(corp));
            }
        }

        container.getChildren().add(scrollPane);
        return container;
    }

    private VBox createCorporationCard(Corporation corp) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(12));
        card.setStyle("-fx-background-color: rgba(45, 55, 65, 0.7); -fx-background-radius: 8; -fx-border-color: #f1c40f; -fx-border-width: 1;");
        
        Text name = new Text(corp.name() + " (" + corp.marketOrientation() + ")");
        name.setFill(Color.GOLD);
        name.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
        
        Text details = new Text("HQ: " + corp.headquartersEntityId() + " | Capital: " + String.format("%.0f", corp.liquidCapitalReserves()) + " ₵");
        details.setFill(Color.WHITE);
        
        card.getChildren().addAll(name, details);
        return card;
    }
}
