package com.spaceconquest.control;

import com.spaceconquest.control.command.CommandOutcome;
import com.spaceconquest.control.command.CommandQueue;
import com.spaceconquest.control.command.ShareFleetFuelCommand;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.SaveGameManager;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.logistics.TradeRoute;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static com.spaceconquest.engine.ship.ShipSupplyTransfer.Destination.*;
import static com.spaceconquest.engine.ship.ShipSupplyTransfer.Source.*;
import static org.junit.jupiter.api.Assertions.*;

class FleetFuelSharingTest {
    @TempDir Path directory;
    private GameState state() {
        var profile = new ShipPowerProfile(0, 100, 100, 500, 100, 200, 5000, 10, 2, 20, 0, .65,
                Map.of("rp1", new ShipPowerProfile.Fuel("rp1_kerosene", "liquid_oxygen", .28, 1.008),
                        "methalox", new ShipPowerProfile.Fuel("liquid_methane", "liquid_oxygen", .22, .924),
                        "uranium", new ShipPowerProfile.Fuel("refined_uranium", null, 1, 6e6)));
        var design = new ShipDesign("design", "Supply ship", "owner", ShipRole.CARGO_TRANSPORT, "steel",
                List.of("mod_chemical_rocket"), "steel", 0, 1000, 20000, 1000, 100, 1, 0, 2000,
                false, false, ShipManufacturingProfile.baseline(), profile);
        var donor = new ShipInstance("donor", "design", "owner", 100, 0, 900,
                Map.of("rp1_kerosene", 2800.0, "liquid_oxygen", 7200.0, "refined_uranium", 10.0))
                .withPowerState(power(1000, 0));
        var receivers = List.of(new ShipInstance("one", "design", "owner", 100, 0, 100, Map.of()),
                new ShipInstance("two", "design", "owner", 100, 0, 100, Map.of()));
        return GameState.builder().shipDesigns(List.of(design)).solarSystems(List.of(
                system("a"), system("b"))).fleets(List.of(new Fleet("fleet", "Supply fleet", "owner", "a", "",
                0, 0, 0, false, "PASSIVE", List.of(donor, receivers.getFirst(), receivers.getLast())))).build();
    }
    private SolarSystem system(String id) {
        return new SolarSystem(id, id, "", 0, 0, 0, 1.989e30, 1, "yellow", List.of(), List.of());
    }
    private ShipPowerState power(double chemical, double nuclear) {
        return new ShipPowerState(Map.of("rp1_kerosene", chemical * .28, "liquid_oxygen", chemical * .72,
                "refined_uranium", nuclear), "rp1", "uranium", 0, false, 1, 1, 0, 0, 0, 0, 0);
    }
    private ShipInstance ship(GameState state, String id) {
        return state.fleets().stream().flatMap(fleet -> fleet.ships().stream()).filter(ship -> ship.id().equals(id)).findFirst().orElseThrow();
    }
    private FleetFuelSharing.Request request(ShipSupplyTransfer.Source source, ShipSupplyTransfer.Destination destination,
                                              String fuel, double target, double reserve) {
        return new FleetFuelSharing.Request("owner", "fleet", List.of("donor"), List.of("one", "two"), source,
                destination, fuel, target, reserve);
    }
    private ShareFleetFuelCommand command(GameState state, FleetFuelSharing.Request request) {
        return new ShareFleetFuelCommand(request, FleetFuelSharing.preview(state, request).transfers());
    }
    @Test void cargoSupplyFillsMultipleShipsConservesMixturesAndPersistsWithoutMutation() throws Exception {
        var initial = state();
        var request = request(CARGO, PROPELLANT, "mod_chemical_rocket", .9, .25);
        var preview = FleetFuelSharing.preview(initial, request);
        assertEquals(1600, preview.totalKg());
        assertEquals(2, preview.transfers().size());
        assertEquals(0, preview.balances().getLast().shortfallKg());
        var next = command(initial, request).apply(initial);
        assertEquals(900, ship(next, "one").currentFuelKg());
        assertEquals(900, ship(next, "two").currentFuelKg());
        assertEquals(2800 - 1600 * .28, ship(next, "donor").storedCargoKg().get("rp1_kerosene"), 1e-9);
        assertEquals(7200 - 1600 * .72, ship(next, "donor").storedCargoKg().get("liquid_oxygen"), 1e-9);
        assertEquals(ship(initial, "donor").powerState(), ship(next, "donor").powerState());
        assertEquals(100, ship(initial, "one").currentFuelKg());
        assertEquals(initial.empires(), next.empires());
        var manager = new SaveGameManager(directory);
        var file = directory.resolve("sharing.scsave").toFile();
        manager.save(file, next, 1, "2027-01-01T08:00:00");
        assertEquals(next.fleets(), manager.load(file).toGameState(0, "RUNNING").fleets());
    }
    @Test void tankSharingProtectsSupplierPropellantAndReportsPartialAllocationInSelectionOrder() {
        var initial = state();
        var request = request(TANK, PROPELLANT, "mod_chemical_rocket", 1, .25);
        var plan = FleetFuelSharing.preview(initial, request);
        assertEquals(650, plan.totalKg(), 1e-7);
        assertEquals("one", plan.transfers().getFirst().receiverShipId());
        assertFalse(plan.explanations().isEmpty());
        var next = command(initial, request).apply(initial);
        assertEquals(250, ship(next, "donor").currentFuelKg(), 1e-7);
        assertEquals(750, ship(next, "one").currentFuelKg(), 1e-7);
        assertEquals(100, ship(next, "two").currentFuelKg());
    }
    @Test void electricalTankSharingRetains48HoursAtActualOutputAndDoesNotTouchMainFuel() {
        var initial = state();
        // Use chemical generation only so the nuclear generator cannot cover the reserve.
        var d = initial.shipDesigns().getFirst();
        var p = d.powerProfile();
        var chemical = new ShipPowerProfile(0, 100, 0, 500, 100, 200, 5000, 0, 2, 20, 0, .65, p.fuels());
        initial = initial.withShipDesigns(List.of(new ShipDesign(d.id(), d.name(), d.ownerEntityId(), d.role(), d.hullMaterialId(),
                d.equippedModuleIds(), d.armorMaterialId(), 0, 1000, 20000, 1000, 100, 1, 0, 2000, false, false,
                d.manufacturingProfile(), chemical)));
        var request = request(TANK, ELECTRICAL_FUEL, "rp1", 1, .25);
        var next = command(initial, request).apply(initial);
        assertEquals(96 / 1.008, ship(next, "donor").generatorFuelMassKg(), 1e-6);
        assertEquals(1000 - 96 / 1.008, ship(next, "one").generatorFuelMassKg(), 1e-6);
        assertEquals(900, ship(next, "donor").currentFuelKg());
        assertTrue(ShipArrivalReserve.check(chemical, ship(next, "donor").powerState(), 2, 0, ShipSolarEnvironment.DARK).ready());
    }
    @Test void multipleSuppliersCombineStockAndMissingOxidizerCannotCreateFuel() {
        var initial = state();
        var donor = ship(initial, "donor");
        var another = new ShipInstance("another", "design", "owner", 100, 0, 900,
                Map.of("rp1_kerosene", 140.0, "liquid_oxygen", 360.0)).withPowerState(power(1000, 0));
        initial = ShipPowerResupply.replace(initial, new ShipInstance(donor.id(), donor.designId(), donor.ownerEntityId(), 100, 0, 900,
                Map.of("rp1_kerosene", 140.0, "liquid_oxygen", 360.0)).withPowerState(donor.powerState()));
        var ships = new ArrayList<>(initial.fleets().getFirst().ships());
        ships.add(another);
        initial = initial.withFleets(List.of(initial.fleets().getFirst().withShips(ships)));
        var request = new FleetFuelSharing.Request("owner", "fleet", List.of("donor", "another"), List.of("one"),
                CARGO, PROPELLANT, "mod_chemical_rocket", 1, .25);
        var plan = FleetFuelSharing.preview(initial, request);
        assertEquals(2, plan.transfers().size());
        assertEquals(900, plan.totalKg());
        var dry = ShipPowerResupply.replace(initial, new ShipInstance("donor", "design", "owner", 100, 0, 900,
                Map.of("rp1_kerosene", 140.0)).withPowerState(donor.powerState()));
        assertEquals(500, FleetFuelSharing.preview(dry, request).totalKg(), 1e-9);
    }
    @Test void reactorFeedSharingProtectsSuppliersOwnDriveFeedAndReceiverCargoCapacity() {
        var initial = state();
        var d = initial.shipDesigns().getFirst();
        initial = initial.withShipDesigns(List.of(new ShipDesign(d.id(), d.name(), "owner", d.role(), "steel", List.of("mod_fission_thruster"),
                "steel", 0, 1000, 20000, 1000, 100, 1, 0, 2000, false, false, d.manufacturingProfile(), d.powerProfile())));
        initial = ShipPowerResupply.replace(initial, new ShipInstance("donor", "design", "owner", 100, 0, 900,
                Map.of("refined_uranium", 1.0)).withPowerState(power(1000, 0)));
        var request = request(CARGO, DRIVE_REACTOR, "refined_uranium", 1, .25);
        var next = command(initial, request).apply(initial);
        assertEquals(.9, ship(next, "donor").storedCargoKg().get("refined_uranium"), 1e-7);
        assertEquals(.1, ship(next, "one").storedCargoKg().get("refined_uranium"), 1e-7);
        assertEquals(900, ship(next, "donor").currentFuelKg());
    }
    @Test void changedStockRejectsEntireReviewedAllocationAndTrackedReceiptReportsRejection() {
        var initial = state();
        var request = request(CARGO, PROPELLANT, "mod_chemical_rocket", 1, .25);
        var reviewed = command(initial, request);
        CommandQueue queue = new CommandQueue();
        var receipt = queue.submitTracked(reviewed);
        var donor = ship(initial, "donor");
        var changed = ShipPowerResupply.replace(initial, new ShipInstance("donor", "design", "owner", 100, 0, 900,
                Map.of("rp1_kerosene", 100.0, "liquid_oxygen", 100.0)).withPowerState(donor.powerState()));
        assertSame(changed, queue.drainAndExecute(changed));
        assertEquals(CommandOutcome.REJECTED, receipt.join());
        assertEquals(100, ship(changed, "one").currentFuelKg());
        assertEquals(100, ship(changed, "two").currentFuelKg());
    }
    @Test void invalidSelectionsOwnershipMovementLegacySuppliersAndTradeFreightReject() {
        var initial = state();
        for (var bad : List.of(new FleetFuelSharing.Request("other", "fleet", List.of("donor"), List.of("one"), CARGO, PROPELLANT,
                        "mod_chemical_rocket", 1, .25), request(CARGO, PROPELLANT, "mod_chemical_rocket", Double.NaN, .25),
                new FleetFuelSharing.Request("owner", "fleet", List.of("one"), List.of("one"), CARGO, PROPELLANT, "mod_chemical_rocket", 1, .25),
                new FleetFuelSharing.Request("owner", "fleet", List.of("donor", "donor"), List.of("one"), CARGO, PROPELLANT, "mod_chemical_rocket", 1, .25)))
            assertFalse(FleetFuelSharing.preview(initial, bad).available());
        var request = request(CARGO, PROPELLANT, "mod_chemical_rocket", 1, .25);
        var moving = initial.withFleets(List.of(initial.fleets().getFirst().withLocation(
                initial.fleets().getFirst().location().depart(FleetLocation.Site.orbit("body"), 2))));
        assertFalse(FleetFuelSharing.preview(moving, request).available());
        var route = new TradeRoute("route", "Freight", "owner", "a", "b", "rp1_kerosene", 1, 0, 20000, List.of("donor"), 0, true);
        assertFalse(FleetFuelSharing.preview(initial.withTradeRoutes(List.of(route)), request).available());
        var d = initial.shipDesigns().getFirst();
        var legacy = initial.withShipDesigns(List.of(new ShipDesign(d.id(), d.name(), "owner", d.role(), "steel", d.equippedModuleIds(),
                "steel", 0, 1000, 20000, 1000, 100, 1, 0, 2000, false, false)));
        assertFalse(FleetFuelSharing.preview(legacy, request).available());
    }
    @Test void selectionsAndReviewedTransfersAreDefensiveCopies() {
        var initial = state();
        var donors = new ArrayList<>(List.of("donor"));
        var receivers = new ArrayList<>(List.of("one"));
        var request = new FleetFuelSharing.Request("owner", "fleet", donors, receivers, CARGO, PROPELLANT, "mod_chemical_rocket", 1, .25);
        donors.clear(); receivers.clear();
        var transfers = new ArrayList<>(FleetFuelSharing.preview(initial, request).transfers());
        var command = new ShareFleetFuelCommand(request, transfers);
        transfers.clear();
        assertTrue(command.validate(initial));
        assertThrows(UnsupportedOperationException.class, () -> request.donorShipIds().clear());
        assertThrows(UnsupportedOperationException.class, () -> command.approvedTransfers().clear());
    }

