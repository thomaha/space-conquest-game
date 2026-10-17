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
        return state.industrialFacilities().stream().anyMatch(f -> f.id().equals(facilityId)
                && !f.isUndergoingExpansion()
                && state.industryAccounts().stream().noneMatch(a -> a.facilityId().equals(f.id())
                && a.farmingState().cultivatedBatches() > 0 && !f.applicationId().equals(targetRecipeOrApplicationId))
                && com.spaceconquest.engine.industry.ProductionWork.supports(f, targetRecipeOrApplicationId)
                && com.spaceconquest.engine.industry.FarmingDefinition.canInstall(state, targetRecipeOrApplicationId,
                f.planetId(), f.id(), f.tier(), f.productionEquipment())
                && FacilityResearchEligibility.allows(state, f.ownerEntityId(), targetRecipeOrApplicationId)
                && com.spaceconquest.engine.industry.FacilityManufacturingCapacity.canOperate(state,
                new IndustrialFacility(f.id(), f.planetId(), targetRecipeOrApplicationId, f.ownerEntityId(),
                        f.ownershipType(), f.tier(), f.allocatedWorkers(), f.workerProfessionId(), false, 0)));
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
                        f.isUndergoingExpansion(), f.expansionProgress(), f.productionEquipment()
                );
            }
            return f;
        }).toList();

        return state.toBuilder()
                .industrialFacilities(updated)
                .build();
    }
}
