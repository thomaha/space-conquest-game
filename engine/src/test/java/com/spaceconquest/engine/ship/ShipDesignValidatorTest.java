package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ShipDesignValidatorTest {

    private ShipDesignValidator validator;
    private ShipHullFrame smallFrame;
    private ShipHullFrame mediumFrame;
    private Material steel;
    private Material carbonNanotubes;

    @BeforeEach
    public void setUp() {
        validator = new ShipDesignValidator();
        steel = new Material("steel", "Steel", "Standard alloy", true, Map.of(), 7800.0, 60.0, 1);
        carbonNanotubes = new Material("carbon_nanotubes", "Carbon Nanotubes", "Advanced composite", false, Map.of(), 1300.0, 300.0, 5);

        smallFrame = new ShipHullFrame("frame_small", "Scout Frame", 6, "steel", 3000.0, 60.0);
        mediumFrame = new ShipHullFrame("frame_medium", "Cruiser Frame", 20, "steel", 15000.0, 60.0);
    }

    @Test
    public void testValidShipDesignPassesValidation() {
        ShipModule reactor = new ShipModule("mod_reactor", "Fission Reactor", "SMALL", 2, 2000.0, 0.0, 300.0, 0.0, 2, Map.of(), Map.of());
        ShipModule thruster = new ShipModule("mod_engine", "Ion Thruster", "SMALL", 2, 2000.0, 100.0, 0.0, 500000.0, 2, Map.of(), Map.of());
        ShipModule cargo = new ShipModule("mod_cargo", "Cargo Pod", "SMALL", 2, 1000.0, 20.0, 0.0, 0.0, 1, Map.of(), Map.of("cargoCapacityKg", 10000.0));

        ShipDesign design = validator.buildAndValidate(
                "design_cargo_1", "Atlas Light Hauler", "emp_terran",
                ShipRole.CARGO_TRANSPORT, smallFrame, List.of(reactor, thruster, cargo),
                steel, steel, 1.0, 1.0, 1.0, 3, false
        );

        assertTrue(design.isValidForLaunch());
        assertTrue(design.powerBalanceKw() > 0);
        assertTrue(design.calculatedStructuralIntegrity() >= ShipDesignValidator.MIN_STRUCTURAL_INTEGRITY_THRESHOLD);
        assertEquals(10000.0, design.maxCargoMassKg());
        assertEquals(500000.0, design.totalThrustN());
    }

    @Test
    public void testStructuralIntegrityCeilingFailure() {
        // Create 20 heavy modules on a weak frame with 0 armor strength
        ShipHullFrame fragileFrame = new ShipHullFrame("frame_fragile", "Fragile Frame", 50, "wood", 10000.0, 5.0);
        Material weakMat = new Material("wood", "Wood", "Organic", true, Map.of(), 500.0, 5.0, 1);

        ShipModule reactor = new ShipModule("mod_reactor", "Fission Reactor", "SMALL", 10, 50000.0, 0.0, 1000.0, 0.0, 1, Map.of(), Map.of());
        ShipModule thruster = new ShipModule("mod_engine", "Ion Thruster", "SMALL", 10, 50000.0, 100.0, 0.0, 2000000.0, 1, Map.of(), Map.of());

        ShipDesignValidator.ValidationResult result = validator.validate(
                ShipRole.COMBAT_SHIP, fragileFrame, List.of(reactor, thruster),
                weakMat, weakMat, 0.0, 1.0, 1.0, 5
        );

        assertFalse(result.isValid(), "Design should fail structural integrity ceiling check");
        assertTrue(result.structuralIntegrity() < ShipDesignValidator.MIN_STRUCTURAL_INTEGRITY_THRESHOLD);
        assertTrue(result.validationErrors().stream().anyMatch(e -> e.contains("structural integrity")));
    }

    @Test
    public void testPowerGridDeficitFailure() {
        // High power draw with insufficient reactor output
        ShipModule hungryThruster = new ShipModule("mod_engine", "Plasma Drive", "MEDIUM", 4, 3000.0, 500.0, 0.0, 800000.0, 2, Map.of(), Map.of());
        ShipModule weakReactor = new ShipModule("mod_reactor", "Aux Battery", "SMALL", 2, 1000.0, 0.0, 100.0, 0.0, 1, Map.of(), Map.of());

        ShipDesignValidator.ValidationResult result = validator.validate(
                ShipRole.COMBAT_SHIP, smallFrame, List.of(hungryThruster, weakReactor),
                steel, steel, 1.0, 1.0, 1.0, 5
        );

        assertFalse(result.isValid(), "Design should fail due to power grid deficit");
        assertTrue(result.powerBalanceKw() < 0.0);
        assertTrue(result.validationErrors().stream().anyMatch(e -> e.contains("Power grid deficit")));
    }

    @Test
    public void testNanotechnologyComplexityCapFailure() {
        // Module complexity level 6 when imperial tech tier is only 2
        ShipModule advancedShield = new ShipModule("mod_antimatter_shield", "Antimatter Barrier", "SMALL", 2, 1500.0, 50.0, 0.0, 0.0, 6, Map.of(), Map.of());
        ShipModule reactor = new ShipModule("mod_reactor", "Standard Reactor", "SMALL", 2, 1000.0, 0.0, 200.0, 0.0, 2, Map.of(), Map.of());

        ShipDesignValidator.ValidationResult result = validator.validate(
                ShipRole.COMBAT_SHIP, smallFrame, List.of(advancedShield, reactor),
                steel, steel, 1.0, 1.0, 1.0, 2
        );

        assertFalse(result.isValid(), "Design should fail nanotech complexity cap");
        assertTrue(result.validationErrors().stream().anyMatch(e -> e.contains("nanotechnology tier")));
    }

    @Test
    public void testThrustToMassLaunchBarrier() {
        // Heavy ship on heavy gravity / dense atmosphere world
        ShipModule reactor = new ShipModule("mod_reactor", "Heavy Reactor", "MEDIUM", 4, 20000.0, 0.0, 1000.0, 0.0, 2, Map.of(), Map.of());
        // Thruster produces 100 kN (100,000 N)
        ShipModule weakThruster = new ShipModule("mod_engine", "Weak Thruster", "MEDIUM", 4, 10000.0, 100.0, 0.0, 100000.0, 2, Map.of(), Map.of());

        // Total mass ~ 15000 (frame) + 30000 (modules) = 45000 kg
        // On 24.525 m/s² planet with 2.0 atm pressure:
        // Min thrust = 45000 * 24.525 * (1 + 2.0) = 45000 * 24.525 * 3 = 3,310,875 N > 100,000 N
        ShipDesignValidator.ValidationResult result = validator.validate(
                ShipRole.COMBAT_SHIP, mediumFrame, List.of(reactor, weakThruster),
                steel, steel, 0.0, 24.525, 2.0, 5
        );

        assertFalse(result.isLaunchCapable(), "Ship with weak thrust cannot blast off from high-G dense world");
        assertTrue(result.minLaunchThrustRequiredN() > result.totalThrustN());
    }

    @Test
    public void testRoleRequirementsCheck() {
        ShipModule reactor = new ShipModule("mod_reactor", "Reactor", "SMALL", 2, 1000.0, 0.0, 500.0, 0.0, 1, Map.of(), Map.of());
        ShipModule thruster = new ShipModule("mod_engine", "Engine", "SMALL", 2, 1000.0, 50.0, 0.0, 500000.0, 1, Map.of(), Map.of());

        // Cargo role with no cargo vault
        ShipDesignValidator.ValidationResult cargoResult = validator.validate(
                ShipRole.CARGO_TRANSPORT, smallFrame, List.of(reactor, thruster),
                steel, steel, 0.0, 1.0, 0.0, 5
        );
        assertFalse(cargoResult.isValid());
        assertTrue(cargoResult.validationErrors().stream().anyMatch(e -> e.contains("cargo vault")));

        // Colony role with no colonization module
        ShipDesignValidator.ValidationResult colonyResult = validator.validate(
                ShipRole.COLONY_SHIP, smallFrame, List.of(reactor, thruster),
                steel, steel, 0.0, 1.0, 0.0, 5
        );
        assertFalse(colonyResult.isValid());
        assertTrue(colonyResult.validationErrors().stream().anyMatch(e -> e.contains("colonization module")));
    }
}
