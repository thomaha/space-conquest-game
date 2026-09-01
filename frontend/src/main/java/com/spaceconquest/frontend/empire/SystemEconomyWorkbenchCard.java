package com.spaceconquest.frontend.empire;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.SetSystemEconomyBudgetCommand;
import com.spaceconquest.engine.economy.SystemEconomy;
import com.spaceconquest.engine.economy.SystemEconomyProcessor;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

public class SystemEconomyWorkbenchCard {
    private static final SystemEconomyProcessor economyProcessor = new SystemEconomyProcessor();

    public static VBox createCard(
            SystemEconomyReport report,
            VBox overviewContainer,
            VBox breakdownContainer,
            VBox ledgerContainer,
            Runnable refreshImperialSummary,
            EmpireView parent
    ) {
        VBox box = new VBox(12);
        box.setPadding(new Insets(14));
        box.setStyle("-fx-background-color: rgba(25, 45, 80, 0.8); -fx-background-radius: 8; " +
                "-fx-border-color: #3498db; -fx-border-width: 1.5; -fx-border-radius: 8;");

        Text title = new Text("System public funding budget and taxation controls");
        title.setFill(Color.AQUA);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 15));

        Text desc = new Text("Configure colonial fiscal policy, adjust public sector allocations across five municipal priorities, and enact local colonial tax tariffs.");
        desc.setFill(Color.LIGHTCYAN);
        desc.setFont(Font.font("Verdana", 11));

        SystemEconomy economy = report.economy();

        HBox budgetRow = new HBox(12);
        budgetRow.setAlignment(Pos.CENTER_LEFT);

        Label budgetLbl = new Label("Total system budget allocation (Credits/turn):");
        budgetLbl.setTextFill(Color.WHITE);
        budgetLbl.setFont(Font.font("Verdana", FontWeight.BOLD, 12));

        TextField budgetField = new TextField(String.format("%.0f", economy.totalBudgetCredits()));
        budgetField.setPrefWidth(120);
        budgetField.setStyle("-fx-font-family: 'Verdana'; -fx-font-weight: bold; -fx-font-size: 12px;");

        long pop = report.systemPopulation();
        double standardBudget = pop * 0.002;
        Button standardBtn = createRateButton("Standard (1.0x)", budgetField, standardBudget);
        Button doubleBtn = createRateButton("Double (2.0x)", budgetField, standardBudget * 2.0);
        Button halfBtn = createRateButton("Austerity (0.5x)", budgetField, standardBudget * 0.5);

        budgetRow.getChildren().addAll(budgetLbl, budgetField, standardBtn, doubleBtn, halfBtn);

        GridPane slidersGrid = new GridPane();
        slidersGrid.setHgap(16);
        slidersGrid.setVgap(10);
        slidersGrid.setPadding(new Insets(6, 0, 6, 0));

        Slider eduSlider = createSectorSlider(economy.educationAllocation() * 100.0);
        Label eduValLbl = createPercentLabel(economy.educationAllocation() * 100.0);
        addSliderRow(slidersGrid, 0, "Colonial Education & Science:", eduSlider, eduValLbl, Color.LIGHTSKYBLUE);

        Slider lawSlider = createSectorSlider(economy.lawAndOrderAllocation() * 100.0);
        Label lawValLbl = createPercentLabel(economy.lawAndOrderAllocation() * 100.0);
        addSliderRow(slidersGrid, 1, "Planetary Police & Law Enforcement:", lawSlider, lawValLbl, Color.LIGHTGOLDENRODYELLOW);

        Slider healthSlider = createSectorSlider(economy.healthAndWelfareAllocation() * 100.0);
        Label healthValLbl = createPercentLabel(economy.healthAndWelfareAllocation() * 100.0);
        addSliderRow(slidersGrid, 2, "Public Healthcare & Hospitals:", healthSlider, healthValLbl, Color.LIGHTGREEN);

        Slider infraSlider = createSectorSlider(economy.infrastructureAllocation() * 100.0);
        Label infraValLbl = createPercentLabel(economy.infrastructureAllocation() * 100.0);
        addSliderRow(slidersGrid, 3, "Municipal Infrastructure & Transit:", infraSlider, infraValLbl, Color.ORANGE);

        Slider militiaSlider = createSectorSlider(economy.planetaryMilitiasAllocation() * 100.0);
        Label militiaValLbl = createPercentLabel(economy.planetaryMilitiasAllocation() * 100.0);
        addSliderRow(slidersGrid, 4, "Planetary Defense Militias:", militiaSlider, militiaValLbl, Color.TOMATO);

        Slider taxSlider = createSectorSlider(economy.taxRate() * 100.0);
        taxSlider.setMax(50.0);
        Label taxValLbl = createPercentLabel(economy.taxRate() * 100.0);
        taxValLbl.setTextFill(Color.GOLD);
        addSliderRow(slidersGrid, 5, "Colonial Income Tax Tariff Rate:", taxSlider, taxValLbl, Color.GOLD);

        HBox statusRow = new HBox(20);
        statusRow.setAlignment(Pos.CENTER_LEFT);

        Label sumLbl = new Label("Total Allocation: 100.0%");
        sumLbl.setTextFill(Color.LIGHTGREEN);
        sumLbl.setFont(Font.font("Verdana", FontWeight.BOLD, 12));

        Label balancePreviewLbl = new Label(String.format("Projected net balance: %+,.0f ₵/turn", report.netSystemBalance()));
        balancePreviewLbl.setTextFill(report.netSystemBalance() >= 0 ? Color.LIGHTGREEN : Color.LIGHTCORAL);
        balancePreviewLbl.setFont(Font.font("Verdana", FontWeight.BOLD, 12));

        statusRow.getChildren().addAll(sumLbl, balancePreviewLbl);

        HBox presetsRow = new HBox(8);
        presetsRow.setAlignment(Pos.CENTER_LEFT);
        Label presetTitle = new Label("Quick policy presets:");
        presetTitle.setTextFill(Color.LIGHTGRAY);
        presetTitle.setFont(Font.font("Verdana", 10));

        Button balancedPreset = createPresetButton("Balanced (20% each)", eduSlider, lawSlider, healthSlider, infraSlider, militiaSlider, 20, 20, 20, 20, 20);
        Button industrialPreset = createPresetButton("Heavy industry (40% infra)", eduSlider, lawSlider, healthSlider, infraSlider, militiaSlider, 10, 15, 15, 40, 20);
        Button securityPreset = createPresetButton("High security (35% law/mil)", eduSlider, lawSlider, healthSlider, infraSlider, militiaSlider, 10, 35, 10, 10, 35);
        Button welfarePreset = createPresetButton("Welfare & Health (35% health)", eduSlider, lawSlider, healthSlider, infraSlider, militiaSlider, 25, 10, 35, 20, 10);
        Button maxTaxPreset = new Button("Standard 10% tax");
        maxTaxPreset.setFont(Font.font("Verdana", 10));
        maxTaxPreset.setOnAction(e -> taxSlider.setValue(10.0));

        presetsRow.getChildren().addAll(presetTitle, balancedPreset, industrialPreset, securityPreset, welfarePreset, maxTaxPreset);

        HBox actionRow = new HBox(12);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        Button applyBtn = new Button("Enact and stage system economy budget command");
        applyBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; " +
                "-fx-padding: 8 16 8 16; -fx-background-radius: 4; -fx-cursor: hand;");
        applyBtn.setCursor(javafx.scene.Cursor.HAND);

        Region actSpacer = new Region();
        HBox.setHgrow(actSpacer, Priority.ALWAYS);

        Label feedback = new Label("Ready to stage fiscal decree.");
        feedback.setTextFill(Color.LIGHTCYAN);
        feedback.setFont(Font.font("Verdana", 11));

        actionRow.getChildren().addAll(applyBtn, actSpacer, feedback);

        Runnable updateCalculations = () -> updateCalculations(
                report, budgetField, eduSlider, lawSlider, healthSlider, infraSlider, militiaSlider, taxSlider,
                eduValLbl, lawValLbl, healthValLbl, infraValLbl, militiaValLbl, taxValLbl, sumLbl, balancePreviewLbl
        );

        eduSlider.valueProperty().addListener((obs, oldV, newV) -> updateCalculations.run());
        lawSlider.valueProperty().addListener((obs, oldV, newV) -> updateCalculations.run());
        healthSlider.valueProperty().addListener((obs, oldV, newV) -> updateCalculations.run());
        infraSlider.valueProperty().addListener((obs, oldV, newV) -> updateCalculations.run());
        militiaSlider.valueProperty().addListener((obs, oldV, newV) -> updateCalculations.run());
        taxSlider.valueProperty().addListener((obs, oldV, newV) -> updateCalculations.run());
        budgetField.textProperty().addListener((obs, oldV, newV) -> updateCalculations.run());

        applyBtn.setOnAction(e -> {
            try {
                double bVal = Double.parseDouble(budgetField.getText().trim());
                double eVal = eduSlider.getValue() / 100.0;
                double lVal = lawSlider.getValue() / 100.0;
                double hVal = healthSlider.getValue() / 100.0;
                double iVal = infraSlider.getValue() / 100.0;
                double mVal = militiaSlider.getValue() / 100.0;
                double tVal = taxSlider.getValue() / 100.0;

                SetSystemEconomyBudgetCommand cmd = new SetSystemEconomyBudgetCommand(
                        parent.getPlayerEmpireId(), report.systemId(), eVal, lVal, hVal, iVal, mVal, bVal, tVal
                );

                HumanController controller = parent.getHumanController();
                if (controller != null) {
                    controller.stageCommand(cmd);
                }

                SystemEconomy updated = new SystemEconomy(
                        report.systemId(), parent.getPlayerEmpireId(),
                        eVal, lVal, hVal, iVal, mVal, bVal,
                        economy.accumulatedMilitiaInvestment(),
                        economy.educationLevel(), economy.lawAndOrderLevel(),
                        economy.healthAndWelfareLevel(), economy.infrastructureLevel(),
                        economy.planetaryMilitiaLevel(),
                        economy.employedTeachers(), economy.employedScientists(),
                        economy.employedPolice(), economy.employedMedics(),
                        economy.employedEngineers(), economy.employedTechnicians(),
                        economy.employedSoldiers(), economy.recruitableSoldiers(),
                        tVal
                );

                parent.getSystemEconomies().removeIf(se -> se.systemId().equals(report.systemId()));
                parent.getSystemEconomies().add(updated);

                feedback.setText("Staged budget command for " + report.systemName() + "!");
                feedback.setTextFill(Color.LIGHTGREEN);

                SystemEconomyReport newReport = parent.calculateSystemEconomyReport(report.systemId());
                overviewContainer.getChildren().setAll(EconomyCards.createSystemEconomyOverviewCard(newReport));
                breakdownContainer.getChildren().setAll(
                        EconomyCards.createSystemRevenuesCard(newReport),
                        EconomyCards.createSystemExpensesCard(newReport)
                );
                ledgerContainer.getChildren().setAll(EconomyCards.createSystemCelestialBodiesLedgerCard(newReport, parent));

                if (refreshImperialSummary != null) {
                    refreshImperialSummary.run();
                }

            } catch (NumberFormatException ex) {
                feedback.setText("Invalid budget amount!");
                feedback.setTextFill(Color.LIGHTCORAL);
            }
        });

        box.getChildren().addAll(title, desc, budgetRow, slidersGrid, statusRow, presetsRow, actionRow);
        return box;
    }

    private static void updateCalculations(
            SystemEconomyReport report,
            TextField budgetField,
            Slider eduSlider, Slider lawSlider, Slider healthSlider, Slider infraSlider, Slider militiaSlider, Slider taxSlider,
            Label eduValLbl, Label lawValLbl, Label healthValLbl, Label infraValLbl, Label militiaValLbl, Label taxValLbl,
            Label sumLbl, Label balancePreviewLbl
    ) {
        eduValLbl.setText(String.format("%.1f%%", eduSlider.getValue()));
        lawValLbl.setText(String.format("%.1f%%", lawSlider.getValue()));
        healthValLbl.setText(String.format("%.1f%%", healthSlider.getValue()));
        infraValLbl.setText(String.format("%.1f%%", infraSlider.getValue()));
        militiaValLbl.setText(String.format("%.1f%%", militiaSlider.getValue()));
        taxValLbl.setText(String.format("%.1f%%", taxSlider.getValue()));

        double sum = eduSlider.getValue() + lawSlider.getValue() + healthSlider.getValue() + infraSlider.getValue() + militiaSlider.getValue();
        sumLbl.setText(String.format("Total allocation: %.1f%% %s", sum, Math.abs(sum - 100.0) < 0.1 ? "✓" : "⚠ (Normalized)"));
        sumLbl.setTextFill(Math.abs(sum - 100.0) < 0.1 ? Color.LIGHTGREEN : Color.GOLD);

        double budget = 0;
        try {
            budget = Double.parseDouble(budgetField.getText().trim());
        } catch (Exception ignored) {}

        double newTaxes = report.grossSystemOutput() * (taxSlider.getValue() / 100.0);
        double totalRev = newTaxes + report.corporateTariffs() + report.spaceElevatorFees() + report.miningRoyalties() + report.stateIndustryIncome();
        double totalExp = budget + report.governorAdministration() + report.stationMaintenance();
        double projectedBalance = totalRev - totalExp;

        balancePreviewLbl.setText(String.format("Projected net balance: %+,.0f ₵/turn", projectedBalance));
        balancePreviewLbl.setTextFill(projectedBalance >= 0 ? Color.LIGHTGREEN : Color.LIGHTCORAL);
    }

    public static Slider createSectorSlider(double initialVal) {
        Slider slider = new Slider(0, 100, initialVal);
        slider.setPrefWidth(260);
        slider.setShowTickLabels(false);
        slider.setShowTickMarks(true);
        slider.setMajorTickUnit(25);
        slider.setMinorTickCount(4);
        slider.setBlockIncrement(5);
        slider.setCursor(javafx.scene.Cursor.HAND);
        return slider;
    }

    public static Label createPercentLabel(double val) {
        Label lbl = new Label(String.format("%.1f%%", val));
        lbl.setTextFill(Color.GOLD);
        lbl.setFont(Font.font("Verdana", FontWeight.BOLD, 12));
        lbl.setPrefWidth(55);
        return lbl;
    }

    public static void addSliderRow(GridPane grid, int row, String name, Slider slider, Label valLbl, Color labelColor) {
        Label nameLbl = new Label(name);
        nameLbl.setTextFill(labelColor);
        nameLbl.setFont(Font.font("Verdana", FontWeight.BOLD, 11));
        nameLbl.setPrefWidth(280);

        grid.add(nameLbl, 0, row);
        grid.add(slider, 1, row);
        grid.add(valLbl, 2, row);
    }

    public static Button createRateButton(String title, TextField budgetField, double value) {
        Button btn = new Button(title);
        btn.setFont(Font.font("Verdana", 10));
        btn.setCursor(javafx.scene.Cursor.HAND);
        btn.setStyle("-fx-background-color: #34495e; -fx-text-fill: white; -fx-padding: 3 8 3 8; -fx-background-radius: 4;");
        btn.setOnAction(e -> budgetField.setText(String.format("%.0f", value)));
        return btn;
    }

    public static Button createPresetButton(String title, Slider s1, Slider s2, Slider s3, Slider s4, Slider s5,
                                            double v1, double v2, double v3, double v4, double v5) {
        Button btn = new Button(title);
        btn.setFont(Font.font("Verdana", 10));
        btn.setCursor(javafx.scene.Cursor.HAND);
        btn.setStyle("-fx-background-color: rgba(40, 60, 95, 0.8); -fx-text-fill: #dfe6e9; -fx-padding: 3 8 3 8; " +
                "-fx-background-radius: 4; -fx-border-color: #3498db; -fx-border-width: 1; -fx-border-radius: 4;");
        btn.setOnAction(e -> {
            s1.setValue(v1);
            s2.setValue(v2);
            s3.setValue(v3);
            s4.setValue(v4);
            s5.setValue(v5);
        });
        return btn;
    }
}
