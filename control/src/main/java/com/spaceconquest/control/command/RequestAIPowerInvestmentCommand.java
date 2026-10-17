package com.spaceconquest.control.command;

import com.spaceconquest.control.ai.PowerInvestmentQuote;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.*;

/** Revalidates an observed power shortage before staging an ordinary material-funded project. */
public record RequestAIPowerInvestmentCommand(String empireId, String bodyId, String applicationId,
        String facilityId, int targetTier, long firstDeficitTurn) implements GameCommand {
    @Override public boolean validate(GameState state) {
        if (state == null || empireId == null || bodyId == null || applicationId == null || firstDeficitTurn < 0) return false;
        var empire = state.empires().stream().filter(value -> empireId.equals(value.id())).findFirst().orElse(null);
        var plant = PowerPlantCatalog.find(applicationId);
        if (empire == null || plant == null || state.turn() < firstDeficitTurn
                || state.turn() - firstDeficitTurn + 1 < plant.shortageObservationTurns()) return false;
        var quote = PowerInvestmentQuote.evaluate(state, empire, bodyId, applicationId, facilityId);
        return quote != null && quote.targetTier() == targetTier;
    }
    @Override public GameState apply(GameState state) {
        if (!validate(state)) return state;
        var plant = PowerPlantCatalog.find(applicationId);
        if (facilityId == null) return new BuildFacilityCommand(bodyId, applicationId, empireId,
                IndustrialFacility.PUBLIC_STATE, plant.requiredWorkers(), plant.workerProfessionId()).apply(state);
        var facility = state.industrialFacilities().stream().filter(value -> facilityId.equals(value.id())).findFirst().orElseThrow();
        return new ExpandFacilityCommand(facilityId, targetTier,
                plant.expansionWorkHoursPerTier() * (targetTier - facility.tier()), 0).apply(state);
    }
}
