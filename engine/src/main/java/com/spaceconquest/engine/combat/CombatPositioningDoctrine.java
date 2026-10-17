package com.spaceconquest.engine.combat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;

/** Bounded movement priorities independent of captured weapon capabilities. */
public record CombatPositioningDoctrine(List<Double> rangeFractions, double neutralizationWeight,
        double progressWeight, double incomingDamageWeight, double distanceWeight, double arrivalDelayDiscount,
        double travelDayDiscount, double minimumImprovement, int maxSalvoImpacts) {
    public CombatPositioningDoctrine {
        rangeFractions = List.copyOf(rangeFractions);
        if (rangeFractions.isEmpty() || rangeFractions.size() > 9 || rangeFractions.stream()
                .anyMatch(value -> !Double.isFinite(value) || value <= 0 || value >= 1))
            throw new IllegalArgumentException("Invalid positioning range fractions");
        for (double value : new double[]{neutralizationWeight, progressWeight, incomingDamageWeight,
                distanceWeight, arrivalDelayDiscount, travelDayDiscount, minimumImprovement})
            if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid positioning weight");
        if (neutralizationWeight + progressWeight <= 0 || maxSalvoImpacts < 1 || maxSalvoImpacts > 256)
            throw new IllegalArgumentException("Invalid positioning forecast bound");
    }
    private static final CombatPositioningDoctrine DEFAULT = load();
    public static CombatPositioningDoctrine defaults() { return DEFAULT; }
    private static CombatPositioningDoctrine load() {
        try (var source = CombatPositioningDoctrine.class.getResourceAsStream("/combat_positioning_doctrine.json")) {
            if (source == null) throw new IllegalStateException("Missing combat_positioning_doctrine.json");
            return new ObjectMapper().readValue(source, CombatPositioningDoctrine.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Invalid combat positioning doctrine", exception);
        }
    }
}
