package com.spaceconquest.engine;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class GalaxyGenerator {

    private static final List<String> STAR_NAMES = List.of(
        "Alpha Centauri", "Sirius", "Canopus", "Arcturus", "Vega", "Capella", "Rigel", "Procyon", "Achernar", "Betelgeuse",
        "Hadar", "Altair", "Acrux", "Aldebaran", "Spica", "Antares", "Pollux", "Fomalhaut", "Deneb", "Mimosa",
        "Regulus", "Adhara", "Castor", "Gacrux", "Shaula", "Bellatrix", "Elnath", "Miaplacidus", "Alnilam", "Alnair",
        "Alioth", "Alnitak", "Dubhe", "Mirfak", "Wezen", "Sargas", "Kaus Australis", "Avior", "Alkaid", "Menkalinan",
        "Atria", "Alhena", "Peacock", "Alsephina", "Mirzam", "Alphard", "Algieba", "Hamal", "Diphda", "Nunki"
    );

    private static final List<String> PLANET_TYPES = List.of("terrestrial", "gas_giant", "ice_giant", "planetoid");
    private static final double SOLAR_MASS = 1.989e30;

    private final List<Material> naturalMaterials;
    private final List<Race> races;
    private final List<StarProperty> starProperties;

    public GalaxyGenerator() throws IOException {
        this.naturalMaterials = DataModelLoader.loadMaterials().stream()
                .filter(Material::foundInNature)
                .toList();
        this.races = DataModelLoader.loadRaces();
        this.starProperties = DataModelLoader.loadStarProperties();
    }

    public List<SolarSystem> generate(int numSystems) {
        List<SolarSystem> systems = new ArrayList<>();
        List<String> availableNames = new ArrayList<>(STAR_NAMES);
        Collections.shuffle(availableNames);

        for (int i = 0; i < numSystems; i++) {
            String name = (i < availableNames.size()) ? availableNames.get(i) : "System " + (i + 1);
            systems.add(generateSolarSystem(name, i));
        }
        return systems;
    }

    private SolarSystem generateSolarSystem(String name, int index) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        
        // Coordinates in light-years, spread them out
        double x = random.nextDouble(-10, 10);
        double y = random.nextDouble(-10, 10);
        double z = random.nextDouble(-2, 2);

        // Weighted random selection for star type based on probability
        double p = random.nextDouble();
        double cumulativeProbability = 0.0;
        StarProperty selectedProp = starProperties.get(starProperties.size() - 1); // Default to M-type
        for (StarProperty prop : starProperties) {
            cumulativeProbability += prop.probability();
            if (p <= cumulativeProbability) {
                selectedProp = prop;
                break;
            }
        }

        double sunMass = random.nextDouble(selectedProp.minMassSolar(), selectedProp.maxMassSolar()) * SOLAR_MASS;
        // Diameter roughly scales with mass for main sequence stars: R ~ M^0.8
        double massRatio = sunMass / SOLAR_MASS;
        double sunDiameter = 1.3927e6 * Math.pow(massRatio, 0.8);
        String sunColor = selectedProp.color();

        int numPlanets = random.nextInt(3, 11);
        List<Planet> planets = new ArrayList<>();
        
        double currentDistance = 5.0e7 * Math.pow(massRatio, 0.5); // Spacing scales with luminosity/mass
        for (int i = 0; i < numPlanets; i++) {
            currentDistance *= random.nextDouble(1.3, 2.0); // Spacing
            planets.add(generatePlanet(name + " " + (i + 1), currentDistance));
        }

        return new SolarSystem(
            "sys_" + index,
            name,
            "A randomly generated solar system.",
            x, y, z,
            sunMass,
            sunDiameter,
            sunColor,
            planets,
            List.of() // Asteroid belts could be added too
        );
    }

    private Planet generatePlanet(String name, double distance) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String type = PLANET_TYPES.get(random.nextInt(PLANET_TYPES.size()));
        
        double diameter;
        double mass;
        if (type.equals("gas_giant") || type.equals("ice_giant")) {
            diameter = random.nextDouble(30000, 150000);
            mass = random.nextDouble(1.0e26, 2.0e27);
        } else if (type.equals("planetoid")) {
            diameter = random.nextDouble(500, 3000);
            mass = random.nextDouble(1.0e22, 5.0e23);
        } else { // terrestrial
            diameter = random.nextDouble(5000, 15000);
            mass = random.nextDouble(3.0e24, 8.0e24);
        }

        double gravity = (6.674e-11 * mass) / Math.pow(diameter * 500, 2);
        double inclination = random.nextDouble(-5, 5);

        List<String> resources = pickRandomResources(random.nextInt(1, 5));
        
        int numMoons = random.nextInt(0, 4);
        if (type.equals("gas_giant")) numMoons = random.nextInt(2, 8);
        
        List<Moon> moons = new ArrayList<>();
        for (int i = 0; i < numMoons; i++) {
            moons.add(generateMoon(name + " " + (char)('a' + i), diameter * random.nextDouble(2, 10)));
        }

        List<Population> populations = new ArrayList<>();
        if (type.equals("terrestrial") && random.nextDouble() < 0.2) {
            populations.add(generatePopulation());
        }

        return new Planet(
            name.toLowerCase().replace(" ", "_"),
            name,
            "A " + type + " planet.",
            mass,
            gravity,
            distance,
            inclination,
            diameter,
            type,
            resources,
            moons,
            populations
        );
    }

    private Moon generateMoon(String name, double distance) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double diameter = random.nextDouble(500, 4000);
        double mass = random.nextDouble(1.0e22, 1.0e23);
        double gravity = (6.674e-11 * mass) / Math.pow(diameter * 500, 2);
        List<String> resources = pickRandomResources(random.nextInt(1, 3));

        return new Moon(
            name.toLowerCase().replace(" ", "_"),
            name,
            "A moon.",
            mass,
            gravity,
            distance,
            diameter,
            resources,
            List.of()
        );
    }

    private List<String> pickRandomResources(int count) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Set<String> selected = new HashSet<>();
        int attempts = 0;
        while (selected.size() < count && attempts < 10) {
            selected.add(naturalMaterials.get(random.nextInt(naturalMaterials.size())).id());
            attempts++;
        }
        return new ArrayList<>(selected);
    }

    private Population generatePopulation() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Race race = races.get(random.nextInt(races.size()));
        Map<Integer, Long> ageGroups = new HashMap<>();
        long totalPop = random.nextLong(100_000, 5_000_000_000L);
        // Distribute population across some age groups
        ageGroups.put(0, totalPop / 4);
        ageGroups.put(20, totalPop / 2);
        ageGroups.put(40, totalPop / 4);
        
        return new Population(race.id(), ageGroups);
    }
}
