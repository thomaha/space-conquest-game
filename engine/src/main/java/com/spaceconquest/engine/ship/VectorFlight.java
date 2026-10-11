package com.spaceconquest.engine.ship;

/** Saved physical vector state for two finite burns with an intervening coast. */
public record VectorFlight(Trajectory trajectory, LocalSiteGeometry.Point position,
                           LocalSiteGeometry.Point velocity, double elapsedSeconds) {
    public VectorFlight {
        if (trajectory == null || position == null || velocity == null || !Double.isFinite(elapsedSeconds) || elapsedSeconds < 0)
            throw new IllegalArgumentException("Invalid vector flight");
    }
    public VectorFlight advance(double seconds, boolean interrupted) {
        if (trajectory.gravity() != null) return TwoBodyFlight.advance(this, seconds, interrupted);
        return interrupted ? new VectorFlight(trajectory, add(position, scale(velocity, seconds)), velocity, elapsedSeconds)
                : trajectory.at(elapsedSeconds + seconds);
    }

    public record Trajectory(LocalSiteGeometry.Point start, LocalSiteGeometry.Point initialVelocity,
                             LocalSiteGeometry.Point firstAcceleration, LocalSiteGeometry.Point lastAcceleration,
                             double burnSeconds, double coastSeconds, TwoBodyFlight.Gravity gravity) {
        public Trajectory(LocalSiteGeometry.Point start, LocalSiteGeometry.Point initialVelocity,
                          LocalSiteGeometry.Point firstAcceleration, LocalSiteGeometry.Point lastAcceleration,
                          double burnSeconds, double coastSeconds) {
            this(start, initialVelocity, firstAcceleration, lastAcceleration, burnSeconds, coastSeconds, null);
        }
        public Trajectory {
            if (start == null || initialVelocity == null || firstAcceleration == null || lastAcceleration == null
                    || !Double.isFinite(burnSeconds) || burnSeconds <= 0 || !Double.isFinite(coastSeconds) || coastSeconds < 0
                    || !Double.isFinite(2 * burnSeconds + coastSeconds) || !Double.isFinite(norm(firstAcceleration))
                    || !Double.isFinite(norm(lastAcceleration)) || !Double.isFinite(norm(initialVelocity))
                    || Math.max(norm(firstAcceleration), norm(lastAcceleration)) <= 0)
                throw new IllegalArgumentException("Invalid vector trajectory");
        }
        public double totalSeconds() { return 2 * burnSeconds + coastSeconds; }
        public double impulse(double seconds) {
            return norm(firstAcceleration) * Math.clamp(seconds, 0, burnSeconds)
                    + norm(lastAcceleration) * Math.clamp(seconds - burnSeconds - coastSeconds, 0, burnSeconds);
        }
        public VectorFlight at(double seconds) {
            if (gravity != null) return TwoBodyFlight.predict(new VectorFlight(this, start, initialVelocity, 0), seconds);
            double first = Math.clamp(seconds, 0, burnSeconds);
            double coast = Math.clamp(seconds - burnSeconds, 0, coastSeconds);
            double last = Math.clamp(seconds - burnSeconds - coastSeconds, 0, burnSeconds);
            var velocity = add(initialVelocity, scale(firstAcceleration, first));
            var position = add(start, add(scale(initialVelocity, first), scale(firstAcceleration, .5 * first * first)));
            position = add(position, scale(velocity, coast));
            position = add(position, add(scale(velocity, last), scale(lastAcceleration, .5 * last * last)));
            velocity = add(velocity, scale(lastAcceleration, last));
            position = add(position, scale(velocity, Math.max(0, seconds - totalSeconds())));
            return new VectorFlight(this, position, velocity, seconds);
        }
        /** Conservative full-power burn envelope; vector impulse remains authoritative for fuel. */
        public FlightMotion.Trajectory envelope() {
            double acceleration = Math.max(norm(firstAcceleration), norm(lastAcceleration));
            return new FlightMotion.Trajectory(0, 0, acceleration, acceleration * burnSeconds,
                    burnSeconds, coastSeconds, burnSeconds);
        }
    }
    public static LocalSiteGeometry.Point add(LocalSiteGeometry.Point a, LocalSiteGeometry.Point b) {
        return new LocalSiteGeometry.Point(a.xMeters() + b.xMeters(), a.yMeters() + b.yMeters(), a.zMeters() + b.zMeters());
    }
    public static LocalSiteGeometry.Point subtract(LocalSiteGeometry.Point a, LocalSiteGeometry.Point b) {
        return add(a, scale(b, -1));
    }
    public static LocalSiteGeometry.Point scale(LocalSiteGeometry.Point a, double factor) {
        return new LocalSiteGeometry.Point(a.xMeters() * factor, a.yMeters() * factor, a.zMeters() * factor);
    }
    public static double norm(LocalSiteGeometry.Point a) {
        return Math.hypot(Math.hypot(a.xMeters(), a.yMeters()), a.zMeters());
    }
    public static LocalSiteGeometry.Point velocity(LocalFlight flight) {
        return flight.vectorFlight() != null ? flight.vectorFlight().velocity()
                : scale(subtract(flight.geometry().destination(), flight.geometry().origin()),
                flight.motion().velocityMps() / flight.geometry().distanceMeters());
    }
    public static FlightMotion motion(VectorFlight vector, LocalSiteGeometry.Leg geometry) {
        var direction = scale(subtract(geometry.destination(), geometry.origin()), 1 / geometry.distanceMeters());
        var offset = subtract(vector.position(), geometry.origin());
        double projected = offset.xMeters() * direction.xMeters() + offset.yMeters() * direction.yMeters() + offset.zMeters() * direction.zMeters();
        return new FlightMotion(Math.max(0, projected), norm(vector.velocity()), vector.trajectory().envelope(),
                Math.min(vector.elapsedSeconds(), vector.trajectory().totalSeconds()));
    }
}
