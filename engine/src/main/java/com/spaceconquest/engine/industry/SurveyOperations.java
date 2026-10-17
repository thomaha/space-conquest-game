package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.EconomicOwnership;
import com.spaceconquest.engine.technology.ApplicationProduction;
import java.util.*;

/** Standing surface surveys use local payroll, bought supplies and repeatable discovery passes. */
public final class SurveyOperations {
    public static final double TREASURY_RESERVE = 5000;
    private SurveyOperations() {}
    public static boolean controls(GameState state, String owner, String body) {
        String system = ConstructionMaterials.systemForBody(state, body);
        if (system == null) return false;
        return state.empires().stream().anyMatch(e -> e.id().equals(owner) && e.controlledSystemIds().contains(system))
                && state.solarSystems().stream().filter(s -> s.id().equals(system)).flatMap(s -> s.planets().stream())
                .anyMatch(p -> p.id().equals(body) || p.moons().stream().anyMatch(m -> m.id().equals(body)));
    }
    public static SurveyCapability.Definition definition(GameState state, String owner) {
        var empire = state.empires().stream().filter(e -> e.id().equals(owner)).findFirst().orElse(null);
        if (empire == null) return null;
        return SurveyCapability.all().stream().filter(d -> d.capability().mode() == SurveyCapability.Mode.SURFACE
                && empire.unlockedTechIds().contains(d.applicationId()) && empire.unlockedTechIds().contains(d.technologyId()))
                .findFirst().orElse(null);
    }
    public static double reserve(SurveyCapability capability) {
        return capability.equipment().referenceCrew()
                * Profession.getBaseWageForProfession(capability.equipment().operatorProfession()) * 14;
    }
    private static boolean workforce(GameState state, String owner, String body, SurveyCapability cap) {
        return cap.equipment().autonomousFraction() > 0 || state.householdAccounts().stream()
                .anyMatch(a -> a.empireId().equals(owner) && a.bodyId().equals(body) && a.headcount() > 0);
    }
    /** Creates one team at a time and restores payroll reserves. Purchases commit only complete bills. */
    public static GameState maintain(GameState state, String owner, String body, int requestedTeams) {
        if (state == null || body == null || owner == null || !controls(state, owner, body)) return state;
        var definition = definition(state, owner);
        if (definition == null) return state;
        var cap = definition.capability();
        if (!workforce(state, owner, body, cap)) return state;
        var current = state;
        var existing = state.surveyTeams().stream().filter(t -> t.fleetId().isBlank() && t.empireId().equals(owner) && t.bodyId().equals(body)).toList();
        for (var team : existing) current = fund(current, team);
        if (existing.size() >= Math.clamp(requestedTeams, 1, 3)) return current;
        if (EconomicOwnership.cash(current, owner) < TREASURY_RESERVE + reserve(cap)) return current;
        var purchase = ConstructionMaterials.buyUpTo(current, body, owner, cap.equipmentMaterialsKg());
        if (!purchase.covers(cap.equipmentMaterialsKg())
                || EconomicOwnership.cash(purchase.state(), owner) < TREASURY_RESERVE + reserve(cap)) return current;
        String id = "survey:" + owner + ":" + body + ":" + existing.size();
        var team = new SurveyTeam(id, owner, body, definition.applicationId(), cap, cap.installationWork(),
                0, 0, state.turn(), 0, "Waiting for local payroll");
        var teams = new ArrayList<>(current.surveyTeams()); teams.add(team);
        return fund(purchase.state().toBuilder().surveyTeams(teams).build(), team);
    }
    private static GameState fund(GameState state, SurveyTeam team) {
        var account = account(state, team.id());
        double target = reserve(team.capability());
        if (account.operatingCashCredits() >= target / 2) return state;
        double amount = Math.min(target - account.operatingCashCredits(),
                Math.max(0, EconomicOwnership.cash(state, team.empireId()) - TREASURY_RESERVE));
        if (amount <= 0) return state;
        return replaceAccount(treasury(state, team.empireId(), -amount),
                account.withOperatingCash(account.operatingCashCredits() + amount));
    }
    private static IndustryAccount account(GameState state, String id) {
        return state.industryAccounts().stream().filter(a -> a.facilityId().equals(id)).findFirst().orElse(IndustryAccount.empty(id));
    }
    private static GameState replaceAccount(GameState state, IndustryAccount account) {
        var accounts = new ArrayList<>(state.industryAccounts()); accounts.removeIf(a -> a.facilityId().equals(account.facilityId()));
        accounts.add(account); return state.toBuilder().industryAccounts(accounts).build();
    }
    private static GameState treasury(GameState state, String owner, double change) {
        return state.withEmpires(state.empires().stream().map(e -> !e.id().equals(owner) ? e
                : new Empire(e.id(), e.name(), e.raceId(), e.societyStructure(), e.treasuryCredits() + change,
                e.corporateTaxRate(), e.controlledSystemIds(), e.ministries(), e.systemGovernorAssignments(),
                e.unlockedTechIds(), e.activeShipDesignIds())).toList());
    }
    public static GameState advance(GameState state, Map<String, Integer> paidWorkers) {
        state = OrbitalSurveyOperations.advance(state);
        var current = state; var teams = new ArrayList<SurveyTeam>();
        for (var team : state.surveyTeams()) {
            if (!team.fleetId().isBlank()) { teams.add(team); continue; }
            if (team.lastWorkedTurn() >= state.turn()) { teams.add(team); continue; }
            String blocker = controls(current, team.empireId(), team.bodyId()) ? "" : "No access to survey site";
            var cap = team.capability(); var gear = cap.equipment();
            int workers = Math.min(team.payrollJob().allocatedWorkers(), Math.max(0, paidWorkers.getOrDefault(team.id(), 0)));
            double effect = ApplicationProduction.modifiersForOwner(current, team.empireId(), team.applicationId()).effectMultiplier();
            double work = Math.min(gear.ratedWorkPerDay() * gear.staffedFraction(workers) * Math.max(0, effect),
                    team.installationRemaining() + cap.workPerPass() - team.progressWork());
            if (work <= 0 && blocker.isEmpty()) blocker = "No paid local survey crew";
            if (blocker.isEmpty()) {
                var bill = new TreeMap<String, Double>(); double units = work;
                cap.suppliesKgPerWorkUnit().forEach((id, kg) -> bill.put(id, kg * units));
                var purchase = ConstructionMaterials.buyUpTo(current, team.bodyId(), team.empireId(), bill);
                if (!purchase.covers(bill)) blocker = "Field supplies or treasury funding unavailable";
                else current = purchase.state();
            }
            if (!blocker.isEmpty()) work = 0;
            double setup = Math.max(0, team.installationRemaining() - work);
            double progress = team.progressWork() + Math.max(0, work - team.installationRemaining());
            long passes = team.completedPasses();
            if (progress + 1e-8 >= cap.workPerPass()) {
                progress = 0; passes++;
                var random = new Random(31L * team.id().hashCode() + passes * 104729L);
                var deposits = new ProspectingProcessor(random).executeProspectingSurvey(team.bodyId(), team.empireId(),
                        cap.discoveryProbability(), 1, current.geologicalDeposits());
                current = current.toBuilder().geologicalDeposits(deposits).build();
            }
            teams.add(new SurveyTeam(team.id(), team.empireId(), team.bodyId(), team.applicationId(), cap,
                    setup, progress, passes, state.turn(), team.totalWork() + work, blocker));
            current = replaceAccount(current, account(current, team.id()).withPaidWorkers(workers));
        }
        return current.toBuilder().surveyTeams(teams).build();
    }
    public static String status(GameState state, String owner, String body) {
        if (!controls(state, owner, body)) return "No access to survey site";
        var definition = definition(state, owner);
        if (definition == null) return "Research a surface survey application";
        if (!workforce(state, owner, body, definition.capability())) return "No local workforce";
        if (EconomicOwnership.cash(state, owner) < TREASURY_RESERVE + reserve(definition.capability())) return "Survey funding below reserve";
        var hub = state.commercialHubs().stream().filter(h -> h.entityId().equals(body)).findFirst().orElse(null);
        if (hub == null) return "No local market for survey equipment and supplies";
        for (var bill : definition.capability().equipmentMaterialsKg().entrySet()) {
            var order = hub.activeOrders().get(bill.getKey());
            if (order == null || order.supplyKg() < bill.getValue()) return "Missing survey equipment material: " + bill.getKey();
        }
        return "Ready when local equipment materials and field supplies are available";
    }
    public static Map<String, Double> suppliesDemand(GameState state, String body) {
        var result = new TreeMap<String, Double>();
        for (var team : state.surveyTeams()) if (team.fleetId().isBlank() && team.bodyId().equals(body)
                && controls(state, team.empireId(), body) && EconomicOwnership.cash(state, team.empireId()) > 0) {
            double work = team.capability().equipment().ratedWorkPerDay();
            team.capability().suppliesKgPerWorkUnit().forEach((id, kg) -> result.merge(id, kg * work, Double::sum));
        }
        return Map.copyOf(result);
    }
}
