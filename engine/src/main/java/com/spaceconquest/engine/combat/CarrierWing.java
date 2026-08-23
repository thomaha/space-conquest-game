package com.spaceconquest.engine.combat;

/**
 * Represents a specialized strike craft squadron deployed from a carrier starship.
 *
 * @param id               unique squadron identifier
 * @param carrierShipId    host carrier ShipInstance ID
 * @param wingType         craft type (INTERCEPTOR_FIGHTER, TORPEDO_BOMBER)
 * @param activeCraftCount current operational craft count
 * @param maxCraftCount    maximum hangar capacity
 * @param hullPerCraft     hit points per individual craft
 * @param damagePerCraft   firepower output per operational craft
 * @param targetPriority   tactical engagement priority (CAPITAL_SHIPS, BOMBERS, FIGHTERS, TORPEDOES)
 */
public record CarrierWing(
        String id,
        String carrierShipId,
        String wingType,
        int activeCraftCount,
        int maxCraftCount,
        double hullPerCraft,
        double damagePerCraft,
        String targetPriority
) {
    public static final String TYPE_INTERCEPTOR_FIGHTER = "INTERCEPTOR_FIGHTER";
    public static final String TYPE_TORPEDO_BOMBER = "TORPEDO_BOMBER";

    public static final String TARGET_CAPITAL_SHIPS = "CAPITAL_SHIPS";
    public static final String TARGET_BOMBERS = "BOMBERS";
    public static final String TARGET_FIGHTERS = "FIGHTERS";
    public static final String TARGET_TORPEDOES = "TORPEDOES";

    public CarrierWing {
        if (wingType == null) wingType = TYPE_INTERCEPTOR_FIGHTER;
        if (targetPriority == null) targetPriority = TARGET_CAPITAL_SHIPS;
    }

    public double getTotalFirepower() {
        return activeCraftCount * damagePerCraft;
    }

    public double getTotalHull() {
        return activeCraftCount * hullPerCraft;
    }
}
