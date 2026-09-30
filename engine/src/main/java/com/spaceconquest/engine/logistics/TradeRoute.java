package com.spaceconquest.engine.logistics;

import java.util.List;

/**
 * Represents an automated cargo logistics route transporting materials between planets or commercial hubs.
 */
public record TradeRoute(
        String id,
        String name,
        String ownerEntityId,
        String originEntityId,
        String destinationEntityId,
        String materialId,
        double transferAmountPerTurnKg,
        double minSourceInventoryThresholdKg,
        double maxDestinationCapacityKg,
        List<String> assignedFreighterIds,
        double totalVolumeMovedKg,
        boolean isActive,
        String phase,
        double onboardKg,
        double onboardCostCredits,
        double dailyOperatingResultCredits,
        double cumulativeOperatingResultCredits
) {
    public static final String LOADING = "LOADING";
    public static final String DELIVERING = "DELIVERING";
    public static final String RETURNING = "RETURNING";

    public TradeRoute {
        assignedFreighterIds = assignedFreighterIds == null ? List.of() : List.copyOf(assignedFreighterIds);
        if (phase == null || phase.isBlank()) phase = LOADING;
        if (!Double.isFinite(onboardKg) || onboardKg < 0.0)
            throw new IllegalArgumentException("Invalid route cargo mass");
        if (!Double.isFinite(onboardCostCredits) || onboardCostCredits < 0.0
                || !Double.isFinite(dailyOperatingResultCredits)
                || !Double.isFinite(cumulativeOperatingResultCredits))
            throw new IllegalArgumentException("Invalid route financial balance");
    }

    public TradeRoute(String id, String name, String ownerEntityId, String originEntityId,
                      String destinationEntityId, String materialId, double transferAmountPerTurnKg,
                      double minSourceInventoryThresholdKg, double maxDestinationCapacityKg,
                      List<String> assignedFreighterIds, double totalVolumeMovedKg,
                      boolean isActive) {
        this(id, name, ownerEntityId, originEntityId, destinationEntityId, materialId,
                transferAmountPerTurnKg, minSourceInventoryThresholdKg,
                maxDestinationCapacityKg, assignedFreighterIds, totalVolumeMovedKg,
                isActive, LOADING, 0.0, 0.0, 0.0, 0.0);
    }

    public TradeRoute(String id, String name, String ownerEntityId, String originEntityId,
                      String destinationEntityId, String materialId, double transferAmountPerTurnKg,
                      double minSourceInventoryThresholdKg, double maxDestinationCapacityKg,
                      List<String> assignedFreighterIds, double totalVolumeMovedKg,
                      boolean isActive, String phase, double onboardKg) {
        this(id, name, ownerEntityId, originEntityId, destinationEntityId, materialId,
                transferAmountPerTurnKg, minSourceInventoryThresholdKg,
                maxDestinationCapacityKg, assignedFreighterIds, totalVolumeMovedKg,
                isActive, phase, onboardKg, 0.0, 0.0, 0.0);
    }

    public TradeRoute withTrip(String newPhase, double newOnboardKg, double movedKg) {
        return new TradeRoute(id, name, ownerEntityId, originEntityId,
                destinationEntityId, materialId, transferAmountPerTurnKg,
                minSourceInventoryThresholdKg, maxDestinationCapacityKg,
                assignedFreighterIds, totalVolumeMovedKg + movedKg, isActive,
                newPhase, newOnboardKg, onboardCostCredits,
                dailyOperatingResultCredits, cumulativeOperatingResultCredits);
    }

    public TradeRoute withLoadedCargo(double massKg, double costCredits) {
        return new TradeRoute(id, name, ownerEntityId, originEntityId,
                destinationEntityId, materialId, transferAmountPerTurnKg,
                minSourceInventoryThresholdKg, maxDestinationCapacityKg,
                assignedFreighterIds, totalVolumeMovedKg, isActive,
                DELIVERING, massKg, costCredits,
                dailyOperatingResultCredits, cumulativeOperatingResultCredits);
    }

    public TradeRoute withDeliveredCargo(double massKg, double revenueCredits,
                                         double tariffCredits) {
        double delivered = Math.min(onboardKg, massKg);
        double cost = onboardKg <= 0.0 ? 0.0 : onboardCostCredits * delivered / onboardKg;
        double result = revenueCredits - tariffCredits - cost;
        double remaining = Math.max(0.0, onboardKg - delivered);
        return new TradeRoute(id, name, ownerEntityId, originEntityId,
                destinationEntityId, materialId, transferAmountPerTurnKg,
                minSourceInventoryThresholdKg, maxDestinationCapacityKg,
                assignedFreighterIds, totalVolumeMovedKg + delivered, isActive,
                remaining > 0.000001 ? DELIVERING : RETURNING,
                remaining, remaining > 0.000001
                ? Math.max(0.0, onboardCostCredits - cost) : 0.0,
                dailyOperatingResultCredits + result,
                cumulativeOperatingResultCredits + result);
    }

    public TradeRoute withOperatingCost(double costCredits) {
        if (!Double.isFinite(costCredits) || costCredits < 0.0)
            throw new IllegalArgumentException("Invalid route operating cost");
        if (costCredits == 0.0) return this;
        return new TradeRoute(id, name, ownerEntityId, originEntityId,
                destinationEntityId, materialId, transferAmountPerTurnKg,
                minSourceInventoryThresholdKg, maxDestinationCapacityKg,
                assignedFreighterIds, totalVolumeMovedKg, isActive,
                phase, onboardKg, onboardCostCredits,
                dailyOperatingResultCredits - costCredits,
                cumulativeOperatingResultCredits - costCredits);
    }

    public TradeRoute resetDailyResult() {
        return new TradeRoute(id, name, ownerEntityId, originEntityId,
                destinationEntityId, materialId, transferAmountPerTurnKg,
                minSourceInventoryThresholdKg, maxDestinationCapacityKg,
                assignedFreighterIds, totalVolumeMovedKg, isActive,
                phase, onboardKg, onboardCostCredits, 0.0,
                cumulativeOperatingResultCredits);
    }
}
