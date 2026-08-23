package com.spaceconquest.engine.combat;

import com.spaceconquest.engine.AsteroidBelt;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.Population;
import com.spaceconquest.engine.Race;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipInstance;
import com.spaceconquest.engine.ship.ShipRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class TacticalCombatProcessorTest {

    private TacticalCombatProcessor combatProcessor;
    private OrbitalBombardmentProcessor bombardmentProcessor;
    private ColonizationProcessor colonizationProcessor;

    @BeforeEach
    public void setUp() {
        combatProcessor = new TacticalCombatProcessor(new Random(42));
        bombardmentProcessor = new OrbitalBombardmentProcessor();
        colonizationProcessor = new ColonizationProcessor();
    }

    @Test
    public void testTacticalSpaceCombatFleetEngagement() {
        ShipDesign combatDesign = new ShipDesign(
                "design_dreadnought", "Titan Dreadnought", "emp_terran",
                ShipRole.COMBAT_SHIP, "steel", List.of(), "steel", 2.0,
                20000.0, 1000.0, 200.0, 1.5, 100000.0, 500000.0, true, false
        );

        ShipInstance attackerShip1 = new ShipInstance("a1", "design_dreadnought", "emp_terran", 1000.0, 500.0, 100.0, Map.of());
        ShipInstance attackerShip2 = new ShipInstance("a2", "design_dreadnought", "emp_terran", 1000.0, 500.0, 100.0, Map.of());
        ShipInstance defenderShip1 = new ShipInstance("d1", "design_dreadnought", "emp_alien", 300.0, 100.0, 50.0, Map.of());

        Fleet attackerFleet = new Fleet("f_att", "Terran 1st Fleet", "emp_terran", "sol", "", 0, 0, 0, false, "AGGRESSIVE", List.of(attackerShip1, attackerShip2));
        Fleet defenderFleet = new Fleet("f_def", "Alien Defense Guard", "emp_alien", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(defenderShip1));

        TacticalCombatProcessor.CombatEngagementResult result = combatProcessor.resolveFleetEngagement(
                attackerFleet, defenderFleet, List.of(combatDesign)
        );

        assertEquals("emp_terran", result.winnerOwnerEntityId());
        assertFalse(result.roundReports().isEmpty());
        assertTrue(result.survivingDefenderFleet().ships().isEmpty(), "Defender ship should be eliminated");
        assertFalse(result.survivingAttackerFleet().ships().isEmpty(), "Attacker ships should survive");
    }

    @Test
    public void testAntimatterPlanetCrackerObliteratesTargetToAsteroids() {
        Planet earth = new Planet("earth", "Earth", "Home world", 5.97e24, 1.0, 1.0, 0.0, 12742.0, "Terrestrial", "Oxygen", true, 0.7, List.of(), List.of(), List.of(new Population("terran", Map.of(25, 5000L))));
        SolarSystem sol = new SolarSystem("sol", "Sol", "Home solar system", 0, 0, 0, 1.0, 1.0, "Yellow", List.of(earth), List.of());
        Empire attacker = new Empire("emp_alien", "Alien Empire", "alien", "Autocracy", 10000.0, 0.05, List.of(), List.of(), Map.of(), List.of(), List.of());

        OrbitalBombardmentProcessor.BombardmentResult res = bombardmentProcessor.executeBombardment(
                OrbitalBombardmentProcessor.PLANET_CRACKER, earth, sol, attacker
        );

        assertTrue(res.isPlanetDestroyed());
        assertTrue(res.updatedSystem().planets().isEmpty(), "Planet must be removed from solar system");
        assertFalse(res.updatedSystem().asteroidBelts().isEmpty(), "Shattered asteroid field should be generated");
        assertEquals(5000.0, res.civilianCasualties(), 0.001);
    }

    @Test
    public void testIsotopicFissionWarheadBombardment() {
        Planet mars = new Planet("mars", "Mars", "Red planet", 6.42e23, 0.38, 1.5, 0.0, 6779.0, "Terrestrial", "Carbon Dioxide", false, 0.0, List.of(), List.of(), List.of(new Population("terran", Map.of(25, 1000L))));
        SolarSystem sol = new SolarSystem("sol", "Sol", "Home solar system", 0, 0, 0, 1.0, 1.0, "Yellow", List.of(mars), List.of());
        Empire attacker = new Empire("emp_alien", "Alien Empire", "alien", "Autocracy", 10000.0, 0.05, List.of(), List.of(), Map.of(), List.of(), List.of());

        OrbitalBombardmentProcessor.BombardmentResult res = bombardmentProcessor.executeBombardment(
                OrbitalBombardmentProcessor.FISSION_WARHEADS, mars, sol, attacker
        );

        assertFalse(res.isPlanetDestroyed());
        assertEquals(700.0, res.civilianCasualties(), 0.001);
        assertEquals(300L, res.updatedPlanet().populations().getFirst().ageGroups().get(25));
    }

    @Test
    public void testVirginWorldColonization() {
        Planet virginWorld = new Planet("kepler_452b", "Kepler 452b", "Super Earth", 5.0e24, 1.1, 1.05, 0.0, 15000.0, "Terrestrial", "Oxygen", true, 0.5, List.of(), List.of(), List.of());

        ShipDesign colonyDesign = new ShipDesign(
                "design_colony_ark", "Colony Ark", "emp_terran",
                ShipRole.COLONY_SHIP, "steel", List.of(), "steel", 1.0,
                20000.0, 5000.0, 100.0, 1.2, 100000.0, 400000.0, true, false
        );

        ShipInstance colonyShip = new ShipInstance("ark_1", "design_colony_ark", "emp_terran", 1000.0, 200.0, 100.0, Map.of());
        Fleet colonyFleet = new Fleet("f_colony", "Pioneer Expedition", "emp_terran", "kepler", "", 0, 0, 0, false, "PASSIVE", List.of(colonyShip));

        Race human = new Race("terran", "Human", "Desc", 1.0, 1.0, "Individualist", 1.0, 290.0, "Carbon based", "Oxygen", 18, 50, "Organic", "Diverse", 80);
        Empire terran = new Empire("emp_terran", "Terran", "terran", "Individualist", 50000.0, 0.05, List.of(), List.of(), Map.of(), List.of(), List.of());

        ColonizationProcessor.ColonizationResult res = colonizationProcessor.colonizeWorld(
                virginWorld, colonyFleet, human, terran, List.of(colonyDesign)
        );

        assertTrue(res.isSuccessful());
        assertFalse(res.colonizedPlanet().populations().isEmpty(), "Planet should have seeded population");
        assertEquals(1000L, res.colonizedPlanet().populations().getFirst().ageGroups().get(20));
        assertTrue(res.updatedFleet().ships().isEmpty(), "Colony ship should be consumed upon landing");
    }
}
