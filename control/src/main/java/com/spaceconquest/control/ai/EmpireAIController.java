package com.spaceconquest.control.ai;

import com.spaceconquest.control.Controller;
import com.spaceconquest.control.command.AppointMinisterCommand;
import com.spaceconquest.control.command.AssignGovernorCommand;
import com.spaceconquest.control.command.BuildMegastructureCommand;
import com.spaceconquest.control.command.CommandQueue;
import com.spaceconquest.control.command.SetDiplomaticTierCommand;
import com.spaceconquest.control.command.SetSystemEconomyBudgetCommand;
import com.spaceconquest.control.command.StartResearchCommand;
import com.spaceconquest.control.command.SubsidizeCorporationCommand;
import com.spaceconquest.control.command.VoteResolutionCommand;
import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.Technology;
import com.spaceconquest.engine.community.GalacticResolution;
import com.spaceconquest.engine.governance.DiplomacyProcessor;
import com.spaceconquest.engine.megastructure.Megastructure;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.List;

/**
 * Autonomous AI decision controller for sovereign empires.
 */
public class EmpireAIController implements Controller {
    private static final Logger logger = LogManager.getLogger(EmpireAIController.class);

    private final String empireId;
    private final CommandQueue commandQueue;

    public EmpireAIController(String empireId, CommandQueue commandQueue) {
        this.empireId = empireId;
        this.commandQueue = commandQueue;
    }

    public String getEmpireId() {
        return empireId;
    }

    @Override
    public void onGameStateUpdate(GameState state) {
        if (state == null || commandQueue == null) return;

        Empire empire = state.empires().stream().filter(e -> e.id().equals(empireId)).findFirst().orElse(null);
        if (empire == null) return;

        boolean isHiveMind = "Hive Mind".equalsIgnoreCase(empire.societyStructure())
                || "Hive mind".equalsIgnoreCase(empire.societyStructure());

        manageResearch(state, empire);

        if (!isHiveMind) {
            manageCabinetAndGovernors(empire);
            manageCorporateSubsidies(state, empire);
            manageDiplomacyAndSenate(state);
            manageMegastructuresAndEconomies(state, empire);
        }
    }

    private void manageResearch(GameState state, Empire empire) {
        boolean hasActiveResearch = state.researchProjects().stream()
                .anyMatch(p -> p.empireId().equals(empireId) && !p.isComplete());

        if (!hasActiveResearch) {
            try {
                List<Technology> allTechs = DataModelLoader.loadTechnologies();
                for (Technology tech : allTechs) {
                    if (!empire.unlockedTechIds().contains(tech.id())) {
                        boolean reqsMet = tech.requiredTechnologies().stream()
                                .allMatch(req -> empire.unlockedTechIds().contains(req));
                        if (reqsMet) {
                            commandQueue.submit(new StartResearchCommand(empireId, tech.id(), false, 5));
                            break;
                        }
                    }
                }
            } catch (IOException e) {
                logger.error("Failed to load technologies for AI research selection", e);
            }
        }
    }

    private void manageCabinetAndGovernors(Empire empire) {
        if (empire.ministries().isEmpty()) {
            commandQueue.submit(new AppointMinisterCommand(empireId, "ministry_industry_refining", "miner"));
            commandQueue.submit(new AppointMinisterCommand(empireId, "ministry_technology_application", "scientist"));
            commandQueue.submit(new AppointMinisterCommand(empireId, "ministry_defense_logistics", "soldier"));
        }

        for (String systemId : empire.controlledSystemIds()) {
            if (!empire.systemGovernorAssignments().containsKey(systemId)) {
                commandQueue.submit(new AssignGovernorCommand(empireId, systemId, "bureaucrat"));
            }
        }
    }

    private void manageCorporateSubsidies(GameState state, Empire empire) {
        if (empire.treasuryCredits() >= 40000.0) {
            for (Corporation corp : state.corporations()) {
                if (corp.empireId().equals(empireId) && corp.liquidCapitalReserves() < 5000.0) {
                    logger.info("Empire {} subsidizing struggling corporation {}", empireId, corp.id());
                    commandQueue.submit(new SubsidizeCorporationCommand(empireId, corp.id(), 5000.0));
                    break;
                }
            }
        }
    }

    private void manageDiplomacyAndSenate(GameState state) {
        for (Empire foreign : state.empires()) {
            if (!foreign.id().equals(empireId)) {
                String tier = new DiplomacyProcessor().getDiplomaticTier(empireId, foreign.id(), state.diplomaticRelations());
                if (DiplomacyProcessor.NEUTRAL.equals(tier)) {
                    commandQueue.submit(new SetDiplomaticTierCommand(empireId, foreign.id(), DiplomacyProcessor.COMMERCIAL_ALLIANCE));
                }
            }
        }

        if (state.galacticCommunity() != null) {
            for (GalacticResolution res : state.galacticCommunity().activeResolutions()) {
                if (!res.votes().containsKey(empireId)) {
                    String choice = empireId.equalsIgnoreCase(res.targetEmpireId()) ? GalacticResolution.VOTE_NAY : GalacticResolution.VOTE_AYE;
                    commandQueue.submit(new VoteResolutionCommand(empireId, res.id(), choice));
                }
            }
        }
    }

    private void manageMegastructuresAndEconomies(GameState state, Empire empire) {
        if (empire.treasuryCredits() >= 100000.0) {
            boolean hasMegastructure = state.megastructures().stream()
                    .anyMatch(m -> m.ownerEmpireId().equalsIgnoreCase(empireId));
            if (!hasMegastructure && !empire.controlledSystemIds().isEmpty()) {
                String targetSys = empire.controlledSystemIds().get(0);
                commandQueue.submit(new BuildMegastructureCommand(
                        empireId, Megastructure.TYPE_DYSON_SWARM, targetSys, targetSys, "Imperial Dyson Swarm"
                ));
            }
        }

        for (String systemId : empire.controlledSystemIds()) {
            boolean hasEconomy = state.systemEconomies().stream()
                    .anyMatch(se -> se.systemId().equals(systemId));
            if (!hasEconomy) {
                commandQueue.submit(new SetSystemEconomyBudgetCommand(
                        empireId, systemId, 0.20, 0.20, 0.20, 0.20, 0.20, 1000.0, 0.10
                ));
            }
        }
    }
}
