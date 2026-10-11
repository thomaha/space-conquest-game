package com.spaceconquest.control;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spaceconquest.control.command.MoveFleetLocalCommand;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.galaxy.SensorPosition;
import com.spaceconquest.engine.macrostructure.*;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.spaceconquest.engine.ship.VectorFlight.*;

class StationEphemerisTest {
    private GameState world() throws Exception {
        var state = new OrbitalRendezvousTest().world();
        var first = state.orbitalStations().getFirst().withParkingAltitudeKm(500.0).withOrbitalPhaseRadians(0);
        var last = state.orbitalStations().getLast().withParkingAltitudeKm(500.0).withOrbitalPhaseRadians(Math.PI);
        return state.toBuilder().orbitalStations(List.of(first, last))
                .fleets(List.of(state.fleets().getFirst().withLocation(FleetLocation.at(FleetLocation.Site.docked(first.id()))))).build();
    }
    @Test void distinctStationPhasesUsePaidTravelAndOnePhysicalEphemerisAfterReload() throws Exception {
        var state = world(); var before = state.fleets().getFirst(); var destination = FleetLocation.Site.docked("enemy-yard");
        var first = StationRendezvous.at(state, "owner-yard", state.turn()); var last = StationRendezvous.at(state, "enemy-yard", state.turn());
        assertTrue(norm(subtract(first.position(), last.position())) > 1e7);
        var p = SensorPosition.at(state, "sol", before.location());
        assertEquals(p, LocalSiteGeometry.position(state, "sol", before.location().current()));
        var stationPosition = SensorPosition.at(state, "sol", FleetLocation.at(destination));
        assertEquals(norm(subtract(first.position(), last.position())), norm(subtract(p, stationPosition)), .0001);
        var plan = LocalTravel.plan(state, before, destination); assertNotNull(plan);
        assertEquals(first.position(), plan.physical().vectorTrajectory().start());
        assertEquals(first.velocity(), plan.physical().vectorTrajectory().initialVelocity());
        var command = new MoveFleetLocalCommand("hunter", FleetLocation.Kind.DOCKED, "enemy-yard"); assertTrue(command.validate(state));
        var launched = command.apply(state); assertEquals(p, SensorPosition.at(launched, "sol", launched.fleets().getFirst().location()));
        var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        var restored = mapper.readValue(mapper.writeValueAsString(SaveGame.fromGameState(launched, "now", 1, "now")), SaveGame.class).toGameState(launched.turn(), "RUNNING");
        assertEquals(launched.orbitalStations(), restored.orbitalStations());
        for (int day = 0; day < Math.ceil(plan.days()); day++) restored = restored.withFleets(new FleetProcessor().processFleetMovements(
                ShipPowerProcessor.advanceDay(restored), restored.orbitalStations(), List.of())).withTurn(restored.turn() + 1);
        assertTrue(restored.fleets().getFirst().location().isAt(destination));
        assertEquals(before.ships().getFirst().currentFuelKg() - plan.propellantKg().get("owner-ship"), restored.fleets().getFirst().ships().getFirst().currentFuelKg(), .00001);
    }

    @Test void phasePersistsThroughStationCopiesAndOlderRecordsReceiveStableDistinctPhases() throws Exception {
        var state = world(); var station = state.orbitalStations().getFirst();
        assertEquals(station.orbitalPhaseRadians(), station.withParkingAltitudeKm(1000.0).orbitalPhaseRadians());
        assertEquals(station.orbitalPhaseRadians(), station.withPopulations(List.of()).orbitalPhaseRadians());
        assertEquals(station.orbitalPhaseRadians(), new MacroStructureProcessor().processOrbitalStation(station, 0).updatedStation().orbitalPhaseRadians());
        var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        var first = (ObjectNode) mapper.valueToTree(station); first.remove("orbitalPhaseRadians");
        var last = (ObjectNode) mapper.valueToTree(state.orbitalStations().getLast()); last.remove("orbitalPhaseRadians");
        var a = mapper.treeToValue(first, OrbitalStation.class); var b = mapper.treeToValue(last, OrbitalStation.class);
        assertNotEquals(a.orbitalPhaseRadians(), b.orbitalPhaseRadians());
        assertEquals(a, mapper.treeToValue(first, OrbitalStation.class));
        assertThrows(IllegalArgumentException.class, () -> station.withOrbitalPhaseRadians(Double.NaN));
    }
}
