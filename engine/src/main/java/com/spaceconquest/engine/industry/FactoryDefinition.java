package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.industry.refinement.RefinementRecipe;
import java.util.List;
import java.util.Map;

/** Shared infrastructure schema for construction, scaling and placement; also used by farming definitions. */
public record FactoryDefinition(double constructionWorkPerTier, double expansionWorkPerTier, double investmentCredits,
                                double constructionWorkPerDay, Map<String, Double> constructionMaterialsKg,
                                double tierCapacityMultiplier, double expansionCapacityFraction,
                                int complexityOffset, int complexityPerTier, boolean shedDuringBrownout,
                                String environment, double minimumGravityMs2, double maximumGravityMs2,
                                List<String> byproductMaterialIds, List<String> marketOrientations) {
    public FactoryDefinition {
        constructionMaterialsKg = Map.copyOf(constructionMaterialsKg);
        byproductMaterialIds = List.copyOf(byproductMaterialIds);
        marketOrientations = List.copyOf(marketOrientations);
        for (double value : new double[]{constructionWorkPerTier, expansionWorkPerTier, investmentCredits, constructionWorkPerDay,
                tierCapacityMultiplier, maximumGravityMs2})
            if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException("Invalid factory capacity");
        if (!Double.isFinite(expansionCapacityFraction) || expansionCapacityFraction < 0 || expansionCapacityFraction > 1
                || complexityPerTier <= 0 || !Double.isFinite(minimumGravityMs2) || minimumGravityMs2 < 0
                || maximumGravityMs2 < minimumGravityMs2 || !List.of("UNIVERSAL", "GRAVITY_LOCKED", "MICROGRAVITY_NATIVE").contains(environment)
                || constructionMaterialsKg.isEmpty() || constructionMaterialsKg.values().stream()
                .anyMatch(v -> !Double.isFinite(v) || v <= 0)) throw new IllegalArgumentException("Invalid factory definition");
    }

    public static FactoryDefinition find(String applicationId) {
        var recipe = IndustryRecipeCatalog.find(applicationId);
        return recipe == null ? null : recipe.infrastructure();
    }

    public double capacity(int tier, boolean expanding) {
        return Math.pow(tierCapacityMultiplier, Math.max(0, tier - 1)) * (expanding ? expansionCapacityFraction : 1);
    }

    public boolean permitsGravity(double accelerationMs2) {
        return Double.isFinite(accelerationMs2) && accelerationMs2 >= 0
                && (!"GRAVITY_LOCKED".equals(environment) || accelerationMs2 >= minimumGravityMs2)
                && (!"MICROGRAVITY_NATIVE".equals(environment) || accelerationMs2 <= maximumGravityMs2);
    }

    /** Unknown locations retain the legacy permissive placement policy. */
    public boolean permitsSite(com.spaceconquest.engine.GameState state, String bodyId) {
        for (var system : state.solarSystems()) for (var planet : system.planets()) {
            if (planet.id().equals(bodyId)) return permitsGravity(planet.gravity());
            for (var moon : planet.moons()) if (moon.id().equals(bodyId)) return permitsGravity(moon.gravity());
        }
        if (state.orbitalStations().stream().anyMatch(station -> station.id().equals(bodyId))) return permitsGravity(0);
        return true;
    }

    /** Compatibility projection for older recipe views and standalone refinement callers. */
    public RefinementRecipe refinement(String name, IndustryRecipeCatalog.Recipe recipe) {
        var work = recipe.work() != null ? recipe.work() : recipe.refineryProcess().profiles().getFirst().work();
        var products = new java.util.HashMap<>(recipe.outputsKg());
        var byproducts = new java.util.HashMap<String, Double>();
        for (String id : byproductMaterialIds) byproducts.put(id, products.remove(id));
        return new RefinementRecipe(recipe.applicationId(), name, work.processType(), environment,
                minimumGravityMs2, maximumGravityMs2, recipe.inputsKg(), work.energyKwhPerBatch() / 24,
                products, byproducts, recipe.minTier(), work.equipment().operatorProfession());
    }
}
