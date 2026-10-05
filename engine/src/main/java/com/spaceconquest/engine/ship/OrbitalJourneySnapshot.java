package com.spaceconquest.engine.ship;

import java.util.ArrayList;
import java.util.List;

/** Immutable presentation data derived exclusively from a saved orbital solution. */
public record OrbitalJourneySnapshot(
        String sourceBodyId, String targetBodyId, double sourceAltitudeKm, double targetAltitudeKm,
        double sourceRadiusMeters, double targetRadiusMeters,
        CircularOrbitalEphemeris.State source, CircularOrbitalEphemeris.State target,
        CircularOrbitalEphemeris.State ship, List<Point> transferArc, List<Point> ballisticOrbit,
        List<Maneuver> maneuvers, OrbitalFlight.Status status, String message, OrbitalFlight.Frame frame
) {
    public record Point(double xMeters, double yMeters) {}
    public record Maneuver(String name, double daysUntil, boolean completed, boolean abandoned) {}

    public OrbitalJourneySnapshot {
        transferArc = List.copyOf(transferArc);
        ballisticOrbit = List.copyOf(ballisticOrbit);
        maneuvers = List.copyOf(maneuvers);
    }

    /** Bounded sampling belongs on the preview worker, never the JavaFX application thread. */
    public static OrbitalJourneySnapshot from(OrbitalFlight flight) {
        var itinerary = flight.itinerary();
        var coast = itinerary.coast();
        var orbit = new ArrayList<Point>();
        for (int index = 0; index <= 128; index++) {
            var point = coast.at(coast.coastSeconds() * index / 64);
            orbit.add(new Point(point.xMeters(), point.yMeters()));
        }
        var events = new ArrayList<Maneuver>();
        var names = itinerary.maneuverNames();
        for (int index = 0; index < itinerary.maneuvers().size(); index++)
            events.add(new Maneuver(names.get(index),
                    (itinerary.maneuvers().get(index).seconds() - flight.elapsedSeconds()) / 86400,
                    index < flight.nextManeuver(), flight.failed() && index >= flight.nextManeuver()));
        var source = circular(coast, coast.departureRadiusMeters(), coast.departureAngleRadians(),
                flight.elapsedSeconds() - itinerary.waitSeconds());
        var target = circular(coast, coast.arrivalRadiusMeters(), coast.departureAngleRadians() + Math.PI,
                flight.elapsedSeconds() - itinerary.waitSeconds() - coast.coastSeconds());
        if (itinerary.maneuvers().size() == 1) source = target = flight.position();
        return new OrbitalJourneySnapshot(itinerary.sourceBodyId(), itinerary.targetBodyId(),
                itinerary.sourceAltitudeKm(), itinerary.targetAltitudeKm(), coast.departureRadiusMeters(), coast.arrivalRadiusMeters(),
                source, target, flight.position(), itinerary.maneuvers().size() == 1
                ? List.of(new Point(flight.position().xMeters(), flight.position().yMeters())) : orbit.subList(0, 65),
                orbit, events, flight.status(), flight.message(), itinerary.frame());
    }

    public boolean parkingTransfer() { return sourceBodyId.equals(targetBodyId); }
    public boolean bodyCentered() { return frame.kind() != OrbitalFlight.CenterKind.STELLAR; }

    private static CircularOrbitalEphemeris.State circular(HohmannCoast coast, double radius, double referenceAngle, double seconds) {
        double rate = Math.sqrt(coast.gravitationalParameter() / radius) / radius;
        double angle = CircularOrbitalEphemeris.angle(referenceAngle + rate * seconds);
        double speed = rate * radius;
        return new CircularOrbitalEphemeris.State(radius * Math.cos(angle), radius * Math.sin(angle),
                -speed * Math.sin(angle), speed * Math.cos(angle));
    }
}
