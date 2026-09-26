package com.spaceconquest.engine.demographics;

import com.spaceconquest.engine.Population;
import com.spaceconquest.engine.Profession;
import com.spaceconquest.engine.economy.SystemEconomy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Simulates single-education cohort fragmentation across colonies, including multi-profession
 * staffing requirements, administrative bureaucrat allocations, and upward social mobility retraining.
 */
public class CohortFragmentationProcessor {

    public static final double ALPHA_EDUCATION = 0.005;
    public static final double ALPHA_LAW = 0.008;
    public static final double ALPHA_HEALTH = 0.006;
    public static final double ALPHA_INFRASTRUCTURE = 0.004;
    public static final double ALPHA_MILITARY = 0.005;

    public static final double DEFAULT_BASE_MOBILITY_RATE = 0.02;

    /**
     * Generates a balanced distribution of single-education cohort fragments based on colony specialization.
     *
     * @param colonyId        colony identifier
     * @param systemId        system identifier
     * @param raceId          species identifier
     * @param totalPopulation total population headcount
     * @param focus           colony specialization profile
     * @param baseAge         starting chronological age bracket
     * @return initialized ColonyDemographics with single-education cohort fragments
     */
    public ColonyDemographics generateSpecializedColony(
            String colonyId,
            String systemId,
            String raceId,
            long totalPopulation,
            ColonyFocus focus,
            int baseAge
    ) {
        if (totalPopulation <= 0) {
            return new ColonyDemographics(colonyId, systemId, List.of());
        }

        Map<String, Double> distribution = getDistributionProfile(focus != null ? focus : ColonyFocus.BALANCED);
        List<CitizenCohort> cohorts = new ArrayList<>();

        long allocated = 0L;
        String primaryProf = distribution.keySet().iterator().next();

        for (Map.Entry<String, Double> entry : distribution.entrySet()) {
            String profId = entry.getKey();
            double ratio = entry.getValue();
            long count = Math.round(totalPopulation * ratio);
            if (count > 0) {
                cohorts.add(new CitizenCohort(raceId, profId, baseAge, count));
                allocated += count;
            }
        }

        // Reconcile rounding discrepancy on primary profession
        long discrepancy = totalPopulation - allocated;
        if (discrepancy != 0) {
            for (int i = 0; i < cohorts.size(); i++) {
                CitizenCohort c = cohorts.get(i);
                if (c.professionId().equalsIgnoreCase(primaryProf)) {
                    long newHeadcount = Math.max(0L, c.headcount() + discrepancy);
                    cohorts.set(i, c.withHeadcount(newHeadcount));
                    break;
                }
            }
        }

        return new ColonyDemographics(colonyId, systemId, cohorts);
    }

    /**
     * Converts a generic Population record with age groups into single-education cohort fragments.
     */
    public ColonyDemographics convertPopulationToCohorts(
            Population population,
            String colonyId,
            String systemId,
            ColonyFocus focus
    ) {
        if (population == null || population.totalCount() <= 0) {
            return new ColonyDemographics(colonyId, systemId, List.of());
        }

        Map<String, Double> distribution = getDistributionProfile(focus != null ? focus : ColonyFocus.BALANCED);
        List<CitizenCohort> resultCohorts = new ArrayList<>();

        for (Map.Entry<Integer, Long> ageEntry : population.ageGroups().entrySet()) {
            int age = ageEntry.getKey();
            long agePop = ageEntry.getValue();
            if (agePop <= 0) continue;

            String primaryProf = distribution.keySet().iterator().next();
            long remaining = agePop;

            for (Map.Entry<String, Double> distEntry : distribution.entrySet()) {
                String profId = distEntry.getKey();
                if (profId.equals(primaryProf)) continue;
                double ratio = distEntry.getValue();
                long count = Math.min(remaining, Math.max(0L, Math.round(agePop * ratio)));
                if (count > 0) {
                    resultCohorts.add(new CitizenCohort(population.raceId(), profId, age, count));
                    remaining -= count;
                }
            }
            if (remaining > 0) {
                resultCohorts.add(new CitizenCohort(population.raceId(), primaryProf, age, remaining));
            }
        }

        return new ColonyDemographics(colonyId, systemId, resultCohorts).consolidated();
    }

