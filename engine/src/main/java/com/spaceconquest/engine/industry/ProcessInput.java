package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.GameState;
import java.util.*;

/** A material quantity per process batch and the physical source that supplies it. */
public record ProcessInput(Source source, String materialId, double kgPerBatch) {
    public enum Source { MARKET, DEPOSIT, SURFACE_LIQUID_WATER, AMBIENT_ATMOSPHERE }
    public ProcessInput {
        if (source == null || materialId == null || materialId.isBlank() || !Double.isFinite(kgPerBatch) || kgPerBatch <= 0)
            throw new IllegalArgumentException("Invalid process input");
    }

    public static Map<String, Double> purchased(List<ProcessInput> inputs) {
        var result = new TreeMap<String, Double>();
        inputs.stream().filter(i -> i.source == Source.MARKET).forEach(i -> result.merge(i.materialId, i.kgPerBatch, Double::sum));
        return Map.copyOf(result);
    }

    public static boolean environmentAvailable(GameState state, String body, List<ProcessInput> inputs) {
        if (inputs.stream().noneMatch(i -> i.source == Source.SURFACE_LIQUID_WATER || i.source == Source.AMBIENT_ATMOSPHERE)) return true;
        for (var system : state.solarSystems()) for (var planet : system.planets()) {
            if (planet.id().equals(body)) return permits(inputs, planet.hasLiquidWater(), planet.atmosphere());
            for (var moon : planet.moons()) if (moon.id().equals(body)) return permits(inputs, moon.hasLiquidWater(), moon.atmosphere());
        }
        return false;
    }
    private static boolean permits(List<ProcessInput> inputs, boolean water, String atmosphere) {
        return (water || inputs.stream().noneMatch(i -> i.source == Source.SURFACE_LIQUID_WATER))
                && (inputs.stream().noneMatch(i -> i.source == Source.AMBIENT_ATMOSPHERE)
                || atmosphere != null && !atmosphere.isBlank() && !Set.of("none", "vacuum").contains(atmosphere.toLowerCase(Locale.ROOT)));
    }

    /** Bound only non-market sources; live settlement separately checks the current hub ledger. */
    public static double maximumBatches(GameState state, String body, List<ProcessInput> inputs, Collection<GeologicalDeposit> deposits) {
        if (!environmentAvailable(state, body, inputs)) return 0;
        double maximum = Double.MAX_VALUE;
        for (var need : depositNeeds(inputs).entrySet()) {
            double available = deposits.stream().filter(d -> eligible(d, body, need.getKey()))
                    .mapToDouble(d -> Math.max(0, d.remainingVolumeKg())).sum();
            maximum = Math.min(maximum, available / need.getValue());
        }
        return maximum;
    }

    public static void consumeDeposits(Map<String, GeologicalDeposit> deposits, String body, List<ProcessInput> inputs, double batches) {
        if (!Double.isFinite(batches) || batches < 0) throw new IllegalArgumentException("Invalid process quantity");
        var needs = depositNeeds(inputs);
        for (var need : needs.entrySet()) {
            double required = need.getValue() * batches;
            double available = deposits.values().stream().filter(d -> eligible(d, body, need.getKey()))
                    .mapToDouble(d -> Math.max(0, d.remainingVolumeKg())).sum();
            if (!Double.isFinite(required) || available + 1e-6 < required)
                throw new IllegalStateException("Process exceeded its quoted deposit input");
        }
        for (var need : needs.entrySet()) {
            double remaining = need.getValue() * batches;
            for (var deposit : deposits.values().stream().filter(d -> eligible(d, body, need.getKey()))
                    .sorted(Comparator.comparing(GeologicalDeposit::id)).toList()) {
                double taken = Math.min(remaining, Math.max(0, deposit.remainingVolumeKg()));
                deposits.put(deposit.id(), new GeologicalDeposit(deposit.id(), deposit.planetId(), deposit.materialId(),
                        deposit.initialVolumeKg(), deposit.remainingVolumeKg() - taken, deposit.concentrationModifier(),
                        deposit.isDiscovered(), deposit.ownerEntityId()));
                remaining -= taken;
                if (remaining <= 1e-8) break;
            }
            if (remaining > 1e-6) throw new IllegalStateException("Process exceeded its quoted deposit input");
        }
    }

    private static Map<String, Double> depositNeeds(List<ProcessInput> inputs) {
        var result = new TreeMap<String, Double>();
        inputs.stream().filter(i -> i.source == Source.DEPOSIT).forEach(i -> result.merge(i.materialId, i.kgPerBatch, Double::sum));
        return result;
    }
    private static boolean eligible(GeologicalDeposit deposit, String body, String material) {
        return deposit.planetId().equals(body) && deposit.materialId().equals(material) && deposit.isDiscovered();
    }
}
