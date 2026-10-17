package com.spaceconquest.engine.industry.refinement;

import com.spaceconquest.engine.Population;
import com.spaceconquest.engine.Race;

import java.util.List;
import java.util.Map;

/**
 * Executes chemical/metallurgical multi-stage refinement recipes and standard of living satisfaction.
 */
public class RefinementProcessor {

    public static final List<RefinementRecipe> STANDARD_RECIPES = applicationRecipes();

    private static List<RefinementRecipe> applicationRecipes() {
        var recipes = new java.util.ArrayList<RefinementRecipe>();
        try {
            for (var technology : com.spaceconquest.engine.DataModelLoader.loadTechnologies()) {
                for (var application : technology.applications()) {
                    var recipe = application.productionRecipe();
                    if (recipe != null && recipe.factory() != null && recipe.inputSources().stream()
                            .allMatch(input -> input.source() == com.spaceconquest.engine.industry.ProcessInput.Source.MARKET))
                        recipes.add(recipe.factory().refinement(application.name(), recipe));
                }
            }
        } catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
        return List.copyOf(recipes);
    }

    public record RefinementExecutionResult(
            boolean isSuccessful,
            Map<String, Double> consumedInputsKg,
            Map<String, Double> producedOutputsKg,
            Map<String, Double> producedByproductsKg,
            double powerConsumedKw,
            String failureReason
    ) {}

    public List<RefinementRecipe> getStandardRecipes() {
        return STANDARD_RECIPES;
    }

    public record StandardOfLivingResult(
            double satisfactionIndex,
            double happinessModifier,
            double crimeModifier,
            double immigrationModifier
    ) {}

    /**
     * Attempts to execute a refinement recipe against a local material cache and planetary gravity.
     */
    public RefinementExecutionResult processRecipeExecution(
            RefinementRecipe recipe,
            Map<String, Double> availableMaterialsKg,
            double availablePowerKw,
            double localGravity,
            int facilityTier
    ) {
        if (recipe == null) {
            return new RefinementExecutionResult(false, Map.of(), Map.of(), Map.of(), 0.0, "Recipe is null");
        }

        if (facilityTier < recipe.minFacilityTier()) {
            return new RefinementExecutionResult(false, Map.of(), Map.of(), Map.of(), 0.0,
                    "Facility tier " + facilityTier + " below required tier " + recipe.minFacilityTier());
        }

        // Gravity check in SI units (m/s²)
        if (RefinementRecipe.ENV_GRAVITY_LOCKED.equalsIgnoreCase(recipe.operationalEnvironment())
                && localGravity < recipe.minGravity()) {
            return new RefinementExecutionResult(false, Map.of(), Map.of(), Map.of(), 0.0,
                    "Local gravity " + localGravity + " m/s² below required minimum " + recipe.minGravity() + " m/s²");
        }
        if (RefinementRecipe.ENV_MICROGRAVITY_NATIVE.equalsIgnoreCase(recipe.operationalEnvironment())
                && localGravity > recipe.maxGravity()) {
            return new RefinementExecutionResult(false, Map.of(), Map.of(), Map.of(), 0.0,
                    "Local gravity " + localGravity + " m/s² exceeds microgravity ceiling " + recipe.maxGravity() + " m/s²");
        }

        if (availablePowerKw < recipe.powerDrawKw()) {
            return new RefinementExecutionResult(false, Map.of(), Map.of(), Map.of(), 0.0, "Insufficient power grid capacity");
        }

        // Verify inputs
        Map<String, Double> inputs = availableMaterialsKg != null ? availableMaterialsKg : Map.of();
        for (Map.Entry<String, Double> req : recipe.inputMaterialsKg().entrySet()) {
            if (inputs.getOrDefault(req.getKey(), 0.0) < req.getValue()) {
                return new RefinementExecutionResult(false, Map.of(), Map.of(), Map.of(), 0.0,
                        "Missing required input: " + req.getKey());
            }
        }

        return new RefinementExecutionResult(
                true,
                recipe.inputMaterialsKg(),
                recipe.outputMaterialsKg(),
                recipe.byproductMaterialsKg(),
                recipe.powerDrawKw(),
                ""
        );
    }

    /**
     * Evaluates colony standard of living satisfaction from consumer goods distribution.
     */
    public StandardOfLivingResult evaluateStandardOfLiving(
            Population population,
            Race race,
            double suppliedConsumerGoodsKg
    ) {
        if (population == null || race == null) {
            return new StandardOfLivingResult(1.0, 0.0, 0.0, 1.0);
        }

        if ("Hive mind".equalsIgnoreCase(race.societyStructure())
                || "Hive Mind".equalsIgnoreCase(race.societyStructure())) {
            // Hive minds have zero consumer goods demand
            return new StandardOfLivingResult(1.0, 0.0, 0.0, 1.0);
        }

        long totalPop = population.ageGroups().values().stream().mapToLong(Long::longValue).sum();
        if (totalPop <= 0) {
            return new StandardOfLivingResult(1.0, 0.0, 0.0, 1.0);
        }

        double demandKg = (totalPop / 1000.0) * 50.0; // 50 kg consumer goods per 1,000 citizens
        double satisfaction = Math.clamp(suppliedConsumerGoodsKg / Math.max(1.0, demandKg), 0.0, 2.0);

        double happinessMod;
        double crimeMod;
        double immigrationMod;

        if (satisfaction >= 1.0) {
            happinessMod = 0.15 * Math.min(1.5, satisfaction);
            crimeMod = -0.10 * Math.min(1.5, satisfaction);
            immigrationMod = 1.25;
        } else {
            double deficit = 1.0 - satisfaction;
            happinessMod = -0.20 * deficit;
            crimeMod = 0.15 * deficit;
            immigrationMod = 0.75;
        }

        return new StandardOfLivingResult(satisfaction, happinessMod, crimeMod, immigrationMod);
    }
}
