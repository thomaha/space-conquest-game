package com.spaceconquest.engine.combat;

import com.spaceconquest.engine.ship.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Resolves separate paid hits without discarding application-defined interaction or pulse strength. */
public final class WeaponImpactResolver {
    public record Result(List<ShipInstance> ships, double landedDamage) {
        public Result { ships = List.copyOf(ships); }
    }
    public record Impact(ShipInstance ship, double shieldAbsorbed, double shieldBypass,
                         double armorPrevented, double hullDamage, double electronicsDamage,
                         List<Integer> disabledMounts) {
        public Impact { disabledMounts = List.copyOf(disabledMounts); }
        public String description() {
            return String.format(java.util.Locale.ROOT,
                    "shields absorbed %.3f; shield bypass %.3f; armor prevented %.3f; hull damage %.3f; electronics damage %.3f%s",
                    shieldAbsorbed, shieldBypass, armorPrevented, hullDamage, electronicsDamage,
                    disabledMounts.isEmpty() ? "" : "; disabled mounts " + disabledMounts);
        }
    }
    private WeaponImpactResolver() {}

    public static Result apply(List<ShipInstance> ships, List<WeaponHit> hits, List<ShipDesign> designs,
                               String targetedSubsystem, Random random,
                               List<TacticalCombatProcessor.CombatActionReport> actions) {
        return apply(ships, hits, designs, targetedSubsystem, random, actions, CombatTargetingDoctrine.defaults());
    }
    public static Result apply(List<ShipInstance> ships, List<WeaponHit> hits, List<ShipDesign> designs,
                               String targetedSubsystem, Random random,
                               List<TacticalCombatProcessor.CombatActionReport> actions, CombatTargetingDoctrine doctrine) {
        return apply(ships, hits, designs, targetedSubsystem, random, actions, doctrine, ProjectileTargetingContext.empty());
    }
    public static Result apply(List<ShipInstance> ships, List<WeaponHit> hits, List<ShipDesign> designs,
                               String targetedSubsystem, Random random, List<TacticalCombatProcessor.CombatActionReport> actions,
                               CombatTargetingDoctrine doctrine, ProjectileTargetingContext projectiles) {
        if (ships.isEmpty() || hits.isEmpty()) return new Result(ships, 0);
        var targets = new ArrayList<>(ships);
        var selector = new CombatTargetSelector(designs, doctrine, projectiles);
        var arriving = new ArrayList<>(hits.stream().filter(hit -> ShipMissileWeapons.projectile(hit.weapon())).toList());
        double landed = 0;
        for (var hit : hits) {
            if (ShipMissileWeapons.projectile(hit.weapon())) arriving.remove(hit);
            selector.arriving(arriving);
            var choice = selector.choose(targets, hit, targetedSubsystem);
            if (choice == null) break;
            int index = choice.index();
            var ship = targets.get(index);
            actions.add(new TacticalCombatProcessor.CombatActionReport("TARGET_SELECTION",
                    choice.explanation(hit.weapon().name(), ship.id()), 0, ship.id()));
            var design = designs.stream().filter(d -> d.id().equals(ship.designId())).findFirst().orElse(null);
            double accuracy = hit.accuracy(acceleration(design, ship));
            if (accuracy <= 0 || accuracy < 1 && random.nextDouble() >= accuracy) {
                actions.add(new TacticalCombatProcessor.CombatActionReport("WEAPON_MISS", String.format(java.util.Locale.ROOT,
                        "%s missed: accuracy %.1f%% at %.3f km; target acceleration %.3f m/s²",
                        hit.weapon().name(), accuracy * 100, hit.separationMeters() / 1000, acceleration(design, ship)), 0, ship.id()));
                continue;
            }
            var impact = resolve(ship, design, hit, targetedSubsystem);
            targets.set(index, impact.ship());
            landed += hit.damage();
            actions.add(new TacticalCombatProcessor.CombatActionReport("WEAPON_HIT", hit.weapon().name() + " hit: " + impact.description(), hit.damage(), ship.id()));
        }
        return new Result(targets.stream().filter(ship -> ship.currentHullHealth() > 0).toList(), landed);
    }

