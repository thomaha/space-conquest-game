package com.spaceconquest.engine.ship;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AtmosphericAerodynamicsTest {

    private ShipDesignValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ShipDesignValidator();
    }

    @Test
    void testManeuveringAgilityDeepSpace() {
        double dryMass = 10000.0;
        double cargoMass = 5000.0;
        double rcsImpulse = 150000.0;

        double agility = validator.calculateManeuveringAgility(dryMass, cargoMass, rcsImpulse);
        // 150000 / 15000 = 10.0
        assertEquals(10.0, agility, 0.01);

        // Sluggish when laden with 50,000 kg cargo
        double ladenAgility = validator.calculateManeuveringAgility(dryMass, 50000.0, rcsImpulse);
        // 150000 / 60000 = 2.5
        assertEquals(2.5, ladenAgility, 0.01);
    }

    @Test
    void testAtmosphericSpeedAndPowerMultipliers() {
        double pressureAtm = 1.0;
        double drag = 0.5;

        double speedMult = validator.calculateAtmosphericSpeedMultiplier(pressureAtm, drag);
        // 1.0 / (1.0 + 0.5 * 1.0) = 1.0 / 1.5 = 0.6667
        assertEquals(0.6667, speedMult, 0.001);

        double powerDrawMult = validator.calculateAtmosphericPowerDrawMultiplier(pressureAtm);
        // 1.0 + 1.0 * 0.5 = 1.5
        assertEquals(1.5, powerDrawMult, 0.01);
    }

    @Test
    void testAtmosphericEntryThermalStressAluminumVsTungsten() {
        double entrySpeed = 25000.0; // km/h
        double pressureAtm = 1.0;

        // Aluminum melting point 933K
        ThermalStressResult alumRes = validator.calculateAtmosphericEntryThermalStress(
                "refined_aluminum", entrySpeed, pressureAtm, false
        );
        assertTrue(alumRes.suffersThermalFailure());
        assertTrue(alumRes.hullDamagePercentage() > 0.0);
        assertEquals(933.0, alumRes.materialMeltingPointK(), 0.01);

        // Tungsten melting point 3695K withstands entry
        ThermalStressResult tungRes = validator.calculateAtmosphericEntryThermalStress(
                "tungsten", entrySpeed, pressureAtm, false
        );
        assertFalse(tungRes.suffersThermalFailure());
        assertEquals(0.0, tungRes.hullDamagePercentage(), 0.01);
    }

    @Test
    void testLaunchGravityTaxCalculation() {
        double dryMass = 20000.0;
        double cargo = 10000.0;
        double gravity = 1.0;
        double pressure = 1.0;

        double tax = validator.calculateLaunchGravityTax(dryMass, cargo, gravity, pressure);
        // (30000) * 1.0 * (2.0) * 0.001 = 60.0 credits
        assertEquals(60.0, tax, 0.01);
    }
}
