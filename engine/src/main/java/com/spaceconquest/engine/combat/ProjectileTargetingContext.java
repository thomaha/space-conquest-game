package com.spaceconquest.engine.combat;

import com.spaceconquest.engine.ship.*;
import java.util.*;
import java.util.function.Predicate;

/** Immutable tick-local flight information. Reservations are forecasts, never committed damage or guidance. */
public record ProjectileTargetingContext(List<PendingImpact> committed, List<PendingImpact> incoming,
                                         Predicate<List<ShipInstance>> enemyGuidance) {
    public record PendingImpact(WeaponHit hit, double secondsLeft, boolean requiresLiveGuidance) {
        public PendingImpact {
            if (hit == null || !Double.isFinite(secondsLeft) || secondsLeft < 0) throw new IllegalArgumentException("Invalid pending impact");
        }
    }
    public ProjectileTargetingContext { committed = List.copyOf(committed); incoming = List.copyOf(incoming); }
    public static ProjectileTargetingContext empty() { return new ProjectileTargetingContext(List.of(), List.of(), ships -> false); }
    public ProjectileTargetingContext withArriving(List<WeaponHit> hits) {
        if (hits.isEmpty()) return this;
        var all = new ArrayList<>(committed);
        hits.forEach(hit -> all.add(new PendingImpact(hit, 0, false)));
        return new ProjectileTargetingContext(all, incoming, enemyGuidance);
    }
    public double confidence(PendingImpact impact, CombatTargetingDoctrine doctrine) {
        return 1 / (1 + doctrine.arrivalDelayDiscount() * impact.secondsLeft() / TacticalCombatProcessor.SECONDS_PER_ROUND);
    }
    public double guidedDanger(CombatTargetingDoctrine doctrine) {
        return incoming.stream().filter(PendingImpact::requiresLiveGuidance)
                .sorted(Comparator.comparingDouble(PendingImpact::secondsLeft)).limit(doctrine.maxPendingImpacts())
                .mapToDouble(impact -> impact.hit().damage() * confidence(impact, doctrine)).sum() * doctrine.guidanceThreatWeight();
    }
    public double guidanceValue(List<ShipInstance> targets, ShipInstance source, ShipInstance projected, CombatTargetingDoctrine doctrine) {
        if (incoming.isEmpty() || !enemyGuidance.test(targets)) return 0;
        var without = targets.stream().filter(ship -> !ship.id().equals(source.id()) && ship.currentHullHealth() > 0).toList();
        if (enemyGuidance.test(without)) return 0;
        var after = targets.stream().map(ship -> ship.id().equals(source.id()) ? projected : ship)
                .filter(ship -> ship.currentHullHealth() > 0).toList();
        return enemyGuidance.test(after) ? guidedDanger(doctrine) : 0;
    }
    public Map<String, ShipInstance> reserve(List<ShipInstance> targets, List<ShipDesign> designs,
                                             String subsystem, CombatTargetingDoctrine doctrine) {
        if (committed.isEmpty() || doctrine.committedFireWeight() <= 0) return Map.of();
        var projected = new ArrayList<>(targets);
        var selector = new CombatTargetSelector(designs, doctrine);
        for (var flight : committed.stream().sorted(Comparator.comparingDouble(PendingImpact::secondsLeft))
                .limit(doctrine.maxPendingImpacts()).toList()) {
            var choice = selector.choose(projected, flight.hit(), subsystem); if (choice == null) break;
            var ship = projected.get(choice.index());
            var design = designs.stream().filter(item -> item.id().equals(ship.designId())).findFirst().orElse(null);
            double accuracy = flight.hit().accuracy(WeaponImpactResolver.acceleration(design, ship));
            var hit = flight.hit().scaled(accuracy * confidence(flight, doctrine));
            projected.set(choice.index(), WeaponImpactResolver.resolve(ship, design, hit, subsystem).ship());
        }
        var result = new HashMap<String, ShipInstance>(); projected.forEach(ship -> result.put(ship.id(), ship)); return Map.copyOf(result);
    }
}
