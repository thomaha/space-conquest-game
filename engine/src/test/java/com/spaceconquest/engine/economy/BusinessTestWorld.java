package com.spaceconquest.engine.economy;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.market.*;
import java.util.*;

public final class BusinessTestWorld {
    private BusinessTestWorld() {}
    public static GameState create(String application) throws Exception {
        var knowledge = new ArrayList<String>();
        DataModelLoader.loadTechnologies().forEach(t -> { knowledge.add(t.id()); t.applications().forEach(a -> knowledge.add(a.id())); });
        var empire = new Empire("owner", "Owner", "human", "Individualist", 1e8, .25, List.of("sol"), List.of(), Map.of(), knowledge, List.of());
        var planet = new Planet("site", "Site", "", 5.97e24, 9.81, SolarRadiation.AU_KM, 0, 12742, "terrestrial", "nitrogen_oxygen", true, .3,
                List.of(), List.of(), List.of(new Population("human", Map.of(25, 1000L))));
        var recipe = IndustryRecipeCatalog.find(application);
        var farmer = new IndustrialFacility("producer", "site", application, "producer-owner", IndustrialFacility.PRIVATE_CORPORATE,
                1, recipe.work().equipment().referenceOperators(), recipe.work().equipment().operatorProfession(), false, 0);
        var factory = new IndustrialFacility("processor", "site", "food_processing", "processor-owner", IndustrialFacility.PRIVATE_CORPORATE,
                1, 10, "industrial_worker", false, 0);
        var generator = new IndustrialFacility("generator", "site", "nuclear_power_app", "grid-owner", IndustrialFacility.PRIVATE_CORPORATE,
                1, 100, "technician", false, 0);
        var corporations = List.of(firm("producer-owner", "producer"), firm("processor-owner", "processor"), firm("grid-owner", "generator"));
        var orders = new HashMap<String, MarketOrder>();
        recipe.inputsKg().forEach((id, kg) -> orders.put(id, new MarketOrder(id, kg * 100, 10, MarketProcessor.basePricePerKg(id), 0)));
        orders.put("refined_uranium", new MarketOrder("refined_uranium", 50000, 10, 100, 0));
        orders.put("food_matrix", new MarketOrder("food_matrix", 14000, 100, 4, 0));
        orders.put("oxygen_gas", new MarketOrder("oxygen_gas", 2000, 0, 2, 0));
        orders.put("harvested_crops", new MarketOrder("harvested_crops", 0, 100, 2, 0));
        recipe.outputsKg().keySet().forEach(id -> orders.putIfAbsent(id, new MarketOrder(id, 0, 0, MarketProcessor.basePricePerKg(id), 0)));
        var hub = new CommercialHub("hub", "site", 0, 1e10, orders.values().stream().mapToDouble(MarketOrder::supplyKg).sum(), 10, orders);
        return GameState.builder().solarSystems(List.of(new SolarSystem("sol", "Sol", "", 0, 0, 0, SolarRadiation.SOLAR_MASS_KG, 1392700,
                        "yellow", List.of(planet), List.of()))).empires(List.of(empire)).corporations(corporations)
                .industrialFacilities(List.of(farmer, factory, generator)).commercialHubs(List.of(hub)).marketAccounts(List.of(new MarketAccount("hub", 1e8)))
                .industryAccounts(List.of(IndustryAccount.empty("producer"), IndustryAccount.empty("processor"), IndustryAccount.empty("generator")))
                .householdAccounts(List.of(household("farmer", 100), household("industrial_worker", 200), household("technician", 200), household("miner", 500))).build();
    }
    private static Corporation firm(String id, String facility) {
        return new Corporation(id, id, "owner", "site", "AUDIT", 1e7, List.of(facility), List.of(), List.of());
    }
    private static HouseholdAccount household(String profession, int count) {
        return new HouseholdAccount("site", "sol", "owner", "human", profession, count, count * 10000.0, 0, 0, 0, 0,
                Map.of(), 1, 1, 0, 0, HouseholdWellbeing.healthy(), new HouseholdEmployment(count, 0, 0, 0));
    }
}
