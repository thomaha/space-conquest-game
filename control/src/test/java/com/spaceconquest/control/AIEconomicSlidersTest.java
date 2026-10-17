package com.spaceconquest.control;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spaceconquest.control.ai.*;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.*;
import com.spaceconquest.engine.industry.PowerGridState;
import com.spaceconquest.engine.technology.ResearchProject;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AIEconomicSlidersTest {
    @TempDir Path directory;
    private GameState world(double net, double localCash, double debt) {
        var state = new AIPowerInvestmentTest().world("solar_power", "breathable", true);
        var owner = state.empires().getFirst(); var knowledge = new ArrayList<>(owner.unlockedTechIds()); knowledge.add(PlanetaryMunicipalProcessor.TECH_SUBSPACE_BANKING);
        var economy = new SystemEconomy("sol", "owner", .2, .2, .2, .2, .2, 2000, 700, 1, 1, 1, 1, 1, 10, 10, 10, 10, 10, 10, 10, 100, .2, 0);
        var sheet = new PlanetaryBalanceSheet("world", "sol", "owner", 10000, 2000 + net, 0, 0, 2000 + net, 0, 0, 0, 0, 2000, net, localCash, 0, 2000, 0, debt);
        return state.toBuilder().empires(List.of(new Empire(owner.id(), owner.name(), owner.raceId(), owner.societyStructure(), owner.treasuryCredits(), 0,
                owner.controlledSystemIds(), owner.ministries(), owner.systemGovernorAssignments(), knowledge, owner.activeShipDesignIds())))
                .systemEconomies(List.of(economy)).planetaryBalanceSheets(List.of(sheet)).powerGrids(List.of()).build();
    }
    private AISystemEconomyPlan quote(GameState state) { return AISystemEconomyPlan.quote(state, "owner", "sol", EconomicPolicy.current()); }
    private double[] shares(SetSystemEconomyBudgetCommand command) {
        return new double[]{command.educationAllocation(), command.lawAndOrderAllocation(), command.healthAndWelfareAllocation(), command.infrastructureAllocation(), command.planetaryMilitiasAllocation()};
    }
    private SystemEconomy levels(SystemEconomy source, int sector) {
        var values = new double[]{1, 1, 1, 1, 1}; values[sector] = .2;
        return new SystemEconomy(source.systemId(), source.empireId(), .2, .2, .2, .2, .2, source.totalBudgetCredits(), source.accumulatedMilitiaInvestment(),
                values[0], values[1], values[2], values[3], values[4], 10, 10, 10, 10, 10, 10, 10, 100, source.taxRate(), source.empireContributionRate());
    }
    @Test void eachSectorCanGainInvestmentWhileSharesRemainNormalizedAndBounded() {
        var state = world(0, 0, 0); var source = state.systemEconomies().getFirst();
        for (int sector = 0; sector < 5; sector++) {
            var changed = state.withSystemEconomies(List.of(levels(source, sector))); var plan = quote(changed); var shares = shares(plan.proposed());
            assertTrue(shares[sector] > .2, "Sector " + sector); assertEquals(1, Arrays.stream(shares).sum(), 1e-9);
            for (double value : shares) assertTrue(Math.abs(value - .2) <= EconomicPolicy.current().allocationStep() + 1e-9);
            assertTrue(plan.proposed().validate(changed));
        }
    }
    @Test void liveResearchPowerShortagesWarAndHouseholdHardshipAffectSectorChoices() {
        var state = world(0, 0, 0);
        var research = state.withResearchProjects(List.of(new ResearchProject("project", "owner", "rocketry", false, 0, 100, 10, 1)));
        assertTrue(quote(research).proposed().educationAllocation() > .2);
        var power = state.withPowerGrids(List.of(new PowerGridState("world", 0, 100, -100, 0, 0, true)));
        assertTrue(quote(power).proposed().infrastructureAllocation() > .2);
        var war = state.withDiplomaticRelations(List.of(new DiplomaticRelation("owner", "enemy", "TOTAL_WAR", 0)));
        assertTrue(quote(war).proposed().planetaryMilitiasAllocation() > .2);
        var household = new HouseholdAccount("world", "sol", "owner", "human", "industrial_worker", 100, 0, 0, 0, 0, 0,
                Map.of(), 0, 0, 0, 0, HouseholdWellbeing.healthy().withMaterialCoverage(.3), HouseholdEmployment.none());
        var poor = state.toBuilder().householdAccounts(List.of(household)).build();
        assertTrue(quote(poor).proposed().healthAndWelfareAllocation() > .2); assertTrue(quote(poor).proposed().taxRate() < .2);
    }
    @Test void deficitsRaiseTaxReduceBudgetAndRequestSubsidiesWhileSurplusesSendFundsToEmpire() {
        var deficit = world(-500, 0, 1000); var deficitPlan = quote(deficit).proposed();
        assertTrue(deficitPlan.taxRate() > .2); assertTrue(deficitPlan.totalBudgetCredits() < 2000); assertTrue(deficitPlan.empireContributionRate() < 0);
        var surplus = world(500, 20000, 0); var surplusPlan = quote(surplus).proposed();
        assertTrue(surplusPlan.taxRate() < .2); assertTrue(surplusPlan.totalBudgetCredits() > 2000); assertTrue(surplusPlan.empireContributionRate() > 0);
        var owner = deficit.empires().getFirst();
        var poorEmpire = deficit.withEmpires(List.of(new Empire(owner.id(), owner.name(), owner.raceId(), owner.societyStructure(), 0, 0,
                owner.controlledSystemIds(), owner.ministries(), owner.systemGovernorAssignments(), owner.unlockedTechIds(), List.of())));
        assertEquals(0, quote(poorEmpire).proposed().empireContributionRate());
    }
    @Test void signedPoliciesSettleRealTransfersAndSurviveDiskReload() throws Exception {
        for (var state : List.of(world(-500, 0, 1000), world(500, 20000, 0))) {
            var plan = quote(state); var configured = new AdjustAISystemEconomyCommand("owner", "sol", plan.expected()).apply(state);
            double before = configured.empires().getFirst().treasuryCredits();
            var settled = new PlanetaryMunicipalProcessor().processMunicipalFinances(configured); var sheet = settled.balanceSheets().getFirst();
            double rate = configured.systemEconomies().getFirst().empireContributionRate();
            assertTrue(rate * sheet.empireTransferCredits() > 0); assertEquals(before + sheet.empireTransferCredits(), settled.updatedEmpires().getFirst().treasuryCredits(), .001);
            assertTrue(settled.newImperialDebtCredits().isEmpty());
            var result = configured.toBuilder().empires(settled.updatedEmpires()).planetaryBalanceSheets(settled.balanceSheets()).build();
            var file = directory.resolve(rate < 0 ? "subsidy.scsave" : "contribution.scsave").toFile(); var manager = new SaveGameManager(directory);
            manager.save(file, result, 1, GameClock.START_TIME.toString()); var loaded = manager.load(file).toGameState(result.turn(), result.status());
            assertEquals(result.systemEconomies(), loaded.systemEconomies()); assertEquals(result.planetaryBalanceSheets(), loaded.planetaryBalanceSheets());
        }
    }
    @Test void queuedAdjustmentsRejectDuplicatesInterveningSettingsAndLostControlWithoutAddingMilitiaInvestment() {
        var state = world(-500, 0, 1000); var plan = quote(state); var command = new AdjustAISystemEconomyCommand("owner", "sol", plan.expected());
        var queue = new CommandQueue(); queue.submit(command); queue.submit(command); var updated = queue.drainAndExecute(state);
        assertEquals(plan.proposed().taxRate(), updated.systemEconomies().getFirst().taxRate());
        assertEquals(700, updated.systemEconomies().getFirst().accumulatedMilitiaInvestment()); assertSame(updated, command.apply(updated));
        var player = new SetSystemEconomyBudgetCommand("owner", "sol", .3, .1, .2, .2, .2, 1500, .15, .25).apply(state);
        assertSame(player, command.apply(player));
        var owner = state.empires().getFirst(); var lost = state.withEmpires(List.of(new Empire(owner.id(), owner.name(), owner.raceId(), owner.societyStructure(), owner.treasuryCredits(), 0,
                List.of(), List.of(), Map.of(), owner.unlockedTechIds(), List.of())));
        assertFalse(command.validate(lost)); assertSame(lost, command.apply(lost));
        assertTrue(new SystemEconomyProcessor().processSystemEconomy(updated.systemEconomies().getFirst(), 20000, false).accumulatedMilitiaInvestment() > 700);
    }
    @Test void planningReviewsExistingPoliciesPeriodicallyAndControllerAppliesThem() {
        var state = world(-500, 0, 1000); var planner = new EmpireEconomicPlanner(); var owner = state.empires().getFirst();
        assertEquals(1, planner.plan(state, owner).size()); assertTrue(planner.plan(state, owner).isEmpty());
        assertTrue(planner.plan(state.withTurn(4), owner).isEmpty()); assertEquals(1, planner.plan(state.withTurn(5), owner).size());
        var queue = new CommandQueue(); new EmpireAIController("owner", queue).onGameStateUpdate(state); var updated = queue.drainAndExecute(state);
        assertTrue(updated.systemEconomies().getFirst().taxRate() > state.systemEconomies().getFirst().taxRate());
        assertTrue(updated.systemEconomies().getFirst().empireContributionRate() < 0);
        var missing = state.withSystemEconomies(List.of()); assertEquals(1, new EmpireEconomicPlanner().plan(missing, owner).size());
    }
    @Test void economicPolicyDataControlsAdjustmentSizes() throws Exception {
        var mapper = new ObjectMapper(); ObjectNode data = mapper.valueToTree(EconomicPolicy.current());
        data.put("taxStep", .01); data.put("transferStep", .025); data.put("allocationStep", .01);
        var state = world(-500, 0, 1000).withPowerGrids(List.of(new PowerGridState("world", 0, 100, -100, 0, 0, true)));
        var proposed = AISystemEconomyPlan.quote(state, "owner", "sol", mapper.treeToValue(data, EconomicPolicy.class)).proposed();
        assertEquals(.21, proposed.taxRate(), 1e-9); assertEquals(-.025, proposed.empireContributionRate(), 1e-9);
        assertEquals(.21, proposed.infrastructureAllocation(), 1e-9);
    }
    @Test void subsidyCapsAreSharedAcrossSystemsAndRecheckedAfterTreasuryChanges() {
        var state = world(-500, 0, 1000); var owner = state.empires().getFirst();
        var first = state.solarSystems().getFirst(); var planet = first.planets().getFirst();
        var secondPlanet = new Planet("second-world", planet.name(), planet.description(), planet.mass(), planet.gravity(), planet.distance(), planet.inclination(), planet.diameter(),
                planet.type(), planet.atmosphere(), planet.hasLiquidWater(), planet.waterLevel(), planet.resources(), planet.moons(), planet.populations());
        var secondSystem = new SolarSystem("second", "Second", "", 0, 0, 0, first.sunMass(), first.sunDiameter(), first.sunColor(), List.of(secondPlanet), List.of());
        var secondEconomy = SystemEconomy.createDefault("second", "owner", 20000);
        var secondSheet = new PlanetaryBalanceSheet("second-world", "second", "owner", 0, 0, 0, 0, 0, 0, 0, 0, 0, 500, -500, 0, 0).withOutstandingDebt(1000);
        state = state.toBuilder().solarSystems(List.of(first, secondSystem)).systemEconomies(List.of(state.systemEconomies().getFirst(), secondEconomy))
                .planetaryBalanceSheets(List.of(state.planetaryBalanceSheets().getFirst(), secondSheet))
                .empires(List.of(new Empire(owner.id(), owner.name(), owner.raceId(), owner.societyStructure(), 11000, 0, List.of("sol", "second"), List.of(), Map.of(), owner.unlockedTechIds(), List.of()))).build();
        double daily = 0;
        for (String system : List.of("sol", "second")) {
            var proposal = AISystemEconomyPlan.quote(state, "owner", system, EconomicPolicy.current()).proposed();
            daily += -proposal.empireContributionRate() * proposal.totalBudgetCredits();
        }
        assertTrue(daily * EconomicPolicy.current().subsidyCashHorizonDays() <= 1000 + 1e-9);
        var planned = quote(state); var command = new AdjustAISystemEconomyCommand("owner", "sol", planned.expected());
        var empty = state.withEmpires(List.of(new Empire(owner.id(), owner.name(), owner.raceId(), owner.societyStructure(), 0, 0, List.of("sol", "second"), List.of(), Map.of(), owner.unlockedTechIds(), List.of())));
        assertEquals(0, command.apply(empty).systemEconomies().stream().filter(value -> value.systemId().equals("sol")).findFirst().orElseThrow().empireContributionRate());
        var obsolete = world(-500, 0, 1000).withSystemEconomies(List.of(state.systemEconomies().getFirst().withEmpireContributionRate(.5)));
        assertTrue(quote(obsolete).proposed().empireContributionRate() <= 0);
    }
    @Test void taxCorrectionAccountsForTheBudgetChangeAndHoldsInsideTheFiscalDeadband() {
        var state = world(100, 10000, 0); var current = state.systemEconomies().getFirst();
        var quiet = quote(state).proposed();
        assertEquals(current.taxRate(), quiet.taxRate()); assertEquals(current.totalBudgetCredits(), quiet.totalBudgetCredits());
        var nearBalance = world(300, 10000, 0); var proposed = quote(nearBalance).proposed();
        double taxable = nearBalance.planetaryBalanceSheets().getFirst().incomeTaxRevenue() / current.taxRate();
        double expected = current.taxRate() + (proposed.totalBudgetCredits() - current.totalBudgetCredits() - 300) / taxable;
        assertEquals(expected, proposed.taxRate(), 1e-9);
        assertTrue(current.taxRate() - proposed.taxRate() < EconomicPolicy.current().taxStep());
    }
}
