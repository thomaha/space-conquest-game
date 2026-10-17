package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.MarketAccount;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.market.MarketProcessor;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.ship.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Paid catalog food shipments with measured travel consumption and an empty-return operating quote. */
class FoodImportCostAuditTest {
    private static final double CARGO_KG = 2500;
    record Scenario(String drive, String body) {}
    private record Measurement(Scenario scenario, boolean delivered, String reason, int days, double boughtFuel,
                               double outgoingFuel, double returnFuel, double shipCapital, double sold,
                               double landed, double retailBreakEven) {}

    @Test void finiteImportOffersIncludeReturnCostsCapitalAndAfterTaxMarginAndRaiseOnlyDestinationPrices() throws Exception {
        var state = world(new Scenario("mod_fission_thruster", "moon"));
        var original = state; var fleet = state.fleets().getFirst(); var ship = fleet.ships().getFirst();
        var empire = state.empires().getFirst();
        var taxed = new Empire(empire.id(), empire.name(), empire.raceId(), empire.societyStructure(), empire.treasuryCredits(), .25,
                empire.controlledSystemIds(), empire.ministries(), empire.systemGovernorAssignments(), empire.unlockedTechIds(), empire.activeShipDesignIds());
        var company = new Corporation("carrier", "Carrier", "owner", "source", "TRANSPORT", 1e9, List.of(), List.of(ship.id()), List.of());
        var privateShip = new ShipInstance(ship.id(), ship.designId(), company.id(), ship.currentHullHealth(), ship.currentShieldHealth(),
                ship.currentFuelKg(), ship.storedCargoKg()).withPowerState(ship.powerState());
        var privateFleet = new Fleet(fleet.id(), fleet.name(), company.id(), fleet.currentSystemId(), "", 0, 0, 0, false, "PASSIVE",
                List.of(privateShip), fleet.location());
        state = state.toBuilder().empires(List.of(taxed)).corporations(List.of(company)).fleets(List.of(privateFleet)).tradeRoutes(List.of()).build();
        var source = state.commercialHubs().getFirst(); var destination = state.commercialHubs().getLast();
        var offer = TradeSupplyPricing.quote(state, privateFleet, source, destination, "food_matrix");
        assertNotNull(offer); assertEquals(.25, offer.profitTaxRate()); assertTrue(offer.retailPricePerKg() > 8);
        double receipts = offer.retailPricePerKg() * offer.quantityKg() * .8;
        assertEquals(receipts * com.spaceconquest.engine.market.SupplyPricingPolicy.current().targetNetMargin(),
                receipts - offer.operatingCosts() - .25 * (receipts - offer.operatingCosts()) - offer.capitalReserve(), 1e-6);
        var priced = new MarketProcessor().updateCommercialHubs(state, state.commercialHubs());
        assertEquals(offer.retailPricePerKg(), priced.getLast().activeOrders().get("food_matrix").pricePerKg(), 1e-6);
        assertEquals(0, priced.getLast().activeOrders().get("food_matrix").supplyKg());
        assertEquals(original.fleets().getFirst().ships().getFirst().currentFuelKg(), state.fleets().getFirst().ships().getFirst().currentFuelKg());
        var emptyBuyer = state.toBuilder().marketAccounts(List.of(new MarketAccount("source", 1e7), new MarketAccount("target", 0))).build();
        assertNull(TradeSupplyPricing.quote(emptyBuyer, privateFleet, source, destination, "food_matrix"));
    }

