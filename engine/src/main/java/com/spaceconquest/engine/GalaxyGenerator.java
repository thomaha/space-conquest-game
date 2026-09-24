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
        return generate(numSystems, GameStartScenario.PRE_SPACE_FLIGHT);
    }

    public List<SolarSystem> generate(int numSystems, GameStartScenario scenario) {
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
        return setupScenario(systems, scenario != null ? scenario : GameStartScenario.PRE_SPACE_FLIGHT);
    }

    public GameState generateGameState(int numSystems, GameStartScenario scenario) {
        return generateGameState(numSystems, 0, scenario);
    }

    public GameState generateGameState(int numSystems, int numAIEmpires, GameStartScenario scenario) {
        GameStartScenario activeScenario = scenario != null ? scenario : GameStartScenario.PRE_SPACE_FLIGHT;
        List<SolarSystem> rawSystems = generate(numSystems, activeScenario);
        
        List<Empire> allEmpires = new ArrayList<>();
        List<Corporation> allCorporations = new ArrayList<>();
        List<CommercialHub> allHubs = new ArrayList<>();
        List<com.spaceconquest.engine.industry.GeologicalDeposit> allDeposits = new ArrayList<>();
        List<com.spaceconquest.engine.industry.PowerGridState> allGrids = new ArrayList<>();
        List<com.spaceconquest.engine.economy.SystemEconomy> allEconomies = new ArrayList<>();

        // 1. Setup Player Empire
        Race humanRace = races.stream().filter(r -> r.id().equals("human")).findFirst().orElse(races.get(0));
        SolarSystem playerHome = prepareHomeSystem(rawSystems.get(0), humanRace, activeScenario);
        
        // Replace raw home with configured home in systems list
        List<SolarSystem> systems = new ArrayList<>(rawSystems);
        systems.set(0, playerHome);

        Empire playerEmpire = createEmpireForGenerator(
                "terran_confederation", "Terran Confederation", humanRace, playerHome, systems, activeScenario, true
        );
        allEmpires.add(playerEmpire);
        setupEmpireAssets(playerEmpire, playerHome, systems, activeScenario, allCorporations, allHubs, allDeposits, allGrids);

        // 2. Setup AI Empires
        int maxAI = Math.min(numAIEmpires, systems.size() - 1);
        List<Race> aiRaces = races.stream().filter(r -> !r.id().equals("human")).toList();
        if (aiRaces.isEmpty()) aiRaces = races;

        for (int i = 0; i < maxAI; i++) {
            SolarSystem rawAIHome = systems.get(i + 1);
            Race aiRace = aiRaces.get(i % aiRaces.size());
            
            SolarSystem aiHome = prepareHomeSystem(rawAIHome, aiRace, activeScenario);
            systems.set(i + 1, aiHome);

            String id = "ai_empire_" + (i + 1);
            String name = aiRace.name() + " Collective " + (i + 1);
            
            Empire aiEmpire = createEmpireForGenerator(id, name, aiRace, aiHome, systems, activeScenario, false);
            allEmpires.add(aiEmpire);
            setupEmpireAssets(aiEmpire, aiHome, systems, activeScenario, allCorporations, allHubs, allDeposits, allGrids);
        }

        // 3. Setup Economies for all controlled systems
        for (Empire emp : allEmpires) {
            for (String sysId : emp.controlledSystemIds()) {
                SolarSystem sys = systems.stream().filter(s -> s.id().equals(sysId)).findFirst().orElse(null);
                if (sys == null) continue;
                
                long sysPop = 0;
                for (Planet p : sys.planets()) {
                    for (Population pop : p.populations()) {
                        sysPop += pop.totalCount();
                    }
                }
                if (sysPop > 0) {
                    allEconomies.add(com.spaceconquest.engine.economy.SystemEconomy.createDefault(
                            sys.id(), emp.id(), sysPop
                    ));
                }
            }
        }

        GameState initial = GameState.builder()
                .status("RUNNING")
                .solarSystems(systems)
                .empires(allEmpires)
                .corporations(allCorporations)
                .commercialHubs(allHubs)
                .geologicalDeposits(allDeposits)
                .powerGrids(allGrids)
                .systemEconomies(allEconomies)
                .build();
        com.spaceconquest.engine.economy.PlanetaryMunicipalProcessor municipalProcessor =
                new com.spaceconquest.engine.economy.PlanetaryMunicipalProcessor();
        return initial.withPlanetaryBalanceSheets(municipalProcessor.processMunicipalFinances(initial).balanceSheets());
    }

    private Empire createEmpireForGenerator(String id, String name, Race race, SolarSystem home, List<SolarSystem> allSystems, GameStartScenario scenario, boolean isPlayer) {
        List<String> controlledIds = new ArrayList<>();
        controlledIds.add(home.id());

        if (scenario == GameStartScenario.BASIC_WARP) {
            List<SolarSystem> closest = findClosestNeighbors(home, allSystems, 2);
            for (SolarSystem neighbor : closest) {
                controlledIds.add(neighbor.id());
            }
        }

        double treasury = switch (scenario) {
            case PRE_SPACE_FLIGHT -> 50000.0;
            case ADVANCED_ROCKETRY -> 80000.0;
            case BASIC_WARP -> 150000.0;
        };

        return new Empire(
                id, name, race.id(), race.societyStructure(), treasury, 0.15, controlledIds,
                List.of(
                        new MinistryAssignment("ministry_industry_refining", "industrial_worker", 1.25),
                        new MinistryAssignment("ministry_agricultural_biosphere", "farmer", 1.25),
                        new MinistryAssignment("ministry_technology_application", "scientist", 1.25),
                        new MinistryAssignment("ministry_defense_logistics", "soldier", 1.25),
                        new MinistryAssignment("ministry_finance_commerce", "bureaucrat", 1.10)
                ),
                Map.of(home.id(), "gov_" + home.id() + "_" + id),
                scenario.startingTechnologies(),
                scenario.startingShipDesigns()
        );
    }

    private void setupEmpireAssets(Empire empire, SolarSystem homeSystem, List<SolarSystem> allSystems, GameStartScenario scenario, 
                                   List<Corporation> corps, List<CommercialHub> hubs, 
                                   List<com.spaceconquest.engine.industry.GeologicalDeposit> deposits, 
                                   List<com.spaceconquest.engine.industry.PowerGridState> grids) {
        
        Planet homePlanet = homeSystem.planets().stream()
                .filter(p -> !p.populations().isEmpty())
                .findFirst()
                .orElse(homeSystem.planets().isEmpty() ? null : homeSystem.planets().get(0));

        if (homePlanet != null) {
            hubs.add(new CommercialHub("hub_" + homePlanet.id(), homePlanet.id(), 0.05, 500000.0, 50000.0, 15.0, Map.of()));
            
            deposits.add(new com.spaceconquest.engine.industry.GeologicalDeposit(
                    "dep_" + homePlanet.id() + "_iron", homePlanet.id(), "refined_iron", 
                    1_000_000.0, 1_000_000.0, 1.2, true, empire.id()
            ));

            grids.add(new com.spaceconquest.engine.industry.PowerGridState(
                    homePlanet.id(), 5000.0, 2500.0, 2500.0, 10000.0, 5000.0, false
            ));

            List<String> transportShips = switch (scenario) {
                case PRE_SPACE_FLIGHT -> List.of();
                case ADVANCED_ROCKETRY -> List.of("cargo_freighter_01");
                case BASIC_WARP -> List.of("cargo_freighter_01", "cargo_freighter_02", "cargo_freighter_03");
            };
            List<String> miningShips = switch (scenario) {
                case PRE_SPACE_FLIGHT -> List.of();
                case ADVANCED_ROCKETRY -> List.of("mine_ship_alpha_1");
                case BASIC_WARP -> List.of("mine_ship_alpha_1", "mine_ship_alpha_2");
            };

            corps.add(new Corporation(
                    "corp_" + empire.id() + "_transport", empire.name() + " Transport", empire.id(),
                    homePlanet.id(), "TRANSPORT", scenario == GameStartScenario.BASIC_WARP ? 60000.0 : 20000.0,
                    List.of("cargo_terminal_" + homePlanet.id()), transportShips, List.of()
            ));
            corps.add(new Corporation(
                    "corp_" + empire.id() + "_extraction", empire.name() + " Extraction", empire.id(),
                    homePlanet.id(), "EXTRACTION", scenario == GameStartScenario.BASIC_WARP ? 50000.0 : 15000.0,
                    List.of(), miningShips, List.of()
            ));
        }

        if (scenario == GameStartScenario.ADVANCED_ROCKETRY || scenario == GameStartScenario.BASIC_WARP) {
            for (Planet p : homeSystem.planets()) {
                if (homePlanet != null && !p.id().equals(homePlanet.id()) && !p.populations().isEmpty()) {
                    hubs.add(new CommercialHub("hub_" + p.id(), p.id(), 0.05, 200000.0, 20000.0, 10.0, Map.of()));
                }
            }
        }

        if (scenario == GameStartScenario.BASIC_WARP) {
            for (SolarSystem sys : allSystems) {
                if (!sys.id().equals(homeSystem.id()) && empire.controlledSystemIds().contains(sys.id())) {
                    for (Planet p : sys.planets()) {
                        if (!p.populations().isEmpty()) {
                            hubs.add(new CommercialHub("hub_" + p.id(), p.id(), 0.05, 100000.0, 10000.0, 25.0, Map.of()));
                            break;
                        }
                    }
                }
            }
        }
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

    private record PlanetDimensions(double diameter, double mass) {}
    private record PlanetAtmosphereInfo(String atmosphere, String description, boolean hasLiquidWater, double waterLevel) {}

    private PlanetDimensions calculatePlanetDimensions(String type, ThreadLocalRandom random) {
        double diameter;
        double mass;
        if (type.equals("gas_giant") || type.equals("ice_giant")) {
            diameter = random.nextDouble(30000, 150000);
            mass = random.nextDouble(1.0e26, 2.0e27);
        } else if (type.equals("dwarf_planet") || type.equals("protoplanet") || type.equals("barren")) {
            diameter = random.nextDouble(2000, 6000); 
            mass = random.nextDouble(1.0e23, 1.0e24); 
        } else {
            diameter = random.nextDouble(5000, 20000); 
            mass = random.nextDouble(3.0e24, 1.5e25); 
        }
        return new PlanetDimensions(diameter, mass);
    }

    private PlanetAtmosphereInfo determineAtmosphereAndDescription(String type, boolean hasLiquidWater, double initialWaterLevel, ThreadLocalRandom random) {
        String description = "A " + type.replace("_", " ") + " planet.";
        String atmosphere = "none";
        boolean liquidWater = hasLiquidWater;
        double waterLevel = initialWaterLevel;

        switch (type) {
            case "ocean" -> {
                description += " It is covered in vast oceans.";
                atmosphere = "nitrogen_oxygen";
            }
            case "desert" -> {
                description += " It is a dry, sandy world.";
                atmosphere = "thin_nitrogen_co2";
            }
            case "lava" -> {
                description += " Its surface is molten rock.";
                atmosphere = "trace_silicate";
            }
            case "ice" -> {
                description += " It is covered in thick layers of ice.";
                atmosphere = "thin_nitrogen";
            }
            case "barren" -> {
                description += " It is a desolate, airless world of rock and metal.";
                atmosphere = "none";
            }
            case "toxic" -> {
                description += " It is shrouded in a thick, noxious atmosphere and crushing heat.";
                atmosphere = "dense_co2";
                liquidWater = false;
            }
            case "terrestrial" -> atmosphere = random.nextDouble() < 0.5 ? "nitrogen_oxygen" : "nitrogen_argon";
            case "gas_giant", "ice_giant" -> {
                atmosphere = type.equals("gas_giant") ? "hydrogen_helium" : "hydrogen_helium_methane";
                liquidWater = false;
            }
            case "dwarf_planet", "protoplanet" -> {
                atmosphere = random.nextDouble() < 0.1 ? "trace_nitrogen" : "none";
                liquidWater = false;
            }
        }
        return new PlanetAtmosphereInfo(atmosphere, description, liquidWater, waterLevel);
    }

    private Planet generatePlanet(String name, double distance, double hzInner, double hzOuter) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String type = PLANET_TYPES.get(random.nextInt(PLANET_TYPES.size()));
        
        PlanetDimensions dims = calculatePlanetDimensions(type, random);
        double diameter = dims.diameter();
        double mass = dims.mass();

        double gravity = (6.674e-11 * mass) / Math.pow(diameter * 500, 2);
        double inclination = random.nextDouble(-5, 5);

        List<String> resources = new ArrayList<>();
        if (type.equals("barren") || type.equals("lava") || type.equals("desert")) {
            if (random.nextDouble() < 0.6) resources.add("iron_ore");
        }
        resources.addAll(pickRandomResources(random.nextInt(1, 4)));
        
        boolean inHabitableZone = distance >= hzInner && distance <= hzOuter;
        boolean enoughMass = mass >= 1.0e24;
        boolean hasLiquidWater = inHabitableZone && enoughMass;
        double waterLevel = 0.0;

        if (hasLiquidWater) {
            if (random.nextDouble() < 0.7) {
                type = random.nextBoolean() ? "terrestrial" : "ocean";
            }
            waterLevel = type.equals("ocean") ? random.nextDouble(0.8, 1.0) : random.nextDouble(0.2, 0.8);
        } else if (distance < hzInner && (type.equals("terrestrial") || type.equals("ocean") || type.equals("ice"))) {
            type = "lava";
        } else if (distance > hzOuter && (type.equals("terrestrial") || type.equals("ocean") || type.equals("lava"))) {
            type = "ice";
            if (type.equals("ice")) waterLevel = random.nextDouble(0.0, 0.2);
        }

        int numMoons = (type.equals("gas_giant") || type.equals("ice_giant")) ? random.nextInt(2, 12) : random.nextInt(0, 4);
        List<Moon> moons = new ArrayList<>();
        for (int i = 0; i < numMoons; i++) {
            moons.add(generateMoon(name + " " + (char)('a' + i), diameter * random.nextDouble(2, 10), hzInner, hzOuter));
        }

        List<Population> populations = new ArrayList<>();
        if (List.of("terrestrial", "desert", "ocean").contains(type) && random.nextDouble() < 0.2 && hasLiquidWater) {
            populations.add(generatePopulation());
        }

        PlanetAtmosphereInfo atmoInfo = determineAtmosphereAndDescription(type, hasLiquidWater, waterLevel, random);

        return new Planet(
            name.toLowerCase().replace(" ", "_"),
            name,
            atmoInfo.description(),
            mass,
            gravity,
            distance,
            inclination,
            diameter,
            type,
            atmoInfo.atmosphere(),
            atmoInfo.hasLiquidWater(),
            atmoInfo.waterLevel(),
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

    private List<SolarSystem> setupScenario(List<SolarSystem> systems, GameStartScenario scenario) {
        if (systems.isEmpty()) {
            return systems;
        }

        Race primaryRace = races.stream().filter(r -> r.id().equals("human")).findFirst().orElse(races.get(0));
        SolarSystem rawHome = systems.get(0);
        SolarSystem configuredHome = prepareHomeSystem(rawHome, primaryRace, scenario);

        List<SolarSystem> result = new ArrayList<>();
        result.add(configuredHome);

        if (scenario == GameStartScenario.BASIC_WARP && systems.size() > 1) {
            List<SolarSystem> closest = findClosestNeighbors(rawHome, systems, 2);
            Set<String> closestIds = new HashSet<>();
            for (SolarSystem cs : closest) {
                closestIds.add(cs.id());
            }

            for (int i = 1; i < systems.size(); i++) {
                SolarSystem current = systems.get(i);
                if (closestIds.contains(current.id())) {
                    result.add(configureNeighborSystem(current, primaryRace));
                } else {
                    result.add(current);
                }
            }
        } else {
            for (int i = 1; i < systems.size(); i++) {
                result.add(systems.get(i));
            }
        }

        return result;
    }

    private SolarSystem prepareHomeSystem(SolarSystem home, Race primaryRace, GameStartScenario scenario) {
        List<Planet> planets = new ArrayList<>(home.planets());
        int homePlanetIndex = ensureHomePlanetIndex(planets, home.name(), primaryRace);

        List<Planet> configuredPlanets = new ArrayList<>();
        for (int i = 0; i < planets.size(); i++) {
            Planet p = planets.get(i);
            boolean isHome = (i == homePlanetIndex);
            configuredPlanets.add(configurePlanetForScenario(p, isHome, primaryRace, scenario, configuredPlanets.size()));
        }

        List<AsteroidBelt> configuredBelts = configureAsteroidBeltsForScenario(home.asteroidBelts(), primaryRace, scenario);

        return new SolarSystem(
                home.id(), home.name(), home.description(), home.x(), home.y(), home.z(),
                home.sunMass(), home.sunDiameter(), home.sunColor(), configuredPlanets, configuredBelts
        );
    }

    private int ensureHomePlanetIndex(List<Planet> planets, String homeName, Race primaryRace) {
        if (planets.isEmpty()) {
            planets.add(generateHomePlanet(homeName + " Prime", primaryRace));
            return 0;
        }

        for (int i = 0; i < planets.size(); i++) {
            Planet p = planets.get(i);
            if (p.hasLiquidWater() && (p.type().equals("terrestrial") || p.type().equals("ocean"))) {
                return i;
            }
        }

        Planet first = planets.get(0);
        planets.set(0, new Planet(
                first.id(), first.name(), "The cradle world of civilization.",
                5.97e24, 9.81, first.distance(), first.inclination(), 12742.0,
                "terrestrial", "nitrogen_oxygen", true, 0.71,
                first.resources(), first.moons(),
                List.of(generateColonyPopulation(primaryRace, 7_800_000_000L))
        ));
        return 0;
    }

    private Planet configurePlanetForScenario(Planet p, boolean isHomePlanet, Race primaryRace, GameStartScenario scenario, int configuredPlanetCount) {
        if (isHomePlanet) {
            return new Planet(
                    p.id(), p.name(), p.description(), p.mass(), p.gravity(), p.distance(),
                    p.inclination(), p.diameter(), p.type(), p.atmosphere(), p.hasLiquidWater(),
                    p.waterLevel(), p.resources(), p.moons(),
                    List.of(generateColonyPopulation(primaryRace, 7_800_000_000L))
            );
        }

        return switch (scenario) {
            case PRE_SPACE_FLIGHT -> {
                List<Moon> unpopulatedMoons = p.moons().stream()
                        .map(m -> new Moon(m.id(), m.name(), m.description(), m.mass(), m.gravity(), m.distance(), m.diameter(), m.atmosphere(), m.hasLiquidWater(), m.waterLevel(), m.resources(), List.of()))
                        .toList();
                yield new Planet(
                        p.id(), p.name(), p.description(), p.mass(), p.gravity(), p.distance(),
                        p.inclination(), p.diameter(), p.type(), p.atmosphere(), p.hasLiquidWater(),
                        p.waterLevel(), p.resources(), unpopulatedMoons, List.of()
                );
            }
            case ADVANCED_ROCKETRY -> {
                boolean isFriendly = List.of("terrestrial", "desert", "ocean", "ice", "barren").contains(p.type()) && !p.atmosphere().equals("dense_co2");
                List<Population> planetPops = (isFriendly && configuredPlanetCount <= 2)
                        ? List.of(generateColonyPopulation(primaryRace, 1_500_000L))
                        : List.of();

                List<Moon> moons = p.moons().stream()
                        .map(m -> (!m.resources().isEmpty() && m.diameter() > 1000)
                                ? new Moon(m.id(), m.name(), m.description(), m.mass(), m.gravity(), m.distance(), m.diameter(), m.atmosphere(), m.hasLiquidWater(), m.waterLevel(), m.resources(), List.of(generateColonyPopulation(primaryRace, 50_000L)))
                                : new Moon(m.id(), m.name(), m.description(), m.mass(), m.gravity(), m.distance(), m.diameter(), m.atmosphere(), m.hasLiquidWater(), m.waterLevel(), m.resources(), List.of()))
                        .toList();

                yield new Planet(
                        p.id(), p.name(), p.description(), p.mass(), p.gravity(), p.distance(),
                        p.inclination(), p.diameter(), p.type(), p.atmosphere(), p.hasLiquidWater(),
                        p.waterLevel(), p.resources(), moons, planetPops
                );
            }
            case BASIC_WARP -> {
                boolean habitable = !p.type().equals("lava") && !p.type().equals("gas_giant") && !p.type().equals("ice_giant");
                List<Population> planetPops = habitable
                        ? List.of(generateColonyPopulation(primaryRace, 15_000_000L))
                        : List.of();

                List<Moon> moons = p.moons().stream()
                        .map(m -> new Moon(m.id(), m.name(), m.description(), m.mass(), m.gravity(), m.distance(), m.diameter(), m.atmosphere(), m.hasLiquidWater(), m.waterLevel(), m.resources(), List.of(generateColonyPopulation(primaryRace, 200_000L))))
                        .toList();

                yield new Planet(
                        p.id(), p.name(), p.description(), p.mass(), p.gravity(), p.distance(),
                        p.inclination(), p.diameter(), p.type(), p.atmosphere(), p.hasLiquidWater(),
                        p.waterLevel(), p.resources(), moons, planetPops
                );
            }
        };
    }

    private List<AsteroidBelt> configureAsteroidBeltsForScenario(List<AsteroidBelt> belts, Race primaryRace, GameStartScenario scenario) {
        return belts.stream()
                .map(ab -> {
                    if (scenario == GameStartScenario.PRE_SPACE_FLIGHT) {
                        return new AsteroidBelt(ab.id(), ab.name(), ab.description(), ab.resources(), List.of());
                    } else if (scenario == GameStartScenario.ADVANCED_ROCKETRY) {
                        return new AsteroidBelt(ab.id(), ab.name(), ab.description(), ab.resources(), List.of(generateColonyPopulation(primaryRace, 25_000L)));
                    } else {
                        return new AsteroidBelt(ab.id(), ab.name(), ab.description(), ab.resources(), List.of(generateColonyPopulation(primaryRace, 100_000L)));
                    }
                })
                .toList();
    }

    private SolarSystem configureNeighborSystem(SolarSystem system, Race primaryRace) {
        List<Planet> planets = new ArrayList<>();
        boolean outpostPlaced = false;

        if (system.planets().isEmpty()) {
            planets.add(new Planet(
                    system.id() + "_outpost",
                    system.name() + " Outpost",
                    "A frontier outpost planet in a neighboring star system.",
                    5.0e24,
                    8.5,
                    1.5e8,
                    0.0,
                    10000.0,
                    "terrestrial",
                    "nitrogen_oxygen",
                    true,
                    0.5,
                    List.of("iron_ore", "silicon"),
                    List.of(),
                    List.of(generateColonyPopulation(primaryRace, 500_000L))
            ));
        } else {
            for (Planet p : system.planets()) {
                if (!outpostPlaced && !p.type().equals("lava") && !p.type().equals("gas_giant") && !p.type().equals("ice_giant")) {
                    planets.add(new Planet(
                            p.id(), p.name(), p.description(), p.mass(), p.gravity(), p.distance(),
                            p.inclination(), p.diameter(), p.type(), p.atmosphere(), p.hasLiquidWater(),
                            p.waterLevel(), p.resources(), p.moons(),
                            List.of(generateColonyPopulation(primaryRace, 500_000L))
                    ));
                    outpostPlaced = true;
                } else {
                    planets.add(p);
                }
            }
            if (!outpostPlaced) {
                Planet first = system.planets().get(0);
                planets.set(0, new Planet(
                        first.id(), first.name(), first.description(), first.mass(), first.gravity(), first.distance(),
                        first.inclination(), first.diameter(), first.type(), first.atmosphere(), first.hasLiquidWater(),
                        first.waterLevel(), first.resources(), first.moons(),
                        List.of(generateColonyPopulation(primaryRace, 500_000L))
                ));
            }
        }

        return new SolarSystem(
                system.id(), system.name(), system.description(), system.x(), system.y(), system.z(),
                system.sunMass(), system.sunDiameter(), system.sunColor(), planets, system.asteroidBelts()
        );
    }

    private Planet generateHomePlanet(String name, Race race) {
        return new Planet(
                name.toLowerCase().replace(" ", "_"),
                name,
                "The cradle world of civilization.",
                5.97e24,
                9.81,
                1.496e8,
                0.0,
                12742.0,
                "terrestrial",
                "nitrogen_oxygen",
                true,
                0.71,
                List.of("iron_ore", "silicon", "liquid_water"),
                List.of(new Moon("moon_" + name.toLowerCase().replace(" ", "_"), "Luna", "A natural satellite.", 7.34e22, 1.62, 384400.0, 3474.0, "none", false, 0.0, List.of("silicates", "titanium"), List.of())),
                List.of(generateColonyPopulation(race, 7_800_000_000L))
        );
    }

    private Population generateColonyPopulation(Race race, long count) {
        Map<Integer, Long> ageGroups = new HashMap<>();
        ageGroups.put(0, (long) (count * 0.25));
        ageGroups.put(20, (long) (count * 0.50));
        ageGroups.put(40, (long) (count * 0.25));
        return new Population(race.id(), ageGroups);
    }

    private List<SolarSystem> findClosestNeighbors(SolarSystem origin, List<SolarSystem> systems, int count) {
        return systems.stream()
                .filter(s -> !s.id().equals(origin.id()))
                .sorted(Comparator.comparingDouble(s -> distance(origin, s)))
                .limit(count)
                .toList();
    }

    private double distance(SolarSystem a, SolarSystem b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Generates deep-space anomalies scattered across non-home solar systems.
     */
    public List<com.spaceconquest.engine.galaxy.Anomaly> generateAnomalies(List<SolarSystem> systems, int anomalyCount) {
        if (systems == null || systems.isEmpty() || anomalyCount <= 0) {
            return List.of();
        }

        List<com.spaceconquest.engine.galaxy.Anomaly> anomalies = new ArrayList<>();
        String[] types = {
                com.spaceconquest.engine.galaxy.Anomaly.TYPE_DERELICT_STARSHIP,
                com.spaceconquest.engine.galaxy.Anomaly.TYPE_ANCIENT_RUINS,
                com.spaceconquest.engine.galaxy.Anomaly.TYPE_UNSTABLE_WORMHOLE,
                com.spaceconquest.engine.galaxy.Anomaly.TYPE_STELLAR_PHENOMENON
        };

        for (int i = 0; i < anomalyCount; i++) {
            SolarSystem sys = systems.get(ThreadLocalRandom.current().nextInt(systems.size()));
            String type = types[i % types.length];
            String id = "anomaly_" + (i + 1) + "_" + sys.id();
            String title = switch (type) {
                case com.spaceconquest.engine.galaxy.Anomaly.TYPE_DERELICT_STARSHIP -> "Derelict Alien Cruiser";
                case com.spaceconquest.engine.galaxy.Anomaly.TYPE_ANCIENT_RUINS -> "Precursor Megalith Ruins";
                case com.spaceconquest.engine.galaxy.Anomaly.TYPE_UNSTABLE_WORMHOLE -> "Subspace Rift Anomaly";
                default -> "High-Energy Coronal Loop";
            };
            String desc = "Subspace sensors detect unusual energy emissions originating within the " + sys.name() + " system.";
            double difficulty = 20.0 + (i * 10.0);
            String rewardType = switch (i % 3) {
                case 0 -> com.spaceconquest.engine.galaxy.Anomaly.REWARD_RESEARCH_POINTS;
                case 1 -> com.spaceconquest.engine.galaxy.Anomaly.REWARD_CREDITS;
                default -> com.spaceconquest.engine.galaxy.Anomaly.REWARD_TECH_UNLOCK;
            };
            double rewardAmt = rewardType.equals(com.spaceconquest.engine.galaxy.Anomaly.REWARD_CREDITS) ? 25000.0 : 500.0;
            String techId = rewardType.equals(com.spaceconquest.engine.galaxy.Anomaly.REWARD_TECH_UNLOCK) ? "quantum_communication" : "";

            anomalies.add(new com.spaceconquest.engine.galaxy.Anomaly(
                    id, sys.id(), type, title, desc, difficulty, false, rewardType, rewardAmt, techId
            ));
        }

        return anomalies;
    }

    /**
     * Generates the hyperlane warp network connecting all systems in the galaxy.
     */
    public List<com.spaceconquest.engine.galaxy.WarpLane> generateWarpNetwork(List<SolarSystem> systems) {
        return new com.spaceconquest.engine.galaxy.WarpNetwork().generateNetwork(systems, 80.0);
    }
}
