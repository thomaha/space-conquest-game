package com.spaceconquest.engine.economy;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.MarketOrder;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.industry.IndustryAccount;
import com.spaceconquest.engine.market.CorporateInvestmentProcessor;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.ShipInstance;

import java.util.HashMap;
import java.util.Map;

/** Conservative book value of tracked corporate assets less recorded tax debt. */
public final class CorporateValuation {
    private CorporateValuation() {}

    public record Valuation(double cashCredits, double facilitiesCredits, double shipsCredits,
                            double inventoryCredits, double liabilitiesCredits, double netWorthCredits) {}

    public static Valuation value(GameState state, Corporation corporation) {
        Map<String, CommercialHub> hubs = new HashMap<>();
        for (CommercialHub hub : state.commercialHubs()) hubs.put(hub.entityId(), hub);
        Map<String, IndustryAccount> accounts = new HashMap<>();
        for (IndustryAccount account : state.industryAccounts()) accounts.put(account.facilityId(), account);
        double facilities = 0.0;
        double inventory = 0.0;
        for (IndustrialFacility facility : state.industrialFacilities()) {
            if (!IndustrialFacility.PRIVATE_CORPORATE.equals(facility.ownershipType())
                    || !corporation.id().equals(facility.ownerEntityId())) continue;
            facilities += CorporateInvestmentProcessor.INFRASTRUCTURE_COST * Math.max(1, facility.tier());
            IndustryAccount account = accounts.get(facility.id());
            CommercialHub hub = hubs.get(facility.planetId());
            if (account == null || hub == null) continue;
            for (var stock : account.unsoldStockKg().entrySet()) {
                MarketOrder order = hub.activeOrders().get(stock.getKey());
                if (order != null) inventory += Math.max(0.0, stock.getValue())
                        * Math.max(0.0, order.pricePerKg());
            }
        }
        double ships = 0.0;
        for (Fleet fleet : state.fleets()) {
            for (ShipInstance ship : fleet.ships()) {
                if (corporation.id().equals(ship.ownerEntityId())) {
                    ships += CorporateInvestmentProcessor.SHIP_PROCUREMENT_COST
                            * Math.clamp(ship.currentHullHealth() / 1000.0, 0.0, 1.0);
                }
            }
        }
        for (var route : state.tradeRoutes()) {
            if (!corporation.id().equals(route.ownerEntityId())
                    || route.onboardKg() <= 0.0 || route.onboardCostCredits() <= 0.0
                    || route.assignedFreighterIds().isEmpty()) continue;
            String shipId = route.assignedFreighterIds().getFirst();
            double loaded = state.fleets().stream().flatMap(fleet -> fleet.ships().stream())
                    .filter(ship -> shipId.equals(ship.id()))
                    .mapToDouble(ship -> ship.storedCargoKg().getOrDefault(route.materialId(), 0.0))
                    .findFirst().orElse(0.0);
            inventory += route.onboardCostCredits()
                    * Math.clamp(loaded / route.onboardKg(), 0.0, 1.0);
        }
        double liabilities = state.corporateTaxAccounts().stream()
                .filter(account -> corporation.id().equals(account.corporationId()))
                .mapToDouble(CorporateTaxAccount::unpaidTaxCredits).sum();
        double cash = corporation.liquidCapitalReserves();
        return new Valuation(cash, facilities, ships, inventory, liabilities,
                cash + facilities + ships + inventory - liabilities);
    }
}
