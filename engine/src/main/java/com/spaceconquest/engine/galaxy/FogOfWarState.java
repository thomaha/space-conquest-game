package com.spaceconquest.engine.galaxy;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks planetary discovery, foreign fleet sightings and fog-of-war visibility per empire.
 */
public record FogOfWarState(
        String empireId,
        List<String> exploredSystemIds,
        List<String> scannedPlanetIds,
        List<String> detectedFleetIds,
        List<String> discoveredAnomalyIds,
        List<String> discoveredPirateBaseIds
) {
    public FogOfWarState {
        if (exploredSystemIds == null) exploredSystemIds = List.of();
        if (scannedPlanetIds == null) scannedPlanetIds = List.of();
        if (detectedFleetIds == null) detectedFleetIds = List.of();
        if (discoveredAnomalyIds == null) discoveredAnomalyIds = List.of();
        if (discoveredPirateBaseIds == null) discoveredPirateBaseIds = List.of();
    }

    public boolean isSystemExplored(String systemId) {
        return exploredSystemIds != null && exploredSystemIds.contains(systemId);
    }

    public boolean isPlanetScanned(String planetId) {
        return scannedPlanetIds != null && scannedPlanetIds.contains(planetId);
    }

    public FogOfWarState withExploredSystem(String systemId) {
        if (isSystemExplored(systemId)) return this;
        List<String> updated = new ArrayList<>(exploredSystemIds);
        updated.add(systemId);
        return new FogOfWarState(empireId, updated, scannedPlanetIds, detectedFleetIds, discoveredAnomalyIds, discoveredPirateBaseIds);
    }

    public FogOfWarState withScannedPlanet(String planetId) {
        if (isPlanetScanned(planetId)) return this;
        List<String> updated = new ArrayList<>(scannedPlanetIds);
        updated.add(planetId);
        return new FogOfWarState(empireId, exploredSystemIds, updated, detectedFleetIds, discoveredAnomalyIds, discoveredPirateBaseIds);
    }
}
