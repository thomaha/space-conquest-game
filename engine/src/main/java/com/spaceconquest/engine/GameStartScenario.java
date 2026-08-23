package com.spaceconquest.engine;

import java.util.List;

/**
 * Represents the starting technological and expansion era chosen by the player at game start.
 */
public enum GameStartScenario {
    PRE_SPACE_FLIGHT(
            "Pre space flight",
            "Only home planet and basic rocketry technology",
            List.of("electricity", "rocketry"),
            List.of()
    ),
    ADVANCED_ROCKETRY(
            "Advanced rocketry",
            "Colonized the most friendly planets in starting solar system, some space bases and several offworld mining bases",
            List.of("electricity", "rocketry", "nuclear_fission", "basic_automation", "deep_core_drilling"),
            List.of("cargo_transport_mk1", "mining_vessel_mk1")
    ),
    BASIC_WARP(
            "Basic warp technology",
            "Extensive expansion in starting system and some bases in the closest neighboring systems",
            List.of("electricity", "rocketry", "nuclear_fission", "nuclear_fusion", "energy_fields", "gravitational_engineering", "warp"),
            List.of("cargo_transport_mk1", "mining_vessel_mk1", "scout_corvette_mk1", "colony_transport_mk1")
    );

    private final String displayName;
    private final String description;
    private final List<String> startingTechnologies;
    private final List<String> startingShipDesigns;

    GameStartScenario(String displayName, String description, List<String> startingTechnologies, List<String> startingShipDesigns) {
        this.displayName = displayName;
        this.description = description;
        this.startingTechnologies = startingTechnologies;
        this.startingShipDesigns = startingShipDesigns;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public List<String> startingTechnologies() {
        return startingTechnologies;
    }

    public List<String> startingShipDesigns() {
        return startingShipDesigns;
    }

    public static GameStartScenario fromDisplayName(String name) {
        if (name == null) return PRE_SPACE_FLIGHT;
        for (GameStartScenario scenario : values()) {
            if (scenario.displayName.equalsIgnoreCase(name.trim()) || scenario.name().equalsIgnoreCase(name.trim())) {
                return scenario;
            }
        }
        return PRE_SPACE_FLIGHT;
    }
}
