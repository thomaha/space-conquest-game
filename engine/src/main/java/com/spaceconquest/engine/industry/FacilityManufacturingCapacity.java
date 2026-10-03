package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.technology.ApplicationProduction;

import java.io.IOException;
import java.io.UncheckedIOException;

/** Provisional specialized facility capacity relative to its application's baseline complexity. */
public final class FacilityManufacturingCapacity {
    public record Limit(String applicationId, int complexityOffset, int complexityPerTier) {
        public Limit {
            if (applicationId == null || complexityPerTier <= 0)
                throw new IllegalArgumentException("Invalid facility manufacturing limit");
        }
    }

    private FacilityManufacturingCapacity() {}

    private static Limit limit(String applicationId) {
        try {
            var rules = DataModelLoader.loadFacilityManufacturingLimits();
            return rules.stream().filter(rule -> applicationId.equals(rule.applicationId())).findFirst()
                    .orElseGet(() -> rules.stream().filter(rule -> "*".equals(rule.applicationId()))
                            .findFirst().orElseThrow(() -> new IllegalStateException("Missing default facility capacity")));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load facility manufacturing limits", e);
        }
    }

    private static int baseline(String applicationId) {
        try {
            return DataModelLoader.loadTechnologies().stream().flatMap(technology -> technology.applications().stream())
                    .filter(application -> applicationId.equals(application.id())).mapToInt(application -> application.complexity())
                    .findFirst().orElse(1);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load application complexities", e);
        }
    }

    public static int available(String applicationId, int tier) {
        if (tier <= 0) return 0;
        Limit rule = limit(applicationId);
        return Math.max(1, baseline(applicationId) + rule.complexityOffset()
                + (tier - 1) * rule.complexityPerTier());
    }

    public static int required(GameState state, String ownerId, String applicationId) {
        return Math.max(1, baseline(applicationId)
                + ApplicationProduction.modifiersForOwner(state, ownerId, applicationId).complexityShift());
    }

    public static boolean canOperate(GameState state, IndustrialFacility facility) {
        return available(facility.applicationId(), facility.tier())
                >= required(state, facility.ownerEntityId(), facility.applicationId());
    }

    public static int minimumTier(GameState state, String ownerId, String applicationId) {
        Limit rule = limit(applicationId);
        int extraComplexity = Math.max(0, required(state, ownerId, applicationId)
                - baseline(applicationId) - rule.complexityOffset());
        return 1 + Math.ceilDiv(extraComplexity, rule.complexityPerTier());
    }
}
