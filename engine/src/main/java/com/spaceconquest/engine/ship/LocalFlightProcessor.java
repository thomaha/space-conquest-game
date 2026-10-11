package com.spaceconquest.engine.ship;

/** Tick-owned local burns and ballistic drift. Controlled arrival requires completed braking. */
public final class LocalFlightProcessor {
    private LocalFlightProcessor() {}

    public static double availableHours(Fleet fleet, double hours) {
        var flight = fleet.location().localFlight();
        if (flight == null || flight.interrupted()) return hours;
        double available = hours;
        for (var ship : fleet.ships()) {
            if (used(flight, ship.id(), hours) <= ship.currentFuelKg() + 1e-9) continue;
            double lower = 0, upper = hours;
            for (int iteration = 0; iteration < 64; iteration++) {
                double middle = (lower + upper) / 2;
                if (used(flight, ship.id(), middle) <= ship.currentFuelKg()) lower = middle;
                else upper = middle;
            }
            available = Math.min(available, lower);
        }
        return available;
    }

    private static double fraction(LocalFlight flight, String shipId, double elapsed) {
        var trajectory = flight.motion().trajectory();
        var propulsion = flight.propulsion().get(shipId);
        if (propulsion == null) return 0;
        double exhaust = propulsion.exhaustVelocityMps();
        double totalImpulse = flight.vectorFlight() == null ? trajectory.impulse(trajectory.totalSeconds())
                : flight.vectorFlight().trajectory().impulse(trajectory.totalSeconds());
        double impulse = flight.vectorFlight() == null ? trajectory.impulse(elapsed) : flight.vectorFlight().trajectory().impulse(elapsed);
        double total = -Math.expm1(-totalImpulse / exhaust);
        return total <= 0 ? 0 : -Math.expm1(-impulse / exhaust) / total;
    }

    private static double used(LocalFlight flight, String shipId, double hours) {
        double elapsed = flight.motion().elapsedSeconds();
        return flight.propellantKg().getOrDefault(shipId, 0.0)
                * Math.max(0, fraction(flight, shipId, elapsed + hours * 3600) - fraction(flight, shipId, elapsed));
    }

    static double remainingPropellantKg(Fleet fleet, String shipId, double seconds) {
        return used(fleet.location().localFlight(), shipId, seconds / 3600);
    }

    public static Fleet advance(Fleet fleet, double hours, boolean powerTick) {
        var flight = fleet.location().localFlight();
        if (flight == null) return fleet;
        if (flight.interrupted()) return drift(fleet, hours, powerTick);
        double supplied = availableHours(fleet, hours);
        boolean stationApproach = StationRendezvous.approach(fleet);
        if (stationApproach) supplied = Math.min(supplied,
                Math.max(0, flight.motion().trajectory().totalSeconds() - flight.motion().elapsedSeconds()) / 3600);
        var motion = flight.motion().trajectory().at(flight.motion().elapsedSeconds() + supplied * 3600);
        var vector = flight.vectorFlight() == null ? null : flight.vectorFlight().advance(supplied * 3600, false);
        if (vector != null) motion = VectorFlight.motion(vector, flight.geometry());
        double fundedHours = supplied;
        var ships = fleet.ships().stream().map(ship -> new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(),
                ship.currentHullHealth(), ship.currentShieldHealth(),
                Math.max(0, ship.currentFuelKg() - used(flight, ship.id(), fundedHours)), ship.storedCargoKg(),
                ship.passengerCount(), ship.passengerRaceId(), ship.transitMode(), ship.powerState(), ship.supplyState(), ship.moduleDamage())).toList();
        Fleet moved = fleet.withShips(ships);
        if (vector == null && flight.rendezvousTargetId() != null && motion.elapsedSeconds() + 1e-6 >= motion.trajectory().totalSeconds()) {
            var end = motion.trajectory().at(motion.trajectory().totalSeconds());
            double priorDrift = flight.motion().elapsedSeconds() >= motion.trajectory().totalSeconds()
                    ? Math.max(0, flight.motion().positionMeters() - end.positionMeters()) : 0;
            double driftSeconds = Math.max(0, flight.motion().elapsedSeconds() + supplied * 3600 - motion.trajectory().totalSeconds());
            motion = new FlightMotion(end.positionMeters() + priorDrift + end.velocityMps() * driftSeconds,
                    end.velocityMps(), end.trajectory(), end.elapsedSeconds());
        }
        boolean completed = motion.elapsedSeconds() + 1e-6 >= motion.trajectory().totalSeconds();
        boolean gravityFlight = vector != null && vector.trajectory().gravity() != null;
        if (stationApproach && completed && StationRendezvous.contact(vector, flight.geometry().destination()))
            return moved.withLocation(FleetLocation.at(fleet.location().destination()));
        if (flight.rendezvousTargetId() == null && completed && !gravityFlight)
            return moved.withLocation(FleetLocation.at(fleet.location().destination()));
        moved = set(moved, new LocalFlight(flight.geometry(), motion, flight.propellantKg(), flight.propulsion(),
                supplied + 1e-9 < hours || gravityFlight && completed && flight.rendezvousTargetId() == null,
                powerTick, flight.rendezvousTargetId(), vector));
        return supplied + 1e-9 < hours ? drift(moved, hours - supplied, powerTick) : moved;
    }

    public static Fleet interrupt(Fleet fleet, double poweredHours) {
        Fleet moved = advance(fleet, poweredHours, true);
        if (moved.location().localFlight() == null) return moved;
        var flight = moved.location().localFlight();
        moved = set(moved, new LocalFlight(flight.geometry(), flight.motion(), flight.propellantKg(), flight.propulsion(), true, true, flight.rendezvousTargetId(), flight.vectorFlight()));
        return drift(moved, 24 - poweredHours, true);
    }

    private static Fleet drift(Fleet fleet, double hours, boolean powerTick) {
        var flight = fleet.location().localFlight();
        var prior = flight.motion();
        var motion = new FlightMotion(prior.positionMeters() + prior.velocityMps() * hours * 3600,
                prior.velocityMps(), prior.trajectory(), prior.elapsedSeconds());
        var vector = flight.vectorFlight() == null ? null : flight.vectorFlight().advance(hours * 3600, true);
        if (vector != null) motion = VectorFlight.motion(vector, flight.geometry());
        return set(fleet, new LocalFlight(flight.geometry(), motion, flight.propellantKg(), flight.propulsion(), true, powerTick, flight.rendezvousTargetId(), vector));
    }

    private static Fleet set(Fleet fleet, LocalFlight flight) {
        var location = fleet.location();
        return fleet.withLocation(new FleetLocation(location.current(), location.destination(),
                Math.clamp(flight.motion().positionMeters() / flight.geometry().distanceMeters(), 0, 1),
                location.travelDays(), flight));
    }

    public static Fleet movementTick(Fleet fleet) {
        var flight = fleet.location().localFlight();
        if (!flight.advancedInPowerTick()) return advance(fleet, 24, false);
        return fleet.withLocation(fleet.location().withFlight(new LocalFlight(flight.geometry(), flight.motion(),
                flight.propellantKg(), flight.propulsion(), flight.interrupted(), false, flight.rendezvousTargetId(), flight.vectorFlight())));
    }
}
