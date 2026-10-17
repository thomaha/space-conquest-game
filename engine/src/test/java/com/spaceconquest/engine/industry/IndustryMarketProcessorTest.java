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

    @Test void kineticDefenseCartridgesUsePaidResearchedProductionAndCreateStockDemand() {
        var plant = facility("kinetic_pd_ammunition_mfg");
        var hub = new CommercialHub("hub", "earth", 0, 10000, 5, 10,
                Map.of("steel", order("steel", 4, 1), "refined_tungsten", order("refined_tungsten", 1, 1)));
        var tech = List.of("electricity", "industrial_production", "energy_fields", "electromagnetic_weapons", "mass_drivers", "pd_mass_drivers");
        var initial = state(plant, hub, 1000, List.of(), tech);
        initial = requestStock(initial, "kinetic_pd_round", 5);
        initial = initial.withCommercialHubs(new com.spaceconquest.engine.market.MarketDemandProcessor().refresh(initial, List.of()));
        assertTrue(initial.commercialHubs().getFirst().activeOrders().get("kinetic_pd_round").demandKg() > 0);
        var result = processor.process(initial, Map.of("farm", 1), Map.of());
        assertEquals(5, result.industryAccounts().getFirst().producedKg().get("kinetic_pd_round"));
        assertEquals(5, result.industryAccounts().getFirst().inputCostsCredits());
        assertEquals(5, result.hubs().getFirst().activeOrders().get("kinetic_pd_round").supplyKg());
        var locked = state(plant, hub, 1000, List.of(), tech.stream().filter(id -> !id.equals("pd_mass_drivers")).toList());
        assertTrue(processor.process(locked, Map.of("farm", 1), Map.of()).industryAccounts().getFirst().producedKg().isEmpty());
        assertTrue(processor.process(initial, Map.of(), Map.of(), Map.of("farm", 1)).industryAccounts().getFirst().producedKg().isEmpty());
    }

    @Test void guidedAmmunitionUsesOwnedResearchPaidInputsWorkersAndPoweredIndustry() {
        var plant = facility("guided_missile_mfg");
        var hub = new CommercialHub("hub", "earth", 0, 10000, 100, 10,
                Map.of("steel", order("steel", 60, 1), "silicon", order("silicon", 10, 1),
                        "hydrocarbons", order("hydrocarbons", 20, 1), "nitrates", order("nitrates", 10, 1)));
        var initial = state(plant, hub, 1000, List.of(), List.of("electricity", "industrial_production", "rocketry"));
        initial = requestStock(initial, "guided_missile", 100);
        initial = initial.withCommercialHubs(new com.spaceconquest.engine.market.MarketDemandProcessor().refresh(initial, List.of()));
        assertTrue(initial.commercialHubs().getFirst().activeOrders().get("guided_missile").demandKg() > 0);
        var result = processor.process(initial, Map.of("farm", 10), Map.of("farm", 0.0));
        var account = result.industryAccounts().getFirst();
        assertEquals(100, account.producedKg().get("guided_missile"), 1e-9);
        assertEquals(100, account.inputCostsCredits(), 1e-9);
        assertEquals(100, result.hubs().getFirst().activeOrders().get("guided_missile").supplyKg(), 1e-9);
        assertEquals(0, result.hubs().getFirst().activeOrders().get("steel").supplyKg(), 1e-9);
        var locked = state(plant, hub, 1000, List.of(), List.of("electricity", "industrial_production"));
        assertTrue(processor.process(locked, Map.of("farm", 10), Map.of()).industryAccounts().getFirst().producedKg().isEmpty());
        assertTrue(processor.process(initial, Map.of(), Map.of(), Map.of("farm", 10)).industryAccounts().getFirst().producedKg().isEmpty());
        assertEquals(60, initial.commercialHubs().getFirst().activeOrders().get("steel").supplyKg());
    }

    @Test
    void productionRequirementsExistInResearchCatalogAndImprovementsUseTechnologies() throws IOException {
        var technologies = DataModelLoader.loadTechnologies();
        var technologyIds = technologies.stream().map(technology -> technology.id()).toList();
        var researchIds = new java.util.HashSet<>(technologyIds);
        technologies.forEach(technology -> technology.applications().forEach(application -> researchIds.add(application.id())));
        for (var recipe : IndustryRecipeCatalog.all()) {
            assertTrue(researchIds.containsAll(recipe.requiredTechnologies()), recipe.applicationId());
            assertTrue(technologyIds.containsAll(recipe.improvements().keySet()), recipe.applicationId());
        }
    }

    @Test
    void foodProcessorBuysInputsProducesAndSellsAgainstHubCash() {
        var plant = facility("food_processing");
        var hub = new CommercialHub("hub", "earth", .05, 10000, 1000, 10,
                Map.of("harvested_crops", order("harvested_crops", 1000, .1),
                        "food_matrix", order("food_matrix", 0, 2), "agricultural_biomass", order("agricultural_biomass", 0, 2)));
        var initial = state(plant, hub, 3000, List.of(), List.of("industrial_production"));
        var result = processor.process(initial, Map.of("farm", 100), Map.of("farm", 50.0));
        var account = result.industryAccounts().getFirst();
        assertEquals(100, account.inputCostsCredits(), .001);
        assertEquals(800, account.producedKg().get("food_matrix"), .001);
        assertEquals(200, account.producedKg().get("agricultural_biomass"), .001);
        assertEquals(800, account.soldKg().get("food_matrix"), .001);
        assertEquals(1600, account.salesCredits(), .001);
        assertEquals(0, account.tariffCredits(), .001);
        assertEquals(1450, account.realizedResultCredits(), .001);
        assertEquals(2000, result.corporations().getFirst().liquidCapitalReserves(), .001);
        assertEquals(1500, result.marketAccounts().getFirst().unsettledSalesCredits(), .001);
        assertEquals(3500, result.corporations().getFirst().liquidCapitalReserves()
                + result.marketAccounts().getFirst().unsettledSalesCredits(), .001);
        assertEquals(0, result.hubs().getFirst().activeOrders().get("harvested_crops").supplyKg(), .001);
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
        assertEquals(890.0, account.producedKg().get("oxygen_gas"), 0.001);
        assertEquals(110.0, account.producedKg().get("hydrogen_gas"), 0.001);
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
                .empires(List.of(new Empire("empire", "Empire", "human", "Individualist", 0, .05,
                        List.of("sol"), List.of(), Map.of(), List.of("industrial_production", "water_ice_mining", "ice_water_treatment"), List.of())))
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
    void stateFoodProcessorReportsGrossReceiptsAndInputExpensesSeparately() {
        var plant = new IndustrialFacility("state_food", "earth", "food_processing", "empire", IndustrialFacility.PUBLIC_STATE, 1, 10, "industrial_worker", false, 0);
        var hub = new CommercialHub("hub", "earth", .05, 10000, 1000, 10,
                Map.of("harvested_crops", order("harvested_crops", 1000, .1),
                        "food_matrix", order("food_matrix", 0, 2), "agricultural_biomass", order("agricultural_biomass", 0, 2)));
        var empire = new Empire("empire", "Empire", "human", "Individualist", 500, .05, List.of("sol"), List.of(), Map.of(),
                List.of("industrial_production", "food_processing"), List.of());
        var initial = GameState.builder().empires(List.of(empire)).industrialFacilities(List.of(plant)).commercialHubs(List.of(hub))
                .industryAccounts(List.of(IndustryAccount.empty("state_food").withOperatingCash(500)))
                .marketAccounts(List.of(new MarketAccount("hub", 3000))).build();
        var result = processor.process(initial, Map.of("state_food", 10), Map.of("state_food", 50.0));
        assertTrue(result.imperialExpenses().isEmpty()); assertTrue(result.imperialReceipts().isEmpty());
        assertEquals(500, result.empires().getFirst().treasuryCredits(), .001);
        assertEquals(80, result.industryAccounts().getFirst().maintenanceCostsCredits(), .001);
        assertEquals(1920, result.industryAccounts().getFirst().operatingCashCredits(), .001);
    }
    @Test
    void hiveFoodProcessorUsesPhysicalInputsAndStoresOutputWithoutCreditsOrCorporation() {
        var plant = new IndustrialFacility("hive_food", "nest", "food_processing", "hive", IndustrialFacility.HIVE_GRID, 1, 10, "industrial_worker", false, 0);
        var hub = new CommercialHub("hub", "nest", .05, 10000, 1000, 10, Map.of("harvested_crops", order("harvested_crops", 1000, 1)));
        var hive = new Empire("hive", "Hive", "plasma_anomaly", "Hive mind", 0, 0, List.of("nest_system"), List.of(), Map.of(),
                List.of("industrial_production", "food_processing"), List.of());
        var initial = GameState.builder().empires(List.of(hive)).industrialFacilities(List.of(plant)).commercialHubs(List.of(hub)).build();
        var result = processor.process(initial, Map.of("hive_food", 10), Map.of());
        var account = result.industryAccounts().getFirst();
        assertEquals(800, account.producedKg().get("food_matrix"), .001);
        assertEquals(800, result.hubs().getFirst().activeOrders().get("food_matrix").supplyKg(), .001);
        assertEquals(0, result.hubs().getFirst().activeOrders().get("harvested_crops").supplyKg(), .001);
        assertEquals(0, account.inputCostsCredits()); assertEquals(0, account.salesCredits());
        assertTrue(result.marketAccounts().isEmpty()); assertTrue(result.corporations().isEmpty());
        assertEquals(0, result.empires().getFirst().treasuryCredits());
    }
    @Test
    void farmBiomassConsumerAndLuxuryFacilitiesFeedOneAnother() throws IOException {
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
                List.of("sol"), List.of(), Map.of(), List.of("industrial_production", "industrial_soil_cultivation",
                        "biomass_processing", "consumer_goods_mfg", "luxury_goods_mfg"), List.of());
        Corporation corp = new Corporation("corp", "Corp", "empire", "earth", "CONSUMER",
                1_000_000.0, facilities.stream().map(IndustrialFacility::id).toList(), List.of(), List.of());
        GameState state = GameState.builder().empires(List.of(empire)).corporations(List.of(corp))
                .solarSystems(DataModelLoader.loadSolarSystems())
                .industrialFacilities(facilities).commercialHubs(List.of(hub))
                .marketAccounts(List.of(new MarketAccount("hub", 1_000_000.0))).build();

        for (int day = 0; day < 119; day++) {
            var growing = processor.process(state.withTurn(day), Map.of("farm", 1), Map.of());
            assertTrue(growing.industryAccounts().getFirst().producedKg().isEmpty());
            state = state.toBuilder().corporations(growing.corporations()).commercialHubs(growing.hubs())
                    .industryAccounts(growing.industryAccounts()).marketAccounts(growing.marketAccounts()).build();
        }

        var result = processor.process(state.withTurn(119),
                Map.of("farm", 250, "biomass", 10, "consumer", 10, "luxury", 10), Map.of());
        assertEquals(14400.0, result.industryAccounts().getFirst().producedKg().get("agricultural_biomass"), 0.001);
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

    @Test
    void massDriverSlugsRequireTheirRecipeResearchPaidInputsWorkersAndPower() throws Exception {
        var recipe = IndustryRecipeCatalog.find("mass_driver_ammunition_mfg");
        var application = com.spaceconquest.engine.DataModelLoader.loadTechnologies().stream()
                .flatMap(t -> t.applications().stream()).filter(a -> a.id().equals(recipe.applicationId())).findFirst().orElseThrow();
        assertEquals(application.productionRecipe(), recipe);
        var plant = facility(recipe.applicationId());
        var hub = new CommercialHub("hub", "earth", 0, 10000, 20, 10,
                Map.of("steel", order("steel", 5, 1), "refined_tungsten", order("refined_tungsten", 15, 1)));
        var initial = state(plant, hub, 10000, List.of(), recipe.requiredTechnologies());
        initial = requestStock(initial, "mass_driver_slug", 20);
        initial = initial.withCommercialHubs(new com.spaceconquest.engine.market.MarketDemandProcessor().refresh(initial, List.of()));
        assertTrue(initial.commercialHubs().getFirst().activeOrders().get("mass_driver_slug").demandKg() > 0);
        var result = processor.process(initial, Map.of("farm", 2), Map.of());
        assertEquals(20, result.industryAccounts().getFirst().producedKg().get("mass_driver_slug"));
        assertEquals(20, result.industryAccounts().getFirst().inputCostsCredits());
        assertEquals(0, result.hubs().getFirst().activeOrders().get("steel").supplyKg());
        assertEquals(0, result.hubs().getFirst().activeOrders().get("refined_tungsten").supplyKg());
        assertEquals(20, result.hubs().getFirst().activeOrders().get("mass_driver_slug").supplyKg());
        var locked = state(plant, hub, 10000, List.of(), recipe.requiredTechnologies().stream()
                .filter(id -> !id.equals(recipe.applicationId())).toList(), false);
        var missingInputs = initial.withCommercialHubs(List.of(new CommercialHub("hub", "earth", 0, 10000, 5, 10,
                Map.of("steel", order("steel", 5, 1)))));
        var insolvent = initial.toBuilder().corporations(List.of(new Corporation("corp", "Corp", "empire", "earth",
                "INDUSTRY", 0, List.of("farm"), List.of(), List.of()))).build();
        for (var unavailable : List.of(locked, missingInputs, insolvent))
            assertTrue(processor.process(unavailable, Map.of("farm", 2), Map.of())
                    .industryAccounts().getFirst().producedKg().isEmpty());
        assertTrue(processor.process(initial, Map.of(), Map.of()).industryAccounts().getFirst().producedKg().isEmpty());
        assertTrue(processor.process(initial, Map.of(), Map.of(), Map.of("farm", 2))
                .industryAccounts().getFirst().producedKg().isEmpty());
    }

    @Test
    void cannonRoundsUseApplicationRecipeResearchPaidMaterialsWorkersAndPower() throws Exception {
        var recipe = IndustryRecipeCatalog.find("cannon_ammunition_mfg");
        var app = DataModelLoader.loadTechnologies().stream().flatMap(t -> t.applications().stream())
                .filter(a -> a.id().equals(recipe.applicationId())).findFirst().orElseThrow();
        assertEquals(app.productionRecipe(), recipe);
        var plant = facility(recipe.applicationId());
        var hub = new CommercialHub("hub", "earth", 0, 10000, 5, 10,
                Map.of("steel", order("steel", 4, 1), "hydrocarbons", order("hydrocarbons", 1, 1)));
        var initial = state(plant, hub, 10000, List.of(), recipe.requiredTechnologies());
        initial = requestStock(initial, "cannon_round", 5);
        initial = initial.withCommercialHubs(new com.spaceconquest.engine.market.MarketDemandProcessor().refresh(initial, List.of()));
        assertTrue(initial.commercialHubs().getFirst().activeOrders().get("cannon_round").demandKg() > 0);
        var result = processor.process(initial, Map.of("farm", 1), Map.of());
        assertEquals(5, result.industryAccounts().getFirst().producedKg().get("cannon_round"));
        assertEquals(5, result.industryAccounts().getFirst().inputCostsCredits());
        assertEquals(0, result.hubs().getFirst().activeOrders().get("steel").supplyKg());
        assertEquals(0, result.hubs().getFirst().activeOrders().get("hydrocarbons").supplyKg());
        assertEquals(5, result.hubs().getFirst().activeOrders().get("cannon_round").supplyKg());
        var locked = state(plant, hub, 10000, List.of(), recipe.requiredTechnologies().stream()
                .filter(id -> !id.equals(recipe.applicationId())).toList(), false);
        var missingInputs = initial.withCommercialHubs(List.of(new CommercialHub("hub", "earth", 0, 10000, 4, 10,
                Map.of("steel", order("steel", 4, 1)))));
        var insolvent = initial.toBuilder().corporations(List.of(new Corporation("corp", "Corp", "empire", "earth",
                "INDUSTRY", 0, List.of("farm"), List.of(), List.of()))).build();
        for (var unavailable : List.of(locked, missingInputs, insolvent))
            assertTrue(processor.process(unavailable, Map.of("farm", 1), Map.of())
                    .industryAccounts().getFirst().producedKg().isEmpty());
        assertTrue(processor.process(initial, Map.of(), Map.of()).industryAccounts().getFirst().producedKg().isEmpty());
        assertTrue(processor.process(initial, Map.of(), Map.of(), Map.of("farm", 1))
                .industryAccounts().getFirst().producedKg().isEmpty());
    }

    private IndustrialFacility facility(String application) {
        var work = ProductionWork.definition(application);
        return new IndustrialFacility("farm", "earth", application, "corp",
                IndustrialFacility.PRIVATE_CORPORATE, 1, 100,
                work == null ? "farmer" : work.equipment().operatorProfession(), false, 0.0);
    }

    private GameState requestStock(GameState state, String material, double kg) {
        var hub = state.commercialHubs().getFirst();
        var targets = Map.of(material, new com.spaceconquest.engine.market.HubStockTarget(kg, kg, true));
        var orders = new java.util.HashMap<>(hub.activeOrders());
        orders.putIfAbsent(material, new MarketOrder(material, 0, 0, 2, 0));
        return state.withCommercialHubs(List.of(new CommercialHub(hub.id(), hub.entityId(), hub.transactionTariffRate(),
                hub.storageCapacityKg(), hub.currentStoredWeightKg(), hub.logisticsRangeUnits(), orders,
                targets, hub.stockHistory(), hub.reservedTradeFuel())));
    }

    private MarketOrder order(String resource, double supply, double price) {
        return new MarketOrder(resource, supply, 1_000.0, price, 0.0);
    }

    private GameState state(IndustrialFacility facility, CommercialHub hub, double hubCash,
                            List<GeologicalDeposit> deposits, List<String> tech) {
        return state(facility, hub, hubCash, deposits, tech, true);
    }
    private GameState state(IndustrialFacility facility, CommercialHub hub, double hubCash,
                            List<GeologicalDeposit> deposits, List<String> tech, boolean ownsApplication) {
        // These production fixtures own the selected application; missing parent research still blocks it.
        var knowledge = new java.util.ArrayList<>(tech); if (ownsApplication) knowledge.add(facility.applicationId());
        Empire empire = new Empire("empire", "Empire", "human", "Individualist", 0.0, 0.05,
                List.of("sol"), List.of(), Map.of(), knowledge, List.of());
        Corporation corporation = new Corporation("corp", "Corp", "empire", "earth", "AGRICULTURE",
                500.0, List.of("farm"), List.of(), List.of());
        return GameState.builder().empires(List.of(empire)).corporations(List.of(corporation))
                .industrialFacilities(List.of(facility)).commercialHubs(List.of(hub))
                .marketAccounts(List.of(new MarketAccount("hub", hubCash)))
                .geologicalDeposits(deposits).build();
    }
}
