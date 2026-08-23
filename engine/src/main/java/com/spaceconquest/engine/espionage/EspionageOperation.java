package com.spaceconquest.engine.espionage;

/**
 * Covert operation directed by imperial intelligence against a rival entity.
 *
 * @param id                 unique operation identifier
 * @param operationType      type of covert action (TECH_THEFT, POWER_GRID_SABOTAGE, UNREST_PROVOCATION, FALSE_FLAG_INCIDENT)
 * @param initiatorEmpireId  empire launching the operation
 * @param targetEmpireId     target empire affected
 * @param targetEntityId     target planet, station or corporation ID
 * @param assignedAgentId    assigned sleeper agent ID
 * @param successProbability base success probability (0.0 to 1.0)
 * @param progressPercent    current completion percentage (0.0 to 100.0)
 * @param isCompleted        true if operation has executed
 * @param wasDetected        true if target counter-espionage uncovered the operation
 */
public record EspionageOperation(
        String id,
        String operationType,
        String initiatorEmpireId,
        String targetEmpireId,
        String targetEntityId,
        String assignedAgentId,
        double successProbability,
        double progressPercent,
        boolean isCompleted,
        boolean wasDetected
) {
    public static final String OP_TECH_THEFT = "TECH_THEFT";
    public static final String OP_POWER_GRID_SABOTAGE = "POWER_GRID_SABOTAGE";
    public static final String OP_UNREST_PROVOCATION = "UNREST_PROVOCATION";
    public static final String OP_FALSE_FLAG_INCIDENT = "FALSE_FLAG_INCIDENT";

    public EspionageOperation {
        if (operationType == null) operationType = OP_TECH_THEFT;
    }
}
