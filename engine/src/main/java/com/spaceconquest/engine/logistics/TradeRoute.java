package com.spaceconquest.engine.logistics;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

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
        String status,
        Map<String, TradeCargo> cargoManifest
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
        if (cargoManifest == null) cargoManifest = onboardKg > 0
                ? Map.of(materialId, new TradeCargo(onboardKg, onboardCostCredits)) : Map.of();
        cargoManifest = Map.copyOf(cargoManifest);
        if (cargoManifest.keySet().stream().anyMatch(key -> key == null || key.isBlank()))
            throw new IllegalArgumentException("Invalid manifest material");
        onboardKg = cargoManifest.values().stream().mapToDouble(TradeCargo::massKg).sum();
        onboardCostCredits = cargoManifest.values().stream().mapToDouble(TradeCargo::costCredits).sum();
        if (!Double.isFinite(onboardKg) || !Double.isFinite(onboardCostCredits))
            throw new IllegalArgumentException("Manifest totals overflow");
    }

    public TradeRoute(String id, String name, String ownerEntityId, String originEntityId,
                      String destinationEntityId, String materialId, double transferAmountPerTurnKg,
                      double minSourceInventoryThresholdKg, double maxDestinationCapacityKg,
                      List<String> assignedFreighterIds, double totalVolumeMovedKg, boolean isActive,
                      String phase, double onboardKg, double onboardCostCredits,
                      double dailyOperatingResultCredits, double cumulativeOperatingResultCredits,
                      boolean roaming, String status) {
        this(id, name, ownerEntityId, originEntityId, destinationEntityId, materialId,
                transferAmountPerTurnKg, minSourceInventoryThresholdKg, maxDestinationCapacityKg,
                assignedFreighterIds, totalVolumeMovedKg, isActive, phase, onboardKg, onboardCostCredits,
                dailyOperatingResultCredits, cumulativeOperatingResultCredits, roaming, status, null);
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
        var manifest = new HashMap<String, TradeCargo>();
        cargoManifest.forEach((material, lot) -> {
            double mass = onboardKg <= 0 ? 0 : lot.massKg() * newOnboardKg / onboardKg;
            if (mass > 1e-6) manifest.put(material, lot.remaining(mass));
        });
        return new TradeRoute(id, name, ownerEntityId, originEntityId,
                destinationEntityId, materialId, transferAmountPerTurnKg,
                minSourceInventoryThresholdKg, maxDestinationCapacityKg,
                assignedFreighterIds, totalVolumeMovedKg + movedKg, isActive,
                newPhase, newOnboardKg, onboardCostCredits,
                dailyOperatingResultCredits, cumulativeOperatingResultCredits, roaming, status, manifest);
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
        return withDeliveredCargo(materialId, massKg, revenueCredits, tariffCredits);
    }

    public TradeRoute withDeliveredCargo(String material, double massKg, double revenueCredits, double tariffCredits) {
        var lot = cargoManifest.getOrDefault(material, new TradeCargo(0, 0));
        double delivered = Math.clamp(massKg, 0, lot.massKg());
        var retained = lot.remaining(lot.massKg() - delivered);
        double cost = lot.costCredits() - retained.costCredits();
        var manifest = new HashMap<>(cargoManifest);
        if (retained.massKg() > 1e-6) manifest.put(material, retained); else manifest.remove(material);
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
                cumulativeOperatingResultCredits + result, roaming, status, manifest);
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
                cumulativeOperatingResultCredits - costCredits, roaming, status, cargoManifest);
    }

    /** Writes off missing paid freight without a second cash charge or claimed delivery. */
    public TradeRoute withAvailableCargo(double availableKg) {
        if (!Double.isFinite(availableKg) || availableKg < 0) throw new IllegalArgumentException("Invalid available cargo");
        var available = new HashMap<String, Double>();
        cargoManifest.forEach((material, lot) -> available.put(material, lot.massKg()));
        available.put(materialId, availableKg);
        return withAvailableCargo(available);
    }

    public TradeRoute withAvailableCargo(Map<String, Double> available) {
        var manifest = new HashMap<String, TradeCargo>();
        cargoManifest.forEach((material, lot) -> {
            double mass = available.getOrDefault(material, 0.0);
            if (!Double.isFinite(mass) || mass < 0) throw new IllegalArgumentException("Invalid available cargo");
            var retained = lot.remaining(mass);
            if (retained.massKg() > 1e-6) manifest.put(material, retained);
        });
        if (manifest.equals(cargoManifest)) return this;
        double remaining = manifest.values().stream().mapToDouble(TradeCargo::massKg).sum();
        double retainedCost = manifest.values().stream().mapToDouble(TradeCargo::costCredits).sum();
        double loss = Math.max(0, onboardCostCredits - retainedCost);
        return new TradeRoute(id, name, ownerEntityId, originEntityId, destinationEntityId, materialId,
                transferAmountPerTurnKg, minSourceInventoryThresholdKg, maxDestinationCapacityKg,
                assignedFreighterIds, totalVolumeMovedKg, isActive, remaining > 0 ? phase : RETURNING,
                remaining, retainedCost, dailyOperatingResultCredits - loss, cumulativeOperatingResultCredits - loss, roaming, status, manifest);
    }

    public TradeRoute resetDailyResult() {
        return new TradeRoute(id, name, ownerEntityId, originEntityId,
                destinationEntityId, materialId, transferAmountPerTurnKg,
                minSourceInventoryThresholdKg, maxDestinationCapacityKg,
                assignedFreighterIds, totalVolumeMovedKg, isActive,
                phase, onboardKg, onboardCostCredits, 0.0,
                cumulativeOperatingResultCredits, roaming, status, cargoManifest);
    }

    public TradeRoute withDestination(String newDestinationEntityId) {
        if (newDestinationEntityId == null || newDestinationEntityId.isBlank()
                || newDestinationEntityId.equals(destinationEntityId)) return this;
        return new TradeRoute(id, name, ownerEntityId, originEntityId,
                newDestinationEntityId, materialId, transferAmountPerTurnKg,
                minSourceInventoryThresholdKg, maxDestinationCapacityKg,
                assignedFreighterIds, totalVolumeMovedKg, isActive, phase,
                onboardKg, onboardCostCredits, dailyOperatingResultCredits,
                cumulativeOperatingResultCredits, roaming, status, cargoManifest);
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
                cumulativeOperatingResultCredits, roaming, status, cargoManifest);
    }

    public TradeRoute withRoaming(boolean enabled) {
        return new TradeRoute(id, name, ownerEntityId, originEntityId, destinationEntityId, materialId,
                transferAmountPerTurnKg, minSourceInventoryThresholdKg, maxDestinationCapacityKg, assignedFreighterIds,
                totalVolumeMovedKg, isActive, phase, onboardKg, onboardCostCredits, dailyOperatingResultCredits,
                cumulativeOperatingResultCredits, enabled, status, cargoManifest);
    }

    public TradeRoute withStatus(String message) {
        return new TradeRoute(id, name, ownerEntityId, originEntityId, destinationEntityId, materialId,
                transferAmountPerTurnKg, minSourceInventoryThresholdKg, maxDestinationCapacityKg, assignedFreighterIds,
                totalVolumeMovedKg, isActive, phase, onboardKg, onboardCostCredits, dailyOperatingResultCredits,
                cumulativeOperatingResultCredits, roaming, message, cargoManifest);
    }

    public TradeRoute atNewOrigin(String hubId) {
        return new TradeRoute(id, name, ownerEntityId, hubId, destinationEntityId, materialId,
                transferAmountPerTurnKg, minSourceInventoryThresholdKg, maxDestinationCapacityKg, assignedFreighterIds,
                totalVolumeMovedKg, isActive, LOADING, onboardKg, onboardCostCredits, dailyOperatingResultCredits,
                cumulativeOperatingResultCredits, roaming, "At port; choosing the next trade after sale.", cargoManifest);
    }

    public TradeRoute withLoadedManifest(Map<String, TradeCargo> manifest) {
        return new TradeRoute(id, name, ownerEntityId, originEntityId, destinationEntityId, materialId,
                transferAmountPerTurnKg, minSourceInventoryThresholdKg, maxDestinationCapacityKg, assignedFreighterIds,
                totalVolumeMovedKg, isActive, DELIVERING, 0, 0, dailyOperatingResultCredits,
                cumulativeOperatingResultCredits, roaming, status, manifest);
    }

    public TradeRoute withActive(boolean enabled) {
        return new TradeRoute(id, name, ownerEntityId, originEntityId, destinationEntityId, materialId,
                transferAmountPerTurnKg, minSourceInventoryThresholdKg, maxDestinationCapacityKg, assignedFreighterIds,
                totalVolumeMovedKg, enabled, phase, onboardKg, onboardCostCredits, dailyOperatingResultCredits,
                cumulativeOperatingResultCredits, roaming, status, cargoManifest);
    }
}
