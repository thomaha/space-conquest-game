package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import java.util.*;

/** Funds only the convoy's unexecuted burns using the escort's own actual equipment and stores. */
public final class EscortFlightContinuation {
    private EscortFlightContinuation() {}
    public static Fleet align(GameState state, Fleet escort, Fleet convoy) {
        if (!escort.ownerEntityId().equals(convoy.ownerEntityId()) || !escort.currentSystemId().equals(convoy.currentSystemId())) return null;
        if (!convoy.location().inTransit()) return parked(state, escort, convoy);
        var local = convoy.location().localFlight(); var orbital = convoy.location().orbitalFlight();
        if (local != null) return local(state, escort, convoy);
        // A saved docking approach has its own subsequent trajectory; do not invent compatible budgets for it.
        if (orbital == null || orbital.itinerary().docking() != null || orbital.failed()) return null;
        var itinerary = orbital.itinerary(); var ships = new ArrayList<ShipInstance>(escort.ships());
        var events = new ArrayList<OrbitalFlight.Maneuver>();
        for (int index = 0; index < itinerary.maneuvers().size(); index++) {
            var event = itinerary.maneuvers().get(index); var sample = event.burns().values().stream().findFirst().orElse(null);
            if (sample == null) return null;
            var burns = new HashMap<String, OrbitalFlight.Burn>();
            for (var ship : ships) {
                var capability = LocalSpaceTravel.capability(state, escort, ship, convoy.location().destination(), escort.fuelPolicy().reserveKg(state, escort, ship));
                if (capability == null) return null;
                double kg = capability.massKg() * -Math.expm1(-sample.deltaVMps() / capability.exhaustMps());
                var feed = capability.reactor();
                if (index >= orbital.nextManeuver()) {
                    if (capability.deltaVMps() + 1e-6 < sample.deltaVMps()) return null;
                    var design = FleetSupplySimulation.design(state, ship);
                    double seconds = sample.deltaVMps() / capability.accelerationMps2();
                    if (seconds > event.auxiliaryHours() * 3600 || design == null) return null;
                }
                burns.put(ship.id(), new OrbitalFlight.Burn(sample.deltaVMps(), capability.exhaustMps(), kg,
                        escort.fuelPolicy().reserveKg(state, escort, ship), feed == null ? null : feed.materialId(),
                        feed == null ? 0 : feed.kgPerPropellantKg()));
            }
            events.add(new OrbitalFlight.Maneuver(event.seconds(), event.auxiliaryHours(), burns));
            // Later event quotes use post-burn mass. These are immutable forecasts, not live consumption.
            if (index >= orbital.nextManeuver()) ships = new ArrayList<>(ships.stream().map(ship -> {
                var burn = burns.get(ship.id()); var cargo = new HashMap<>(ship.storedCargoKg());
                if (burn.reactorMaterialId() != null) cargo.merge(burn.reactorMaterialId(), -burn.propellantKg() * burn.reactorKgPerPropellantKg(), Double::sum);
                return new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), ship.currentHullHealth(), ship.currentShieldHealth(),
                        ship.currentFuelKg() - burn.propellantKg(), cargo, ship.passengerCount(), ship.passengerRaceId(), ship.transitMode(), ship.powerState(), ship.supplyState(), ship.moduleDamage());
            }).toList());
        }
        var own = new OrbitalFlight.Itinerary(itinerary.sourceBodyId(), itinerary.targetBodyId(), itinerary.sourceAltitudeKm(), itinerary.targetAltitudeKm(),
                itinerary.epochDays(), itinerary.waitSeconds(), itinerary.coast(), itinerary.environment(), events, itinerary.frame(), itinerary.sourceMotion());
        var aligned = escort.withLocation(convoy.location().withOrbitalFlight(new OrbitalFlight(own, orbital.elapsedSeconds(), orbital.nextManeuver(), orbital.status(), orbital.message())));
        return LocalSpacePowerForecast.remainingReady(state, aligned, Math.max(0, own.totalSeconds() - orbital.elapsedSeconds())) ? aligned : null;
    }
    private static Fleet parked(GameState state, Fleet escort, Fleet convoy) {
        if (convoy.location().current().kind() != FleetLocation.Kind.ORBIT || !BodyFleetRendezvous.matches(state, escort, convoy)) return null;
        for (var ship : escort.ships()) {
            var design = FleetSupplySimulation.design(state, ship);
            if (design == null) return null;
            var profile = design.powerProfile(ship);
            if (profile != null && !ShipArrivalReserve.check(profile, ShipPowerProcessor.reserves(ship), profile.essentialKw(ship, design),
                    ShipPowerProcessor.cargoKw(profile, ship, design), ShipSolarEnvironment.journey(state, escort, convoy.location().current())).ready()) return null;
        }
        return escort.withLocation(convoy.location());
    }
    private static Fleet local(GameState state, Fleet escort, Fleet convoy) {
        var flight = convoy.location().localFlight(); var trajectory = flight.motion().trajectory();
        double elapsed = flight.motion().elapsedSeconds();
        double totalImpulse = impulse(flight, trajectory.totalSeconds());
        double pastImpulse = impulse(flight, elapsed);
        double remaining = flight.interrupted() ? 0 : Math.max(0, totalImpulse - pastImpulse);
        var budgets = new HashMap<String, Double>(); var drives = new HashMap<String, JourneyPropulsion>();
        var ships = new ArrayList<ShipInstance>();
        for (var ship : escort.ships()) {
            var capability = LocalSpaceTravel.capability(state, escort, ship, convoy.location().destination(), escort.fuelPolicy().reserveKg(state, escort, ship));
            if (capability == null || remaining > capability.deltaVMps() + 1e-6
                    || remaining > 0 && trajectory.accelerationMps2() > capability.accelerationMps2() + 1e-9) return null;
            double kg = capability.massKg() * -Math.expm1(-remaining / capability.exhaustMps());
            var feed = capability.reactor(); var cargo = new HashMap<>(ship.storedCargoKg());
            double feedKg = feed == null ? 0 : kg * feed.kgPerPropellantKg();
            if (feed != null) {
                if (cargo.getOrDefault(feed.materialId(), 0.0) + 1e-9 < feedKg) return null;
                cargo.merge(feed.materialId(), -feedKg, Double::sum);
            }
            double fraction = totalImpulse <= 0 ? 1 : (-Math.expm1(-totalImpulse / capability.exhaustMps())
                    + Math.expm1(-pastImpulse / capability.exhaustMps())) / -Math.expm1(-totalImpulse / capability.exhaustMps());
            budgets.put(ship.id(), remaining == 0 ? 0 : kg / fraction);
            drives.put(ship.id(), JourneyPropulsion.capture(ship, capability.drive(), feed, kg).withProtectedPropellant(escort.fuelPolicy().reserveKg(state, escort, ship)));
            ships.add(new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), ship.currentHullHealth(), ship.currentShieldHealth(), ship.currentFuelKg(),
                    cargo, ship.passengerCount(), ship.passengerRaceId(), ship.transitMode(), ship.powerState(), ship.supplyState(), ship.moduleDamage()));
        }
        var own = new LocalFlight(flight.geometry(), flight.motion(), budgets, drives, flight.interrupted(), flight.advancedInPowerTick(), flight.rendezvousTargetId(), flight.vectorFlight());
        var aligned = escort.withShips(ships).withLocation(convoy.location().withFlight(own));
        if (flight.interrupted()) {
            for (var ship : aligned.ships()) {
                var design = FleetSupplySimulation.design(state, ship); var profile = design.powerProfile(ship);
                if (profile != null && !ShipArrivalReserve.check(profile, ShipPowerProcessor.reserves(ship), profile.essentialKw(ship, design),
                        ShipPowerProcessor.cargoKw(profile, ship, design), ShipSolarEnvironment.journey(state, aligned, convoy.location().destination())).ready()) return null;
            }
            return aligned;
        }
        double seconds = Math.max(0, trajectory.totalSeconds() - elapsed);
        return LocalSpacePowerForecast.remainingReady(state, aligned, seconds) ? aligned : null;
    }
    private static double impulse(LocalFlight flight, double seconds) {
        return flight.vectorFlight() == null ? flight.motion().trajectory().impulse(seconds) : flight.vectorFlight().trajectory().impulse(seconds);
    }
    static Fleet projected(Fleet convoy, double seconds) {
        if (!convoy.location().inTransit()) return convoy;
        var local = convoy.location().localFlight();
        if (local != null) return LocalFlightProcessor.advance(convoy, seconds / 3600, false);
        var orbital = convoy.location().orbitalFlight();
        if (orbital == null || orbital.elapsedSeconds() + seconds >= orbital.itinerary().maneuvers().get(orbital.nextManeuver()).seconds()) return null;
        return convoy.withLocation(convoy.location().withOrbitalFlight(new OrbitalFlight(orbital.itinerary(), orbital.elapsedSeconds() + seconds,
                orbital.nextManeuver(), orbital.status(), orbital.message())));
    }
}
