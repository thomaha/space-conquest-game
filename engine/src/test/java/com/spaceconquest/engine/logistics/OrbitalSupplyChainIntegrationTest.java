package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.AsteroidBelt;
import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.MarketOrder;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.Population;
import com.spaceconquest.engine.Race;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.SpaceConquestEngine;
import com.spaceconquest.engine.economy.HouseholdAccount;
import com.spaceconquest.engine.economy.HouseholdEconomyProcessor;
import com.spaceconquest.engine.economy.MarketAccount;
import com.spaceconquest.engine.industry.GeologicalDeposit;
import com.spaceconquest.engine.industry.IndustryAccount;
import com.spaceconquest.engine.industry.IndustryMarketProcessor;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.macrostructure.StationModule;
import com.spaceconquest.engine.market.MarketDemandProcessor;
import com.spaceconquest.engine.market.MarketProcessor;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FleetLocation;
import com.spaceconquest.engine.ship.FleetProcessor;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipInstance;
import com.spaceconquest.engine.ship.ShipRole;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OrbitalSupplyChainIntegrationTest {
    @Test
    void dailyEngineTicksMoveAsteroidStockAndServeOrbitalResidents() {
        GameState initial = scenario();
        SolarSystem originalSystem = initial.solarSystems().getFirst();
        AsteroidBelt originalBelt = originalSystem.asteroidBelts().getFirst();
        AsteroidBelt inhabitedBelt = new AsteroidBelt(originalBelt.id(), originalBelt.name(),
                originalBelt.description(), originalBelt.resources(),
                List.of(new Population("human", Map.of(25, 300L))));
        SolarSystem system = new SolarSystem(originalSystem.id(), originalSystem.name(),
                originalSystem.description(), originalSystem.x(), originalSystem.y(), originalSystem.z(),
                originalSystem.sunMass(), originalSystem.sunDiameter(), originalSystem.sunColor(),
                originalSystem.planets(), List.of(inhabitedBelt));
        Planet originalWorld = system.planets().getFirst();
        Planet populatedWorld = new Planet(originalWorld.id(), originalWorld.name(),
                originalWorld.description(), originalWorld.mass(), originalWorld.gravity(),
                originalWorld.distance(), originalWorld.inclination(), originalWorld.diameter(),
                originalWorld.type(), originalWorld.atmosphere(), originalWorld.hasLiquidWater(),
                originalWorld.waterLevel(), originalWorld.resources(), originalWorld.moons(),
                List.of(new Population("human", Map.of(25, 2_000L))));
        system = new SolarSystem(system.id(), system.name(), system.description(), system.x(),
                system.y(), system.z(), system.sunMass(), system.sunDiameter(), system.sunColor(),
                List.of(populatedWorld), system.asteroidBelts());
        CommercialHub asteroidHub = new CommercialHub("hub_belt", "belt", 0,
                100_000, 1_000, 10, Map.of("water_ice",
                new MarketOrder("water_ice", 1_000, 0, 1, 0)));
        CommercialHub homeHub = new CommercialHub("hub_world", "world", 0,
                100_000, 50_000, 10, Map.of("food_matrix",
                new MarketOrder("food_matrix", 50_000, 0, 4, 0)));
        Fleet initialFleet = initial.fleets().getFirst();
        TradeRoute route = initial.tradeRoutes().getFirst();
        GameState tickScenario = initial.toBuilder().solarSystems(List.of(system))
                .commercialHubs(List.of(asteroidHub, homeHub, initial.commercialHubs().getFirst()))
                .industrialFacilities(List.of(initial.industrialFacilities().getLast()))
                .industryAccounts(List.of(initial.industryAccounts().getLast()))
                .geologicalDeposits(List.of())
                .marketAccounts(List.of(new MarketAccount(asteroidHub.id(), 1_000_000),
                        new MarketAccount(homeHub.id(), 1_000_000),
                        new MarketAccount("station_market", 1_000_000)))
                .fleets(List.of(initialFleet)).tradeRoutes(List.of(route)).build();
        SpaceConquestEngine engine = new SpaceConquestEngine();
        engine.reset(tickScenario);

        engine.stepTurn();
        GameState enroute = engine.getGameState();
        assertEquals(0.0, hub(enroute, "station_market").activeOrders()
                .get("water_ice").supplyKg(), 0.001,
                "cargo should not be available to the destination before completing its travel day");
        assertTrue(enroute.fleets().getFirst().location()
                .isAt(FleetLocation.Site.docked("station")));
        assertTrue(enroute.fleets().getFirst().ships().getFirst()
                .storedCargoKg().getOrDefault("water_ice", 0.0) > 0.0,
                "the freighter should still carry the load when it arrives after the travel day");
        for (int day = 0; day < 3; day++) engine.stepTurn();

        GameState advanced = engine.getGameState();
        assertTrue(hub(advanced, "station_market").activeOrders()
                .get("water_ice").supplyKg() > 0.0,
                "daily tick coordination should deliver asteroid stock to the orbital hub");
        assertTrue(hub(advanced, "station_market").activeOrders()
                .get("food_matrix").supplyKg() < 500.0,
                "orbital residents should consume locally stocked food during scheduled ticks");
    }

    @Test
    void asteroidMiningFeedsAnOrbitalRefineryAndResidentMarketAcrossTurns() throws IOException {
        GameState state = scenario();
        List<Race> races = DataModelLoader.loadRaces();
        MarketDemandProcessor demand = new MarketDemandProcessor();
        MarketProcessor prices = new MarketProcessor();
        IndustryMarketProcessor industry = new IndustryMarketProcessor();
        LogisticsProcessor logistics = new LogisticsProcessor();
        FleetProcessor movement = new FleetProcessor();

        state = refreshDemandAndPrices(state, demand, prices, races);
        IndustryMarketProcessor.TurnResult mining = industry.process(state,
                Map.of("asteroid_mine", 100), Map.of());
        state = state.toBuilder().commercialHubs(mining.hubs())
                .marketAccounts(mining.marketAccounts()).industryAccounts(mining.industryAccounts())
                .geologicalDeposits(mining.deposits()).build();
        assertTrue(hub(state, "hub_belt").activeOrders().get("water_ice").supplyKg() > 0.0,
                "the staffed asteroid mine should sell extracted ice into its local hub");
        assertTrue(hub(state, "station_market").activeOrders().get("water_ice").demandKg() > 0.0,
                "the orbital refinery should create demand for transported ice");
        assertTrue(hub(state, "station_market").activeOrders().get("food_matrix").demandKg() > 0.0,
                "orbital residents should create local demand that replenishes the food reserve");

        state = logistics.processTradeRoutes(state).state();
        assertTrue(state.fleets().getFirst().location().inTransit(),
                "the freighter should leave the asteroid hub with a profitable load");
        assertEquals("water_ice", state.tradeRoutes().getFirst().materialId());

        for (int day = 0; day < 3; day++) {
            state = state.withFleets(movement.processFleetMovements(state.fleets(),
                    state.orbitalStations(), state.diplomaticRelations()));
            state = logistics.processTradeRoutes(state).state();
            state = refreshDemandAndPrices(state, demand, prices, races);
            var households = new HouseholdEconomyProcessor().process(state, races);
            state = state.toBuilder().commercialHubs(households.commercialHubs())
                    .marketAccounts(households.marketAccounts())
                    .householdAccounts(households.householdAccounts()).build();
        }

        assertTrue(hub(state, "station_market").activeOrders().get("water_ice").supplyKg() > 0.0,
                "the route should deliver mined ice to the orbital hub");
        assertTrue(hub(state, "station_market").activeOrders().get("food_matrix").supplyKg() < 500.0,
                "orbital residents should buy food from their local hub during freight transit");
        assertEquals(200, state.householdAccounts().stream()
                .filter(account -> "station".equals(account.bodyId()))
                .mapToLong(HouseholdAccount::headcount).sum());

        var refined = industry.process(state, Map.of("orbital_refinery", 10), Map.of());
        assertTrue(refined.industryAccounts().stream()
                .filter(account -> "orbital_refinery".equals(account.facilityId()))
                .anyMatch(account -> account.producedKg().getOrDefault("purified_water", 0.0) > 0.0),
                "the orbital refinery should consume delivered ice and produce local water");
    }

    private GameState refreshDemandAndPrices(GameState state, MarketDemandProcessor demand,
                                             MarketProcessor prices, List<Race> races) {
        return state.withCommercialHubs(prices.updateCommercialHubs(demand.refresh(state, races)));
    }

    private CommercialHub hub(GameState state, String id) {
        return state.commercialHubs().stream().filter(item -> id.equals(item.id()))
                .findFirst().orElseThrow();
    }

    private GameState scenario() {
        AsteroidBelt belt = new AsteroidBelt("belt", "Ice-rich asteroid", "",
                List.of("water_ice"), List.of());
        Planet world = new Planet("world", "Home", "", 5.97e24, 9.81, 149_600_000,
                0, 12_742, "terrestrial", "nitrogen_oxygen", true, 0.71,
                List.of(), List.of(), List.of());
        SolarSystem system = new SolarSystem("sol", "Sol", "", 0, 0, 0,
                1.989e30, 1_392_700, "yellow", List.of(world), List.of(belt));
        Empire empire = new Empire("empire", "Empire", "human", "Individualist",
                10_000_000, 0.1, List.of("sol"), List.of(), Map.of(),
                List.of("industrial_production"), List.of());
        StationModule habitation = new StationModule("hab", "Habitation",
                StationModule.TYPE_HABITATION, 6, 12_000, 30, 0, Map.of(),
                "technician", 3, true);
        OrbitalStation station = new OrbitalStation("station", "Orbital settlement", "sol",
                "world", "empire", OrbitalStation.OWNERSHIP_PUBLIC_STATE, 40,
                List.of(habitation), Map.of(), 0, 30, 0, 0, 100, 100,
                "steel", 1, true, List.of(new Population("human", Map.of(25, 200L))));
        CommercialHub stationHub = new CommercialHub("station_market", station.id(), 0,
                100_000, 1_000, 10, Map.of(
                "food_matrix", new MarketOrder("food_matrix", 500, 0, 4, 0),
                "consumer_goods", new MarketOrder("consumer_goods", 100, 0, 12, 0)));
        IndustrialFacility mine = new IndustrialFacility("asteroid_mine", "belt",
                "water_ice_mining", empire.id(), IndustrialFacility.PUBLIC_STATE,
                1, 100, "industrial_worker", false, 0);
        IndustrialFacility refinery = new IndustrialFacility("orbital_refinery", station.id(),
                "ice_water_treatment", empire.id(), IndustrialFacility.PUBLIC_STATE,
                1, 10, "industrial_worker", false, 0);
        ShipDesign design = new ShipDesign("cargo_design", "Ice freighter", empire.id(),
                ShipRole.CARGO_TRANSPORT, "steel", List.of(), "steel", 0,
                1_000, 100, 0, 1, 0, 0, true, false);
        ShipInstance ship = new ShipInstance("freighter", design.id(), empire.id(),
                100, 0, 100, Map.of());
        Fleet fleet = new Fleet("fleet", "Ice freighter", empire.id(), "sol", "",
                0, 0, 0, false, "PASSIVE", List.of(ship),
                FleetLocation.at(FleetLocation.Site.deepSpace("belt")));
        TradeRoute route = new TradeRoute("route", "Asteroid to station", empire.id(),
                "hub_belt", stationHub.id(), "water_ice", 500, 0, 50_000,
                List.of(ship.id()), 0, true);
        return GameState.builder().solarSystems(List.of(system)).empires(List.of(empire))
                .orbitalStations(List.of(station)).commercialHubs(List.of(stationHub))
                .industrialFacilities(List.of(mine, refinery))
                .industryAccounts(List.of(IndustryAccount.empty("asteroid_mine"),
                        IndustryAccount.empty("orbital_refinery").withOperatingCash(1_000_000)))
                .geologicalDeposits(List.of(new GeologicalDeposit("ice_vein", "belt", "water_ice",
                        1_000_000, 1_000_000, 1.0, true, empire.id())))
                .marketAccounts(List.of(new MarketAccount("hub_belt", 1_000_000),
                        new MarketAccount(stationHub.id(), 1_000_000)))
                .shipDesigns(List.of(design)).fleets(List.of(fleet))
                .tradeRoutes(List.of(route)).build();
    }
}