    @Test void surfaceExportAddsARealPaidLaunchBillToOrbitalFoodTransport() throws Exception {
        var out = new StringBuilder("# Food surface export cost audit\n\n");
        out.append("Independent 2,500 kg export service probes use existing rocket cargo terminals, baseline RP-1 and oxygen prices and finite fuel. Each actually settles the launch service, consumes propellant and pays the service provider. This is a launch-service cost increment; the orbital food voyages are separate probes.\n\n| Surface | Propellant kg | Fuel credits | Service fee credits | Total credits/kg cargo |\n|---|---:|---:|---:|---:|\n");
        for (String body : List.of("earth", "moon", "mars")) {
            var state = world(new Scenario("mod_hydrolox_rocket", body));
            var orders = Map.of("rp1_kerosene", new MarketOrder("rp1_kerosene", 1e6, 0, 4, 0),
                    "liquid_oxygen", new MarketOrder("liquid_oxygen", 1e6, 0, 4, 0));
            var terminal = new IndustrialFacility("terminal", body, "cargo_terminal", "port-owner", "PUBLIC_STATE", 1, 0, "technician", false, 0);
            var portOwner = new Empire("port-owner", "Port", "human", "Individualist", 0, 0, List.of(), List.of(), Map.of(), List.of(), List.of());
            state = state.toBuilder().empires(List.of(state.empires().getFirst(), portOwner)).industrialFacilities(List.of(terminal))
                    .commercialHubs(List.of(new CommercialHub("ground", body, 0, 1e8, 2e6, 10, orders)))
                    .marketAccounts(List.of(new MarketAccount("ground", 0))).build();
            var plan = LaunchService.choose(state, body, "owner", CARGO_KG, false, false, 0, LaunchService.Mode.ROCKET);
            assertNotNull(plan); double cash = state.empires().getFirst().treasuryCredits();
            var paid = LaunchService.settle(state, "owner", plan);
            assertEquals(plan.totalCredits(), cash - paid.empires().stream().filter(e -> e.id().equals("owner")).findFirst().orElseThrow().treasuryCredits(), 1e-6);
            assertEquals(2e6 - plan.fuelKg(), paid.commercialHubs().getFirst().activeOrders().values().stream().mapToDouble(MarketOrder::supplyKg).sum(), 1e-6);
            assertTrue(plan.fuelKg() > 0);
            out.append(String.format(Locale.ROOT, "| %s | %.2f | %.2f | %.2f | %.2f |\n", body, plan.fuelKg(), plan.fuelCostCredits(),
                    plan.serviceFeeCredits(), plan.totalCredits() / CARGO_KG));
        }
        out.append("\nSurface export does not complete an orbital transfer or a destination landing. The existing launch model supplies its own geometry, fixed carrier mass and provider capacity assumptions. Terminal construction, workforce and capital recovery are outside this service bill. No new launch coefficients or food-specific transport behavior were introduced.\n");
        Files.writeString(Path.of("target/food-surface-export-report.md"), out);
    }

    @Test void bodyGravityUsesSiUnitsEvenBelowFourMetersPerSecondSquared() throws Exception {
        var state = world(new Scenario("mod_hydrolox_rocket", "moon"));
        var earth = OrbitalFreightCost.profile(state, "earth", 1000, CARGO_KG, 4);
        var moon = OrbitalFreightCost.profile(state, "moon", 1000, CARGO_KG, 4);
        var mars = OrbitalFreightCost.profile(state, "mars", 1000, CARGO_KG, 4);
        assertTrue(moon.deltaVRequiredMps() < mars.deltaVRequiredMps());
        assertTrue(mars.deltaVRequiredMps() < earth.deltaVRequiredMps());
        var surface = state.solarSystems().getFirst().planets().stream().filter(p -> p.id().equals("mars")).findFirst().orElseThrow();
        var explicit = new MarketProcessor().calculateOrbitalLiftCostMs2(1000, CARGO_KG, surface.gravity(), 0,
                surface.diameter(), 0, 4, List.of(), List.of(), 1);
        assertEquals(explicit, mars);
    }

    @Test void importsExposeActualFreightCostsAndUnreachableRoutesWithoutInventingSupply() throws Exception {
        var rows = new ArrayList<Measurement>();
        for (String body : List.of("earth", "moon", "mars")) rows.add(run(new Scenario("mod_hydrolox_rocket", body)));
        for (String body : List.of("moon", "mars")) rows.add(run(new Scenario("mod_fission_thruster", body)));
        assertTrue(rows.stream().anyMatch(Measurement::delivered), rows.toString());
        assertTrue(rows.stream().filter(Measurement::delivered).allMatch(r -> r.landed() > 4 && r.outgoingFuel() > 0 && r.sold() == CARGO_KG));
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/food-import-cost-report.md"), report(rows));
    }

