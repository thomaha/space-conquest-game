package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.MarketAccount;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.industry.IndustryMarketProcessor;
import com.spaceconquest.engine.industry.IndustryAccount;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Catalog trade probes with actual paid refinery output and finite industrial inputs. */
class TradePropulsionAuditTest {
    private record Scenario(String drive, boolean crossing) {}
    private record Measurement(Scenario scenario, boolean initiallyReady, String readiness, double plannedDays,
                               int departures, double sold, int portOutages, double produced, double inputCosts,
                               boolean unfinished, double finalCash) {}

    @Test void catalogTradesAndProductionShortagesRespectPhysicalDepartureChecks() throws Exception {
        var rows = new ArrayList<Measurement>();
        for (String drive : List.of("mod_chemical_rocket", "mod_fission_thruster", "mod_ion_drive")) {
            rows.add(run(new Scenario(drive, false)));
            rows.add(run(new Scenario(drive, true)));
        }
        Path output = Path.of("target/trade-propulsion-report.md");
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, report(rows));
        assertTrue(rows.stream().filter(row -> !row.scenario().crossing()).allMatch(row ->
                row.initiallyReady() && row.departures() == 8 && row.sold() == 800 && row.portOutages() == 0));
        assertTrue(rows.stream().allMatch(row -> row.produced() > 0 && row.inputCosts() > 0));
        assertTrue(rows.stream().anyMatch(row -> row.scenario().crossing() && !row.initiallyReady()),
                "The audit must exercise a realistically distant departure rejected by current reserves.");
    }

    private Measurement run(Scenario scenario) {
        var state = world(scenario);
        var fleet = state.fleets().getFirst(); var target = state.commercialHubs().getLast();
        var readiness = TradeLegReadiness.inspect(state, fleet, target);
        var local = LocalTravel.plan(state, fleet, FleetLocation.Site.docked(target.entityId()));
        double days = local == null ? 0 : local.days();
        if (scenario.crossing()) {
            var departure = LocalTravel.plan(state, fleet, FleetLocation.Site.deepSpace());
            var outbound = departure == null ? null : LocalTravel.depart(fleet, FleetLocation.Site.deepSpace(), departure)
                    .withLocation(FleetLocation.at(FleetLocation.Site.deepSpace()));
            var plan = outbound == null ? null : TradeLegReadiness.crossing(state, outbound, target);
            days = plan == null ? 0 : Math.ceil(plan.days());
        }
        int departures = 0, outages = 0; double produced = 0, inputCosts = 0;
        var movement = new FleetProcessor(); var trade = new LogisticsProcessor();
        for (int day = 0; day < 360; day++) {
            var staff = new HashMap<String, Integer>();
            for (var facility : state.industrialFacilities()) staff.put(facility.id(), day >= 20 && day < 80 ? 0 : 40);
            var industry = new IndustryMarketProcessor().process(state, staff, Map.of());
            produced += industry.industryAccounts().stream().flatMap(account -> account.producedKg().values().stream()).mapToDouble(Double::doubleValue).sum();
            inputCosts += industry.industryAccounts().stream().mapToDouble(account -> account.inputCostsCredits()).sum();
            state = state.toBuilder().empires(industry.empires()).corporations(industry.corporations())
                    .commercialHubs(industry.hubs()).marketAccounts(industry.marketAccounts()).industryAccounts(industry.industryAccounts()).build();
            state = state.withFleets(movement.processFleetMovements(ShipPowerProcessor.advanceDay(state), List.of(), List.of()));
            fleet = state.fleets().getFirst();
            assertNotEquals(Fleet.MODE_POWER_INTERRUPTED, fleet.interstellarMode(), scenario.toString());
            if (!fleet.hasInterstellarOrder() && !fleet.location().inTransit()
                    && fleet.ships().getFirst().powerState().lastUnmetEssentialKwh() > 1e-6) outages++;
            double before = state.tradeRoutes().getFirst().onboardKg();
            state = trade.processTradeRoutes(state).state();
            if (before == 0 && state.tradeRoutes().getFirst().onboardKg() > 0) departures++;
            var ship = state.fleets().getFirst().ships().getFirst();
            assertTrue(Double.isFinite(ship.currentFuelKg()) && ship.currentFuelKg() >= 0);
            assertTrue(ship.generatorFuelMassKg() >= 0);
            assertTrue(state.empires().getFirst().treasuryCredits() >= 0);
        }
        return new Measurement(scenario, readiness.ready(), readiness.explanation(), days, departures,
                state.tradeRoutes().getFirst().totalVolumeMovedKg(), outages, produced, inputCosts,
                state.fleets().getFirst().hasInterstellarOrder() || state.fleets().getFirst().location().inTransit(),
                state.empires().getFirst().treasuryCredits());
    }

    private GameState world(Scenario scenario) {
        var planet = new Planet("earth", "Reference", "", 1, 9.81, SolarRadiation.AU_KM,
                0, 12000, "terrestrial", "air", false, 0, List.of(), List.of(), List.of());
        var systems = List.of(new SolarSystem("a", "A", "", 0, 0, 0, SolarRadiation.SOLAR_MASS_KG,
                1392000, "yellow", List.of(planet), List.of()), new SolarSystem("b", "B", "", 1, 0, 0,
                SolarRadiation.SOLAR_MASS_KG, 1392000, "yellow", List.of(), List.of()));
        var owner = new Empire("owner", "Owner", "human", "Individualist", 1000000, 0, List.of("a", "b"), List.of(), Map.of(),
                List.of("rocketry", "electricity", "industrial_production", "nuclear_fission", "superconductors"), List.of());
        var facilities = new ArrayList<IndustrialFacility>();
        facilities.add(new IndustrialFacility("yard", "earth", "surface_shipyard", "owner", IndustrialFacility.PUBLIC_STATE, 3, 0, "engineer", false, 0));
        var ports = new ArrayList<CommercialHub>(); var stations = new ArrayList<OrbitalStation>();
        for (int index = 0; index < 2; index++) {
            String id = "port_" + index;
            stations.add(new OrbitalStation(id, id, index == 1 && scenario.crossing() ? "b" : "a", "", "owner",
                    "PUBLIC_STATE", 10, List.of(), Map.of(), 0, 0, 0, 0, 100, 100, "steel", 0, true));
            var orders = new HashMap<String, MarketOrder>();
            orders.put("steel", new MarketOrder("steel", index == 0 ? 400 : 0, index == 0 ? 0 : 1000, index == 0 ? 1 : 20, 0));
            orders.put("refined_copper", new MarketOrder("refined_copper", index == 1 ? 400 : 0, index == 1 ? 0 : 1000, index == 1 ? 1 : 20, 0));
            for (String input : List.of("hydrocarbons", "oxygen_gas", "uranium_ore")) orders.put(input, new MarketOrder(input, 2000000, 0, .001, 0));
            for (String fuel : List.of("rp1_kerosene", "liquid_oxygen", "refined_uranium")) orders.put(fuel, new MarketOrder(fuel, 0, 100000, .001, 0));
            orders.put("hydrogen_gas", new MarketOrder("hydrogen_gas", 100000, 0, .001, 0));
            orders.put("methane_ice", new MarketOrder("methane_ice", 100000, 0, .001, 0));
            ports.add(new CommercialHub(id, id, 0, 1000000000, orders.values().stream().mapToDouble(MarketOrder::supplyKg).sum(), 10, orders));
            for (String recipe : List.of("rp1_refining", "oxygen_liquefaction", "uranium_refining"))
                facilities.add(new IndustrialFacility(id + recipe, id, recipe, "owner", IndustrialFacility.PUBLIC_STATE, 1, 40, "technician", false, 0));
        }
        var state = GameState.builder().solarSystems(systems).empires(List.of(owner)).industrialFacilities(facilities)
                .industryAccounts(facilities.stream().map(facility -> IndustryAccount.empty(facility.id()).withOperatingCash(100000)).toList())
                .orbitalStations(stations).commercialHubs(ports).marketAccounts(List.of(new MarketAccount("port_0", 100000), new MarketAccount("port_1", 100000))).build();
        boolean chemical = scenario.drive().equals("mod_chemical_rocket");
        var powerModule = chemical ? ShipComponentCatalog.CHEMICAL_GENERATOR_ID : ShipComponentCatalog.FISSION_REACTOR_ID;
        var evaluation = ShipBlueprintFactory.evaluate(state, new ShipDesignSpecification("design", scenario.drive(), "owner",
                ShipRole.CARGO_TRANSPORT, "steel", ShipComponentCatalog.workbenchModules(scenario.drive(), false, powerModule), "steel", 0));
        assertTrue(evaluation.valid(), evaluation.errors().toString());
        var design = evaluation.design();
        var feed = chemical ? "rp1" : "uranium";
        var power = new ShipPowerState(design.powerProfile().fuels().get(feed).materials(chemical ? 15000 : 100),
                "rp1", "uranium", 500, true, 1, 1, 0, 0, 0, 0, 0);
        var ship = new ShipInstance("ship", "design", "owner", 100, 0, design.fuelCapacityKg(),
                scenario.drive().equals("mod_fission_thruster") ? Map.of("refined_uranium", 15.0) : Map.of()).withPowerState(power);
        var fleet = new Fleet("fleet", "Trader", "owner", "a", "", 0, 0, 0, false, "PASSIVE", List.of(ship), FleetLocation.at(FleetLocation.Site.docked("port_0")));
        var route = new TradeRoute("route", "Catalog trader", "owner", "port_0", "port_1", "steel", 100, 0, 10000, List.of("ship"), 0, true).withRoaming(true);
        return state.withShipDesigns(List.of(design)).withFleets(List.of(fleet)).withTradeRoutes(List.of(route));
    }

    private String report(List<Measurement> rows) {
        var text = new StringBuilder("# Catalog trade and production audit\n\n");
        text.append("Six 360-day probes use blueprints built by the normal catalog. Local station travel uses provisional site minimums extended by available thrust and loaded mass. Crossing probes place stars one light-year apart. Fuel factories use the real paid industry processor with finite input stocks; finished chemical and electrical reactor fuels start at zero in port markets. Hydrogen and MPD propellant have explicit opening stocks. Powered staff are fixture inputs and production pauses on days 20-79; wages and grid generation are not simulated. Opening ship tanks, factory operating cash and buyer cash are explicit seeds. Ports start with opposing steel and copper stocks. No goods, fuel or cash is replenished externally during the run.\n\n")
                .append("| Drive | Destination | Initial next-leg readiness | Unloaded local/crossing days | Departures | Sold kg | Port outage days | Factory output kg | Paid industrial input credits | Voyage unfinished | Final owner cash |\n")
                .append("|---|---|---|---:|---:|---:|---:|---:|---:|---|---:|\n");
        for (var row : rows) text.append(String.format(Locale.ROOT, "| %s | %s | %s | %.0f | %d | %.0f | %d | %.0f | %.1f | %s | %.1f |\n",
                row.scenario().drive(), row.scenario().crossing() ? "1 light-year" : "Local station", row.initiallyReady(), row.plannedDays(),
                row.departures(), row.sold(), row.portOutages(), row.produced(), row.inputCosts(), row.unfinished(), row.finalCash()));
        text.append("\n## Initial readiness explanations\n\n");
        for (var row : rows) text.append("- ").append(row.scenario().drive()).append(row.scenario().crossing() ? " at 1 light-year: " : " locally: ").append(row.readiness()).append('\n');
        text.append("\n## Limits and reproduction\n\nRun `mvn test` with JDK 27; the measured report is regenerated at `engine/target/trade-propulsion-report.md`. Unloaded plan duration is a scale probe, not a funded shipment estimate. Crossing rows report only the crossing duration; local departure and approach are excluded. Journeys longer than the run are not claimed as completed. Assertions protect movement against power interruptions and finite nonnegative fuel and owner cash. Factory output includes all three fuel recipes at both ports; payments and input depletion use actual industry accounting. This fixture does not simulate the full population, power grid, changing prices or industrial workforce economy. No coefficient is tuned to make interstellar chemical or thermal travel artificially viable.\n");
        return text.toString();
    }
}
