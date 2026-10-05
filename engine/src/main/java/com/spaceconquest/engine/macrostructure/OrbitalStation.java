package com.spaceconquest.engine.macrostructure;

import java.util.List;
import java.util.Map;
import com.spaceconquest.engine.Population;

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
        boolean isOperational,
        List<Population> populations,
        Double parkingAltitudeKm
) {
    public static final String OWNERSHIP_PUBLIC_STATE = "PUBLIC_STATE";
    public static final String OWNERSHIP_PRIVATE_CORPORATE = "PRIVATE_CORPORATE";
    public static final String OWNERSHIP_HIVE_GRID = "HIVE_GRID";

    public OrbitalStation {
        if (parkingAltitudeKm != null && (!Double.isFinite(parkingAltitudeKm) || parkingAltitudeKm <= 0))
            throw new IllegalArgumentException("Parking altitude must be finite and above the surface");
        modules = modules == null ? List.of() : List.copyOf(modules);
        storedCargoKg = storedCargoKg == null ? Map.of() : Map.copyOf(storedCargoKg);
        populations = populations == null ? List.of() : List.copyOf(populations);
    }

    public OrbitalStation(String id, String name, String systemId, String planetOrbitId,
                          String ownerEntityId, String ownershipType, int totalSlots,
                          List<StationModule> modules, Map<String, Double> storedCargoKg,
                          double currentPowerGenerationKw, double currentPowerDemandKw,
                          double currentShieldHealth, double maxShieldHealth,
                          double currentHullHealth, double maxHullHealth,
                          String armorMaterialId, double armorThicknessCm, boolean isOperational) {
        this(id, name, systemId, planetOrbitId, ownerEntityId, ownershipType, totalSlots,
                modules, storedCargoKg, currentPowerGenerationKw, currentPowerDemandKw,
                currentShieldHealth, maxShieldHealth, currentHullHealth, maxHullHealth,
                armorMaterialId, armorThicknessCm, isOperational, List.of());
    }

    public OrbitalStation(String id, String name, String systemId, String planetOrbitId, String ownerEntityId,
                         String ownershipType, int totalSlots, List<StationModule> modules, Map<String, Double> storedCargoKg,
                         double currentPowerGenerationKw, double currentPowerDemandKw, double currentShieldHealth,
                         double maxShieldHealth, double currentHullHealth, double maxHullHealth, String armorMaterialId,
                         double armorThicknessCm, boolean isOperational, List<Population> populations) {
        this(id, name, systemId, planetOrbitId, ownerEntityId, ownershipType, totalSlots, modules, storedCargoKg,
                currentPowerGenerationKw, currentPowerDemandKw, currentShieldHealth, maxShieldHealth,
                currentHullHealth, maxHullHealth, armorMaterialId, armorThicknessCm, isOperational, populations, null);
    }

    public double effectiveParkingAltitudeKm() { return parkingAltitudeKm == null ? 500 : parkingAltitudeKm; }

    public OrbitalStation withParkingAltitudeKm(Double altitude) {
        return new OrbitalStation(id, name, systemId, planetOrbitId, ownerEntityId, ownershipType, totalSlots,
                modules, storedCargoKg, currentPowerGenerationKw, currentPowerDemandKw, currentShieldHealth,
                maxShieldHealth, currentHullHealth, maxHullHealth, armorMaterialId, armorThicknessCm,
                isOperational, populations, altitude);
    }

    public OrbitalStation withPopulations(List<Population> value) {
        return new OrbitalStation(id, name, systemId, planetOrbitId, ownerEntityId, ownershipType,
                totalSlots, modules, storedCargoKg, currentPowerGenerationKw, currentPowerDemandKw,
                currentShieldHealth, maxShieldHealth, currentHullHealth, maxHullHealth,
                armorMaterialId, armorThicknessCm, isOperational, value, parkingAltitudeKm);
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

    public long habitationCapacity() {
        return modules.stream().filter(module ->
                StationModule.TYPE_HABITATION.equalsIgnoreCase(module.type()))
                .mapToLong(module -> Math.max(0, module.slotSize()) * 100L).sum();
    }

    public long residentCount() {
        return populations.stream().mapToLong(Population::totalCount).sum();
    }
}
