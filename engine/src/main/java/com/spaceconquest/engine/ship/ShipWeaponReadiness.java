package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.combat.TacticalCombatProcessor;
import java.util.*;

/** Immutable, read-only next-round quote at zero separation with eligible incoming defensive targets. */
public record ShipWeaponReadiness(String shipId, List<Weapon> weapons, String note) {
    public ShipWeaponReadiness { weapons = List.copyOf(weapons); }
    public enum Blocker { NONE, DESTROYED, DISABLED, HELD, AMMUNITION, PROFILE, CONDITION, POWER, UNKNOWN }
    private record Status(String text, Blocker blocker) {}
    public record Mount(int number, double condition) {}
    public record Weapon(String moduleId, String name, List<Mount> mounts, String ammunitionId,
                         double roundMassKg, double cargoKg, int wholeRounds, List<Integer> sharedMounts,
                         int fundedShots, String status, Blocker blocker) {
        public Weapon { mounts = List.copyOf(mounts); sharedMounts = List.copyOf(sharedMounts); }
        public Weapon(String moduleId, String name, List<Mount> mounts, String ammunitionId,
                      double roundMassKg, double cargoKg, int wholeRounds, List<Integer> sharedMounts,
                      int fundedShots, String status) {
            this(moduleId, name, mounts, ammunitionId, roundMassKg, cargoKg, wholeRounds, sharedMounts,
                    fundedShots, status, Blocker.UNKNOWN);
        }
    }
    public static ShipWeaponReadiness inspect(ShipDesign design, ShipInstance ship, boolean firing) {
        if (design == null) return new ShipWeaponReadiness(ship.id(), List.of(), "Blueprint unavailable; weapon requirements unknown.");
        var groups = new LinkedHashMap<String, List<Mount>>();
        var magazines = new HashMap<String, List<Integer>>();
        double hull = Math.clamp(ship.currentHullHealth() / Math.max(100, design.calculatedStructuralIntegrity() * 100), 0, 1);
        double defenseDemand = 0;
        for (int index = 0; index < design.equippedModuleIds().size(); index++) {
            var module = CombatModuleCatalog.module(design, design.equippedModuleIds().get(index));
            if (module == null || !(module.operationalStats().containsKey("weaponDamage")
                    || module.operationalStats().containsKey("pointDefenseCapacity"))) continue;
            double condition = ship.moduleDamage().condition(index);
            groups.computeIfAbsent(module.id(), key -> new ArrayList<>()).add(new Mount(index + 1, condition));
            if (!module.ammunitionId().isBlank()) magazines.computeIfAbsent(module.ammunitionId(), key -> new ArrayList<>()).add(index + 1);
            defenseDemand += Math.floor(module.operationalStats().getOrDefault("pointDefenseCapacity", 0.0) * condition * hull + 1e-9);
        }
        // Bound diagnostic work, independently of the weapon's actual defensive capacity.
        var incoming = Collections.nCopies((int) Math.min(10000, defenseDemand), 0.0);
        var quote = ShipCombatPower.round(ship, design, TacticalCombatProcessor.SECONDS_PER_ROUND / 3600, firing, false, 0, incoming);
        var shots = new HashMap<String, Integer>(); var damage = new HashMap<String, Double>();
        quote.directHits().forEach(hit -> { shots.merge(hit.weapon().id(), 1, Integer::sum); damage.merge(hit.weapon().id(), hit.damage(), Double::sum); });
        quote.missileLaunches().forEach(hit -> { shots.merge(hit.weaponId(), 1, Integer::sum); damage.merge(hit.weaponId(), hit.damage(), Double::sum); });
        quote.pointDefenseShots().forEach(hit -> shots.merge(hit.weaponId(), 1, Integer::sum));
        var rows = new ArrayList<Weapon>();
        for (var group : groups.entrySet()) {
            var module = CombatModuleCatalog.module(design, group.getKey());
            int funded = shots.getOrDefault(module.id(), 0);
            int rounds = module.ammunitionId().isBlank() ? 0 : ShipWeaponAmmunition.rounds(ship.storedCargoKg(), module);
            double expectedDamage = group.getValue().stream().mapToDouble(m -> m.condition() * hull
                    * module.operationalStats().getOrDefault("weaponDamage", 0.0)
                    * (ShipMissileWeapons.projectile(module) ? module.operationalStats().get("launchesPerRound") : 1)).sum();
            int expectedShots = group.getValue().stream().mapToInt(m -> m.condition() <= 0 ? 0
                    : module.operationalStats().containsKey("pointDefenseCapacity")
                    ? (int) Math.floor(module.operationalStats().get("pointDefenseCapacity") * m.condition() * hull + 1e-9)
                    : ShipMissileWeapons.projectile(module) ? module.operationalStats().get("launchesPerRound").intValue() : WeaponHit.pulses(module)).sum();
            var status = status(design, ship, module, group.getValue(), firing, rounds, funded, expectedShots,
                    damage.getOrDefault(module.id(), 0.0), expectedDamage, quote.ship());
            rows.add(new Weapon(module.id(), module.name(), group.getValue(), module.ammunitionId(),
                    module.operationalStats().getOrDefault("ammunitionMassKg", 0.0), ship.storedCargoKg().getOrDefault(module.ammunitionId(), 0.0),
                    rounds, magazines.getOrDefault(module.ammunitionId(), List.of()), funded, status.text(), status.blocker()));
        }
        return new ShipWeaponReadiness(ship.id(), rows,
                "Current-store preview at zero separation with eligible incoming targets. Defense precedes offense; "
                        + (defenseDemand > 10000 ? "Defensive preview limited to 10,000 incoming targets; " : "")
                        + "range, live targeting and actual incoming threats are checked in combat. Quotes do not spend stores.");
    }
    private static Status status(ShipDesign design, ShipInstance ship, ShipModule module, List<Mount> mounts,
                                 boolean firing, int rounds, int funded, int expected, double damage, double expectedDamage, ShipInstance after) {
        if (ship.currentHullHealth() <= 0) return new Status("Destroyed ship", Blocker.DESTROYED);
        if (mounts.stream().allMatch(m -> m.condition() <= 0)) return new Status("Disabled hardware", Blocker.DISABLED);
        if (!firing && module.operationalStats().containsKey("weaponDamage")) return new Status("Fire held while withdrawing", Blocker.HELD);
        if (!module.ammunitionId().isBlank() && rounds == 0) return new Status("Empty magazine: no whole round", Blocker.AMMUNITION);
        if (design.powerProfile() == null) return new Status("Electrical profile unavailable; firing power unverified", Blocker.PROFILE);
        if (expected == 0) return new Status("Condition too low for a whole defensive shot", Blocker.CONDITION);
        if (funded >= expected && damage + 1e-9 >= expectedDamage) return new Status("Ready for quoted round", Blocker.NONE);
        if (!module.ammunitionId().isBlank() && ShipWeaponAmmunition.rounds(after.storedCargoKg(), module) == 0)
            return new Status("Limited by shared ammunition", Blocker.AMMUNITION);
        return new Status(funded > 0 ? "Reduced salvo: insufficient firing power" : "Insufficient firing power or unfunded weapon upkeep", Blocker.POWER);
    }
}
