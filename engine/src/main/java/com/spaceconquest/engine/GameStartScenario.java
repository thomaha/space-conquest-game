package com.spaceconquest.engine;

import java.util.List;
import java.time.LocalDateTime;

/**
 * Represents the starting technological and expansion era chosen by the player at game start.
 */
public enum GameStartScenario {
    PRE_SPACE_FLIGHT(
            "Contemporary industry",
            "Industrialized homeworld with funded power, food and manufacturing and all tracked starting population needs met",
            LocalDateTime.of(2027, 1, 1, 8, 0),
            List.of("electricity", "rocketry", "nuclear_fission", "industrial_production"),
            List.of()
    ),
    ADVANCED_ROCKETRY(
            "Advanced rocketry",
            "Healthy industrialized homeworld, colonies in the starting system and offworld mining bases",
            LocalDateTime.of(2050, 1, 1, 8, 0),
            List.of("electricity", "rocketry", "nuclear_fission", "industrial_production",
                    "computers", "robotics", "supply_chain_automation"),
            List.of("cargo_transport_mk1", "mining_vessel_mk1")
    ),
    BASIC_WARP(
            "Basic warp technology",
            "Healthy industrialized homeworld, extensive expansion in the starting system and nearby bases",
            LocalDateTime.of(2200, 1, 1, 8, 0),
            List.of("electricity", "rocketry", "nuclear_fission", "industrial_production",
                    "computers", "robotics", "supply_chain_automation", "superconductors",
                    "geological_prospecting", "nuclear_fusion", "energy_fields",
                    "gravitational_engineering", "warp"),
            List.of("cargo_transport_mk1", "mining_vessel_mk1", "scout_corvette_mk1", "colony_transport_mk1")
    );

    private final String displayName;
    private final String description;
    private final LocalDateTime startTime;
    private final List<String> startingTechnologies;
    private final List<String> startingShipDesigns;

    GameStartScenario(String displayName, String description, LocalDateTime startTime,
                      List<String> startingTechnologies, List<String> startingShipDesigns) {
        this.displayName = displayName;
        this.description = description;
        this.startTime = startTime;
        this.startingTechnologies = startingTechnologies;
        this.startingShipDesigns = startingShipDesigns;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public LocalDateTime startTime() {
        return startTime;
    }

    @Override
    public String toString() {
        return startTime.getYear() + " — " + displayName;
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
            if (scenario.displayName.equalsIgnoreCase(name.trim())
                    || scenario.name().equalsIgnoreCase(name.trim())
                    || scenario.toString().equalsIgnoreCase(name.trim())) {
                return scenario;
            }
        }
        return PRE_SPACE_FLIGHT;
    }
}
