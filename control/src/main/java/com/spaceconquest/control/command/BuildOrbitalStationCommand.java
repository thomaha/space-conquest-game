package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.macrostructure.StationModule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Command to construct a new orbital space station.
 */
public record BuildOrbitalStationCommand(
        String name,
        String systemId,
        String planetOrbitId,
        String ownerEntityId,
        String ownershipType,
        int totalSlots,
        String armorMaterialId,
        double armorThicknessCm
) implements GameCommand {

    public BuildOrbitalStationCommand(String ownerEntityId, String systemId, String name) {
        this(name, systemId, "low_orbit", ownerEntityId, OrbitalStation.OWNERSHIP_PUBLIC_STATE, 20, "steel", 5.0);
    }

    @Override
    public boolean validate(GameState state) {
        if (state == null || systemId == null || ownerEntityId == null) {
            return false;
        }
        return totalSlots > 0;
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) {
            return state;
        }

        String stationId = "station_" + UUID.randomUUID().toString().substring(0, 8);
        StationModule controlMod = new StationModule(
                "mod_ctrl_" + stationId, "Command Core", StationModule.TYPE_CONTROL,
                6, 12000.0, 50.0, 0.0, Map.of(), "bureaucrat", 5, true
        );
        StationModule powerMod = new StationModule(
                "mod_pwr_" + stationId, "Fission Power Hub", StationModule.TYPE_POWER,
                10, 22000.0, 0.0, 300.0, Map.of(), "technician", 4, true
        );

        OrbitalStation newStation = new OrbitalStation(
                stationId,
                name != null ? name : "Orbital Station " + stationId,
                systemId,
                planetOrbitId != null ? planetOrbitId : "",
                ownerEntityId,
                ownershipType != null ? ownershipType : OrbitalStation.OWNERSHIP_PUBLIC_STATE,
                totalSlots,
                List.of(controlMod, powerMod),
                Map.of(),
                300.0, 50.0,
                500.0, 500.0,
                1000.0, 1000.0,
                armorMaterialId != null ? armorMaterialId : "steel",
                armorThicknessCm > 0.0 ? armorThicknessCm : 5.0,
                true
        );

        List<OrbitalStation> updatedStations = new ArrayList<>(state.orbitalStations());
        updatedStations.add(newStation);

        return state.toBuilder()
                .orbitalStations(updatedStations)
                .build();
    }
}