    /**
     * Evaluates multi-profession staffing requirements and identifies bottlenecks for a facility or project.
     *
     * @param demographics        colony demographic composition
     * @param requiredProfessions map of professionId -> required headcount
     * @return staffing evaluation result
     */
    public FacilityStaffingResult evaluateFacilityStaffing(
            ColonyDemographics demographics,
            Map<String, Long> requiredProfessions
    ) {
        if (requiredProfessions == null || requiredProfessions.isEmpty()) {
            return new FacilityStaffingResult(1.0, Map.of(), Map.of(), Map.of(), null, true);
        }

        Map<String, Long> assigned = new HashMap<>();
        Map<String, Double> ratios = new HashMap<>();
        double minRatio = 1.0;
        String bottleneck = null;
        boolean fullyStaffed = true;

        for (Map.Entry<String, Long> entry : requiredProfessions.entrySet()) {
            String profId = entry.getKey();
            long req = entry.getValue();
            long avail = demographics != null ? demographics.getHeadcount(profId) : 0L;
            assigned.put(profId, avail);

            if (req <= 0) {
                ratios.put(profId, 1.0);
            } else {
                double ratio = (double) avail / (double) req;
                ratios.put(profId, ratio);
                if (ratio < 1.0) {
                    fullyStaffed = false;
                }
                if (ratio < minRatio || bottleneck == null) {
                    minRatio = ratio;
                    bottleneck = profId;
                }
            }
        }

        double operationalEfficiency = Math.min(1.0, Math.max(0.0, minRatio));
        return new FacilityStaffingResult(
                operationalEfficiency,
                Collections.unmodifiableMap(requiredProfessions),
                Collections.unmodifiableMap(assigned),
                Collections.unmodifiableMap(ratios),
                bottleneck,
                fullyStaffed
        );
    }

    /**
     * Calculates administrative bureaucrat demand across the 5 public sectors and assigns available bureaucrats.
     * Formula: D_admin,i = ceil(P_total * alpha_i * E_base,i)
     * Effective efficiency: E_eff,i = E_base,i * (0.35 + 0.65 * A_i)
     */
    public BureaucratAllocationResult calculateAdministrativeBureaucratAllocation(
            ColonyDemographics demographics,
            SystemEconomy systemEconomy
    ) {
        long pop = demographics != null ? demographics.totalHeadcount() : 0L;
        long availableBureaucrats = demographics != null ? demographics.getHeadcount("bureaucrat") : 0L;

        if (pop <= 0) {
            return new BureaucratAllocationResult(availableBureaucrats, 0L, Map.of(), Map.of(), Map.of(), Map.of(), false);
        }

        double eduBase = systemEconomy != null ? systemEconomy.educationLevel() : 1.0;
        double lawBase = systemEconomy != null ? systemEconomy.lawAndOrderLevel() : 1.0;
        double healthBase = systemEconomy != null ? systemEconomy.healthAndWelfareLevel() : 1.0;
        double infraBase = systemEconomy != null ? systemEconomy.infrastructureLevel() : 1.0;
        double milBase = systemEconomy != null ? systemEconomy.planetaryMilitiaLevel() : 1.0;

        Map<String, Long> demand = new LinkedHashMap<>();
        demand.put("education", (long) Math.ceil(pop * ALPHA_EDUCATION * eduBase));
        demand.put("law_and_order", (long) Math.ceil(pop * ALPHA_LAW * lawBase));
        demand.put("health_and_welfare", (long) Math.ceil(pop * ALPHA_HEALTH * healthBase));
        demand.put("infrastructure", (long) Math.ceil(pop * ALPHA_INFRASTRUCTURE * infraBase));
        demand.put("military", (long) Math.ceil(pop * ALPHA_MILITARY * milBase));

        long totalDemand = demand.values().stream().mapToLong(Long::longValue).sum();
        boolean bottlenecked = availableBureaucrats < totalDemand;

        Map<String, Long> assigned = new LinkedHashMap<>();
        Map<String, Double> ratios = new LinkedHashMap<>();
        Map<String, Double> effEff = new LinkedHashMap<>();

        Map<String, Double> baseLevels = Map.of(
                "education", eduBase,
                "law_and_order", lawBase,
                "health_and_welfare", healthBase,
                "infrastructure", infraBase,
                "military", milBase
        );

        for (Map.Entry<String, Long> entry : demand.entrySet()) {
            String sector = entry.getKey();
            long secDemand = entry.getValue();
            long secAssigned;

            if (!bottlenecked) {
                secAssigned = secDemand;
            } else if (totalDemand > 0) {
                secAssigned = Math.round((double) availableBureaucrats * ((double) secDemand / (double) totalDemand));
            } else {
                secAssigned = 0L;
            }

            double ratio = secDemand > 0 ? Math.min(1.0, (double) secAssigned / (double) secDemand) : 1.0;
            double baseEff = baseLevels.getOrDefault(sector, 1.0);
            double effectiveEff = baseEff * (0.35 + 0.65 * ratio);

            assigned.put(sector, secAssigned);
            ratios.put(sector, ratio);
            effEff.put(sector, effectiveEff);
        }

        return new BureaucratAllocationResult(
                availableBureaucrats,
                totalDemand,
                Collections.unmodifiableMap(demand),
                Collections.unmodifiableMap(assigned),
                Collections.unmodifiableMap(ratios),
                Collections.unmodifiableMap(effEff),
                bottlenecked
        );
    }

