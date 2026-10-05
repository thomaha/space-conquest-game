package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.SolarSystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** Authoritative opt-in planetary impulse prototype. Unsupported orbital orders never fall back to straight travel. */
public final class OrbitalTravel {
    public record Parking(OrbitalBody body, double altitudeKm, boolean configured) {}
    public record Preview(LocalTravel.Plan plan, String problem) {}
    private OrbitalTravel() {}

    public static Parking parking(GameState state, String systemId, FleetLocation.Site site) {
        if (state == null || site == null || site.kind() != FleetLocation.Kind.ORBIT && site.kind() != FleetLocation.Kind.DOCKED) return null;
        String bodyId = site.entityId();
        double altitude = site.parkingAltitudeKm() == null ? 500 : site.parkingAltitudeKm();
        boolean configured = site.parkingAltitudeKm() != null;
        if (site.kind() == FleetLocation.Kind.DOCKED) {
            var station = state.orbitalStations().stream().filter(item -> item.id().equals(site.entityId())
                    && systemId.equals(item.systemId())).findFirst().orElse(null);
            if (station == null) return null;
            bodyId = station.planetOrbitId(); altitude = station.effectiveParkingAltitudeKm(); configured = station.parkingAltitudeKm() != null;
        }
        var body = OrbitalBody.find(state, systemId, bodyId);
        return body == null ? null : new Parking(body, altitude, configured);
    }

    public static boolean applies(GameState state, Fleet fleet, FleetLocation.Site target) {
        var from = parking(state, fleet.currentSystemId(), fleet.location().current());
        var to = parking(state, fleet.currentSystemId(), target);
        return from != null && to != null
                && (from.configured() || to.configured() || fleet.ships().stream().anyMatch(ship -> {
            var design = FleetSupplySimulation.design(state, ship);
            return design != null && design.equippedModuleIds().contains(ChemicalFreighterCatalog.MAIN_TANK);
        }));
    }

    public static Preview preview(GameState state, Fleet fleet, FleetLocation.Site target) {
        if (state == null || fleet == null || target == null) return new Preview(null, "Choose a known fleet and destination");
        if (fleet.location().current().equals(target)) return new Preview(null, "Fleet is already at this destination");
        try {
            var from = parking(state, fleet.currentSystemId(), fleet.location().current());
            var to = parking(state, fleet.currentSystemId(), target);
            return new Preview(from != null && to != null && from.body().id().equals(to.body().id())
                    ? ParkingOrbitTravel.plan(state, fleet, target, from, to) : build(state, fleet, target), "");
        }
        catch (IllegalArgumentException failure) { return new Preview(null, failure.getMessage()); }
    }

