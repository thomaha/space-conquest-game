package com.spaceconquest.engine.ship;

/** Weapon-specific defensive capability, distinct from offensive reach and salvo damage. */
public final class ShipPointDefense {
    public static final String AMMUNITION = "kinetic_pd_round";
    public static final double AMMUNITION_KG = 5;
    public record Shot(String weaponId, double rangeMeters, double energyKwh) {}
    private ShipPointDefense() {}
    public static double rangeMeters() {
        return CombatModuleCatalog.module(CombatModuleCatalog.LASER_POINT_DEFENSE).operationalStats().get("pointDefenseRangeMeters");
    }
    public static double shotKwh() {
        return CombatModuleCatalog.module(CombatModuleCatalog.LASER_POINT_DEFENSE).operationalStats().get("pointDefenseShotKwh");
    }
    public static int capacity(ShipDesign design, ShipInstance ship) {
        if (design == null || design.powerProfile() == null || ship.currentHullHealth() <= 0
                || !ShipServicePower.funded(ship, ShipServicePower.WEAPONS)) return 0;
        double hull = Math.clamp(ship.currentHullHealth() / Math.max(100, design.calculatedStructuralIntegrity() * 100), 0, 1);
        int shots = 0;
        int ammunition = rounds(ship);
        for (int mount = 0; mount < design.equippedModuleIds().size(); mount++) {
            var module = CombatModuleCatalog.module(design.equippedModuleIds().get(mount));
            if (module != null) {
                int capacity = (int) Math.floor(module.operationalStats().getOrDefault("pointDefenseCapacity", 0.0)
                        * ship.moduleDamage().condition(mount) * hull + 1e-9);
                if (CombatModuleCatalog.KINETIC_POINT_DEFENSE.equals(module.id())) { capacity = Math.min(capacity, ammunition); ammunition -= capacity; }
                shots += Math.max(0, capacity);
            }
        }
        return shots;
    }

    public static int rounds(ShipInstance ship) {
        double mass = ship.storedCargoKg().getOrDefault(AMMUNITION, 0.0);
        return Double.isFinite(mass) && mass > 0 ? (int) Math.min(Integer.MAX_VALUE, Math.floor(mass / AMMUNITION_KG)) : 0;
    }

    /** Plans whole shots in mount order against closest observed approaching missiles, with one assignment per missile. */
    public static java.util.List<Shot> plan(ShipDesign design, ShipInstance ship, java.util.List<Double> distances, double budgetKwh) {
        if (capacity(design, ship) == 0) return java.util.List.of();
        var shots = new java.util.ArrayList<Shot>();
        boolean[] assigned = new boolean[distances.size()];
        int ammunition = rounds(ship);
        double hull = Math.clamp(ship.currentHullHealth() / Math.max(100, design.calculatedStructuralIntegrity() * 100), 0, 1);
        for (int mount = 0; mount < design.equippedModuleIds().size(); mount++) {
            var module = CombatModuleCatalog.module(design.equippedModuleIds().get(mount));
            if (module == null || !module.operationalStats().containsKey("pointDefenseCapacity")) continue;
            boolean kinetic = CombatModuleCatalog.KINETIC_POINT_DEFENSE.equals(module.id());
            int capacity = (int) Math.floor(module.operationalStats().get("pointDefenseCapacity") * ship.moduleDamage().condition(mount) * hull + 1e-9);
            double range = module.operationalStats().get("pointDefenseRangeMeters"), cost = module.operationalStats().get("pointDefenseShotKwh");
            for (int missile = 0; missile < distances.size() && capacity > 0; missile++) {
                double distance = distances.get(missile);
                if (assigned[missile] || !Double.isFinite(distance) || distance < 0 || distance > range
                        || budgetKwh + 1e-9 < cost || kinetic && ammunition == 0) continue;
                assigned[missile] = true; capacity--; budgetKwh -= cost;
                if (kinetic) ammunition--;
                shots.add(new Shot(module.id(), range, cost));
            }
        }
        return java.util.List.copyOf(shots);
    }

    public static ShipInstance consume(ShipInstance ship, java.util.List<Shot> shots) {
        long count = shots.stream().filter(shot -> CombatModuleCatalog.KINETIC_POINT_DEFENSE.equals(shot.weaponId())).count();
        if (count == 0) return ship;
        if (count > rounds(ship)) throw new IllegalArgumentException("Insufficient kinetic ammunition");
        var cargo = new java.util.HashMap<>(ship.storedCargoKg());
        double left = cargo.get(AMMUNITION) - count * AMMUNITION_KG;
        if (left == 0) cargo.remove(AMMUNITION); else cargo.put(AMMUNITION, left);
        return new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), ship.currentHullHealth(), ship.currentShieldHealth(),
                ship.currentFuelKg(), cargo, ship.passengerCount(), ship.passengerRaceId(), ship.transitMode(), ship.powerState(), ship.supplyState(), ship.moduleDamage());
    }
}
