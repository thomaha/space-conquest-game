package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.ConstructionMaterials;
import java.util.List;
import java.util.Map;

/** Recovery of the existing abstract local and warp itineraries, preserving paid maneuver budgets. */
public final class PausedTravelRecovery {
    public record Preview(boolean local, String mode, double remainingDays,
                          List<ShipPowerForecast.Readiness> electrical) {
        public Preview { electrical = List.copyOf(electrical); }
        public boolean ready() { return ShipPowerForecast.ready(electrical); }
    }
    private PausedTravelRecovery() {}

    public static Preview preview(GameState state, Fleet fleet) {
        if (state == null || fleet == null || fleet.ships().isEmpty()
                || !Fleet.MODE_POWER_INTERRUPTED.equals(fleet.interstellarMode())) return null;
        boolean local = fleet.location().inTransit();
        if (!local && (fleet.interstellarDistanceMeters() > 0 || !fleet.hasInterstellarOrder()
                || !fleet.location().isAt(FleetLocation.Site.deepSpace()))) return null;
        if (state.solarSystems().stream().noneMatch(system -> system.id().equals(fleet.currentSystemId()))) return null;
        String mode = fleet.hasInterstellarOrder()
                ? fleet.interstellarDistanceMeters() > 0 ? Fleet.MODE_SUBLIGHT : Fleet.MODE_WARP : "";
        for (ShipInstance ship : fleet.ships()) {
            var design = state.shipDesigns().stream().filter(item -> item.id().equals(ship.designId())).findFirst().orElse(null);
            if (!ship.ownerEntityId().equals(fleet.ownerEntityId()) || design == null
                    || !Double.isFinite(ship.currentFuelKg()) || ship.currentFuelKg() < 0
                    || ship.currentFuelKg() > design.fuelCapacityKg() + .000001) return null;
            if (Fleet.MODE_SUBLIGHT.equals(mode) && design.powerProfile() != null
                    && PropulsionCatalog.mainDrive(design.equippedModuleIds()) != null
                    && fleet.interstellarFuelBudgetKg().getOrDefault(ship.id(), 0.0) <= 0) return null;
        }
        InterstellarTravel.Plan crossing = null;
        if (fleet.hasInterstellarOrder()) {
            if (fleet.currentSystemId().equals(fleet.targetSystemId()) || state.solarSystems().stream()
                    .noneMatch(system -> system.id().equals(fleet.targetSystemId()))) return null;
            if (Fleet.MODE_WARP.equals(mode) && !warpResearched(state, fleet)) return null;
            if (Fleet.MODE_SUBLIGHT.equals(mode) && (fleet.interstellarAccelerationMps2() <= 0
                    || fleet.interstellarPeakSpeedMps() <= 0
                    || fleet.interstellarFuelBudgetKg().values().stream().anyMatch(value -> !Double.isFinite(value) || value < 0)
                    || fleet.ships().stream().anyMatch(ship -> ship.currentFuelKg() + .000001
                    < fleet.interstellarFuelBudgetKg().getOrDefault(ship.id(), 0.0)))) return null;
            double days = fleet.interstellarTravelDays() - fleet.interstellarElapsedDays();
            if (days <= 0 || !Double.isFinite(days)) return null;
            crossing = new InterstellarTravel.Plan(mode, days, fleet.interstellarDistanceMeters(),
                    fleet.interstellarAccelerationMps2(), fleet.interstellarPeakSpeedMps(),
                    fleet.interstellarFuelBudgetKg(), Map.of());
        }
        double localDays = local ? (1 - fleet.location().progress()) * fleet.location().travelDays() : 0;
        if (local && (!Double.isFinite(localDays) || localDays <= 0 || !validSite(state, fleet, fleet.location().current())
                || !validSite(state, fleet, fleet.location().destination()))) return null;
        if (local && fleet.hasInterstellarOrder()
                && (!fleet.location().destination().equals(FleetLocation.Site.deepSpace())
                || fleet.interstellarElapsedDays() > .000001 || fleet.transitProgress() > .000001)) return null;
        var localPlan = local ? new LocalTravel.Plan(localDays, Map.of(), Map.of()) : null;
        var destination = local ? fleet.location().destination() : FleetLocation.Site.deepSpace();
        var power = ShipPowerForecast.departure(state, fleet, destination, localPlan, crossing);
        return new Preview(local, mode, Math.ceil(localDays) + (crossing == null ? 0 : Math.ceil(crossing.days())), power);
    }

