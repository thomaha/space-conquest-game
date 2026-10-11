package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;

/** Shared conservative services and atomic dark auxiliary bursts for the impulse prototype. */
final class OrbitalPowerAccounting {
    record Projection(ShipInstance ship, boolean ready, double journeyKwh) {}
    private OrbitalPowerAccounting() {}

    static ShipPowerState startDay(ShipPowerState prior) {
        return new ShipPowerState(prior.generatorMaterialsKg(), prior.chemicalMixture(), prior.reactorFuel(),
                prior.batteryChargeKwh(), prior.arraysDeployed(), prior.arrayCondition(), prior.orientationFraction(),
                prior.unmetEssentialHours(), 0, 0, 0, 0, prior.cargoPreservation(), prior.rescueStatus(), java.util.Set.of());
    }

    static ShipPowerProcessor.Interval burst(ShipDesign design, ShipInstance ship, double hours) {
        return burst(design, ship, hours, ShipServicePower.Activity.CRUISE);
    }
    static ShipPowerProcessor.Interval burst(ShipDesign design, ShipInstance ship, double hours, ShipServicePower.Activity activity) {
        var profile = design.powerProfile(ship);
        if (profile == null) return null;
        var peak = ShipPowerProcessor.interval(profile, ShipPowerProcessor.reserves(ship), hours, 0,
                profile.essentialKw(ship, design), ShipPowerProcessor.cargoKw(profile, ship, design), profile.driveKw(), activity);
        if (!peak.supplied()) return peak;
        // No extra sunlight or charging time is granted by the impulse approximation.
        return ShipPowerProcessor.interval(profile, ShipPowerProcessor.reserves(ship), hours, 0, 0, 0, profile.driveKw());
    }

    static Fleet services(GameState state, Fleet fleet, double startSeconds, double endSeconds, ShipSolarEnvironment environment) {
        var activity = ShipServicePower.activity(state, fleet);
        return fleet.withShips(fleet.ships().stream().map(ship -> {
            var design = FleetSupplySimulation.design(state, ship);
            if (design == null || design.powerProfile() == null) return ship;
            var flight = fleet.location().orbitalFlight();
            int next = flight.nextManeuver();
            int limit = flight.itinerary().docking() == null ? flight.itinerary().maneuvers().size() : 2;
            while (next < limit && flight.itinerary().maneuvers().get(next).seconds() < endSeconds - 1e-8) next++;
            return services(ship, design, flight.itinerary(), startSeconds, endSeconds, next,
                    !flight.failed() && !fleet.hasInterstellarOrder() && FleetShieldPolicy.recharge(state, fleet, true), activity).ship();
        }).toList());
    }

    static LocalSpacePowerForecast.Budget forecast(ShipInstance ship, ShipDesign design, OrbitalFlight.Itinerary itinerary, boolean recharge) {
        return forecast(ship, design, itinerary, recharge, ShipServicePower.Activity.CRUISE);
    }
    static LocalSpacePowerForecast.Budget forecast(ShipInstance ship, ShipDesign design, OrbitalFlight.Itinerary itinerary,
                                                  boolean recharge, ShipServicePower.Activity activity) {
        var projection = projection(ship, design, itinerary, 0, 0, recharge, activity);
        return new LocalSpacePowerForecast.Budget(projection.ship().powerState(), projection.ready(), projection.journeyKwh(), projection.ship());
    }

    static Projection projection(ShipInstance ship, ShipDesign design, OrbitalFlight.Itinerary itinerary,
                                                          double startSeconds, int firstManeuver) {
        return projection(ship, design, itinerary, startSeconds, firstManeuver, false);
    }

