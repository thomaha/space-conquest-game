package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** Provisional planet-centered circular parking transfers with separately funded rendezvous. */
public final class ParkingOrbitTravel {
    private ParkingOrbitTravel() {}

    static LocalTravel.Plan plan(GameState state, Fleet fleet, FleetLocation.Site target,
                                 OrbitalTravel.Parking from, OrbitalTravel.Parking to) {
        if (fleet.location().inTransit() || fleet.hasInterstellarOrder() || fleet.ships().isEmpty()
                || FleetSupplySimulation.hasOrders(fleet))
            throw new IllegalArgumentException("Choose a stationary fleet without scheduled supply rendezvous");
        var planet = from.body();
        var system = state.solarSystems().stream().filter(item -> item.id().equals(fleet.currentSystemId())).findFirst().orElseThrow();
        double mu = PlanetaryTransferComparison.GRAVITATIONAL_CONSTANT * planet.mass();
        double r1 = (planet.diameter() / 2 + from.altitudeKm()) * 1000;
        double r2 = (planet.diameter() / 2 + to.altitudeKm()) * 1000;
        double sphere = planet.sphereKm(system) * 1000;
        if (!Double.isFinite(sphere) || r1 >= sphere || r2 >= sphere)
            throw new IllegalArgumentException("Parking orbit is outside the planetary sphere-of-influence approximation");
        CircularOrbitalEphemeris.positive(mu); CircularOrbitalEphemeris.positive(r1); CircularOrbitalEphemeris.positive(r2);
        double period1 = 2 * Math.PI * Math.sqrt(r1 / mu) * r1;
        double period2 = 2 * Math.PI * Math.sqrt(r2 / mu) * r2;
        double phase = Integer.toUnsignedLong((planet.id() + ":parking").hashCode()) / 4294967296.0 * 2 * Math.PI
                + (state.turn() * 86400 % period1) / period1 * 2 * Math.PI;
        var coast = new HohmannCoast(mu, r1, r2, phase);
        boolean approachOnly = r1 == r2;
        if (approachOnly && fleet.location().current().kind() == FleetLocation.Kind.ORBIT && target.kind() == FleetLocation.Kind.ORBIT)
            throw new IllegalArgumentException("Fleet already occupies this parking orbit");
        double dv1 = Math.abs(Math.sqrt(mu * (2 / r1 - 1 / coast.axisMeters())) - Math.sqrt(mu / r1));
        double dv2 = Math.abs(Math.sqrt(mu / r2) - Math.sqrt(mu * (2 / r2 - 1 / coast.axisMeters())));
        var benchmark = new PlanetaryTransferComparison.Orbit(planet.id(), approachOnly ? 0 : coast.coastSeconds() / 86400,
                0, 0, 0, 0, approachOnly ? 10 : dv1, approachOnly ? 0 : dv2, period1, period2);
        double[] impulses = approachOnly ? new double[]{10} : new double[]{dv1, dv2, target.kind() == FleetLocation.Kind.DOCKED ? 10 : .001};
        double[] epochs = approachOnly ? new double[]{3600} : new double[]{0, coast.coastSeconds(), coast.coastSeconds() + 3600};
        var events = new ArrayList<OrbitalFlight.Maneuver>();
        var mass = new HashMap<String, Double>(); var total = new HashMap<String, Double>();
        var reactor = new HashMap<String, InterstellarTravel.ReactorFuelUse>();
        for (int index = 0; index < impulses.length; index++) {
            Map<String, OrbitalFlight.Burn> burns = new HashMap<>();
            double hours = 0;
            for (var ship : fleet.ships()) {
                var design = FleetSupplySimulation.design(state, ship);
                if (design == null || design.powerProfile() == null || !fleet.ownerEntityId().equals(ship.ownerEntityId()))
                    throw new IllegalArgumentException("Every fleet member needs an owned blueprint with electrical equipment");
                var drive = PropulsionCatalog.mainDrive(design.equippedModuleIds());
                if (drive == null || "mod_ion_drive".equals(drive.moduleId()))
                    throw new IllegalArgumentException("Parking transfers require a recognized short-burn drive");
                var budget = PlanetaryTransferComparison.budget(state, fleet, ship, benchmark);
                if (!budget.propellantFits() || !budget.reactorFeedFits() || !budget.impulseFits())
                    throw new IllegalArgumentException(budget.explanation());
                var feed = PropulsionCatalog.availableReactorFuel(drive, ship);
                double exhaust = drive.exhaustVelocityMps() * (feed == null ? 1 : feed.exhaustMultiplier());
                double loaded = mass.getOrDefault(ship.id(), budget.wetMassKg());
                double kg = loaded * -Math.expm1(-impulses[index] / exhaust);
                double feedRate = feed == null ? 0 : feed.kgPerPropellantKg();
                double used = total.getOrDefault(ship.id(), 0.0) + kg;
                if (ship.currentFuelKg() + 1e-6 < used + budget.protectedPropellantKg())
                    throw new IllegalArgumentException("Main fuel cannot fund departure, circularization and approach with protected reserve");
                if (feed != null && used * feedRate > ship.storedCargoKg().getOrDefault(feed.materialId(), 0.0) + 1e-6)
                    throw new IllegalArgumentException("Drive reactor feed cannot fund all parking maneuvers");
                burns.put(ship.id(), new OrbitalFlight.Burn(impulses[index], exhaust, kg, budget.protectedPropellantKg(),
                        feed == null ? null : feed.materialId(), feedRate));
                total.put(ship.id(), used); mass.put(ship.id(), loaded - kg * (1 + feedRate));
                if (feed != null && feedRate > 0) reactor.put(ship.id(), new InterstellarTravel.ReactorFuelUse(feed.materialId(), used * feedRate));
                hours = Math.max(hours, impulses[index] * loaded / design.totalThrustN() / 3600);
            }
            if (!Double.isFinite(hours) || hours <= 0 || hours > 6)
                throw new IllegalArgumentException("Maneuver exceeds the prototype's six-hour short-burn limit");
            events.add(new OrbitalFlight.Maneuver(epochs[index], hours, burns));
        }
        var itinerary = new OrbitalFlight.Itinerary(planet.id(), planet.id(), from.altitudeKm(), to.altitudeKm(), state.turn(),
                epochs[0], coast, ShipSolarEnvironment.journey(state, fleet, target), events, planet.frame());
        return new LocalTravel.Plan(itinerary.totalSeconds() / 86400, total, reactor, null, itinerary);
    }
}
