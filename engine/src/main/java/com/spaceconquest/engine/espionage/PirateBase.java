package com.spaceconquest.engine.espionage;

/**
 * Autonomous hidden pirate stronghold in deep space or an asteroid belt.
 *
 * @param id                   unique base identifier
 * @param syndicateId          controlling shadow syndicate identifier
 * @param systemId             solar system location identifier
 * @param celestialLocationId  asteroid belt or rogue node identifier
 * @param illicitCapitalStored liquid credit pool accumulated from piracy and extortion
 * @param pirateShipCount      active rogue combat and raiding ships
 * @param isHidden             true if undetected by imperial scanners
 */
public record PirateBase(
        String id,
        String syndicateId,
        String systemId,
        String celestialLocationId,
        double illicitCapitalStored,
        int pirateShipCount,
        boolean isHidden
) {}