    static Projection projection(ShipInstance ship, ShipDesign design, OrbitalFlight.Itinerary itinerary,
                                         double startSeconds, int firstManeuver, boolean recharge) {
        return projection(ship, design, itinerary, startSeconds, firstManeuver, recharge, ShipServicePower.Activity.CRUISE);
    }
    static Projection projection(ShipInstance ship, ShipDesign design, OrbitalFlight.Itinerary itinerary,
                                 double startSeconds, int firstManeuver, boolean recharge, ShipServicePower.Activity activity) {
        int limit = itinerary.docking() == null ? itinerary.maneuvers().size() : 2;
        double end = itinerary.docking() == null ? Math.ceil(itinerary.totalSeconds() / 86400) * 86400
                : itinerary.docking().departureSeconds();
        var nativePart = projection(ship, design, itinerary, startSeconds, firstManeuver, limit, end, recharge, activity);
        if (itinerary.docking() == null) return nativePart;
        var physical = itinerary.docking().physical();
        var powered = LocalSpacePowerForecast.physical(nativePart.ship(), design, itinerary.docking().environment(), physical, recharge, activity);
        var actual = powered.projectedShip() == null ? nativePart.ship().withPowerState(powered.state()) : powered.projectedShip();
        var feed = physical.reactorFuelKg().get(ship.id());
        var propulsion = physical.propulsion().get(ship.id());
        boolean funded = propulsion != null && actual.currentFuelKg() + 1e-6 >= physical.propellantKg().getOrDefault(ship.id(), 0.0)
                + propulsion.protectedPropellantKg() && (feed == null
                || actual.storedCargoKg().getOrDefault(feed.materialId(), 0.0) + 1e-6 >= feed.quantityKg());
        var cargo = new java.util.HashMap<>(actual.storedCargoKg());
        if (feed != null) cargo.put(feed.materialId(), Math.max(0, cargo.getOrDefault(feed.materialId(), 0.0) - feed.quantityKg()));
        actual = new ShipInstance(actual.id(), actual.designId(), actual.ownerEntityId(), actual.currentHullHealth(), actual.currentShieldHealth(),
                Math.max(0, actual.currentFuelKg() - physical.propellantKg().getOrDefault(ship.id(), 0.0)), cargo,
                actual.passengerCount(), actual.passengerRaceId(), actual.transitMode(), actual.powerState(), actual.supplyState(), actual.moduleDamage());
        return new Projection(actual, nativePart.ready() && powered.ready() && funded, nativePart.journeyKwh() + powered.journeyKwh());
    }

    static Projection captureProjection(ShipInstance ship, ShipDesign design, OrbitalFlight.Itinerary itinerary, double endSeconds) {
        return projection(ship, design, itinerary, 0, 0, 2, endSeconds, false);
    }

    private static Projection projection(ShipInstance ship, ShipDesign design, OrbitalFlight.Itinerary itinerary,
                                         double startSeconds, int firstManeuver, int limit, double endSeconds, boolean recharge) {
        return projection(ship, design, itinerary, startSeconds, firstManeuver, limit, endSeconds, recharge, ShipServicePower.Activity.CRUISE);
    }
    private static Projection projection(ShipInstance ship, ShipDesign design, OrbitalFlight.Itinerary itinerary,
                                         double startSeconds, int firstManeuver, int limit, double endSeconds, boolean recharge, ShipServicePower.Activity activity) {
        var current = ShipPowerProcessor.reserves(ship);
        var profile = design.powerProfile(ship);
        if (profile == null) return new Projection(ship.withPowerState(current), false, 0);
        var actual = ship;
        double elapsed = startSeconds / 3600, energy = 0;
        boolean ready = true;
        int next = firstManeuver;
        for (var event : itinerary.maneuvers().subList(firstManeuver, limit)) {
            var service = services(actual.withPowerState(current), design, itinerary, elapsed * 3600, event.seconds(), next, recharge, activity);
            actual = service.ship(); current = actual.powerState(); ready &= service.ready(); energy += service.journeyKwh();
            var burst = burst(design, actual.withPowerState(current), event.auxiliaryHours(), activity);
            current = burst.state(); ready &= burst.supplied(); energy += profile.driveKw() * event.auxiliaryHours();
            var paid = OrbitalFuelAccounting.pay(actual, design, event.burns().get(ship.id()), current);
            if (paid == null) ready = false; else actual = paid;
            elapsed = event.seconds() / 3600; next++;
        }
        var last = services(actual.withPowerState(current), design, itinerary, elapsed * 3600, endSeconds, limit, recharge, activity);
        return new Projection(last.ship(), ready && last.ready(), energy + last.journeyKwh());
    }

