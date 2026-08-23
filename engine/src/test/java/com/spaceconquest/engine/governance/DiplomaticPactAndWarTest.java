package com.spaceconquest.engine.governance;

import com.spaceconquest.engine.Empire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class DiplomaticPactAndWarTest {

    private DiplomacyProcessor processor;
    private Empire terran;
    private Empire vulkanEmpire;
    private Empire hiveCollective;

    @BeforeEach
    void setUp() {
        processor = new DiplomacyProcessor();
        terran = new Empire(
                "terran", "Terran Confederation", "human", "Individualist",
                50000.0, 0.10, List.of("sol"),
                List.of(), Map.of(), List.of(), List.of()
        );
        vulkanEmpire = new Empire(
                "vulkan", "Vulkan Sovereignty", "vulkan", "Collectivist",
                60000.0, 0.08, List.of("vulkan_sys"),
                List.of(), Map.of(), List.of(), List.of()
        );
        hiveCollective = new Empire(
                "hive", "Zerg Collective", "plasma_anomaly", "Hive mind",
                10000.0, 0.0, List.of("hive_sys"),
                List.of(), Map.of(), List.of(), List.of()
        );
    }

    @Test
    void testHiveMindRejectsTradeProposals() {
        DiplomaticProposal proposal = new DiplomaticProposal(
                "prop_1", "terran", "hive", DiplomaticPact.MUTUAL_TRADE_AGREEMENT, DiplomaticProposal.STATUS_PENDING
        );
        boolean accepted = processor.evaluateProposalAcceptance(proposal, terran, hiveCollective, List.of(), 1.0);
        assertFalse(accepted);
    }

    @Test
    void testDemocraticWarPenaltyWithoutCasusBelli() {
        // Unprovoked war by democracy
        DiplomacyProcessor.WarDeclarationResult unprovoked = processor.evaluateWarDeclarationImpact(
                terran, vulkanEmpire, List.of(), List.of()
        );
        assertFalse(unprovoked.isJustified());
        assertEquals(-0.40, unprovoked.civilianHappinessPenalty(), 0.01);

        // With valid Casus Belli
        CasusBelli cb = new CasusBelli("cb_1", "terran", "vulkan", CasusBelli.BORDER_FRICTION, 50.0, 5.0);
        DiplomacyProcessor.WarDeclarationResult justified = processor.evaluateWarDeclarationImpact(
                terran, vulkanEmpire, List.of(cb), List.of()
        );
        assertTrue(justified.isJustified());
        assertEquals(0.0, justified.civilianHappinessPenalty(), 0.01);
    }
}
