package com.spaceconquest.engine.scenario;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameStartScenario;
import com.spaceconquest.engine.MarketOrder;
import com.spaceconquest.engine.Moon;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.Population;
import com.spaceconquest.engine.PopulationProcessor;
import com.spaceconquest.engine.Race;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.demographics.ColonyFocus;
import com.spaceconquest.engine.economy.HouseholdAccount;
import com.spaceconquest.engine.economy.HouseholdEmployment;
import com.spaceconquest.engine.economy.HouseholdWellbeing;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.industry.PowerBillingProcessor;
import com.spaceconquest.engine.industry.PowerProcessor;
import com.spaceconquest.engine.market.MarketProcessor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Creates opening assets for generated campaigns without simulating a trading day. */
public class StartingEconomySeeder {
    private static final double HOUSEHOLD_CREDITS_PER_PERSON = 20.0;
    private static final double CORPORATE_CREDITS_PER_PERSON = 0.5;
    private static final int INITIAL_MARKET_DAYS = 60;
    private static final int LIVE_CHAIN_RESERVE_DAYS = 7;
    private final Map<String, Race> races = new HashMap<>();
    private final PopulationProcessor populationProcessor = new PopulationProcessor();

    public StartingEconomySeeder(List<Race> races) {
        for (Race race : races) this.races.put(race.id(), race);
    }

    public record HomeAssets(List<Corporation> corporations, List<IndustrialFacility> facilities) {}

