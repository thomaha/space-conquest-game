package com.spaceconquest.engine.industry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.refinement.RefinementProcessor;
import com.spaceconquest.engine.scenario.StartingApplicationKnowledge;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FactoryApplicationDataTest {
    @Test void aRenamedDataDefinedProcessExecutesWithoutARecognizedFactoryId() throws Exception {
        var mapper = new ObjectMapper();
        ObjectNode data = mapper.valueToTree(IndustryRecipeCatalog.find("alloy_steel"));
        data.put("applicationId", "test_unlisted_process");
        var work = (ObjectNode) data.get("work");
        work.put("applicationId", "test_unlisted_process");
        work.put("processType", "TEST_PROCESS"); work.put("energyKwhPerBatch", 240);
        ((ObjectNode) work.get("equipment")).put("processType", "TEST_PROCESS");
        ((ObjectNode) data.get("factory")).put("tierCapacityMultiplier", 2);
        var recipe = mapper.treeToValue(data, IndustryRecipeCatalog.Recipe.class);
        var projected = recipe.factory().refinement("Application-provided name", recipe);
        assertEquals("Application-provided name", projected.name());
        assertEquals("TEST_PROCESS", projected.category());
        assertEquals(4, recipe.factory().capacity(3, false));
        var processor = new RefinementProcessor();
        assertFalse(processor.processRecipeExecution(projected, recipe.inputsKg(), 9, 9.81, 1).isSuccessful());
        var actual = processor.processRecipeExecution(projected, recipe.inputsKg(), 10, 9.81, 1);
        assertTrue(actual.isSuccessful());
        assertEquals(recipe.outputsKg(), actual.producedOutputsKg());
    }

    @Test void declaredSiteConstraintsBlockTheSharedWorkQuote() {
        var smelter = facility("pyro_iron_smelting", "industrial_worker");
        var steel = facility("alloy_steel", "industrial_worker");
        var planet = new Planet("body", "Body", "", 1e20, .1, SolarRadiation.AU_KM, 0, 1000,
                "terrestrial", "vacuum", false, 0, List.of(), List.of(), List.of());
        var system = new SolarSystem("sol", "Sol", "", 0, 0, 0, SolarRadiation.SOLAR_MASS_KG,
                1392700, "Yellow", List.of(planet), List.of());
        var state = GameState.builder().empires(List.of(owner(List.of("industrial_production", "pyro_iron_smelting", "alloy_steel"))))
                .solarSystems(List.of(system)).build();
        assertEquals("Process environment unavailable", ProductionWork.quote(state, smelter, 100).blocker());
        assertTrue(ProductionWork.quote(state, steel, 100).batchesPerDay() > 0);
    }

    @Test void openingApplicationKnowledgeResolvesPrerequisitesIndependentlyOfFacilityOrder() {
        var known = StartingApplicationKnowledge.forAssets(owner(List.of("industrial_production")),
                List.of(facility("alloy_steel", "industrial_worker"), facility("pyro_iron_smelting", "industrial_worker")), List.of());
        assertTrue(known.unlockedTechIds().containsAll(List.of("alloy_steel", "pyro_iron_smelting")));
        assertThrows(IllegalStateException.class, () -> StartingApplicationKnowledge.forAssets(owner(List.of("industrial_production")),
                List.of(facility("alloy_steel", "industrial_worker")), List.of()));
    }

    @Test void invalidEquipmentAndConstructionDataCannotProduceAFactoryDefinition() {
        var mapper = new ObjectMapper();
        ObjectNode data = mapper.valueToTree(FactoryDefinition.find("alloy_steel"));
        data.put("constructionWorkPerDay", -1);
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class, () -> mapper.treeToValue(data, FactoryDefinition.class));
        assertThrows(UnsupportedOperationException.class, () -> FactoryDefinition.find("alloy_steel").constructionMaterialsKg().put("gold", 1.0));
    }

    private IndustrialFacility facility(String application, String profession) {
        return new IndustrialFacility(application, "body", application, "owner", IndustrialFacility.PUBLIC_STATE, 1, 100, profession, false, 0);
    }
    private Empire owner(List<String> known) {
        return new Empire("owner", "Owner", "human", "Individualist", 1e6, 0, List.of("sol"), List.of(), Map.of(), known, List.of());
    }
}
