package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.ConstructionMaterials;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.ship.ShipyardWorkCapacity;

import java.util.ArrayList;
import java.util.List;

/** Sets the persistent worker allocation for an empire-owned surface shipyard. */
public record SetSurfaceShipyardStaffingCommand(String empireId, String facilityId,
                                                int allocatedWorkers) implements GameCommand {
    @Override
    public boolean validate(GameState state) {
        if (state == null || empireId == null || facilityId == null || allocatedWorkers < 0) return false;
        IndustrialFacility facility = findFacility(state);
        if (facility == null || !empireId.equals(facility.ownerEntityId())) return false;
        String systemId = ConstructionMaterials.systemForBody(state, facility.planetId());
        boolean controlled = state.empires().stream().anyMatch(empire -> empireId.equals(empire.id())
                && systemId != null && empire.controlledSystemIds().contains(systemId));
        if (!controlled) return false;
        return allocatedWorkers <= facility.allocatedWorkers()
                || allocatedWorkers <= ShipyardWorkCapacity.assignableWorkers(state, facility);
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) return state;
        List<IndustrialFacility> updated = new ArrayList<>(state.industrialFacilities());
        for (int index = 0; index < updated.size(); index++) {
            IndustrialFacility facility = updated.get(index);
            if (!facilityId.equals(facility.id())) continue;
            updated.set(index, new IndustrialFacility(facility.id(), facility.planetId(),
                    facility.applicationId(), facility.ownerEntityId(), facility.ownershipType(),
                    facility.tier(), allocatedWorkers, facility.workerProfessionId(),
                    facility.isUndergoingExpansion(), facility.expansionProgress()));
            break;
        }
        return state.withIndustrialFacilities(updated);
    }

    private IndustrialFacility findFacility(GameState state) {
        return state.industrialFacilities().stream()
                .filter(facility -> facilityId.equals(facility.id())
                        && ShipyardWorkCapacity.SURFACE_SHIPYARD_APPLICATION_ID
                        .equals(facility.applicationId()))
                .findFirst().orElse(null);
    }
}
