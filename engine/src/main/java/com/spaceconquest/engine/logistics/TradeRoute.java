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
        double cumulativeOperatingResultCredits,
        boolean roaming,
        String status
) {
    public static final String LOADING = "LOADING";
    public static final String DELIVERING = "DELIVERING";
    public static final String RETURNING = "RETURNING";

    public TradeRoute {
        assignedFreighterIds = assignedFreighterIds == null ? List.of() : List.copyOf(assignedFreighterIds);
        if (status == null) status = "";
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
                      List<String> assignedFreighterIds, double totalVolumeMovedKg, boolean isActive,
                      String phase, double onboardKg, double onboardCostCredits,
                      double dailyOperatingResultCredits, double cumulativeOperatingResultCredits) {
        this(id, name, ownerEntityId, originEntityId, destinationEntityId, materialId,
                transferAmountPerTurnKg, minSourceInventoryThresholdKg, maxDestinationCapacityKg,
                assignedFreighterIds, totalVolumeMovedKg, isActive, phase, onboardKg,
                onboardCostCredits, dailyOperatingResultCredits, cumulativeOperatingResultCredits, false, "");
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
                dailyOperatingResultCredits, cumulativeOperatingResultCredits, roaming, status);
    }

    public TradeRoute withLoadedCargo(double massKg, double costCredits) {
        return new TradeRoute(id, name, ownerEntityId, originEntityId,
                destinationEntityId, materialId, transferAmountPerTurnKg,
                minSourceInventoryThresholdKg, maxDestinationCapacityKg,
                assignedFreighterIds, totalVolumeMovedKg, isActive,
                DELIVERING, massKg, costCredits,
                dailyOperatingResultCredits, cumulativeOperatingResultCredits, roaming, status);
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
                cumulativeOperatingResultCredits + result, roaming, status);
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
                cumulativeOperatingResultCredits - costCredits, roaming, status);
    }

    /** Writes off missing paid freight without a second cash charge or claimed delivery. */
    public TradeRoute withAvailableCargo(double availableKg) {
        if (!Double.isFinite(availableKg) || availableKg < 0) throw new IllegalArgumentException("Invalid available cargo");
        double remaining = Math.min(onboardKg, availableKg);
        if (remaining == onboardKg) return this;
        double retainedCost = onboardKg <= 0 ? 0 : onboardCostCredits * remaining / onboardKg;
        double loss = Math.max(0, onboardCostCredits - retainedCost);
        return new TradeRoute(id, name, ownerEntityId, originEntityId, destinationEntityId, materialId,
                transferAmountPerTurnKg, minSourceInventoryThresholdKg, maxDestinationCapacityKg,
                assignedFreighterIds, totalVolumeMovedKg, isActive, remaining > 0 ? phase : RETURNING,
                remaining, retainedCost, dailyOperatingResultCredits - loss, cumulativeOperatingResultCredits - loss, roaming, status);
    }

    public TradeRoute resetDailyResult() {
        return new TradeRoute(id, name, ownerEntityId, originEntityId,
                destinationEntityId, materialId, transferAmountPerTurnKg,
                minSourceInventoryThresholdKg, maxDestinationCapacityKg,
                assignedFreighterIds, totalVolumeMovedKg, isActive,
                phase, onboardKg, onboardCostCredits, 0.0,
                cumulativeOperatingResultCredits, roaming, status);
    }

    public TradeRoute withDestination(String newDestinationEntityId) {
        if (newDestinationEntityId == null || newDestinationEntityId.isBlank()
                || newDestinationEntityId.equals(destinationEntityId)) return this;
        return new TradeRoute(id, name, ownerEntityId, originEntityId,
                newDestinationEntityId, materialId, transferAmountPerTurnKg,
                minSourceInventoryThresholdKg, maxDestinationCapacityKg,
                assignedFreighterIds, totalVolumeMovedKg, isActive, phase,
                onboardKg, onboardCostCredits, dailyOperatingResultCredits,
                cumulativeOperatingResultCredits, roaming, status);
    }

    public TradeRoute withMarketChoice(String newMaterialId, String newDestinationEntityId) {
        String material = newMaterialId == null || newMaterialId.isBlank()
                ? materialId : newMaterialId;
        String destination = newDestinationEntityId == null || newDestinationEntityId.isBlank()
                ? destinationEntityId : newDestinationEntityId;
        if (material.equals(materialId) && destination.equals(destinationEntityId)) return this;
        return new TradeRoute(id, name, ownerEntityId, originEntityId,
                destination, material, transferAmountPerTurnKg,
                minSourceInventoryThresholdKg, maxDestinationCapacityKg,
                assignedFreighterIds, totalVolumeMovedKg, isActive, phase,
                onboardKg, onboardCostCredits, dailyOperatingResultCredits,
                cumulativeOperatingResultCredits, roaming, status);
    }

    public TradeRoute withRoaming(boolean enabled) {
        return new TradeRoute(id, name, ownerEntityId, originEntityId, destinationEntityId, materialId,
                transferAmountPerTurnKg, minSourceInventoryThresholdKg, maxDestinationCapacityKg, assignedFreighterIds,
                totalVolumeMovedKg, isActive, phase, onboardKg, onboardCostCredits, dailyOperatingResultCredits,
                cumulativeOperatingResultCredits, enabled, status);
    }

    public TradeRoute withStatus(String message) {
        return new TradeRoute(id, name, ownerEntityId, originEntityId, destinationEntityId, materialId,
                transferAmountPerTurnKg, minSourceInventoryThresholdKg, maxDestinationCapacityKg, assignedFreighterIds,
                totalVolumeMovedKg, isActive, phase, onboardKg, onboardCostCredits, dailyOperatingResultCredits,
                cumulativeOperatingResultCredits, roaming, message);
    }

    public TradeRoute atNewOrigin(String hubId) {
        return new TradeRoute(id, name, ownerEntityId, hubId, destinationEntityId, materialId,
                transferAmountPerTurnKg, minSourceInventoryThresholdKg, maxDestinationCapacityKg, assignedFreighterIds,
                totalVolumeMovedKg, isActive, LOADING, onboardKg, onboardCostCredits, dailyOperatingResultCredits,
                cumulativeOperatingResultCredits, roaming, "At port; choosing the next trade after sale.");
    }
}
