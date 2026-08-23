package com.spaceconquest.control.command;

import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.MinistryAssignment;
import com.spaceconquest.engine.MinistryPortfolio;
import com.spaceconquest.engine.governance.GovernanceProcessor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Command to appoint a minister to a cabinet portfolio.
 */
public record AppointMinisterCommand(
        String empireId,
        String portfolioId,
        String professionId
) implements GameCommand {

    private static final GovernanceProcessor governanceProcessor = new GovernanceProcessor();

    @Override
    public boolean validate(GameState state) {
        if (state == null || empireId == null || portfolioId == null || professionId == null) return false;

        Empire empire = state.empires().stream().filter(e -> e.id().equals(empireId)).findFirst().orElse(null);
        if (empire == null) return false;

        boolean isHiveMind = "Hive Mind".equalsIgnoreCase(empire.societyStructure())
                || "Hive mind".equalsIgnoreCase(empire.societyStructure());
        return !isHiveMind;
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) return state;

        List<MinistryPortfolio> portfolios = List.of();
        try {
            portfolios = DataModelLoader.loadMinistries();
        } catch (IOException ignored) {}

        double efficiency = governanceProcessor.calculateMinisterEfficiency(portfolioId, professionId, portfolios, false);

        List<Empire> updatedEmpires = new ArrayList<>();
        for (Empire e : state.empires()) {
            if (e.id().equals(empireId)) {
                List<MinistryAssignment> cabinet = new ArrayList<>(e.ministries());
                cabinet.removeIf(m -> m.portfolioId().equals(portfolioId));
                cabinet.add(new MinistryAssignment(portfolioId, professionId, efficiency));

                updatedEmpires.add(new Empire(
                        e.id(), e.name(), e.raceId(), e.societyStructure(),
                        e.treasuryCredits(), e.corporateTaxRate(),
                        e.controlledSystemIds(), cabinet, e.systemGovernorAssignments(),
                        e.unlockedTechIds(), e.activeShipDesignIds()
                ));
            } else {
                updatedEmpires.add(e);
            }
        }

        return new GameState(
                state.turn(),
                state.status(),
                state.solarSystems(),
                updatedEmpires,
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
                state.systemEconomies()
        );
    }
}
