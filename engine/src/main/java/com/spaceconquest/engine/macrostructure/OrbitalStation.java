package com.spaceconquest.engine.macrostructure;

import java.util.List;
import java.util.Map;

/**
 * Represents a space station orbiting a celestial body or positioned at strategic space coordinates.
 *
 * @param id                        unique station identifier
 * @param name                      display name of the station
 * @param systemId                  host solar system identifier
 * @param planetOrbitId             orbital celestial body ID (or empty if deep space)
 * @param ownerEntityId             empire or corporate owner identifier
 * @param ownershipType             ownership model (PUBLIC_STATE, PRIVATE_CORPORATE, HIVE_GRID)
 * @param totalSlots                maximum module slots available on starframe
 * @param modules                   list of attached StationModules
 * @param storedCargoKg             material inventory storage manifest
 * @param currentPowerGenerationKw  total active power generation
 * @param currentPowerDemandKw      total active power demand
 * @param currentShieldHealth       current shield integrity
 * @param maxShieldHealth           maximum shield capacity
 * @param currentHullHealth         current structural hit points
 * @param maxHullHealth             maximum structural hit points
 * @param armorMaterialId           external armor plating material
 * @param armorThicknessCm          thickness of armor overlay
 * @param isOperational             true if control module is online and operational
 */
public record OrbitalStation(
        String id,
        String name,
        String systemId,
        String planetOrbitId,
        String ownerEntityId,
        String ownershipType,
        int totalSlots,
        List<StationModule> modules,
        Map<String, Double> storedCargoKg,
        double currentPowerGenerationKw,
        double currentPowerDemandKw,
        double currentShieldHealth,
        double maxShieldHealth,
        double currentHullHealth,
        double maxHullHealth,
        String armorMaterialId,
        double armorThicknessCm,
        boolean isOperational
) {
    public static final String OWNERSHIP_PUBLIC_STATE = "PUBLIC_STATE";
    public static final String OWNERSHIP_PRIVATE_CORPORATE = "PRIVATE_CORPORATE";
    public static final String OWNERSHIP_HIVE_GRID = "HIVE_GRID";

    public OrbitalStation {
        if (modules == null) modules = List.of();
        if (storedCargoKg == null) storedCargoKg = Map.of();
    }

    public int getAllocatedSlots() {
        return modules.stream().mapToInt(StationModule::slotSize).sum();
    }

    public boolean hasAvailableSlots(int requiredSlots) {
        return getAllocatedSlots() + requiredSlots <= totalSlots;
    }

    public boolean hasModuleType(String type) {
        return modules.stream().anyMatch(m -> m.isOnline() && m.type().equalsIgnoreCase(type));
    }
}
