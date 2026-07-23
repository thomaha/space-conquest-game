package com.spaceconquest.engine;

import java.util.List;

public record GameState(long turn, String status, List<SolarSystem> solarSystems) {
    public GameState() {
        this(0, "INITIALIZING", List.of());
    }
}
