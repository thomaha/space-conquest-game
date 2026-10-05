package com.spaceconquest.control;

import com.spaceconquest.control.command.MoveFleetCommand;
import com.spaceconquest.control.command.MoveFleetLocalCommand;
import com.spaceconquest.control.command.RecoverFleetTravelCommand;
import com.spaceconquest.control.command.TransferShipSuppliesCommand;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.Moon;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.SolarRadiation;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FleetLocation;
import com.spaceconquest.engine.ship.FleetProcessor;
import com.spaceconquest.engine.ship.InterstellarTravel;
import com.spaceconquest.engine.ship.LocalTravel;
import com.spaceconquest.engine.ship.ShipBlueprintFactory;
import com.spaceconquest.engine.ship.ShipArrivalReserve;
import com.spaceconquest.engine.ship.ShipComponentCatalog;
import com.spaceconquest.engine.ship.ShipDesignSpecification;
import com.spaceconquest.engine.ship.ShipInstance;
import com.spaceconquest.engine.ship.ShipPowerForecast;
import com.spaceconquest.engine.ship.ShipPowerProcessor;
import com.spaceconquest.engine.ship.ShipPowerState;
import com.spaceconquest.engine.ship.ShipRole;
import com.spaceconquest.engine.ship.ShipSolarEnvironment;
import com.spaceconquest.engine.ship.ShipSupplyTransfer;
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

/** Deterministic catalog balance audit. The optional report path exports its Markdown result. */
class ShipEnduranceAuditTest {
    private static final int MAX_SIMULATED_DAYS = 180;
    private static final double SYNTHETIC_CROSSING_LY = 5e9 / InterstellarTravel.METERS_PER_LIGHT_YEAR;
    private record Scenario(String name, String drive, String source, String feed, double reservesKg,
                            double starMass, double distanceAu, int passengers, boolean stasis,
                            boolean food, boolean atmosphere, double crossingLy, double cargoKg) {
        private Scenario(String name, String drive, String source, String feed, double reservesKg,
                         double starMass, double distanceAu, int passengers, boolean stasis,
                         boolean food, boolean atmosphere, double crossingLy) {
            this(name, drive, source, feed, reservesKg, starMass, distanceAu, passengers, stasis, food, atmosphere, crossingLy, 1000);
        }
    }
    private record Fixture(GameState state, FleetLocation.Site destination) {}
    private record Measurement(Scenario scenario, ShipPowerForecast.Readiness preview, double plannedDays,
                               double solarKw, double essentialKw, double cargoKw, double driveKw,
                               boolean commandReady, int days, boolean arrived, boolean interrupted,
                               boolean reserveReady, double fuelUsed, double electricalUsed,
                               double battery, double batteryDelta, double cargoLost, Double batteryError,
                               Double electricalError, String outcome) {}

    @Test void catalogEnduranceMatchesTicksAndProducesARepeatableReport() throws Exception {
        List<Measurement> measurements = new ArrayList<>();
        for (Scenario scenario : scenarios()) measurements.add(measure(scenario));
        String recovery = recoveryAudit();
        String report = report(measurements, recovery);
        Path path = Path.of(System.getProperty("endurance.report", "target/ship-endurance-report.md"));
        Path parent = path.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Files.writeString(path, report, StandardCharsets.UTF_8);
        for (Measurement result : measurements) {
            assertEquals(result.preview().ready(), result.commandReady(), result.scenario().name());
            if (result.days() == 0) continue;
            assertEquals(result.preview().ready(), result.arrived() && !result.interrupted() && result.reserveReady(),
                    result.scenario().name() + ": departure readiness differs from completed journey plus reserve");
            if (result.batteryError() != null) assertEquals(0, result.batteryError(), 1e-6, result.scenario().name());
            if (result.electricalError() != null) assertEquals(0, result.electricalError(), 1e-6, result.scenario().name());
        }
    }

