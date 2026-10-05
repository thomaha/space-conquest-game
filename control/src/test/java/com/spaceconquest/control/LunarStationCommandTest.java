package com.spaceconquest.control;

import com.spaceconquest.control.command.BuildOrbitalStationCommand;
import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class LunarStationCommandTest {
    private BuildOrbitalStationCommand command(double altitude) {
        return new BuildOrbitalStationCommand("Moon port", "sol", "moon", "owner",
                OrbitalStation.OWNERSHIP_PUBLIC_STATE, 20, "steel", 1, altitude);
    }
    @Test void lunarConstructionUsesMoonSphereAndRetainsChosenAltitude() throws Exception {
        var owner = new Empire("owner", "Owner", "human", "Individualist", 1e6, 0, List.of("sol"), List.of(), Map.of(),
                List.of("space_stations", "rocketry"), List.of());
        var state = GameState.builder().solarSystems(DataModelLoader.loadSolarSystems()).empires(List.of(owner)).build();
        for (double altitude : List.of(100.0, 500.0, 8000.0)) {
            var command = command(altitude); assertTrue(command.validate(state));
            var changed = command.apply(state);
            assertEquals(altitude, changed.constructionProjects().getFirst().parkingAltitudeKm());
            assertEquals("moon", changed.constructionProjects().getFirst().targetCelestialId());
            assertTrue(state.constructionProjects().isEmpty());
        }
        assertFalse(command(100000).validate(state));
        assertFalse(command(Double.NaN).validate(state));
        assertFalse(command(-1).validate(state));
    }
}
