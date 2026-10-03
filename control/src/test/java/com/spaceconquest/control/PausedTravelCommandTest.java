package com.spaceconquest.control;

import com.spaceconquest.control.command.RecoverFleetTravelCommand;
import com.spaceconquest.control.command.SetShipSolarArraysCommand;
import com.spaceconquest.control.command.AddStationModuleCommand;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.Moon;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FleetLocation;
import com.spaceconquest.engine.ship.FleetProcessor;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipInstance;
import com.spaceconquest.engine.ship.ShipManufacturingProfile;
import com.spaceconquest.engine.ship.ShipPowerProcessor;
import com.spaceconquest.engine.ship.ShipPowerProfile;
import com.spaceconquest.engine.ship.ShipPowerState;
import com.spaceconquest.engine.ship.ShipRole;
import com.spaceconquest.engine.ship.ShipSolarEnvironment;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.macrostructure.StationModule;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PausedTravelCommandTest {
    private GameState state(boolean local, boolean stocked) {
        var profile = new ShipPowerProfile(local ? 120 : 0, local ? 0 : 100, 0, local ? 500 : 0,
                local ? 100 : 0, local ? 200 : 0, 15000, 0, 2, 20, 0, .65,
                Map.of("rp1", new ShipPowerProfile.Fuel("rp1_kerosene", "liquid_oxygen", .28, 1.008)));
        var design = new ShipDesign("design", "Ship", "owner", ShipRole.EXPLORER, "steel",
                List.of("mod_chemical_rocket"), "steel", 0, 1000, 0, 1000, 100, 1, 0, 2000,
                false, false, ShipManufacturingProfile.baseline(), profile);
        double kg = stocked && !local ? 2500 / 1.008 : 0;
        var power = new ShipPowerState(Map.of("rp1_kerosene", kg * .28, "liquid_oxygen", kg * .72),
                "rp1", "uranium", stocked && local ? 500 : 0, stocked, 1, 1, 0, 0, 0, 0, 0);
        var ship = new ShipInstance("ship", "design", "owner", 100, 0, 900, Map.of(), 0, "", "CONSCIOUS", power);
        var location = local ? new FleetLocation(FleetLocation.Site.orbit("earth"), FleetLocation.Site.orbit("moon"), .25, 2)
                : FleetLocation.at(FleetLocation.Site.deepSpace());
        var fleet = new Fleet("fleet", "Fleet", "owner", "a", local ? null : "b", 0, 0, local ? 0 : .25,
                false, "PASSIVE", List.of(ship), location, Fleet.MODE_POWER_INTERRUPTED, local ? 0 : 4,
                0, 0, local ? 0 : 1, 0, Map.of());
        var moon = new Moon("moon", "Moon", "", 1, 1, 384400, 3000, "none", false, 0, List.of(), List.of());
        var earth = new Planet("earth", "Earth", "", 1, 9.81, ShipSolarEnvironment.AU_KM, 0, 12000,
                "terrestrial", "air", true, 0, List.of(), List.of(moon), List.of());
        var empire = new Empire("owner", "Owner", "human", "Individualist", 100000, 0, List.of("a"), List.of(),
                Map.of(), List.of("warp", "electricity", "solar_power"), List.of());
        return GameState.builder().shipDesigns(List.of(design)).fleets(List.of(fleet)).empires(List.of(empire))
                .solarSystems(List.of(new SolarSystem("a", "A", "", 0, 0, 0, 1.989e30, 1, "yellow", List.of(earth), List.of()),
                        new SolarSystem("b", "B", "", 1, 0, 0, 1.989e30, 1, "yellow", List.of(), List.of()))).build();
    }
    private GameState tick(GameState state) {
        return state.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state), List.of(), List.of()));
    }

    @Test void deploymentDuringAPauseRechargesAndResumesWithoutRebuyingManeuverFuel() {
        var initial = state(true, false);
        var recover = new RecoverFleetTravelCommand("fleet");
        assertFalse(recover.validate(initial));
        assertSame(initial, recover.apply(initial));
        var deploy = new SetShipSolarArraysCommand("ship", true);
        assertTrue(deploy.validate(initial));
        var deployed = deploy.apply(initial);
        assertEquals(0, deployed.fleets().getFirst().ships().getFirst().powerState().batteryChargeKwh());
        assertTrue(new SetShipSolarArraysCommand("ship", false).validate(deployed));
        var charged = tick(deployed);
        assertEquals(.25, charged.fleets().getFirst().location().progress());
        assertTrue(recover.validate(charged));
        var resumed = recover.apply(charged);
        assertEquals(charged.fleets().getFirst().ships(), resumed.fleets().getFirst().ships());
        assertTrue(deploy.validate(resumed));
        var arrived = tick(tick(resumed)).fleets().getFirst();
        assertTrue(arrived.location().isAt(FleetLocation.Site.orbit("moon")));
        assertEquals(900, arrived.ships().getFirst().currentFuelKg());
        assertEquals(initial.empires(), resumed.empires());
    }

    @Test void warpResumptionRequiresResearchAndDoesNotResetOrRefillTheJourney() {
        var initial = state(false, true);
        var recover = new RecoverFleetTravelCommand("fleet");
        assertEquals(3, recover.pausedPreview(initial).remainingDays());
        assertTrue(recover.validate(initial));
        var resumed = recover.apply(initial);
        assertEquals(initial.fleets().getFirst().ships(), resumed.fleets().getFirst().ships());
        assertEquals(1, resumed.fleets().getFirst().interstellarElapsedDays());
        assertEquals(.25, resumed.fleets().getFirst().transitProgress());
        assertTrue(resumed.fleets().getFirst().isInWarp());
        assertSame(resumed, recover.apply(resumed));
        var arrived = tick(tick(tick(resumed))).fleets().getFirst();
        assertEquals("b", arrived.currentSystemId());
        var noResearch = initial.toBuilder().empires(List.of()).build();
        assertSame(noResearch, recover.apply(noResearch));
        var empty = state(false, false);
        assertFalse(recover.pausedPreview(empty).ready());
        assertSame(empty, recover.apply(empty));
    }

    @Test void invalidLocalDestinationAndMissingReservedCrossingPropellantRejectRecovery() {
        var initial = state(true, true);
        var fleet = initial.fleets().getFirst();
        var invalidSite = initial.withFleets(List.of(fleet.withLocation(new FleetLocation(fleet.location().current(),
                FleetLocation.Site.docked("missing"), .25, 2))));
        var command = new RecoverFleetTravelCommand("fleet");
        assertSame(invalidSite, command.apply(invalidSite));
        for (var budget : List.of(Map.of("ship", 1000.0), Map.<String, Double>of(), Map.of("ship", Double.NaN))) {
            var crossing = new Fleet(fleet.id(), fleet.name(), fleet.ownerEntityId(), "a", "b", 0, 0, 0,
                    false, "PASSIVE", fleet.ships(), new FleetLocation(fleet.location().current(), FleetLocation.Site.deepSpace(), .25, 2),
                    Fleet.MODE_POWER_INTERRUPTED, 1, 1e6, 1, 0, 100, budget);
            var unfueled = initial.withFleets(List.of(crossing));
            assertNull(command.pausedPreview(unfueled));
            assertSame(unfueled, command.apply(unfueled));
        }
    }

    @Test void atmosphericTransfersCannotDeployOrGenerateWithAlreadyDeployedPanels() {
        var initial = state(true, true);
        var fleet = initial.fleets().getFirst().withLocation(new FleetLocation(FleetLocation.Site.orbit("earth"),
                FleetLocation.Site.surface("earth"), .25, 1));
        var atmospheric = initial.withFleets(List.of(fleet));
        var deploy = new SetShipSolarArraysCommand("ship", true);
        assertFalse(deploy.validate(atmospheric));
        assertSame(atmospheric, deploy.apply(atmospheric));
        assertTrue(new SetShipSolarArraysCommand("ship", false).validate(atmospheric));
        assertEquals(452, tick(atmospheric).fleets().getFirst().ships().getFirst().powerState().batteryChargeKwh(), .000001);
    }

    @Test void stationSolarConstructionRequiresSolarResearchAndQueuesReferenceOutput() {
        var initial = state(true, true);
        var station = new OrbitalStation("station", "Station", "a", "earth", "owner", OrbitalStation.OWNERSHIP_PUBLIC_STATE,
                20, List.of(), Map.of(), 0, 0, 0, 0, 100, 100, "steel", 1, true);
        var empire = new Empire("owner", "Owner", "human", "Individualist", 100000, 0, List.of("a"), List.of(), Map.of(),
                List.of("space_stations", "electricity", "solar_power"), List.of());
        var ready = initial.toBuilder().orbitalStations(List.of(station)).empires(List.of(empire)).build();
        var command = new AddStationModuleCommand("station", "Solar array", StationModule.TYPE_SOLAR_ARRAY,
                4, 2500, 0, 500, "technician", 0);
        assertTrue(command.validate(ready));
        var queued = command.apply(ready);
        assertEquals(500, queued.constructionProjects().getFirst().plannedModule().powerOutputKw());
        assertEquals(ready.orbitalStations(), queued.orbitalStations());
        var missing = new Empire("owner", "Owner", "human", "Individualist", 100000, 0, List.of("a"), List.of(), Map.of(),
                List.of("space_stations", "electricity"), List.of());
        var noSolar = ready.toBuilder().empires(List.of(missing)).build();
        assertSame(noSolar, command.apply(noSolar));
    }
}
