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
        leftSidebar.setPrefWidth(300);
        leftSidebar.setMinWidth(280);
        leftSidebar.setPadding(new Insets(6));
        leftSidebar.setStyle("-fx-background-color: rgba(15, 25, 45, 0.6); -fx-border-color: rgba(120, 170, 255, 0.2); -fx-border-width: 0 1 0 0;");

        ComboBox<FilterCategory> filterCombo = new ComboBox<>();
        filterCombo.getItems().addAll(FilterCategory.values());
        filterCombo.setValue(parent.getCurrentFilter());
        filterCombo.setMaxWidth(Double.MAX_VALUE);
        filterCombo.setTooltip(new Tooltip("Filter planetary bodies"));
        filterCombo.setAccessibleText("Filter planetary bodies");
        filterCombo.setOnAction(e -> {
            parent.setCurrentFilter(filterCombo.getValue());
        });

        ComboBox<SortOption> sortCombo = new ComboBox<>();
        sortCombo.getItems().addAll(SortOption.values());
        sortCombo.setValue(parent.getCurrentSort());
        sortCombo.setMaxWidth(Double.MAX_VALUE);
        sortCombo.setTooltip(new Tooltip("Sort planetary bodies"));
        sortCombo.setAccessibleText("Sort planetary bodies");
        sortCombo.setOnAction(e -> {
            parent.setCurrentSort(sortCombo.getValue());
        });

        leftSidebar.getChildren().addAll(filterCombo, sortCombo);

        ScrollPane listScroll = new ScrollPane();
        listScroll.setFitToWidth(true);
        listScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(listScroll, Priority.ALWAYS);

        VBox listContent = new VBox(4);
        List<PlanetaryBodyEntry> bodies = parent.getFilteredAndSortedPlanetaryBodies();

        for (PlanetaryBodyEntry body : bodies) {
            listContent.getChildren().add(createBodyListEntry(body));
        }
        if (bodies.isEmpty()) {
            Label emptyList = new Label("No planetary bodies match this filter.");
            emptyList.setTextFill(Color.LIGHTGRAY);
            emptyList.setWrapText(true);
            listContent.getChildren().add(emptyList);
        }
        listScroll.setContent(listContent);
        leftSidebar.getChildren().add(listScroll);

        VBox mainView = new VBox(12);
        HBox.setHgrow(mainView, Priority.ALWAYS);
        mainView.setMinWidth(0);
        mainView.setPadding(new Insets(10));

        // Auto-select first item if none selected and list not empty
        if (parent.getSelectedBody() == null && !bodies.isEmpty()) {
            parent.setSelectedBody(bodies.get(0), false);
        }

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
        btn.setMinHeight(Region.USE_PREF_SIZE);
        btn.setMaxHeight(Region.USE_PREF_SIZE);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setPadding(new Insets(3, 8, 3, 8));

        boolean selected = parent.getSelectedBody() != null && parent.getSelectedBody().id().equals(entry.id());
        btn.setStyle(selected ?
                "-fx-background-color: rgba(52, 152, 219, 0.4); -fx-border-color: #3498db; -fx-border-width: 0 0 0 4;" :
                "-fx-background-color: rgba(40, 60, 95, 0.3); -fx-border-color: transparent;");

        VBox content = new VBox(1);
        Label nameLbl = new Label(entry.name() + (entry.isMoon() ? " (Moon)" : ""));
        nameLbl.setTextFill(entry.isColonized() ? Color.GOLD : Color.WHITE);
        nameLbl.setFont(Font.font("Verdana", FontWeight.BOLD, 12));
        nameLbl.setMaxWidth(235);

        Label systemLbl = new Label("System: " + entry.systemName());
        systemLbl.setTextFill(Color.LIGHTGRAY);
        systemLbl.setFont(Font.font("Verdana", 10));
        systemLbl.setMaxWidth(235);

        content.getChildren().addAll(nameLbl, systemLbl);
        btn.setGraphic(content);
        btn.setTooltip(new Tooltip(entry.name() + " — " + entry.systemName()));
        btn.setOnAction(e -> {
            parent.setSelectedBody(entry);
        });
        return btn;
    }

    private ScrollPane createPlanetaryBodyDetailView(PlanetaryBodyEntry body) {
        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setMinWidth(0);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox content = new VBox(16);
        content.setMinWidth(0);
        content.setPadding(new Insets(0, 10, 0, 0));

        content.getChildren().add(parent.createBodyCard(body));
        
        GridPane mainGrid = new GridPane();
        mainGrid.setMinWidth(0);
        mainGrid.setHgap(16);
        mainGrid.setVgap(16);
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(50);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(50);
        mainGrid.getColumnConstraints().addAll(col1, col2);

        mainGrid.add(parent.createPopulationDemographicsSection(body), 0, 0, 2, 1);
        mainGrid.add(parent.createSurfaceBiomeSection(body), 0, 1, 2, 1);
        mainGrid.add(parent.createPowerAndDepositsSection(body), 0, 2);
        mainGrid.add(parent.createTechnologyGatedOperationsSection(body), 1, 2);
        
        content.getChildren().addAll(mainGrid, parent.createIndustrySection(body));

        VBox container = new VBox(content);
        container.setMinWidth(0);
        container.setPadding(new Insets(10));
        scroll.setContent(container);
        return scroll;
    }
}
