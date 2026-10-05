package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.MarketAccount;
import com.spaceconquest.engine.industry.PowerGridState;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.macrostructure.StationModule;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Paid mixed shipments through real launch windows; fixture prices are scenarios rather than balance targets. */
class LunarTradeAuditTest {
    @TempDir Path directory;
    private record Scenario(String drive, double reserve, int epoch, boolean lowOrbit, double fuelPrice, boolean outward) {}
    private record Measurement(Scenario scenario, boolean accepted, String reason, double waitDays, int days,
                               double fuelUsed, double fuelRemaining, double reserveMargin, double cashChange,
                               double consumptionResult, double routeResult) {}

    @Test void partialLunarSalesSurviveReloadAndDestinationRefuelingConsumesRealStockAndCash() throws Exception {
        var state = world(new Scenario("mod_hydrolox_rocket", .20, 0, false, .01, true));
        state = ShipBatteryCharging.charge(ShipFueling.refuel(state, "ship", "source-port", 120000),
                "ship", "source-port", 500 / .9);
        var logistics = new LogisticsProcessor();
        var shipment = logistics.previewBasket(state, state.tradeRoutes().getFirst(), Map.of("steel", 1250.0, "food_matrix", 1250.0));
        assertNotNull(shipment);
        state = shipment.state().withTradeRoutes(List.of(shipment.route()));
        for (int days = 0; days < 20 && state.fleets().getFirst().location().inTransit(); days++) {
            state = state.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state),
                    state.orbitalStations(), List.of())).withTurn(state.turn() + 1);
        }
        assertTrue(state.fleets().getFirst().location().isAt(FleetLocation.Site.docked("target-port")));
        state = withBuyerCash(state, 4500);
        state = logistics.processTradeRoutes(state).state();
        assertEquals(625, state.tradeRoutes().getFirst().onboardKg(), .00001);
        assertEquals(625, state.tradeRoutes().getFirst().onboardCostCredits(), .00001);
        assertEquals(1, state.tradeRoutes().getFirst().cargoManifest().size());
        var remainingMaterial = state.tradeRoutes().getFirst().cargoManifest().keySet().iterator().next();
        var remainingLot = state.tradeRoutes().getFirst().cargoManifest().get(remainingMaterial);
        assertEquals(625, remainingLot.massKg(), .00001); assertEquals(625, remainingLot.costCredits(), .00001);
        assertEquals(625, state.fleets().getFirst().ships().getFirst().storedCargoKg().values().stream().mapToDouble(Double::doubleValue).sum(), .00001);
        assertEquals(625, state.fleets().getFirst().ships().getFirst().storedCargoKg().get(remainingMaterial), .00001);
        var manager = new SaveGameManager(directory); var file = directory.resolve("lunar-trade.scsave").toFile();
        manager.save(file, state, 1, "2027-01-01T08:00:00");
        var restored = manager.load(file).toGameState(9999, "RUNNING");
        assertEquals(state.tradeRoutes(), restored.tradeRoutes()); assertEquals(state.fleets(), restored.fleets());
        var waiting = logistics.processTradeRoutes(restored).state();
        assertEquals(625, waiting.tradeRoutes().getFirst().onboardKg(), .00001);
        assertFalse(waiting.fleets().getFirst().location().inTransit());
        state = logistics.processTradeRoutes(withBuyerCash(waiting, 1500)).state();
        assertEquals(0, state.tradeRoutes().getFirst().onboardKg());
        assertEquals(2500, state.tradeRoutes().getFirst().totalVolumeMovedKg(), .00001);
        assertEquals(3500, state.tradeRoutes().getFirst().cumulativeOperatingResultCredits(), .00001);
        assertFalse(state.fleets().getFirst().location().inTransit());
        var fuelOrders = new HashMap<>(state.commercialHubs().getLast().activeOrders());
        PropulsionCatalog.drive("mod_hydrolox_rocket").propellantMaterials(120000)
                .forEach((id, kg) -> fuelOrders.put(id, new MarketOrder(id, kg, 0, .01, 0)));
        state = replaceTarget(state, fuelOrders, state.commercialHubs().getLast().currentStoredWeightKg() + 120000);
        double beforeFuel = state.fleets().getFirst().ships().getFirst().currentFuelKg(), beforeCash = cash(state);
        var fueled = ShipFueling.refuel(state, "ship", "target-port", 120000 - beforeFuel);
        assertEquals(120000, fueled.fleets().getFirst().ships().getFirst().currentFuelKg(), .00001);
        assertEquals((120000 - beforeFuel) * .01, beforeCash - cash(fueled), .00001);
        assertEquals(state.commercialHubs().getLast().currentStoredWeightKg() - (120000 - beforeFuel),
                fueled.commercialHubs().getLast().currentStoredWeightKg(), .00001);
        assertFalse(fueled.fleets().getFirst().location().inTransit());
    }

    private GameState replaceTarget(GameState state, Map<String, MarketOrder> orders, double stored) {
        var target = state.commercialHubs().getLast();
        return state.withCommercialHubs(List.of(state.commercialHubs().getFirst(), new CommercialHub(target.id(), target.entityId(),
                target.transactionTariffRate(), target.storageCapacityKg(), stored, target.logisticsRangeUnits(), orders)));
    }

    private GameState withBuyerCash(GameState state, double credits) {
        var accounts = new ArrayList<>(state.marketAccounts());
        accounts.removeIf(account -> account.hubId().equals("target-market"));
        accounts.add(new MarketAccount("target-market", credits));
        return state.withMarketAccounts(accounts);
    }

    @Test void automatedTraderRejectsFuelLossesAndSelectsAProfitableMixedOrbitalBasket() throws Exception {
        for (boolean outward : List.of(true, false)) for (double price : List.of(1.0, .01)) {
            var state = world(new Scenario("mod_hydrolox_rocket", .20, 0, false, price, outward));
            state = ShipFueling.refuel(state, "ship", "source-port", 120000);
            state = ShipBatteryCharging.charge(state, "ship", "source-port", 500 / .9);
            double before = cash(state);
            var selected = RoamingTradePlanner.choose(state, state.tradeRoutes().getFirst(), state.fleets().getFirst());
            assertEquals(before, cash(state), "Candidate search must not spend live cash.");
            assertTrue(state.fleets().getFirst().ships().getFirst().storedCargoKg().isEmpty());
            assertFalse(state.fleets().getFirst().location().inTransit());
            if (price == 1) assertNull(selected.shipment(), "An already full tank does not make consumed fuel free.");
            else {
                assertNotNull(selected.shipment());
                assertEquals(2, selected.shipment().route().cargoManifest().size());
                assertEquals(2500, selected.shipment().route().onboardKg());
                assertNotNull(selected.shipment().state().fleets().getFirst().location().orbitalFlight());
                assertTrue(selected.profitCredits() > 0);
                assertEquals(6, selected.days());
            }
        }
    }

    @Test void paidVoyagesPreserveReservesAndExposeFuelEconomics() throws Exception {
        var rows = new ArrayList<Measurement>();
        for (String drive : ChemicalFreighterCatalog.DRIVES) for (boolean outward : List.of(true, false)) {
            for (double reserve : List.of(.05, .10, .20)) rows.add(run(new Scenario(drive, reserve, 0, false, 1, outward)));
            rows.add(run(new Scenario(drive, .05, 0, true, 1, outward)));
        }
        rows.add(run(new Scenario("mod_hydrolox_rocket", .20, 365, false, 1, true)));
        rows.add(run(new Scenario("mod_hydrolox_rocket", .20, 0, false, .01, true)));
        assertEquals(26, rows.size());
        assertTrue(rows.stream().anyMatch(Measurement::accepted));
        assertTrue(rows.stream().filter(row -> row.scenario().lowOrbit()).noneMatch(Measurement::accepted));
        assertTrue(rows.stream().filter(row -> row.accepted() && row.scenario().fuelPrice() == 1)
                .allMatch(row -> row.consumptionResult() < 0));
        assertTrue(rows.getLast().consumptionResult() > 0, "Cheap fuel illustrates the sensitivity rather than changing catalog prices.");
        var first = rows.stream().filter(row -> row.accepted() && row.scenario().drive().equals("mod_hydrolox_rocket"))
                .findFirst().orElseThrow();
        assertNotEquals(first.waitDays(), rows.get(rows.size() - 2).waitDays(), "Campaign epoch changes the launch wait.");
        var output = Path.of("target/lunar-trade-report.md");
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, report(rows));
    }

    private Measurement run(Scenario scenario) throws Exception {
        var opening = world(scenario);
        double openingCash = cash(opening);
        var fueled = ShipFueling.refuel(opening, "ship", "source-port", 120000);
        assertEquals(120000, fueled.fleets().getFirst().ships().getFirst().currentFuelKg());
        assertEquals(120000 * scenario.fuelPrice(), openingCash - cash(fueled), .00001);
        var initial = ShipBatteryCharging.charge(fueled, "ship", "source-port", 500 / .9);
        double chargingCost = cash(fueled) - cash(initial);
        assertTrue(chargingCost > 0, "The fixture uses a distinct electricity provider.");
        assertEquals(500, initial.fleets().getFirst().ships().getFirst().powerState().batteryChargeKwh(), .00001);
        var route = initial.tradeRoutes().getFirst();
        var logistics = new LogisticsProcessor();
        var shipment = logistics.previewBasket(initial, route, Map.of("steel", 1250.0, "food_matrix", 1250.0));
        if (shipment == null) {
            var fleet = initial.fleets().getFirst(); var ship = fleet.ships().getFirst();
            var loaded = new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), 100, 0, ship.currentFuelKg(),
                    Map.of("steel", 1250.0, "food_matrix", 1250.0), 0, "", ship.transitMode(), ship.powerState(), ship.supplyState());
            var rejected = OrbitalTravel.preview(initial, fleet.withShips(List.of(loaded)), FleetLocation.Site.docked("target-port"));
            assertNull(rejected.plan(), "A rejected shipment must have a physical orbital budget blocker.");
            assertTrue(TradeLegReadiness.inspect(initial, fleet.withShips(List.of(loaded)), initial.commercialHubs().getLast())
                    .explanation().contains(rejected.problem()), "Readiness must explain the actual orbital blocker.");
            assertEquals(0, initial.fleets().getFirst().ships().getFirst().storedCargoKg().size());
            assertEquals(1250, initial.commercialHubs().getFirst().activeOrders().get("steel").supplyKg());
            return new Measurement(scenario, false, rejected.problem(), 0, 0, 0, 120000, 0,
                    cash(initial) - openingCash, 0, 0);
        }
        var fleet = shipment.preparedFleet();
        var target = initial.commercialHubs().getLast();
        var quote = RoamingTradeCost.quote(shipment.state(), fleet, initial.commercialHubs().getFirst(), target);
        assertNotNull(quote);
        var itinerary = shipment.state().fleets().getFirst().location().orbitalFlight().itinerary();
        var state = shipment.state().withTradeRoutes(List.of(shipment.route()));
        assertEquals(2500, shipment.route().onboardCostCredits());
        assertEquals(2500, cash(initial) - cash(state), .00001);
        double sales = 0;
        int days = 0;
        while (state.tradeRoutes().getFirst().onboardKg() > 1e-6 && days++ < 1200) {
            var powered = ShipPowerProcessor.advanceDay(state);
            state = state.withFleets(new FleetProcessor().processFleetMovements(powered, state.orbitalStations(), List.of()))
                    .withTurn(state.turn() + 1);
            var arrived = state.fleets().getFirst();
            assertFalse(arrived.location().orbitalFlight() != null && arrived.location().orbitalFlight().failed());
            assertEquals(0, arrived.ships().getFirst().powerState().lastUnmetEssentialKwh(), .00001);
            boolean docked = arrived.location().isAt(FleetLocation.Site.docked("target-port"));
            double beforeSale = cash(state);
            var result = logistics.processTradeRoutes(state);
            if (!docked) assertEquals(0, result.deliveredKg(), "No sale before funded docking.");
            state = result.state(); sales += cash(state) - beforeSale;
        }
        assertEquals(Math.ceil(itinerary.totalSeconds() / 86400), days);
        assertEquals(quote.days(), days);
        assertTrue(state.fleets().getFirst().location().isAt(FleetLocation.Site.docked("target-port")));
        assertEquals(2500, state.tradeRoutes().getFirst().totalVolumeMovedKg());
        assertTrue(state.tradeRoutes().getFirst().cargoManifest().isEmpty());
        assertEquals(6000, sales, .00001);
        double remaining = state.fleets().getFirst().ships().getFirst().currentFuelKg();
        double used = 120000 - remaining;
        assertEquals(used * scenario.fuelPrice(), quote.fuelCredits(), .00001);
        double margin = remaining - scenario.reserve() * 120000;
        assertTrue(margin >= 0, "The voyage must retain its selected protected reserve.");
        double cashChange = cash(state) - openingCash;
        double consumptionResult = sales - 2500 - used * scenario.fuelPrice() - chargingCost;
        assertEquals(consumptionResult, cashChange + remaining * scenario.fuelPrice(), .00001,
                "Unused paid fuel is retained inventory, not a cash refund.");
        assertEquals(3500, state.tradeRoutes().getFirst().cumulativeOperatingResultCredits(), .00001);
        var parked = state.fleets().getFirst();
        var idle = logistics.processTradeRoutes(state).state();
        assertFalse(idle.fleets().getFirst().location().inTransit(), "An unknown onward trade must not force a return journey.");
        assertEquals(parked.location(), idle.fleets().getFirst().location());
        assertEquals(remaining, idle.fleets().getFirst().ships().getFirst().currentFuelKg());
        return new Measurement(scenario, true, "Funded docking and mixed sale", itinerary.waitSeconds() / 86400,
                days, used, remaining, margin, cashChange, consumptionResult,
                state.tradeRoutes().getFirst().cumulativeOperatingResultCredits());
    }

    private GameState world(Scenario scenario) throws Exception {
        var owner = new Empire("owner", "Trader", "human", "Individualist", 1e6, 0, List.of("sol"), List.of(), Map.of(),
                List.of("rocketry", "methalox_propulsion", "hydrolox_propulsion", "electricity", "solar_power",
                        "industrial_production", "space_stations"), List.of());
        var provider = new Empire("provider", "Port operator", "human", "Individualist", 1e6, 0,
                List.of(), List.of(), Map.of(), List.of(), List.of());
        var orders = new HashMap<String, MarketOrder>();
        PropulsionCatalog.drive(scenario.drive()).propellantMaterials(120000).forEach((id, kg) ->
                orders.put(id, new MarketOrder(id, kg, 0, scenario.fuelPrice(), 0)));
        for (String id : List.of("steel", "food_matrix")) orders.put(id, new MarketOrder(id, 1250, 0, 1, 0));
        var source = new CommercialHub("source-market", "source-port", 0, 1e6, 122500, 10, orders);
        var target = new CommercialHub("target-market", "target-port", 0, 1e6, 0, 10, Map.of(
                "steel", new MarketOrder("steel", 0, 1250, 3, 0),
                "food_matrix", new MarketOrder("food_matrix", 0, 1250, 3, 0)));
        var state = GameState.builder().turn(scenario.epoch()).solarSystems(DataModelLoader.loadSolarSystems())
                .empires(List.of(owner, provider)).orbitalStations(List.of(
                        port("source-port", scenario.outward() ? "earth" : "moon", scenario.outward() ? scenario.lowOrbit() ? 500 : 2000 : 500),
                        port("target-port", scenario.outward() ? "moon" : "earth", scenario.outward() ? 500 : scenario.lowOrbit() ? 500 : 2000)))
                .commercialHubs(List.of(source, target)).marketAccounts(List.of(new MarketAccount(source.id(), 100000),
                        new MarketAccount(target.id(), 100000)))
                .powerGrids(List.of(new PowerGridState("source-port", 1000, 0, 1000, 2000, 2000, false))).build();
        var blueprint = ShipBlueprintFactory.evaluate(state, new ShipDesignSpecification("design", "Freighter", "owner",
                ShipRole.CARGO_TRANSPORT, "steel", ChemicalFreighterCatalog.modules(scenario.drive()), "steel", .5));
        assertTrue(blueprint.valid(), blueprint.errors().toString());
        var ship = new ShipInstance("ship", "design", "owner", 100, 0, 0, Map.of()).withPowerState(ShipPowerState.empty());
        var fleet = new Fleet("fleet", "Trader", "owner", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(ship),
                FleetLocation.at(FleetLocation.Site.docked("source-port")))
                .withFuelPolicy(new FleetFuelPolicy(scenario.reserve(), scenario.reserve(), scenario.reserve()));
        var route = new TradeRoute("route", "Mixed shipment", "owner", source.id(), target.id(), "steel",
                2500, 0, 1e6, List.of("ship"), 0, true).withRoaming(true);
        return state.withShipDesigns(List.of(blueprint.design())).withFleets(List.of(fleet)).withTradeRoutes(List.of(route));
    }

    private OrbitalStation port(String id, String body, double altitude) {
        var yard = new StationModule("yard-" + id, "Capital yard", StationModule.TYPE_CAPITAL_SLIPWAY,
                4, 10000, 10, 0, Map.of(), "engineer", 0, true);
        return new OrbitalStation(id, id, "sol", body, "provider", OrbitalStation.OWNERSHIP_PUBLIC_STATE,
                20, List.of(yard), Map.of(), 100, 10, 0, 0, 100, 100, "steel", 0, true).withParkingAltitudeKm(altitude);
    }

    private double cash(GameState state) {
        return state.empires().stream().filter(empire -> empire.id().equals("owner")).findFirst().orElseThrow().treasuryCredits();
    }

    private String report(List<Measurement> rows) {
        var text = new StringBuilder("# Paid lunar trade audit\n\n");
        text.append("Twenty-six deterministic Earth–Moon probes use live chemical freighter blueprints and actual paid main fuel, "
                + "grid charging and mixed cargo. Ships begin empty. Source ports hold finite stocks and destination buyers have finite cash. "
                + "Each accepted basket carries 1,250 kg steel and 1,250 kg food bought at 1 credit/kg and sold against a 3 credit/kg "
                + "posted price at the current 80% wholesale share. Normal reserves are tested explicitly at 5%, 10% and 20%. "
                + "Ports are fixture-built at 2,000 km Earth and 500 km Moon; low Earth cases use 500 km. Both directions are tested. No return leg is required. "
                + "Campaign epoch changes the actual saved launch window. Solar arrays supply auxiliary electricity.\n\n")
                .append("Fuel prices of 1 and 0.01 credits/kg are sensitivity inputs, not proposed catalog prices. Charging pays a distinct "
                        + "port owner and withdraws real grid storage. Consumption result equals sales minus cargo cost, consumed fuel replacement "
                        + "cost and initial charging. Cash change also includes the purchase of unused fuel that remains aboard. Route result "
                        + "is existing realized cargo accounting; it excludes supplies bought before assignment. Ship and station construction, "
                        + "wages, depreciation, factory replenishment, interest, tariffs and exact shadows are outside this audit.\n\n")
                .append("| Drive | Direction | Reserve | Epoch | Earth parking | Fuel price | Accepted | Wait days | Total days | Main used kg | Main remaining kg | Reserve margin kg | Cash change | Consumption result | Route result |\n")
                .append("|---|---|---:|---:|---|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (var row : rows) text.append(String.format(Locale.ROOT,
                "| %s | %s | %.0f%% | %d | %s | %.2f | %s | %s | %s | %s | %.2f | %s | %.2f | %s | %.2f |%n",
                row.scenario().drive(), row.scenario().outward() ? "Outward" : "Inward", row.scenario().reserve() * 100,
                row.scenario().epoch(), row.scenario().lowOrbit() ? "500 km" : "2,000 km",
                row.scenario().fuelPrice(), row.accepted(), row.accepted() ? number(row.waitDays()) : "Unavailable",
                row.accepted() ? row.days() : "Unavailable", row.accepted() ? number(row.fuelUsed()) : "Unavailable", row.fuelRemaining(),
                row.accepted() ? number(row.reserveMargin()) : "Unavailable", row.cashChange(),
                row.accepted() ? number(row.consumptionResult()) : "Unavailable", row.routeResult()));
        text.append("\n## Measured tuning findings\n\n")
                .append("- ").append(rows.stream().filter(Measurement::accepted).count()).append(" of ").append(rows.size())
                .append(" scenarios complete a funded mixed shipment. All tested 500 km Earth cases fail the physical planner.\n")
                .append("- At 1 credit/kg fuel all accepted voyages lose money on the stated consumption basis. At 0.01 credit/kg "
                        + "the hydrolox case earns a positive result before omitted costs. Equipment feasibility does not imply profitability.\n")
                .append("- A separate automated-planner check rejects the loss-making case even with prepaid full tanks and selects "
                        + "the profitable mixed basket without mutating its input snapshot.\n")
                .append("- The route ledger reports 3,500 credits of cargo margin because main fuel and commissioning charge were bought "
                        + "before assignment. That ledger is not a complete voyage consumption result. Rejected baskets spend no cargo cash; "
                        + "fuel and charge bought before planning remain real paid inventory.\n");
        text.append("\n## Rejected shipments\n\n");
        rows.stream().filter(row -> !row.accepted()).forEach(row -> text.append("- ").append(row.scenario().drive())
                .append(" at ").append(row.scenario().reserve() * 100).append("% reserve, ")
                .append(row.scenario().outward() ? "outward, " : "inward, ")
                .append(row.scenario().lowOrbit() ? "500 km Earth parking" : "2,000 km Earth parking").append(": ").append(row.reason()).append(".\n"));
        return text.toString();
    }

    private String number(double value) { return String.format(Locale.ROOT, "%.2f", value); }
}
