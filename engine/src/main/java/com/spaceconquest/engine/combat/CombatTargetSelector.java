package com.spaceconquest.engine.combat;

import com.spaceconquest.engine.ship.*;
import java.util.*;

/** Discarded impact forecasts prioritize funded enemy offense removed per expected paid hit. */
public final class CombatTargetSelector {
    public record Choice(int index, double threat, double immediateReduction, double expectedHits, double score,
                         double reservedReduction, double guidanceThreat) {
        public double fundedFirepower() { return Math.max(0, threat - guidanceThreat); }
        public String explanation(String weapon, String target) {
            String reason = score <= 0 ? "no effective impact projected on remaining targets"
                    : threat <= 0 ? "no reducible funded threat; fallback to useful damage"
                    : "reduce incoming damage by neutralizing funded weapons or critical missile guidance";
            String hits = Double.isFinite(expectedHits) ? String.format(Locale.ROOT, "%.2f", expectedHits) : "unreachable";
            return String.format(Locale.ROOT,
                    "%s prioritizes %s: %s; funded firepower %.3f per round; combined funded threat %.3f; expected hits to neutralize %s; expected immediate threat reduction %.3f; committed fire reduction %.3f; guidance threat %.3f. Forecasts are estimates, not guaranteed damage.",
                    weapon, target, reason, fundedFirepower(), threat, hits, immediateReduction, reservedReduction, guidanceThreat);
        }
    }
    private final Map<String, ShipDesign> designs = new HashMap<>();
    private record ThreatQuote(ShipInstance ship, double meters) {}
    private final Map<ThreatQuote, Double> threats = new HashMap<>();
    private final CombatTargetingDoctrine doctrine;
    private final List<ShipDesign> blueprints;
    private final ProjectileTargetingContext baseProjectiles;
    private ProjectileTargetingContext projectiles;
    private List<ShipInstance> currentTargets = List.of();
    private Map<String, ShipInstance> reserved = Map.of();
    private String previous = "";
    public CombatTargetSelector(List<ShipDesign> designs, CombatTargetingDoctrine doctrine) {
        this(designs, doctrine, ProjectileTargetingContext.empty());
    }
    public CombatTargetSelector(List<ShipDesign> designs, CombatTargetingDoctrine doctrine, ProjectileTargetingContext projectiles) {
        designs.forEach(design -> this.designs.putIfAbsent(design.id(), design)); this.doctrine = doctrine;
        this.blueprints = List.copyOf(designs); this.baseProjectiles = projectiles; this.projectiles = projectiles;
    }
    public void arriving(List<WeaponHit> hits) { projectiles = baseProjectiles.withArriving(hits); }
    public Choice choose(List<ShipInstance> targets, WeaponHit hit, String subsystem) {
        if (targets.stream().noneMatch(ship -> ship.currentHullHealth() > 0)) return null;
        currentTargets = List.copyOf(targets); reserved = projectiles.reserve(targets, blueprints, subsystem, doctrine);
        Choice dangerous = null, fallback = null;
        for (int index = 0; index < targets.size(); index++) {
            var ship = targets.get(index); if (ship.currentHullHealth() <= 0) continue;
            var choice = score(index, ship, hit, subsystem);
            if (choice.threat() > 0 && choice.score() > 0) dangerous = better(dangerous, choice, targets);
            else fallback = better(fallback, choice, targets);
        }
        var chosen = dangerous != null ? dangerous : fallback;
        if (chosen != null) previous = targets.get(chosen.index()).id();
        return chosen;
    }
    private Choice better(Choice old, Choice next, List<ShipInstance> targets) {
        if (old == null || next.score() > old.score()
                || next.score() == old.score() && targets.get(next.index()).id().compareTo(targets.get(old.index()).id()) < 0) return next;
        return old;
    }
    private Choice score(int index, ShipInstance ship, WeaponHit hit, String subsystem) {
        var design = designs.get(ship.designId());
        double guidance = guidance(ship, ship), threat = threat(ship, hit.separationMeters()) + guidance;
        double accuracy = hit.accuracy(WeaponImpactResolver.acceleration(design, ship));
        if (accuracy <= 0 || hit.damage() <= 0) return new Choice(index, threat, 0, Double.POSITIVE_INFINITY, 0, 0, guidance);
        var first = WeaponImpactResolver.resolve(ship, design, hit, subsystem);
        double work = progress(first);
        if (work <= 0) return new Choice(index, threat, 0, Double.POSITIVE_INFINITY, 0, 0, guidance);
        double reduction = Math.max(0, threat - threat(first.ship(), hit.separationMeters()) - guidance(ship, first.ship()));
        double expected = forecast(ship, hit, subsystem, threat);
        double score = threat > 0 ? doctrine.neutralizationWeight() * threat / expected
                + doctrine.immediateThreatReductionWeight() * reduction * accuracy
                + doctrine.progressWeight() * threat * Math.min(1, work / remainingWork(ship, design, hit)) * accuracy
                : work / remainingWork(ship, design, hit) * accuracy;
        if (ship.id().equals(previous)) score *= doctrine.focusMultiplier();
        var projected = reserved.get(ship.id());
        double committedReduction = projected == null ? 0 : Math.max(0,
                threat - threat(projected, hit.separationMeters()) - guidance(ship, projected));
        if (threat > 0) score *= 1 - doctrine.committedFireWeight() * Math.min(1, committedReduction / threat);
        return new Choice(index, threat, reduction * accuracy, expected, score, committedReduction, guidance);
    }
    private double forecast(ShipInstance ship, WeaponHit hit, String subsystem, double initialThreat) {
        var design = designs.get(ship.designId()); var projected = ship;
        double elapsed = 0, work = 0, remainingThreat = initialThreat;
        for (int step = 0; step < doctrine.maxProjectionHits(); step++) {
            double accuracy = hit.accuracy(WeaponImpactResolver.acceleration(design, projected));
            if (accuracy <= 0) return Double.POSITIVE_INFINITY;
            var impact = WeaponImpactResolver.resolve(projected, design, hit, subsystem);
            if (progress(impact) <= 0) return Double.POSITIVE_INFINITY;
            elapsed += 1 / accuracy; work += progress(impact); projected = impact.ship();
            remainingThreat = threat(projected, hit.separationMeters()) + guidance(ship, projected);
            if (projected.currentHullHealth() <= 0 || initialThreat > 0 && remainingThreat <= 0) return elapsed;
        }
        double removed = initialThreat - remainingThreat;
        return removed > 0 ? elapsed * initialThreat / removed
                : elapsed + remainingWork(projected, design, hit) / Math.max(1e-9, work / elapsed);
    }
    private double guidance(ShipInstance source, ShipInstance projected) {
        return projectiles.guidanceValue(currentTargets, source, projected, doctrine);
    }
    private double threat(ShipInstance ship, double meters) {
        return threats.computeIfAbsent(new ThreatQuote(ship, meters), quote -> {
            var source = quote.ship();
            var design = designs.get(source.designId());
            return design == null || source.currentHullHealth() <= 0 ? 0
                    : ShipCombatPower.fundedFirepower(source, design, TacticalCombatProcessor.SECONDS_PER_ROUND / 3600, true, meters);
        });
    }
    private static double progress(WeaponImpactResolver.Impact impact) {
        return impact.shieldAbsorbed() + impact.hullDamage() + impact.electronicsDamage();
    }
    private static double remainingWork(ShipInstance ship, ShipDesign design, WeaponHit hit) {
        double electronicFraction = hit.stat("electronicDamageFraction", 0);
        double physical = electronicFraction < 1 ? ship.currentHullHealth() / (1 - electronicFraction) : Double.POSITIVE_INFINITY;
        double electronics = 0;
        if (design != null) for (int mount = 0; mount < design.equippedModuleIds().size(); mount++) {
            String id = design.equippedModuleIds().get(mount); var module = ShipModuleDamage.damageableModule(design, id);
            if (module != null && (module.powerDrawKw() > 0 || ShipElectricalDamage.hardware(id)))
                electronics += ShipModuleDamage.DURABILITY * ship.moduleDamage().condition(mount);
        }
        double multiplier = electronicFraction * hit.stat("electronicDamageMultiplier", 1);
        double electronicWork = multiplier > 0 && electronics > 0 ? electronics / multiplier : Double.POSITIVE_INFINITY;
        return Math.max(1e-9, ShipCombatStats.of(design, ship).shields() + Math.min(physical, electronicWork));
    }
}
