package com.spaceconquest.control;

import com.fasterxml.jackson.databind.*;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.combat.*;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class InterceptFleetCommandTest {
    private GameState world() throws Exception {
        var owner = new Empire("owner", "Owner", "human", "Individualist", 1e6, 0, List.of("sol"), List.of(), Map.of(),
                List.of("rocketry", "hydrolox_propulsion", "electricity", "solar_power", "industrial_production", "ship_cannons"), List.of());
        var state = GameState.builder().empires(List.of(owner)).solarSystems(DataModelLoader.loadSolarSystems())
                .diplomaticRelations(List.of(new DiplomaticRelation("owner", "enemy", "TOTAL_WAR", 0))).build();
        var modules = new ArrayList<>(ChemicalFreighterCatalog.modules("mod_hydrolox_rocket"));
        modules.remove(ChemicalFreighterCatalog.CARGO_HOLD);
        modules.add(SensorModuleCatalog.BASIC);
        modules.add(CombatModuleCatalog.GUN);
        var result = ShipBlueprintFactory.evaluate(state, new ShipDesignSpecification("military", "Ship", "owner",
                ShipRole.COMBAT_SHIP, "steel", modules, "steel", .5));
        assertTrue(result.valid(), result.errors().toString());
        return state.withShipDesigns(List.of(result.design())).withFleets(List.of(
                fleet("interceptor", "owner", FleetLocation.Site.orbit("earth", 2000)),
                fleet("target", "enemy", FleetLocation.Site.orbit("earth", 4000))));
    }
    private Fleet fleet(String id, String owner, FleetLocation.Site site) {
        var ship = new ShipInstance(id + "-ship", "military", owner, 100, 0, 120000, Map.of("cannon_round", 100.0))
                .withPowerState(new ShipPowerState(Map.of(), "rp1", "uranium", 500, true, 1, 1, 0, 0, 0, 0, 0));
        return new Fleet(id, id, owner, "sol", "", 0, 0, 0, false, "PASSIVE", List.of(ship), FleetLocation.at(site));
    }
    private InterceptFleetCommand command() { return new InterceptFleetCommand("owner", "interceptor", "target"); }
    private Fleet interceptor(GameState state) { return FleetOrganization.find(state, "interceptor"); }
    private GameState replace(GameState state, Fleet value) {
        return state.withFleets(state.fleets().stream().map(fleet -> fleet.id().equals(value.id()) ? value : fleet).toList());
    }

    @Test void queuedOrderRequiresLiveOwnershipMilitaryHardwareWarAndDetection() throws Exception {
        var initial = world(); var queue = new CommandQueue(); queue.submit(command());
        assertNull(interceptor(initial).interceptOrder());
        assertNotNull(interceptor(queue.drainAndExecute(initial)).interceptOrder());
        assertFalse(new InterceptFleetCommand("enemy", "interceptor", "target").validate(initial));
        assertFalse(command().validate(initial.toBuilder().diplomaticRelations(List.of()).build()));
        assertFalse(command().validate(initial.withShipDesigns(List.of())));
        var remote = replace(initial, FleetOrganization.find(initial, "target")
                .withLocation(FleetLocation.at(FleetLocation.Site.orbit("mars", 4000))));
        assertFalse(command().validate(remote));
        assertNull(interceptor(queue.drainAndExecute(remote)).interceptOrder());
        queue.submit(command());
        assertNull(interceptor(queue.drainAndExecute(remote)).interceptOrder());
    }

    @Test void fundedParkingPursuitUsesRealBurnsAndArrivesBeforeCompleting() throws Exception {
        var initial = world(); var state = FleetInterceptProcessor.advance(command().apply(initial));
        assertNotNull(interceptor(state).location().orbitalFlight());
        assertNotNull(interceptor(state).interceptOrder());
        assertEquals(interceptor(initial).ships().getFirst().currentFuelKg(), interceptor(state).ships().getFirst().currentFuelKg());
        int ticks = 0;
        while (interceptor(state).location().inTransit() && ticks++ < 10) {
            state = state.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state), List.of(), List.of()))
                    .withTurn(state.turn() + 1);
        }
        assertTrue(ticks < 10);
        assertTrue(ShipWeaponRange.reaches(state, interceptor(state), FleetOrganization.find(state, "target")));
        assertFalse(interceptor(state).location().isAt(FleetOrganization.find(state, "target").location().current()));
        assertTrue(interceptor(state).ships().getFirst().currentFuelKg() < 120000);
        assertNotNull(interceptor(state).interceptOrder());
        assertNull(interceptor(FleetInterceptProcessor.finishContacts(state)).interceptOrder());
    }

    @Test void unavailableFuelWaitsWithoutInventingMotionOrReserves() throws Exception {
        var initial = world(); var fleet = interceptor(initial); var ship = fleet.ships().getFirst();
        var empty = new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), 100, 0, 0, Map.of())
                .withPowerState(ship.powerState());
        var assigned = command().apply(replace(initial, fleet.withShips(List.of(empty))));
        var advanced = FleetInterceptProcessor.advance(assigned);
        assertEquals(interceptor(assigned), interceptor(advanced));
        assertNotNull(interceptor(advanced).interceptOrder());
    }

    @Test void movingContactWaitsAndLostContactOrPeaceEndsPursuit() throws Exception {
        var initial = world(); var target = FleetOrganization.find(initial, "target");
        var destination = FleetLocation.Site.orbit("earth", 6000);
        var plan = LocalTravel.plan(initial, target, destination);
        assertNotNull(plan);
        var moving = LocalTravel.depart(target, destination, plan);
        var assigned = command().apply(replace(initial, moving));
        assertNotNull(interceptor(assigned).interceptOrder());
        assertEquals(interceptor(assigned), interceptor(FleetInterceptProcessor.advance(assigned)));
        var stopped = replace(assigned, target.withLocation(FleetLocation.at(FleetLocation.Site.orbit("earth", 6000))));
        assertTrue(interceptor(FleetInterceptProcessor.advance(stopped)).location().inTransit());
        var lost = replace(assigned, target.withLocation(FleetLocation.at(FleetLocation.Site.orbit("mars", 500))));
        assertNull(interceptor(FleetInterceptProcessor.advance(lost)).interceptOrder());
        assertNull(interceptor(FleetInterceptProcessor.advance(assigned.toBuilder().diplomaticRelations(List.of()).build())).interceptOrder());
        assertNull(interceptor(FleetInterceptProcessor.advance(assigned.withFleets(List.of(interceptor(assigned))))).interceptOrder());
    }

    @Test void cancellationAndReloadPreserveCommittedPhysicalJourney() throws Exception {
        var departing = FleetInterceptProcessor.advance(command().apply(world()));
        var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        var restored = mapper.readValue(mapper.writeValueAsString(SaveGame.fromGameState(departing, "now", 1, "now")), SaveGame.class)
                .toGameState(0, "RUNNING");
        assertEquals(interceptor(departing), interceptor(restored));
        assertFalse(new CancelInterceptFleetCommand("enemy", "interceptor").validate(restored));
        var canceled = new CancelInterceptFleetCommand("owner", "interceptor").apply(restored);
        assertNull(interceptor(canceled).interceptOrder());
        assertEquals(interceptor(restored).location(), interceptor(canceled).location());
        assertEquals(interceptor(restored).ships(), interceptor(canceled).ships());
        var lost = replace(restored, FleetOrganization.find(restored, "target")
                .withLocation(FleetLocation.at(FleetLocation.Site.orbit("mars", 500))));
        var ended = FleetInterceptProcessor.advance(lost);
        assertNull(interceptor(ended).interceptOrder());
        assertEquals(interceptor(restored).location(), interceptor(ended).location());
        assertEquals(interceptor(restored).ships(), interceptor(ended).ships());
        var travelCanceled = new CancelOrbitalTravelCommand("owner", "interceptor").apply(restored);
        assertNull(interceptor(travelCanceled).interceptOrder());
        var legacy = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(interceptor(restored));
        legacy.remove("interceptOrder");
        assertNull(mapper.treeToValue(legacy, Fleet.class).interceptOrder());
        assertNull(interceptor(new SetFleetStanceCommand("interceptor", "AGGRESSIVE").apply(restored)).interceptOrder());
    }

    @Test void alreadyInWeaponRangeKeepsPositionInsteadOfStartingAnotherApproach() throws Exception {
        var initial = world(); var target = FleetOrganization.find(initial, "target")
                .withLocation(FleetLocation.at(FleetLocation.Site.orbit("earth", 2050)));
        initial = replace(initial, target);
        assertTrue(ShipWeaponRange.reaches(initial, interceptor(initial), target));
        assertFalse(interceptor(initial).location().current().equals(target.location().current()));
        var ordered = command().apply(initial); var approached = FleetInterceptProcessor.advance(ordered);
        assertEquals(interceptor(ordered).location(), interceptor(approached).location());
        assertEquals(interceptor(ordered).ships(), interceptor(approached).ships());
        assertNotNull(interceptor(approached).interceptOrder());
    }

    @Test void explicitTargetGetsEncounterBeforeOtherHostileAtSite() throws Exception {
        var initial = world(); var site = interceptor(initial).location().current();
        var target = FleetOrganization.find(initial, "target").withLocation(FleetLocation.at(site));
        initial = initial.withFleets(List.of(interceptor(initial), target, fleet("a-other", "enemy", site)));
        var resolver = new TacticalFleetEncounterResolver(new TacticalCombatProcessor(new Random(42)));
        var result = resolver.resolveEncounters(command().apply(initial));
        assertEquals(1, result.fleetEngagements().size());
        assertEquals("interceptor", result.fleetEngagements().getFirst().attackerFleetId());
        assertEquals("target", result.fleetEngagements().getFirst().defenderFleetId());
    }
}
