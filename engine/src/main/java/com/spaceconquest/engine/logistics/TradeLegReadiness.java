package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Bounded next-port checks and tick-owned fueling. Automated freight also uses TradeReturnFuel. */
public final class TradeLegReadiness {
    public record Report(boolean ready, String explanation) {}
    private TradeLegReadiness() {}

    /** Provisional three-leg allocation retains two thirds for the crossing and destination approach. */
    public static LocalTravel.Plan departureLocal(GameState state, Fleet fleet) {
        return LocalTravel.planRetaining(state, fleet, FleetLocation.Site.deepSpace(), 2.0 / 3);
    }

    public static InterstellarTravel.Plan crossing(GameState state, Fleet fleet, CommercialHub destination) {
        String system = FleetPositioning.systemForHub(state, destination);
        var site = FleetPositioning.hubSite(state, destination);
        if (system == null || site == null || state.solarSystems().stream().noneMatch(item -> item.id().equals(system))
                || state.solarSystems().stream().noneMatch(item -> item.id().equals(fleet.currentSystemId()))) return null;
        Map<String, Double> approach = new HashMap<>();
        for (var ship : fleet.ships()) {
            var design = FleetSupplySimulation.design(state, ship);
            if (design == null) return null;
            double reserve = LocalTravel.requiredPropellantKg(design, ship, FleetLocation.Site.deepSpace(), site) * 1.05;
            if (site.kind() != FleetLocation.Kind.SURFACE && LocalSiteGeometry.position(state, system, site) != null)
                reserve = Math.max(reserve, ship.currentFuelKg() / 2);
            approach.put(ship.id(), reserve);
        }
        return InterstellarTravel.plan(state, fleet, system, approach);
    }

    public static Report inspect(GameState state, Fleet fleet, CommercialHub destination) {
        var site = destination == null ? null : FleetPositioning.hubSite(state, destination);
        String system = destination == null ? null : FleetPositioning.systemForHub(state, destination);
        if (site == null || system == null) return new Report(false, "Waiting: destination port is unavailable.");
        if (state.orbitalStations().stream().anyMatch(station -> station.id().equals(destination.entityId()) && !station.isOperational()))
            return new Report(false, "Waiting: destination station is not operational.");
        if (fleet.hasInterstellarOrder() || fleet.location().inTransit())
            return new Report(false, "Travel is already in progress.");
        if (FleetPositioning.atHub(state, fleet, destination)) return new Report(true, "At the trading port.");
        if (system.equals(fleet.currentSystemId())) {
            var local = TradeLocalPlan.plan(state, fleet, site);
            if (local == null) {
                if (OrbitalTravel.applies(state, fleet, site))
                    return new Report(false, "Waiting: " + OrbitalTravel.preview(state, fleet, site).problem());
                return new Report(false, "Waiting: local maneuver propellant or drive feed is insufficient.");
            }
            if (!ShipPowerForecast.ready(ShipPowerForecast.departure(state, fleet, site, local, null)))
                return new Report(false, "Local leg requires electricity and an arrival reserve.");
            var arrival = LocalTravel.arrivalPreview(state, fleet, site, local);
            return TradeArrivalReadiness.inspect(state, fleet, arrival, destination, Math.ceil(local.days()));
        }
        Fleet crossingFleet = fleet;
        double journeyDays = 0;
        if (!fleet.location().isAt(FleetLocation.Site.deepSpace())) {
            var local = departureLocal(state, fleet);
            if (local == null) return new Report(false, "Waiting: departure maneuver propellant or drive feed is insufficient.");
            if (!ShipPowerForecast.ready(ShipPowerForecast.departure(state, fleet, FleetLocation.Site.deepSpace(), local, null)))
                return new Report(false, "Waiting: departure maneuver electricity is insufficient.");
            crossingFleet = LocalSpacePowerForecast.consume(state, fleet, FleetLocation.Site.deepSpace(), local);
            crossingFleet = LocalTravel.projectedArrival(crossingFleet, FleetLocation.Site.deepSpace(), local);
            journeyDays += Math.ceil(local.days());
        }
        var plan = crossing(state, crossingFleet, destination);
        if (plan == null) return new Report(false, "Waiting: crossing propellant or reactor feed cannot retain destination approach fuel.");
        if (!ShipPowerForecast.ready(ShipPowerForecast.departure(state, crossingFleet, FleetLocation.Site.deepSpace(), null, plan)))
            return new Report(false, "Waiting: crossing electricity and arrival reserve are insufficient.");
        Fleet arrival = InterstellarTravel.commitReactorFuel(crossingFleet, plan);
        arrival = arrival.withShips(arrival.ships().stream().map(ship -> new ShipInstance(ship.id(), ship.designId(),
                ship.ownerEntityId(), ship.currentHullHealth(), ship.currentShieldHealth(),
                Math.max(0, ship.currentFuelKg() - plan.fuelBudgetKg().getOrDefault(ship.id(), 0.0)), ship.storedCargoKg(),
                ship.passengerCount(), ship.passengerRaceId(), ship.transitMode(), ship.powerState(), ship.supplyState(), ship.moduleDamage())).toList());
        if (Fleet.MODE_SUBLIGHT.equals(plan.mode()) && crossingFleet.ships().stream().allMatch(ship -> {
            var design = FleetSupplySimulation.design(state, ship);
            return design.powerProfile() != null && PropulsionCatalog.mainDrive(design.equippedModuleIds()) != null;
        })) {
            var forecast = FleetPropulsionSupply.forecast(state, crossingFleet, plan);
            if (!ShipPowerForecast.ready(forecast.electrical())) return new Report(false, "Waiting: outbound electrical endurance is insufficient.");
            arrival = forecast.arrival();
        } else if (Fleet.MODE_WARP.equals(plan.mode())) {
            arrival = consumeLocal(state, arrival, Math.ceil(plan.days()) * 24);
            if (arrival == null) return new Report(false, "Waiting: warp electricity is insufficient.");
        }
        arrival = new Fleet(arrival.id(), arrival.name(), arrival.ownerEntityId(), system, "", 0, 0, 0,
                false, arrival.fleetStance(), arrival.ships(), FleetLocation.at(FleetLocation.Site.deepSpace())).withFuelPolicy(arrival.fuelPolicy()).withShieldPolicy(arrival.shieldPolicy()).withEscortOrder(arrival.escortOrder());
        var dock = TradeLocalPlan.plan(state, arrival, site);
        if (dock == null) return new Report(false, "Waiting: destination approach propellant or reactor feed is insufficient.");
        if (!ShipPowerForecast.ready(ShipPowerForecast.departure(state, arrival, site, dock, null)))
            return new Report(false, "Waiting: destination approach electricity and 48-hour port reserve are insufficient.");
        arrival = LocalTravel.arrivalPreview(state, arrival, site, dock);
        return TradeArrivalReadiness.inspect(state, fleet, arrival, destination,
                journeyDays + Math.ceil(plan.days()) + Math.ceil(dock.days()));
    }

