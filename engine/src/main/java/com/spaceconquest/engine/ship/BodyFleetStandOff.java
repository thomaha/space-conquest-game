package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import java.util.HashMap;
import static com.spaceconquest.engine.ship.VectorFlight.*;

/** Paid same-body moving engagement at an offset, with target velocity matched at arrival. */
public final class BodyFleetStandOff {
    private BodyFleetStandOff() {}

    public static LocalTravel.Plan plan(GameState state, Fleet hunter, Fleet target) {
        if (state == null || hunter == null || target == null || !target.location().inTransit()
                || !FleetInterceptProcessor.contact(state, hunter, target) || hunter.hasInterstellarOrder() || hunter.isInWarp()
                || hunter.location().inTransit() && !BodyFleetRendezvous.ballisticAvailable(hunter)) return null;
        var preferred = FleetCombatPositioning.preferred(state, hunter, target);
        if (preferred == null || preferred.meters() <= 0) return null;
        double range = preferred.meters();
        var actual = BodyFleetRendezvous.initial(state, hunter); var observed = BodyFleetRendezvous.initial(state, target);
        double horizon = BodyFleetRendezvous.horizon(target);
        if (actual == null || observed == null || horizon < 86400
                || actual.gravity().frame().kind() == OrbitalFlight.CenterKind.STELLAR
                || !actual.gravity().frame().equals(observed.gravity().frame())) return null;
        var destination = target.location().destination();
        var capabilities = new HashMap<String, LocalSpaceTravel.Capability>();
        double acceleration = Double.POSITIVE_INFINITY, deltaV = Double.POSITIVE_INFINITY;
        for (var ship : hunter.ships()) {
            var capability = LocalSpaceTravel.capability(state, hunter, ship, destination, hunter.fuelPolicy().reserveKg(state, hunter, ship));
            if (capability == null || ship.currentHullHealth() <= 0) return null;
            capabilities.put(ship.id(), capability);
            acceleration = Math.min(acceleration, capability.accelerationMps2()); deltaV = Math.min(deltaV, capability.deltaVMps());
        }
        for (int day = 1; day <= 7 && day * 86400 <= horizon; day++) for (int direction = 0; direction < 2; direction++) {
            double seconds = day * 86400;
            try {
                var prediction = BodyFleetRendezvous.predicted(state, target, seconds);
                var axis = direction == 0 ? prediction.position() : prediction.velocity();
                if (norm(axis) <= 0) continue;
                var goal = add(prediction.position(), scale(axis, range / norm(axis)));
                var trajectory = OrbitalRendezvousPlanner.shoot(actual.position(), actual.velocity(), goal, prediction.velocity(),
                        seconds, actual.gravity(), acceleration, deltaV);
                if (trajectory == null) continue;
                var end = trajectory.at(seconds); double distance = norm(subtract(end.position(), prediction.position()));
                if (Math.abs(distance - range) > .1
                        || norm(subtract(end.velocity(), prediction.velocity())) > .0005) continue;
                var geometry = new LocalSiteGeometry.Leg(actual.position(), goal, Math.max(1, norm(subtract(goal, actual.position()))));
                var physical = VectorInterceptPlanner.budget(state, hunter, geometry, trajectory, capabilities);
                var plan = new LocalTravel.Plan(physical.days(), physical.propellantKg(), physical.reactorFuelKg(), physical);
                if (!ShipPowerForecast.ready(ShipPowerForecast.departure(state, hunter, destination, plan, null))) continue;
                var arrival = LocalTravel.arrivalPreview(state, hunter, destination, plan);
                if (FleetCombatPositioning.quote(state, arrival, target, distance).usefulDamage() > 0) return plan;
            } catch (IllegalArgumentException outsideDomain) { /* Keep the old approach when no safe funded shot exists. */ }
        }
        return null;
    }
}
