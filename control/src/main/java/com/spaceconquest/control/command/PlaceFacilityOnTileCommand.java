package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.IndustrialFacility;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Command to construct a facility positioned directly on a planetary surface biome tile.
 */
public record PlaceFacilityOnTileCommand(
        String planetId,
        int tileIndex,
        String applicationId,
        String ownerEntityId,
        String ownershipType,
        int allocatedWorkers,
        String workerProfessionId
) implements GameCommand {

    @Override
    public boolean validate(GameState state) {
        if (state == null || planetId == null || applicationId == null || ownerEntityId == null) {
            return false;
        }
        return tileIndex >= 0 && allocatedWorkers >= 0;
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) {
            return state;
        }

        String facilityId = "fac_tile_" + tileIndex + "_" + UUID.randomUUID().toString().substring(0, 6);
        IndustrialFacility newFacility = new IndustrialFacility(
                facilityId,
                planetId,
                applicationId,
                ownerEntityId,
                (ownershipType != null) ? ownershipType : IndustrialFacility.PUBLIC_STATE,
                1,
                allocatedWorkers,
                workerProfessionId != null ? workerProfessionId : "industrial_worker",
                false,
                0.0
        );

        List<IndustrialFacility> updatedFacilities = new ArrayList<>(state.industrialFacilities());
        updatedFacilities.add(newFacility);

        return state.toBuilder()
                .industrialFacilities(updatedFacilities)
                .build();
    }
}