    /**
     * Executes upward social mobility retraining pass on cohort fragments.
     * Retrains working-age low-complexity strata into higher-complexity professions.
     * Formula: Retrained = round(Headcount * Base Mobility Rate * E_eff,edu)
     *
     * @param demographics        current colony demographics
     * @param educationEfficiency effective education efficiency
     * @param baseMobilityRate    baseline mobility rate per turn (e.g. 0.02)
     * @return updated ColonyDemographics after retraining
     */
    public ColonyDemographics executeSocialMobilityPass(
            ColonyDemographics demographics,
            double educationEfficiency,
            double baseMobilityRate
    ) {
        if (demographics == null || demographics.cohorts().isEmpty() || educationEfficiency <= 0.0) {
            return demographics;
        }

        double rate = Math.min(0.20, Math.max(0.0, baseMobilityRate * educationEfficiency));
        List<CitizenCohort> updatedCohorts = new ArrayList<>();
        List<CitizenCohort> newlyTrained = new ArrayList<>();

        for (CitizenCohort cohort : demographics.cohorts()) {
            String prof = cohort.professionId().toLowerCase();
            boolean isLowComplexity = prof.equals("miner") || prof.equals("farmer")
                    || prof.equals("laborer") || prof.equals("industrial_worker");

            if (!isLowComplexity || cohort.headcount() <= 10) {
                updatedCohorts.add(cohort);
                continue;
            }

            long retrained = Math.round(cohort.headcount() * rate);
            if (retrained <= 0) {
                updatedCohorts.add(cohort);
                continue;
            }

            long remaining = cohort.headcount() - retrained;
            updatedCohorts.add(cohort.withHeadcount(remaining));

            // Distribute retrained into higher tiers:
            // technician (40%), engineer (25%), teacher (20%), medic (10%), scientist (5%)
            long techCount = Math.round(retrained * 0.40);
            long engCount = Math.round(retrained * 0.25);
            long teacherCount = Math.round(retrained * 0.20);
            long medicCount = Math.round(retrained * 0.10);
            long sciCount = retrained - (techCount + engCount + teacherCount + medicCount);

            String race = cohort.raceId();
            int age = cohort.ageBracket();

            if (techCount > 0) newlyTrained.add(new CitizenCohort(race, "technician", age, techCount));
            if (engCount > 0) newlyTrained.add(new CitizenCohort(race, "engineer", age, engCount));
            if (teacherCount > 0) newlyTrained.add(new CitizenCohort(race, "teacher", age, teacherCount));
            if (medicCount > 0) newlyTrained.add(new CitizenCohort(race, "medic", age, medicCount));
            if (sciCount > 0) newlyTrained.add(new CitizenCohort(race, "scientist", age, sciCount));
        }

        updatedCohorts.addAll(newlyTrained);
        return new ColonyDemographics(demographics.colonyId(), demographics.systemId(), updatedCohorts).consolidated();
    }

    private Map<String, Double> getDistributionProfile(ColonyFocus focus) {
        Map<String, Double> dist = new LinkedHashMap<>();
        switch (focus) {
            case MINING -> {
                dist.put("miner", 0.64);
                dist.put("technician", 0.16);
                dist.put("engineer", 0.08);
                dist.put("medic", 0.06);
                dist.put("bureaucrat", 0.04);
                dist.put("police", 0.02);
            }
            case AGRICULTURE -> {
                dist.put("farmer", 0.64);
                dist.put("technician", 0.16);
                dist.put("engineer", 0.08);
                dist.put("medic", 0.06);
                dist.put("bureaucrat", 0.04);
                dist.put("police", 0.02);
            }
            case INDUSTRIAL -> {
                dist.put("industrial_worker", 0.50);
                dist.put("technician", 0.20);
                dist.put("engineer", 0.12);
                dist.put("bureaucrat", 0.08);
                dist.put("medic", 0.06);
                dist.put("police", 0.04);
            }
            case RESEARCH -> {
                dist.put("scientist", 0.45);
                dist.put("technician", 0.25);
                dist.put("engineer", 0.15);
                dist.put("bureaucrat", 0.08);
                dist.put("medic", 0.05);
                dist.put("police", 0.02);
            }
            case MILITARY_OUTPOST -> {
                dist.put("soldier", 0.60);
                dist.put("technician", 0.15);
                dist.put("engineer", 0.10);
                dist.put("medic", 0.08);
                dist.put("bureaucrat", 0.04);
                dist.put("police", 0.03);
            }
            case BALANCED -> {
                dist.put("miner", 0.15);
                dist.put("farmer", 0.15);
                dist.put("industrial_worker", 0.15);
                dist.put("technician", 0.15);
                dist.put("engineer", 0.10);
                dist.put("teacher", 0.08);
                dist.put("medic", 0.07);
                dist.put("bureaucrat", 0.05);
                dist.put("police", 0.04);
                dist.put("scientist", 0.03);
                dist.put("soldier", 0.03);
            }
        }
        return dist;
    }
}