    @Test void verifiedCoastingContactCanShareWithoutChangingMotionAndDonorCanStillRecover() {
        var initial = state();
        var donor = ship(initial, "donor").withPowerState(power(0, 1));
        var motion = new FlightMotion(10000, 100, null, 0);
        var drift = new Fleet("fleet", "Drifting", "owner", "a", "b", 0, 0, .00001, false, "PASSIVE",
                List.of(donor, ship(initial, "one"), ship(initial, "two")), FleetLocation.at(FleetLocation.Site.deepSpace()),
                Fleet.MODE_POWER_INTERRUPTED, 100, 1e9, .1, 1, 100, Map.of(), motion);
        initial = initial.withFleets(List.of(drift));
        assertTrue(FlightRecoveryReadiness.check(initial, drift.withShips(List.of(donor))).ready());
        var request = request(CARGO, ELECTRICAL_FUEL, "uranium", .1, .25);
        var plan = FleetFuelSharing.preview(initial, request);
        assertEquals(2, plan.totalKg(), 1e-8);
        var next = command(initial, request).apply(initial);
        assertEquals(motion, next.fleets().getFirst().flightMotion());
        assertEquals(drift.interstellarFuelBudgetKg(), next.fleets().getFirst().interstellarFuelBudgetKg());
        assertTrue(FlightRecoveryReadiness.check(next, next.fleets().getFirst().withShips(List.of(ship(next, "donor")))).ready());
        var remote = new Fleet("remote", "Remote", "owner", "a", "b", 0, 0, .00001, false, "PASSIVE",
                List.of(ship(next, "one"), ship(next, "two")), drift.location(), Fleet.MODE_POWER_INTERRUPTED, 100, 1e9,
                .1, 1, 100, Map.of(), new FlightMotion(10010, 100, null, 0));
        var separated = initial.withFleets(List.of(drift.withShips(List.of(donor)), remote));
        var remoteRequest = new FleetFuelSharing.Request("owner", "remote", List.of("donor"), List.of("one", "two"),
                CARGO, ELECTRICAL_FUEL, "uranium", .1, .25);
        assertFalse(FleetFuelSharing.preview(separated, remoteRequest).available());
    }

    @Test void fullCapacityIncompatibleElectricalMixtureAndChangedPowerDoNotPermitSharing() {
        var initial = state();
        var one = ship(initial, "one").withPowerState(new ShipPowerState(Map.of("liquid_methane", 22.0, "liquid_oxygen", 78.0),
                "methalox", "uranium", 0, false, 1, 1, 0, 0, 0, 0, 0));
        initial = ShipPowerResupply.replace(initial, one);
        var onlyOne = new FleetFuelSharing.Request("owner", "fleet", List.of("donor"), List.of("one"), CARGO,
                ELECTRICAL_FUEL, "rp1", 1, .25);
        assertFalse(FleetFuelSharing.preview(initial, onlyOne).available());
        var request = request(CARGO, PROPELLANT, "mod_chemical_rocket", 1, .25);
        var reviewed = command(initial, request);
        var drained = ShipPowerResupply.replace(initial, ship(initial, "donor").withPowerState(power(0, 0)));
        assertSame(drained, reviewed.apply(drained));
        assertEquals(100, ship(drained, "two").currentFuelKg());
    }
}
