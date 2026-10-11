package com.spaceconquest.control;

import com.fasterxml.jackson.databind.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.combat.*;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MovingEscortReturnTest {
    private Fleet find(GameState state, String id) { return FleetOrganization.find(state, id); }
    private GameState replace(GameState state, Fleet fleet) { return FleetReturnTravel.replace(state, fleet); }
    private ShipInstance fuel(ShipInstance ship, String id, double kg) {
        return new ShipInstance(id, ship.designId(), "owner", ship.currentHullHealth(), ship.currentShieldHealth(), kg,
                ship.storedCargoKg(), ship.passengerCount(), ship.passengerRaceId(), ship.transitMode(), ship.powerState(), ship.supplyState());
    }
    private GameState world(boolean drifting) throws Exception {
        var state = new MovingFleetRendezvousTest().world(drifting, 100);
        var old = find(state, "target");
        var convoy = new Fleet(old.id(), "Convoy", "owner", "sol", "", 0, 0, 0, false, "PASSIVE",
                List.of(fuel(old.ships().getFirst(), "protected", 120000)), old.location());
        if (!drifting) {
            var flight = convoy.location().localFlight();
            var design = state.shipDesigns().getFirst(); var ship = convoy.ships().getFirst();
            double acceleration = .9 * ShipPowerProcessor.poweredThrust(design, ship, ShipSolarEnvironment.journey(state, convoy, convoy.location().destination()))
                    / (design.totalDryMassKg() + ship.currentFuelKg());
            var trajectory = new FlightMotion.Trajectory(1e6, 100, acceleration, 100, 0, 3 * 86400, 100 / acceleration);
            var drive = PropulsionCatalog.mainDrive(design.equippedModuleIds());
            double kg = (design.totalDryMassKg() + ship.currentFuelKg()) * -Math.expm1(-100 / drive.exhaustVelocityMps());
            convoy = convoy.withLocation(convoy.location().withFlight(new LocalFlight(flight.geometry(), trajectory.at(0),
                    Map.of(ship.id(), kg), Map.of(ship.id(), JourneyPropulsion.capture(ship, drive, null, kg).withProtectedPropellant(24000)), false, false)));
        }
        var escort = find(state, "hunter");
        var assignment = new FleetProtectionAssignment(null, "target", "hunter", escort.location().current(), FleetProtectionAssignment.Stage.RETURNING, "");
        escort = escort.withInterceptOrder(new FleetInterceptOrder("owner", "old-enemy", "sol", assignment));
        return state.withFleets(List.of(escort, convoy));
    }
    private GameState day(GameState state) {
        return state.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state), List.of(), List.of()))
                .withTurn(state.turn() + 1);
    }
    private GameState resolve(GameState state) { return new TacticalFleetEncounterResolver(new TacticalCombatProcessor(new Random(42))).resolveEncounters(state); }
    private GameState reload(GameState state) throws Exception {
        var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper.readValue(mapper.writeValueAsString(SaveGame.fromGameState(state, "now", 1, "now")), SaveGame.class).toGameState(state.turn(), "RUNNING");
    }
    @Test void paidMovingContactRejoinsWithoutStoppingConvoyAndSurvivesSave() throws Exception {
        for (boolean drifting : List.of(false, true)) {
            var initial = world(drifting); var state = FleetInterceptProcessor.advance(initial);
            assertNotNull(MovingFleetRendezvous.plan(initial, find(initial, "hunter"), find(initial, "target")), "Raw approach: " + drifting);
            assertNotNull(FleetEscortReturn.plan(initial, find(initial, "hunter"), find(initial, "target")), "Combined approach: " + drifting);
            assertEquals("target", find(state, "hunter").location().localFlight().rendezvousTargetId());
            assertEquals(find(initial, "target"), find(state, "target"));
            assertFalse(MovingFleetRendezvous.matches(state, find(state, "hunter"), find(state, "target")), "Friendly contact is not combat authorization.");
            state = day(reload(state)); var convoy = find(state, "target"); var escort = find(state, "hunter");
            assertTrue(MovingFleetRendezvous.physicalMatches(state, escort, convoy));
            var spent = escort.ships().getFirst().currentFuelKg(); assertTrue(spent < 120000);
            state = resolve(state);
            assertNull(find(state, "hunter")); var joined = find(state, "target");
            assertEquals(2, joined.ships().size()); assertEquals("hunter", joined.escortOrder().escortFleetId());
            assertEquals(convoy.location().localFlight().motion(), joined.location().localFlight().motion());
            assertEquals(convoy.ships().getFirst(), joined.ships().getFirst());
            assertEquals(spent, joined.ships().getLast().currentFuelKg()); assertTrue(state.fleetEngagements().isEmpty());
            assertEquals(state.fleets(), reload(state).fleets());
            assertTrue(find(day(state), "target").location().inTransit());
            if (!drifting) {
                for (int tick = 0; tick < 5 && find(state, "target").location().inTransit(); tick++) state = day(state);
                assertFalse(find(state, "target").location().inTransit());
                assertTrue(find(state, "target").ships().getLast().currentFuelKg() < spent, "Joined escorts pay the convoy's later braking burn.");
                assertTrue(find(state, "target").ships().getLast().currentFuelKg() >= 24000);
            }
        }
    }
    @Test void changedConvoyMotionMissesContactAndRetriesFromPaidActualDrift() throws Exception {
        var state = FleetInterceptProcessor.advance(world(true)); var convoy = find(state, "target"); var flight = convoy.location().localFlight();
        var changed = new FlightMotion(flight.motion().positionMeters(), 101, flight.motion().trajectory(), flight.motion().elapsedSeconds());
        state = day(replace(state, convoy.withLocation(convoy.location().withFlight(new LocalFlight(flight.geometry(), changed,
                flight.propellantKg(), flight.propulsion(), true, false)))));
        var missed = find(state, "hunter"); var fuel = missed.ships().getFirst().currentFuelKg();
        state = resolve(state); assertNotNull(find(state, "hunter")); assertNull(find(state, "target").escortOrder());
        assertTrue(find(state, "hunter").location().localFlight().interrupted());
        assertEquals(fuel, find(state, "hunter").ships().getFirst().currentFuelKg());
        assertEquals(FleetProtectionAssignment.Stage.RETURNING, find(state, "hunter").interceptOrder().protection().stage());
        assertNotNull(MovingFleetRendezvous.plan(state, find(state, "hunter"), find(state, "target")), "Retry raw approach");
        assertNotNull(FleetEscortReturn.plan(state, find(state, "hunter"), find(state, "target")), "Retry combined approach");
        state = FleetInterceptProcessor.advance(state);
        assertFalse(find(state, "hunter").location().localFlight().interrupted());
        assertEquals(missed.location().localFlight().motion().positionMeters(), find(state, "hunter").location().localFlight().motion().positionMeters(), .001);
        state = resolve(day(state)); assertNull(find(state, "hunter")); assertNotNull(find(state, "target").escortOrder());
    }
    @Test void emptyEscortRetainsReturnIntentThroughEncounterCleanupWithoutTeleporting() throws Exception {
        var initial = world(true); var escort = find(initial, "hunter");
        initial = replace(initial, escort.withShips(List.of(fuel(escort.ships().getFirst(), "hunter-ship", 0))));
        var state = resolve(FleetInterceptProcessor.advance(initial));
        assertEquals(find(initial, "hunter"), find(state, "hunter")); assertNull(find(state, "target").escortOrder());
        assertEquals(initial.fleets(), reload(state).fleets());
    }
    @Test void compatibleSharingFundsMovingReturnAndFailedSharingSplitsOnlyWeakShips() throws Exception {
        for (double donorFuel : List.of(120000.0, 26000.0)) {
            var initial = world(true); var escort = find(initial, "hunter");
            initial = replace(initial, escort.withShips(List.of(fuel(escort.ships().getFirst(), "hunter-ship", donorFuel),
                    fuel(escort.ships().getFirst(), "empty", 0))));
            double total = initial.fleets().stream().flatMap(fleet -> fleet.ships().stream()).mapToDouble(ShipInstance::currentFuelKg).sum();
            var state = FleetInterceptProcessor.advance(initial);
            assertNotNull(find(state, "hunter").location().localFlight());
            assertEquals("target", find(state, "hunter").location().localFlight().rendezvousTargetId());
            assertEquals(total, state.fleets().stream().flatMap(fleet -> fleet.ships().stream()).mapToDouble(ShipInstance::currentFuelKg).sum(), .000001);
            if (donorFuel == 120000) assertEquals(2, find(state, "hunter").ships().size());
            else {
                assertEquals(1, find(state, "hunter").ships().size());
                var weak = state.fleets().stream().filter(fleet -> fleet.ships().stream().anyMatch(ship -> ship.id().equals("empty"))).findFirst().orElseThrow();
                assertEquals(escort.location(), weak.location()); assertNotNull(weak.interceptOrder().protection());
            }
        }
    }
    @Test void realEngineTickRetainsFriendlyReturnAndRejoinsMovingConvoy() throws Exception {
        var engine = new SpaceConquestEngine(); engine.applyGameState(world(true)); engine.stepTurn();
        assertNull(find(engine.getGameState(), "hunter")); assertNotNull(find(engine.getGameState(), "target").escortOrder());
        assertTrue(find(engine.getGameState(), "target").location().inTransit());
    }
    @Test void bodyFrameReturnsMatchActualEarthAndMoonCoastsBeforeJoining() throws Exception {
        for (String body : List.of("earth", "moon")) {
            var initial = new BodyFleetRendezvousTest().world(body, false); var escort = find(initial, "hunter"); var target = find(initial, "target");
            var assignment = new FleetProtectionAssignment(null, "target", "hunter", escort.location().current(), FleetProtectionAssignment.Stage.RETURNING, "");
            escort = escort.withInterceptOrder(new FleetInterceptOrder("owner", "old-enemy", "sol", assignment));
            var convoy = new Fleet("target", "Convoy", "owner", "sol", "", 0, 0, 0, false, "PASSIVE",
                    List.of(fuel(escort.ships().getFirst(), "protected", escort.ships().getFirst().currentFuelKg())), target.location());
            initial = initial.withFleets(List.of(escort, convoy));
            var state = FleetInterceptProcessor.advance(initial);
            assertEquals(body, find(state, "hunter").location().localFlight().vectorFlight().trajectory().gravity().frame().centerBodyId());
            state = resolve(day(reload(state)));
            assertNull(find(state, "hunter")); assertNotNull(find(state, "target").escortOrder());
            assertTrue(find(state, "target").location().inTransit()); assertTrue(state.fleetEngagements().isEmpty());
        }
    }
    @Test void nativeStellarCoastReattachmentQuotesOwnRemainingManeuvers() throws Exception {
        var initial = new OrbitalRendezvousTest().world(); var escort = find(initial, "hunter"); var old = find(initial, "target");
        var ship = fuel(escort.ships().getFirst(), "protected", escort.ships().getFirst().currentFuelKg());
        var drive = PropulsionCatalog.mainDrive(initial.shipDesigns().getFirst().equippedModuleIds());
        var feed = PropulsionCatalog.availableReactorFuel(drive, ship);
        double exhaust = drive.exhaustVelocityMps() * feed.exhaustMultiplier();
        double kg = (initial.shipDesigns().getFirst().totalDryMassKg() + ship.currentFuelKg() + 205) * -Math.expm1(-10 / exhaust);
        var burn = new OrbitalFlight.Burn(10, exhaust, kg, 3000, feed.materialId(), feed.kgPerPropellantKg());
        var itinerary = old.location().orbitalFlight().itinerary();
        var events = itinerary.maneuvers().stream().map(event -> new OrbitalFlight.Maneuver(event.seconds(), event.auxiliaryHours(), Map.of(ship.id(), burn))).toList();
        var paid = new OrbitalFlight.Itinerary(itinerary.sourceBodyId(), itinerary.targetBodyId(), itinerary.sourceAltitudeKm(), itinerary.targetAltitudeKm(),
                itinerary.epochDays(), itinerary.waitSeconds(), itinerary.coast(), itinerary.environment(), events, itinerary.frame());
        var convoy = new Fleet("target", "Convoy", "owner", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(ship),
                old.location().withOrbitalFlight(new OrbitalFlight(paid, 0, 1, OrbitalFlight.Status.COAST, "")));
        var assignment = new FleetProtectionAssignment(null, "target", "hunter", escort.location().current(), FleetProtectionAssignment.Stage.RETURNING, "");
        escort = escort.withInterceptOrder(new FleetInterceptOrder("owner", "old-enemy", "sol", assignment));
        initial = initial.withFleets(List.of(escort, convoy));
        var state = FleetInterceptProcessor.advance(initial); assertNotNull(find(state, "hunter").location().localFlight());
        state = day(reload(state)); var before = find(state, "target"); state = resolve(state);
        assertNull(find(state, "hunter")); var joined = find(state, "target"); assertNotNull(joined.escortOrder());
        assertEquals(before.location().orbitalFlight().elapsedSeconds(), joined.location().orbitalFlight().elapsedSeconds());
        assertTrue(joined.location().orbitalFlight().itinerary().maneuvers().getLast().burns().containsKey("owner-ship"));
        assertEquals(before.ships().getFirst(), joined.ships().getFirst()); assertEquals(state.fleets(), reload(state).fleets());
    }
}
