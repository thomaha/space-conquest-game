package com.spaceconquest.control;

import com.spaceconquest.control.command.RecoverFleetTravelCommand;
import com.spaceconquest.control.command.RescueFleetCommand;
import com.spaceconquest.control.command.TransferShipSuppliesCommand;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.SaveGameManager;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FleetLocation;
import com.spaceconquest.engine.ship.FleetProcessor;
import com.spaceconquest.engine.ship.FlightMotion;
import com.spaceconquest.engine.ship.RescueOrder;
import com.spaceconquest.engine.ship.RescueRendezvous;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipInstance;
import com.spaceconquest.engine.ship.ShipManufacturingProfile;
import com.spaceconquest.engine.ship.ShipPowerProcessor;
import com.spaceconquest.engine.ship.ShipPowerProfile;
import com.spaceconquest.engine.ship.ShipPowerState;
import com.spaceconquest.engine.ship.ShipRole;
import com.spaceconquest.engine.ship.ShipSupplyTransfer;
import com.spaceconquest.engine.ship.RescueStatus;
import com.spaceconquest.engine.ship.FlightRecoveryReadiness;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RescueRendezvousTest {
    @TempDir Path directory;
    private final ShipPowerProfile profile = new ShipPowerProfile(0, 0, 100, 0, 0, 0, 0, 10,
            2, 20, 0, .65, Map.of("uranium", new ShipPowerProfile.Fuel("refined_uranium", null, 1, 6e6)));
    private final ShipDesign design = new ShipDesign("design", "Rescue rocket", "owner", ShipRole.EXPLORER,
            "steel", List.of("mod_chemical_rocket"), "steel", 0, 1000, 1000, 2000, 100, 1,
            0, 2000, false, false, ShipManufacturingProfile.baseline(), profile);

    private ShipPowerState power(double uranium) {
        return new ShipPowerState(Map.of("refined_uranium", uranium), "rp1", "uranium", 0,
                false, 1, 1, 0, 0, 0, 0, 0);
    }
    private GameState state() {
        var donor = new ShipInstance("donor", "design", "owner", 100, 0, 2000,
                Map.of("refined_uranium", .02)).withPowerState(power(1));
        var receiver = new ShipInstance("receiver", "design", "owner", 100, 0, 1000, Map.of()).withPowerState(power(0));
        return GameState.builder().shipDesigns(List.of(design)).fleets(List.of(
                new Fleet("rescuer", "Rescuer", "owner", "a", "", 0, 0, 0, false, "PASSIVE", List.of(donor)),
                new Fleet("target", "Target", "owner", "a", "b", 0, 0, .5, false, "PASSIVE", List.of(receiver),
                        FleetLocation.at(FleetLocation.Site.deepSpace()), Fleet.MODE_POWER_INTERRUPTED,
                        20, 1e9, 1, 5, 100, Map.of(), new FlightMotion(5e8, 100, null, 0))))
                .solarSystems(List.of(system("a"), system("b"))).build();
    }
    private SolarSystem system(String id) {
        return new SolarSystem(id, id, "", 0, 0, 0, 1, 1, "yellow", List.of(), List.of());
    }
    private RescueFleetCommand command() {
        return new RescueFleetCommand("rescuer", new RescueOrder("target", "donor", "receiver",
                ShipSupplyTransfer.Source.CARGO, ShipSupplyTransfer.Destination.ELECTRICAL_FUEL, "uranium", .001));
    }
    private Fleet fleet(GameState state, String id) { return RescueRendezvous.find(state, id); }
    private GameState tick(GameState state) {
        return state.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state), List.of(), List.of()));
    }
    private GameState replace(GameState state, Fleet fleet) {
        return state.withFleets(state.fleets().stream().map(item -> item.id().equals(fleet.id()) ? fleet : item).toList());
    }
    private GameState run(GameState state) {
        for (int day = 0; day < 30 && Fleet.MODE_RECOVERY.equals(fleet(state, "rescuer").interstellarMode()); day++) state = tick(state);
        return state;
    }

    @Test void rescueMatchesDriftTransfersOnceAndPermitsSeparateTravelRecovery() {
        var initial = state();
        var plan = command().preview(initial);
        assertNotNull(plan);
        double seconds = plan.trajectory().totalSeconds();
        var contact = plan.trajectory().at(seconds);
        assertEquals(5e8 + 100 * seconds, contact.positionMeters(), .001);
        assertEquals(100, contact.velocityMps(), .000001);
        var launched = command().apply(initial);
        assertEquals(plan.propulsion(), fleet(launched, "rescuer").journeyPropulsion());
        assertEquals(RescueStatus.Phase.APPROACHING, fleet(launched, "target").ships().getFirst().powerState().rescueStatus().phase());
        assertEquals(.02, fleet(launched, "rescuer").ships().getFirst().storedCargoKg().get("refined_uranium"));
        assertEquals(0, fleet(launched, "target").ships().getFirst().generatorFuelMassKg());
        var arrived = run(launched);
        var rescuer = fleet(arrived, "rescuer");
        assertEquals(plan.propulsion(), rescuer.journeyPropulsion());
        var target = fleet(arrived, "target");
        assertTrue(RescueRendezvous.contact(rescuer, target));
        assertEquals("a", rescuer.currentSystemId());
        assertEquals("b", rescuer.targetSystemId());
        assertEquals(2000 - plan.fuelKg().get("donor"), rescuer.ships().getFirst().currentFuelKg(), 1e-6);
        assertEquals(.019, rescuer.ships().getFirst().storedCargoKg().get("refined_uranium"), 1e-9);
        assertEquals(.001, target.ships().getFirst().generatorFuelMassKg(), 1e-9);
        assertEquals(RescueStatus.Phase.DELIVERED, target.ships().getFirst().powerState().rescueStatus().phase());
        assertEquals(target.ships().getFirst().powerState().rescueStatus(), rescuer.ships().getFirst().powerState().rescueStatus());
        assertNull(rescuer.flightMotion().trajectory());
        var later = tick(arrived);
        assertEquals(target.ships().getFirst().powerState().rescueStatus(), fleet(later, "target").ships().getFirst().powerState().rescueStatus());
        assertEquals(.019, fleet(later, "rescuer").ships().getFirst().storedCargoKg().get("refined_uranium"));
        assertTrue(RescueRendezvous.contact(fleet(later, "rescuer"), fleet(later, "target")));
        assertTrue(new RecoverFleetTravelCommand("target").validate(later));
        assertTrue(new TransferShipSuppliesCommand("donor", "receiver", ShipSupplyTransfer.Source.CARGO,
                ShipSupplyTransfer.Destination.ELECTRICAL_FUEL, "uranium", .001).validate(later));
    }

    @Test void fleetIterationOrderDoesNotChangeInterceptOrTransfer() {
        var launched = command().apply(state());
        var reversed = launched.withFleets(launched.fleets().reversed());
        var first = run(launched);
        var second = run(reversed);
        assertEquals(fleet(first, "rescuer"), fleet(second, "rescuer"));
        assertEquals(fleet(first, "target"), fleet(second, "target"));
    }

    @Test void unreachableTargetsMissingPayloadAndDepletedPowerRejectWithoutMutation() {
        var initial = state();
        var rescuer = fleet(initial, "rescuer");
        var donor = rescuer.ships().getFirst();
        for (var ship : List.of(new ShipInstance(donor.id(), donor.designId(), donor.ownerEntityId(), 100, 0,
                1, donor.storedCargoKg()).withPowerState(donor.powerState()),
                new ShipInstance(donor.id(), donor.designId(), donor.ownerEntityId(), 100, 0,
                        donor.currentFuelKg(), Map.of()).withPowerState(donor.powerState()), donor.withPowerState(power(0)))) {
            var invalid = replace(initial, rescuer.withShips(List.of(ship)));
            assertFalse(command().validate(invalid));
            assertSame(invalid, command().apply(invalid));
        }
        var target = fleet(initial, "target");
        var wrongOwner = new Fleet(target.id(), target.name(), "other", "a", "b", 0, 0, .5, false, "PASSIVE",
                target.ships(), target.location(), target.interstellarMode(), 20, 1e9, 1, 5, 100, Map.of(), target.flightMotion());
        assertFalse(command().validate(replace(initial, wrongOwner)));
        assertFalse(command().validate(replace(initial, rescuer.withLocation(FleetLocation.at(FleetLocation.Site.orbit("earth"))))));
    }

    @Test void propellantPayloadIsReservedInAdditionToInterceptFuel() {
        var initial = state();
        var target = fleet(initial, "target");
        var receiver = target.ships().getFirst();
        initial = replace(initial, target.withShips(List.of(new ShipInstance(receiver.id(), receiver.designId(),
                receiver.ownerEntityId(), 100, 0, 0, Map.of()).withPowerState(power(1)))));
        var command = new RescueFleetCommand("rescuer", new RescueOrder("target", "donor", "receiver",
                ShipSupplyTransfer.Source.TANK, ShipSupplyTransfer.Destination.PROPELLANT, "mod_chemical_rocket", 500));
        var plan = command.preview(initial);
        assertNotNull(plan);
        assertTrue(plan.fuelKg().get("donor") <= 1500 + 1e-6);
        var arrived = run(command.apply(initial));
        assertEquals(500, fleet(arrived, "target").ships().getFirst().currentFuelKg(), 1e-6);
        assertEquals(2000 - plan.fuelKg().get("donor") - 500,
                fleet(arrived, "rescuer").ships().getFirst().currentFuelKg(), 1e-6);
    }

    @Test void changedTargetOrPayloadCannotCauseRemoteOrPartialTransfer() {
        var launched = command().apply(state());
        var target = fleet(launched, "target");
        var moving = new Fleet(target.id(), target.name(), target.ownerEntityId(), "a", "b", 0, 0, .5, false,
                "PASSIVE", target.ships(), target.location(), Fleet.MODE_SUBLIGHT, 20, 1e9, 1, 5, 1000, Map.of());
        var missed = run(replace(launched, moving));
        assertEquals(RescueStatus.Phase.MISSED_CONTACT, fleet(missed, "rescuer").ships().getFirst().powerState().rescueStatus().phase());
        assertEquals(.02, fleet(missed, "rescuer").ships().getFirst().storedCargoKg().get("refined_uranium"));
        var donor = fleet(launched, "rescuer").ships().getFirst();
        var empty = new ShipInstance(donor.id(), donor.designId(), donor.ownerEntityId(), 100, 0,
                donor.currentFuelKg(), Map.of()).withPowerState(donor.powerState());
        var depleted = run(replace(launched, fleet(launched, "rescuer").withShips(List.of(empty))));
        assertEquals(RescueStatus.Phase.TRANSFER_REJECTED, fleet(depleted, "target").ships().getFirst().powerState().rescueStatus().phase());
        assertEquals(0, fleet(depleted, "target").ships().getFirst().generatorFuelMassKg());
    }

    @Test void powerFailureDuringInterceptPreservesDriftAndDoesNotTransfer() {
        var launched = command().apply(state());
        var rescuer = fleet(launched, "rescuer");
        var failed = tick(replace(launched, rescuer.withShips(List.of(rescuer.ships().getFirst().withPowerState(power(0))))));
        var stopped = fleet(failed, "rescuer");
        assertEquals(RescueStatus.Phase.POWER_FAILED, fleet(failed, "target").ships().getFirst().powerState().rescueStatus().phase());
        assertEquals(Fleet.MODE_POWER_INTERRUPTED, stopped.interstellarMode());
        assertEquals(0, stopped.flightMotion().velocityMps());
        assertEquals(2000, stopped.ships().getFirst().currentFuelKg());
        assertEquals(.02, stopped.ships().getFirst().storedCargoKg().get("refined_uranium"));
        assertFalse(RescueRendezvous.contact(stopped, fleet(failed, "target")));
    }

    @Test void saveLoadRetainsPendingTransferAndContinuesIdentically() throws Exception {
        var pending = tick(command().apply(state()));
        assertEquals(Fleet.MODE_RECOVERY, fleet(pending, "rescuer").interstellarMode());
        var manager = new SaveGameManager(directory);
        var file = directory.resolve("rescue.scsave").toFile();
        manager.save(file, pending, 1, "2027-01-01T08:00:00");
        var loaded = manager.load(file).toGameState(0, "RUNNING");
        assertEquals(pending.fleets(), loaded.fleets());
        assertEquals(run(pending).fleets(), run(loaded).fleets());
        var completed = run(loaded);
        manager.save(file, completed, 1, "2027-01-01T08:00:00");
        assertEquals(completed.fleets(), manager.load(file).toGameState(0, "RUNNING").fleets());
    }

    @Test void recoveryFeedbackDistinguishesPowerPropellantAndOvershoot() {
        var initial = state();
        var target = fleet(initial, "target");
        var shortage = FlightRecoveryReadiness.check(initial, target);
        assertFalse(shortage.ready());
        assertNotNull(shortage.plan());
        assertTrue(shortage.blockers().stream().anyMatch(reason -> reason.contains("electrical fuel")));
        var ship = target.ships().getFirst();
        var empty = target.withShips(List.of(new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), 100, 0,
                0, Map.of()).withPowerState(power(1))));
        assertTrue(FlightRecoveryReadiness.check(replace(initial, empty), empty).blockers().stream()
                .anyMatch(reason -> reason.contains("propellant is empty")));
        var overshot = new Fleet(target.id(), target.name(), target.ownerEntityId(), "a", "b", 0, 0, 1, false,
                "PASSIVE", target.ships(), target.location(), target.interstellarMode(), 20, 1e9, 1, 5, 100, Map.of(),
                new FlightMotion(1.1e9, 100, null, 0));
        assertTrue(FlightRecoveryReadiness.check(replace(initial, overshot), overshot).blockers().stream()
                .anyMatch(reason -> reason.contains("beyond its destination")));
    }
}