    private static Fleet consumeLocal(GameState state, Fleet fleet, double hours) {
        var ships = new java.util.ArrayList<ShipInstance>();
        for (var ship : fleet.ships()) {
            var design = FleetSupplySimulation.design(state, ship);
            var p = design.powerProfile(ship);
            if (p == null) { ships.add(ship); continue; }
            var step = ShipPowerProcessor.interval(p, ShipPowerProcessor.reserves(ship), hours, 0,
                    p.essentialKw(ship, design), ShipPowerProcessor.cargoKw(p, ship, design), p.driveKw());
            if (!step.supplied()) return null;
            ships.add(ship.withPowerState(step.state()));
        }
        return fleet.withShips(ships);
    }

    /** Claims prepaid local reserves, then retains new purchases only if the complete next leg is safe. */
    public static GameState prepare(GameState state, Fleet fleet, CommercialHub destination) {
        state = TradeReturnFuel.claim(state, fleet);
        fleet = find(state, fleet.id());
        var snapshot = state;
        var readyFleet = fleet;
        var port = state.commercialHubs().stream().filter(hub -> FleetPositioning.atHub(snapshot, readyFleet, hub))
                .findFirst().orElse(null);
        boolean full = TradeFuelEconomy.preferFull(state, fleet, port)
                || fleet.ships().stream().allMatch(ship -> FleetSupplySimulation.design(snapshot, ship) != null
                        && FleetSupplySimulation.design(snapshot, ship).powerProfile() == null);
        if (inspect(state, fleet, destination).ready()) return full ? prepareFull(state, fleet, destination) : state;
        if (fleet.hasInterstellarOrder() || fleet.location().inTransit()) return state;
        if (fleet.location().current().kind() == FleetLocation.Kind.DEEP_SPACE) return state;
        if (full) return prepareFull(state, fleet, destination);
        var supplied = TradeReturnFuelSizing.prepare(state, fleet, destination);
        if (supplied == null) return state;
        GameState current = state;
        for (var ship : fleet.ships()) {
            var allowance = supplied.ships().stream().filter(item -> item.id().equals(ship.id())).findFirst().orElseThrow();
            current = buyLargest(current, ship.id(), port.entityId(), allowance.currentFuelKg() - ship.currentFuelKg(), null);
            var profile = FleetSupplySimulation.design(state, ship).powerProfile();
            if (profile == null) continue;
            for (String id : List.of(ShipPowerProcessor.reserves(ship).chemicalMixture(), ShipPowerProcessor.reserves(ship).reactorFuel())) {
                var fuel = profile.fuels().get(id);
                if (fuel == null) continue;
                double additional = fuel.materials(1).keySet().stream().mapToDouble(material ->
                        Math.max(0, ShipPowerProcessor.reserves(allowance).generatorMaterialsKg().getOrDefault(material, 0.0)
                                - ShipPowerProcessor.reserves(ship).generatorMaterialsKg().getOrDefault(material, 0.0))).sum();
                current = buyLargest(current, ship.id(), port.entityId(), additional, id);
            }
        }
        return inspect(current, find(current, fleet.id()), destination).ready() ? current : state;
    }

