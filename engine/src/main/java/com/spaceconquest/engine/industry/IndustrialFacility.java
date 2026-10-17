package com.spaceconquest.engine.industry;

/**
 * Scalable industrial facility executing closed-loop material recipes.
 *
 * @param id                      unique facility identifier
 * @param planetId                host planetary body ID
 * @param applicationId           technical application or recipe ID
 * @param ownerEntityId           owner sovereign empire or private corporation ID
 * @param ownershipType           PUBLIC_STATE, PRIVATE_CORPORATE, or HIVE_GRID
 * @param tier                    scaling tier (1 to 4+)
 * @param allocatedWorkers        workforce headcount assigned
 * @param workerProfessionId      profession required to operate the facility
 * @param isUndergoingExpansion   true if currently undergoing tier upgrade
 * @param expansionProgress       normalized progress toward next tier (0.0 to 1.0)
 * @param productionEquipment     captured typed process capacity and operator proficiency, or legacy production
 */
public record IndustrialFacility(
        String id,
        String planetId,
        String applicationId,
        String ownerEntityId,
        String ownershipType,
        int tier,
        int allocatedWorkers,
        String workerProfessionId,
        boolean isUndergoingExpansion,
        double expansionProgress,
        ProductionEquipment productionEquipment
) {
    public IndustrialFacility {
        if (productionEquipment == null) productionEquipment = ProductionWork.initialEquipment(applicationId);
    }
    public IndustrialFacility(String id, String planetId, String applicationId, String ownerEntityId,
                              String ownershipType, int tier, int allocatedWorkers, String workerProfessionId,
                              boolean isUndergoingExpansion, double expansionProgress) {
        this(id, planetId, applicationId, ownerEntityId, ownershipType, tier, allocatedWorkers,
                workerProfessionId, isUndergoingExpansion, expansionProgress, null);
    }
    public static final String PUBLIC_STATE = "PUBLIC_STATE";
    public static final String PRIVATE_CORPORATE = "PRIVATE_CORPORATE";
    public static final String HIVE_GRID = "HIVE_GRID";

    public double getEffectiveThroughputMultiplier() {
        var definition = FactoryDefinition.find(applicationId);
        if (definition != null) return definition.capacity(tier, isUndergoingExpansion);
        double tierMultiplier = Math.pow(1.5, Math.max(0, tier - 1));
        double expansionPenalty = isUndergoingExpansion ? 0.50 : 1.00;
        return tierMultiplier * expansionPenalty;
    }
}
