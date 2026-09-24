package com.spaceconquest.engine;

import com.spaceconquest.engine.demographics.BureaucratAllocationResult;
import com.spaceconquest.engine.demographics.CitizenCohort;
import com.spaceconquest.engine.demographics.CohortFragmentationProcessor;
import com.spaceconquest.engine.demographics.ColonyDemographics;
import com.spaceconquest.engine.demographics.ColonyFocus;
import com.spaceconquest.engine.demographics.FacilityStaffingResult;
import com.spaceconquest.engine.economy.SystemEconomy;
import com.spaceconquest.engine.economy.SystemEconomyProcessor;
import com.spaceconquest.engine.habitation.BiochemicalConsumptionResult;
import com.spaceconquest.engine.habitation.DemographicWorkforceResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CohortFragmentationTest {

    private CohortFragmentationProcessor processor;
    private PopulationProcessor populationProcessor;
    private SystemEconomyProcessor systemEconomyProcessor;

    @BeforeEach
    void setUp() {
        processor = new CohortFragmentationProcessor();
        populationProcessor = new PopulationProcessor();
        systemEconomyProcessor = new SystemEconomyProcessor();
    }

    @Test
    @DisplayName("Single citizen cohort unit conversion and fractional efficiency")
    void testCitizenCohortUnitsAndEfficiency() {
        CitizenCohort miners = new CitizenCohort("human", "miner", 30, 16_000L);

        assertEquals(0.16, miners.toCohortUnits(), 0.0001);
        assertEquals(0.80, miners.calculateStaffingEfficiency(20_000L), 0.001);
        assertEquals(1.00, miners.calculateStaffingEfficiency(10_000L), 0.001);

        // Required 0.20 cohort units (20,000 citizens)
        assertEquals(0.80, miners.calculateWorkforceEfficiency(0.20), 0.001);

        // Nominal output 500 units * 80% staffing = 400
        assertEquals(400.0, miners.calculateWeightedOutput(500.0, 0.20), 0.001);

        // Wage bill and income tax
        assertEquals(160_000.0, miners.calculateWageBill(10.0), 0.001);
        assertEquals(16_000.0, miners.calculateIncomeTax(10.0, 0.10), 0.001);

        // Immutability and age
        CitizenCohort older = miners.withAgeBracket(65);
        assertEquals(65, older.ageBracket());
        assertTrue(older.isRetired(65));
        assertFalse(miners.isRetired(65));
    }

    @Test
    @DisplayName("Cohort merging and compatibility rules")
    void testCitizenCohortMerging() {
        CitizenCohort c1 = new CitizenCohort("human", "technician", 25, 2_000L);
        CitizenCohort c2 = new CitizenCohort("human", "technician", 25, 1_500L);
        CitizenCohort diffAge = new CitizenCohort("human", "technician", 35, 1_000L);
        CitizenCohort diffProf = new CitizenCohort("human", "engineer", 25, 1_000L);

        assertTrue(c1.canMerge(c2));
        assertFalse(c1.canMerge(diffAge));
        assertFalse(c1.canMerge(diffProf));

        CitizenCohort merged = c1.merge(c2);
        assertEquals(3_500L, merged.headcount());
        assertEquals("human", merged.raceId());
        assertEquals("technician", merged.professionId());

        assertThrows(IllegalArgumentException.class, () -> c1.merge(diffProf));
    }

    @Test
    @DisplayName("Frontier mining colony fragment breakdown and exact population preservation")
    void testSpecializedMiningColonyGeneration() {
        long targetPop = 25_000L;
        ColonyDemographics colony = processor.generateSpecializedColony(
                "mining_outpost_ceres", "sol", "human", targetPop, ColonyFocus.MINING, 30
        );

        assertEquals(targetPop, colony.totalHeadcount(), "Total headcount must exactly match target population");
        assertEquals(0.25, colony.totalCohortUnits(), 0.0001, "25,000 citizens must equal exactly 0.25 cohort units");

        // Single education per cohort verification
        assertTrue(colony.getHeadcount("miner") > 0);
        assertTrue(colony.getHeadcount("technician") > 0);
        assertTrue(colony.getHeadcount("engineer") > 0);
        assertTrue(colony.getHeadcount("medic") > 0);
        assertTrue(colony.getHeadcount("bureaucrat") > 0);
        assertTrue(colony.getHeadcount("police") > 0);

        // Verification of approximate distributions (64% miners, 16% tech, 8% eng, 6% med, 4% bureau, 2% police)
        assertEquals(16_000L, colony.getHeadcount("miner"));
        assertEquals(4_000L, colony.getHeadcount("technician"));
        assertEquals(2_000L, colony.getHeadcount("engineer"));
        assertEquals(1_500L, colony.getHeadcount("medic"));
        assertEquals(1_000L, colony.getHeadcount("bureaucrat"));
        assertEquals(500L, colony.getHeadcount("police"));

        assertEquals(0.16, colony.getCohortUnits("miner"), 0.001);
        assertEquals(0.04, colony.getCohortUnits("technician"), 0.001);
        assertEquals(0.02, colony.getCohortUnits("engineer"), 0.001);
        assertEquals(0.015, colony.getCohortUnits("medic"), 0.001);
        assertEquals(0.010, colony.getCohortUnits("bureaucrat"), 0.001);
        assertEquals(0.005, colony.getCohortUnits("police"), 0.001);
    }

    @Test
    @DisplayName("Multi-profession facility staffing bottleneck calculation")
    void testMultiProfessionStaffingBottleneck() {
        // Build a colony with unbalanced fragments
        List<CitizenCohort> cohorts = List.of(
                new CitizenCohort("human", "miner", 30, 16_000L),
                new CitizenCohort("human", "technician", 30, 4_000L),
                new CitizenCohort("human", "engineer", 30, 250L) // Severely bottlenecked engineers
        );
        ColonyDemographics colony = new ColonyDemographics("outpost", "sol", cohorts);

        // Smelting complex requiring 3,000 miners, 1,000 technicians and 500 engineers
        Map<String, Long> requirements = Map.of(
                "miner", 3_000L,
                "technician", 1_000L,
                "engineer", 500L
        );

        FacilityStaffingResult result = processor.evaluateFacilityStaffing(colony, requirements);

        assertFalse(result.isFullyStaffed());
        assertEquals("engineer", result.bottleneckProfession());
        assertEquals(0.50, result.operationalEfficiency(), 0.001, "Operational efficiency must be capped by the 50% engineer bottleneck");

        Map<String, Double> ratios = result.staffingRatios();
        assertTrue(ratios.get("miner") > 1.0);
        assertTrue(ratios.get("technician") > 1.0);
        assertEquals(0.50, ratios.get("engineer"), 0.001);
    }

    @Test
    @DisplayName("Administrative bureaucrat allocation and red tape bottlenecks")
    void testBureaucratAllocationAndBottlenecks() {
        // Colony with 25,000 citizens
        long pop = 25_000L;

        // 1. Adequate bureaucrat supply
        ColonyDemographics adequate = processor.generateSpecializedColony(
                "colony_1", "sol", "human", pop, ColonyFocus.MINING, 30
        ); // Has 1,000 bureaucrats
        SystemEconomy eco = SystemEconomy.createDefault("sol", "terran", pop);

        BureaucratAllocationResult adequateResult = processor.calculateAdministrativeBureaucratAllocation(adequate, eco);
        assertFalse(adequateResult.isBottlenecked());
        assertTrue(adequateResult.availableBureaucrats() >= adequateResult.totalDemand());

        for (double ratio : adequateResult.administrativeRatios().values()) {
            assertEquals(1.0, ratio, 0.001);
        }
        for (double eff : adequateResult.effectiveEfficiencies().values()) {
            assertEquals(1.0, eff, 0.001);
        }

        // 2. Severe bureaucrat deficit (only 100 bureaucrats instead of ~575 demanded)
        List<CitizenCohort> deficientCohorts = List.of(
                new CitizenCohort("human", "miner", 30, 24_900L),
                new CitizenCohort("human", "bureaucrat", 30, 100L)
        );
        ColonyDemographics deficient = new ColonyDemographics("colony_def", "sol", deficientCohorts);

        BureaucratAllocationResult deficientResult = processor.calculateAdministrativeBureaucratAllocation(deficient, eco);
        assertTrue(deficientResult.isBottlenecked());
        assertEquals(100L, deficientResult.availableBureaucrats());
        assertTrue(deficientResult.totalDemand() > 500L);

        // Effective efficiency must drop due to red tape bottleneck formula: E_eff = E_base * (0.35 + 0.65 * A_i)
        for (double eff : deficientResult.effectiveEfficiencies().values()) {
            assertTrue(eff < 0.60, "Effective efficiency must be heavily penalized by bureaucrat shortage");
            assertTrue(eff >= 0.35, "Effective efficiency must not fall below 35% baseline floor");
        }
    }

    @Test
    @DisplayName("Continuous upward social mobility retraining on cohort fragments")
    void testSocialMobilityRetraining() {
        ColonyDemographics initial = processor.generateSpecializedColony(
                "ceres", "sol", "human", 25_000L, ColonyFocus.MINING, 30
        );

        long initialMiners = initial.getHeadcount("miner"); // 16,000
        long initialTechs = initial.getHeadcount("technician"); // 4,000
        long initialEngs = initial.getHeadcount("engineer"); // 2,000

        // Retraining pass at 100% education efficiency and 2% base mobility rate
        ColonyDemographics retrained = processor.executeSocialMobilityPass(initial, 1.0, 0.02);

        assertEquals(25_000L, retrained.totalHeadcount(), "Total population must be conserved without rounding loss");

        long retrainedMiners = retrained.getHeadcount("miner");
        long retrainedTechs = retrained.getHeadcount("technician");
        long retrainedEngs = retrained.getHeadcount("engineer");

        assertTrue(retrainedMiners < initialMiners, "Low-skill miners should decrease after retraining");
        assertTrue(retrainedTechs > initialTechs, "Technicians should expand after retraining");
        assertTrue(retrainedEngs > initialEngs, "Engineers should expand after retraining");

        long minersPromoted = initialMiners - retrainedMiners;
        assertEquals(320L, minersPromoted, "2% of 16,000 miners = 320 citizens");
    }

    @Test
    @DisplayName("Colony wage bills and localized income tax collection")
    void testWageBillAndIncomeTaxCollection() {
        ColonyDemographics colony = processor.generateSpecializedColony(
                "ceres", "sol", "human", 25_000L, ColonyFocus.MINING, 30
        );

        Map<String, Double> wageMap = Map.of(
                "miner", 10.0,
                "technician", 25.0,
                "engineer", 50.0,
                "medic", 50.0,
                "bureaucrat", 50.0,
                "police", 25.0
        );

        // Expected wage bill:
        // 16,000 * 10 = 160,000
        // 4,000 * 25 = 100,000
        // 2,000 * 50 = 100,000
        // 1,500 * 50 = 75,000
        // 1,000 * 50 = 50,000
        // 500 * 25 = 12,500
        // Total = 497,500 credits
        double expectedWage = 497_500.0;
        assertEquals(expectedWage, colony.calculateTotalWageBill(wageMap), 0.001);

        double taxRate = 0.15;
        double expectedTax = expectedWage * 0.15;
        assertEquals(expectedTax, colony.calculateTotalIncomeTax(wageMap, taxRate), 0.001);
    }

    @Test
    @DisplayName("Profession wage complexity scaling helper")
    void testProfessionWageComplexity() {
        assertEquals(10.0, Profession.getBaseWageForProfession("miner"), 0.001);
        assertEquals(10.0, Profession.getBaseWageForProfession("farmer"), 0.001);
        assertEquals(25.0, Profession.getBaseWageForProfession("technician"), 0.001);
        assertEquals(25.0, Profession.getBaseWageForProfession("police"), 0.001);
        assertEquals(50.0, Profession.getBaseWageForProfession("engineer"), 0.001);
        assertEquals(50.0, Profession.getBaseWageForProfession("bureaucrat"), 0.001);
        assertEquals(100.0, Profession.getBaseWageForProfession("scientist"), 0.001);

        Profession customProf = new Profession("test_eng", "Test Engineer", "Desc", "engineer", 7, 3, 7, 0.80);
        assertEquals(50.0, customProf.getBaseWageCredits(), 0.001);
    }

    @Test
    @DisplayName("PopulationProcessor integration with ColonyDemographics")
    void testPopulationProcessorWithDemographics() {
        ColonyDemographics colony = processor.generateSpecializedColony(
                "ceres", "sol", "human", 25_000L, ColonyFocus.MINING, 30
        );
        Race human = new Race("human", "Human", "Desc", 1.0, 1.0, "Individualist", 9.81, 288.15, "carbon", "nitrogen_oxygen", 18, 45, "ORGANIC", "Diverse", 80);

        BiochemicalConsumptionResult bio = populationProcessor.calculateBiochemicalConsumption(
                colony, human, Map.of("oxygen_gas", 50_000.0, "food_matrix", 50_000.0), true
        );

        assertFalse(bio.isDeficit());
        assertTrue(bio.consumedResources().get("oxygen_gas") > 0);
        assertTrue(bio.consumedResources().get("food_matrix") > 0);

        DemographicWorkforceResult workforce = populationProcessor.calculateWorkforceAndRetirement(
                colony, human, Map.of(), List.of()
        );

        assertEquals(25_000L, workforce.activeWorkers());
        assertEquals(0L, workforce.retiredCitizens());
        assertEquals(0.0, workforce.welfareCreditsCost(), 0.001);
    }

    @Test
    @DisplayName("SystemEconomyProcessor incorporates available bureaucrat fragments")
    void testSystemEconomyProcessorWithBureaucratFragments() {
        SystemEconomy defaultEco = SystemEconomy.createDefault("sol", "terran", 25_000L);

        // 1. Unconstrained (-1)
        SystemEconomy unconstrained = systemEconomyProcessor.processSystemEconomy(defaultEco, 25_000L, -1L, false);
        assertTrue(unconstrained.educationLevel() > 0.0);

        // 2. Severe bureaucrat bottleneck (only 50 bureaucrats)
        SystemEconomy bottlenecked = systemEconomyProcessor.processSystemEconomy(defaultEco, 25_000L, 50L, false);
        assertTrue(bottlenecked.educationLevel() < unconstrained.educationLevel(),
                "Sector efficiency should decrease when bureaucrat fragments are bottlenecked");
        assertTrue(bottlenecked.lawAndOrderLevel() < unconstrained.lawAndOrderLevel());
    }

    @Test
    @DisplayName("Conversion of Population to ColonyDemographics")
    void testPopulationConversion() {
        Population pop = new Population("human", Map.of(20, 10_000L, 40, 15_000L));
        ColonyDemographics demographics = pop.toDemographics("earth", "sol", ColonyFocus.INDUSTRIAL);

        assertEquals(25_000L, demographics.totalHeadcount());
        assertTrue(demographics.getHeadcount("industrial_worker") > 0);
        assertTrue(demographics.getHeadcount("technician") > 0);
        assertTrue(demographics.getHeadcount("engineer") > 0);
        assertTrue(demographics.getHeadcount("bureaucrat") > 0);
    }
}
