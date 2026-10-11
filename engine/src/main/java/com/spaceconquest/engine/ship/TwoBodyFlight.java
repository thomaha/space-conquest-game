package com.spaceconquest.engine.ship;

import static com.spaceconquest.engine.ship.VectorFlight.*;

/** Bounded RK4 propagation with central gravity and phase-constant funded thrust. */
public final class TwoBodyFlight {
    private static final LocalSiteGeometry.Point ZERO = new LocalSiteGeometry.Point(0, 0, 0);
    private TwoBodyFlight() {}
    public record Gravity(double gravitationalParameter, double minimumRadiusMeters,
                          OrbitalFlight.Frame frame, double maximumRadiusMeters) {
        public Gravity(double gravitationalParameter, double minimumRadiusMeters) {
            this(gravitationalParameter, minimumRadiusMeters, null, Double.MAX_VALUE);
        }
        public Gravity {
            if (frame == null) frame = new OrbitalFlight.Frame("", OrbitalFlight.CenterKind.STELLAR);
            if (maximumRadiusMeters == 0) maximumRadiusMeters = Double.MAX_VALUE;
            if (!Double.isFinite(gravitationalParameter) || gravitationalParameter <= 0
                    || !Double.isFinite(minimumRadiusMeters) || minimumRadiusMeters <= 0
                    || !Double.isFinite(maximumRadiusMeters) || maximumRadiusMeters <= minimumRadiusMeters)
                throw new IllegalArgumentException("Invalid central gravity");
        }
    }
    private record State(LocalSiteGeometry.Point position, LocalSiteGeometry.Point velocity) {}

    public static VectorFlight advance(VectorFlight flight, double seconds, boolean interrupted) {
        return propagate(flight, seconds, interrupted, false);
    }
    public static VectorFlight predict(VectorFlight flight, double seconds) {
        return propagate(flight, seconds, false, true);
    }
    private static VectorFlight propagate(VectorFlight flight, double seconds, boolean interrupted, boolean rejectInterior) {
        if (!Double.isFinite(seconds) || seconds < 0 || seconds > 30 * 86400)
            throw new IllegalArgumentException("Gravity propagation exceeds its bounded interval");
        var trajectory = flight.trajectory(); var state = new State(flight.position(), flight.velocity());
        double cursor = flight.elapsedSeconds(), end = cursor + seconds;
        while (cursor < end) {
            LocalSiteGeometry.Point thrust = ZERO; double boundary = end;
            if (!interrupted && cursor < trajectory.burnSeconds()) {
                thrust = trajectory.firstAcceleration(); boundary = Math.min(end, trajectory.burnSeconds());
            } else if (!interrupted && cursor < trajectory.burnSeconds() + trajectory.coastSeconds()) {
                boundary = Math.min(end, trajectory.burnSeconds() + trajectory.coastSeconds());
            } else if (!interrupted && cursor < trajectory.totalSeconds()) {
                thrust = trajectory.lastAcceleration(); boundary = Math.min(end, trajectory.totalSeconds());
            }
            while (cursor < boundary) {
                double radius = Math.max(trajectory.gravity().minimumRadiusMeters(), norm(state.position()));
                double period = 2 * Math.PI * Math.sqrt(radius / trajectory.gravity().gravitationalParameter()) * radius;
                double dt = Math.min(boundary - cursor, Math.min(120, period / 2048));
                if (!(dt > 0) || cursor + dt <= cursor) throw new IllegalArgumentException("Gravity step cannot advance time");
                state = step(state, thrust, trajectory.gravity(), dt, rejectInterior);
                cursor += dt;
            }
        }
        return new VectorFlight(trajectory, state.position(), state.velocity(), interrupted ? flight.elapsedSeconds() : end);
    }

    private static State step(State s, LocalSiteGeometry.Point thrust, Gravity gravity, double dt, boolean rejectInterior) {
        var a1 = acceleration(s.position(), thrust, gravity, rejectInterior);
        var v2 = add(s.velocity(), scale(a1, dt / 2));
        var a2 = acceleration(add(s.position(), scale(s.velocity(), dt / 2)), thrust, gravity, rejectInterior);
        var v3 = add(s.velocity(), scale(a2, dt / 2));
        var a3 = acceleration(add(s.position(), scale(v2, dt / 2)), thrust, gravity, rejectInterior);
        var v4 = add(s.velocity(), scale(a3, dt));
        var a4 = acceleration(add(s.position(), scale(v3, dt)), thrust, gravity, rejectInterior);
        var position = add(s.position(), scale(add(add(s.velocity(), scale(v2, 2)), add(scale(v3, 2), v4)), dt / 6));
        var velocity = add(s.velocity(), scale(add(add(a1, scale(a2, 2)), add(scale(a3, 2), a4)), dt / 6));
        acceleration(position, thrust, gravity, rejectInterior);
        if (norm(velocity) >= InterstellarTravel.MAX_CRUISE_MPS) throw new IllegalArgumentException("Gravity flight exceeds speed limit");
        return new State(position, velocity);
    }

    private static LocalSiteGeometry.Point acceleration(LocalSiteGeometry.Point position, LocalSiteGeometry.Point thrust, Gravity gravity, boolean rejectInterior) {
        double radius = norm(position);
        if (rejectInterior && radius < gravity.minimumRadiusMeters()) throw new IllegalArgumentException("Gravity flight enters the central body");
        if (rejectInterior && radius >= gravity.maximumRadiusMeters()) throw new IllegalArgumentException("Gravity flight leaves its frame domain");
        // No collision damage rule is introduced. A uniform-density interior keeps unfunded drift finite.
        radius = Math.max(radius, gravity.minimumRadiusMeters());
        return add(thrust, scale(position, -gravity.gravitationalParameter() / (radius * radius * radius)));
    }
}
