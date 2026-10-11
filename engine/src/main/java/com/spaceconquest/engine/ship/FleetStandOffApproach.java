package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.combat.TacticalCombatProcessor;

/** Bounded funded parking candidates for a stationary target around the same body. */
public final class FleetStandOffApproach {
    public record Approach(FleetLocation.Site destination, LocalTravel.Plan plan, double separationMeters) {}
    private static final double HOURS = TacticalCombatProcessor.SECONDS_PER_ROUND / 3600;
    private FleetStandOffApproach() {}

    static double hostileRange(GameState state, Fleet target) {
        return target.ships().stream().mapToDouble(ship ->
                ShipWeaponRange.maximumRange(FleetSupplySimulation.design(state, ship), ship)).max().orElse(0);
    }

    static double fundedRange(GameState state, Fleet hunter) {
        return hunter.ships().stream().mapToDouble(ship -> {
            var design = FleetSupplySimulation.design(state, ship);
            double range = ShipWeaponRange.maximumRange(design, ship);
            return ShipCombatPower.fundedFirepower(ship, design, HOURS, true, range) > 0 ? range : 0;
        }).max().orElse(0);
    }

    public static Approach plan(GameState state, Fleet hunter, Fleet target) {
        if (state == null || hunter == null || target == null || hunter.location().inTransit()
                || target.location().inTransit() || !FleetInterceptProcessor.contact(state, hunter, target)) return null;
        var from = OrbitalTravel.parking(state, hunter.currentSystemId(), hunter.location().current());
        var to = OrbitalTravel.parking(state, target.currentSystemId(), target.location().current());
        if (from == null || to == null || !from.body().id().equals(to.body().id())) return null;
        double hostileRange = hostileRange(state, target), ownRange = fundedRange(state, hunter);
        if (ownRange <= hostileRange * 1.05) return null;
        Approach best = null;
        for (double fraction : new double[]{.9, .5, .25, .125, .0625, .03125, .015625, .0078125, .00390625}) for (int side : new int[]{-1, 1}) {
            double altitude = to.altitudeKm() + side * ownRange * fraction / 1000;
            if (altitude <= 0) continue;
            var destination = FleetLocation.Site.orbit(to.body().id(), altitude);
            var travel = LocalTravel.plan(state, hunter, destination);
            if (travel == null || travel.orbital() == null || travel.days() > 7
                    || !ShipPowerForecast.ready(ShipPowerForecast.departure(state, hunter, destination, travel, null))) continue;
            var arrival = LocalTravel.arrivalPreview(state, hunter, destination, travel);
            var future = state.withTurn(state.turn() + (int) Math.ceil(travel.days()));
            double distance = ShipWeaponRange.separation(future, arrival, target);
            if (distance <= hostileRange * 1.05 || distance > ownRange * .95
                    || arrival.ships().stream().noneMatch(ship -> ShipCombatPower.fundedFirepower(ship,
                    FleetSupplySimulation.design(future, ship), HOURS, true, distance) > 0)) continue;
            if (best == null || distance > best.separationMeters()) best = new Approach(destination, travel, distance);
        }
        return best;
    }
}
