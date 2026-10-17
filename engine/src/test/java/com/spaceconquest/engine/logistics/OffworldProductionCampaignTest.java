package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.*;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.ship.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** One finite produced cargo passes through metered production, paid lift and an actual offworld sale. */
class OffworldProductionCampaignTest {
    @TempDir Path directory;
    private GameState world() throws Exception {
        var state = new FoodImportCostAuditTest().world(new FoodImportCostAuditTest.Scenario("mod_fission_thruster", "moon"));
        var owner = state.empires().getFirst(); var known = new ArrayList<String>();
        DataModelLoader.loadTechnologies().forEach(t -> { known.add(t.id()); t.applications().forEach(a -> known.add(a.id())); });
        owner = new Empire(owner.id(), owner.name(), owner.raceId(), owner.societyStructure(), owner.treasuryCredits(), .25,
                owner.controlledSystemIds(), owner.ministries(), owner.systemGovernorAssignments(), known, owner.activeShipDesignIds());
        var factory = new IndustrialFacility("factory", "earth", "surface_water_treatment", "owner", IndustrialFacility.PUBLIC_STATE, 1, 10, "technician", false, 0);
        var terminal = new IndustrialFacility("terminal", "earth", "cargo_terminal", "launch-firm", IndustrialFacility.PRIVATE_CORPORATE, 1, 50, "technician", false, 0);
        var power = new IndustrialFacility("power", "earth", "solar_power", "owner", IndustrialFacility.PUBLIC_STATE, 1, 100, "technician", false, 0);
        var orders = new HashMap<String, MarketOrder>();
        orders.put("purified_water", new MarketOrder("purified_water", 0, 1000, 10, 1));
        orders.put("rp1_kerosene", new MarketOrder("rp1_kerosene", 1e7, 0, 4, 0));
        orders.put("liquid_oxygen", new MarketOrder("liquid_oxygen", 1e7, 0, 4, 0));
        var ground = new CommercialHub("ground", "earth", 0, 1e9, 2e7, 10, orders);
        var target = state.commercialHubs().getLast(); var remoteOrders = new HashMap<>(target.activeOrders());
        remoteOrders.put("purified_water", new MarketOrder("purified_water", 0, 100, 8, 1));
        return state.toBuilder().empires(List.of(owner)).corporations(List.of(new Corporation("launch-firm", "Launch firm", "owner", "earth", "TRANSPORT", 1e6, List.of("terminal"), List.of(), List.of())))
                .industrialFacilities(List.of(factory, terminal, power))
                .industryAccounts(List.of(IndustryAccount.empty("factory").withOperatingCash(1000000), IndustryAccount.empty("terminal").withOperatingCash(1000000),
                        IndustryAccount.empty("power").withOperatingCash(1000000)))
                .commercialHubs(List.of(state.commercialHubs().getFirst(), target.withInventory(0, target.currentStoredWeightKg(), remoteOrders), ground))
                .marketAccounts(List.of(new MarketAccount("source", 1e7), new MarketAccount("target", 1e7), new MarketAccount("ground", 1e7)))
                .tradeRoutes(List.of(new TradeRoute("route", "Produced water", "owner", "ground", "target", "purified_water", 100, 0, 100, List.of("ship"), 0, false))).build();
    }
    private GameState produce(GameState state) throws Exception {
        var wages = new HouseholdEconomyProcessor().process(state, DataModelLoader.loadRaces());
        state = state.toBuilder().empires(wages.empires()).corporations(wages.corporations()).householdAccounts(wages.householdAccounts())
                .commercialHubs(wages.commercialHubs()).marketAccounts(wages.marketAccounts()).industryAccounts(wages.industryAccounts()).build();
        var generation = new PowerGenerationProcessor().process(state, wages.paidWorkersByFacility(), wages.wagesByFacility());
        state = state.toBuilder().industryAccounts(generation.industryAccounts()).commercialHubs(generation.hubs()).build();
        var balance = new PowerProcessor().balanceDay(state, generation.generationKw(), wages.paidWorkersByFacility());
        var billed = new PowerBillingProcessor().process(state, balance, generation.accounts(), wages.paidWorkersByFacility());
        state = state.toBuilder().industryAccounts(billed.industryAccounts()).householdAccounts(billed.households()).build();
        var result = new IndustryMarketProcessor().process(state, billed.poweredWorkers(), wages.wagesByFacility(), wages.paidWorkersByFacility(), billed.productionEnergy());
        return state.toBuilder().empires(result.empires()).corporations(result.corporations()).industryAccounts(result.industryAccounts())
                .commercialHubs(result.hubs()).marketAccounts(result.marketAccounts()).build();
    }
    private GameState advance(GameState state) {
        return state.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state), state.orbitalStations(), List.of())).withTurn(state.turn() + 1);
    }
    @Test void producedWaterPaysForLaunchCrossesReloadInTransitAndSellsToActualRemoteBuyer() throws Exception {
        var state = produce(world());
        var receipt = state.industryAccounts().stream().filter(a -> a.facilityId().equals("factory")).findFirst().orElseThrow();
        assertTrue(receipt.producedKg().getOrDefault("purified_water", 0.0) >= 100);
        assertTrue(receipt.salesCredits() > 0); assertTrue(receipt.wageCostsCredits() > 0); assertTrue(receipt.powerCostsCredits() > 0);
        var fleet = state.fleets().getFirst(); var port = state.commercialHubs().getFirst();
        double treasury = state.empires().getFirst().treasuryCredits();
        state = TradeLegReadiness.refillAtPort(state, fleet, port);
        assertTrue(state.empires().getFirst().treasuryCredits() < treasury);
        fleet = state.fleets().getFirst(); var orbit = FleetLocation.Site.orbit("earth", 4000);
        var undock = LocalTravel.plan(state, fleet, orbit); assertNotNull(undock);
        state = state.withFleets(List.of(LocalTravel.depart(fleet, orbit, undock)));
        for (int day = 0; day < 100 && state.fleets().getFirst().location().inTransit(); day++) state = advance(state);
        assertTrue(state.fleets().getFirst().location().isAt(orbit));
        var ground = state.commercialHubs().stream().filter(h -> h.id().equals("ground")).findFirst().orElseThrow();
        double beforeStock = ground.activeOrders().get("purified_water").supplyKg();
        double beforeFuel = ground.activeOrders().get("liquid_oxygen").supplyKg();
        var logistics = new LogisticsProcessor(); var preview = logistics.previewBasket(state, state.tradeRoutes().getFirst(), Map.of("purified_water", 100.0));
        assertNotNull(preview, TradeLegReadiness.inspect(state, state.fleets().getFirst(), state.commercialHubs().get(1)).explanation());
        state = preview.state().withTradeRoutes(List.of(preview.route()));
        assertTrue(state.launchUsageKg().getOrDefault("terminal", 0.0) >= 100);
        assertTrue(state.launchActivities().stream().anyMatch(a -> a.providerId().equals("terminal") && a.feeCredits() > 0));
        ground = state.commercialHubs().stream().filter(h -> h.id().equals("ground")).findFirst().orElseThrow();
        assertEquals(beforeStock - 100, ground.activeOrders().get("purified_water").supplyKg(), 1e-6);
        assertTrue(ground.activeOrders().get("liquid_oxygen").supplyKg() < beforeFuel);
        assertTrue(state.fleets().getFirst().location().inTransit());
        var saves = new SaveGameManager(directory);
        var restored = saves.load(saves.save("offworld", state, 1, GameClock.START_TIME.toString()).toFile()).toGameState(state.turn(), state.status());
        assertEquals(state.fleets(), restored.fleets()); assertEquals(state.tradeRoutes(), restored.tradeRoutes());
        var target = state.commercialHubs().get(1); int days = 0;
        while (!FleetPositioning.atHub(state, state.fleets().getFirst(), target) && days++ < 12000) {
            state = advance(state); restored = advance(restored);
        }
        assertTrue(days < 12000); assertEquals(state.fleets(), restored.fleets());
        double buyerCash = state.marketAccounts().stream().filter(a -> a.hubId().equals("target")).findFirst().orElseThrow().unsettledSalesCredits();
        var unfunded = restored.toBuilder().marketAccounts(restored.marketAccounts().stream().map(a -> a.hubId().equals("target") ? new MarketAccount("target", 0) : a).toList()).build();
        assertNull(logistics.fundedDelivery(unfunded, unfunded.tradeRoutes().getFirst()));
        var settlement = logistics.processTradeRoutes(restored); var delivered = settlement.state();
        assertEquals(100, settlement.deliveredKg(), 1e-6);
        assertEquals(100, delivered.commercialHubs().get(1).activeOrders().get("purified_water").supplyKg(), 1e-6);
        var remoteAfter = delivered.commercialHubs().get(1);
        double fuelPurchases = target.activeOrders().entrySet().stream().filter(e -> !e.getKey().equals("purified_water"))
                .mapToDouble(e -> Math.max(0, e.getValue().supplyKg() - remoteAfter.activeOrders().get(e.getKey()).supplyKg()) * e.getValue().pricePerKg()).sum();
        assertEquals(buyerCash - 100 * 8 * IndustryMarketProcessor.WHOLESALE_SHARE + fuelPurchases,
                delivered.marketAccounts().stream().filter(a -> a.hubId().equals("target")).findFirst().orElseThrow().unsettledSalesCredits(), 1e-6);
        assertEquals(0, delivered.fleets().getFirst().ships().getFirst().storedCargoKg().getOrDefault("purified_water", 0.0));
        assertEquals(beforeStock, delivered.commercialHubs().stream().mapToDouble(h -> h.activeOrders().containsKey("purified_water")
                ? h.activeOrders().get("purified_water").supplyKg() : 0).sum(), 1e-6);
    }
}
