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
        "Atria", "Alhena", "Peacock", "Alsephina", "Mirzam", "Alphard", "Algieba", "Hamal", "Diphda", "Nunki",
        "Menkent", "Mirach", "Alpheratz", "Saiph", "Kochab", "Ras Alhague", "Algol", "Almach", "Denebola", "Alphekka",
        "Aludra", "Gienah", "Markab", "Menkar", "Alnitak", "Mintaka", "Polaris", "Eltanin", "Kaus Media", "Schedar",
        "Naos", "Almuredin", "Kaus Borealis", "Kornephoros", "Zubeneschamali", "Caph", "Dschubba", "Muphrid", "Albireo", "Scheat",
        "Alnair", "Alcyone", "Alderamin", "Kraz", "Markab", "Menkar", "Algenib", "Alkarab", "Alsuhail", "Ankaa",
        "Arneb", "Ascella", "Aspidiske", "Asterope", "Atik", "Atlas", "Auva", "Azha", "Baten Kaitos", "Beid",
        "Botein", "Canes Venatici", "Celaeno", "Chara", "Cor Caroli", "Cujam", "Curtiss", "Dabih", "Deneb Algedi", "Dschubba",
        "Edasich", "Electra", "Enif", "Errai", "Fawaris", "Fulu", "Furud", "Gemma", "Gianfar", "Gomeisa",
        "Graffias", "Grumium", "Haedi", "Hamal", "Hassaleh", "Hatysa", "Helvetios", "Heze", "Homam", "Hyadum",
        "Iklil", "Imai", "Intercrus", "Izar", "Jabbah", "Kaffaljidhma", "Kajam", "Kaus Borealis", "Kaus Media", "Keid",
        "Kitalpha", "Kraz", "Kuma", "Kurhah", "La Superba", "Larawag", "Lesath", "Libertas", "Lich", "Lilii Borea",
        "Maia", "Marfik", "Matar", "Mebsuta", "Megrez", "Meissa", "Mekbuda", "Meleph", "Menkalinan", "Menkar",
        "Menkib", "Merak", "Merga", "Merope", "Mesarthim", "Miaplacidus", "Mimosa", "Minchir", "Minelauva", "Mintaka",
        "Mira", "Mirach", "Miram", "Mirfak", "Mirzam", "Misam", "Mizar", "Mothallah", "Muliphein", "Muphrid",
        "Muscida", "Nair al Saif", "Nan He", "Nashira", "Nekkar", "Nihal", "Nunki", "Nusakan", "Okab", "Paivine",
        "Pherkad", "Phurad", "Piautos", "Pikent", "Polaris Australis", "Polis", "Porrima", "Praecipua", "Prima Hyadum", "Propus",
        "Ran", "Rana", "Rasalas", "Rasalgethi", "Rasalhague", "Rastaban", "Regor", "Regulus", "Revati", "Rigel Kentaurus",
        "Rotanev", "Ruchbah", "Rukbat", "Sabik", "Sadachbia", "Sadalbari", "Sadalmelik", "Sadalsuud", "Sadr", "Saiph",
        "Salm", "Sargas", "Sarin", "Sceptrum", "Scheat", "Schedar", "Secunda Hyadum", "Segin", "Seginus", "Sham",
        "Shaula", "Shelyak", "Sheratan", "Sirius", "Situla", "Skat", "Spica", "Sualocin", "Subra", "Suhail",
        "Sulafat", "Syrma", "Tabit", "Taiyangshou", "Talitha", "Tania Borealis", "Tania Australis", "Tarazed", "Taygeta", "Tegmine",
        "Tejat", "Terebellum", "Theemin", "Thuban", "Tiaki", "Tianguan", "Tianyi", "Toliman", "Torcular", "Tureis",
        "Unukalhai", "Unurgunite", "Uruk", "Vega", "Veritate", "Vindemiatrix", "Wasat", "Wazn", "Wezen", "Xamidimura",
        "Yed Prior", "Yed Posterior", "Yildun", "Zaniah", "Zaurak", "Zavijava", "Zhang", "Zibal", "Zosma", "Zubenelgenubi",
        "Zubenelhakrabi", "Zubeneschamali"
    );

    private static final List<String> PLANET_TYPES = List.of(
        "terrestrial", "desert", "ocean", "lava", "ice", "gas_giant", "ice_giant", "dwarf_planet", "protoplanet",
        "barren", "toxic"
    );
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
        List<String> availableNames = new ArrayList<>(new LinkedHashSet<>(STAR_NAMES));
        Collections.shuffle(availableNames);

        for (int i = 0; i < numSystems; i++) {
            String name;
            if (i < availableNames.size()) {
                name = availableNames.get(i);
            } else {
                // Procedural fallback: e.g., HD 123456 or HIP 12345
                name = (ThreadLocalRandom.current().nextBoolean() ? "HD " : "HIP ") + 
                       ThreadLocalRandom.current().nextInt(1000, 999999);
            }
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
        double massRatio = sunMass / SOLAR_MASS;
        
        // Estimate luminosity (L ~ M^3.5 for main sequence)
        // For Giants and White Dwarfs this is a rough approximation, but good enough for a game
        double luminosity = Math.pow(massRatio, 3.5);
        if (selectedProp.spectralType().contains("Giant")) luminosity *= 100;
        if (selectedProp.spectralType().contains("White Dwarf")) luminosity *= 0.01;
        if (selectedProp.spectralType().contains("Black Hole")) luminosity = 0;

        // Habitable zone range (AU): sqrt(L) * 0.95 to sqrt(L) * 1.37
        // Convert to km (1 AU = 1.496e8 km)
        double hzInner = Math.sqrt(luminosity) * 0.95 * 1.496e8;
        double hzOuter = Math.sqrt(luminosity) * 1.37 * 1.496e8;
        
        // Adjust diameter and description for special star types
        String spectralType = selectedProp.spectralType();
        double sunDiameter;
        String description = "A randomly generated solar system.";
        
        if (spectralType.contains("Giant")) {
            // Giants are much larger than main sequence for same mass
            sunDiameter = 1.3927e6 * Math.pow(massRatio, 0.8) * random.nextDouble(10, 100);
            description = "A solar system with a massive " + spectralType + ".";
        } else if (spectralType.contains("White Dwarf")) {
            // White dwarfs are very small (~Earth size)
            sunDiameter = random.nextDouble(8000, 15000); // km
            description = "A solar system centered around a dense White Dwarf.";
        } else if (spectralType.contains("Black Hole")) {
            // Black hole "diameter" for visualization
            sunDiameter = random.nextDouble(20, 100); // Very small but detectable
            description = "A gravitational anomaly: a stellar-mass Black Hole.";
        } else {
            // Main sequence stars: R ~ M^0.8
            sunDiameter = 1.3927e6 * Math.pow(massRatio, 0.8);
            description = "A solar system with a " + spectralType + " star.";
        }

        String sunColor = selectedProp.color();
        
        // Handle double stars
        boolean isBinary = random.nextDouble() < selectedProp.isBinaryProbability();
        if (isBinary) {
            name += " AB"; // Convention for binary systems
            description += " This is a binary star system.";
        }

        List<Planet> planets = new ArrayList<>();
        // Only generate planets if probability allows
        if (random.nextDouble() < selectedProp.hasPlanetsProbability()) {
            int numPlanets = random.nextInt(1, 11);
            double currentDistance = 5.0e7 * Math.pow(massRatio, 0.5); 
            for (int i = 0; i < numPlanets; i++) {
                currentDistance *= random.nextDouble(1.3, 2.0); 
                planets.add(generatePlanet(name + " " + (i + 1), currentDistance, hzInner, hzOuter));
            }
        }

        return new SolarSystem(
            "sys_" + index,
            name,
            description,
            x, y, z,
            sunMass,
            sunDiameter,
            sunColor,
            planets,
            List.of()
        );
    }

    private Planet generatePlanet(String name, double distance, double hzInner, double hzOuter) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String type = PLANET_TYPES.get(random.nextInt(PLANET_TYPES.size()));
        
        double diameter;
        double mass;
        if (type.equals("gas_giant") || type.equals("ice_giant")) {
            diameter = random.nextDouble(30000, 150000);
            mass = random.nextDouble(1.0e26, 2.0e27);
        } else if (type.equals("dwarf_planet") || type.equals("protoplanet") || type.equals("barren")) {
            diameter = random.nextDouble(2000, 6000); 
            mass = random.nextDouble(1.0e23, 1.0e24); 
        } else { // terrestrial types
            diameter = random.nextDouble(5000, 20000); 
            mass = random.nextDouble(3.0e24, 1.5e25); 
        }

        double gravity = (6.674e-11 * mass) / Math.pow(diameter * 500, 2);
        double inclination = random.nextDouble(-5, 5);

        // Preliminary resource logic
        List<String> resources = new ArrayList<>();
        if (type.equals("barren") || type.equals("lava") || type.equals("desert")) {
            if (random.nextDouble() < 0.6) resources.add("iron_ore");
        }
        resources.addAll(pickRandomResources(random.nextInt(1, 4)));
        
        // Liquid water criteria: In HZ, enough mass to hold atmosphere, and not too toxic
        boolean inHabitableZone = distance >= hzInner && distance <= hzOuter;
        boolean enoughMass = mass >= 1.0e24; // Mars is ~0.6e24, Earth is ~6e24
        boolean hasLiquidWater = inHabitableZone && enoughMass;
        double waterLevel = 0.0;

        // Force type/atmosphere adjustment for HZ planets
        if (hasLiquidWater) {
            if (random.nextDouble() < 0.7) {
                type = random.nextBoolean() ? "terrestrial" : "ocean";
            }
            waterLevel = type.equals("ocean") ? random.nextDouble(0.8, 1.0) : random.nextDouble(0.2, 0.8);
        } else if (distance < hzInner && (type.equals("terrestrial") || type.equals("ocean") || type.equals("ice"))) {
            type = "lava"; // Too hot
        } else if (distance > hzOuter && (type.equals("terrestrial") || type.equals("ocean") || type.equals("lava"))) {
            type = "ice"; // Too cold
            if (type.equals("ice")) waterLevel = random.nextDouble(0.0, 0.2); // Ice worlds might have some frozen water
        }

        int numMoons = random.nextInt(0, 4);
        if (type.equals("gas_giant") || type.equals("ice_giant")) numMoons = random.nextInt(2, 12);
        
        List<Moon> moons = new ArrayList<>();
        for (int i = 0; i < numMoons; i++) {
            moons.add(generateMoon(name + " " + (char)('a' + i), diameter * random.nextDouble(2, 10), hzInner, hzOuter));
        }

        List<Population> populations = new ArrayList<>();
        // Only certain types can be naturally inhabited
        List<String> habitableTypes = List.of("terrestrial", "desert", "ocean");
        if (habitableTypes.contains(type) && random.nextDouble() < 0.2 && hasLiquidWater) {
            populations.add(generatePopulation());
        }

        String description = "A " + type.replace("_", " ") + " planet.";
        String atmosphere = "none";
        if (type.equals("ocean")) {
            description += " It is covered in vast oceans.";
            atmosphere = "nitrogen_oxygen";
        }
        if (type.equals("desert")) {
            description += " It is a dry, sandy world.";
            atmosphere = "thin_nitrogen_co2";
        }
        if (type.equals("lava")) {
            description += " Its surface is molten rock.";
            atmosphere = "trace_silicate";
        }
        if (type.equals("ice")) {
            description += " It is covered in thick layers of ice.";
            atmosphere = "thin_nitrogen";
        }
        if (type.equals("barren")) {
            description += " It is a desolate, airless world of rock and metal.";
            atmosphere = "none";
        }
        if (type.equals("toxic")) {
            description += " It is shrouded in a thick, noxious atmosphere and crushing heat.";
            atmosphere = "dense_co2";
            hasLiquidWater = false; // Even if in HZ, toxic is too hot/crushing
        }
        if (type.equals("terrestrial")) {
            atmosphere = random.nextDouble() < 0.5 ? "nitrogen_oxygen" : "nitrogen_argon";
        }
        if (type.equals("gas_giant") || type.equals("ice_giant")) {
            atmosphere = type.equals("gas_giant") ? "hydrogen_helium" : "hydrogen_helium_methane";
            hasLiquidWater = false; // No surface for liquid water
        }
        if (type.equals("dwarf_planet") || type.equals("protoplanet")) {
            atmosphere = random.nextDouble() < 0.1 ? "trace_nitrogen" : "none";
            hasLiquidWater = false; // Too small
        }

        return new Planet(
            name.toLowerCase().replace(" ", "_"),
            name,
            description,
            mass,
            gravity,
            distance,
            inclination,
            diameter,
            type,
            atmosphere,
            hasLiquidWater,
            waterLevel,
            resources,
            moons,
            populations
        );
    }

    private Moon generateMoon(String name, double distance, double hzInner, double hzOuter) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double diameter = random.nextDouble(500, 4000);
        double mass = random.nextDouble(1.0e22, 1.0e23);
        double gravity = (6.674e-11 * mass) / Math.pow(diameter * 500, 2);
        List<String> resources = pickRandomResources(random.nextInt(1, 3));
        
        boolean hasLiquidWater = false; // Moons rarely have liquid water on surface unless massive or special
        double waterLevel = 0.0;
        // For gameplay, let's say very large moons in HZ can have it occasionally
        if (diameter > 3000 && distance >= hzInner && distance <= hzOuter && random.nextDouble() < 0.1) {
            hasLiquidWater = true;
            waterLevel = random.nextDouble(0.1, 0.5);
        }

        String atmosphere = "none";
        if (diameter > 3000 && random.nextDouble() < 0.1) {
            atmosphere = "trace_nitrogen";
        }

        return new Moon(
            name.toLowerCase().replace(" ", "_"),
            name,
            "A moon.",
            mass,
            gravity,
            distance,
            diameter,
            atmosphere,
            hasLiquidWater,
            waterLevel,
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
