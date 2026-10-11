package com.spaceconquest.engine.ship;

/** Optional electrical load; emitted charge is paid and bounded by surviving hardware. */
public final class ShipShieldRecharge {
    public static final double KWH_PER_POINT = 2;
    private ShipShieldRecharge() {}
    static double capacity(ShipDesign design, ShipInstance ship) {
        return ship.currentHullHealth() <= 0 ? 0 : ship.moduleDamage().capability(design, "shieldCapacity");
    }
    static double requestedKw(ShipDesign design, ShipInstance ship, double hours) {
        if (design == null || design.powerProfile() == null || ship.currentHullHealth() <= 0 || hours <= 0) return 0;
        long emitters = design.equippedModuleIds().stream().filter(CombatModuleCatalog.SHIELD::equals).count();
        if (design.powerProfile().hotelKw() + 1e-9 < emitters * CombatModuleCatalog.module(CombatModuleCatalog.SHIELD).powerDrawKw()) return 0;
        double missing = Math.max(0, capacity(design, ship) - Math.max(0, ship.currentShieldHealth()));
        return Math.min(missing / hours, ship.moduleDamage().capability(design, "shieldRechargePointsPerHour")) * KWH_PER_POINT;
    }
    static ShipInstance apply(ShipInstance ship, ShipDesign design, double deliveredKwh) {
        double charge = Math.min(capacity(design, ship), Math.max(0, ship.currentShieldHealth()) + deliveredKwh / KWH_PER_POINT);
        return new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), ship.currentHullHealth(), charge,
                ship.currentFuelKg(), ship.storedCargoKg(), ship.passengerCount(), ship.passengerRaceId(), ship.transitMode(),
                ship.powerState(), ship.supplyState(), ship.moduleDamage());
    }
}
