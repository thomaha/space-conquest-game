package com.spaceconquest.engine;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.TreeMap;
import static org.junit.jupiter.api.Assertions.*;

public class PopulationProcessorTest {

    @Test
    public void testPopulationAging() {
        PopulationProcessor processor = new PopulationProcessor();
        Race human = new Race("human", "Human", "", 1.0, 1.0, "Individualist", 1.0, 288.0, "Carbon", "Oxygen", 15, 45, "Organic", "Diverse", 65);
        
        Map<Integer, Long> ageGroups = new TreeMap<>();
        ageGroups.put(20, 1000L); // 1000 people aged 20
        Population pop = new Population("human", ageGroups);
        
        // Advance 10 years
        Population futurePop = processor.advanceYears(pop, human, 10);
        
        // Age 20 becomes age 30
        assertTrue(futurePop.ageGroups().containsKey(30));
        long agedCount = futurePop.ageGroups().get(30);
        assertTrue(agedCount < 1000 && agedCount > 900, "Should have some deaths but mostly survivors: " + agedCount);
        
        // Should have newborns at age 0
        assertTrue(futurePop.ageGroups().containsKey(0));
        assertTrue(futurePop.ageGroups().get(0) > 0);
    }

    @Test
    public void testMortality() {
        PopulationProcessor processor = new PopulationProcessor();
        Race human = new Race("human", "Human", "", 1.0, 1.0, "Individualist", 1.0, 288.0, "Carbon", "Oxygen", 15, 45, "Organic", "Diverse", 65);
        
        Map<Integer, Long> ageGroups = new TreeMap<>();
        ageGroups.put(130, 1000L); // 1000 people aged 130
        Population pop = new Population("human", ageGroups);
        
        // Advance 20 years
        Population futurePop = processor.advanceYears(pop, human, 20);
        
        // At age 150 (more than 2x retirement age of 65), they should all be dead
        assertNull(futurePop.ageGroups().get(150));
    }
}
