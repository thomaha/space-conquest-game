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
 * Calculates sensor detection cones, uncovers uncharted star systems, detects foreign fleets and reveals hidden anomalies.
 */
public class SensorProcessor {

    public static final double BASE_COLONY_SENSOR_RANGE = 15.0;
    public static final double STATION_SENSOR_RANGE = 25.0;

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

        List<FogOfWarState> updatedStates = new ArrayList<>();

        for (Empire empire : empires) {
            FogOfWarState currentState = findOrCreateState(empire.id(), existingStates, empire.controlledSystemIds());

            Set<String> exploredSystems = new HashSet<>(currentState.exploredSystemIds());
            Set<String> scannedPlanets = new HashSet<>(currentState.scannedPlanetIds());
            Set<String> detectedFleets = new HashSet<>();
            Set<String> discoveredAnomalies = new HashSet<>(currentState.discoveredAnomalyIds());
            Set<String> discoveredPirates = new HashSet<>(currentState.discoveredPirateBaseIds());

            // Always explore controlled systems and scan their planets
            if (empire.controlledSystemIds() != null) {
                for (String sysId : empire.controlledSystemIds()) {
                    exploredSystems.add(sysId);
                    if (solarSystems != null) {
                        for (SolarSystem sys : solarSystems) {
                            if (sys.id().equals(sysId) && sys.planets() != null) {
                                for (Planet p : sys.planets()) {
                                    scannedPlanets.add(p.id());
                                }
                            }
                        }
                    }
                }
            }

            // Sensor ranges from mobile fleets
            if (fleets != null) {
                for (Fleet fleet : fleets) {
                    if (empire.id().equals(fleet.ownerEntityId())) {
                        String currentSys = fleet.currentSystemId();
                        if (currentSys != null && !currentSys.isEmpty()) {
                            exploredSystems.add(currentSys);
                            double range = fleetProcessor.calculateFleetScannerRange(fleet, designs);
                            if (range >= 20.0 && solarSystems != null) {
                                // Full explorer scan scans all planets in the system
                                for (SolarSystem sys : solarSystems) {
                                    if (sys.id().equals(currentSys) && sys.planets() != null) {
                                        for (Planet p : sys.planets()) {
                                            scannedPlanets.add(p.id());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Sensor ranges from orbital stations
            if (stations != null) {
                for (OrbitalStation station : stations) {
                    if (empire.id().equals(station.ownerEntityId()) && station.systemId() != null) {
                        exploredSystems.add(station.systemId());
                    }
                }
            }

            // Detect foreign fleets in explored systems
            if (fleets != null) {
                for (Fleet fleet : fleets) {
                    if (!empire.id().equals(fleet.ownerEntityId())) {
                        if (exploredSystems.contains(fleet.currentSystemId())) {
                            detectedFleets.add(fleet.id());
                        }
                    }
                }
            }

            // Discover anomalies in explored systems
            if (anomalies != null) {
                for (Anomaly anomaly : anomalies) {
                    if (exploredSystems.contains(anomaly.systemId())) {
                        discoveredAnomalies.add(anomaly.id());
                    }
                }
            }

            // Discover pirate bases in explored systems with high scanner coverage
            if (pirateBases != null) {
                for (PirateBase pb : pirateBases) {
                    if (exploredSystems.contains(pb.systemId())) {
                        discoveredPirates.add(pb.id());
                    }
                }
            }

            updatedStates.add(new FogOfWarState(
                    empire.id(),
                    new ArrayList<>(exploredSystems),
                    new ArrayList<>(scannedPlanets),
                    new ArrayList<>(detectedFleets),
                    new ArrayList<>(discoveredAnomalies),
                    new ArrayList<>(discoveredPirates)
            ));
        }

        return updatedStates;
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
                    updated.detectedFleetIds(), discovered, updated.discoveredPirateBaseIds()
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
