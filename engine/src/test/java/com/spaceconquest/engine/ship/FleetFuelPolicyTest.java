package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class FleetFuelPolicyTest {
    @TempDir Path directory;

    private GameState world() {
        var design = new ShipDesign("design", "Rocket", "owner", ShipRole.EXPLORER, "steel",
                List.of("mod_chemical_rocket"), "steel", 0, 1000, 100, 1000, 0, 1, 0, 2000, false, false);
        var ship = new ShipInstance("ship", "design", "owner", 100, 0, 1000, Map.of());
        var fleet = new Fleet("fleet", "Fleet", "owner", "a", "", 0, 0, 0, false, "PASSIVE", List.of(ship),
                FleetLocation.at(FleetLocation.Site.deepSpace()));
        var planet = new Planet("planet", "Planet", "", 1, 1, SolarRadiation.AU_KM, 15, 12000,
                "terrestrial", "air", true, 0, List.of(), List.of(), List.of());
        return GameState.builder().shipDesigns(List.of(design)).fleets(List.of(fleet)).solarSystems(List.of(
                new SolarSystem("a", "A", "", 0, 0, 0, SolarRadiation.SOLAR_MASS_KG, 1392000, "yellow", List.of(planet), List.of()),
                new SolarSystem("b", "B", "", 1e6 / InterstellarTravel.METERS_PER_LIGHT_YEAR, 0, 0, 1, 1, "yellow", List.of(), List.of()))).build();
    }

    @Test void capacityTargetsFollowOwnerDiplomacyIncludingCorporations() {
        var state = world(); var fleet = state.fleets().getFirst(); var policy = fleet.fuelPolicy();
        assertEquals(50, policy.reserveKg(state, fleet, fleet.ships().getFirst()));
        state = state.withDiplomaticRelations(List.of(new DiplomaticRelation("other", "owner", "COLD_WAR", 0)));
        assertEquals(.10, policy.fraction(state, fleet));
        state = state.withDiplomaticRelations(List.of(new DiplomaticRelation("owner", "other", "TOTAL_WAR", 0)));
        assertEquals(.20, policy.fraction(state, fleet));
        var corp = new Corporation("owner", "Company", "empire", "", "LOGISTICS", 0, List.of(), List.of(), List.of());
        state = state.withCorporations(List.of(corp)).withDiplomaticRelations(List.of(new DiplomaticRelation("empire", "other", "TOTAL_WAR", 0)));
        assertEquals(.20, policy.fraction(state, fleet));
        var low = new ShipInstance("ship", "design", "owner", 100, 0, 10, Map.of());
        assertEquals(200, policy.reserveKg(state, fleet, low));
    }

    @Test void physicalBurnsAndExpectedApproachKeepIndependentContingencyStock() {
        var state = world(); var fleet = state.fleets().getFirst();
        var plan = LocalTravel.plan(state, fleet, FleetLocation.Site.orbit("planet"));
        assertNotNull(plan);
        var arrival = LocalTravel.projectedArrival(fleet, FleetLocation.Site.orbit("planet"), plan);
        assertTrue(arrival.ships().getFirst().currentFuelKg() >= 50 - 1e-6);
        assertEquals(50, plan.physical().propulsion().get("ship").protectedPropellantKg());
        var cross = InterstellarTravel.plan(state, fleet, "b", Map.of("ship", 100.0));
        assertNotNull(cross);
        assertTrue(cross.fuelBudgetKg().get("ship") <= 850 + 1e-6);
        assertEquals(50, cross.propulsion().get("ship").protectedPropellantKg());
        state = state.withDiplomaticRelations(List.of(new DiplomaticRelation("owner", "enemy", "TOTAL_WAR", 0)));
        cross = InterstellarTravel.plan(state, fleet, "b", Map.of("ship", 100.0));
        assertTrue(cross.fuelBudgetKg().get("ship") <= 700 + 1e-6);
    }

    @Test void mixedFleetProtectsEachMembersOwnCapacityTarget() {
        var state = world(); var fleet = state.fleets().getFirst();
        var design = new ShipDesign("large", "Large tank", "owner", ShipRole.EXPLORER, "steel",
                List.of("mod_chemical_rocket"), "steel", 0, 1000, 100, 2000, 0, 1, 0, 2000, false, false);
        var second = new ShipInstance("second", "large", "owner", 100, 0, 2000, Map.of());
        state = state.withShipDesigns(List.of(state.shipDesigns().getFirst(), design));
        fleet = fleet.withShips(List.of(fleet.ships().getFirst(), second));
        var target = FleetLocation.Site.orbit("planet");
        var plan = LocalTravel.plan(state, fleet, target);
        assertNotNull(plan);
        var arrival = LocalTravel.projectedArrival(fleet, target, plan);
        assertTrue(arrival.ships().getFirst().currentFuelKg() >= 50 - 1e-6);
        assertTrue(arrival.ships().getLast().currentFuelKg() >= 100 - 1e-6);
    }

    @Test void nuclearContingencyRetainsFeedThatMakesProtectedPropellantUsable() {
        var state = world(); var fleet = state.fleets().getFirst();
        var design = new ShipDesign("nuclear", "Thermal", "owner", ShipRole.EXPLORER, "steel",
                List.of("mod_fission_thruster"), "steel", 0, 1000, 100, 1000, 0, 1, 0, 2000, false, false);
        var ship = new ShipInstance("ship", "nuclear", "owner", 100, 0, 1000, Map.of("refined_uranium", .8));
        state = state.withShipDesigns(List.of(design));
        fleet = fleet.withShips(List.of(ship));
        var target = FleetLocation.Site.orbit("planet");
        var plan = LocalTravel.plan(state, fleet, target);
        assertNotNull(plan);
        var arrival = LocalTravel.projectedArrival(fleet, target, plan).ships().getFirst();
        assertTrue(arrival.currentFuelKg() >= 50 - 1e-6);
        assertTrue(arrival.storedCargoKg().get("refined_uranium") >= .05 - 1e-6);
    }

    @Test void splitMergeAndSaveLoadRetainTheMoreProtectivePolicy() throws Exception {
        var state = world(); var fleet = state.fleets().getFirst().withFuelPolicy(new FleetFuelPolicy(.1, .2, .3));
        var second = new ShipInstance("second", "design", "owner", 100, 0, 1000, Map.of());
        state = state.withFleets(List.of(fleet.withShips(List.of(fleet.ships().getFirst(), second))));
        state = FleetOrganization.split(state, "owner", "fleet", List.of("second"), "split", "Split");
        assertEquals(fleet.fuelPolicy(), state.fleets().getLast().fuelPolicy());
        var protectedSplit = state.fleets().getLast().withFuelPolicy(new FleetFuelPolicy(.2, .3, .4));
        state = state.withFleets(List.of(state.fleets().getFirst(), protectedSplit));
        state = FleetOrganization.transfer(state, "owner", "split", "fleet", List.of("second"));
        assertEquals(protectedSplit.fuelPolicy(), state.fleets().getFirst().fuelPolicy());
        fleet = state.fleets().getFirst();
        var target = FleetLocation.Site.orbit("planet");
        state = state.withFleets(List.of(LocalTravel.depart(fleet, target, LocalTravel.plan(state, fleet, target))));
        var save = new SaveGameManager(); var file = directory.resolve("reserve.scsave").toFile();
        save.save(file, state, 1, "2026-10-04T00:00:00Z");
        var reloaded = save.load(file).fleets().getFirst();
        assertEquals(protectedSplit.fuelPolicy(), reloaded.fuelPolicy());
        assertEquals(state.fleets().getFirst().location(), reloaded.location());
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        var older = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(fleet);
        older.remove("fuelPolicy");
        assertEquals(FleetFuelPolicy.automatic(), mapper.treeToValue(older, Fleet.class).fuelPolicy());
        var propulsion = mapper.readValue("""
                {"designId":"design","driveModuleId":"mod_chemical_rocket","exhaustVelocityMps":3400,
                 "reactorFeedId":null,"committedReactorKg":0}
                """, JourneyPropulsion.class);
        assertEquals(0, propulsion.protectedPropellantKg());
    }

    @Test void invalidTargetsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new FleetFuelPolicy(Double.NaN, .1, .2));
        assertThrows(IllegalArgumentException.class, () -> new FleetFuelPolicy(.2, .1, .3));
        assertThrows(IllegalArgumentException.class, () -> new FleetFuelPolicy(0, 0, 1));
    }
}
