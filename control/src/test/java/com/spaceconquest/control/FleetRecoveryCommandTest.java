package com.spaceconquest.control;

import com.spaceconquest.control.command.RecoverFleetTravelCommand;
import com.spaceconquest.control.command.SetFleetStanceCommand;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FleetLocation;
import com.spaceconquest.engine.ship.FlightMotion;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipInstance;
import com.spaceconquest.engine.ship.ShipManufacturingProfile;
import com.spaceconquest.engine.ship.ShipPowerProcessor;
import com.spaceconquest.engine.ship.ShipPowerProfile;
import com.spaceconquest.engine.ship.ShipPowerState;
import com.spaceconquest.engine.ship.ShipRole;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class FleetRecoveryCommandTest {
    private GameState state(double electricalEnergy, double propellant, double position) {
        var profile = new ShipPowerProfile(0, 100, 0, 0, 0, 0, 15000, 0, 2, 20, 0, .65,
                Map.of("rp1", new ShipPowerProfile.Fuel("rp1_kerosene", "liquid_oxygen", .28, 1.008)));
        var design = new ShipDesign("design", "Ship", "owner", ShipRole.EXPLORER, "steel",
                List.of("mod_chemical_rocket"), "steel", 0, 1000, 0, 1000, 100, 1, 0, 2000,
                false, false, ShipManufacturingProfile.baseline(), profile);
        var kg = electricalEnergy / 1.008;
        var power = new ShipPowerState(Map.of("rp1_kerosene", kg * .28, "liquid_oxygen", kg * .72),
                "rp1", "uranium", 0, true, 1, 1, 20, 0, 0, 0, 0);
        var ship = new ShipInstance("ship", "design", "owner", 100, 0, propellant, Map.of(), 0, "", "CONSCIOUS", power);
        var fleet = new Fleet("fleet", "Fleet", "owner", "a", "b", 0, 0, Math.min(1, position / 1e6),
                false, "PASSIVE", List.of(ship), FleetLocation.at(FleetLocation.Site.deepSpace()),
                Fleet.MODE_POWER_INTERRUPTED, 5, 1e6, 1, 1, 100, Map.of("ship", 100.0),
                new FlightMotion(position, 100, null, 0));
        return GameState.builder().shipDesigns(List.of(design)).fleets(List.of(fleet)).solarSystems(List.of(
                new SolarSystem("b", "B", "", 0, 0, 0, 1, 1, "yellow", List.of(), List.of()))).build();
    }

    @Test void recoveryStagesANewTrajectoryWithoutRefillingOrImmediatelyBurningPropellant() {
        var initial = state(500, 1000, 10000);
        var command = new RecoverFleetTravelCommand("fleet");
        assertTrue(command.validate(initial));
        var recovered = command.apply(initial);
        var fleet = recovered.fleets().getFirst();
        assertEquals(Fleet.MODE_RECOVERY, fleet.interstellarMode());
        assertEquals(initial.fleets().getFirst().ships(), fleet.ships());
        assertEquals(10000, fleet.flightMotion().positionMeters());
        assertEquals(100, fleet.flightMotion().velocityMps());
        assertTrue(fleet.interstellarFuelBudgetKg().get("ship") > 0);
        assertSame(recovered, command.apply(recovered));
        var ticked = ShipPowerProcessor.advanceDay(recovered).getFirst();
        assertEquals("b", ticked.currentSystemId());
        assertTrue(ticked.ships().getFirst().currentFuelKg() < 1000);
    }

    @Test void rejectedRecoveryIsAtomicForMissingPowerPropellantOvershootAndUnknownFleet() {
        var command = new RecoverFleetTravelCommand("fleet");
        for (var invalid : List.of(state(0, 1000, 10000), state(500, 1, 10000), state(500, 1000, 2e6))) {
            assertFalse(command.validate(invalid));
            assertSame(invalid, command.apply(invalid));
        }
        assertFalse(new RecoverFleetTravelCommand("missing").validate(state(500, 1000, 10000)));
        assertFalse(command.validate(null));
        var missingDestination = state(500, 1000, 10000).toBuilder().solarSystems(List.of()).build();
        assertSame(missingDestination, command.apply(missingDestination));
    }

    @Test void changingStancePreservesDriftAndRecoveryMotion() {
        var drifting = state(500, 1000, 10000);
        var changed = new SetFleetStanceCommand("fleet", "ESCORT").apply(drifting);
        assertEquals(drifting.fleets().getFirst().flightMotion(), changed.fleets().getFirst().flightMotion());
        var recovering = new RecoverFleetTravelCommand("fleet").apply(drifting);
        changed = new SetFleetStanceCommand("fleet", "PATROL").apply(recovering);
        assertEquals(recovering.fleets().getFirst().flightMotion(), changed.fleets().getFirst().flightMotion());
        assertEquals(recovering.fleets().getFirst().interstellarFuelBudgetKg(), changed.fleets().getFirst().interstellarFuelBudgetKg());
    }
}
