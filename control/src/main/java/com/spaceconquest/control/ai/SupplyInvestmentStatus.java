package com.spaceconquest.control.ai;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.*;
import java.util.*;

/** Immutable advisory diagnostics calculated from one injected snapshot. No commands are executed. */
public record SupplyInvestmentStatus(List<Row> rows) {
    public record Row(String bodyId, String materialId, String applicationId, String action, String reason) {}
    public SupplyInvestmentStatus { rows = List.copyOf(rows); }
    public static SupplyInvestmentStatus inspect(GameState state, String empireId) {
        if (state == null) return new SupplyInvestmentStatus(List.of());
        var empire = state.empires().stream().filter(e -> e.id().equals(empireId)).findFirst().orElse(null);
        if (empire == null) return new SupplyInvestmentStatus(List.of());
        var rows = new ArrayList<Row>(); var goals = SupplyChainNeeds.goals(state, empire);
        for (var body : SurveyProgramPlanner.targets(state, empire)) {
            var teams = state.surveyTeams().stream().filter(t -> t.fleetId().isBlank() && t.empireId().equals(empire.id()) && t.bodyId().equals(body)).toList();
            if (teams.isEmpty()) rows.add(new Row(body, "", "", "Standing survey", SurveyOperations.status(state, empire.id(), body)));
            for (var team : teams) rows.add(new Row(body, "", team.applicationId(), "Standing survey",
                    team.completedPasses() + " passes; " + Math.round(team.progressWork()) + "/" + Math.round(team.capability().workPerPass())
                    + " work; setup remaining " + Math.round(team.installationRemaining())
                    + (team.blocker().isBlank() ? "; surveying" : "; " + team.blocker())));
        }
        for (var team : state.surveyTeams()) if (team.empireId().equals(empire.id()) && !team.fleetId().isBlank())
            rows.add(new Row(team.bodyId(), "", team.applicationId(), "Orbital survey",
                    team.fleetId() + "; " + team.completedPasses() + " passes; " + Math.round(team.progressWork()) + " work; "
                    + (team.blocker().isBlank() ? "mapping" : team.blocker())));
        if (OrbitalSurveyOperations.definition(state, empire.id()) != null
                && state.surveyTeams().stream().noneMatch(t -> t.empireId().equals(empire.id()) && !t.fleetId().isBlank()))
            rows.add(new Row("", "", "", "Orbital survey", "Need an available explorer with healthy sensors, electrical storage and equipment materials at its port"));
        for (var plan : state.supplyDevelopmentPlans()) if (plan.empireId().equals(empire.id()) && !plan.closed())
            rows.add(new Row(plan.sourceHubId() + " → " + plan.destinationHubId(), plan.materialId(), "", "Develop remote source",
                    SupplyDevelopmentProgress.next(state, plan).reason() + "; initial buyer funding " + plan.buyerFundingCredits() + " credits"));
        for (var goal : goals) {
            if (SupplyChainNeeds.installed(state, empire, goal.bodyId(), goal.applicationId())) {
                if (goal.materialId().isBlank()) rows.add(new Row(goal.bodyId(), "", goal.applicationId(), "Existing service", "Restore paid staffing and launch resources"));
                else {
                    var assessment = SupplyExpansionQuote.assess(state, empire, goal);
                    rows.add(new Row(goal.bodyId(), goal.materialId(), goal.applicationId(), "Expand supplier", assessment.reason()));
                }
            } else {
                var assessment = SupplyInvestmentQuote.assess(state, empire, goal.bodyId(), goal.applicationId());
                String reason = assessment.reason();
                int tier = FacilityManufacturingCapacity.minimumTier(state, empire.id(), goal.applicationId());
                if (SupplyInvestmentQuote.load(state, goal.bodyId(), goal.applicationId(), tier) > SupplyInvestmentQuote.freePower(state, goal.bodyId())) {
                    String power = SupplyInvestmentQuote.generation(state, empire, goal.bodyId());
                    reason += power == null ? "; no feasible generation application" : "; generation: "
                            + IndustryRecipeCatalog.applicationName(power) + " — " + SupplyInvestmentQuote.assess(state, empire, goal.bodyId(), power).reason();
                }
                rows.add(new Row(goal.bodyId(), goal.materialId(), goal.applicationId(), "Build supplier", reason));
            }
        }
        for (var hub : state.commercialHubs().stream().filter(h -> PowerInvestmentQuote.controls(state, empire, h.entityId()))
                .sorted(Comparator.comparing(CommercialHub::id)).limit(8).toList()) {
            boolean funded = state.marketAccounts().stream().anyMatch(a -> a.hubId().equals(hub.id()) && a.unsettledSalesCredits() > 0);
            for (var order : hub.activeOrders().values().stream().filter(o -> o.demandKg() > o.supplyKg())
                    .sorted(Comparator.comparing(MarketOrder::resourceId)).limit(12).toList()) {
                if (rows.size() >= 48) break;
                if (!funded || goals.stream().noneMatch(g -> g.bodyId().equals(hub.entityId()) && g.materialId().equals(order.resourceId())))
                    rows.add(new Row(hub.entityId(), order.resourceId(), "", "Unserved demand", !funded ? "No funded marketplace buyer"
                            : "No viable local production path; check sources, deposits and site conditions"));
            }
        }
        return new SupplyInvestmentStatus(rows);
    }
}
