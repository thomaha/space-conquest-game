package com.spaceconquest.engine.ship;

/** One simultaneous electrical interval: upkeep, funded salvos then optional shield recharge. */
public final class ShipCombatPower {
    public record Result(ShipInstance ship, double firepower, double requestedWeaponKwh, double paidWeaponKwh,
                         double directFirepower, java.util.List<ShipMissileWeapons.Launch> missileLaunches,
                         int pointDefenseInterceptions, double paidPointDefenseKwh, java.util.List<ShipPointDefense.Shot> pointDefenseShots) {
        public Result { missileLaunches = java.util.List.copyOf(missileLaunches); pointDefenseShots = java.util.List.copyOf(pointDefenseShots); }
        public Result(ShipInstance ship, double firepower, double requestedWeaponKwh, double paidWeaponKwh) {
            this(ship, firepower, requestedWeaponKwh, paidWeaponKwh, firepower, java.util.List.of(), 0, 0, java.util.List.of());
        }
    }
    private ShipCombatPower() {}

    public static Result round(ShipInstance original, ShipDesign design, double hours, boolean firing) {
        return round(original, design, hours, firing, true);
    }

    /** Quotes the next salvo without optional recharge or any mutation of the supplied snapshot. */
    public static double fundedFirepower(ShipInstance ship, ShipDesign design, double hours, boolean firing) {
        return fundedFirepower(ship, design, hours, firing, 0);
    }

    public static double fundedFirepower(ShipInstance ship, ShipDesign design, double hours, boolean firing, double separationMeters) {
        return round(ship, design, hours, firing, false, separationMeters).firepower();
    }

