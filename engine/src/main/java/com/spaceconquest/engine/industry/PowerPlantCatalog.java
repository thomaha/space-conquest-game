package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.DataModelLoader;
import java.util.HashMap;
import java.util.Map;

/** Daily output and fuel requirements for grid-connected generation facilities. */
public final class PowerPlantCatalog {
    public enum Environment { ANY, SOLAR, ATMOSPHERE, LIQUID_WATER }
    public record Plant(double kwPerTier, int requiredWorkers, String requiredTechnology,
                        String fuelMaterialId, double fuelKgPerTierDay, Environment environment,
                        double atmosphericTransmission, String workerProfessionId,
                        Map<String, Double> constructionMaterialsKg, double constructionWorkHoursPerTier,
                        double operatingCapitalCredits, int shortageObservationTurns, double expansionWorkHoursPerTier) {
        public Plant {
            constructionMaterialsKg = constructionMaterialsKg == null ? Map.of() : Map.copyOf(constructionMaterialsKg);
            if (!Double.isFinite(kwPerTier) || kwPerTier <= 0 || requiredWorkers <= 0 || requiredTechnology == null
                    || !Double.isFinite(fuelKgPerTierDay) || fuelKgPerTierDay < 0
                    || (fuelMaterialId == null ? fuelKgPerTierDay != 0 : fuelKgPerTierDay <= 0) || environment == null
                    || !Double.isFinite(atmosphericTransmission) || atmosphericTransmission < 0 || atmosphericTransmission > 1
                    || workerProfessionId == null || constructionMaterialsKg.isEmpty()
                    || constructionMaterialsKg.values().stream().anyMatch(kg -> kg == null || !Double.isFinite(kg) || kg <= 0)
                    || !Double.isFinite(constructionWorkHoursPerTier) || constructionWorkHoursPerTier <= 0
                    || !Double.isFinite(expansionWorkHoursPerTier) || expansionWorkHoursPerTier <= 0
                    || !Double.isFinite(operatingCapitalCredits) || operatingCapitalCredits <= 0 || shortageObservationTurns < 1)
                throw new IllegalArgumentException("Invalid power plant application data");
        }
    }
    private static final Map<String, Plant> PLANTS = load();
    private static Map<String, Plant> load() {
        var plants = new HashMap<String, Plant>();
        try {
            for (var technology : DataModelLoader.loadTechnologies()) for (var application : technology.applications())
                if (application.powerPlant() != null && plants.put(application.id(), application.powerPlant()) != null)
                    throw new IllegalArgumentException("Duplicate power plant application: " + application.id());
        } catch (java.io.IOException exception) { throw new IllegalStateException("Cannot load power plant applications", exception); }
        return Map.copyOf(plants);
    }

    private PowerPlantCatalog() {}

    public static Plant find(String applicationId) {
        return applicationId == null ? null : PLANTS.get(applicationId);
    }

    public static Map<String, Plant> all() {
        return PLANTS;
    }
}
