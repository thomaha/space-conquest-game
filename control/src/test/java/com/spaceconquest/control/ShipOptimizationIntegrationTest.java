package com.spaceconquest.control;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spaceconquest.control.command.QueueShipBuildCommand;
import com.spaceconquest.control.command.DesignShipCommand;
import com.spaceconquest.control.command.SelectOptimizationPathCommand;
import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.SaveGameManager;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.macrostructure.StationModule;
import com.spaceconquest.engine.ship.PropulsionCatalog;
import com.spaceconquest.engine.ship.ShipApplicationProduction;
import com.spaceconquest.engine.ship.ShipConstructionOrder;
import com.spaceconquest.engine.ship.ShipConstructionProcessor;
import com.spaceconquest.engine.ship.ShipConstructionRequirements;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipDesignValidator;
import com.spaceconquest.engine.ship.ShipManufacturingProfile;
import com.spaceconquest.engine.ship.ShipManufacturingCapacity;
import com.spaceconquest.engine.ship.ShipModule;
import com.spaceconquest.engine.ship.ShipRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ShipOptimizationIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    void allDriveApplicationLinksReferToExistingApplications() throws IOException {
        var applications = DataModelLoader.loadTechnologies().stream()
                .flatMap(technology -> technology.applications().stream()).map(application -> application.id()).toList();
        for (String moduleId : PropulsionCatalog.MAIN_DRIVE_IDS)
            assertTrue(applications.contains(ShipApplicationProduction.applicationId(moduleId)), moduleId);
        assertTrue(applications.contains(ShipApplicationProduction.applicationId("mod_fission_reactor")));
        for (String moduleId : List.of("cryogenic_stasis_pod", "mod_cargo_vault", "mod_cargo_hold_large"))
            assertTrue(applications.contains(ShipApplicationProduction.applicationId(moduleId)), moduleId);
    }

    @Test
    void reactorOptimizationChangesPowerBalanceAndKeepsQuotedValuesAfterSaveLoad() throws IOException {
        Empire empire = new Empire("empire", "Empire", "human", "Individualist", 100_000.0,
                0.0, List.of("sol"), List.of(), Map.of(),
                List.of("rocketry", "electricity", "nuclear_fission", "fission_reactors"), List.of());
        GameState state = state(false).toBuilder().empires(List.of(empire)).build();
        ShipModule reactor = new ShipModule("mod_fission_reactor", "Fission reactor tier 2",
                "MEDIUM", 4, 3000, 0, 500, 0, 2, Map.of(), Map.of());
        var performance = new SelectOptimizationPathCommand("empire", "fission_reactors", "PATH_A");
        assertTrue(performance.validate(state));
        GameState selected = performance.apply(state);
        ShipModule optimized = ShipApplicationProduction.optimize(selected, "empire", reactor);
        assertEquals(575.0, optimized.powerOutputKw(), 0.001);
        assertEquals(3, optimized.complexityLevel());
        ShipModule drive = PropulsionCatalog.module("mod_chemical_rocket");
        ShipModule cargo = new ShipModule("mod_cargo_vault", "Cargo", "LARGE",
                8, 2000, 30, 0, 0, 1, Map.of(), Map.of("cargoCapacityKg", 30000.0));
        List<ShipModule> modules = List.of(optimized, drive, cargo, PropulsionCatalog.fuelTankModule());
        var validation = new ShipDesignValidator().validate(ShipRole.CARGO_TRANSPORT,
                null, modules, null, null, 0.0, 1.0, 1.0, 7);
        assertTrue(validation.isValid(), validation.validationErrors().toString());
        assertEquals(525.0, validation.powerBalanceKw(), 0.001);
        var profile = ShipApplicationProduction.profile(selected, "empire", modules, validation.totalDryMassKg());
        assertEquals(1.0 + 3000.0 / validation.totalDryMassKg() * 0.20, profile.costMultiplier(), 0.000001);
        ShipDesign design = new ShipDesign("blueprint", "Reactor freighter", "empire", ShipRole.CARGO_TRANSPORT,
                "steel", modules.stream().map(ShipModule::id).toList(), "steel", 0.0,
                validation.totalDryMassKg(), validation.maxCargoMassKg(), validation.fuelCapacityKg(), validation.powerBalanceKw(),
                validation.structuralIntegrity(), validation.minLaunchThrustRequiredN(), validation.totalThrustN(),
                validation.isLaunchCapable(), false, profile);
        GameState ready = selected.withShipDesigns(List.of(design));
        assertTrue(command().validate(ready));
        GameState queued = command().apply(ready);
        var miniaturization = new SelectOptimizationPathCommand("empire", "fission_reactors", "PATH_B");
        GameState switched = miniaturization.apply(queued);
        ShipModule miniaturized = ShipApplicationProduction.optimize(switched, "empire", reactor);
        assertEquals(500.0, miniaturized.powerOutputKw());
        assertEquals(1, miniaturized.complexityLevel());
        assertTrue(ShipApplicationProduction.profile(switched, "empire", List.of(miniaturized),
                validation.totalDryMassKg()).costMultiplier() < 1.0);
        var file = tempDir.resolve("optimized_reactor.scsave").toFile();
        SaveGameManager saves = new SaveGameManager();
        saves.save(file, switched, 1, "2026-01-01T00:00:00");
        GameState loaded = saves.load(file).toGameState(0, "RUNNING");
        assertEquals(design, loaded.shipDesigns().getFirst());
        assertEquals(queued.shipConstructionOrders(), loaded.shipConstructionOrders());
        assertEquals(ShipConstructionRequirements.estimate(design).workUnits(),
                loaded.shipConstructionOrders().getFirst().requiredWorkHours());
        assertEquals(ShipConstructionRequirements.estimate(design).materialsKg(),
                loaded.shipConstructionOrders().getFirst().requiredMaterialsKg());
    }

    @Test
    void unresearchedOrUnlinkedComponentsKeepTheirBaselineValues() {
        ShipModule reactor = new ShipModule("mod_fission_reactor", "Reactor", "MEDIUM",
                4, 3000, 0, 500, 0, 2, Map.of(), Map.of());
        assertEquals(reactor, ShipApplicationProduction.optimize(state(false), "empire", reactor));
        ShipModule cargo = new ShipModule("mod_cargo_vault", "Cargo", "LARGE",
                8, 2000, 30, 0, 0, 1, Map.of(), Map.of("cargoCapacityKg", 30000.0));
        assertSame(cargo, ShipApplicationProduction.optimize(state(false), "empire", cargo));
    }

    @Test
    void miniaturizedFusionDriveFitsGridThatRejectsBaselineAndPerformanceVariants() {
        GameState state = state(false);
        ShipDesign baseline = blueprint(state);
        assertEquals(7, ShipManufacturingCapacity.forOwner(state, "empire"));
        assertEquals(5, ShipManufacturingCapacity.forOwner(state, "other_empire"));
        assertEquals(8, baseline.manufacturingProfile().requiredComplexity());
        assertFalse(new DesignShipCommand(baseline).validate(state));
        assertFalse(command().validate(state.withShipDesigns(List.of(baseline))));

        GameState miniaturized = select(state, "PATH_B");
        ShipDesign miniaturizedDesign = blueprint(miniaturized);
        GameState ready = miniaturized.withShipDesigns(List.of(miniaturizedDesign));
        assertEquals(7, miniaturizedDesign.manufacturingProfile().requiredComplexity());
        assertTrue(new DesignShipCommand(miniaturizedDesign).validate(miniaturized));
        assertTrue(command().validate(ready));
        var order = command().apply(ready).shipConstructionOrders().getFirst();
        assertEquals(ShipConstructionRequirements.estimate(miniaturizedDesign).workUnits(), order.requiredWorkHours());
        assertTrue(order.requiredWorkHours() < ShipConstructionRequirements.estimate(baseline).workUnits());

        GameState performance = select(state, "PATH_A");
        ShipDesign performanceDesign = blueprint(performance);
        assertEquals(9, performanceDesign.manufacturingProfile().requiredComplexity());
        assertEquals(baseline.totalThrustN() * 1.15, performanceDesign.totalThrustN(), 0.001);
        assertFalse(command().validate(performance.withShipDesigns(List.of(performanceDesign))));
    }

    @Test
    void selectionSkipsUnderspecifiedYardAndConstructionPausesUntilCapacityReturns() {
        GameState selected = select(state(true), "PATH_A");
        ShipDesign design = blueprint(selected);
        assertTrue(new DesignShipCommand(design).validate(selected));
        GameState ready = selected.withShipDesigns(List.of(design));
        assertEquals("capital", command().resolveYardEntity(ready));
        ShipConstructionOrder order = new ShipConstructionOrder("order", "empire", "blueprint", "sol",
                "capital", 0.0, 500.0, Map.of(), Map.of());
        GameState queued = ready.withShipConstructionOrders(List.of(order));
        GameState downgraded = queued.toBuilder().orbitalStations(List.of(station("capital", false))).build();
        GameState paused = new ShipConstructionProcessor().process(downgraded);
        assertEquals(order, paused.shipConstructionOrders().getFirst());
        GameState upgraded = paused.toBuilder().orbitalStations(List.of(station("capital", true))).build();
        GameState resumed = new ShipConstructionProcessor().process(upgraded);
        assertEquals(300.0, resumed.shipConstructionOrders().getFirst().accumulatedWorkHours(), 0.001);
    }

    @Test
    void blueprintThrustAndQuotesRemainFixedAcrossPathChangesAndSaveLoad() throws IOException {
        GameState selected = select(state(true), "PATH_A");
        ShipDesign design = blueprint(selected);
        GameState switched = select(selected.withShipDesigns(List.of(design)), "PATH_B");
        var quote = ShipConstructionRequirements.estimate(design);
        var file = tempDir.resolve("optimized_ship.scsave").toFile();
        SaveGameManager saves = new SaveGameManager();
        saves.save(file, switched, 1, "2026-01-01T00:00:00");
        GameState loaded = saves.load(file).toGameState(0, "RUNNING");
        assertEquals(design, loaded.shipDesigns().getFirst());
        assertEquals(quote, ShipConstructionRequirements.estimate(loaded.shipDesigns().getFirst()));
        var order = command().apply(loaded).shipConstructionOrders().getFirst();
        assertEquals(quote.materialsKg(), order.requiredMaterialsKg());
        assertEquals(quote.workUnits(), order.requiredWorkHours());
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode oldDesign = mapper.valueToTree(design);
        oldDesign.remove("manufacturingProfile");
        ShipDesign legacy = mapper.treeToValue(oldDesign, ShipDesign.class);
        assertEquals(ShipManufacturingProfile.baseline(), legacy.manufacturingProfile());
    }

    private GameState select(GameState state, String path) {
        var command = new SelectOptimizationPathCommand("empire", "fusion_engines", path);
        assertTrue(command.validate(state));
        return command.apply(state);
    }

    private QueueShipBuildCommand command() {
        return new QueueShipBuildCommand("empire", "blueprint", "sol");
    }

    private ShipDesign blueprint(GameState state) {
        ShipModule drive = ShipApplicationProduction.optimize(state, "empire", PropulsionCatalog.module("mod_fusion_drive"));
        List<ShipModule> modules = List.of(drive, PropulsionCatalog.fuelTankModule());
        return new ShipDesign("blueprint", "Fusion freighter", "empire", ShipRole.CARGO_TRANSPORT,
                "steel", modules.stream().map(ShipModule::id).toList(), "steel", 1.0,
                20_000.0, 10_000.0, 15_000.0, 100.0, 1.0, 0.0, drive.thrustOutputN(), true, false,
                ShipApplicationProduction.profile(state, "empire", modules, 20_000.0));
    }

    private GameState state(boolean capitalYard) {
        Empire empire = new Empire("empire", "Empire", "human", "Individualist", 100_000.0,
                0.0, List.of("sol"), List.of(), Map.of(), List.of("rocketry", "nuclear_fusion", "fusion_engines"), List.of());
        return GameState.builder().empires(List.of(empire))
                .orbitalStations(capitalYard ? List.of(station("grid", false), station("capital", true)) : List.of(station("grid", false)))
                .commercialHubs(List.of(new CommercialHub("grid_hub", "grid", 0, 100_000, 0, 1, Map.of()),
                        new CommercialHub("capital_hub", "capital", 0, 100_000, 0, 1, Map.of()))).build();
    }

    private OrbitalStation station(String id, boolean capital) {
        StationModule module = new StationModule("module_" + id, "Yard",
                capital ? StationModule.TYPE_CAPITAL_SLIPWAY : StationModule.TYPE_SHIPYARD_GRID,
                5, 1000, 0, 0, Map.of(), "industrial_worker", capital ? 300 : 100, true,
                capital ? 300 : 100, 0.0);
        return new OrbitalStation(id, id, "sol", "earth", "empire", OrbitalStation.OWNERSHIP_PUBLIC_STATE,
                10, List.of(module), Map.of(), 0, 0, 0, 0, 100, 100, "steel", 1, true);
    }
}
