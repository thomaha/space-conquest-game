package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.logistics.InfrastructureService;

/** Persistent service allocation; adding workers never creates a paid payroll receipt. */
public record SetLaunchServiceStaffingCommand(String empireId, String facilityId, int workers) implements GameCommand {
    @Override public boolean validate(GameState state) {
        if (state == null || empireId == null || facilityId == null || workers < 0) return false;
        var facility = state.industrialFacilities().stream().filter(f -> f.id().equals(facilityId)).findFirst().orElse(null);
        if (facility == null || !empireId.equals(facility.ownerEntityId())
                || !IndustrialFacility.PUBLIC_STATE.equals(facility.ownershipType())
                || InfrastructureService.find(facility.applicationId()) == null || facility.productionEquipment() == null) return false;
        String system = ConstructionMaterials.systemForBody(state, facility.planetId());
        return state.empires().stream().anyMatch(e -> e.id().equals(empireId) && e.controlledSystemIds().contains(system))
                && workers <= facility.productionEquipment().referenceCrew();
    }
    @Override public GameState apply(GameState state) {
        if (!validate(state)) return state;
        return state.withIndustrialFacilities(state.industrialFacilities().stream().map(f -> !f.id().equals(facilityId) ? f
                : new IndustrialFacility(f.id(), f.planetId(), f.applicationId(), f.ownerEntityId(), f.ownershipType(), f.tier(),
                        workers, f.workerProfessionId(), f.isUndergoingExpansion(), f.expansionProgress(), f.productionEquipment())).toList());
    }
}
