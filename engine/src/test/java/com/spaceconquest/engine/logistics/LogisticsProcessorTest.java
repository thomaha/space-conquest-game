package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.MarketOrder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class LogisticsProcessorTest {

    @Test
    public void testAutomatedTradeRouteMaterialTransferAndTariff() {
        LogisticsProcessor processor = new LogisticsProcessor();

        CommercialHub originHub = new CommercialHub(
                "hub_earth", "earth", 0.05, 500000.0, 6000.0, 15.0,
                Map.of(
                        "refined_iron", new MarketOrder("refined_iron", 5000.0, 2000.0, 10.0, 0.0),
                        "consumer_goods", new MarketOrder("consumer_goods", 1000.0, 500.0, 25.0, 0.0)
                )
        );

        CommercialHub destHub = new CommercialHub(
                "hub_mars", "mars", 0.05, 500000.0, 200.0, 15.0,
                Map.of(
                        "refined_iron", new MarketOrder("refined_iron", 200.0, 4000.0, 10.0, 0.8)
                )
        );

        Empire terran = new Empire(
                "terran_confederation", "Terran Confederation", "human", "Individualist",
                100000.0, 0.10, List.of("sol"), List.of(), Map.of(), List.of(), List.of()
        );

        Corporation transportCorp = new Corporation(
                "corp_terran_transport", "Terran Transport", "terran_confederation", "earth",
                "TRANSPORT", 50000.0, List.of(), List.of("freighter_01", "freighter_02"), List.of()
        );

        TradeRoute route = new TradeRoute(
                "route_earth_mars_iron", "Sol Earth-Mars Iron Supply Route",
                "terran_confederation", "hub_earth", "hub_mars", "refined_iron",
                1500.0, 1000.0, 10000.0, List.of("freighter_01"), 0.0, true
        );

        LogisticsProcessor.LogisticsResult result = processor.processTradeRoutes(
                List.of(route),
                List.of(originHub, destHub),
                List.of(terran),
                List.of(transportCorp)
        );

        assertNotNull(result);
        assertEquals(1500.0, result.totalVolumeMovedThisTurnKg(), 0.001);

        TradeRoute updatedRoute = result.updatedTradeRoutes().get(0);
        assertEquals(1500.0, updatedRoute.totalVolumeMovedKg(), 0.001);

        CommercialHub updatedOrigin = result.updatedCommercialHubs().stream()
                .filter(h -> h.id().equals("hub_earth")).findFirst().orElseThrow();
        assertEquals(3500.0, updatedOrigin.activeOrders().get("refined_iron").supplyKg(), 0.001);

        CommercialHub updatedDest = result.updatedCommercialHubs().stream()
                .filter(h -> h.id().equals("hub_mars")).findFirst().orElseThrow();
        assertEquals(1700.0, updatedDest.activeOrders().get("refined_iron").supplyKg(), 0.001);

        Empire updatedEmpire = result.updatedEmpires().stream()
                .filter(e -> e.id().equals("terran_confederation")).findFirst().orElseThrow();
        // Tariff 2% of 1500 = 30 credits added to treasury
        assertEquals(100030.0, updatedEmpire.treasuryCredits(), 0.001);
    }

    @Test
    public void testTradeRouteRespectsMinimumThresholdAndCapacity() {
        LogisticsProcessor processor = new LogisticsProcessor();

        CommercialHub originHub = new CommercialHub(
                "hub_earth", "earth", 0.05, 500000.0, 1200.0, 15.0,
                Map.of(
                        "refined_iron", new MarketOrder("refined_iron", 1200.0, 1000.0, 10.0, 0.0)
                )
        );

        CommercialHub destHub = new CommercialHub(
                "hub_mars", "mars", 0.05, 500000.0, 9500.0, 15.0,
                Map.of(
                        "refined_iron", new MarketOrder("refined_iron", 9500.0, 10000.0, 10.0, 0.5)
                )
        );

        // Min source threshold is 1000 kg -> only 200 kg available surplus
        // Max dest capacity is 9600 kg -> only 100 kg room at dest
        TradeRoute route = new TradeRoute(
                "route_restricted", "Restricted Iron Transfer",
                "terran_confederation", "hub_earth", "hub_mars", "refined_iron",
                1000.0, 1000.0, 9600.0, List.of(), 0.0, true
        );

        LogisticsProcessor.LogisticsResult result = processor.processTradeRoutes(
                List.of(route), List.of(originHub, destHub), List.of(), List.of()
        );

        assertEquals(100.0, result.totalVolumeMovedThisTurnKg(), 0.001);

        CommercialHub updatedOrigin = result.updatedCommercialHubs().stream()
                .filter(h -> h.id().equals("hub_earth")).findFirst().orElseThrow();
        assertEquals(1100.0, updatedOrigin.activeOrders().get("refined_iron").supplyKg(), 0.001);

        CommercialHub updatedDest = result.updatedCommercialHubs().stream()
                .filter(h -> h.id().equals("hub_mars")).findFirst().orElseThrow();
        assertEquals(9600.0, updatedDest.activeOrders().get("refined_iron").supplyKg(), 0.001);
    }
}
