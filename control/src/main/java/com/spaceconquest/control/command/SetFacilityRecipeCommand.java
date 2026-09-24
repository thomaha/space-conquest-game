package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.IndustrialFacility;

import java.util.List;

/**
 * Command to update the operational recipe or application ID of an industrial facility.
 */
public record SetFacilityRecipeCommand(
        String facilityId,
        String targetRecipeOrApplicationId
) implements GameCommand {

    @Override
    public boolean validate(GameState state) {
        if (state == null || facilityId == null || targetRecipeOrApplicationId == null) {
            return false;
        }
        return state.industrialFacilities().stream().anyMatch(f -> f.id().equals(facilityId));
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) {
            return state;
        }

        List<IndustrialFacility> updated = state.industrialFacilities().stream().map(f -> {
            if (f.id().equals(facilityId)) {
                return new IndustrialFacility(
                        f.id(), f.planetId(), targetRecipeOrApplicationId,
                        f.ownerEntityId(), f.ownershipType(), f.tier(),
                        f.allocatedWorkers(), f.workerProfessionId(),
                        f.isUndergoingExpansion(), f.expansionProgress()
                );
            }
            return f;
        }).toList();

        return state.toBuilder()
                .industrialFacilities(updated)
                .build();
    }
}
