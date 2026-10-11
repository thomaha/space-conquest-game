package com.spaceconquest.control;

import com.fasterxml.jackson.databind.*;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.combat.*;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GuardObjectCommandTest {
    private final ShipDesign design = new ShipDesign("warship", "Warship", "owner", ShipRole.COMBAT_SHIP, "steel",
            List.of(), "steel", 2, 20000, 1000, 200, 1.5, 100000, 500000, true, false);

    private Fleet fleet(String id, String owner, FleetLocation.Site site) {
        var ship = new ShipInstance(id + "ship", design.id(), owner, 1000, 500, 100, Map.of());
        return new Fleet(id, id, owner, "sol", "", 0, 0, 0, false, "PASSIVE", List.of(ship), FleetLocation.at(site));
    }

    private GameState world() {
        var owner = new Empire("owner", "Owner", "human", "Individualist", 1000, 0,
                List.of("sol"), List.of(), Map.of(), List.of(), List.of());
        var station = new OrbitalStation("station", "Station", "sol", "earth", "owner", OrbitalStation.OWNERSHIP_PUBLIC_STATE,
                20, List.of(), Map.of(), 100, 10, 0, 0, 100, 100, "steel", 0, true);
        var planet = new Planet("earth", "Earth", "", 1, 1, 1, 0, 1, "rock", "", false, 0, List.of(), List.of(), List.of());
        var system = new SolarSystem("sol", "Sol", "", 0, 0, 0, 1, 1, "yellow", List.of(planet), List.of());
        return GameState.builder().empires(List.of(owner)).shipDesigns(List.of(design)).solarSystems(List.of(system))
                .orbitalStations(List.of(station)).fleets(List.of(fleet("guard", "owner", FleetLocation.Site.docked("station"))))
                .build();
    }

    private GuardObjectCommand guardStation() {
        return new GuardObjectCommand("owner", "guard", FleetGuardOrder.TargetType.STATION, "station");
    }

    private TacticalFleetEncounterResolver resolver() {
        return new TacticalFleetEncounterResolver(new TacticalCombatProcessor(new Random(42)));
    }

    @Test void assignmentAndCancellationAreQueuedAndDoNotMoveOrConsumeFuel() {
        var initial = world();
        var queue = new CommandQueue();
        queue.submit(guardStation());
        assertNull(initial.fleets().getFirst().guardOrder());
        var guarded = queue.drainAndExecute(initial);
        assertEquals("GUARD", guarded.fleets().getFirst().fleetStance());
        assertEquals(initial.fleets().getFirst().location(), guarded.fleets().getFirst().location());
        assertEquals(initial.fleets().getFirst().ships(), guarded.fleets().getFirst().ships());
        assertFalse(new CancelGuardObjectCommand("other", "guard").validate(guarded));
        queue.submit(new CancelGuardObjectCommand("owner", "guard"));
        var canceled = queue.drainAndExecute(guarded);
        assertNull(canceled.fleets().getFirst().guardOrder());
        assertEquals("PASSIVE", canceled.fleets().getFirst().fleetStance());
    }

    @Test void validatesOwnershipPhysicalSiteAndAvailableMilitaryHullAgainstLiveState() {
        var initial = world();
        assertFalse(new GuardObjectCommand("other", "guard", FleetGuardOrder.TargetType.STATION, "station").validate(initial));
        assertFalse(new GuardObjectCommand("owner", "guard", FleetGuardOrder.TargetType.STATION, "missing").validate(initial));
        var civilian = initial.toBuilder().shipDesigns(List.of()).build();
        assertFalse(guardStation().validate(civilian));
        var remote = initial.withFleets(List.of(initial.fleets().getFirst().withLocation(FleetLocation.at(FleetLocation.Site.orbit("earth")))));
        assertFalse(guardStation().validate(remote));
        var queue = new CommandQueue();
        queue.submit(guardStation());
        assertNull(queue.drainAndExecute(initial.toBuilder().orbitalStations(List.of()).build()).fleets().getFirst().guardOrder());
        var guarded = guardStation().apply(initial);
        assertNull(resolver().resolveEncounters(guarded.toBuilder().orbitalStations(List.of()).build()).fleets().getFirst().guardOrder());
    }

    @Test void canGuardControlledBodyOrStationaryOwnedFleetAndStopsWhenTargetMoves() {
        var initial = world();
        var orbit = FleetLocation.Site.orbit("earth", 500);
        var guard = initial.fleets().getFirst().withLocation(FleetLocation.at(orbit));
        var target = fleet("target", "owner", orbit);
        var state = initial.withFleets(List.of(guard, target));
        var body = new GuardObjectCommand("owner", "guard", FleetGuardOrder.TargetType.BODY, "earth");
        assertTrue(body.validate(state));
        assertEquals(orbit, body.apply(state).fleets().getFirst().guardOrder().site());
        assertFalse(new GuardObjectCommand("owner", "guard", FleetGuardOrder.TargetType.FLEET, "guard").validate(state));
        var command = new GuardObjectCommand("owner", "guard", FleetGuardOrder.TargetType.FLEET, "target");
        var guarded = command.apply(state);
        assertNotNull(guarded.fleets().getFirst().guardOrder());
        var departed = guarded.withFleets(List.of(guarded.fleets().getFirst(), target.withLocation(
                target.location().depart(FleetLocation.Site.deepSpace(), 2))));
        assertNull(resolver().resolveEncounters(departed).fleets().getFirst().guardOrder());
    }

    @Test void guardTakesHostileEncounterBeforeProtectedFleetRegardlessOfIds() {
        var initial = world();
        var site = initial.fleets().getFirst().location().current();
        var civilian = fleet("a-protected", "owner", site);
        var hostile = fleet("b-hostile", "enemy", site);
        var state = initial.withFleets(List.of(civilian, hostile, initial.fleets().getFirst()))
                .toBuilder().diplomaticRelations(List.of(new DiplomaticRelation("owner", "enemy", "TOTAL_WAR", 0))).build();
        var guarded = new GuardObjectCommand("owner", "guard", FleetGuardOrder.TargetType.FLEET, "a-protected").apply(state);
        var resolved = resolver().resolveEncounters(guarded);
        assertEquals(1, resolved.fleetEngagements().size());
        assertEquals("guard", resolved.fleetEngagements().getFirst().attackerFleetId());
        assertEquals("b-hostile", resolved.fleetEngagements().getFirst().defenderFleetId());
        assertEquals(civilian, resolved.fleets().stream().filter(fleet -> fleet.id().equals(civilian.id())).findFirst().orElseThrow());
        var peaceful = guarded.toBuilder().diplomaticRelations(List.of()).build();
        assertTrue(resolver().resolveEncounters(peaceful).fleetEngagements().isEmpty());
    }

    @Test void orderSurvivesDailyMovementAndSaveLoadButClearsOnReplacementOrders() throws Exception {
        var guarded = guardStation().apply(world());
        var fleet = guarded.fleets().getFirst();
        var advanced = new FleetProcessor().processFleetMovements(guarded.fleets(), guarded.orbitalStations(), List.of()).getFirst();
        assertEquals(fleet.guardOrder(), advanced.guardOrder());
        var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        var save = SaveGame.fromGameState(guarded, "now", 1, "now");
        var restored = mapper.readValue(mapper.writeValueAsString(save), SaveGame.class).toGameState(0, "RUNNING");
        assertEquals(fleet.guardOrder(), restored.fleets().getFirst().guardOrder());
        var oldFleetJson = mapper.valueToTree(world().fleets().getFirst());
        ((com.fasterxml.jackson.databind.node.ObjectNode) oldFleetJson).remove("guardOrder");
        assertNull(mapper.treeToValue(oldFleetJson, Fleet.class).guardOrder());
        var moved = fleet.withLocation(fleet.location().depart(FleetLocation.Site.orbit("earth"), 2));
        assertNull(moved.guardOrder());
        assertEquals("PASSIVE", moved.fleetStance());
        var replacement = new SetFleetStanceCommand("guard", "AGGRESSIVE").apply(guarded);
        assertNull(replacement.fleets().getFirst().guardOrder());
        assertEquals("AGGRESSIVE", replacement.fleets().getFirst().fleetStance());
    }
}
