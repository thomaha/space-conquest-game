package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.MarketOrder;
import com.spaceconquest.engine.economy.MarketAccount;
import com.spaceconquest.engine.economy.CorporateValuation;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FleetLocation;
import com.spaceconquest.engine.ship.FleetProcessor;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipInstance;
import com.spaceconquest.engine.ship.ShipRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class LogisticsProcessorTest {

    @Test
    void routeBuysLocalPropellantAndRecordsItsOperatingCost() {
        OrbitalStation sourceStation = station("station_a");
        OrbitalStation destinationStation = station("station_b");
        CommercialHub source = new CommercialHub("source", sourceStation.id(), 0,
                5_000, 2_100, 10, Map.of(
                "steel", new MarketOrder("steel", 100, 0, 2, 0),
                "rp1_kerosene", new MarketOrder("rp1_kerosene", 1_000, 0, 2, 0),
                "liquid_oxygen", new MarketOrder("liquid_oxygen", 1_000, 0, 2, 0)));
        CommercialHub destination = new CommercialHub("destination", destinationStation.id(),
                0, 5_000, 0, 10, Map.of("steel", new MarketOrder("steel", 0, 100, 5, 1)));
        Corporation corporation = new Corporation("corp", "Carrier", "emp", "earth",
                "TRANSPORT", 1_000, List.of(), List.of("ship"), List.of());
        Empire empire = new Empire("emp", "Empire", "human", "Individualist",
                0, 0.2, List.of("sol"), List.of(), Map.of(), List.of(), List.of());
        ShipDesign design = new ShipDesign("design", "Rocket freighter", "corp",
                ShipRole.CARGO_TRANSPORT, "steel", List.of("mod_chemical_rocket"),
                "steel", 0, 1_000, 100, 100, 0, 1, 0, 500_000, true, true);
        Fleet fleet = new Fleet("fleet", "Freighter", "corp", "sol", "",
                0, 0, 0, false, "PASSIVE", List.of(new ShipInstance("ship", design.id(),
                "corp", 100, 0, 0, Map.of())),
                FleetLocation.at(FleetLocation.Site.docked(sourceStation.id())));
        TradeRoute route = new TradeRoute("route", "Route", "corp", source.id(),
                destination.id(), "steel", 20, 0, 100, List.of("ship"), 0, true);
        GameState state = GameState.builder().orbitalStations(List.of(sourceStation,
                        destinationStation)).commercialHubs(List.of(source, destination))
                .marketAccounts(List.of(new MarketAccount(source.id(), 0),
                        new MarketAccount(destination.id(), 100)))
                .corporations(List.of(corporation)).empires(List.of(empire))
                .shipDesigns(List.of(design)).fleets(List.of(fleet))
                .tradeRoutes(List.of(route)).build();
        GameState moved = new LogisticsProcessor().processTradeRoutes(state).state();
        assertTrue(moved.fleets().getFirst().location().inTransit());
        assertTrue(moved.fleets().getFirst().ships().getFirst().currentFuelKg() > 0.0);
        assertTrue(moved.commercialHubs().getFirst().activeOrders()
                .get("rp1_kerosene").supplyKg() < 1_000.0);
        assertTrue(moved.commercialHubs().getFirst().activeOrders()
                .get("liquid_oxygen").supplyKg() < 1_000.0);
        assertTrue(moved.tradeRoutes().getFirst().dailyOperatingResultCredits() < 0.0);
        assertEquals(moved.tradeRoutes().getFirst().dailyOperatingResultCredits(),
                moved.tradeRoutes().getFirst().cumulativeOperatingResultCredits(), 0.001);
        assertTrue(moved.corporations().getFirst().liquidCapitalReserves() < 960.0);
        CommercialHub drySource = new CommercialHub(source.id(), source.entityId(),
                source.transactionTariffRate(), source.storageCapacityKg(), 1_100, 10,
                Map.of("steel", new MarketOrder("steel", 100, 0, 2, 0),
                        "rp1_kerosene", new MarketOrder("rp1_kerosene", 1_000, 0, 2, 0)));
        GameState blocked = new LogisticsProcessor().processTradeRoutes(state.withCommercialHubs(
                List.of(drySource, destination))).state();
        assertTrue(blocked.fleets().getFirst().location().isAt(
                FleetLocation.Site.docked(sourceStation.id())));
        assertEquals(20, blocked.tradeRoutes().getFirst().onboardKg(), 0.001);
    }

    @Test
    void loadingFreighterIsPulledTowardTheBestPricedMarket() {
        OrbitalStation sourceStation = station("station_a");
        OrbitalStation lowPriceStation = station("station_b");
        OrbitalStation premiumStation = station("station_c");
        CommercialHub source = new CommercialHub("source", sourceStation.id(), 0,
                5_000, 500, 10, Map.of("steel", new MarketOrder("steel", 100, 0, 2, 0),
                "gold", new MarketOrder("gold", 100, 0, 3, 0)));
        CommercialHub lowPrice = new CommercialHub("low", lowPriceStation.id(), 0,
                5_000, 0, 10, Map.of("steel", new MarketOrder("steel", 0, 20, 3, 0)));
        CommercialHub premium = new CommercialHub("premium", premiumStation.id(), 0,
                5_000, 0, 10, Map.of("steel", new MarketOrder("steel", 0, 100, 10, 1),
                "gold", new MarketOrder("gold", 0, 100, 20, 1)));
        Corporation corporation = new Corporation("corp", "Carrier", "emp", "earth",
                "TRANSPORT", 1_000, List.of(), List.of("ship"), List.of());
        Empire empire = new Empire("emp", "Empire", "human", "Individualist",
                0, 0.2, List.of("sol"), List.of(), Map.of(), List.of(), List.of());
        ShipDesign design = new ShipDesign("design", "Freighter", "corp",
                ShipRole.CARGO_TRANSPORT, "steel", List.of(), "steel", 0,
                1_000, 100, 0, 1, 0, 0, true, true);
        Fleet fleet = new Fleet("fleet", "Freighter", "corp", "sol", "",
                0, 0, 0, false, "PASSIVE", List.of(new ShipInstance("ship", "design",
                "corp", 100, 0, 100, Map.of())),
                FleetLocation.at(FleetLocation.Site.docked(sourceStation.id())));
        TradeRoute route = new TradeRoute("route", "Steel route", "corp", source.id(),
                lowPrice.id(), "steel", 20, 0, 100, List.of("ship"), 0, true);
        GameState state = GameState.builder().orbitalStations(List.of(sourceStation,
                        lowPriceStation, premiumStation)).commercialHubs(List.of(source, lowPrice, premium))
                .marketAccounts(List.of(new MarketAccount(source.id(), 0),
                        new MarketAccount(lowPrice.id(), 1_000), new MarketAccount(premium.id(), 1_000)))
                .corporations(List.of(corporation)).empires(List.of(empire))
                .shipDesigns(List.of(design)).fleets(List.of(fleet)).tradeRoutes(List.of(route)).build();

        GameState loaded = new LogisticsProcessor().processTradeRoutes(state).state();
        assertEquals("premium", loaded.tradeRoutes().getFirst().destinationEntityId());
        assertEquals("gold", loaded.tradeRoutes().getFirst().materialId());
        assertEquals(20.0, loaded.fleets().getFirst().ships().getFirst()
                .storedCargoKg().get("gold"), 0.001);
        assertTrue(loaded.fleets().getFirst().location().inTransit());
    }

    @Test
    void corporateCarrierBuysStockAndWaitsForDestinationCash() {
        OrbitalStation sourceStation = station("station_a");
        OrbitalStation destinationStation = station("station_b");
        CommercialHub source = new CommercialHub("source", sourceStation.id(), 0,
                1_000, 100, 10, Map.of("steel", new MarketOrder("steel", 100, 0, 2, 0)));
        CommercialHub destination = new CommercialHub("destination", destinationStation.id(), 0.02,
                1_000, 0, 10, Map.of("steel", new MarketOrder("steel", 0, 20, 5, 1)));
        Corporation corporation = new Corporation("corp", "Carrier", "emp", "earth",
                "TRANSPORT", 1_000, List.of(), List.of("ship"), List.of());
        Empire empire = new Empire("emp", "Empire", "human", "Individualist",
                0, 0.2, List.of("sol"), List.of(), Map.of(), List.of(), List.of());
        ShipDesign design = new ShipDesign("design", "Freighter", "corp",
                ShipRole.CARGO_TRANSPORT, "steel", List.of(), "steel", 0,
                1_000, 100, 0, 1, 0, 0, true, true);
        Fleet fleet = new Fleet("fleet", "Freighter", "corp", "sol", "",
                0, 0, 0, false, "PASSIVE", List.of(new ShipInstance("ship", "design",
                "corp", 1_000, 0, 100, Map.of())),
                FleetLocation.at(FleetLocation.Site.docked(sourceStation.id())));
        TradeRoute route = new TradeRoute("route", "Paid route", "corp", source.id(),
                destination.id(), "steel", 20, 0, 100, List.of("ship"), 0, true);
        GameState state = GameState.builder().orbitalStations(List.of(sourceStation,
                        destinationStation)).commercialHubs(List.of(source, destination))
                .marketAccounts(List.of(new MarketAccount(source.id(), 0),
                        new MarketAccount(destination.id(), 40)))
                .corporations(List.of(corporation)).empires(List.of(empire))
                .shipDesigns(List.of(design)).fleets(List.of(fleet))
                .tradeRoutes(List.of(route)).build();
        double openingNetWorth = CorporateValuation.value(state, corporation).netWorthCredits();
        double openingCredits = trackedCredits(state);
        double openingSteel = trackedMaterial(state, "steel");
        LogisticsProcessor processor = new LogisticsProcessor();
        GameState loaded = processor.processTradeRoutes(state).state();
        assertEquals(960, loaded.corporations().getFirst().liquidCapitalReserves(), 0.001);
        assertEquals(40, cash(loaded, source.id()), 0.001);
        assertEquals(40, loaded.tradeRoutes().getFirst().onboardCostCredits(), 0.001);
        assertEquals(openingNetWorth, CorporateValuation.value(loaded,
                loaded.corporations().getFirst()).netWorthCredits(), 0.001);
        assertEquals(openingCredits, trackedCredits(loaded), 0.001);
        assertEquals(openingSteel, trackedMaterial(loaded, "steel"), 0.001);
        GameState arrived = loaded.withFleets(new FleetProcessor().processFleetMovements(
                loaded.fleets(), List.of(), List.of()));
        GameState partlySold = processor.processTradeRoutes(arrived).state();
        assertEquals(10, partlySold.tradeRoutes().getFirst().onboardKg(), 0.001);
        assertEquals(20, partlySold.tradeRoutes().getFirst().onboardCostCredits(), 0.001);
        assertEquals(19.2, partlySold.tradeRoutes().getFirst().dailyOperatingResultCredits(), 0.001);
        assertEquals(999.2, partlySold.corporations().getFirst().liquidCapitalReserves(), 0.001);
        assertEquals(0, cash(partlySold, destination.id()), 0.001);
        assertEquals(openingNetWorth + 19.2, CorporateValuation.value(partlySold,
                partlySold.corporations().getFirst()).netWorthCredits(), 0.001);
        assertEquals(openingCredits, trackedCredits(partlySold), 0.001);
        assertEquals(openingSteel, trackedMaterial(partlySold, "steel"), 0.001);
        GameState stalled = processor.processTradeRoutes(partlySold).state();
        assertEquals(10, stalled.tradeRoutes().getFirst().onboardKg(), 0.001);
        assertEquals(0, stalled.tradeRoutes().getFirst().dailyOperatingResultCredits(), 0.001);
        GameState funded = stalled.withMarketAccounts(List.of(
                new MarketAccount(source.id(), cash(stalled, source.id())),
                new MarketAccount(destination.id(), 40)));
        GameState completed = processor.processTradeRoutes(funded).state();
        assertEquals(20, completed.tradeRoutes().getFirst().totalVolumeMovedKg(), 0.001);
        assertEquals(38.4, completed.tradeRoutes().getFirst().cumulativeOperatingResultCredits(), 0.001);
        assertEquals(0, completed.tradeRoutes().getFirst().onboardKg(), 0.001);
        assertEquals(0, completed.fleets().getFirst().ships().getFirst().storedCargoKg()
                .get("steel"), 0.001);
        assertEquals(openingCredits + 40, trackedCredits(completed), 0.001);
        assertEquals(openingSteel, trackedMaterial(completed, "steel"), 0.001);
        Corporation shortOfCash = new Corporation("corp", "Carrier", "emp", "earth",
                "TRANSPORT", 30, List.of(), List.of("ship"), List.of());
        GameState limited = processor.processTradeRoutes(state.withCorporations(
                List.of(shortOfCash))).state();
        assertEquals(15, limited.tradeRoutes().getFirst().onboardKg(), 0.001);
        assertEquals(0, limited.corporations().getFirst().liquidCapitalReserves(), 0.001);
    }

    private OrbitalStation station(String id) {
        return new OrbitalStation(id, id, "sol", "earth", "emp",
                OrbitalStation.OWNERSHIP_PUBLIC_STATE, 10, List.of(), Map.of(),
                0, 0, 0, 0, 100, 100, "steel", 1, true);
    }

    private double cash(GameState state, String hubId) {
        return state.marketAccounts().stream().filter(account -> hubId.equals(account.hubId()))
                .mapToDouble(MarketAccount::unsettledSalesCredits).findFirst().orElse(0.0);
    }

    private double trackedCredits(GameState state) {
        return state.empires().stream().mapToDouble(Empire::treasuryCredits).sum()
                + state.corporations().stream().mapToDouble(Corporation::liquidCapitalReserves).sum()
                + state.marketAccounts().stream().mapToDouble(MarketAccount::unsettledSalesCredits).sum();
    }

    private double trackedMaterial(GameState state, String materialId) {
        return state.commercialHubs().stream().mapToDouble(hub -> hub.activeOrders()
                .getOrDefault(materialId, new MarketOrder(materialId, 0, 0, 0, 0))
                .supplyKg()).sum() + state.fleets().stream().flatMap(fleet -> fleet.ships().stream())
                .mapToDouble(ship -> ship.storedCargoKg().getOrDefault(materialId, 0.0)).sum();
    }

    @Test
    void physicalRouteMovesRealCargoOnlyAfterArrivalAndChargesLift() {
        Planet earth = new Planet("earth", "Earth", "", 1, 9.81, 1, 0,
                12_742, "terrestrial", "breathable", true, 1,
                List.of(), List.of(), List.of());
        Planet mars = new Planet("mars", "Mars", "", 1, 3.71, 1, 0,
                6_779, "terrestrial", "none", false, 1,
                List.of(), List.of(), List.of());
        SolarSystem sol = new SolarSystem("sol", "Sol", "", 0, 0, 0,
                1, 1, "Yellow", List.of(earth, mars), List.of());
        CommercialHub origin = new CommercialHub("hub_earth", "earth", 0,
                30_000, 20_100, 10, Map.of("steel", new MarketOrder("steel", 100, 0, 1, 0),
                "rp1_kerosene", new MarketOrder("rp1_kerosene", 10_000, 0, 1, 0),
                "liquid_oxygen", new MarketOrder("liquid_oxygen", 10_000, 0, 1, 0)));
        CommercialHub destination = new CommercialHub("hub_mars", "mars", 0.02,
                1_000, 0, 10, Map.of("steel", new MarketOrder("steel", 0, 100, 1, 0)));
        Empire empire = new Empire("emp", "Empire", "human", "Individualist",
                1_000_000_000, 0.1, List.of("sol"), List.of(), Map.of(), List.of(), List.of());
        ShipDesign design = new ShipDesign("cargo_design", "Cargo", "emp",
                ShipRole.CARGO_TRANSPORT, "steel", List.of(), "steel", 0,
                1_000, 100, 0, 1, 0, 0, true, false);
        ShipInstance ship = new ShipInstance("ship", design.id(), "emp",
                100, 0, 100, Map.of());
        Fleet fleet = new Fleet("fleet", "Freighter", "emp", "sol", "",
                0, 0, 0, false, "PASSIVE", List.of(ship),
                FleetLocation.at(FleetLocation.Site.surface("earth")));
        TradeRoute route = new TradeRoute("route", "Earth to Mars", "emp", origin.id(),
                destination.id(), "steel", 20, 0, 1_000, List.of(ship.id()), 0, true);
        GameState state = GameState.builder().solarSystems(List.of(sol))
                .commercialHubs(List.of(origin, destination)).empires(List.of(empire))
                .industrialFacilities(List.of(new IndustrialFacility("terminal", "earth",
                        "cargo_terminal", "emp", IndustrialFacility.PUBLIC_STATE,
                        1, 10, "technician", false, 0.0)))
                .marketAccounts(List.of(new MarketAccount(origin.id(), 0),
                        new MarketAccount(destination.id(), 1_000)))
                .shipDesigns(List.of(design)).fleets(List.of(fleet))
                .tradeRoutes(List.of(route)).build();
        LogisticsProcessor processor = new LogisticsProcessor();
        GameState dispatched = processor.processTradeRoutes(state).state();
        assertEquals(trackedCredits(state), trackedCredits(dispatched), 0.001);
        assertEquals(trackedMaterial(state, "steel"), trackedMaterial(dispatched, "steel"), 0.001);
        assertEquals(80, dispatched.commercialHubs().getFirst().activeOrders()
                .get("steel").supplyKg(), 0.001);
        assertEquals(0, dispatched.commercialHubs().get(1).activeOrders()
                .get("steel").supplyKg(), 0.001);
        assertEquals(20, dispatched.fleets().getFirst().ships().getFirst()
                .storedCargoKg().get("steel"), 0.001);
        assertEquals(empire.treasuryCredits() - dispatched.empires().getFirst()
                .treasuryCredits(), dispatched.marketAccounts().stream().filter(account ->
                origin.id().equals(account.hubId())).findFirst().orElseThrow()
                .unsettledSalesCredits(), 0.001);
        assertTrue(dispatched.tradeRoutes().getFirst().onboardCostCredits() > 20);
        assertTrue(dispatched.fleets().getFirst().location().inTransit());
        assertTrue(dispatched.empires().getFirst().treasuryCredits() < empire.treasuryCredits());

        FleetProcessor movement = new FleetProcessor();
        GameState ascending = dispatched.withFleets(movement.processFleetMovements(
                dispatched.fleets(), List.of(), List.of()));
        assertTrue(ascending.fleets().getFirst().location()
                .isAt(FleetLocation.Site.orbit("earth")));
        GameState enroute = processor.processTradeRoutes(ascending).state();
        GameState halfway = enroute.withFleets(movement.processFleetMovements(
                enroute.fleets(), List.of(), List.of()));
        assertEquals(0.5, halfway.fleets().getFirst().location().progress(), 0.001);
        assertEquals(0, processor.processTradeRoutes(halfway).deliveredKg(), 0.001);
        GameState arrived = halfway.withFleets(movement.processFleetMovements(
                halfway.fleets(), List.of(), List.of()));
        LogisticsProcessor.FreightResult delivery = processor.processTradeRoutes(arrived);
        assertEquals(trackedCredits(state), trackedCredits(delivery.state()), 0.001);
        assertEquals(trackedMaterial(state, "steel"), trackedMaterial(delivery.state(), "steel"), 0.001);
        assertEquals(20, delivery.deliveredKg(), 0.001);
        assertEquals(20, delivery.state().commercialHubs().get(1).activeOrders()
                .get("steel").supplyKg(), 0.001);
        assertEquals(0, delivery.state().fleets().getFirst().ships().getFirst()
                .storedCargoKg().get("steel"), 0.001);
        assertEquals(984, delivery.state().marketAccounts().stream().filter(account ->
                destination.id().equals(account.hubId())).findFirst().orElseThrow()
                .unsettledSalesCredits(), 0.001);
        assertEquals(16 - 0.32 - dispatched.tradeRoutes().getFirst().onboardCostCredits(),
                delivery.state().tradeRoutes().getFirst().dailyOperatingResultCredits(), 0.001);

        GameState noFreighter = state.withFleets(List.of());
        assertEquals(0, processor.processTradeRoutes(noFreighter).deliveredKg(), 0.001);
        assertEquals(100, processor.processTradeRoutes(noFreighter).state()
                .commercialHubs().getFirst().activeOrders().get("steel").supplyKg(), 0.001);
    }

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

        TradeRoute updatedRoute = result.updatedTradeRoutes().getFirst();
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
