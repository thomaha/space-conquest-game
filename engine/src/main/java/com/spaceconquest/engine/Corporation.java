package com.spaceconquest.engine;

import java.util.List;

/**
 * Represents an autonomous private corporation operating in the galaxy.
 *
 * @param id                     unique identifier
 * @param name                   display name
 * @param empireId               home empire identifier
 * @param headquartersEntityId   celestial body or station where headquarters is located
 * @param marketOrientation      market specialization ("EXTRACTION", "METALLURGY", "AGRICULTURE", "TRANSPORT", "CONSUMER")
 * @param liquidCapitalReserves  liquid capital available for investments and transactions
 * @param ownedFacilityIds       list of owned industrial or commercial facility IDs
 * @param ownedShipIds           list of owned cargo haulers and mining ships
 * @param claimedVeinIds         list of claimed geological veins
 */
public record Corporation(
        String id,
        String name,
        String empireId,
        String headquartersEntityId,
        String marketOrientation,
        double liquidCapitalReserves,
        List<String> ownedFacilityIds,
        List<String> ownedShipIds,
        List<String> claimedVeinIds
) {
    public Corporation {
        if (ownedFacilityIds == null) ownedFacilityIds = List.of();
        if (ownedShipIds == null) ownedShipIds = List.of();
        if (claimedVeinIds == null) claimedVeinIds = List.of();
    }
}
