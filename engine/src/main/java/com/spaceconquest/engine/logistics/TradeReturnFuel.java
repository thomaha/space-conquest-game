package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.ConstructionMaterials;
import com.spaceconquest.engine.ship.*;
import java.util.*;

/** Tick-owned direct-return insurance. Candidate failures discard every purchase. */
final class TradeReturnFuel {
    private TradeReturnFuel() {}
    record Departure(GameState state, Fleet planningFleet, LocalTravel.Plan localPlan) {}
    /** Extra reserve belongs to the departure trajectory; it never changes the live fleet's normal policy. */
    static Departure departure(GameState state, Fleet fleet, CommercialHub origin, CommercialHub destination, String shipId,
                               Map<String, TradeCargo> freight, boolean roaming) {
        boolean local = fleet.currentSystemId().equals(FleetPositioning.systemForHub(state, destination));
        boolean protectedLocal = local && fleet.location().current().kind() != FleetLocation.Kind.SURFACE
                && FleetPositioning.hubSite(state, destination).kind() != FleetLocation.Kind.SURFACE;
        var original = fleet.fuelPolicy();
        var attempted = new HashSet<FleetFuelPolicy>();
        for (double fraction : protectedLocal ? TradeDepartureFuelPolicy.current().reserveFractions() : List.of(0.0)) {
            var reserve = new FleetFuelPolicy(Math.max(original.routineFraction(), fraction), Math.max(original.elevatedFraction(), fraction), Math.max(original.wartimeFraction(), fraction));
            if (!attempted.add(reserve)) continue;
            var planning = fleet.withFuelPolicy(reserve);
            var plan = protectedLocal ? TradeLocalPlan.plan(state, planning, FleetPositioning.hubSite(state, destination)) : null;
            if (protectedLocal && plan == null || !TradeLegReadiness.inspect(state, planning, destination).ready()) continue;
            var funded = secure(state, planning, origin, destination, shipId, freight, roaming, plan);
            if (funded != null) return new Departure(funded, planning, plan);
        }
        return null;
    }

