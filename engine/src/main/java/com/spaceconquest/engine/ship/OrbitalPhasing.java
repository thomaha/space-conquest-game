package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import java.util.Map;
import static com.spaceconquest.engine.ship.VectorFlight.*;

/** Bounded same-radius phasing ellipses with two paid finite burns and actual central gravity. */
final class OrbitalPhasing {
    private OrbitalPhasing() {}

    static LocalTravel.Plan plan(GameState state, Fleet fleet, FleetLocation.Site destination,
                                 OrbitalTravel.Parking parking, OrbitalRecovery.InitialState actual,
                                 Map<String, LocalSpaceTravel.Capability> capabilities, double acceleration, double deltaV) {
        return plan(state, fleet, destination, parking, actual, capabilities, acceleration, deltaV, null);
    }

    static LocalTravel.Plan plan(GameState state, Fleet fleet, FleetLocation.Site destination,
                                 OrbitalTravel.Parking parking, OrbitalRecovery.InitialState actual,
                                 Map<String, LocalSpaceTravel.Capability> capabilities, double acceleration, double deltaV, Fleet targetFleet) {
        return plan(state, fleet, destination, parking, actual, capabilities, acceleration, deltaV, targetFleet, 3);
    }
    static LocalTravel.Plan plan(GameState state, Fleet fleet, FleetLocation.Site destination,
                                 OrbitalTravel.Parking parking, OrbitalRecovery.InitialState actual,
                                 Map<String, LocalSpaceTravel.Capability> capabilities, double acceleration, double deltaV, Fleet targetFleet, int maximumRevolutions) {
        double radius = norm(actual.position()), mu = actual.gravity().gravitationalParameter();
        double targetRadius = (parking.body().diameter() / 2 + parking.altitudeKm()) * 1000;
        if (Math.abs(radius - targetRadius) > 1) return null;
        double speed = Math.sqrt(mu / radius), period = 2 * Math.PI * radius / speed;
        var station = target(state, destination, parking, state.turn(), targetFleet);
        double phase = Math.atan2(actual.position().yMeters(), actual.position().xMeters())
                - Math.atan2(station.position().yMeters(), station.position().xMeters());
        phase = Math.atan2(Math.sin(phase), Math.cos(phase));
        for (int offset : new int[]{0, -1, 1}) {
            for (int revolutions = 1; revolutions <= maximumRevolutions; revolutions++) {
                double seconds = period * (revolutions + offset + phase / (2 * Math.PI));
                if (seconds <= 0 || seconds > 7 * 86400) continue;
                double axis = Math.cbrt(mu * Math.pow(seconds / (2 * Math.PI * revolutions), 2));
                double opposite = 2 * axis - radius;
                if (opposite <= actual.gravity().minimumRadiusMeters() || opposite >= actual.gravity().maximumRadiusMeters()) continue;
                double change = Math.sqrt(mu * (2 / radius - 1 / axis)) - speed;
                if (!Double.isFinite(change) || Math.abs(change) < 1e-9 || 2 * Math.abs(change) > deltaV) continue;
                double burn = Math.max(2, 1.5 * Math.abs(change) / acceleration);
                if (!Double.isFinite(burn) || burn > period / 50 || 2 * burn >= seconds) continue;
                var goal = target(state, destination, parking, state.turn() + seconds / 86400, targetFleet);
                var first = scale(actual.velocity(), change / (speed * burn));
                var last = scale(goal.velocity(), -change / (speed * burn));
                var trajectory = solve(actual, goal, seconds, burn, first, last, acceleration, deltaV);
                if (trajectory == null) continue;
                var geometry = new LocalSiteGeometry.Leg(actual.position(), goal.position(), Math.max(1, norm(subtract(goal.position(), actual.position()))));
                var physical = VectorInterceptPlanner.budget(state, fleet, geometry, trajectory, capabilities);
                var plan = new LocalTravel.Plan(physical.days(), physical.propellantKg(), physical.reactorFuelKg(), physical);
                if (ShipPowerForecast.ready(ShipPowerForecast.departure(state, fleet, destination, plan, null))) return plan;
            }
        }
        return null;
    }

