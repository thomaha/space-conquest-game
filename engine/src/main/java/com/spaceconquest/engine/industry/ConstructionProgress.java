package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.GameState;

import java.util.HashMap;
import java.util.Map;

/** Advances construction in proportion to the portion of its material bill actually delivered. */
public final class ConstructionProgress {
    private ConstructionProgress() {}

    public record Step(GameState state, double workHours, Map<String, Double> consumedKg,
                       boolean complete) {}

    public static Step advance(GameState state, String bodyId, String ownerId,
                               Map<String, Double> requiredKg, Map<String, Double> priorConsumedKg,
                               double priorHours, double requiredHours, double dailyHours) {
        return advanceWith(state, requiredKg, priorConsumedKg, priorHours, requiredHours,
                dailyHours, (current, request, maximum) -> ConstructionMaterials.buyUpTo(
                        current, bodyId, ownerId, request, maximum));
    }

    public static Step advanceOrbital(GameState state, String systemId, String siteId,
                                      String ownerId, Map<String, Double> requiredKg,
                                      Map<String, Double> priorConsumedKg, double priorHours,
                                      double requiredHours, double dailyHours) {
        return advanceWith(state, requiredKg, priorConsumedKg, priorHours, requiredHours,
                dailyHours, (current, request, maximum) -> ConstructionMaterials.buyOrbitalUpTo(
                        current, systemId, siteId, ownerId, request, maximum));
    }

    private interface Buyer {
        ConstructionMaterials.Purchase buy(GameState state, Map<String, Double> request, double maxKg);
    }

    private static Step advanceWith(GameState state, Map<String, Double> requiredKg,
                                    Map<String, Double> priorConsumedKg, double priorHours,
                                    double requiredHours, double dailyHours, Buyer buyer) {
        double totalHours = Math.max(1.0, requiredHours);
        double targetHours = Math.min(totalHours, priorHours + Math.max(0.0, dailyHours));
        Map<String, Double> consumed = new HashMap<>(priorConsumedKg);
        double totalMass = requiredKg.values().stream().mapToDouble(Double::doubleValue).sum();
        GameState current = state;
        if (totalMass > 0.0) {
            double desiredMass = totalMass * targetHours / totalHours;
            double missingMass = Math.max(0.0, desiredMass - mass(consumed));
            ConstructionMaterials.Purchase purchase = buyer.buy(state,
                    remaining(requiredKg, consumed), missingMass);
            current = purchase.state();
            purchase.acquiredKg().forEach((material, kg) -> consumed.merge(material, kg, Double::sum));
        }
        double supportedHours = totalMass <= 0.0 ? targetHours
                : Math.min(targetHours, mass(consumed) / totalMass * totalHours);
        double hours = Math.max(priorHours, supportedHours);
        boolean complete = hours + 0.000001 >= totalHours && requiredKg.entrySet().stream()
                .allMatch(entry -> consumed.getOrDefault(entry.getKey(), 0.0) + 0.000001
                        >= entry.getValue());
        return new Step(current, hours, Map.copyOf(consumed), complete);
    }

    private static Map<String, Double> remaining(Map<String, Double> required,
                                                  Map<String, Double> consumed) {
        Map<String, Double> request = new HashMap<>();
        for (var entry : required.entrySet()) {
            double quantity = Math.max(0.0,
                    entry.getValue() - consumed.getOrDefault(entry.getKey(), 0.0));
            if (quantity > 0.0) request.put(entry.getKey(), quantity);
        }
        return request;
    }

    private static double mass(Map<String, Double> materials) {
        return materials.values().stream().mapToDouble(Double::doubleValue).sum();
    }
}
