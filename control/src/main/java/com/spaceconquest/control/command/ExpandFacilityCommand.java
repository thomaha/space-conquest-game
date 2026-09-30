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
 * Command to initiate a facility tier scaling expansion project (-50% output penalty during construction).
 */
public record ExpandFacilityCommand(
        String facilityId,
        int targetTier,
        double requiredWorkHours,
        double costCredits
) implements GameCommand {

    public ExpandFacilityCommand(String facilityId, int targetTier, double costCredits) {
        this(facilityId, targetTier, 200.0, costCredits);
    }

    @Override
    public boolean validate(GameState state) {
        if (state == null || facilityId == null) {
            return false;
        }
        return state.industrialFacilities().stream().anyMatch(f ->
                f.id().equals(facilityId) && targetTier > f.tier())
                && state.expansionProjects().stream()
                .noneMatch(project -> facilityId.equals(project.facilityId()));
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) {
            return state;
        }

        List<IndustrialFacility> updatedFacilities = new ArrayList<>();
        for (IndustrialFacility fac : state.industrialFacilities()) {
            if (fac.id().equals(facilityId)) {
                updatedFacilities.add(new IndustrialFacility(
                        fac.id(),
                        fac.planetId(),
                        fac.applicationId(),
                        fac.ownerEntityId(),
                        fac.ownershipType(),
                        fac.tier(),
                        fac.allocatedWorkers(),
                        fac.workerProfessionId(),
                        true, // Undergoing expansion (-50% output throttle)
                        0.0
                ));
            } else {
                updatedFacilities.add(fac);
            }
        }

        String projectId = "exp_" + UUID.randomUUID().toString().substring(0, 8);
        IndustrialFacility original = state.industrialFacilities().stream()
                .filter(facility -> facilityId.equals(facility.id())).findFirst().orElseThrow();
        FacilityExpansionProject newProject = new FacilityExpansionProject(
                projectId,
                facilityId,
                targetTier,
                0.0,
                requiredWorkHours > 0.0 ? requiredWorkHours : 200.0,
                costCredits,
                ConstructionMaterialCatalog.facility(original.applicationId(),
                        targetTier - original.tier()),
                Map.of()
        );

        List<FacilityExpansionProject> updatedProjects = new ArrayList<>(state.expansionProjects());
        updatedProjects.add(newProject);

        return state.toBuilder()
                .industrialFacilities(updatedFacilities)
                .expansionProjects(updatedProjects)
                .build();
    }
}
