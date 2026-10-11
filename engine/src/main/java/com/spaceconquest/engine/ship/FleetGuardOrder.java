package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.ConstructionMaterials;

/** Holds a physical site for an owned object; following a moving target requires escort. */
public record FleetGuardOrder(TargetType targetType, String targetId, String systemId, FleetLocation.Site site) {
    public enum TargetType { BODY, STATION, FLEET }
    public boolean atSite(FleetLocation location) {
        return location.isAt(site) || targetType == TargetType.BODY && RecoveredParkingTravel.atSite(location, site);
    }

    public boolean validTarget(GameState state, Fleet guard) {
        if (state == null || guard == null || targetType == null || targetId == null || systemId == null || site == null
                || !systemId.equals(guard.currentSystemId()) || !atSite(guard.location())
                || guard.hasInterstellarOrder() || guard.isInWarp() || guard.ships().isEmpty()) return false;
        return switch (targetType) {
            case STATION -> state.orbitalStations().stream().anyMatch(station -> targetId.equals(station.id())
                    && guard.ownerEntityId().equals(station.ownerEntityId()) && systemId.equals(station.systemId())
                    && station.isOperational() && site.equals(FleetLocation.Site.docked(targetId)));
            case BODY -> targetId.equals(site.entityId())
                    && (site.kind() == FleetLocation.Kind.ORBIT || site.kind() == FleetLocation.Kind.SURFACE)
                    && systemId.equals(ConstructionMaterials.systemForBody(state, targetId))
                    && state.empires().stream().anyMatch(empire -> guard.ownerEntityId().equals(empire.id())
                    && empire.controlledSystemIds().contains(systemId));
            case FLEET -> state.fleets().stream().anyMatch(target -> targetId.equals(target.id())
                    && !guard.id().equals(target.id()) && guard.ownerEntityId().equals(target.ownerEntityId())
                    && systemId.equals(target.currentSystemId()) && target.location().isAt(site)
                    && !target.hasInterstellarOrder() && !target.isInWarp() && !target.ships().isEmpty());
        };
    }
}
