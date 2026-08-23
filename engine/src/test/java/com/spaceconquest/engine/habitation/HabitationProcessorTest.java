package com.spaceconquest.engine.habitation;

import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.Population;
import com.spaceconquest.engine.PopulationProcessor;
import com.spaceconquest.engine.Profession;
import com.spaceconquest.engine.Race;
import com.spaceconquest.engine.ship.ShipInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class HabitationProcessorTest {

    private PopulationProcessor processor;
    private List<Race> races;
    private List<Profession> professions;

    @BeforeEach
    void setUp() throws IOException {
        processor = new PopulationProcessor();
        races = DataModelLoader.loadRaces();
        professions = DataModelLoader.loadProfessions();
    }

    private Race findRace(String id) {
        return races.stream().filter(r -> r.id().equalsIgnoreCase(id)).findFirst().orElseThrow();
    }

    private Profession findProfession(String id) {
        return professions.stream().filter(p -> p.id().equalsIgnoreCase(id)).findFirst().orElseThrow();
    }

    @Test
    void testCarbonBiochemicalConsumptionDiverseFood() {
        Race human = findRace("human");
        Population pop = new Population("human", Map.of(25, 10000L)); // 10k humans

        Map<String, Double> inventory = new HashMap<>();
        inventory.put("oxygen_gas", 1000.0);
        inventory.put("food_matrix", 2000.0);

        BiochemicalConsumptionResult res = processor.calculateBiochemicalConsumption(pop, human, inventory, true);

        assertFalse(res.isDeficit());
        assertEquals(500.0, res.consumedResources().get("oxygen_gas"), 0.01);
        assertEquals(1000.0, res.consumedResources().get("food_matrix"), 0.01);
        assertTrue(res.happinessModifier() > 0);
        assertTrue(res.growthModifier() > 1.0);
    }

    @Test
    void testCarbonBiochemicalDeficit() {
        Race human = findRace("human");
        Population pop = new Population("human", Map.of(25, 10000L));

        Map<String, Double> emptyInventory = Map.of();
        BiochemicalConsumptionResult res = processor.calculateBiochemicalConsumption(pop, human, emptyInventory, false);

        assertTrue(res.isDeficit());
        assertTrue(res.happinessModifier() < 0);
        assertEquals(0.0, res.growthModifier(), 0.01);
    }

    @Test
    void testSiliconLithovoreConsumption() {
        Race silicon = findRace("silicon_core");
        Population pop = new Population("silicon_core", Map.of(100, 5000L));

        Map<String, Double> inventory = new HashMap<>();
        inventory.put("silicates", 1500.0);
        inventory.put("limestone", 500.0);

        BiochemicalConsumptionResult res = processor.calculateBiochemicalConsumption(pop, silicon, inventory, true);

        assertFalse(res.isDeficit());
        assertFalse(res.consumedResources().containsKey("oxygen_gas"));
        assertEquals(750.0, res.consumedResources().get("silicates"), 0.01);
        assertEquals(250.0, res.consumedResources().get("limestone"), 0.01);
    }

    @Test
    void testSyntheticMachineImmuneToAging() {
        Race synthetic = findRace("synthetic_machine");
        Profession scientist = findProfession("scientist");
        Population pop = new Population("synthetic_machine", Map.of(500, 1000L));

        DemographicWorkforceResult workforce = processor.calculateWorkforceAndRetirement(
                pop, synthetic, scientist, List.of()
        );

        assertEquals(1000L, workforce.activeWorkers());
        assertEquals(0L, workforce.retiredCitizens());
        assertEquals(0.0, workforce.welfareCreditsCost(), 0.01);
    }

    @Test
    void testDemographicRetirementAndGeneTechExtensions() {
        Race human = findRace("human");
        Profession miner = findProfession("miner"); // retirement ~ 0.65 * 85 = 55.25 -> 55

        Population pop = new Population("human", Map.of(
                30, 1000L,
                60, 500L
        ));

        // Baseline without gene tech
        DemographicWorkforceResult baseline = processor.calculateWorkforceAndRetirement(
                pop, human, miner, List.of()
        );
        assertEquals(1000L, baseline.activeWorkers());
        assertEquals(500L, baseline.retiredCitizens());
        assertEquals(55, baseline.effectiveRetirementAge());

        // With gene therapy (+30 natural lifespan -> 115 natural lifespan -> miner ret = 115 * 0.65 = 75)
        DemographicWorkforceResult augmented = processor.calculateWorkforceAndRetirement(
                pop, human, miner, List.of("gene_therapy")
        );
        assertEquals(1500L, augmented.activeWorkers());
        assertEquals(0L, augmented.retiredCitizens());
        assertEquals(75, augmented.effectiveRetirementAge());
    }

    @Test
    void testHiveMindReproductionByQueens() {
        Race plasma = findRace("plasma_anomaly");
        Population pop = new Population("plasma_anomaly", Map.of(20, 500L));

        Population advanced = processor.advanceYears(pop, plasma, 2, 3, List.of());
        // 3 queens * 1000 * 2 years = 6000 newborns
        long newborns = advanced.ageGroups().getOrDefault(0, 0L);
        assertEquals(6000L, newborns);
    }

    @Test
    void testPassengerTransitStasisVsConscious() {
        Race human = findRace("human");
        ShipInstance shipStasis = new ShipInstance(
                "ship_1", "design_1", "emp_1", 100.0, 100.0, 50.0,
                Map.of(), 500, "human", ShipInstance.MODE_CRYOGENIC_STASIS
        );

        PassengerLogisticsResult stasisRes = processor.processPassengerLifeSupport(shipStasis, human, Map.of());
        assertEquals(0, stasisRes.consumedSupplies().size());
        assertEquals(0, stasisRes.casualtyCount());
        assertFalse(stasisRes.lifeSupportDeficit());

        ShipInstance shipConscious = new ShipInstance(
                "ship_2", "design_1", "emp_1", 100.0, 100.0, 50.0,
                Map.of(), 500, "human", ShipInstance.MODE_CONSCIOUS
        );

        Map<String, Double> pantry = Map.of("oxygen_gas", 50.0, "food_matrix", 100.0);
        PassengerLogisticsResult consciousRes = processor.processPassengerLifeSupport(shipConscious, human, pantry);
        assertFalse(consciousRes.lifeSupportDeficit());
        assertEquals(25.0, consciousRes.consumedSupplies().get("oxygen_gas"), 0.01);
        assertEquals(50.0, consciousRes.consumedSupplies().get("food_matrix"), 0.01);
    }
}
