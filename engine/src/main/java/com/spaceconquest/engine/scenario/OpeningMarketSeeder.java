package com.spaceconquest.engine.scenario;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.MarketOrder;
import com.spaceconquest.engine.Race;
import com.spaceconquest.engine.economy.MarketAccount;
import com.spaceconquest.engine.industry.IndustryMarketProcessor;
import com.spaceconquest.engine.market.MarketDemandProcessor;
import com.spaceconquest.engine.market.MarketProcessor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Sizes generated homeworld reserves against actual operating recipes. */
public final class OpeningMarketSeeder {
    private static final Set<String> LIVE_CHAIN = Set.of("food_matrix", "consumer_goods",
            "luxury_goods", "nitrates", "phosphates", "potash", "purified_water",
            "aluminum_ore", "copper_ore", "silicates", "carbon", "rare_earth_fluorides",
            "refined_aluminum", "refined_copper", "silicon", "refined_rare_earths",
            "agricultural_biomass", "bio_polymers");

    public GameState align(GameState state, List<Race> races) {
        Set<String> producingBodies = state.industrialFacilities().stream()
                .filter(facility -> "industrial_soil_cultivation".equals(facility.applicationId()))
                .map(facility -> facility.planetId()).collect(java.util.stream.Collectors.toSet());
        List<CommercialHub> hubs = new MarketDemandProcessor().refresh(state, races).stream()
                .map(hub -> producingBodies.contains(hub.entityId()) ? alignHome(hub) : hub)
                .toList();
        List<MarketAccount> accounts = hubs.stream().map(hub -> new MarketAccount(hub.id(),
                hub.activeOrders().values().stream().mapToDouble(order -> order.demandKg()
                        * MarketProcessor.basePricePerKg(order.resourceId())
                        * IndustryMarketProcessor.WHOLESALE_SHARE).sum() * 3.0)).toList();
        return state.withCommercialHubs(hubs).withMarketAccounts(accounts);
    }

    private CommercialHub alignHome(CommercialHub hub) {
        Map<String, MarketOrder> orders = new HashMap<>(hub.activeOrders());
        for (var entry : hub.activeOrders().entrySet()) {
            if (!LIVE_CHAIN.contains(entry.getKey())) continue;
            MarketOrder old = entry.getValue();
            int days = "bio_polymers".equals(entry.getKey()) ? 1 : 7;
            orders.put(entry.getKey(), new MarketOrder(entry.getKey(),
                    Math.max(0.0, old.demandKg() * days), old.demandKg(),
                    MarketProcessor.basePricePerKg(entry.getKey()), old.shortcomingScore()));
        }
        double stored = orders.values().stream().mapToDouble(MarketOrder::supplyKg).sum();
        return new CommercialHub(hub.id(), hub.entityId(), hub.transactionTariffRate(),
                Math.max(hub.storageCapacityKg(), stored * 1.5), stored,
                hub.logisticsRangeUnits(), Map.copyOf(orders));
    }
}
