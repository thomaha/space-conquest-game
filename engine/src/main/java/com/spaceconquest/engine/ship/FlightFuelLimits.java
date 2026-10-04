package com.spaceconquest.engine.ship;

/** Finds the first unavailable powered burn without iterating through voyage days. */
public final class FlightFuelLimits {
    private FlightFuelLimits() {}

    public static double availableHours(Fleet fleet, double requestedHours) {
        if (fleet.interstellarFuelBudgetKg().isEmpty() || fleet.location().inTransit() || !fleet.hasInterstellarOrder()
                || !(Fleet.MODE_SUBLIGHT.equals(fleet.interstellarMode())
                || Fleet.MODE_RECOVERY.equals(fleet.interstellarMode()))) return requestedHours;
        double initial = fraction(fleet, 0), end = fraction(fleet, requestedHours);
        double limit = requestedHours;
        for (var ship : fleet.ships()) {
            double budget = fleet.interstellarFuelBudgetKg().getOrDefault(ship.id(), 0.0);
            if (budget <= 0 || budget * (end - initial) <= ship.currentFuelKg() + 1e-8) continue;
            double low = 0, high = requestedHours;
            for (int iteration = 0; iteration < 64; iteration++) {
                double middle = (low + high) / 2;
                if (budget * (fraction(fleet, middle) - initial) <= ship.currentFuelKg()) low = middle;
                else high = middle;
            }
            limit = Math.min(limit, low);
        }
        return limit;
    }

    private static double fraction(Fleet fleet, double hours) {
        if (Fleet.MODE_RECOVERY.equals(fleet.interstellarMode())) {
            var motion = fleet.flightMotion();
            var trajectory = motion.trajectory();
            double total = trajectory.impulse(trajectory.totalSeconds());
            return total <= 0 ? 0 : trajectory.impulse(motion.elapsedSeconds() + hours * 3600) / total;
        }
        return InterstellarTravel.fuelBurnFraction(fleet.interstellarDistanceMeters(),
                fleet.interstellarAccelerationMps2(), fleet.interstellarPeakSpeedMps(),
                fleet.interstellarElapsedDays() * 86400 + hours * 3600);
    }
}
