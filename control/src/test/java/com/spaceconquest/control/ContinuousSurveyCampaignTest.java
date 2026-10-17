package com.spaceconquest.control;

import com.spaceconquest.control.ai.*;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.*;
import com.spaceconquest.engine.industry.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ContinuousSurveyCampaignTest {
    @TempDir Path directory;
    @Test void standingTeamsRepeatPaidWorkAndDiscoverOnlyExistingVeinsAcrossReload() throws Exception {
        var state = world(); double steel = stock(state, "steel"), fuel = stock(state, "hydrocarbons");
        var miningInputs = IndustryRecipeCatalog.find("mining_outpost").inputSources();
        assertEquals(0, ProcessInput.maximumBatches(state, "research-world", miningInputs, state.geologicalDeposits()));
        var commands = SurveyProgramPlanner.plan(state, state.empires().getFirst());
        assertEquals(1, commands.size());
        state = commands.getFirst().apply(state);
        assertEquals(1, state.surveyTeams().size());
        assertEquals(steel - 50, stock(state, "steel"), 1e-6);
        assertFalse(state.geologicalDeposits().getFirst().isDiscovered());
        assertTrue(state.industrialFacilities().isEmpty(), "Survey teams are not factories");
        var engine = new SpaceConquestEngine(42); engine.applyGameState(state);
        double wages = 0; boolean reloaded = false;
        for (int day = 0; day < 85; day++) {
            state = engine.getGameState();
            for (var command : SurveyProgramPlanner.plan(state, state.empires().getFirst())) state = command.apply(state);
            engine.applyGameState(state); engine.stepTurn(); state = engine.getGameState();
            assertEquals(1, state.surveyTeams().size());
            var team = state.surveyTeams().getFirst();
            wages += state.householdAccounts().stream().mapToDouble(a -> a.employment().industryWorkers() * Profession.getBaseWageForProfession(a.professionId())).sum();
            if (day == 5) {
                assertTrue(team.progressWork() > 0, team.toString());
                var saves = new SaveGameManager(directory);
                var restored = saves.load(saves.save("survey", state, 42, GameClock.START_TIME.toString()).toFile())
                        .toGameState(state.turn(), state.status());
                assertEquals(state.surveyTeams(), restored.surveyTeams());
                assertEquals(state.industryAccounts(), restored.industryAccounts());
                assertEquals(restored.surveyTeams(), SurveyOperations.advance(restored, Map.of(team.id(), 5)).surveyTeams(),
                        "A reload cannot repeat the same day's work");
                engine.applyGameState(restored); reloaded = true;
            }
        }
        state = engine.getGameState();
        assertTrue(reloaded); assertTrue(wages > 0);
        assertTrue(state.surveyTeams().getFirst().completedPasses() >= 6, state.surveyTeams().toString());
        assertTrue(state.surveyTeams().getFirst().blocker().isBlank(), state.surveyTeams().toString());
        assertTrue(stock(state, "hydrocarbons") < fuel);
        assertEquals(1, state.geologicalDeposits().size(), "No fabricated replacement veins");
        assertTrue(state.geologicalDeposits().getFirst().isDiscovered(), state.surveyTeams().toString());
        assertEquals(1000, state.geologicalDeposits().getFirst().remainingVolumeKg());
        assertTrue(ProcessInput.maximumBatches(state, "research-world", miningInputs, state.geologicalDeposits()) > 0,
                "Survey discoveries must open the ordinary finite mining source path");
    }
    @Test void missingResearchMaterialsCashWorkersAndControlRejectStaleStartsWithoutSpending() throws Exception {
        var state = world(); var command = new StartProspectingMissionCommand("research-world", "owner");
        assertTrue(command.validate(state));
        var owner = state.empires().getFirst();
        var noTech = state.withEmpires(List.of(owner(owner, owner.treasuryCredits(), List.of("sol"), List.of())));
        var noMoney = state.withEmpires(List.of(owner(owner, 0, List.of("sol"), owner.unlockedTechIds())));
        var noAccess = state.withEmpires(List.of(owner(owner, owner.treasuryCredits(), List.of(), owner.unlockedTechIds())));
        var noStock = state.withCommercialHubs(List.of());
        var noWorkers = state.toBuilder().householdAccounts(List.of()).build();
        for (var blocked : List.of(noTech, noMoney, noAccess, noStock, noWorkers)) {
            assertFalse(command.validate(blocked)); assertSame(blocked, command.apply(blocked));
        }
        var started = command.apply(state);
        assertFalse(command.validate(started), "Duplicate requests must not purchase another kit");
        assertSame(started, command.apply(started));
    }
    @Test void workWaitsForActualPaidCrewAndSuppliesAndResumesWithoutLosingProgress() throws Exception {
        var state = new StartProspectingMissionCommand("research-world", "owner").apply(world());
        var team = state.surveyTeams().getFirst();
        state = SurveyOperations.advance(state.toBuilder().turn(1).build(), Map.of());
        assertEquals(0, state.surveyTeams().getFirst().totalWork());
        assertTrue(state.surveyTeams().getFirst().blocker().contains("crew"));
        var supplied = state;
        state = SurveyOperations.advance(state.toBuilder().turn(2).commercialHubs(List.of()).build(), Map.of(team.id(), 5));
        assertEquals(0, state.surveyTeams().getFirst().totalWork());
        assertTrue(state.surveyTeams().getFirst().blocker().contains("supplies"));
        state = SurveyOperations.advance(state.toBuilder().turn(3).commercialHubs(supplied.commercialHubs()).build(), Map.of(team.id(), 5));
        assertEquals(100, state.surveyTeams().getFirst().totalWork(), 1e-6);
        assertEquals(100, state.surveyTeams().getFirst().installationRemaining(), 1e-6);
        var owner = state.empires().getFirst();
        state = state.withEmpires(List.of(owner(owner, owner.treasuryCredits(), List.of(), owner.unlockedTechIds()))).toBuilder().turn(4).build();
        var blocked = SurveyOperations.advance(state, Map.of(team.id(), 5));
        assertEquals(100, blocked.surveyTeams().getFirst().totalWork());
        assertTrue(blocked.surveyTeams().getFirst().blocker().contains("access"));
    }
    @Test void effortIncreasesWithKnownNeedsButNeverUsesHiddenVeinsAndNeverStopsAtZeroDemand() throws Exception {
        var state = world(); assertEquals(1, SurveyProgramPlanner.desiredTeams(state, "research-world"));
        var hidden = state.geologicalDeposits().getFirst();
        assertEquals(SurveyProgramPlanner.plan(state, state.empires().getFirst()),
                SurveyProgramPlanner.plan(state.toBuilder().geologicalDeposits(List.of()).build(), state.empires().getFirst()));
        var hub = state.commercialHubs().getFirst(); var orders = new HashMap<>(hub.activeOrders());
        orders.put("refined_iron", new MarketOrder("refined_iron", 0, 100, 10, 1));
        state = state.withCommercialHubs(List.of(hub.withInventory(0, hub.currentStoredWeightKg(), orders)));
        assertEquals(2, SurveyProgramPlanner.desiredTeams(state, "research-world"));
        state = state.toBuilder().geologicalDeposits(List.of(new GeologicalDeposit(hidden.id(), hidden.planetId(), hidden.materialId(),
                1000, 0, 1, true, "owner"))).build();
        assertEquals(3, SurveyProgramPlanner.desiredTeams(state, "research-world"));
        for (int i = 0; i < 5; i++) for (var command : SurveyProgramPlanner.plan(state, state.empires().getFirst())) state = command.apply(state);
        assertEquals(3, state.surveyTeams().size());
    }
    @Test void researchAndInspectorExplainTheStandingProgram() throws Exception {
        var state = world(); var owner = state.empires().getFirst();
        state = state.withEmpires(List.of(owner(owner, owner.treasuryCredits(), owner.controlledSystemIds(), List.of("geological_prospecting"))));
        var commands = EmpireResearchPlanner.plan(state, state.empires().getFirst());
        assertTrue(commands.stream().anyMatch(c -> c instanceof StartResearchCommand r && r.targetTechOrAppId().equals("seismic_crustal_sounding")), commands.toString());
        assertTrue(SupplyInvestmentStatus.inspect(state, owner.id()).rows().stream()
                .anyMatch(r -> r.action().equals("Standing survey") && r.reason().contains("Research")));
    }
    @Test void standingCrewIsPaidBeforeCompetingMinesWithoutCountingWorkersTwice() throws Exception {
        var state = new StartProspectingMissionCommand("research-world", "owner").apply(world());
        var team = state.surveyTeams().getFirst();
        var mine = new IndustrialFacility("mine", "research-world", "mining_outpost", "owner",
                IndustrialFacility.PUBLIC_STATE, 1, 100000, "miner", false, 0);
        var accounts = new ArrayList<>(state.industryAccounts()); accounts.add(IndustryAccount.empty(mine.id()).withOperatingCash(1e8));
        state = state.toBuilder().industrialFacilities(List.of(mine)).industryAccounts(accounts).build();
        var payroll = new HouseholdEconomyProcessor().process(state, DataModelLoader.loadRaces());
        assertEquals(5, payroll.paidWorkersByFacility().get(team.id()));
        long industrial = payroll.paidWorkersByFacility().values().stream().mapToLong(Integer::longValue).sum();
        long workers = payroll.householdAccounts().stream().mapToLong(a -> a.employment().workingAge()).sum();
        long publicJobs = payroll.householdAccounts().stream().mapToLong(a -> a.employment().publicWorkers()).sum();
        assertTrue(industrial + publicJobs <= workers);
        assertTrue(payroll.industryAccounts().stream().filter(a -> a.facilityId().equals(team.id()))
                .allMatch(a -> a.operatingCashCredits() < SurveyOperations.reserve(team.capability())));
        var demand = new com.spaceconquest.engine.market.MarketDemandProcessor().refresh(state, List.of());
        assertTrue(demand.getFirst().activeOrders().get("hydrocarbons").demandKg() >= 2);
    }
    private GameState world() throws Exception {
        var state = new AISupplyChainCampaignTest().world(true); var hub = state.commercialHubs().getFirst();
        var orders = new HashMap<>(hub.activeOrders());
        orders.replaceAll((id, o) -> new MarketOrder(id, 1e9, 0, 1, 0));
        return state.withCommercialHubs(List.of(new CommercialHub(hub.id(), hub.entityId(), 0, 1e14, orders.size() * 1e9, 10, orders)))
                .toBuilder().geologicalDeposits(List.of(new GeologicalDeposit("hidden", "research-world", "iron_ore", 1000, 1000, 1, false, ""))).build();
    }
    private double stock(GameState state, String id) { return state.commercialHubs().getFirst().activeOrders().get(id).supplyKg(); }
    private Empire owner(Empire e, double cash, List<String> systems, List<String> known) {
        return new Empire(e.id(), e.name(), e.raceId(), e.societyStructure(), cash, e.corporateTaxRate(), systems,
                e.ministries(), e.systemGovernorAssignments(), known, e.activeShipDesignIds());
    }
}
