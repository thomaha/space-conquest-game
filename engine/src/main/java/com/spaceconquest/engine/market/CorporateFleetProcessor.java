package com.spaceconquest.engine.market;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.MarketOrder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages autonomous corporate fleets including cargo transport arbitrage and mining operations.
 */
public class CorporateFleetProcessor {

    private final MarketProcessor marketProcessor;

    public CorporateFleetProcessor() {
        this(new MarketProcessor());
    }

    public CorporateFleetProcessor(MarketProcessor marketProcessor) {
        this.marketProcessor = marketProcessor;
    }

    /**
     * Executes autonomous fleet operations for all corporations and updates market inventories.
     *
     * @param corporations list of corporations
     * @param hubs         list of commercial hubs
     * @return updated CorporateFleetResult containing updated corporations and hubs
     */
    public CorporateFleetResult processFleetOperations(
            List<Corporation> corporations,
            List<CommercialHub> hubs,
            Map<String, Double> gravityMap,
            Map<String, Double> atmosphereMap
    ) {
        if (corporations == null) {
            return new CorporateFleetResult(List.of(), hubs != null ? hubs : List.of());
        }
        if (hubs == null) {
            return new CorporateFleetResult(corporations, List.of());
        }

        List<Corporation> updatedCorps = new ArrayList<>();
        Map<String, CommercialHub> hubMap = new HashMap<>();
        for (CommercialHub hub : hubs) {
            hubMap.put(hub.id(), hub);
        }

        for (Corporation corp : corporations) {
            if ("TRANSPORT".equalsIgnoreCase(corp.marketOrientation())) {
                Corporation updated = processTransportArbitrage(corp, hubMap, gravityMap, atmosphereMap);
                updatedCorps.add(updated);
            } else if ("EXTRACTION".equalsIgnoreCase(corp.marketOrientation())) {
                Corporation updated = processMiningFleet(corp);
                updatedCorps.add(updated);
            } else {
                updatedCorps.add(corp);
            }
        }

        return new CorporateFleetResult(updatedCorps, new ArrayList<>(hubMap.values()));
    }

    private Corporation processTransportArbitrage(
            Corporation corp,
            Map<String, CommercialHub> hubMap,
            Map<String, Double> gravityMap,
            Map<String, Double> atmosphereMap
    ) {
        long transportCount = corp.ownedShipIds().stream()
                .filter(s -> s.toLowerCase().contains("transport") || s.toLowerCase().contains("freighter") || s.toLowerCase().contains("cargo"))
                .count();

        if (transportCount == 0 || hubMap.size() < 2) {
            return corp;
        }

        double totalNetProfit = 0.0;
        double cargoCapacityPerShip = 1000.0; // kg
        double shipDryMass = 5000.0; // kg

        for (int i = 0; i < transportCount; i++) {
            TradeOpportunity bestOpp = findBestTradeOpportunity(hubMap, gravityMap, atmosphereMap, shipDryMass, cargoCapacityPerShip);
            if (bestOpp != null && bestOpp.netProfit() > 0) {
                totalNetProfit += bestOpp.netProfit();
                // Apply inventory update
                executeTrade(bestOpp, hubMap, cargoCapacityPerShip);
            }
        }

        return new Corporation(
                corp.id(),
                corp.name(),
                corp.empireId(),
                corp.headquartersEntityId(),
                corp.marketOrientation(),
                corp.liquidCapitalReserves() + totalNetProfit,
                corp.ownedFacilityIds(),
                corp.ownedShipIds(),
                corp.claimedVeinIds()
        );
    }

    private Corporation processMiningFleet(Corporation corp) {
        long mineShipCount = corp.ownedShipIds().stream()
                .filter(s -> s.toLowerCase().contains("mine") || s.toLowerCase().contains("mining") || s.toLowerCase().contains("barge"))
                .count();

        if (mineShipCount == 0) {
            return corp;
        }

        double revenuePerShip = 500.0;
        double addedRevenue = mineShipCount * revenuePerShip;

        return new Corporation(
                corp.id(),
                corp.name(),
                corp.empireId(),
                corp.headquartersEntityId(),
                corp.marketOrientation(),
                corp.liquidCapitalReserves() + addedRevenue,
                corp.ownedFacilityIds(),
                corp.ownedShipIds(),
                corp.claimedVeinIds()
        );
    }

