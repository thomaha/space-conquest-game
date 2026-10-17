package com.spaceconquest.engine.ship;

import java.util.List;

/**
 * Blueprint specification for a modular starframe vessel.
 *
 * @param id                               unique design identifier
 * @param name                             display name of the blueprint
 * @param ownerEntityId                    empire or corporation identifier owning the blueprint
 * @param role                             operational classification
 * @param hullMaterialId                   structural material ID used for frame
 * @param equippedModuleIds                list of equipped module IDs
 * @param armorMaterialId                  material ID used for external shell plating
 * @param armorThicknessCm                 thickness of external armor plating in cm
 * @param totalDryMassKg                   aggregate dry mass of frame, modules and armor
 * @param maxCargoMassKg                   maximum capacity of internal cargo holds
 * @param fuelCapacityKg                   maximum propulsion propellant stored in fuel tanks
 * @param powerBalanceKw                   net electrical output balance (generation - draw)
 * @param calculatedStructuralIntegrity   evaluated structural integrity score
 * @param minLaunchThrustRequiredN         required minimum thrust to blast off from planet surface
 * @param totalThrustN                     aggregate propulsion thrust in Newtons
 * @param isValidForLaunch                 true if design satisfies launch thrust-to-mass barrier
 * @param isProprietaryCorporateDesign     true if created by private corporate AI
 * @param manufacturingProfile            captured optimization cost multiplier and manufacturing complexity
 * @param sensorProfile                   frozen sensor range and survey strength of installed modules
 * @param combatModules                   immutable application-derived combat hardware captured for this blueprint
 */
