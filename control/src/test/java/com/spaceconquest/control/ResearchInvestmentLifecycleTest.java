package com.spaceconquest.control;

import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.HouseholdEconomyProcessor;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.market.CorporateInvestmentProcessor;
import com.spaceconquest.engine.technology.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ResearchInvestmentLifecycleTest {
    @TempDir Path directory;
    private GameState world() {
        var empire = new Empire("owner", "Owner", "human", "Individualist", 10_000_000, 0, List.of("sol"), List.of(), Map.of(),
                List.of("electricity", "industrial_production", "ship_cannons", "solar_power"), List.of());
        var state = ResearchTestWorld.staffed(GameState.builder().empires(List.of(empire)).build());
        var planet = new Planet("research-world", "Research world", "", 1, 9.81, SolarRadiation.AU_KM, 0, 12742, "terrestrial", "breathable", true, 1,
                List.of(), List.of(), List.of(new Population("human", Map.of(25, 20000L))));
        var stock = new HashMap<String, MarketOrder>();
        for (String material : List.of("refined_iron", "refined_aluminum", "refined_copper", "silicon", "steel", "hydrocarbons"))
            stock.put(material, new MarketOrder(material, 5000, 0, 1, 0));
        stock.put("cannon_round", new MarketOrder("cannon_round", 0, 1000, 1000, .95));
        stock.put("luxury_goods", new MarketOrder("luxury_goods", 0, 10000, 100000, 1)); // More attractive but unresearched.
        return state.toBuilder().solarSystems(List.of(new SolarSystem("sol", "Sol", "", 0, 0, 0, SolarRadiation.SOLAR_MASS_KG, 1392700, "Yellow", List.of(planet), List.of())))
                .corporations(List.of(new Corporation("maker", "Maker", "owner", "research-world", "CONSUMER", 500000, List.of(), List.of(), List.of())))
                .commercialHubs(List.of(new CommercialHub("hub", "research-world", 0, 1000000, 30000, 10, stock)))
                .marketAccounts(List.of(new com.spaceconquest.engine.economy.MarketAccount("hub", 1e7)))
                .industrialFacilities(List.of(new IndustrialFacility("solar", "research-world", "solar_power", "owner", IndustrialFacility.PUBLIC_STATE, 4, 100, "technician", false, 0)))
                .industryAccounts(List.of(IndustryAccount.empty("solar").withOperatingCash(100000))).build();
    }
    private GameState payroll(GameState state, HouseholdEconomyProcessor.TurnResult payroll) {
        return state.toBuilder().empires(payroll.empires()).corporations(payroll.corporations()).householdAccounts(payroll.householdAccounts())
                .commercialHubs(payroll.commercialHubs()).marketAccounts(payroll.marketAccounts()).industryAccounts(payroll.industryAccounts()).build();
    }
    @Test void researchUnlocksAutomaticInvestmentMaterialFundedConstructionPaidPowerAndProductionThenExpansion() throws Exception {
        var state = world(); var investment = new CorporateInvestmentProcessor();
        assertEquals(state, investment.processCorporateInvestments(state, Map.of()));
        var queue = new CommandQueue(); queue.submit(new StartResearchCommand("owner", "cannon_ammunition_mfg", true, 30));
        state = queue.drainAndExecute(state); assertEquals(1, state.researchProjects().size());
        var researcher = new ResearchProcessor(new Random() { @Override public double nextDouble() { return .01; } });
        for (int day = 0; day < 20 && !state.researchProjects().isEmpty(); day++) {
            state = payroll(state, new HouseholdEconomyProcessor().process(state, DataModelLoader.loadRaces()));
            var research = new ResearchTickProcessor().process(state, DataModelLoader.loadRaces(), researcher);
            state = state.toBuilder().empires(research.empires()).researchProjects(research.projects()).applicationOptimizations(research.optimizations()).researchHistory(research.history()).build();
        }
        assertTrue(state.researchProjects().isEmpty()); assertTrue(state.empires().getFirst().unlockedTechIds().contains("cannon_ammunition_mfg"));
        queue.submit(new ResolveApplicationResearchCommand("owner", "cannon_ammunition_mfg", true)); state = queue.drainAndExecute(state);
        var unfunded = state.toBuilder().marketAccounts(List.of()).build();
        assertEquals(unfunded, investment.processCorporateInvestments(unfunded, Map.of()), "Unfunded output demand cannot justify corporate investment");
        double before = state.corporations().getFirst().liquidCapitalReserves(); state = investment.processCorporateInvestments(state, Map.of());
        var facility = state.industrialFacilities().stream().filter(value -> value.applicationId().equals("cannon_ammunition_mfg")).findFirst().orElseThrow();
        assertEquals(before - CorporateInvestmentProcessor.INFRASTRUCTURE_COST, state.corporations().getFirst().liquidCapitalReserves());
        assertEquals(0, facility.tier()); assertEquals(1, state.expansionProjects().size());
        assertEquals(state, investment.processCorporateInvestments(state, Map.of()), "A pending factory must not receive duplicate investment");
        for (int day = 0; day < 15 && !state.expansionProjects().isEmpty(); day++) state = new IndustryProcessor().processIndustrialProduction(state);
        assertTrue(state.expansionProjects().isEmpty()); assertTrue(state.corporations().getFirst().liquidCapitalReserves() < before - CorporateInvestmentProcessor.INFRASTRUCTURE_COST);
        var paid = new HouseholdEconomyProcessor().process(state, DataModelLoader.loadRaces()); state = payroll(state, paid);
        assertTrue(paid.paidWorkersByFacility().getOrDefault(facility.id(), 0) > 0);
        var generation = new PowerGenerationProcessor().process(state, paid.paidWorkersByFacility(), paid.wagesByFacility());
        state = state.toBuilder().empires(generation.empires()).corporations(generation.corporations()).commercialHubs(generation.hubs()).industryAccounts(generation.industryAccounts()).build();
        var power = new PowerProcessor().balanceDay(state, generation.generationKw(), paid.paidWorkersByFacility());
        assertTrue(power.poweredWorkers().getOrDefault(facility.id(), 0) > 0);
        var billed = new PowerBillingProcessor().process(state, power, generation.accounts(), paid.paidWorkersByFacility());
        state = state.toBuilder().industryAccounts(billed.industryAccounts()).householdAccounts(billed.households()).build();
        var produced = new IndustryMarketProcessor().process(state, billed.poweredWorkers(), paid.wagesByFacility(), paid.paidWorkersByFacility(), billed.productionEnergy());
        var account = produced.industryAccounts().stream().filter(value -> value.facilityId().equals(facility.id())).findFirst().orElseThrow();
        assertTrue(account.producedKg().getOrDefault("cannon_round", 0.0) > 0); assertTrue(account.inputCostsCredits() > 0); assertTrue(account.wageCostsCredits() > 0);
        assertTrue(account.powerCostsCredits() > 0); assertTrue(account.soldKg().getOrDefault("cannon_round", 0.0) > 0);
        state = state.toBuilder().empires(produced.empires()).corporations(produced.corporations()).commercialHubs(produced.hubs())
                .marketAccounts(produced.marketAccounts()).industryAccounts(produced.industryAccounts()).build();
        var file = directory.resolve("research-industry.scsave").toFile(); var manager = new SaveGameManager(directory); manager.save(file, state, 1, GameClock.START_TIME.toString());
        state = manager.load(file).toGameState(state.turn(), state.status());
        var expanded = investment.processCorporateInvestments(state, Map.of());
        assertEquals(state.industrialFacilities().size(), expanded.industrialFacilities().size());
        assertEquals(facility.id(), expanded.expansionProjects().getFirst().facilityId()); assertTrue(expanded.expansionProjects().getFirst().targetTier() > 1);
        assertEquals(expanded.corporations().getFirst().ownedFacilityIds().size(), new HashSet<>(expanded.corporations().getFirst().ownedFacilityIds()).size());
    }
}
