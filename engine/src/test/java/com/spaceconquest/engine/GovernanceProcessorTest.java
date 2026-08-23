package com.spaceconquest.engine;

import com.spaceconquest.engine.governance.GovernanceProcessor;
import com.spaceconquest.engine.governance.IdeologicalAccessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class GovernanceProcessorTest {

    private GovernanceProcessor governanceProcessor;
    private IdeologicalAccessionManager accessionManager;
    private List<MinistryPortfolio> portfolios;

    @BeforeEach
    public void setUp() throws IOException {
        governanceProcessor = new GovernanceProcessor();
        accessionManager = new IdeologicalAccessionManager(governanceProcessor);
        portfolios = DataModelLoader.loadMinistries();
    }

    @Test
    public void testExpertiseSynergyBonus() {
        // Scientist assigned to Ministry of Technology -> full synergy bonus 1.25
        double scientistBonus = governanceProcessor.calculateMinisterEfficiency(
                "ministry_technology_application",
                "scientist",
                portfolios,
                false
        );
        assertEquals(1.25, scientistBonus, 0.001);

        // Miner assigned to Ministry of Industry -> full synergy bonus 1.25
        double minerBonus = governanceProcessor.calculateMinisterEfficiency(
                "ministry_industry_refining",
                "miner",
                portfolios,
                false
        );
        assertEquals(1.25, minerBonus, 0.001);

        // Farmer assigned to Ministry of Agriculture -> full synergy bonus 1.25
        double farmerBonus = governanceProcessor.calculateMinisterEfficiency(
                "ministry_agricultural_biosphere",
                "farmer",
                portfolios,
                false
        );
        assertEquals(1.25, farmerBonus, 0.001);
    }

    @Test
    public void testBureaucratUniversalAdministrativeBonus() {
        // Bureaucrat assigned to ANY ministry grants baseline 1.10 modifier
        double techBureaucrat = governanceProcessor.calculateMinisterEfficiency(
                "ministry_technology_application",
                "bureaucrat",
                portfolios,
                false
        );
        assertEquals(1.10, techBureaucrat, 0.001);

        double agriBureaucrat = governanceProcessor.calculateMinisterEfficiency(
                "ministry_agricultural_biosphere",
                "bureaucrat",
                portfolios,
                false
        );
        assertEquals(1.10, agriBureaucrat, 0.001);
    }

    @Test
    public void testNonMatchingProfessionYieldsBaseline() {
        // Soldier assigned to Ministry of Agriculture -> 1.0 (no bonus)
        double soldierInAgri = governanceProcessor.calculateMinisterEfficiency(
                "ministry_agricultural_biosphere",
                "soldier",
                portfolios,
                false
        );
        assertEquals(1.00, soldierInAgri, 0.001);
    }

    @Test
    public void testHiveMindTotalGovernmentBypass() {
        // Hive minds bypass ministries and governors completely
        double hiveTechBonus = governanceProcessor.calculateMinisterEfficiency(
                "ministry_technology_application",
                "scientist",
                portfolios,
                true // isHiveMind
        );
        assertEquals(1.00, hiveTechBonus, 0.001);

        SystemGovernor gov = new SystemGovernor("gov_1", "Hive Unit", "sol", "scientist", 1.25, 0.20);
        double hiveGovBonus = governanceProcessor.calculateGovernorEfficiency(gov, "RESEARCH", true);
        assertEquals(1.00, hiveGovBonus, 0.001);
    }

    @Test
    public void testSystemGovernorLocalizedIndustrySynergy() {
        SystemGovernor miningGov = new SystemGovernor("gov_miner", "Marcus", "sol", "miner", 1.15, 0.20);

        // Matching industry (MINING)
        double matchingBonus = governanceProcessor.calculateGovernorEfficiency(miningGov, "MINING", false);
        assertEquals(1.15, matchingBonus, 0.001);

        // Non-matching industry (AGRICULTURE) -> 1.0 baseline
        double nonMatchingBonus = governanceProcessor.calculateGovernorEfficiency(miningGov, "AGRICULTURE", false);
        assertEquals(1.00, nonMatchingBonus, 0.001);
    }

    @Test
    public void testDemocraticElectionDrivenByResourceShortages() {
        Empire terran = new Empire(
                "terran",
                "Terran Confederation",
                "human",
                "Individualist",
                50000.0,
                0.15,
                List.of("sol"),
                List.of(),
                Map.of(),
                List.of(),
                List.of()
        );

        // Severe food crisis in democracy -> citizens vote for a farmer
        Map<String, Double> shortages = Map.of("food", 0.85);
        Empire postElection = accessionManager.runDemocraticElection(terran, shortages, portfolios);

        assertNotNull(postElection);
        MinistryAssignment agriMinistry = postElection.ministries().stream()
                .filter(m -> m.portfolioId().equals("ministry_agricultural_biosphere"))
                .findFirst()
                .orElseThrow();
        assertEquals("farmer", agriMinistry.assignedCitizenProfessionId());
        assertEquals(1.25, agriMinistry.calculatedEfficiencyModifier(), 0.001);
    }

    @Test
    public void testAutocraticDirectAppointment() {
        Empire silicon = new Empire(
                "silicon",
                "Silicon Hegemony",
                "silicon_core",
                "Collectivist",
                40000.0,
                0.20,
                List.of("sol"),
                List.of(),
                Map.of(),
                List.of(),
                List.of()
        );

        Empire updated = accessionManager.appointMinister(silicon, "ministry_technology_application", "scientist", portfolios);
        assertNotNull(updated);
        assertEquals(1, updated.ministries().size());
        assertEquals("scientist", updated.ministries().getFirst().assignedCitizenProfessionId());
        assertEquals(1.25, updated.ministries().getFirst().calculatedEfficiencyModifier(), 0.001);
    }
}