    public HomeAssets createHomeAssets(Empire empire, Planet home, GameStartScenario scenario) {
        long population = home.populations().stream().mapToLong(Population::totalCount).sum();
        int workers = 100;
        int tier = switch (scenario) {
            case PRE_SPACE_FLIGHT -> 2;
            case ADVANCED_ROCKETRY -> 3;
            case BASIC_WARP -> 4;
        };
        Map<String, Integer> jobs = planJobs(home, scenario, tier);
        String prefix = "fac_" + empire.id() + "_" + home.id() + "_";
        String corporatePrefix = "corp_" + empire.id() + "_";
        String transportId = corporatePrefix + "transport";
        String extractionId = corporatePrefix + "extraction";
        String agricultureId = corporatePrefix + "agriculture";
        String manufacturingId = corporatePrefix + "manufacturing";

        List<IndustrialFacility> facilities = new ArrayList<>(List.of(
                facility(prefix + "solar", home, "solar_power", empire.id(), IndustrialFacility.PUBLIC_STATE, tier, workers, "technician"),
                facility(prefix + "fission", home, "nuclear_power_app", empire.id(), IndustrialFacility.PUBLIC_STATE, tier, workers, "engineer"),
                facility(prefix + "fission_aux", home, "nuclear_power_app", empire.id(), IndustrialFacility.PUBLIC_STATE, tier, workers, "engineer"),
                facility(prefix + "combustion", home, "combustion_power", empire.id(), IndustrialFacility.PUBLIC_STATE, tier, workers, "technician"),
                facility(prefix + "freight", home, "cargo_terminal", transportId, IndustrialFacility.PRIVATE_CORPORATE, tier, workers / 2, "technician"),
                facility(prefix + "mine", home, "mining_outpost", extractionId, IndustrialFacility.PRIVATE_CORPORATE, tier, workers, "miner"),
                facility(prefix + "nitrates", home, "nitrates_mining", extractionId, IndustrialFacility.PRIVATE_CORPORATE, tier, jobs.get("nitrates"), "miner"),
                facility(prefix + "phosphates", home, "phosphates_mining", extractionId, IndustrialFacility.PRIVATE_CORPORATE, tier, jobs.get("phosphates"), "miner"),
                facility(prefix + "potash", home, "potash_mining", extractionId, IndustrialFacility.PRIVATE_CORPORATE, tier, jobs.get("potash"), "miner"),
                facility(prefix + "aluminum_ore", home, "aluminum_ore_mining", extractionId, IndustrialFacility.PRIVATE_CORPORATE, tier, jobs.get("aluminum_mine"), "miner"),
                facility(prefix + "copper_ore", home, "copper_ore_mining", extractionId, IndustrialFacility.PRIVATE_CORPORATE, tier, jobs.get("copper_mine"), "miner"),
                facility(prefix + "silicates", home, "silicates_mining", extractionId, IndustrialFacility.PRIVATE_CORPORATE, tier, jobs.get("silicates"), "miner"),
                facility(prefix + "carbon", home, "carbon_mining", extractionId, IndustrialFacility.PRIVATE_CORPORATE, tier, jobs.get("carbon"), "miner"),
                facility(prefix + "uranium_mine", home, "uranium_ore_mining", extractionId,
                        IndustrialFacility.PRIVATE_CORPORATE, tier, workers, "miner"),
                facility(prefix + "thorium_mine", home, "thorium_ore_mining", extractionId,
                        IndustrialFacility.PRIVATE_CORPORATE, tier, workers, "miner"),
                facility(prefix + "rare_earths", home, "rare_earth_fluorides_mining", extractionId, IndustrialFacility.PRIVATE_CORPORATE, tier, jobs.get("rare_mine"), "miner"),
                facility(prefix + "water", home, "surface_water_treatment", agricultureId, IndustrialFacility.PRIVATE_CORPORATE, tier, jobs.get("water"), "technician"),
                facility(prefix + "hydrogen", home, "water_electrolysis", manufacturingId,
                        IndustrialFacility.PRIVATE_CORPORATE, tier, workers / 2, "technician"),
                facility(prefix + "hydrocarbon_mine", home, "hydrocarbons_mining", extractionId,
                        IndustrialFacility.PRIVATE_CORPORATE, tier, workers / 2, "miner"),
                facility(prefix + "rp1", home, "rp1_refining", manufacturingId,
                        IndustrialFacility.PRIVATE_CORPORATE, tier, workers / 2, "industrial_worker"),
                facility(prefix + "lox", home, "oxygen_liquefaction", manufacturingId,
                        IndustrialFacility.PRIVATE_CORPORATE, tier, workers / 2, "industrial_worker"),
                facility(prefix + "uranium_refinery", home, "uranium_refining", manufacturingId,
                        IndustrialFacility.PRIVATE_CORPORATE, tier, workers, "industrial_worker"),
                facility(prefix + "thorium_refinery", home, "thorium_refining", manufacturingId,
                        IndustrialFacility.PRIVATE_CORPORATE, tier, workers, "industrial_worker"),
                facility(prefix + "farm", home, "industrial_soil_cultivation", agricultureId, IndustrialFacility.PRIVATE_CORPORATE, tier, jobs.get("farm"), "farmer"),
                facility(prefix + "fiber", home, "industrial_biomass_cultivation", agricultureId, IndustrialFacility.PRIVATE_CORPORATE, tier, jobs.get("fiber"), "industrial_worker"),
                facility(prefix + "aluminum", home, "aluminum_refining", manufacturingId, IndustrialFacility.PRIVATE_CORPORATE, tier, jobs.get("aluminum"), "industrial_worker"),
                facility(prefix + "copper", home, "copper_refining", manufacturingId, IndustrialFacility.PRIVATE_CORPORATE, tier, jobs.get("copper"), "industrial_worker"),
                facility(prefix + "silicon", home, "silicon_refining", manufacturingId, IndustrialFacility.PRIVATE_CORPORATE, tier, jobs.get("silicon"), "industrial_worker"),
                facility(prefix + "rare_refinery", home, "rare_earth_refining", manufacturingId, IndustrialFacility.PRIVATE_CORPORATE, tier, jobs.get("rare_refinery"), "industrial_worker"),
                facility(prefix + "biomass", home, "biomass_processing", manufacturingId, IndustrialFacility.PRIVATE_CORPORATE, tier, jobs.get("biomass"), "industrial_worker"),
                facility(prefix + "smelter", home, "pyro_iron_smelting", manufacturingId, IndustrialFacility.PRIVATE_CORPORATE, tier, workers, "industrial_worker"),
                facility(prefix + "steelworks", home, "alloy_steel", manufacturingId, IndustrialFacility.PRIVATE_CORPORATE, tier, 20, "industrial_worker"),
                facility(prefix + "consumer", home, "consumer_goods_mfg", manufacturingId, IndustrialFacility.PRIVATE_CORPORATE, tier, jobs.get("consumer"), "industrial_worker"),
                facility(prefix + "luxury", home, "luxury_goods_mfg", manufacturingId, IndustrialFacility.PRIVATE_CORPORATE, tier, jobs.get("luxury"), "industrial_worker")
        ));
        if (scenario != GameStartScenario.PRE_SPACE_FLIGHT) {
            facilities.add(facility(prefix + "methalox", home, "methane_liquefaction",
                    manufacturingId, IndustrialFacility.PRIVATE_CORPORATE, tier,
                    workers / 2, "industrial_worker"));
        }
        if (scenario == GameStartScenario.BASIC_WARP) {
            facilities.add(facility(prefix + "hydrolox", home, "hydrogen_liquefaction",
                    manufacturingId, IndustrialFacility.PRIVATE_CORPORATE, tier,
                    workers / 2, "industrial_worker"));
        }
        double demandKw = facilities.stream().mapToDouble(facility ->
                PowerProcessor.requestedIndustryKw(facility, facility.allocatedWorkers())).sum()
                + PowerBillingProcessor.householdDemandKwh(empire, population) / 24.0;
        double openingGenerationKw = tier * (6000.0 + 2 * 10000.0 + 4500.0);
        int extraFissionPlants = (int) Math.ceil(Math.max(0.0,
                demandKw * 1.10 - openingGenerationKw) / (10000.0 * tier));
        for (int index = 0; index < extraFissionPlants; index++) {
            facilities.add(facility(prefix + "fission_extra_" + index, home, "nuclear_power_app",
                    empire.id(), IndustrialFacility.PUBLIC_STATE, tier, workers, "engineer"));
        }

        if (empire.societyStructure() != null && empire.societyStructure().toLowerCase().contains("hive")) {
            List<IndustrialFacility> gridFacilities = facilities.stream()
                    .filter(facility -> !"luxury_goods_mfg".equals(facility.applicationId()))
                    .map(facility -> new IndustrialFacility(facility.id(), facility.planetId(),
                            facility.applicationId(), empire.id(), IndustrialFacility.HIVE_GRID,
                            facility.tier(), facility.allocatedWorkers(), facility.workerProfessionId(),
                            false, 0.0))
                    .toList();
            return new HomeAssets(List.of(), gridFacilities);
        }

        double capital = Math.max(20_000.0, population * CORPORATE_CREDITS_PER_PERSON);
        List<Corporation> corporations = List.of(
                corporation(transportId, empire, home, "TRANSPORT", capital,
                        List.of(prefix + "freight"), List.of()),
                corporation(extractionId, empire, home, "EXTRACTION", capital,
                        List.of(prefix + "mine", prefix + "nitrates", prefix + "phosphates", prefix + "potash",
                                prefix + "aluminum_ore", prefix + "copper_ore", prefix + "silicates", prefix + "carbon",
                                prefix + "rare_earths", prefix + "uranium_mine",
                                prefix + "thorium_mine", prefix + "hydrocarbon_mine"), List.of()),
                corporation(agricultureId, empire, home, "AGRICULTURE", capital,
                        List.of(prefix + "water", prefix + "farm", prefix + "fiber"), List.of()),
                corporation(manufacturingId, empire, home, "CONSUMER", capital,
                        List.of(prefix + "aluminum", prefix + "copper", prefix + "silicon", prefix + "rare_refinery", prefix + "biomass", prefix + "hydrogen",
                                prefix + "uranium_refinery", prefix + "thorium_refinery",
                                prefix + "smelter", prefix + "steelworks", prefix + "consumer", prefix + "luxury",
                                prefix + "rp1", prefix + "lox"), List.of())
        );
        if (scenario != GameStartScenario.PRE_SPACE_FLIGHT) {
            Corporation manufacturing = corporations.getLast();
            List<String> owned = new ArrayList<>(manufacturing.ownedFacilityIds());
            owned.add(prefix + "methalox");
            if (scenario == GameStartScenario.BASIC_WARP) owned.add(prefix + "hydrolox");
            List<Corporation> updated = new ArrayList<>(corporations);
            updated.set(updated.size() - 1, corporation(manufacturingId, empire, home,
                    "CONSUMER", capital, List.copyOf(owned), List.of()));
            corporations = List.copyOf(updated);
        }
        return new HomeAssets(corporations, facilities);
    }

