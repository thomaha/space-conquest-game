package com.spaceconquest.control.ai;

import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.*;
import java.util.*;

/** Restores one viable cash-starved public facility per observed day, with generation first. */
public final class EmpireOperatingCapitalPlanner {
    private long lastProposedTurn = -1;
    public static boolean needsSupport(GameState state, Empire empire) {
        return state.industrialFacilities().stream().filter(facility -> empire.id().equals(facility.ownerEntityId()))
                .anyMatch(facility -> FacilityOperatingReadiness.inspect(state, empire.id(), facility.id()).status() == FacilityOperatingReadiness.Status.CASH_LOW);
    }
    public List<GameCommand> plan(GameState state, Empire empire) {
        if (state.turn() == lastProposedTurn) return List.of();
        var quote = state.industrialFacilities().stream().filter(facility -> empire.id().equals(facility.ownerEntityId()))
                .map(facility -> FacilityOperatingReadiness.inspect(state, empire.id(), facility.id()))
                .filter(value -> value.status() == FacilityOperatingReadiness.Status.CASH_LOW)
                .sorted(Comparator.comparing((FacilityOperatingReadiness value) -> state.industrialFacilities().stream()
                        .filter(facility -> value.facilityId().equals(facility.id())).noneMatch(facility -> PowerPlantCatalog.find(facility.applicationId()) != null))
                        .thenComparing(FacilityOperatingReadiness::facilityId)).findFirst().orElse(null);
        if (quote == null) return List.of();
        lastProposedTurn = state.turn();
        return List.of(new FundAIFacilityOperationsCommand(empire.id(), quote.facilityId()));
    }
}
