package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.ConstructionMaterialCatalog;
import com.spaceconquest.engine.industry.FacilityExpansionProject;
import com.spaceconquest.engine.industry.IndustrialFacility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Command to construct a new planetary industrial processing facility.
 */
public record BuildFacilityCommand(
        String planetId,
        String applicationId,
        String ownerEntityId,
        String ownershipType,
        int allocatedWorkers,
        String workerProfessionId
) implements GameCommand {

    public BuildFacilityCommand(String ownerEmpireId, String planetId, String applicationId, String workerProfessionId, int tier) {
        this(planetId, applicationId, ownerEmpireId, IndustrialFacility.PUBLIC_STATE, 100, workerProfessionId);
    }

    @Override
    public boolean validate(GameState state) {
        if (state == null || planetId == null || applicationId == null || ownerEntityId == null) {
            return false;
        }
        return allocatedWorkers >= 0;
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) {
            return state;
        }

        String facilityId = "fac_" + UUID.randomUUID().toString().substring(0, 8);
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
        List<FacilityExpansionProject> projects = new ArrayList<>(funded.expansionProjects());
        projects.add(new FacilityExpansionProject("project_" + UUID.randomUUID(), facilityId,
                1, 0.0, 500.0, 0.0,
                ConstructionMaterialCatalog.facility(applicationId, 1), Map.of()));
        return funded.withExpansionProjects(projects);
    }
}
