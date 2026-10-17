package com.spaceconquest.control;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.*;
import com.spaceconquest.engine.technology.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ResearchLifecycleTest {
    @TempDir Path directory;
    private final ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    GameState world() {
        var empire = new Empire("research", "Research", "human", "Individualist", 100000, 0,
                List.of("sol"), List.of(), Map.of(), List.of("electricity", "solar_power"), List.of());
        return ResearchTestWorld.staffed(GameState.builder().empires(List.of(empire)).build());
    }
    GameState scientists(GameState state, int count) {
        return state.toBuilder().householdAccounts(state.householdAccounts().stream().map(account -> {
            if (!"scientist".equals(account.professionId())) return account;
            return new HouseholdAccount(account.bodyId(), account.systemId(), account.empireId(), account.raceId(), account.professionId(),
                    account.headcount(), account.savingsCredits(), count * 100, account.welfareIncomeCredits(), account.incomeTaxPaidCredits(),
                    account.marketSpendingCredits(), account.unmetBasicKg(), account.secondaryNeedsMetFraction(), account.luxuryNeedsMetFraction(),
                    account.electricitySpendingCredits(), account.unmetBasicElectricityKwh(), account.wellbeing(),
                    new HouseholdEmployment(account.employment().workingAge(), count, 0, 0));
        }).toList()).build();
    }
    ResearchTickProcessor.Result tick(GameState state) { return new ResearchTickProcessor().process(state, List.of(), new ResearchProcessor(new Random(42))); }
    @Test void quotasWithoutPaidPhysicalScientistsCannotStartOrAdvanceResearch() {
        var state = world(); var start = new StartResearchCommand("research", "nuclear_fission", false, 5);
        assertEquals(30, ResearchWorkforce.capacity(state, state.empires().getFirst()));
        assertFalse(start.validate(state.toBuilder().householdAccounts(List.of()).build()));
        assertFalse(start.validate(state.toBuilder().solarSystems(List.of()).build()));
        assertFalse(start.validate(state.withSystemEconomies(List.of())));
        var active = start.apply(state); var lost = scientists(active, 0);
        assertEquals(0, tick(lost).projects().getFirst().accumulatedPoints());
        assertEquals(5, tick(lost).projects().getFirst().assignedScientists());
        assertEquals(5, tick(active).projects().getFirst().accumulatedPoints());
    }
    @Test void competingAssignmentsRevalidateAndShortagesAreSharedWithoutInventedOutput() {
        var state = world(); var queue = new CommandQueue();
        var first = queue.submitTracked(new StartResearchCommand("research", "nuclear_fission", false, 20));
        var second = queue.submitTracked(new StartResearchCommand("research", "solar_power", true, 11));
        var assigned = queue.drainAndExecute(state);
        assertEquals(CommandOutcome.EXECUTED, first.join()); assertEquals(CommandOutcome.REJECTED, second.join());
        assigned = new StartResearchCommand("research", "nuclear_fission", false, 15).apply(assigned);
        assigned = new StartResearchCommand("research", "solar_power", true, 15).apply(assigned);
        var limited = scientists(assigned, 6); var result = tick(limited);
        assertEquals(2, result.projects().size());
        assertTrue(result.projects().stream().allMatch(project -> project.accumulatedPoints() == 3));
        var reversed = limited.withResearchProjects(limited.researchProjects().reversed());
        assertEquals(result.projects().stream().mapToDouble(ResearchProject::accumulatedPoints).sum(),
                tick(reversed).projects().stream().mapToDouble(ResearchProject::accumulatedPoints).sum());
        var reduce = new StartResearchCommand("research", "nuclear_fission", false, 10);
        assertTrue(reduce.validate(limited), "Staffing shortages must still allow reducing requested work");
        assertEquals(10, reduce.apply(limited).researchProjects().stream()
                .filter(project -> "nuclear_fission".equals(project.targetTechOrAppId())).findFirst().orElseThrow().assignedScientists());
        assertFalse(new StartResearchCommand("research", "nuclear_fission", false, 16).validate(limited));
    }
    @Test void pauseResumeCancellationAndDiskReloadRetainOnlyLegitimateProgress() throws Exception {
        var state = new StartResearchCommand("research", "nuclear_fission", false, 30).apply(world());
        state = state.withResearchProjects(tick(state).projects());
        double points = state.researchProjects().getFirst().accumulatedPoints();
        state = new StartResearchCommand("research", "nuclear_fission", false, 0).apply(state);
        assertEquals(points, tick(state).projects().getFirst().accumulatedPoints());
        var manager = new SaveGameManager(directory); var file = directory.resolve("paused.scsave").toFile();
        manager.save(file, state, 1, "2027-01-01T00:00:00");
        var loaded = manager.load(file).toGameState(state.turn(), "RUNNING");
        assertEquals(state.researchProjects(), loaded.researchProjects());
        var resumed = new StartResearchCommand("research", "nuclear_fission", false, 5).apply(loaded);
        assertEquals(points + 5, tick(resumed).projects().getFirst().accumulatedPoints());
        var project = resumed.researchProjects().getFirst();
        assertFalse(new CancelResearchCommand("foreign", project.id()).validate(resumed));
        assertTrue(new CancelResearchCommand("research", project.id()).apply(resumed).researchProjects().isEmpty());
        assertFalse(new StartResearchCommand("research", "nuclear_fusion", false, 0).validate(resumed));
    }
    @Test void unsupportedSalvageAndMalformedCompletedTargetsNeverGrantUnlocks() {
        var state = world();
        var salvage = new ReverseEngineerSalvageCommand("research", "nonexistent_technology", null, true, 100);
        for (int claim = 0; claim < 3; claim++) { assertFalse(salvage.validate(state)); assertSame(state, salvage.apply(state)); }
        var forged = state.withResearchProjects(List.of(new ResearchProject("forged", "research", "nonexistent_technology", false, 1000, 1000, 1, 1)));
        var result = tick(forged); assertTrue(result.projects().isEmpty()); assertEquals(state.empires(), result.empires());
        var missingPrerequisite = state.withResearchProjects(List.of(new ResearchProject("locked", "research", "warp", false, 10000, 10000, 1, 1)));
        assertFalse(tick(missingPrerequisite).empires().getFirst().unlockedTechIds().contains("warp"));
    }
    @Test void normalTickDoesNotResearchWithPreviousPayrollAfterEducationFundingIsCut() throws Exception {
        var state = new StartResearchCommand("research", "nuclear_fission", false, 5).apply(world());
        ObjectNode economy = mapper.valueToTree(state.systemEconomies().getFirst()); economy.put("totalBudgetCredits", 0);
        state = state.withSystemEconomies(List.of(mapper.treeToValue(economy, SystemEconomy.class)));
        var engine = new SpaceConquestEngine(); engine.applyGameState(state); engine.stepTurn();
        var stopped = engine.getGameState();
        assertEquals(0, stopped.researchProjects().getFirst().accumulatedPoints());
        assertEquals(0, ResearchWorkforce.capacity(stopped, stopped.empires().getFirst()));
    }
    @Test void catalogCostsAreSeparateFromManufacturingAndAlreadyQuotedProjectsStayFixed() throws Exception {
        var state = world(); var catalog = DataModelLoader.loadTechnologies(); var policy = ResearchPolicy.current();
        assertTrue(policy.cost(state.empires().getFirst(), "warp", false, catalog) > policy.cost(state.empires().getFirst(), "electricity", false, catalog));
        var technology = catalog.stream().filter(value -> "nuclear_fission".equals(value.id())).findFirst().orElseThrow();
        var edited = new Technology(technology.id(), technology.name(), technology.description(), technology.complexity(),
                technology.requiredTechnologies(), technology.applications(), 77.0);
        assertEquals(77, policy.cost(state.empires().getFirst(), edited.id(), false, List.of(edited)));
        var started = new StartResearchCommand("research", "nuclear_fission", false, 5).apply(state);
        var updated = new StartResearchCommand("research", "nuclear_fission", false, 10).apply(started);
        assertEquals(started.researchProjects().getFirst().requiredPoints(), updated.researchProjects().getFirst().requiredPoints());
        assertEquals(technology.complexity(), edited.complexity());
    }
    @Test void researchUsesTheActualMixedSpeciesWorkersAndTheirTraining() throws Exception {
        var state = scientists(world(), 6);
        ObjectNode system = mapper.valueToTree(state.solarSystems().getFirst());
        var populations = (com.fasterxml.jackson.databind.node.ArrayNode) system.get("planets").get(0).get("populations");
        populations.add(mapper.valueToTree(new Population("fast", Map.of(25, 100000L))));
        var accounts = new ArrayList<>(state.householdAccounts());
        ObjectNode second = mapper.valueToTree(accounts.stream().filter(account -> "scientist".equals(account.professionId())).findFirst().orElseThrow());
        second.put("raceId", "fast"); accounts.add(mapper.treeToValue(second, HouseholdAccount.class));
        ObjectNode economy = mapper.valueToTree(state.systemEconomies().getFirst()); economy.put("educationLevel", 2);
        state = state.toBuilder().solarSystems(List.of(mapper.treeToValue(system, SolarSystem.class))).householdAccounts(accounts)
                .systemEconomies(List.of(mapper.treeToValue(economy, SystemEconomy.class))).build();
        state = new StartResearchCommand("research", "nuclear_fission", false, 6).apply(state);
        var human = new Race("human", "Human", "", 1, 1, "Individualist", 1, 293, "Carbon based", "Oxygen based", 18, 50, "Organic", "Diverse", 80);
        var fast = new Race("fast", "Fast", "", 2, 1, "Individualist", 1, 293, "Carbon based", "Oxygen based", 18, 50, "Organic", "Diverse", 80);
        assertEquals(12, ResearchWorkforce.capacity(state, state.empires().getFirst()));
        var result = new ResearchTickProcessor().process(state, List.of(human, fast), new ResearchProcessor(new Random(42)));
        assertEquals(18, result.projects().getFirst().accumulatedPoints(), .0001);
    }
    @Test void hiveResearchRequiresPhysicalWorkingScientistsInsteadOfCurrencyPayroll() {
        var state = world(); var old = state.empires().getFirst();
        var hive = new Empire(old.id(), old.name(), old.raceId(), "Hive", 0, 0, old.controlledSystemIds(),
                old.ministries(), old.systemGovernorAssignments(), old.unlockedTechIds(), old.activeShipDesignIds());
        state = state.toBuilder().empires(List.of(hive)).householdAccounts(List.of()).build();
        assertEquals(30, ResearchWorkforce.capacity(state, hive));
        assertTrue(new StartResearchCommand("research", "nuclear_fission", false, 5).validate(state));
        assertEquals(0, ResearchWorkforce.capacity(state.toBuilder().solarSystems(List.of()).build(), hive));
        assertEquals(0, ResearchWorkforce.capacity(state.withSystemEconomies(List.of()), hive));
    }
}
