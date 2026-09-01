package com.spaceconquest.frontend.empire;

import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.SystemGovernor;
import com.spaceconquest.engine.MinistryAssignment;
import javafx.geometry.Insets;
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

public class CabinetTab {
    private final EmpireView parent;

    public CabinetTab(EmpireView parent) {
        this.parent = parent;
    }

    public VBox buildCabinetTabContent() {
        VBox container = new VBox(12);
        container.setPadding(new Insets(4));
        VBox.setVgrow(container, Priority.ALWAYS);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.setPadding(new Insets(6));
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        VBox content = new VBox(14);
        scrollPane.setContent(content);

        Empire playerEmpire = parent.getEmpires().stream()
                .filter(e -> e.id().equalsIgnoreCase(parent.getPlayerEmpireId()))
                .findFirst()
                .orElse(parent.getEmpires().isEmpty() ? null : parent.getEmpires().get(0));

        if (playerEmpire != null) {
            content.getChildren().add(EconomyCards.createSovereignEmpireCard(playerEmpire));
            content.getChildren().add(createMinistriesSection(playerEmpire));
            content.getChildren().add(createGovernorsSection(playerEmpire));
        } else {
            Text noEmpireText = new Text("No sovereign empire data available.");
            noEmpireText.setFill(Color.LIGHTCORAL);
            content.getChildren().add(noEmpireText);
        }

        if (parent.getEmpires().size() > 1) {
            Text otherTitle = new Text("Other sovereign galactic empires:");
            otherTitle.setFill(Color.LIGHTSKYBLUE);
            otherTitle.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
            content.getChildren().add(otherTitle);

            for (Empire other : parent.getEmpires()) {
                if (playerEmpire != null && other.id().equals(playerEmpire.id())) continue;
                content.getChildren().add(createOtherEmpireSummaryCard(other));
            }
        }

        container.getChildren().add(scrollPane);
        return container;
    }

    private VBox createMinistriesSection(Empire empire) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: rgba(20, 35, 65, 0.65); -fx-background-radius: 8; " +
                "-fx-border-color: rgba(120, 170, 255, 0.4); -fx-border-width: 1; -fx-border-radius: 8;");

        Text title = new Text("Imperial cabinet ministries and ministerial portfolios");
        title.setFill(Color.AQUA);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
        box.getChildren().add(title);

        if (empire.ministries().isEmpty()) {
            Text empty = new Text("No active ministerial portfolio appointments.");
            empty.setFill(Color.LIGHTGRAY);
            box.getChildren().add(empty);
        } else {
            GridPane grid = new GridPane();
            grid.setHgap(16);
            grid.setVgap(8);

            int row = 0;
            for (MinistryAssignment assignment : empire.ministries()) {
                Label portLabel = new Label("• " + parent.formatTitle(assignment.portfolioId()) + ":");
                portLabel.setTextFill(Color.GOLD);
                portLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 12));

                Label appointeeLabel = new Label(parent.formatTitle(assignment.assignedCitizenProfessionId()));
                appointeeLabel.setTextFill(Color.WHITE);
                appointeeLabel.setFont(Font.font("Verdana", 12));

                Label synergyLabel = new Label(String.format("Efficiency multiplier: %.2fx", assignment.calculatedEfficiencyModifier()));
                synergyLabel.setTextFill(Color.LIGHTGREEN);
                synergyLabel.setFont(Font.font("Verdana", 11));

                grid.add(portLabel, 0, row);
                grid.add(appointeeLabel, 1, row);
                grid.add(synergyLabel, 2, row);
                row++;
            }
            box.getChildren().add(grid);
        }
        return box;
    }

    private VBox createGovernorsSection(Empire empire) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: rgba(20, 35, 65, 0.65); -fx-background-radius: 8; " +
                "-fx-border-color: rgba(120, 170, 255, 0.4); -fx-border-width: 1; -fx-border-radius: 8;");

        Text title = new Text("System governors and regional jurisdiction assignments");
        title.setFill(Color.AQUA);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
        box.getChildren().add(title);

        if (parent.getSystemGovernors().isEmpty()) {
            Text empty = new Text("No regional system governors assigned.");
            empty.setFill(Color.LIGHTGRAY);
            box.getChildren().add(empty);
        } else {
            GridPane grid = new GridPane();
            grid.setHgap(16);
            grid.setVgap(8);
            int row = 0;
            for (SystemGovernor gov : parent.getSystemGovernors()) {
                String assignedEmpire = null;
                for (Empire e : parent.getEmpires()) {
                    if (e.systemGovernorAssignments().containsValue(gov.id())) {
                        assignedEmpire = e.id();
                        break;
                    }
                }
                if (assignedEmpire == null || !assignedEmpire.equalsIgnoreCase(empire.id())) continue;

                Label name = new Label("• Governor " + EmpireView.formatTitle(gov.professionId()) + ":");
                name.setTextFill(Color.GOLD);
                name.setFont(Font.font("Verdana", FontWeight.BOLD, 12));
                Label jurisdiction = new Label("System " + gov.solarSystemId());
                jurisdiction.setTextFill(Color.WHITE);
                jurisdiction.setFont(Font.font("Verdana", 12));
                grid.add(name, 0, row);
                grid.add(jurisdiction, 1, row);
                row++;
            }
            box.getChildren().add(grid);
        }
        return box;
    }

    private VBox createOtherEmpireSummaryCard(Empire other) {
        VBox box = new VBox(5);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-background-color: rgba(45, 55, 80, 0.7); -fx-background-radius: 6; -fx-border-color: #3498db; -fx-border-width: 1; -fx-border-radius: 6;");
        Text nameText = new Text(parent.formatTitle(other.id()));
        nameText.setFill(Color.WHITE);
        nameText.setFont(Font.font("Verdana", FontWeight.BOLD, 13));
        Text detail = new Text("Controlled systems: " + other.controlledSystemIds().size() + " | Treasury: " + String.format("%.0f", other.treasuryCredits()) + " ₵");
        detail.setFill(Color.LIGHTGRAY);
        detail.setFont(Font.font("Verdana", 11));
        box.getChildren().addAll(nameText, detail);
        return box;
    }
}
