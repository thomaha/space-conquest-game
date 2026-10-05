package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.MarketAccount;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Deterministic stress harness; injected production and liquidity changes are explicit fixture inputs. */
class TradeReliabilityAuditTest {
    private static final int DAYS = 360;
    private record Scenario(String name, boolean roaming, boolean crossing, String shock, int ships) {}
    private static final class Measurement {
        final Scenario scenario;
        int departures, completed, waitDays, longestWait, portOutages, unfinished;
        double delivered, expected, realized, saleDayCash, cashResult, mainFuel, electricalFuel;
        final Set<String> interrupted = new TreeSet<>();
        final Map<String, Integer> reasons = new TreeMap<>();
        final Map<String, Integer> streaks = new HashMap<>();
        final Map<String, Double> expectedTrips = new HashMap<>();
        final Map<String, Double> tripMargins = new HashMap<>();
        final Map<String, Integer> completedByRoute = new TreeMap<>();
        Measurement(Scenario scenario) { this.scenario = scenario; }
    }

    @Test void tradersStayPhysicallySafeThroughRepeatedVoyagesAndMarketShocks() throws Exception {
        var rows = new ArrayList<Measurement>();
        for (var scenario : List.of(new Scenario("Fixed local baseline", false, false, "none", 1),
                new Scenario("Roaming local baseline", true, false, "none", 1),
                new Scenario("Roaming crossing baseline", true, true, "none", 1),
                new Scenario("Fixed fuel shortages", false, true, "fuel", 1),
                new Scenario("Roaming fuel shortages", true, true, "fuel", 1),
                new Scenario("Roaming price collapse", true, false, "price", 1),
                new Scenario("Roaming buyer cash drought", true, false, "cash", 1),
                new Scenario("Fixed mixed-load trader", false, false, "mixed", 1),
                new Scenario("Three competing roaming traders", true, true, "competition", 3))) rows.add(run(scenario));
        Path path = Path.of(System.getProperty("trade.report", "target/trade-reliability-report.md"));
        Files.createDirectories(path.toAbsolutePath().getParent());
        Files.writeString(path, report(rows), StandardCharsets.UTF_8);
        for (var row : rows) {
            assertTrue(row.interrupted.isEmpty(), row.scenario.name() + ": stranded fleets " + row.interrupted);
            assertTrue(row.completed > 2, row.scenario.name() + ": did not demonstrate repeated deliveries");
            assertTrue(Double.isFinite(row.cashResult));
            assertEquals(0, row.portOutages, row.scenario.name());
            if (row.scenario.ships() > 1) assertEquals(row.scenario.ships(), row.completedByRoute.size(), "Every competing trader must complete a shipment.");
            if (!row.scenario.shock().equals("none")) assertTrue(row.waitDays > 0, row.scenario.name());
        }
    }

    @Test void dispatchMovesTraderBehindWaitingRoutes() {
        var scenario = new Scenario("Competition ordering", true, true, "competition", 3);
        var state = marketInputs(world(scenario), scenario, 0);
        var result = new LogisticsProcessor().processTradeRoutes(state).state();
        assertEquals(List.of("route_1", "route_2", "route_0"),
                result.tradeRoutes().stream().map(TradeRoute::id).toList());
        assertTrue(result.fleets().getFirst().hasInterstellarOrder()
                || result.fleets().getFirst().location().inTransit());
        assertEquals(80, result.tradeRoutes().getLast().onboardKg(), 1e-6);
        assertEquals(0, result.tradeRoutes().getFirst().onboardKg(), 1e-6);
    }

