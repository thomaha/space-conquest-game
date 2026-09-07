package com.spaceconquest.frontend.empire;

import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.SolarSystem;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.util.List;

/**
 * Handles the visual rendering and interaction logic for the Empire Economy and System Economy tabs.
 */
public class EconomyTab {

    private final EmpireView parent;

    public EconomyTab(EmpireView parent) {
        this.parent = parent;
    }

    public VBox buildEconomyTabContent() {
        VBox container = new VBox(10);
        VBox.setVgrow(container, Priority.ALWAYS);

        HBox subNav = new HBox(8);
        subNav.setAlignment(Pos.CENTER_LEFT);
        subNav.setPadding(new Insets(2, 0, 6, 0));

        javafx.scene.control.Button nationalEconomyBtn = new javafx.scene.control.Button("Macro Imperial Treasury & Budget");
        nationalEconomyBtn.setStyle(parent.getEconomySubView() == EconomySubView.IMPERIAL ?
                "-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; -fx-cursor: hand;" :
                "-fx-background-color: #1a2744; -fx-text-fill: #bdc3c7; -fx-font-size: 11px; -fx-cursor: hand;");
        nationalEconomyBtn.setOnAction(e -> {
            parent.setEconomySubView(EconomySubView.IMPERIAL);
            parent.renderCurrentTab();
        });

        javafx.scene.control.Button systemWorkbenchBtn = new javafx.scene.control.Button("Jurisdiction System Economy Workbench");
        systemWorkbenchBtn.setStyle(parent.getEconomySubView() == EconomySubView.SYSTEM ?
                "-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; -fx-cursor: hand;" :
                "-fx-background-color: #1a2744; -fx-text-fill: #bdc3c7; -fx-font-size: 11px; -fx-cursor: hand;");
        systemWorkbenchBtn.setOnAction(e -> {
            parent.setEconomySubView(EconomySubView.SYSTEM);
            parent.renderCurrentTab();
        });

        subNav.getChildren().addAll(nationalEconomyBtn, systemWorkbenchBtn);
        container.getChildren().add(subNav);

        if (parent.getEconomySubView() == EconomySubView.SYSTEM) {
            container.getChildren().add(buildSystemEconomyWorkbenchContent());
        } else {
            container.getChildren().add(buildNationalEconomyContent());
        }

        return container;
    }

    public VBox buildNationalEconomyContent() {
        VBox container = new VBox(10);
        VBox.setVgrow(container, Priority.ALWAYS);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.setPadding(new Insets(6));
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        VBox content = new VBox(12);
        scrollPane.setContent(content);

        Empire playerEmpire = parent.getPlayerEmpire();
        if (playerEmpire != null) {
            EmpireEconomyReport report = parent.calculateEmpireEconomyReport();

            content.getChildren().add(EconomyCards.createEmpireTreasuryCard(playerEmpire, report));
            content.getChildren().add(EconomyCards.createMacroFiscalPolicyCard(playerEmpire, report, parent));

            HBox columnsBox = new HBox(12);
            columnsBox.setAlignment(Pos.TOP_LEFT);

            VBox incomesCard = EconomyCards.createRevenuesAndIncomesCard(report);
            HBox.setHgrow(incomesCard, Priority.ALWAYS);

            VBox costsCard = EconomyCards.createExpensesAndCostsCard(report);
            HBox.setHgrow(costsCard, Priority.ALWAYS);

            columnsBox.getChildren().addAll(incomesCard, costsCard);
            content.getChildren().add(columnsBox);

            content.getChildren().add(EconomyCards.createColonyLedgerCard(report));
            content.getChildren().add(EconomyCards.createCorporateEconomyCard(report));
        } else {
            Text noEmpireText = new Text("No sovereign empire economy data available.");
            noEmpireText.setFill(Color.LIGHTCORAL);
            content.getChildren().add(noEmpireText);
        }

        container.getChildren().add(scrollPane);
        return container;
    }

