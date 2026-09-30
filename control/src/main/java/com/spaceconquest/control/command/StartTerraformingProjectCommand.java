package com.spaceconquest.control.command;

import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.ConstructionMaterialCatalog;
import com.spaceconquest.engine.industry.ConstructionMaterials;
import com.spaceconquest.engine.terraforming.GeoengineeringProject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Command to fund and initiate a planetary terraforming or geoengineering macro-project.
 */
public record StartTerraformingProjectCommand(
        String empireId,
        String planetId,
        String projectType,
        double targetPressureAtm,
        double targetTemperatureK,
        Map<String, Double> targetGasRatios
) implements GameCommand {

    public StartTerraformingProjectCommand {
        if (targetGasRatios == null) targetGasRatios = Map.of();
    }

    @Override
    public boolean validate(GameState state) {
        if (state == null || empireId == null || planetId == null) {
            return false;
        }
        String systemId = ConstructionMaterials.systemForBody(state, planetId);
        return systemId != null && ConstructionMaterials.bodyForSystem(state, systemId, planetId) != null
                && state.empires().stream().anyMatch(e -> e.id().equalsIgnoreCase(empireId)
                && e.controlledSystemIds().contains(systemId) && e.treasuryCredits() >= 10000.0);
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) {
            return state;
        }

        double projectCostCredits = 10000.0;
        List<Empire> updatedEmpires = state.empires().stream().map(e -> {
            if (e.id().equalsIgnoreCase(empireId)) {
                return new Empire(
                        e.id(), e.name(), e.raceId(), e.societyStructure(),
                        e.treasuryCredits() - projectCostCredits,
                        e.corporateTaxRate(), e.controlledSystemIds(),
                        e.ministries(), e.systemGovernorAssignments(),
                        e.unlockedTechIds(), e.activeShipDesignIds()
                );
            }
            return e;
        }).toList();

        GeoengineeringProject proj = new GeoengineeringProject(
                "terra_" + planetId + "_" + UUID.randomUUID().toString().substring(0, 6),
                planetId,
                empireId,
                projectType != null ? projectType : GeoengineeringProject.TYPE_GREENHOUSE_FACTORY,
                0.0,
                10.0,
                targetPressureAtm > 0 ? targetPressureAtm : 1.0,
                targetTemperatureK > 0 ? targetTemperatureK : 288.0,
                targetGasRatios,
                ConstructionMaterialCatalog.terraforming(projectType), Map.of(),
                false
        );

        List<GeoengineeringProject> updatedProjects = new ArrayList<>(state.terraformingProjects());
        updatedProjects.add(proj);

        return state.toBuilder()
                .empires(updatedEmpires)
                .terraformingProjects(updatedProjects)
                .build();
    }
}
