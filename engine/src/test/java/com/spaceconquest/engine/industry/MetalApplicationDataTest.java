package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MetalApplicationDataTest {
    @Test void extractionDefinitionsAllBelongToResearchableApplications() throws Exception {
        var applications = DataModelLoader.loadTechnologies().stream().flatMap(t -> t.applications().stream()).toList();
        for (var recipe : IndustryRecipeCatalog.all()) if (recipe.extractsDeposit()) {
            var application = applications.stream().filter(a -> a.id().equals(recipe.applicationId())).findFirst().orElseThrow();
            assertEquals(application.productionRecipe(), recipe);
            assertNotNull(recipe.factory()); assertNotNull(recipe.work());
            assertNotNull(application.operatingReserve());
            assertTrue(recipe.inputSources().stream().anyMatch(i -> i.source() == ProcessInput.Source.DEPOSIT));
        }
    }

    @Test void metallurgicalRecipesConserveTheirDeclaredElementsIncludingRealRetainedResidue() throws Exception {
        var technologies = DataModelLoader.loadTechnologies();
        var knowledge = new ArrayList<String>();
        technologies.forEach(t -> { knowledge.add(t.id()); t.applications().forEach(a -> knowledge.add(a.id())); });
        var owner = new Empire("owner", "Owner", "human", "Individualist", 1e9, 0, List.of(), List.of(), Map.of(), knowledge, List.of());
        var state = GameState.builder().empires(List.of(owner)).build();
        for (var recipe : IndustryRecipeCatalog.all()) {
            if (recipe.factory() == null || !recipe.factory().marketOrientations().contains("METALLURGY")) continue;
            var input = new TreeMap<String, Double>(); var output = new TreeMap<String, Double>();
            if (recipe.refineryProcess() != null) {
                var work = ProductionWork.initialEquipment(recipe.applicationId());
                var facility = new IndustrialFacility("unit", "body", recipe.applicationId(), "owner", IndustrialFacility.PUBLIC_STATE,
                        recipe.minTier(), work.referenceOperators(), work.operatorProfession(), false, 0);
                var plan = RefineryProcess.plan(state, facility, RefineryState.empty());
                input.putAll(plan.intakePerBatch().elementsKg());
                plan.recipe().outputsKg().forEach((id, kg) -> elements(output, id, kg));
                plan.residuePerBatch().elementsKg().forEach((e, kg) -> output.merge(e, kg, Double::sum));
            } else {
                recipe.inputSources().forEach(i -> elements(input, i.materialId(), i.kgPerBatch()));
                recipe.outputsKg().forEach((id, kg) -> elements(output, id, kg));
            }
            var elements = new TreeSet<>(input.keySet()); elements.addAll(output.keySet());
            for (String element : elements) assertEquals(input.getOrDefault(element, 0.0), output.getOrDefault(element, 0.0), 1e-6,
                    recipe.applicationId() + " changes " + element);
        }
    }

    @Test void nuclearSeparationRetainsTheMassThatUsedToDisappear() {
        for (String id : List.of("uranium_refining", "thorium_refining")) {
            var equipment = ProductionWork.initialEquipment(id);
            var facility = new IndustrialFacility("unit", "body", id, "owner", IndustrialFacility.PUBLIC_STATE, 1, 10, equipment.operatorProfession(), false, 0);
            var plan = RefineryProcess.plan(GameState.builder().build(), facility, RefineryState.empty());
            double primary = plan.recipe().outputsKg().values().stream().mapToDouble(Double::doubleValue).sum();
            assertEquals(1000, primary + plan.residuePerBatch().massKg(), 1e-8);
            assertTrue(plan.residuePerBatch().massKg() >= 900);
            var saved = plan.complete(.5, 1);
            assertEquals(plan.residuePerBatch().massKg() / 2, saved.storedKg(), 1e-8);
        }
    }

    private void elements(Map<String, Double> elements, String material, double mass) {
        try {
            var definition = DataModelLoader.loadMaterials().stream().filter(m -> m.id().equals(material)).findFirst().orElseThrow();
            definition.composition().forEach((element, percent) -> elements.merge(element, mass * percent / 100, Double::sum));
        } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
}
