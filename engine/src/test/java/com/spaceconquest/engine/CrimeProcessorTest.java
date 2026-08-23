package com.spaceconquest.engine;

import com.spaceconquest.engine.market.CrimeProcessor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class CrimeProcessorTest {

    private final CrimeProcessor crimeProcessor = new CrimeProcessor();

    @Test
    public void testStandardCrimeCalculationAndSuppression() {
        CommercialHub hub = new CommercialHub(
                "hub_earth",
                "earth",
                0.10,
                50000.0,
                40000.0,
                10.0,
                Map.of(
                        "silicon", new MarketOrder("silicon", 500.0, 500.0, 10.0, 0.0),
                        "iron", new MarketOrder("iron", 1000.0, 1000.0, 8.0, 0.0),
                        "food", new MarketOrder("food", 2000.0, 2000.0, 5.0, 0.0)
                )
        );

        // 1. Unpoliced without governor
        double unpolicedCrime = crimeProcessor.calculateCrimeMetric(hub, false, 0.0, 0.0);
        assertTrue(unpolicedCrime > 0.30, "Unpoliced hub with high density should have positive crime metric");

        // 2. Stationing police reduces crime
        double policedCrime = crimeProcessor.calculateCrimeMetric(hub, false, 0.25, 0.0);
        assertTrue(policedCrime < unpolicedCrime);

        // 3. Appointing system governor further reduces crime
        double governorSuppressedCrime = crimeProcessor.calculateCrimeMetric(hub, false, 0.25, 0.20);
        assertTrue(governorSuppressedCrime < policedCrime);
    }

    @Test
    public void testHiveMindTotalCrimeImmunity() {
        CommercialHub denseHub = new CommercialHub(
                "hub_hive",
                "hive_world",
                0.15,
                100000.0,
                90000.0,
                10.0,
                Map.of("silicon", new MarketOrder("silicon", 5000.0, 5000.0, 10.0, 0.0))
        );

        // Hive mind society structure -> strictly 0 crime
        double hiveCrime = crimeProcessor.calculateCrimeMetric(denseHub, true, 0.0, 0.0);
        assertEquals(0.0, hiveCrime, "Hive minds must be 100% immune to crime");

        double leakage = crimeProcessor.calculateBlackMarketLeakage(50000.0, 0.15, hiveCrime, 0.0);
        assertEquals(0.0, leakage, "Hive minds must generate zero black market leakage");
    }

    @Test
    public void testBlackMarketLeakageAndPirateShipConstruction() {
        MarketOrder order = new MarketOrder("gold", 1000.0, 1000.0, 100.0, 0.0); // 100,000 credits gross value
        CommercialHub hub = new CommercialHub(
                "hub_unpoliced",
                "earth",
                0.20, // 20% tariff rate -> 20,000 potential tariff
                100000.0,
                80000.0,
                10.0,
                Map.of("gold", order)
        );

        Empire terran = new Empire(
                "terran",
                "Terran Confederation",
                "human",
                "Individualist",
                50000.0,
                0.15,
                List.of("sol"),
                List.of(),
                Map.of(),
                List.of(),
                List.of()
        );

        GameState state = new GameState(
                1,
                "RUNNING",
                List.of(),
                List.of(terran),
                List.of(),
                List.of(hub),
                List.of(),
                List.of(),
                List.of()
        );

        CrimeProcessor.CrimeResult result = crimeProcessor.processCrime(state);
        assertTrue(result.totalLeakedCredits() > 0.0, "Black market leakage should siphon credits from unpoliced hub");
        assertFalse(result.shadowSyndicates().isEmpty(), "Shadow syndicate should be spawned");

        ShadowSyndicate syndicate = result.shadowSyndicates().getFirst();
        assertEquals("terran", syndicate.empireId());
        // Since gross value was 100,000, leakage easily crosses 5000 credits threshold, triggering pirate raider build
        assertFalse(syndicate.rogueShipIds().isEmpty(), "Syndicate should have autonomously built rogue raider ship");
    }
}
