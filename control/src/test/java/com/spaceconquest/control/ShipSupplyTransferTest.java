package com.spaceconquest.control;

import com.spaceconquest.control.command.CommandQueue;
import com.spaceconquest.control.command.RecoverFleetTravelCommand;
import com.spaceconquest.control.command.TransferShipSuppliesCommand;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.SaveGameManager;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.CargoPreservationState;
import com.spaceconquest.engine.ship.FleetLocation;
import com.spaceconquest.engine.ship.FleetProcessor;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipInstance;
import com.spaceconquest.engine.ship.ShipManufacturingProfile;
import com.spaceconquest.engine.ship.ShipPowerProcessor;
import com.spaceconquest.engine.ship.ShipPowerProfile;
import com.spaceconquest.engine.ship.ShipPowerResupply;
import com.spaceconquest.engine.ship.ShipPowerState;
import com.spaceconquest.engine.ship.ShipRole;
import com.spaceconquest.engine.ship.ShipSupplyTransfer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static com.spaceconquest.engine.ship.ShipSupplyTransfer.Destination.*;
import static com.spaceconquest.engine.ship.ShipSupplyTransfer.Source.*;
import static org.junit.jupiter.api.Assertions.*;

class ShipSupplyTransferTest {
    @TempDir Path directory;

    private GameState state() {
        var profile = new ShipPowerProfile(0, 100, 100, 500, 100, 200, 5000, 10, 2, 20, 0, .65,
                Map.of("rp1", new ShipPowerProfile.Fuel("rp1_kerosene", "liquid_oxygen", .28, 1.008),
                        "methalox", new ShipPowerProfile.Fuel("liquid_methane", "liquid_oxygen", .22, .924),
                        "uranium", new ShipPowerProfile.Fuel("refined_uranium", null, 1, 6e6)));
        var design = new ShipDesign("design", "Ship", "owner", ShipRole.EXPLORER, "steel",
                List.of("mod_chemical_rocket"), "steel", 0, 1000, 20000, 1000, 100, 1, 0, 2000,
                false, false, ShipManufacturingProfile.baseline(), profile);
        var donor = new ShipInstance("donor", "design", "owner", 100, 0, 900,
                Map.of("rp1_kerosene", 2800.0, "liquid_oxygen", 7200.0, "refined_uranium", 20.0));
        var receiver = new ShipInstance("receiver", "design", "owner", 100, 0, 100, Map.of())
                .withPowerState(new ShipPowerState(Map.of(), "rp1", "uranium", 12, false, .8, .7, 3, 4, 5, 6, 7,
                        new CargoPreservationState(Map.of("food_matrix", 42.0), Map.of("food_matrix", 5.0))));
        var site = FleetLocation.at(FleetLocation.Site.orbit("earth"));
        var earth = new Planet("earth", "Earth", "", 1, 9.81, 149600000, 0, 12000,
                "terrestrial", "air", true, 0, List.of(), List.of(), List.of());
        return GameState.builder().shipDesigns(List.of(design)).solarSystems(List.of(
                new SolarSystem("a", "A", "", 0, 0, 0, 1.989e30, 1, "yellow", List.of(earth), List.of())))
                .fleets(List.of(new Fleet("donorFleet", "Donor", "owner", "a", "", 0, 0, 0, false,
                        "PASSIVE", List.of(donor), site), new Fleet("receiverFleet", "Receiver", "owner", "a", "",
                        0, 0, 0, false, "PASSIVE", List.of(receiver), site))).build();
    }
    private ShipInstance ship(GameState state, String id) {
        return state.fleets().stream().flatMap(fleet -> fleet.ships().stream()).filter(ship -> ship.id().equals(id)).findFirst().orElseThrow();
    }
    private TransferShipSuppliesCommand command(ShipSupplyTransfer.Source source,
                                                ShipSupplyTransfer.Destination destination, String fuel, double kg) {
        return new TransferShipSuppliesCommand("donor", "receiver", source, destination, fuel, kg);
    }
    private GameState receiverFleet(GameState state, Fleet fleet) { return state.withFleets(List.of(state.fleets().getFirst(), fleet)); }

    @Test void freightMixtureTransferConservesBothMaterialsAndPreservesOtherElectricalState() {
        var initial = state();
        var transfer = command(CARGO, ELECTRICAL_FUEL, "rp1", 1000);
        assertTrue(transfer.validate(initial));
        var next = transfer.apply(initial);
        assertEquals(Map.of("rp1_kerosene", 280.0, "liquid_oxygen", 720.0), ship(next, "receiver").powerState().generatorMaterialsKg());
        assertEquals(2520, ship(next, "donor").storedCargoKg().get("rp1_kerosene"));
        assertEquals(6480, ship(next, "donor").storedCargoKg().get("liquid_oxygen"));
        var power = ship(next, "receiver").powerState();
        assertEquals(12, power.batteryChargeKwh());
        assertEquals(3, power.unmetEssentialHours());
        assertEquals(7, power.chargedInputKwhToday());
        assertEquals(ship(initial, "receiver").powerState().cargoPreservation(), power.cargoPreservation());
        assertFalse(power.arraysDeployed());
        assertEquals(100, ship(next, "receiver").currentFuelKg());
        assertEquals(initial.empires(), next.empires());
        assertTrue(ship(initial, "receiver").powerState().generatorMaterialsKg().isEmpty());
    }

