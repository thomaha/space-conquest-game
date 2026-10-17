package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.combat.*;
import java.util.ArrayList;
import java.util.List;

/** Discarded paid salvos rank physical fleet destinations without changing positions or stores. */
public final class FleetCombatPositioning {
    public record Quote(double meters, double score, double threatReduction, double usefulDamage,
                        double incomingDamage, int projectedImpacts) {}
    private static final double HOURS = TacticalCombatProcessor.SECONDS_PER_ROUND / 3600;
    private FleetCombatPositioning() {}

    public static Quote quote(GameState state, Fleet own, Fleet enemy, double meters) {
        return quote(state.shipDesigns(), own.ships().stream().filter(ship -> !ShipRefitPlan.reserved(state, ship.id())).toList(),
                enemy.ships().stream().filter(ship -> !ShipRefitPlan.reserved(state, ship.id())).toList(), meters, CombatPositioningDoctrine.defaults());
    }
    public static Quote quote(List<ShipDesign> designs, List<ShipInstance> own, List<ShipInstance> enemy,
                              double meters, CombatPositioningDoctrine doctrine) {
        if (!Double.isFinite(meters) || meters < 0) throw new IllegalArgumentException("Invalid positioning distance");
        var projected = new ArrayList<>(enemy);
        var selector = new CombatTargetSelector(designs, CombatTargetingDoctrine.defaults());
        double damage = 0, incoming = 0; int count = 0;
        for (var source : own) {
            var paid = ShipCombatPower.round(source, design(designs, source), HOURS, true, false, meters);
            for (var hit : hits(paid, meters)) {
                if (count >= doctrine.maxSalvoImpacts()) break;
                var choice = selector.choose(projected, hit, "ALL");
                if (choice == null) break;
                var target = projected.get(choice.index()); var blueprint = design(designs, target);
                var impact = WeaponImpactResolver.resolve(target, blueprint, hit.scaled(
                        hit.accuracy(WeaponImpactResolver.acceleration(blueprint, target)) * confidence(hit, doctrine)), "ALL");
                projected.set(choice.index(), impact.ship()); count++;
                damage += impact.shieldAbsorbed() + impact.hullDamage() + impact.electronicsDamage();
            }
            if (count >= doctrine.maxSalvoImpacts()) break;
        }
        int incomingCount = 0;
        var defenderSelector = new CombatTargetSelector(designs, CombatTargetingDoctrine.defaults());
        for (var source : enemy) {
            var paid = ShipCombatPower.round(source, design(designs, source), HOURS, true, false, meters);
            for (var hit : hits(paid, meters)) {
                if (incomingCount++ >= doctrine.maxSalvoImpacts()) break;
                var choice = defenderSelector.choose(own, hit, "ALL");
                if (choice == null) break;
                var target = own.get(choice.index()); var blueprint = design(designs, target);
                var impact = WeaponImpactResolver.resolve(target, blueprint, hit.scaled(
                        hit.accuracy(WeaponImpactResolver.acceleration(blueprint, target)) * confidence(hit, doctrine)), "ALL");
                incoming += impact.shieldAbsorbed() + impact.hullDamage() + impact.electronicsDamage();
            }
            if (incomingCount >= doctrine.maxSalvoImpacts()) break;
        }
        double potential = threat(designs, enemy), reduction = Math.max(0, potential - threat(designs, projected));
        double range = own.stream().mapToDouble(ship -> ShipWeaponRange.maximumRange(design(designs, ship), ship)).max().orElse(0);
        double scale = Math.max(1, potential);
        double score = doctrine.neutralizationWeight() * reduction / scale + doctrine.progressWeight() * damage / scale
                - doctrine.incomingDamageWeight() * incoming / Math.max(1, threat(designs, own))
                + (damage > 0 && range > 0 ? doctrine.distanceWeight() * Math.min(1, meters / range) : 0);
        return new Quote(meters, score, reduction, damage, incoming, count);
    }
    public static Quote preferred(GameState state, Fleet own, Fleet enemy) {
        var candidates = candidates(state, own, enemy);
        return candidates.isEmpty() ? null : quote(state, own, enemy, candidates.getFirst());
    }
    /** Sample distinct installed mount ranges before bounding expensive physical destination searches. */
    public static List<Double> candidates(GameState state, Fleet own, Fleet enemy) {
        var doctrine = CombatPositioningDoctrine.defaults();
        var ranges = new java.util.TreeSet<Double>();
        for (var ship : own.ships()) {
            var blueprint = design(state.shipDesigns(), ship);
            if (blueprint == null || ship.currentHullHealth() <= 0) continue;
            for (int mount = 0; mount < blueprint.equippedModuleIds().size(); mount++) {
                var module = CombatModuleCatalog.module(blueprint, blueprint.equippedModuleIds().get(mount));
                if (module != null && module.operationalStats().containsKey("weaponDamage")
                        && ship.moduleDamage().condition(mount) > 0 && ShipWeaponAmmunition.rounds(ship.storedCargoKg(), module) > 0)
                    ranges.add(module.operationalStats().get("weaponRangeMeters"));
            }
        }
        var distances = new java.util.TreeSet<Double>();
        ranges.stream().limit(doctrine.rangeFractions().size()).forEach(range ->
                doctrine.rangeFractions().forEach(fraction -> distances.add(range * fraction)));
        var ranked = distances.stream().map(meters -> quote(state, own, enemy, meters)).filter(candidate -> candidate.usefulDamage() > 0)
                .sorted(java.util.Comparator.comparingDouble(Quote::score).reversed()
                        .thenComparing(java.util.Comparator.comparingDouble(Quote::meters).reversed()))
                .toList();
        var selected = new java.util.HashSet<Double>();
        double closest = doctrine.rangeFractions().stream().mapToDouble(Double::doubleValue).min().orElseThrow();
        ranges.stream().limit(doctrine.rangeFractions().size()).map(range -> range * closest)
                .filter(meters -> ranked.stream().anyMatch(candidate -> candidate.meters() == meters)).forEach(selected::add);
        for (var candidate : ranked) {
            if (selected.size() >= doctrine.rangeFractions().size()) break;
            selected.add(candidate.meters());
        }
        return ranked.stream().filter(candidate -> selected.contains(candidate.meters())).map(Quote::meters).toList();
    }
    public static boolean holds(GameState state, Fleet own, Fleet enemy) {
        double meters = ShipWeaponRange.separation(state, own, enemy);
        if (!Double.isFinite(meters) || !ShipWeaponRange.reaches(state, own, enemy)) return false;
        var current = quote(state, own, enemy, meters); var preferred = preferred(state, own, enemy);
        return preferred == null || preferred.score() <= current.score() + CombatPositioningDoctrine.defaults().minimumImprovement();
    }
    public static double arrivalScore(Quote quote, double days) {
        return quote.score() - CombatPositioningDoctrine.defaults().travelDayDiscount() * days;
    }
    private static List<WeaponHit> hits(ShipCombatPower.Result paid, double meters) {
        var hits = new ArrayList<>(paid.directHits());
        paid.missileLaunches().forEach(launch -> hits.add(new WeaponHit(launch.weapon(), launch.damage(), meters)));
        return hits;
    }
    private static double confidence(WeaponHit hit, CombatPositioningDoctrine doctrine) {
        double speed = hit.stat("projectileSpeedMetersPerSecond", 0);
        double rounds = speed > 0 ? Math.max(1, hit.separationMeters() / speed / TacticalCombatProcessor.SECONDS_PER_ROUND) : 0;
        return 1 / (1 + doctrine.arrivalDelayDiscount() * rounds);
    }
    private static double threat(List<ShipDesign> designs, List<ShipInstance> ships) {
        return ships.stream().filter(ship -> ship.currentHullHealth() > 0).mapToDouble(ship ->
                ShipCombatPower.fundedFirepower(ship, design(designs, ship), HOURS, true, 0)).sum();
    }
    private static ShipDesign design(List<ShipDesign> designs, ShipInstance ship) {
        return designs.stream().filter(design -> design.id().equals(ship.designId())).findFirst().orElse(null);
    }
}
