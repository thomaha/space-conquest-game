package com.spaceconquest.frontend.empire;

import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.Moon;
import com.spaceconquest.engine.Population;
import com.spaceconquest.engine.economy.SystemEconomy;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

public class EconomyCards {

    public static VBox createEconomyOverviewCard(EmpireEconomyReport report, Empire empire) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(14));
        box.setStyle("-fx-background-color: rgba(30, 50, 90, 0.7); -fx-background-radius: 8; " +
                "-fx-border-color: #f39c12; -fx-border-width: 1.5; -fx-border-radius: 8;");

        HBox topRow = new HBox(12);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Text empireName = new Text(empire.name() + " — Imperial economy overview");
        empireName.setFill(Color.GOLD);
        empireName.setFont(Font.font("Verdana", FontWeight.BOLD, 18));

        Label structureBadge = new Label(empire.societyStructure());
        structureBadge.setStyle("-fx-background-color: #2c3e50; -fx-text-fill: #00cec9; " +
                "-fx-font-weight: bold; -fx-padding: 3 8 3 8; -fx-background-radius: 4; -fx-font-size: 11;");

        Label raceBadge = new Label("Primary race: " + empire.raceId());
        raceBadge.setStyle("-fx-background-color: #34495e; -fx-text-fill: #dfe6e9; " +
                "-fx-padding: 3 8 3 8; -fx-background-radius: 4; -fx-font-size: 11;");

        topRow.getChildren().addAll(empireName, structureBadge, raceBadge);

        GridPane metricsGrid = new GridPane();
        metricsGrid.setHgap(20);
        metricsGrid.setVgap(8);

        Label treasuryLbl = new Label(String.format("Liquid treasury: %,.0f ₵", report.treasuryCredits()));
        treasuryLbl.setTextFill(Color.GOLD);
        treasuryLbl.setFont(Font.font("Verdana", FontWeight.BOLD, 13));

        boolean positiveNet = report.netBudgetBalance() >= 0;
        Label netLbl = new Label(String.format("Net treasury flow: %+,.0f ₵/day", report.netBudgetBalance()));
        netLbl.setTextFill(positiveNet ? Color.LIGHTGREEN : Color.LIGHTCORAL);
        netLbl.setFont(Font.font("Verdana", FontWeight.BOLD, 13));

        Label incomeLbl = new Label(String.format("Treasury receipts: +%,.0f ₵/day", report.totalIncome()));
        incomeLbl.setTextFill(Color.LIGHTGREEN);
        incomeLbl.setFont(Font.font("Verdana", FontWeight.BOLD, 12));

        Label costLbl = new Label(String.format("Treasury expenditures: -%,.0f ₵/day", report.totalCosts()));
        costLbl.setTextFill(Color.LIGHTCORAL);
        costLbl.setFont(Font.font("Verdana", FontWeight.BOLD, 12));

        Label systemsLbl = new Label(String.format("Controlled star systems: %d", report.controlledSystemCount()));
        systemsLbl.setTextFill(Color.WHITE);
        systemsLbl.setFont(Font.font("Verdana", 11));

        Label coloniesLbl = new Label(String.format("Colonized worlds: %d", report.colonizedWorldCount()));
        coloniesLbl.setTextFill(Color.LIGHTCYAN);
        coloniesLbl.setFont(Font.font("Verdana", 11));

        Label popLbl = new Label(String.format("Imperial citizen population: %,d", report.totalPopulation()));
        popLbl.setTextFill(Color.LIGHTYELLOW);
        popLbl.setFont(Font.font("Verdana", 11));

        Label taxLbl = new Label(String.format("Corporate tariff rate: %.1f%%", report.corporateTaxRate() * 100.0));
        taxLbl.setTextFill(Color.LIGHTSKYBLUE);
        taxLbl.setFont(Font.font("Verdana", 11));

        metricsGrid.add(treasuryLbl, 0, 0);
        metricsGrid.add(netLbl, 1, 0);

        Label debtLbl = new Label(String.format("Imperial debt: %,.0f ₵", report.outstandingDebtCredits()));
        debtLbl.setTextFill(Color.LIGHTCORAL);
        metricsGrid.add(debtLbl, 2, 0);
        metricsGrid.add(incomeLbl, 2, 0);
        metricsGrid.add(costLbl, 3, 0);

        metricsGrid.add(systemsLbl, 0, 1);
        metricsGrid.add(coloniesLbl, 1, 1);
        metricsGrid.add(popLbl, 2, 1);
        metricsGrid.add(taxLbl, 3, 1);

        box.getChildren().addAll(topRow, metricsGrid);
        return box;
    }

    public static VBox createIncomesAndRevenuesCard(EmpireEconomyReport report) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: rgba(20, 45, 35, 0.7); -fx-background-radius: 8; " +
                "-fx-border-color: #2ecc71; -fx-border-width: 1.5; -fx-border-radius: 8;");

        Text title = new Text("Imperial treasury receipts (per day)");
        title.setFill(Color.LIGHTGREEN);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 14));

        Text subtitle = new Text("Credits actually received by the central treasury during the last day.");
        subtitle.setFill(Color.LIGHTGRAY);
        subtitle.setFont(Font.font("Verdana", 10));

        VBox list = new VBox(8);
        list.getChildren().add(createEconomyLineItem("Actual treasury receipts", String.format("+%,.0f ₵", report.totalIncome()), "Delivered couriers, electronic transfers, trade tariffs and industrial receipts.", Color.LIGHTGREEN));

        HBox totalRow = new HBox(8);
        totalRow.setAlignment(Pos.CENTER_RIGHT);
        totalRow.setPadding(new Insets(6, 0, 0, 0));
        Label totalLbl = new Label(String.format("Total received: +%,.0f ₵/day", report.totalIncome()));
        totalLbl.setTextFill(Color.LIGHTGREEN);
        totalLbl.setFont(Font.font("Verdana", FontWeight.BOLD, 13));
        totalRow.getChildren().add(totalLbl);

        box.getChildren().addAll(title, subtitle, list, totalRow);
        return box;
    }

    public static VBox createExpensesAndCostsCard(EmpireEconomyReport report) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: rgba(45, 20, 25, 0.7); -fx-background-radius: 8; " +
                "-fx-border-color: #e74c3c; -fx-border-width: 1.5; -fx-border-radius: 8;");

        Text title = new Text("Imperial treasury expenditures (per day)");
        title.setFill(Color.TOMATO);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 14));

        Text subtitle = new Text("Credits committed by the central treasury during the last day.");
        subtitle.setFill(Color.LIGHTGRAY);
        subtitle.setFont(Font.font("Verdana", 10));

        VBox list = new VBox(8);
        list.getChildren().add(createEconomyLineItem("Actual treasury expenditures", String.format("-%,.0f ₵", report.totalCosts()), "System subsidies and purchases or grants executed by commands.", Color.TOMATO));
        list.getChildren().add(createEconomyLineItem("Debt principal repaid", String.format("-%,.0f ₵", report.debtRepaidCredits()), "Cash used to settle earlier imperial obligations; excluded from current expenses.", Color.TOMATO));

        HBox totalRow = new HBox(8);
        totalRow.setAlignment(Pos.CENTER_RIGHT);
        totalRow.setPadding(new Insets(6, 0, 0, 0));
        Label totalLbl = new Label(String.format("Total committed: -%,.0f ₵/day", report.totalCosts()));
        totalLbl.setTextFill(Color.TOMATO);
        totalLbl.setFont(Font.font("Verdana", FontWeight.BOLD, 13));
        totalRow.getChildren().add(totalLbl);

        box.getChildren().addAll(title, subtitle, list, totalRow);
        return box;
    }

    public static VBox createColonyLedgerCard(EmpireEconomyReport report) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: rgba(20, 35, 60, 0.7); -fx-background-radius: 8; " +
                "-fx-border-color: #3498db; -fx-border-width: 1.5; -fx-border-radius: 8;");

        Text title = new Text("Colonial economy ledger (" + report.colonyEntries().size() + " colonized worlds)");
        title.setFill(Color.LIGHTSKYBLUE);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
        box.getChildren().add(title);

        if (report.colonyEntries().isEmpty()) {
            Text empty = new Text("No sovereign colonies founded. Deploy colony ships to establish revenue-generating worlds.");
            empty.setFill(Color.LIGHTGRAY);
            box.getChildren().add(empty);
        } else {
            VBox list = new VBox(6);
            for (ColonyEconomyEntry entry : report.colonyEntries()) {
                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(6, 10, 6, 10));
                row.setStyle("-fx-background-color: rgba(15, 25, 45, 0.6); -fx-background-radius: 4;");

                Text name = new Text(entry.bodyName() + (entry.isMoon() ? " (Moon)" : ""));
                name.setFill(Color.WHITE);
                name.setFont(Font.font("Verdana", FontWeight.BOLD, 12));
                name.setWrappingWidth(130);

                Text sys = new Text("System: " + entry.systemName());
                sys.setFill(Color.LIGHTGRAY);
                sys.setFont(Font.font("Verdana", 11));
                sys.setWrappingWidth(110);

                Text pop = new Text(String.format("Pop: %,d", entry.population()));
                pop.setFill(Color.LIGHTYELLOW);
                pop.setFont(Font.font("Verdana", 11));
                pop.setWrappingWidth(110);

                Text tax = new Text(String.format("Taxes: +%,.0f ₵", entry.taxCollectedCredits()));
                tax.setFill(Color.LIGHTGREEN);
                tax.setFont(Font.font("Verdana", 11));
                tax.setWrappingWidth(120);

                Text gov = new Text(String.format("Gov: -%,.0f ₵", entry.localGovernanceCostCredits()));
                gov.setFill(Color.TOMATO);
                gov.setFont(Font.font("Verdana", 11));
                gov.setWrappingWidth(110);

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                boolean positive = entry.netContributionCredits() >= 0;
                Text net = new Text(String.format("Net: %+,.0f ₵/turn", entry.netContributionCredits()));
                net.setFill(positive ? Color.LIGHTGREEN : Color.LIGHTCORAL);
                net.setFont(Font.font("Verdana", FontWeight.BOLD, 11));

                Text debt = new Text(String.format("Debt: %,.0f ₵", entry.outstandingDebtCredits()));
                debt.setFill(Color.LIGHTCORAL);

                row.getChildren().addAll(name, sys, pop, tax, gov, spacer, net, debt);
                list.getChildren().add(row);
            }
            box.getChildren().add(list);
        }

        return box;
    }

    public static VBox createCorporateEconomyCard(EmpireEconomyReport report) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: rgba(20, 35, 60, 0.7); -fx-background-radius: 8; " +
                "-fx-border-color: #f1c40f; -fx-border-width: 1.5; -fx-border-radius: 8;");

        Text title = new Text("Private corporations and commercial registry (" + report.corporateEntries().size() + " enterprises)");
        title.setFill(Color.GOLD);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
        box.getChildren().add(title);

        if (report.corporateEntries().isEmpty()) {
            Text empty = new Text("No private corporations registered in sovereign space.");
            empty.setFill(Color.LIGHTGRAY);
            box.getChildren().add(empty);
        } else {
            VBox list = new VBox(6);
            for (CorporateEconomyEntry entry : report.corporateEntries()) {
                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(6, 10, 6, 10));
                row.setStyle("-fx-background-color: rgba(15, 25, 45, 0.6); -fx-background-radius: 4;");

                Text name = new Text(entry.corporationName());
                name.setFill(Color.WHITE);
                name.setFont(Font.font("Verdana", FontWeight.BOLD, 12));
                name.setWrappingWidth(180);

                Label orientationBadge = new Label(entry.marketOrientation());
                orientationBadge.setStyle("-fx-background-color: #2c3e50; -fx-text-fill: #f1c40f; -fx-padding: 2 6 2 6; -fx-background-radius: 3; -fx-font-size: 10;");

                Text hq = new Text("HQ: " + entry.hqEntityId().toUpperCase());
                hq.setFill(Color.LIGHTGRAY);
                hq.setFont(Font.font("Verdana", 11));
                hq.setWrappingWidth(100);

                Text cap = new Text(String.format("Capital: %,.0f ₵", entry.liquidCapital()));
                cap.setFill(Color.GOLD);
                cap.setFont(Font.font("Verdana", 11));
                cap.setWrappingWidth(130);

                Text fac = new Text(String.format("Facilities: %d", entry.ownedFacilitiesCount()));
                fac.setFill(Color.LIGHTCYAN);
                fac.setFont(Font.font("Verdana", 11));

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                Text tariff = new Text(String.format("Estimated tariffs: +%,.0f ₵/turn", entry.estimatedTariffPaid()));
                tariff.setFill(Color.LIGHTGREEN);
                tariff.setFont(Font.font("Verdana", FontWeight.BOLD, 11));

                row.getChildren().addAll(name, orientationBadge, hq, cap, fac, spacer, tariff);
                list.getChildren().add(row);
            }
            box.getChildren().add(list);
        }

        return box;
    }

    public static VBox createSystemEconomyOverviewCard(SystemEconomyReport report) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(14));
        box.setStyle("-fx-background-color: rgba(30, 50, 90, 0.7); -fx-background-radius: 8; " +
                "-fx-border-color: #3498db; -fx-border-width: 1.5; -fx-border-radius: 8;");

        HBox topRow = new HBox(12);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Text title = new Text("Star system economy overview — " + report.systemName() + " system");
        title.setFill(Color.GOLD);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 18));

        Label empBadge = new Label("Empire: " + report.empireId().toUpperCase());
        empBadge.setStyle("-fx-background-color: #2c3e50; -fx-text-fill: #00cec9; -fx-font-weight: bold; -fx-padding: 3 8 3 8; -fx-background-radius: 4; -fx-font-size: 11;");

        topRow.getChildren().addAll(title, empBadge);

        GridPane metricsGrid = new GridPane();
        metricsGrid.setHgap(20);
        metricsGrid.setVgap(8);

        Label popLbl = new Label(String.format("System Population: %,d", report.systemPopulation()));
        popLbl.setTextFill(Color.LIGHTYELLOW);
        popLbl.setFont(Font.font("Verdana", FontWeight.BOLD, 13));

        Label grossLbl = new Label(String.format("Gross Economic Output: %,.0f ₵/turn", report.grossSystemOutput()));
        grossLbl.setTextFill(Color.LIGHTCYAN);
        grossLbl.setFont(Font.font("Verdana", FontWeight.BOLD, 12));

        Label revLbl = new Label(String.format("System Revenues: +%,.0f ₵/turn", report.totalRevenues()));
        revLbl.setTextFill(Color.LIGHTGREEN);
        revLbl.setFont(Font.font("Verdana", FontWeight.BOLD, 12));

        Label expLbl = new Label(String.format("System Expenditures: -%,.0f ₵/turn", report.totalExpenditures()));
        expLbl.setTextFill(Color.LIGHTCORAL);
        expLbl.setFont(Font.font("Verdana", FontWeight.BOLD, 12));

        boolean positiveNet = report.netSystemBalance() >= 0;
        Label netLbl = new Label(String.format("Net system balance: %+,.0f ₵/turn", report.netSystemBalance()));
        netLbl.setTextFill(positiveNet ? Color.LIGHTGREEN : Color.LIGHTCORAL);
        netLbl.setFont(Font.font("Verdana", FontWeight.BOLD, 13));

        Label bodiesLbl = new Label(String.format("Colonized worlds: %d", report.colonizedBodiesCount()));
        bodiesLbl.setTextFill(Color.WHITE);
        bodiesLbl.setFont(Font.font("Verdana", 11));

        Label taxLbl = new Label(String.format("Colonial income tax rate: %.1f%%", report.taxRate() * 100.0));
        taxLbl.setTextFill(Color.GOLD);
        taxLbl.setFont(Font.font("Verdana", 11));

        Label budgetLbl = new Label(String.format("Public sector budget: %,.0f ₵", report.publicSectorFunding()));
        budgetLbl.setTextFill(Color.LIGHTSKYBLUE);
        budgetLbl.setFont(Font.font("Verdana", 11));

        Label debtLbl = new Label(String.format("Local debt: %,.0f ₵", report.outstandingDebtCredits()));
        debtLbl.setTextFill(Color.LIGHTCORAL);

        metricsGrid.add(popLbl, 0, 0);
        metricsGrid.add(grossLbl, 1, 0);
        metricsGrid.add(revLbl, 2, 0);
        metricsGrid.add(expLbl, 3, 0);

        metricsGrid.add(netLbl, 0, 1);
        metricsGrid.add(bodiesLbl, 1, 1);
        metricsGrid.add(taxLbl, 2, 1);
        metricsGrid.add(budgetLbl, 3, 1);
        metricsGrid.add(debtLbl, 0, 2);

        box.getChildren().addAll(topRow, metricsGrid);
        return box;
    }

    public static VBox createSystemRevenuesCard(SystemEconomyReport report) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: rgba(20, 45, 30, 0.7); -fx-background-radius: 8; -fx-border-color: #2ecc71; -fx-border-width: 1; -fx-border-radius: 8;");

        Text header = new Text(String.format("SYSTEM REVENUE LINE ITEMS (+%,.0f ₵/turn)", report.totalRevenues()));
        header.setFill(Color.LIGHTGREEN);
        header.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
        box.getChildren().add(header);

        VBox itemsBox = new VBox(6);
        itemsBox.getChildren().add(createEconomyLineItem("Colonial taxes (" + String.format("%.1f%%", report.taxRate() * 100.0) + ")", String.format("+%,.0f ₵", report.colonialTaxes()), "Direct income and production levies from system planetary populations.", Color.LIGHTGREEN));
        itemsBox.getChildren().add(createEconomyLineItem("Corporate commerce tariffs", String.format("+%,.0f ₵", report.corporateTariffs()), "Tariffs collected from corporation headquarters and commercial facilities.", Color.LIGHTGREEN));
        itemsBox.getChildren().add(createEconomyLineItem(
                report.fromBalanceSheets() ? "Docking and handling fees" : "Space elevator transit fees",
                String.format("+%,.0f ₵", report.spaceElevatorFees()),
                report.fromBalanceSheets() ? "Fees collected by local commercial hubs." : "Orbital freight transfer tolls in this system.",
                Color.LIGHTGREEN));
        if (!report.fromBalanceSheets()) {
            itemsBox.getChildren().add(createEconomyLineItem("Geological mining royalties", String.format("+%,.0f ₵", report.miningRoyalties()), "Mining concessions from active geological resource veins.", Color.LIGHTGREEN));
            itemsBox.getChildren().add(createEconomyLineItem("State industrial output dividends", String.format("+%,.0f ₵", report.stateIndustryIncome()), "Dividends from public manufacturing facilities.", Color.LIGHTGREEN));
        }

        box.getChildren().add(itemsBox);
        return box;
    }

    public static VBox createSystemExpensesCard(SystemEconomyReport report) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: rgba(35, 20, 40, 0.7); -fx-background-radius: 8; -fx-border-color: #e74c3c; -fx-border-width: 1; -fx-border-radius: 8;");

        Text header = new Text(String.format("SYSTEM EXPENDITURE LINE ITEMS (-%,.0f ₵/turn)", report.totalExpenditures()));
        header.setFill(Color.TOMATO);
        header.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
        box.getChildren().add(header);

        VBox itemsBox = new VBox(6);
        itemsBox.getChildren().add(createEconomyLineItem("Public sector funding budget", String.format("-%,.0f ₵", report.publicSectorFunding()), "Allocated budget distributed across education, law, health, infrastructure and militias.", Color.TOMATO));
        itemsBox.getChildren().add(createEconomyLineItem(
                report.fromBalanceSheets() ? "State workforce salaries" : "Governor & municipal administration",
                String.format("-%,.0f ₵", report.governorAdministration()),
                report.fromBalanceSheets() ? "Salaries recorded on local balance sheets." : "Local administrative salaries and system governance expenses.",
                Color.TOMATO));
        itemsBox.getChildren().add(createEconomyLineItem(
                report.fromBalanceSheets() ? "Facility, welfare and infrastructure upkeep" : "Orbital station maintenance",
                String.format("-%,.0f ₵", report.stationMaintenance()),
                report.fromBalanceSheets() ? "Other local operating expenses recorded by municipalities." : "Upkeep and repairs for orbital starbases in this system.",
                Color.TOMATO));

        box.getChildren().add(itemsBox);
        return box;
    }

    public static VBox createSystemCelestialBodiesLedgerCard(SystemEconomyReport report, EmpireView parent) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: rgba(20, 30, 50, 0.7); -fx-background-radius: 8; -fx-border-color: #3498db; -fx-border-width: 1; -fx-border-radius: 8;");

        Text header = new Text("Celestial bodies in " + report.systemName() + " system");
        header.setFill(Color.LIGHTSKYBLUE);
        header.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
        box.getChildren().add(header);

        SolarSystem system = parent.getSystems().stream()
                .filter(s -> s.id().equals(report.systemId()))
                .findFirst()
                .orElse(null);

        if (system != null && system.planets() != null) {
            VBox list = new VBox(6);
            for (Planet p : system.planets()) {
                long pPop = 0;
                if (p.populations() != null) {
                    for (Population pop : p.populations()) pPop += pop.totalCount();
                }
                double pGross = pPop * 0.005;
                double pTax = pGross * report.taxRate();

                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(6, 10, 6, 10));
                row.setStyle("-fx-background-color: rgba(15, 25, 45, 0.6); -fx-background-radius: 4;");

                Text pName = new Text("Planet " + p.name());
                pName.setFill(Color.WHITE);
                pName.setFont(Font.font("Verdana", FontWeight.BOLD, 12));
                pName.setWrappingWidth(140);

                Text type = new Text(p.type());
                type.setFill(Color.LIGHTCYAN);
                type.setFont(Font.font("Verdana", 11));
                type.setWrappingWidth(110);

                Text popTxt = new Text(pPop > 0 ? String.format("Pop: %,d", pPop) : "Uncolonized");
                popTxt.setFill(pPop > 0 ? Color.LIGHTGREEN : Color.LIGHTGRAY);
                popTxt.setFont(Font.font("Verdana", 11));
                popTxt.setWrappingWidth(120);

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                Text taxTxt = new Text(pPop > 0 ? String.format("Est. Tax: +%,.0f ₵/turn", pTax) : "0 ₵");
                taxTxt.setFill(pPop > 0 ? Color.LIGHTGREEN : Color.GRAY);
                taxTxt.setFont(Font.font("Verdana", FontWeight.BOLD, 11));

                row.getChildren().addAll(pName, type, popTxt, spacer, taxTxt);
                list.getChildren().add(row);
            }
            box.getChildren().add(list);
        }

        return box;
    }

    public static VBox createSovereignEmpireCard(Empire empire) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(14));
        box.setStyle("-fx-background-color: rgba(30, 50, 90, 0.7); -fx-background-radius: 8; " +
                "-fx-border-color: #f1c40f; -fx-border-width: 1.5; -fx-border-radius: 8;");

        HBox topRow = new HBox(12);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Text empireName = new Text(empire.name() + " (" + empire.id() + ")");
        empireName.setFill(Color.GOLD);
        empireName.setFont(Font.font("Verdana", FontWeight.BOLD, 17));

        Label structureBadge = new Label(empire.societyStructure());
        structureBadge.setStyle("-fx-background-color: #2c3e50; -fx-text-fill: #00cec9; " +
                "-fx-font-weight: bold; -fx-padding: 3 8 3 8; -fx-background-radius: 4; -fx-font-size: 11;");

        Label raceBadge = new Label("Primary race: " + empire.raceId());
        raceBadge.setStyle("-fx-background-color: #34495e; -fx-text-fill: #dfe6e9; " +
                "-fx-padding: 3 8 3 8; -fx-background-radius: 4; -fx-font-size: 11;");

        topRow.getChildren().addAll(empireName, structureBadge, raceBadge);

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(8);

        grid.add(createMetricItem("Treasury Reserves:", String.format("%,.0f credits", empire.treasuryCredits()), Color.GOLD), 0, 0);
        grid.add(createMetricItem("Corporate Tax Rate:", String.format("%.1f%%", empire.corporateTaxRate() * 100.0), Color.LIGHTSKYBLUE), 1, 0);
        grid.add(createMetricItem("Controlled Systems:", String.valueOf(empire.controlledSystemIds().size()), Color.LIGHTGREEN), 2, 0);
        grid.add(createMetricItem("Appointed Ministries:", String.valueOf(empire.ministries().size()), Color.WHITE), 3, 0);

        box.getChildren().addAll(topRow, grid);
        return box;
    }

    public static VBox createEconomyLineItem(String title, String amount, String description, Color amountColor) {
        VBox box = new VBox(2);
        HBox top = new HBox(8);
        top.setAlignment(Pos.CENTER_LEFT);

        Text t = new Text(title);
        t.setFill(Color.WHITE);
        t.setFont(Font.font("Verdana", FontWeight.BOLD, 11));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Text a = new Text(amount);
        a.setFill(amountColor);
        a.setFont(Font.font("Verdana", FontWeight.BOLD, 12));

        top.getChildren().addAll(t, spacer, a);

        Text d = new Text(description);
        d.setFill(Color.LIGHTGRAY);
        d.setFont(Font.font("Verdana", 10));

        box.getChildren().addAll(top, d);
        return box;
    }

    public static VBox createMetricItem(String label, String value, Color valueColor) {
        VBox box = new VBox(2);
        Label l = new Label(label);
        l.setTextFill(Color.LIGHTGRAY);
        l.setFont(Font.font("Verdana", 10));

        Label v = new Label(value);
        v.setTextFill(valueColor);
        v.setFont(Font.font("Verdana", FontWeight.BOLD, 12));

        box.getChildren().addAll(l, v);
        return box;
    }
    public static VBox createEmpireTreasuryCard(Empire empire, EmpireEconomyReport report) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(14));
        box.setStyle("-fx-background-color: rgba(30, 50, 90, 0.7); -fx-background-radius: 8; " +
                "-fx-border-color: #f39c12; -fx-border-width: 1.5; -fx-border-radius: 8;");

        HBox topRow = new HBox(12);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Text empireName = new Text(empire.name() + " — Imperial economy overview");
        empireName.setFill(Color.GOLD);
        empireName.setFont(Font.font("Verdana", FontWeight.BOLD, 18));

        Label structureBadge = new Label(empire.societyStructure());
        structureBadge.setStyle("-fx-background-color: #2c3e50; -fx-text-fill: #00cec9; " +
                "-fx-font-weight: bold; -fx-padding: 3 8 3 8; -fx-background-radius: 4; -fx-font-size: 11;");

        topRow.getChildren().addAll(empireName, structureBadge);

        GridPane metricsGrid = new GridPane();
        metricsGrid.setHgap(20);
        metricsGrid.setVgap(8);

        Label treasuryLbl = new Label(String.format("Liquid treasury: %,.0f ₵", report.treasuryCredits()));
        treasuryLbl.setTextFill(Color.GOLD);
        treasuryLbl.setFont(Font.font("Verdana", FontWeight.BOLD, 13));

        boolean positiveNet = report.netBudgetBalance() >= 0;
        Label netLbl = new Label(String.format("Net treasury flow: %+,.0f ₵/day", report.netBudgetBalance()));
        netLbl.setTextFill(positiveNet ? Color.LIGHTGREEN : Color.LIGHTCORAL);
        netLbl.setFont(Font.font("Verdana", FontWeight.BOLD, 13));

        metricsGrid.add(treasuryLbl, 0, 0);
        metricsGrid.add(netLbl, 1, 0);

        Label debtLbl = new Label(String.format("Imperial debt: %,.0f ₵", report.outstandingDebtCredits()));
        debtLbl.setTextFill(Color.LIGHTCORAL);
        metricsGrid.add(debtLbl, 2, 0);

        box.getChildren().addAll(topRow, metricsGrid);
        return box;
    }

    public static VBox createMacroFiscalPolicyCard(Empire playerEmpire, EmpireEconomyReport report, EmpireView parent) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: rgba(20, 35, 65, 0.65); -fx-background-radius: 8; " +
                "-fx-border-color: rgba(120, 170, 255, 0.4); -fx-border-width: 1; -fx-border-radius: 8;");
        Text title = new Text("Macro fiscal policy & imperial tax code");
        title.setFill(Color.AQUA);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
        box.getChildren().add(title);
        return box;
    }

    public static VBox createRevenuesAndIncomesCard(EmpireEconomyReport report) {
        return createIncomesAndRevenuesCard(report);
    }
}