    private List<Scenario> scenarios() {
        List<Scenario> rows = new ArrayList<>();
        rows.add(local("RP-1/LOX", "mod_chemical_rocket", "rp1", 15000));
        rows.add(local("Methane/LOX", "mod_methalox_rocket", "methalox", 15000));
        rows.add(local("Hydrogen/LOX", "mod_hydrolox_rocket", "hydrolox", 15000));
        rows.add(local("Chemical under-supplied", "mod_chemical_rocket", "rp1", 20));
        for (double distance : new double[]{.5, 1, 2, 5}) rows.add(solar("Solar at " + distance + " AU", 1, distance, 0, false, true, false));
        rows.add(solar("Solar at 2 AU, empty hold", 1, 2, 0, false, false, false));
        var full = solar("Solar at 2 AU, full food hold", 1, 2, 0, false, true, false);
        rows.add(new Scenario(full.name(), full.drive(), full.source(), full.feed(), full.reservesKg(), full.starMass(),
                full.distanceAu(), full.passengers(), full.stasis(), full.food(), full.atmosphere(), full.crossingLy(), 30000));
        rows.add(solar("Solar around 0.5 solar masses", .5, 1, 0, false, true, false));
        rows.add(solar("Solar around 2 solar masses at 5 AU", 2, 5, 0, false, true, false));
        rows.add(solar("Solar during atmospheric landing", 1, 1, 0, false, true, true));
        rows.add(solar("Solar with 100 conscious passengers", 1, 1, 100, false, true, false));
        rows.add(solar("Two solar arrays with 100 stasis passengers at 0.5 AU", 1, .5, 100, true, true, false));
        rows.add(solar("Two solar arrays with 100 stasis passengers at 1 AU", 1, 1, 100, true, true, false));
        rows.add(new Scenario("Chemical with 100 conscious passengers", "mod_chemical_rocket",
                ShipComponentCatalog.CHEMICAL_GENERATOR_ID, "rp1", 15000, 1, 1, 100, false, true, false, 0));
        rows.add(new Scenario("Chemical with 100 stasis passengers", "mod_chemical_rocket",
                ShipComponentCatalog.CHEMICAL_GENERATOR_ID, "rp1", 15000, 1, 1, 100, true, true, false, 0));
        rows.add(new Scenario("Chemical with 10 conscious passengers", "mod_chemical_rocket",
                ShipComponentCatalog.CHEMICAL_GENERATOR_ID, "rp1", 15000, 1, 1, 10, false, true, false, 0));
        rows.add(new Scenario("Chemical with 10 stasis passengers", "mod_chemical_rocket",
                ShipComponentCatalog.CHEMICAL_GENERATOR_ID, "rp1", 15000, 1, 1, 10, true, true, false, 0));
        rows.add(crossing("Fission synthetic crossing", "mod_fission_thruster", ShipComponentCatalog.FISSION_REACTOR_ID, "uranium", 100, SYNTHETIC_CROSSING_LY));
        rows.add(crossing("Chemical synthetic crossing", "mod_chemical_rocket", ShipComponentCatalog.CHEMICAL_GENERATOR_ID, "rp1", 15000, SYNTHETIC_CROSSING_LY));
        rows.add(crossing("Fission at 1 light-year", "mod_fission_thruster", ShipComponentCatalog.FISSION_REACTOR_ID, "uranium", 100, 1));
        rows.add(crossing("Chemical at 1 light-year", "mod_chemical_rocket", ShipComponentCatalog.CHEMICAL_GENERATOR_ID, "rp1", 15000, 1));
        return List.copyOf(rows);
    }

    private Scenario local(String name, String drive, String feed, double kg) {
        return new Scenario(name, drive, ShipComponentCatalog.CHEMICAL_GENERATOR_ID, feed, kg, 1, 1, 0, false, true, false, 0);
    }
    private Scenario solar(String name, double mass, double distance, int passengers, boolean stasis, boolean food, boolean atmosphere) {
        return new Scenario(name, "mod_chemical_rocket", ShipComponentCatalog.SOLAR_ARRAY_ID, "rp1", 0,
                mass, distance, passengers, stasis, food, atmosphere, 0);
    }
    private Scenario crossing(String name, String drive, String source, String feed, double kg, double ly) {
        return new Scenario(name, drive, source, feed, kg, 1, 1, 0, false, true, false, ly);
    }

