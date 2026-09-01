package com.spaceconquest.frontend.empire;

import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.frontend.PlanetaryBodyEntry;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PlanetsTab {
    private final EmpireView parent;

    public PlanetsTab(EmpireView parent) {
        this.parent = parent;
    }

    public HBox buildPlanetsTabContent() {
        HBox container = new HBox(0);
        VBox.setVgrow(container, Priority.ALWAYS);

        VBox leftSidebar = new VBox(10);
        leftSidebar.setPrefWidth(280);
        leftSidebar.setPadding(new Insets(6));
        leftSidebar.setStyle("-fx-background-color: rgba(15, 25, 45, 0.6); -fx-border-color: rgba(120, 170, 255, 0.2); -fx-border-width: 0 1 0 0;");

        HBox filterBox = new HBox(8);
        filterBox.setAlignment(Pos.CENTER_LEFT);
        Label filterLbl = new Label("Filter:");
        filterLbl.setTextFill(Color.LIGHTGRAY);
        ComboBox<FilterCategory> filterCombo = new ComboBox<>();
        filterCombo.getItems().addAll(FilterCategory.values());
        filterCombo.setValue(parent.getCurrentFilter());
        filterCombo.setOnAction(e -> {
            parent.setCurrentFilter(filterCombo.getValue());
            parent.renderCurrentTab();
        });
        filterBox.getChildren().addAll(filterLbl, filterCombo);

        HBox sortBox = new HBox(8);
        sortBox.setAlignment(Pos.CENTER_LEFT);
        Label sortLbl = new Label("Sort:");
        sortLbl.setTextFill(Color.LIGHTGRAY);
        ComboBox<SortOption> sortCombo = new ComboBox<>();
        sortCombo.getItems().addAll(SortOption.values());
        sortCombo.setValue(parent.getCurrentSort());
        sortCombo.setOnAction(e -> {
            parent.setCurrentSort(sortCombo.getValue());
            parent.renderCurrentTab();
        });
        sortBox.getChildren().addAll(sortLbl, sortCombo);

        leftSidebar.getChildren().addAll(filterBox, sortBox);

        ScrollPane listScroll = new ScrollPane();
        listScroll.setFitToWidth(true);
        listScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(listScroll, Priority.ALWAYS);

        VBox listContent = new VBox(4);
        List<PlanetaryBodyEntry> bodies = parent.getFilteredAndSortedPlanetaryBodies();
        for (PlanetaryBodyEntry body : bodies) {
            listContent.getChildren().add(createBodyListEntry(body));
        }
        listScroll.setContent(listContent);
        leftSidebar.getChildren().add(listScroll);

        VBox mainView = new VBox(12);
        HBox.setHgrow(mainView, Priority.ALWAYS);
        mainView.setPadding(new Insets(10));

        if (parent.getSelectedBody() != null) {
            mainView.getChildren().add(createPlanetaryBodyDetailView(parent.getSelectedBody()));
        } else {
            VBox empty = new VBox();
            empty.setAlignment(Pos.CENTER);
            VBox.setVgrow(empty, Priority.ALWAYS);
            Text t = new Text("Select a planetary body to view detailed surface telemetry, demographics and industrial operations.");
            t.setFill(Color.LIGHTGRAY);
            t.setFont(Font.font("Verdana", 14));
            t.setWrappingWidth(400);
            empty.getChildren().add(t);
            mainView.getChildren().add(empty);
        }

        container.getChildren().addAll(leftSidebar, mainView);
        return container;
    }

    private Button createBodyListEntry(PlanetaryBodyEntry entry) {
        Button btn = new Button();
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setPadding(new Insets(8));

        boolean selected = parent.getSelectedBody() != null && parent.getSelectedBody().id().equals(entry.id());
        btn.setStyle(selected ?
                "-fx-background-color: rgba(52, 152, 219, 0.4); -fx-border-color: #3498db; -fx-border-width: 0 0 0 4;" :
                "-fx-background-color: rgba(40, 60, 95, 0.3); -fx-border-color: transparent;");

        VBox content = new VBox(2);
        Label nameLbl = new Label(entry.name() + (entry.isMoon() ? " (Moon)" : ""));
        nameLbl.setTextFill(entry.isColonized() ? Color.GOLD : Color.WHITE);
        nameLbl.setFont(Font.font("Verdana", FontWeight.BOLD, 12));

        Label systemLbl = new Label("System: " + entry.systemName());
        systemLbl.setTextFill(Color.LIGHTGRAY);
        systemLbl.setFont(Font.font("Verdana", 10));

        content.getChildren().addAll(nameLbl, systemLbl);
        btn.setGraphic(content);
        btn.setOnAction(e -> {
            parent.setSelectedBody(entry);
            parent.renderCurrentTab();
        });
        return btn;
    }

    private ScrollPane createPlanetaryBodyDetailView(PlanetaryBodyEntry body) {
        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox content = new VBox(16);
        content.setPadding(new Insets(0, 10, 0, 0));

        content.getChildren().add(parent.createBodyCard(body));
        
        GridPane mainGrid = new GridPane();
        mainGrid.setHgap(16);
        mainGrid.setVgap(16);
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(50);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(50);
        mainGrid.getColumnConstraints().addAll(col1, col2);

        mainGrid.add(parent.createPopulationDemographicsSection(body), 0, 0);
        mainGrid.add(parent.createIndustryTableSection(body), 1, 0);
        mainGrid.add(parent.createSurfaceBiomeSection(body), 0, 1);
        mainGrid.add(parent.createPowerAndDepositsSection(body), 1, 1);
        
        content.getChildren().add(mainGrid);
        content.getChildren().add(parent.createTechnologyGatedOperationsSection(body));

        scroll.setContent(content);
        return scroll;
    }
}
