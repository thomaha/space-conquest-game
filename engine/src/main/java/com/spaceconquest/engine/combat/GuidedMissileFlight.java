package com.spaceconquest.engine.combat;

import com.spaceconquest.engine.ship.ShipCombatPower;
import com.spaceconquest.engine.ship.ShipMissileWeapons;
import com.spaceconquest.engine.ship.ShipPointDefense;
import com.spaceconquest.engine.ship.WeaponHit;
import java.util.ArrayList;
import java.util.List;

/** Tick-local guided flights within the bounded tactical engagement. No hidden position lookup or reacquisition. */
final class GuidedMissileFlight {
    private record Missile(long id, double secondsLeft, WeaponHit hit, double speedMetersPerSecond, boolean requiresLiveGuidance) {}
    private long nextId;
    private static boolean interceptable(Missile missile) { return missile.hit().stat("pointDefenseInterceptable", 1) > 0; }
    private final List<Missile> flying = new ArrayList<>();

    List<ProjectileTargetingContext.PendingImpact> pending(boolean guidanceContact) {
        return flying.stream().filter(missile -> guidanceContact || !missile.requiresLiveGuidance())
                .map(missile -> new ProjectileTargetingContext.PendingImpact(missile.hit(), missile.secondsLeft(), missile.requiresLiveGuidance())).toList();
    }

    List<Double> threats(double sensorRangeMeters, boolean guidanceContact) {
        return incomingThreats(sensorRangeMeters, guidanceContact).stream().map(ShipPointDefense.Threat::distanceMeters).toList();
    }

    List<ShipPointDefense.Threat> incomingThreats(double sensorRangeMeters, boolean guidanceContact) {
        if (sensorRangeMeters <= 0) return List.of();
        return flying.stream().filter(GuidedMissileFlight::interceptable).filter(missile -> guidanceContact || !missile.requiresLiveGuidance())
                .filter(missile -> nextDistance(missile) <= sensorRangeMeters)
                .sorted(java.util.Comparator.comparingDouble(GuidedMissileFlight::nextDistance))
                .map(missile -> new ShipPointDefense.Threat(missile.id(), nextDistance(missile), missile.hit().stat("projectileHasElectronics", 1) > 0)).toList();
    }

    void intercept(List<ShipPointDefense.Shot> shots, String defendedFleet, List<String> logs,
                   List<TacticalCombatProcessor.CombatActionReport> actions) {
        flying.sort(java.util.Comparator.comparingDouble(GuidedMissileFlight::nextDistance));
        var destroyed = new java.util.LinkedHashMap<String, Integer>();
        for (var shot : shots) {
            var missile = flying.stream().filter(candidate -> interceptable(candidate) && nextDistance(candidate) <= shot.rangeMeters()
                    && (shot.targetId() < 0 || shot.targetId() == candidate.id())
                    && (!shot.requiresTargetElectronics() || candidate.hit().stat("projectileHasElectronics", 1) > 0)).findFirst().orElse(null);
            if (missile != null) {
                flying.remove(missile);
                destroyed.merge(shot.weaponId(), 1, Integer::sum);
            }
        }
        destroyed.forEach((weapon, count) -> {
            logs.add(defendedFleet + ": " + weapon + " intercepted " + count + " incoming projectile(s) before impact.");
            actions.add(new TacticalCombatProcessor.CombatActionReport("POINT_DEFENSE", weapon + " destroyed " + count + " incoming projectile(s)", 0, defendedFleet));
        });
    }

    private static double nextDistance(Missile missile) {
        return Math.max(0, missile.secondsLeft() - TacticalCombatProcessor.SECONDS_PER_ROUND) * missile.speedMetersPerSecond();
    }

    double advance(boolean contact, String target, List<String> logs,
                   List<TacticalCombatProcessor.CombatActionReport> actions) {
        return advanceHits(contact, target, logs, actions).stream().mapToDouble(WeaponHit::damage).sum();
    }

    List<WeaponHit> advanceHits(boolean contact, String target, List<String> logs,
                   List<TacticalCombatProcessor.CombatActionReport> actions) {
        if (!contact && flying.removeIf(Missile::requiresLiveGuidance)) {
            logs.add("Guided missiles self-destruct: live guidance contact lost.");
            actions.add(new TacticalCombatProcessor.CombatActionReport("GUIDANCE_LOST",
                    "Guided projectiles self-destruct: live guidance contact lost; launch costs remain spent", 0, target));
        }
        var hits = new ArrayList<WeaponHit>();
        var remaining = new ArrayList<Missile>();
        for (var missile : flying) {
            double seconds = missile.secondsLeft() - TacticalCombatProcessor.SECONDS_PER_ROUND;
            if (seconds <= 1e-9) hits.add(missile.hit());
            else remaining.add(new Missile(missile.id(), seconds, missile.hit(), missile.speedMetersPerSecond(), missile.requiresLiveGuidance()));
        }
        flying.clear(); flying.addAll(remaining);
        double damage = hits.stream().mapToDouble(WeaponHit::damage).sum();
        if (damage > 0) {
            logs.add("Projectiles reached " + target + "; accuracy and shield, armor and electronics effects resolve on impact.");
            actions.add(new TacticalCombatProcessor.CombatActionReport("MISSILE_IMPACT", "Launched projectiles reached their observed target fleet", damage, target));
        }
        return List.copyOf(hits);
    }

    void launch(List<ShipCombatPower.Result> ships, double meters, double stance, String target,
                List<String> logs, List<TacticalCombatProcessor.CombatActionReport> actions) {
        for (var ship : ships) for (var launch : ship.missileLaunches()) {
            double flight = Math.max(TacticalCombatProcessor.SECONDS_PER_ROUND, meters / launch.speedMetersPerSecond());
            flying.add(new Missile(nextId++, flight, new WeaponHit(launch.weapon(), launch.damage() * stance, meters), launch.speedMetersPerSecond(), launch.requiresLiveGuidance()));
            logs.add(ship.ship().id() + ": " + launch.weaponId() + " projectile launched; " + flight + " seconds to impact after this round.");
            actions.add(new TacticalCombatProcessor.CombatActionReport("MISSILE_LAUNCH", launch.weaponId() + " projectile launched", 0, target));
        }
    }

    /** Launch only hits already funded by finite auxiliary stores. */
    void launchHits(List<WeaponHit> hits, String target,
                      List<String> logs, List<TacticalCombatProcessor.CombatActionReport> actions) {
        for (var hit : hits) {
            var weapon = hit.weapon(); var stats = weapon.operationalStats();
            double speed = stats.get("projectileSpeedMetersPerSecond");
            double flight = Math.max(TacticalCombatProcessor.SECONDS_PER_ROUND, hit.separationMeters() / speed);
            flying.add(new Missile(nextId++, flight, hit,
                    speed, stats.getOrDefault("requiresLiveGuidance", 0.0) > 0));
            logs.add(weapon.name() + " projectile launched; " + flight + " seconds to impact after this round.");
            actions.add(new TacticalCombatProcessor.CombatActionReport("PROJECTILE_LAUNCH", weapon.name() + " fired", 0, target));
        }
    }

    void end(List<String> logs, String reason) {
        if (!flying.isEmpty()) logs.add(flying.size() + " projectile(s) discarded: " + reason + ".");
        flying.clear();
    }
}
