package com.spaceconquest.engine;

import java.util.List;

/**
 * Represents a technology in the research tree.
 *
 * @param id                   unique identifier of the technology
 * @param name                 display name of the technology
 * @param description          detailed description of what the technology encompasses
 * @param complexity           how challenging the technology is to discover
 * @param requiredTechnologies list of prerequisite technology IDs required before researching
 * @param applications         list of practical technical applications unlocked by the technology
 */
public record Technology(
    String id,
    String name,
    String description,
    int complexity,
    List<String> requiredTechnologies,
    List<TechnicalApplication> applications,
    Double researchPoints
) {
    public Technology(String id, String name, String description, int complexity,
                      List<String> requiredTechnologies, List<TechnicalApplication> applications) {
        this(id, name, description, complexity, requiredTechnologies, applications, null);
    }
    public Technology {
        requiredTechnologies = requiredTechnologies == null ? List.of() : List.copyOf(requiredTechnologies);
        applications = applications == null ? List.of() : List.copyOf(applications);
        if (researchPoints != null && (!Double.isFinite(researchPoints) || researchPoints <= 0))
            throw new IllegalArgumentException("Invalid research cost for " + id);
    }
}
