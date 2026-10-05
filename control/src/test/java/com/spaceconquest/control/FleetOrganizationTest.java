package com.spaceconquest.control;

import com.spaceconquest.control.command.CommandQueue;
import com.spaceconquest.control.command.MergeFleetsCommand;
import com.spaceconquest.control.command.RenameFleetCommand;
import com.spaceconquest.control.command.SplitFleetCommand;
import com.spaceconquest.control.command.TransferFleetShipsCommand;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.SaveGameManager;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.habitation.PassengerManifest;
import com.spaceconquest.engine.logistics.TradeRoute;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FleetLocation;
import com.spaceconquest.engine.ship.FleetOrganization;
import com.spaceconquest.engine.ship.FleetProcessor;
import com.spaceconquest.engine.ship.FlightMotion;
import com.spaceconquest.engine.ship.InterstellarTravel;
import com.spaceconquest.engine.ship.RescueOrder;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipInstance;
import com.spaceconquest.engine.ship.ShipManufacturingProfile;
import com.spaceconquest.engine.ship.ShipPowerState;
import com.spaceconquest.engine.ship.ShipRole;
import com.spaceconquest.engine.ship.ShipSupplyTransfer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class FleetOrganizationTest {
    @TempDir Path directory;
    private ShipDesign design(String id, String role, double thrust) {
        return new ShipDesign(id, id, "owner", role, "steel", List.of("mod_chemical_rocket"), "steel", 0,
                1000, 30000, 1000, 100, 1, 0, thrust, false, false, ShipManufacturingProfile.baseline(), null);
    }
    private GameState state() {
        var fighter = new ShipInstance("fighter", "fast", "owner", 92, 15, 1000, Map.of("steel", 25.0));
        var cargo = new ShipInstance("cargo", "slow", "owner", 87, 4, 1000, Map.of("food_matrix", 50.0),
                10, "human", ShipInstance.MODE_CONSCIOUS, ShipPowerState.empty());
        var escort = new ShipInstance("escort", "fast", "owner", 80, 20, 900, Map.of());
        var target = new ShipInstance("targetShip", "fast", "owner", 100, 0, 800, Map.of());
        return GameState.builder().solarSystems(List.of(system("a", 0), system("b", 1e-7)))
                .shipDesigns(List.of(design("fast", ShipRole.COMBAT_SHIP, 100000), design("slow", ShipRole.CARGO_TRANSPORT, 1000)))
                .fleets(List.of(new Fleet("source", "Mixed fleet", "owner", "a", "", 5, 6, 0, false, "ESCORT", List.of(fighter, cargo, escort)),
                        new Fleet("target", "Destination", "owner", "a", "", 5, 6, 0, false, "PASSIVE", List.of(target))))
                .passengerManifests(List.of(new PassengerManifest("cargo", "earth", "moon", "human", Map.of(30, 10L)))).build();
    }
    private SolarSystem system(String id, double x) { return new SolarSystem(id, id, "", x, 0, 0, 1, 1, "yellow", List.of(), List.of()); }
    private Fleet fleet(GameState state, String id) { return FleetOrganization.find(state, id); }
    private Map<String, ShipInstance> ships(GameState state) {
        return state.fleets().stream().flatMap(fleet -> fleet.ships().stream()).collect(Collectors.toMap(ShipInstance::id, ship -> ship));
    }
    private GameState replace(GameState state, Fleet fleet) {
        return state.withFleets(state.fleets().stream().map(item -> item.id().equals(fleet.id()) ? fleet : item).toList());
    }
    private TradeRoute route(String id, String ship) { return new TradeRoute(id, id, "owner", "one", "two", "food_matrix",
            100, 0, 1000, List.of(ship), 0, true); }

    @Test void mergeAndTransferKeepAllShipStatePassengersAndRouteIdentity() {
        var initial = state().withTradeRoutes(List.of(route("route", "cargo")));
        var moved = new TransferFleetShipsCommand("owner", "source", "target", List.of("cargo")).apply(initial);
        assertEquals(List.of("fighter", "escort"), fleet(moved, "source").ships().stream().map(ShipInstance::id).toList());
        assertEquals(List.of("targetShip", "cargo"), fleet(moved, "target").ships().stream().map(ShipInstance::id).toList());
        assertEquals(ships(initial), ships(moved));
        assertEquals(initial.passengerManifests(), moved.passengerManifests());
        assertEquals(initial.tradeRoutes(), moved.tradeRoutes());
        var merged = new MergeFleetsCommand("owner", "source", "target").apply(moved);
        assertEquals(1, merged.fleets().size());
        assertEquals("Destination", merged.fleets().getFirst().name());
        assertEquals("PASSIVE", merged.fleets().getFirst().fleetStance());
        assertEquals(ships(initial), ships(merged));
    }

    @Test void detachingAndRenamingAreTrackedTickCommandsWithImmutableSelections() {
        var initial = state();
        var selection = new ArrayList<>(List.of("cargo"));
        var command = new SplitFleetCommand("owner", "source", selection, "new", " Civilian wing ");
        selection.clear();
        var queue = new CommandQueue();
        var receipt = queue.submitTracked(command);
        assertFalse(receipt.isDone());
        assertEquals(2, initial.fleets().size());
        var split = queue.drainAndExecute(initial);
        assertEquals(3, split.fleets().size());
        assertTrue(receipt.isDone());
        assertEquals("Civilian wing", fleet(split, "new").name());
        assertEquals(ships(initial), ships(split));
        assertEquals("ESCORT", fleet(split, "new").fleetStance());
        var renamed = new RenameFleetCommand("owner", "new", "Freighter group").apply(split);
        assertEquals("new", fleet(renamed, "new").id());
        assertEquals("Freighter group", fleet(renamed, "new").name());
        assertEquals(ships(initial), ships(renamed));
    }

    @Test void mixedFleetTravelUsesTheSlowestAccelerationAndFuelLimitedPeak() {
        var initial = state();
        Fleet mixed = fleet(initial, "source");
        var all = InterstellarTravel.plan(initial, mixed, "b");
        var fast = InterstellarTravel.plan(initial, mixed.withShips(List.of(mixed.ships().getFirst())), "b");
        var slow = InterstellarTravel.plan(initial, mixed.withShips(List.of(mixed.ships().get(1))), "b");
        assertNotNull(all);
        assertTrue(all.days() > fast.days());
        assertEquals(slow.accelerationMps2(), all.accelerationMps2(), 1e-9);
        assertEquals(slow.peakSpeedMps(), all.peakSpeedMps(), 1e-9);
        assertEquals(slow.days(), all.days(), 1e-9);
        assertEquals(Set.of("fighter", "cargo", "escort"), all.fuelBudgetKg().keySet());
        var split = new SplitFleetCommand("owner", "source", List.of("cargo"), "civilian", "Civilian").apply(initial);
        assertTrue(InterstellarTravel.plan(split, fleet(split, "source"), "b").days() < all.days());
    }

    @Test void splittingInFlightPreservesPositionProgressAndPerShipFuelCommitments() {
        var initial = state();
        Fleet source = fleet(initial, "source");
        var crossing = InterstellarTravel.plan(initial, source, "b");
        var trajectory = new FlightMotion.Trajectory(100, 10, 1, 100, 90, 1000, 100);
        for (Fleet traveling : List.of(
                source.withLocation(source.location().depart(FleetLocation.Site.orbit("earth"), 2).advanceDays(.25)),
                flight(source, Fleet.MODE_WARP, .25, 4, 0, 0, 1, 0, Map.of(), null),
                flight(source, Fleet.MODE_SUBLIGHT, .2, crossing.days(), crossing.distanceMeters(), crossing.accelerationMps2(),
                        1, crossing.peakSpeedMps(), crossing.fuelBudgetKg(), null),
                flight(source, Fleet.MODE_RECOVERY, .1, 10, 1e8, 1, 0, 100,
                        Map.of("fighter", 100.0, "cargo", 200.0, "escort", 50.0), trajectory.at(50)),
                flight(source, Fleet.MODE_POWER_INTERRUPTED, .1, 10, 1e8, 1, 0, 100,
                        Map.of("fighter", 100.0, "cargo", 200.0), new FlightMotion(1e7, 100, null, 0)))) {
            var before = replace(initial, traveling);
            var after = new SplitFleetCommand("owner", "source", List.of("cargo"), "new", "Detached").apply(before);
            Fleet detached = fleet(after, "new");
            assertNotNull(detached);
            assertEquals(traveling.location(), detached.location());
            assertEquals(traveling.flightMotion(), detached.flightMotion());
            assertEquals(traveling.transitProgress(), detached.transitProgress());
            assertEquals(traveling.interstellarElapsedDays(), detached.interstellarElapsedDays());
            assertEquals(traveling.interstellarMode(), detached.interstellarMode());
            assertEquals(traveling.interstellarFuelBudgetKg().get("cargo"), detached.interstellarFuelBudgetKg().get("cargo"));
            assertFalse(fleet(after, "source").interstellarFuelBudgetKg().containsKey("cargo"));
            assertEquals(ships(before), ships(after));
            var rejoined = new MergeFleetsCommand("owner", "new", "source").apply(after);
            assertNull(fleet(rejoined, "new"));
            assertEquals(traveling.interstellarFuelBudgetKg(), fleet(rejoined, "source").interstellarFuelBudgetKg());
            assertEquals(ships(before), ships(rejoined));
        }
    }
    private Fleet flight(Fleet source, String mode, double progress, double days, double distance, double acceleration,
                         double elapsed, double peak, Map<String, Double> budget, FlightMotion motion) {
        return new Fleet(source.id(), source.name(), source.ownerEntityId(), "a", "b", 5, 6, progress,
                mode.equals(Fleet.MODE_WARP), source.fleetStance(), source.ships(), FleetLocation.at(FleetLocation.Site.deepSpace()),
                mode, days, distance, acceleration, elapsed, peak, budget, motion);
    }

    @Test void remoteTransfersForeignOwnersInvalidSelectionsAndConflictingRoutesRejectAtomically() {
        var initial = state();
        var command = new MergeFleetsCommand("owner", "source", "target");
        var remote = replace(initial, fleet(initial, "target").withLocation(FleetLocation.at(FleetLocation.Site.orbit("earth"))));
        assertSame(remote, command.apply(remote));
        var conflict = initial.withTradeRoutes(List.of(route("one", "cargo"), route("two", "targetShip")));
        assertSame(conflict, command.apply(conflict));
        assertTrue(new TransferFleetShipsCommand("owner", "source", "target", List.of("escort")).validate(conflict));
        for (var transfer : List.of(new TransferFleetShipsCommand("other", "source", "target", List.of("cargo")),
                new TransferFleetShipsCommand("owner", "source", "source", List.of("cargo")),
                new TransferFleetShipsCommand("owner", "source", "target", List.of("missing")),
                new TransferFleetShipsCommand("owner", "source", "target", List.of("cargo", "cargo"))))
            assertSame(initial, transfer.apply(initial));
        for (var split : List.of(new SplitFleetCommand("owner", "source", List.of(), "new", "Name"),
                new SplitFleetCommand("owner", "source", List.of("cargo"), "target", "Name"),
                new SplitFleetCommand("owner", "source", List.of("cargo"), "new", " "),
                new SplitFleetCommand("owner", "source", List.of("fighter", "cargo", "escort"), "new", "Name")))
            assertSame(initial, split.apply(initial));
        assertFalse(command.validate(null));
    }

    @Test void membershipCannotChangeDuringAnActiveRescueButNamingCan() {
        var initial = state();
        Fleet source = fleet(initial, "source");
        var order = new RescueOrder("target", "fighter", "targetShip", ShipSupplyTransfer.Source.CARGO,
                ShipSupplyTransfer.Destination.PROPELLANT, "mod_chemical_rocket", 10);
        var trajectory = new FlightMotion.Trajectory(0, 0, 1, 100, 100, 0, 90, 10, order);
        var busy = replace(initial, flight(source, Fleet.MODE_RECOVERY, 0, 1, 1e8, 1, 0, 100, Map.of(), trajectory.at(0)));
        assertFalse(new SplitFleetCommand("owner", "source", List.of("cargo"), "new", "Detached").validate(busy));
        assertFalse(new TransferFleetShipsCommand("owner", "target", "source", List.of("targetShip")).validate(busy));
        assertTrue(new RenameFleetCommand("owner", "source", "Rescue wing").validate(busy));
    }

    @Test void saveLoadKeepsSplitMembershipAndNextMovementWithoutDoubleFuelBurn() throws Exception {
        var initial = state();
        var source = fleet(initial, "source");
        var plan = InterstellarTravel.plan(initial, source, "b");
        var traveling = flight(source, plan.mode(), 0, plan.days(), plan.distanceMeters(), plan.accelerationMps2(),
                0, plan.peakSpeedMps(), plan.fuelBudgetKg(), null);
        var split = new SplitFleetCommand("owner", "source", List.of("cargo"), "new", "Detached").apply(replace(initial, traveling));
        var manager = new SaveGameManager(directory);
        var file = directory.resolve("fleets.scsave").toFile();
        manager.save(file, split, 1, "2027-01-01T08:00:00");
        var loaded = manager.load(file).toGameState(0, "RUNNING");
        assertEquals(split.fleets(), loaded.fleets());
        var processor = new FleetProcessor();
        var actual = processor.processFleetMovements(split.fleets(), List.of(), List.of());
        var original = processor.processFleetMovements(replace(initial, traveling).fleets(), List.of(), List.of());
        assertEquals(ships(split.withFleets(original)), ships(split.withFleets(actual)));
        assertEquals(actual, processor.processFleetMovements(loaded.fleets(), List.of(), List.of()));
    }
}
