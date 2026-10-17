package com.spaceconquest.engine.scenario;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.technology.ApplicationProduction;
import java.util.*;

/** A generated empire already knows the catalog applications of its prebuilt opening industry. */
public final class StartingApplicationKnowledge {
    private StartingApplicationKnowledge() {}
    public static Empire forAssets(Empire empire, List<IndustrialFacility> facilities, List<Corporation> corporations) {
        Set<String> owners = new HashSet<>(); owners.add(empire.id());
        corporations.stream().filter(corporation -> empire.id().equals(corporation.empireId()))
                .forEach(corporation -> owners.add(corporation.id()));
        try {
            var catalog = DataModelLoader.loadTechnologies(); var knowledge = new LinkedHashSet<>(empire.unlockedTechIds());
            var pending = new LinkedHashSet<String>();
            for (var facility : facilities) {
                if (!owners.contains(facility.ownerEntityId())) continue;
                boolean application = catalog.stream().flatMap(technology -> technology.applications().stream())
                        .anyMatch(value -> facility.applicationId().equals(value.id()));
                if (!application) continue;
                pending.add(facility.applicationId());
            }
            Empire current = empire;
            while (!pending.isEmpty()) {
                var available = new ArrayList<String>();
                for (String id : pending) if (ApplicationProduction.canResearch(current, id, true, catalog)) available.add(id);
                if (available.isEmpty()) throw new IllegalStateException("Opening industry lacks prerequisites for " + pending);
                knowledge.addAll(available); pending.removeAll(available);
                current = new Empire(empire.id(), empire.name(), empire.raceId(), empire.societyStructure(), empire.treasuryCredits(),
                        empire.corporateTaxRate(), empire.controlledSystemIds(), empire.ministries(), empire.systemGovernorAssignments(),
                        List.copyOf(knowledge), empire.activeShipDesignIds());
            }
            return new Empire(empire.id(), empire.name(), empire.raceId(), empire.societyStructure(), empire.treasuryCredits(),
                    empire.corporateTaxRate(), empire.controlledSystemIds(), empire.ministries(), empire.systemGovernorAssignments(),
                    List.copyOf(knowledge), empire.activeShipDesignIds());
        } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
}
