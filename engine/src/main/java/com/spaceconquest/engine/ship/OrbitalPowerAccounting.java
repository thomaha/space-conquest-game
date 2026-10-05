package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;

/** Shared conservative services and atomic dark auxiliary bursts for the impulse prototype. */
final class OrbitalPowerAccounting {
    record Projection(ShipInstance ship, boolean ready, double journeyKwh) {}
    private OrbitalPowerAccounting() {}

    static ShipPowerState startDay(ShipPowerState prior) {
        return new ShipPowerState(prior.generatorMaterialsKg(), prior.chemicalMixture(), prior.reactorFuel(),
                prior.batteryChargeKwh(), prior.arraysDeployed(), prior.arrayCondition(), prior.orientationFraction(),
                prior.unmetEssentialHours(), 0, 0, 0, 0, prior.cargoPreservation(), prior.rescueStatus());
    }

    static ShipPowerProcessor.Interval burst(ShipDesign design, ShipInstance ship, double hours) {
        var profile = design.powerProfile();
        if (profile == null) return null;
        var peak = ShipPowerProcessor.interval(profile, ShipPowerProcessor.reserves(ship), hours, 0,
                profile.essentialKw(ship, design), ShipPowerProcessor.cargoKw(profile, ship, design), profile.driveKw());
        if (!peak.supplied()) return peak;
        // No extra sunlight or charging time is granted by the impulse approximation.
        return ShipPowerProcessor.interval(profile, ShipPowerProcessor.reserves(ship), hours, 0, 0, 0, profile.driveKw());
    }

    static Fleet services(GameState state, Fleet fleet, double startSeconds, double endSeconds, ShipSolarEnvironment environment) {
        return fleet.withShips(fleet.ships().stream().map(ship -> {
            var design = FleetSupplySimulation.design(state, ship);
            if (design == null || design.powerProfile() == null) return ship;
            var current = ShipPowerProcessor.reserves(ship);
            double hour = startSeconds / 3600, end = endSeconds / 3600;
            double cycle = environment.lightHours() + environment.darkHours();
            while (hour < end - 1e-10) {
                double position = hour % cycle;
                boolean light = position < environment.lightHours();
                double duration = Math.min(end - hour, (light ? environment.lightHours() : cycle) - position);
                if (duration <= 1e-12) break;
                var step = ShipPowerProcessor.interval(design.powerProfile(), current, duration,
                        light ? ShipPowerProcessor.solarKw(design.powerProfile(), current, environment) : 0,
                        design.powerProfile().essentialKw(ship, design), ShipPowerProcessor.cargoKw(design.powerProfile(), ship, design), 0);
                current = step.state(); hour += duration;
            }
            return ship.withPowerState(current);
        }).toList());
    }

    static LocalSpacePowerForecast.Budget forecast(ShipInstance ship, ShipDesign design, OrbitalFlight.Itinerary itinerary) {
        var projection = projection(ship, design, itinerary, 0, 0);
        return new LocalSpacePowerForecast.Budget(projection.ship().powerState(), projection.ready(), projection.journeyKwh());
    }

    static Projection projection(ShipInstance ship, ShipDesign design, OrbitalFlight.Itinerary itinerary,
                                                          double startSeconds, int firstManeuver) {
        var current = ShipPowerProcessor.reserves(ship);
        var profile = design.powerProfile();
        if (profile == null) return new Projection(ship.withPowerState(current), false, 0);
        var actual = ship;
        double elapsed = startSeconds / 3600, energy = 0;
        boolean ready = true;
        for (var event : itinerary.maneuvers().subList(firstManeuver, itinerary.maneuvers().size())) {
            double essential = profile.essentialKw(actual, design), cargo = ShipPowerProcessor.cargoKw(profile, actual, design);
            double hours = event.seconds() / 3600 - elapsed;
            var services = ShipLocalPowerForecast.check(profile, current, hours, itinerary.environment(), essential, cargo, 0, elapsed);
            current = services.state(); ready &= services.ready(); energy += (essential + cargo) * hours;
            var burst = burst(design, actual.withPowerState(current), event.auxiliaryHours());
            current = burst.state(); ready &= burst.supplied(); energy += profile.driveKw() * event.auxiliaryHours();
            var paid = OrbitalFuelAccounting.pay(actual, design, event.burns().get(ship.id()), current);
            if (paid == null) ready = false; else actual = paid;
            elapsed = event.seconds() / 3600;
        }
        double remainder = Math.ceil(itinerary.totalSeconds() / 86400) * 24 - elapsed;
        double essential = profile.essentialKw(actual, design), cargo = ShipPowerProcessor.cargoKw(profile, actual, design);
        var last = ShipLocalPowerForecast.check(profile, current, remainder, itinerary.environment(), essential, cargo, 0, elapsed);
        return new Projection(actual.withPowerState(last.state()), ready && last.ready(), energy + (essential + cargo) * remainder);
    }

    static boolean canDepart(GameState state, Fleet fleet, OrbitalFlight.Itinerary itinerary) {
        for (var ship : fleet.ships()) {
            var design = FleetSupplySimulation.design(state, ship);
            if (design == null || design.powerProfile() == null) return false;
            double required = itinerary.maneuvers().stream().mapToDouble(event -> event.burns().get(ship.id()).propellantKg()).sum();
            if (ship.currentFuelKg() + 1e-6 < required + itinerary.maneuvers().getFirst().burns().get(ship.id()).reserveKg()) return false;
            var budget = projection(ship, design, itinerary, itinerary.waitSeconds(), 0);
            if (!budget.ready() || !ShipArrivalReserve.check(design.powerProfile(), budget.ship().powerState(),
                    design.powerProfile().essentialKw(ship, design), ShipPowerProcessor.cargoKw(design.powerProfile(), ship, design),
                    itinerary.environment()).ready()) return false;
        }
        return true;
    }
}
