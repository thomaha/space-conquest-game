package com.spaceconquest.engine.economy;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.market.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SupplyContractLifecycleTest {
    @TempDir Path directory;
    static GameState contractHarvest(GameState state) {
        var hub = state.commercialHubs().getFirst(); var forecast = GridSupplyPricing.forecast(state);
        var offers = FacilitySupplyPricing.offers(state, hub, forecast.tariffs().getOrDefault("site", .02), forecast.productionEnergyKwh(), "producer");
        assertFalse(offers.isEmpty());
        for (var offer : offers) {
            long days = Math.max(1, (long) Math.ceil(offer.deliveryDays()));
            var next = SupplyProcurement.accept(state, "harvest-" + state.turn() + "-" + offer.materialId(), "producer", "hub",
                    offer.materialId(), offer.quantityKg() * days, offer.retailPricePerKg() * .8 * 1.1, state.turn() + days + 7);
            assertNotSame(state, next); state = next;
        }
        return state;
    }
    @Test void aSavedFundedContractPaysOnlyForAnActualPoweredHarvest() throws Exception {
        var initial = BusinessTestWorld.create("hydroponic_growth_arrays"); var state = contractHarvest(initial);
        assertEquals(initial.commercialHubs(), state.commercialHubs()); assertEquals(initial.corporations(), state.corporations());
        assertEquals(1e8, state.marketAccounts().getFirst().unsettledSalesCredits() + state.businessLedger().escrowCredits(), 1e-6);
        var engine = new SpaceConquestEngine(42); engine.applyGameState(state);
        for (int day = 0; day < 9; day++) engine.stepTurn();
        var growing = engine.getGameState();
        assertTrue(growing.businessLedger().contracts().stream().allMatch(c -> c.deliveredKg() == 0));
        assertTrue(growing.industryAccounts().stream().filter(a -> a.facilityId().equals("producer")).findFirst().orElseThrow().farmingState().cultivatedBatches() > 0);
        var saves = new SaveGameManager(directory); var file = directory.resolve("growing.scsave").toFile();
        saves.save(file, growing, 1, GameClock.START_TIME.toString());
        var resumed = new SpaceConquestEngine(42); resumed.applyGameState(saves.load(file).toGameState(growing.turn(), growing.status()));
        double produced = 0;
        for (int day = 9; day < 35; day++) {
            engine.stepTurn(); resumed.stepTurn();
            produced += resumed.getGameState().industryAccounts().stream().filter(a -> a.facilityId().equals("producer"))
                    .mapToDouble(a -> a.producedKg().getOrDefault("food_matrix", 0.0)).sum();
        }
        var end = resumed.getGameState();
        assertTrue(produced > 0);
        var originalOrders = end.businessLedger().contracts().stream().filter(c -> c.id().startsWith("harvest-0-")).toList();
        assertEquals(state.businessLedger().contracts().size(), originalOrders.size());
        assertTrue(originalOrders.stream().allMatch(c -> SupplyContract.FULFILLED.equals(c.status())), originalOrders.toString());
        assertEquals(0, originalOrders.stream().mapToDouble(SupplyContract::escrowCredits).sum());
        assertEquals(engine.getGameState().businessLedger(), end.businessLedger());
        assertEquals(engine.getGameState().corporations(), end.corporations());
        assertTrue(end.businessLedger().serviceClearingCredits() > 0);
    }
    @Test void expiryRefundsOnlyUnspentEscrowAndAnUnfundedRequestCreatesNothing() throws Exception {
        var initial = BusinessTestWorld.create("hydroponic_growth_arrays"); var state = contractHarvest(initial);
        var expired = SupplyProcurement.prepare(state.withTurn(100));
        assertEquals(1e8, expired.marketAccounts().getFirst().unsettledSalesCredits() + expired.businessLedger().escrowCredits(), 1e-6);
        assertEquals(initial.commercialHubs(), expired.commercialHubs()); assertEquals(initial.corporations(), expired.corporations());
        assertTrue(expired.businessLedger().contracts().stream().filter(c -> c.id().startsWith("harvest-0-"))
                .allMatch(c -> SupplyContract.EXPIRED.equals(c.status())));
        var poor = initial.toBuilder().marketAccounts(List.of(new MarketAccount("hub", 0))).build();
        assertSame(poor, SupplyProcurement.accept(poor, "unfunded", "producer", "hub", "food_matrix", 10, 1000, 40));
    }
    @Test void anUnprofitablePrivateFacilityPausesAndAFundedContractCanRestartIt() throws Exception {
        var initial = BusinessTestWorld.create("hydroponic_growth_arrays");
        var paused = BusinessOperatingPlan.prepare(initial);
        assertFalse(paused.businessLedger().operatingDecisions().get("producer").operate());
        var restart = BusinessOperatingPlan.prepare(contractHarvest(paused));
        assertTrue(restart.businessLedger().operatingDecisions().get("producer").operate());
        assertEquals(initial.industrialFacilities(), restart.industrialFacilities());
    }
    @Test void automaticLongLeadOrdersUseOnlyAffordableBuyerCashBeforeStocksRunOut() throws Exception {
        var state = BusinessTestWorld.create("hydroponic_growth_arrays");
        state = state.toBuilder().marketAccounts(List.of(new MarketAccount("hub", 10000))).build();
        var next = SupplyProcurement.prepare(state);
        assertTrue(next.businessLedger().escrowCredits() > 0);
        assertEquals(10000, next.businessLedger().escrowCredits()
                + next.marketAccounts().getFirst().unsettledSalesCredits(), 1e-8);
        assertEquals(state.commercialHubs(), next.commercialHubs());
        assertEquals(state.corporations(), next.corporations());
        assertTrue(BusinessOperatingPlan.prepare(next).businessLedger().operatingDecisions().get("producer").operate());
    }
    @Test void acceptedObligationsContinueWhenInputPricesRiseAfterAgreement() throws Exception {
        var state = contractHarvest(BusinessTestWorld.create("hydroponic_growth_arrays"));
        var hub = state.commercialHubs().getFirst(); var orders = new HashMap<>(hub.activeOrders()); var old = orders.get("carbon_dioxide_gas");
        orders.put(old.resourceId(), new MarketOrder(old.resourceId(), old.supplyKg(), old.demandKg(), old.pricePerKg() * 1000, 0));
        var changed = state.withCommercialHubs(List.of(hub.withInventory(0, hub.currentStoredWeightKg(), orders)));
        var plan = BusinessOperatingPlan.prepare(changed).businessLedger().operatingDecisions().get("producer");
        assertTrue(plan.operate()); assertTrue(plan.expectedReceipts() < plan.requiredReceipts());
        assertTrue(plan.reason().startsWith("Fulfilling funded contract"));
    }
    @Test void aFundedByproductBuyerCanSupportRealMixedOutputWithoutASpeculativePrimaryBuyer() throws Exception {
        var state = BusinessTestWorld.create("hydroponic_growth_arrays"); var original = state.industrialFacilities().getFirst();
        var smelter = new IndustrialFacility(original.id(), original.planetId(), "pyro_iron_smelting", original.ownerEntityId(), original.ownershipType(),
                1, 100, "industrial_worker", false, 0);
        var facilities = new ArrayList<>(state.industrialFacilities()); facilities.set(0, smelter);
        var hub = state.commercialHubs().getFirst(); var orders = new HashMap<>(hub.activeOrders());
        orders.put("iron_ore", new MarketOrder("iron_ore", 1e6, 0, 2, 0)); orders.put("carbon", new MarketOrder("carbon", 1e6, 0, 2, 0));
        state = state.toBuilder().industrialFacilities(facilities).commercialHubs(List.of(hub.withInventory(0,
                orders.values().stream().mapToDouble(MarketOrder::supplyKg).sum(), orders))).build();
        var withConsumer = new ArrayList<>(facilities);
        withConsumer.add(new IndustrialFacility("consumer", original.planetId(), original.applicationId(), original.ownerEntityId(), original.ownershipType(),
                original.tier(), original.allocatedWorkers(), original.workerProfessionId(), false, 0, original.productionEquipment()));
        var depletedOrders = new HashMap<>(orders); var existingGas = depletedOrders.get("carbon_dioxide_gas");
        depletedOrders.put("carbon_dioxide_gas", new MarketOrder("carbon_dioxide_gas", 0, existingGas.demandKg(), existingGas.pricePerKg(), 0));
        var autoStart = state.withIndustrialFacilities(withConsumer).withCommercialHubs(List.of(hub.withInventory(0,
                depletedOrders.values().stream().mapToDouble(MarketOrder::supplyKg).sum(), depletedOrders)));
        var automatic = SupplyProcurement.prepare(autoStart);
        assertTrue(automatic.businessLedger().contracts().stream().anyMatch(c -> c.facilityId().equals("producer")
                && c.materialId().equals("carbon_dioxide_gas") && c.escrowCredits() > 0));
        assertEquals(autoStart.commercialHubs(), automatic.commercialHubs());
        var contracted = SupplyProcurement.accept(state, "byproduct", "producer", "hub", "carbon_dioxide_gas", 10, 100000, 20);
        assertNotSame(state, contracted);
        var engine = new SpaceConquestEngine(42); engine.applyGameState(contracted); engine.stepTurn();
        var end = engine.getGameState(); var receipt = end.industryAccounts().stream().filter(a -> a.facilityId().equals("producer")).findFirst().orElseThrow();
        assertTrue(receipt.producedKg().getOrDefault("carbon_dioxide_gas", 0.0) >= 10);
        assertEquals(10, receipt.soldKg().get("carbon_dioxide_gas"), 1e-8);
        double iron = receipt.producedKg().getOrDefault("refined_iron", 0.0);
        assertTrue(iron > 0);
        assertEquals(iron, receipt.unsoldStockKg().getOrDefault("refined_iron", 0.0)
                + receipt.soldKg().getOrDefault("refined_iron", 0.0), 1e-8);
        assertEquals(SupplyContract.FULFILLED, end.businessLedger().contracts().getFirst().status());
    }
}