    private static boolean warpResearched(GameState state, Fleet fleet) {
        String empireId = state.corporations().stream().filter(corp -> corp.id().equals(fleet.ownerEntityId()))
                .map(Corporation::empireId).findFirst().orElse(fleet.ownerEntityId());
        return state.empires().stream().anyMatch(empire -> empire.id().equals(empireId)
                && empire.unlockedTechIds().contains("warp"));
    }

    private static boolean validSite(GameState state, Fleet fleet, FleetLocation.Site site) {
        return switch (site.kind()) {
            case SURFACE, ORBIT -> fleet.currentSystemId().equals(ConstructionMaterials.systemForBody(state, site.entityId()));
            case DOCKED -> state.orbitalStations().stream().anyMatch(station -> station.id().equals(site.entityId())
                    && station.systemId().equals(fleet.currentSystemId()));
            case DEEP_SPACE -> site.equals(FleetLocation.Site.deepSpace())
                    || site.entityId().equals(fleet.currentSystemId() + "_star")
                    || state.megastructures().stream().anyMatch(mega -> mega.systemId().equals(fleet.currentSystemId())
                    && mega.targetCelestialId().equals(site.entityId()));
        };
    }

    public static Fleet resume(Fleet fleet, Preview preview) {
        return copy(fleet, fleet.location(), preview.mode(), fleet.interstellarElapsedDays(), fleet.transitProgress());
    }

    /** Only the powered fraction advances abstract progress. A paused itinerary never advances itself. */
    public static Fleet interrupt(Fleet fleet, double poweredHours) {
        if (Fleet.MODE_POWER_INTERRUPTED.equals(fleet.interstellarMode())) return fleet;
        if (fleet.location().inTransit()) {
            var location = fleet.location().advanceDays(poweredHours / 24);
            return copy(fleet, location, location.inTransit() || fleet.hasInterstellarOrder()
                    ? Fleet.MODE_POWER_INTERRUPTED : "", fleet.interstellarElapsedDays(), fleet.transitProgress());
        }
        double elapsed = fleet.interstellarElapsedDays() + poweredHours / 24;
        double days = fleet.interstellarTravelDays() > 0 ? fleet.interstellarTravelDays() : InterstellarTravel.WARP_DAYS;
        if (elapsed >= days) return new Fleet(fleet.id(), fleet.name(), fleet.ownerEntityId(), fleet.targetSystemId(), "",
                fleet.coordinateX(), fleet.coordinateY(), 0, false, fleet.fleetStance(), fleet.ships(), fleet.location(), "", 0);
        return copy(fleet, fleet.location(), Fleet.MODE_POWER_INTERRUPTED, elapsed, Math.clamp(elapsed / days, 0, 1));
    }

    private static Fleet copy(Fleet fleet, FleetLocation location, String mode, double elapsed, double progress) {
        return new Fleet(fleet.id(), fleet.name(), fleet.ownerEntityId(), fleet.currentSystemId(), fleet.targetSystemId(),
                fleet.coordinateX(), fleet.coordinateY(), progress, Fleet.MODE_WARP.equals(mode) && !location.inTransit(),
                fleet.fleetStance(), fleet.ships(), location,
                mode, fleet.interstellarTravelDays(), fleet.interstellarDistanceMeters(), fleet.interstellarAccelerationMps2(),
                elapsed, fleet.interstellarPeakSpeedMps(), fleet.interstellarFuelBudgetKg(), null, fleet.journeyPropulsion());
    }
}
