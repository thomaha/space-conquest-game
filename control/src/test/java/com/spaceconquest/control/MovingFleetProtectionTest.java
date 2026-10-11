package com.spaceconquest.control;

import com.fasterxml.jackson.databind.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.galaxy.SensorPosition;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.spaceconquest.engine.ship.VectorFlight.*;
import static org.junit.jupiter.api.Assertions.*;

class MovingFleetProtectionTest {
    private Fleet convoy(GameState state) { return FleetOrganization.find(state, "hunter"); }
    private GameState replace(GameState state, Fleet fleet) {
        return state.withFleets(state.fleets().stream().map(old -> old.id().equals(fleet.id()) ? fleet : old).toList());
    }
    private ShipInstance copy(ShipInstance ship, String id, double fuel) {
        return new ShipInstance(id, ship.designId(), ship.ownerEntityId(), ship.currentHullHealth(), ship.currentShieldHealth(),
                fuel, ship.storedCargoKg()).withPowerState(ship.powerState());
    }
    GameState world(String bodyId) throws Exception {
        var state = new OrbitalRendezvousTest().world(); var body = OrbitalBody.find(state, "sol", bodyId);
        var motion = StationRendezvous.at(body, 500, state.turn());
        var gravity = new TwoBodyFlight.Gravity(PlanetaryTransferComparison.GRAVITATIONAL_CONSTANT * body.mass(),
                body.diameter() * 500, body.frame(), body.sphereKm(state.solarSystems().getFirst()) * 1000);
        var tiny = new LocalSiteGeometry.Point(1e-30, 0, 0);
        var vector = new VectorFlight(new VectorFlight.Trajectory(motion.position(), motion.velocity(), tiny, tiny, 1, 0, gravity),
                motion.position(), motion.velocity(), 2);
        var geometry = new LocalSiteGeometry.Leg(motion.position(), add(motion.position(), motion.velocity()), norm(motion.velocity()));
        var local = new LocalFlight(geometry, VectorFlight.motion(vector, geometry), Map.of(), Map.of(), true, false, null, vector);
        var hunter = convoy(state); var ship = hunter.ships().getFirst();
        hunter = hunter.withShips(List.of(ship, copy(ship, "escort", ship.currentFuelKg())))
                .withEscortOrder(new FleetEscortOrder("owner", "escort-fleet", "Escort", "PASSIVE", List.of("escort")))
                .withLocation(new FleetLocation(FleetLocation.Site.orbit(bodyId), FleetLocation.Site.orbit(bodyId, 500), 0, 1, local));
        var enemy = FleetOrganization.find(state, "target"); var enemyShips = new ArrayList<ShipInstance>();
        for (int i = 0; i < 20; i++) enemyShips.add(copy(enemy.ships().getFirst(), "enemy-" + i, enemy.ships().getFirst().currentFuelKg()));
        enemy = enemy.withShips(enemyShips).withLocation(FleetLocation.at(FleetLocation.Site.orbit(bodyId, 1000)));
        state = state.withFleets(List.of(hunter, enemy));
        var firstLeg = MovingFleetEscape.plan(state, hunter, FleetLocation.Site.orbit(bodyId, 1000));
        assertNotNull(firstLeg);
        hunter = MovingFleetEscape.depart(hunter, FleetLocation.Site.orbit(bodyId, 1000), firstLeg);
        hunter = LocalFlightProcessor.advance(hunter, firstLeg.physical().vectorTrajectory().burnSeconds() / 7200, false);
        // Put the hostile convoy at the observed moving position so its cannons present a current threat.
        var paid = hunter.location().localFlight();
        var hostileCoast = new LocalFlight(paid.geometry(), paid.motion(), Map.of(), Map.of(), true, false, null, paid.vectorFlight());
        enemy = enemy.withLocation(hunter.location().withFlight(hostileCoast));
        return replace(replace(state, hunter), enemy);
    }
    private GameState day(GameState state) {
        return state.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state), state.orbitalStations(), List.of()))
                .withTurn(state.turn() + 1);
    }

    @Test void paidBodyCourseChangesStartAtActualMotionAndRetainConvoyThroughReload() throws Exception {
        for (String id : List.of("earth", "moon")) {
            var state = world(id); var fleet = convoy(state); var old = fleet.location().localFlight().vectorFlight();
            var decision = FleetProtectionProcessor.decide(state, fleet, List.of());
            assertEquals(FleetProtectionProcessor.Outcome.ESCAPING, decision.outcome(), id);
            var changed = FleetProtectionProcessor.advance(state, List.of()); var actual = convoy(changed).location().localFlight().vectorFlight();
            assertEquals(old.position(), actual.position()); assertEquals(old.velocity(), actual.velocity());
            assertEquals(fleet.escortOrder(), convoy(changed).escortOrder());
            assertEquals(fleet.ships().getFirst().currentFuelKg(), convoy(changed).ships().getFirst().currentFuelKg());
            for (var ship : fleet.ships()) {
                var feed = decision.plans().get(fleet.id()).reactorFuelKg().get(ship.id());
                if (feed != null) assertEquals(ship.storedCargoKg().get(feed.materialId()) - feed.quantityKg(),
                        convoy(changed).ships().stream().filter(item -> item.id().equals(ship.id())).findFirst().orElseThrow()
                                .storedCargoKg().get(feed.materialId()), .000001);
            }
            assertNotEquals(old.trajectory(), actual.trajectory());
            var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            var restored = mapper.readValue(mapper.writeValueAsString(SaveGame.fromGameState(changed, "now", 1, "now")), SaveGame.class)
                    .toGameState(changed.turn(), "RUNNING");
            assertEquals(changed.fleets(), restored.fleets());
            var quote = LocalTravel.arrivalPreview(state, fleet, decision.destination(), decision.plans().get(fleet.id()));
            var arrived = day(restored);
            assertTrue(convoy(arrived).ships().getFirst().currentFuelKg() < fleet.ships().getFirst().currentFuelKg());
            assertEquals(quote.ships().getFirst().currentFuelKg(), convoy(arrived).ships().getFirst().currentFuelKg(), .00001);
            assertEquals(fleet.escortOrder(), convoy(arrived).escortOrder());
        }
    }

    @Test void noFeasibleCourseChangeLeavesOriginalThrustMotionAndSpentStoresIntact() throws Exception {
        var state = world("earth"); var fleet = convoy(state);
        var dry = fleet.withShips(fleet.ships().stream().map(ship -> copy(ship, ship.id(),
                ship.id().equals("escort") ? 0 : ship.currentFuelKg())).toList());
        state = replace(state, dry);
        var decision = FleetProtectionProcessor.decide(state, dry, List.of());
        assertEquals(FleetProtectionProcessor.Outcome.ESCAPE_BLOCKED, decision.outcome());
        assertEquals(state, FleetProtectionProcessor.advance(state, List.of()));
        assertFalse(convoy(state).location().localFlight().interrupted());
    }

    @Test void separationAndVelocityExcludeFleetsSharingOnlyAnOldDepartureSite() throws Exception {
        var state = world("moon"); var fleet = convoy(state); var ship = fleet.ships().getFirst();
        var support = new Fleet("support", "Support", "owner", "sol", "", 0, 0, 0, false, "PASSIVE",
                List.of(copy(ship, "support-ship", ship.currentFuelKg())), fleet.location());
        var close = state.withFleets(List.of(fleet, FleetOrganization.find(state, "target"), support));
        assertEquals(Set.of("hunter", "support"), new HashSet<>(FleetThreatAssessment.assess(close, fleet).friendlyFleetIds()));
        var local = support.location().localFlight(); var old = local.vectorFlight();
        var shifted = new VectorFlight(old.trajectory(), add(old.position(), new LocalSiteGeometry.Point(100, 0, 0)), old.velocity(), old.elapsedSeconds());
        var flight = new LocalFlight(local.geometry(), VectorFlight.motion(shifted, local.geometry()), local.propellantKg(),
                local.propulsion(), local.interrupted(), false, null, shifted);
        support = support.withLocation(support.location().withFlight(flight));
        var apart = state.withFleets(List.of(fleet, FleetOrganization.find(state, "target"), support));
        assertEquals(List.of("hunter"), FleetThreatAssessment.assess(apart, fleet).friendlyFleetIds());
    }

    @Test void saferCommittedRouteContinuesAndLostContactDoesNotRedirect() throws Exception {
        var state = world("earth"); var fleet = convoy(state);
        var enemy = FleetOrganization.find(state, "target");
        state = replace(state, enemy);
        var plan = MovingFleetEscape.plan(state, fleet, FleetLocation.Site.orbit("earth", 8000)); assertNotNull(plan);
        fleet = MovingFleetEscape.depart(fleet, FleetLocation.Site.orbit("earth", 8000), plan);
        state = replace(state, fleet);
        assertEquals(FleetProtectionProcessor.Outcome.CONTINUE_TO_SAFETY, FleetProtectionProcessor.decide(state, fleet, List.of()).outcome());
        assertEquals(state, FleetProtectionProcessor.advance(state, List.of()));
        var peace = state.toBuilder().diplomaticRelations(List.of()).build();
        assertEquals(FleetProtectionProcessor.Outcome.NO_CONTACT, FleetProtectionProcessor.decide(peace, fleet, List.of()).outcome());
        assertEquals(peace, FleetProtectionProcessor.advance(peace, List.of()));
    }

    @Test void nativeBodyCoastRedirectUsesObservedEllipsePositionAndVelocity() throws Exception {
        var state = world("earth"); var fleet = convoy(state); var body = OrbitalBody.find(state, "sol", "earth");
        var p = StationRendezvous.at(body, 500, state.turn());
        var ellipse = new HohmannCoast(PlanetaryTransferComparison.GRAVITATIONAL_CONSTANT * body.mass(),
                norm(p.position()), norm(p.position()) + 500_000, Math.atan2(p.position().yMeters(), p.position().xMeters()));
        var events = List.of(new OrbitalFlight.Maneuver(0, 1, Map.of()), new OrbitalFlight.Maneuver(ellipse.coastSeconds(), 1, Map.of()),
                new OrbitalFlight.Maneuver(ellipse.coastSeconds() + 3600, 1, Map.of()));
        var itinerary = new OrbitalFlight.Itinerary("earth", "earth", 500, 1000, 0, 0, ellipse, ShipSolarEnvironment.DARK, events, body.frame());
        var nativeCoast = new OrbitalFlight(itinerary, 300, 1, OrbitalFlight.Status.COAST, "Coasting");
        fleet = fleet.withLocation(new FleetLocation(FleetLocation.Site.orbit("earth", 500), FleetLocation.Site.orbit("earth", 1000),
                0, itinerary.totalSeconds() / 86400, null, nativeCoast));
        state = replace(state, fleet);
        var plan = MovingFleetEscape.plan(state, fleet, FleetLocation.Site.orbit("earth", 8000)); assertNotNull(plan);
        var changed = MovingFleetEscape.depart(fleet, FleetLocation.Site.orbit("earth", 8000), plan);
        var actual = changed.location().localFlight().vectorFlight(); var observed = nativeCoast.position();
        assertEquals(observed.xMeters(), actual.position().xMeters()); assertEquals(observed.yMeters(), actual.position().yMeters());
        assertEquals(observed.vxMps(), actual.velocity().xMeters()); assertEquals(observed.vyMps(), actual.velocity().yMeters());
        assertEquals(fleet.escortOrder(), changed.escortOrder());
        assertEquals(fleet.ships().getFirst().currentFuelKg(), changed.ships().getFirst().currentFuelKg());
    }

    @Test void gravityFreeMovingConvoyCanFundEscapeToOwnedDeepSpacePort() throws Exception {
        var state = world("earth"); var fleet = convoy(state);
        var port = new OrbitalStation("refuge", "Refuge", "sol", "", "owner", OrbitalStation.OWNERSHIP_PUBLIC_STATE,
                10, List.of(), Map.of(), 100, 1, 0, 0, 100, 100, "steel", 0, true);
        state = state.toBuilder().orbitalStations(List.of(port)).build();
        var site = FleetLocation.Site.deepSpace(); var center = SensorPosition.at(state, "sol", FleetLocation.at(site));
        var refuge = SensorPosition.at(state, "sol", FleetLocation.at(FleetLocation.Site.docked("refuge")));
        var offset = subtract(refuge, center); var start = subtract(center, scale(offset, 2_000_000 / norm(offset)));
        var velocity = new LocalSiteGeometry.Point(100, 50, 0); var tiny = new LocalSiteGeometry.Point(1e-30, 0, 0);
        var trajectory = new VectorFlight.Trajectory(start, velocity, tiny, tiny, 300, 3600);
        var vector = trajectory.at(0); var geometry = new LocalSiteGeometry.Leg(start, add(start, scale(velocity, 4200)), norm(velocity) * 4200);
        var flight = new LocalFlight(geometry, VectorFlight.motion(vector, geometry), Map.of(), Map.of(), false, false, null, vector);
        fleet = fleet.withLocation(new FleetLocation(site, FleetLocation.Site.deepSpace("old-destination"), 0, 1, flight));
        var still = new LocalSiteGeometry.Point(0, 0, 0);
        var enemyVector = new VectorFlight(new VectorFlight.Trajectory(start, still, tiny, tiny, 1, 0), start, still, 2);
        var enemyFlight = new LocalFlight(geometry, VectorFlight.motion(enemyVector, geometry), Map.of(), Map.of(), true, false, null, enemyVector);
        var enemy = FleetOrganization.find(state, "target").withLocation(new FleetLocation(site, FleetLocation.Site.deepSpace("enemy-drift"), 0, 1, enemyFlight));
        state = state.withFleets(List.of(fleet, enemy));
        var decision = FleetProtectionProcessor.decide(state, fleet, List.of());
        assertEquals(FleetProtectionProcessor.Outcome.ESCAPING, decision.outcome());
        var changed = convoy(FleetProtectionProcessor.advance(state, List.of())); var actual = changed.location().localFlight().vectorFlight();
        assertEquals(0, norm(subtract(start, actual.position())), .000001);
        assertEquals(0, norm(subtract(velocity, actual.velocity())), .000001);
        assertNull(actual.trajectory().gravity()); assertEquals(fleet.escortOrder(), changed.escortOrder());
        var lostRefuge = state.withFleets(List.of(changed, enemy)).toBuilder().orbitalStations(List.of()).build();
        assertEquals(FleetProtectionProcessor.Outcome.ESCAPE_BLOCKED, FleetProtectionProcessor.decide(lostRefuge, changed, List.of()).outcome());
        assertEquals(lostRefuge, FleetProtectionProcessor.advance(lostRefuge, List.of()));
        var arrived = day(state.withFleets(List.of(changed, enemy)));
        assertTrue(convoy(arrived).location().isAt(FleetLocation.Site.docked("refuge")));
        assertTrue(convoy(arrived).ships().getFirst().currentFuelKg() < fleet.ships().getFirst().currentFuelKg());
    }
}
