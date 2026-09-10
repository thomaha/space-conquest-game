package com.spaceconquest.engine;

import com.spaceconquest.engine.demographics.CohortFragmentationProcessor;
import com.spaceconquest.engine.demographics.ColonyDemographics;
import com.spaceconquest.engine.demographics.ColonyFocus;

import java.util.Map;

public record Population(
    String raceId,
    Map<Integer, Long> ageGroups
) {
    public long totalCount() {
        if (ageGroups == null) return 0L;
        return ageGroups.values().stream().mapToLong(Long::longValue).sum();
    }

    /**
     * Converts this population to single-education citizen cohort fragments according to colony focus.
     */
    public ColonyDemographics toDemographics(String colonyId, String systemId, ColonyFocus focus) {
        return new CohortFragmentationProcessor().convertPopulationToCohorts(this, colonyId, systemId, focus);
    }
}