    private Fixture fixture(Scenario scenario) {
        var moon = new Moon("moon", "Moon", "", 1, 1.62, 384400, 3474, "none", false, 0, List.of(), List.of());
        var planet = new Planet("earth", "Reference planet", "", 1, 9.81, scenario.distanceAu() * SolarRadiation.AU_KM,
                0, 12000, "terrestrial", "air", false, 0, List.of(), List.of(moon), List.of());
        var system = new SolarSystem("a", "A", "", 0, 0, 0, scenario.starMass() * SolarRadiation.SOLAR_MASS_KG,
                1392000, "yellow", List.of(planet), List.of());
        var empire = new Empire("owner", "Owner", "human", "Individualist", 100000, 0, List.of("a"), List.of(), Map.of(),
                List.of("rocketry", "methalox_propulsion", "hydrolox_propulsion", "nuclear_fission", "electricity", "solar_power", "cryogenic_stasis"), List.of());
        var yard = new IndustrialFacility("yard", "earth", "surface_shipyard", "owner", IndustrialFacility.PUBLIC_STATE,
                3, 0, "engineer", false, 0);
        GameState state = GameState.builder().empires(List.of(empire)).industrialFacilities(List.of(yard))
                .solarSystems(List.of(system, new SolarSystem("b", "B", "", Math.max(1e-12, scenario.crossingLy()),
                        0, 0, SolarRadiation.SOLAR_MASS_KG, 1392000, "yellow", List.of(), List.of()))).build();
        var modules = new ArrayList<>(ShipComponentCatalog.workbenchModules(scenario.drive(), scenario.stasis(), scenario.source()));
        if (scenario.stasis() && scenario.source().equals(ShipComponentCatalog.SOLAR_ARRAY_ID))
            modules.add(ShipComponentCatalog.SOLAR_ARRAY_ID);
        var evaluation = ShipBlueprintFactory.evaluate(state, new ShipDesignSpecification("design", scenario.name(), "owner",
                ShipRole.CARGO_TRANSPORT, "steel", modules, "steel", 0));
        assertTrue(evaluation.valid(), scenario.name() + ": " + evaluation.errors());
        var design = evaluation.design();
        Map<String, Double> materials = scenario.reservesKg() == 0 ? Map.of()
                : design.powerProfile().fuels().get(scenario.feed()).materials(scenario.reservesKg());
        var power = new ShipPowerState(materials, scenario.feed().equals("uranium") ? "rp1" : scenario.feed(), "uranium",
                design.powerProfile().batteryKwh(), true, 1, 1, 0, 0, 0, 0, 0);
        var cargo = new HashMap<String, Double>();
        if (scenario.food()) cargo.put("food_matrix", scenario.cargoKg());
        if (scenario.drive().equals("mod_fission_thruster")) cargo.put("refined_uranium", design.fuelCapacityKg() * .001);
        var ship = new ShipInstance("ship", "design", "owner", 100, 0, design.fuelCapacityKg(), cargo, scenario.passengers(),
                "human", scenario.stasis() ? ShipInstance.MODE_CRYOGENIC_STASIS : ShipInstance.MODE_CONSCIOUS, power);
        var origin = scenario.crossingLy() > 0 ? FleetLocation.Site.deepSpace() : FleetLocation.Site.orbit("earth");
        var destination = scenario.atmosphere() ? FleetLocation.Site.surface("earth") : FleetLocation.Site.orbit("moon");
        var fleet = new Fleet("fleet", "Fleet", "owner", "a", "", 0, 0, 0, false, "PASSIVE", List.of(ship), FleetLocation.at(origin));
        return new Fixture(state.withShipDesigns(List.of(design)).withFleets(List.of(fleet)), destination);
    }