    private Map<String, Integer> planJobs(Planet home, GameStartScenario scenario, int tier) {
        double population = home.populations().stream().mapToLong(Population::totalCount).sum();
        Map<String, Double> basic = new HashMap<>();
        for (Population group : home.populations()) {
            Race race = races.get(group.raceId());
            if (race == null) continue;
            populationProcessor.calculateDailyMarketRequirements(group.totalCount(), race, home.atmosphere())
                    .forEach((resource, kg) -> basic.merge(resource, kg, Double::sum));
        }
        double throughput = Math.pow(1.5, tier - 1)
                * (scenario == GameStartScenario.PRE_SPACE_FLIGHT ? 1.0 : 1.10);
        Map<String, Integer> jobs = new HashMap<>();
        jobs.put("farm", workersFor(basic.getOrDefault("food_matrix", 0.0), 120.0, 1, throughput));
        double consumerKg = population * (0.005 + 0.001 * 500.0 / 800.0);
        double polymerKg = consumerKg * 0.499 + population * 0.001 * 200.0 / 800.0;
        jobs.put("consumer", workersFor(consumerKg, 1000.0, 10, throughput));
        jobs.put("luxury", workersFor(population * 0.001, 800.0, 10, throughput));
        jobs.put("biomass", workersFor(polymerKg, 1000.0, 10, throughput));
        double farmBatches = jobs.get("farm") * throughput;
        jobs.put("fiber", farmBatches > 0.0 ? 0 : workersFor(polymerKg, 1000.0, 10, throughput));
        double fiberBatches = jobs.get("fiber") * throughput / 10.0;
        double consumerBatches = jobs.get("consumer") * throughput / 10.0;
        double luxuryBatches = jobs.get("luxury") * throughput / 10.0;
        jobs.put("nitrates", workersFor(farmBatches * 10.0 + fiberBatches * 20.0, 1000.0, 100, throughput));
        jobs.put("phosphates", workersFor(farmBatches * 5.0 + fiberBatches * 15.0, 1000.0, 100, throughput));
        jobs.put("potash", workersFor(farmBatches * 5.0 + fiberBatches * 15.0, 1000.0, 100, throughput));
        jobs.put("aluminum", workersFor(consumerBatches * 250.0, 500.0, 10, throughput));
        jobs.put("copper", workersFor(consumerBatches * 150.0 + luxuryBatches * 100.0
                + basic.getOrDefault("refined_copper", 0.0), 600.0, 10, throughput));
        jobs.put("aluminum_mine", workersFor(jobs.get("aluminum") * throughput * 100.0,
                1000.0, 100, throughput));
        jobs.put("copper_mine", workersFor(jobs.get("copper") * throughput * 100.0,
                1000.0, 100, throughput));
        jobs.put("silicon", workersFor(consumerBatches * 100.0
                + basic.getOrDefault("silicon", 0.0), 500.0, 10, throughput));
        jobs.put("silicates", workersFor(jobs.get("silicon") * throughput * 100.0
                + basic.getOrDefault("silicates", 0.0), 1000.0, 100, throughput));
        jobs.put("rare_refinery", workersFor(consumerBatches, 800.0, 10, throughput));
        jobs.put("rare_mine", workersFor(jobs.get("rare_refinery") * throughput * 100.0,
                25.0, 100, throughput));
        jobs.put("water", workersFor(farmBatches * 50.0 + fiberBatches * 550.0
                + jobs.get("biomass") * throughput * 10.0, 1000.0, 10, throughput));
        jobs.put("carbon", workersFor(jobs.get("silicon") * throughput * 18.75
                + fiberBatches * 400.0 + 112.5 * throughput, 1000.0, 100, throughput));
        return Map.copyOf(jobs);
    }

