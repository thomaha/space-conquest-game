package com.spaceconquest.engine.galaxy;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.*;

/** Current installed hardware coverage, separate from permanent exploration history. */
public final class SensorCoverage {
    private SensorCoverage() {}

    public static double rangeKm(GameState state, Fleet fleet) {
        return fleet.ships().stream().mapToDouble(ship -> state.shipDesigns().stream()
                .filter(design -> ship.designId().equals(design.id()))
                .mapToDouble(design -> SensorModuleCatalog.profile(design, ship).rangeKm()).max().orElse(0))
                .max().orElse(0);
    }

    public static boolean hasSensor(GameState state, String owner, String system) {
        var owners = SensorSharing.observerOwners(state, owner);
        return state.fleets().stream().anyMatch(fleet -> owners.contains(fleet.ownerEntityId())
                && system.equals(fleet.currentSystemId()) && !fleet.isInWarp() && !fleet.isInterstellarTransit() && rangeKm(state, fleet) > 0)
                || state.orbitalStations().stream().anyMatch(station -> owners.contains(station.ownerEntityId())
                && system.equals(station.systemId()) && station.isOperational() && station.modules().stream()
                .anyMatch(module -> module.isOnline() && SensorModuleCatalog.module(module.type()) != null));
    }

    public static boolean detects(GameState state, String owner, Fleet target) {
        if (target.isInWarp() || target.isInterstellarTransit() || target.ships().isEmpty()) return false;
        return covers(state, owner, target.currentSystemId(), target.location());
    }

    /** Current authorized coverage at a fleet's actual site or motion, including shared observers. */
    public static boolean covers(GameState state, String owner, String system, FleetLocation location) {
        var owners = SensorSharing.observerOwners(state, owner);
        return state.fleets().stream().anyMatch(observer -> owners.contains(observer.ownerEntityId())
                && observer.currentSystemId().equals(system) && !observer.isInWarp() && !observer.isInterstellarTransit()
                && within(state, system, observer.location(), location, rangeKm(state, observer)))
                || state.orbitalStations().stream().anyMatch(station -> owners.contains(station.ownerEntityId())
                && station.systemId().equals(system) && station.isOperational()
                && station.modules().stream().anyMatch(module -> module.isOnline()
                && SensorModuleCatalog.module(module.type()) != null && within(state, system,
                FleetLocation.at(FleetLocation.Site.docked(station.id())), location,
                SensorModuleCatalog.module(module.type()).operationalStats().get("sensorRangeKm"))));
    }

    private static boolean within(GameState state, String system, FleetLocation source, FleetLocation target, double rangeKm) {
        if (rangeKm <= 0) return false;
        if (!source.inTransit() && !target.inTransit() && source.current().equals(target.current())) return true;
        var first = SensorPosition.at(state, system, source);
        var last = SensorPosition.at(state, system, target);
        return first != null && last != null && Math.hypot(Math.hypot(first.xMeters() - last.xMeters(),
                first.yMeters() - last.yMeters()), first.zMeters() - last.zMeters()) <= rangeKm * 1000;
    }

}
