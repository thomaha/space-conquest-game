package com.spaceconquest.engine.technology;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.demographics.ColonyFocus;
import com.spaceconquest.engine.economy.HouseholdAccount;
import java.util.*;

/** Research uses settled public scientist payroll, bounded by current controlled physical population. */
public final class ResearchWorkforce {
    public record Scientists(String raceId, long count, double training) {}
    private record Body(String system, List<Population> populations) {}
    private ResearchWorkforce() {}
    public static List<Scientists> available(GameState state, Empire empire) {
        var bodies = bodies(state, empire); var result = new ArrayList<Scientists>();
        boolean hive = empire.societyStructure() != null && empire.societyStructure().toLowerCase(Locale.ROOT).contains("hive");
        if (hive) {
            var remaining = new HashMap<String, Long>();
            state.systemEconomies().stream().filter(value -> empire.id().equals(value.empireId()))
                    .forEach(value -> remaining.put(value.systemId(), Math.max(0, value.employedScientists())));
            bodies.forEach((id, body) -> body.populations().forEach(population -> {
                long trained = population.toDemographics(id, body.system(), ColonyFocus.BALANCED).cohorts().stream()
                        .filter(cohort -> "scientist".equals(cohort.professionId()) && cohort.ageBracket() >= 18 && cohort.ageBracket() < 65)
                        .mapToLong(cohort -> cohort.headcount()).sum();
                long count = Math.min(trained, remaining.getOrDefault(body.system(), 0L));
                remaining.merge(body.system(), -count, Long::sum);
                if (count > 0) result.add(new Scientists(population.raceId(), count, training(state, empire.id(), body.system())));
            }));
        } else {
            var seen = new HashSet<String>();
            for (HouseholdAccount account : state.householdAccounts()) {
                if (!empire.id().equals(account.empireId()) || !"scientist".equals(account.professionId())
                        || account.wageIncomeCredits() <= 0 || !seen.add(account.key())) continue;
                var body = bodies.get(account.bodyId());
                if (body == null || !body.system().equals(account.systemId())) continue;
                if (state.systemEconomies().stream().noneMatch(economy -> empire.id().equals(economy.empireId())
                        && body.system().equals(economy.systemId()) && economy.educationAllocation() > 0
                        && economy.totalBudgetCredits() > 0 && economy.employedScientists() > 0)) continue;
                long livingWorkers = body.populations().stream().filter(population -> account.raceId().equals(population.raceId()))
                        .flatMap(population -> population.ageGroups().entrySet().stream())
                        .filter(age -> age.getKey() >= 18 && age.getKey() < 65).mapToLong(Map.Entry::getValue).sum();
                long count = Math.min(Math.min(account.headcount(), livingWorkers), account.employment().publicWorkers());
                if (count > 0) result.add(new Scientists(account.raceId(), count, training(state, empire.id(), body.system())));
            }
        }
        return List.copyOf(result);
    }
    public static long capacity(GameState state, Empire empire) {
        return available(state, empire).stream().mapToLong(Scientists::count).sum();
    }
    private static double training(GameState state, String empire, String system) {
        return state.systemEconomies().stream().filter(value -> empire.equals(value.empireId()) && system.equals(value.systemId()))
                .mapToDouble(value -> Math.max(0, value.educationLevel())).findFirst().orElse(1);
    }
    private static Map<String, Body> bodies(GameState state, Empire empire) {
        var result = new LinkedHashMap<String, Body>();
        for (var system : state.solarSystems()) {
            if (!empire.controlledSystemIds().contains(system.id())) continue;
            for (var planet : system.planets()) {
                result.put(planet.id(), new Body(system.id(), planet.populations()));
                for (var moon : planet.moons()) result.put(moon.id(), new Body(system.id(), moon.populations()));
            }
            for (var belt : system.asteroidBelts()) result.put(belt.id(), new Body(system.id(), belt.populations()));
            for (var station : state.orbitalStations()) if (system.id().equals(station.systemId())
                    && (empire.id().equals(station.ownerEntityId()) || state.corporations().stream().anyMatch(corporation ->
                    empire.id().equals(corporation.empireId()) && station.ownerEntityId().equals(corporation.id()))))
                result.put(station.id(), new Body(system.id(), station.populations()));
        }
        return result;
    }
}
