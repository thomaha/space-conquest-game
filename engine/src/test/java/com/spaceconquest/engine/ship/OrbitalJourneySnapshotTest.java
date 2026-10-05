package com.spaceconquest.engine.ship;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class OrbitalJourneySnapshotTest {
    private OrbitalFlight.Itinerary itinerary(boolean outward) {
        var coast = new HohmannCoast(1e20, outward ? 1e11 : 2e11, outward ? 2e11 : 1e11, .7);
        double wait = 10 * 86400;
        return new OrbitalFlight.Itinerary("source", "target", 100000, 8000, 123, wait, coast, ShipSolarEnvironment.DARK,
                List.of(new OrbitalFlight.Maneuver(wait, 1, Map.of()),
                        new OrbitalFlight.Maneuver(wait + coast.coastSeconds(), 1, Map.of()),
                        new OrbitalFlight.Maneuver(wait + coast.coastSeconds() + 3600, 1, Map.of())));
    }

    private void samePosition(CircularOrbitalEphemeris.State expected, CircularOrbitalEphemeris.State actual) {
        assertEquals(expected.xMeters(), actual.xMeters(), .001);
        assertEquals(expected.yMeters(), actual.yMeters(), .001);
    }

    @Test void waitingTracksMovingBodiesWithoutChangingTheFrozenTransferOrCompletingEvents() {
        var itinerary = itinerary(true);
        var initial = OrbitalJourneySnapshot.from(new OrbitalFlight(itinerary, 0, 0, OrbitalFlight.Status.WAITING, "Waiting"));
        var laterFlight = new OrbitalFlight(itinerary, 86400, 0, OrbitalFlight.Status.WAITING, "Waiting");
        var later = OrbitalJourneySnapshot.from(laterFlight);
        assertNotEquals(initial.source(), later.source());
        assertNotEquals(initial.target(), later.target());
        assertEquals(initial.transferArc(), later.transferArc());
        samePosition(later.source(), later.ship()); samePosition(laterFlight.position(), later.ship());
        assertEquals(9, later.maneuvers().getFirst().daysUntil());
        assertTrue(later.maneuvers().stream().noneMatch(OrbitalJourneySnapshot.Maneuver::completed));
        assertThrows(UnsupportedOperationException.class, () -> later.transferArc().clear());
        assertThrows(UnsupportedOperationException.class, () -> later.maneuvers().clear());
    }

    @Test void inwardAndOutwardCaptureMeetTheMovingTargetAndStillLeaveApproachUnexecuted() {
        for (boolean outward : List.of(true, false)) {
            var itinerary = itinerary(outward);
            var capture = new OrbitalFlight(itinerary, itinerary.waitSeconds() + itinerary.coast().coastSeconds(),
                    2, OrbitalFlight.Status.CAPTURED, "Captured");
            var snapshot = OrbitalJourneySnapshot.from(capture);
            samePosition(itinerary.coast().at(itinerary.coast().coastSeconds()), snapshot.target());
            samePosition(snapshot.target(), snapshot.ship());
            assertTrue(snapshot.maneuvers().getFirst().completed());
            assertTrue(snapshot.maneuvers().get(1).completed());
            assertFalse(snapshot.maneuvers().getLast().completed());
            assertEquals(1.0 / 24, snapshot.maneuvers().getLast().daysUntil(), 1e-9);
            var arcEnd = snapshot.transferArc().getLast();
            assertEquals(snapshot.target().xMeters(), arcEnd.xMeters(), .001);
            assertEquals(snapshot.target().yMeters(), arcEnd.yMeters(), .001);
        }
    }

    @Test void missedCaptureShowsActualBallisticPositionAndNoCompletedArrival() {
        var itinerary = itinerary(true);
        var failed = new OrbitalFlight(itinerary, itinerary.waitSeconds() + itinerary.coast().coastSeconds() + 5 * 86400,
                1, OrbitalFlight.Status.MISSED, "Missing capture fuel");
        var snapshot = OrbitalJourneySnapshot.from(failed);
        assertEquals(itinerary.coast().at(itinerary.coast().coastSeconds() + 5 * 86400), snapshot.ship());
        assertNotEquals(snapshot.target(), snapshot.ship());
        assertTrue(snapshot.maneuvers().getFirst().completed());
        assertTrue(snapshot.maneuvers().get(1).abandoned());
        assertTrue(snapshot.maneuvers().getLast().abandoned());
        assertFalse(snapshot.maneuvers().getLast().completed());
        assertEquals(129, snapshot.ballisticOrbit().size());
        var first = snapshot.ballisticOrbit().getFirst(); var last = snapshot.ballisticOrbit().getLast();
        assertEquals(first.xMeters(), last.xMeters(), .001);
        assertEquals(first.yMeters(), last.yMeters(), .001);
    }
}
