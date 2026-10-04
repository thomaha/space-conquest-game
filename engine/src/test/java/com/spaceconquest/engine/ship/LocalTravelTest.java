package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LocalTravelTest {
    @Test
    void fissionManeuverRequiresAndConsumesPropellantAndReactorFuel() {
        ShipDesign design = new ShipDesign("fission", "Thermal freighter", "owner",
                ShipRole.CARGO_TRANSPORT, "steel", List.of("mod_fission_thruster"),
                "steel", 0, 10_000, 20_000, 500, 0, 1, 0, 850_000, true, false);
        GameState state = GameState.builder().shipDesigns(List.of(design)).build();
        FleetLocation.Site orbit = FleetLocation.Site.orbit("earth");
        FleetLocation.Site space = FleetLocation.Site.deepSpace();
        ShipInstance empty = new ShipInstance("ship", design.id(), "owner",
                100, 0, 100, Map.of());
        Fleet fleet = fleet(empty, orbit);
        assertNull(LocalTravel.plan(state, fleet, space));
        ShipInstance loaded = new ShipInstance("ship", design.id(), "owner",
                100, 0, 100, Map.of("refined_uranium", 0.1));
        Fleet ready = fleet(loaded, orbit);
        LocalTravel.Plan plan = LocalTravel.plan(state, ready, space);
        assertNotNull(plan);
        assertTrue(plan.propellantKg().get("ship") > 0.0);
        Fleet departing = LocalTravel.depart(ready, space, plan);
        assertTrue(departing.location().inTransit());
        assertTrue(departing.ships().getFirst().currentFuelKg() < 100.0);
        assertTrue(departing.ships().getFirst().storedCargoKg()
                .get("refined_uranium") < 0.1);
    }

    @Test
    void providerAscentDoesNotBurnShipPropellant() {
        ShipDesign design = new ShipDesign("chemical", "Rocket", "owner",
                ShipRole.CARGO_TRANSPORT, "steel", List.of("mod_chemical_rocket"),
                "steel", 0, 1_000, 2_000, 500, 0, 1, 0, 500_000, true, false);
        GameState state = GameState.builder().shipDesigns(List.of(design)).build();
        Fleet fleet = fleet(new ShipInstance("ship", design.id(), "owner",
                100, 0, 0, Map.of()), FleetLocation.Site.surface("earth"));
        LocalTravel.Plan plan = LocalTravel.plan(state, fleet, FleetLocation.Site.orbit("earth"));
        assertNotNull(plan);
        assertTrue(plan.propellantKg().isEmpty());
    }

    private Fleet fleet(ShipInstance ship, FleetLocation.Site site) {
        return new Fleet("fleet", "Fleet", "owner", "sol", "",
                0, 0, 0, false, "PASSIVE", List.of(ship), FleetLocation.at(site));
    }

    @Test
    void aLoadedSlowMemberSetsTheDurationForTheWholeFleet() {
        var design = new ShipDesign("slow", "Slow rocket", "owner", ShipRole.CARGO_TRANSPORT,
                "steel", List.of("mod_chemical_rocket"), "steel", 0, 10_000, 50_000, 1000,
                0, 1, 0, .1, false, false);
        var fastDesign = new ShipDesign("fast", "Fast rocket", "owner", ShipRole.EXPLORER,
                "steel", List.of("mod_chemical_rocket"), "steel", 0, 10_000, 0, 1000,
                0, 1, 0, 10_000, false, false);
        var state = GameState.builder().shipDesigns(List.of(design, fastDesign)).build();
        var ship = new ShipInstance("slow", "slow", "owner", 100, 0, 1000, Map.of());
        var origin = FleetLocation.Site.docked("port");
        var destination = FleetLocation.Site.deepSpace();
        var empty = LocalTravel.plan(state, fleet(ship, origin), destination);
        var loaded = new ShipInstance("slow", "slow", "owner", 100, 0, 1000,
                Map.of("steel", 20_000.0), 10, "human", "CONSCIOUS",
                new ShipPowerState(Map.of("refined_uranium", 100.0), "rp1", "uranium",
                        0, true, 1, 1, 0, 0, 0, 0, 0),
                new ShipSupplyState(Map.of("hydrogen_gas", 1000.0), null, ""));
        var fast = new ShipInstance("fast", "fast", "owner", 100, 0, 1000, Map.of());
        var group = fleet(loaded, origin).withShips(List.of(fast, loaded));
        var plan = LocalTravel.plan(state, group, destination);
        assertNotNull(plan);
        assertEquals(Math.ceil(50 * 32_900 / .1 / 86400), plan.days());
        assertTrue(plan.days() > empty.days());
        var departed = LocalTravel.depart(group, destination, plan);
        assertEquals(plan.days(), departed.location().travelDays());
        assertTrue(departed.location().advanceDays(2).inTransit());
    }

    @Test
    void aRecognizedDriveWithoutThrustCannotAuthorizeAManeuver() {
        var design = new ShipDesign("zero", "Disabled rocket", "owner", ShipRole.EXPLORER,
                "steel", List.of("mod_chemical_rocket"), "steel", 0, 1000, 0, 1000,
                0, 1, 0, 0, false, false);
        var state = GameState.builder().shipDesigns(List.of(design)).build();
        var fleet = fleet(new ShipInstance("ship", "zero", "owner", 100, 0, 1000, Map.of()),
                FleetLocation.Site.orbit("earth"));
        assertNull(LocalTravel.plan(state, fleet, FleetLocation.Site.deepSpace()));
    }
}