    /** Null means neither carried working stores nor a paid destination reservation fund the return. */
    static GameState secure(GameState state, Fleet loaded, CommercialHub origin, CommercialHub destination,
                            String freighterId, Map<String, TradeCargo> freight, boolean roaming, LocalTravel.Plan localPlan) {
        if (loaded.ships().stream().allMatch(ship -> FleetSupplySimulation.design(state, ship).powerProfile() == null)) return state;
        var arrival = localPlan == null ? arrival(state, loaded, destination)
                : LocalTravel.arrivalPreview(state, loaded, FleetPositioning.hubSite(state, destination), localPlan).withFuelPolicy(fleet(state, loaded.id()).fuelPolicy());
        if (arrival == null) return null;
        var loadedArrival = arrival;
        arrival = arrival.withShips(arrival.ships().stream().map(ship -> {
            if (!ship.id().equals(freighterId)) return ship;
            var cargo = new HashMap<>(ship.storedCargoKg());
            freight.forEach((id, lot) -> cargo.computeIfPresent(id, (key, kg) -> Math.max(0, kg - lot.massKg())));
            return copy(ship, ship.currentFuelKg(), cargo);
        }).toList());
        var quote = localPlan == null ? RoamingTradeCost.quote(state, loaded, origin, destination) : null;
        if (localPlan == null && quote == null) return null;
        double days = localPlan == null ? quote.days() : Math.ceil(localPlan.days());
        var projected = replace(state.withTurn(state.turn() + (long) Math.ceil(days)), arrival);
        if (TradeLegReadiness.inspect(projected, arrival, origin).ready()) return state;
        if (roaming) {
            for (var exit : state.commercialHubs()) {
                if (!exit.id().equals(destination.id()) && TradeLegReadiness.inspect(projected, arrival, exit).ready()) return state;
            }
        }
        var funded = TradeReturnFuelSizing.prepare(projected, arrival, origin);
        if (funded == null) return deliverySupportsReturn(state, loadedArrival, origin, destination, freighterId, freight, days) ? state : null;
        var demands = new HashMap<String, Map<String, Double>>();
        var request = new HashMap<String, Double>();
        for (var ship : arrival.ships()) {
            var supplied = funded.ships().stream().filter(item -> item.id().equals(ship.id())).findFirst().orElseThrow();
            var previous = destination.reservedTradeFuel().get(ship.id());
            if (previous != null && (!previous.ownerEntityId().equals(ship.ownerEntityId())
                    || !previous.designId().equals(ship.designId()))) return null;
            var needed = addedMaterials(state, ship, supplied);
            if (previous != null) previous.materialsKg().forEach((id, kg) ->
                    needed.computeIfPresent(id, (key, mass) -> Math.max(0, mass - kg)));
            needed.values().removeIf(kg -> kg <= 0);
            demands.put(ship.id(), needed);
            needed.forEach((id, kg) -> request.merge(id, kg, Double::sum));
        }
        if (request.isEmpty()) return state; // An existing private reservation funds this ship.
        var purchase = ConstructionMaterials.buyUpTo(state, destination.entityId(), loaded.ownerEntityId(), request);
        if (!purchase.covers(request)) return deliverySupportsReturn(state, loadedArrival, origin, destination, freighterId, freight, days) ? state : null;
        var paid = hub(purchase.state(), destination.id());
        var reservations = new HashMap<>(paid.reservedTradeFuel());
        var acquired = new HashMap<>(purchase.acquiredKg());
        for (var ship : loaded.ships()) {
            if (demands.get(ship.id()).isEmpty()) continue;
            var previous = reservations.get(ship.id());
            var materials = new HashMap<>(previous == null ? Map.<String, Double>of() : previous.materialsKg());
            demands.get(ship.id()).forEach((id, kg) -> {
                double allocated = Math.min(kg, acquired.getOrDefault(id, 0.0));
                materials.merge(id, allocated, Double::sum);
                acquired.put(id, acquired.getOrDefault(id, 0.0) - allocated);
            });
            reservations.put(ship.id(), new ReservedTradeFuel(ship.ownerEntityId(), ship.designId(), materials));
        }
        return replaceHub(purchase.state(), paid.withReservedTradeFuel(
                paid.currentStoredWeightKg() + purchase.totalKg(), reservations));
    }
    private static boolean deliverySupportsReturn(GameState state, Fleet arrival, CommercialHub origin, CommercialHub destination,
                                                 String shipId, Map<String, TradeCargo> freight, double days) {
        var fuels = new HashSet<String>();
        for (var ship : arrival.ships()) {
            var design = FleetSupplySimulation.design(state, ship);
            var drive = PropulsionCatalog.mainDrive(design.equippedModuleIds());
            if (drive != null) {
                fuels.addAll(drive.propellantMaterials(1).keySet());
                PropulsionCatalog.reactorFuels(drive.moduleId()).forEach(fuel -> fuels.add(fuel.materialId()));
            }
            if (design.powerProfile() != null) design.powerProfile().fuels().values().forEach(fuel -> fuels.addAll(fuel.materials(1).keySet()));
        }
        if (freight.keySet().stream().noneMatch(fuels::contains)) return false;
        var source = state.tradeRoutes().stream().filter(route -> route.assignedFreighterIds().contains(shipId)).findFirst().orElse(null);
        if (source == null) return false;
        var route = source.atNewOrigin(origin.id()).withDestination(destination.id()).withActive(false).withRoaming(false).withLoadedManifest(freight);
        var projected = replace(state.withTurn(state.turn() + (long) Math.ceil(days)), arrival);
        return new LogisticsProcessor().deliveryFundsReturn(projected, route);
    }

    private static HashMap<String, Double> addedMaterials(GameState state, ShipInstance before, ShipInstance after) {
        var result = new HashMap<String, Double>();
        var design = FleetSupplySimulation.design(state, before);
        var drive = PropulsionCatalog.mainDrive(design.equippedModuleIds());
        if (drive != null) result.putAll(drive.propellantMaterials(Math.max(0, after.currentFuelKg() - before.currentFuelKg())));
        after.storedCargoKg().forEach((id, kg) -> {
            double added = kg - before.storedCargoKg().getOrDefault(id, 0.0);
            if (added > 0) result.merge(id, added, Double::sum);
        });
        ShipPowerProcessor.reserves(after).generatorMaterialsKg().forEach((id, kg) -> {
            double added = kg - ShipPowerProcessor.reserves(before).generatorMaterialsKg().getOrDefault(id, 0.0);
            if (added > 0) result.merge(id, added, Double::sum);
        });
        return result;
    }