    private Measurement run(Scenario scenario) {
        var row = new Measurement(scenario);
        var state = world(scenario);
        var movement = new FleetProcessor();
        for (int day = 0; day < DAYS; day++) {
            state = marketInputs(state, scenario, day);
            var prior = state;
            state = state.withFleets(movement.processFleetMovements(ShipPowerProcessor.advanceDay(state), List.of(), List.of()));
            consumed(prior, state, row);
            for (var fleet : state.fleets()) {
                if (Fleet.MODE_POWER_INTERRUPTED.equals(fleet.interstellarMode())
                        || fleet.location().localFlight() != null && fleet.location().localFlight().interrupted()) row.interrupted.add(fleet.id());
                if (!fleet.hasInterstellarOrder() && !fleet.location().inTransit() && fleet.ships().stream()
                        .anyMatch(ship -> ShipPowerProcessor.reserves(ship).lastUnmetEssentialKwh() > 1e-6)) row.portOutages++;
            }
            for (var route : List.copyOf(state.tradeRoutes())) state = tradeTick(state, route, row);
            for (var fleet : state.fleets()) for (var ship : fleet.ships()) {
                assertTrue(Double.isFinite(ship.currentFuelKg()) && ship.currentFuelKg() >= 0, scenario.name());
                assertTrue(ship.generatorFuelMassKg() >= 0, scenario.name());
            }
        }
        row.delivered = state.tradeRoutes().stream().mapToDouble(TradeRoute::totalVolumeMovedKg).sum();
        row.cashResult = state.tradeRoutes().stream().mapToDouble(TradeRoute::cumulativeOperatingResultCredits).sum();
        row.unfinished = row.expectedTrips.size();
        return row;
    }

    private GameState tradeTick(GameState state, TradeRoute route, Measurement row) {
        var fleet = state.fleets().stream().filter(item -> item.ships().stream()
                .anyMatch(ship -> route.assignedFreighterIds().contains(ship.id()))).findFirst().orElseThrow();
        boolean idle = !fleet.hasInterstellarOrder() && !fleet.location().inTransit();
        var forecastState = TradePortMaintenance.supply(state, fleet);
        var forecastFleet = forecastState.fleets().stream().filter(item -> item.id().equals(fleet.id())).findFirst().orElseThrow();
        RoamingTradePlanner.Selection forecast = idle && route.roaming() && TradeRoute.LOADING.equals(route.phase())
                ? RoamingTradePlanner.choose(forecastState, route, forecastFleet) : null;
        var next = new LogisticsProcessor().processTradeRoutes(state.withTradeRoutes(List.of(route))).state();
        var updated = next.tradeRoutes().getFirst();
        var moved = next.fleets().stream().filter(item -> item.id().equals(fleet.id())).findFirst().orElseThrow();
        double localBurn = localFuelBurn(state, next);
        if (route.onboardKg() == 0 && updated.onboardKg() > 0) {
            row.departures++;
            double expected = forecast != null && forecast.shipment() != null ? forecast.profitCredits() : fixedForecast(state, updated, moved, localBurn);
            row.expectedTrips.put(route.id(), expected);
            row.tripMargins.put(route.id(), -updated.onboardCostCredits());
        }
        row.mainFuel += localBurn;
        boolean saleComplete = updated.totalVolumeMovedKg() > route.totalVolumeMovedKg() && updated.onboardKg() == 0;
        if (!saleComplete && row.tripMargins.containsKey(route.id())) row.tripMargins.merge(route.id(), -localBurn * .001, Double::sum);
        if (updated.totalVolumeMovedKg() > route.totalVolumeMovedKg()) {
            row.saleDayCash += updated.dailyOperatingResultCredits();
            double quantity = updated.totalVolumeMovedKg() - route.totalVolumeMovedKg();
            double bid = Math.max(.01, port(state, route.destinationEntityId()).activeOrders().get(route.materialId()).pricePerKg()) * .8;
            row.tripMargins.merge(route.id(), quantity * bid, Double::sum);
            if (updated.onboardKg() == 0) {
                row.completed++;
                row.completedByRoute.merge(route.id(), 1, Integer::sum);
                row.expected += row.expectedTrips.getOrDefault(route.id(), 0.0);
                row.expectedTrips.remove(route.id());
                row.realized += row.tripMargins.remove(route.id());
            }
        }
        boolean waiting = idle && !moved.hasInterstellarOrder() && !moved.location().inTransit()
                && updated.totalVolumeMovedKg() == route.totalVolumeMovedKg() && updated.onboardKg() == route.onboardKg();
        if (waiting) {
            row.waitDays++;
            int length = row.streaks.merge(route.id(), 1, Integer::sum);
            row.longestWait = Math.max(row.longestWait, length);
            row.reasons.merge(waitReason(state, route, fleet), 1, Integer::sum);
        } else row.streaks.put(route.id(), 0);
        var routes = new ArrayList<>(state.tradeRoutes().stream().map(item -> item.id().equals(route.id()) ? updated : item).toList());
        if (idle && (moved.hasInterstellarOrder() || moved.location().inTransit())) { routes.remove(updated); routes.add(updated); }
        return next.withTradeRoutes(routes);
    }

