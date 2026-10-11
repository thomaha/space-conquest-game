package com.spaceconquest.engine.ship;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** Whole packaged missiles occupy ordinary carried cargo mass and are never created by launchers. */
public final class ShipMissileWeapons {
    public static final String AMMUNITION = "guided_missile";
    public static final double MASS_KG = 100;
    public static final double SPEED_METERS_PER_SECOND = 10_000;
    public record Launch(int mount, double damage, double energyKwh) {}
    private ShipMissileWeapons() {}

    public static int rounds(ShipInstance ship) {
        double mass = ship.storedCargoKg().getOrDefault(AMMUNITION, 0.0);
        return Double.isFinite(mass) && mass > 0 ? (int) Math.min(Integer.MAX_VALUE, Math.floor(mass / MASS_KG)) : 0;
    }

    public static List<Launch> candidates(ShipDesign design, ShipInstance ship, double meters) {
        if (design == null || ship.currentHullHealth() <= 0 || !Double.isFinite(meters) || meters < 0
                || !ShipServicePower.funded(ship, ShipServicePower.WEAPONS)) return List.of();
        int remaining = rounds(ship);
        double hull = Math.clamp(ship.currentHullHealth() / Math.max(100, design.calculatedStructuralIntegrity() * 100), 0, 1);
        var launches = new ArrayList<Launch>();
        var module = CombatModuleCatalog.module(CombatModuleCatalog.MISSILE);
        for (int mount = 0; mount < design.equippedModuleIds().size() && remaining > 0; mount++) {
            if (CombatModuleCatalog.MISSILE.equals(design.equippedModuleIds().get(mount))
                    && ship.moduleDamage().condition(mount) > 0 && meters <= module.operationalStats().get("weaponRangeMeters")) {
                launches.add(new Launch(mount, module.operationalStats().get("weaponDamage") * hull * ship.moduleDamage().condition(mount),
                        module.operationalStats().get("weaponSalvoKwh")));
                remaining--;
            }
        }
        return List.copyOf(launches);
    }

    public static ShipInstance consume(ShipInstance ship, int count) {
        if (count < 0 || count > rounds(ship)) throw new IllegalArgumentException("Insufficient whole missile rounds");
        if (count == 0) return ship;
        var cargo = new HashMap<>(ship.storedCargoKg());
        double left = cargo.get(AMMUNITION) - count * MASS_KG;
        if (left == 0) cargo.remove(AMMUNITION); else cargo.put(AMMUNITION, left);
        return new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), ship.currentHullHealth(), ship.currentShieldHealth(),
                ship.currentFuelKg(), cargo, ship.passengerCount(), ship.passengerRaceId(), ship.transitMode(),
                ship.powerState(), ship.supplyState(), ship.moduleDamage());
    }
}
