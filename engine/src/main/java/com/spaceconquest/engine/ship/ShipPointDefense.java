package com.spaceconquest.engine.ship;

/** Weapon-specific defensive capability, distinct from offensive reach and salvo damage. */
public final class ShipPointDefense {
    public static final String AMMUNITION = CombatModuleCatalog.module(CombatModuleCatalog.KINETIC_POINT_DEFENSE).ammunitionId();
    public static final double AMMUNITION_KG = CombatModuleCatalog.module(CombatModuleCatalog.KINETIC_POINT_DEFENSE).operationalStats().get("ammunitionMassKg");
    public record Threat(long id, double distanceMeters, boolean hasElectronics) {}
    public record Shot(String weaponId, double rangeMeters, double energyKwh, String ammunitionId, double ammunitionKg,
                       long targetId, boolean requiresTargetElectronics) {
        public Shot(String weaponId, double rangeMeters, double energyKwh, String ammunitionId, double ammunitionKg) {
            this(weaponId, rangeMeters, energyKwh, ammunitionId, ammunitionKg, -1, false);
        }
    }
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
        var ammunition = new java.util.HashMap<>(ship.storedCargoKg());
        for (int mount = 0; mount < design.equippedModuleIds().size(); mount++) {
            var module = CombatModuleCatalog.module(design, design.equippedModuleIds().get(mount));
            if (module != null) {
                int capacity = (int) Math.floor(module.operationalStats().getOrDefault("pointDefenseCapacity", 0.0)
                        * ship.moduleDamage().condition(mount) * hull + 1e-9);
                capacity = Math.min(capacity, ShipWeaponAmmunition.rounds(ammunition, module));
                ShipWeaponAmmunition.reserve(ammunition, module, capacity);
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
        return planThreats(design, ship, distances.stream().map(distance -> new Threat(-1, distance, true)).toList(), budgetKwh);
    }

    public static java.util.List<Shot> planThreats(ShipDesign design, ShipInstance ship, java.util.List<Threat> threats, double budgetKwh) {
        if (capacity(design, ship) == 0) return java.util.List.of();
        var shots = new java.util.ArrayList<Shot>();
        boolean[] assigned = new boolean[threats.size()];
        var ammunition = new java.util.HashMap<>(ship.storedCargoKg());
        double hull = Math.clamp(ship.currentHullHealth() / Math.max(100, design.calculatedStructuralIntegrity() * 100), 0, 1);
        for (int mount = 0; mount < design.equippedModuleIds().size(); mount++) {
            var module = CombatModuleCatalog.module(design, design.equippedModuleIds().get(mount));
            if (module == null || !module.operationalStats().containsKey("pointDefenseCapacity")) continue;
            int capacity = (int) Math.floor(module.operationalStats().get("pointDefenseCapacity") * ship.moduleDamage().condition(mount) * hull + 1e-9);
            double range = module.operationalStats().get("pointDefenseRangeMeters"), cost = module.operationalStats().get("pointDefenseShotKwh");
            boolean electronics = module.operationalStats().getOrDefault("pointDefenseRequiresTargetElectronics", 0.0) > 0;
            for (int missile = 0; missile < threats.size() && capacity > 0; missile++) {
                var threat = threats.get(missile);
                double distance = threat.distanceMeters();
                if (assigned[missile] || !Double.isFinite(distance) || distance < 0 || distance > range
                        || electronics && !threat.hasElectronics()
                        || budgetKwh + 1e-9 < cost || ShipWeaponAmmunition.rounds(ammunition, module) == 0) continue;
                assigned[missile] = true; capacity--; budgetKwh -= cost;
                ShipWeaponAmmunition.reserve(ammunition, module, 1);
                shots.add(new Shot(module.id(), range, cost, module.ammunitionId(), module.operationalStats().getOrDefault("ammunitionMassKg", 0.0),
                        threat.id(), electronics));
            }
        }
        return java.util.List.copyOf(shots);
    }

    public static ShipInstance consume(ShipInstance ship, java.util.List<Shot> shots) {
        var used = new java.util.HashMap<String, Double>();
        shots.stream().filter(shot -> !shot.ammunitionId().isBlank()).forEach(shot -> used.merge(shot.ammunitionId(), shot.ammunitionKg(), Double::sum));
        return ShipWeaponAmmunition.consume(ship, used);
    }
}
