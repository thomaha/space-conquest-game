package com.spaceconquest.engine.combat;

import com.spaceconquest.engine.AsteroidBelt;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.Population;
import com.spaceconquest.engine.SolarSystem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves orbital planetary bombardment operations: kinetic dart strikes,
 * isotopic fission warheads and antimatter planet-crackers with shattered asteroid debris generation.
 */
public class OrbitalBombardmentProcessor {

    public static final String KINETIC_DARTS = "KINETIC_DARTS";
    public static final String FISSION_WARHEADS = "FISSION_WARHEADS";
    public static final String PLANET_CRACKER = "PLANET_CRACKER";

    public record BombardmentResult(
            String bombType,
            boolean isPlanetDestroyed,
            SolarSystem updatedSystem,
            Planet updatedPlanet,
            double civilianCasualties,
            String report
    ) {}

    /**
     * Executes orbital bombardment on a target celestial body.
     */
    public BombardmentResult executeBombardment(
            String bombType,
            Planet targetPlanet,
            SolarSystem system,
            Empire attackerEmpire
    ) {
        if (targetPlanet == null || system == null) {
            return new BombardmentResult(bombType, false, system, targetPlanet, 0.0, "Invalid target");
        }

        String type = (bombType != null) ? bombType.toUpperCase() : KINETIC_DARTS;

        if (PLANET_CRACKER.equals(type)) {
            // Antimatter planet-cracker: completely obliterate the planet and spawn a shattered asteroid field
            List<Planet> remainingPlanets = new ArrayList<>(system.planets());
            remainingPlanets.removeIf(p -> p.id().equals(targetPlanet.id()));

            List<AsteroidBelt> updatedBelts = new ArrayList<>(system.asteroidBelts());
            updatedBelts.add(new AsteroidBelt(
                    "shattered_belt_" + targetPlanet.id(),
                    "Shattered Belt " + targetPlanet.name(),
                    "Remnants of planet " + targetPlanet.name(),
                    targetPlanet.resources(),
                    List.of()
            ));

            SolarSystem updatedSys = new SolarSystem(
                    system.id(), system.name(), system.description(),
                    system.x(), system.y(), system.z(),
                    system.sunMass(), system.sunDiameter(), system.sunColor(),
                    remainingPlanets, updatedBelts
            );

            return new BombardmentResult(
                    PLANET_CRACKER,
                    true,
                    updatedSys,
                    null,
                    calculateTotalPopulation(targetPlanet),
                    "Antimatter planet-cracker detonated. Celestial body obliterated into asteroid debris field."
            );
        } else if (FISSION_WARHEADS.equals(type)) {
            // Isotopic fission warheads: wipe 70% of population and irradiate planet
            double totalPop = calculateTotalPopulation(targetPlanet);
            double casualties = totalPop * 0.70;

            List<Population> decimatedPop = targetPlanet.populations().stream().map(pop -> {
                Map<Integer, Long> reducedAges = new HashMap<>();
                for (Map.Entry<Integer, Long> entry : pop.ageGroups().entrySet()) {
                    long surviving = (long) Math.round(entry.getValue() * 0.30);
                    if (surviving > 0) {
                        reducedAges.put(entry.getKey(), surviving);
                    }
                }
                return new Population(pop.raceId(), reducedAges);
            }).toList();

            Planet irradiatedPlanet = new Planet(
                    targetPlanet.id(), targetPlanet.name(), targetPlanet.description(),
                    targetPlanet.mass(), targetPlanet.gravity(),
                    targetPlanet.distance(), targetPlanet.inclination(),
                    targetPlanet.diameter(), targetPlanet.type(),
                    targetPlanet.atmosphere(), targetPlanet.hasLiquidWater(),
                    targetPlanet.waterLevel(), targetPlanet.resources(),
                    targetPlanet.moons(), decimatedPop
            );

            List<Planet> updatedPlanets = new ArrayList<>(system.planets());
            updatedPlanets.removeIf(p -> p.id().equals(targetPlanet.id()));
            updatedPlanets.add(irradiatedPlanet);

            SolarSystem updatedSys = new SolarSystem(
                    system.id(), system.name(), system.description(),
                    system.x(), system.y(), system.z(),
                    system.sunMass(), system.sunDiameter(), system.sunColor(),
                    updatedPlanets, system.asteroidBelts()
            );

            return new BombardmentResult(
                    FISSION_WARHEADS,
                    false,
                    updatedSys,
                    irradiatedPlanet,
                    casualties,
                    String.format("Fission warheads obliterated surface infrastructure. Estimated casualties: %.0f", casualties)
            );
        } else {
            // Kinetic dart pods: precision strikes on fortifications with minimal collateral
            double casualties = Math.min(50.0, calculateTotalPopulation(targetPlanet) * 0.02);

            return new BombardmentResult(
                    KINETIC_DARTS,
                    false,
                    system,
                    targetPlanet,
                    casualties,
                    "Kinetic dart pods destroyed planetary surface defense batteries. Collateral casualties minimal."
            );
        }
    }

    private double calculateTotalPopulation(Planet planet) {
        if (planet == null || planet.populations() == null) return 0.0;
        double total = 0.0;
        for (Population pop : planet.populations()) {
            for (long count : pop.ageGroups().values()) {
                total += count;
            }
        }
        return total;
    }
}