    /** Full working-store attempt, isolated by the caller when used only for forecasting. */
    public static GameState refillAtPort(GameState state, Fleet fleet, CommercialHub port) {
        return FleetPositioning.atHub(state, fleet, port) ? prepareFull(state, fleet, port) : state;
    }

    static GameState prepareFull(GameState state, Fleet fleet, CommercialHub destination) {
        if (fleet.hasInterstellarOrder() || fleet.location().inTransit()
                || fleet.location().current().kind() == FleetLocation.Kind.DEEP_SPACE) return state;
        String source = fleet.location().current().entityId();
        GameState current = state;
        for (var original : fleet.ships()) {
            var ship = find(current, fleet.id()).ships().stream().filter(item -> item.id().equals(original.id())).findFirst().orElseThrow();
            var design = FleetSupplySimulation.design(current, ship);
            if (design == null) continue;
            double kg = design.fuelCapacityKg() - ship.currentFuelKg();
            if (kg > 1e-6) current = buyLargest(current, ship.id(), source, kg, null);
            var p = design.powerProfile();
            if (p == null) continue;
            for (String feed : List.of(ShipPowerProcessor.reserves(ship).chemicalMixture(), ShipPowerProcessor.reserves(ship).reactorFuel())) {
                var fuel = p.fuels().get(feed);
                if (fuel == null || (fuel.oxidizerId() == null ? p.fissionKw() <= 0 : p.chemicalKw() <= 0)) continue;
                double capacity = fuel.oxidizerId() == null ? p.reactorTankKg() : p.generatorTankKg();
                double occupied = fuel.materials(1).keySet().stream().mapToDouble(id ->
                        ShipPowerProcessor.reserves(original).generatorMaterialsKg().getOrDefault(id, 0.0)).sum();
                if (capacity - occupied > 1e-6) current = buyLargest(current, ship.id(), source, capacity - occupied, feed);
            }
        }
        return inspect(current, find(current, fleet.id()), destination).ready() ? current : state;
    }

    private static Fleet find(GameState state, String id) {
        return state.fleets().stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
    }

    static GameState buyLargest(GameState state, String ship, String source, double kg, String electricalFeed) {
        if (kg <= 1e-5) return state;
        var full = buy(state, ship, source, kg, electricalFeed);
        if (full != state) return full;
        var vessel = state.fleets().stream().flatMap(item -> item.ships().stream()).filter(item -> item.id().equals(ship)).findFirst().orElse(null);
        var design = vessel == null ? null : FleetSupplySimulation.design(state, vessel);
        var port = state.commercialHubs().stream().filter(item -> item.entityId().equals(source)).findFirst().orElse(null);
        if (design == null || port == null) return state;
        Map<String, Double> ingredients;
        if (electricalFeed == null) {
            var drive = PropulsionCatalog.mainDrive(design.equippedModuleIds());
            if (drive == null) return state;
            ingredients = drive.propellantMaterials(1);
        } else {
            var fuel = design.powerProfile() == null ? null : design.powerProfile().fuels().get(electricalFeed);
            if (fuel == null) return state;
            ingredients = fuel.materials(1);
        }
        if (ingredients.keySet().stream().anyMatch(id -> !port.activeOrders().containsKey(id)
                || port.activeOrders().get(id).supplyKg() <= 0)) return state;
        double low = 0, high = kg;
        GameState best = state;
        for (int attempt = 0; attempt < 32; attempt++) {
            double middle = (low + high) / 2;
            var paid = buy(state, ship, source, middle, electricalFeed);
            if (paid == state) high = middle;
            else { low = middle; best = paid; }
        }
        return best;
    }

    private static GameState buy(GameState state, String ship, String source, double kg, String electricalFeed) {
        if (kg <= 1e-5) return state;
        return electricalFeed == null ? ShipFueling.refuel(state, ship, source, kg)
                : ShipPowerResupply.buy(state, ship, source, electricalFeed, kg);
    }
}
