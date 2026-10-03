package com.spaceconquest.control;

import com.spaceconquest.control.command.DesignShipCommand;
import com.spaceconquest.control.command.UpdateShipDesignCommand;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.ShipComponentCatalog;
import com.spaceconquest.engine.ship.ShipConstructionOrder;
import com.spaceconquest.engine.ship.ShipConstructionRequirements;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipDesignSpecification;
import com.spaceconquest.engine.ship.ShipInstance;
import com.spaceconquest.engine.ship.ShipManufacturingProfile;
import com.spaceconquest.engine.ship.ShipRole;
import com.spaceconquest.engine.technology.ApplicationOptimization;
import com.spaceconquest.engine.technology.ResearchVarianceResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BlueprintAuthorityTest {
    private GameState state() {
        var empire = new Empire("empire", "Empire", "human", "Individualist", 100_000,
                0, List.of(), List.of(), Map.of(), List.of("rocketry", "rocket_engines"), List.of());
        return GameState.builder().empires(List.of(empire)).build();
    }

    private ShipDesignSpecification specification(List<String> modules, String material, double armor) {
        return new ShipDesignSpecification("blueprint", "Freighter", "empire", ShipRole.CARGO_TRANSPORT,
                material, modules, "steel", armor);
    }

    private ShipDesignSpecification specification() {
        return specification(ShipComponentCatalog.workbenchModules("mod_chemical_rocket", false), "steel", 1);
    }

    private ShipDesign forged() {
        var specification = specification();
        return new ShipDesign(specification.id(), specification.name(), "empire", specification.role(),
                "steel", specification.moduleIds(), "steel", 1, 1, 1e9, 1e9, 1e9, 999,
                0, 1e12, true, false, new ShipManufacturingProfile(0.01, 0, 10_000));
    }

    @Test
    void registrationAndEditingIgnoreSubmittedPhysicsAndManufacturingMetadata() {
        var state = state();
        var forged = forged();
        var command = new DesignShipCommand(forged);
        assertTrue(command.validate(state));
        var registered = command.apply(state);
        var actual = registered.shipDesigns().getFirst();
        assertEquals(30_000, actual.maxCargoMassKg());
        assertEquals(15_000, actual.fuelCapacityKg());
        assertEquals(600_000, actual.totalThrustN());
        assertTrue(actual.totalDryMassKg() > 15_000);
        assertEquals(448, actual.powerBalanceKw());
        assertEquals(2, actual.manufacturingProfile().requiredComplexity());
        assertEquals(1, actual.manufacturingProfile().costMultiplier());
        assertEquals(0, actual.manufacturingProfile().stasisCapacity());
        assertFalse(actual.isValidForLaunch(), "No surface reference is available");
        assertTrue(ShipConstructionRequirements.estimate(actual).workUnits()
                > ShipConstructionRequirements.estimate(forged).workUnits());
        assertSame(registered, command.apply(registered), "Duplicate IDs are rejected");
        var edit = new UpdateShipDesignCommand(forged);
        assertTrue(edit.validate(registered));
        assertEquals(actual, edit.apply(registered).shipDesigns().getFirst());
    }

    @Test
    void malformedComponentsMaterialsAndArmorCannotBeRegistered() {
        var valid = specification().moduleIds();
        var cases = List.of(
                specification(List.of("unknown"), "steel", 1),
                specification(valid, "unknown", 1),
                specification(valid, "steel", Double.NaN),
                specification(valid, "steel", -1),
                specification(valid, "steel", 11),
                specification(List.of("mod_chemical_generator", "mod_chemical_rocket", "mod_cargo_vault"), "steel", 1),
                specification(List.of("mod_chemical_rocket", "mod_cargo_vault", "mod_propellant_tank"), "steel", 1),
                specification(List.of("mod_chemical_generator", "mod_chemical_rocket", "mod_propellant_tank"), "steel", 1),
                specification(List.of("mod_chemical_generator", "mod_chemical_rocket", "mod_chemical_rocket",
                        "mod_cargo_vault", "mod_propellant_tank"), "steel", 1),
                specification(List.of("mod_chemical_generator", "mod_chemical_rocket", "mod_cargo_vault",
                        "mod_cargo_vault", "mod_cargo_vault", "mod_propellant_tank"), "steel", 1));
        var state = state();
        for (var request : cases) {
            var command = new DesignShipCommand(request);
            assertFalse(command.validate(state), request.toString());
            assertSame(state, command.apply(state), request.toString());
        }
    }

    @Test
    void queuedRequestUsesLiveResearchAndRechecksManufacturingCapacity() {
        var state = state();
        var command = new DesignShipCommand(specification());
        assertTrue(command.validate(state));
        var improvement = new ApplicationOptimization("empire", "rocket_engines", "PATH_A",
                new ResearchVarianceResult(ResearchVarianceResult.OPTIMIZED_SUCCESS, 1.15, 1.20, 1));
        var changed = state.toBuilder().applicationOptimizations(List.of(improvement)).build();
        var actual = command.apply(changed).shipDesigns().getFirst();
        assertEquals(690_000, actual.totalThrustN(), 0.001);
        assertEquals(3, actual.manufacturingProfile().requiredComplexity());
        assertTrue(actual.manufacturingProfile().costMultiplier() > 1);
        var unavailable = new ApplicationOptimization("empire", "rocket_engines", "PATH_A",
                new ResearchVarianceResult(ResearchVarianceResult.OPTIMIZED_SUCCESS, 1.15, 1.20, 4));
        var insufficient = state.toBuilder().applicationOptimizations(List.of(unavailable)).build();
        assertFalse(command.validate(insufficient));
        assertSame(insufficient, command.apply(insufficient));
        var lostResearch = state.toBuilder().empires(List.of(new Empire("empire", "Empire", "human",
                "Individualist", 100_000, 0, List.of(), List.of(), Map.of(), List.of(), List.of()))).build();
        assertSame(lostResearch, command.apply(lostResearch));
    }

    @Test
    void editingCannotChangeBlueprintsReferencedByOrdersOrBuiltShips() {
        var registered = new DesignShipCommand(specification()).apply(state());
        var edit = new UpdateShipDesignCommand(specification());
        var order = new ShipConstructionOrder("order", "empire", "blueprint", "sol", "yard",
                0, 100, Map.of(), Map.of());
        var ordered = registered.withShipConstructionOrders(List.of(order));
        assertFalse(edit.validate(ordered));
        assertSame(ordered, edit.apply(ordered));
        var ship = new ShipInstance("ship", "blueprint", "empire", 100, 0, 0, Map.of());
        var fleet = new Fleet("fleet", "Fleet", "empire", "sol", null, 0, 0, 0, false, "PASSIVE", List.of(ship));
        var built = registered.toBuilder().fleets(List.of(fleet)).build();
        assertFalse(edit.validate(built));
        assertSame(built, edit.apply(built));
    }
}
