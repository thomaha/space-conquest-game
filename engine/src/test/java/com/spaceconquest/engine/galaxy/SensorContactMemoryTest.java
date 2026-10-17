package com.spaceconquest.engine.galaxy;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SensorContactMemoryTest {
    @TempDir Path directory;
    private Empire empire(String id) {
        return new Empire(id, id, "human", "Individualist", 0, 0, List.of(), List.of(), Map.of(), List.of(), List.of());
    }
    private ShipDesign design(String owner) {
        return new ShipDesign(owner, owner, owner, ShipRole.COMBAT_SHIP, "steel",
                List.of(SensorModuleCatalog.BASIC, CombatModuleCatalog.GUN), "steel", 0,
                1000, 1000, 1000, 100, 10, 0, 1000, false, false, ShipManufacturingProfile.baseline(), null);
    }
    private Fleet fleet(String owner, int count) {
        var ships = new ArrayList<ShipInstance>();
        for (int i = 0; i < count; i++) ships.add(new ShipInstance(owner + i, owner, owner, 1000, 0, 100, Map.of("cannon_round", 1000.0)));
        return new Fleet(owner, owner, owner, "sol", "", 0, 0, 0, false, "PASSIVE", ships);
    }
    private GameState world() {
        return GameState.builder().turn(10).empires(List.of(empire("own"), empire("enemy")))
                .shipDesigns(List.of(design("own"), design("enemy"))).fleets(List.of(fleet("own", 1), fleet("enemy", 10)))
                .solarSystems(List.of(new SolarSystem("sol", "Sol", "", 0, 0, 0, 1.989e30, 1392700, "yellow", List.of(), List.of())))
                .diplomaticRelations(List.of(new DiplomaticRelation("own", "enemy", "TOTAL_WAR", 0))).build();
    }
    private GameState refresh(GameState state) {
        return state.withFogOfWarStates(new SensorProcessor().updateSensorCoverage(state, List.of()));
    }
    private GameState lose(GameState state) {
        var own = state.fleets().getFirst();
        own = own.withShips(List.of(own.ships().getFirst().withModuleDamage(new ShipModuleDamage(Map.of(0, 100.0)))));
        return refresh(state.withFleets(List.of(own, state.fleets().getLast())));
    }
    private SensorContact contact(GameState state) { return SensorSharing.view(state, "own").contacts().getFirst(); }

    @Test void lostContactRetainsObservationButCannotAuthorizeTargetingOrReadHiddenStrength() {
        var observed = refresh(world()); var original = contact(observed); var lost = lose(observed);
        var own = lost.fleets().getFirst(); var enemy = lost.fleets().getLast();
        assertEquals(10, original.observedTurn()); assertEquals(0, original.uncertaintyMeters(10));
        assertTrue(SensorSharing.view(lost, "own").detectedFleetIds().isEmpty());
        assertFalse(SensorCoverage.detects(lost, "own", enemy));
        assertFalse(FleetInterceptProcessor.contact(lost, own, enemy));
        assertEquals(FleetThreatAssessment.Action.NO_CONTACT, FleetThreatAssessment.assess(lost, own).action());
        assertEquals(FleetThreatAssessment.Action.ESCAPE, FleetThreatAssessment.caution(lost, own).action());
        assertEquals(ShipServicePower.Activity.ESCAPE, ShipServicePower.activity(lost, own));
        assertTrue(FleetShieldPolicy.recharge(lost, own, true));
        var hiddenDestroyed = lost.withFleets(List.of(own, enemy.withShips(List.of())));
        assertEquals(original.observedStrength(), FleetThreatAssessment.caution(hiddenDestroyed, own).hostileStrength());
        assertEquals(original, contact(hiddenDestroyed)); assertTrue(observed.fleets().getFirst().ships().getFirst().moduleDamage().damageByMount().isEmpty());
    }

    @Test void sheddingSensorsDoesNotImmediatelyDropDefensivePowerPriority() {
        var observed = refresh(world()); var own = observed.fleets().getFirst();
        var power = new ShipPowerState(Map.of(), "rp1", "uranium", 0, false, 1, 1,
                0, 0, 0, 0, 0, null, null, Set.of(ShipServicePower.SENSORS));
        own = own.withShips(List.of(own.ships().getFirst().withPowerState(power)));
        var lost = refresh(observed.withFleets(List.of(own, observed.fleets().getLast())));
        assertFalse(SensorCoverage.detects(lost, "own", lost.fleets().getLast()));
        assertEquals(ShipServicePower.Activity.ESCAPE, ShipServicePower.activity(lost, own));
        assertTrue(FleetShieldPolicy.recharge(lost, own, true));
        assertEquals(contact(observed), contact(lost));
    }

    @Test void uncertaintyGrowsThenMemoryExpiresWithoutRestoringCoverage() {
        var lost = lose(refresh(world())); var aged = refresh(lost.toBuilder().turn(12).build());
        assertEquals(172_800_000, contact(aged).uncertaintyMeters(12));
        assertEquals(10, contact(aged).observedTurn());
        var expired = refresh(aged.toBuilder().turn(13).build());
        assertTrue(SensorSharing.view(expired, "own").contacts().isEmpty());
        assertFalse(FleetShieldPolicy.recharge(expired, expired.fleets().getFirst(), true));
        assertEquals(FleetThreatAssessment.Action.NO_CONTACT, FleetThreatAssessment.caution(expired, expired.fleets().getFirst()).action());
        assertTrue(SensorSharing.view(expired, "own").detectedFleetIds().isEmpty());
    }

    @Test void uncertaintyEnvelopeCanOverlapThreatRadiusWithoutReadingHiddenMotion() {
        var lost = lose(refresh(world())); var own = lost.fleets().getFirst();
        var position = SensorPosition.at(lost, "sol", own.location());
        var distant = new SensorContact("enemy", "enemy", "sol",
                new LocalSiteGeometry.Point(position.xMeters() + 150_000_000, position.yMeters(), position.zMeters()),
                10, contact(lost).observedStrength());
        var view = SensorSharing.view(lost, "own");
        lost = lost.withFogOfWarStates(List.of(new FogOfWarState("own", view.exploredSystemIds(), view.scannedPlanetIds(),
                List.of(), view.discoveredAnomalyIds(), view.discoveredPirateBaseIds(), List.of(distant))));
        assertEquals(FleetThreatAssessment.Action.NO_CONTACT, FleetThreatAssessment.caution(lost, own).action());
        assertEquals(FleetThreatAssessment.Action.ESCAPE, FleetThreatAssessment.caution(lost.withTurn(11), own).action());
    }

    @Test void reacquisitionReplacesObservationAndDoesNotDoubleCountLiveEnemy() {
        var lost = lose(refresh(world())); var own = fleet("own", 1); var enemy = fleet("enemy", 1);
        var reacquired = refresh(lost.toBuilder().turn(12).fleets(List.of(own, enemy)).build());
        assertEquals(12, contact(reacquired).observedTurn()); assertEquals(0, contact(reacquired).uncertaintyMeters(12));
        assertTrue(contact(reacquired).observedStrength() < contact(lost).observedStrength());
        assertEquals(FleetThreatAssessment.assess(reacquired, own), FleetThreatAssessment.caution(reacquired, own));
        assertEquals(List.of("enemy"), SensorSharing.view(reacquired, "own").detectedFleetIds());
    }

    @Test void escapeReturnWaitsForMemoryExpiryAndPeaceClearsSafetyConcern() {
        var lost = lose(refresh(world())); var own = lost.fleets().getFirst();
        var saved = new FleetProtectionAssignment(null, "", own.id(), own.location().current(),
                FleetProtectionAssignment.Stage.RETURNING, "", true);
        own = own.withInterceptOrder(new FleetInterceptOrder("own", own.id(), "sol", saved));
        assertFalse(FleetEscapeReturn.clearToReturn(lost, own, own.location().current()));
        assertTrue(FleetEscapeReturn.clearToReturn(lost.toBuilder().turn(13).build(), own, own.location().current()));
        assertTrue(FleetEscapeReturn.clearToReturn(lost.toBuilder().diplomaticRelations(List.of()).build(), own, own.location().current()));
    }

    @Test void saveLoadAndExplorationCopiesPreserveMemoryAndOldRecordsDefaultEmpty() throws Exception {
        var state = lose(refresh(world())); var manager = new SaveGameManager(directory);
        var file = directory.resolve("contacts.scsave").toFile(); manager.save(file, state, 1, "2027-01-01T08:00:00");
        var loaded = manager.load(file).toGameState(state.turn(), "RUNNING");
        assertEquals(state.fogOfWarStates(), loaded.fogOfWarStates());
        var view = SensorSharing.view(loaded, "own");
        assertEquals(view.contacts(), view.withExploredSystem("new").withScannedPlanet("planet").contacts());
        assertEquals(view.contacts(), new SensorProcessor().scanSystem(view, "new", null, List.of()).contacts());
        assertThrows(UnsupportedOperationException.class, () -> view.contacts().clear());
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper(); var json = mapper.valueToTree(view);
        ((com.fasterxml.jackson.databind.node.ObjectNode) json).remove("contacts");
        assertTrue(mapper.treeToValue(json, FogOfWarState.class).contacts().isEmpty());
    }
}
