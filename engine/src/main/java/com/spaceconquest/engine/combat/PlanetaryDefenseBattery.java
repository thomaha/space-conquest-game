package com.spaceconquest.engine.combat;

/**
 * Surface-to-orbit planetary defense battery engaging hostile blockading fleets and descent transports.
 *
 * @param id                    unique battery identifier
 * @param planetId              host planet identifier
 * @param weaponApplicationId   developed application supplying the battery weapon
 * @param surfaceSlotsOccupied  surface area slots occupied
 * @param isOperational         true if battery is active and staffed
 */
public record PlanetaryDefenseBattery(
        String id,
        String planetId,
        String weaponApplicationId,
        int surfaceSlotsOccupied,
        boolean isOperational,
        com.spaceconquest.engine.ship.ShipModule weapon,
        AuxiliaryWeaponStores stores,
        AuxiliaryHardware hardware,
        double hullHealth,
        com.spaceconquest.engine.ship.ShipModuleDamage moduleDamage
) {
    public PlanetaryDefenseBattery {
        if (hardware == null) hardware = AuxiliaryApplicationCatalog.baseline("BATTERY");
        if (!hardware.kind().equals("BATTERY")) throw new IllegalArgumentException("Invalid battery chassis");
        if (moduleDamage == null) { moduleDamage = com.spaceconquest.engine.ship.ShipModuleDamage.empty(); hullHealth = hardware.value("hullHealth"); }
        if (!Double.isFinite(hullHealth) || hullHealth < 0) throw new IllegalArgumentException("Invalid battery health");
        if (stores == null) stores = AuxiliaryWeaponStores.empty();
        if (weapon == null) weapon = com.spaceconquest.engine.ship.CombatModuleCatalog.moduleForApplication(weaponApplicationId);
        if (!weapon.id().equals(com.spaceconquest.engine.ship.CombatModuleCatalog.moduleForApplication(weaponApplicationId).id()))
            throw new IllegalArgumentException("Battery weapon does not match its application");
    }
    public PlanetaryDefenseBattery(String id, String planetId, String weaponApplicationId, int surfaceSlotsOccupied,
                                   boolean isOperational, com.spaceconquest.engine.ship.ShipModule weapon, AuxiliaryWeaponStores stores) {
        this(id, planetId, weaponApplicationId, surfaceSlotsOccupied, isOperational, weapon, stores, null, 0, null);
    }
    public PlanetaryDefenseBattery(String id, String planetId, String weaponApplicationId, int surfaceSlotsOccupied,
                                   boolean isOperational, com.spaceconquest.engine.ship.ShipModule weapon) {
        this(id, planetId, weaponApplicationId, surfaceSlotsOccupied, isOperational, weapon, null);
    }
    public PlanetaryDefenseBattery withStores(AuxiliaryWeaponStores supplies) {
        return new PlanetaryDefenseBattery(id, planetId, weaponApplicationId, surfaceSlotsOccupied, isOperational, weapon, supplies, hardware, hullHealth, moduleDamage);
    }
    public PlanetaryDefenseBattery withDamage(double health, com.spaceconquest.engine.ship.ShipModuleDamage damage) {
        return new PlanetaryDefenseBattery(id, planetId, weaponApplicationId, surfaceSlotsOccupied, isOperational && health > 0,
                weapon, stores, hardware, health, damage);
    }
    public String combatEntityId() { return "surface-battery-" + id; }
    public PlanetaryDefenseBattery(String id, String planetId, String weaponApplicationId, int surfaceSlotsOccupied, boolean isOperational) {
        this(id, planetId, weaponApplicationId, surfaceSlotsOccupied, isOperational, null);
    }
    public double damagePerRound() {
        return com.spaceconquest.engine.ship.WeaponSalvo.of(weapon).damage();
    }
}
