package com.spaceconquest.control.ai;

import com.spaceconquest.engine.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResearchOpportunityTest {
    @Test void prerequisitesLeadToAUsableApplicationInsteadOfCheaperUnimplementedFields() throws Exception {
        var wind = DataModelLoader.loadTechnologies().stream().flatMap(technology -> technology.applications().stream())
                .filter(app -> app.id().equals("wind_power")).findFirst().orElseThrow();
        var catalog = List.of(new Technology("unused", "Unused", "", 1, List.of(), List.of(), 1.0),
                new Technology("root", "Root", "", 1, List.of(), List.of(), 100.0),
                new Technology("power", "Power", "", 1, List.of("root"), List.of(wind), 200.0));
        for (var known : List.of(List.<String>of(), List.of("root"), List.of("root", "power"))) {
            var empire = new Empire("owner", "Owner", "human", "Individualist", 1000, 0, List.of(), List.of(), Map.of(), known, List.of());
            var state = GameState.builder().empires(List.of(empire)).build();
            var choice = ResearchOpportunities.choose(state, empire, catalog, Set.of(), new EmpireResearchNeeds(state, empire)).orElseThrow();
            assertEquals(known.isEmpty() ? "root" : known.size() == 1 ? "power" : "wind_power", choice.target());
            assertEquals(known.size() == 2, choice.application());
            assertEquals("wind_power", choice.goalApplicationId());
            assertTrue(!choice.application() ? choice.reason().contains("Prerequisite") : choice.reason().contains("Discovery"));
        }
    }
}
