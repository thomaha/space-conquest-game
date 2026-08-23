package com.spaceconquest.engine.espionage;

/**
 * Represents an undercover operative embedded within a target colony, space station or corporate entity.
 *
 * @param id                unique agent identifier
 * @param ownerEmpireId     sponsor empire identifier
 * @param targetEntityId    target planet, station or corporation ID
 * @param coverProfessionId undercover cover profession (e.g. bureaucrat, technician, engineer)
 * @param infiltrationLevel security clearance infiltration depth (1 to 10)
 * @param isCompromised     true if agent has been uncovered by counter-intelligence
 */
public record SleeperAgent(
        String id,
        String ownerEmpireId,
        String targetEntityId,
        String coverProfessionId,
        int infiltrationLevel,
        boolean isCompromised
) {
    public SleeperAgent {
        if (infiltrationLevel < 1) infiltrationLevel = 1;
        if (coverProfessionId == null) coverProfessionId = "bureaucrat";
    }
}
