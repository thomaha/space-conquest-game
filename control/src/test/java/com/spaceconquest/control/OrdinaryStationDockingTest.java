package com.spaceconquest.control;

import com.fasterxml.jackson.databind.*;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.spaceconquest.engine.ship.VectorFlight.*;

class OrdinaryStationDockingTest {
    private Fleet fleet(GameState state) { return FleetOrganization.find(state, "hunter"); }
    private GameState day(GameState state) {
        return state.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state), state.orbitalStations(), List.of()))
                .withTurn(state.turn() + 1);
    }
    private GameState world(String target, double altitude) throws Exception {
        var state = new OrbitalRendezvousTest().world().toBuilder().solarSystems(DataModelLoader.loadSolarSystems()).build();
        var port = new OrbitalStation("port", "Port", "sol", target, "owner", OrbitalStation.OWNERSHIP_PUBLIC_STATE,
                20, List.of(), Map.of(), 100, 0, 0, 0, 100, 100, "steel", 0, true).withParkingAltitudeKm(altitude);
        var stations = new ArrayList<>(state.orbitalStations()); stations.add(port);
        var source = FleetLocation.Site.orbit("earth", target.equals("earth") ? 500 : 100000);
        return state.toBuilder().orbitalStations(stations).fleets(List.of(fleet(state).withLocation(FleetLocation.at(source)))).build();
    }
    private LocalTravel.Plan plan(GameState state) {
        var preview = OrbitalTravel.preview(state, fleet(state), FleetLocation.Site.docked("port"));
        assertNotNull(preview.plan(), preview.problem()); return preview.plan();
    }
    private GameState reload(GameState state) throws Exception {
        var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper.readValue(mapper.writeValueAsString(SaveGame.fromGameState(state, "now", 1, "now")), SaveGame.class)
                .toGameState(state.turn(), "RUNNING");
    }

    @Test void ordinaryParkingAndInterplanetaryArrivalPayForPhysicalApproachAndSurviveReload() throws Exception {
        for (String target : List.of("earth", "mars", "moon")) {
            var state = world(target, target.equals("earth") ? 500.1 : 8000); var before = fleet(state);
            var destination = FleetLocation.Site.docked("port"); var plan = plan(state);
            assertNotNull(plan.orbital().docking());
            var expected = LocalTravel.arrivalPreview(state, before, destination, plan);
            var command = new MoveFleetLocalCommand("hunter", FleetLocation.Kind.DOCKED, "port");
            assertTrue(command.validate(state)); state = reload(command.apply(state));
            while (fleet(state).location().orbitalFlight() != null && !fleet(state).location().orbitalFlight().failed()) state = day(state);
            assertNotNull(fleet(state).location().localFlight()); assertFalse(fleet(state).location().localFlight().interrupted());
            assertEquals(plan.orbital().docking().physical().vectorTrajectory().start(), fleet(state).location().localFlight().vectorFlight().position());
            assertEquals(OrbitalBody.find(state, "sol", target).frame(), OrbitalRecovery.frame(fleet(state)));
            assertFalse(fleet(state).location().isAt(destination));
            state = reload(state);
            for (int days = 0; days < Math.ceil(plan.orbital().docking().physical().days()); days++) state = day(state);
            assertTrue(fleet(state).location().isAt(destination));
            assertEquals(Math.ceil(plan.days()), state.turn());
            assertEquals(expected.ships().getFirst().currentFuelKg(), fleet(state).ships().getFirst().currentFuelKg(), .0001);
            assertEquals(expected.ships().getFirst().powerState().batteryChargeKwh(), fleet(state).ships().getFirst().powerState().batteryChargeKwh(), .0001);
        }
    }

    @Test void sameAltitudeOrdinaryDockingUsesVectorContactInsteadOfAtomicApproach() throws Exception {
        var state = world("earth", 500); var plan = plan(state); assertNull(plan.orbital());
        assertNotNull(plan.physical().vectorTrajectory().gravity());
        var destination = FleetLocation.Site.docked("port");
        state = day(state.withFleets(List.of(LocalTravel.depart(fleet(state), destination, plan))));
        assertTrue(fleet(state).location().isAt(destination));
        state = world("earth", 500);
        state = state.toBuilder().orbitalStations(state.orbitalStations().stream().map(port -> port.withParkingAltitudeKm(null)).toList())
                .fleets(List.of(fleet(state).withLocation(FleetLocation.at(FleetLocation.Site.orbit("earth"))))).build();
        plan = LocalTravel.plan(state, fleet(state), destination); assertNotNull(plan); assertNull(plan.orbital());
        assertNotNull(plan.physical().vectorTrajectory().gravity());
        assertTrue(fleet(day(state.withFleets(List.of(LocalTravel.depart(fleet(state), destination, plan))))).location().isAt(destination));
    }

    @Test void insufficientApproachStoresAndCancellationRetainCapturedCoast() throws Exception {
        var initial = world("earth", 500.1); var plan = plan(initial); var destination = FleetLocation.Site.docked("port");
        var launched = new MoveFleetLocalCommand("hunter", FleetLocation.Kind.DOCKED, "port").apply(initial);
        var approach = day(launched); var actual = fleet(approach).location().localFlight().vectorFlight();
        var canceled = new CancelOrbitalTravelCommand("owner", "hunter").apply(approach);
        assertEquals(actual.position(), fleet(canceled).location().localFlight().vectorFlight().position());
        assertEquals(actual.velocity(), fleet(canceled).location().localFlight().vectorFlight().velocity());
        assertEquals(fleet(approach).ships(), fleet(canceled).ships()); assertTrue(RecoveredParkingTravel.ready(fleet(canceled)));
        var itinerary = plan.orbital();
        var boundary = fleet(launched).withLocation(fleet(launched).location().withOrbitalFlight(new OrbitalFlight(itinerary,
                itinerary.docking().departureSeconds(), 2, OrbitalFlight.Status.CAPTURED, "Captured")));
        var stopped = OrbitalFlightProcessor.cancel(boundary);
        assertEquals(itinerary.docking().physical().vectorTrajectory().start(), stopped.location().localFlight().vectorFlight().position());
        assertEquals(boundary.ships(), stopped.ships()); assertTrue(RecoveredParkingTravel.ready(stopped));
        var ship = fleet(approach).ships().getFirst();
        var cold = ship.withPowerState(new ShipPowerState(Map.of(), "rp1", "uranium", 0, false, 1, 1, 0, 0, 0, 0, 0));
        var failed = day(approach.withFleets(List.of(fleet(approach).withShips(List.of(cold)))));
        assertFalse(fleet(failed).location().isAt(destination)); assertTrue(fleet(failed).location().localFlight().interrupted());
        var dry = new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), 100, 0, 0, ship.storedCargoKg()).withPowerState(ship.powerState());
        failed = day(approach.withFleets(List.of(fleet(approach).withShips(List.of(dry)))));
        assertFalse(fleet(failed).location().isAt(destination)); assertTrue(fleet(failed).location().localFlight().interrupted());
        var removed = day(launched.toBuilder().orbitalStations(List.of()).build());
        assertFalse(fleet(removed).location().isAt(destination)); assertTrue(RecoveredParkingTravel.ready(fleet(removed)));
        assertEquals(plan.orbital().docking().physical().vectorTrajectory().start(), fleet(removed).location().localFlight().vectorFlight().position());
    }

    @Test void splitAndMergeRetainPerShipSavedApproachBudgetsAndOlderItinerariesDefaultToNoApproach() throws Exception {
        var state = world("earth", 500.1); var original = fleet(state); var first = original.ships().getFirst();
        var second = new ShipInstance("second", first.designId(), first.ownerEntityId(), 100, 0, first.currentFuelKg(), first.storedCargoKg())
                .withPowerState(first.powerState());
        state = state.withFleets(List.of(original.withShips(List.of(first, second))));
        var plan = plan(state); state = state.withFleets(List.of(LocalTravel.depart(fleet(state), FleetLocation.Site.docked("port"), plan)));
        state = FleetOrganization.split(state, "owner", "hunter", List.of("second"), "split", "Split");
        for (var part : state.fleets()) {
            var approach = part.location().orbitalFlight().itinerary().docking().physical();
            assertEquals(Set.of(part.ships().getFirst().id()), approach.propellantKg().keySet());
            assertEquals(1, approach.propulsion().size());
        }
        state = FleetOrganization.transfer(state, "owner", "split", "hunter", List.of("second"));
        assertEquals(plan.orbital(), fleet(state).location().orbitalFlight().itinerary());
        var mapper = new ObjectMapper(); var legacy = mapper.valueToTree(plan.orbital());
        ((com.fasterxml.jackson.databind.node.ObjectNode) legacy).remove("docking");
        assertNull(mapper.treeToValue(legacy, OrbitalFlight.Itinerary.class).docking());
    }
}
