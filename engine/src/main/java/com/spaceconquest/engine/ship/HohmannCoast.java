package com.spaceconquest.engine.ship;

/** Analytic two-body ellipse or circular orbit. Maneuvers and finite burns are separate operations. */
public record HohmannCoast(double gravitationalParameter, double departureRadiusMeters,
                          double arrivalRadiusMeters, double departureAngleRadians) {
    public HohmannCoast {
        CircularOrbitalEphemeris.positive(gravitationalParameter);
        CircularOrbitalEphemeris.positive(departureRadiusMeters);
        CircularOrbitalEphemeris.positive(arrivalRadiusMeters);
        if (!Double.isFinite(departureAngleRadians))
            throw new IllegalArgumentException("A finite departure angle is required");
        double axis = (departureRadiusMeters + arrivalRadiusMeters) / 2;
        double eccentricity = Math.abs(arrivalRadiusMeters - departureRadiusMeters) / (2 * axis);
        if (!Double.isFinite(axis) || !Double.isFinite(eccentricity) || eccentricity >= 1)
            throw new IllegalArgumentException("A representable bound transfer ellipse is required");
        CircularOrbitalEphemeris.positive(Math.sqrt(gravitationalParameter / axis) / axis);
        CircularOrbitalEphemeris.positive(2 * Math.PI / (Math.sqrt(gravitationalParameter / axis) / axis));
        departureAngleRadians = CircularOrbitalEphemeris.angle(departureAngleRadians);
    }

    public double axisMeters() { return (departureRadiusMeters + arrivalRadiusMeters) / 2; }
    public double eccentricity() { return Math.abs(arrivalRadiusMeters - departureRadiusMeters) / (2 * axisMeters()); }
    public double meanMotionRadiansPerSecond() { return Math.sqrt(gravitationalParameter / axisMeters()) / axisMeters(); }
    public double coastSeconds() { return Math.PI / meanMotionRadiansPerSecond(); }

    /** Continues the ellipse after a missed capture; no arrival or circularization is implied. */
    public CircularOrbitalEphemeris.State at(double elapsedSeconds) {
        if (!Double.isFinite(elapsedSeconds) || elapsedSeconds < 0)
            throw new IllegalArgumentException("Finite nonnegative elapsed coast time is required");
        boolean outward = arrivalRadiusMeters > departureRadiusMeters;
        double n = meanMotionRadiansPerSecond();
        double mean = CircularOrbitalEphemeris.angle((outward ? 0 : Math.PI)
                + (elapsedSeconds % (2 * coastSeconds())) * n);
        double eccentric = eccentricAnomaly(mean, eccentricity());
        double e = eccentricity(), a = axisMeters();
        double sine = Math.sin(eccentric), cosine = Math.cos(eccentric), minor = Math.sqrt(1 - e * e);
        double x = a * (cosine - e), y = a * minor * sine;
        double vx = -a * n * sine / (1 - e * cosine), vy = a * n * minor * cosine / (1 - e * cosine);
        double rotation = departureAngleRadians - (outward ? 0 : Math.PI);
        double c = Math.cos(rotation), s = Math.sin(rotation);
        return new CircularOrbitalEphemeris.State(x * c - y * s, x * s + y * c,
                vx * c - vy * s, vx * s + vy * c);
    }

    private static double eccentricAnomaly(double mean, double eccentricity) {
        if (mean == 0 || mean == Math.PI) return mean;
        double low = 0, high = 2 * Math.PI;
        // Monotonic Kepler equation for 0 <= e < 1; bounded work even near very eccentric orbits.
        for (int iteration = 0; iteration < 64; iteration++) {
            double middle = (low + high) / 2;
            if (middle - eccentricity * Math.sin(middle) < mean) low = middle;
            else high = middle;
        }
        return (low + high) / 2;
    }
}