    /** Moves only this ship's already paid fuel into compatible working stores at its physical port. */
    static GameState claim(GameState state, Fleet fleet) {
        if (fleet.hasInterstellarOrder() || fleet.location().inTransit()) return state;
        var port = state.commercialHubs().stream().filter(item -> FleetPositioning.atHub(state, fleet, item))
                .findFirst().orElse(null);
        if (port == null || port.reservedTradeFuel().isEmpty()) return state;
        GameState current = state;
        for (var original : fleet.ships()) current = claimShip(current, fleet.id(), original.id(), port.id());
        return current;
    }

    private static GameState claimShip(GameState state, String fleetId, String shipId, String hubId) {
        var port = hub(state, hubId);
        var reserved = port.reservedTradeFuel().get(shipId);
        var ship = fleet(state, fleetId).ships().stream().filter(item -> item.id().equals(shipId)).findFirst().orElseThrow();
        if (reserved == null || !reserved.ownerEntityId().equals(ship.ownerEntityId())
                || !reserved.designId().equals(ship.designId())) return state;
        var design = FleetSupplySimulation.design(state, ship);
        if (design == null) return state;
        // A private immutable transaction snapshot lets the ordinary fueling gates enforce mixtures,
        // tank space, feed space and lift charges. Public orders never see this temporary inventory.
        var orders = new HashMap<String, MarketOrder>();
        reserved.materialsKg().forEach((id, kg) -> orders.put(id, new MarketOrder(id, kg, 0, 0, 0)));
        var temporary = new CommercialHub(port.id(), port.entityId(), port.transactionTariffRate(), port.storageCapacityKg(),
                port.currentStoredWeightKg(), port.logisticsRangeUnits(), orders, port.stockTargets(), port.stockHistory(), port.reservedTradeFuel());
        var current = replaceHub(state, temporary);
        current = TradeLegReadiness.buyLargest(current, ship.id(), port.entityId(),
                design.fuelCapacityKg() - ship.currentFuelKg(), null);
        var profile = design.powerProfile();
        if (profile != null) {
            for (String id : List.of(ShipPowerProcessor.reserves(ship).chemicalMixture(), ShipPowerProcessor.reserves(ship).reactorFuel())) {
                var fuel = profile.fuels().get(id);
                if (fuel == null || (fuel.oxidizerId() == null ? profile.fissionKw() <= 0 : profile.chemicalKw() <= 0)) continue;
                var updated = fleet(current, fleetId).ships().stream().filter(item -> item.id().equals(shipId)).findFirst().orElseThrow();
                double occupied = fuel.materials(1).keySet().stream().mapToDouble(material ->
                        ShipPowerProcessor.reserves(updated).generatorMaterialsKg().getOrDefault(material, 0.0)).sum();
                double capacity = fuel.oxidizerId() == null ? profile.reactorTankKg() : profile.generatorTankKg();
                current = TradeLegReadiness.buyLargest(current, ship.id(), port.entityId(), capacity - occupied, id);
            }
        }
        var remaining = new HashMap<String, Double>();
        hub(current, hubId).activeOrders().forEach((id, order) -> {
            if (order.supplyKg() > 0) remaining.put(id, order.supplyKg());
        });
        double left = remaining.values().stream().mapToDouble(Double::doubleValue).sum();
        if (left == reserved.massKg()) return state;
        var reservations = new HashMap<>(port.reservedTradeFuel());
        if (remaining.isEmpty()) reservations.remove(shipId);
        else reservations.put(shipId, new ReservedTradeFuel(reserved.ownerEntityId(), reserved.designId(), remaining));
        return replaceHub(current, port.withReservedTradeFuel(port.currentStoredWeightKg() - reserved.massKg() + left, reservations));
    }