    private int workersFor(double dailyOutputKg, double outputKgPerBatch, int workersPerBatch,
                           double throughput) {
        return (int) Math.ceil(dailyOutputKg * workersPerBatch * 1.10 / (outputKgPerBatch * throughput));
    }

    private IndustrialFacility facility(String id, Planet home, String application, String owner,
                                        String ownership, int tier, int workers, String profession) {
        return new IndustrialFacility(id, home.id(), application, owner, ownership, tier,
                workers, profession, false, 0.0);
    }

    private Corporation corporation(String id, Empire empire, Planet home, String orientation,
                                    double capital, List<String> facilities, List<String> ships) {
        return new Corporation(id, empire.name() + " " + orientation.toLowerCase(), empire.id(),
                home.id(), orientation, capital, facilities, ships, List.of());
    }

    public CommercialHub createOpeningHub(String id, String bodyId, String atmosphere, List<Population> populations,
                                          double logisticsRange) {
        Map<String, Double> dailyNeeds = new HashMap<>();
        long population = 0;
        for (Population group : populations) {
            population += group.totalCount();
            Race race = races.get(group.raceId());
            Map<String, Double> needs = race == null
                    ? Map.of("food_matrix", group.totalCount() * 0.1)
                    : populationProcessor.calculateDailyMarketRequirements(group.totalCount(), race, atmosphere);
            needs.forEach((resource, quantity) -> dailyNeeds.merge(resource, quantity, Double::sum));
        }
        dailyNeeds.merge("consumer_goods", population * 0.005, Double::sum);
        dailyNeeds.merge("luxury_goods", population * 0.001, Double::sum);
        dailyNeeds.put("bio_polymers", population * (0.005625 * 0.5 + 0.001 * 0.25));
        dailyNeeds.put("refined_rare_earths", population * 0.005625 * 0.001);
        double industrialStock = Math.max(1_000_000.0, population);
        for (String material : List.of("nitrates", "phosphates", "potash", "purified_water", "water_ice",
                "iron_ore", "carbon", "refined_iron", "steel", "refined_aluminum",
                "refined_copper", "silicon", "uranium_ore", "thorium_ore",
                "refined_uranium", "refined_thorium",
                "hydrogen_gas", "deuterium_gas", "fusion_fuel_pellets",
                "hydrocarbons", "rp1_kerosene", "liquid_oxygen")) {
            double stock = "purified_water".equals(material) ? industrialStock * 3.0
                    : List.of("refined_uranium", "refined_thorium").contains(material)
                    ? industrialStock * 0.1
                    : "deuterium_gas".equals(material) ? industrialStock * 0.001
                    : industrialStock;
            dailyNeeds.putIfAbsent(material, stock / INITIAL_MARKET_DAYS);
        }
        Map<String, MarketOrder> orders = new HashMap<>();
        double storedWeight = 0.0;
        for (Map.Entry<String, Double> need : dailyNeeds.entrySet()) {
            int days = "bio_polymers".equals(need.getKey()) ? 1
                    : List.of("food_matrix", "consumer_goods", "luxury_goods", "nitrates",
                    "phosphates", "potash", "purified_water", "aluminum_ore", "copper_ore",
                    "silicates", "carbon", "refined_aluminum", "refined_copper", "silicon",
                    "refined_rare_earths").contains(need.getKey())
                    ? LIVE_CHAIN_RESERVE_DAYS : INITIAL_MARKET_DAYS;
            double stock = need.getValue() * days;
            double price = MarketProcessor.basePricePerKg(need.getKey());
            orders.put(need.getKey(), new MarketOrder(need.getKey(), stock, need.getValue(), price, 0.0));
            storedWeight += stock;
        }
        return new CommercialHub(id, bodyId, 0.05, Math.max(500_000.0, storedWeight * 1.5),
                storedWeight, logisticsRange, Map.copyOf(orders));
    }

