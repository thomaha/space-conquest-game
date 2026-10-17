package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.combat.CombatPositioningDoctrine;

/** Bounded funded parking candidates for a stationary target around the same body. */
public final class FleetStandOffApproach {
    public record Approach(FleetLocation.Site destination, LocalTravel.Plan plan, double separationMeters) {}
    private FleetStandOffApproach() {}

    public static Approach plan(GameState state, Fleet hunter, Fleet target) {
        if (state == null || hunter == null || target == null || hunter.location().inTransit()
                || target.location().inTransit() || !FleetInterceptProcessor.contact(state, hunter, target)) return null;
        var from = OrbitalTravel.parking(state, hunter.currentSystemId(), hunter.location().current());
        var to = OrbitalTravel.parking(state, target.currentSystemId(), target.location().current());
        if (from == null || to == null || !from.body().id().equals(to.body().id())) return null;
        var candidates = FleetCombatPositioning.candidates(state, hunter, target);
        if (candidates.isEmpty()) return null;
        var doctrine = CombatPositioningDoctrine.defaults();
        double currentDistance = ShipWeaponRange.separation(state, hunter, target);
        double currentScore = Double.isFinite(currentDistance) ? FleetCombatPositioning.quote(state, hunter, target, currentDistance).score() : 0;
        boolean currentFire = ShipWeaponRange.reaches(state, hunter, target);
        Approach best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (double offset : candidates) for (int side : new int[]{-1, 1}) {
            double altitude = to.altitudeKm() + side * offset / 1000;
            if (altitude <= 0) continue;
            var destination = FleetLocation.Site.orbit(to.body().id(), altitude);
            var travel = LocalTravel.plan(state, hunter, destination);
            if (travel == null || travel.orbital() == null || travel.days() > 7
                    || !ShipPowerForecast.ready(ShipPowerForecast.departure(state, hunter, destination, travel, null))) continue;
            var arrival = LocalTravel.arrivalPreview(state, hunter, destination, travel);
            var future = state.withTurn(state.turn() + (int) Math.ceil(travel.days()));
            future = future.withFleets(future.fleets().stream().map(fleet -> fleet.id().equals(arrival.id()) ? arrival : fleet).toList());
            if (!com.spaceconquest.engine.galaxy.SensorCoverage.detects(future, hunter.ownerEntityId(), target)) continue;
            double distance = ShipWeaponRange.separation(future, arrival, target);
            var quote = FleetCombatPositioning.quote(future, arrival, target, distance);
            double score = FleetCombatPositioning.arrivalScore(quote, travel.days());
            if (quote.usefulDamage() <= 0 || currentFire && score <= currentScore + doctrine.minimumImprovement()) continue;
            if (best == null || score > bestScore) {
                best = new Approach(destination, travel, distance); bestScore = score;
            }
        }
        return best;
    }
}