    static Fleet arrival(GameState state, Fleet fleet, CommercialHub destination) {
        var site = FleetPositioning.hubSite(state, destination);
        String system = FleetPositioning.systemForHub(state, destination);
        if (site == null || system == null) return null;
        if (fleet.currentSystemId().equals(system)) {
            var plan = TradeLocalPlan.plan(state, fleet, site);
            return plan == null ? null : LocalTravel.arrivalPreview(state, fleet, site, plan).withFuelPolicy(fleet(state, fleet.id()).fuelPolicy());
        }
        Fleet crossing = fleet;
        if (!fleet.location().isAt(FleetLocation.Site.deepSpace())) {
            var departure = TradeLegReadiness.departureLocal(state, fleet);
            if (departure == null) return null;
            crossing = LocalSpacePowerForecast.consume(state, fleet, FleetLocation.Site.deepSpace(), departure);
            crossing = LocalTravel.projectedArrival(crossing, FleetLocation.Site.deepSpace(), departure);
        }
        var plan = TradeLegReadiness.crossing(state, crossing, destination);
        if (plan == null) return null;
        Fleet arrival;
        if (Fleet.MODE_SUBLIGHT.equals(plan.mode()) && crossing.ships().stream().allMatch(ship -> {
            var design = FleetSupplySimulation.design(state, ship);
            return design.powerProfile() != null && PropulsionCatalog.mainDrive(design.equippedModuleIds()) != null;
        })) {
            var forecast = FleetPropulsionSupply.forecast(state, crossing, plan);
            if (!ShipPowerForecast.ready(forecast.electrical())) return null;
            arrival = forecast.arrival();
        } else {
            arrival = InterstellarTravel.commitReactorFuel(crossing, plan);
            arrival = arrival.withShips(arrival.ships().stream().map(ship -> copy(ship,
                    Math.max(0, ship.currentFuelKg() - plan.fuelBudgetKg().getOrDefault(ship.id(), 0.0)), ship.storedCargoKg())).toList());
            if (Fleet.MODE_WARP.equals(plan.mode())) {
                var ships = new ArrayList<ShipInstance>();
                for (var ship : arrival.ships()) {
                    var design = FleetSupplySimulation.design(state, ship);
                    var p = design.powerProfile(ship);
                    if (p == null) { ships.add(ship); continue; }
                    var step = ShipPowerProcessor.interval(p, ShipPowerProcessor.reserves(ship), Math.ceil(plan.days()) * 24, 0,
                            p.essentialKw(ship, design), ShipPowerProcessor.cargoKw(p, ship, design), p.driveKw());
                    if (!step.supplied()) return null;
                    ships.add(ship.withPowerState(step.state()));
                }
                arrival = arrival.withShips(ships);
            }
        }
        arrival = new Fleet(arrival.id(), arrival.name(), arrival.ownerEntityId(), system, "", 0, 0, 0,
                false, arrival.fleetStance(), arrival.ships(), FleetLocation.at(FleetLocation.Site.deepSpace())).withFuelPolicy(arrival.fuelPolicy()).withShieldPolicy(arrival.shieldPolicy()).withEscortOrder(arrival.escortOrder());
        var dock = TradeLocalPlan.plan(state, arrival, site);
        return dock == null ? null : LocalTravel.arrivalPreview(state, arrival, site, dock);
    }

    private static ShipInstance copy(ShipInstance ship, double fuel, Map<String, Double> cargo) {
        return new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), ship.currentHullHealth(), ship.currentShieldHealth(),
                fuel, cargo, ship.passengerCount(), ship.passengerRaceId(), ship.transitMode(), ship.powerState(), ship.supplyState(), ship.moduleDamage());
    }
    private static Fleet fleet(GameState state, String id) {
        return state.fleets().stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
    }
    private static CommercialHub hub(GameState state, String id) {
        return state.commercialHubs().stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
    }
    private static GameState replace(GameState state, Fleet fleet) {
        return state.withFleets(state.fleets().stream().map(item -> item.id().equals(fleet.id()) ? fleet : item).toList());
    }
    private static GameState replaceHub(GameState state, CommercialHub hub) {
        return state.withCommercialHubs(state.commercialHubs().stream().map(item -> item.id().equals(hub.id()) ? hub : item).toList());
    }
}