public record ShipDesign(
        String id,
        String name,
        String ownerEntityId,
        String role,
        String hullMaterialId,
        List<String> equippedModuleIds,
        String armorMaterialId,
        double armorThicknessCm,
        double totalDryMassKg,
        double maxCargoMassKg,
        double fuelCapacityKg,
        double powerBalanceKw,
        double calculatedStructuralIntegrity,
        double minLaunchThrustRequiredN,
        double totalThrustN,
        boolean isValidForLaunch,
        boolean isProprietaryCorporateDesign,
        ShipManufacturingProfile manufacturingProfile,
        ShipPowerProfile powerProfile,
        ShipSensorProfile sensorProfile,
        java.util.Map<String, ShipModule> combatModules,
        ShipHullFrame hullFrame,
        ShipBlueprintAssembly assembly
) {
    public ShipDesign {
        manufacturingProfile = manufacturingProfile == null ? ShipManufacturingProfile.baseline() : manufacturingProfile;
        if (equippedModuleIds == null) equippedModuleIds = List.of();
        equippedModuleIds = List.copyOf(equippedModuleIds);
        combatModules = combatModules == null ? CombatModuleCatalog.capture(equippedModuleIds.stream()
                .map(CombatModuleCatalog::module).filter(java.util.Objects::nonNull).toList()) : java.util.Map.copyOf(combatModules);
        combatModules.forEach((moduleId, module) -> {
            if (!moduleId.equals(module.id()) || CombatModuleCatalog.application(moduleId) == null)
                throw new IllegalArgumentException("Unlinked combat module: " + moduleId);
        });
        if (!Double.isFinite(fuelCapacityKg) || fuelCapacityKg < 0.0)
            throw new IllegalArgumentException("Invalid fuel capacity");
        if (assembly != null && !assembly.installations().stream().map(item -> item.hardware().id()).toList().equals(equippedModuleIds))
            throw new IllegalArgumentException("Assembly does not match blueprint components");
    }
    public ShipDesign(String id, String name, String ownerEntityId, String role, String hullMaterialId, List<String> equippedModuleIds,
                      String armorMaterialId, double armorThicknessCm, double totalDryMassKg, double maxCargoMassKg, double fuelCapacityKg,
                      double powerBalanceKw, double calculatedStructuralIntegrity, double minLaunchThrustRequiredN, double totalThrustN,
                      boolean isValidForLaunch, boolean isProprietaryCorporateDesign, ShipManufacturingProfile manufacturingProfile,
                      ShipPowerProfile powerProfile, ShipSensorProfile sensorProfile, java.util.Map<String, ShipModule> combatModules, ShipHullFrame hullFrame) {
        this(id, name, ownerEntityId, role, hullMaterialId, equippedModuleIds, armorMaterialId, armorThicknessCm, totalDryMassKg,
                maxCargoMassKg, fuelCapacityKg, powerBalanceKw, calculatedStructuralIntegrity, minLaunchThrustRequiredN, totalThrustN,
                isValidForLaunch, isProprietaryCorporateDesign, manufacturingProfile, powerProfile, sensorProfile, combatModules, hullFrame, null);
    }
    public ShipDesign(String id, String name, String ownerEntityId, String role, String hullMaterialId, List<String> equippedModuleIds,
                      String armorMaterialId, double armorThicknessCm, double totalDryMassKg, double maxCargoMassKg, double fuelCapacityKg,
                      double powerBalanceKw, double calculatedStructuralIntegrity, double minLaunchThrustRequiredN, double totalThrustN,
                      boolean isValidForLaunch, boolean isProprietaryCorporateDesign, ShipManufacturingProfile manufacturingProfile,
                      ShipPowerProfile powerProfile, ShipSensorProfile sensorProfile, java.util.Map<String, ShipModule> combatModules) {
        this(id, name, ownerEntityId, role, hullMaterialId, equippedModuleIds, armorMaterialId, armorThicknessCm, totalDryMassKg,
                maxCargoMassKg, fuelCapacityKg, powerBalanceKw, calculatedStructuralIntegrity, minLaunchThrustRequiredN, totalThrustN,
                isValidForLaunch, isProprietaryCorporateDesign, manufacturingProfile, powerProfile, sensorProfile, combatModules, null);
    }

    public ShipDesign(String id, String name, String ownerEntityId, String role,
                      String hullMaterialId, List<String> equippedModuleIds, String armorMaterialId,
                      double armorThicknessCm, double totalDryMassKg, double maxCargoMassKg,
                      double fuelCapacityKg, double powerBalanceKw, double calculatedStructuralIntegrity,
                      double minLaunchThrustRequiredN, double totalThrustN, boolean isValidForLaunch,
                      boolean isProprietaryCorporateDesign, ShipManufacturingProfile manufacturingProfile,
                      ShipPowerProfile powerProfile, ShipSensorProfile sensorProfile) {
        this(id, name, ownerEntityId, role, hullMaterialId, equippedModuleIds, armorMaterialId, armorThicknessCm,
                totalDryMassKg, maxCargoMassKg, fuelCapacityKg, powerBalanceKw, calculatedStructuralIntegrity,
                minLaunchThrustRequiredN, totalThrustN, isValidForLaunch, isProprietaryCorporateDesign,
                manufacturingProfile, powerProfile, sensorProfile, null);
    }

    public ShipDesign(String id, String name, String ownerEntityId, String role,
                      String hullMaterialId, List<String> equippedModuleIds, String armorMaterialId,
                      double armorThicknessCm, double totalDryMassKg, double maxCargoMassKg,
                      double fuelCapacityKg, double powerBalanceKw, double calculatedStructuralIntegrity,
                      double minLaunchThrustRequiredN, double totalThrustN, boolean isValidForLaunch,
                      boolean isProprietaryCorporateDesign, ShipManufacturingProfile manufacturingProfile,
                      ShipPowerProfile powerProfile) {
        this(id, name, ownerEntityId, role, hullMaterialId, equippedModuleIds, armorMaterialId, armorThicknessCm,
                totalDryMassKg, maxCargoMassKg, fuelCapacityKg, powerBalanceKw, calculatedStructuralIntegrity,
                minLaunchThrustRequiredN, totalThrustN, isValidForLaunch, isProprietaryCorporateDesign,
                manufacturingProfile, powerProfile, null);
    }

    public ShipDesign(String id, String name, String ownerEntityId, String role,
                      String hullMaterialId, List<String> equippedModuleIds, String armorMaterialId,
                      double armorThicknessCm, double totalDryMassKg, double maxCargoMassKg,
                      double fuelCapacityKg, double powerBalanceKw, double calculatedStructuralIntegrity,
                      double minLaunchThrustRequiredN, double totalThrustN, boolean isValidForLaunch,
                      boolean isProprietaryCorporateDesign, ShipManufacturingProfile manufacturingProfile) {
        this(id, name, ownerEntityId, role, hullMaterialId, equippedModuleIds, armorMaterialId,
                armorThicknessCm, totalDryMassKg, maxCargoMassKg, fuelCapacityKg, powerBalanceKw,
                calculatedStructuralIntegrity, minLaunchThrustRequiredN, totalThrustN,
                isValidForLaunch, isProprietaryCorporateDesign, manufacturingProfile, null);
    }

    public ShipDesign(String id, String name, String ownerEntityId, String role,
                      String hullMaterialId, List<String> equippedModuleIds,
                      String armorMaterialId, double armorThicknessCm,
                      double totalDryMassKg, double maxCargoMassKg, double fuelCapacityKg,
                      double powerBalanceKw, double calculatedStructuralIntegrity,
                      double minLaunchThrustRequiredN, double totalThrustN,
                      boolean isValidForLaunch, boolean isProprietaryCorporateDesign) {
        this(id, name, ownerEntityId, role, hullMaterialId, equippedModuleIds, armorMaterialId,
                armorThicknessCm, totalDryMassKg, maxCargoMassKg, fuelCapacityKg, powerBalanceKw,
                calculatedStructuralIntegrity, minLaunchThrustRequiredN, totalThrustN,
                isValidForLaunch, isProprietaryCorporateDesign, ShipManufacturingProfile.baseline());
    }

    public ShipDesign(String id, String name, String ownerEntityId, String role,
                      String hullMaterialId, List<String> equippedModuleIds,
                      String armorMaterialId, double armorThicknessCm,
                      double totalDryMassKg, double maxCargoMassKg, double powerBalanceKw,
                      double calculatedStructuralIntegrity, double minLaunchThrustRequiredN,
                      double totalThrustN, boolean isValidForLaunch,
                      boolean isProprietaryCorporateDesign) {
        this(id, name, ownerEntityId, role, hullMaterialId, equippedModuleIds,
                armorMaterialId, armorThicknessCm, totalDryMassKg, maxCargoMassKg,
                Math.max(100.0, totalDryMassKg * 0.5), powerBalanceKw,
                calculatedStructuralIntegrity, minLaunchThrustRequiredN, totalThrustN,
                isValidForLaunch, isProprietaryCorporateDesign);
    }
    /** Condition-scaled live capability; the no-argument accessor remains the blueprint rating. */
    public ShipPowerProfile powerProfile(ShipInstance ship) {
        return ShipElectricalDamage.profile(this, ship);
    }
}
