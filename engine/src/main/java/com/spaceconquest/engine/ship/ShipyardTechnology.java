package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.macrostructure.StationModule;
import java.util.*;

/** Application-defined yard work, construction and staffing capabilities. */
public record ShipyardTechnology(Map<String, Yard> yards, double workMultiplier, double workerMultiplier) {
    public record Yard(double workPerDay, int workers, String professionId, int slots,
                       double dryMassKg, double powerDrawKw, double constructionWork,
                       Map<String, Double> materialsKg, double tierMultiplier, double expansionMultiplier,
                       int baseComplexity, int complexityPerTier, boolean surface, boolean buildsShips) {
        public Yard(double workPerDay, int workers, String professionId, int slots,
                    double dryMassKg, double powerDrawKw, double constructionWork,
                    Map<String, Double> materialsKg, double tierMultiplier, double expansionMultiplier,
                    int baseComplexity, int complexityPerTier) {
            this(workPerDay, workers, professionId, slots, dryMassKg, powerDrawKw, constructionWork,
                    materialsKg, tierMultiplier, expansionMultiplier, baseComplexity, complexityPerTier, false, false);
        }
        public Yard {
            materialsKg = materialsKg == null ? Map.of() : Map.copyOf(materialsKg);
            for (double value : new double[]{workPerDay, dryMassKg, constructionWork, tierMultiplier, expansionMultiplier})
                if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException("Invalid yard capability");
            if (baseComplexity < 0 || complexityPerTier < 0 || workers < 1 || slots < 1 || professionId == null || professionId.isBlank()
                    || !Double.isFinite(powerDrawKw) || powerDrawKw < 0 || materialsKg.isEmpty()
                    || materialsKg.values().stream().anyMatch(kg -> kg == null || !Double.isFinite(kg) || kg <= 0))
                throw new IllegalArgumentException("Invalid yard construction data");
        }
        public Yard(double workPerDay, int workers, String professionId, int slots, double dryMassKg,
                    double powerDrawKw, double constructionWork, Map<String, Double> materialsKg,
                    double tierMultiplier, double expansionMultiplier) {
            this(workPerDay, workers, professionId, slots, dryMassKg, powerDrawKw, constructionWork,
                    materialsKg, tierMultiplier, expansionMultiplier, 0, 0);
        }
        public StationModule module(String type) {
            return new StationModule("mod_" + UUID.randomUUID(), type, type, slots, dryMassKg,
                    powerDrawKw, 0, Map.of(), professionId, workers, true);
        }
    }
    public ShipyardTechnology {
        yards = yards == null ? Map.of() : Map.copyOf(yards);
        if (!Double.isFinite(workMultiplier) || workMultiplier <= 0
                || !Double.isFinite(workerMultiplier) || workerMultiplier <= 0)
            throw new IllegalArgumentException("Invalid yard technology modifier");
    }
    private record Entry(String application, ShipyardTechnology capabilities) {}
    private static final class Loaded {
        static final List<Entry> ENTRIES = load();
        static List<Entry> load() {
            var result = new ArrayList<Entry>();
            try {
                for (var tech : DataModelLoader.loadTechnologies()) for (var app : tech.applications())
                    if (app.shipyardTechnology() != null) result.add(new Entry(app.id(), app.shipyardTechnology()));
            } catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
            var types = new HashSet<String>();
            for (var entry : result) for (var type : entry.capabilities().yards().keySet())
                if (!types.add(type)) throw new IllegalArgumentException("Duplicate yard capability: " + type);
            return List.copyOf(result);
        }
    }
    public static Yard yard(String type) {
        return Loaded.ENTRIES.stream().map(entry -> entry.capabilities().yards().get(type)).filter(Objects::nonNull).findFirst().orElse(null);
    }
    public static boolean isSurface(String type) {
        var capability = yard(type);
        return capability != null && capability.surface();
    }
    public static String application(String type) {
        return Loaded.ENTRIES.stream().filter(entry -> entry.capabilities().yards().containsKey(type)).map(Entry::application).findFirst().orElse(null);
    }
    public static Map<String, Yard> allYards() {
        var result = new TreeMap<String, Yard>();
        Loaded.ENTRIES.forEach(entry -> result.putAll(entry.capabilities().yards()));
        return Collections.unmodifiableMap(result);
    }
    public static double workMultiplier(List<String> researched) {
        return Loaded.ENTRIES.stream().filter(entry -> researched.contains(entry.application()))
                .mapToDouble(entry -> entry.capabilities().workMultiplier()).reduce(1, (a, b) -> a * b);
    }
    public static double workerMultiplier(List<String> researched) {
        return Loaded.ENTRIES.stream().filter(entry -> researched.contains(entry.application()))
                .mapToDouble(entry -> entry.capabilities().workerMultiplier()).reduce(1, (a, b) -> a * b);
    }
}