    private double fixedForecast(GameState state, TradeRoute selected, Fleet moved, double localBurn) {
        var source = port(state, selected.originEntityId()); var target = port(state, selected.destinationEntityId());
        var ships = moved.ships().stream().map(ship -> new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(),
                ship.currentHullHealth(), ship.currentShieldHealth(), ship.currentFuelKg() + localBurn,
                ship.storedCargoKg(), ship.passengerCount(), ship.passengerRaceId(), ship.transitMode(), ship.powerState(), ship.supplyState())).toList();
        var prepared = new Fleet(moved.id(), moved.name(), moved.ownerEntityId(), FleetPositioning.systemForHub(state, source),
                "", 0, 0, 0, false, moved.fleetStance(), ships, FleetLocation.at(FleetPositioning.hubSite(state, source)));
        var quote = RoamingTradeCost.quote(state, prepared, source, target);
        assertNotNull(quote, "Executed fixed leg must have a quote using its actually purchased departure stores.");
        double quantity = selected.onboardKg();
        return quantity * (target.activeOrders().get(selected.materialId()).pricePerKg() * .8
                - source.activeOrders().get(selected.materialId()).pricePerKg()) - quote.fuelCredits() - quote.launchCredits();
    }

    private String waitReason(GameState state, TradeRoute route, Fleet fleet) {
        if (route.onboardKg() > 0 && FleetPositioning.atHub(state, fleet, port(state, route.destinationEntityId())))
            return "Unsold cargo: buyer cash or capacity";
        var target = port(state, TradeRoute.RETURNING.equals(route.phase()) ? route.originEntityId() : route.destinationEntityId());
        if (target != null && !FleetPositioning.atHub(state, fleet, target)
                && !TradeLegReadiness.inspect(state, fleet, target).ready()) return "Supplies or next-leg readiness";
        return "No viable profitable trade or source stock";
    }

    private void consumed(GameState before, GameState after, Measurement row) {
        for (var fleet : before.fleets()) for (var ship : fleet.ships()) {
            var changed = after.fleets().stream().flatMap(item -> item.ships().stream())
                    .filter(item -> item.id().equals(ship.id())).findFirst().orElseThrow();
            row.mainFuel += Math.max(0, ship.currentFuelKg() - changed.currentFuelKg());
            row.electricalFuel += Math.max(0, ship.generatorFuelMassKg() - changed.generatorFuelMassKg());
            double cost = (Math.max(0, ship.currentFuelKg() - changed.currentFuelKg())
                    + Math.max(0, ship.generatorFuelMassKg() - changed.generatorFuelMassKg())) * .001;
            for (var route : before.tradeRoutes()) if (route.assignedFreighterIds().contains(ship.id()) && row.tripMargins.containsKey(route.id()))
                row.tripMargins.merge(route.id(), -cost, Double::sum);
        }
    }

    private double localFuelBurn(GameState before, GameState after) {
        double purchased = 0, storedChange = 0;
        for (var hub : before.commercialHubs()) for (String material : List.of("rp1_kerosene", "liquid_oxygen")) {
            double old = hub.activeOrders().get(material).supplyKg();
            double next = port(after, hub.id()).activeOrders().get(material).supplyKg();
            purchased += old - next;
        }
        for (var fleet : before.fleets()) for (var ship : fleet.ships()) {
            var updated = after.fleets().stream().flatMap(item -> item.ships().stream())
                    .filter(item -> item.id().equals(ship.id())).findFirst().orElseThrow();
            storedChange += updated.currentFuelKg() + updated.generatorFuelMassKg() - ship.currentFuelKg() - ship.generatorFuelMassKg();
        }
        assertTrue(purchased - storedChange >= -1e-5, "Fuel acquisition must cover all increases in working stores.");
        return Math.max(0, purchased - storedChange);
    }

    private CommercialHub port(GameState state, String id) {
        return state.commercialHubs().stream().filter(item -> item.id().equals(id)).findFirst().orElse(null);
    }

    private GameState world(Scenario scenario) {
        var p = new ShipPowerProfile(0, 100, 0, 0, 0, 0, 5000, 0, 1, 2, 0, .65,
                Map.of("rp1", new ShipPowerProfile.Fuel("rp1_kerosene", "liquid_oxygen", .28, 1.008)));
        var design = new ShipDesign("design", "Audit freighter", "owner", ShipRole.CARGO_TRANSPORT, "steel",
                List.of("mod_chemical_rocket"), "steel", 0, 1000, 1000, 1000, 100, 1, 0, 2000,
                false, false, ShipManufacturingProfile.baseline(), p);
        var systems = List.of(new SolarSystem("a", "A", "", 0, 0, 0, 1, 1, "yellow", List.of(), List.of()),
                new SolarSystem("b", "B", "", 1e6 / InterstellarTravel.METERS_PER_LIGHT_YEAR, 0, 0, 1, 1, "yellow", List.of(), List.of()));
        var stations = new ArrayList<OrbitalStation>(); var ports = new ArrayList<CommercialHub>();
        for (int index = 0; index < 3; index++) {
            String id = "port_" + index;
            stations.add(new OrbitalStation(id + "_station", id, index == 1 && scenario.crossing() ? "b" : "a", "",
                    "owner", "PUBLIC_STATE", 10, List.of(), Map.of(), 0, 0, 0, 0, 100, 100, "steel", 0, true));
            ports.add(new CommercialHub(id, id + "_station", 0, 100000, 20100, 10, Map.of()));
        }
        var fleets = new ArrayList<Fleet>(); var routes = new ArrayList<TradeRoute>();
        for (int index = 0; index < scenario.ships(); index++) {
            var power = new ShipPowerState(Map.of("rp1_kerosene", 1120.0, "liquid_oxygen", 2880.0),
                    "rp1", "uranium", 0, true, 1, 1, 0, 0, 0, 0, 0);
            var ship = new ShipInstance("ship_" + index, "design", "owner", 100, 0, 1000, Map.of()).withPowerState(power);
            fleets.add(new Fleet("fleet_" + index, "Freighter", "owner", "a", "", 0, 0, 0, false, "PASSIVE", List.of(ship),
                    FleetLocation.at(FleetLocation.Site.docked("port_0_station"))));
            routes.add(new TradeRoute("route_" + index, "Trader", "owner", "port_0", "port_1", "steel", 100, 0, 1000,
                    List.of(ship.id()), 0, true).withRoaming(scenario.roaming()));
        }
        return GameState.builder().solarSystems(systems).orbitalStations(stations).commercialHubs(ports)
                .empires(List.of(new Empire("owner", "Owner", "human", "Individualist", 100000, 0,
                        List.of("a", "b"), List.of(), Map.of(), List.of("rocketry"), List.of())))
                .shipDesigns(List.of(design)).fleets(fleets).tradeRoutes(routes).build();
    }

    private GameState marketInputs(GameState state, Scenario scenario, int day) {
        boolean refresh = day % 12 == 0;
        var ports = new ArrayList<CommercialHub>();
        for (int index = 0; index < state.commercialHubs().size(); index++) {
            var hub = state.commercialHubs().get(index); var orders = new HashMap<>(hub.activeOrders());
            boolean collapse = scenario.shock().equals("price") && day % 90 >= 25 && day % 90 < 65;
            for (String material : List.of("steel", "refined_copper")) {
                boolean producer = scenario.shock().equals("mixed") ? index != 1 : material.equals("steel") == (index != 1);
                var old = orders.get(material);
                double supply = old == null ? 0 : old.supplyKg();
                if (refresh) {
                    double change = scenario.shock().equals("competition") ? 80 : 400;
                    supply = producer ? supply + change : Math.max(0, supply - change);
                }
                orders.put(material, new MarketOrder(material, supply, producer ? 0 : scenario.shock().equals("mixed") ? 50 : 1000,
                        producer ? 1 : collapse ? 1 : 20, producer ? 0 : 1));
            }
            boolean drought = scenario.shock().equals("fuel") && day % 90 >= 20 && day % 90 < 70;
            for (String material : List.of("rp1_kerosene", "liquid_oxygen")) {
                var old = orders.get(material); double supply = old == null ? 10000 : old.supplyKg();
                if (drought) supply = 0; else if (refresh) supply = 10000;
                orders.put(material, new MarketOrder(material, supply, 0, .001, 0));
            }
            double weight = orders.values().stream().mapToDouble(MarketOrder::supplyKg).sum();
            ports.add(new CommercialHub(hub.id(), hub.entityId(), 0, 100000, weight, 10, orders));
        }
        var accounts = new ArrayList<>(state.marketAccounts());
        if (refresh || scenario.shock().equals("cash")) {
            accounts.clear();
            for (var port : ports) accounts.add(new MarketAccount(port.id(), scenario.shock().equals("cash")
                    && day % 90 >= 20 && day % 90 < 70 ? 0 : 10000));
        }
        return state.withCommercialHubs(ports).withMarketAccounts(accounts);
    }

    private String report(List<Measurement> rows) {
        var text = new StringBuilder("# Trade reliability audit\n\n## Scope\n\n");
        text.append("Deterministic 360-day scenarios use modeled chemical freighters, actual power and movement ticks and paid logistics. ")
                .append("The fixture injects production, destination consumption and buyer liquidity every 12 days. Fuel replacement prices stay at 0.001 credits/kg. Shocks explicitly alter goods prices, fuel stocks or buyer cash. ")
                .append("Crossings cover one million meters rather than full light-year distances. Default 5% main-tank contingency targets are protected; automated traders never use emergency overrides. Port upkeep targets 60 days of stationary essential and cargo electricity, with paid partial purchases when stock or cash is limited. Port departures carry a 60-day arrival allowance when destination refill stock is insufficient or total scheduled travel exceeds two days. Short stocked trips retain the 48-hour arrival requirement. No tanker rescue is provided.\n\n");
        text.append("## Results\n\n| Scenario | Ships | Departures | Sales completed | Delivered kg | Wait ship-days | Longest wait days | Interrupted fleets | Port outage ship-days | Main fuel kg | Electrical fuel kg | Expected completed profit | Realized completed profit | Sale-day cash result | Total route cash result | Unfinished trips |\n")
                .append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (var row : rows) text.append(String.format(Locale.ROOT,
                "| %s | %d | %d | %d | %.0f | %d | %d | %d | %d | %.1f | %.1f | %.1f | %.1f | %.1f | %.1f | %d |\n",
                row.scenario.name(), row.scenario.ships(), row.departures, row.completed, row.delivered, row.waitDays,
                row.longestWait, row.interrupted.size(), row.portOutages, row.mainFuel, row.electricalFuel,
                row.expected, row.realized, row.saleDayCash, row.cashResult, row.unfinished));
        text.append("\n## Waiting reasons\n\n");
        for (var row : rows) text.append("- ").append(row.scenario.name()).append(": ").append(row.reasons).append("\n");
        text.append("\n## Per-trader completed shipments\n\n");
        for (var row : rows) text.append("- ").append(row.scenario.name()).append(": ").append(row.completedByRoute).append("\n");
        text.append("\n## Interpretation and limits\n\n")
                .append("Expected profit prices consumed working fuel at departure. Realized completed profit uses actual sale revenue, cargo cost and consumed working fuel through sale; idle-before-loading and empty-return fuel are excluded from that comparison. Sale-day cash results charge cargo and any same-day resupply or empty return purchases. ")
                .append("Total route cash also includes tank top-ups with usable leftovers. These columns are not directly interchangeable. ")
                .append("Waiting can drain stationary electrical stores even at a safe port. Interruptions count physical failed itineraries; port outages are reported separately. ")
                .append("Main fuel totals include actual physical local burns, reconstructed from physical market withdrawals and working-store changes. ")
                .append("This audit excludes full economy production, diplomacy, passengers, combat, solar propulsion and market reservations.\n");
        return text.toString();
    }

    @Test void waitingTraderBuysRealElectricalReservesWithoutStartingAJourney() {
        var state = marketInputs(world(new Scenario("Hotel", true, false, "price", 1)), new Scenario("Hotel", true, false, "price", 1), 0);
        var fleet = state.fleets().getFirst();
        var ship = fleet.ships().getFirst().withPowerState(ShipPowerState.empty());
        fleet = fleet.withShips(List.of(ship)); state = state.withFleets(List.of(fleet));
        var supplied = TradePortMaintenance.supply(state, fleet);
        var updated = supplied.fleets().getFirst().ships().getFirst();
        var design = state.shipDesigns().getFirst(); var p = design.powerProfile();
        assertTrue(ShipArrivalReserve.check(p, updated.powerState(), p.essentialKw(updated, design),
                ShipPowerProcessor.cargoKw(p, updated, design), ShipSolarEnvironment.DARK,
                TradePortMaintenance.DWELL_RESERVE_HOURS).ready());
        assertEquals(ship.currentFuelKg(), updated.currentFuelKg());
        assertEquals(fleet.fuelPolicy(), supplied.fleets().getFirst().fuelPolicy());
        assertSame(supplied, TradePortMaintenance.supply(supplied, supplied.fleets().getFirst()));
        assertTrue(supplied.empires().getFirst().treasuryCredits() < state.empires().getFirst().treasuryCredits());
        assertTrue(supplied.commercialHubs().getFirst().activeOrders().get("liquid_oxygen").supplyKg()
                < state.commercialHubs().getFirst().activeOrders().get("liquid_oxygen").supplyKg());
        assertFalse(supplied.fleets().getFirst().hasInterstellarOrder());
        var poor = state.withEmpires(List.of(new Empire("owner", "Owner", "human", "Individualist", 0, 0,
                List.of("a", "b"), List.of(), Map.of(), List.of("rocketry"), List.of())));
        assertSame(poor, TradePortMaintenance.supply(poor, fleet));
        var distant = fleet.withLocation(FleetLocation.at(FleetLocation.Site.deepSpace()));
        var outside = state.withFleets(List.of(distant));
        assertSame(outside, TradePortMaintenance.supply(outside, distant));
    }

    @Test void portUpkeepBuysPartialReservesWhenStockOrCashCannotCoverTheTarget() {
        var state = emptyWaitingTrader();
        var hub = state.commercialHubs().getFirst();
        var orders = new HashMap<>(hub.activeOrders());
        orders.put("rp1_kerosene", new MarketOrder("rp1_kerosene", 28, 0, .001, 0));
        orders.put("liquid_oxygen", new MarketOrder("liquid_oxygen", 72, 0, .001, 0));
        var limited = state.withCommercialHubs(List.of(new CommercialHub(hub.id(), hub.entityId(), 0,
                hub.storageCapacityKg(), 100, hub.logisticsRangeUnits(), orders)));
        var supplied = TradePortMaintenance.supply(limited, limited.fleets().getFirst());
        assertEquals(100, supplied.fleets().getFirst().ships().getFirst().generatorFuelMassKg(), 1e-5);
        assertEquals(.1, limited.empires().getFirst().treasuryCredits()
                - supplied.empires().getFirst().treasuryCredits(), 1e-6);
        assertEquals(0, supplied.commercialHubs().getFirst().activeOrders().get("liquid_oxygen").supplyKg(), 1e-5);

        var poor = state.withEmpires(List.of(new Empire("owner", "Owner", "human", "Individualist", .05, 0,
                List.of("a", "b"), List.of(), Map.of(), List.of("rocketry"), List.of())));
        var affordable = TradePortMaintenance.supply(poor, poor.fleets().getFirst());
        assertEquals(50, affordable.fleets().getFirst().ships().getFirst().generatorFuelMassKg(), 1e-5);
        assertEquals(0, affordable.empires().getFirst().treasuryCredits(), 1e-8);
        assertEquals(1000, affordable.fleets().getFirst().ships().getFirst().currentFuelKg());
    }

    @Test void waitingDualPowerTraderUsesPaidReactorFeedDuringChemicalStockDrought() {
        var state = emptyWaitingTrader();
        var profile = new ShipPowerProfile(0, 100, 100, 0, 0, 0, 5000, 10, 1, 2, 0, .65,
                Map.of("rp1", new ShipPowerProfile.Fuel("rp1_kerosene", "liquid_oxygen", .28, 1.008),
                        "uranium", new ShipPowerProfile.Fuel("refined_uranium", null, 1, 6_000_000)));
        var design = new ShipDesign("design", "Dual-power freighter", "owner", ShipRole.CARGO_TRANSPORT, "steel",
                List.of("mod_chemical_rocket"), "steel", 0, 1000, 1000, 1000, 100, 1, 0, 2000,
                false, false, ShipManufacturingProfile.baseline(), profile);
        var hub = state.commercialHubs().getFirst();
        var uranium = new MarketOrder("refined_uranium", 1, 0, 10, 0);
        state = state.withShipDesigns(List.of(design)).withCommercialHubs(List.of(new CommercialHub(hub.id(),
                hub.entityId(), 0, 100000, 1, 10, Map.of("refined_uranium", uranium))));
        var supplied = TradePortMaintenance.supply(state, state.fleets().getFirst());
        var ship = supplied.fleets().getFirst().ships().getFirst();
        double purchased = ship.powerState().generatorMaterialsKg().get("refined_uranium");
        assertTrue(purchased > 0);
        assertEquals(purchased * 10, state.empires().getFirst().treasuryCredits()
                - supplied.empires().getFirst().treasuryCredits(), 1e-6);
        assertEquals(1 - purchased, supplied.commercialHubs().getFirst().activeOrders()
                .get("refined_uranium").supplyKg(), 1e-9);
        assertTrue(ShipArrivalReserve.check(profile, ship.powerState(), 1, 0, ShipSolarEnvironment.DARK,
                TradePortMaintenance.DWELL_RESERVE_HOURS).ready());
        assertEquals(1000, ship.currentFuelKg());
        assertFalse(supplied.fleets().getFirst().location().inTransit());
    }

    private GameState emptyWaitingTrader() {
        var scenario = new Scenario("Waiting", true, false, "none", 1);
        var state = marketInputs(world(scenario), scenario, 0);
        var fleet = state.fleets().getFirst();
        return state.withFleets(List.of(fleet.withShips(List.of(fleet.ships().getFirst()
                .withPowerState(ShipPowerState.empty())))));
    }
}
