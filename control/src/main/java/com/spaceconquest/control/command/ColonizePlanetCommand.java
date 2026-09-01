package com.spaceconquest.control.command;

import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.Race;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.combat.ColonizationProcessor;
import com.spaceconquest.engine.ship.Fleet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Command to deploy a colony ship to establish a new colonial settlement on a virgin celestial world.
 */
public record ColonizePlanetCommand(
        String empireId,
        String targetSystemId,
        String targetPlanetId,
        String fleetId
) implements GameCommand {

    public ColonizePlanetCommand(String empireId, String targetPlanetId, String fleetId, int colonistCount, String raceId) {
        this(empireId, "sol", targetPlanetId, fleetId);
    }

    @Override
    public boolean validate(GameState state) {
        if (state == null || empireId == null || targetSystemId == null || targetPlanetId == null || fleetId == null) {
            return false;
        }
        return state.fleets().stream().anyMatch(f -> f.id().equals(fleetId));
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) {
            return state;
        }

        Empire empire = state.empires().stream().filter(e -> e.id().equals(empireId)).findFirst().orElse(null);
        Fleet fleet = state.fleets().stream().filter(f -> f.id().equals(fleetId)).findFirst().orElse(null);
        SolarSystem system = state.solarSystems().stream().filter(s -> s.id().equals(targetSystemId)).findFirst().orElse(null);
        if (empire == null || fleet == null || system == null) return state;

        Planet planet = system.planets().stream().filter(p -> p.id().equals(targetPlanetId)).findFirst().orElse(null);
        if (planet == null) return state;

        Race race = loadEmpireRace(empire.raceId());
        if (race == null) return state;

        ColonizationProcessor processor = new ColonizationProcessor();
        ColonizationProcessor.ColonizationResult res = processor.colonizeWorld(
                planet, fleet, race, empire, state.shipDesigns()
        );

        if (!res.isSuccessful()) {
            return state;
        }

        List<SolarSystem> updatedSystems = updateSystemsWithColonizedPlanet(state.solarSystems(), targetSystemId, targetPlanetId, res.colonizedPlanet());
        List<Fleet> updatedFleets = updateFleetsWithColonizer(state.fleets(), fleetId, res.updatedFleet());
        List<Empire> updatedEmpires = updateEmpireControlledSystems(state.empires(), empireId, targetSystemId);

        return new GameState(
                state.turn(),
                state.status(),
                updatedSystems,
                updatedEmpires,
                state.corporations(),
                state.commercialHubs(),
                state.shadowSyndicates(),
                state.diplomaticRelations(),
                state.systemGovernors(),
                state.researchProjects(),
                state.technologyExchangeRoutes(),
                state.shipDesigns(),
                updatedFleets,
                state.geologicalDeposits(),
                state.powerGrids(),
                state.industrialFacilities(),
                state.expansionProjects()
        );
    }

    private Race loadEmpireRace(String raceId) {
        try {
            List<Race> races = DataModelLoader.loadRaces();
            for (Race r : races) {
                if (r.id().equalsIgnoreCase(raceId)) {
                    return r;
                }
            }
        } catch (IOException ignored) {}
        return null;
    }

    private List<SolarSystem> updateSystemsWithColonizedPlanet(List<SolarSystem> systems, String sysId, String planetId, Planet colonizedPlanet) {
        List<SolarSystem> updatedSystems = new ArrayList<>();
        for (SolarSystem s : systems) {
            if (s.id().equals(sysId)) {
                List<Planet> updatedPlanets = new ArrayList<>();
                for (Planet p : s.planets()) {
                    updatedPlanets.add(p.id().equals(planetId) ? colonizedPlanet : p);
                }
                updatedSystems.add(new SolarSystem(
                        s.id(), s.name(), s.description(), s.x(), s.y(), s.z(),
                        s.sunMass(), s.sunDiameter(), s.sunColor(), updatedPlanets, s.asteroidBelts()
                ));
            } else {
                updatedSystems.add(s);
            }
        }
        return updatedSystems;
    }

    private List<Fleet> updateFleetsWithColonizer(List<Fleet> fleets, String targetFleetId, Fleet updatedFleet) {
        List<Fleet> updatedFleets = new ArrayList<>();
        for (Fleet f : fleets) {
            updatedFleets.add(f.id().equals(targetFleetId) ? updatedFleet : f);
        }
        return updatedFleets;
    }

    private List<Empire> updateEmpireControlledSystems(List<Empire> empires, String targetEmpireId, String newSystemId) {
        List<Empire> updatedEmpires = new ArrayList<>();
        for (Empire e : empires) {
            if (e.id().equals(targetEmpireId)) {
                List<String> sysList = new ArrayList<>(e.controlledSystemIds());
                if (!sysList.contains(newSystemId)) {
                    sysList.add(newSystemId);
                }
                updatedEmpires.add(new Empire(
                        e.id(), e.name(), e.raceId(), e.societyStructure(),
                        e.treasuryCredits(), e.corporateTaxRate(), sysList,
                        e.ministries(), e.systemGovernorAssignments(), e.unlockedTechIds(), e.activeShipDesignIds()
                ));
            } else {
                updatedEmpires.add(e);
            }
        }
        return updatedEmpires;
    }
}
