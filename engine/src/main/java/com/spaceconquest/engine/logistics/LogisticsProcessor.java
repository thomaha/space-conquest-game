package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.MarketOrder;
import com.spaceconquest.engine.economy.MarketAccount;
import com.spaceconquest.engine.industry.IndustryMarketProcessor;
import com.spaceconquest.engine.market.MarketProcessor;
import com.spaceconquest.engine.market.MarketStockpilePolicy;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FleetLocation;
import com.spaceconquest.engine.ship.LocalTravel;
import com.spaceconquest.engine.ship.FleetPositioning;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipInstance;
import com.spaceconquest.engine.ship.ShipRole;
import com.spaceconquest.engine.ship.ShipFueling;
import com.spaceconquest.engine.ship.PropulsionCatalog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * Simulates automated cargo logistics routes between commercial hubs and planetary storehouses.
 */
public class LogisticsProcessor {

    public static final double BASE_FREIGHTER_CAPACITY_KG = 2000.0;
    public static final double TARIFF_RATE_PERCENT = 0.02; // 2% imperial transit tariff

    public record FreightResult(GameState state, double deliveredKg) {}

    private record Carrier(Fleet fleet, ShipInstance ship, ShipDesign design) {}
    private record RouteStep(GameState state, TradeRoute route, double deliveredKg, Fleet preparedFleet) {
        private RouteStep(GameState state, TradeRoute route, double deliveredKg) { this(state, route, deliveredKg, null); }
    }
    public record ShipmentPreview(GameState state, TradeRoute route, Fleet preparedFleet) {}
    private record MarketChoice(String materialId, String destinationHubId, double score, boolean resupplyAvailable) {}
    private record Shipment(double massKg, LaunchService.Plan launch) {
    }
    private record CarrierDeparture(GameState state, double launchCostCredits) {}

    /** Advances one physical shipment per assigned cargo ship on the daily tick. */
    public FreightResult processTradeRoutes(GameState state) {
        if (state == null || state.tradeRoutes().isEmpty())
            return new FreightResult(state, 0.0);
        GameState current = state;
        List<TradeRoute> updated = new ArrayList<>();
        Set<String> usedShips = new HashSet<>();
        Set<String> usedFleets = new HashSet<>();
        Set<String> dispatched = new HashSet<>();
        double delivered = 0.0;
        for (TradeRoute previous : state.tradeRoutes()) {
            TradeRoute route = previous.resetDailyResult();
            Carrier carrier = findCarrier(current, route);
            if ((!route.isActive() && TradeRoute.LOADING.equals(route.phase())) || carrier == null
                    || !usedShips.add(carrier.ship().id())
                    || !usedFleets.add(carrier.fleet().id())) {
                updated.add(route);
                continue;
            }
            double beforeMaintenance = balance(current, route.ownerEntityId());
            current = TradePortMaintenance.supply(current, carrier.fleet());
            route = route.withOperatingCost(Math.max(0, beforeMaintenance - balance(current, route.ownerEntityId())));
            carrier = findCarrier(current, route);
            if (TradeRoute.DELIVERING.equals(route.phase())) route = route.withAvailableCargo(
                    carrier.ship().storedCargoKg().getOrDefault(route.materialId(), 0.0));
            RouteStep step = switch (route.phase()) {
                case TradeRoute.LOADING -> route.roaming() ? roam(current, route, carrier) : load(current, route, carrier);
                case TradeRoute.DELIVERING -> deliver(current, route, carrier);
                case TradeRoute.RETURNING -> returnCarrier(current, route, carrier);
                default -> new RouteStep(current, route, 0.0);
            };
            current = step.state();
            var after = findCarrier(current, route).fleet();
            if (!carrier.fleet().hasInterstellarOrder() && !carrier.fleet().location().inTransit()
                    && (after.hasInterstellarOrder() || after.location().inTransit())) dispatched.add(route.id());
            updated.add(step.route());
            delivered += step.deliveredKg();
        }
        updated.sort(java.util.Comparator.comparing(route -> dispatched.contains(route.id())));
        return new FreightResult(current.withTradeRoutes(updated), delivered);
    }

    private Carrier findCarrier(GameState state, TradeRoute route) {
        if (route.assignedFreighterIds().isEmpty()) return null;
        if (state.corporations().stream().noneMatch(item ->
                route.ownerEntityId().equals(item.id()))
                && state.empires().stream().noneMatch(item ->
                route.ownerEntityId().equals(item.id()))) return null;
        String shipId = route.assignedFreighterIds().getFirst();
        for (Fleet fleet : state.fleets()) {
            if (!route.ownerEntityId().equals(fleet.ownerEntityId())) continue;
            for (ShipInstance ship : fleet.ships()) {
                if (!shipId.equals(ship.id()) || !route.ownerEntityId().equals(ship.ownerEntityId()))
                    continue;
                ShipDesign design = state.shipDesigns().stream()
                        .filter(item -> item.id().equals(ship.designId())).findFirst().orElse(null);
                if (design != null && ShipRole.CARGO_TRANSPORT.equalsIgnoreCase(design.role()))
                    return new Carrier(fleet, ship, design);
            }
        }
        return null;
    }