    private static LocalTravel.Plan build(GameState state, Fleet fleet, FleetLocation.Site target) {
        if (state == null || fleet == null || fleet.location().inTransit() || fleet.hasInterstellarOrder() || fleet.ships().isEmpty())
            throw new IllegalArgumentException("Choose a stationary nonempty fleet");
        if (FleetSupplySimulation.hasOrders(fleet)) throw new IllegalArgumentException("Scheduled orbital supply rendezvous are unsupported");
        var from = parking(state, fleet.currentSystemId(), fleet.location().current());
        var to = parking(state, fleet.currentSystemId(), target);
        if (from == null || to == null) throw new IllegalArgumentException("Orbital transfers require two known planetary or lunar parking sites");
        SolarSystem system = state.solarSystems().stream().filter(item -> item.id().equals(fleet.currentSystemId())).findFirst().orElseThrow();
        boolean lunar = from.body().moon() != null || to.body().moon() != null;
        LunarTransferComparison.Result comparison = null;
        if (lunar) {
            boolean outbound = from.body().moon() == null;
            var parent = outbound ? from : to;
            var moon = outbound ? to : from;
            if (parent.body().moon() != null || moon.body().moon() == null
                    || !parent.body().id().equals(moon.body().planet().id()))
                throw new IllegalArgumentException("Lunar transfers require a moon and its own parent planet");
            comparison = LunarTransferComparison.compare(system, parent.body().planet(), moon.body().moon(), state.turn(),
                    parent.altitudeKm(), moon.altitudeKm(), outbound);
        }
        var orbit = lunar ? comparison.orbit() : CircularOrbitalEphemeris.comparison(system, from.body().planet(), to.body().planet(),
                state.turn(), from.altitudeKm(), to.altitudeKm());
        double wait = orbit.waitDays() * 86400;
        var coast = lunar ? comparison.coast() : new HohmannCoast(CircularOrbitalEphemeris.gravitationalParameter(system), from.body().distance() * 1000,
                to.body().distance() * 1000, CircularOrbitalEphemeris.phase(system, from.body().planet(), state.turn() + orbit.waitDays()));
        var events = new ArrayList<OrbitalFlight.Maneuver>();
        var mass = new HashMap<String, Double>();
        var total = new HashMap<String, Double>();
        var reactor = new HashMap<String, InterstellarTravel.ReactorFuelUse>();
        double[] impulses = {orbit.departureDeltaVMps(), orbit.captureDeltaVMps(), target.kind() == FleetLocation.Kind.DOCKED ? 10 : .001};
        double[] epochs = {wait, wait + coast.coastSeconds(), wait + coast.coastSeconds() + 3600};
        for (int phase = 0; phase < 3; phase++) {
            Map<String, OrbitalFlight.Burn> burns = new HashMap<>();
            double hours = 0;
            for (var ship : fleet.ships()) {
                var design = FleetSupplySimulation.design(state, ship);
                if (design == null || design.powerProfile() == null || !fleet.ownerEntityId().equals(ship.ownerEntityId()))
                    throw new IllegalArgumentException("Every fleet member needs an owned blueprint");
                var drive = PropulsionCatalog.mainDrive(design.equippedModuleIds());
                if (drive == null || "mod_ion_drive".equals(drive.moduleId()))
                    throw new IllegalArgumentException("This orbital prototype requires a recognized short-burn drive");
                var budget = PlanetaryTransferComparison.budget(state, fleet, ship, orbit);
                if (!budget.propellantFits() || !budget.reactorFeedFits() || !budget.impulseFits())
                    throw new IllegalArgumentException(budget.explanation());
                var feed = PropulsionCatalog.availableReactorFuel(drive, ship);
                double exhaust = drive.exhaustVelocityMps() * (feed == null ? 1 : feed.exhaustMultiplier());
                double loaded = mass.getOrDefault(ship.id(), budget.wetMassKg());
                double kg = loaded * -Math.expm1(-impulses[phase] / exhaust);
                double feedRate = feed == null ? 0 : feed.kgPerPropellantKg();
                double used = total.getOrDefault(ship.id(), 0.0) + kg;
                if (ship.currentFuelKg() + 1e-6 < used + budget.protectedPropellantKg())
                    throw new IllegalArgumentException("Main fuel cannot fund escape, capture and approach with protected reserve");
                if (feed != null && used * feedRate > ship.storedCargoKg().getOrDefault(feed.materialId(), 0.0) + 1e-6)
                    throw new IllegalArgumentException("Drive reactor feed cannot fund all maneuvers");
                burns.put(ship.id(), new OrbitalFlight.Burn(impulses[phase], exhaust, kg, budget.protectedPropellantKg(),
                        feed == null ? null : feed.materialId(), feedRate));
                total.put(ship.id(), used); mass.put(ship.id(), loaded - kg * (1 + feedRate));
                if (feed != null && feedRate > 0) reactor.put(ship.id(), new InterstellarTravel.ReactorFuelUse(feed.materialId(), used * feedRate));
                hours = Math.max(hours, impulses[phase] * loaded / design.totalThrustN() / 3600);
            }
            if (!Double.isFinite(hours) || hours <= 0 || hours > 6)
                throw new IllegalArgumentException("Maneuver exceeds the prototype's six-hour short-burn limit");
            events.add(new OrbitalFlight.Maneuver(epochs[phase], hours, burns));
        }
        var environment = ShipSolarEnvironment.journey(state, fleet, target);
        var itinerary = new OrbitalFlight.Itinerary(from.body().id(), to.body().id(), from.altitudeKm(), to.altitudeKm(),
                state.turn(), wait, coast, environment, events, lunar
                ? new OrbitalFlight.Frame(comparison.parentId(), OrbitalFlight.CenterKind.PLANETARY)
                : new OrbitalFlight.Frame("", OrbitalFlight.CenterKind.STELLAR));
        return new LocalTravel.Plan(itinerary.totalSeconds() / 86400, total, reactor, null, itinerary);
    }

    public static String recoveryProblem(Fleet fleet) {
        return fleet.location().orbitalFlight() == null ? "" : fleet.location().orbitalFlight().atSource()
                ? fleet.location().orbitalFlight().itinerary().parkingTransfer()
                ? "Cancel the waiting order and plan a new funded parking maneuver from this site"
                : "Cancel the waiting order and plan a new funded launch window from this base"
                : "Orbital recovery from this ballistic state is unsupported; no free capture or docking is available";
    }
}