    public VBox buildSystemEconomyWorkbenchContent() {
        VBox container = new VBox(10);
        VBox.setVgrow(container, Priority.ALWAYS);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.setPadding(new Insets(6));
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        VBox content = new VBox(12);
        scrollPane.setContent(content);

        Empire playerEmpire = parent.getPlayerEmpire();
        if (playerEmpire == null) {
            content.getChildren().add(new Text("No sovereign empire data found."));
            container.getChildren().add(scrollPane);
            return container;
        }

        List<String> controlledSystems = playerEmpire.controlledSystemIds();
        List<SolarSystem> availableSystems = parent.getSystems().stream()
                .filter(s -> controlledSystems.isEmpty() || controlledSystems.contains(s.id()))
                .toList();

        if (availableSystems.isEmpty()) {
            availableSystems = parent.getSystems();
        }

        if (availableSystems.isEmpty()) {
            content.getChildren().add(new Text("No star systems available for economic administration."));
            container.getChildren().add(scrollPane);
            return container;
        }

        String currentSysId = resolveCurrentSystemId(availableSystems);
        content.getChildren().add(buildSystemSelectorRow(availableSystems, currentSysId));

        buildWorkbenchReportsContent(currentSysId, content);

        container.getChildren().add(scrollPane);
        return container;
    }

    private String resolveCurrentSystemId(List<SolarSystem> availableSystems) {
        String selectedSysId = parent.getSelectedEconomySystemId();
        boolean exists = selectedSysId != null && availableSystems.stream().anyMatch(s -> s.id().equals(selectedSysId));
        if (exists) {
            return selectedSysId;
        } else {
            String defaultSysId = availableSystems.get(0).id();
            parent.setSelectedEconomySystemId(defaultSysId, false);
            return defaultSysId;
        }
    }

    private HBox buildSystemSelectorRow(List<SolarSystem> availableSystems, String currentSysId) {
        HBox selectorRow = new HBox(12);
        selectorRow.setAlignment(Pos.CENTER_LEFT);
        selectorRow.setPadding(new Insets(4, 8, 4, 8));
        selectorRow.setStyle("-fx-background-color: rgba(20, 35, 65, 0.65); -fx-background-radius: 6;");

        Label selLbl = new Label("Select jurisdiction star system:");
        selLbl.setTextFill(Color.GOLD);
        selLbl.setFont(Font.font("Verdana", FontWeight.BOLD, 12));

        ComboBox<String> systemCombo = new ComboBox<>();
        for (SolarSystem sys : availableSystems) {
            systemCombo.getItems().add(sys.name() + " [" + sys.id() + "]");
        }

        SolarSystem activeSys = availableSystems.stream().filter(s -> s.id().equals(currentSysId)).findFirst().orElse(availableSystems.get(0));
        systemCombo.setValue(activeSys.name() + " [" + activeSys.id() + "]");

        systemCombo.setOnAction(e -> {
            String val = systemCombo.getValue();
            if (val != null && val.contains("[") && val.contains("]")) {
                String extractedId = val.substring(val.indexOf("[") + 1, val.indexOf("]"));
                parent.setSelectedEconomySystemId(extractedId);
                parent.renderCurrentTab();
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label helpLbl = new Label("Real-time public sector budget & taxation adjustment workbench");
        helpLbl.setTextFill(Color.LIGHTCYAN);
        helpLbl.setFont(Font.font("Verdana", 10));

        selectorRow.getChildren().addAll(selLbl, systemCombo, spacer, helpLbl);
        return selectorRow;
    }

    private void buildWorkbenchReportsContent(String currentSysId, VBox content) {
        SystemEconomyReport sysReport = parent.calculateSystemEconomyReport(currentSysId);

        VBox overviewContainer = new VBox(EconomyCards.createSystemEconomyOverviewCard(sysReport));
        content.getChildren().add(overviewContainer);

        HBox breakdownColumns = new HBox(12);
        breakdownColumns.setAlignment(Pos.TOP_LEFT);

        VBox revCard = EconomyCards.createSystemRevenuesCard(sysReport);
        HBox.setHgrow(revCard, Priority.ALWAYS);

        VBox expCard = EconomyCards.createSystemExpensesCard(sysReport);
        HBox.setHgrow(expCard, Priority.ALWAYS);

        breakdownColumns.getChildren().addAll(revCard, expCard);
        content.getChildren().add(breakdownColumns);

        VBox breakdownContainer = new VBox(12, breakdownColumns);
        VBox ledgerContainer = new VBox(EconomyCards.createSystemCelestialBodiesLedgerCard(sysReport, parent));

        VBox workbenchCard = SystemEconomyWorkbenchCard.createCard(
                sysReport, overviewContainer, breakdownContainer,
                ledgerContainer,
                () -> parent.renderCurrentTab(),
                parent
        );
        content.getChildren().add(workbenchCard);

        content.getChildren().add(ledgerContainer);
    }
}
