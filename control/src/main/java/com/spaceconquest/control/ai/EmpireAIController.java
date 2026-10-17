package com.spaceconquest.control.ai;

import com.spaceconquest.control.Controller;
import com.spaceconquest.control.command.AppointMinisterCommand;
import com.spaceconquest.control.command.AssignGovernorCommand;
import com.spaceconquest.control.command.BuildMegastructureCommand;
import com.spaceconquest.control.command.CommandQueue;
import com.spaceconquest.control.command.ResolveDiplomaticProposalCommand;
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
import com.spaceconquest.engine.governance.DiplomaticProposal;
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
    private final EmpirePowerPlanner powerPlanner = new EmpirePowerPlanner();
    private final EmpireOperatingCapitalPlanner operatingCapitalPlanner = new EmpireOperatingCapitalPlanner();
    private final EmpireEconomicPlanner economicPlanner = new EmpireEconomicPlanner();
    private final EmpireLogisticsPlanner logisticsPlanner = new EmpireLogisticsPlanner();
    private final TransportConstructionPlanner transportConstructionPlanner = new TransportConstructionPlanner();
    private final ShipyardInvestmentPlanner shipyardInvestmentPlanner = new ShipyardInvestmentPlanner();

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
        CrossWorldSupplyPlanner.commands(state, empire).forEach(commandQueue::submit);
        AIShipDeploymentPlanner.plan(state, empire).forEach(commandQueue::submit);

        var operatingSupport = operatingCapitalPlanner.plan(state, empire);
        operatingSupport.forEach(commandQueue::submit);
        if (operatingSupport.isEmpty() && !EmpireOperatingCapitalPlanner.needsSupport(state, empire))
            powerPlanner.plan(state, empire).forEach(commandQueue::submit);
        var supply = logisticsPlanner.plan(state, empire);
        supply.commands().forEach(commandQueue::submit);
        transportConstructionPlanner.plan(state, empire, !supply.commands().isEmpty() || !supply.reservedFleetIds().isEmpty()).forEach(commandQueue::submit);
        var reservedFleets = new java.util.HashSet<>(supply.reservedFleetIds());
        reservedFleets.addAll(CrossWorldSupplyPlanner.reservedFleets(state, empire));
        reservedFleets.addAll(SurveyProgramPlanner.reservedFleets(state, empire));
        var lifecycle = ShipLifecyclePlanner.plan(state, empire, reservedFleets);
        lifecycle.commands().forEach(commandQueue::submit);
        reservedFleets.addAll(lifecycle.reservedFleetIds());
        EmpireFleetPlanner.plan(state, empire, reservedFleets).forEach(commandQueue::submit);
        FleetNeedsConstructionPlanner.plan(state, empire).forEach(commandQueue::submit);
        if (operatingSupport.isEmpty() && !EmpireOperatingCapitalPlanner.needsSupport(state, empire))
            shipyardInvestmentPlanner.plan(state, empire).forEach(commandQueue::submit);
        commandQueue.submit(new com.spaceconquest.control.command.PrioritizeAIShipConstructionCommand(empire.id()));

        boolean isHiveMind = "Hive Mind".equalsIgnoreCase(empire.societyStructure())
                || "Hive mind".equalsIgnoreCase(empire.societyStructure());

        SurveyProgramPlanner.plan(state, empire).forEach(commandQueue::submit);
        EmpireResearchPlanner.plan(state, empire).forEach(commandQueue::submit);
        if (operatingSupport.isEmpty() && !EmpireOperatingCapitalPlanner.needsSupport(state, empire))
            SupplyChainInvestmentPlanner.plan(state, empire).forEach(commandQueue::submit);
        resolveDiplomaticProposals(state, empire);

        if (!isHiveMind) {
            manageCabinetAndGovernors(empire);
            manageCorporateSubsidies(state, empire);
            manageDiplomacyAndSenate(state, empire);
            manageMegastructures(state, empire);
            economicPlanner.plan(state, empire).forEach(commandQueue::submit);
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

    private void manageDiplomacyAndSenate(GameState state, Empire empire) {
        if (state.galacticCommunity() != null) {
            for (GalacticResolution res : state.galacticCommunity().activeResolutions()) {
                if (!res.votes().containsKey(empireId)) {
                    String choice = empireId.equalsIgnoreCase(res.targetEmpireId()) ? GalacticResolution.VOTE_NAY : GalacticResolution.VOTE_AYE;
                    commandQueue.submit(new VoteResolutionCommand(empireId, res.id(), choice));
                }
            }
        }
    }

    private void resolveDiplomaticProposals(GameState state, Empire empire) {
        DiplomacyProcessor diplomacy = new DiplomacyProcessor();
        for (DiplomaticProposal proposal : state.diplomaticProposals()) {
            if (!empireId.equals(proposal.receiverEmpireId())
                    || !proposal.isPendingAtTurn(state.turn())) {
                continue;
            }
            Empire sender = state.empires().stream()
                    .filter(candidate -> candidate.id().equals(proposal.senderEmpireId())).findFirst().orElse(null);
            boolean accepted = diplomacy.evaluateProposalAcceptance(
                    proposal, sender, empire, state.diplomaticRelations(), 1.0);
            commandQueue.submit(new ResolveDiplomaticProposalCommand(proposal.id(), empireId, accepted));
        }
    }

    private void manageMegastructures(GameState state, Empire empire) {
        if (empire.treasuryCredits() >= 100000.0) {
            boolean hasMegastructure = state.megastructures().stream()
                    .anyMatch(m -> m.ownerEmpireId().equalsIgnoreCase(empireId));
            if (!hasMegastructure && !empire.controlledSystemIds().isEmpty()) {
                String targetSys = empire.controlledSystemIds().getFirst();
                commandQueue.submit(new BuildMegastructureCommand(
                        empireId, Megastructure.TYPE_DYSON_SWARM, targetSys, targetSys, "Imperial Dyson Swarm"
                ));
            }
        }

    }
}