    private static StationRendezvous.Motion target(GameState state, FleetLocation.Site destination, OrbitalTravel.Parking parking, double day, Fleet targetFleet) {
        if (targetFleet != null) return BodyFleetRendezvous.predicted(state, targetFleet, (day - state.turn()) * 86400);
        return destination.kind() == FleetLocation.Kind.ORBIT ? StationRendezvous.at(parking.body(), parking.altitudeKm(), day)
                : StationRendezvous.at(state, destination.entityId(), day);
    }
    private static VectorFlight.Trajectory solve(OrbitalRecovery.InitialState actual, StationRendezvous.Motion goal,
                                                 double seconds, double burn, LocalSiteGeometry.Point first,
                                                 LocalSiteGeometry.Point last, double acceleration, double deltaV) {
        double[] controls = {first.xMeters(), first.yMeters(), last.xMeters(), last.yMeters()};
        for (int iteration = 0; iteration < 20; iteration++) {
            var trajectory = trajectory(actual, seconds, burn, controls);
            var error = error(trajectory, goal);
            if (error == null) return null;
            if (Math.hypot(error[0], error[1]) * seconds <= .01 && Math.hypot(error[2], error[3]) <= .00001) {
                return norm(trajectory.firstAcceleration()) <= acceleration && norm(trajectory.lastAcceleration()) <= acceleration
                        && trajectory.impulse(seconds) <= deltaV ? trajectory : null;
            }
            double[][] matrix = new double[4][5];
            for (int column = 0; column < 4; column++) {
                double step = Math.max(1e-6, Math.abs(controls[column]) * 1e-4);
                var shifted = controls.clone(); shifted[column] += step;
                var changed = error(trajectory(actual, seconds, burn, shifted), goal);
                shifted[column] -= 2 * step;
                var opposite = error(trajectory(actual, seconds, burn, shifted), goal);
                if (changed == null || opposite == null) return null;
                for (int row = 0; row < 4; row++) matrix[row][column] = (changed[row] - opposite[row]) / (2 * step);
            }
            for (int row = 0; row < 4; row++) matrix[row][4] = -error[row];
            var correction = eliminate(matrix);
            if (correction == null) return null;
            boolean improved = false;
            for (double fraction = 1; fraction >= 1.0 / 16; fraction /= 2) {
                var shifted = controls.clone();
                for (int column = 0; column < 4; column++) shifted[column] += fraction * correction[column];
                var candidate = trajectory(actual, seconds, burn, shifted);
                if (norm(candidate.firstAcceleration()) > acceleration || norm(candidate.lastAcceleration()) > acceleration
                        || candidate.impulse(seconds) > deltaV) continue;
                var changed = error(candidate, goal);
                if (changed != null && magnitude(changed) < magnitude(error)) { controls = shifted; improved = true; break; }
            }
            if (!improved) return null;
        }
        return null;
    }

    private static VectorFlight.Trajectory trajectory(OrbitalRecovery.InitialState actual, double seconds, double burn, double[] controls) {
        return new VectorFlight.Trajectory(actual.position(), actual.velocity(),
                new LocalSiteGeometry.Point(controls[0], controls[1], 0), new LocalSiteGeometry.Point(controls[2], controls[3], 0),
                burn, seconds - 2 * burn, actual.gravity());
    }

    private static double[] error(VectorFlight.Trajectory trajectory, StationRendezvous.Motion goal) {
        try {
            var end = trajectory.at(trajectory.totalSeconds());
            var p = scale(subtract(end.position(), goal.position()), 1 / trajectory.totalSeconds());
            var v = subtract(end.velocity(), goal.velocity());
            return new double[]{p.xMeters(), p.yMeters(), v.xMeters(), v.yMeters()};
        } catch (IllegalArgumentException outsideDomain) { return null; }
    }

    private static double magnitude(double[] vector) {
        double sum = 0; for (double value : vector) sum += value * value; return sum;
    }

    private static double[] eliminate(double[][] matrix) {
        for (int column = 0; column < 4; column++) {
            int pivot = column;
            for (int row = column + 1; row < 4; row++) if (Math.abs(matrix[row][column]) > Math.abs(matrix[pivot][column])) pivot = row;
            if (!Double.isFinite(matrix[pivot][column]) || Math.abs(matrix[pivot][column]) < 1e-12) return null;
            var swap = matrix[column]; matrix[column] = matrix[pivot]; matrix[pivot] = swap;
            double divisor = matrix[column][column];
            for (int item = column; item <= 4; item++) matrix[column][item] /= divisor;
            for (int row = 0; row < 4; row++) {
                if (row == column) continue;
                double factor = matrix[row][column];
                for (int item = column; item <= 4; item++) matrix[row][item] -= factor * matrix[column][item];
            }
        }
        var result = new double[]{matrix[0][4], matrix[1][4], matrix[2][4], matrix[3][4]};
        for (double value : result) if (!Double.isFinite(value)) return null;
        return result;
    }
}
