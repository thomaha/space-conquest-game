package com.spaceconquest.engine.demographics;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable demographic composition of a colony, settlement, or space station.
 * Comprises multiple single-education citizen cohort fragments spanning diverse professions.
 *
 * @param colonyId unique colony or celestial body identifier
 * @param systemId solar system identifier
 * @param cohorts  immutable list of single-education citizen cohort fragments
 */
public record ColonyDemographics(
        @JsonProperty("colonyId") String colonyId,
        @JsonProperty("systemId") String systemId,
        @JsonProperty("cohorts") List<CitizenCohort> cohorts
) {
    public ColonyDemographics {
        Objects.requireNonNull(colonyId, "colonyId cannot be null");
        systemId = systemId != null ? systemId : "";
        cohorts = cohorts != null ? List.copyOf(cohorts) : List.of();
    }

    /**
     * Calculates the total population headcount across all cohort fragments.
     */
    public long totalHeadcount() {
        long total = 0L;
        for (CitizenCohort c : cohorts) {
            total += c.headcount();
        }
        return total;
    }

    /**
     * Calculates the total population in standard cohort units (1.0 = 100,000 citizens).
     */
    public double totalCohortUnits() {
        return (double) totalHeadcount() / (double) CitizenCohort.STANDARD_COHORT_SIZE;
    }

    /**
     * Returns the total headcount of citizens trained in the specified profession.
     */
    public long getHeadcount(String professionId) {
        if (professionId == null) return 0L;
        long total = 0L;
        for (CitizenCohort c : cohorts) {
            if (professionId.equalsIgnoreCase(c.professionId())) {
                total += c.headcount();
            }
        }
        return total;
    }

    /**
     * Returns the fraction of standard cohort units for the specified profession.
     */
    public double getCohortUnits(String professionId) {
        return (double) getHeadcount(professionId) / (double) CitizenCohort.STANDARD_COHORT_SIZE;
    }

    /**
     * Aggregates total headcount by profession identifier.
     */
    public Map<String, Long> getHeadcountByProfession() {
        Map<String, Long> map = new HashMap<>();
        for (CitizenCohort c : cohorts) {
            map.put(c.professionId(), map.getOrDefault(c.professionId(), 0L) + c.headcount());
        }
        return Collections.unmodifiableMap(map);
    }

    /**
     * Aggregates total cohort units by profession identifier.
     */
    public Map<String, Double> getCohortUnitsByProfession() {
        Map<String, Double> map = new HashMap<>();
        for (Map.Entry<String, Long> entry : getHeadcountByProfession().entrySet()) {
            map.put(entry.getKey(), (double) entry.getValue() / (double) CitizenCohort.STANDARD_COHORT_SIZE);
        }
        return Collections.unmodifiableMap(map);
    }

    /**
     * Returns all cohort fragments matching the specified profession.
     */
    public List<CitizenCohort> getCohortsForProfession(String professionId) {
        if (professionId == null) return List.of();
        List<CitizenCohort> result = new ArrayList<>();
        for (CitizenCohort c : cohorts) {
            if (professionId.equalsIgnoreCase(c.professionId())) {
                result.add(c);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Calculates staffing efficiency for a single profession.
     */
    public double calculateStaffingEfficiency(String professionId, long requiredHeadcount) {
        if (requiredHeadcount <= 0) {
            return 1.0;
        }
        long available = getHeadcount(professionId);
        return Math.min(1.0, (double) available / (double) requiredHeadcount);
    }

    /**
     * Calculates operational efficiency across multiple required professions using the bottleneck formula:
     * Efficiency = min_p ( Assigned Headcount_p / Required Headcount_p, 1.0 )
     *
     * @param requiredProfessions map of professionId -> required personnel count
     * @return overall facility operational efficiency (0.0 to 1.0)
     */
    public double calculateMultiProfessionEfficiency(Map<String, Long> requiredProfessions) {
        if (requiredProfessions == null || requiredProfessions.isEmpty()) {
            return 1.0;
        }
        double minRatio = 1.0;
        for (Map.Entry<String, Long> entry : requiredProfessions.entrySet()) {
            String profId = entry.getKey();
            long required = entry.getValue();
            if (required <= 0) continue;
            long available = getHeadcount(profId);
            double ratio = (double) available / (double) required;
            if (ratio < minRatio) {
                minRatio = ratio;
            }
        }
        return Math.min(1.0, Math.max(0.0, minRatio));
    }

    /**
     * Computes individual staffing ratios for each required profession.
     */
    public Map<String, Double> calculateStaffingRatios(Map<String, Long> requiredProfessions) {
        if (requiredProfessions == null || requiredProfessions.isEmpty()) {
            return Map.of();
        }
        Map<String, Double> ratios = new HashMap<>();
        for (Map.Entry<String, Long> entry : requiredProfessions.entrySet()) {
            String profId = entry.getKey();
            long required = entry.getValue();
            if (required <= 0) {
                ratios.put(profId, 1.0);
            } else {
                long available = getHeadcount(profId);
                ratios.put(profId, (double) available / (double) required);
            }
        }
        return Collections.unmodifiableMap(ratios);
    }

    /**
     * Identifies the bottleneck profession with the lowest staffing ratio among required professions.
     */
    public String findBottleneckProfession(Map<String, Long> requiredProfessions) {
        if (requiredProfessions == null || requiredProfessions.isEmpty()) {
            return null;
        }
        String bottleneck = null;
        double minRatio = Double.MAX_VALUE;
        for (Map.Entry<String, Long> entry : requiredProfessions.entrySet()) {
            String profId = entry.getKey();
            long required = entry.getValue();
            if (required <= 0) continue;
            long available = getHeadcount(profId);
            double ratio = (double) available / (double) required;
            if (ratio < minRatio) {
                minRatio = ratio;
                bottleneck = profId;
            }
        }
        return bottleneck;
    }

    /**
     * Calculates the total gross wage bill across all cohort fragments.
     */
    public double calculateTotalWageBill(Map<String, Double> wageByProfession) {
        double total = 0.0;
        for (CitizenCohort c : cohorts) {
            double wage = wageByProfession != null ? wageByProfession.getOrDefault(c.professionId(), 10.0) : 10.0;
            total += c.calculateWageBill(wage);
        }
        return total;
    }

    /**
     * Calculates the total localized income tax collected across all cohort fragments.
     */
    public double calculateTotalIncomeTax(Map<String, Double> wageByProfession, double systemTaxRate) {
        return calculateTotalWageBill(wageByProfession) * Math.max(0.0, systemTaxRate);
    }

    /**
     * Returns a new ColonyDemographics instance with an added or merged cohort fragment.
     */
    public ColonyDemographics withAddedCohort(CitizenCohort newCohort) {
        if (newCohort == null || newCohort.headcount() <= 0) {
            return this;
        }
        List<CitizenCohort> updated = new ArrayList<>();
        boolean merged = false;
        for (CitizenCohort c : cohorts) {
            if (!merged && c.canMerge(newCohort)) {
                updated.add(c.merge(newCohort));
                merged = true;
            } else {
                updated.add(c);
            }
        }
        if (!merged) {
            updated.add(newCohort);
        }
        return new ColonyDemographics(colonyId, systemId, updated);
    }

    /**
     * Consolidates all compatible cohort fragments sharing race, profession and age.
     */
    public ColonyDemographics consolidated() {
        Map<String, CitizenCohort> map = new HashMap<>();
        for (CitizenCohort c : cohorts) {
            String key = c.raceId() + "_" + c.professionId() + "_" + c.ageBracket();
            if (map.containsKey(key)) {
                map.put(key, map.get(key).merge(c));
            } else {
                map.put(key, c);
            }
        }
        return new ColonyDemographics(colonyId, systemId, new ArrayList<>(map.values()));
    }
}
