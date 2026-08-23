package com.spaceconquest.engine;

import java.util.List;

/**
 * Represents a criminal syndicate or pirate faction fueled by black market tariff leakage.
 *
 * @param id                 unique identifier
 * @param name               display name
 * @param empireId           originating home empire identifier
 * @param baseSystemId       home solar system coordinate base
 * @param shadowCapitalPool  accumulated illicit wealth used for illegal ship construction
 * @param rogueShipIds       list of stealth cargo freighters and rogue mine ships
 */
public record ShadowSyndicate(
        String id,
        String name,
        String empireId,
        String baseSystemId,
        double shadowCapitalPool,
        List<String> rogueShipIds
) {
    public ShadowSyndicate {
        if (rogueShipIds == null) rogueShipIds = List.of();
    }
}
