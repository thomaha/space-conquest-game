package com.spaceconquest.engine.market;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.EconomicOwnership;
import com.spaceconquest.engine.industry.*;
import java.util.*;

/** Bounded local production schedules. Estimates reserve time, never inputs or guaranteed deliveries. */
public final class SupplyLeadTimes {
    private SupplyLeadTimes() {}
    public static Map<String, Double> local(GameState state, CommercialHub hub) {
        var facilities = state.industrialFacilities().stream().filter(f -> f.planetId().equals(hub.entityId()) && f.tier() > 0
                && !f.isUndergoingExpansion() && FacilityManufacturingCapacity.canOperate(state, f))
                .filter(f -> IndustryRecipeCatalog.isUnlocked(IndustryRecipeCatalog.forFacility(state, f),
                        EconomicOwnership.empireFor(state, f.ownerEntityId()))).toList();
        var times = new HashMap<String, Double>();
        // Eight upstream stages bound cycles and ordinary tick cost. Missing suppliers add no invented capability.
        for (int pass = 0; pass < 8; pass++) {
            var previous = times; var next = new HashMap<String, Double>();
            for (var facility : facilities) {
                var recipe = IndustryRecipeCatalog.forFacility(state, facility);
                double upstream = recipe.inputsKg().keySet().stream().mapToDouble(id -> previous.getOrDefault(id, 0.0)).max().orElse(0);
                double own = recipe.farming() == null ? recipe.work() == null ? 1 : Math.max(1, recipe.work().minimumProcessingDays())
                        : recipe.farming().growingDays();
                if (recipe.farming() != null) {
                    var growth = state.industryAccounts().stream().filter(a -> a.facilityId().equals(facility.id()))
                            .map(IndustryAccount::farmingState).findFirst().orElse(FarmingState.empty());
                    if (growth.cultivatedBatches() > 0) own = Math.max(1, own - (state.turn() - growth.startedTurn() + 1));
                }
                double lead = Math.min(360, own + upstream);
                recipe.outputsKg().keySet().forEach(id -> next.merge(id, lead, Math::min));
            }
            if (next.equals(times)) break;
            times = next;
        }
        return Map.copyOf(times);
    }
}
