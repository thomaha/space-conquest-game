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
}
