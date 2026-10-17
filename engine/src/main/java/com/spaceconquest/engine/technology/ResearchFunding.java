package com.spaceconquest.engine.technology;

import com.spaceconquest.engine.*;
import java.util.List;

/** Immutable current-payroll quote shared by progression and snapshot-based previews. */
public record ResearchFunding(long capacity, long requested, double pointsPerScientist,
                              List<TechnologyExchangeRoute> routes) {
    public ResearchFunding { routes = List.copyOf(routes); }
    public static ResearchFunding quote(GameState state, Empire empire, List<Race> races,
                                        List<Technology> catalog, ResearchProcessor processor) {
        var workforce = ResearchWorkforce.available(state, empire);
        long capacity = workforce.stream().mapToLong(ResearchWorkforce.Scientists::count).sum();
        long requested = state.researchProjects().stream().filter(project -> empire.id().equals(project.empireId())
                        && !project.isComplete() && ApplicationProduction.canResearch(empire, project.targetTechOrAppId(), project.isApplication(), catalog))
                .mapToLong(project -> Math.max(0, project.assignedScientists())).sum();
        double output = workforce.stream().mapToDouble(group -> {
            Race race = races.stream().filter(value -> group.raceId().equalsIgnoreCase(value.id())).findFirst().orElse(null);
            return group.count() * processor.calculateTurnResearchPoints(1, group.training(), race, empire, 1);
        }).sum();
        return new ResearchFunding(capacity, requested, capacity == 0 ? 0 : output / capacity,
                ResearchExchange.active(state, catalog));
    }
    public double scientists(ResearchProject project) {
        return requested <= 0 ? 0 : Math.min(requested, capacity) * (double) Math.max(0, project.assignedScientists()) / requested;
    }
    public double basePoints(ResearchProject project) { return scientists(project) * pointsPerScientist; }
    public double points(ResearchProject project) { return earned(project, basePoints(project), routes); }
    public static double exchangeBonus(ResearchProject project, List<TechnologyExchangeRoute> routes) {
        return routes.stream().filter(route -> route.receiverEmpireId().equals(project.empireId())
                && route.technologyId().equals(project.targetTechOrAppId()))
                .mapToDouble(TechnologyExchangeRoute::trainingSpeedBonus).max().orElse(0);
    }
    public static double earned(ResearchProject project, double base, List<TechnologyExchangeRoute> routes) {
        return Math.max(0, base) * Math.max(0, project.speedModifier()) * (1 + exchangeBonus(project, routes));
    }
}
