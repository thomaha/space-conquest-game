package com.spaceconquest.engine.technology;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spaceconquest.engine.DataModelLoader;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResearchPolicyTest {
    private final ObjectMapper mapper = new ObjectMapper();
    @Test void resourceEditsChangeOutcomeWeightsAndOptimizationWithoutConsumerChanges() throws Exception {
        ObjectNode json = mapper.valueToTree(ResearchPolicy.current());
        var outcomes = json.putArray("outcomes");
        var outcome = outcomes.addObject(); outcome.put("weight", 1);
        outcome.putObject("result").put("outcomeType", "INCREMENTAL_GAIN")
                .put("effectMultiplier", 1.7).put("costMultiplier", .9).put("complexityShift", -2);
        ((ObjectNode) json.get("performance")).put("effectMultiplier", 1.4).put("costMultiplier", 1.1).put("complexityShift", 3);
        ((ObjectNode) json.get("miniaturization")).put("costMultiplier", .7);
        var policy = mapper.treeToValue(json, ResearchPolicy.class);
        var processor = new ResearchProcessor(new Random(42), policy);
        assertEquals(1.7, processor.rollBreakthrough(.99).effectMultiplier());
        assertEquals(1.4, processor.evaluateOptimizationPath("PATH_A", 1).effectMultiplier());
        assertEquals(3, processor.evaluateOptimizationPath("PATH_A", 1).complexityShift());
        assertEquals(.7, processor.evaluateOptimizationPath("PATH_B", 1).costMultiplier());
    }
    @Test void malformedPolicyIsRejectedAndEveryCatalogEntryHasExplicitCosts() throws Exception {
        ObjectNode json = mapper.valueToTree(ResearchPolicy.current());
        ((ObjectNode) json.get("outcomes").get(0)).put("weight", .6);
        final ObjectNode invalidWeights = json;
        assertThrows(Exception.class, () -> mapper.treeToValue(invalidWeights, ResearchPolicy.class));
        json = mapper.valueToTree(ResearchPolicy.current()); json.put("exchangeProgressFraction", 1);
        final ObjectNode invalidExchange = json;
        assertThrows(Exception.class, () -> mapper.treeToValue(invalidExchange, ResearchPolicy.class));
        for (var technology : DataModelLoader.loadTechnologies()) {
            assertNotNull(technology.researchPoints(), technology.id());
            for (var application : technology.applications()) {
                assertNotNull(application.researchPoints(), application.id());
                assertNotNull(application.refinementResearchPoints(), application.id());
            }
        }
    }
}
