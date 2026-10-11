package com.spaceconquest.control;

import com.fasterxml.jackson.databind.*;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EscortFleetCommandTest {
    private ShipDesign design(String id, String role, double thrust) {
        return new ShipDesign(id, id, "owner", role, "steel", List.of("mod_chemical_rocket"), "steel", 0,
                1000, 30000, 1000, 100, 1, 0, thrust, false, false, ShipManufacturingProfile.baseline(), null);
    }
    private SolarSystem system(String id, double x) {
        return new SolarSystem(id, id, "", x, 0, 0, 1, 1, "yellow", List.of(), List.of());
    }
    private GameState world() {
        var cargo = new ShipInstance("cargo", "cargo-design", "owner", 100, 0, 1000, Map.of("steel", 50.0));
        var escort = new ShipInstance("warship", "warship-design", "owner", 100, 0, 1000, Map.of());
        return GameState.builder().solarSystems(List.of(system("a", 0), system("b", 1e-7)))
                .shipDesigns(List.of(design("cargo-design", ShipRole.CARGO_TRANSPORT, 100000),
                        design("warship-design", ShipRole.COMBAT_SHIP, 1000)))
                .fleets(List.of(new Fleet("carrier", "Carrier", "owner", "a", "", 0, 0, 0, false, "PASSIVE", List.of(cargo)),
                        new Fleet("escort", "Escort", "owner", "a", "", 0, 0, 0, false, "PASSIVE", List.of(escort))))
                .build();
    }
    private EscortFleetCommand join() { return new EscortFleetCommand("owner", "escort", "carrier"); }
    private GameState replace(GameState state, Fleet replacement) {
        return state.withFleets(state.fleets().stream().map(fleet -> fleet.id().equals(replacement.id()) ? replacement : fleet).toList());
    }

    @Test void queuedEscortJoinsConvoyWithoutChangingShipsAndDetachesWithOriginalIdentity() {
        var route = new com.spaceconquest.engine.logistics.TradeRoute("route", "Route", "owner", "one", "two", "steel",
                100, 0, 1000, List.of("cargo"), 0, true);
        var initial = world().withTradeRoutes(List.of(route));
        var queue = new CommandQueue();
        queue.submit(join());
        queue.submit(join());
        assertEquals(2, initial.fleets().size());
        var joined = queue.drainAndExecute(initial);
        assertEquals(1, joined.fleets().size());
        var convoy = joined.fleets().getFirst();
        assertEquals("carrier", convoy.id());
        assertEquals("ESCORT", convoy.fleetStance());
        assertEquals(List.of("warship"), convoy.escortOrder().shipIds());
        assertEquals(initial.fleets().stream().flatMap(fleet -> fleet.ships().stream()).toList(), convoy.ships());
        assertFalse(new CancelEscortFleetCommand("other", "carrier").validate(joined));
        queue.submit(new CancelEscortFleetCommand("owner", "carrier"));
        var detached = queue.drainAndExecute(joined);
        assertEquals(initial.fleets(), detached.fleets());
        assertEquals(initial.tradeRoutes(), detached.tradeRoutes());
    }

    @Test void requiresOwnedMilitaryEscortAtSameSiteAndRejectsChangedLiveSelection() {
        var initial = world();
        assertFalse(new EscortFleetCommand("other", "escort", "carrier").validate(initial));
        assertFalse(new EscortFleetCommand("owner", "carrier", "escort").validate(initial));
        assertFalse(new EscortFleetCommand("owner", "escort", "escort").validate(initial));
        var source = FleetOrganization.find(initial, "escort");
        var remote = replace(initial, source.withLocation(FleetLocation.at(FleetLocation.Site.deepSpace("remote"))));
        assertFalse(join().validate(remote));
        var foreign = initial.toBuilder().shipDesigns(List.of(initial.shipDesigns().getFirst())).build();
        var queue = new CommandQueue();
        queue.submit(join());
        assertEquals(initial.fleets(), queue.drainAndExecute(foreign).fleets());
        var traveling = replace(initial, source.withLocation(source.location().depart(FleetLocation.Site.deepSpace("other"), 2)));
        assertFalse(join().validate(traveling));
        var route = new com.spaceconquest.engine.logistics.TradeRoute("route", "Route", "owner", "one", "two", "steel",
                100, 0, 1000, List.of("warship"), 0, true);
        assertFalse(join().validate(initial.withTradeRoutes(List.of(route))));
    }

    @Test void sharedDepartureUsesSlowestShipAndRejectsAnUnfueledEscort() {
        var initial = world();
        var alone = InterstellarTravel.plan(initial, FleetOrganization.find(initial, "carrier"), "b");
        var joined = join().apply(initial);
        var convoy = joined.fleets().getFirst();
        var plan = InterstellarTravel.plan(joined, convoy, "b");
        assertNotNull(plan);
        assertTrue(plan.days() > alone.days());
        assertEquals(Set.of("cargo", "warship"), plan.fuelBudgetKg().keySet());
        var movement = new MoveFleetCommand("carrier", "b");
        assertTrue(movement.validate(joined));
        var departing = movement.apply(joined);
        assertEquals(convoy.escortOrder(), departing.fleets().getFirst().escortOrder());
        var empty = convoy.withShips(convoy.ships().stream().map(ship -> "warship".equals(ship.id())
                ? new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), ship.currentHullHealth(),
                ship.currentShieldHealth(), 0, ship.storedCargoKg()) : ship).toList());
        var unready = joined.withFleets(List.of(empty));
        assertFalse(movement.validate(unready));
        assertSame(unready, movement.apply(unready));
    }

    @Test void dailyTravelSaveLoadAndMidflightDetachmentPreservePaidBudgets() throws Exception {
        var joined = join().apply(world());
        var moving = new MoveFleetCommand("carrier", "b").apply(joined);
        var advanced = moving.withFleets(new FleetProcessor().processFleetMovements(moving.fleets(), List.of(), List.of()));
        var convoy = advanced.fleets().getFirst();
        assertNotNull(convoy.escortOrder());
        assertTrue(convoy.interstellarElapsedDays() > 0);
        var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        var save = SaveGame.fromGameState(advanced, "now", 1, "now");
        var loaded = mapper.readValue(mapper.writeValueAsString(save), SaveGame.class).toGameState(0, "RUNNING");
        assertEquals(convoy.escortOrder(), loaded.fleets().getFirst().escortOrder());
        var legacy = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(world().fleets().getFirst());
        legacy.remove("escortOrder");
        assertNull(mapper.treeToValue(legacy, Fleet.class).escortOrder());
        var detached = new CancelEscortFleetCommand("owner", "carrier").apply(loaded);
        assertEquals(2, detached.fleets().size());
        var carrier = FleetOrganization.find(detached, "carrier");
        var escort = FleetOrganization.find(detached, "escort");
        assertEquals(convoy.location(), escort.location());
        assertEquals(convoy.interstellarElapsedDays(), escort.interstellarElapsedDays());
        assertEquals(convoy.transitProgress(), carrier.transitProgress());
        assertEquals(Set.of("cargo"), carrier.interstellarFuelBudgetKg().keySet());
        assertEquals(Set.of("warship"), escort.interstellarFuelBudgetKg().keySet());
        assertEquals(convoy.ships(), detached.fleets().stream().flatMap(fleet -> fleet.ships().stream()).toList());
    }

    @Test void lossesRemoveStaleEscortMembershipWithoutCreatingReplacementShips() {
        var joined = join().apply(world());
        var convoy = joined.fleets().getFirst();
        var lost = convoy.withShips(List.of(convoy.ships().getFirst()));
        assertNull(lost.escortOrder());
        assertEquals("PASSIVE", lost.fleetStance());
        assertFalse(new CancelEscortFleetCommand("owner", "carrier").validate(joined.withFleets(List.of(lost))));
        var cargoLost = convoy.withShips(List.of(convoy.ships().getLast()));
        assertNull(cargoLost.escortOrder());
        assertEquals("warship", cargoLost.ships().getFirst().id());
        var initial = new SetFleetStanceCommand("carrier", "AGGRESSIVE").apply(world());
        var detached = new CancelEscortFleetCommand("owner", "carrier").apply(join().apply(initial));
        assertEquals("AGGRESSIVE", FleetOrganization.find(detached, "carrier").fleetStance());
    }
}
