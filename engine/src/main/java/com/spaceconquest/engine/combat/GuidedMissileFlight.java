package com.spaceconquest.engine.combat;

import com.spaceconquest.engine.ship.ShipCombatPower;
import com.spaceconquest.engine.ship.ShipMissileWeapons;
import com.spaceconquest.engine.ship.ShipPointDefense;
import java.util.ArrayList;
import java.util.List;

/** Tick-local guided flights within the bounded tactical engagement. No hidden position lookup or reacquisition. */
final class GuidedMissileFlight {
    private record Missile(double secondsLeft, double damage) {}
    private final List<Missile> flying = new ArrayList<>();

    List<Double> threats(double sensorRangeMeters, boolean guidanceContact) {
        if (!guidanceContact || sensorRangeMeters <= 0) return List.of();
        return flying.stream().map(GuidedMissileFlight::nextDistance).filter(distance -> distance <= sensorRangeMeters).sorted().toList();
    }

    void intercept(List<ShipPointDefense.Shot> shots, String defendedFleet, List<String> logs,
                   List<TacticalCombatProcessor.CombatActionReport> actions) {
        flying.sort(java.util.Comparator.comparingDouble(GuidedMissileFlight::nextDistance));
        var destroyed = new java.util.LinkedHashMap<String, Integer>();
        for (var shot : shots) {
            var missile = flying.stream().filter(candidate -> nextDistance(candidate) <= shot.rangeMeters()).findFirst().orElse(null);
            if (missile != null) {
                flying.remove(missile);
                destroyed.merge(shot.weaponId(), 1, Integer::sum);
            }
        }
        destroyed.forEach((weapon, count) -> {
            logs.add(defendedFleet + ": " + weapon + " intercepted " + count + " incoming missile(s) before impact.");
            actions.add(new TacticalCombatProcessor.CombatActionReport("POINT_DEFENSE", weapon + " destroyed " + count + " incoming missile(s)", 0, defendedFleet));
        });
    }

    private static double nextDistance(Missile missile) {
        return Math.max(0, missile.secondsLeft() - TacticalCombatProcessor.SECONDS_PER_ROUND) * ShipMissileWeapons.SPEED_METERS_PER_SECOND;
    }

    double advance(boolean contact, String target, List<String> logs,
                   List<TacticalCombatProcessor.CombatActionReport> actions) {
        if (!contact) { end(logs, "live guidance contact lost"); return 0; }
        double damage = 0;
        var remaining = new ArrayList<Missile>();
        for (var missile : flying) {
            double seconds = missile.secondsLeft() - TacticalCombatProcessor.SECONDS_PER_ROUND;
            if (seconds <= 1e-9) damage += missile.damage();
            else remaining.add(new Missile(seconds, missile.damage()));
        }
        flying.clear(); flying.addAll(remaining);
        if (damage > 0) {
            logs.add("Guided missile impact on " + target + ": " + damage + " damage.");
            actions.add(new TacticalCombatProcessor.CombatActionReport("MISSILE_IMPACT", "Guided missiles reached their observed target fleet", damage, target));
        }
        return damage;
    }

    void launch(List<ShipCombatPower.Result> ships, double meters, double stance, String target,
                List<String> logs, List<TacticalCombatProcessor.CombatActionReport> actions) {
        double flight = Math.max(TacticalCombatProcessor.SECONDS_PER_ROUND, meters / ShipMissileWeapons.SPEED_METERS_PER_SECOND);
        for (var ship : ships) for (var launch : ship.missileLaunches()) {
            flying.add(new Missile(flight, launch.damage() * stance));
            logs.add(ship.ship().id() + ": guided missile launched; " + flight + " seconds to impact after this round.");
            actions.add(new TacticalCombatProcessor.CombatActionReport("MISSILE_LAUNCH", "One carried guided missile launched", 0, target));
        }
    }

    void end(List<String> logs, String reason) {
        if (!flying.isEmpty()) logs.add(flying.size() + " guided missile(s) self-destruct: " + reason + ".");
        flying.clear();
    }
}
