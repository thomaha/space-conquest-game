package com.spaceconquest.engine.ship;

import java.util.HashMap;
import java.util.Map;

/** Whole ammunition reservations use each application's resource and packaged mass. */
public final class ShipWeaponAmmunition {
    private ShipWeaponAmmunition() {}
    public static int rounds(Map<String, Double> cargo, ShipModule module) {
        if (module.ammunitionId().isBlank()) return Integer.MAX_VALUE;
        double unit = module.operationalStats().getOrDefault("ammunitionMassKg", 0.0);
        double mass = cargo.getOrDefault(module.ammunitionId(), 0.0);
        return unit > 0 && Double.isFinite(unit) && Double.isFinite(mass) && mass > 0
                ? (int) Math.min(Integer.MAX_VALUE, Math.floor(mass / unit)) : 0;
    }
    public static void reserve(Map<String, Double> cargo, ShipModule module, int count) {
        if (!module.ammunitionId().isBlank()) cargo.merge(module.ammunitionId(),
                -count * module.operationalStats().get("ammunitionMassKg"), Double::sum);
    }
    public static ShipInstance consume(ShipInstance ship, Map<String, Double> used) {
        if (used.isEmpty()) return ship;
        var cargo = new HashMap<>(ship.storedCargoKg());
        used.forEach((id, mass) -> {
            if (!Double.isFinite(mass) || mass < 0 || cargo.getOrDefault(id, 0.0) + 1e-9 < mass)
                throw new IllegalArgumentException("Insufficient ammunition: " + id);
            double left = cargo.getOrDefault(id, 0.0) - mass;
            if (left <= 1e-9) cargo.remove(id); else cargo.put(id, left);
        });
        return new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), ship.currentHullHealth(), ship.currentShieldHealth(),
                ship.currentFuelKg(), cargo, ship.passengerCount(), ship.passengerRaceId(), ship.transitMode(),
                ship.powerState(), ship.supplyState(), ship.moduleDamage());
    }
}
