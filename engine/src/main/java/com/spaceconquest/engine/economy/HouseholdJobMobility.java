package com.spaceconquest.engine.economy;

import com.spaceconquest.engine.Population;
import com.spaceconquest.engine.Profession;
import com.spaceconquest.engine.demographics.CitizenCohort;
import com.spaceconquest.engine.demographics.ColonyFocus;
import com.spaceconquest.engine.industry.IndustrialFacility;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Carries profession shares forward and retrains a small share of unemployed workers for vacancies. */
public final class HouseholdJobMobility {
    private static final double DAILY_RETRAINING_SHARE = 0.01;

    public List<CitizenCohort> distribute(String bodyId, String systemId,
                                         List<Population> populations,
                                         Map<String, HouseholdAccount> previous,
                                         Map<String, Long> publicJobs,
                                         List<IndustrialFacility> facilities) {
        List<CitizenCohort> cohorts = new ArrayList<>();
        for (Population population : populations) {
            List<HouseholdAccount> prior = previous.values().stream()
                    .filter(account -> bodyId.equals(account.bodyId())
                            && population.raceId().equals(account.raceId())
                            && account.headcount() > 0)
                    .sorted(Comparator.comparing(HouseholdAccount::professionId)).toList();
            long oldTotal = prior.stream().mapToLong(HouseholdAccount::headcount).sum();
            if (oldTotal <= 0 || oldTotal < population.totalCount() * 0.5
                    || oldTotal > population.totalCount() * 2.0) {
                cohorts.addAll(population.toDemographics(bodyId, systemId, ColonyFocus.BALANCED).cohorts());
                continue;
            }
            for (var age : population.ageGroups().entrySet()) {
                long unassigned = age.getValue();
                for (int index = 0; index < prior.size(); index++) {
                    HouseholdAccount account = prior.get(index);
                    long count = index == prior.size() - 1 ? unassigned
                            : Math.min(unassigned, Math.round((double) age.getValue()
                            * account.headcount() / oldTotal));
                    if (count > 0) cohorts.add(new CitizenCohort(population.raceId(),
                            account.professionId(), age.getKey(), count));
                    unassigned -= count;
                }
            }
        }
        return retrain(bodyId, cohorts, publicJobs, facilities);
    }

    private List<CitizenCohort> retrain(String bodyId, List<CitizenCohort> cohorts,
                                       Map<String, Long> publicJobs,
                                       List<IndustrialFacility> facilities) {
        Map<String, Long> jobs = new HashMap<>(publicJobs);
        for (IndustrialFacility facility : facilities) {
            if (bodyId.equals(facility.planetId()) && facility.tier() > 0) {
                jobs.merge(facility.workerProfessionId(),
                        (long) Math.max(0, facility.allocatedWorkers()), Long::sum);
            }
        }
        Map<String, Long> workers = new HashMap<>();
        for (CitizenCohort cohort : cohorts) {
            if (cohort.ageBracket() >= 18 && cohort.ageBracket() < 65)
                workers.merge(cohort.professionId(), cohort.headcount(), Long::sum);
        }
        Map<String, Long> trainingBudget = new HashMap<>();
        workers.forEach((profession, count) -> {
            long surplus = Math.max(0, count - jobs.getOrDefault(profession, 0L));
            if (surplus > 0) trainingBudget.put(profession,
                    Math.max(1L, (long) Math.floor(surplus * DAILY_RETRAINING_SHARE)));
        });
        List<CitizenCohort> updated = new ArrayList<>(cohorts);
        List<String> targets = jobs.keySet().stream()
                .sorted(Comparator.comparingDouble(Profession::getBaseWageForProfession).reversed())
                .toList();
        for (String target : targets) {
            long vacancy = Math.max(0, jobs.get(target) - workers.getOrDefault(target, 0L));
            if (vacancy == 0) continue;
            for (int index = 0; index < updated.size() && vacancy > 0; index++) {
                CitizenCohort source = updated.get(index);
                if (source.ageBracket() < 18 || source.ageBracket() >= 65
                        || source.professionId().equals(target)) continue;
                long training = trainingBudget.getOrDefault(source.professionId(), 0L);
                if (training == 0) continue;
                long moved = Math.min(vacancy, Math.min(source.headcount(), training));
                updated.set(index, source.withHeadcount(source.headcount() - moved));
                updated.add(new CitizenCohort(source.raceId(), target,
                        source.ageBracket(), moved));
                workers.merge(source.professionId(), -moved, Long::sum);
                workers.merge(target, moved, Long::sum);
                trainingBudget.merge(source.professionId(), -moved, Long::sum);
                vacancy -= moved;
            }
        }
        return updated.stream().filter(cohort -> cohort.headcount() > 0).toList();
    }
}
