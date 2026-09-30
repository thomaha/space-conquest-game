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
        for (String material : List.of("aluminum_ore", "copper_ore", "silicates", "nitrates",
                "phosphates", "potash", "carbon", "water_ice", "hydrocarbons")) {
            String application = material + "_mining";
            recipes.put(application, new Recipe(application, List.of("industrial_production"),
                    Map.of("geological_prospecting", 0.25, "supply_chain_automation", 0.10),
                    Map.of(), Map.of(material, 1000.0), 100, 1, 500.0, true));
        }
        Map.of("gold", 10.0, "silver_ore", 50.0, "rare_earth_fluorides", 25.0,
                "uranium_ore", 50.0, "thorium_ore", 40.0,
                "lithium_ore", 100.0, "nickel_ore", 100.0)
                .forEach((material, yieldKg) -> {
                    String application = material + "_mining";
                    recipes.put(application, new Recipe(application, List.of("industrial_production"),
                            Map.of("geological_prospecting", 0.25, "supply_chain_automation", 0.10),
                            Map.of(), Map.of(material, yieldKg), 100, 1, 800.0, true));
                });
        recipes.put("surface_water_treatment", new Recipe("surface_water_treatment",
                List.of("industrial_production"), Map.of("supply_chain_automation", 0.10),
                Map.of(), Map.of("purified_water", 1000.0), 10, 1, 2000.0, false));
        recipes.put("ice_water_treatment", new Recipe("ice_water_treatment",
                List.of("industrial_production"), Map.of("supply_chain_automation", 0.10),
                Map.of("water_ice", 1000.0), Map.of("purified_water", 950.0), 10, 1, 2000.0, false));
        recipes.put("uranium_refining", new Recipe("uranium_refining",
                List.of("nuclear_fission"), Map.of("supply_chain_automation", 0.10),
                Map.of("uranium_ore", 1000.0), Map.of("refined_uranium", 100.0),
                10, 1, 6000.0, false));
        recipes.put("thorium_refining", new Recipe("thorium_refining",
                List.of("nuclear_fission"), Map.of("supply_chain_automation", 0.10),
                Map.of("thorium_ore", 1000.0), Map.of("refined_thorium", 80.0),
                10, 1, 6000.0, false));
        recipes.put("water_isotope_separation", new Recipe("water_isotope_separation",
                List.of("nuclear_fusion"), Map.of("supply_chain_automation", 0.10),
                Map.of("purified_water", 1000.0),
                Map.of("oxygen_gas", 888.9, "hydrogen_gas", 110.95,
                        "deuterium_gas", 0.15), 10, 1, 8000.0, false));
        recipes.put("industrial_soil_cultivation", new Recipe("industrial_soil_cultivation",
                List.of("industrial_production"), Map.of("supply_chain_automation", 0.10),
                Map.of("nitrates", 10.0, "phosphates", 5.0, "potash", 5.0, "purified_water", 50.0),
                Map.of("food_matrix", 120.0, "agricultural_biomass", 4.0), 1, 1, 2500.0, false));
        recipes.put("industrial_biomass_cultivation", new Recipe("industrial_biomass_cultivation",
                List.of("industrial_production"), Map.of("supply_chain_automation", 0.10),
                Map.of("carbon", 400.0, "purified_water", 550.0,
                        "nitrates", 20.0, "phosphates", 15.0, "potash", 15.0),
                Map.of("agricultural_biomass", 1000.0), 10, 1, 3000.0, false));
        recipes.put("biomass_processing", new Recipe("biomass_processing",
                List.of("industrial_production"), Map.of("supply_chain_automation", 0.10),
                Map.of("agricultural_biomass", 1000.0, "purified_water", 100.0),
                Map.of("bio_polymers", 1000.0), 10, 1, 4000.0, false));
        recipes.put("water_electrolysis", new Recipe("water_electrolysis",
                List.of("industrial_production", "electricity"), Map.of("supply_chain_automation", 0.10),
                Map.of("purified_water", 1000.0),
                Map.of("oxygen_gas", 888.9, "hydrogen_gas", 111.1), 10, 1, 5000.0, false));
        recipes.put("rp1_refining", new Recipe("rp1_refining", List.of("rocketry"), Map.of(),
                Map.of("hydrocarbons", 1000.0), Map.of("rp1_kerosene", 850.0),
                10, 1, 1500.0, false));
        recipes.put("oxygen_liquefaction", new Recipe("oxygen_liquefaction", List.of("rocketry"),
                Map.of(), Map.of("oxygen_gas", 1000.0), Map.of("liquid_oxygen", 1000.0),
                10, 1, 3000.0, false));
        recipes.put("methane_liquefaction", new Recipe("methane_liquefaction",
                List.of("methalox_propulsion"), Map.of(), Map.of("hydrocarbons", 1000.0),
                Map.of("liquid_methane", 700.0), 10, 1, 3500.0, false));
        recipes.put("hydrogen_liquefaction", new Recipe("hydrogen_liquefaction",
                List.of("hydrolox_propulsion"), Map.of(), Map.of("hydrogen_gas", 1000.0),
                Map.of("liquid_hydrogen", 1000.0), 10, 1, 6000.0, false));
        for (RefinementRecipe refinement : RefinementProcessor.STANDARD_RECIPES) {
            int workersPerBatch = switch (refinement.id()) {
                case "consumer_goods_mfg", "luxury_goods_mfg", "alloy_steel", "aluminum_refining",
                        "copper_refining", "silicon_refining", "rare_earth_refining" -> 10;
                default -> 100;
            };
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
