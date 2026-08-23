package com.spaceconquest.engine.community;

import com.spaceconquest.engine.Empire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class GalacticCommunityProcessorTest {

    private GalacticCommunityProcessor processor;

    @BeforeEach
    public void setup() {
        processor = new GalacticCommunityProcessor();
    }

    @Test
    public void testVotingWeightCalculation() {
        Empire empire = new Empire(
                "terran_confederation", "Terran Confederation", "human", "Individualist",
                50000.0, 0.05, List.of("sol"), List.of(), Map.of(), List.of(), List.of()
        );

        double weight = processor.calculateVotingWeight(empire, 100000.0, 5000.0);
        // Base 100 + 100000*0.001 (100) + 5000*0.01 (50) = 250.0
        assertEquals(250.0, weight, 0.1);
    }

    @Test
    public void testResolutionVotingPassageAndSanctionEnactment() {
        Empire emp1 = new Empire("emp_1", "Empire One", "human", "Individualist", 10000.0, 0.05, List.of("sol"), List.of(), Map.of(), List.of(), List.of());
        Empire emp2 = new Empire("emp_2", "Empire Two", "vulkan", "Collectivist", 10000.0, 0.05, List.of("alpha"), List.of(), Map.of(), List.of(), List.of());
        Empire rogue = new Empire("emp_rogue", "Rogue Syndicate Empire", "alien", "Autocratic", 5000.0, 0.05, List.of("sirius"), List.of(), Map.of(), List.of(), List.of());

        GalacticResolution embargo = new GalacticResolution(
                "res_embargo_rogue", "Trade Embargo against Rogue State",
                GalacticResolution.TYPE_SANCTION_EMBARGO, "emp_1", "emp_rogue",
                1, GalacticResolution.STATUS_PROPOSED,
                Map.of("emp_1", GalacticResolution.VOTE_AYE, "emp_2", GalacticResolution.VOTE_AYE, "emp_rogue", GalacticResolution.VOTE_NAY)
        );

        GalacticCommunity community = new GalacticCommunity(
                "comm_1", "Galactic Senate",
                List.of("emp_1", "emp_2", "emp_rogue"),
                List.of(embargo),
                List.of(),
                List.of(),
                10,
                10
        );

        GalacticCommunityProcessor.CommunityTurnResult result = processor.processSenateSession(
                community, List.of(emp1, emp2, rogue),
                Map.of("emp_1", 20000.0, "emp_2", 20000.0, "emp_rogue", 5000.0),
                Map.of("emp_1", 1000.0, "emp_2", 1000.0, "emp_rogue", 500.0),
                1
        );

        assertNotNull(result);
        assertEquals(1, result.newlyPassedResolutions().size());
        assertEquals(GalacticResolution.STATUS_PASSED, result.newlyPassedResolutions().get(0).status());
        assertEquals(1, result.newlyEnactedSanctions().size());
        assertEquals("emp_rogue", result.newlyEnactedSanctions().get(0).targetEmpireId());
        assertEquals(0.50, result.newlyEnactedSanctions().get(0).tradeTariffPenaltyRate(), 0.01);
    }
}
