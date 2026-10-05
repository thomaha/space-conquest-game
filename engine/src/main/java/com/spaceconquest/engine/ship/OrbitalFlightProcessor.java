package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import java.util.ArrayList;
import java.util.HashMap;

/** Tick-owned orbital events. Failed maneuvers never award capture or docking. */
public final class OrbitalFlightProcessor {
    private OrbitalFlightProcessor() {}

    public static Fleet advanceDay(GameState state, Fleet fleet) {
        var flight = fleet.location().orbitalFlight();
        var itinerary = flight.itinerary();
        Fleet current = fleet.withShips(fleet.ships().stream()
                .map(ship -> ship.withPowerState(OrbitalPowerAccounting.startDay(ShipPowerProcessor.reserves(ship)))).toList());
        double start = flight.elapsedSeconds(), end = start + 86400, cursor = start;
        int next = flight.nextManeuver();
        var status = flight.status();
        String message = flight.message();
        if (!flight.failed()) {
            while (next < itinerary.maneuvers().size() && itinerary.maneuvers().get(next).seconds() <= end + 1e-8) {
                var event = itinerary.maneuvers().get(next);
                current = OrbitalPowerAccounting.services(state, current, cursor, event.seconds(), itinerary.environment());
                cursor = event.seconds();
                var executed = next == 0 && !OrbitalPowerAccounting.canDepart(state, current, itinerary) ? null : burn(state, current, event);
                if (executed == null) {
                    status = next == 0 ? OrbitalFlight.Status.WAITING_FAILED : next == 1
                            ? OrbitalFlight.Status.MISSED : OrbitalFlight.Status.APPROACH_FAILED;
                    message = "Maneuver rejected: actual fuel, reactor feed or auxiliary power is insufficient. "
                            + (next == 0 ? "Still at departure base." : next == 1
                            ? itinerary.bodyCentered() ? "Capture or circularization was not funded; "
                            + (itinerary.frame().kind() == OrbitalFlight.CenterKind.LUNAR ? "moon-centered" : "planet-centered")
                            + " ballistic orbit continues."
                            : "Missed capture; unpowered star-centered coast continues. Planetary flyby deflection is unsupported."
                            : "Captured parking orbit retained; docking was not funded.");
                    break;
                }
                current = executed; next++;
                status = next == 1 ? OrbitalFlight.Status.COAST : OrbitalFlight.Status.CAPTURED;
                message = next == 1 ? itinerary.parkingTransfer() ? "Funded departure burn completed; coasting"
                        : "Funded escape completed; coasting" : "Funded circularization completed; approaching destination";
            }
        }
        current = OrbitalPowerAccounting.services(state, current, cursor, end, itinerary.environment());
        current = current.withShips(current.ships().stream().map(ship -> {
            var design = FleetSupplySimulation.design(state, ship);
            return design == null ? ship : CargoDeterioration.advanceDay(ship.withPowerState(ship.powerState().withChargeInputToday(0)), design);
        }).toList());
        if (next == itinerary.maneuvers().size()) return current.withLocation(FleetLocation.at(fleet.location().destination()));
        var location = fleet.location();
        return current.withLocation(new FleetLocation(location.current(), location.destination(),
                Math.clamp(end / itinerary.totalSeconds(), 0, 1), location.travelDays(), null,
                new OrbitalFlight(itinerary, end, next, status, message)));
    }

    private static Fleet burn(GameState state, Fleet fleet, OrbitalFlight.Maneuver event) {
        var ships = new ArrayList<ShipInstance>();
        for (var ship : fleet.ships()) {
            var commitment = event.burns().get(ship.id());
            var design = FleetSupplySimulation.design(state, ship);
            if (commitment == null || design == null) return null;
            var burst = OrbitalPowerAccounting.burst(design, ship, event.auxiliaryHours());
            if (burst == null || !burst.supplied()) return null;
            var paid = OrbitalFuelAccounting.pay(ship, design, commitment, burst.state());
            if (paid == null) return null;
            ships.add(paid);
        }
        return fleet.withShips(ships);
    }

    public static Fleet projectedArrival(Fleet fleet, FleetLocation.Site destination, LocalTravel.Plan plan) {
        return fleet.withShips(fleet.ships().stream().map(ship -> {
            var cargo = new HashMap<>(ship.storedCargoKg());
            var feed = plan.reactorFuelKg().get(ship.id());
            if (feed != null) {
                double remaining = Math.max(0, cargo.getOrDefault(feed.materialId(), 0.0) - feed.quantityKg());
                if (remaining <= 1e-6) cargo.remove(feed.materialId()); else cargo.put(feed.materialId(), remaining);
            }
            return new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), ship.currentHullHealth(), ship.currentShieldHealth(),
                    Math.max(0, ship.currentFuelKg() - plan.propellantKg().getOrDefault(ship.id(), 0.0)), cargo,
                    ship.passengerCount(), ship.passengerRaceId(), ship.transitMode(), ship.powerState(), ship.supplyState());
        }).toList()).withLocation(FleetLocation.at(destination));
    }

    public static Fleet cancel(Fleet fleet) {
        var flight = fleet.location().orbitalFlight();
        if (flight == null) return fleet;
        if (flight.atSource()) return fleet.withLocation(FleetLocation.at(fleet.location().current()));
        if (flight.nextManeuver() >= 2) return fleet.withLocation(FleetLocation.at(FleetLocation.Site.orbit(
                flight.itinerary().targetBodyId(), flight.itinerary().targetAltitudeKm())));
        return fleet.withLocation(fleet.location().withOrbitalFlight(new OrbitalFlight(flight.itinerary(), flight.elapsedSeconds(),
                flight.nextManeuver(), OrbitalFlight.Status.MISSED, "Order cancelled; ballistic coast continues. Orbital recovery is unsupported.")));
    }
}
