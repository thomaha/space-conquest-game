package com.spaceconquest.engine;

import com.spaceconquest.engine.market.CorporateFleetProcessor;
import com.spaceconquest.engine.market.CorporateInvestmentProcessor;
import com.spaceconquest.engine.market.MarketProcessor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class CorporateProcessorTest {

    private final MarketProcessor marketProcessor = new MarketProcessor();
    private final CorporateInvestmentProcessor investmentProcessor = new CorporateInvestmentProcessor();
    private final CorporateFleetProcessor fleetProcessor = new CorporateFleetProcessor(marketProcessor);

    @Test
    public void testCorporateInvestmentsTriggeredByShortcoming() {
        // Create hub with severe deficit in silicon (high shortcoming)
        MarketOrder siliconOrder = new MarketOrder("refined_silicon", 5.0, 200.0, 15.0, 0.85);
        CommercialHub hub = new CommercialHub(
                "hub_earth",
                "earth",
                0.05,
                100000.0,
                5000.0,
                10.0,
                Map.of("refined_silicon", siliconOrder)
        );

        Corporation metallurgyCorp = new Corporation(
                "corp_sol_refining",
                "Sol Metallurgy & Refining",
                "terran",
                "earth",
                "METALLURGY",
                30000.0,
                List.of(),
                List.of(),
                List.of()
        );

        Corporation updated = investmentProcessor.evaluateAndInvest(metallurgyCorp, List.of(hub));
        assertNotNull(updated);
        assertTrue(updated.liquidCapitalReserves() < 30000.0, "Capital should be deducted for investments");
        assertFalse(updated.ownedFacilityIds().isEmpty(), "Infrastructure should be expanded");
        assertFalse(updated.ownedShipIds().isEmpty(), "Ship should be procured");
    }

    @Test
    public void testDepletedCapitalCorporationIdlesSafely() {
        MarketOrder siliconOrder = new MarketOrder("refined_silicon", 5.0, 200.0, 15.0, 0.85);
        CommercialHub hub = new CommercialHub(
                "hub_earth",
                "earth",
                0.05,
                100000.0,
                5000.0,
                10.0,
                Map.of("refined_silicon", siliconOrder)
        );

        Corporation brokeCorp = new Corporation(
                "corp_broke",
                "Bankrupt Holdings",
                "terran",
                "earth",
                "METALLURGY",
                100.0, // very low capital
                List.of(),
                List.of(),
                List.of()
        );

        Corporation updated = investmentProcessor.evaluateAndInvest(brokeCorp, List.of(hub));
        assertNotNull(updated);
        assertEquals(100.0, updated.liquidCapitalReserves(), "Capital should remain untouched");
        assertTrue(updated.ownedFacilityIds().isEmpty());
        assertTrue(updated.ownedShipIds().isEmpty());
    }

    @Test
    public void testTransportFleetArbitrageTradeExecution() {
        // Planet A: Surplus (high supply 10000 kg, low price 5 credits/kg)
        MarketOrder surplusOrder = new MarketOrder("refined_silicon", 10000.0, 100.0, 5.0, 0.0);
        CommercialHub srcHub = new CommercialHub(
                "hub_source",
                "luna", // low gravity moon
                0.02,
                50000.0,
                10000.0,
                20.0,
                Map.of("refined_silicon", surplusOrder)
        );

        // Planet B: Acute Shortage (low supply 0 kg, high demand 5000 kg, high price 50 credits/kg)
        MarketOrder deficitOrder = new MarketOrder("refined_silicon", 0.0, 5000.0, 50.0, 0.95);
        CommercialHub destHub = new CommercialHub(
                "hub_dest",
                "mars",
                0.05,
                50000.0,
                0.0,
                20.0,
                Map.of("refined_silicon", deficitOrder)
        );

        Corporation transportCorp = new Corporation(
                "corp_transport",
                "Interstellar Freight",
                "terran",
                "luna",
                "TRANSPORT",
                10000.0,
                List.of(),
                List.of("cargo_transport_01"),
                List.of()
        );

        Map<String, Double> gravityMap = Map.of("luna", 0.16, "mars", 0.38);
        Map<String, Double> atmosphereMap = Map.of("luna", 0.0, "mars", 0.01);

        CorporateFleetProcessor.CorporateFleetResult result = fleetProcessor.processFleetOperations(
                List.of(transportCorp),
                List.of(srcHub, destHub),
                gravityMap,
                atmosphereMap
        );

        Corporation updatedCorp = result.corporations().getFirst();
        assertTrue(updatedCorp.liquidCapitalReserves() > 10000.0, "Corporation should have earned arbitrage net profit");

        CommercialHub updatedSrc = result.commercialHubs().stream().filter(h -> h.id().equals("hub_source")).findFirst().orElseThrow();
        CommercialHub updatedDest = result.commercialHubs().stream().filter(h -> h.id().equals("hub_dest")).findFirst().orElseThrow();

        assertEquals(9000.0, updatedSrc.activeOrders().get("refined_silicon").supplyKg(), 0.1);
        assertEquals(1000.0, updatedDest.activeOrders().get("refined_silicon").supplyKg(), 0.1);
    }

    @Test
    public void testExtremeGravityTaxRejectsUnprofitableTrade() {
        // High surface gravity and thick atmosphere creating prohibitive launch cost
        MarketOrder surplusOrder = new MarketOrder("iron_ore", 5000.0, 100.0, 10.0, 0.0);
        CommercialHub hyperGravHub = new CommercialHub(
                "hub_hyper",
                "heavy_world",
                0.10,
                50000.0,
                5000.0,
                10.0,
                Map.of("iron_ore", surplusOrder)
        );

        MarketOrder deficitOrder = new MarketOrder("iron_ore", 10.0, 500.0, 15.0, 0.50);
        CommercialHub destHub = new CommercialHub(
                "hub_orbital",
                "orbital_station",
                0.02,
                50000.0,
                100.0,
                10.0,
                Map.of("iron_ore", deficitOrder)
        );

        Corporation transportCorp = new Corporation(
                "corp_transport",
                "Freight Corp",
                "terran",
                "heavy_world",
                "TRANSPORT",
                5000.0,
                List.of(),
                List.of("cargo_transport_01"),
                List.of()
        );

        // Hyper-gravity world: 5.0 G, 10.0 atm atmosphere -> massive launch tax
        Map<String, Double> gravityMap = Map.of("heavy_world", 5.0, "orbital_station", 0.0);
        Map<String, Double> atmosphereMap = Map.of("heavy_world", 10.0, "orbital_station", 0.0);

        CorporateFleetProcessor.CorporateFleetResult result = fleetProcessor.processFleetOperations(
                List.of(transportCorp),
                List.of(hyperGravHub, destHub),
                gravityMap,
                atmosphereMap
        );

        Corporation updatedCorp = result.corporations().getFirst();
        assertEquals(5000.0, updatedCorp.liquidCapitalReserves(), 0.001,
                "Corporation should reject unprofitable trade due to launch tax and keep capital intact");
    }

    @Test
    public void testMiningFleetHarvestRevenue() {
        Corporation miningCorp = new Corporation(
                "corp_mining",
                "Belt Mining Corp",
                "terran",
                "ceres",
                "EXTRACTION",
                8000.0,
                List.of(),
                List.of("mine_ship_01", "mine_ship_02"),
                List.of("vein_ceres_01")
        );

        CorporateFleetProcessor.CorporateFleetResult result = fleetProcessor.processFleetOperations(
                List.of(miningCorp),
                List.of(),
                Map.of(),
                Map.of()
        );

        Corporation updatedCorp = result.corporations().getFirst();
        assertEquals(8000.0 + (2 * 500.0), updatedCorp.liquidCapitalReserves(), 0.001);
    }
}