    private Measurement measure(Scenario scenario) {
        Fixture fixture = fixture(scenario);
        GameState initial = fixture.state(), current;
        Fleet original = fleet(initial);
        var profile = initial.shipDesigns().getFirst().powerProfile();
        ShipPowerForecast.Readiness preview;
        double plannedDays;
        boolean commandReady;
        var environment = scenario.crossingLy() > 0 ? ShipSolarEnvironment.DARK
                : ShipSolarEnvironment.journey(initial, original, fixture.destination());
        if (scenario.crossingLy() > 0) {
            var command = new MoveFleetCommand("fleet", "b");
            var departure = command.preview(initial);
            assertNotNull(departure, scenario.name());
            preview = departure.electrical().getFirst();
            plannedDays = departure.totalDays();
            commandReady = command.validate(initial);
            // Long crossings are plan-only. Rejected shorter cases are diagnostic forced runs.
            current = plannedDays > MAX_SIMULATED_DAYS ? initial : forceCrossing(initial, departure.crossing());
        } else {
            var local = LocalTravel.plan(initial, original, fixture.destination());
            assertNotNull(local, scenario.name());
            preview = ShipPowerForecast.departure(initial, original, fixture.destination(), local, null).getFirst();
            plannedDays = Math.ceil(local.days());
            var command = new MoveFleetLocalCommand("fleet", fixture.destination().kind(), fixture.destination().entityId());
            commandReady = command.validate(initial);
            current = commandReady ? command.apply(initial) : initial.withFleets(List.of(LocalTravel.depart(original, fixture.destination(), local)));
        }
        boolean interrupted = false;
        int days = 0;
        if (plannedDays <= MAX_SIMULATED_DAYS) for (; days < plannedDays; days++) {
            current = tick(current);
            interrupted |= Fleet.MODE_POWER_INTERRUPTED.equals(fleet(current).interstellarMode())
                    || fleet(current).location().localFlight() != null && fleet(current).location().localFlight().interrupted();
        }
        boolean arrived = days > 0 && (scenario.crossingLy() > 0 ? fleet(current).currentSystemId().equals("b")
                : fleet(current).location().isAt(fixture.destination()));
        var end = ship(current);
        var reserve = ShipArrivalReserve.check(profile, end.powerState(),
                profile.essentialKw(end, initial.shipDesigns().getFirst()),
                ShipPowerProcessor.cargoKw(profile, end, initial.shipDesigns().getFirst()),
                ShipArrivalReserve.environment(current, fleet(current), fixture.destination(), scenario.crossingLy() > 0));
        double electricalUsed = ship(initial).generatorFuelMassKg() - end.generatorFuelMassKg();
        Double batteryError = arrived && !interrupted ? end.powerState().batteryChargeKwh() - preview.remainingBatteryKwh() : null;
        Double electricalError = arrived && !interrupted ? electricalUsed - preview.generatorFuelUsedKg() : null;
        String outcome = days == 0 ? "Plan only" : arrived ? reserve.ready() && !interrupted ? "Arrived + reserve" : "Arrived; reserve short"
                : interrupted ? "Power interruption" : "Unfinished";
        return new Measurement(scenario, preview, plannedDays, ShipPowerProcessor.solarKw(profile, ship(initial).powerState(), environment),
                profile.essentialKw(ship(initial), initial.shipDesigns().getFirst()), ShipPowerProcessor.cargoKw(profile, ship(initial), initial.shipDesigns().getFirst()),
                profile.driveKw(), commandReady, days, arrived, interrupted, reserve.ready(),
                ship(initial).currentFuelKg() - end.currentFuelKg(), electricalUsed, end.powerState().batteryChargeKwh(),
                end.powerState().batteryChargeKwh() - ship(initial).powerState().batteryChargeKwh(),
                ship(initial).storedCargoKg().getOrDefault("food_matrix", 0.0) - end.storedCargoKg().getOrDefault("food_matrix", 0.0),
                batteryError, electricalError, outcome);
    }

