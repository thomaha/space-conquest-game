package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.MarketOrder;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FleetLocation;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipInstance;
import com.spaceconquest.engine.ship.ShipRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConstructionProgressTest {
    @Test
    void stationCargoRequiresDockingRatherThanNearbyPlanetaryOrbit() {
        Corporation owner = new Corporation("corp", "Builder", "emp", "earth", "BUILDER",
                1_000, List.of(), List.of(), List.of());
        OrbitalStation station = new OrbitalStation("station", "Dock", "sol", "earth",
                "corp", OrbitalStation.OWNERSHIP_PRIVATE_CORPORATE, 20, List.of(), Map.of(),
                0, 0, 0, 0, 100, 100, "steel", 1, true);
        ShipDesign design = new ShipDesign("transport", "Transport", "corp",
                ShipRole.CARGO_TRANSPORT, "steel", List.of(), "steel", 0,
                1_000, 100, 0, 1, 0, 0, true, false);
        Fleet nearby = new Fleet("fleet", "Cargo", "corp", "sol", "",
                0, 0, 0, false, "PASSIVE", List.of(new ShipInstance("ship", design.id(),
                "corp", 100, 0, 100, Map.of("steel", 10.0))),
                FleetLocation.at(FleetLocation.Site.orbit("earth")));
        GameState state = GameState.builder().corporations(List.of(owner))
                .orbitalStations(List.of(station)).shipDesigns(List.of(design))
                .fleets(List.of(nearby)).build();
        assertEquals(0, ConstructionMaterials.buyOrbitalUpTo(state, "sol", station.id(),
                "corp", Map.of("steel", 10.0), 10).totalKg(), 0.001);
        GameState docked = state.withFleets(List.of(nearby.withLocation(FleetLocation.at(
                FleetLocation.Site.docked(station.id())))));
        assertEquals(10, ConstructionMaterials.buyOrbitalUpTo(docked, "sol", station.id(),
                "corp", Map.of("steel", 10.0), 10).totalKg(), 0.001);
    }

    @Test
    void groundWorkCannotSpendAnotherPlanetsStock() {
        Corporation owner = new Corporation("corp", "Builder", "emp", "earth", "BUILDER",
                1_000.0, List.of(), List.of(), List.of());
        Planet earth = new Planet("earth", "Earth", "", 1, 1, 1, 0, 1,
                "terrestrial", "breathable", true, 1, List.of(), List.of(), List.of());
        Planet mars = new Planet("mars", "Mars", "", 1, 1, 1, 0, 1,
                "terrestrial", "none", false, 0, List.of(), List.of(), List.of());
        SolarSystem sol = new SolarSystem("sol", "Sol", "", 0, 0, 0,
                1, 1, "Yellow", List.of(earth, mars), List.of());
        CommercialHub hub = new CommercialHub("hub_earth", "earth", 0.0, 100.0, 10.0,
                1.0, Map.of("steel", new MarketOrder("steel", 10.0, 0.0, 1.0, 0.0)));
        GameState state = GameState.builder().solarSystems(List.of(sol))
                .corporations(List.of(owner)).commercialHubs(List.of(hub)).build();
        assertNull(ConstructionMaterials.bodyForSystem(state, "sol", "mars"));
        ConstructionProgress.Step step = ConstructionProgress.advance(state, "mars", "corp",
                Map.of("steel", 10.0), Map.of(), 0.0, 1.0, 1.0);
        assertEquals(0.0, step.workHours());
        assertEquals(10.0, step.state().commercialHubs().getFirst().activeOrders()
                .get("steel").supplyKg(), 0.000001);
    }

    @Test
    void orbitalWorkUsesStationHubAndNeverSurfaceStock() {
        Corporation owner = new Corporation("corp", "Builder", "emp", "earth", "BUILDER",
                1_000.0, List.of(), List.of(), List.of());
        CommercialHub surface = new CommercialHub("surface", "earth", 0.0, 100.0,
                10.0, 1.0, Map.of("steel", new MarketOrder("steel", 10.0, 0, 1.0, 0)));
        OrbitalStation station = new OrbitalStation("station", "Dock", "sol", "earth",
                "corp", OrbitalStation.OWNERSHIP_PRIVATE_CORPORATE, 20, List.of(), Map.of(),
                0, 0, 0, 0, 100, 100, "steel", 1.0, true);
        GameState groundOnly = GameState.builder().corporations(List.of(owner))
                .commercialHubs(List.of(surface)).orbitalStations(List.of(station)).build();
        ConstructionProgress.Step stalled = ConstructionProgress.advanceOrbital(groundOnly,
                "sol", "earth", "corp", Map.of("steel", 10.0), Map.of(), 0, 1, 1);
        assertEquals(0.0, stalled.workHours());
        CommercialHub orbit = new CommercialHub("orbit", "station", 0.0, 100.0,
                10.0, 1.0, Map.of("steel", new MarketOrder("steel", 10.0, 0, 1.0, 0)));
        GameState supplied = groundOnly.withCommercialHubs(List.of(surface, orbit));
        ConstructionProgress.Step built = ConstructionProgress.advanceOrbital(supplied,
                "sol", "earth", "corp", Map.of("steel", 10.0), Map.of(), 0, 1, 1);
        assertTrue(built.complete());
        assertEquals(10.0, built.state().commercialHubs().getFirst().activeOrders()
                .get("steel").supplyKg(), 0.000001);
        assertEquals(0.0, built.state().commercialHubs().get(1).activeOrders()
                .get("steel").supplyKg(), 0.000001);
    }

    @Test
    void orbitalWorkCannotTakeForeignOrInTransitCargo() {
        Corporation owner = new Corporation("owner", "Owner", "emp", "earth", "BUILDER",
                1_000.0, List.of(), List.of(), List.of());
        ShipDesign design = new ShipDesign("transport", "Transport", "other",
                ShipRole.CARGO_TRANSPORT, "steel", List.of(), "steel", 0.0,
                1_000.0, 100.0, 0.0, 1.0, 0.0, 0.0, true, false);
        ShipInstance ship = new ShipInstance("ship", "transport", "other", 100, 0, 100,
                Map.of("steel", 10.0));
        Fleet foreign = new Fleet("fleet", "Foreign", "other", "sol", "",
                0, 0, 0, false, "PASSIVE", List.of(ship));
        GameState state = GameState.builder().corporations(List.of(owner))
                .shipDesigns(List.of(design)).fleets(List.of(foreign)).build();
        Map<String, Double> bill = Map.of("steel", 10.0);
        assertEquals(0.0, ConstructionProgress.advanceOrbital(state, "sol", "earth",
                "owner", bill, Map.of(), 0, 1, 1).workHours());
        Fleet inWarp = new Fleet("fleet", "Owner in transit", "owner", "sol", "alpha",
                0, 0, 0.5, true, "PASSIVE", List.of(new ShipInstance("ship",
                "transport", "owner", 100, 0, 100, Map.of("steel", 10.0))));
        assertEquals(0.0, ConstructionProgress.advanceOrbital(state.withFleets(List.of(inWarp)),
                "sol", "earth", "owner", bill, Map.of(), 0, 1, 1).workHours());
    }

    @Test
    void partialMaterialsStartWorkThenResumeWhenSupplyArrives() {
        Corporation owner = new Corporation("corp", "Builder", "emp", "earth", "BUILDER",
                1_000.0, List.of(), List.of(), List.of());
        CommercialHub hub = new CommercialHub("hub", "earth", 0.0, 100.0, 5.0, 1.0,
                Map.of("aluminum", new MarketOrder("aluminum", 5.0, 0.0, 2.0, 0.0),
                        "steel", new MarketOrder("steel", 0.0, 0.0, 2.0, 0.0)));
        GameState state = GameState.builder().corporations(List.of(owner))
                .commercialHubs(List.of(hub)).build();
        Map<String, Double> bill = Map.of("aluminum", 5.0, "steel", 5.0);

        ConstructionProgress.Step first = ConstructionProgress.advance(state, "earth", "corp",
                bill, Map.of(), 0.0, 10.0, 10.0);
        assertEquals(5.0, first.workHours(), 0.00001);
        assertFalse(first.complete());
        assertEquals(5.0, first.consumedKg().get("aluminum"), 0.00001);
        assertEquals(990.0, first.state().corporations().getFirst().liquidCapitalReserves(), 0.00001);

        ConstructionProgress.Step stalled = ConstructionProgress.advance(first.state(), "earth", "corp",
                bill, first.consumedKg(), first.workHours(), 10.0, 10.0);
        assertEquals(5.0, stalled.workHours(), 0.00001);
        assertFalse(stalled.complete());
        CommercialHub oldHub = stalled.state().commercialHubs().getFirst();
        CommercialHub restocked = new CommercialHub(oldHub.id(), oldHub.entityId(),
                oldHub.transactionTariffRate(), oldHub.storageCapacityKg(), 5.0,
                oldHub.logisticsRangeUnits(), Map.of(
                "aluminum", oldHub.activeOrders().get("aluminum"),
                "steel", new MarketOrder("steel", 5.0, 0.0, 2.0, 0.0)));
        GameState replenished = stalled.state().withCommercialHubs(List.of(restocked));
        ConstructionProgress.Step finished = ConstructionProgress.advance(replenished, "earth", "corp",
                bill, stalled.consumedKg(), stalled.workHours(), 10.0, 10.0);
        assertTrue(finished.complete());
        assertEquals(10.0, finished.workHours(), 0.00001);
        assertEquals(980.0, finished.state().corporations().getFirst().liquidCapitalReserves(), 0.00001);
    }
}
