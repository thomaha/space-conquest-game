package com.spaceconquest.engine.galaxy;

import com.fasterxml.jackson.databind.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OrbitalSensorCoverageTest {
    private GameState world(long day) {
        var moon = new Moon("moon", "Moon", "", 7.35e22, 1.62, 384400, 3474, "", false, 0, List.of(), List.of());
        var earth = new Planet("earth", "Earth", "", 5.97e24, 9.81, 149600000, 0, 12742, "rock", "", false, 0,
                List.of(), List.of(moon), List.of());
        var mars = new Planet("mars", "Mars", "", 6.42e23, 3.7, 227900000, 0, 6792, "rock", "", false, 0,
                List.of(), List.of(), List.of());
        var system = new SolarSystem("sol", "Sol", "", 0, 0, 0, 1.989e30, 1392700, "yellow", List.of(earth, mars), List.of());
        var empire = new Empire("owner", "Owner", "human", "Individualist", 1000, 0, List.of("sol"), List.of(), Map.of(),
                List.of("electricity"), List.of());
        var design = new ShipDesign("sensor", "Sensor", "owner", ShipRole.EXPLORER, "steel", List.of(SensorModuleCatalog.BASIC),
                "steel", 0, 1000, 0, 100, 0, 1, 0, 0, false, false);
        return GameState.builder().turn(day).empires(List.of(empire)).solarSystems(List.of(system)).shipDesigns(List.of(design)).build();
    }
    private Fleet fleet(String id, String owner, FleetLocation location) {
        return new Fleet(id, id, owner, "sol", "", 0, 0, 0, false, "PASSIVE",
                List.of(new ShipInstance(id + "-ship", "sensor", owner, 100, 0, 50, Map.of())), location);
    }
    private OrbitalFlight.Itinerary itinerary(String source, String target, OrbitalFlight.Frame frame, HohmannCoast coast,
                                              double epoch, double wait) {
        return new OrbitalFlight.Itinerary(source, target, 500, 1000, epoch, wait, coast, ShipSolarEnvironment.DARK,
                List.of(new OrbitalFlight.Maneuver(wait, 1, Map.of()),
                        new OrbitalFlight.Maneuver(wait + coast.coastSeconds(), 1, Map.of()),
                        new OrbitalFlight.Maneuver(wait + coast.coastSeconds() + 3600, 1, Map.of())), frame);
    }
    private FleetLocation location(OrbitalFlight.Itinerary itinerary, double seconds, int cursor, OrbitalFlight.Status status) {
        return new FleetLocation(FleetLocation.Site.orbit(itinerary.sourceBodyId(), 500),
                FleetLocation.Site.orbit(itinerary.targetBodyId(), 1000), 0, itinerary.totalSeconds() / 86400, null,
                new OrbitalFlight(itinerary, seconds, cursor, status, ""));
    }
    private void near(LocalSiteGeometry.Point expected, LocalSiteGeometry.Point actual) {
        assertNotNull(actual);
        assertEquals(expected.xMeters(), actual.xMeters(), .001);
        assertEquals(expected.yMeters(), actual.yMeters(), .001);
        assertEquals(expected.zMeters(), actual.zMeters(), .001);
    }

    @Test void stellarDepartureCoastCaptureAndArrivalUseInstalledRange() {
        var initial = world(0);
        var system = initial.solarSystems().getFirst();
        var earth = system.planets().getFirst();
        var mars = system.planets().getLast();
        var comparison = CircularOrbitalEphemeris.comparison(system, earth, mars, 0, 500, 1000);
        double wait = comparison.waitDays() * 86400;
        var coast = new HohmannCoast(CircularOrbitalEphemeris.gravitationalParameter(system), earth.distance() * 1000,
                mars.distance() * 1000, CircularOrbitalEphemeris.phase(system, earth, comparison.waitDays()));
        var itinerary = itinerary("earth", "mars", new OrbitalFlight.Frame("", OrbitalFlight.CenterKind.STELLAR), coast, 0, wait);
        var observer = fleet("observer", "owner", FleetLocation.at(FleetLocation.Site.orbit("earth", 500)));
        var target = fleet("target", "enemy", location(itinerary, 0, 0, OrbitalFlight.Status.WAITING));
        var state = initial.withFleets(List.of(observer, target));
        assertTrue(SensorCoverage.detects(state, "owner", target));
        double mid = wait + coast.coastSeconds() / 2;
        target = target.withLocation(location(itinerary, mid, 1, OrbitalFlight.Status.COAST));
        state = state.toBuilder().turn((long) (mid / 86400)).fleets(List.of(observer, target)).build();
        assertFalse(SensorCoverage.detects(state, "owner", target));
        var probe = fleet("probe", "owner", target.location());
        assertTrue(SensorCoverage.detects(state.withFleets(List.of(probe, target)), "owner", target));
        double capture = wait + coast.coastSeconds();
        target = target.withLocation(location(itinerary, capture, 2, OrbitalFlight.Status.CAPTURED));
        observer = observer.withLocation(FleetLocation.at(FleetLocation.Site.orbit("mars", 1000)));
        state = state.toBuilder().turn((long) (capture / 86400)).fleets(List.of(observer, target)).build();
        assertTrue(SensorCoverage.detects(state, "owner", target));
        target = target.withLocation(FleetLocation.at(FleetLocation.Site.orbit("mars", 1000)));
        assertTrue(SensorCoverage.detects(state.withFleets(List.of(observer, target)), "owner", target));
    }

    @Test void planetaryAndLunarFramesTranslateThroughTheirCurrentBodyCenters() {
        var state = world(1234);
        var system = state.solarSystems().getFirst();
        var earth = system.planets().getFirst();
        for (String id : List.of("earth", "moon")) {
            var body = OrbitalBody.find(state, "sol", id);
            var kind = body.moon() == null ? OrbitalFlight.CenterKind.PLANETARY : OrbitalFlight.CenterKind.LUNAR;
            var coast = new HohmannCoast(PlanetaryTransferComparison.GRAVITATIONAL_CONSTANT * body.mass(),
                    (body.diameter() / 2 + 500) * 1000, (body.diameter() / 2 + 1000) * 1000, .7);
            var itinerary = itinerary(id, id, new OrbitalFlight.Frame(id, kind), coast, 1234, 0);
            var flightLocation = location(itinerary, coast.coastSeconds() / 2, 1, OrbitalFlight.Status.COAST);
            var parent = CircularOrbitalEphemeris.at(system, earth, 1234);
            double x = parent.xMeters(), y = parent.yMeters();
            if (body.moon() != null) {
                double phase = LunarTransferComparison.moonPhase(system, earth, body.moon(), 1234);
                x += body.moon().distance() * 1000 * Math.cos(phase);
                y += body.moon().distance() * 1000 * Math.sin(phase);
            }
            var relative = flightLocation.orbitalFlight().position();
            near(new LocalSiteGeometry.Point(x + relative.xMeters(), y + relative.yMeters(), 0), SensorPosition.at(state, "sol", flightLocation));
            var observer = fleet("observer", "owner", FleetLocation.at(FleetLocation.Site.orbit(id, 500)));
            var target = fleet("target", "enemy", flightLocation);
            assertTrue(SensorCoverage.detects(state.withFleets(List.of(observer, target)), "owner", target));
        }
    }

    @Test void equalRelativeCoordinatesAroundDifferentPlanetsAreNotNearbyContacts() {
        var state = world(1000);
        var coast = new HohmannCoast(4e14, 7e6, 9e6, .2);
        var first = itinerary("earth", "earth", new OrbitalFlight.Frame("earth", OrbitalFlight.CenterKind.PLANETARY), coast, 1000, 0);
        var second = itinerary("mars", "mars", new OrbitalFlight.Frame("mars", OrbitalFlight.CenterKind.PLANETARY), coast, 1000, 0);
        var observer = fleet("observer", "owner", location(first, 100, 1, OrbitalFlight.Status.COAST));
        var target = fleet("target", "enemy", location(second, 100, 1, OrbitalFlight.Status.COAST));
        assertEquals(observer.location().orbitalFlight().position(), target.location().orbitalFlight().position());
        assertFalse(SensorCoverage.detects(state.withFleets(List.of(observer, target)), "owner", target));
        var missing = itinerary("unknown", "unknown", new OrbitalFlight.Frame("unknown", OrbitalFlight.CenterKind.PLANETARY), coast, 1000, 0);
        assertNull(SensorPosition.at(state, "sol", location(missing, 100, 1, OrbitalFlight.Status.COAST)));
    }

    @Test void crossFrameContactsUseSnapshotEpochAndSurviveSaveLoadAndFailedCapture() throws Exception {
        var state = world(1234);
        var coast = new HohmannCoast(4e14, 7e6, 9e6, .2);
        var itinerary = itinerary("earth", "earth", new OrbitalFlight.Frame("earth", OrbitalFlight.CenterKind.PLANETARY), coast, 1234, 0);
        var target = fleet("target", "enemy", location(itinerary, coast.coastSeconds() + 100, 1, OrbitalFlight.Status.MISSED));
        var expected = SensorPosition.at(state, "sol", target.location());
        var stellar = new HohmannCoast(1e20, Math.hypot(expected.xMeters(), expected.yMeters()), 3e11,
                Math.atan2(expected.yMeters(), expected.xMeters()));
        var observerPlan = itinerary("earth", "mars", new OrbitalFlight.Frame("", OrbitalFlight.CenterKind.STELLAR), stellar, 1234, 0);
        var observer = fleet("observer", "owner", location(observerPlan, 0, 1, OrbitalFlight.Status.COAST));
        state = state.withFleets(List.of(observer, target));
        assertTrue(SensorCoverage.detects(state, "owner", target));
        assertFalse(SensorCoverage.detects(state.toBuilder().turn(0).build(), "owner", target));
        assertEquals(List.of("target"), new SensorProcessor().updateSensorCoverage(state, List.of()).getFirst().detectedFleetIds());
        var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        var save = SaveGame.fromGameState(state, "now", 1, "now");
        var loaded = mapper.readValue(mapper.writeValueAsString(save), SaveGame.class).toGameState(1234, "RUNNING");
        assertTrue(SensorCoverage.detects(loaded, "owner", loaded.fleets().getLast()));
        near(expected, SensorPosition.at(loaded, "sol", loaded.fleets().getLast().location()));
    }
}