    private Measurement run(Scenario scenario) throws Exception {
        var state = world(scenario); var trade = new LogisticsProcessor();
        var source = state.commercialHubs().getFirst(); var target = state.commercialHubs().getLast();
        var route = state.tradeRoutes().getFirst();
        var preview = trade.previewBasket(state, route, Map.of("food_matrix", CARGO_KG));
        double capital = ShipConstructionRequirements.estimate(state.shipDesigns().getFirst()).materialsKg().entrySet().stream()
                .mapToDouble(e -> e.getValue() * MarketProcessor.basePricePerKg(e.getKey())).sum();
        if (preview == null) return new Measurement(scenario, false,
                TradeLegReadiness.inspect(state, state.fleets().getFirst(), target).explanation(), 0, 0, 0, 0, capital, 0, 0, 0);
        var quote = RoamingTradeCost.quote(preview.state(), preview.preparedFleet(), source, target);
        assertNotNull(quote);
        double purchasedFuel = state.empires().getFirst().treasuryCredits() - preview.state().empires().getFirst().treasuryCredits()
                - CARGO_KG * source.activeOrders().get("food_matrix").pricePerKg();
        assertTrue(purchasedFuel > 0, "Initially empty working tanks must be bought from finite port stock");
        var prepared = preview.preparedFleet().ships().getFirst();
        state = preview.state().withTradeRoutes(List.of(preview.route()));
        var site = FleetPositioning.hubSite(state, target); int days = 0;
        while (days < 12000 && !state.fleets().getFirst().location().isAt(site)) {
            state = state.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state),
                    state.orbitalStations(), List.of())).withTurn(state.turn() + 1);
            days++;
            var fleet = state.fleets().getFirst(); var ship = fleet.ships().getFirst();
            assertTrue(fleet.location().localFlight() == null || !fleet.location().localFlight().interrupted());
            assertEquals(0, ship.powerState().lastUnmetEssentialKwh(), 1e-6);
            assertTrue(ship.currentFuelKg() >= fleet.fuelPolicy().reserveKg(state, fleet, ship) - 1e-5);
        }
        assertTrue(state.fleets().getFirst().location().isAt(site), "A funded local import must finish inside the audit horizon");
        var arrived = state.fleets().getFirst().ships().getFirst();
        double fuel = consumedCost(state, prepared, arrived, scenario.drive());
        assertEquals(quote.fuelCredits(), fuel, 1e-4);
        state = trade.fundedDelivery(state, preview.route().withRoaming(false));
        assertNotNull(state, "Actual arrival must sell to funded market demand");
        assertEquals(CARGO_KG, state.commercialHubs().getLast().activeOrders().get("food_matrix").supplyKg(), 1e-6);
        var back = RoamingTradeCost.quote(state, state.fleets().getFirst(), target, source);
        double backFuel = back == null ? Double.NaN : back.fuelCredits() + back.launchCredits();
        double landed = (CARGO_KG * 4 + fuel + quote.launchCredits()) / CARGO_KG;
        double roundTrip = back == null ? Double.NaN : (CARGO_KG * 4 + fuel + quote.launchCredits() + backFuel) / CARGO_KG / IndustryMarketProcessor.WHOLESALE_SHARE;
        return new Measurement(scenario, true, back == null ? "Delivered; empty return unavailable" : "Delivered; empty return quoted",
                days, purchasedFuel, fuel, backFuel, capital, CARGO_KG, landed, roundTrip);
    }

    private double consumedCost(GameState state, ShipInstance before, ShipInstance after, String driveId) {
        var materials = new HashMap<>(PropulsionCatalog.drive(driveId).propellantMaterials(before.currentFuelKg() - after.currentFuelKg()));
        before.storedCargoKg().forEach((id, kg) -> {
            if (!id.equals("food_matrix")) materials.merge(id, Math.max(0, kg - after.storedCargoKg().getOrDefault(id, 0.0)), Double::sum);
        });
        before.powerState().generatorMaterialsKg().forEach((id, kg) -> materials.merge(id,
                Math.max(0, kg - after.powerState().generatorMaterialsKg().getOrDefault(id, 0.0)), Double::sum));
        return materials.entrySet().stream().mapToDouble(e -> e.getValue() * MarketProcessor.basePricePerKg(e.getKey())).sum();
    }

    GameState world(Scenario scenario) throws Exception {
        var known = List.of("rocketry", "large_hull_engineering", "spacecraft", "hydrolox_propulsion", "electricity", "solar_power",
                "industrial_production", "nuclear_fission", "superconductors");
        var owner = new Empire("owner", "Carrier", "human", "Individualist", 1e9, 0, List.of("sol"), List.of(), Map.of(), known, List.of());
        var state = GameState.builder().solarSystems(DataModelLoader.loadSolarSystems()).empires(List.of(owner))
                .industrialFacilities(List.of(new IndustrialFacility("yard", "earth", "surface_shipyard", "owner", "PUBLIC_STATE", 3, 0, "engineer", false, 0)))
                .orbitalStations(List.of(station("source", "earth", 4000), station("target", scenario.body(), 500)))
                .commercialHubs(List.of(port("source", true), port("target", false)))
                .marketAccounts(List.of(new MarketAccount("source", 1e7), new MarketAccount("target", 1e7))).build();
        boolean chemical = scenario.drive().equals("mod_hydrolox_rocket");
        var modules = chemical ? ChemicalFreighterCatalog.modules(scenario.drive())
                : ShipComponentCatalog.workbenchModules(scenario.drive(), false, ShipComponentCatalog.FISSION_REACTOR_ID);
        var evaluated = ShipBlueprintFactory.evaluate(state, new ShipDesignSpecification("design", "Food carrier", "owner", ShipRole.CARGO_TRANSPORT,
                "steel", modules, "steel", 0, modules.stream().map(ShipComponentCatalog::module).mapToInt(ShipModule::slotCost).sum()));
        assertTrue(evaluated.valid(), evaluated.errors().toString());
        var design = evaluated.design();
        var power = new ShipPowerState(Map.of(), "rp1", "uranium", design.powerProfile().batteryKwh(), true, 1, 1, 0, 0, 0, 0, 0);
        var ship = new ShipInstance("ship", design.id(), "owner", 100, 0, 0, Map.of()).withPowerState(power);
        var fleet = new Fleet("fleet", "Carrier", "owner", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(ship), FleetLocation.at(FleetLocation.Site.docked("source")));
        var route = new TradeRoute("route", "Food import", "owner", "source", "target", "food_matrix", CARGO_KG, 0, 1e9, List.of("ship"), 0, true);
        return state.withShipDesigns(List.of(design)).withFleets(List.of(fleet)).withTradeRoutes(List.of(route));
    }

    private OrbitalStation station(String id, String body, double altitude) {
        return new OrbitalStation(id, id, "sol", body, "owner", "PUBLIC_STATE", 40, List.of(), Map.of(),
                0, 0, 0, 0, 100, 100, "steel", 0, true, List.of(), altitude);
    }
    private CommercialHub port(String id, boolean source) {
        var orders = new HashMap<String, MarketOrder>();
        orders.put("food_matrix", new MarketOrder("food_matrix", source ? CARGO_KG : 0, source ? 0 : CARGO_KG, source ? 4 : 8, 0));
        for (String fuel : List.of("liquid_hydrogen", "liquid_oxygen", "hydrogen_gas", "refined_uranium", "rp1_kerosene"))
            orders.put(fuel, new MarketOrder(fuel, 1e6, 0, MarketProcessor.basePricePerKg(fuel), 0));
        return new CommercialHub(id, id, 0, 1e8, orders.values().stream().mapToDouble(MarketOrder::supplyKg).sum(), 10, orders);
    }

    private String report(List<Measurement> rows) {
        var out = new StringBuilder("# Food import cost audit\n\n");
        out.append("Five catalog-carrier probes buy 2,500 kg food at 4 credits/kg and working fuels at baseline prices from finite stocks. Main and generator tanks start empty; batteries start charged. Earth departure stations are at 4,000 km altitude and destination stations at 500 km. The existing travel and daily electrical models execute funded arrivals. Arrival markets have cash and a food price of 8, the ordinary spot rule's limiting scarcity price. Manual shipments may lose money.\n\n")
                .append("| Drive | Destination | Delivered | Days | Initial fuel purchases | Outbound fuel consumed credits | Empty-return fuel quote credits | One-way landed credits/kg | Retail break-even with empty return | Ship construction materials credits |\n|---|---|---|---:|---:|---:|---:|---:|---:|---:|\n");
        for (var r : rows) out.append(String.format(Locale.ROOT, "| %s | %s | %s | %d | %.2f | %.2f | %.2f | %.2f | %.2f | %.2f |\n",
                r.scenario.drive(), r.scenario.body(), r.delivered(), r.days(), r.boughtFuel(), r.outgoingFuel(), r.returnFuel(), r.landed(), r.retailBreakEven(), r.shipCapital()));
        out.append("\n## Route outcomes\n\n");
        for (var r : rows) out.append("- ").append(r.scenario.drive()).append(" to ").append(r.scenario.body()).append(": ").append(r.reason()).append('\n');
        return out.append("\n## Cost boundaries\n\nOne-way landed cost includes source food and consumed outbound working fuel. Unused purchased reserves remain assets rather than voyage expenses. Retail break-even additionally includes the quoted empty return and the 80% wholesale settlement share; it excludes crew, maintenance, financing, tax and capital recovery. The return is a read-only operating quote after actual paid delivery, not an executed return voyage. Rejected departures are unavailable alternatives rather than zero-cost imports. Ship capital is a baseline material bill; its work and yard service are additional. No fresh fuel is injected during a voyage. These are station-to-station costs: surface export and final landing must be added for surface food. Source food is explicitly stocked at its market price, not claimed as free farm output. The fixed representative orbital phases and current propulsion catalog limit these comparisons. Reproduction: `mvn -pl engine -Dtest=FoodImportCostAuditTest test` with JDK 27.\n").toString();
    }
}
