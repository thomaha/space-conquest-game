package com.spaceconquest.engine.ship;

/** Optional electrical load; emitted charge is paid and bounded by surviving hardware. */
public final class ShipShieldRecharge {
    public static final double KWH_PER_POINT = CombatModuleCatalog.module(CombatModuleCatalog.SHIELD).operationalStats().get("shieldRechargeKwhPerPoint");
    private ShipShieldRecharge() {}
    private static double kwhPerPoint(ShipDesign design, ShipInstance ship) {
        if (design == null) return Double.POSITIVE_INFINITY;
        double rate = 0, energy = 0;
        for (int mount = 0; mount < design.equippedModuleIds().size(); mount++) {
            var module = CombatModuleCatalog.module(design, design.equippedModuleIds().get(mount));
            if (module == null || !module.operationalStats().containsKey("shieldCapacity")) continue;
            double contribution = module.operationalStats().get("shieldRechargePointsPerHour") * ship.moduleDamage().condition(mount);
            rate += contribution;
            energy += contribution * module.operationalStats().get("shieldRechargeKwhPerPoint");
        }
        return rate > 0 ? energy / rate : Double.POSITIVE_INFINITY;
    }
    static double capacity(ShipDesign design, ShipInstance ship) {
        return ship.currentHullHealth() <= 0 ? 0 : ship.moduleDamage().capability(design, "shieldCapacity");
    }
    static double requestedKw(ShipDesign design, ShipInstance ship, double hours) {
        if (design == null || design.powerProfile() == null || ship.currentHullHealth() <= 0 || hours <= 0) return 0;
        if (design.powerProfile().hotelKw() + 1e-9 < design.equippedModuleIds().stream().map(id -> CombatModuleCatalog.module(design, id))
                .filter(module -> module != null && module.operationalStats().containsKey("shieldCapacity")).mapToDouble(ShipModule::powerDrawKw).sum()) return 0;
        double missing = Math.max(0, capacity(design, ship) - Math.max(0, ship.currentShieldHealth()));
        double rate = ship.moduleDamage().capability(design, "shieldRechargePointsPerHour");
        return missing > 0 && rate > 0 ? Math.min(missing / hours, rate) * kwhPerPoint(design, ship) : 0;
    }
    static ShipInstance apply(ShipInstance ship, ShipDesign design, double deliveredKwh) {
        double charge = Math.min(capacity(design, ship), Math.max(0, ship.currentShieldHealth()) + deliveredKwh / kwhPerPoint(design, ship));
        return new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), ship.currentHullHealth(), charge,
                ship.currentFuelKg(), ship.storedCargoKg(), ship.passengerCount(), ship.passengerRaceId(), ship.transitMode(),
                ship.powerState(), ship.supplyState(), ship.moduleDamage());
    }
}