    public List<HouseholdAccount> createOpeningHouseholds(List<SolarSystem> systems, List<Empire> empires) {
        List<HouseholdAccount> accounts = new ArrayList<>();
        for (SolarSystem system : systems) {
            Empire owner = empires.stream().filter(empire -> empire.controlledSystemIds().contains(system.id()))
                    .findFirst().orElse(null);
            if (owner == null || (owner.societyStructure() != null
                    && owner.societyStructure().toLowerCase().contains("hive"))) continue;
            for (Planet planet : system.planets()) {
                addBodyHouseholds(accounts, planet.id(), system.id(), owner.id(), planet.populations());
                for (Moon moon : planet.moons()) {
                    addBodyHouseholds(accounts, moon.id(), system.id(), owner.id(), moon.populations());
                }
            }
        }
        return List.copyOf(accounts);
    }

    private void addBodyHouseholds(List<HouseholdAccount> accounts, String bodyId, String systemId,
                                   String empireId, List<Population> populations) {
        for (Population population : populations) {
            Map<String, Long> byProfession = new TreeMap<>();
            population.toDemographics(bodyId, systemId, ColonyFocus.BALANCED).cohorts().forEach(cohort ->
                    byProfession.merge(cohort.professionId(), cohort.headcount(), Long::sum));
            byProfession.forEach((profession, count) -> accounts.add(new HouseholdAccount(
                    bodyId, systemId, empireId, population.raceId(), profession, count,
                    count * HOUSEHOLD_CREDITS_PER_PERSON, 0.0, 0.0, 0.0, 0.0,
                    Map.of(), 1.0, 1.0, 0.0, 0.0, HouseholdWellbeing.healthy(),
                    HouseholdEmployment.none())));
        }
    }
}
