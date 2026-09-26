package com.spaceconquest.engine;

import com.spaceconquest.engine.demographics.CitizenCohort;
import com.spaceconquest.engine.demographics.ColonyDemographics;
import com.spaceconquest.engine.habitation.BiochemicalConsumptionResult;
import com.spaceconquest.engine.habitation.DemographicWorkforceResult;
import com.spaceconquest.engine.habitation.PassengerLogisticsResult;
import com.spaceconquest.engine.ship.ShipInstance;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class PopulationProcessor {

    /**
     * Calculates the effective natural lifespan of a race taking into account unlocked gene technologies.
     */
    public int calculateEffectiveNaturalLifespan(Race race, List<String> unlockedTechIds) {
        if (race == null) return 85;
        if ("synthetic_machine".equalsIgnoreCase(race.id()) || race.naturalLifespan() >= 9000) {
            return race.naturalLifespan();
        }

        int bonusYears = 0;
        if (unlockedTechIds != null) {
            if (unlockedTechIds.contains("gene_sequencing")) bonusYears += 10;
            if (unlockedTechIds.contains("gene_editing")) bonusYears += 20;
            if (unlockedTechIds.contains("gene_therapy")) bonusYears += 30;
        }

        return race.naturalLifespan() + bonusYears;
    }

    /**
     * Evaluates per-turn biochemical nutrient and life support consumption for a population cohort.
     */
    public BiochemicalConsumptionResult calculateBiochemicalConsumption(
            Population population,
            Race race,
            Map<String, Double> localInventory,
            boolean hasDiverseFood
    ) {
        if (population == null || race == null) {
            return new BiochemicalConsumptionResult("", Map.of(), false, "", 0.0, 0.0, 1.0);
        }

        long totalPop = population.ageGroups().values().stream().mapToLong(Long::longValue).sum();
        if (totalPop <= 0) {
            return new BiochemicalConsumptionResult(race.id(), Map.of(), false, "", 0.0, 0.0, 1.0);
        }

        Map<String, Double> required = calculateDailyNutrientRequirements(totalPop, race);
        Map<String, Double> inventory = localInventory != null ? localInventory : Map.of();

        boolean isDeficit = false;
        String missing = "";
        Map<String, Double> consumed = new HashMap<>();

        for (Map.Entry<String, Double> entry : required.entrySet()) {
            String resId = entry.getKey();
            double reqAmount = entry.getValue();
            double available = inventory.getOrDefault(resId, 0.0);
            if (available < reqAmount) {
                isDeficit = true;
                missing = resId;
                consumed.put(resId, available);
            } else {
                consumed.put(resId, reqAmount);
            }
        }

        double happinessMod = 0.0;
        double crimeMod = 0.0;
        double growthMod = 1.0;

        if (isDeficit) {
            happinessMod = -0.35;
            crimeMod = 0.20;
            growthMod = 0.0; // Starvation freezes growth
        } else {
            if ("Diverse".equalsIgnoreCase(race.nutrientSpreadRequirement())) {
                if (hasDiverseFood) {
                    happinessMod = 0.15;
                    crimeMod = -0.05;
                    growthMod = 1.25;
                } else {
                    happinessMod = -0.15;
                    crimeMod = 0.08;
                    growthMod = 0.85;
                }
            } else {
                happinessMod = 0.05;
                growthMod = 1.05;
            }
        }

        return new BiochemicalConsumptionResult(race.id(), consumed, isDeficit, missing, happinessMod, crimeMod, growthMod);
    }

    /** Calculates biological requirements before accounting for the body's atmosphere. */
    public Map<String, Double> calculateDailyNutrientRequirements(long headcount, Race race) {
        if (race == null || headcount <= 0) return Map.of();
        Map<String, Double> required = new HashMap<>();

        double popScale = headcount / 1000.0; // Per 1,000 citizens

        String nutrientType = race.nutrientType() != null ? race.nutrientType().toUpperCase() : "ORGANIC";
        String chem = race.chemicalComposition() != null ? race.chemicalComposition().toLowerCase() : "";

        if ("ORGANIC".equals(nutrientType) || chem.contains("carbon")) {
            required.put("oxygen_gas", 50.0 * popScale);
            required.put("food_matrix", 100.0 * popScale);
        } else if ("ROCK".equals(nutrientType) || chem.contains("silicon")) {
            required.put("silicates", 150.0 * popScale);
            required.put("limestone", 50.0 * popScale);
        } else if ("GAS".equals(nutrientType) || chem.contains("nitrogen")) {
            required.put("nitrogen_gas", 50.0 * popScale);
            required.put("methane_ice", 100.0 * popScale);
        } else if ("ELECTRICITY".equals(nutrientType) || "synthetic_machine".equalsIgnoreCase(race.id())) {
            required.put("refined_copper", 10.0 * popScale);
            required.put("refined_silicon", 10.0 * popScale);
            required.put("silver", 5.0 * popScale);
        } else if ("METAL".equals(nutrientType) || "plasma_anomaly".equalsIgnoreCase(race.id())) {
            required.put("hydrogen_gas", 100.0 * popScale);
            required.put("helium_3", 20.0 * popScale);
        }

        return required;
    }

    /** Only known breathable atmospheres supply ambient oxygen to surface residents. */
    public static boolean hasAmbientBreathableOxygen(String atmosphere) {
        return "nitrogen_oxygen".equalsIgnoreCase(atmosphere)
                || "BREATHABLE".equalsIgnoreCase(atmosphere);
    }

    /** Excludes free ambient oxygen from household market demand on breathable bodies. */
    public Map<String, Double> calculateDailyMarketRequirements(long headcount, Race race, String atmosphere) {
        Map<String, Double> required = calculateDailyNutrientRequirements(headcount, race);
        if (hasAmbientBreathableOxygen(atmosphere)) required.remove("oxygen_gas");
        return required;
    }

    /**
     * Evaluates biochemical consumption for an entire colony demographic composition of cohort fragments.
     */
    public BiochemicalConsumptionResult calculateBiochemicalConsumption(
            ColonyDemographics demographics,
            Race race,
            Map<String, Double> localInventory,
            boolean hasDiverseFood
    ) {
        if (demographics == null || race == null) {
            return new BiochemicalConsumptionResult("", Map.of(), false, "", 0.0, 0.0, 1.0);
        }
        Map<Integer, Long> ageGroups = new HashMap<>();
        for (CitizenCohort cohort : demographics.cohorts()) {
            ageGroups.put(cohort.ageBracket(), ageGroups.getOrDefault(cohort.ageBracket(), 0L) + cohort.headcount());
        }
        Population pop = new Population(race.id(), ageGroups);
        return calculateBiochemicalConsumption(pop, race, localInventory, hasDiverseFood);
    }

    /**
     * Calculates active workers, retired cohorts and welfare costs for a profession and species.
     */
    public DemographicWorkforceResult calculateWorkforceAndRetirement(
            Population population,
            Race race,
            Profession profession,
            List<String> unlockedTechIds
    ) {
        if (population == null || race == null) {
            return new DemographicWorkforceResult("", "", 0L, 0L, 0, 0, 0.0);
        }

        String profId = profession != null ? profession.id() : "unassigned";
        int naturalLifespan = calculateEffectiveNaturalLifespan(race, unlockedTechIds);

        boolean isHiveMind = "Hive Mind".equalsIgnoreCase(race.societyStructure())
                || "Hive mind".equalsIgnoreCase(race.societyStructure());
        boolean isSynthetic = "synthetic_machine".equalsIgnoreCase(race.id()) || naturalLifespan >= 9000;

        if (isHiveMind || isSynthetic) {
            long total = population.ageGroups().values().stream().mapToLong(Long::longValue).sum();
            return new DemographicWorkforceResult(race.id(), profId, total, 0L, naturalLifespan, naturalLifespan, 0.0);
        }

        int retirementAge = profession != null
                ? profession.calculateRetirementAge(naturalLifespan)
                : (int) (naturalLifespan * 0.75);

        long activeWorkers = 0L;
        long retiredCitizens = 0L;

        for (Map.Entry<Integer, Long> entry : population.ageGroups().entrySet()) {
            int age = entry.getKey();
            long count = entry.getValue();
            if (age < retirementAge) {
                activeWorkers += count;
            } else {
                retiredCitizens += count;
            }
        }

        double welfareCost = (retiredCitizens / 1000.0) * 25.0; // 25 credits per 1,000 retirees

        return new DemographicWorkforceResult(
                race.id(), profId, activeWorkers, retiredCitizens, retirementAge, naturalLifespan, welfareCost
        );
    }

    /**
     * Calculates active workers, retired cohorts and welfare costs across all single-education cohort fragments in a colony.
     */
    public DemographicWorkforceResult calculateWorkforceAndRetirement(
            ColonyDemographics demographics,
            Race race,
            Map<String, Profession> professionsCatalog,
            List<String> unlockedTechIds
    ) {
        if (demographics == null || race == null) {
            return new DemographicWorkforceResult("", "all", 0L, 0L, 0, 0, 0.0);
        }

        int naturalLifespan = calculateEffectiveNaturalLifespan(race, unlockedTechIds);
        boolean isHiveMind = "Hive Mind".equalsIgnoreCase(race.societyStructure())
                || "Hive mind".equalsIgnoreCase(race.societyStructure());
        boolean isSynthetic = "synthetic_machine".equalsIgnoreCase(race.id()) || naturalLifespan >= 9000;

        if (isHiveMind || isSynthetic) {
            long total = demographics.totalHeadcount();
            return new DemographicWorkforceResult(race.id(), "all", total, 0L, naturalLifespan, naturalLifespan, 0.0);
        }

        long totalActive = 0L;
        long totalRetired = 0L;

        for (CitizenCohort cohort : demographics.cohorts()) {
            Profession prof = professionsCatalog != null ? professionsCatalog.get(cohort.professionId().toLowerCase()) : null;
            int retAge = prof != null
                    ? prof.calculateRetirementAge(naturalLifespan)
                    : (int) (naturalLifespan * 0.75);

            if (cohort.ageBracket() < retAge) {
                totalActive += cohort.headcount();
            } else {
                totalRetired += cohort.headcount();
            }
        }

        double welfareCost = (totalRetired / 1000.0) * 25.0;
        int defaultRetAge = (int) (naturalLifespan * 0.75);

        return new DemographicWorkforceResult(
                race.id(), "all", totalActive, totalRetired, defaultRetAge, naturalLifespan, welfareCost
        );
    }

    /**
     * Evaluates in-transit life support consumption for passengers aboard a ship instance.
     */
    public PassengerLogisticsResult processPassengerLifeSupport(
            ShipInstance ship,
            Race race,
            Map<String, Double> shipInventory
    ) {
        if (ship == null || ship.passengerCount() <= 0) {
            return new PassengerLogisticsResult(
                    ship != null ? ship.id() : "", 0, ShipInstance.MODE_CRYOGENIC_STASIS, Map.of(), false, 0, 1.0
            );
        }

        String mode = ship.transitMode() != null ? ship.transitMode() : ShipInstance.MODE_CRYOGENIC_STASIS;

        if (ShipInstance.MODE_CRYOGENIC_STASIS.equalsIgnoreCase(mode)) {
            // Suspended animation: 0 food, 0 oxygen consumed
            return new PassengerLogisticsResult(
                    ship.id(), ship.passengerCount(), ShipInstance.MODE_CRYOGENIC_STASIS, Map.of(), false, 0, 1.0
            );
        }

        // Conscious transit: consumes food and oxygen per turn
        Map<String, Double> supplies = shipInventory != null ? shipInventory : ship.storedCargoKg();
        double oxygenReq = ship.passengerCount() * 0.05;
        double foodReq = ship.passengerCount() * 0.10;

        double availO2 = supplies.getOrDefault("oxygen_gas", 0.0);
        double availFood = supplies.getOrDefault("food_matrix", 0.0);

        boolean deficit = availO2 < oxygenReq || availFood < foodReq;
        int casualties = 0;
        double happiness = 0.90;

        Map<String, Double> consumed = new HashMap<>();
        if (deficit) {
            casualties = Math.max(1, (int) (ship.passengerCount() * 0.15));
            happiness = 0.10;
            consumed.put("oxygen_gas", availO2);
            consumed.put("food_matrix", availFood);
        } else {
            consumed.put("oxygen_gas", oxygenReq);
            consumed.put("food_matrix", foodReq);
        }

        return new PassengerLogisticsResult(
                ship.id(), ship.passengerCount(), ShipInstance.MODE_CONSCIOUS, consumed, deficit, casualties, happiness
        );
    }

    /**
     * Advances the population by a certain number of years.
     */
    public Population advanceYears(Population population, Race race, int years) {
        return advanceYears(population, race, years, 1, List.of());
    }

    /**
     * Advances the population by a certain number of years with queen counts and tech bonuses.
     */
    public Population advanceYears(
            Population population,
            Race race,
            int years,
            int activeQueens,
            List<String> unlockedTechIds
    ) {
        if (years <= 0 || population == null || race == null) return population;

        int effectiveLifespan = calculateEffectiveNaturalLifespan(race, unlockedTechIds);
        Map<Integer, Long> currentGroups = new TreeMap<>(population.ageGroups());
        Map<Integer, Long> nextGroups = new TreeMap<>();

        // 1. Aging and Mortality
        for (Map.Entry<Integer, Long> entry : currentGroups.entrySet()) {
            int oldAge = entry.getKey();
            long count = entry.getValue();
            int newAge = oldAge + years;

            double survivalRate = calculateSurvivalRate(oldAge, effectiveLifespan, years);
            long survivors = (long) (count * survivalRate);

            if (survivors > 0) {
                nextGroups.put(newAge, nextGroups.getOrDefault(newAge, 0L) + survivors);
            }
        }

        // 2. Births / Queen Reproduction
        long newborns = calculateBirths(currentGroups, race, years, activeQueens);
        if (newborns > 0) {
            nextGroups.put(0, nextGroups.getOrDefault(0, 0L) + newborns);
        }

        return new Population(population.raceId(), nextGroups);
    }

    private double calculateSurvivalRate(int age, int naturalLifespan, int years) {
        if (naturalLifespan >= 9000) return 1.0; // Synthetics don't age
        if (age + years >= naturalLifespan * 2) {
            return 0.0;
        }

        double annualSurvival = 0.995;
        if (age > naturalLifespan) {
            double overage = age - naturalLifespan;
            annualSurvival -= 0.01 * (overage / 10.0);
        }

        return Math.pow(Math.max(0, annualSurvival), years);
    }

    private long calculateBirths(Map<Integer, Long> ageGroups, Race race, int years, int activeQueens) {
        if ("Hive mind".equalsIgnoreCase(race.societyStructure())
                || "Hive Mind".equalsIgnoreCase(race.societyStructure())) {
            int queens = Math.max(1, activeQueens);
            return (long) (queens * 1000L * years);
        } else if ("synthetic_machine".equalsIgnoreCase(race.id())) {
            return 0L; // Synthetics require industrial manufacturing commands
        } else {
            long fertilePop = 0;
            for (Map.Entry<Integer, Long> entry : ageGroups.entrySet()) {
                int age = entry.getKey();
                if (age >= race.fertileAgeStart() && age <= race.fertileAgeEnd()) {
                    fertilePop += entry.getValue();
                }
            }
            return (long) (fertilePop * 0.05 * years);
        }
    }
}
