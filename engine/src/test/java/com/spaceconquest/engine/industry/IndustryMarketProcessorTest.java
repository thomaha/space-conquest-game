package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.MarketOrder;
import com.spaceconquest.engine.economy.MarketAccount;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndustryMarketProcessorTest {
    private final IndustryMarketProcessor processor = new IndustryMarketProcessor();

    @Test
    void everyProductionRequirementAndImprovementExistsInTheTechnologyCatalog() throws IOException {
        var technologyIds = DataModelLoader.loadTechnologies().stream()
                .map(technology -> technology.id()).toList();
        for (var recipe : IndustryRecipeCatalog.all()) {
            assertTrue(technologyIds.containsAll(recipe.requiredTechnologies()), recipe.applicationId());
            assertTrue(technologyIds.containsAll(recipe.improvements().keySet()), recipe.applicationId());
        }
    }

    @Test
    void farmBuysInputsProducesAndSellsAgainstHubCash() {
        IndustrialFacility farm = facility("industrial_soil_cultivation");
        CommercialHub hub = new CommercialHub("hub", "earth", 0.05, 10_000.0, 70.0, 10.0,
                Map.of("nitrates", order("nitrates", 10, 1),
                        "phosphates", order("phosphates", 5, 1),
                        "potash", order("potash", 5, 1),
                        "water_ice", order("water_ice", 50, 1),
                        "food_matrix", order("food_matrix", 0, 2)));
        GameState state = state(farm, hub, 1_000.0, List.of(), List.of("industrial_production"));

        var result = processor.process(state, Map.of("farm", 100), Map.of("farm", 50.0));
        IndustryAccount account = result.industryAccounts().getFirst();

        assertEquals(70.0, account.inputCostsCredits(), 0.001);
        assertEquals(120.0, account.producedKg().get("food_matrix"), 0.001);
        assertEquals(120.0, account.soldKg().get("food_matrix"), 0.001);
        assertEquals(240.0, account.salesCredits(), 0.001);
        assertEquals(0.0, account.tariffCredits(), 0.001);
        assertEquals(120.0, account.realizedResultCredits(), 0.001);
        assertEquals(670.0, result.corporations().getFirst().liquidCapitalReserves(), 0.001);
        assertEquals(0.0, result.empires().getFirst().treasuryCredits(), 0.001);
        assertEquals(830.0, result.marketAccounts().getFirst().unsettledSalesCredits(), 0.001);
        assertEquals(0.0, result.hubs().getFirst().activeOrders().get("nitrates").supplyKg(), 0.001);
        assertEquals(120.0, result.hubs().getFirst().activeOrders().get("food_matrix").supplyKg(), 0.001);
    }

    @Test
    void smelterSellsRecoveredByproductsAlongsidePrimaryOutput() {
        IndustrialFacility smelter = facility("pyro_iron_smelting");
        CommercialHub hub = new CommercialHub("hub", "earth", 0.05, 10_000.0, 1_200.0, 10.0,
                Map.of("iron_ore", order("iron_ore", 1_000, 0.1),
                        "carbon_monoxide_ice", order("carbon_monoxide_ice", 200, 0.1)));
        GameState state = state(smelter, hub, 3_000.0, List.of(), List.of("industrial_production"));

        var result = processor.process(state, Map.of("farm", 100), Map.of());
        IndustryAccount account = result.industryAccounts().getFirst();

        assertEquals(700.0, account.producedKg().get("refined_iron"), 0.001);
        assertEquals(300.0, account.producedKg().get("oxygen_gas"), 0.001);
        assertEquals(700.0, account.soldKg().get("refined_iron"), 0.001);
        assertEquals(300.0, account.soldKg().get("oxygen_gas"), 0.001);
        assertEquals(700.0, result.hubs().getFirst().activeOrders().get("refined_iron").supplyKg(), 0.001);
        assertEquals(300.0, result.hubs().getFirst().activeOrders().get("oxygen_gas").supplyKg(), 0.001);
    }

    @Test
    void stateFarmReportsGrossReceiptsAndInputExpensesSeparately() {
        IndustrialFacility farm = new IndustrialFacility("state_farm", "earth",
                "industrial_soil_cultivation", "empire", IndustrialFacility.PUBLIC_STATE,
                1, 100, "farmer", false, 0.0);
        CommercialHub hub = new CommercialHub("hub", "earth", 0.05, 10_000.0, 70.0, 10.0,
                Map.of("nitrates", order("nitrates", 10, 1),
                        "phosphates", order("phosphates", 5, 1),
                        "potash", order("potash", 5, 1),
                        "water_ice", order("water_ice", 50, 1),
                        "food_matrix", order("food_matrix", 0, 2)));
        Empire empire = new Empire("empire", "Empire", "human", "Individualist", 500.0,
                0.05, List.of("sol"), List.of(), Map.of(), List.of("industrial_production"), List.of());
        GameState state = GameState.builder().empires(List.of(empire))
                .industrialFacilities(List.of(farm)).commercialHubs(List.of(hub))
                .marketAccounts(List.of(new MarketAccount("hub", 1_000.0))).build();

        var result = processor.process(state, Map.of("state_farm", 100), Map.of("state_farm", 50.0));
        assertEquals(70.0, result.imperialExpenses().get("empire"), 0.001);
        assertEquals(240.0, result.imperialReceipts().get("empire"), 0.001);
        assertEquals(670.0, result.empires().getFirst().treasuryCredits(), 0.001);
    }

    @Test
    void noTechnologyOrPaidWorkersMeansNoNewProduction() {
        IndustrialFacility farm = facility("industrial_soil_cultivation");
        CommercialHub hub = new CommercialHub("hub", "earth", 0.05, 1_000.0, 10.0, 10.0,
                Map.of("nitrates", order("nitrates", 10, 1)));
        var locked = processor.process(state(farm, hub, 1_000.0, List.of(), List.of()),
                Map.of("farm", 100), Map.of());
        var unpaid = processor.process(state(farm, hub, 1_000.0, List.of(),
                List.of("industrial_production")), Map.of(), Map.of());

        assertTrue(locked.industryAccounts().getFirst().producedKg().isEmpty());
        assertTrue(unpaid.industryAccounts().getFirst().producedKg().isEmpty());
        assertEquals(500.0, locked.corporations().getFirst().liquidCapitalReserves(), 0.001);
    }

    @Test
    void minedOrePersistsWhenHubCannotPayAndSellsLater() {
        IndustrialFacility mine = facility("mining_outpost");
        CommercialHub hub = new CommercialHub("hub", "earth", 0.05, 1_000.0, 0.0, 10.0, Map.of());
        GeologicalDeposit deposit = new GeologicalDeposit("iron", "earth", "iron_ore",
                500.0, 500.0, 1.0, true, "corp");
        GameState state = state(mine, hub, 0.0, List.of(deposit), List.of("industrial_production"));

        var first = processor.process(state, Map.of("farm", 100), Map.of());
        assertEquals(100.0, first.industryAccounts().getFirst().unsoldStockKg().get("iron_ore"), 0.001);
        assertEquals(400.0, first.deposits().getFirst().remainingVolumeKg(), 0.001);
        assertEquals(0.0, first.industryAccounts().getFirst().salesCredits(), 0.001);

        GameState next = state.toBuilder().industryAccounts(first.industryAccounts())
                .geologicalDeposits(first.deposits())
                .marketAccounts(List.of(new MarketAccount("hub", 50.0))).build();
        var second = processor.process(next, Map.of(), Map.of());
        assertEquals(25.0, second.industryAccounts().getFirst().soldKg().get("iron_ore"), 0.001);
        assertEquals(75.0, second.industryAccounts().getFirst().unsoldStockKg().get("iron_ore"), 0.001);
        assertEquals(0.0, second.marketAccounts().getFirst().unsettledSalesCredits(), 0.001);
    }

    @Test
    void researchedImprovementIncreasesOutputWithoutCreatingExtraOre() {
        IndustrialFacility mine = facility("mining_outpost");
        CommercialHub hub = new CommercialHub("hub", "earth", 0.05, 1_000.0, 0.0, 10.0, Map.of());
        GeologicalDeposit deposit = new GeologicalDeposit("iron", "earth", "iron_ore",
                110.0, 110.0, 1.0, true, "corp");
        GameState state = state(mine, hub, 0.0, List.of(deposit),
                List.of("industrial_production", "geological_prospecting"));

        var result = processor.process(state, Map.of("farm", 100), Map.of());
        assertEquals(110.0, result.industryAccounts().getFirst().producedKg().get("iron_ore"), 0.001);
        assertEquals(0.0, result.deposits().getFirst().remainingVolumeKg(), 0.001);
    }

    private IndustrialFacility facility(String application) {
        return new IndustrialFacility("farm", "earth", application, "corp",
                IndustrialFacility.PRIVATE_CORPORATE, 1, 100, "farmer", false, 0.0);
    }

    private MarketOrder order(String resource, double supply, double price) {
        return new MarketOrder(resource, supply, 0.0, price, 0.0);
    }

    private GameState state(IndustrialFacility facility, CommercialHub hub, double hubCash,
                            List<GeologicalDeposit> deposits, List<String> tech) {
        Empire empire = new Empire("empire", "Empire", "human", "Individualist", 0.0, 0.05,
                List.of("sol"), List.of(), Map.of(), tech, List.of());
        Corporation corporation = new Corporation("corp", "Corp", "empire", "earth", "AGRICULTURE",
                500.0, List.of("farm"), List.of(), List.of());
        return GameState.builder().empires(List.of(empire)).corporations(List.of(corporation))
                .industrialFacilities(List.of(facility)).commercialHubs(List.of(hub))
                .marketAccounts(List.of(new MarketAccount("hub", hubCash)))
                .geologicalDeposits(deposits).build();
    }
}