    /** Accuracy uses actual surviving thrust and carried mass, rather than a hull-role bonus. */
    public static double acceleration(ShipDesign design, ShipInstance ship) {
        if (design == null) return 0;
        double mass = design.totalDryMassKg() + ship.currentFuelKg() + ship.generatorFuelMassKg() + ship.supplyFuelMassKg() + ship.passengerCount() * 80.0
                + ship.storedCargoKg().values().stream().mapToDouble(Double::doubleValue).sum();
        return PropulsionDamage.thrust(design, ship) / Math.max(1, mass);
    }

    public static ShipInstance hit(ShipInstance ship, ShipDesign design, WeaponHit hit, String targetedSubsystem) {
        return resolve(ship, design, hit, targetedSubsystem).ship();
    }

    public static Impact resolve(ShipInstance ship, ShipDesign design, WeaponHit hit, String targetedSubsystem) {
        var stats = ShipCombatStats.of(design, ship);
        double bypass = hit.damage() * hit.stat("shieldBypassFraction", 0);
        double shieldFacing = hit.damage() - bypass;
        double absorbed = Math.min(stats.shields(), shieldFacing);
        double shields = stats.shields() - absorbed;
        double penetrating = bypass + shieldFacing - absorbed;
        double electronics = penetrating * hit.stat("electronicDamageFraction", 0) * hit.stat("electronicDamageMultiplier", 1);
        double physical = penetrating * (1 - hit.stat("electronicDamageFraction", 0));
        double armorMultiplier = design != null && design.armorThicknessCm() >= hit.stat("heavyArmorThresholdCm", Double.POSITIVE_INFINITY)
                ? hit.stat("heavyArmorDamageMultiplier", 1) : 1;
        double armorAbsorption = design == null ? 0 : design.armorThicknessCm() * hit.stat("armorAbsorptionPerCm", 0);
        double hullDamage = Math.max(0, physical * armorMultiplier * (1 - stats.armorDeflection()) - armorAbsorption);
        var damage = ship.moduleDamage().hit(design, hullDamage, targetStat(targetedSubsystem));
        var physicalDamage = damage;
        if (electronics > 0) damage = damage.hitElectronics(design, electronics);
        double electronicsApplied = damage.damageByMount().entrySet().stream().mapToDouble(entry ->
                entry.getValue() - physicalDamage.damageByMount().getOrDefault(entry.getKey(), 0.0)).sum();
        var disabled = new ArrayList<Integer>();
        if (design != null) for (int mount = 0; mount < design.equippedModuleIds().size(); mount++)
            if (ship.moduleDamage().condition(mount) > 0 && damage.condition(mount) <= 0) disabled.add(mount + 1);
        shields = Math.min(shields, damage.capability(design, "shieldCapacity"));
        var result = new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(),
                Math.max(0, ship.currentHullHealth() - hullDamage), shields, ship.currentFuelKg(), ship.storedCargoKg(),
                ship.passengerCount(), ship.passengerRaceId(), ship.transitMode(), ship.powerState(), ship.supplyState(), damage);
        return new Impact(ShipElectricalDamage.clampCharge(design, result), absorbed, bypass,
                Math.max(0, physical - hullDamage), Math.min(ship.currentHullHealth(), hullDamage), electronicsApplied, disabled);
    }

    private static String targetStat(String subsystem) {
        if (TacticalCombatProcessor.TARGET_SUBSYSTEM_WEAPONS.equalsIgnoreCase(subsystem)) return "weaponDamage";
        if (TacticalCombatProcessor.TARGET_SUBSYSTEM_SHIELDS.equalsIgnoreCase(subsystem)) return "shieldCapacity";
        if (TacticalCombatProcessor.TARGET_SUBSYSTEM_ENGINES.equalsIgnoreCase(subsystem)) return "driveThrust";
        return null;
    }
}
