package com.spaceconquest.control;

import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ContingencyFuelCommandTest {
    private GameState state(boolean powerRequired) {
        var profile = powerRequired ? new ShipPowerProfile(0, 0, 0, 0, 0, 0, 0, 0, 1, 20, 0, .65, Map.of()) : null;
        var design = new ShipDesign("design", "Rocket", "owner", ShipRole.EXPLORER, "steel",
                List.of("mod_chemical_rocket"), "steel", 0, 1000, 100, 1000, 0, 1, 0, 2000,
                false, false, ShipManufacturingProfile.baseline(), profile);
        var fleet = new Fleet("fleet", "Fleet", "owner", "a", "", 0, 0, 0, false, "PASSIVE",
                List.of(new ShipInstance("ship", "design", "owner", 100, 0, 10, Map.of())),
                FleetLocation.at(FleetLocation.Site.deepSpace()));
        var planet = new Planet("planet", "Planet", "", 1, 1, SolarRadiation.AU_KM, 15, 12000,
                "terrestrial", "air", true, 0, List.of(), List.of(), List.of());
        return GameState.builder().shipDesigns(List.of(design)).fleets(List.of(fleet)).solarSystems(List.of(
                new SolarSystem("a", "A", "", 0, 0, 0, SolarRadiation.SOLAR_MASS_KG, 1392000, "yellow", List.of(planet), List.of()),
                new SolarSystem("b", "B", "", 1e6 / InterstellarTravel.METERS_PER_LIGHT_YEAR, 0, 0, 1, 1, "yellow", List.of(), List.of()))).build();
    }

    @Test void localEmergencyConsumesReserveForOneOrderAndRetainsNormalPolicy() {
        var state = state(false); var normal = new MoveFleetLocalCommand("fleet", FleetLocation.Kind.ORBIT, "planet");
        assertFalse(normal.validate(state)); assertSame(state, normal.apply(state));
        var emergency = new MoveFleetLocalCommand("fleet", FleetLocation.Kind.ORBIT, "planet", true);
        assertTrue(emergency.validate(state));
        var departed = emergency.apply(state).fleets().getFirst();
        assertEquals(state.fleets().getFirst().fuelPolicy(), departed.fuelPolicy());
        var flight = departed.location().localFlight();
        assertEquals(0, flight.propulsion().get("ship").protectedPropellantKg());
        var arrival = LocalFlightProcessor.advance(departed, departed.location().travelDays() * 24, false);
        assertTrue(arrival.location().isAt(FleetLocation.Site.orbit("planet")));
        assertTrue(arrival.ships().getFirst().currentFuelKg() < 50);
        assertEquals(departed.fuelPolicy(), arrival.fuelPolicy());
    }

    @Test void crossingEmergencyDisclosesShortfallAndNeverBypassesElectricity() {
        var state = state(false);
        assertFalse(new MoveFleetCommand("fleet", "b").validate(state));
        var emergency = new MoveFleetCommand("fleet", "b", true);
        var preview = emergency.preview(state, false);
        assertNotNull(preview); assertTrue(preview.emergencyOverride());
        assertTrue(preview.fuelReserves().getFirst().shortfallKg() > 0);
        var departed = emergency.apply(state).fleets().getFirst();
        assertEquals(FleetFuelPolicy.automatic(), departed.fuelPolicy());
        assertEquals(0, departed.journeyPropulsion().get("ship").protectedPropellantKg());
        var unpowered = state(true);
        assertFalse(emergency.validate(unpowered)); assertSame(unpowered, emergency.apply(unpowered));
        assertFalse(new MoveFleetLocalCommand("fleet", FleetLocation.Kind.ORBIT, "planet", true).validate(unpowered));
    }

    @Test void localRecoveryRequiresExplicitPermissionToUseContingency() {
        var state = state(false);
        state = new MoveFleetLocalCommand("fleet", FleetLocation.Kind.ORBIT, "planet", true).apply(state);
        var fleet = state.fleets().getFirst(); var flight = fleet.location().localFlight();
        fleet = fleet.withLocation(fleet.location().withFlight(new LocalFlight(flight.geometry(), flight.motion(),
                flight.propellantKg(), flight.propulsion(), true, false)));
        state = state.withFleets(List.of(fleet));
        assertFalse(new RecoverFleetTravelCommand("fleet").validate(state));
        var emergency = new RecoverFleetTravelCommand("fleet", true);
        assertTrue(emergency.validate(state));
        var resumed = emergency.apply(state).fleets().getFirst();
        assertFalse(resumed.location().localFlight().interrupted());
        assertEquals(FleetFuelPolicy.automatic(), resumed.fuelPolicy());
        assertEquals(0, resumed.location().localFlight().propulsion().get("ship").protectedPropellantKg());
    }

    @Test void policyEditsValidateOwnershipAndPreserveAlreadyCommittedMotion() {
        var state = state(false);
        state = new MoveFleetLocalCommand("fleet", FleetLocation.Kind.ORBIT, "planet", true).apply(state);
        var chosen = new FleetFuelPolicy(.1, .2, .3);
        assertSame(state, new SetFleetFuelPolicyCommand("wrong", "fleet", chosen).apply(state));
        var changed = new SetFleetFuelPolicyCommand("owner", "fleet", chosen).apply(state);
        assertEquals(chosen, changed.fleets().getFirst().fuelPolicy());
        assertEquals(state.fleets().getFirst().location(), changed.fleets().getFirst().location());
    }
}
