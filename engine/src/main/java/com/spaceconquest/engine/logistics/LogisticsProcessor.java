package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.MarketOrder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simulates automated cargo logistics routes between commercial hubs and planetary storehouses.
 */
public class LogisticsProcessor {

    public static final double BASE_FREIGHTER_CAPACITY_KG = 2000.0;
    public static final double TARIFF_RATE_PERCENT = 0.02; // 2% imperial transit tariff

    public record LogisticsResult(
            List<TradeRoute> updatedTradeRoutes,
            List<CommercialHub> updatedCommercialHubs,
            List<Empire> updatedEmpires,
            List<Corporation> updatedCorporations,
            double totalVolumeMovedThisTurnKg
    ) {}

    public LogisticsResult processTradeRoutes(
            List<TradeRoute> routes,
            List<CommercialHub> hubs,
            List<Empire> empires,
            List<Corporation> corporations
    ) {
        if (routes == null || routes.isEmpty()) {
            return new LogisticsResult(
                    routes != null ? routes : List.of(),
                    hubs != null ? hubs : List.of(),
                    empires != null ? empires : List.of(),
                    corporations != null ? corporations : List.of(),
                    0.0
            );
        }

        Map<String, CommercialHub> hubMap = new HashMap<>();
        if (hubs != null) {
            for (CommercialHub h : hubs) {
                hubMap.put(h.id(), h);
            }
        }

        Map<String, Empire> empireMap = new HashMap<>();
        if (empires != null) {
            for (Empire emp : empires) {
                empireMap.put(emp.id(), emp);
            }
        }

        Map<String, Corporation> corpMap = new HashMap<>();
        if (corporations != null) {
            for (Corporation c : corporations) {
                corpMap.put(c.id(), c);
            }
        }

        List<TradeRoute> updatedRoutes = new ArrayList<>();
        double totalVolumeMoved = 0.0;

        for (TradeRoute route : routes) {
            if (!route.isActive()) {
                updatedRoutes.add(route);
                continue;
            }

            CommercialHub originHub = hubMap.get(route.originEntityId());
            CommercialHub destHub = hubMap.get(route.destinationEntityId());

            if (originHub == null || destHub == null) {
                updatedRoutes.add(route);
                continue;
            }

            MarketOrder originOrder = originHub.activeOrders().get(route.materialId());
            double sourceStock = originOrder != null ? originOrder.supplyKg() : 0.0;

            MarketOrder destOrder = destHub.activeOrders().get(route.materialId());
            double destStock = destOrder != null ? destOrder.supplyKg() : 0.0;

            double availableSurplus = Math.max(0.0, sourceStock - route.minSourceInventoryThresholdKg());
            if (availableSurplus <= 0.0) {
                updatedRoutes.add(route);
                continue;
            }

            int freighterCount = route.assignedFreighterIds() != null && !route.assignedFreighterIds().isEmpty()
                    ? route.assignedFreighterIds().size() : 1;
            double maxHaulCapacity = freighterCount * BASE_FREIGHTER_CAPACITY_KG;
            double desiredTransfer = Math.min(route.transferAmountPerTurnKg(), maxHaulCapacity);

            double roomAtDest = Math.max(0.0, route.maxDestinationCapacityKg() - destStock);
            double actualTransfer = Math.min(availableSurplus, Math.min(desiredTransfer, roomAtDest));

            if (actualTransfer > 0.0) {
                // Deduct from origin hub
                Map<String, MarketOrder> originOrders = new HashMap<>(originHub.activeOrders());
                if (originOrder != null) {
                    originOrders.put(route.materialId(), new MarketOrder(
                            route.materialId(),
                            Math.max(0.0, sourceStock - actualTransfer),
                            originOrder.demandKg(),
                            originOrder.pricePerKg(),
                            originOrder.shortcomingScore()
                    ));
                }
                CommercialHub updatedOrigin = new CommercialHub(
                        originHub.id(), originHub.entityId(), originHub.transactionTariffRate(),
                        originHub.storageCapacityKg(),
                        Math.max(0.0, originHub.currentStoredWeightKg() - actualTransfer),
                        originHub.logisticsRangeUnits(), originOrders
                );
                hubMap.put(updatedOrigin.id(), updatedOrigin);

                // Add to destination hub
                Map<String, MarketOrder> destOrders = new HashMap<>(destHub.activeOrders());
                double newDestStock = destStock + actualTransfer;
                double destDemand = destOrder != null ? destOrder.demandKg() : 0.0;
                double destPrice = destOrder != null ? destOrder.pricePerKg() : 10.0;
                double destShortcoming = destOrder != null ? destOrder.shortcomingScore() : 0.0;

                destOrders.put(route.materialId(), new MarketOrder(
                        route.materialId(), newDestStock, destDemand, destPrice, destShortcoming
                ));
                CommercialHub updatedDest = new CommercialHub(
                        destHub.id(), destHub.entityId(), destHub.transactionTariffRate(),
                        destHub.storageCapacityKg(), destHub.currentStoredWeightKg() + actualTransfer,
                        destHub.logisticsRangeUnits(), destOrders
                );
                hubMap.put(updatedDest.id(), updatedDest);

                // Process transit tariffs to owner empire
                double tariffAmount = actualTransfer * TARIFF_RATE_PERCENT;
                Empire controllingEmpire = empireMap.get(route.ownerEntityId());
                if (controllingEmpire == null && !empireMap.isEmpty()) {
                    controllingEmpire = empireMap.values().iterator().next();
                }
                if (controllingEmpire != null) {
                    Empire updatedEmpire = new Empire(
                            controllingEmpire.id(), controllingEmpire.name(), controllingEmpire.raceId(),
                            controllingEmpire.societyStructure(), controllingEmpire.treasuryCredits() + tariffAmount,
                            controllingEmpire.corporateTaxRate(), controllingEmpire.controlledSystemIds(),
                            controllingEmpire.ministries(), controllingEmpire.systemGovernorAssignments(),
                            controllingEmpire.unlockedTechIds(), controllingEmpire.activeShipDesignIds()
                    );
                    empireMap.put(updatedEmpire.id(), updatedEmpire);
                }

                totalVolumeMoved += actualTransfer;
                updatedRoutes.add(new TradeRoute(
                        route.id(), route.name(), route.ownerEntityId(), route.originEntityId(),
                        route.destinationEntityId(), route.materialId(), route.transferAmountPerTurnKg(),
                        route.minSourceInventoryThresholdKg(), route.maxDestinationCapacityKg(),
                        route.assignedFreighterIds(), route.totalVolumeMovedKg() + actualTransfer,
                        route.isActive()
                ));
            } else {
                updatedRoutes.add(route);
            }
        }

        return new LogisticsResult(
                updatedRoutes,
                new ArrayList<>(hubMap.values()),
                new ArrayList<>(empireMap.values()),
                new ArrayList<>(corpMap.values()),
                totalVolumeMoved
        );
    }
}
