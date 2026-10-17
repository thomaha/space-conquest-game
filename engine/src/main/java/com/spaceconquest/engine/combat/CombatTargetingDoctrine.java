package com.spaceconquest.engine.combat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

/** Data-defined tactical priorities; weapon interactions remain captured technological capabilities. */
public record CombatTargetingDoctrine(double neutralizationWeight, double immediateThreatReductionWeight,
                                      double progressWeight, double focusMultiplier, int maxProjectionHits,
                                      double committedFireWeight, double arrivalDelayDiscount, double guidanceThreatWeight,
                                      int maxPendingImpacts) {
    public CombatTargetingDoctrine {
        for (double value : new double[]{neutralizationWeight, immediateThreatReductionWeight, progressWeight, focusMultiplier,
                committedFireWeight, arrivalDelayDiscount, guidanceThreatWeight})
            if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid combat targeting weight");
        if (neutralizationWeight + immediateThreatReductionWeight <= 0 || focusMultiplier < 1
                || maxProjectionHits < 1 || maxProjectionHits > 32 || committedFireWeight > 1
                || maxPendingImpacts < 1 || maxPendingImpacts > 256)
            throw new IllegalArgumentException("Invalid combat targeting doctrine or projection bound");
    }
    public CombatTargetingDoctrine(double neutralizationWeight, double immediateThreatReductionWeight,
                                   double progressWeight, double focusMultiplier, int maxProjectionHits) {
        this(neutralizationWeight, immediateThreatReductionWeight, progressWeight, focusMultiplier, maxProjectionHits,
                defaults().committedFireWeight(), defaults().arrivalDelayDiscount(), defaults().guidanceThreatWeight(), defaults().maxPendingImpacts());
    }
    private static final CombatTargetingDoctrine DEFAULT = load();
    public static CombatTargetingDoctrine defaults() { return DEFAULT; }
    private static CombatTargetingDoctrine load() {
        try (var source = CombatTargetingDoctrine.class.getResourceAsStream("/combat_targeting_doctrine.json")) {
            if (source == null) throw new IllegalStateException("Missing combat_targeting_doctrine.json");
            return new ObjectMapper().readValue(source, CombatTargetingDoctrine.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Invalid combat targeting doctrine", exception);
        }
    }
}
