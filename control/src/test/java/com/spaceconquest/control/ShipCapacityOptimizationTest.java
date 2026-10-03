package com.spaceconquest.control;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spaceconquest.control.command.SelectOptimizationPathCommand;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.SaveGameManager;
import com.spaceconquest.engine.habitation.PassengerStasis;
import com.spaceconquest.engine.ship.PropulsionCatalog;
import com.spaceconquest.engine.ship.ShipApplicationProduction;
import com.spaceconquest.engine.ship.ShipConstructionRequirements;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipDesignValidator;
import com.spaceconquest.engine.ship.ShipInstance;
import com.spaceconquest.engine.ship.ShipModule;
import com.spaceconquest.engine.ship.ShipRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ShipCapacityOptimizationTest {
    @TempDir Path tempDir;

    @Test
    void cargoAndStasisCapacitiesAreCapturedAndEnforcedAfterPathChangesAndSaveLoad() throws Exception {
        GameState state = state();
        for (String app : List.of("cryogenic_stasis_pod", "pressurized_cargo_holds")) {
            var command = new SelectOptimizationPathCommand("empire", app, "PATH_A");
            assertTrue(command.validate(state));
            state = command.apply(state);
        }
        ShipDesign design = design(state);
        assertEquals(115, PassengerStasis.capacity(design));
        assertEquals(34_500.0, design.maxCargoMassKg(), 0.001);
        assertEquals(8, design.manufacturingProfile().requiredComplexity());
        assertTrue(ShipConstructionRequirements.estimate(design).workUnits()
                > ShipConstructionRequirements.estimate(design(state())).workUnits());
        state = state.withShipDesigns(List.of(design));
        for (String app : List.of("cryogenic_stasis_pod", "pressurized_cargo_holds"))
            state = new SelectOptimizationPathCommand("empire", app, "PATH_B").apply(state);
        var file = tempDir.resolve("capacities.scsave").toFile();
        SaveGameManager manager = new SaveGameManager();
        manager.save(file, state, 1, "2026-01-01T00:00:00");
        GameState loaded = manager.load(file).toGameState(0, "RUNNING");
        assertEquals(design, loaded.shipDesigns().getFirst());
        ShipInstance ship = new ShipInstance("ship", "blueprint", "empire", 100, 0, 0, Map.of());
        assertTrue(PassengerStasis.availableFor(loaded, ship, 115));
        assertFalse(PassengerStasis.availableFor(loaded, ship, 116));
        assertFalse(PassengerStasis.availableFor(loaded, ship, 0));
        assertEquals(100, PassengerStasis.capacity(design(state)));
        assertEquals(30_000.0, design(state).maxCargoMassKg());
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode legacy = mapper.valueToTree(design);
        ((ObjectNode) legacy.get("manufacturingProfile")).remove("stasisCapacity");
        assertEquals(100, PassengerStasis.capacity(mapper.treeToValue(legacy, ShipDesign.class)));
    }

    @Test
    void optimizationDoesNotMutateComponentCatalogStatsAndUnresearchedCapacityStaysBaseline() {
        ShipModule pod = pod();
        GameState optimized = new SelectOptimizationPathCommand("empire", "cryogenic_stasis_pod", "PATH_A").apply(state());
        ShipModule upgraded = ShipApplicationProduction.optimize(optimized, "empire", pod);
        assertEquals(100.0, pod.operationalStats().get("stasisCapacity"));
        assertEquals(115.0, upgraded.operationalStats().get("stasisCapacity"));
        assertThrows(UnsupportedOperationException.class, () -> upgraded.operationalStats().put("stasisCapacity", 999.0));
        assertEquals(100, PassengerStasis.capacity(design(state())));
    }

    private ShipDesign design(GameState state) {
        List<ShipModule> modules = List.of(pod(),
                new ShipModule("mod_cargo_vault", "Cargo vault", "LARGE", 8, 2000, 30, 0, 0, 1,
                        Map.of(), Map.of("cargoCapacityKg", 30_000.0)),
                new ShipModule("mod_fission_reactor", "Reactor", "MEDIUM", 4, 3000, 0, 500, 0, 2, Map.of(), Map.of()),
                PropulsionCatalog.module("mod_chemical_rocket"), PropulsionCatalog.fuelTankModule())
                .stream().map(module -> ShipApplicationProduction.optimize(state, "empire", module)).toList();
        var result = new ShipDesignValidator().validate(ShipRole.CARGO_TRANSPORT, null, modules,
                null, null, 0, 1, 1, 10);
        assertTrue(result.isValid(), result.validationErrors().toString());
        return new ShipDesign("blueprint", "Passenger freighter", "empire", ShipRole.CARGO_TRANSPORT, "steel",
                modules.stream().map(ShipModule::id).toList(), "steel", 0, result.totalDryMassKg(),
                result.maxCargoMassKg(), result.fuelCapacityKg(), result.powerBalanceKw(), result.structuralIntegrity(),
                result.minLaunchThrustRequiredN(), result.totalThrustN(), result.isLaunchCapable(), false,
                ShipApplicationProduction.profile(state, "empire", modules, result.totalDryMassKg()));
    }

    private ShipModule pod() {
        return new ShipModule(PassengerStasis.MODULE_ID, "Stasis pod", "MEDIUM", 2, 1200, 80, 0, 0, 7,
                Map.of(), Map.of("stasisCapacity", 100.0));
    }

    private GameState state() {
        return GameState.builder().empires(List.of(new Empire("empire", "Empire", "human", "Individualist",
                100_000, 0, List.of(), List.of(), Map.of(), List.of("electricity", "rocketry", "cryogenic_stasis",
                "cryogenic_stasis_pod", "interstellar_shipping_optimization", "pressurized_cargo_holds"), List.of()))).build();
    }
}
