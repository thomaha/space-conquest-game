package com.spaceconquest.engine;

import com.spaceconquest.engine.governance.GroundCombatProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class GroundCombatProcessorTest {

    private GroundCombatProcessor combatProcessor;
    private Race humanRace;
    private Race siliconRace;

    @BeforeEach
    public void setUp() throws IOException {
        combatProcessor = new GroundCombatProcessor();
        List<Race> races = DataModelLoader.loadRaces();
        humanRace = races.stream().filter(r -> r.id().equals("human")).findFirst().orElseThrow();
        siliconRace = races.stream().filter(r -> r.id().equals("silicon_core")).findFirst().orElseThrow();
    }

    @Test
    public void testAttackingStrengthCalculation() {
        // 1000 Human soldiers (strength 1.0) with 1.25x ministry defense bonus
        double humanAttack = combatProcessor.calculateAttackerStrength(1000, humanRace, 1.25);
        assertEquals(1000 * 1.0 * 1.25, humanAttack, 0.001);

        // 1000 Silicon Core soldiers (strength 1.8) with 1.0x bonus
        double siliconAttack = combatProcessor.calculateAttackerStrength(1000, siliconRace, 1.0);
        assertEquals(1000 * 1.8 * 1.0, siliconAttack, 0.001);
    }

    @Test
    public void testDefendingStrengthCalculation() {
        // 500 soldiers + 1000 conscripts (at 0.5 efficiency) = 1000 effective human troops
        // 2 fortification nodes (+100% -> 2.0x multiplier), soldier governor (+25% -> 1.25x)
        double defenderPower = combatProcessor.calculateDefenderStrength(
                500,
                1000,
                humanRace,
                2, // 2 fortification nodes
                true // military governor
        );

        double expectedBase = (500 * 1.0) + (1000 * 1.0 * 0.5); // 1000
        double expectedFort = 1.0 + (2 * 0.50); // 2.0
        double expectedGov = 1.25;
        double expectedTotal = expectedBase * expectedFort * expectedGov; // 2500

        assertEquals(expectedTotal, defenderPower, 0.001);
    }

    @Test
    public void testGroundCombatResolution() {
        // High strength Silicon invasion vs weak unfortified human garrison
        GroundCombatProcessor.GroundCombatResult siegeResult = combatProcessor.resolveCombat(
                2000, // attackers
                siliconRace,
                1.25,
                500, // defenders
                0,
                humanRace,
                0,
                false
        );

        assertTrue(siegeResult.attackerWon(), "Silicon heavy assault should break unfortified garrison");
        assertTrue(siegeResult.survivingAttackers() > 1000);
        assertEquals(0, siegeResult.survivingDefenders());

        // Heavy fortified defender with military governor repelling assault
        GroundCombatProcessor.GroundCombatResult fortifiedResult = combatProcessor.resolveCombat(
                1000,
                humanRace,
                1.0,
                1000,
                1000,
                humanRace,
                3, // 3 fortifications (+150%)
                true // military governor (+25%)
        );

        assertFalse(fortifiedResult.attackerWon(), "Heavily fortified garrison should repel attackers");
        assertTrue(fortifiedResult.survivingDefenders() > 0);
        assertEquals(0, fortifiedResult.survivingAttackers());
    }
}
