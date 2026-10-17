package com.spaceconquest.control.ai;

import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.*;
import java.util.*;

/** Builds one affordable missing link at a time, before commissioning dependent load. */
public final class SupplyChainInvestmentPlanner {
    private SupplyChainInvestmentPlanner() {}
    public static List<GameCommand> plan(GameState state, Empire empire) {
        var release = new ReleaseExhaustedWorkersCommand(empire.id());
        if (release.validate(state)) return List.of(release);
        var quote = next(state, empire);
        if (quote != null) return List.of(new RequestAISupplyInvestmentCommand(empire.id(), quote.command().planetId(), quote.command().applicationId()));
        var expansion = expansion(state, empire);
        return expansion == null ? List.of() : List.of(new RequestAISupplyExpansionCommand(empire.id(), expansion.command().facilityId(), expansion.command().targetTier()));
    }
    public static SupplyExpansionQuote expansion(GameState state, Empire empire) {
        if (empire.societyStructure() != null && empire.societyStructure().toLowerCase(Locale.ROOT).contains("hive")) return null;
        for (var goal : SupplyChainNeeds.goals(state, empire)) {
            if (goal.materialId().isBlank()) continue;
            var quote = SupplyExpansionQuote.assess(state, empire, goal).quote();
            if (quote != null) return quote;
        }
        return null;
    }
    public static SupplyInvestmentQuote next(GameState state, Empire empire) {
        if (empire.societyStructure() != null && empire.societyStructure().toLowerCase(Locale.ROOT).contains("hive")) return null;
        for (var goal : SupplyChainNeeds.goals(state, empire)) {
            if (SupplyChainNeeds.installed(state, empire, goal.bodyId(), goal.applicationId())) {
                if (!goal.materialId().isBlank() && SupplyExpansionQuote.assess(state, empire, goal).additionalPowerKw() > 0) {
                    String power = SupplyInvestmentQuote.generation(state, empire, goal.bodyId());
                    var quote = power == null ? null : SupplyInvestmentQuote.evaluate(state, empire, goal.bodyId(), power);
                    if (quote != null) return quote;
                }
                continue;
            }
            int tier = FacilityManufacturingCapacity.minimumTier(state, empire.id(), goal.applicationId());
            double load = SupplyInvestmentQuote.load(state, goal.bodyId(), goal.applicationId(), tier);
            if (load > SupplyInvestmentQuote.freePower(state, goal.bodyId()) + 1e-6) {
                String power = SupplyInvestmentQuote.generation(state, empire, goal.bodyId());
                if (power != null) {
                    var quote = SupplyInvestmentQuote.evaluate(state, empire, goal.bodyId(), power);
                    if (quote != null) return quote;
                }
                continue;
            }
            var quote = SupplyInvestmentQuote.evaluate(state, empire, goal.bodyId(), goal.applicationId());
            if (quote != null) return quote;
        }
        return null;
    }
    public static Set<String> researchGoals(GameState state, Empire empire) {
        var result = new HashSet<String>();
        for (var goal : SupplyChainNeeds.goals(state, empire)) {
            result.add(com.spaceconquest.engine.technology.ApplicationProduction.capabilityApplication(goal.applicationId()));
            int tier = FacilityManufacturingCapacity.minimumTier(state, empire.id(), goal.applicationId());
            boolean expansionPower = !goal.materialId().isBlank() && SupplyChainNeeds.installed(state, empire, goal.bodyId(), goal.applicationId())
                    && SupplyExpansionQuote.assess(state, empire, goal).additionalPowerKw() > 0;
            if (expansionPower || !SupplyChainNeeds.installed(state, empire, goal.bodyId(), goal.applicationId())
                    && SupplyInvestmentQuote.load(state, goal.bodyId(), goal.applicationId(), tier) > SupplyInvestmentQuote.freePower(state, goal.bodyId())) {
                String power = SupplyInvestmentQuote.generation(state, empire, goal.bodyId());
                if (power != null) result.add(power);
            }
        }
        return Set.copyOf(result);
    }
}
