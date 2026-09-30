package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.ConstructionMaterials;
import com.spaceconquest.engine.macrostructure.OrbitalStation;

/** Resolves physical fleet sites for construction and hub cargo handling. */
public final class FleetPositioning {
    private FleetPositioning() {}

    public static String systemForHub(GameState state, CommercialHub hub) {
        String surface = ConstructionMaterials.systemForBody(state, hub.entityId());
        if (surface != null) return surface;
        return state.orbitalStations().stream().filter(station ->
                station.id().equals(hub.entityId())).map(OrbitalStation::systemId)
                .findFirst().orElse(null);
    }

    public static FleetLocation.Site hubSite(GameState state, CommercialHub hub) {
        if (ConstructionMaterials.systemForBody(state, hub.entityId()) != null)
            return FleetLocation.Site.surface(hub.entityId());
        return state.orbitalStations().stream().anyMatch(station ->
                station.id().equals(hub.entityId()))
                ? FleetLocation.Site.docked(hub.entityId()) : null;
    }

    public static boolean atHub(GameState state, Fleet fleet, CommercialHub hub) {
        FleetLocation.Site site = hubSite(state, hub);
        return site != null && !fleet.hasInterstellarOrder()
                && systemForHub(state, hub).equals(fleet.currentSystemId())
                && fleet.location().isAt(site);
    }

    public static boolean atOrbitalSite(GameState state, Fleet fleet, String systemId,
                                        String siteId) {
        if (!systemId.equals(fleet.currentSystemId()) || fleet.hasInterstellarOrder()
                || fleet.location().inTransit()) return false;
        OrbitalStation station = state.orbitalStations().stream()
                .filter(item -> item.id().equals(siteId)).findFirst().orElse(null);
        if (station != null) return systemId.equals(station.systemId())
                && fleet.location().isAt(FleetLocation.Site.docked(station.id()));
        if (siteId == null || siteId.equals(systemId))
            return fleet.location().isAt(FleetLocation.Site.deepSpace());
        if (systemId.equals(ConstructionMaterials.systemForBody(state, siteId)))
            return fleet.location().isAt(FleetLocation.Site.orbit(siteId));
        return fleet.location().isAt(FleetLocation.Site.deepSpace(siteId));
    }
}
