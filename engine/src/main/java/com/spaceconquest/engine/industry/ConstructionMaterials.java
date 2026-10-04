package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.MarketOrder;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.economy.MarketAccount;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FleetPositioning;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipInstance;
import com.spaceconquest.engine.ship.ShipRole;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Buys physical construction inputs from one body's hub and settles the owner's payment. */
public final class ConstructionMaterials {
    private ConstructionMaterials() {}

    public record Purchase(GameState state, Map<String, Double> acquiredKg) {
        public double totalKg() {
            return acquiredKg.values().stream().mapToDouble(Double::doubleValue).sum();
        }

        public boolean covers(Map<String, Double> bill) {
            return bill.entrySet().stream().allMatch(entry ->
                    acquiredKg.getOrDefault(entry.getKey(), 0.0) + 0.000001 >= entry.getValue());
        }
    }

    /** Resolves only a hub on the requested planet or moon. */
    public static String bodyForSystem(GameState state, String systemId, String preferredBodyId) {
        if (state == null || systemId == null || preferredBodyId == null) return null;
        SolarSystem system = state.solarSystems().stream()
                .filter(item -> item.id().equals(systemId)).findFirst().orElse(null);
        if (system == null) return null;
        List<String> bodies = new ArrayList<>();
        system.planets().forEach(planet -> {
            bodies.add(planet.id());
            planet.moons().forEach(moon -> bodies.add(moon.id()));
        });
        system.asteroidBelts().forEach(belt -> bodies.add(belt.id()));
        if (preferredBodyId != null && bodies.contains(preferredBodyId)
                && state.commercialHubs().stream().anyMatch(hub ->
                preferredBodyId.equals(hub.entityId()))) return preferredBodyId;
        return null;
    }

    /** Finds a hub on an orbital station at the specified site, never a surface hub. */
    public static String orbitalHubEntity(GameState state, String systemId, String siteId) {
        if (state == null || systemId == null) return null;
        return state.orbitalStations().stream()
                .filter(station -> systemId.equals(station.systemId())
                        && (siteId == null || siteId.equals(systemId)
                        || siteId.equals(station.id())
                        || siteId.equals(station.planetOrbitId())))
                .map(OrbitalStation::id)
                .filter(id -> state.commercialHubs().stream()
                        .anyMatch(hub -> id.equals(hub.entityId())))
                .findFirst().orElse(null);
    }

    /** Orbital projects can also consume already loaded cargo in an owner's local transport. */
    public static Purchase buyOrbitalUpTo(GameState state, String systemId, String siteId,
                                          String ownerId, Map<String, Double> requestedKg,
                                          double maxKg) {
        if (state == null || systemId == null || ownerId == null || requestedKg == null)
            return new Purchase(state, Map.of());
        String hubEntity = orbitalHubEntity(state, systemId, siteId);
        Purchase fromHub = buyUpTo(state, hubEntity, ownerId, requestedKg, maxKg);
        GameState current = fromHub.state();
        Map<String, Double> acquired = new HashMap<>(fromHub.acquiredKg());
        double remainingKg = Math.max(0.0, maxKg - fromHub.totalKg());
        if (remainingKg <= 0.000001) return fromHub;
        List<Fleet> fleets = new ArrayList<>(current.fleets());
        for (int fleetIndex = 0; fleetIndex < fleets.size() && remainingKg > 0.000001; fleetIndex++) {
            Fleet fleet = fleets.get(fleetIndex);
            if (!ownerId.equals(fleet.ownerEntityId())
                    || !FleetPositioning.atOrbitalSite(current, fleet, systemId, siteId)) continue;
            List<ShipInstance> ships = new ArrayList<>(fleet.ships());
            boolean changed = false;
            for (int shipIndex = 0; shipIndex < ships.size() && remainingKg > 0.000001; shipIndex++) {
                ShipInstance ship = ships.get(shipIndex);
                if (!ownerId.equals(ship.ownerEntityId()) || !isOrbitalTransport(current, ship)) continue;
                Map<String, Double> cargo = new HashMap<>(ship.storedCargoKg());
                boolean loaded = false;
                for (var request : new TreeMap<>(requestedKg).entrySet()) {
                    double needed = request.getValue() - acquired.getOrDefault(request.getKey(), 0.0);
                    double quantity = Math.min(remainingKg,
                            Math.min(Math.max(0.0, needed), cargo.getOrDefault(request.getKey(), 0.0)));
                    if (quantity <= 0.000001) continue;
                    cargo.put(request.getKey(), cargo.get(request.getKey()) - quantity);
                    acquired.merge(request.getKey(), quantity, Double::sum);
                    remainingKg -= quantity;
                    loaded = true;
                }
                if (loaded) {
                    ships.set(shipIndex, new ShipInstance(ship.id(), ship.designId(),
                            ship.ownerEntityId(), ship.currentHullHealth(), ship.currentShieldHealth(),
                            ship.currentFuelKg(), Map.copyOf(cargo), ship.passengerCount(),
                            ship.passengerRaceId(), ship.transitMode(), ship.powerState(), ship.supplyState()));
                    changed = true;
                }
            }
            if (changed) fleets.set(fleetIndex, fleet.withShips(ships));
        }
        return new Purchase(current.withFleets(fleets), Map.copyOf(acquired));
    }

    public static boolean isOrbitalTransport(GameState state, ShipInstance ship) {
        ShipDesign design = state.shipDesigns().stream()
                .filter(item -> item.id().equals(ship.designId())).findFirst().orElse(null);
        return design != null && (ShipRole.CARGO_TRANSPORT.equalsIgnoreCase(design.role())
                || ShipRole.CONSTRUCTION_SHIP.equalsIgnoreCase(design.role()));
    }

