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
                        "purified_water", order("purified_water", 50, 1),
                        "food_matrix", order("food_matrix", 0, 2),
                        "agricultural_biomass", order("agricultural_biomass", 0, 2)));
        GameState state = state(farm, hub, 1_000.0, List.of(), List.of("industrial_production"));

        var result = processor.process(state, Map.of("farm", 100), Map.of("farm", 50.0));
        IndustryAccount account = result.industryAccounts().getFirst();

        assertEquals(70.0, account.inputCostsCredits(), 0.001);
        assertEquals(120.0, account.producedKg().get("food_matrix"), 0.001);
        assertEquals(4.0, account.producedKg().get("agricultural_biomass"), 0.001);
        assertEquals(120.0, account.soldKg().get("food_matrix"), 0.001);
        assertEquals(198.4, account.salesCredits(), 0.001);
        assertEquals(0.0, account.tariffCredits(), 0.001);
        assertEquals(78.4, account.realizedResultCredits(), 0.001);
        assertEquals(628.4, result.corporations().getFirst().liquidCapitalReserves(), 0.001);
        assertEquals(0.0, result.empires().getFirst().treasuryCredits(), 0.001);
        assertEquals(871.6, result.marketAccounts().getFirst().unsettledSalesCredits(), 0.001);
        assertEquals(state.corporations().getFirst().liquidCapitalReserves()
                        + state.marketAccounts().getFirst().unsettledSalesCredits(),
                result.corporations().getFirst().liquidCapitalReserves()
                        + result.marketAccounts().getFirst().unsettledSalesCredits(), 0.001);
        assertEquals(0.0, result.hubs().getFirst().activeOrders().get("nitrates").supplyKg(), 0.001);
        assertEquals(120.0, result.hubs().getFirst().activeOrders().get("food_matrix").supplyKg(), 0.001);
    }

    @Test
    void smelterSellsRecoveredByproductsAlongsidePrimaryOutput() {
        IndustrialFacility smelter = facility("pyro_iron_smelting");
        CommercialHub hub = new CommercialHub("hub", "earth", 0.05, 10_000.0, 1_112.5, 10.0,
                Map.of("iron_ore", order("iron_ore", 1_000, 0.1),
                        "carbon", order("carbon", 112.5, 0.1),
                        "refined_iron", order("refined_iron", 0, 2)));
        GameState state = state(smelter, hub, 3_000.0, List.of(), List.of("industrial_production"));

        var result = processor.process(state, Map.of("farm", 100), Map.of());
        IndustryAccount account = result.industryAccounts().getFirst();

        assertEquals(700.0, account.producedKg().get("refined_iron"), 0.001);
        assertEquals(412.5, account.producedKg().get("carbon_dioxide_gas"), 0.001);
        assertEquals(700.0, account.soldKg().get("refined_iron"), 0.001);
        assertEquals(412.5, account.unsoldStockKg().get("carbon_dioxide_gas"), 0.001);
        assertEquals(700.0, result.hubs().getFirst().activeOrders().get("refined_iron").supplyKg(), 0.001);
        assertTrue(!result.hubs().getFirst().activeOrders().containsKey("carbon_dioxide_gas"));
    }

    @Test
    void waterElectrolysisProducesLifeSupportOxygenWithoutSmelting() {
        IndustrialFacility plant = facility("water_electrolysis");
        CommercialHub hub = new CommercialHub("hub", "earth", 0.0, 10_000.0, 1_000.0, 10.0,
                Map.of("purified_water", order("purified_water", 1_000.0, 0.1)));
        GameState state = state(plant, hub, 5_000.0, List.of(),
                List.of("industrial_production", "electricity"));

        var result = processor.process(state, Map.of("farm", 10), Map.of());
        IndustryAccount account = result.industryAccounts().getFirst();
        assertEquals(888.9, account.producedKg().get("oxygen_gas"), 0.001);
        assertEquals(111.1, account.producedKg().get("hydrogen_gas"), 0.001);
        assertEquals(0.0, result.hubs().getFirst().activeOrders().get("purified_water").supplyKg(), 0.001);
    }

    @Test
    void surfaceWaterRequiresLiquidWaterAndSpaceWaterRequiresMinedIce() throws IOException {
        IndustrialFacility treatment = facility("surface_water_treatment");
        CommercialHub wetHub = new CommercialHub("hub", "earth", 0.0, 10_000.0, 0.0, 10.0,
                Map.of("purified_water", order("purified_water", 0.0, 1.5)));
        GameState wet = state(treatment, wetHub, 10_000.0, List.of(), List.of("industrial_production"))
                .withSolarSystems(DataModelLoader.loadSolarSystems());
        var wetResult = processor.process(wet, Map.of("farm", 10), Map.of());
        assertEquals(1_000.0, wetResult.industryAccounts().getFirst()
                .producedKg().get("purified_water"), 0.001);

        IndustrialFacility dryTreatment = new IndustrialFacility("farm", "moon",
                "surface_water_treatment", "corp", IndustrialFacility.PRIVATE_CORPORATE,
                1, 10, "technician", false, 0.0);
        CommercialHub dryHub = new CommercialHub("hub", "moon", 0.0, 10_000.0, 0.0, 10.0,
                Map.of("purified_water", order("purified_water", 0.0, 1.5)));
        GameState dry = state(dryTreatment, dryHub, 10_000.0, List.of(),
                List.of("industrial_production")).withSolarSystems(DataModelLoader.loadSolarSystems());
        assertTrue(processor.process(dry, Map.of("farm", 10), Map.of())
                .industryAccounts().getFirst().producedKg().isEmpty());

        List<IndustrialFacility> iceFacilities = List.of(
                new IndustrialFacility("mine", "moon", "water_ice_mining", "corp",
                        IndustrialFacility.PRIVATE_CORPORATE, 1, 100, "miner", false, 0.0),
                new IndustrialFacility("treat", "moon", "ice_water_treatment", "corp",
                        IndustrialFacility.PRIVATE_CORPORATE, 1, 10, "technician", false, 0.0));
        GeologicalDeposit ice = new GeologicalDeposit("ice", "moon", "water_ice",
                2_000.0, 2_000.0, 1.0, true, "corp");
        CommercialHub iceHub = new CommercialHub("hub", "moon", 0.0, 10_000.0, 0.0, 10.0,
                Map.of("water_ice", order("water_ice", 0.0, 1.0),
                        "purified_water", order("purified_water", 0.0, 1.5)));
        GameState iceState = dry.toBuilder().industrialFacilities(iceFacilities)
                .geologicalDeposits(List.of(ice)).commercialHubs(List.of(iceHub))
                .marketAccounts(List.of(new MarketAccount("hub", 10_000.0))).build();
        var iceResult = processor.process(iceState, Map.of("mine", 100, "treat", 10), Map.of());
        assertEquals(1_000.0, iceResult.industryAccounts().getFirst().producedKg().get("water_ice"), 0.001);
        assertEquals(950.0, iceResult.industryAccounts().get(1).producedKg().get("purified_water"), 0.001);
        assertEquals(1_000.0, iceResult.deposits().getFirst().remainingVolumeKg(), 0.001);
    }

    @Test
    void scarceGoldMineProducesLittleAndKeepsOutputWithoutBuyer() {
        IndustrialFacility mine = facility("gold_mining");
        CommercialHub hub = new CommercialHub("hub", "earth", 0.0, 10_000.0, 0.0, 10.0, Map.of());
        GeologicalDeposit gold = new GeologicalDeposit("gold", "earth", "gold",
                100.0, 100.0, 1.0, true, "corp");
        var result = processor.process(state(mine, hub, 10_000.0, List.of(gold),
                List.of("industrial_production")), Map.of("farm", 100), Map.of());
        assertEquals(10.0, result.industryAccounts().getFirst().producedKg().get("gold"), 0.001);
        assertEquals(10.0, result.industryAccounts().getFirst().unsoldStockKg().get("gold"), 0.001);
        assertEquals(90.0, result.deposits().getFirst().remainingVolumeKg(), 0.001);
        assertTrue(result.industryAccounts().getFirst().soldKg().isEmpty());
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
                        "purified_water", order("purified_water", 50, 1),
                        "food_matrix", order("food_matrix", 0, 2),
                        "agricultural_biomass", order("agricultural_biomass", 0, 2)));
        Empire empire = new Empire("empire", "Empire", "human", "Individualist", 500.0,
                0.05, List.of("sol"), List.of(), Map.of(), List.of("industrial_production"), List.of());
        GameState state = GameState.builder().empires(List.of(empire))
                .industrialFacilities(List.of(farm)).commercialHubs(List.of(hub))
                .industryAccounts(List.of(IndustryAccount.empty("state_farm").withOperatingCash(500.0)))
                .marketAccounts(List.of(new MarketAccount("hub", 1_000.0))).build();

        var result = processor.process(state, Map.of("state_farm", 100), Map.of("state_farm", 50.0));
        assertTrue(result.imperialExpenses().isEmpty());
        assertTrue(result.imperialReceipts().isEmpty());
        assertEquals(500.0, result.empires().getFirst().treasuryCredits(), 0.001);
        assertEquals(80.0, result.industryAccounts().getFirst().maintenanceCostsCredits(), 0.001);
        assertEquals(548.4, result.industryAccounts().getFirst().operatingCashCredits(), 0.001);
    }

    @Test
    void hiveFarmUsesPhysicalInputsAndStoresOutputWithoutCreditsOrCorporation() {
        IndustrialFacility farm = new IndustrialFacility("hive_farm", "nest",
                "industrial_soil_cultivation", "hive", IndustrialFacility.HIVE_GRID,
                1, 100, "farmer", false, 0.0);
        CommercialHub hub = new CommercialHub("hub", "nest", 0.05, 10_000.0, 70.0, 10.0,
                Map.of("nitrates", order("nitrates", 10, 1),
                        "phosphates", order("phosphates", 5, 1),
                        "potash", order("potash", 5, 1),
                        "purified_water", order("purified_water", 50, 1)));
        Empire hive = new Empire("hive", "Hive", "plasma_anomaly", "Hive mind", 0.0,
                0.0, List.of("nest_system"), List.of(), Map.of(),
                List.of("industrial_production"), List.of());
        GameState state = GameState.builder().empires(List.of(hive))
                .industrialFacilities(List.of(farm)).commercialHubs(List.of(hub)).build();

        var result = processor.process(state, Map.of("hive_farm", 100), Map.of());
        IndustryAccount account = result.industryAccounts().getFirst();

        assertEquals(120.0, account.producedKg().get("food_matrix"), 0.001);
        assertEquals(120.0, result.hubs().getFirst().activeOrders().get("food_matrix").supplyKg(), 0.001);
        assertEquals(0.0, result.hubs().getFirst().activeOrders().get("nitrates").supplyKg(), 0.001);
        assertEquals(0.0, account.inputCostsCredits(), 0.001);
        assertEquals(0.0, account.salesCredits(), 0.001);
        assertTrue(result.marketAccounts().isEmpty());
        assertTrue(result.corporations().isEmpty());
        assertEquals(0.0, result.empires().getFirst().treasuryCredits(), 0.001);
    }

    @Test
    void farmBiomassConsumerAndLuxuryFacilitiesFeedOneAnother() {
        List<IndustrialFacility> facilities = List.of(
                new IndustrialFacility("farm", "earth", "industrial_soil_cultivation", "corp",
                        IndustrialFacility.PRIVATE_CORPORATE, 1, 250, "farmer", false, 0.0),
                new IndustrialFacility("biomass", "earth", "biomass_processing", "corp",
                        IndustrialFacility.PRIVATE_CORPORATE, 1, 10, "industrial_worker", false, 0.0),
                new IndustrialFacility("consumer", "earth", "consumer_goods_mfg", "corp",
                        IndustrialFacility.PRIVATE_CORPORATE, 1, 10, "industrial_worker", false, 0.0),
                new IndustrialFacility("luxury", "earth", "luxury_goods_mfg", "corp",
                        IndustrialFacility.PRIVATE_CORPORATE, 1, 10, "industrial_worker", false, 0.0));
        Map<String, MarketOrder> orders = Map.ofEntries(
                Map.entry("nitrates", order("nitrates", 2500, 1)),
                Map.entry("phosphates", order("phosphates", 1250, 1)),
                Map.entry("potash", order("potash", 1250, 1)),
                Map.entry("purified_water", order("purified_water", 12600, 1)),
                Map.entry("refined_aluminum", order("refined_aluminum", 250, 1)),
                Map.entry("refined_copper", order("refined_copper", 250, 1)),
                Map.entry("silicon", order("silicon", 100, 1)),
                Map.entry("refined_rare_earths", order("refined_rare_earths", 1, 1)),
                Map.entry("food_matrix", order("food_matrix", 0, 2)),
                Map.entry("agricultural_biomass", order("agricultural_biomass", 0, 2)),
                Map.entry("bio_polymers", order("bio_polymers", 0, 2)),
                Map.entry("consumer_goods", order("consumer_goods", 0, 2)),
                Map.entry("luxury_goods", order("luxury_goods", 0, 2)));
        CommercialHub hub = new CommercialHub("hub", "earth", 0.0, 1_000_000.0,
                18_201.0, 10.0, orders);
        Empire empire = new Empire("empire", "Empire", "human", "Individualist", 0.0, 0.05,
                List.of("sol"), List.of(), Map.of(), List.of("industrial_production"), List.of());
        Corporation corp = new Corporation("corp", "Corp", "empire", "earth", "CONSUMER",
                1_000_000.0, facilities.stream().map(IndustrialFacility::id).toList(), List.of(), List.of());
        GameState state = GameState.builder().empires(List.of(empire)).corporations(List.of(corp))
                .industrialFacilities(facilities).commercialHubs(List.of(hub))
                .marketAccounts(List.of(new MarketAccount("hub", 1_000_000.0))).build();

        var result = processor.process(state,
                Map.of("farm", 250, "biomass", 10, "consumer", 10, "luxury", 10), Map.of());
        assertEquals(1000.0, result.industryAccounts().get(0).producedKg().get("agricultural_biomass"), 0.001);
        assertEquals(1000.0, result.industryAccounts().get(1).producedKg().get("bio_polymers"), 0.001);
        assertEquals(1000.0, result.industryAccounts().get(2).producedKg().get("consumer_goods"), 0.001);
        assertEquals(800.0, result.industryAccounts().get(3).producedKg().get("luxury_goods"), 0.001);
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
        CommercialHub hub = new CommercialHub("hub", "earth", 0.05, 1_000.0, 0.0, 10.0,
                Map.of("iron_ore", order("iron_ore", 0, 2)));
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
        assertEquals(31.25, second.industryAccounts().getFirst().soldKg().get("iron_ore"), 0.001);
        assertEquals(68.75, second.industryAccounts().getFirst().unsoldStockKg().get("iron_ore"), 0.001);
        assertEquals(0.0, second.marketAccounts().getFirst().unsettledSalesCredits(), 0.001);
    }

    @Test
    void researchedImprovementIncreasesOutputWithoutCreatingExtraOre() {
        IndustrialFacility mine = facility("mining_outpost");
        CommercialHub hub = new CommercialHub("hub", "earth", 0.05, 1_000.0, 0.0, 10.0,
                Map.of("iron_ore", order("iron_ore", 0, 2)));
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
        return new MarketOrder(resource, supply, 1_000.0, price, 0.0);
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