    private static Projection services(ShipInstance ship, ShipDesign design, OrbitalFlight.Itinerary itinerary,
                                       double start, double end, int next, boolean recharge, ShipServicePower.Activity activity) {
        var p = design.powerProfile(ship); var actual = ship; double energy = 0; boolean ready = true;
        int quotes = 0;
        while (start < end - 1e-8) {
            double until = Math.min(end, (Math.floor(start / 86400) + 1) * 86400);
            double hours = (until - start) / 3600;
            if (recharge && quotes++ < 64 && ShipShieldRecharge.requestedKw(design, actual, hours) > 1e-9) {
                double boundary = until;
                var step = ShipShieldPower.segment(actual, design, new ShipShieldPower.Phase(hours, 0, itinerary.environment(), activity),
                        start / 3600, candidate -> onward(candidate, design, itinerary, boundary, next, activity));
                actual = step.ship(); ready &= step.ready(); energy += step.journeyKwh();
            } else {
                hours = (end - start) / 3600; until = end;
                var step = ShipLocalPowerForecast.check(p, actual.powerState(), hours, itinerary.environment(),
                        p.essentialKw(actual, design), ShipPowerProcessor.cargoKw(p, actual, design), 0, start / 3600, activity);
                energy += hours * (p.essentialKw(actual, design) + ShipPowerProcessor.cargoKw(p, actual, design));
                actual = actual.withPowerState(step.state()); ready &= step.ready();
            }
            start = until;
            if (Math.abs(start / 86400 - Math.rint(start / 86400)) < 1e-9)
                actual = actual.withPowerState(actual.powerState().withChargeInputToday(0));
        }
        return new Projection(actual, ready, energy);
    }

    private static boolean onward(ShipInstance ship, ShipDesign design, OrbitalFlight.Itinerary itinerary, double start, int next, ShipServicePower.Activity activity) {
        int limit = itinerary.docking() == null ? itinerary.maneuvers().size() : 2;
        double end = itinerary.docking() == null ? Math.ceil(itinerary.totalSeconds() / 86400) * 86400
                : itinerary.docking().departureSeconds();
        var tail = projection(ship, design, itinerary, start, next, limit, Math.max(start, end), false, activity);
        var p = design.powerProfile(ship);
        if (itinerary.docking() != null) {
            var physical = LocalSpacePowerForecast.physical(tail.ship(), design, itinerary.docking().environment(), itinerary.docking().physical(), false, activity);
            tail = new Projection(tail.ship().withPowerState(physical.state()), tail.ready() && physical.ready(), 0);
        }
        return tail.ready() && ShipArrivalReserve.check(p, tail.ship().powerState(), p.essentialKw(tail.ship(), design),
                ShipPowerProcessor.cargoKw(p, tail.ship(), design), itinerary.environment(), ShipPowerProcessor.ARRIVAL_RESERVE_HOURS, activity).ready();
    }

    static boolean canDepart(GameState state, Fleet fleet, OrbitalFlight.Itinerary itinerary) {
        var activity = ShipServicePower.activity(state, fleet);
        for (var ship : fleet.ships()) {
            var design = FleetSupplySimulation.design(state, ship);
            if (design == null || design.powerProfile() == null) return false;
            double required = itinerary.maneuvers().subList(0, itinerary.docking() == null ? itinerary.maneuvers().size() : 2)
                    .stream().mapToDouble(event -> event.burns().get(ship.id()).propellantKg()).sum()
                    + (itinerary.docking() == null ? 0 : itinerary.docking().physical().propellantKg().getOrDefault(ship.id(), 0.0));
            if (ship.currentFuelKg() + 1e-6 < required + itinerary.maneuvers().getFirst().burns().get(ship.id()).reserveKg()) return false;
            var budget = projection(ship, design, itinerary, itinerary.waitSeconds(), 0, false, activity);
            if (!budget.ready() || !ShipArrivalReserve.check(design.powerProfile(ship), budget.ship().powerState(),
                    design.powerProfile(ship).essentialKw(ship, design), ShipPowerProcessor.cargoKw(design.powerProfile(ship), ship, design),
                    itinerary.environment(), ShipPowerProcessor.ARRIVAL_RESERVE_HOURS, activity).ready()) return false;
        }
        return true;
    }
}
