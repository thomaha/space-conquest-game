package com.spaceconquest.control;

import com.fasterxml.jackson.databind.*;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.governance.*;
import com.spaceconquest.engine.macrostructure.*;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FleetProtectionTest {
    private Fleet fleet(GameState state, String id) { return FleetOrganization.find(state, id); }
    private ShipInstance copy(ShipInstance ship, String id, double fuel) {
        return new ShipInstance(id, ship.designId(), ship.ownerEntityId(), ship.currentHullHealth(), ship.currentShieldHealth(),
                fuel, ship.storedCargoKg()).withPowerState(ship.powerState());
    }
    GameState world(int enemies) throws Exception {
        var state = new OrbitalRendezvousTest().world();
        var hunter = fleet(state, "hunter").withLocation(FleetLocation.at(FleetLocation.Site.orbit("earth", 500)));
        var target = fleet(state, "target").withLocation(FleetLocation.at(FleetLocation.Site.orbit("earth", 550)));
        var ship = target.ships().getFirst(); var enemyShips = new ArrayList<ShipInstance>();
        for (int i = 0; i < enemies; i++) enemyShips.add(copy(ship, "enemy-" + i, ship.currentFuelKg()));
        return new GuardObjectCommand("owner", "hunter", FleetGuardOrder.TargetType.BODY, "earth")
                .apply(state.withFleets(List.of(hunter, target.withShips(enemyShips))));
    }
    private Fleet support(GameState state, String id) {
        var guard = fleet(state, "hunter"); var ship = guard.ships().getFirst();
        return new Fleet(id, id, "owner", "sol", "", 0, 0, 0, false, "PASSIVE",
                List.of(copy(ship, id + "-ship", ship.currentFuelKg())), guard.location());
    }
    private GameState day(GameState state) {
        return state.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state), state.orbitalStations(), List.of()))
                .withTurn(state.turn() + 1);
    }

    @Test void overwhelmingDetectedForceSelectsPaidCommonEscapeAndPreservesEscortMembershipAfterReload() throws Exception {
        var state = world(20); var convoy = support(state, "convoy"); var ship = convoy.ships().getFirst();
        convoy = convoy.withShips(List.of(ship, copy(ship, "escort-ship", ship.currentFuelKg())))
                .withEscortOrder(new FleetEscortOrder("owner", "escort", "Escort", "PASSIVE", List.of("escort-ship")));
        state = state.withFleets(List.of(fleet(state, "hunter"), fleet(state, "target"), convoy));
        var decision = FleetProtectionProcessor.decide(state, fleet(state, "hunter"), List.of());
        assertEquals(FleetThreatAssessment.Action.ESCAPE, decision.assessment().action());
        assertEquals(FleetProtectionProcessor.Outcome.ESCAPING, decision.outcome());
        assertEquals(Set.of("hunter", "convoy"), decision.plans().keySet());
        var departed = FleetProtectionProcessor.advance(state, List.of());
        assertEquals(decision.destination(), fleet(departed, "hunter").location().destination());
        assertEquals(decision.destination(), fleet(departed, "convoy").location().destination());
        assertNull(fleet(departed, "hunter").guardOrder());
        assertEquals(convoy.escortOrder(), fleet(departed, "convoy").escortOrder());
        assertEquals(fleet(state, "hunter").ships().getFirst().currentFuelKg(), fleet(departed, "hunter").ships().getFirst().currentFuelKg());
        var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        var restored = mapper.readValue(mapper.writeValueAsString(SaveGame.fromGameState(departed, "now", 1, "now")), SaveGame.class)
                .toGameState(departed.turn(), "RUNNING");
        assertEquals(departed.fleets(), restored.fleets());
        var quote = LocalTravel.arrivalPreview(state, fleet(state, "hunter"), decision.destination(), decision.plans().get("hunter"));
        int ticks = 0; var actual = restored;
        while (fleet(actual, "hunter").location().inTransit() && ticks++ < 10) actual = day(actual);
        assertTrue(ticks < 10); assertTrue(fleet(actual, "hunter").location().isAt(decision.destination()));
        assertEquals(quote.ships().getFirst().currentFuelKg(), fleet(actual, "hunter").ships().getFirst().currentFuelKg(), .00001);
        assertTrue(fleet(actual, "hunter").ships().getFirst().currentFuelKg() < fleet(state, "hunter").ships().getFirst().currentFuelKg());
    }

    @Test void localReinforcementsChangeAssessmentButDistantOrCommittedForcesDoNot() throws Exception {
        var state = world(3);
        assertEquals(FleetThreatAssessment.Action.ESCAPE, FleetThreatAssessment.assess(state, fleet(state, "hunter")).action());
        var first = support(state, "support-a"); var second = support(state, "support-b");
        var reinforced = state.withFleets(List.of(fleet(state, "hunter"), fleet(state, "target"), first, second));
        assertEquals(FleetProtectionProcessor.Outcome.HOLD, FleetProtectionProcessor.decide(reinforced, fleet(reinforced, "hunter"), List.of()).outcome());
        assertEquals(reinforced, FleetProtectionProcessor.advance(reinforced, List.of()));
        var distant = first.withLocation(FleetLocation.at(FleetLocation.Site.orbit("moon", 500)));
        var moving = second.withLocation(second.location().depart(FleetLocation.Site.orbit("earth", 2000), 1));
        var unavailable = state.withFleets(List.of(fleet(state, "hunter"), fleet(state, "target"), distant, moving));
        assertEquals(List.of("hunter"), FleetThreatAssessment.assess(unavailable, fleet(unavailable, "hunter")).friendlyFleetIds());
        assertEquals(FleetThreatAssessment.Action.ESCAPE, FleetThreatAssessment.assess(unavailable, fleet(unavailable, "hunter")).action());
    }

    @Test void oneUnfundedMemberBlocksWholeEscapeWithoutSpendingOrAbandoningIt() throws Exception {
        var state = world(20); var support = support(state, "convoy");
        support = support.withShips(List.of(copy(support.ships().getFirst(), "dry", 0)));
        state = state.withFleets(List.of(fleet(state, "hunter"), fleet(state, "target"), support));
        assertEquals(FleetProtectionProcessor.Outcome.ESCAPE_BLOCKED, FleetProtectionProcessor.decide(state, fleet(state, "hunter"), List.of()).outcome());
        assertEquals(state, FleetProtectionProcessor.advance(state, List.of()));
        assertNotNull(fleet(state, "hunter").guardOrder());
    }

    @Test void currentConditionAndSensorLossChangeDecisionWithoutUsingRememberedContacts() throws Exception {
        var state = world(1); var hunter = fleet(state, "hunter"); var ship = hunter.ships().getFirst();
        var damaged = new ShipInstance(ship.id(), ship.designId(), "owner", 1, 0, ship.currentFuelKg(), ship.storedCargoKg()).withPowerState(ship.powerState());
        var weak = state.withFleets(List.of(hunter.withShips(List.of(damaged)), fleet(state, "target")));
        assertEquals(FleetThreatAssessment.Action.ESCAPE, FleetThreatAssessment.assess(weak, fleet(weak, "hunter")).action());
        var cold = damaged.withPowerState(new ShipPowerState(Map.of(), "rp1", "uranium", 0, false, 1, 1, 0, 0, 0, 1, 0));
        var lost = weak.withFleets(List.of(hunter.withShips(List.of(cold)), fleet(state, "target")));
        assertEquals(FleetProtectionProcessor.Outcome.NO_CONTACT, FleetProtectionProcessor.decide(lost, fleet(lost, "hunter"), List.of()).outcome());
        assertEquals(lost, FleetProtectionProcessor.advance(lost, List.of()));
        var peace = state.toBuilder().diplomaticRelations(List.of()).build();
        assertEquals(FleetThreatAssessment.Action.NO_CONTACT, FleetThreatAssessment.assess(peace, fleet(peace, "hunter")).action());
    }

    @Test void sharedCorporateSensorsContributeThreatKnowledgeWithoutContributingForeignCombatForces() throws Exception {
        var state = world(20); var guard = fleet(state, "hunter");
        var cold = guard.ships().getFirst().withPowerState(new ShipPowerState(Map.of(), "rp1", "uranium", 0, false, 1, 1, 0, 0, 0, 1, 0));
        state = state.withFleets(List.of(guard.withShips(List.of(cold)), fleet(state, "target")));
        var corporation = new Corporation("global", "Global", "", "earth", "TRANSPORT", 1000, List.of(), List.of(), List.of());
        var module = new StationModule("sensor", "Sensor", SensorModuleCatalog.BASIC, 1, 100, 1, 0, Map.of(), "engineer", 0, true);
        var station = new OrbitalStation("observer", "Observer", "sol", "earth", "global", OrbitalStation.OWNERSHIP_PRIVATE_CORPORATE,
                10, List.of(module), Map.of(), 100, 1, 0, 0, 100, 100, "steel", 0, true).withParkingAltitudeKm(500.0);
        state = state.toBuilder().corporations(List.of(corporation)).orbitalStations(List.of(station)).build();
        assertEquals(FleetThreatAssessment.Action.NO_CONTACT, FleetThreatAssessment.assess(state, fleet(state, "hunter")).action());
        state = new ProposeDiplomaticPactCommand("global", "owner", DiplomaticPact.SENSOR_DATA_SHARING).apply(state);
        var grant = state.diplomaticProposals().getLast();
        state = new ResolveDiplomaticProposalCommand(grant.id(), "owner", true).apply(state);
        var known = FleetThreatAssessment.assess(state, fleet(state, "hunter"));
        assertEquals(FleetThreatAssessment.Action.ESCAPE, known.action());
        assertEquals(List.of("hunter"), known.friendlyFleetIds());
        var revoked = new RevokeSensorSharingCommand(grant.id(), "owner").apply(state);
        assertEquals(FleetThreatAssessment.Action.NO_CONTACT, FleetThreatAssessment.assess(revoked, fleet(revoked, "hunter")).action());
    }
}
