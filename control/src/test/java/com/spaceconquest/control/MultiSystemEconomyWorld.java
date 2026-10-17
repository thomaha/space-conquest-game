package com.spaceconquest.control;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.*;
import com.spaceconquest.engine.industry.*;
import java.util.*;

/** Finite opening stock, cash and profession populations; subsequent days use normal engine settlement. */
final class MultiSystemEconomyWorld {
    private MultiSystemEconomyWorld() {}
    static GameState create() {
        var systems = new ArrayList<SolarSystem>(); var hubs = new ArrayList<CommercialHub>();
        var households = new ArrayList<HouseholdAccount>(); var market = new ArrayList<MarketAccount>();
        for (String id : List.of("rich", "poor")) {
            var population = new Population("human", Map.of(25, 20000L));
            var body = new Planet(id + "-world", id, "", 1, 9.81, SolarRadiation.AU_KM, 0, 12742, "terrestrial", "breathable", true, 1,
                    List.of(), List.of(), List.of(population));
            systems.add(new SolarSystem(id, id, "", 0, 0, 0, SolarRadiation.SOLAR_MASS_KG, 1392700, "Yellow", List.of(body), List.of()));
            var orders = new HashMap<String, MarketOrder>();
            for (String material : List.of("food_matrix", "purified_water", "oxygen_gas", "consumer_goods", "luxury_goods"))
                orders.put(material, new MarketOrder(material, 5000000, 0, 2, 0));
            for (String material : List.of("refined_iron", "refined_aluminum", "refined_copper", "silicon", "carbon", "steel", "hydrocarbons"))
                orders.put(material, new MarketOrder(material, id.equals("poor") && material.equals("steel") ? 0 : 600000, 0, 2, 0));
            orders.put("cannon_round", new MarketOrder("cannon_round", 0, 5000, 100, 1));
            double stored = orders.values().stream().mapToDouble(MarketOrder::supplyKg).sum();
            hubs.add(new CommercialHub("hub-" + id, body.id(), 0, 100000000, stored, 10, orders));
            market.add(new MarketAccount("hub-" + id, 10000000));
            for (var cohort : population.toDemographics(body.id(), id, com.spaceconquest.engine.demographics.ColonyFocus.BALANCED).cohorts())
                households.add(new HouseholdAccount(body.id(), id, "owner", "human", cohort.professionId(), cohort.headcount(), 1000000,
                        0, 0, 0, 0, Map.of(), 1, 1, 0, 0, HouseholdWellbeing.healthy(), new HouseholdEmployment(cohort.headcount(), 0, 0, 0)));
        }
        var empire = new Empire("owner", "Owner", "human", "Individualist", 1500000, 0, List.of("rich", "poor"), List.of(), Map.of(),
                List.of("electricity", "solar_power", "industrial_production", "ship_cannons", "cannon_ammunition_mfg", PlanetaryMunicipalProcessor.TECH_SUBSPACE_BANKING), List.of());
        var facilities = List.of(facility("rich-solar", "rich", "solar_power", "owner", 100, "technician", true),
                facility("poor-solar", "poor", "solar_power", "owner", 100, "technician", true),
                facility("rich-industry", "rich", "cannon_ammunition_mfg", "business", 1000, "industrial_worker", false),
                facility("poor-steel", "poor", "alloy_steel", "business", 10, "industrial_worker", false),
                facility("poor-ammo", "poor", "cannon_ammunition_mfg", "owner", 1, "industrial_worker", true));
        return GameState.builder().empires(List.of(empire)).solarSystems(systems).commercialHubs(hubs).householdAccounts(households).marketAccounts(market)
                .corporations(List.of(new Corporation("business", "Business", "owner", "rich-world", "TRANSPORT", 20000000,
                        List.of("rich-industry", "poor-steel"), List.of(), List.of())))
                .industrialFacilities(facilities).industryAccounts(List.of(IndustryAccount.empty("rich-solar").withOperatingCash(50000),
                        IndustryAccount.empty("poor-solar"), IndustryAccount.empty("poor-ammo")))
                .systemEconomies(List.of(economy("rich", 1000), economy("poor", 4000)))
                .planetaryBalanceSheets(List.of(sheet("rich", 3000, 20000, 0), sheet("poor", -4000, 0, 3000)))
                .powerGrids(List.of(new PowerGridState("poor-world", 0, 8000, -8000, 0, 0, true))).build();
    }
    private static IndustrialFacility facility(String id, String system, String app, String owner, int workers, String profession, boolean publicFacility) {
        return new IndustrialFacility(id, system + "-world", app, owner, publicFacility ? IndustrialFacility.PUBLIC_STATE : IndustrialFacility.PRIVATE_CORPORATE,
                1, workers, profession, false, 0);
    }
    private static SystemEconomy economy(String system, double budget) {
        return new SystemEconomy(system, "owner", .2, .2, .2, .2, .2, budget, 5000, 1, 1, 1, 1, 1, 10, 10, 15, 10, 20, 30, 25, 100, .20, 0);
    }
    private static PlanetaryBalanceSheet sheet(String system, double net, double cash, double debt) {
        return new PlanetaryBalanceSheet(system + "-world", system, "owner", 0, Math.max(0, net), 0, 0, Math.max(0, net), 0, 0, 0, 0,
                Math.max(0, -net), net, cash, 0, 0, 0, debt);
    }
}
