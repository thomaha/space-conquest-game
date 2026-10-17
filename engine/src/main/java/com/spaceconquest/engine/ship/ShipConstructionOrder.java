package com.spaceconquest.engine.ship;

import java.util.Map;

/** Daily ship build order with paid, physically consumed material installments. */
public record ShipConstructionOrder(
        String id,
        String ownerEntityId,
        String designId,
        String systemId,
        String yardBodyId,
        double accumulatedWorkHours,
        double requiredWorkHours,
        Map<String, Double> requiredMaterialsKg,
        Map<String, Double> consumedMaterialsKg,
        ShipyardContract yardContract,
        ShipConstructionPriority priority,
        ShipDeploymentIntent deployment,
        ShipRefitPlan refit,
        ShipReplacementPlan replacement,
        ShipRetirementPlan retirement
) {
    public ShipConstructionOrder {
        if (refit != null && (replacement != null || deployment != null)) throw new IllegalArgumentException("Refits cannot commission a replacement or deployment");
        if (retirement != null && (refit != null || replacement != null || deployment != null || requiredMaterialsKg != null && !requiredMaterialsKg.isEmpty()))
            throw new IllegalArgumentException("Retirement cannot build hardware or deploy ships");
        requiredMaterialsKg = requiredMaterialsKg == null ? Map.of() : Map.copyOf(requiredMaterialsKg);
        consumedMaterialsKg = consumedMaterialsKg == null ? Map.of() : Map.copyOf(consumedMaterialsKg);
    }
    public ShipConstructionOrder(String id, String ownerEntityId, String designId, String systemId, String yardBodyId, double accumulatedWorkHours,
            double requiredWorkHours, Map<String, Double> requiredMaterialsKg, Map<String, Double> consumedMaterialsKg,
            ShipyardContract yardContract, ShipConstructionPriority priority, ShipDeploymentIntent deployment, ShipRefitPlan refit, ShipReplacementPlan replacement) {
        this(id, ownerEntityId, designId, systemId, yardBodyId, accumulatedWorkHours, requiredWorkHours, requiredMaterialsKg,
                consumedMaterialsKg, yardContract, priority, deployment, refit, replacement, null);
    }
    public ShipConstructionOrder withRetirement(ShipRetirementPlan value) {
        return new ShipConstructionOrder(id, ownerEntityId, designId, systemId, yardBodyId, accumulatedWorkHours, requiredWorkHours,
                requiredMaterialsKg, consumedMaterialsKg, yardContract, priority, deployment, refit, replacement, value);
    }
    public ShipConstructionOrder(String id, String ownerEntityId, String designId, String systemId, String yardBodyId, double accumulatedWorkHours,
                                 double requiredWorkHours, Map<String, Double> requiredMaterialsKg, Map<String, Double> consumedMaterialsKg,
                                 ShipyardContract yardContract, ShipConstructionPriority priority, ShipDeploymentIntent deployment, ShipRefitPlan refit) {
        this(id, ownerEntityId, designId, systemId, yardBodyId, accumulatedWorkHours, requiredWorkHours, requiredMaterialsKg,
                consumedMaterialsKg, yardContract, priority, deployment, refit, null);
    }
    public ShipConstructionOrder withReplacement(ShipReplacementPlan value) {
        return new ShipConstructionOrder(id, ownerEntityId, designId, systemId, yardBodyId, accumulatedWorkHours, requiredWorkHours,
                requiredMaterialsKg, consumedMaterialsKg, yardContract, priority, deployment, refit, value, retirement);
    }
    public ShipConstructionOrder(String id, String ownerEntityId, String designId, String systemId, String yardBodyId, double accumulatedWorkHours,
                                 double requiredWorkHours, Map<String, Double> requiredMaterialsKg, Map<String, Double> consumedMaterialsKg,
                                 ShipyardContract yardContract, ShipConstructionPriority priority, ShipDeploymentIntent deployment) {
        this(id, ownerEntityId, designId, systemId, yardBodyId, accumulatedWorkHours, requiredWorkHours, requiredMaterialsKg,
                consumedMaterialsKg, yardContract, priority, deployment, null);
    }
    public ShipConstructionOrder withRefit(ShipRefitPlan value) {
        return new ShipConstructionOrder(id, ownerEntityId, designId, systemId, yardBodyId, accumulatedWorkHours, requiredWorkHours,
                requiredMaterialsKg, consumedMaterialsKg, yardContract, priority, deployment, value, replacement, retirement);
    }
    public ShipConstructionOrder(String id, String ownerEntityId, String designId, String systemId, String yardBodyId, double accumulatedWorkHours,
                                 double requiredWorkHours, Map<String, Double> requiredMaterialsKg, Map<String, Double> consumedMaterialsKg, ShipyardContract yardContract, ShipConstructionPriority priority) {
        this(id, ownerEntityId, designId, systemId, yardBodyId, accumulatedWorkHours, requiredWorkHours, requiredMaterialsKg, consumedMaterialsKg, yardContract, priority, null);
    }
    public ShipConstructionOrder withDeployment(ShipDeploymentIntent value) {
        return new ShipConstructionOrder(id, ownerEntityId, designId, systemId, yardBodyId, accumulatedWorkHours, requiredWorkHours, requiredMaterialsKg, consumedMaterialsKg, yardContract, priority, value, refit, replacement, retirement);
    }
    public ShipConstructionOrder(String id, String ownerEntityId, String designId, String systemId, String yardBodyId, double accumulatedWorkHours,
                                 double requiredWorkHours, Map<String, Double> requiredMaterialsKg, Map<String, Double> consumedMaterialsKg, ShipyardContract yardContract) {
        this(id, ownerEntityId, designId, systemId, yardBodyId, accumulatedWorkHours, requiredWorkHours, requiredMaterialsKg, consumedMaterialsKg, yardContract, null);
    }
    public ShipConstructionOrder withPriority(ShipConstructionPriority value) {
        return new ShipConstructionOrder(id, ownerEntityId, designId, systemId, yardBodyId, accumulatedWorkHours, requiredWorkHours, requiredMaterialsKg, consumedMaterialsKg, yardContract, value, deployment, refit, replacement, retirement);
    }
    public ShipConstructionOrder(String id, String ownerEntityId, String designId, String systemId, String yardBodyId, double accumulatedWorkHours,
                                 double requiredWorkHours, Map<String, Double> requiredMaterialsKg, Map<String, Double> consumedMaterialsKg) {
        this(id, ownerEntityId, designId, systemId, yardBodyId, accumulatedWorkHours, requiredWorkHours, requiredMaterialsKg, consumedMaterialsKg, null);
    }
}
