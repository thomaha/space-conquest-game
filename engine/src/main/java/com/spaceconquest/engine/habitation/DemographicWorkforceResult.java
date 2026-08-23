package com.spaceconquest.engine.habitation;

/**
 * Breakdown of active workforce, retired citizens and welfare costs for a population cohort.
 *
 * @param raceId                 species identifier
 * @param professionId           profession identifier
 * @param activeWorkers          number of working citizens generating labor hours
 * @param retiredCitizens        number of retired citizens consuming welfare
 * @param effectiveRetirementAge effective chronological age of retirement
 * @param naturalLifespan        effective natural lifespan after tech bonuses
 * @param welfareCreditsCost     public state credits required per turn to sustain retired citizens
 */
public record DemographicWorkforceResult(
        String raceId,
        String professionId,
        long activeWorkers,
        long retiredCitizens,
        int effectiveRetirementAge,
        int naturalLifespan,
        double welfareCreditsCost
) {}
