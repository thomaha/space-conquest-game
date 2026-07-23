package com.spaceconquest.engine;

import java.util.Map;
import java.util.TreeMap;

public class PopulationProcessor {

    /**
     * Advances the population by a certain number of years.
     * 
     * @param population The current population.
     * @param race The race properties.
     * @param years The number of years to advance.
     * @return A new Population instance representing the state after the given time.
     */
    public Population advanceYears(Population population, Race race, int years) {
        if (years <= 0) return population;

        Map<Integer, Long> currentGroups = new TreeMap<>(population.ageGroups());
        Map<Integer, Long> nextGroups = new TreeMap<>();

        // 1. Aging and Mortality
        // We iterate through existing groups, add years to their age, 
        // and apply some death rate.
        for (Map.Entry<Integer, Long> entry : currentGroups.entrySet()) {
            int oldAge = entry.getKey();
            long count = entry.getValue();
            int newAge = oldAge + years;

            // Simple mortality model: 
            // - Base death rate increases with age.
            // - After retirement age, death rate increases significantly.
            // - 100% death at some maximum age (e.g. 120 for humans, or based on retirement age)
            
            double survivalRate = calculateSurvivalRate(oldAge, race, years);
            long survivors = (long) (count * survivalRate);

            if (survivors > 0) {
                nextGroups.put(newAge, nextGroups.getOrDefault(newAge, 0L) + survivors);
            }
        }

        // 2. Births
        long newborns = calculateBirths(currentGroups, race, years);
        if (newborns > 0) {
            nextGroups.put(0, nextGroups.getOrDefault(0, 0L) + newborns);
        }

        // 3. Consolidate age groups to prevent Map from growing indefinitely
        // For simplicity, let's group by 5 or 10 years if needed, 
        // but for now, let's keep it as is and see.
        // Actually, let's keep it consistent with the input format (multiples of 20 or similar if that's the convention)
        // or just let it be granular.
        
        return new Population(population.raceId(), nextGroups);
    }

    private double calculateSurvivalRate(int age, Race race, int years) {
        // Base survival rate: 99% per year for young/middle age
        // Decreases as they approach and pass retirement age
        double annualSurvival = 0.995; 
        
        if (age > race.retirementAge()) {
            double overage = age - race.retirementAge();
            annualSurvival -= 0.01 * (overage / 10.0); // Simple linear decrease
        }
        
        // Hard limit: Nobody lives forever. 
        if (age >= race.retirementAge() * 2) {
            annualSurvival = 0.0;
        }

        return Math.pow(Math.max(0, annualSurvival), years);
    }

    private long calculateBirths(Map<Integer, Long> ageGroups, Race race, int years) {
        if ("Hive mind".equalsIgnoreCase(race.societyStructure())) {
            // Hive societies reproduce linearly with respect to how many queens they have
            // For now, let's assume 1 queen per 1 million population? 
            // Or we need a way to track queens. 
            // Let's assume some default birth rate for now if queens aren't explicitly tracked.
            long totalPop = ageGroups.values().stream().mapToLong(Long::longValue).sum();
            return (long) (totalPop * 0.02 * years); // 2% growth per year for hive
        } else {
            // Other societies reproduce with respect to how many they are in fertile age
            long fertilePop = 0;
            for (Map.Entry<Integer, Long> entry : ageGroups.entrySet()) {
                int age = entry.getKey();
                if (age >= race.fertileAgeStart() && age <= race.fertileAgeEnd()) {
                    fertilePop += entry.getValue();
                }
            }
            // Base birth rate: 0.05 per year per person in fertile age group
            return (long) (fertilePop * 0.05 * years);
        }
    }
}
