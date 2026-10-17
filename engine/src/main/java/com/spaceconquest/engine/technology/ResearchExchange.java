package com.spaceconquest.engine.technology;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.governance.DiplomacyProcessor;
import java.util.*;

/** Voluntary sharing by a knowledgeable source under a current ratified alliance. */
public final class ResearchExchange {
    private ResearchExchange() {}
    public static boolean eligible(GameState state, TechnologyExchangeRoute route, List<Technology> catalog) {
        if (route == null || route.senderEmpireId() == null || route.receiverEmpireId() == null
                || route.senderEmpireId().equals(route.receiverEmpireId()) || route.technologyId() == null
                || !Double.isFinite(route.prePopulatedPercentage()) || route.prePopulatedPercentage() < 0 || route.prePopulatedPercentage() >= 1
                || !Double.isFinite(route.trainingSpeedBonus()) || route.trainingSpeedBonus() < 0) return false;
        var sender = state.empires().stream().filter(value -> route.senderEmpireId().equals(value.id())).findFirst().orElse(null);
        var receiver = state.empires().stream().filter(value -> route.receiverEmpireId().equals(value.id())).findFirst().orElse(null);
        if (sender == null || receiver == null || !sender.unlockedTechIds().contains(route.technologyId())) return false;
        boolean application = catalog.stream().flatMap(technology -> technology.applications().stream())
                .anyMatch(value -> route.technologyId().equals(value.id()));
        if (!ApplicationProduction.canResearch(receiver, route.technologyId(), application, catalog)) return false;
        return state.diplomaticRelations().stream().anyMatch(relation ->
                (route.senderEmpireId().equals(relation.empireAId()) && route.receiverEmpireId().equals(relation.empireBId())
                        || route.senderEmpireId().equals(relation.empireBId()) && route.receiverEmpireId().equals(relation.empireAId()))
                        && (DiplomacyProcessor.COMMERCIAL_ALLIANCE.equals(relation.tier())
                        || DiplomacyProcessor.INTEGRATED_FEDERATION.equals(relation.tier())));
    }
    public static List<TechnologyExchangeRoute> active(GameState state, List<Technology> catalog) {
        return state.technologyExchangeRoutes().stream().filter(route -> eligible(state, route, catalog)).toList();
    }
    public static double openingPoints(GameState state, Empire empire, String target, double required, List<Technology> catalog) {
        if (empire.unlockedTechIds().contains(target)) return 0;
        return required * active(state, catalog).stream().filter(route -> empire.id().equals(route.receiverEmpireId())
                && target.equals(route.technologyId())).mapToDouble(TechnologyExchangeRoute::prePopulatedPercentage).max().orElse(0);
    }
}
