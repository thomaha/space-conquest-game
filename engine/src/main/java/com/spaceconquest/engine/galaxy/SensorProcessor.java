package com.spaceconquest.engine.galaxy;

import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.espionage.PirateBase;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FleetProcessor;
import com.spaceconquest.engine.ship.ShipDesign;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Refreshes permanent exploration knowledge and current module-backed sensor contacts.
 */
public class SensorProcessor {

    private final FleetProcessor fleetProcessor = new FleetProcessor();

    public List<FogOfWarState> updateSensorCoverage(
            List<Empire> empires,
            List<SolarSystem> solarSystems,
            List<Fleet> fleets,
            List<ShipDesign> designs,
            List<OrbitalStation> stations,
            List<Anomaly> anomalies,
            List<PirateBase> pirateBases,
            List<FogOfWarState> existingStates
    ) {
        if (empires == null) return List.of();
        var coverage = com.spaceconquest.engine.GameState.builder().empires(empires).solarSystems(solarSystems)
                .fleets(fleets).shipDesigns(designs).orbitalStations(stations)
                .pirateBases(pirateBases).fogOfWarStates(existingStates).build();
        return updateSensorCoverage(coverage, anomalies);
    }

    /** Rebuilds current contacts from all authorized hardware on this exact campaign day. */
    public List<FogOfWarState> updateSensorCoverage(com.spaceconquest.engine.GameState coverage, List<Anomaly> anomalies) {
        if (coverage == null) return List.of();
        var viewers = new ArrayList<String>();
        coverage.empires().forEach(empire -> viewers.add(empire.id()));
        coverage.corporations().forEach(corporation -> viewers.add(corporation.id()));
        List<FogOfWarState> updatedStates = new ArrayList<>();
        for (String viewer : viewers) {
            String home = SensorSharing.homeEmpire(coverage, viewer);
            var empire = coverage.empires().stream().filter(item -> home.equals(item.id())).findFirst().orElse(null);
            var controlled = empire == null ? List.<String>of() : empire.controlledSystemIds();
            var current = findOrCreateState(viewer, coverage.fogOfWarStates(), controlled);
            Set<String> explored = new HashSet<>(current.exploredSystemIds());
            Set<String> scanned = new HashSet<>(current.scannedPlanetIds());
            Set<String> detected = new HashSet<>();
            Set<String> discoveries = new HashSet<>(current.discoveredAnomalyIds());
            Set<String> pirates = new HashSet<>(current.discoveredPirateBaseIds());
            var owners = SensorSharing.observerOwners(coverage, viewer);
            for (String system : controlled) { explored.add(system); scanPlanets(coverage, system, scanned); }
            for (var fleet : coverage.fleets()) {
                if (owners.contains(fleet.ownerEntityId()) && !fleet.isInterstellarTransit() && !fleet.isInWarp()
                        && SensorCoverage.rangeKm(coverage, fleet) > 0) {
                    explored.add(fleet.currentSystemId());
                    if (fleetProcessor.calculateFleetScannerRange(fleet, coverage.shipDesigns()) >= 20)
                        scanPlanets(coverage, fleet.currentSystemId(), scanned);
                }
                if (!(home.isBlank() ? viewer : home).equals(fleet.ownerEntityId()) && SensorCoverage.detects(coverage, viewer, fleet))
                    detected.add(fleet.id());
            }
            for (var station : coverage.orbitalStations()) {
                if (owners.contains(station.ownerEntityId()) && station.isOperational() && station.modules().stream()
                        .anyMatch(module -> module.isOnline() && com.spaceconquest.engine.ship.SensorModuleCatalog.module(module.type()) != null))
                    explored.add(station.systemId());
            }
            if (anomalies != null) for (var anomaly : anomalies)
                if (SensorCoverage.hasSensor(coverage, viewer, anomaly.systemId())) discoveries.add(anomaly.id());
            for (var base : coverage.pirateBases())
                if (SensorCoverage.hasSensor(coverage, viewer, base.systemId())) pirates.add(base.id());
            updatedStates.add(new FogOfWarState(viewer, sorted(explored), sorted(scanned), sorted(detected), sorted(discoveries), sorted(pirates),
                    SensorContactMemory.refresh(coverage, current.contacts(), detected)));
        }
        return List.copyOf(updatedStates);
    }

    private static List<String> sorted(Set<String> values) { return values.stream().sorted().toList(); }

    private static void scanPlanets(com.spaceconquest.engine.GameState state, String systemId, Set<String> scanned) {
        for (var system : state.solarSystems()) if (system.id().equals(systemId))
            for (var planet : system.planets()) scanned.add(planet.id());
    }
    public FogOfWarState scanSystem(
            FogOfWarState state,
            String systemId,
            SolarSystem system,
            List<Anomaly> anomalies
    ) {
        if (state == null || systemId == null) return state;

        FogOfWarState updated = state.withExploredSystem(systemId);
        if (system != null && system.planets() != null) {
            for (Planet p : system.planets()) {
                updated = updated.withScannedPlanet(p.id());
            }
        }
        if (anomalies != null) {
            List<String> discovered = new ArrayList<>(updated.discoveredAnomalyIds());
            for (Anomaly a : anomalies) {
                if (systemId.equals(a.systemId()) && !discovered.contains(a.id())) {
                    discovered.add(a.id());
                }
            }
            updated = new FogOfWarState(
                    updated.empireId(), updated.exploredSystemIds(), updated.scannedPlanetIds(),
                    updated.detectedFleetIds(), discovered, updated.discoveredPirateBaseIds(), updated.contacts()
            );
        }
        return updated;
    }

    private FogOfWarState findOrCreateState(String empireId, List<FogOfWarState> states, List<String> controlledSystems) {
        if (states != null) {
            for (FogOfWarState s : states) {
                if (s.empireId().equals(empireId)) {
                    return s;
                }
            }
        }
        List<String> explored = controlledSystems != null ? new ArrayList<>(controlledSystems) : new ArrayList<>();
        return new FogOfWarState(
                empireId,
                explored,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>()
        );
    }
}