    public TradeOpportunity findBestTradeOpportunity(
            Map<String, CommercialHub> hubMap,
            Map<String, Double> gravityMap,
            Map<String, Double> atmosphereMap,
            double shipDryMass,
            double cargoMass
    ) {
        TradeOpportunity best = null;
        List<CommercialHub> hubs = new ArrayList<>(hubMap.values());

        for (int i = 0; i < hubs.size(); i++) {
            CommercialHub srcHub = hubs.get(i);
            double srcGravity = gravityMap != null ? gravityMap.getOrDefault(srcHub.entityId(), 0.0) : 0.0;
            double srcAtmosphere = atmosphereMap != null ? atmosphereMap.getOrDefault(srcHub.entityId(), 0.0) : 0.0;
            double launchTax = marketProcessor.calculateGravityLaunchTax(shipDryMass, cargoMass, srcGravity, srcAtmosphere);

            for (int j = 0; j < hubs.size(); j++) {
                if (i == j) continue;
                CommercialHub destHub = hubs.get(j);

                for (Map.Entry<String, MarketOrder> srcEntry : srcHub.activeOrders().entrySet()) {
                    String resourceId = srcEntry.getKey();
                    MarketOrder srcOrder = srcEntry.getValue();
                    MarketOrder destOrder = destHub.activeOrders().get(resourceId);

                    if (destOrder == null) continue;
                    if (srcOrder.supplyKg() < cargoMass) continue;

                    double grossRevenue = (destOrder.pricePerKg() - srcOrder.pricePerKg()) * cargoMass;
                    double srcTariff = marketProcessor.calculateTariff(srcOrder.pricePerKg() * cargoMass, srcHub.transactionTariffRate());
                    double destTariff = marketProcessor.calculateTariff(destOrder.pricePerKg() * cargoMass, destHub.transactionTariffRate());
                    double netProfit = grossRevenue - launchTax - srcTariff - destTariff;

                    if (netProfit > 0 && (best == null || netProfit > best.netProfit())) {
                        best = new TradeOpportunity(srcHub.id(), destHub.id(), resourceId, netProfit);
                    }
                }
            }
        }
        return best;
    }

    private void executeTrade(TradeOpportunity opp, Map<String, CommercialHub> hubMap, double volumeKg) {
        CommercialHub src = hubMap.get(opp.sourceHubId());
        CommercialHub dest = hubMap.get(opp.destHubId());
        if (src == null || dest == null) return;

        Map<String, MarketOrder> srcOrders = new HashMap<>(src.activeOrders());
        Map<String, MarketOrder> destOrders = new HashMap<>(dest.activeOrders());

        MarketOrder srcOrder = srcOrders.get(opp.resourceId());
        MarketOrder destOrder = destOrders.get(opp.resourceId());

        if (srcOrder != null && destOrder != null) {
            srcOrders.put(opp.resourceId(), new MarketOrder(
                    opp.resourceId(),
                    Math.max(0, srcOrder.supplyKg() - volumeKg),
                    srcOrder.demandKg(),
                    srcOrder.pricePerKg(),
                    srcOrder.shortcomingScore()
            ));
            destOrders.put(opp.resourceId(), new MarketOrder(
                    opp.resourceId(),
                    destOrder.supplyKg() + volumeKg,
                    destOrder.demandKg(),
                    destOrder.pricePerKg(),
                    destOrder.shortcomingScore()
            ));

            hubMap.put(src.id(), new CommercialHub(
                    src.id(), src.entityId(), src.transactionTariffRate(), src.storageCapacityKg(),
                    Math.max(0, src.currentStoredWeightKg() - volumeKg), src.logisticsRangeUnits(), srcOrders
            ));
            hubMap.put(dest.id(), new CommercialHub(
                    dest.id(), dest.entityId(), dest.transactionTariffRate(), dest.storageCapacityKg(),
                    dest.currentStoredWeightKg() + volumeKg, dest.logisticsRangeUnits(), destOrders
            ));
        }
    }

    public record TradeOpportunity(
            String sourceHubId,
            String destHubId,
            String resourceId,
            double netProfit
    ) {}

    public record CorporateFleetResult(
            List<Corporation> corporations,
            List<CommercialHub> commercialHubs
    ) {}
}
