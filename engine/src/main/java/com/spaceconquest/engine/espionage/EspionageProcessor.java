package com.spaceconquest.engine.espionage;

import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.Empire;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Executes covert intelligence missions, counter-espionage detection checks and shadow syndicate extortion.
 */
public class EspionageProcessor {

    private final Random random;

    public EspionageProcessor() {
        this(new Random());
    }

    public EspionageProcessor(Random random) {
        this.random = random;
    }

    public record ExtortionResult(
            boolean isSuccessful,
            double creditsExtorted,
            String targetCorpId,
            String targetBaseId
    ) {}

    public record EspionageTurnResult(
            List<EspionageOperation> updatedOperations,
            List<SleeperAgent> updatedAgents,
            List<PirateBase> updatedBases,
            List<String> powerGridSabotagedEntityIds,
            List<String> stolenTechApplicationIds
    ) {}

    /**
     * Advances all active espionage operations and evaluates outcome triggers.
     */
    public EspionageTurnResult processEspionageTurn(
            List<EspionageOperation> operations,
            List<SleeperAgent> agents,
            List<PirateBase> bases,
            List<Empire> empires
    ) {
        if (operations == null) operations = List.of();
        if (agents == null) agents = List.of();
        if (bases == null) bases = List.of();

        List<EspionageOperation> remainingOperations = new ArrayList<>();
        List<SleeperAgent> updatedAgents = new ArrayList<>(agents);
        List<String> powerSabotagedIds = new ArrayList<>();
        List<String> stolenTechIds = new ArrayList<>();

        for (EspionageOperation op : operations) {
            if (op.isCompleted()) {
                continue;
            }

            SleeperAgent agent = agents.stream()
                    .filter(a -> a.id().equals(op.assignedAgentId()))
                    .findFirst()
                    .orElse(null);

            double agentBonus = (agent != null) ? agent.infiltrationLevel() * 0.05 : 0.0;
            double nextProgress = op.progressPercent() + 25.0 + (agentBonus * 100.0);

            if (nextProgress >= 100.0) {
                double roll = random.nextDouble();
                boolean isSuccess = roll <= (op.successProbability() + agentBonus);
                boolean wasDetected = random.nextDouble() < 0.25;

                if (isSuccess) {
                    if (EspionageOperation.OP_POWER_GRID_SABOTAGE.equalsIgnoreCase(op.operationType())) {
                        powerSabotagedIds.add(op.targetEntityId());
                    } else if (EspionageOperation.OP_TECH_THEFT.equalsIgnoreCase(op.operationType())) {
                        stolenTechIds.add(op.targetEntityId());
                    }
                }

                if (wasDetected && agent != null) {
                    updatedAgents = updatedAgents.stream().map(a -> {
                        if (a.id().equals(agent.id())) {
                            return new SleeperAgent(a.id(), a.ownerEmpireId(), a.targetEntityId(), a.coverProfessionId(), a.infiltrationLevel(), true);
                        }
                        return a;
                    }).toList();
                }

                remainingOperations.add(new EspionageOperation(
                        op.id(), op.operationType(), op.initiatorEmpireId(), op.targetEmpireId(),
                        op.targetEntityId(), op.assignedAgentId(), op.successProbability(),
                        100.0, true, wasDetected
                ));
            } else {
                remainingOperations.add(new EspionageOperation(
                        op.id(), op.operationType(), op.initiatorEmpireId(), op.targetEmpireId(),
                        op.targetEntityId(), op.assignedAgentId(), op.successProbability(),
                        nextProgress, false, false
                ));
            }
        }

        return new EspionageTurnResult(
                remainingOperations,
                updatedAgents,
                bases,
                powerSabotagedIds,
                stolenTechIds
        );
    }

    /**
     * Executes extortion against an un-escorted corporate supply line, routing credits to a pirate base.
     */
    public ExtortionResult processSupplyLineExtortion(
            PirateBase pirateBase,
            Corporation targetCorp,
            double extortionDemandCredits
    ) {
        if (pirateBase == null || targetCorp == null) {
            return new ExtortionResult(false, 0.0, "", "");
        }

        double availableReserves = targetCorp.liquidCapitalReserves();
        double extorted = Math.min(extortionDemandCredits, availableReserves * 0.20);
        if (extorted <= 0.0) {
            return new ExtortionResult(false, 0.0, targetCorp.id(), pirateBase.id());
        }

        return new ExtortionResult(true, extorted, targetCorp.id(), pirateBase.id());
    }
}
