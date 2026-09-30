package com.spaceconquest.frontend.empire;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.Population;
import com.spaceconquest.engine.economy.HouseholdAccount;
import com.spaceconquest.frontend.PlanetaryBodyEntry;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Snapshot-only demographic and household report for one planetary body. */
final class PlanetPeoplePanel {
    VBox build(PlanetaryBodyEntry body, GameState state) {
        VBox content = new VBox(12);
        List<Population> populations = body.isMoon()
                ? (body.moon() == null ? List.of() : body.moon().populations())
                : (body.planet() == null ? List.of() : body.planet().populations());
        List<HouseholdAccount> households = state == null ? List.of() : state.householdAccounts().stream()
                .filter(account -> body.id().equals(account.bodyId())).toList();
        content.getChildren().add(demographics(populations));
        content.getChildren().add(households(households));
        if (!households.isEmpty()) content.getChildren().add(householdGroups(households));
        return content;
    }

    private VBox demographics(List<Population> populations) {
        VBox card = card("Population and age groups");
        if (populations == null || populations.isEmpty()) {
            row(card, "Population", "No residents recorded");
            return card;
        }
        long children = 0;
        long adults = 0;
        long elders = 0;
        for (Population population : populations) {
            if (population.ageGroups() == null) continue;
            long raceCount = population.totalCount();
            row(card, population.raceId(), String.format("%,d people", raceCount));
            for (Map.Entry<Integer, Long> age : population.ageGroups().entrySet()) {
                if (age.getKey() < 18) children += age.getValue();
                else if (age.getKey() < 65) adults += age.getValue();
                else elders += age.getValue();
            }
            population.ageGroups().entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(age -> row(card, "  " + population.raceId() + " age " + age.getKey(),
                            String.format("%,d", age.getValue())));
        }
        row(card, "Under 18", String.format("%,d", children));
        row(card, "18 to 64", String.format("%,d", adults));
        row(card, "65 and older", String.format("%,d", elders));
        return card;
    }

    private VBox householdGroups(List<HouseholdAccount> accounts) {
        VBox card = card("Household groups");
        accounts.stream().filter(account -> account.headcount() > 0)
                .sorted(java.util.Comparator.comparing(HouseholdAccount::raceId)
                        .thenComparing(HouseholdAccount::professionId))
                .forEach(account -> {
                    String group = account.raceId() + " / " + account.professionId().replace('_', ' ');
                    row(card, group, String.format("%,d people; %,d public jobs; %,d industry jobs; %,d unemployed",
                            account.headcount(), account.employment().publicWorkers(),
                            account.employment().industryWorkers(), account.employment().unemployedWorkers()));
                    row(card, "  Wellbeing and basic goods met", percent(account.wellbeing().wellbeingIndex())
                            + " / " + percent(account.wellbeing().materialCoverage()));
                    row(card, "  Wages and savings", credits(account.wageIncomeCredits())
                            + " / " + credits(account.savingsCredits()));
                });
        return card;
    }

    private VBox households(List<HouseholdAccount> accounts) {
        VBox card = card("Household economy and wellbeing");
        if (accounts.isEmpty()) {
            row(card, "Household data", "Unavailable before household settlement or for collective allocation");
            return card;
        }
        long people = accounts.stream().mapToLong(HouseholdAccount::headcount).sum();
        long working = accounts.stream().mapToLong(a -> a.employment().workingAge()).sum();
        long publicJobs = accounts.stream().mapToLong(a -> a.employment().publicWorkers()).sum();
        long industryJobs = accounts.stream().mapToLong(a -> a.employment().industryWorkers()).sum();
        long unemployed = accounts.stream().mapToLong(a -> a.employment().unemployedWorkers()).sum();
        row(card, "Wellbeing", percent(weighted(accounts, Metric.WELLBEING, people)));
        row(card, "Basic goods met", percent(weighted(accounts, Metric.MATERIAL, people)));
        row(card, "Electricity met", percent(weighted(accounts, Metric.ELECTRICITY, people)));
        row(card, "Secondary needs met", percent(weighted(accounts, Metric.SECONDARY, people)));
        row(card, "Luxury needs met", percent(weighted(accounts, Metric.LUXURY, people)));
        row(card, "Working age in household groups", String.format("%,d", working));
        row(card, "Public jobs", String.format("%,d", publicJobs));
        row(card, "Industry jobs", String.format("%,d", industryJobs));
        row(card, "Unemployed", String.format("%,d (%.1f%%)", unemployed,
                working == 0 ? 0.0 : 100.0 * unemployed / working));
        row(card, "Wages last day", credits(accounts.stream().mapToDouble(HouseholdAccount::wageIncomeCredits).sum()));
        row(card, "Welfare last day", credits(accounts.stream().mapToDouble(HouseholdAccount::welfareIncomeCredits).sum()));
        row(card, "Income tax last day", credits(accounts.stream().mapToDouble(HouseholdAccount::incomeTaxPaidCredits).sum()));
        row(card, "Goods and power spending last day", credits(accounts.stream().mapToDouble(
                a -> a.marketSpendingCredits() + a.electricitySpendingCredits()).sum()));
        row(card, "Household savings", credits(accounts.stream().mapToDouble(HouseholdAccount::savingsCredits).sum()));
        Map<String, Double> unmet = new TreeMap<>();
        for (HouseholdAccount account : accounts) {
            account.unmetBasicKg().forEach((good, kg) -> unmet.merge(good, kg, Double::sum));
        }
        unmet.forEach((good, kg) -> {
            if (kg > 0.001) row(card, "Unmet " + good.replace('_', ' '), String.format("%,.1f kg last day", kg));
        });
        return card;
    }

    private enum Metric { WELLBEING, MATERIAL, ELECTRICITY, SECONDARY, LUXURY }

    private double weighted(List<HouseholdAccount> accounts, Metric metric, long total) {
        if (total == 0) return 0.0;
        double sum = 0.0;
        for (HouseholdAccount account : accounts) {
            double value = switch (metric) {
                case WELLBEING -> account.wellbeing().wellbeingIndex();
                case MATERIAL -> account.wellbeing().materialCoverage();
                case ELECTRICITY -> account.wellbeing().electricityCoverage();
                case SECONDARY -> account.secondaryNeedsMetFraction();
                case LUXURY -> account.luxuryNeedsMetFraction();
            };
            sum += value * account.headcount();
        }
        return sum / total;
    }

    private VBox card(String title) {
        VBox box = new VBox(6);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: rgba(20, 35, 65, 0.6); -fx-background-radius: 6;");
        Label heading = new Label(title);
        heading.setTextFill(Color.AQUA);
        box.getChildren().add(heading);
        return box;
    }

    private void row(VBox card, String name, String value) {
        Label label = new Label(name + ": " + value);
        label.setTextFill(Color.WHITE);
        label.setWrapText(true);
        card.getChildren().add(label);
    }

    private String percent(double fraction) {
        return String.format("%.1f%%", fraction * 100.0);
    }

    private String credits(double amount) {
        return String.format("%,.2f credits", amount);
    }
}
