package com.spaceconquest.engine.industry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.MarketAccount;
import com.spaceconquest.engine.market.MarketDemandProcessor;
import com.spaceconquest.engine.market.MarketProcessor;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RefineryProcessTest {
    @TempDir Path directory;
    @Test void baselineBatchCapturesTheFeedstockAndConservesEveryElementAcrossProductsAndTailings() throws Exception {
        var state = world(false);
        var plan = plan(state);
        assertEquals(800, plan.recipe().outputsKg().get("refined_rare_earths"), 1e-9);
        assertEquals(.5, plan.recipe().outputsKg().get("silver"), 1e-9);
        assertEquals(.01, plan.recipe().outputsKg().get("gold"), 1e-9);
        var completed = plan.complete(.5, state.turn());
        assertEquals(552.5, completed.latestReceipt().intake().massKg(), 1e-9);
        assertEquals(152.245, completed.storedKg(), 1e-9);
        assertEquals(.75, completed.tailings().getFirst().elementsKg().get("Ag"), 1e-9);
        assertConserved(completed.latestReceipt());
        assertThrows(UnsupportedOperationException.class, () -> completed.tailings().clear());
        assertThrows(UnsupportedOperationException.class, () -> completed.tailings().getFirst().elementsKg().put("Ag", 999.0));
        assertTrue(RefineryProcess.inventory(state, "refinery").tailings().isEmpty());
    }
    @Test void discoveryImprovesRecoveryFromTheSameOreAndChargesMoreProcessResources() throws Exception {
        var old = plan(world(false)); var improved = plan(world(true));
        assertEquals(1.5, improved.recipe().outputsKg().get("silver"), 1e-9);
        assertEquals(.07, improved.recipe().outputsKg().get("gold"), 1e-9);
        assertEquals(old.recipe().outputsKg().get("refined_rare_earths"), improved.recipe().outputsKg().get("refined_rare_earths"));
        assertEquals(.5, improved.residuePerBatch().elementsKg().get("Ag"), 1e-9);
        assertEquals(.03, improved.residuePerBatch().elementsKg().get("Au"), 1e-9);
        assertEquals(old.intakePerBatch().elementsKg().get("Ag"), improved.intakePerBatch().elementsKg().get("Ag"));
        assertTrue(improved.profile().work().workPerBatch() > old.profile().work().workPerBatch());
        assertTrue(improved.profile().work().energyKwhPerBatch() > old.profile().work().energyKwhPerBatch());
        assertTrue(improved.recipe().inputsKg().get("purified_water") > old.recipe().inputsKg().get("purified_water"));
        assertConserved(improved.complete(1, 1).latestReceipt());
    }
    @Test void retainedGradesRatherThanTheOriginalOreDetermineReprocessingAndDepletedSelectionsStayBlocked() throws Exception {
        var state = world(true);
        var lot = new RefineryLot("graded", "assayed residue", Map.of("Ag", 2.0, "F", 998.0));
        state = inventory(state, new RefineryState(null, lot.id(), List.of(lot), null));
        var quote = plan(state);
        assertEquals(1.5, quote.recipe().outputsKg().get("silver"), 1e-9);
        assertFalse(quote.recipe().outputsKg().containsKey("gold"));
        assertFalse(quote.recipe().inputsKg().containsKey("rare_earth_fluorides"));
        var completed = quote.complete(1, 1);
        assertTrue(completed.tailings().stream().noneMatch(l -> l.id().equals("graded")));
        assertEquals(.5, completed.tailings().getFirst().elementsKg().get("Ag"), 1e-9);
        assertConserved(completed.latestReceipt());
        var depleted = plan(inventory(state, completed));
        assertEquals(0, depleted.maximumBatches()); assertTrue(depleted.blocker().contains("depleted"));
        assertThrows(IllegalArgumentException.class, () -> depleted.complete(1, 2));
    }
    @Test void actualProductionBuysInputsPaysElectricityAndPlacesSeparateProductsInTheMarket() throws Exception {
        var state = world(false); double before = money(state);
        var result = execute(state, 6000, 6000, 10);
        var account = account(result);
        assertEquals(.5, account.producedKg().get("silver"), 1e-9);
        assertEquals(120, account.powerCostsCredits(), 1e-9);
        assertEquals(6000, result.productionEnergyKwh().get("earth"), 1e-9);
        assertEquals(120, result.productionPlantSales().get("plant"), 1e-9);
        assertEquals(.5, result.hubs().getFirst().activeOrders().get("silver").supplyKg(), 1e-9);
        assertEquals(9000, result.hubs().getFirst().activeOrders().get("rare_earth_fluorides").supplyKg(), 1e-9);
        assertEquals(9900, result.hubs().getFirst().activeOrders().get("purified_water").supplyKg(), 1e-9);
        assertEquals(304.49, account.refineryState().storedKg(), 1e-9);
        assertEquals(before, money(result), .001);
        assertConserved(account.refineryState().latestReceipt());
    }
    @Test void shortagesAndFullRetainedStorageDoNotConsumeInputsOrCreateResidue() throws Exception {
        var state = world(false);
        var full = new RefineryLot("full", "old waste", Map.of("F", 100000.0));
        var blocked = inventory(state, new RefineryState(null, null, List.of(full), null));
        for (var unavailable : List.of(blocked, state.withEmpires(List.of(new Empire("owner", "Owner", "human", "Individualist", 0, 0,
                List.of(), List.of(), Map.of(), List.of("industrial_production"), List.of()))))) {
            var result = execute(unavailable, 6000, 6000, 10);
            assertTrue(account(result).producedKg().isEmpty());
            assertTrue(result.productionEnergyKwh().isEmpty());
            assertEquals(unavailable.commercialHubs(), result.hubs());
        }
        var noPower = execute(state, 0, 0, 10);
        assertTrue(account(noPower).refineryState().tailings().isEmpty());
        assertTrue(account(noPower).producedKg().isEmpty());
        var stock = new HashMap<>(state.commercialHubs().getFirst().activeOrders()); stock.remove("purified_water");
        var hub = state.commercialHubs().getFirst();
        var missing = state.withCommercialHubs(List.of(new CommercialHub(hub.id(), hub.entityId(), 0, 1e8, 20000, 10, stock)));
        assertTrue(account(execute(missing, 6000, 6000, 10)).producedKg().isEmpty());
    }
    @Test void savedTailingsKeepTheirActualAssayAfterResearchAndReprocessingDoesNotBuyFreshOreAgain() throws Exception {
        var state = apply(world(false), execute(world(false), 6000, 6000, 10));
        var initial = account(execute(world(false), 6000, 6000, 10)).refineryState();
        assertEquals(1.5, initial.tailings().getFirst().elementsKg().get("Ag"), 1e-9);
        var manager = new SaveGameManager(directory); var file = manager.save("tailings", state, 1, GameClock.START_TIME.toString());
        state = manager.load(file.toFile()).toGameState(state.turn(), state.status());
        var saved = RefineryProcess.inventory(state, "refinery");
        var old = state.empires().getFirst(); var knowledge = new ArrayList<>(old.unlockedTechIds()); knowledge.add("rare_earth_byproduct_recovery");
        state = state.withEmpires(List.of(new Empire(old.id(), old.name(), old.raceId(), old.societyStructure(), old.treasuryCredits(),
                old.corporateTaxRate(), old.controlledSystemIds(), old.ministries(), old.systemGovernorAssignments(), knowledge, old.activeShipDesignIds())));
        state = inventory(state, saved.select(null, saved.tailings().getFirst().id()));
        double ore = state.commercialHubs().getFirst().activeOrders().get("rare_earth_fluorides").supplyKg();
        var reprocessed = execute(state, 7500, 7500, 10);
        assertEquals(ore, reprocessed.hubs().getFirst().activeOrders().get("rare_earth_fluorides").supplyKg());
        assertEquals(1.125, account(reprocessed).producedKg().get("silver"), 1e-9);
        assertEquals(.063, account(reprocessed).producedKg().get("gold"), 1e-9);
        assertConserved(account(reprocessed).refineryState().latestReceipt());
        assertEquals(0, plan(apply(state, reprocessed)).maximumBatches());
        assertEquals(2, new HashSet<>(List.of(saved.tailings().getFirst().id(), account(reprocessed).refineryState().tailings().getFirst().id())).size());
    }
    @Test void unsoldProductsAndTailingsAreDifferentStocksAndLaterSalesDoNotRepeatRecovery() throws Exception {
        var state = world(false); var hub = state.commercialHubs().getFirst(); var orders = new HashMap<>(hub.activeOrders());
        for (String id : List.of("refined_rare_earths", "silver", "gold")) orders.put(id, new MarketOrder(id, 0, 0, 10, 0));
        state = state.withCommercialHubs(List.of(new CommercialHub(hub.id(), hub.entityId(), 0, 1e8, hub.currentStoredWeightKg(), 10, orders)));
        var first = execute(state, 6000, 6000, 10); assertTrue(account(first).soldKg().isEmpty());
        assertEquals(.5, account(first).unsoldStockKg().get("silver"), 1e-9);
        state = apply(state, first);
        var market = state.commercialHubs().getFirst();
        state = state.withCommercialHubs(List.of(new CommercialHub(market.id(), market.entityId(), 0, 1e8,
                market.currentStoredWeightKg(), 10, market.activeOrders(),
                Map.of("silver", new com.spaceconquest.engine.market.HubStockTarget(1, 1, true)),
                market.stockHistory(), market.reservedTradeFuel())));
        state = state.withCommercialHubs(new MarketDemandProcessor().refresh(state, List.of()));
        var later = execute(state, 0, 0, 0);
        assertEquals(.5, account(later).soldKg().get("silver"), 1e-9);
        assertTrue(account(later).producedKg().isEmpty());
        assertEquals(account(first).refineryState(), account(later).refineryState());
    }
    @Test void combinedFundingAndGridBudgetsLimitEveryRecoveredProductAndItsResidueTogether() throws Exception {
        var state = world(false); var corp = state.corporations().getFirst();
        var poor = state.toBuilder().corporations(List.of(new Corporation(corp.id(), corp.name(), corp.empireId(), corp.headquartersEntityId(),
                corp.marketOrientation(), 25140, corp.ownedFacilityIds(), corp.ownedShipIds(), corp.claimedVeinIds()))).build();
        for (var result : List.of(execute(poor, 6000, 6000, 10), execute(state, 3000, 3000, 10))) {
            assertEquals(.25, account(result).producedKg().get("silver"), 1e-9);
            assertEquals(400, account(result).producedKg().get("refined_rare_earths"), 1e-9);
            assertEquals(3000, result.productionEnergyKwh().get("earth"), 1e-9);
            assertEquals(152.245, account(result).refineryState().storedKg(), 1e-9);
            assertConserved(account(result).refineryState().latestReceipt());
        }
        var baseline = plan(state).profile();
        assertThrows(IllegalArgumentException.class, () -> new RefineryProcess.Profile(baseline.requiredApplicationId(),
                Map.of("silver", 1.01), baseline.reagentsKg(), baseline.work()));
    }
    @Test void normalEngineTickRetainsAssayedTailingsAndAccountsForActualRefineryPower() throws Exception {
        var state = world(false); var owner = state.empires().getFirst();
        owner = new Empire(owner.id(), owner.name(), owner.raceId(), "Hive Mind", 0, 0, owner.controlledSystemIds(),
                owner.ministries(), owner.systemGovernorAssignments(), owner.unlockedTechIds(), List.of());
        var refinery = new IndustrialFacility("refinery", "earth", "rare_earth_refining", "owner", IndustrialFacility.HIVE_GRID,
                1, 10, "industrial_worker", false, 0);
        var plant = new IndustrialFacility("plant", "earth", "solar_power", "owner", IndustrialFacility.HIVE_GRID,
                1, 100, "technician", false, 0);
        var planet = new Planet("earth", "Earth", "", 5.97e24, 9.81, SolarRadiation.AU_KM, 0, 12742,
                "terrestrial", "breathable", true, 1, List.of(), List.of(), List.of(new Population("human", Map.of(25, 20000L))));
        var system = new SolarSystem("sol", "Sol", "", 0, 0, 0, SolarRadiation.SOLAR_MASS_KG, 1392700, "Yellow", List.of(planet), List.of());
        state = state.toBuilder().empires(List.of(owner)).corporations(List.of()).industrialFacilities(List.of(refinery, plant))
                .solarSystems(List.of(system)).build();
        var engine = new SpaceConquestEngine(); engine.applyGameState(state); engine.stepTurn();
        var result = engine.getGameState();
        var saved = RefineryProcess.inventory(result, "refinery");
        assertEquals(.5, saved.latestReceipt().productsKg().get("silver"), 1e-9);
        assertEquals(304.49, saved.storedKg(), 1e-9);
        assertEquals(250, result.powerGrids().getFirst().totalDemandKw(), 1e-9);
        assertEquals(.5, result.commercialHubs().getFirst().activeOrders().get("silver").supplyKg(), 1e-9);
        assertConserved(saved.latestReceipt());
    }
    private RefineryProcess.Plan plan(GameState state) {
        return RefineryProcess.plan(state, state.industrialFacilities().getFirst(), RefineryProcess.inventory(state, "refinery"));
    }
    private GameState inventory(GameState state, RefineryState inventory) {
        var accounts = new ArrayList<>(state.industryAccounts());
        var account = accounts.stream().filter(a -> a.facilityId().equals("refinery")).findFirst().orElseThrow();
        accounts.removeIf(a -> a.facilityId().equals("refinery")); accounts.add(account.withRefineryState(inventory));
        return state.toBuilder().industryAccounts(accounts).build();
    }
    private void assertConserved(RefineryState.Receipt receipt) throws Exception {
        var actual = new HashMap<>(receipt.residue().elementsKg());
        for (var product : receipt.productsKg().entrySet()) {
            var material = DataModelLoader.loadMaterials().stream().filter(m -> m.id().equals(product.getKey())).findFirst().orElseThrow();
            material.composition().forEach((element, fraction) -> actual.merge(element, product.getValue() * fraction / 100, Double::sum));
        }
        assertEquals(receipt.intake().elementsKg().keySet(), actual.keySet());
        receipt.intake().elementsKg().forEach((element, mass) -> assertEquals(mass, actual.get(element), 1e-8, element));
        assertEquals(receipt.intake().massKg(), receipt.residue().massKg() + receipt.productsKg().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-8);
    }
    private IndustryMarketProcessor.TurnResult execute(GameState state, double energy, double priced, int workers) {
        return new IndustryMarketProcessor().process(state, Map.of("refinery", workers), Map.of(), Map.of("refinery", workers),
                Map.of("refinery", new ProductionWork.EnergyBudget(energy, priced)));
    }
    private IndustryAccount account(IndustryMarketProcessor.TurnResult result) {
        return result.industryAccounts().stream().filter(a -> a.facilityId().equals("refinery")).findFirst().orElseThrow();
    }
    private GameState apply(GameState state, IndustryMarketProcessor.TurnResult result) {
        return state.toBuilder().empires(result.empires()).corporations(result.corporations()).industryAccounts(result.industryAccounts())
                .commercialHubs(result.hubs()).marketAccounts(result.marketAccounts()).build();
    }
    private double money(GameState state) {
        return state.corporations().getFirst().liquidCapitalReserves() + state.marketAccounts().getFirst().unsettledSalesCredits()
                + state.industryAccounts().stream().mapToDouble(IndustryAccount::operatingCashCredits).sum();
    }
    private double money(IndustryMarketProcessor.TurnResult result) {
        return result.corporations().getFirst().liquidCapitalReserves() + result.marketAccounts().getFirst().unsettledSalesCredits()
                + result.industryAccounts().stream().mapToDouble(a -> a.operatingCashCredits() + a.maintenanceCostsCredits()).sum();
    }
    private GameState world(boolean improved) {
        var knowledge = new ArrayList<>(List.of("electricity", "industrial_production", "rare_earth_refining", "solar_power"));
        if (improved) knowledge.add("rare_earth_byproduct_recovery");
        var orders = new HashMap<String, MarketOrder>();
        for (var id : List.of("rare_earth_fluorides", "purified_water", "carbon"))
            orders.put(id, new MarketOrder(id, 10000, 1e6, MarketProcessor.basePricePerKg(id), 0));
        for (var id : List.of("refined_rare_earths", "silver", "gold")) orders.put(id, new MarketOrder(id, 0, 1e6, MarketProcessor.basePricePerKg(id), 0));
        return GameState.builder().empires(List.of(new Empire("owner", "Owner", "human", "Individualist", 1e6, 0,
                        List.of("sol"), List.of(), Map.of(), knowledge, List.of())))
                .corporations(List.of(new Corporation("corp", "Corp", "owner", "earth", "INDUSTRY", 1e6, List.of("refinery"), List.of(), List.of())))
                .industrialFacilities(List.of(new IndustrialFacility("refinery", "earth", "rare_earth_refining", "corp", IndustrialFacility.PRIVATE_CORPORATE,
                        1, 10, "industrial_worker", false, 0), new IndustrialFacility("plant", "earth", "solar_power", "owner", IndustrialFacility.PUBLIC_STATE, 1, 100, "technician", false, 0)))
                .industryAccounts(List.of(IndustryAccount.empty("refinery"), new IndustryAccount("plant", Map.of(), Map.of(), Map.of(),
                        0, 0, 0, 0, 1e6, 0).withOperatingCash(10000)))
                .commercialHubs(List.of(new CommercialHub("hub", "earth", 0, 1e8, 30000, 10, orders)))
                .marketAccounts(List.of(new MarketAccount("hub", 1e7))).build();
    }
}
