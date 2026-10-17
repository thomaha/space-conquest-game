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
 * @param weaponApplicationId developed application supplying each craft's weapon
 * @param targetPriority   tactical engagement priority (CAPITAL_SHIPS, BOMBERS, FIGHTERS, TORPEDOES)
 */
public record CarrierWing(
        String id,
        String carrierShipId,
        String wingType,
        int activeCraftCount,
        int maxCraftCount,
        double hullPerCraft,
        String weaponApplicationId,
        String targetPriority,
        com.spaceconquest.engine.ship.ShipModule weapon,
        AuxiliaryWeaponStores stores,
        AuxiliaryHardware hardware
) {
    public static final String TYPE_INTERCEPTOR_FIGHTER = "INTERCEPTOR_FIGHTER";
    public static final String TYPE_TORPEDO_BOMBER = "TORPEDO_BOMBER";

    public static final String TARGET_CAPITAL_SHIPS = "CAPITAL_SHIPS";
    public static final String TARGET_BOMBERS = "BOMBERS";
    public static final String TARGET_FIGHTERS = "FIGHTERS";
    public static final String TARGET_TORPEDOES = "TORPEDOES";

    public CarrierWing {
        if (hardware == null) hardware = AuxiliaryApplicationCatalog.baseline("WING");
        if (!hardware.kind().equals("WING")) throw new IllegalArgumentException("Invalid wing chassis");
        if (stores == null) stores = AuxiliaryWeaponStores.empty();
        if (activeCraftCount < 0 || maxCraftCount < activeCraftCount) throw new IllegalArgumentException("Invalid wing count");
        if (wingType == null) wingType = TYPE_INTERCEPTOR_FIGHTER;
        if (targetPriority == null) targetPriority = TARGET_CAPITAL_SHIPS;
        if (weapon == null) weapon = com.spaceconquest.engine.ship.CombatModuleCatalog.moduleForApplication(weaponApplicationId);
        if (!weapon.id().equals(com.spaceconquest.engine.ship.CombatModuleCatalog.moduleForApplication(weaponApplicationId).id()))
            throw new IllegalArgumentException("Wing weapon does not match its application");
    }
    public CarrierWing(String id, String carrierShipId, String wingType, int activeCraftCount,
                      int maxCraftCount, double hullPerCraft, String weaponApplicationId, String targetPriority,
                      com.spaceconquest.engine.ship.ShipModule weapon, AuxiliaryWeaponStores stores) {
        this(id, carrierShipId, wingType, activeCraftCount, maxCraftCount, hullPerCraft, weaponApplicationId, targetPriority, weapon, stores, null);
    }
    public CarrierWing(String id, String carrierShipId, String wingType, int activeCraftCount,
                      int maxCraftCount, double hullPerCraft, String weaponApplicationId, String targetPriority,
                      com.spaceconquest.engine.ship.ShipModule weapon) {
        this(id, carrierShipId, wingType, activeCraftCount, maxCraftCount, hullPerCraft, weaponApplicationId, targetPriority, weapon, null);
    }
    public CarrierWing withStores(AuxiliaryWeaponStores supplies) {
        return new CarrierWing(id, carrierShipId, wingType, activeCraftCount, maxCraftCount, hullPerCraft,
                weaponApplicationId, targetPriority, weapon, supplies, hardware);
    }
    public CarrierWing(String id, String carrierShipId, String wingType, int activeCraftCount,
                      int maxCraftCount, double hullPerCraft, String weaponApplicationId, String targetPriority) {
        this(id, carrierShipId, wingType, activeCraftCount, maxCraftCount, hullPerCraft, weaponApplicationId, targetPriority, null);
    }
    public double damagePerCraft() {
        return com.spaceconquest.engine.ship.WeaponSalvo.of(weapon).damage();
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public double getTotalFirepower() {
        return activeCraftCount * damagePerCraft();
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public double getTotalHull() {
        return activeCraftCount * hullPerCraft;
    }
}
