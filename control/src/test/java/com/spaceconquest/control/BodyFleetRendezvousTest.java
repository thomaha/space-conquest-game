package com.spaceconquest.control;

import com.fasterxml.jackson.databind.*;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.combat.*;
import com.spaceconquest.engine.galaxy.SensorCoverage;
import com.spaceconquest.engine.governance.*;
import com.spaceconquest.engine.macrostructure.*;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.spaceconquest.engine.ship.VectorFlight.*;

class BodyFleetRendezvousTest {
    private Fleet hunter(GameState state) { return FleetOrganization.find(state, "hunter"); }
    private Fleet target(GameState state) { return FleetOrganization.find(state, "target"); }
    private GameState replace(GameState state, Fleet changed) {
        return state.withFleets(state.fleets().stream().map(fleet -> fleet.id().equals(changed.id()) ? changed : fleet).toList());
    }
    GameState world(String id, boolean nativeCoast) throws Exception {
        return world(id, nativeCoast, -.05);
    }
    GameState world(String id, boolean nativeCoast, double angle) throws Exception {
        var state = new OrbitalRendezvousTest().world(); var body = OrbitalBody.find(state, "sol", id);
        var system = state.solarSystems().getFirst();
        double mu = PlanetaryTransferComparison.GRAVITATIONAL_CONSTANT * body.mass();
        var gravity = new TwoBodyFlight.Gravity(mu, body.diameter() * 500, body.frame(), body.sphereKm(system) * 1000);
        var station = StationRendezvous.at(body, 500, state.turn());
        var p = rotate(station.position(), angle); var v = rotate(station.velocity(), angle);
        var tiny = new LocalSiteGeometry.Point(1e-30, 0, 0);
        var vector = new VectorFlight(new VectorFlight.Trajectory(p, v, tiny, tiny, 1, 0, gravity), p, v, 2);
        var geometry = new LocalSiteGeometry.Leg(p, add(p, v), norm(v));
        var coast = new LocalFlight(geometry, VectorFlight.motion(vector, geometry), Map.of(), Map.of(), true, false, null, vector);
        var observed = target(state).withLocation(new FleetLocation(FleetLocation.Site.orbit(id), FleetLocation.Site.orbit(id, 500), 0, 1).withFlight(coast));
        if (nativeCoast) {
            var ellipse = new HohmannCoast(mu, norm(p), norm(p) + 100, Math.atan2(p.yMeters(), p.xMeters()));
            var events = List.of(new OrbitalFlight.Maneuver(0, 1, Map.of()), new OrbitalFlight.Maneuver(ellipse.coastSeconds(), 1, Map.of()),
                    new OrbitalFlight.Maneuver(ellipse.coastSeconds() + 3600, 1, Map.of()));
            var itinerary = new OrbitalFlight.Itinerary(id, id, 500, 500.1, 0, 0, ellipse, ShipSolarEnvironment.DARK, events, body.frame());
            observed = observed.withLocation(new FleetLocation(FleetLocation.Site.orbit(id, 500), FleetLocation.Site.orbit(id, 500.1),
                    0, itinerary.totalSeconds() / 86400, null, new OrbitalFlight(itinerary, 0, 1, OrbitalFlight.Status.MISSED, "Unfunded capture")));
        }
        return state.withFleets(List.of(hunter(state).withLocation(FleetLocation.at(FleetLocation.Site.orbit(id, 500))), observed));
    }
    private LocalSiteGeometry.Point rotate(LocalSiteGeometry.Point p, double angle) {
        return new LocalSiteGeometry.Point(p.xMeters() * Math.cos(angle) - p.yMeters() * Math.sin(angle),
                p.xMeters() * Math.sin(angle) + p.yMeters() * Math.cos(angle), p.zMeters());
    }
    private GameState assigned(GameState state) { return new InterceptFleetCommand("owner", "hunter", "target").apply(state); }
    private GameState day(GameState state) {
        return state.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state), state.orbitalStations(), List.of()))
                .withTurn(state.turn() + 1);
    }
    private TacticalFleetEncounterResolver resolver() { return new TacticalFleetEncounterResolver(new TacticalCombatProcessor(new Random(42))); }

    @Test void paidEarthAndMoonVectorAndNativeCoastsMeetAtCoordinatedTickAfterReload() throws Exception {
        for (String id : List.of("earth", "moon")) for (boolean nativeCoast : List.of(false, true)) {
            var state = assigned(world(id, nativeCoast)); assertNotNull(hunter(state).interceptOrder());
            var plan = BodyFleetRendezvous.plan(state, hunter(state), target(state)); assertNotNull(plan, id + " native=" + nativeCoast);
            var expected = LocalTravel.arrivalPreview(state, hunter(state), target(state).location().destination(), plan);
            var launched = FleetInterceptProcessor.advance(state); var flight = hunter(launched).location().localFlight(); assertNotNull(flight);
            assertEquals("target", flight.rendezvousTargetId()); assertEquals(id, flight.vectorFlight().trajectory().gravity().frame().centerBodyId());
            assertEquals(hunter(state).ships().getFirst().currentFuelKg(), hunter(launched).ships().getFirst().currentFuelKg());
            assertTrue(resolver().resolveEncounters(launched).fleetEngagements().isEmpty());
            var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            var restored = mapper.readValue(mapper.writeValueAsString(SaveGame.fromGameState(launched, "now", 1, "now")), SaveGame.class).toGameState(launched.turn(), "RUNNING");
            assertEquals(hunter(launched), hunter(restored));
            var actual = day(restored); assertTrue(MovingFleetRendezvous.matches(actual, hunter(actual), target(actual)), id);
            assertEquals(expected.ships().getFirst().currentFuelKg(), hunter(actual).ships().getFirst().currentFuelKg(), .00001);
            assertEquals(expected.ships().getFirst().powerState().batteryChargeKwh(), hunter(actual).ships().getFirst().powerState().batteryChargeKwh(), .00001);
            var reversed = day(launched.withFleets(List.of(target(launched), hunter(launched))));
            assertEquals(hunter(actual).location(), hunter(reversed).location());
            assertEquals(1, resolver().resolveEncounters(actual).fleetEngagements().size());
        }
    }

    @Test void interruptedHunterStartsFromActualMotionAndThrustCancellationStopsOnwardPursuit() throws Exception {
        var state = world("moon", false); var body = OrbitalBody.find(state, "sol", "moon");
        var actual = StationRendezvous.at(body, 500, state.turn());
        var old = target(state).location().localFlight(); var gravity = old.vectorFlight().trajectory().gravity(); var tiny = new LocalSiteGeometry.Point(1e-30, 0, 0);
        var velocity = add(actual.velocity(), new LocalSiteGeometry.Point(.01, 0, 0));
        var vector = new VectorFlight(new VectorFlight.Trajectory(actual.position(), velocity, tiny, tiny, 1, 0, gravity), actual.position(), velocity, 2);
        var flight = new LocalFlight(old.geometry(), VectorFlight.motion(vector, old.geometry()), Map.of(), Map.of(), true, false, null, vector);
        state = replace(state, hunter(state).withLocation(target(state).location().withFlight(flight)));
        assertTrue(new InterceptFleetCommand("owner", "hunter", "target").validate(state));
        var launched = FleetInterceptProcessor.advance(assigned(state)); var start = hunter(launched).location().localFlight().vectorFlight();
        assertEquals(vector.position(), start.position()); assertEquals(vector.velocity(), start.velocity());
        var burned = LocalFlightProcessor.advance(hunter(launched), start.trajectory().burnSeconds() / 3600, false);
        var canceled = new CancelOrbitalTravelCommand("owner", "hunter").apply(replace(launched, burned));
        assertNull(hunter(canceled).interceptOrder()); assertTrue(hunter(canceled).location().localFlight().interrupted());
        assertEquals(burned.ships(), hunter(canceled).ships()); assertEquals(burned.location().localFlight().vectorFlight(), hunter(canceled).location().localFlight().vectorFlight());
        assertEquals(hunter(canceled), hunter(FleetInterceptProcessor.advance(canceled)));
        assertTrue(resolver().resolveEncounters(day(canceled)).fleetEngagements().isEmpty());
    }

    @Test void differentFramesShortPoweredTargetsEmptyFuelAndElectricalFailureCannotAwardContact() throws Exception {
        var state = assigned(world("earth", true));
        var moon = target(world("moon", false)); var different = replace(state, moon);
        assertNull(BodyFleetRendezvous.plan(different, hunter(different), target(different)));
        assertEquals(hunter(different), hunter(FleetInterceptProcessor.advance(different)));
        var target = target(state); var orbital = target.location().orbitalFlight();
        var powered = replace(state, target.withLocation(target.location().withOrbitalFlight(new OrbitalFlight(orbital.itinerary(), 0, 1, OrbitalFlight.Status.COAST, "Capture soon"))));
        assertNull(BodyFleetRendezvous.plan(powered, hunter(powered), target(powered)));
        var ship = hunter(state).ships().getFirst();
        var dry = replace(state, hunter(state).withShips(List.of(new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), 100, 0, 0, ship.storedCargoKg()).withPowerState(ship.powerState()))));
        assertEquals(hunter(dry), hunter(FleetInterceptProcessor.advance(dry)));
        var launched = FleetInterceptProcessor.advance(state);
        var cold = hunter(launched).ships().getFirst().withPowerState(new ShipPowerState(Map.of(), "rp1", "uranium", 0, false, 1, 1, 0, 0, 0, 0, 0));
        var failed = day(replace(launched, hunter(launched).withShips(List.of(cold))));
        assertTrue(hunter(failed).location().localFlight().interrupted());
        assertTrue(resolver().resolveEncounters(failed).fleetEngagements().isEmpty());
    }

    @Test void corporateCoverageFundsPursuitAndLossOfGrantOrHardwarePreventsCombatWithoutRefund() throws Exception {
        var state = world("moon", false);
        var modules = ShipComponentCatalog.workbenchModules("mod_fusion_drive", false, ShipComponentCatalog.FISSION_REACTOR_ID);
        var evaluated = ShipBlueprintFactory.evaluate(state, new ShipDesignSpecification("sensorless", "Sensorless", "owner",
                ShipRole.COMBAT_SHIP, "steel", modules, "steel", .5));
        assertTrue(evaluated.valid(), evaluated.errors().toString());
        var old = hunter(state).ships().getFirst();
        var ship = new ShipInstance(old.id(), evaluated.design().id(), "owner", 100, 0,
                evaluated.design().fuelCapacityKg(), old.storedCargoKg()).withPowerState(old.powerState());
        var designs = new ArrayList<>(state.shipDesigns()); designs.add(evaluated.design());
        state = replace(state.withShipDesigns(designs), hunter(state).withShips(List.of(ship)));
        assertFalse(SensorCoverage.detects(state, "owner", target(state)));
        var company = new Corporation("global", "Global", "", "earth", "TRANSPORT", 1000, List.of(), List.of(), List.of());
        var sensor = new StationModule("sensor", "Sensor", SensorModuleCatalog.BASIC, 1, 100, 1, 0, Map.of(), "engineer", 0, true);
        var station = new OrbitalStation("observer", "Observer", "sol", "moon", "global", OrbitalStation.OWNERSHIP_PRIVATE_CORPORATE,
                10, List.of(sensor), Map.of(), 100, 1, 0, 0, 100, 100, "steel", 0, true).withParkingAltitudeKm(500.0);
        state = state.toBuilder().corporations(List.of(company)).orbitalStations(List.of(station)).build();
        assertFalse(new InterceptFleetCommand("owner", "hunter", "target").validate(state));
        state = new ProposeDiplomaticPactCommand("global", "owner", DiplomaticPact.SENSOR_DATA_SHARING).apply(state);
        var proposal = state.diplomaticProposals().getLast();
        state = new ResolveDiplomaticProposalCommand(proposal.id(), "owner", true).apply(state);
        assertTrue(SensorCoverage.detects(state, "owner", target(state)));
        var launched = FleetInterceptProcessor.advance(assigned(state));
        assertNotNull(hunter(launched).location().localFlight());
        var coveredArrival = day(launched);
        assertTrue(MovingFleetRendezvous.matches(coveredArrival, hunter(coveredArrival), target(coveredArrival)));
        for (var lost : List.of(new RevokeSensorSharingCommand(proposal.id(), "owner").apply(launched),
                launched.toBuilder().orbitalStations(List.of()).build())) {
            var cleared = FleetInterceptProcessor.advance(lost);
            assertNull(hunter(cleared).interceptOrder());
            assertEquals(hunter(launched).location(), hunter(cleared).location());
            assertEquals(hunter(launched).ships(), hunter(cleared).ships());
            var arrived = day(cleared);
            assertTrue(hunter(arrived).ships().getFirst().currentFuelKg() < ship.currentFuelKg());
            assertFalse(SensorCoverage.detects(arrived, "owner", target(arrived)));
            assertFalse(resolver().resolveEncounters(arrived).fleetEngagements().isEmpty(), "The enemy's own sensors still authorize its fire.");
        }
    }

    @Test void changedActualVelocityPreventsRendezvousButNotInRangeCombat() throws Exception {
        var state = day(FleetInterceptProcessor.advance(assigned(world("earth", false))));
        assertTrue(MovingFleetRendezvous.matches(state, hunter(state), target(state)));
        var flight = target(state).location().localFlight(); var v = flight.vectorFlight();
        var changed = new VectorFlight(v.trajectory(), v.position(), add(v.velocity(), new LocalSiteGeometry.Point(.01, 0, 0)), v.elapsedSeconds());
        var motion = new LocalFlight(flight.geometry(), VectorFlight.motion(changed, flight.geometry()), flight.propellantKg(),
                flight.propulsion(), true, false, null, changed);
        state = replace(state, target(state).withLocation(target(state).location().withFlight(motion)));
        assertFalse(MovingFleetRendezvous.matches(state, hunter(state), target(state)));
        assertTrue(ShipWeaponRange.reaches(state, hunter(state), target(state)));
        assertFalse(resolver().resolveEncounters(state).fleetEngagements().isEmpty());
    }

    @Test void activeVectorCoastCanBeMetBeforeItsNextBurn() throws Exception {
        var state = world("moon", false); var old = target(state).location().localFlight(); var v = old.vectorFlight();
        var trajectory = new VectorFlight.Trajectory(v.position(), v.velocity(), v.trajectory().firstAcceleration(),
                v.trajectory().lastAcceleration(), 1, 2 * 86400, v.trajectory().gravity());
        var coast = new VectorFlight(trajectory, v.position(), v.velocity(), 1);
        var flight = new LocalFlight(old.geometry(), VectorFlight.motion(coast, old.geometry()), Map.of(), Map.of(), false, false, null, coast);
        state = replace(state, target(state).withLocation(target(state).location().withFlight(flight)));
        var launched = FleetInterceptProcessor.advance(assigned(state));
        assertNotNull(hunter(launched).location().localFlight());
        var arrived = day(launched);
        assertTrue(MovingFleetRendezvous.matches(arrived, hunter(arrived), target(arrived)));
        assertEquals(1, resolver().resolveEncounters(arrived).fleetEngagements().size());
    }
}
