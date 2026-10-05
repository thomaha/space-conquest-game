package com.spaceconquest.control;

import com.spaceconquest.control.command.DesignShipCommand;
import com.spaceconquest.control.command.QueueShipBuildCommand;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.macrostructure.StationModule;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChemicalFreighterLifecycleTest {
    @TempDir Path directory;

    private GameState state(String drive) throws Exception {
        var owner = new Empire("owner", "Owner", "human", "Individualist", 1e6, 0, List.of("sol"), List.of(), Map.of(),
                List.of("rocketry", "methalox_propulsion", "hydrolox_propulsion", "electricity", "solar_power", "industrial_production"), List.of());
        var yard = new IndustrialFacility("yard", "earth", "surface_shipyard", "owner", IndustrialFacility.PUBLIC_STATE,
                3, 0, "engineer", false, 0);
        var module = new StationModule("slipway", "Slipway", StationModule.TYPE_CAPITAL_SLIPWAY, 4, 10000, 10, 0,
                Map.of(), "engineer", 0, true);
        var port = new OrbitalStation("port", "Port", "sol", "earth", "owner", OrbitalStation.OWNERSHIP_PUBLIC_STATE,
                20, List.of(module), Map.of(), 100, 10, 0, 0, 100, 100, "steel", 0, true);
        var mixture = PropulsionCatalog.drive(drive).propellantMaterials(120000);
        var orders = new java.util.HashMap<String, MarketOrder>();
        mixture.forEach((id, kg) -> orders.put(id, new MarketOrder(id, kg, 0, 1, 0)));
        var hub = new CommercialHub("hub", "port", 0, 1e6, 120000, 10, orders);
        var initial = GameState.builder().solarSystems(DataModelLoader.loadSolarSystems()).empires(List.of(owner))
                .industrialFacilities(List.of(yard)).orbitalStations(List.of(port)).commercialHubs(List.of(hub)).build();
        var command = new DesignShipCommand(new ShipDesignSpecification("design", "Freighter", "owner", ShipRole.CARGO_TRANSPORT,
                "steel", ChemicalFreighterCatalog.modules(drive), "steel", .5));
        assertTrue(command.validate(initial));
        return command.apply(initial);
    }

    private GameState ship(GameState state, double fuel, boolean powered) {
        var power = new ShipPowerState(Map.of(), "rp1", "uranium", powered ? 500 : 0, powered, 1, 1, 0, 0, 0, 0, 0);
        var ship = new ShipInstance("ship", "design", "owner", 100, 0, fuel,
                Map.of("steel", 1250.0, "food_matrix", 1250.0)).withPowerState(power);
        var fleet = new Fleet("fleet", "Freighter", "owner", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(ship),
                FleetLocation.at(FleetLocation.Site.docked("port")));
        return state.withFleets(List.of(fleet));
    }

    @Test void presetsCaptureTheMeasuredComponentsAndDeclaredVolume() throws Exception {
        for (String drive : ChemicalFreighterCatalog.DRIVES) {
            var state = state(drive);
            var design = state.shipDesigns().getFirst();
            assertEquals(120000, design.fuelCapacityKg());
            assertEquals(2500, design.maxCargoMassKg());
            assertEquals(12, design.powerProfile().hotelKw());
            assertTrue(ChemicalFreighterCatalog.mixtureVolumeM3(drive, 120000) <= 400);
            assertTrue(design.manufacturingProfile().requiredComplexity() >= 3);
            assertTrue(design.totalThrustN() < (design.totalDryMassKg() + 120000 + 2500) * 9.81,
                    "Fully loaded freighters cannot lift off from Earth.");
        }
        assertThrows(IllegalArgumentException.class, () -> ChemicalFreighterCatalog.modules("mod_ion_drive"));
    }

    @Test void constructionRequiresLiveOrbitalYardAndCurrentResearch() throws Exception {
        var initial = state("mod_hydrolox_rocket");
        var command = new QueueShipBuildCommand("owner", "design", "sol");
        assertTrue(command.validate(initial));
        assertEquals("port", command.resolveYardEntity(initial));
        var queued = command.apply(initial);
        assertEquals(400, queued.shipConstructionOrders().getFirst().requiredWorkHours());
        assertEquals(40000, queued.shipConstructionOrders().getFirst().requiredMaterialsKg().values().stream().mapToDouble(Double::doubleValue).sum());
        var noOrbit = initial.toBuilder().orbitalStations(List.of()).build();
        assertFalse(command.validate(noOrbit), "Ground manufacturing capacity cannot replace orbital assembly.");
        assertSame(noOrbit, command.apply(noOrbit));
        var owner = initial.empires().getFirst();
        var lostResearch = new Empire(owner.id(), owner.name(), owner.raceId(), owner.societyStructure(), owner.treasuryCredits(),
                owner.corporateTaxRate(), owner.controlledSystemIds(), owner.ministries(), owner.systemGovernorAssignments(),
                List.of("rocketry", "hydrolox_propulsion", "electricity", "solar_power"), owner.activeShipDesignIds());
        assertFalse(command.validate(initial.toBuilder().empires(List.of(lostResearch)).build()));
    }

    @Test void fullMainLoadIsBoughtFromRealMixtureStockAndFreightIsUntouched() throws Exception {
        var state = ship(state("mod_hydrolox_rocket"), 0, true);
        var paid = ShipFueling.refuel(state, "ship", "port", 120000);
        assertNotSame(state, paid);
        assertEquals(120000, paid.fleets().getFirst().ships().getFirst().currentFuelKg());
        assertEquals(880000, paid.empires().getFirst().treasuryCredits(), .000001);
        assertEquals(state.fleets().getFirst().ships().getFirst().storedCargoKg(), paid.fleets().getFirst().ships().getFirst().storedCargoKg());
        paid.commercialHubs().getFirst().activeOrders().values().forEach(order -> assertEquals(0, order.supplyKg(), .000001));
        assertSame(paid, ShipFueling.refuel(paid, "ship", "port", 1));
    }

    @Test void thermalVentingRunsOncePerDayAndSurvivesSaveLoad() throws Exception {
        var state = ship(state("mod_hydrolox_rocket"), 120000, false);
        var first = state.withFleets(ShipPowerProcessor.advanceDay(state));
        assertEquals(117600, first.fleets().getFirst().ships().getFirst().currentFuelKg(), .000001);
        assertEquals(0, first.fleets().getFirst().ships().getFirst().generatorFuelMassKg());
        assertEquals(state.fleets().getFirst().ships().getFirst().storedCargoKg(), first.fleets().getFirst().ships().getFirst().storedCargoKg());
        var manager = new SaveGameManager(directory);
        var file = directory.resolve("freighter.scsave").toFile();
        manager.save(file, first, 1, "2027-01-01T08:00:00");
        var restored = manager.load(file).toGameState(0, "RUNNING");
        assertEquals(first.shipDesigns(), restored.shipDesigns());
        assertEquals(first.fleets(), restored.fleets());
        assertEquals(ShipPowerProcessor.advanceDay(first), ShipPowerProcessor.advanceDay(restored));
        assertEquals(115248, ShipPowerProcessor.advanceDay(restored).getFirst().ships().getFirst().currentFuelKg(), .000001);
    }

    @Test void poweredConditioningPreservesMainStoresAndAnOutageStopsLocalThrust() throws Exception {
        var powered = ship(state("mod_chemical_rocket"), 120000, true);
        assertEquals(120000, ShipPowerProcessor.advanceDay(powered).getFirst().ships().getFirst().currentFuelKg());
        var base = state("mod_chemical_rocket");
        base = base.toBuilder().orbitalStations(List.of(base.orbitalStations().getFirst().withParkingAltitudeKm(100000.0))).build();
        var dark = ship(base, 120000, false);
        var fleet = dark.fleets().getFirst();
        var target = FleetLocation.Site.orbit("mars", 8000);
        var plan = LocalTravel.plan(dark, fleet, target);
        assertNotNull(plan);
        fleet = LocalTravel.depart(fleet, target, plan);
        var after = ShipPowerProcessor.advanceDay(dark.withFleets(List.of(fleet))).getFirst();
        assertTrue(after.location().orbitalFlight().atSource());
        assertEquals(119400, after.ships().getFirst().currentFuelKg(), .000001);
        assertFalse(ShipPowerForecast.ready(ShipPowerForecast.departure(dark, dark.fleets().getFirst(), target, plan, null)));
    }
}
