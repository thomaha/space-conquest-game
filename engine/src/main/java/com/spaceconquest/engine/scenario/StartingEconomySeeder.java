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
import com.spaceconquest.engine.industry.IndustrialFacility;

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
        double throughput = Math.pow(1.5, tier - 1);
        if (scenario != GameStartScenario.PRE_SPACE_FLIGHT) throughput *= 1.10;
        int farmWorkers = (int) Math.ceil(population * 0.1 * 1.10 / (120.0 * throughput));
        int consumerWorkers = (int) Math.ceil(population * 0.005 * 10.0 / (1000.0 * throughput));
        String prefix = "fac_" + empire.id() + "_" + home.id() + "_";
        String corporatePrefix = "corp_" + empire.id() + "_";
        String transportId = corporatePrefix + "transport";
        String extractionId = corporatePrefix + "extraction";
        String agricultureId = corporatePrefix + "agriculture";
        String manufacturingId = corporatePrefix + "manufacturing";

        List<IndustrialFacility> facilities = List.of(
                facility(prefix + "solar", home, "solar_power", empire.id(), IndustrialFacility.PUBLIC_STATE, tier, workers, "technician"),
                facility(prefix + "fission", home, "nuclear_power_app", empire.id(), IndustrialFacility.PUBLIC_STATE, tier, workers, "engineer"),
                facility(prefix + "combustion", home, "combustion_power", empire.id(), IndustrialFacility.PUBLIC_STATE, tier, workers, "technician"),
                facility(prefix + "freight", home, "cargo_terminal", transportId, IndustrialFacility.PRIVATE_CORPORATE, tier, workers / 2, "technician"),
                facility(prefix + "mine", home, "mining_outpost", extractionId, IndustrialFacility.PRIVATE_CORPORATE, tier, workers, "miner"),
                facility(prefix + "farm", home, "industrial_soil_cultivation", agricultureId, IndustrialFacility.PRIVATE_CORPORATE, tier, farmWorkers, "farmer"),
                facility(prefix + "smelter", home, "pyro_iron_smelting", manufacturingId, IndustrialFacility.PRIVATE_CORPORATE, tier, workers, "industrial_worker"),
                facility(prefix + "consumer", home, "consumer_goods_mfg", manufacturingId, IndustrialFacility.PRIVATE_CORPORATE, tier, consumerWorkers, "industrial_worker")
        );

        if (empire.societyStructure() != null && empire.societyStructure().toLowerCase().contains("hive")) {
            List<IndustrialFacility> gridFacilities = facilities.stream()
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
                        List.of(prefix + "freight"), scenario == GameStartScenario.PRE_SPACE_FLIGHT ? List.of() : List.of("cargo_freighter_01")),
                corporation(extractionId, empire, home, "EXTRACTION", capital,
                        List.of(prefix + "mine"), scenario == GameStartScenario.PRE_SPACE_FLIGHT ? List.of() : List.of("mine_ship_alpha_1")),
                corporation(agricultureId, empire, home, "AGRICULTURE", capital,
                        List.of(prefix + "farm"), List.of()),
                corporation(manufacturingId, empire, home, "CONSUMER", capital,
                        List.of(prefix + "smelter", prefix + "consumer"), List.of())
        );
        return new HomeAssets(corporations, facilities);
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
        double industrialStock = Math.max(1_000_000.0, population);
        for (String material : List.of("nitrates", "phosphates", "potash", "water_ice",
                "iron_ore", "carbon_monoxide_ice", "bio_polymers", "refined_aluminum",
                "refined_copper", "silicon", "refined_uranium", "fusion_fuel_pellets",
                "hydrocarbons")) {
            double stock = "water_ice".equals(material) ? industrialStock * 3.0 : industrialStock;
            dailyNeeds.putIfAbsent(material, stock / INITIAL_MARKET_DAYS);
        }
        Map<String, MarketOrder> orders = new HashMap<>();
        double storedWeight = 0.0;
        for (Map.Entry<String, Double> need : dailyNeeds.entrySet()) {
            int days = "consumer_goods".equals(need.getKey()) ? 7 : INITIAL_MARKET_DAYS;
            double stock = need.getValue() * days;
            double price = need.getKey().equals("luxury_goods") ? 12.0 : 2.0;
            orders.put(need.getKey(), new MarketOrder(need.getKey(), stock, need.getValue(), price, 0.0));
            storedWeight += stock;
        }
        return new CommercialHub(id, bodyId, 0.05, Math.max(500_000.0, storedWeight),
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
                    Map.of(), 1.0, 1.0, 0.0, 0.0)));
        }
    }
}
