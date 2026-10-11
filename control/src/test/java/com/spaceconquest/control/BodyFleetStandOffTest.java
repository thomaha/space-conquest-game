package com.spaceconquest.control;

import com.fasterxml.jackson.databind.*;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.combat.*;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.spaceconquest.engine.ship.VectorFlight.*;

class BodyFleetStandOffTest {
    private Fleet hunter(GameState state) { return FleetOrganization.find(state, "hunter"); }
    private Fleet target(GameState state) { return FleetOrganization.find(state, "target"); }
    private GameState world(String bodyId, boolean nativeCoast) throws Exception {
        var state = new BodyFleetRendezvousTest().world(bodyId, false);
        var armed = new FleetStandOffApproachTest().world();
        var designs = new ArrayList<>(state.shipDesigns()); designs.add(armed.shipDesigns().stream().filter(design -> design.id().equals("laser")).findFirst().orElseThrow());
        state = state.toBuilder().shipDesigns(designs).empires(armed.empires()).build();
        double altitude = bodyId.equals("earth") ? 250000 : 50000;
        var body = OrbitalBody.find(state, "sol", bodyId); var motion = StationRendezvous.at(body, altitude, state.turn());
        double angle = -.04; var p = rotate(motion.position(), angle); var v = rotate(motion.velocity(), angle);
        var gravity = target(state).location().localFlight().vectorFlight().trajectory().gravity();
        var tiny = new LocalSiteGeometry.Point(1e-30, 0, 0);
        var vector = new VectorFlight(new VectorFlight.Trajectory(p, v, tiny, tiny, 1, 0, gravity), p, v, 2);
        var geometry = new LocalSiteGeometry.Leg(p, add(p, v), norm(v));
        var coast = new LocalFlight(geometry, VectorFlight.motion(vector, geometry), Map.of(), Map.of(), true, false, null, vector);
        var hostile = target(state).withLocation(new FleetLocation(FleetLocation.Site.orbit(bodyId),
                FleetLocation.Site.orbit(bodyId, altitude), 0, 1, coast));
        if (nativeCoast) {
            var ellipse = new HohmannCoast(gravity.gravitationalParameter(), norm(p), norm(p) + 100, Math.atan2(p.yMeters(), p.xMeters()));
            var events = List.of(new OrbitalFlight.Maneuver(0, 1, Map.of()), new OrbitalFlight.Maneuver(ellipse.coastSeconds(), 1, Map.of()),
                    new OrbitalFlight.Maneuver(ellipse.coastSeconds() + 3600, 1, Map.of()));
            var itinerary = new OrbitalFlight.Itinerary(bodyId, bodyId, altitude, altitude + .1, 0, 0,
                    ellipse, ShipSolarEnvironment.DARK, events, body.frame());
            hostile = hostile.withLocation(new FleetLocation(FleetLocation.Site.orbit(bodyId, altitude), FleetLocation.Site.orbit(bodyId, altitude + .1),
                    0, itinerary.totalSeconds() / 86400, null, new OrbitalFlight(itinerary, 0, 1, OrbitalFlight.Status.MISSED, "Unfunded capture")));
        }
        return state.withFleets(List.of(hunter(state).withShips(hunter(armed).ships())
                .withLocation(FleetLocation.at(FleetLocation.Site.orbit(bodyId, altitude))), hostile));
    }
    private LocalSiteGeometry.Point rotate(LocalSiteGeometry.Point p, double angle) {
        return new LocalSiteGeometry.Point(p.xMeters() * Math.cos(angle) - p.yMeters() * Math.sin(angle),
                p.xMeters() * Math.sin(angle) + p.yMeters() * Math.cos(angle), p.zMeters());
    }
    private GameState day(GameState state) {
        return state.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state), state.orbitalStations(), List.of()))
                .withTurn(state.turn() + 1);
    }

    @Test void fundedEarthAndMoonCoastsArriveInRangeWithObservedVelocityAndPaidBraking() throws Exception {
        for (String body : List.of("earth", "moon")) for (boolean nativeCoast : List.of(false, true)) {
            var initial = world(body, nativeCoast); var plan = BodyFleetStandOff.plan(initial, hunter(initial), target(initial));
            assertNotNull(plan, body + " native=" + nativeCoast);
            assertTrue(norm(plan.physical().vectorTrajectory().lastAcceleration()) > 0);
            var quoted = LocalTravel.arrivalPreview(initial, hunter(initial), target(initial).location().destination(), plan);
            var state = FleetInterceptProcessor.advance(new InterceptFleetCommand("owner", "hunter", "target").apply(initial));
            assertEquals(plan.physical().vectorTrajectory(), hunter(state).location().localFlight().vectorFlight().trajectory());
            for (int tick = 0; tick < Math.ceil(plan.days()); tick++) state = day(state);
            double distance = ShipWeaponRange.separation(state, hunter(state), target(state));
            assertEquals(900000, distance, .1);
            var actual = hunter(state).location().localFlight().vectorFlight();
            LocalSiteGeometry.Point velocity;
            if (nativeCoast) { var p = target(state).location().orbitalFlight().position(); velocity = new LocalSiteGeometry.Point(p.vxMps(), p.vyMps(), 0); }
            else velocity = target(state).location().localFlight().vectorFlight().velocity();
            assertTrue(norm(subtract(actual.velocity(), velocity)) < .0005);
            assertEquals(quoted.ships().getFirst().currentFuelKg(), hunter(state).ships().getFirst().currentFuelKg(), .00001);
            assertTrue(hunter(state).ships().getFirst().currentFuelKg() < hunter(initial).ships().getFirst().currentFuelKg());
            assertFalse(MovingFleetRendezvous.matches(state, hunter(state), target(state)));
            assertTrue(ShipWeaponRange.reaches(state, hunter(state), target(state)));
            assertFalse(ShipWeaponRange.reaches(state, target(state), hunter(state)));
            var resolved = new TacticalFleetEncounterResolver(new TacticalCombatProcessor(new Random(42))).resolveEncounters(state);
            assertEquals("WEAPON_RANGE", resolved.fleetEngagements().getFirst().siteKind());
            assertEquals(actual.position(), hunter(resolved).location().localFlight().vectorFlight().position());
            assertEquals(actual.velocity(), hunter(resolved).location().localFlight().vectorFlight().velocity());
        }
    }

    @Test void dryTankRejectsBrakingAndCancellationReloadRetainsPaidMotion() throws Exception {
        var initial = world("moon", false); var ship = hunter(initial).ships().getFirst();
        var dry = new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), ship.currentHullHealth(), 0, 0,
                ship.storedCargoKg()).withPowerState(ship.powerState());
        var empty = initial.withFleets(List.of(hunter(initial).withShips(List.of(dry)), target(initial)));
        assertNull(BodyFleetStandOff.plan(empty, hunter(empty), target(empty)));
        var waiting = new InterceptFleetCommand("owner", "hunter", "target").apply(empty);
        assertEquals(hunter(waiting), hunter(FleetInterceptProcessor.advance(waiting)));
        var state = FleetInterceptProcessor.advance(new InterceptFleetCommand("owner", "hunter", "target").apply(initial));
        var design = state.shipDesigns().stream().filter(item -> item.id().equals(ship.designId())).findFirst().orElseThrow();
        var blind = hunter(state).ships().getFirst().withModuleDamage(new ShipModuleDamage(Map.of(
                design.equippedModuleIds().indexOf(SensorModuleCatalog.BASIC), 100.0)));
        var lost = state.withFleets(List.of(hunter(state).withShips(List.of(blind)), target(state)));
        var stopped = FleetInterceptProcessor.advance(lost);
        assertNull(hunter(stopped).interceptOrder());
        assertEquals(hunter(lost).location(), hunter(stopped).location()); assertEquals(hunter(lost).ships(), hunter(stopped).ships());
        var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        var restored = mapper.readValue(mapper.writeValueAsString(SaveGame.fromGameState(state, "now", 1, "now")), SaveGame.class).toGameState(0, "RUNNING");
        assertEquals(hunter(state), hunter(restored));
        var canceled = new CancelInterceptFleetCommand("owner", "hunter").apply(restored);
        assertEquals(hunter(state).location(), hunter(canceled).location()); assertEquals(hunter(state).ships(), hunter(canceled).ships());
        var expected = day(state); var actual = day(canceled);
        assertEquals(hunter(expected).location(), hunter(actual).location()); assertEquals(hunter(expected).ships(), hunter(actual).ships());
    }
}
