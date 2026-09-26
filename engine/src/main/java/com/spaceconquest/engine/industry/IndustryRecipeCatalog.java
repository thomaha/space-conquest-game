package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.industry.refinement.RefinementProcessor;
import com.spaceconquest.engine.industry.refinement.RefinementRecipe;

import java.util.HashMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Connects facility types to resource recipes and researched technology. */
public final class IndustryRecipeCatalog {
    private static final Map<String, Recipe> RECIPES = createRecipes();

    private IndustryRecipeCatalog() {}

    public record Recipe(String applicationId, List<String> requiredTechnologies,
                         Map<String, Double> improvements, Map<String, Double> inputsKg,
                         Map<String, Double> outputsKg, int workersPerBatch, int minTier,
                         double powerDrawKw,
                         boolean extractsDeposit) {
        public Recipe {
            requiredTechnologies = List.copyOf(requiredTechnologies);
            improvements = Map.copyOf(improvements);
            inputsKg = Map.copyOf(inputsKg);
            outputsKg = Map.copyOf(outputsKg);
        }

        public double technologyMultiplier(Empire empire) {
            double multiplier = 1.0;
            for (var improvement : improvements.entrySet()) {
                if (empire.unlockedTechIds().contains(improvement.getKey())) {
                    multiplier += improvement.getValue();
                }
            }
            return multiplier;
        }
    }

    public static Recipe find(String applicationId) {
        return RECIPES.get(applicationId);
    }

    public static boolean isUnlocked(Recipe recipe, Empire empire) {
        return recipe != null && empire != null && empire.unlockedTechIds() != null
                && empire.unlockedTechIds().containsAll(recipe.requiredTechnologies());
    }

    public static Collection<Recipe> all() {
        return RECIPES.values();
    }

    private static Map<String, Recipe> createRecipes() {
        Map<String, Recipe> recipes = new HashMap<>();
        recipes.put("mining_outpost", new Recipe("mining_outpost", List.of("industrial_production"),
                Map.of("geological_prospecting", 0.25, "supply_chain_automation", 0.10),
                Map.of(), Map.of("iron_ore", 100.0), 100, 1, 500.0, true));
        recipes.put("industrial_soil_cultivation", new Recipe("industrial_soil_cultivation",
                List.of("industrial_production"), Map.of("supply_chain_automation", 0.10),
                Map.of("nitrates", 10.0, "phosphates", 5.0, "potash", 5.0, "water_ice", 50.0),
                Map.of("food_matrix", 120.0), 1, 1, 2500.0, false));
        for (RefinementRecipe refinement : RefinementProcessor.STANDARD_RECIPES) {
            int workersPerBatch = "consumer_goods_mfg".equals(refinement.id()) ? 10 : 100;
            recipes.put(refinement.id(), new Recipe(refinement.id(), List.of("industrial_production"),
                    Map.of("supply_chain_automation", 0.10), refinement.inputMaterialsKg(),
                    combinedOutputs(refinement), workersPerBatch, refinement.minFacilityTier(),
                    refinement.powerDrawKw(), false));
        }
        return Map.copyOf(recipes);
    }

    private static Map<String, Double> combinedOutputs(RefinementRecipe refinement) {
        Map<String, Double> outputs = new HashMap<>(refinement.outputMaterialsKg());
        refinement.byproductMaterialsKg().forEach((material, quantity) ->
                outputs.merge(material, quantity, Double::sum));
        return outputs;
    }
}
