package com.spaceconquest.engine.market;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.MarketOrder;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.Population;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FleetLocation;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipInstance;
import com.spaceconquest.engine.ship.ShipRole;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketDemandProcessorTest {
    @Test
    void populationGrowthChangesFoodAndConsumerDemandAndMarketShortage() throws IOException {
        MarketDemandProcessor demand = new MarketDemandProcessor();
        GameState initial = state(1_000L);
        CommercialHub first = demand.refresh(initial, DataModelLoader.loadRaces()).getFirst();
        CommercialHub grown = demand.refresh(state(2_000L), DataModelLoader.loadRaces()).getFirst();

        assertEquals(100.0, first.activeOrders().get("food_matrix").demandKg(), 0.001);
        assertEquals(200.0, grown.activeOrders().get("food_matrix").demandKg(), 0.001);
        assertEquals(10.0, grown.activeOrders().get("consumer_goods").demandKg(), 0.001);
        assertTrue(new MarketProcessor().updateHub(grown).activeOrders()
                .get("food_matrix").shortcomingScore() > 0.0);
    }

    @Test
    void operatingReactorsCreateDemandForFuelTheyBuy() throws IOException {
        GameState base = state(1_000L);
        Empire old = base.empires().getFirst();
        Empire researched = new Empire(old.id(), old.name(), old.raceId(),
                old.societyStructure(), old.treasuryCredits(), old.corporateTaxRate(),
                old.controlledSystemIds(), old.ministries(), old.systemGovernorAssignments(),
                List.of("electricity", "nuclear_fission"), old.activeShipDesignIds());
        IndustrialFacility reactor = new IndustrialFacility("reactor", "earth",
                "nuclear_power_app", old.id(), IndustrialFacility.PUBLIC_STATE,
                1, 100, "engineer", false, 0.0);
        GameState fueled = base.toBuilder().empires(List.of(researched))
                .industrialFacilities(List.of(reactor)).build();
        CommercialHub hub = new MarketDemandProcessor().refresh(fueled,
                DataModelLoader.loadRaces()).getFirst();
        assertEquals(10.0, hub.activeOrders().get("refined_uranium").demandKg(), 0.001);
    }

    @Test
    void shipsAtAHubCreatePropellantAndReactorReplenishmentDemand() throws IOException {
        ShipDesign design = new ShipDesign("fission", "Thermal ship", "terran",
                ShipRole.CARGO_TRANSPORT, "steel", List.of("mod_fission_thruster"),
                "steel", 0, 10_000, 1_000, 300, 0, 1, 0, 850_000, true, false);
        ShipInstance ship = new ShipInstance("ship", design.id(), "terran",
                100, 0, 0, Map.of());
        Fleet fleet = new Fleet("fleet", "Fleet", "terran", "sol", "",
                0, 0, 0, false, "PASSIVE", List.of(ship),
                FleetLocation.at(FleetLocation.Site.orbit("earth")));
        GameState state = state(1_000L).toBuilder().shipDesigns(List.of(design))
                .fleets(List.of(fleet)).build();
        CommercialHub hub = new MarketDemandProcessor().refresh(state,
                DataModelLoader.loadRaces()).getFirst();
        assertEquals(10.0, hub.activeOrders().get("hydrogen_gas").demandKg(), 0.001);
        assertEquals(0.01, hub.activeOrders().get("refined_uranium").demandKg(), 0.000001);
        ShipInstance thorium = new ShipInstance("ship", design.id(), "terran",
                100, 0, 100, Map.of("refined_thorium", 0.12));
        GameState usingThorium = state.withFleets(List.of(fleet.withShips(List.of(thorium))));
        CommercialHub preferred = new MarketDemandProcessor().refresh(usingThorium,
                DataModelLoader.loadRaces()).getFirst();
        assertEquals(0.008, preferred.activeOrders().get("refined_thorium").demandKg(),
                0.000001);
    }

    private GameState state(long residents) {
        Population population = new Population("human", Map.of(25, residents));
        Planet planet = new Planet("earth", "Earth", "", 5.97e24, 9.81,
                149_600_000.0, 0.0, 12_742.0, "terrestrial", "nitrogen_oxygen",
                true, 0.71, List.of(), List.of(), List.of(population));
        SolarSystem system = new SolarSystem("sol", "Sol", "", 0.0, 0.0, 0.0,
                1.989e30, 1_392_700.0, "yellow", List.of(planet), List.of());
        Empire empire = new Empire("terran", "Terran", "human", "Individualist", 0.0,
                0.15, List.of("sol"), List.of(), Map.of(), List.of(), List.of());
        CommercialHub hub = new CommercialHub("hub_earth", "earth", 0.05, 1_000.0,
                150.0, 15.0, Map.of("food_matrix",
                new MarketOrder("food_matrix", 150.0, 1.0, 2.0, 0.0)));
        return GameState.builder().solarSystems(List.of(system)).empires(List.of(empire))
                .commercialHubs(List.of(hub)).build();
    }
}