    @Test void missingOxidizerCapacityMixtureAndInvalidRequestsRejectAtomically() {
        var initial = state();
        for (double kg : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY, 5001})
            assertSame(initial, command(CARGO, ELECTRICAL_FUEL, "rp1", kg).apply(initial));
        var donor = ship(initial, "donor");
        var dry = ShipPowerResupply.replace(initial, new ShipInstance(donor.id(), donor.designId(), donor.ownerEntityId(),
                100, 0, 900, Map.of("rp1_kerosene", 2800.0)));
        assertSame(dry, command(CARGO, ELECTRICAL_FUEL, "rp1", 1000).apply(dry));
        var stocked = command(CARGO, ELECTRICAL_FUEL, "rp1", 1000).apply(initial);
        assertSame(stocked, command(CARGO, ELECTRICAL_FUEL, "methalox", 100).apply(stocked));
        assertSame(initial, new TransferShipSuppliesCommand("donor", "donor", CARGO, ELECTRICAL_FUEL, "rp1", 1).apply(initial));
        assertSame(initial, command(CARGO, ELECTRICAL_FUEL, "missing", 1).apply(initial));
    }

    @Test void tankDonationsConserveStockIncludingWithinOneFleet() {
        var initial = state();
        var donor = ship(initial, "donor").withPowerState(new ShipPowerState(
                Map.of("rp1_kerosene", 280.0, "liquid_oxygen", 720.0), "rp1", "uranium", 0, false, 1, 1, 0, 0, 0, 0, 0));
        var combined = initial.withFleets(List.of(initial.fleets().getFirst().withShips(List.of(donor, ship(initial, "receiver")))));
        var next = command(TANK, ELECTRICAL_FUEL, "rp1", 1000).apply(combined);
        assertTrue(ship(next, "donor").powerState().generatorMaterialsKg().isEmpty());
        assertEquals(1000, ship(next, "receiver").generatorFuelMassKg());
        assertSame(next, command(TANK, ELECTRICAL_FUEL, "rp1", 1).apply(next));
        var fueled = command(TANK, PROPELLANT, "mod_chemical_rocket", 200).apply(next);
        assertEquals(700, ship(fueled, "donor").currentFuelKg());
        assertEquals(300, ship(fueled, "receiver").currentFuelKg());
    }

    @Test void propellantRequiresCompatibleTankAndReceiverCapacity() {
        var initial = state();
        var next = command(CARGO, PROPELLANT, "mod_chemical_rocket", 900).apply(initial);
        assertEquals(1000, ship(next, "receiver").currentFuelKg());
        assertEquals(2800 - 900 * .28, ship(next, "donor").storedCargoKg().get("rp1_kerosene"), 1e-9);
        assertSame(next, command(TANK, PROPELLANT, "mod_chemical_rocket", 1).apply(next));
        assertSame(initial, command(TANK, PROPELLANT, "mod_methalox_rocket", 1).apply(initial));
        var d = initial.shipDesigns().getFirst();
        var different = new ShipDesign("other", "Other", "owner", d.role(), "steel", List.of("mod_methalox_rocket"),
                "steel", 0, 1000, 20000, 1000, 100, 1, 0, 2000, false, false, d.manufacturingProfile(), d.powerProfile());
        var donor = new ShipInstance("donor", "other", "owner", 100, 0, 900, Map.of());
        var incompatible = ShipPowerResupply.replace(initial.withShipDesigns(List.of(d, different)), donor);
        assertSame(incompatible, command(TANK, PROPELLANT, "mod_chemical_rocket", 1).apply(incompatible));
    }

    @Test void differentOwnersSitesAndMovingFleetsCannotTransfer() {
        var initial = state();
        var fleet = initial.fleets().get(1);
        var transfer = command(CARGO, ELECTRICAL_FUEL, "rp1", 1);
        var moving = receiverFleet(initial, fleet.withLocation(fleet.location().depart(FleetLocation.Site.deepSpace(), 2)));
        assertSame(moving, transfer.apply(moving));
        var otherSite = receiverFleet(initial, fleet.withLocation(FleetLocation.at(FleetLocation.Site.surface("earth"))));
        assertSame(otherSite, transfer.apply(otherSite));
        var foreign = receiverFleet(initial, new Fleet(fleet.id(), fleet.name(), "foreign", "a", "", 0, 0, 0, false,
                "PASSIVE", fleet.ships(), fleet.location()));
        assertSame(foreign, transfer.apply(foreign));
        var crossing = receiverFleet(initial, new Fleet(fleet.id(), fleet.name(), "owner", "a", "b", 0, 0, .5, false,
                "PASSIVE", fleet.ships(), FleetLocation.at(FleetLocation.Site.deepSpace()), Fleet.MODE_POWER_INTERRUPTED, 4));
        assertSame(crossing, transfer.apply(crossing));
    }

    @Test void reactorFeedsUseDedicatedElectricalStorageOrDriveCargoAndRespectCapacity() {
        var initial = state();
        var electrical = command(CARGO, ELECTRICAL_FUEL, "uranium", 10).apply(initial);
        assertEquals(10, ship(electrical, "receiver").generatorFuelMassKg());
        assertEquals(10, ship(electrical, "donor").storedCargoKg().get("refined_uranium"));
        assertSame(electrical, command(CARGO, ELECTRICAL_FUEL, "uranium", 1).apply(electrical));
        var d = initial.shipDesigns().getFirst();
        var nuclear = new ShipDesign(d.id(), d.name(), "owner", d.role(), "steel", List.of("mod_fission_thruster"),
                "steel", 0, 1000, 10, 1000, 100, 1, 0, 2000, false, false, d.manufacturingProfile(), d.powerProfile());
        var ready = initial.withShipDesigns(List.of(nuclear));
        var next = command(CARGO, DRIVE_REACTOR, "refined_uranium", 10).apply(ready);
        assertEquals(10, ship(next, "receiver").storedCargoKg().get("refined_uranium"));
        assertSame(next, command(CARGO, DRIVE_REACTOR, "refined_uranium", 1).apply(next));
        assertSame(ready, command(TANK, DRIVE_REACTOR, "refined_uranium", 1).apply(ready));
    }

    @Test void queueRevalidatesStockAndRejectsASecondOversubscribedTransfer() {
        var initial = ShipPowerResupply.replace(state(), new ShipInstance("donor", "design", "owner", 100, 0, 900,
                Map.of("rp1_kerosene", 1120.0, "liquid_oxygen", 2880.0)));
        CommandQueue queue = new CommandQueue();
        var command = command(CARGO, ELECTRICAL_FUEL, "rp1", 2500);
        queue.submit(command);
        queue.submit(command);
        assertEquals(0, ship(initial, "receiver").generatorFuelMassKg());
        var next = queue.drainAndExecute(initial);
        assertEquals(2500, ship(next, "receiver").generatorFuelMassKg());
        assertEquals(420, ship(next, "donor").storedCargoKg().get("rp1_kerosene"), 1e-9);
    }

    @Test void stationaryInterruptedDepartureCanReceiveFuelRecoverAndPersistActualReserves() throws Exception {
        var initial = state();
        var fleet = initial.fleets().get(1).withLocation(new FleetLocation(FleetLocation.Site.orbit("earth"),
                FleetLocation.Site.deepSpace(), 0, 2)).withInterruptedTravel();
        var stopped = receiverFleet(initial, fleet);
        var recover = new RecoverFleetTravelCommand(fleet.id());
        assertFalse(recover.validate(stopped));
        var supplied = command(CARGO, ELECTRICAL_FUEL, "rp1", 2000).apply(stopped);
        assertTrue(recover.validate(supplied));
        assertEquals(Fleet.MODE_POWER_INTERRUPTED, supplied.fleets().get(1).interstellarMode());
        var resumed = recover.apply(supplied);
        var processor = new FleetProcessor();
        for (int day = 0; day < 2; day++) resumed = resumed.withFleets(processor.processFleetMovements(
                ShipPowerProcessor.advanceDay(resumed), List.of(), List.of()));
        assertTrue(resumed.fleets().get(1).location().isAt(FleetLocation.Site.deepSpace()));
        assertEquals(100, ship(resumed, "receiver").currentFuelKg());
        assertTrue(ship(resumed, "receiver").generatorFuelMassKg() < 2000);
        var save = directory.resolve("resupply.scsave").toFile();
        var manager = new SaveGameManager(directory);
        manager.save(save, resumed, 1, "2027-01-01T08:00:00");
        var loaded = manager.load(save).toGameState(0, "RUNNING");
        assertEquals(resumed.fleets(), loaded.fleets());
        var midTransfer = receiverFleet(stopped, fleet.withLocation(new FleetLocation(fleet.location().current(),
                fleet.location().destination(), .25, 2)));
        assertSame(midTransfer, command(CARGO, ELECTRICAL_FUEL, "rp1", 2000).apply(midTransfer));
    }
}
