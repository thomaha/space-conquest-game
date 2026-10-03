package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.technology.ApplicationProduction;
import com.spaceconquest.engine.industry.ConstructionMaterialCatalog;
import com.spaceconquest.engine.industry.FacilityExpansionProject;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.industry.FacilityManufacturingCapacity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
                0,
                allocatedWorkers,
                workerProfessionId != null ? workerProfessionId : "industrial_worker",
                true,
                0.0
        );

        GameState funded = FacilityOperatingCapital.addFacility(state, newFacility);
        int targetTier = FacilityManufacturingCapacity.minimumTier(state, ownerEntityId, applicationId);
        List<FacilityExpansionProject> projects = new ArrayList<>(funded.expansionProjects());
        projects.add(new FacilityExpansionProject("project_" + UUID.randomUUID(), facilityId,
                targetTier, 0.0, ApplicationProduction.constructionWorkHours(state, ownerEntityId, applicationId,
                500.0 * targetTier), 0.0,
                ConstructionMaterialCatalog.facility(state, ownerEntityId, applicationId, targetTier), Map.of()));
        return funded.withExpansionProjects(projects);
    }
}
