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
 * @param powerBalanceKw                   net electrical output balance (generation - draw)
 * @param calculatedStructuralIntegrity   evaluated structural integrity score
 * @param minLaunchThrustRequiredN         required minimum thrust to blast off from planet surface
 * @param totalThrustN                     aggregate propulsion thrust in Newtons
 * @param isValidForLaunch                 true if design satisfies launch thrust-to-mass barrier
 * @param isProprietaryCorporateDesign     true if created by private corporate AI
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
        double powerBalanceKw,
        double calculatedStructuralIntegrity,
        double minLaunchThrustRequiredN,
        double totalThrustN,
        boolean isValidForLaunch,
        boolean isProprietaryCorporateDesign
) {
    public ShipDesign {
        if (equippedModuleIds == null) equippedModuleIds = List.of();
    }
}
