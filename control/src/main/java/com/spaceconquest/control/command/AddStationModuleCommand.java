package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.macrostructure.StationModule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Command to install a functional module onto an existing orbital space station.
 */
public record AddStationModuleCommand(
        String stationId,
        String moduleName,
        String moduleType,
        int slotSize,
        double dryMassKg,
        double powerDrawKw,
        double powerOutputKw,
        String professionId,
        int requiredWorkers
) implements GameCommand {

    @Override
    public boolean validate(GameState state) {
        if (state == null || stationId == null || moduleType == null) {
            return false;
        }
        OrbitalStation station = state.orbitalStations().stream()
                .filter(s -> s.id().equals(stationId))
                .findFirst()
                .orElse(null);
        if (station == null) return false;
        return station.hasAvailableSlots(slotSize);
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) {
            return state;
        }

        String modId = "mod_" + UUID.randomUUID().toString().substring(0, 8);
        StationModule newMod = new StationModule(
                modId,
                moduleName != null ? moduleName : moduleType,
                moduleType,
                slotSize > 0 ? slotSize : 6,
                dryMassKg > 0.0 ? dryMassKg : 10000.0,
                powerDrawKw,
                powerOutputKw,
                Map.of(),
                professionId != null ? professionId : "technician",
                requiredWorkers,
                true
        );

        List<OrbitalStation> updatedStations = state.orbitalStations().stream().map(st -> {
            if (st.id().equals(stationId)) {
                List<StationModule> newMods = new ArrayList<>(st.modules());
                newMods.add(newMod);
                return new OrbitalStation(
                        st.id(), st.name(), st.systemId(), st.planetOrbitId(),
                        st.ownerEntityId(), st.ownershipType(), st.totalSlots(),
                        newMods, st.storedCargoKg(),
                        st.currentPowerGenerationKw() + powerOutputKw,
                        st.currentPowerDemandKw() + powerDrawKw,
                        st.currentShieldHealth(), st.maxShieldHealth(),
                        st.currentHullHealth(), st.maxHullHealth(),
                        st.armorMaterialId(), st.armorThicknessCm(),
                        st.isOperational()
                );
            }
            return st;
        }).toList();

        return state.toBuilder()
                .orbitalStations(updatedStations)
                .build();
    }
}
