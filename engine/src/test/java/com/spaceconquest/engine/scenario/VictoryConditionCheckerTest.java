package com.spaceconquest.engine.scenario;

import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.Population;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.community.GalacticCommunity;
import com.spaceconquest.engine.community.GalacticResolution;
import com.spaceconquest.engine.megastructure.Megastructure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class VictoryConditionCheckerTest {

    private VictoryConditionChecker checker;

    @BeforeEach
    public void setup() {
        checker = new VictoryConditionChecker();
    }

    @Test
    public void testEconomicMonopolyVictory() {
        Empire rich = new Empire("emp_rich", "Merchant Guild", "human", "Individualist", 600000.0, 0.05, List.of(), List.of(), Map.of(), List.of(), List.of());
        GameState state = new GameState(1, "RUNNING", List.of(), List.of(rich), List.of(), List.of(), List.of(), List.of(), List.of());
        CampaignSetup setup = new CampaignSetup("Econ Campaign", 10, 0.2, CampaignSetup.AI_BALANCED, 1, CampaignSetup.VICTORY_ECONOMIC_MONOPOLY, 500);

        VictoryConditionChecker.VictoryCheckResult res = checker.evaluateVictory(state, setup, null, List.of());

        assertTrue(res.isVictoryAchieved());
        assertEquals("emp_rich", res.winningEmpireId());
        assertEquals(CampaignSetup.VICTORY_ECONOMIC_MONOPOLY, res.victoryConditionType());
    }

    @Test
    public void testMegastructureAscensionVictory() {
        Megastructure completedDyson = new Megastructure(
                "dyson_sol", "Sol Dyson Sphere", Megastructure.TYPE_DYSON_SPHERE,
                "sol", "sol_star", "terran_confederation",
                3, 3, 0.0, 10.0, true, 1000000.0, Map.of(), 0
        );

        GameState state = new GameState();
        CampaignSetup setup = new CampaignSetup("Dyson Campaign", 10, 0.2, CampaignSetup.AI_BALANCED, 1, CampaignSetup.VICTORY_MEGASTRUCTURE_ASCENSION, 1);

        VictoryConditionChecker.VictoryCheckResult res = checker.evaluateVictory(state, setup, null, List.of(completedDyson));

        assertTrue(res.isVictoryAchieved());
        assertEquals("terran_confederation", res.winningEmpireId());
        assertEquals(CampaignSetup.VICTORY_MEGASTRUCTURE_ASCENSION, res.victoryConditionType());
    }

    @Test
    public void testDiplomaticFederationVictory() {
        Empire emp1 = new Empire("emp1", "Emp 1", "human", "Individualist", 10000.0, 0.05, List.of(), List.of(), Map.of(), List.of(), List.of());
        Empire emp2 = new Empire("emp2", "Emp 2", "vulkan", "Collectivist", 10000.0, 0.05, List.of(), List.of(), Map.of(), List.of(), List.of());
        GameState state = new GameState(1, "RUNNING", List.of(), List.of(emp1, emp2), List.of(), List.of(), List.of(), List.of(), List.of());

        GalacticResolution r1 = new GalacticResolution("r1", "Charter 1", GalacticResolution.TYPE_ANTI_PIRACY, "emp1", "", 0, GalacticResolution.STATUS_PASSED, Map.of());
        GalacticResolution r2 = new GalacticResolution("r2", "Charter 2", GalacticResolution.TYPE_FREE_TRADE, "emp1", "", 0, GalacticResolution.STATUS_PASSED, Map.of());
        GalacticResolution r3 = new GalacticResolution("r3", "Charter 3", GalacticResolution.TYPE_MUTUAL_DEFENSE, "emp1", "", 0, GalacticResolution.STATUS_PASSED, Map.of());

        GalacticCommunity comm = new GalacticCommunity("c1", "Senate", List.of("emp1", "emp2"), List.of(), List.of(r1, r2, r3), List.of(), 10, 10);
        CampaignSetup setup = new CampaignSetup("Fed Campaign", 10, 0.2, CampaignSetup.AI_BALANCED, 1, CampaignSetup.VICTORY_DIPLOMATIC_FEDERATION, 3);

        VictoryConditionChecker.VictoryCheckResult res = checker.evaluateVictory(state, setup, comm, List.of());

        assertTrue(res.isVictoryAchieved());
        assertEquals(CampaignSetup.VICTORY_DIPLOMATIC_FEDERATION, res.victoryConditionType());
    }
}
