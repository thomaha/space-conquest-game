package com.spaceconquest.control;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.*;
import java.util.*;

/** Physical adult population and actual household payroll, rather than scientist quota fixtures. */
final class ResearchTestWorld {
    private ResearchTestWorld() {}
    static GameState staffed(GameState state) {
        var old = state.empires().getFirst();
        var empire = new Empire(old.id(), old.name(), old.raceId(), old.societyStructure(), old.treasuryCredits(), old.corporateTaxRate(),
                List.of("sol"), old.ministries(), old.systemGovernorAssignments(), old.unlockedTechIds(), old.activeShipDesignIds());
        var planet = new Planet("research-world", "Research world", "", 1, 9.81, 1, 0, 12742,
                "terrestrial", "breathable", true, 1, List.of(), List.of(), List.of(new Population(old.raceId(), Map.of(25, 100000L))));
        var system = new SolarSystem("sol", "Sol", "", 0, 0, 0, 1, 1, "Yellow", List.of(planet), List.of());
        var economy = new SystemEconomy("sol", old.id(), .2, .2, .2, .2, .2, 100000, 0,
                1, 1, 1, 1, 1, 50, 30, 15, 10, 20, 30, 25, 100, .1);
        var physical = state.toBuilder().empires(List.of(empire)).solarSystems(List.of(system)).systemEconomies(List.of(economy)).build();
        var payroll = new HouseholdEconomyProcessor().process(physical, List.of());
        return physical.toBuilder().householdAccounts(payroll.householdAccounts()).empires(payroll.empires()).build();
    }
}