    private GameState forceCrossing(GameState state, InterstellarTravel.Plan plan) {
        var original = fleet(state);
        var fueled = InterstellarTravel.commitReactorFuel(original, plan);
        return state.withFleets(List.of(new Fleet(original.id(), original.name(), original.ownerEntityId(), "a", "b", 0, 0, 0,
                false, "PASSIVE", fueled.ships(), original.location(), plan.mode(), plan.days(), plan.distanceMeters(),
                plan.accelerationMps2(), 0, plan.peakSpeedMps(), plan.fuelBudgetKg())));
    }
    private GameState tick(GameState state) {
        return state.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state), List.of(), List.of()));
    }
    private Fleet fleet(GameState state) { return state.fleets().getFirst(); }
    private ShipInstance ship(GameState state) { return fleet(state).ships().getFirst(); }

    private String recoveryAudit() {
        var fixture = fixture(local("Resupply recovery", "mod_chemical_rocket", "rp1", 0));
        var initial = fixture.state();
        var receiver = ship(initial).withPowerState(ShipPowerState.empty());
        var fleet = fleet(initial).withShips(List.of(receiver));
        var plan = LocalTravel.plan(initial.withFleets(List.of(fleet)), fleet, fixture.destination());
        assertNotNull(plan);
        var stopped = initial.withFleets(List.of(LocalTravel.depart(fleet, fixture.destination(), plan)));
        for (int day = 0; day < 3; day++) stopped = tick(stopped);
        assertEquals(0, fleet(stopped).location().progress());
        assertEquals(902.5, ship(stopped).storedCargoKg().get("food_matrix"), 1e-6);
        var donor = new ShipInstance("donor", "design", "owner", 100, 0, 0,
                initial.shipDesigns().getFirst().powerProfile().fuels().get("rp1").materials(4000));
        var donorFleet = new Fleet("donorFleet", "Donor", "owner", "a", "", 0, 0, 0, false, "PASSIVE", List.of(donor),
                FleetLocation.at(fleet(stopped).location().current()));
        var rendezvous = stopped.withFleets(List.of(fleet(stopped), donorFleet));
        var transfer = new TransferShipSuppliesCommand("donor", "ship", ShipSupplyTransfer.Source.CARGO,
                ShipSupplyTransfer.Destination.ELECTRICAL_FUEL, "rp1", 4000);
        assertTrue(transfer.validate(rendezvous));
        var supplied = transfer.apply(rendezvous);
        var recover = new RecoverFleetTravelCommand("fleet");
        assertTrue(recover.validate(supplied));
        var preview = recover.pausedPreview(supplied);
        assertNotNull(preview.physical());
        var arrived = recover.apply(supplied);
        for (int day = 0; day < preview.remainingDays(); day++) arrived = tick(arrived);
        assertTrue(fleet(arrived).location().isAt(fixture.destination()));
        assertEquals(902.5, ship(arrived).storedCargoKg().get("food_matrix"), 1e-6);
        assertEquals(ship(stopped).currentFuelKg() - preview.physical().propellantKg().get("ship"), ship(arrived).currentFuelKg(), 1e-6);
        assertEquals(0, arrived.fleets().get(1).ships().getFirst().storedCargoKg().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-6);
        return String.format(Locale.ROOT, "Three unpowered days spoil 97.5 kg from 1,000 kg of food. A stationary donor transfers 4,000 kg of RP-1/LOX from cargo, then a new physical local recovery completes in %.0f scheduled days. Arrival retains 902.5 kg of food, %.2f kg of generator supplies and %.2f kg of main propellant. Main propellant is consumed during the actual recovery burns.\n",
                preview.remainingDays(), ship(arrived).generatorFuelMassKg(), ship(arrived).currentFuelKg());
    }

    private String report(List<Measurement> results, String recovery) {
        var text = new StringBuilder("# Ship endurance balance report\n\n");
        text.append("## Reproduce the report\n\nFrom the repository root with JDK 27 selected:\n\n```powershell\n")
                .append("mvn --% -q -pl control -am -Dtest=ShipEnduranceAuditTest -Dsurefire.failIfNoSpecifiedTests=false test\n```\n\n")
                .append("The default output is `control/target/ship-endurance-report.md`. Add `-Dendurance.report=../ShipEnduranceReport.md` to refresh this checked-in report. Rows and arithmetic are deterministic; there is no wall-clock timestamp.\n\n")
                .append("## Scope and assumptions\n\n")
                .append("All blueprints pass the normal catalog builder with unoptimized steel hulls, a 500 kWh fully charged battery, a 15,000 kg full propulsion tank and the workbench cargo vault. Fueled sources use a full dedicated compartment except the explicitly under-supplied row. Food loads start at 1,000 kg except the 30,000 kg full-hold row; empty holds use standby draw. Stasis rows use one occupied 100-seat pod and a tier 3 manufacturing fixture. Solar stasis requires two panels to pass the builder's 1 AU static power check. Passenger counts are fixed electrical load probes; nutritional consumption, casualties and ticket economics are excluded. No simulation runs on the UI thread.\n\n")
                .append("Local planet-to-moon transfers use deterministic geometry and loaded acceleration, coasting and braking. Stationary departures conserve fuel within the earliest funded arrival day while protecting the default 5% main-tank contingency target. Atmospheric landing retains its one-day abstract phase. Stellar strength uses the existing mass-to-luminosity relation. Crossings marked synthetic span 5 billion metres, far shorter than actual star separation. All cases exceeding 180 days remain bounded analytic plan-only probes; no arrival or daily endurance is asserted for them.\n\n")
                .append("Rejected short scenarios are deliberately forced through engine tick processing to diagnose depletion. Normal commands still reject them. Readiness includes a separate 48-hour essential and cargo reserve. Known local arrivals use destination sunlight with an initial eclipse; interstellar and unknown arrivals assume darkness. Forecast fuel use describes the journey alone; checking the reserve consumes no actual stock.\n\n")
                .append("## Journey outcomes\n\n| Scenario | Planned days | Sunlit solar kW | Essential / cargo / drive kW | Command ready | Actual outcome |\n| --- | ---: | ---: | --- | --- | --- |\n");
        for (Measurement r : results) text.append(String.format(Locale.ROOT, "| %s | %.2f | %.2f | %.1f / %.1f / %.1f | %s | %s |\n",
                r.scenario().name(), r.plannedDays(), r.solarKw(), r.essentialKw(), r.cargoKw(), r.driveKw(), r.commandReady(), r.outcome()));
        text.append("\n## Actual resource changes\n\nFuel columns separate main propellant from electrical generator mixtures or refined electrical reactor feed. Battery delta is final minus initial. Plan-only rows have no tick measurements.\n\n")
                .append("| Scenario | Main fuel used kg | Electrical fuel used kg | Arrival/end battery kWh | Battery delta kWh | Food lost kg |\n| --- | ---: | ---: | ---: | ---: | ---: |\n");
        for (Measurement r : results) text.append(r.days() == 0 ? "| " + r.scenario().name() + " | — | — | — | — | — |\n"
                : String.format(Locale.ROOT, "| %s | %.3f | %.6f | %.3f | %+.3f | %.3f |\n", r.scenario().name(), r.fuelUsed(),
                r.electricalUsed(), r.battery(), r.batteryDelta(), r.cargoLost()));
        text.append("\n## Preview agreement\n\nOnly completed, uninterrupted journeys compare final battery and journey fuel use. A negative preview is allowed to finish the journey if its arrival reserve is insufficient. Missing values mean that comparison does not apply. Each simulated row asserts readiness matches the actual journey and reserve outcome.\n\n")
                .append("| Scenario | Forecast journey kWh | Forecast reserve kWh | Battery error kWh | Electrical fuel error kg |\n| --- | ---: | ---: | ---: | ---: |\n");
        for (Measurement r : results) text.append(String.format(Locale.ROOT, "| %s | %.2f | %.2f | %s | %s |\n", r.scenario().name(),
                r.preview().journeyKwh(), r.preview().arrivalReserveKwh(), number(r.batteryError()), number(r.electricalError())));
        text.append("\n## Emergency resupply and recovery\n\n").append(recovery);
        text.append("\n## Tuning candidates\n\n")
                .append("- Local arrival reserves now use eclipse-first destination illumination and include cargo preservation. Interstellar arrivals retain darkness. Continue testing weak stars and long nights when tuning storage.\n")
                .append("- Cargo demand now uses 10% standby plus 90% times vulnerable-mass utilization. Food weight is 1, biomass 0.5 and cryogenic liquids 2; stable freight stays at standby. Keep the full rated load as the ceiling.\n")
                .append("- Stasis now uses 2 kW per occupied pod plus 0.78 kW per passenger: 9.8 kW for 10 seats and 80 kW for a full 100-seat pod. Empty pods remain off. Sparse one-seat pods still have overhead.\n")
                .append("- Chemical sources should remain useful locally. Their electrical stores and modest exhaust velocities do not establish viable 1-light-year endurance. Fission electricity does not improve the thermal drive's exhaust velocity.\n")
                .append("- Revisit food grace and loss rates alongside recovery delays. This example loses 9.75% over three fully unpowered days; restoring power prevents additional loss.\n\n")
                .append("These are provisional gameplay coefficients. The audit reports the implemented reserve and occupancy adjustments; executing it changes no game state outside its fixtures.\n");
        return text.toString();
    }
    private String number(Double value) { return value == null ? "—" : String.format(Locale.ROOT, "%.6f", value); }
}
