package com.spaceconquest.engine.economy;

import com.spaceconquest.engine.Population;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HouseholdJobMobilityTest {
    @Test
    void unemployedWorkersRetrainGraduallyAndProfessionPersists() {
        HouseholdJobMobility mobility = new HouseholdJobMobility();
        List<Population> population = List.of(new Population("human", Map.of(30, 100L)));
        HouseholdAccount old = account("industrial_worker", 100);
        var first = mobility.distribute("earth", "sol", population, Map.of(old.key(), old),
                Map.of("engineer", 10L), List.of());
        assertEquals(100L, first.stream().mapToLong(c -> c.headcount()).sum());
        assertEquals(1L, first.stream().filter(c -> "engineer".equals(c.professionId()))
                .mapToLong(c -> c.headcount()).sum());

        HouseholdAccount worker = account("industrial_worker", 99);
        HouseholdAccount engineer = account("engineer", 1);
        var second = mobility.distribute("earth", "sol", population,
                Map.of(worker.key(), worker, engineer.key(), engineer),
                Map.of("engineer", 10L), List.of());
        assertEquals(2L, second.stream().filter(c -> "engineer".equals(c.professionId()))
                .mapToLong(c -> c.headcount()).sum());
    }

    private HouseholdAccount account(String profession, long headcount) {
        return new HouseholdAccount("earth", "sol", "emp", "human", profession,
                headcount, 0, 0, 0, 0, 0, Map.of(), 1, 1, 0, 0,
                HouseholdWellbeing.healthy(), HouseholdEmployment.none());
    }
}
