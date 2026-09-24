package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.espionage.EspionageOperation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Command to initiate a covert espionage operation against a target entity.
 */
public record LaunchCovertOperationCommand(
        String operationType,
        String initiatorEmpireId,
        String targetEmpireId,
        String targetEntityId,
        String assignedAgentId
) implements GameCommand {

    @Override
    public boolean validate(GameState state) {
        return state != null && operationType != null && initiatorEmpireId != null && targetEntityId != null;
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) {
            return state;
        }

        String opId = "op_" + UUID.randomUUID().toString().substring(0, 8);
        EspionageOperation op = new EspionageOperation(
                opId,
                operationType,
                initiatorEmpireId,
                targetEmpireId != null ? targetEmpireId : "",
                targetEntityId,
                assignedAgentId != null ? assignedAgentId : "",
                0.70,
                0.0,
                false,
                false
        );

        List<EspionageOperation> updated = new ArrayList<>(state.espionageOperations());
        updated.add(op);

        return state.toBuilder()
                .espionageOperations(updated)
                .build();
    }
}
