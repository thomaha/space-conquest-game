package com.spaceconquest.control;

import com.spaceconquest.control.command.DesignShipCommand;
import com.spaceconquest.control.command.MoveFleetCommand;
import com.spaceconquest.control.command.QueueShipBuildCommand;
import com.spaceconquest.control.command.RefuelShipCommand;
import com.spaceconquest.control.command.ResupplyShipPowerCommand;
import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.MarketOrder;
import com.spaceconquest.engine.SaveGameManager;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.macrostructure.StationModule;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FleetProcessor;
import com.spaceconquest.engine.ship.InterstellarTravel;
import com.spaceconquest.engine.ship.PropulsionCatalog;
import com.spaceconquest.engine.ship.ShipBlueprintFactory;
import com.spaceconquest.engine.ship.ShipComponentCatalog;
import com.spaceconquest.engine.ship.ShipConstructionProcessor;
import com.spaceconquest.engine.ship.ShipDesignSpecification;
import com.spaceconquest.engine.ship.ShipRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EarlyRocketLifecycleTest {
    @TempDir Path tempDir;

    private GameState state(List<String> research) {
        var owner = new Empire("empire", "Empire", "human", "Individualist", 1_000_000,
                0, List.of("a"), List.of(), Map.of(), research, List.of());
        var module = new StationModule("grid", "Yard", StationModule.TYPE_SHIPYARD_GRID,
                5, 1000, 0, 0, Map.of(), "industrial_worker", 100, true, 100, 0);
        var yard = new OrbitalStation("yard", "Yard", "a", "", "empire",
                OrbitalStation.OWNERSHIP_PUBLIC_STATE, 10, List.of(module), Map.of(),
                0, 0, 0, 0, 100, 100, "steel", 1, true);
        var materials = Map.of("steel", stock("steel", 1), "refined_copper", stock("refined_copper", 1),
                "silicon", stock("silicon", 1), "rp1_kerosene", stock("rp1_kerosene", 2),
                "liquid_methane", stock("liquid_methane", 2), "liquid_hydrogen", stock("liquid_hydrogen", 2),
                "liquid_oxygen", stock("liquid_oxygen", 3));
        return GameState.builder().empires(List.of(owner)).orbitalStations(List.of(yard))
                .commercialHubs(List.of(new CommercialHub("hub", "yard", 0, 1_000_000, 0, 1, materials)))
                .solarSystems(List.of(system("a", 0), system("b", 1e-8))).build();
    }

    private MarketOrder stock(String id, double price) { return new MarketOrder(id, 100_000, 0, price, 0); }

    private SolarSystem system(String id, double x) {
        return new SolarSystem(id, id, "Synthetic short crossing for daily arrival coverage", x, 0, 0,
                1, 1, "yellow", List.of(), List.of());
    }

    private ShipDesignSpecification specification(String drive, String power) {
        return new ShipDesignSpecification("blueprint", "Early scout", "empire", ShipRole.EXPLORER, "steel",
                ShipComponentCatalog.workbenchModules(drive, false, power), "steel", 0);
    }

    private GameState commission(GameState state, String drive) {
        var design = new DesignShipCommand(specification(drive, ShipComponentCatalog.CHEMICAL_GENERATOR_ID));
        assertTrue(design.validate(state));
        var registered = design.apply(state);
        assertTrue(registered.shipDesigns().getFirst().powerBalanceKw() > 0);
        assertFalse(registered.shipDesigns().getFirst().equippedModuleIds()
                .contains(ShipComponentCatalog.FISSION_REACTOR_ID));
        var order = new QueueShipBuildCommand("empire", "blueprint", "a");
        assertTrue(order.validate(registered));
        var building = order.apply(registered);
        for (int day = 0; day < 20 && building.fleets().isEmpty(); day++)
            building = new ShipConstructionProcessor().process(building);
        assertEquals(1, building.fleets().size());
        assertTrue(building.shipConstructionOrders().isEmpty());
        assertTrue(building.fleets().getFirst().ships().getFirst().storedCargoKg().isEmpty());
        return building;
    }

    @ParameterizedTest
    @ValueSource(strings = {"mod_chemical_rocket", "mod_methalox_rocket", "mod_hydrolox_rocket"})
    void chemicalShipsBuildBuyBothPropellantsAndArriveAfterSaveLoad(String driveId) throws Exception {
        var drive = PropulsionCatalog.drive(driveId);
        var state = commission(state(List.of("rocketry", "space_stations", "industrial_production",
                "methalox_propulsion", "hydrolox_propulsion")), driveId);
        var ship = state.fleets().getFirst().ships().getFirst();
        double amount = state.shipDesigns().getFirst().fuelCapacityKg() - ship.currentFuelKg();
        double cash = state.empires().getFirst().treasuryCredits();
        var market = state.commercialHubs().getFirst().activeOrders();
        var refuel = new RefuelShipCommand(ship.id(), "yard", amount);
        assertTrue(refuel.validate(state));
        state = refuel.apply(state);
        var bought = state.commercialHubs().getFirst().activeOrders();
        assertEquals(amount * drive.fuelFraction(), market.get(drive.propellantId()).supplyKg()
                - bought.get(drive.propellantId()).supplyKg(), 0.001);
        assertEquals(amount * (1 - drive.fuelFraction()), market.get("liquid_oxygen").supplyKg()
                - bought.get("liquid_oxygen").supplyKg(), 0.001);
        assertEquals(amount * (2 * drive.fuelFraction() + 3 * (1 - drive.fuelFraction())),
                cash - state.empires().getFirst().treasuryCredits(), 0.001);
        var travel = new MoveFleetCommand(state.fleets().getFirst().id(), "b");
        assertFalse(travel.validate(state), "A full propulsion tank does not provide generator electricity");
        var electricalFuel = new ResupplyShipPowerCommand(ship.id(), "yard", "rp1", 3000);
        assertTrue(electricalFuel.validate(state));
        state = electricalFuel.apply(state);
        assertTrue(travel.validate(state));
        var preview = travel.preview(state);
        assertNotNull(preview);
        double beforeDeparture = state.fleets().getFirst().ships().getFirst().currentFuelKg();
        assertTrue(preview.local().propellantKg().get(ship.id()) > 0);
        state = travel.apply(state);
        assertEquals(preview.crossing().days(), state.fleets().getFirst().interstellarTravelDays());
        assertEquals(preview.crossing().fuelBudgetKg(), state.fleets().getFirst().interstellarFuelBudgetKg());
        assertEquals(beforeDeparture,
                state.fleets().getFirst().ships().getFirst().currentFuelKg(), 0.001);
        assertEquals(Fleet.MODE_SUBLIGHT, state.fleets().getFirst().interstellarMode());
        assertFalse(state.fleets().getFirst().isInWarp());
        double departureFuel = state.fleets().getFirst().ships().getFirst().currentFuelKg();
        var realistic = state.toBuilder().solarSystems(List.of(system("a", 0), system("b", 1))).build();
        var plan = InterstellarTravel.plan(realistic, state.fleets().getFirst(), "b");
        assertNotNull(plan);
        assertTrue(plan.days() > 1_000_000, "Chemical interstellar travel remains extremely slow");
        assertTrue(plan.reactorFuelBudgetKg().isEmpty());
        state = advance(state);
        var file = tempDir.resolve(driveId + ".scsave").toFile();
        var saves = new SaveGameManager();
        saves.save(file, state, 1, "2026-01-01T00:00:00");
        var restored = saves.load(file).toGameState(state.turn(), "RUNNING");
        assertEquals(state.fleets(), restored.fleets());
        assertEquals(state.shipDesigns(), restored.shipDesigns());
        for (int day = 0; day < 20 && !"b".equals(restored.fleets().getFirst().currentSystemId()); day++)
            restored = advance(restored);
        var arrived = restored.fleets().getFirst();
        assertEquals("b", arrived.currentSystemId());
        assertFalse(arrived.hasInterstellarOrder());
        assertTrue(arrived.ships().getFirst().currentFuelKg() < departureFuel);
        assertTrue(arrived.ships().getFirst().currentFuelKg() >= 0);
        assertTrue(arrived.ships().getFirst().generatorFuelMassKg() < 3000);
    }

    private GameState advance(GameState state) {
        return state.toBuilder().turn(state.turn() + 1).fleets(new FleetProcessor()
                .processFleetMovements(com.spaceconquest.engine.ship.ShipPowerProcessor.advanceDay(state),
                        state.orbitalStations(), state.diplomaticRelations())).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"mod_chemical_rocket", "mod_methalox_rocket", "mod_hydrolox_rocket"})
    void missingOxidizerPreventsRefuelingWithoutSpendingOrPartialLoading(String driveId) {
        var ready = commission(state(List.of("rocketry", "methalox_propulsion", "hydrolox_propulsion")), driveId);
        var hub = ready.commercialHubs().getFirst();
        var orders = new java.util.HashMap<>(hub.activeOrders());
        orders.remove("liquid_oxygen");
        var dry = ready.toBuilder().commercialHubs(List.of(new CommercialHub(hub.id(), hub.entityId(),
                hub.transactionTariffRate(), hub.storageCapacityKg(), hub.currentStoredWeightKg(),
                hub.logisticsRangeUnits(), orders))).build();
        var command = new RefuelShipCommand(dry.fleets().getFirst().ships().getFirst().id(), "yard", 1000);
        assertFalse(command.validate(dry));
        assertSame(dry, command.apply(dry));
    }

    @Test
    void fissionReactorRequiresResearchForPreviewRegistrationAndConstruction() {
        var request = specification("mod_chemical_rocket", ShipComponentCatalog.FISSION_REACTOR_ID);
        var early = state(List.of("rocketry"));
        assertFalse(ShipBlueprintFactory.evaluate(early, request).valid());
        assertSame(early, new DesignShipCommand(request).apply(early));
        var nuclear = state(List.of("rocketry", "nuclear_fission"));
        var registered = new DesignShipCommand(request).apply(nuclear);
        assertEquals(1, registered.shipDesigns().size());
        var build = new QueueShipBuildCommand("empire", "blueprint", "a");
        assertTrue(build.validate(registered));
        assertFalse(build.validate(early.withShipDesigns(registered.shipDesigns())));
    }
}
