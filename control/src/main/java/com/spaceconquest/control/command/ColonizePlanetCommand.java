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

        Race race = null;
        try {
            List<Race> races = DataModelLoader.loadRaces();
            for (Race r : races) {
                if (r.id().equalsIgnoreCase(empire.raceId())) {
                    race = r;
                    break;
                }
            }
        } catch (IOException ignored) {}

        if (race == null) return state;

        ColonizationProcessor processor = new ColonizationProcessor();
        ColonizationProcessor.ColonizationResult res = processor.colonizeWorld(
                planet, fleet, race, empire, state.shipDesigns()
        );

        if (!res.isSuccessful()) {
            return state;
        }

        // Update planet in system
        List<Planet> updatedPlanets = new ArrayList<>();
        for (Planet p : system.planets()) {
            if (p.id().equals(targetPlanetId)) {
                updatedPlanets.add(res.colonizedPlanet());
            } else {
                updatedPlanets.add(p);
            }
        }

        SolarSystem updatedSys = new SolarSystem(
                system.id(), system.name(), system.description(),
                system.x(), system.y(), system.z(),
                system.sunMass(), system.sunDiameter(), system.sunColor(),
                updatedPlanets, system.asteroidBelts()
        );

        List<SolarSystem> updatedSystems = new ArrayList<>();
        for (SolarSystem s : state.solarSystems()) {
            if (s.id().equals(targetSystemId)) {
                updatedSystems.add(updatedSys);
            } else {
                updatedSystems.add(s);
            }
        }

        // Update fleet
        List<Fleet> updatedFleets = new ArrayList<>();
        for (Fleet f : state.fleets()) {
            if (f.id().equals(fleetId)) {
                updatedFleets.add(res.updatedFleet());
            } else {
                updatedFleets.add(f);
            }
        }

        // Update empire controlled systems
        List<Empire> updatedEmpires = new ArrayList<>();
        for (Empire e : state.empires()) {
            if (e.id().equals(empireId)) {
                List<String> sysList = new ArrayList<>(e.controlledSystemIds());
                if (!sysList.contains(targetSystemId)) {
                    sysList.add(targetSystemId);
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
}
