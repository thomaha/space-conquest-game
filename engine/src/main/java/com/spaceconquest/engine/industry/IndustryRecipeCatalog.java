package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.technology.ApplicationProduction;

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
                         boolean extractsDeposit, RefineryProcess refineryProcess,
                         ProductionWork.Definition work, FactoryDefinition factory, List<ProcessInput> inputSources,
                         FarmingDefinition farming) {
        public Recipe(String applicationId, List<String> requiredTechnologies, Map<String, Double> improvements,
                      Map<String, Double> inputsKg, Map<String, Double> outputsKg, int workersPerBatch,
                      int minTier, double powerDrawKw, boolean extractsDeposit, RefineryProcess refineryProcess,
                      ProductionWork.Definition work, FactoryDefinition factory, List<ProcessInput> inputSources) {
            this(applicationId, requiredTechnologies, improvements, inputsKg, outputsKg, workersPerBatch,
                    minTier, powerDrawKw, extractsDeposit, refineryProcess, work, factory, inputSources, null);
        }
        public Recipe(String applicationId, List<String> requiredTechnologies, Map<String, Double> improvements,
                      Map<String, Double> inputsKg, Map<String, Double> outputsKg, int workersPerBatch,
                      int minTier, double powerDrawKw, boolean extractsDeposit, RefineryProcess refineryProcess,
                      ProductionWork.Definition work, FactoryDefinition factory) {
            this(applicationId, requiredTechnologies, improvements, inputsKg, outputsKg, workersPerBatch,
                    minTier, powerDrawKw, extractsDeposit, refineryProcess, work, factory, null);
        }
        public Recipe(String applicationId, List<String> requiredTechnologies, Map<String, Double> improvements,
                      Map<String, Double> inputsKg, Map<String, Double> outputsKg, int workersPerBatch,
                      int minTier, double powerDrawKw, boolean extractsDeposit, RefineryProcess refineryProcess) {
            this(applicationId, requiredTechnologies, improvements, inputsKg, outputsKg, workersPerBatch,
                    minTier, powerDrawKw, extractsDeposit, refineryProcess, null, null);
        }
        public Recipe(String applicationId, List<String> requiredTechnologies, Map<String, Double> improvements,
                      Map<String, Double> inputsKg, Map<String, Double> outputsKg, int workersPerBatch,
                      int minTier, double powerDrawKw, boolean extractsDeposit) {
            this(applicationId, requiredTechnologies, improvements, inputsKg, outputsKg, workersPerBatch,
                    minTier, powerDrawKw, extractsDeposit, null);
        }
        public Recipe {
            requiredTechnologies = List.copyOf(requiredTechnologies);
            improvements = Map.copyOf(improvements);
            inputsKg = Map.copyOf(inputsKg);
            outputsKg = Map.copyOf(outputsKg);
            if (farming != null && (factory != null || refineryProcess != null || work == null))
                throw new IllegalArgumentException("Cultivation requires farm infrastructure and work rather than a factory process");
            if (inputSources == null) {
                var sources = new java.util.ArrayList<ProcessInput>();
                inputsKg.forEach((id, kg) -> sources.add(new ProcessInput(ProcessInput.Source.MARKET, id, kg)));
                // Remaining legacy extraction definitions use their single output as the extracted resource.
                if (extractsDeposit) outputsKg.forEach((id, kg) -> sources.add(new ProcessInput(ProcessInput.Source.DEPOSIT, id, kg)));
                inputSources = sources;
            }
            inputSources = List.copyOf(inputSources);
            if (!ProcessInput.purchased(inputSources).equals(inputsKg)) throw new IllegalArgumentException("Market inputs disagree with source definitions");
            extractsDeposit = inputSources.stream().anyMatch(i -> i.source() == ProcessInput.Source.DEPOSIT);
        }

        /** Construction and equipment constraints shared by farms and material processors. */
        public FactoryDefinition infrastructure() { return farming == null ? factory : farming.infrastructure(); }

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
                && empire.unlockedTechIds().containsAll(recipe.requiredTechnologies())
                && com.spaceconquest.engine.technology.ApplicationProduction.isAvailable(empire, recipe.applicationId());
    }

    public static Collection<Recipe> all() {
        return RECIPES.values();
    }

    public static String applicationName(String id) {
        try {
            return DataModelLoader.loadTechnologies().stream().flatMap(t -> t.applications().stream())
                    .filter(a -> a.id().equals(ApplicationProduction.capabilityApplication(id))).map(a -> a.name()).findFirst().orElse(id);
        } catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
    }
    public static Recipe forFacility(com.spaceconquest.engine.GameState state, IndustrialFacility facility) {
        var plan = RefineryProcess.plan(state, facility, RefineryProcess.inventory(state, facility.id()));
        return plan == null ? find(facility.applicationId()) : plan.recipe();
    }

    private static Map<String, Recipe> createRecipes() {
        Map<String, Recipe> recipes = new HashMap<>();
        addApplicationRecipes(recipes);
        return Map.copyOf(recipes);
    }

    private static void addApplicationRecipes(Map<String, Recipe> recipes) {
        try {
            for (var technology : DataModelLoader.loadTechnologies()) for (var application : technology.applications()) {
                var recipe = application.productionRecipe();
                if (recipe == null) continue;
                if (!application.id().equals(recipe.applicationId()) || recipe.workersPerBatch() <= 0
                        || recipe.minTier() <= 0 || !Double.isFinite(recipe.powerDrawKw()) || recipe.powerDrawKw() < 0
                        || recipe.outputsKg().isEmpty()) throw new IllegalArgumentException("Invalid application recipe: " + application.id());
                if (recipe.refineryProcess() != null && !recipe.refineryProcess().profiles().getFirst().work().applicationId().equals(application.id()))
                    throw new IllegalArgumentException("Refinery work must belong to its application: " + application.id());
                if (recipe.work() != null && !recipe.work().applicationId().equals(application.id()))
                    throw new IllegalArgumentException("Process work must belong to its application: " + application.id());
                if (recipe.farming() != null && recipe.inputSources().stream().anyMatch(i -> i.source() == ProcessInput.Source.DEPOSIT))
                    throw new IllegalArgumentException("Cultivation cannot extract geological deposits: " + application.id());
                if (recipe.farming() != null && recipe.farming().controlled() != null) recipe.farming().controlled().validateRecipe(recipe);
                if (recipe.infrastructure() != null && (recipe.work() == null && recipe.refineryProcess() == null
                        || !recipe.outputsKg().keySet().containsAll(recipe.infrastructure().byproductMaterialIds())))
                    throw new IllegalArgumentException("Incomplete factory definition: " + application.id());
                for (var amounts : List.of(recipe.inputsKg(), recipe.outputsKg())) for (double amount : amounts.values())
                    if (!Double.isFinite(amount) || amount <= 0) throw new IllegalArgumentException("Invalid recipe mass: " + application.id());
                if (recipes.putIfAbsent(application.id(), recipe) != null)
                    throw new IllegalArgumentException("Duplicate application recipe: " + application.id());
            }
        } catch (java.io.IOException e) { throw new IllegalStateException("Cannot load application production recipes", e); }
    }

}
