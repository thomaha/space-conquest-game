package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.MarketAccount;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Sol-scale station routes with catalog blueprints, paid mixed cargo and actual daily travel ticks. */
class LocalTradeDistanceAuditTest {
    private static final int HORIZON_DAYS = 12_000;
    private record Equipment(String label, String drive, String power, boolean solarBackup) {
        private Equipment(String label, String drive, String power) { this(label, drive, power, false); }
    }
    private record Measurement(String equipment, double cargoKg, String body, double distanceKm, double flux, boolean funded,
                               String reason, double plannedDays, int actualDays, double plannedMain, double actualMain,
                               double plannedElectric, double actualElectric, double batteryError, double sold,
                               boolean completed, boolean dwellReady) {}

    @Test void solScaleMixedTradesMatchForecastsAndPreserveArrivalInsurance() throws Exception {
        var rows = new ArrayList<Measurement>();
        var equipment = List.of(new Equipment("Chemical auxiliary", "mod_chemical_rocket", ShipComponentCatalog.CHEMICAL_GENERATOR_ID),
                new Equipment("Chemical solar", "mod_chemical_rocket", ShipComponentCatalog.SOLAR_ARRAY_ID),
                new Equipment("Fission thermal", "mod_fission_thruster", ShipComponentCatalog.FISSION_REACTOR_ID),
                new Equipment("MPD solar", "mod_ion_drive", ShipComponentCatalog.SOLAR_ARRAY_ID),
                new Equipment("MPD fission", "mod_ion_drive", ShipComponentCatalog.FISSION_REACTOR_ID));
        for (String body : List.of("moon", "mars", "jupiter"))
            for (var setup : equipment) rows.add(run(setup, body));
        var smaller = run(new Equipment("Chemical auxiliary half load", "mod_chemical_rocket",
                ShipComponentCatalog.CHEMICAL_GENERATOR_ID), "moon", 2500);
        rows.add(smaller);
        assertTrue(smaller.completed());
        var output = Path.of("target/local-trade-distance-report.md");
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, report(rows), StandardCharsets.UTF_8);
        assertTrue(rows.stream().anyMatch(row -> !row.funded()), "Audit must include rejected long-distance equipment.");
        for (String body : List.of("moon", "mars", "jupiter"))
            assertTrue(rows.stream().anyMatch(row -> row.body().equals(body) && row.completed()), "No completed probe for " + body);
    }

    private Measurement run(Equipment equipment, String body) throws Exception {
        return run(equipment, body, 5000);
    }

    @Test void chemicalSolarWithBackupMatchesTicksOnRealStationRoutes() throws Exception {
        var setup = new Equipment("Chemical solar with backup", "mod_chemical_rocket",
                ShipComponentCatalog.CHEMICAL_GENERATOR_ID, true);
        var rows = new ArrayList<Measurement>();
        for (String body : List.of("moon", "mars", "jupiter")) rows.add(run(setup, body));
        assertTrue(rows.getFirst().completed());
        assertEquals(0, rows.getFirst().actualElectric(), .000001);
        var output = Path.of("target/auxiliary-power-report.md");
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, report(rows).replace("# Local trade distance audit", "# Auxiliary power audit")
                .replace("a half-load probe carries 2,500 kg of each", "these probes all carry the full mixed load")
                .replace("Solar MPD blueprints use two catalog arrays and chemical solar uses one.",
                        "Each chemical ship uses one solar array, a battery, a chemical auxiliary generator and a full dedicated generator tank.")
                .replace("engine/target/local-trade-distance-report.md", "engine/target/auxiliary-power-report.md"), StandardCharsets.UTF_8);
    }

    @Test void orbitalBenchmarksExposeFuelAndFiniteBurnLimitsWithoutChangingState() throws Exception {
        var equipment = List.of(new Equipment("RP-1 solar", "mod_chemical_rocket", ShipComponentCatalog.SOLAR_ARRAY_ID),
                new Equipment("Methalox solar", "mod_methalox_rocket", ShipComponentCatalog.SOLAR_ARRAY_ID),
                new Equipment("Hydrolox solar", "mod_hydrolox_rocket", ShipComponentCatalog.SOLAR_ARRAY_ID),
                new Equipment("RP-1 solar backup", "mod_chemical_rocket", ShipComponentCatalog.CHEMICAL_GENERATOR_ID, true),
                new Equipment("Fission thermal", "mod_fission_thruster", ShipComponentCatalog.FISSION_REACTOR_ID),
                new Equipment("MPD fission", "mod_ion_drive", ShipComponentCatalog.FISSION_REACTOR_ID));
        var output = new StringBuilder("# Planetary transfer comparison\n\n## Scope\n\n");
        output.append("72 read-only probes compare circular coplanar Hohmann benchmarks with the current straight-line planner. ")
                .append("Each catalog blueprint carries a full main tank, its normal electrical reserves and either 10,000 kg of mixed steel and food or no trade cargo. ")
                .append("Fission thermal ships retain their 15 kg drive reactor feed. Parking altitude is 500 km at both planets. ")
                .append("Three relative phases are the ideal departure phase, ideal plus 90 degrees and ideal plus 180 degrees. ")
                .append("The provisional impulse check limits each wet-mass burn estimate to 10% of the local parking period. ")
                .append("Wait and coast are informational; no transfer is executed and no power, passenger or economy readiness is asserted.\n\n")
                .append("## Results\n\n| Equipment | Cargo kg | Target | Phase offset degrees | Straight-line scheduled days | Wait days | Coast days | Departure m/s | Capture m/s | Available main delta-v m/s | Required main kg | Protected main kg | Main fits | Reactor feed fits | Departure burn/period | Capture burn/period | Impulse fits |\n")
                .append("|---|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|---|---:|---:|---|\n");
        for (var setup : equipment) for (String body : List.of("mars", "jupiter")) for (double goodsKg : new double[]{5000, 0}) {
            var original = world(setup, body);
            var fleet = original.fleets().getFirst().withShips(List.of(loaded(original.fleets().getFirst().ships().getFirst(), goodsKg)));
            var state = original.withFleets(List.of(fleet));
            var system = state.solarSystems().getFirst();
            var earth = system.planets().stream().filter(planet -> planet.id().equals("earth")).findFirst().orElseThrow();
            var target = system.planets().stream().filter(planet -> planet.id().equals(body)).findFirst().orElseThrow();
            var baseline = LocalTravel.plan(state, fleet, FleetLocation.Site.docked("target"));
            var ideal = PlanetaryTransferComparison.orbit(system, earth, target, 0);
            for (int offset : new int[]{0, 90, 180}) {
                var orbit = PlanetaryTransferComparison.orbit(system, earth, target, ideal.requiredPhaseRadians() + Math.toRadians(offset));
                var budget = PlanetaryTransferComparison.budget(state, fleet, fleet.ships().getFirst(), orbit);
                assertTrue(budget.protectedPropellantKg() > 0);
                assertTrue(budget.requiredPropellantKg() > 0);
                if (setup.drive().equals("mod_ion_drive")) assertFalse(budget.impulseFits(), "Low-thrust MPD cannot use an impulsive capture.");
                if (setup.drive().equals("mod_chemical_rocket")) assertFalse(budget.propellantFits(), "The benchmark must retain both burns and reserve.");
                output.append(String.format(Locale.ROOT,
                        "| %s | %.0f | %s | %d | %s | %.2f | %.2f | %.1f | %.1f | %.1f | %.2f | %.0f | %s | %s | %.4f | %.4f | %s |\n",
                        setup.label(), goodsKg * 2, body, offset, baseline == null ? "Unavailable" : String.format(Locale.ROOT, "%.0f", Math.ceil(baseline.days())),
                        orbit.waitDays(), orbit.coastDays(), orbit.departureDeltaVMps(), orbit.captureDeltaVMps(), budget.availableDeltaVMps(),
                        budget.requiredPropellantKg(), budget.protectedPropellantKg(), budget.propellantFits(), budget.reactorFeedFits(),
                        budget.departureBurnPeriodFraction(), budget.captureBurnPeriodFraction(), budget.impulseFits()));
            }
            assertEquals(state, original.withFleets(List.of(fleet)), "Read-only comparison must preserve all state.");
        }
        output.append("\n## Limits and reproduction\n\nRun `mvn test` with JDK 27. Generated output: `engine/target/planetary-transfer-report.md`. ")
                .append("Coplanar benchmarks omit catalog inclination, station phases, sphere-of-influence transit times, exact finite burns, evolving mass, aerobraking, gravity assists, boil-off and electrical endurance. ")
                .append("Parking escape and powered capture use hyperbolic excess speed and local planet mass. Feed checks retain reactor feed for protected propellant. ")
                .append("Available main delta-v only measures main propellant; the separate feed column remains required. No row establishes full journey readiness or authorizes travel.\n");
        var path = Path.of("target/planetary-transfer-report.md");
        Files.createDirectories(path.toAbsolutePath().getParent());
        Files.writeString(path, output, StandardCharsets.UTF_8);
    }

    @Test void parkingAndTankComparisonsUseValidatedBlueprintsAndRecomputeLoadedMass() throws Exception {
        var equipment = List.of(new Equipment("RP-1 solar", "mod_chemical_rocket", ShipComponentCatalog.SOLAR_ARRAY_ID),
                new Equipment("Methalox solar", "mod_methalox_rocket", ShipComponentCatalog.SOLAR_ARRAY_ID),
                new Equipment("Hydrolox solar", "mod_hydrolox_rocket", ShipComponentCatalog.SOLAR_ARRAY_ID),
                new Equipment("Fission thermal", "mod_fission_thruster", ShipComponentCatalog.FISSION_REACTOR_ID));
        var output = new StringBuilder("# Parking orbit and tank comparison\n\n## Scope\n\n");
        output.append("Read-only catalog probes vary one to three main tanks, empty or 10,000 kg mixed cargo and Mars or Jupiter targets. ")
                .append("Parking altitude pairs in km are 500/500, 100,000/500, 100,000/8,000 and 100,000/100,000. ")
                .append("Every candidate is rebuilt by the blueprint factory. Tanks add real mass, slots and capacity and start full. ")
                .append("Fission drive cargo is seeded at 0.001 kg per kg of main capacity; protected main reserves scale with tank capacity. ")
                .append("These are separate imagined base locations, not free moves from existing stations. High-orbit access costs, electricity and actual navigation remain unevaluated.\n\n")
                .append("## Results\n\n| Equipment | Tanks | Slots | Cargo kg | Target | Source altitude km | Target altitude km | Required delta-v m/s | Available main delta-v m/s | Initial wet kg | Required main kg | Usable main kg | Protected main kg | Main fits | Feed fits | Impulse fits |\n")
                .append("|---|---:|---:|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|---|---|---|\n");
        var rejections = new StringBuilder();
        int probes = 0, passed = 0;
        for (var setup : equipment) for (String body : List.of("mars", "jupiter")) for (int tanks = 1; tanks <= 3; tanks++) {
            var base = world(setup, body);
            var original = base.shipDesigns().getFirst();
            var modules = new ArrayList<>(original.equippedModuleIds());
            for (int added = 1; added < tanks; added++) modules.add(PropulsionCatalog.FUEL_TANK_MODULE_ID);
            var evaluated = ShipBlueprintFactory.evaluate(base, new ShipDesignSpecification("design", setup.label(), "owner",
                    ShipRole.CARGO_TRANSPORT, "steel", modules, "steel", 0));
            if (!evaluated.valid()) {
                rejections.append("- ").append(setup.label()).append(" with ").append(tanks).append(" tanks for ")
                        .append(body).append(": ").append(String.join("; ", evaluated.errors())).append('\n');
                continue;
            }
            var design = evaluated.design();
            int slots = modules.stream().map(ShipComponentCatalog::module).mapToInt(ShipModule::slotCost).sum();
            for (double goodsKg : new double[]{0, 5000}) {
                var prior = base.fleets().getFirst().ships().getFirst();
                var cargo = new HashMap<String, Double>();
                if (goodsKg > 0) { cargo.put("steel", goodsKg); cargo.put("food_matrix", goodsKg); }
                if (setup.drive().equals("mod_fission_thruster")) cargo.put("refined_uranium", design.fuelCapacityKg() * .001);
                var ship = new ShipInstance(prior.id(), design.id(), prior.ownerEntityId(), 100, 0, design.fuelCapacityKg(), cargo)
                        .withPowerState(prior.powerState());
                var fleet = base.fleets().getFirst().withShips(List.of(ship));
                var state = base.withShipDesigns(List.of(design)).withFleets(List.of(fleet));
                var system = state.solarSystems().getFirst();
                var earth = system.planets().stream().filter(planet -> planet.id().equals("earth")).findFirst().orElseThrow();
                var target = system.planets().stream().filter(planet -> planet.id().equals(body)).findFirst().orElseThrow();
                for (double[] altitudes : new double[][]{{500, 500}, {100000, 500}, {100000, 8000}, {100000, 100000}}) {
                    var orbit = PlanetaryTransferComparison.orbit(system, earth, target, 0, altitudes[0], altitudes[1]);
                    var budget = PlanetaryTransferComparison.budget(state, fleet, ship, orbit);
                    probes++;
                    if (budget.propellantFits() && budget.reactorFeedFits() && budget.impulseFits()) passed++;
                    assertEquals(design.fuelCapacityKg() * .05, budget.protectedPropellantKg(), .000001);
                    assertEquals(design.totalDryMassKg() + design.fuelCapacityKg() + prior.generatorFuelMassKg()
                            + cargo.values().stream().mapToDouble(Double::doubleValue).sum(), budget.wetMassKg(), .000001);
                    if (tanks > 1) assertTrue(design.totalDryMassKg() > original.totalDryMassKg());
                    assertTrue(slots <= 30);
                    output.append(String.format(Locale.ROOT,
                            "| %s | %d | %d | %.0f | %s | %.0f | %.0f | %.1f | %.1f | %.2f | %.2f | %.2f | %.0f | %s | %s | %s |\n",
                            setup.label(), tanks, slots, goodsKg * 2, body, altitudes[0], altitudes[1], orbit.totalDeltaVMps(),
                            budget.availableDeltaVMps(), budget.wetMassKg(), budget.requiredPropellantKg(), budget.usablePropellantKg(),
                            budget.protectedPropellantKg(), budget.propellantFits(), budget.reactorFeedFits(), budget.impulseFits()));
                }
                assertEquals(state, base.withShipDesigns(List.of(design)).withFleets(List.of(fleet)));
            }
        }
        assertTrue(passed > 0, "The comparison should identify maneuver-feasible catalog configurations.");
        assertFalse(rejections.isEmpty(), "Oversized tank configurations must be rejected.");
        output.append("\n## Counts and rejected blueprints\n\n").append(probes).append(" valid-blueprint probes; ")
                .append(passed).append(" pass all benchmark maneuver checks. These do not establish full journey readiness.\n\n").append(rejections)
                .append("\n## Reproduction and limits\n\nRun `mvn test` with JDK 27. Generated output: `engine/target/parking-tank-report.md`. ")
                .append("All circular coplanar and impulsive limitations of the planetary report apply. Parking radii must remain within the approximate planetary sphere of influence. ")
                .append("High-altitude station construction, cargo export, local approach, daily power, phase waiting and paid refill readiness require separate budgets. Opening stores are fixture seeds.\n");
        Files.writeString(Path.of("target/parking-tank-report.md"), output, StandardCharsets.UTF_8);
    }

    @Test void smallerMixedChemicalMoonShipmentIsSelectedWithoutInventingFuel() throws Exception {
        var state = world(new Equipment("Chemical auxiliary", "mod_chemical_rocket", ShipComponentCatalog.CHEMICAL_GENERATOR_ID), "moon");
        assertNull(new LogisticsProcessor().previewBasket(state, state.tradeRoutes().getFirst(),
                Map.of("steel", 5000.0, "food_matrix", 5000.0)));
        var chosen = TradeBasketPlanner.choose(state, state.tradeRoutes().getFirst(), state.fleets().getFirst(), state.commercialHubs().getLast());
        assertNotNull(chosen);
        assertTrue(chosen.profit() > 0);
        assertTrue(chosen.shipment().route().onboardKg() > 0 && chosen.shipment().route().onboardKg() < 10000);
        assertEquals(2, chosen.shipment().route().cargoManifest().size());
        assertTrue(TradeLegReadiness.inspect(chosen.shipment().state(), chosen.shipment().preparedFleet(),
                chosen.shipment().state().commercialHubs().getLast()).ready());
        assertEquals(state.fleets().getFirst().ships().getFirst().currentFuelKg(),
                chosen.shipment().preparedFleet().ships().getFirst().currentFuelKg());
        assertEquals(state.fleets().getFirst().ships().getFirst().generatorFuelMassKg(),
                chosen.shipment().preparedFleet().ships().getFirst().generatorFuelMassKg());
        assertEquals(5000, state.commercialHubs().getFirst().activeOrders().get("food_matrix").supplyKg());
    }

    @Test void extraSolarArraysMustRespectTheCatalogHullSlotBudget() throws Exception {
        var state = world(new Equipment("MPD solar", "mod_ion_drive", ShipComponentCatalog.SOLAR_ARRAY_ID), "mars");
        var modules = new ArrayList<>(state.shipDesigns().getFirst().equippedModuleIds());
        modules.add(ShipComponentCatalog.SOLAR_ARRAY_ID); modules.add(ShipComponentCatalog.SOLAR_ARRAY_ID);
        var evaluated = ShipBlueprintFactory.evaluate(state, new ShipDesignSpecification("oversized", "Four-array MPD", "owner",
                ShipRole.CARGO_TRANSPORT, "steel", modules, "steel", 0));
        assertFalse(evaluated.valid());
        assertTrue(evaluated.errors().stream().anyMatch(error -> error.contains("module slots") && error.contains("36") && error.contains("30")));
    }

    private Measurement run(Equipment equipment, String body, double goodsKg) throws Exception {
        var state = world(equipment, body);
        var fleet = state.fleets().getFirst(); var target = state.commercialHubs().getLast();
        var site = FleetPositioning.hubSite(state, target);
        var loaded = fleet.withShips(List.of(loaded(fleet.ships().getFirst(), goodsKg)));
        var initialPlan = LocalTravel.plan(state, loaded, site);
        var geometry = LocalSiteGeometry.resolve(state, fleet.currentSystemId(), fleet.location().current(), site);
        assertNotNull(geometry);
        double distance = geometry.distanceMeters() / 1000;
        var readiness = TradeLegReadiness.inspect(state, loaded, target);
        var preview = new LogisticsProcessor().previewBasket(state, state.tradeRoutes().getFirst(),
                Map.of("steel", goodsKg, "food_matrix", goodsKg));
        double flux = ShipSolarEnvironment.at(state, fleet, site).fluxRelativeToEarth();
        if (preview == null) {
            assertFalse(readiness.ready(), "Paid shipment rejection must agree with loaded readiness.");
            assertEquals(0, state.fleets().getFirst().ships().getFirst().storedCargoKg().getOrDefault("steel", 0.0));
            return new Measurement(equipment.label(), 2 * goodsKg, body, distance, flux, false, readiness.explanation(),
                    initialPlan == null ? 0 : Math.ceil(initialPlan.days()), 0, 0, 0, 0, 0, 0, 0, false, false);
        }
        var prepared = preview.preparedFleet();
        var design = state.shipDesigns().getFirst();
        var plan = LocalTravel.plan(preview.state(), prepared, site);
        assertNotNull(plan);
        var forecast = LocalSpacePowerForecast.check(preview.state(), prepared, prepared.ships().getFirst(), design, site, plan);
        assertTrue(forecast.ready());
        var expectedArrival = LocalTravel.projectedArrival(prepared, site, plan);
        double plannedMain = prepared.ships().getFirst().currentFuelKg() - expectedArrival.ships().getFirst().currentFuelKg();
        double initialElectric = prepared.ships().getFirst().generatorFuelMassKg();
        double initialMain = prepared.ships().getFirst().currentFuelKg();
        double plannedElectric = initialElectric - forecast.state().fuelMassKg();
        state = preview.state().withTradeRoutes(List.of(preview.route()));
        int actualDays = 0;
        var movement = new FleetProcessor();
        while (actualDays < HORIZON_DAYS && !state.fleets().getFirst().location().isAt(site)) {
            state = state.withFleets(movement.processFleetMovements(ShipPowerProcessor.advanceDay(state), List.of(), List.of()));
            actualDays++;
            var current = state.fleets().getFirst(); var ship = current.ships().getFirst();
            assertTrue(current.location().localFlight() == null || !current.location().localFlight().interrupted(), equipment + " to " + body);
            assertEquals(0, ship.powerState().lastUnmetEssentialKwh(), 1e-6);
            assertEquals(0, ship.powerState().unmetCargoKwh(), 1e-6);
            assertTrue(ship.currentFuelKg() >= prepared.fuelPolicy().reserveKg(state, prepared, ship) - 1e-5);
            assertEquals(goodsKg, ship.storedCargoKg().get("food_matrix"), 1e-6);
        }
        boolean completed = state.fleets().getFirst().location().isAt(site);
        var arrived = state.fleets().getFirst().ships().getFirst();
        double actualMain = initialMain - arrived.currentFuelKg(), actualElectric = initialElectric - arrived.generatorFuelMassKg();
        double batteryError = completed ? arrived.powerState().batteryChargeKwh() - forecast.state().batteryChargeKwh() : 0;
        boolean dwellReady = false; double sold = 0;
        if (completed) {
            assertEquals(Math.ceil(plan.days()), actualDays);
            assertEquals(plannedMain, actualMain, 1e-5);
            assertEquals(plannedElectric, actualElectric, 1e-5);
            assertEquals(0, batteryError, 1e-5);
            dwellReady = ShipArrivalReserve.check(design.powerProfile(), arrived.powerState(),
                    design.powerProfile().essentialKw(arrived, design), ShipPowerProcessor.cargoKw(design.powerProfile(), arrived, design),
                    ShipSolarEnvironment.at(state, state.fleets().getFirst(), site), TradePortMaintenance.DWELL_RESERVE_HOURS).ready();
            assertTrue(dwellReady);
            // Destination fuel remains absent: the carried reserve must sustain actual waiting ticks too.
            for (int day = 0; day < TradePortMaintenance.DWELL_RESERVE_HOURS / 24; day++) {
                state = state.withFleets(ShipPowerProcessor.advanceDay(state));
                var waiting = state.fleets().getFirst().ships().getFirst();
                assertEquals(0, waiting.powerState().lastUnmetEssentialKwh(), 1e-6);
                assertEquals(0, waiting.powerState().unmetCargoKwh(), 1e-6);
                assertEquals(goodsKg, waiting.storedCargoKg().get("food_matrix"), 1e-6);
            }
            state = new LogisticsProcessor().processTradeRoutes(state).state();
            sold = state.tradeRoutes().getFirst().totalVolumeMovedKg();
            assertEquals(2 * goodsKg, sold, 1e-6);
            assertTrue(state.tradeRoutes().getFirst().cargoManifest().isEmpty());
        }
        return new Measurement(equipment.label(), 2 * goodsKg, body, distance, flux, true, readiness.explanation(),
                Math.ceil(plan.days()), actualDays, plannedMain, actualMain, plannedElectric, actualElectric,
                batteryError, sold, completed, dwellReady);
    }

    private ShipInstance loaded(ShipInstance ship, double goodsKg) {
        var cargo = new HashMap<>(ship.storedCargoKg());
        cargo.put("steel", goodsKg); cargo.put("food_matrix", goodsKg);
        return new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), ship.currentHullHealth(),
                ship.currentShieldHealth(), ship.currentFuelKg(), cargo, ship.passengerCount(), ship.passengerRaceId(),
                ship.transitMode(), ship.powerState(), ship.supplyState());
    }

    private GameState world(Equipment equipment, String destination) throws Exception {
        var owner = new Empire("owner", "Owner", "human", "Individualist", 1_000_000, 0, List.of("sol"), List.of(), Map.of(),
                List.of("rocketry", "methalox_propulsion", "hydrolox_propulsion", "electricity", "solar_power", "industrial_production", "nuclear_fission", "superconductors"), List.of());
        var yard = new IndustrialFacility("yard", "earth", "surface_shipyard", "owner", IndustrialFacility.PUBLIC_STATE,
                3, 0, "engineer", false, 0);
        var state = GameState.builder().solarSystems(DataModelLoader.loadSolarSystems()).empires(List.of(owner))
                .industrialFacilities(List.of(yard)).orbitalStations(List.of(station("source", "earth"), station("target", destination)))
                .commercialHubs(List.of(port("source", true), port("target", false)))
                .marketAccounts(List.of(new MarketAccount("source", 100000), new MarketAccount("target", 1000000))).build();
        var modules = new ArrayList<>(ShipComponentCatalog.workbenchModules(equipment.drive(), false,
                equipment.power(), equipment.solarBackup()));
        if (equipment.power().equals(ShipComponentCatalog.SOLAR_ARRAY_ID) && equipment.drive().equals("mod_ion_drive"))
            modules.add(ShipComponentCatalog.SOLAR_ARRAY_ID);
        var evaluated = ShipBlueprintFactory.evaluate(state, new ShipDesignSpecification("design", equipment.label(), "owner",
                ShipRole.CARGO_TRANSPORT, "steel", modules, "steel", 0));
        assertTrue(evaluated.valid(), evaluated.errors().toString());
        var design = evaluated.design(); var profile = design.powerProfile();
        var generator = new HashMap<String, Double>();
        if (profile.generatorTankKg() > 0) generator.putAll(profile.fuels().get("rp1").materials(profile.generatorTankKg()));
        if (profile.reactorTankKg() > 0) generator.putAll(profile.fuels().get("uranium").materials(profile.reactorTankKg()));
        var power = new ShipPowerState(generator, "rp1", "uranium", profile.batteryKwh(), true, 1, 1, 0, 0, 0, 0, 0);
        var cargo = equipment.drive().equals("mod_fission_thruster") ? Map.of("refined_uranium", 15.0) : Map.<String, Double>of();
        var ship = new ShipInstance("ship", "design", "owner", 100, 0, design.fuelCapacityKg(), cargo).withPowerState(power);
        var fleet = new Fleet("fleet", "Audit trader", "owner", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(ship),
                FleetLocation.at(FleetLocation.Site.docked("source")));
        var route = new TradeRoute("route", "Distance audit", "owner", "source", "target", "steel", 10000, 0,
                100000, List.of("ship"), 0, true).withRoaming(true);
        return state.withShipDesigns(List.of(design)).withFleets(List.of(fleet)).withTradeRoutes(List.of(route));
    }

    private OrbitalStation station(String id, String body) {
        return new OrbitalStation(id, id, "sol", body, "owner", "PUBLIC_STATE", 10, List.of(), Map.of(),
                0, 0, 0, 0, 100, 100, "steel", 0, true);
    }

    private CommercialHub port(String id, boolean source) {
        var orders = new HashMap<>(Map.of("steel", new MarketOrder("steel", source ? 5000 : 0, source ? 0 : 10000, source ? 1 : 20, 0),
                "food_matrix", new MarketOrder("food_matrix", source ? 5000 : 0, source ? 0 : 10000, source ? 1 : 20, 0)));
        for (String material : List.of("rp1_kerosene", "liquid_oxygen", "hydrogen_gas", "refined_uranium", "methane_ice"))
            orders.put(material, new MarketOrder(material, 0, 0, .001, 0));
        return new CommercialHub(id, id, 0, 1_000_000, source ? 10000 : 0, 10, orders);
    }

    private String report(List<Measurement> rows) {
        var text = new StringBuilder("# Local trade distance audit\n\n## Scope\n\n");
        text.append(rows.size()).append(" one-way loaded station probes use the repository's Sol bodies, catalog blueprints and paid mixed steel and food shipments. The baseline is 5,000 kg of each good; a half-load probe carries 2,500 kg of each. ")
                .append("Source stations orbit Earth; targets orbit the Moon, Mars or Jupiter. Body positions retain deterministic representative orbital phases, not ephemerides or gravity-assisted trajectories. ")
                .append("Ships start with full legal working tanks, batteries and required fission drive feed. No port sells fuel and no free supplies are injected. Destination buyer cash is seeded. ")
                .append("Funded voyages advance actual daily power and movement ticks for up to 12,000 days. Completed voyages retain their mixed cargo for 60 days without electrical purchases, then sell it. ")
                .append("Solar MPD blueprints use two catalog arrays and chemical solar uses one. All arrays occupy slots and add captured dry mass. Main contingency targets remain protected.\n\n")
                .append("## Results\n\n| Equipment | Cargo kg | Target body | Distance km | Destination solar factor | Funded | Planned days | Actual tick days | Completed | Sold kg | Planned main kg | Actual main kg | Planned electrical kg | Actual electrical kg | Battery error kWh | Arrival dwell ready |\n")
                .append("|---|---:|---|---:|---:|---|---:|---:|---|---:|---:|---:|---:|---:|---:|---|\n");
        for (var row : rows) text.append(String.format(Locale.ROOT,
                "| %s | %.0f | %s | %.0f | %.4f | %s | %s | %d | %s | %.0f | %.4f | %.4f | %.6f | %.6f | %.6f | %s |\n",
                row.equipment(), row.cargoKg(), row.body(), row.distanceKm(), row.flux(), row.funded(),
                row.plannedDays() > 0 ? String.format(Locale.ROOT, "%.0f", row.plannedDays()) : "Unavailable", row.actualDays(),
                row.completed(), row.sold(), row.plannedMain(), row.actualMain(), row.plannedElectric(), row.actualElectric(),
                row.batteryError(), row.dwellReady()));
        text.append("\n## Departure explanations\n\n");
        for (var row : rows) text.append("- ").append(row.equipment()).append(" to ").append(row.body()).append(": ")
                .append(row.reason()).append('\n');
        return text.append("\n## Limits and reproduction\n\nRun `mvn test` with JDK 27. The generated report is `engine/target/local-trade-distance-report.md`. ")
                .append("Unfunded rows show loaded analytic durations only; actual days and consumption are zero because no journey is executed. Unfinished funded voyages report partial tick consumption. ")
                .append("Forecast agreement is asserted only for completed voyages. Solar uses the conservative endpoint transfer envelope and destination eclipses; exact illumination along the path, evolving mass, radiation, orbital mechanics, combat, passengers and the full economy are excluded. ")
                .append("Opening supplies are fixture seeds rather than factory output. Fuel stock shortages remain real throughout each probe. Catalog values and reserve coefficients are provisional.\n").toString();
    }
}