    /** Fleet policy can defer optional recharge without changing paid firing or emitter upkeep. */
    public static Result round(ShipInstance original, ShipDesign design, double hours, boolean firing, boolean recharge) {
        return round(original, design, hours, firing, recharge, 0);
    }
    public static Result round(ShipInstance original, ShipDesign design, double hours, boolean firing, boolean recharge, double separationMeters) {
        return round(original, design, hours, firing, recharge, separationMeters, 0);
    }
    public static Result round(ShipInstance original, ShipDesign design, double hours, boolean firing, boolean recharge,
                               double separationMeters, int incomingMissiles) {
        if (incomingMissiles < 0) throw new IllegalArgumentException("Invalid incoming missile count");
        return round(original, design, hours, firing, recharge, separationMeters, java.util.Collections.nCopies(incomingMissiles, 0.0));
    }
    public static Result round(ShipInstance original, ShipDesign design, double hours, boolean firing, boolean recharge,
                               double separationMeters, java.util.List<Double> incomingDistances) {
        if (!Double.isFinite(hours) || hours <= 0) throw new IllegalArgumentException("Invalid combat duration");
        if (design == null || design.powerProfile() == null)
            return new Result(original, firing ? ShipWeaponRange.directFirepower(design, original, separationMeters) : 0, 0, 0);
        var p = design.powerProfile(original);
        var activity = firing || !incomingDistances.isEmpty() ? ShipServicePower.Activity.COMBAT : ShipServicePower.Activity.ESCAPE;
        var ship = original.withPowerState(OrbitalPowerAccounting.startDay(ShipPowerProcessor.reserves(original)));
        double essential = p.essentialKw(ship, design), cargo = ShipPowerProcessor.cargoKw(p, ship, design);
        var upkeep = ShipPowerProcessor.allocate(p, ship.powerState(), hours, 0, essential, cargo, 0, 0, ShipServicePower.SHIELDS, activity);
        double base = firing && upkeep.interval().supplied() && capturedWeapons(design)
                ? ShipWeaponRange.directFirepower(design, ship.withPowerState(upkeep.interval().state()), separationMeters) : 0;
        double condition = Math.clamp(ship.currentHullHealth() / Math.max(100, design.calculatedStructuralIntegrity() * 100), 0, 1);
        var candidates = firing && upkeep.interval().supplied() && capturedWeapons(design)
                ? ShipMissileWeapons.candidates(design, ship.withPowerState(upkeep.interval().state()), separationMeters) : java.util.List.<ShipMissileWeapons.Launch>of();
        double directCost = base > 0 ? ShipWeaponRange.directCapability(design, ship, "weaponSalvoKwh", separationMeters) * condition : 0;
        var defenseShip = ship.withPowerState(upkeep.interval().state());
        var defensePlan = upkeep.interval().supplied() && capturedWeapons(design)
                ? ShipPointDefense.plan(design, defenseShip, incomingDistances, Double.POSITIVE_INFINITY) : java.util.List.<ShipPointDefense.Shot>of();
        double requested = defensePlan.stream().mapToDouble(ShipPointDefense.Shot::energyKwh).sum() + directCost + candidates.stream().mapToDouble(ShipMissileWeapons.Launch::energyKwh).sum();
        double weaponKw = requested / hours;
        var selected = requested > 0 ? ShipPowerProcessor.allocate(p, ship.powerState(), hours, 0, essential, cargo, 0, weaponKw, ShipServicePower.WEAPONS, activity) : upkeep;
        if (requested > 0 && !requiredSupplied(selected)) {
            selected = upkeep;
            double low = 0, high = weaponKw;
            // Protect upkeep for the entire round rather than exhausting fuel during an opening salvo.
            for (int attempt = 0; attempt < 32; attempt++) {
                double middle = (low + high) / 2;
                var partial = ShipPowerProcessor.allocate(p, ship.powerState(), hours, 0, essential, cargo, 0, middle, ShipServicePower.WEAPONS, activity);
                if (requiredSupplied(partial)) { low = middle; selected = partial; }
                else high = middle;
            }
            weaponKw = low;
        }
        double available = Math.min(requested, selected.rechargeKwh());
        var defenseShots = defensePlan.isEmpty() ? defensePlan : ShipPointDefense.plan(design, defenseShip, incomingDistances, available);
        double defensePaid = defenseShots.stream().mapToDouble(ShipPointDefense.Shot::energyKwh).sum();
        double directPaid = Math.min(directCost, Math.max(0, available - defensePaid));
        var launches = new java.util.ArrayList<ShipMissileWeapons.Launch>();
        double launchPaid = 0;
        for (var candidate : candidates) {
            if (defensePaid + directPaid + launchPaid + candidate.energyKwh() <= available + 1e-9) {
                launches.add(candidate); launchPaid += candidate.energyKwh();
            }
        }
        double funded = defensePaid + directPaid + launchPaid;
        if (funded + 1e-9 < available) {
            weaponKw = funded / hours;
            selected = funded > 0 ? ShipPowerProcessor.allocate(p, ship.powerState(), hours, 0, essential, cargo, 0, weaponKw, ShipServicePower.WEAPONS, activity) : upkeep;
        }
        double shieldKw = ShipServicePower.funded(ship.withPowerState(selected.interval().state()), ShipServicePower.SHIELDS)
                ? ShipShieldRecharge.requestedKw(design, ship, hours) : 0;
        if (recharge && shieldKw > 0 && selected.interval().supplied() && selected.rechargeKwh() + 1e-9 >= funded
                && reserve(design, ship.withPowerState(selected.interval().state()))) {
            String activeService = weaponKw > 0 ? ShipServicePower.WEAPONS : ShipServicePower.SHIELDS;
            var full = ShipPowerProcessor.allocate(p, ship.powerState(), hours, 0, essential, cargo, 0, weaponKw + shieldKw, activeService, activity);
            if (reserve(design, ship.withPowerState(full.interval().state()))
                    && ShipServicePower.funded(ship.withPowerState(full.interval().state()), ShipServicePower.SHIELDS)) selected = full;
            else {
                double low = 0, high = shieldKw;
                for (int attempt = 0; attempt < 12; attempt++) {
                    double middle = (low + high) / 2;
                    var partial = ShipPowerProcessor.allocate(p, ship.powerState(), hours, 0, essential, cargo, 0, weaponKw + middle, activeService, activity);
                    if (reserve(design, ship.withPowerState(partial.interval().state()))
                            && ShipServicePower.funded(ship.withPowerState(partial.interval().state()), ShipServicePower.SHIELDS)) { low = middle; selected = partial; }
                    else high = middle;
                }
            }
        }
        double paid = Math.min(funded, selected.rechargeKwh());
        double shields = Math.max(0, selected.rechargeKwh() - paid);
        var actual = ShipShieldRecharge.apply(ship.withPowerState(selected.interval().state()), design, shields);
        double direct = directCost > 0 ? base * directPaid / directCost : 0;
        actual = ShipMissileWeapons.consume(actual, launches.size());
        actual = ShipPointDefense.consume(actual, defenseShots);
        return new Result(actual, direct + launches.stream().mapToDouble(ShipMissileWeapons.Launch::damage).sum(), requested, paid, direct, launches, defenseShots.size(), defensePaid, defenseShots);
    }

    private static boolean requiredSupplied(ShipPowerProcessor.Allocation allocation) {
        var interval = allocation.interval();
        return interval.unmetEssentialKwh() + interval.unmetCargoKwh() + interval.unmetDriveKwh() <= 1e-12;
    }

    private static boolean capturedWeapons(ShipDesign design) {
        double upkeep = design.equippedModuleIds().stream().map(CombatModuleCatalog::module)
                .filter(module -> module != null && (module.operationalStats().containsKey("weaponDamage")
                        || module.operationalStats().containsKey("pointDefenseCapacity")))
                .mapToDouble(ShipModule::powerDrawKw).sum();
        return design.powerProfile().hotelKw() + 1e-9 >= upkeep;
    }

    private static boolean reserve(ShipDesign design, ShipInstance ship) {
        var p = design.powerProfile(ship);
        return ship.powerState().lastUnmetEssentialKwh() <= 1e-6 && ship.powerState().unmetCargoKwh() <= 1e-6
                && ShipArrivalReserve.check(p, ship.powerState(), p.essentialKw(ship, design),
                ShipPowerProcessor.cargoKw(p, ship, design), ShipSolarEnvironment.DARK).ready();
    }
}
