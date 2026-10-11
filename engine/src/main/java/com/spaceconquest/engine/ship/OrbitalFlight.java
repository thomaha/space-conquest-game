package com.spaceconquest.engine.ship;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Frozen orbital solution and executed event cursor. Main and reactor fuel remain physical ship stores. */
public record OrbitalFlight(Itinerary itinerary, double elapsedSeconds, int nextManeuver, Status status, String message) {
    public enum Status { WAITING, COAST, CAPTURED, WAITING_FAILED, MISSED, APPROACH_FAILED }
    public enum CenterKind { STELLAR, PLANETARY, LUNAR }
    public record Frame(String centerBodyId, CenterKind kind) {
        public Frame {
            if (centerBodyId == null || kind == null || kind != CenterKind.STELLAR && centerBodyId.isBlank())
                throw new IllegalArgumentException("Invalid orbital gravity frame");
        }
    }
    public record Burn(double deltaVMps, double exhaustVelocityMps, double propellantKg, double reserveKg,
                       String reactorMaterialId, double reactorKgPerPropellantKg) {
        public Burn {
            if (!Double.isFinite(deltaVMps) || deltaVMps <= 0 || !Double.isFinite(exhaustVelocityMps) || exhaustVelocityMps <= 0
                    || !Double.isFinite(propellantKg) || propellantKg <= 0 || !Double.isFinite(reserveKg) || reserveKg < 0
                    || !Double.isFinite(reactorKgPerPropellantKg) || reactorKgPerPropellantKg < 0)
                throw new IllegalArgumentException("Invalid orbital burn");
        }
    }
    public record Maneuver(double seconds, double auxiliaryHours, Map<String, Burn> burns) {
        public Maneuver {
            if (!Double.isFinite(seconds) || seconds < 0 || !Double.isFinite(auxiliaryHours) || auxiliaryHours <= 0)
                throw new IllegalArgumentException("Invalid maneuver timing");
            burns = Map.copyOf(burns);
        }
    }
    public record Itinerary(String sourceBodyId, String targetBodyId, double sourceAltitudeKm, double targetAltitudeKm,
                            double epochDays, double waitSeconds, HohmannCoast coast, ShipSolarEnvironment environment,
                            List<Maneuver> maneuvers, Frame frame, VectorFlight sourceMotion, OrbitalDocking.Approach docking) {
        public Itinerary(String sourceBodyId, String targetBodyId, double sourceAltitudeKm, double targetAltitudeKm,
                         double epochDays, double waitSeconds, HohmannCoast coast, ShipSolarEnvironment environment,
                         List<Maneuver> maneuvers, Frame frame, VectorFlight sourceMotion) {
            this(sourceBodyId, targetBodyId, sourceAltitudeKm, targetAltitudeKm, epochDays, waitSeconds, coast, environment,
                    maneuvers, frame, sourceMotion, null);
        }
        public Itinerary(String sourceBodyId, String targetBodyId, double sourceAltitudeKm, double targetAltitudeKm,
                         double epochDays, double waitSeconds, HohmannCoast coast, ShipSolarEnvironment environment,
                         List<Maneuver> maneuvers, Frame frame) {
            this(sourceBodyId, targetBodyId, sourceAltitudeKm, targetAltitudeKm, epochDays, waitSeconds, coast, environment, maneuvers, frame, null);
        }
        public Itinerary(String sourceBodyId, String targetBodyId, double sourceAltitudeKm, double targetAltitudeKm,
                         double epochDays, double waitSeconds, HohmannCoast coast, ShipSolarEnvironment environment,
                         List<Maneuver> maneuvers) {
            this(sourceBodyId, targetBodyId, sourceAltitudeKm, targetAltitudeKm, epochDays, waitSeconds, coast, environment, maneuvers, null);
        }
        public Itinerary {
            if (sourceBodyId == null || targetBodyId == null
                    || !Double.isFinite(epochDays) || epochDays < 0 || !Double.isFinite(waitSeconds) || waitSeconds < 0
                    || coast == null || environment == null) throw new IllegalArgumentException("Invalid orbital itinerary");
            CircularOrbitalEphemeris.positive(sourceAltitudeKm); CircularOrbitalEphemeris.positive(targetAltitudeKm);
            if (frame == null) frame = sourceBodyId.equals(targetBodyId)
                    ? new Frame(sourceBodyId, CenterKind.PLANETARY) : new Frame("", CenterKind.STELLAR);
            if (sourceMotion != null && (sourceMotion.trajectory().gravity() == null
                    || !sourceMotion.trajectory().gravity().frame().equals(frame) || !sourceBodyId.equals(targetBodyId)))
                throw new IllegalArgumentException("Saved parking source motion requires the same body frame");
            maneuvers = List.copyOf(maneuvers);
            var events = maneuvers;
            boolean approachOnly = sourceBodyId.equals(targetBodyId) && sourceAltitudeKm == targetAltitudeKm
                    && coast.departureRadiusMeters() == coast.arrivalRadiusMeters();
            if (maneuvers.size() != (approachOnly ? 1 : 3) || maneuvers.getFirst().seconds() != waitSeconds
                    || !approachOnly && (maneuvers.get(1).seconds() <= waitSeconds || maneuvers.getLast().seconds() <= maneuvers.get(1).seconds())
                    || maneuvers.stream().anyMatch(event -> !event.burns().keySet().equals(events.getFirst().burns().keySet())))
                throw new IllegalArgumentException("Orbital maneuvers require complete ordered budgets");
            if (docking != null && (maneuvers.size() != 3 || docking.departureSeconds() < maneuvers.get(1).seconds()
                    || docking.departureSeconds() % 86400 != 0
                    || !docking.physical().vectorTrajectory().gravity().frame().centerBodyId().equals(targetBodyId)
                    || !docking.physical().propellantKg().keySet().equals(maneuvers.getFirst().burns().keySet())))
                throw new IllegalArgumentException("Saved docking must follow capture in the destination body frame with complete fleet budgets");
        }
        public double totalSeconds() { return docking == null ? maneuvers.getLast().seconds()
                : docking.departureSeconds() + docking.physical().trajectory().totalSeconds(); }
        public Itinerary withDocking(OrbitalDocking.Approach approach) {
            return new Itinerary(sourceBodyId, targetBodyId, sourceAltitudeKm, targetAltitudeKm, epochDays,
                    waitSeconds, coast, environment, maneuvers, frame, sourceMotion, approach);
        }
        public Itinerary withSourceMotion(VectorFlight actual) {
            return new Itinerary(sourceBodyId, targetBodyId, sourceAltitudeKm, targetAltitudeKm, epochDays,
                    waitSeconds, coast, environment, maneuvers, frame, actual, docking);
        }
        public boolean parkingTransfer() { return sourceBodyId.equals(targetBodyId); }
        public boolean bodyCentered() { return frame.kind() != CenterKind.STELLAR; }
        public boolean lunarTransfer() { return bodyCentered() && !parkingTransfer(); }
        public double coastDurationSeconds() { return maneuvers.size() == 1 ? 0 : coast.coastSeconds(); }
        public List<String> maneuverNames() {
            String arrival = docking == null ? "Arrival maneuver" : "Station rendezvous";
            if (lunarTransfer()) return frame.centerBodyId().equals(sourceBodyId)
                    ? List.of("Parent departure", "Lunar capture", arrival)
                    : List.of("Lunar escape", "Parent circularization", arrival);
            return maneuvers.size() == 1 ? List.of("Docking or undocking approach") : parkingTransfer()
                    ? List.of("Departure burn", "Circularization", arrival) : List.of("Escape", "Capture", arrival);
        }
        public Itinerary retain(List<ShipInstance> ships) {
            var ids = ships.stream().map(ShipInstance::id).toList();
            return new Itinerary(sourceBodyId, targetBodyId, sourceAltitudeKm, targetAltitudeKm, epochDays,
                    waitSeconds, coast, environment, maneuvers.stream().map(event -> {
                Map<String, Burn> burns = new HashMap<>();
                event.burns().forEach((id, burn) -> { if (ids.contains(id)) burns.put(id, burn); });
                return new Maneuver(event.seconds(), event.auxiliaryHours(), burns);
            }).toList(), frame, sourceMotion, docking == null ? null : docking.retain(ships));
        }
    }
    public OrbitalFlight {
        if (itinerary == null || status == null || !Double.isFinite(elapsedSeconds) || elapsedSeconds < 0
                || nextManeuver < 0 || nextManeuver > itinerary.maneuvers().size()) throw new IllegalArgumentException("Invalid orbital flight state");
        message = message == null ? "" : message;
    }
    public boolean atSource() { return nextManeuver == 0; }
    public boolean failed() { return status == Status.WAITING_FAILED || status == Status.MISSED || status == Status.APPROACH_FAILED; }
    public CircularOrbitalEphemeris.State position() {
        if (atSource() && itinerary.sourceMotion() != null) {
            var actual = itinerary.sourceMotion();
            return new CircularOrbitalEphemeris.State(actual.position().xMeters(), actual.position().yMeters(),
                    actual.velocity().xMeters(), actual.velocity().yMeters());
        }
        if (itinerary.maneuvers().size() == 1) return itinerary.coast().at(elapsedSeconds);
        if (!atSource() && nextManeuver < 2) return itinerary.coast().at(Math.max(0, elapsedSeconds - itinerary.waitSeconds()));
        boolean source = atSource();
        double radius = source ? itinerary.coast().departureRadiusMeters() : itinerary.coast().arrivalRadiusMeters();
        double rate = Math.sqrt(itinerary.coast().gravitationalParameter() / radius) / radius;
        double phase = itinerary.coast().departureAngleRadians() + (source ? 0 : Math.PI)
                + rate * (elapsedSeconds - itinerary.waitSeconds() - (source ? 0 : itinerary.coast().coastSeconds()));
        double speed = rate * radius;
        return new CircularOrbitalEphemeris.State(radius * Math.cos(phase), radius * Math.sin(phase),
                -speed * Math.sin(phase), speed * Math.cos(phase));
    }
    public OrbitalFlight retain(List<ShipInstance> ships) {
        return new OrbitalFlight(itinerary.retain(ships), elapsedSeconds, nextManeuver, status, message);
    }
    public boolean together(OrbitalFlight other) {
        return other != null && elapsedSeconds == other.elapsedSeconds && nextManeuver == other.nextManeuver
                && status == other.status && itinerary.retain(List.of()).equals(other.itinerary.retain(List.of()));
    }
    public OrbitalFlight join(OrbitalFlight other) {
        if (!together(other)) throw new IllegalArgumentException("Orbital itineraries differ");
        var events = new java.util.ArrayList<Maneuver>();
        for (int index = 0; index < itinerary.maneuvers().size(); index++) {
            var event = itinerary.maneuvers().get(index);
            var burns = new HashMap<>(event.burns()); burns.putAll(other.itinerary.maneuvers().get(index).burns());
            events.add(new Maneuver(event.seconds(), event.auxiliaryHours(), burns));
        }
        var merged = new Itinerary(itinerary.sourceBodyId(), itinerary.targetBodyId(), itinerary.sourceAltitudeKm(),
                itinerary.targetAltitudeKm(), itinerary.epochDays(), itinerary.waitSeconds(), itinerary.coast(), itinerary.environment(), events, itinerary.frame(), itinerary.sourceMotion(),
                itinerary.docking() == null ? null : itinerary.docking().join(other.itinerary().docking()));
        return new OrbitalFlight(merged, elapsedSeconds, nextManeuver, status, message);
    }
}
