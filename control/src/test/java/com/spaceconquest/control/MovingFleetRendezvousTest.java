package com.spaceconquest.control;

import com.fasterxml.jackson.databind.*;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.combat.*;
import com.spaceconquest.engine.galaxy.SensorPosition;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MovingFleetRendezvousTest {
    GameState world(boolean drifting, double speed) throws Exception {
        var owner = new Empire("owner", "Owner", "human", "Individualist", 1e6, 0, List.of("sol"), List.of(), Map.of(),
                List.of("rocketry", "hydrolox_propulsion", "electricity", "solar_power", "industrial_production"), List.of());
        var sol = DataModelLoader.loadSolarSystems().stream().filter(system -> system.id().equals("sol")).findFirst().orElseThrow();
        var earth = sol.planets().stream().filter(planet -> planet.id().equals("earth")).findFirst().orElseThrow();
        var smallSystem = new SolarSystem(sol.id(), sol.name(), sol.description(), sol.x(), sol.y(), sol.z(),
                sol.sunMass(), sol.sunDiameter(), sol.sunColor(), List.of(earth), List.of());
        var state = GameState.builder().empires(List.of(owner)).solarSystems(List.of(smallSystem))
                .diplomaticRelations(List.of(new DiplomaticRelation("owner", "enemy", "TOTAL_WAR", 0))).build();
        var modules = new ArrayList<>(ChemicalFreighterCatalog.modules("mod_hydrolox_rocket"));
        modules.remove(ChemicalFreighterCatalog.CARGO_HOLD); modules.add(SensorModuleCatalog.BASIC);
        modules.add(CombatModuleCatalog.GUN);
        var evaluated = ShipBlueprintFactory.evaluate(state, new ShipDesignSpecification("military", "Ship", "owner",
                ShipRole.COMBAT_SHIP, "steel", modules, "steel", .5));
        assertTrue(evaluated.valid(), evaluated.errors().toString());
        var source = FleetLocation.Site.deepSpace(); var destination = FleetLocation.Site.orbit("earth", 500);
        var origin = SensorPosition.at(state, "sol", FleetLocation.at(source));
        var geometry = new LocalSiteGeometry.Leg(origin,
                new LocalSiteGeometry.Point(origin.xMeters() + 1e10, origin.yMeters(), origin.zMeters()), 1e10);
        var trajectory = new FlightMotion.Trajectory(1e6, speed, 1, speed, 0, 100 * 86400, speed);
        var observed = new LocalFlight(geometry, trajectory.at(0), Map.of(), Map.of(), drifting, false);
        var target = fleet("target", "enemy", source).withLocation(new FleetLocation(source, destination, .0001, 100, observed));
        return state.withShipDesigns(List.of(evaluated.design())).withFleets(List.of(fleet("hunter", "owner", source), target));
    }
    private Fleet fleet(String id, String owner, FleetLocation.Site site) {
        var ship = new ShipInstance(id + "-ship", "military", owner, 100, 0, 120000, Map.of())
                .withPowerState(new ShipPowerState(Map.of(), "rp1", "uranium", 500, true, 1, 1, 0, 0, 0, 0, 0));
        return new Fleet(id, id, owner, "sol", "", 0, 0, 0, false, "PASSIVE", List.of(ship), FleetLocation.at(site));
    }
    private Fleet hunter(GameState state) { return FleetOrganization.find(state, "hunter"); }
    private Fleet target(GameState state) { return FleetOrganization.find(state, "target"); }
    private GameState replace(GameState state, Fleet value) {
        return state.withFleets(state.fleets().stream().map(fleet -> fleet.id().equals(value.id()) ? value : fleet).toList());
    }
    private GameState assigned(GameState state) { return new InterceptFleetCommand("owner", "hunter", "target").apply(state); }
    private GameState day(GameState state) {
        return state.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state), List.of(), List.of()))
                .withTurn(state.turn() + 1);
    }
    private TacticalFleetEncounterResolver resolver() {
        return new TacticalFleetEncounterResolver(new TacticalCombatProcessor(new Random(42)));
    }

    @Test void paidCoastAndDriftInterceptionMatchesVelocityAtCoordinatedTickAndTriggersEncounter() throws Exception {
        for (boolean drifting : List.of(false, true)) {
            var initial = assigned(world(drifting, 100));
            var state = FleetInterceptProcessor.advance(initial);
            var flight = hunter(state).location().localFlight(); assertNotNull(flight);
            assertEquals("target", flight.rendezvousTargetId());
            assertEquals(100, flight.motion().trajectory().finalVelocityMps());
            assertFalse(MovingFleetRendezvous.matches(state, hunter(state), target(state)));
            assertTrue(resolver().resolveEncounters(state).fleetEngagements().isEmpty());
            var plan = MovingFleetRendezvous.plan(initial, hunter(initial), target(initial)); assertNotNull(plan);
            var projection = LocalTravel.arrivalPreview(initial, hunter(initial), target(initial).location().destination(), plan);
            state = day(state);
            assertTrue(MovingFleetRendezvous.matches(state, hunter(state), target(state)));
            assertTrue(hunter(state).location().inTransit());
            assertEquals(hunter(state).location().localFlight().motion().positionMeters(),
                    target(state).location().localFlight().motion().positionMeters(), .001);
            assertEquals(100, hunter(state).location().localFlight().motion().velocityMps(), .001);
            assertEquals(projection.ships().getFirst().currentFuelKg(), hunter(state).ships().getFirst().currentFuelKg(), .0001);
            assertTrue(hunter(state).ships().getFirst().currentFuelKg() < 120000);
            var reversed = day(FleetInterceptProcessor.advance(initial.withFleets(List.of(target(initial), hunter(initial)))));
            assertEquals(hunter(state).location(), hunter(reversed).location());
            var resolved = resolver().resolveEncounters(state);
            assertEquals(1, resolved.fleetEngagements().size());
            assertEquals("RENDEZVOUS", resolved.fleetEngagements().getFirst().siteKind());
            assertEquals("target", resolved.fleetEngagements().getFirst().defenderFleetId());
        }
    }

    @Test void unreachableFastAndShortCoastTargetsWaitWhileSidewaysApproachCanLaunch() throws Exception {
        var fast = assigned(world(true, 1e6));
        assertEquals(hunter(fast), hunter(FleetInterceptProcessor.advance(fast)));
        var initial = assigned(world(false, 100)); var target = target(initial); var old = target.location().localFlight();
        var shortCoast = new FlightMotion.Trajectory(1e6, 100, 1, 100, 0, 100, 100);
        var shortFlight = new LocalFlight(old.geometry(), shortCoast.at(0), Map.of(), Map.of(), false, false);
        var shortState = replace(initial, target.withLocation(target.location().withFlight(shortFlight)));
        assertEquals(hunter(initial), hunter(FleetInterceptProcessor.advance(shortState)));
        var from = old.geometry().origin(); var to = old.geometry().destination();
        var offset = new LocalSiteGeometry.Leg(new LocalSiteGeometry.Point(from.xMeters(), from.yMeters() + 100, from.zMeters()),
                new LocalSiteGeometry.Point(to.xMeters(), to.yMeters() + 100, to.zMeters()), old.geometry().distanceMeters());
        var offsetState = replace(initial, target.withLocation(target.location().withFlight(
                new LocalFlight(offset, old.motion(), Map.of(), Map.of(), false, false))));
        assertNotNull(hunter(FleetInterceptProcessor.advance(offsetState)).location().localFlight().vectorFlight());
    }

    @Test void changedTargetMotionDoesNotProduceRemoteContactOrBattle() throws Exception {
        var state = FleetInterceptProcessor.advance(assigned(world(true, 100)));
        var target = target(state); var flight = target.location().localFlight();
        var changed = new FlightMotion(flight.motion().positionMeters(), 200, flight.motion().trajectory(), flight.motion().elapsedSeconds());
        state = day(replace(state, target.withLocation(target.location().withFlight(
                new LocalFlight(flight.geometry(), changed, Map.of(), Map.of(), true, false)))));
        assertFalse(MovingFleetRendezvous.matches(state, hunter(state), target(state)));
        assertTrue(resolver().resolveEncounters(state).fleetEngagements().isEmpty());
        var hunterMotion = hunter(state).location().localFlight().motion();
        var velocityMismatch = new FlightMotion(hunterMotion.positionMeters(), hunterMotion.velocityMps() + 1,
                flight.motion().trajectory(), flight.motion().elapsedSeconds());
        var samePosition = replace(state, target(state).withLocation(target(state).location().withFlight(
                new LocalFlight(flight.geometry(), velocityMismatch, Map.of(), Map.of(), true, false))));
        assertFalse(MovingFleetRendezvous.matches(samePosition, hunter(samePosition), target(samePosition)));
        assertTrue(ShipWeaponRange.reaches(samePosition, hunter(samePosition), target(samePosition)));
        assertEquals("WEAPON_RANGE", resolver().resolveEncounters(samePosition).fleetEngagements().getFirst().siteKind());
        var ended = FleetInterceptProcessor.finishContacts(state);
        assertNull(hunter(ended).interceptOrder());
        assertTrue(hunter(ended).location().localFlight().interrupted());
        assertEquals(hunter(state).location().localFlight().motion(), hunter(ended).location().localFlight().motion());
    }

    @Test void lostContactAndCancellationKeepPaidTrajectoryWhileRangeStillAllowsFiring() throws Exception {
        var state = FleetInterceptProcessor.advance(assigned(world(true, 100)));
        var canceled = new CancelInterceptFleetCommand("owner", "hunter").apply(state);
        assertEquals(hunter(state).location(), hunter(canceled).location());
        assertEquals(hunter(state).ships(), hunter(canceled).ships());
        canceled = day(canceled);
        assertFalse(MovingFleetRendezvous.matches(canceled, hunter(canceled), target(canceled)));
        assertEquals("WEAPON_RANGE", resolver().resolveEncounters(canceled).fleetEngagements().getFirst().siteKind());
        var arrived = day(state); var spent = hunter(arrived).ships().getFirst().currentFuelKg();
        assertTrue(spent < 120000);
        var canceledAfterBurns = new CancelInterceptFleetCommand("owner", "hunter").apply(arrived);
        assertEquals(spent, hunter(canceledAfterBurns).ships().getFirst().currentFuelKg());
        assertEquals("WEAPON_RANGE", resolver().resolveEncounters(canceledAfterBurns).fleetEngagements().getFirst().siteKind());
        var ship = hunter(state).ships().getFirst();
        var outage = new ShipPowerState(Map.of(), "rp1", "uranium", 500, true, 1, 1, 0, 0, 0, 1, 0);
        var lostCoverage = replace(state, hunter(state).withShips(List.of(ship.withPowerState(outage))));
        var stopped = FleetInterceptProcessor.advance(lostCoverage);
        assertNull(hunter(stopped).interceptOrder());
        assertEquals(hunter(lostCoverage).location(), hunter(stopped).location());
        assertEquals(hunter(lostCoverage).ships(), hunter(stopped).ships());
        var absent = state.withFleets(List.of(hunter(state)));
        var ended = FleetInterceptProcessor.advance(absent);
        assertNull(hunter(ended).interceptOrder());
        assertEquals(hunter(state).location(), hunter(ended).location());
        assertEquals(hunter(state).ships(), hunter(ended).ships());
    }

    @Test void emptyTankAndInsufficientElectricityDoNotLaunch() throws Exception {
        var initial = assigned(world(true, 100)); var hunter = hunter(initial); var ship = hunter.ships().getFirst();
        var empty = new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), 100, 0, 0, Map.of())
                .withPowerState(ship.powerState());
        var emptyState = replace(initial, hunter.withShips(List.of(empty)));
        assertNull(MovingFleetRendezvous.plan(emptyState, hunter(emptyState), target(emptyState)));
        assertEquals(hunter(emptyState), hunter(FleetInterceptProcessor.advance(emptyState)));
        var unpowered = ship.withPowerState(new ShipPowerState(Map.of(), "rp1", "uranium", 0, false, 1, 1, 0, 0, 0, 0, 0));
        var darkState = replace(initial, hunter.withShips(List.of(unpowered)));
        assertNull(MovingFleetRendezvous.plan(darkState, hunter(darkState), target(darkState)));
        assertEquals(hunter(darkState), hunter(FleetInterceptProcessor.advance(darkState)));
    }

    @Test void saveLoadRetainsTargetAndTerminalVelocityWithoutChangingOrdinaryLocalFlights() throws Exception {
        var state = FleetInterceptProcessor.advance(assigned(world(true, 100)));
        var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        var restored = mapper.readValue(mapper.writeValueAsString(SaveGame.fromGameState(state, "now", 1, "now")), SaveGame.class)
                .toGameState(0, "RUNNING");
        assertEquals(hunter(state), hunter(restored));
        var advanced = day(restored);
        assertTrue(MovingFleetRendezvous.matches(advanced, hunter(advanced), target(advanced)));
        var legacy = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(target(state).location().localFlight());
        legacy.remove("rendezvousTargetId");
        assertNull(mapper.treeToValue(legacy, LocalFlight.class).rendezvousTargetId());
    }

    private GameState sideways(double y, double z, double length) throws Exception {
        var state = assigned(world(false, 100)); var target = target(state); var flight = target.location().localFlight();
        var from = flight.geometry().origin();
        var origin = new LocalSiteGeometry.Point(from.xMeters(), from.yMeters() + y, from.zMeters() + z);
        var geometry = new LocalSiteGeometry.Leg(origin, new LocalSiteGeometry.Point(origin.xMeters() + length, origin.yMeters(), origin.zMeters()), length);
        return replace(state, target.withLocation(target.location().withFlight(new LocalFlight(geometry, flight.motion(), Map.of(), Map.of(), false, false))));
    }

    @Test void sidewaysThreeDimensionalBurnsMatchFullVelocityAndForecastFuel() throws Exception {
        for (double sign : List.of(-1.0, 1.0)) {
            var initial = sideways(sign * 200000, sign * 300000, 2e7);
            var plan = MovingFleetRendezvous.plan(initial, hunter(initial), target(initial)); assertNotNull(plan);
            var trajectory = plan.physical().vectorTrajectory(); assertNotNull(trajectory);
            assertTrue(Math.abs(trajectory.firstAcceleration().yMeters()) > 0);
            assertTrue(Math.abs(trajectory.firstAcceleration().zMeters()) > 0);
            var expected = LocalTravel.arrivalPreview(initial, hunter(initial), target(initial).location().destination(), plan);
            var state = day(FleetInterceptProcessor.advance(initial));
            assertTrue(MovingFleetRendezvous.matches(state, hunter(state), target(state)));
            var actual = hunter(state).location().localFlight().vectorFlight();
            assertNotNull(actual);
            assertEquals(100, actual.velocity().xMeters(), .001);
            assertEquals(0, actual.velocity().yMeters(), .001); assertEquals(0, actual.velocity().zMeters(), .001);
            var targetPosition = SensorPosition.at(state, "sol", target(state).location());
            assertEquals(targetPosition.xMeters(), actual.position().xMeters(), .001);
            assertEquals(targetPosition.yMeters(), actual.position().yMeters(), .001);
            assertEquals(targetPosition.zMeters(), actual.position().zMeters(), .001);
            assertEquals(expected.ships().getFirst().currentFuelKg(), hunter(state).ships().getFirst().currentFuelKg(), .0001);
            assertEquals(1, resolver().resolveEncounters(state).fleetEngagements().size());
        }
    }

    @Test void interruptedSidewaysBurnRetainsActualPositionAndSupportsFundedVectorRecovery() throws Exception {
        var state = FleetInterceptProcessor.advance(sideways(200000, 300000, 2e7));
        var vector = hunter(state).location().localFlight().vectorFlight(); assertNotNull(vector);
        double poweredHours = vector.trajectory().burnSeconds() / 2 / 3600;
        var interrupted = LocalFlightProcessor.interrupt(hunter(state), poweredHours);
        var expected = vector.advance(poweredHours * 3600, false).advance((24 - poweredHours) * 3600, true);
        assertEquals(expected.position(), interrupted.location().localFlight().vectorFlight().position());
        assertEquals(expected.velocity(), interrupted.location().localFlight().vectorFlight().velocity());
        state = FleetInterceptProcessor.finishContacts(replace(state, interrupted));
        assertNull(hunter(state).interceptOrder());
        assertEquals(expected.position(), SensorPosition.at(state, "sol", hunter(state).location()));
        var recover = new RecoverFleetTravelCommand("hunter");
        assertTrue(recover.validate(state));
        var recovered = recover.apply(state);
        assertEquals(expected.position(), hunter(recovered).location().localFlight().vectorFlight().position());
        assertEquals(expected.velocity(), hunter(recovered).location().localFlight().vectorFlight().velocity());
        var goal = hunter(recovered).location().localFlight().geometry().destination();
        var trajectory = hunter(recovered).location().localFlight().vectorFlight().trajectory();
        var arrival = trajectory.at(trajectory.totalSeconds());
        assertTrue(VectorFlight.norm(VectorFlight.subtract(goal, arrival.position())) <= .001);
        assertTrue(VectorFlight.norm(arrival.velocity()) <= .001);
        var finished = day(recovered);
        assertFalse(hunter(finished).location().inTransit());
        assertTrue(hunter(finished).ships().getFirst().currentFuelKg() < hunter(recovered).ships().getFirst().currentFuelKg());
    }

    @Test void sidewaysCancellationAndReloadRetainVectorTrajectoryAndPaidState() throws Exception {
        var state = FleetInterceptProcessor.advance(sideways(200000, 300000, 2e7));
        var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        var restored = mapper.readValue(mapper.writeValueAsString(SaveGame.fromGameState(state, "now", 1, "now")), SaveGame.class)
                .toGameState(0, "RUNNING");
        assertEquals(hunter(state), hunter(restored));
        var canceled = new CancelInterceptFleetCommand("owner", "hunter").apply(restored);
        assertEquals(hunter(restored).location(), hunter(canceled).location());
        var advanced = day(canceled);
        assertNotNull(hunter(advanced).location().localFlight().vectorFlight());
        assertFalse(MovingFleetRendezvous.matches(advanced, hunter(advanced), target(advanced)));
        assertTrue(ShipWeaponRange.reaches(advanced, hunter(advanced), target(advanced)));
        assertEquals("WEAPON_RANGE", resolver().resolveEncounters(advanced).fleetEngagements().getFirst().siteKind());
        var released = FleetInterceptProcessor.finishContacts(advanced);
        assertEquals(hunter(advanced).location().localFlight().vectorFlight(), hunter(released).location().localFlight().vectorFlight());
        var legacy = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(target(state).location().localFlight());
        legacy.remove("vectorFlight");
        assertNull(mapper.treeToValue(legacy, LocalFlight.class).vectorFlight());
    }
}
