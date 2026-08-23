package com.spaceconquest.control.command;

import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.Moon;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.Population;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.economy.SystemEconomy;
import com.spaceconquest.engine.economy.SystemEconomyProcessor;

import java.util.ArrayList;
import java.util.List;

/**
 * Command to configure public sector budget allocations and total funding for a solar system.
 */
public record SetSystemEconomyBudgetCommand(
        String empireId,
        String systemId,
        double educationAllocation,
        double lawAndOrderAllocation,
        double healthAndWelfareAllocation,
        double infrastructureAllocation,
        double planetaryMilitiasAllocation,
        double totalBudgetCredits
) implements GameCommand {

    @Override
    public boolean validate(GameState state) {
        if (state == null || empireId == null || systemId == null) return false;
        Empire empire = state.empires().stream().filter(e -> e.id().equals(empireId)).findFirst().orElse(null);
        if (empire == null) return false;

        boolean isHiveMind = "Hive Mind".equalsIgnoreCase(empire.societyStructure())
                || "Hive mind".equalsIgnoreCase(empire.societyStructure());
        if (isHiveMind) return false;

        if (totalBudgetCredits < 0) return false;
        if (empire.controlledSystemIds() != null && !empire.controlledSystemIds().isEmpty()) {
            if (!empire.controlledSystemIds().contains(systemId)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) return state;

        double edu = Math.max(0.0, educationAllocation);
        double law = Math.max(0.0, lawAndOrderAllocation);
        double health = Math.max(0.0, healthAndWelfareAllocation);
        double infra = Math.max(0.0, infrastructureAllocation);
        double militia = Math.max(0.0, planetaryMilitiasAllocation);

        double sum = edu + law + health + infra + militia;
        if (sum > 0.0) {
            edu /= sum;
            law /= sum;
            health /= sum;
            infra /= sum;
            militia /= sum;
        } else {
            edu = 0.20;
            law = 0.20;
            health = 0.20;
            infra = 0.20;
            militia = 0.20;
        }

        long systemPop = 0;
        for (SolarSystem sys : state.solarSystems()) {
            if (sys.id().equals(systemId)) {
                if (sys.planets() != null) {
                    for (Planet p : sys.planets()) {
                        if (p.populations() != null) {
                            for (Population pop : p.populations()) {
                                systemPop += pop.totalCount();
                            }
                        }
                        if (p.moons() != null) {
                            for (Moon m : p.moons()) {
                                if (m.populations() != null) {
                                    for (Population pop : m.populations()) {
                                        systemPop += pop.totalCount();
                                    }
                                }
                            }
                        }
                    }
                }
                break;
            }
        }

        SystemEconomy existing = null;
        for (SystemEconomy se : state.systemEconomies()) {
            if (se.systemId().equals(systemId)) {
                existing = se;
                break;
            }
        }

        double accumulatedInvestment = existing != null ? existing.accumulatedMilitiaInvestment() : 5000.0;
        SystemEconomy newEconomy = new SystemEconomy(
                systemId,
                empireId,
                edu,
                law,
                health,
                infra,
                militia,
                totalBudgetCredits,
                accumulatedInvestment,
                existing != null ? existing.educationLevel() : 1.0,
                existing != null ? existing.lawAndOrderLevel() : 1.0,
                existing != null ? existing.healthAndWelfareLevel() : 1.0,
                existing != null ? existing.infrastructureLevel() : 1.0,
                existing != null ? existing.planetaryMilitiaLevel() : 1.0,
                existing != null ? existing.employedTeachers() : Math.max(10, Math.round(systemPop * 0.0005)),
                existing != null ? existing.employedScientists() : Math.max(10, Math.round(systemPop * 0.0003)),
                existing != null ? existing.employedPolice() : Math.max(15, Math.round(systemPop * 0.0008)),
                existing != null ? existing.employedMedics() : Math.max(10, Math.round(systemPop * 0.0004)),
                existing != null ? existing.employedEngineers() : Math.max(20, Math.round(systemPop * 0.0010)),
                existing != null ? existing.employedTechnicians() : Math.max(30, Math.round(systemPop * 0.0015)),
                existing != null ? existing.employedSoldiers() : Math.max(25, Math.round(systemPop * 0.0012)),
                existing != null ? existing.recruitableSoldiers() : Math.max(100, Math.round(systemPop * 0.0050))
        );

        SystemEconomyProcessor processor = new SystemEconomyProcessor();
        SystemEconomy updatedEconomy = processor.processSystemEconomy(newEconomy, systemPop, false);

        List<SystemEconomy> updatedList = new ArrayList<>(state.systemEconomies());
        updatedList.removeIf(se -> se.systemId().equals(systemId));
        updatedList.add(updatedEconomy);

        return new GameState(
                state.turn(),
                state.status(),
                state.solarSystems(),
                state.empires(),
                state.corporations(),
                state.commercialHubs(),
                state.shadowSyndicates(),
                state.diplomaticRelations(),
                state.systemGovernors(),
                state.researchProjects(),
                state.technologyExchangeRoutes(),
                state.shipDesigns(),
                state.fleets(),
                state.geologicalDeposits(),
                state.powerGrids(),
                state.industrialFacilities(),
                state.expansionProjects(),
                state.orbitalStations(),
                state.spaceElevators(),
                state.constructionProjects(),
                state.sleeperAgents(),
                state.espionageOperations(),
                state.pirateBases(),
                state.terraformingProjects(),
                state.megastructures(),
                state.galacticCommunity(),
                state.tradeRoutes(),
                state.fogOfWarStates(),
                updatedList
        );
    }
}
