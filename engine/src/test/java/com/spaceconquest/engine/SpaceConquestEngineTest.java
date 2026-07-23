package com.spaceconquest.engine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SpaceConquestEngineTest {

    @Test
    public void testPopulationGrowthOverTurns() {
        SpaceConquestEngine engine = new SpaceConquestEngine();
        engine.start();
        
        GameState initialState = engine.getGameState();
        SolarSystem sol = initialState.solarSystems().stream().filter(ss -> ss.id().equals("sol")).findFirst().orElseThrow();
        Planet earth = sol.planets().stream().filter(p -> p.id().equals("earth")).findFirst().orElseThrow();
        Population initialPop = earth.populations().getFirst();
        long initialTotal = initialPop.ageGroups().values().stream().mapToLong(Long::longValue).sum();
        
        // Update for 10 turns (10 years)
        for (int i = 0; i < 10; i++) {
            engine.update();
        }
        
        GameState futureState = engine.getGameState();
        SolarSystem futureSol = futureState.solarSystems().stream().filter(ss -> ss.id().equals("sol")).findFirst().orElseThrow();
        Planet futureEarth = futureSol.planets().stream().filter(p -> p.id().equals("earth")).findFirst().orElseThrow();
        Population futurePop = futureEarth.populations().getFirst();
        long futureTotal = futurePop.ageGroups().values().stream().mapToLong(Long::longValue).sum();
        
        assertTrue(futureTotal > initialTotal, "Population should have grown over 10 years. Initial: " + initialTotal + ", Future: " + futureTotal);
        assertTrue(futurePop.ageGroups().containsKey(0), "Should have newborns");
        assertTrue(futurePop.ageGroups().containsKey(10), "Age 0 should have aged to 10 if there were any, but at least existing groups should have aged");
    }
}
