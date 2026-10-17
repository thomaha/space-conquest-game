package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.DataModelLoader;
import java.util.*;

/** Research-defined equipment, field supplies and work required by a surface survey team. */
public record SurveyCapability(Map<String, Double> equipmentMaterialsKg, Map<String, Double> suppliesKgPerWorkUnit,
        ProductionEquipment equipment, double installationWork, double workPerPass, double discoveryProbability,
        Mode mode, double energyKwhPerWorkUnit) {
    public enum Mode { SURFACE, ORBITAL }
    public SurveyCapability {
        equipmentMaterialsKg = Map.copyOf(equipmentMaterialsKg);
        suppliesKgPerWorkUnit = Map.copyOf(suppliesKgPerWorkUnit);
        if (equipment == null || !Double.isFinite(installationWork) || installationWork < 0
                || !Double.isFinite(workPerPass) || workPerPass <= 0 || !Double.isFinite(discoveryProbability)
                || discoveryProbability <= 0 || discoveryProbability > 1 || equipmentMaterialsKg.isEmpty() || mode == null
                || !Double.isFinite(energyKwhPerWorkUnit) || energyKwhPerWorkUnit < 0
                || mode == Mode.SURFACE && suppliesKgPerWorkUnit.isEmpty()
                || mode == Mode.ORBITAL && (energyKwhPerWorkUnit <= 0 || equipment.autonomousFraction() != 1
                || !suppliesKgPerWorkUnit.isEmpty())) throw new IllegalArgumentException("Invalid survey capability");
        for (var bill : List.of(equipmentMaterialsKg, suppliesKgPerWorkUnit)) for (var entry : bill.entrySet())
            if (entry.getKey().isBlank() || !Double.isFinite(entry.getValue()) || entry.getValue() <= 0)
                throw new IllegalArgumentException("Invalid survey material bill");
    }
    public record Definition(String applicationId, String technologyId, SurveyCapability capability) {}
    private static final class Loaded {
        static final List<Definition> ALL = load();
        private static List<Definition> load() {
            try {
                return DataModelLoader.loadTechnologies().stream().flatMap(t -> t.applications().stream()
                        .filter(a -> a.surveyCapability() != null).map(a -> new Definition(a.id(), t.id(), a.surveyCapability())))
                        .sorted(Comparator.comparing(Definition::applicationId)).toList();
            } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        }
    }
    public static List<Definition> all() { return Loaded.ALL; }
}
