package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.Empire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class IndustryProcessorTest {

    private IndustryProcessor industryProcessor;

    @BeforeEach
    public void setUp() {
        industryProcessor = new IndustryProcessor();
    }

    @Test
    public void testExpansionPenaltyReducesThroughputByFiftyPercent() {
        IndustrialFacility normalFacility = new IndustrialFacility(
                "fac_norm", "earth", "refining_blast_furnace", "emp_terran",
                IndustrialFacility.PUBLIC_STATE, 1, 10, "industrial_worker", false, 0.0
        );

        IndustrialFacility expandingFacility = new IndustrialFacility(
                "fac_exp", "earth", "refining_blast_furnace", "emp_terran",
                IndustrialFacility.PUBLIC_STATE, 1, 10, "industrial_worker", true, 0.0
        );

        assertEquals(1.0, normalFacility.getEffectiveThroughputMultiplier(), 0.001);
        assertEquals(0.50, expandingFacility.getEffectiveThroughputMultiplier(), 0.001);

        Empire empire = new Empire(
                "emp_terran", "Terran", "terran", "Individualist",
                1000.0, 0.05, List.of("sol"), List.of(), Map.of(), List.of(), List.of()
        );

        IndustryProcessor.IndustryTurnResult res = industryProcessor.processIndustrialProduction(
                List.of(normalFacility, expandingFacility), List.of(), List.of(empire), List.of(), List.of(), List.of(), 0.05
        );

        assertTrue(res.materialYieldsKg().isEmpty(), "Expansion progress must not mint output");
    }

    @Test
    public void testExpansionPassDoesNotMintOwnerProfit() {
        IndustrialFacility publicStateFac = new IndustrialFacility(
                "fac_pub", "earth", "app_mining", "emp_terran",
                IndustrialFacility.PUBLIC_STATE, 1, 10, "miner", false, 0.0
        );

        IndustrialFacility corporateFac = new IndustrialFacility(
                "fac_corp", "earth", "app_electronics", "corp_atlas",
                IndustrialFacility.PRIVATE_CORPORATE, 1, 10, "technician", false, 0.0
        );

        Empire terran = new Empire(
                "emp_terran", "Terran", "terran", "Individualist",
                0.0, 0.05, List.of("sol"), List.of(), Map.of(), List.of(), List.of()
        );

        Corporation corp = new Corporation(
                "corp_atlas", "Atlas", "emp_terran", "sol", "technology",
                0.0, List.of(), List.of(), List.of()
        );

        IndustryProcessor.IndustryTurnResult res = industryProcessor.processIndustrialProduction(
                List.of(publicStateFac, corporateFac), List.of(), List.of(terran), List.of(corp), List.of(), List.of(), 0.05
        );

        Empire updatedEmpire = res.updatedEmpires().getFirst();
        Corporation updatedCorp = res.updatedCorporations().getFirst();

        assertEquals(0.0, updatedEmpire.treasuryCredits(), 0.001);
        assertEquals(0.0, updatedCorp.liquidCapitalReserves(), 0.001);
    }

    @Test
    public void testFacilityExpansionProjectCompletionUpgradesTier() {
        IndustrialFacility expandingFac = new IndustrialFacility(
                "fac_1", "earth", "app_foundry", "emp_terran",
                IndustrialFacility.PUBLIC_STATE, 1, 10, "industrial_worker", true, 0.0
        );

        FacilityExpansionProject proj = new FacilityExpansionProject(
                "proj_1", "fac_1", 2, 100.0, 200.0, 5000.0
        );

        // Step 1: Advance by 100 hrs -> reaches 200 required hrs -> Completes upgrade!
        IndustryProcessor.IndustryTurnResult res = industryProcessor.processIndustrialProduction(
                List.of(expandingFac), List.of(proj), List.of(), List.of(), List.of(), List.of(), 0.05
        );

        assertTrue(res.remainingProjects().isEmpty(), "Project should be completed and removed");
        IndustrialFacility upgradedFac = res.updatedFacilities().getFirst();
        assertEquals(2, upgradedFac.tier(), "Tier should be upgraded to 2");
        assertFalse(upgradedFac.isUndergoingExpansion(), "Expansion flag should be reset to false");
    }
}