    public static String systemForBody(GameState state, String bodyId) {
        if (state == null || bodyId == null) return null;
        String stationSystem = state.orbitalStations().stream()
                .filter(station -> bodyId.equals(station.id()))
                .map(OrbitalStation::systemId).findFirst().orElse(null);
        if (stationSystem != null) return stationSystem;
        for (SolarSystem system : state.solarSystems()) {
            if (system.asteroidBelts().stream().anyMatch(belt -> bodyId.equals(belt.id())))
                return system.id();
            for (var planet : system.planets()) {
                if (bodyId.equals(planet.id()) || planet.moons().stream()
                        .anyMatch(moon -> bodyId.equals(moon.id()))) return system.id();
            }
        }
        return null;
    }

    public static Purchase buyUpTo(GameState state, String bodyId, String ownerId,
                                   Map<String, Double> requestedKg) {
        return buyUpTo(state, bodyId, ownerId, requestedKg, Double.POSITIVE_INFINITY);
    }

    public static Purchase buyUpTo(GameState state, String bodyId, String ownerId,
                                   Map<String, Double> requestedKg, double maxKg) {
        if (state == null || bodyId == null || ownerId == null || requestedKg == null) {
            return new Purchase(state, Map.of());
        }
        Corporation corporation = state.corporations().stream()
                .filter(item -> ownerId.equals(item.id())).findFirst().orElse(null);
        Empire empire = state.empires().stream()
                .filter(item -> ownerId.equals(item.id())).findFirst().orElse(null);
        if (corporation == null && empire == null) return new Purchase(state, Map.of());
        boolean hive = corporation == null && empire.societyStructure() != null
                && empire.societyStructure().toLowerCase().contains("hive");
        double cash = corporation != null ? corporation.liquidCapitalReserves()
                : hive ? Double.POSITIVE_INFINITY : empire.treasuryCredits();
        CommercialHub hub = state.commercialHubs().stream()
                .filter(item -> bodyId.equals(item.entityId())).findFirst().orElse(null);
        if (hub == null) return new Purchase(state, Map.of());
        Map<String, MarketOrder> orders = new HashMap<>(hub.activeOrders());
        Map<String, Double> acquired = new HashMap<>();
        double spent = 0.0;
        double remainingKg = Math.max(0.0, maxKg);
        for (var request : new TreeMap<>(requestedKg).entrySet()) {
            if (remainingKg <= 0.000001) break;
            double desired = request.getValue();
            if (!Double.isFinite(desired) || desired <= 0.0) continue;
            MarketOrder order = orders.get(request.getKey());
            if (order == null || !Double.isFinite(order.supplyKg()) || order.supplyKg() <= 0.0
                    || !Double.isFinite(order.pricePerKg()) || order.pricePerKg() < 0.0) continue;
            double price = order.pricePerKg();
            double affordable = hive || price == 0.0 ? desired : Math.max(0.0, cash - spent) / price;
            double quantity = Math.min(remainingKg, Math.min(desired,
                    Math.min(order.supplyKg(), affordable)));
            if (quantity <= 0.0) continue;
            acquired.put(request.getKey(), quantity);
            spent += quantity * price;
            remainingKg -= quantity;
            orders.put(request.getKey(), new MarketOrder(order.resourceId(),
                    Math.max(0.0, order.supplyKg() - quantity), order.demandKg(),
                    order.pricePerKg(), order.shortcomingScore()));
        }
        if (acquired.isEmpty()) return new Purchase(state, Map.of());
        CommercialHub updatedHub = new CommercialHub(hub.id(), hub.entityId(),
                hub.transactionTariffRate(), hub.storageCapacityKg(),
                Math.max(0.0, hub.currentStoredWeightKg()
                        - acquired.values().stream().mapToDouble(Double::doubleValue).sum()),
                hub.logisticsRangeUnits(), Map.copyOf(orders));
        List<CommercialHub> hubs = new ArrayList<>(state.commercialHubs());
        hubs.set(hubs.indexOf(hub), updatedHub);
        GameState.Builder builder = state.toBuilder().commercialHubs(hubs);
        if (!hive) {
            double settled = spent;
            List<MarketAccount> accounts = new ArrayList<>(state.marketAccounts());
            MarketAccount old = accounts.stream().filter(account -> hub.id().equals(account.hubId()))
                    .findFirst().orElse(null);
            if (old != null) accounts.remove(old);
            accounts.add(new MarketAccount(hub.id(),
                    (old == null ? 0.0 : old.unsettledSalesCredits()) + settled));
            builder.marketAccounts(accounts);
            if (corporation != null) {
                List<Corporation> corporations = state.corporations().stream().map(item ->
                        item.id().equals(ownerId) ? new Corporation(item.id(), item.name(),
                                item.empireId(), item.headquartersEntityId(), item.marketOrientation(),
                                Math.max(0.0, item.liquidCapitalReserves() - settled),
                                item.ownedFacilityIds(), item.ownedShipIds(), item.claimedVeinIds())
                                : item).toList();
                builder.corporations(corporations);
            } else {
                List<Empire> empires = state.empires().stream().map(item ->
                        item.id().equals(ownerId) ? new Empire(item.id(), item.name(), item.raceId(),
                                item.societyStructure(), Math.max(0.0, item.treasuryCredits() - settled),
                                item.corporateTaxRate(), item.controlledSystemIds(), item.ministries(),
                                item.systemGovernorAssignments(), item.unlockedTechIds(),
                                item.activeShipDesignIds()) : item).toList();
                builder.empires(empires);
            }
        }
        return new Purchase(builder.build(), Map.copyOf(acquired));
    }
}
