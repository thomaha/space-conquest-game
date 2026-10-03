package com.spaceconquest.engine.ship;

/** Position along the original crossing, including drift beyond its destination. */
public record FlightMotion(double positionMeters, double velocityMps, Trajectory trajectory,
                           double elapsedSeconds) {
    public FlightMotion {
        for (double value : new double[]{positionMeters, velocityMps, elapsedSeconds})
            if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid flight motion");
    }

    /** An analytic accelerate, coast and brake itinerary beginning at a nonzero velocity. */
    public record Trajectory(double startMeters, double initialVelocityMps, double accelerationMps2,
                             double peakMps, double accelerationSeconds, double coastSeconds,
                             double brakingSeconds, double finalVelocityMps, RescueOrder rescueOrder) {
        public Trajectory(double startMeters, double initialVelocityMps, double accelerationMps2,
                          double peakMps, double accelerationSeconds, double coastSeconds, double brakingSeconds) {
            this(startMeters, initialVelocityMps, accelerationMps2, peakMps, accelerationSeconds,
                    coastSeconds, brakingSeconds, 0, null);
        }
        public Trajectory {
            for (double value : new double[]{startMeters, initialVelocityMps, accelerationMps2,
                    peakMps, accelerationSeconds, coastSeconds, brakingSeconds, finalVelocityMps})
                if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid recovery trajectory");
            if (accelerationMps2 <= 0 || peakMps <= 0) throw new IllegalArgumentException("Invalid recovery thrust");
            if (finalVelocityMps > peakMps) throw new IllegalArgumentException("Invalid matching velocity");
        }
        public double totalSeconds() { return accelerationSeconds + coastSeconds + brakingSeconds; }
        public double brakingStart() { return accelerationSeconds + coastSeconds; }
        public boolean burning(double seconds) {
            return seconds < accelerationSeconds || seconds >= brakingStart() && seconds < totalSeconds();
        }
        public double impulse(double seconds) {
            return accelerationMps2 * (Math.clamp(seconds, 0, accelerationSeconds)
                    + Math.clamp(seconds - brakingStart(), 0, brakingSeconds));
        }
        public FlightMotion at(double seconds) {
            double accelerate = Math.clamp(seconds, 0, accelerationSeconds);
            double coast = Math.clamp(seconds - accelerationSeconds, 0, coastSeconds);
            double brake = Math.clamp(seconds - brakingStart(), 0, brakingSeconds);
            double position = startMeters + initialVelocityMps * accelerate
                    + .5 * accelerationMps2 * accelerate * accelerate + peakMps * coast
                    + peakMps * brake - .5 * accelerationMps2 * brake * brake;
            double velocity = seconds < accelerationSeconds
                    ? initialVelocityMps + accelerationMps2 * accelerate
                    : Math.max(finalVelocityMps, peakMps - accelerationMps2 * brake);
            return new FlightMotion(position, velocity, this, Math.min(seconds, totalSeconds()));
        }
    }
}
