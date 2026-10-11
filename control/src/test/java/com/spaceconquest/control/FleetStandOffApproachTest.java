package com.spaceconquest.control;

import com.fasterxml.jackson.databind.*;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FleetStandOffApproachTest {
    private Fleet hunter(GameState state) { return FleetOrganization.find(state, "hunter"); }
    private Fleet target(GameState state) { return FleetOrganization.find(state, "target"); }
    GameState world() throws Exception {
        var state = new OrbitalRendezvousTest().world();
        state = state.toBuilder().empires(state.empires().stream().map(empire -> !empire.id().equals("owner") ? empire
                : new Empire(empire.id(), empire.name(), empire.raceId(), empire.societyStructure(), empire.treasuryCredits(),
                empire.corporateTaxRate(), empire.controlledSystemIds(), empire.ministries(), empire.systemGovernorAssignments(),
                java.util.stream.Stream.concat(empire.unlockedTechIds().stream(), java.util.stream.Stream.of("energy_weapons", "laser_weapons"))
                        .toList(), empire.activeShipDesignIds())).toList()).build();
        var modules = new ArrayList<>(state.shipDesigns().getFirst().equippedModuleIds());
        modules.remove(CombatModuleCatalog.GUN); modules.add(CombatModuleCatalog.LASER);
        modules.remove(SensorModuleCatalog.ADVANCED); modules.add(SensorModuleCatalog.BASIC);
        var evaluated = ShipBlueprintFactory.evaluate(state, new ShipDesignSpecification("laser", "Laser", "owner",
                ShipRole.COMBAT_SHIP, "steel", modules, "steel", .5));
        assertTrue(evaluated.valid(), evaluated.errors().toString());
        var designs = new ArrayList<>(state.shipDesigns()); designs.add(evaluated.design()); state = state.withShipDesigns(designs);
        var original = hunter(state).ships().getFirst();
        var ship = new ShipInstance(original.id(), "laser", "owner", original.currentHullHealth(), 0,
                evaluated.design().fuelCapacityKg(), original.storedCargoKg()).withPowerState(original.powerState());
        return state.withFleets(List.of(hunter(state).withShips(List.of(ship))
                .withLocation(FleetLocation.at(FleetLocation.Site.orbit("earth", 500))),
                target(state).withLocation(FleetLocation.at(FleetLocation.Site.orbit("earth", 5000)))));
    }
    private GameState day(GameState state) {
        return state.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state), List.of(), List.of()))
                .withTurn(state.turn() + 1);
    }

    @Test void paidLaserApproachStopsOutsideCannonRangeWithFundedBraking() throws Exception {
        var initial = world(); var approach = FleetStandOffApproach.plan(initial, hunter(initial), target(initial));
        assertNotNull(approach);
        assertNotEquals(target(initial).location().current(), approach.destination());
        assertTrue(approach.separationMeters() > 100000); assertTrue(approach.separationMeters() < 1000000);
        assertTrue(approach.plan().orbital().maneuvers().get(1).burns().values().stream().allMatch(burn -> burn.propellantKg() > 0));
        var quoted = LocalTravel.arrivalPreview(initial, hunter(initial), approach.destination(), approach.plan());
        var queue = new CommandQueue(); queue.submit(new InterceptFleetCommand("owner", "hunter", "target"));
        assertNull(hunter(initial).interceptOrder());
        var ordered = queue.drainAndExecute(initial);
        var state = FleetInterceptProcessor.advance(ordered);
        assertEquals(approach.destination(), hunter(state).location().destination());
        assertEquals(hunter(initial).ships(), hunter(state).ships());
        for (int tick = 0; tick < 8 && hunter(state).location().inTransit(); tick++) state = day(state);
        assertFalse(hunter(state).location().inTransit());
        assertEquals(quoted.ships().getFirst().currentFuelKg(), hunter(state).ships().getFirst().currentFuelKg(), .00001);
        assertTrue(hunter(state).ships().getFirst().currentFuelKg() < hunter(initial).ships().getFirst().currentFuelKg());
        assertTrue(hunter(state).ships().getFirst().generatorFuelMassKg() < hunter(initial).ships().getFirst().generatorFuelMassKg());
        assertTrue(hunter(state).ships().getFirst().storedCargoKg().get("fusion_fuel_pellets")
                < hunter(initial).ships().getFirst().storedCargoKg().get("fusion_fuel_pellets"));
        double distance = ShipWeaponRange.separation(state, hunter(state), target(state));
        assertEquals(approach.separationMeters(), distance, .00001);
        assertTrue(ShipWeaponRange.reaches(state, hunter(state), target(state)));
        assertFalse(ShipWeaponRange.reaches(state, target(state), hunter(state)));
        assertNull(hunter(FleetInterceptProcessor.finishContacts(state)).interceptOrder());
    }

    @Test void unfundedApproachDoesNotSpendOrLaunchAndMovingTargetsKeepRendezvous() throws Exception {
        var state = world(); var fleet = hunter(state); var ship = fleet.ships().getFirst();
        var dry = new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), ship.currentHullHealth(), 0, 0,
                ship.storedCargoKg()).withPowerState(ship.powerState());
        state = state.withFleets(List.of(fleet.withShips(List.of(dry)), target(state)));
        assertNull(FleetStandOffApproach.plan(state, hunter(state), target(state)));
        var ordered = new InterceptFleetCommand("owner", "hunter", "target").apply(state);
        assertEquals(hunter(ordered), hunter(FleetInterceptProcessor.advance(ordered)));
        var moving = new OrbitalRendezvousTest().world();
        assertNull(FleetStandOffApproach.plan(moving, hunter(moving), target(moving)));
    }

    @Test void cancellationAndReloadPreserveCommittedStandOffMotionAndStores() throws Exception {
        var state = world();
        state = FleetInterceptProcessor.advance(new InterceptFleetCommand("owner", "hunter", "target").apply(state));
        var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        var restored = mapper.readValue(mapper.writeValueAsString(SaveGame.fromGameState(state, "now", 1, "now")), SaveGame.class)
                .toGameState(0, "RUNNING");
        assertEquals(hunter(state), hunter(restored));
        var canceled = new CancelInterceptFleetCommand("owner", "hunter").apply(restored);
        assertNull(hunter(canceled).interceptOrder());
        assertEquals(hunter(state).location(), hunter(canceled).location());
        assertEquals(hunter(state).ships(), hunter(canceled).ships());
        var advanced = day(state); var advancedCanceled = day(canceled);
        assertEquals(hunter(advanced).location(), hunter(advancedCanceled).location());
        assertEquals(hunter(advanced).ships(), hunter(advancedCanceled).ships());
    }
}