    public record LogisticsResult(
            List<TradeRoute> updatedTradeRoutes,
            List<CommercialHub> updatedCommercialHubs,
            List<Empire> updatedEmpires,
            List<Corporation> updatedCorporations,
            double totalVolumeMovedThisTurnKg
    ) {}

    /** Legacy list-only calculation retained for callers that lack fleet state. Not used by the game tick. */
    @Deprecated(forRemoval = true)
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

        Map<String, CommercialHub> hubMap = buildHubMap(hubs);
        Map<String, Empire> empireMap = buildEmpireMap(empires);
        Map<String, Corporation> corpMap = buildCorpMap(corporations);

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

            double actualTransfer = calculateActualTransfer(route, originHub, destHub);
            if (actualTransfer > 0.0) {
                transferStock(route, originHub, destHub, actualTransfer, hubMap);
                applyTransitTariffs(route, actualTransfer, empireMap);

                totalVolumeMoved += actualTransfer;
                updatedRoutes.add(route.withTrip(route.phase(), route.onboardKg(), actualTransfer));
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

    private Map<String, CommercialHub> buildHubMap(List<CommercialHub> hubs) {
        Map<String, CommercialHub> map = new HashMap<>();
        if (hubs != null) {
            for (CommercialHub h : hubs) {
                if (h != null) map.put(h.id(), h);
            }
        }
        return map;
    }

    private Map<String, Empire> buildEmpireMap(List<Empire> empires) {
        Map<String, Empire> map = new HashMap<>();
        if (empires != null) {
            for (Empire emp : empires) {
                if (emp != null) map.put(emp.id(), emp);
            }
        }
        return map;
    }

    private Map<String, Corporation> buildCorpMap(List<Corporation> corporations) {
        Map<String, Corporation> map = new HashMap<>();
        if (corporations != null) {
            for (Corporation c : corporations) {
                if (c != null) map.put(c.id(), c);
            }
        }
        return map;
    }

    private double calculateActualTransfer(TradeRoute route, CommercialHub originHub, CommercialHub destHub) {
        MarketOrder originOrder = originHub.activeOrders().get(route.materialId());
        double sourceStock = originOrder != null ? originOrder.supplyKg() : 0.0;

        MarketOrder destOrder = destHub.activeOrders().get(route.materialId());
        double destStock = destOrder != null ? destOrder.supplyKg() : 0.0;

        double availableSurplus = Math.max(0.0, sourceStock - route.minSourceInventoryThresholdKg());
        if (availableSurplus <= 0.0) return 0.0;

        int freighterCount = route.assignedFreighterIds() != null && !route.assignedFreighterIds().isEmpty()
                ? route.assignedFreighterIds().size() : 1;
        double maxHaulCapacity = freighterCount * BASE_FREIGHTER_CAPACITY_KG;
        double desiredTransfer = Math.min(route.transferAmountPerTurnKg(), maxHaulCapacity);
        double roomAtDest = Math.max(0.0, route.maxDestinationCapacityKg() - destStock);

        return Math.min(availableSurplus, Math.min(desiredTransfer, roomAtDest));
    }

    private void transferStock(TradeRoute route, CommercialHub originHub, CommercialHub destHub,
                               double actualTransfer, Map<String, CommercialHub> hubMap) {
        MarketOrder originOrder = originHub.activeOrders().get(route.materialId());
        double sourceStock = originOrder != null ? originOrder.supplyKg() : 0.0;

        Map<String, MarketOrder> originOrders = new HashMap<>(originHub.activeOrders());
        if (originOrder != null) {
            originOrders.put(route.materialId(), new MarketOrder(
                    route.materialId(), Math.max(0.0, sourceStock - actualTransfer),
                    originOrder.demandKg(), originOrder.pricePerKg(), originOrder.shortcomingScore()
            ));
        }
        CommercialHub updatedOrigin = new CommercialHub(
                originHub.id(), originHub.entityId(), originHub.transactionTariffRate(),
                originHub.storageCapacityKg(),
                Math.max(0.0, originHub.currentStoredWeightKg() - actualTransfer),
                originHub.logisticsRangeUnits(), originOrders
        );
        hubMap.put(updatedOrigin.id(), updatedOrigin);

        MarketOrder destOrder = destHub.activeOrders().get(route.materialId());
        double destStock = destOrder != null ? destOrder.supplyKg() : 0.0;
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
    }

    private void applyTransitTariffs(TradeRoute route, double actualTransfer, Map<String, Empire> empireMap) {
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
    }

    private RouteStep load(GameState state, TradeRoute route, Carrier carrier) {
        return load(state, route, carrier, true);
    }

    /** Pure candidate execution on an immutable snapshot; the tick commits only its selected candidate. */
    public ShipmentPreview previewShipment(GameState state, TradeRoute route) {
        return previewShipment(state, route, Double.POSITIVE_INFINITY);
    }

    public ShipmentPreview previewShipment(GameState state, TradeRoute route, double maximumKg) {
        var carrier = findCarrier(state, route);
        if (carrier == null) return null;
        var step = load(state, route, carrier, false, maximumKg);
        var fleet = step.state().fleets().stream().filter(item -> item.id().equals(carrier.fleet().id())).findFirst().orElseThrow();
        return TradeRoute.DELIVERING.equals(step.route().phase()) && (fleet.hasInterstellarOrder() || fleet.location().inTransit())
                ? new ShipmentPreview(step.state(), step.route(), step.preparedFleet()) : null;
    }

    private RouteStep roam(GameState state, TradeRoute route, Carrier carrier) {
        var origin = hub(state, route.originEntityId());
        if (origin == null || !FleetPositioning.atHub(state, carrier.fleet(), origin))
            return load(state, route, carrier, false);
        var selected = RoamingTradePlanner.choose(state, route, carrier.fleet());
        return selected.shipment() == null ? new RouteStep(state, route.withStatus(selected.explanation()), 0)
                : new RouteStep(selected.shipment().state(), selected.shipment().route().withStatus(selected.explanation()), 0);
    }

    private RouteStep load(GameState state, TradeRoute route, Carrier carrier, boolean chooseMarket) {
        return load(state, route, carrier, chooseMarket, Double.POSITIVE_INFINITY);
    }

    private RouteStep load(GameState state, TradeRoute route, Carrier carrier, boolean chooseMarket, double maximumKg) {
        CommercialHub origin = hub(state, route.originEntityId());
        if (origin == null || FleetPositioning.hubSite(state, origin) == null)
            return new RouteStep(state, route, 0.0);
        boolean surface = FleetPositioning.hubSite(state, origin).kind()
                == FleetLocation.Kind.SURFACE;
        boolean orbitalPickup = surface && carrier.fleet().location()
                .isAt(FleetLocation.Site.orbit(origin.entityId()));
        if (!FleetPositioning.atHub(state, carrier.fleet(), origin) && !orbitalPickup)
            return moveRoute(state, route, carrier.fleet().id(), origin);
        MarketChoice choice = chooseMarket ? bestPricedMarketChoice(state, route, origin, carrier.fleet()) : null;
        if (choice != null) route = route.withMarketChoice(choice.materialId(),
                choice.destinationHubId());
        CommercialHub destination = hub(state, route.destinationEntityId());
        if (destination == null || FleetPositioning.hubSite(state, destination) == null)
            return new RouteStep(state, route, 0.0);
        MarketOrder order = origin.activeOrders().get(route.materialId());
        if (order == null || !Double.isFinite(order.pricePerKg())
                || order.pricePerKg() < 0.0) return new RouteStep(state, route, 0.0);
        double cargo = carrier.ship().storedCargoKg().values().stream()
                .mapToDouble(Double::doubleValue).sum();
        double available = Math.max(0.0,
                order.supplyKg() - route.minSourceInventoryThresholdKg());
        double quantity = Math.min(Math.min(maximumKg, route.transferAmountPerTurnKg()),
                Math.min(available, Math.min(room(destination, route),
                        carrier.design().maxCargoMassKg() - cargo
                                - carrier.ship().passengerCount() * 80.0)));
        if (!Double.isFinite(quantity) || quantity <= 0.000001)
            return new RouteStep(state, route, 0.0);
        Shipment affordable = affordableShipment(state, route, origin, carrier.design(),
                quantity, order.pricePerKg(), balance(state, route.ownerEntityId()),
                orbitalPickup, cargo + carrier.ship().currentFuelKg() + carrier.ship().generatorFuelMassKg()
                        + carrier.ship().passengerCount() * 80.0,
                carrier.ship().passengerCount() > 0);
        if (affordable == null) return new RouteStep(state, route, 0);
        quantity = affordable.massKg();
        double lift = LaunchService.payerOperatingCost(state, affordable.launch(),
                route.ownerEntityId());
        if (surface && !orbitalPickup) lift = 0; // Settle the complete carrier launch after loading and refueling.
        double purchase = quantity * order.pricePerKg();
        if (!Double.isFinite(lift) || !Double.isFinite(purchase)
                || quantity <= 0.000001)
            return new RouteStep(state, route, 0.0);
        GameState current = settleHub(state, route.ownerEntityId(), origin.id(), -purchase);
        if (affordable.launch() != null && orbitalPickup)
            current = LaunchService.settle(current, route.ownerEntityId(), affordable.launch());
        current = changeHubStock(current, origin.id(), route.materialId(), -quantity,
                order.pricePerKg());
        current = changeShipCargo(current, carrier.ship().id(), route.materialId(), quantity);
        double beforeMove = balance(current, route.ownerEntityId());
        if (route.roaming() || !FleetPositioning.systemForHub(current, destination).equals(carrier.fleet().currentSystemId())) {
            Fleet loaded = current.fleets().stream().filter(item -> item.id().equals(carrier.fleet().id())).findFirst().orElseThrow();
            current = TradeLegReadiness.prepare(current, loaded, destination);
            loaded = current.fleets().stream().filter(item -> item.id().equals(carrier.fleet().id())).findFirst().orElseThrow();
            if (!TradeLegReadiness.inspect(current, loaded, destination).ready()) return new RouteStep(state, route, 0);
        }
        Fleet prepared = current.fleets().stream().filter(item -> item.id().equals(carrier.fleet().id())).findFirst().orElseThrow();
        if (surface && !orbitalPickup && !route.roaming()) {
            CarrierDeparture departure = departFromSurface(current, carrier.fleet().id(), origin.entityId());
            current = departure.state();
            lift = departure.launchCostCredits();
        } else current = moveToward(current, carrier.fleet().id(), destination);
        double fuelCost = Math.max(0.0, beforeMove - balance(current, route.ownerEntityId())
                - (surface && !orbitalPickup ? lift : 0));
        return new RouteStep(current, route.withLoadedCargo(quantity, purchase + lift)
                .withOperatingCost(fuelCost), 0.0, prepared);
    }

    private MarketChoice bestPricedMarketChoice(GameState state, TradeRoute route,
                                                CommercialHub origin, Fleet fleet) {
        MarketChoice best = null;
        String fromSystem = FleetPositioning.systemForHub(state, origin);
        FleetLocation.Site fromSite = FleetPositioning.hubSite(state, origin);
        for (MarketOrder source : origin.activeOrders().values()) {
            if (!Double.isFinite(source.supplyKg())
                    || source.supplyKg() <= route.minSourceInventoryThresholdKg()
                    || !Double.isFinite(source.pricePerKg()) || source.pricePerKg() < 0.0) continue;
            for (CommercialHub candidate : state.commercialHubs()) {
                if (candidate.id().equals(origin.id())
                        || FleetPositioning.hubSite(state, candidate) == null) continue;
                MarketOrder buyer = candidate.activeOrders().get(source.resourceId());
                if (buyer == null || buyer.demandKg() <= 0.0 || buyer.pricePerKg() < 0.0
                        || hubCash(state, candidate.id()) <= 0.0) continue;
                String toSystem = FleetPositioning.systemForHub(state, candidate);
                if (toSystem != null && !toSystem.equals(fleet.currentSystemId())) {
                    var supplied = TradeLegReadiness.prepare(state, fleet, candidate);
                    var ready = supplied.fleets().stream().filter(item -> item.id().equals(fleet.id())).findFirst().orElseThrow();
                    if (!TradeLegReadiness.inspect(supplied, ready, candidate).ready()) continue;
                }
                double transitDays = fromSystem != null && fromSystem.equals(toSystem)
                        ? FleetLocation.travelDays(fromSite, FleetPositioning.hubSite(state, candidate))
                        : 8.0;
                double wholesaleBid = buyer.pricePerKg() * IndustryMarketProcessor.WHOLESALE_SHARE;
                double transportPenalty = Math.max(0.01, source.pricePerKg() * 0.025) * transitDays;
                double margin = wholesaleBid - source.pricePerKg() - transportPenalty;
                if (margin <= 0.0) continue;
                boolean resupply = com.spaceconquest.engine.ship.FleetPortReadiness.inspect(state, fleet, candidate).available();
                double pricePull = 1.0 + Math.clamp(buyer.demandKg()
                        / Math.max(1.0, buyer.supplyKg()), 0.0, 2.0);
                double score = margin * pricePull;
                if (best == null || resupply && !best.resupplyAvailable()
                        || resupply == best.resupplyAvailable() && score > best.score())
                    best = new MarketChoice(source.resourceId(), candidate.id(), score, resupply);
            }
        }
        return best;
    }

    private RouteStep deliver(GameState state, TradeRoute route, Carrier carrier) {
        CommercialHub destination = hub(state, route.destinationEntityId());
        if (destination == null) return new RouteStep(state, route, 0.0);
        if (!FleetPositioning.atHub(state, carrier.fleet(), destination))
            return moveRoute(state, route, carrier.fleet().id(), destination);
        MarketOrder destinationOrder = destination.activeOrders().get(route.materialId());
        double postedPrice = destinationOrder == null
                ? MarketProcessor.basePricePerKg(route.materialId())
                : destinationOrder.pricePerKg();
        if (!Double.isFinite(postedPrice) || postedPrice < 0.0)
            return new RouteStep(state, route, 0.0);
        double bid = Math.max(0.01, postedPrice) * IndustryMarketProcessor.WHOLESALE_SHARE;
        double quantity = Math.min(route.onboardKg(), Math.min(room(destination, route),
                Math.min(carrier.ship().storedCargoKg().getOrDefault(route.materialId(), 0.0),
                        hubCash(state, destination.id()) / bid)));
        if (quantity <= 0.000001) return new RouteStep(state, route, 0.0);
        double sale = quantity * bid;
        String controller = controllingEmpire(state,
                FleetPositioning.systemForHub(state, destination));
        double tariff = controller == null ? 0.0
                : sale * Math.clamp(destination.transactionTariffRate(), 0.0, 1.0);
        GameState current = settleHub(state, route.ownerEntityId(), destination.id(), sale);
        current = charge(current, route.ownerEntityId(), controller, 0.0, tariff);
        current = changeHubStock(current, destination.id(), route.materialId(),
                quantity, postedPrice);
        current = changeShipCargo(current, carrier.ship().id(), route.materialId(), -quantity);
        TradeRoute delivered = route.withDeliveredCargo(quantity, sale, tariff);
        if (route.roaming() && delivered.onboardKg() <= 1e-6)
            return new RouteStep(current, delivered.atNewOrigin(destination.id()), quantity);
        CommercialHub origin = hub(current, route.originEntityId());
        double beforeMove = balance(current, route.ownerEntityId());
        if (TradeRoute.RETURNING.equals(delivered.phase()) && origin != null)
            current = moveToward(current, carrier.fleet().id(), origin);
        double fuelCost = Math.max(0.0, beforeMove - balance(current, route.ownerEntityId()));
        return new RouteStep(current, delivered.withOperatingCost(fuelCost), quantity);
    }

    private RouteStep returnCarrier(GameState state, TradeRoute route, Carrier carrier) {
        CommercialHub origin = hub(state, route.originEntityId());
        if (origin == null) return new RouteStep(state, route, 0.0);
        if (FleetPositioning.atHub(state, carrier.fleet(), origin))
            return new RouteStep(state, route.withTrip(TradeRoute.LOADING, 0.0, 0.0), 0.0);
        return moveRoute(state, route, carrier.fleet().id(), origin);
    }

    private RouteStep moveRoute(GameState state, TradeRoute route, String fleetId,
                               CommercialHub destination) {
        double before = balance(state, route.ownerEntityId());
        GameState moved = moveToward(state, fleetId, destination);
        double fuelCost = Math.max(0.0, before - balance(moved, route.ownerEntityId()));
        return new RouteStep(moved, route.withOperatingCost(fuelCost), 0.0);
    }

    private CommercialHub hub(GameState state, String hubId) {
        return state.commercialHubs().stream().filter(item -> hubId.equals(item.id()))
                .findFirst().orElse(null);
    }

    private double room(CommercialHub hub, TradeRoute route) {
        MarketOrder order = hub.activeOrders().get(route.materialId());
        double materialStock = order == null ? 0.0 : order.supplyKg();
        double reserveRoom = Math.max(0.0,
                MarketStockpilePolicy.targetStockKg(order) - materialStock);
        return Math.max(0.0, Math.min(route.maxDestinationCapacityKg() - materialStock,
                Math.min(hub.storageCapacityKg() - hub.currentStoredWeightKg(), reserveRoom)));
    }

    private GameState changeHubStock(GameState state, String hubId, String materialId,
                                     double delta, double defaultPrice) {
        List<CommercialHub> hubs = new ArrayList<>(state.commercialHubs());
        for (int index = 0; index < hubs.size(); index++) {
            CommercialHub hub = hubs.get(index);
            if (!hubId.equals(hub.id())) continue;
            Map<String, MarketOrder> orders = new HashMap<>(hub.activeOrders());
            MarketOrder old = orders.get(materialId);
            orders.put(materialId, new MarketOrder(materialId,
                    Math.max(0.0, (old == null ? 0.0 : old.supplyKg()) + delta),
                    old == null ? 0.0 : old.demandKg(),
                    old == null ? defaultPrice : old.pricePerKg(),
                    old == null ? 0.0 : old.shortcomingScore()));
            hubs.set(index, new CommercialHub(hub.id(), hub.entityId(),
                    hub.transactionTariffRate(), hub.storageCapacityKg(),
                    Math.max(0.0, hub.currentStoredWeightKg() + delta),
                    hub.logisticsRangeUnits(), Map.copyOf(orders)));
            break;
        }
        return state.withCommercialHubs(hubs);
    }

    private GameState changeShipCargo(GameState state, String shipId, String materialId,
                                      double delta) {
        List<Fleet> fleets = new ArrayList<>(state.fleets());
        for (int fleetIndex = 0; fleetIndex < fleets.size(); fleetIndex++) {
            Fleet fleet = fleets.get(fleetIndex);
            List<ShipInstance> ships = new ArrayList<>(fleet.ships());
            for (int shipIndex = 0; shipIndex < ships.size(); shipIndex++) {
                ShipInstance ship = ships.get(shipIndex);
                if (!shipId.equals(ship.id())) continue;
                Map<String, Double> cargo = new HashMap<>(ship.storedCargoKg());
                cargo.put(materialId, Math.max(0.0,
                        cargo.getOrDefault(materialId, 0.0) + delta));
                ships.set(shipIndex, new ShipInstance(ship.id(), ship.designId(),
                        ship.ownerEntityId(), ship.currentHullHealth(),
                        ship.currentShieldHealth(), ship.currentFuelKg(), Map.copyOf(cargo),
                        ship.passengerCount(), ship.passengerRaceId(), ship.transitMode(), ship.powerState(), ship.supplyState()));
                fleets.set(fleetIndex, fleet.withShips(ships));
                return state.withFleets(fleets);
            }
        }
        return state;
    }

    private GameState moveToward(GameState state, String fleetId, CommercialHub destination) {
        String systemId = FleetPositioning.systemForHub(state, destination);
        FleetLocation.Site site = FleetPositioning.hubSite(state, destination);
        if (systemId == null || site == null) return state;
        List<Fleet> fleets = new ArrayList<>(state.fleets());
        for (int index = 0; index < fleets.size(); index++) {
            Fleet fleet = fleets.get(index);
            if (!fleetId.equals(fleet.id()) || fleet.hasInterstellarOrder()
                    || fleet.location().inTransit()) continue;
            Fleet moved = fleet;
            if (!systemId.equals(fleet.currentSystemId())) {
                GameState beforeDeparture = state;
                GameState supplied = TradeLegReadiness.prepare(state, fleet, destination);
                Fleet readyFleet = supplied.fleets().stream().filter(item -> item.id().equals(fleetId)).findFirst().orElseThrow();
                if (!TradeLegReadiness.inspect(supplied, readyFleet, destination).ready()) continue;
                state = supplied;
                fleets = new ArrayList<>(state.fleets());
                fleet = fleets.get(index);
                Fleet launchFleet = fleet;
                LocalTravel.Plan launchPlan = null;
                if (!fleet.location().isAt(FleetLocation.Site.deepSpace())) {
                    GameState ready = prepareLocalDeparture(state, fleet,
                            FleetLocation.Site.deepSpace());
                    if (ready == null) continue;
                    state = ready;
                    fleets = new ArrayList<>(state.fleets());
                    fleet = fleets.get(index);
                    LocalTravel.Plan local = LocalTravel.plan(state, fleet,
                            FleetLocation.Site.deepSpace());
                    launchFleet = fleet;
                    launchPlan = local;
                    fleet = LocalTravel.depart(fleet, FleetLocation.Site.deepSpace(), local);
                }
                com.spaceconquest.engine.ship.InterstellarTravel.Plan plan =
                        TradeLegReadiness.crossing(state, fleet, destination);
                if (plan == null || !com.spaceconquest.engine.ship.ShipPowerForecast.ready(
                        com.spaceconquest.engine.ship.ShipPowerForecast.departure(state, launchFleet,
                                FleetLocation.Site.deepSpace(), launchPlan, plan))) {
                    state = beforeDeparture;
                    fleets = new ArrayList<>(state.fleets());
                    continue;
                }
                Fleet fueled = com.spaceconquest.engine.ship.InterstellarTravel
                        .commitReactorFuel(fleet, plan);
                moved = new Fleet(fleet.id(), fleet.name(), fleet.ownerEntityId(),
                        fleet.currentSystemId(), systemId, fleet.coordinateX(),
                        fleet.coordinateY(), 0.0, false, fleet.fleetStance(),
                        fueled.ships(), fleet.location(), plan.mode(), plan.days(),
                        plan.distanceMeters(), plan.accelerationMps2(), 0.0,
                        plan.peakSpeedMps(), plan.fuelBudgetKg(), null, plan.propulsion());
            } else if (!fleet.location().isAt(site)) {
                GameState ready = prepareLocalDeparture(state, fleet, site);
                if (ready == null) continue;
                state = ready;
                fleets = new ArrayList<>(state.fleets());
                fleet = fleets.get(index);
                LocalTravel.Plan local = LocalTravel.plan(state, fleet, site);
                moved = LocalTravel.depart(fleet, site, local);
            }
            fleets.set(index, moved);
            break;
        }
        return state.withFleets(fleets);
    }

    private GameState prepareLocalDeparture(GameState state, Fleet fleet,
                                            FleetLocation.Site destination) {
        GameState ready = com.spaceconquest.engine.ship.ShipPowerResupply.prepareLocal(
                refuelForLocalLeg(state, fleet, destination), fleet, destination);
        ready = refuelForLocalLeg(ready, fleet, destination);
        Fleet updated = ready.fleets().stream().filter(item -> fleet.id().equals(item.id()))
                .findFirst().orElse(null);
        LocalTravel.Plan plan = updated == null ? null : LocalTravel.plan(ready, updated, destination);
        if (plan == null || !com.spaceconquest.engine.ship.ShipPowerForecast.ready(
                com.spaceconquest.engine.ship.ShipPowerForecast.departure(ready, updated, destination, plan, null)))
            return null;
        if (updated.location().current().kind() != FleetLocation.Kind.SURFACE)
            return ready;
        LaunchService.Plan launch = LocalTravel.surfaceLaunchPlan(ready, updated);
        return launch == null ? null : LaunchService.settle(ready,
                updated.ownerEntityId(), launch);
    }

    private GameState refuelForLocalLeg(GameState state, Fleet fleet,
                                       FleetLocation.Site destination) {
        if (LocalTravel.plan(state, fleet, destination) != null
                || fleet.location().current().kind() == FleetLocation.Kind.DEEP_SPACE)
            return state;
        String hubBody = fleet.location().current().entityId();
        GameState current = state;
        for (ShipInstance original : fleet.ships()) {
            ShipInstance ship = current.fleets().stream()
                    .flatMap(item -> item.ships().stream())
                    .filter(item -> original.id().equals(item.id())).findFirst().orElse(original);
            ShipDesign design = current.shipDesigns().stream()
                    .filter(item -> ship.designId().equals(item.id())).findFirst().orElse(null);
            if (design == null || PropulsionCatalog.mainDrive(design.equippedModuleIds()) == null)
                continue;
            double needed = LocalTravel.requiredPropellantKg(design, ship,
                    fleet.location().current(), destination);
            if (!Double.isFinite(needed) || needed <= 0.0) continue;
            double room = design.fuelCapacityKg() - ship.currentFuelKg();
            double quantity = Math.min(room,
                    Math.max(1.0, needed * 1.05 - ship.currentFuelKg()));
            if (quantity > 0.0)
                current = ShipFueling.refuel(current, ship.id(), hubBody, quantity);
        }
        return current;
    }

    private double balance(GameState state, String ownerId) {
        return state.corporations().stream().filter(item -> ownerId.equals(item.id()))
                .mapToDouble(Corporation::liquidCapitalReserves).findFirst().orElseGet(() ->
                state.empires().stream().filter(item -> ownerId.equals(item.id()))
                        .mapToDouble(Empire::treasuryCredits).findFirst().orElse(0.0));
    }

    private double hubCash(GameState state, String hubId) {
        return state.marketAccounts().stream().filter(account -> hubId.equals(account.hubId()))
                .mapToDouble(MarketAccount::unsettledSalesCredits).findFirst().orElse(0.0);
    }

    private Shipment affordableShipment(GameState state, TradeRoute route, CommercialHub origin,
                                        ShipDesign design, double desiredKg, double pricePerKg,
                                        double availableCredits, boolean orbitalPickup,
                                        double onboardPayloadKg, boolean passengers) {
        Shipment desired = affordableCandidate(state, route, origin, design, desiredKg,
                pricePerKg, availableCredits, orbitalPickup, onboardPayloadKg, passengers);
        if (desired != null) return desired;
        double low = 0.0;
        double high = desiredKg;
        for (int attempt = 0; attempt < 36; attempt++) {
            double trial = (low + high) * 0.5;
            if (affordableCandidate(state, route, origin, design, trial, pricePerKg,
                    availableCredits, orbitalPickup, onboardPayloadKg, passengers) != null) low = trial;
            else high = trial;
        }
        if (low <= 0.000001) return new Shipment(0.0, null);
        return affordableCandidate(state, route, origin, design, low, pricePerKg,
                availableCredits, orbitalPickup, onboardPayloadKg, passengers);
    }

    private Shipment affordableCandidate(GameState state, TradeRoute route,
                                          CommercialHub origin, ShipDesign design,
                                          double quantity, double pricePerKg,
                                          double availableCredits, boolean orbitalPickup,
                                          double onboardPayloadKg, boolean passengers) {
        if (quantity <= 0.0 || quantity * pricePerKg > availableCredits) return null;
        if (FleetPositioning.hubSite(state, origin).kind() != FleetLocation.Kind.SURFACE)
            return new Shipment(quantity, null);
        GameState afterCargo = changeHubStock(state, origin.id(), route.materialId(),
                -quantity, pricePerKg);
        double launchPayload = orbitalPickup ? quantity : quantity + onboardPayloadKg;
        LaunchService.Plan launch = LaunchService.choose(afterCargo, origin.entityId(),
                route.ownerEntityId(), launchPayload, passengers, !orbitalPickup,
                design.totalDryMassKg());
        return launch != null && quantity * pricePerKg
                + LaunchService.payerOperatingCost(state, launch, route.ownerEntityId())
                <= availableCredits + 0.000001 ? new Shipment(quantity, launch) : null;
    }

    private CarrierDeparture departFromSurface(GameState state, String fleetId, String bodyId) {
        Fleet departing = state.fleets().stream().filter(fleet -> fleet.id().equals(fleetId)).findFirst().orElse(null);
        if (departing == null) return new CarrierDeparture(state, 0);
        FleetLocation.Site destination = FleetLocation.Site.orbit(bodyId);
        GameState ready = com.spaceconquest.engine.ship.ShipPowerResupply.prepareLocal(
                refuelForLocalLeg(state, departing, destination), departing, destination);
        ready = refuelForLocalLeg(ready, departing, destination);
        Fleet updated = ready.fleets().stream().filter(fleet -> fleet.id().equals(fleetId)).findFirst().orElseThrow();
        LocalTravel.Plan plan = LocalTravel.plan(ready, updated, destination);
        if (plan == null || !com.spaceconquest.engine.ship.ShipPowerForecast.ready(
                com.spaceconquest.engine.ship.ShipPowerForecast.departure(ready, updated, destination, plan, null)))
            return new CarrierDeparture(state, 0);
        LaunchService.Plan launch = LocalTravel.surfaceLaunchPlan(ready, updated);
        if (launch == null) return new CarrierDeparture(state, 0);
        ready = LaunchService.settle(ready, updated.ownerEntityId(), launch);
        state = ready;
        GameState departureState = state;
        List<Fleet> fleets = state.fleets().stream().map(fleet ->
                fleetId.equals(fleet.id()) ? LocalTravel.depart(fleet,
                        FleetLocation.Site.orbit(bodyId), LocalTravel.plan(departureState, fleet,
                                FleetLocation.Site.orbit(bodyId))) : fleet).toList();
        return new CarrierDeparture(state.withFleets(fleets),
                LaunchService.payerOperatingCost(ready, launch, updated.ownerEntityId()));
    }

    /** Positive ownerDelta sells to the hub; negative ownerDelta buys from it. */
    private GameState settleHub(GameState state, String ownerId, String hubId,
                                double ownerDelta) {
        if (ownerDelta == 0.0) return state;
        List<MarketAccount> accounts = new ArrayList<>(state.marketAccounts());
        accounts.removeIf(account -> hubId.equals(account.hubId()));
        accounts.add(new MarketAccount(hubId,
                Math.max(0.0, hubCash(state, hubId) - ownerDelta)));
        List<Corporation> corporations = state.corporations().stream().map(item ->
                ownerId.equals(item.id()) ? new Corporation(item.id(), item.name(),
                        item.empireId(), item.headquartersEntityId(), item.marketOrientation(),
                        item.liquidCapitalReserves() + ownerDelta, item.ownedFacilityIds(),
                        item.ownedShipIds(), item.claimedVeinIds()) : item).toList();
        List<Empire> empires = state.empires().stream().map(item ->
                ownerId.equals(item.id()) ? new Empire(item.id(), item.name(), item.raceId(),
                        item.societyStructure(), item.treasuryCredits() + ownerDelta,
                        item.corporateTaxRate(), item.controlledSystemIds(), item.ministries(),
                        item.systemGovernorAssignments(), item.unlockedTechIds(),
                        item.activeShipDesignIds()) : item).toList();
        return state.toBuilder().marketAccounts(accounts).corporations(corporations)
                .empires(empires).build();
    }

    private String controllingEmpire(GameState state, String systemId) {
        return state.empires().stream().filter(empire ->
                empire.controlledSystemIds().contains(systemId))
                .map(Empire::id).findFirst().orElse(null);
    }

    private GameState charge(GameState state, String ownerId, String recipientId,
                             double lift, double tariff) {
        List<Corporation> corporations = state.corporations().stream().map(item ->
                ownerId.equals(item.id()) ? new Corporation(item.id(), item.name(),
                        item.empireId(), item.headquartersEntityId(), item.marketOrientation(),
                        item.liquidCapitalReserves() - lift - tariff, item.ownedFacilityIds(),
                        item.ownedShipIds(), item.claimedVeinIds()) : item).toList();
        List<Empire> empires = state.empires().stream().map(item -> {
            double delta = (recipientId != null && recipientId.equals(item.id()) ? tariff : 0.0)
                    - (ownerId.equals(item.id()) ? lift + tariff : 0.0);
            return delta == 0.0 ? item : new Empire(item.id(), item.name(), item.raceId(),
                    item.societyStructure(), item.treasuryCredits() + delta,
                    item.corporateTaxRate(), item.controlledSystemIds(), item.ministries(),
                    item.systemGovernorAssignments(), item.unlockedTechIds(),
                    item.activeShipDesignIds());
        }).toList();
        return state.toBuilder().corporations(corporations).empires(empires).build();
    }
}
