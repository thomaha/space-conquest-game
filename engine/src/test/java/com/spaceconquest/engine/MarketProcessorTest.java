package com.spaceconquest.engine;

import com.spaceconquest.engine.market.MarketProcessor;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class MarketProcessorTest {

    private final MarketProcessor marketProcessor = new MarketProcessor();

    @Test
    public void testSpotPriceDiscovery() {
        // Balanced supply and demand
        double priceBalanced = marketProcessor.calculateSpotPrice(10.0, 100.0, 100.0);
        assertEquals(10.0, priceBalanced, 0.05);

        // Acute shortage (high demand, low supply) -> price goes up
        double priceShortage = marketProcessor.calculateSpotPrice(10.0, 10.0, 100.0);
        assertTrue(priceShortage > 10.0, "Shortage price should be greater than base price");

        // Surplus (high supply, low demand) -> price goes down
        double priceSurplus = marketProcessor.calculateSpotPrice(10.0, 200.0, 10.0);
        assertTrue(priceSurplus < 10.0, "Surplus price should be lower than base price");
    }

    @Test
    public void testShortcomingScore() {
        // No shortage when supply >= demand
        double zeroShortcoming = marketProcessor.calculateShortcomingScore(100.0, 50.0, 1.0);
        assertEquals(0.0, zeroShortcoming);

        // Shortage when demand > supply
        double acuteShortcoming = marketProcessor.calculateShortcomingScore(10.0, 100.0, 1.5);
        assertTrue(acuteShortcoming > 0.0);
        assertEquals(((100.0 - 10.0) / (10.0 + 0.001)) * 1.5, acuteShortcoming, 0.01);
    }

    @Test
    public void testPlanetaryGravityLaunchTax() {
        // Zero G and vacuum (Moon/Orbital Station)
        double zeroGLift = marketProcessor.calculateGravityLaunchTax(5000.0, 1000.0, 0.0, 0.0, null);
        assertEquals(0.0, zeroGLift);

        // Terrestrial Earth launch (gravity: 1.0 G, atmosphere: 1.0 atm)
        double earthLaunch = marketProcessor.calculateGravityLaunchTax(5000.0, 1000.0, 1.0, 1.0, null);
        assertEquals((5000.0 + 1000.0) * 1.0 * (1.0 + 1.0), earthLaunch);
        assertEquals(12000.0, earthLaunch);

        // Hyper-gravity world (gravity: 2.5 G, atmosphere: 3.0 atm)
        double hyperGravityLaunch = marketProcessor.calculateGravityLaunchTax(5000.0, 1000.0, 2.5, 3.0, null);
        assertEquals((6000.0) * 2.5 * 4.0, hyperGravityLaunch);
        assertEquals(60000.0, hyperGravityLaunch);
    }

    @Test
    public void testTariffSkimming() {
        double tariff = marketProcessor.calculateTariff(10000.0, 0.15);
        assertEquals(1500.0, tariff, 0.001);

        double zeroTariff = marketProcessor.calculateTariff(10000.0, 0.0);
        assertEquals(0.0, zeroTariff);
    }

    @Test
    public void testUpdateCommercialHub() {
        MarketOrder initialOrder = new MarketOrder("refined_silicon", 20.0, 150.0, 10.0, 0.0);
        CommercialHub hub = new CommercialHub(
                "hub_earth",
                "earth",
                0.10,
                50000.0,
                5000.0,
                15.0,
                Map.of("refined_silicon", initialOrder)
        );

        CommercialHub updated = marketProcessor.updateHub(hub);
        assertNotNull(updated);
        MarketOrder updatedOrder = updated.activeOrders().get("refined_silicon");
        assertNotNull(updatedOrder);
        assertTrue(updatedOrder.pricePerKg() > 10.0);
        assertTrue(updatedOrder.shortcomingScore() > 0.0);
    }
}
