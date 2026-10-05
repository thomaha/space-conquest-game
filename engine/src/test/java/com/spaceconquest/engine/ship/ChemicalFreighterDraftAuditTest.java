package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.GameState;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Proposed equipment is deliberately local to this study and cannot be registered as live catalog hardware. */
class ChemicalFreighterDraftAuditTest {
    private record Draft(ShipDesignValidator.ValidationResult physics, List<ShipModule> modules,
                         ShipDesign design, GameState state, Fleet fleet) {}

    private Draft draft(String drive, double capacity, double cargoCapacity, double armor) throws Exception {
        var modules = List.of(PropulsionCatalog.module(drive), ShipComponentCatalog.module(ShipComponentCatalog.SOLAR_ARRAY_ID),
                ShipComponentCatalog.module(ShipComponentCatalog.BATTERY_ID),
                new ShipModule("draft_freighter_tank", "Draft long-range main tank", "LARGE",
                        (int) (capacity / 30000) * 4, capacity * .1, 10, 0, 0, 3, Map.of(), Map.of("fuelCapacityKg", capacity)),
                new ShipModule("draft_compact_hold", "Draft compact cargo hold", "SMALL",
                        cargoCapacity == 2500 ? 3 : 4, cargoCapacity == 2500 ? 750 : 1000,
                        cargoCapacity == 2500 ? 8 : 10, 0, 0, 2, Map.of(), Map.of("cargoCapacityKg", cargoCapacity)));
        var steel = DataModelLoader.loadMaterials().stream().filter(material -> material.id().equals("steel")).findFirst().orElseThrow();
        var physics = new ShipDesignValidator().validate(ShipRole.CARGO_TRANSPORT, ShipComponentCatalog.mediumFrame("steel"),
                modules, steel, steel, armor, 9.81, 1, 4);
        if (!physics.isValid()) return new Draft(physics, modules, null, null, null);
        var design = new ShipDesign("draft", "Draft chemical freighter", "owner", ShipRole.CARGO_TRANSPORT, "steel",
                modules.stream().map(ShipModule::id).toList(), "steel", armor, physics.totalDryMassKg(),
                physics.maxCargoMassKg(), physics.fuelCapacityKg(), physics.powerBalanceKw(), physics.structuralIntegrity(),
                physics.minLaunchThrustRequiredN(), physics.totalThrustN(), physics.isLaunchCapable(), false,
                new ShipManufacturingProfile(1, modules.stream().mapToInt(ShipModule::complexityLevel).max().orElseThrow()),
                ShipPowerProfile.capture(modules));
        var power = new ShipPowerState(Map.of(), "rp1", "uranium", 500, true, 1, 1, 0, 0, 0, 0, 0);
        var ship = new ShipInstance("ship", "draft", "owner", 100, 0, capacity,
                Map.of("steel", cargoCapacity / 2, "food_matrix", cargoCapacity / 2)).withPowerState(power);
        var fleet = new Fleet("fleet", "Draft", "owner", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(ship),
                FleetLocation.at(FleetLocation.Site.orbit("earth")));
        var state = GameState.builder().solarSystems(DataModelLoader.loadSolarSystems()).shipDesigns(List.of(design)).fleets(List.of(fleet)).build();
        return new Draft(physics, modules, design, state, fleet);
    }

    private PlanetaryTransferComparison.Orbit orbit(Draft draft, boolean highDeparture) {
        var sol = draft.state().solarSystems().getFirst();
        var earth = sol.planets().stream().filter(body -> body.id().equals("earth")).findFirst().orElseThrow();
        var mars = sol.planets().stream().filter(body -> body.id().equals("mars")).findFirst().orElseThrow();
        var seed = PlanetaryTransferComparison.orbit(sol, earth, mars, 0, highDeparture ? 100000 : 500, highDeparture ? 8000 : 500);
        return PlanetaryTransferComparison.orbit(sol, earth, mars, seed.requiredPhaseRadians(), highDeparture ? 100000 : 500,
                highDeparture ? 8000 : 500);
    }

    private PlanetaryTransferComparison.Budget budget(Draft draft, PlanetaryTransferComparison.Orbit orbit, double reserve) {
        var fleet = draft.fleet().withFuelPolicy(new FleetFuelPolicy(reserve, reserve, reserve));
        return PlanetaryTransferComparison.budget(draft.state().withFleets(List.of(fleet)), fleet, fleet.ships().getFirst(), orbit);
    }

    @Test void proposedEquipmentIsMeasuredWithRealFrameValidationAndReserveMargins() throws Exception {
        var output = new StringBuilder("# Chemical freighter draft study\n\n## Scope\n\n");
        output.append("Proposed tank and compact hold values are test-local prototypes, not live catalog components. ")
                .append("They use the existing medium frame, steel material, chemical drives, solar array and battery. ")
                .append("The tank has 10% tare mass, four slots per 30,000 kg capacity and a continuous 10 kW conditioning allowance. ")
                .append("All rows carry their full mixed steel and food hold with 0.5 cm armor. Manufacturing complexity is limited to 4. ")
                .append("High route means 100,000 km Earth departure and 8,000 km Mars arrival; low route is 500 km at each end. ")
                .append("Electrical checks conservatively apply full drive auxiliary demand during 390.05 days of hypothetical phase waiting, 259 rounded coast days and 60 arrival days, under Mars's 10-hour light/2-hour eclipse envelope. ")
                .append("This does not simulate operational orbits, tank boil-off, real purchases or paid construction.\n\n")
                .append("## Results\n\n| Drive | Main capacity kg | Cargo kg | Slots | Valid | Dry kg | Route | Maneuver delta-v m/s | Main required kg | 5% fits | 10% fits | 20% fits | Impulse fits | Auxiliary power fits | Build work | Build complexity |\n")
                .append("|---|---:|---:|---:|---|---:|---|---:|---:|---|---|---|---|---|---:|---:|\n");
        var rejected = new ArrayList<String>();
        for (String drive : List.of("mod_chemical_rocket", "mod_methalox_rocket", "mod_hydrolox_rocket"))
            for (double capacity : new double[]{90000, 120000, 150000}) for (double cargo : new double[]{2500, 5000}) {
                var draft = draft(drive, capacity, cargo, .5);
                int slots = draft.modules().stream().mapToInt(ShipModule::slotCost).sum();
                if (!draft.physics().isValid()) {
                    rejected.add(drive + " / " + capacity + " kg tank / " + cargo + " kg hold: " + String.join("; ", draft.physics().validationErrors()));
                    continue;
                }
                for (boolean high : List.of(false, true)) {
                    var orbit = orbit(draft, high);
                    var routine = budget(draft, orbit, .05);
                    var elevated = budget(draft, orbit, .10);
                    var wartime = budget(draft, orbit, .20);
                    var design = draft.design();
                    var ship = draft.fleet().ships().getFirst();
                    var environment = ShipSolarEnvironment.at(draft.state(), draft.fleet(), FleetLocation.Site.orbit("mars"));
                    var electrical = ShipLocalPowerForecast.check(design.powerProfile(), ship.powerState(), 710 * 24, environment,
                            design.powerProfile().essentialKw(ship, design), ShipPowerProcessor.cargoKw(design.powerProfile(), ship, design), design.powerProfile().driveKw());
                    var construction = ShipConstructionRequirements.estimate(design);
                    assertEquals(design.totalDryMassKg(), construction.materialsKg().values().stream().mapToDouble(Double::doubleValue).sum(), .000001);
                    assertFalse(design.isValidForLaunch(), "These fully loaded draft freighters require orbital assembly.");
                    output.append(String.format(Locale.ROOT,
                            "| %s | %.0f | %.0f | %d | true | %.0f | %s | %.1f | %.2f | %s | %s | %s | %s | %s | %.2f | %d |\n",
                            drive, capacity, cargo, slots, design.totalDryMassKg(), high ? "High" : "Low", orbit.totalDeltaVMps(), routine.requiredPropellantKg(),
                            routine.propellantFits(), elevated.propellantFits(), wartime.propellantFits(), routine.impulseFits(), electrical.ready(),
                            construction.workUnits(), design.manufacturingProfile().requiredComplexity()));
                }
            }
        output.append("\n## Rejected layouts\n\n");
        rejected.forEach(reason -> output.append("- ").append(reason).append('\n'));
        output.append("\n## Reproduction\n\nRun `mvn test` with JDK 27. Generated output: `engine/target/chemical-freighter-draft-report.md`. ")
                .append("The orbital comparison, main reserve, short-burn and auxiliary electricity checks are separate. A passing row is a prototype bound, not full funded voyage readiness.\n");
        var path = Path.of("target/chemical-freighter-draft-report.md");
        Files.createDirectories(path.toAbsolutePath().getParent());
        Files.writeString(path, output, StandardCharsets.UTF_8);
    }

    @Test void smallHydroloxDraftPreservesWartimeFuelButExtraArmorUsesItsMargin() throws Exception {
        var thin = draft("mod_hydrolox_rocket", 120000, 2500, .5);
        assertTrue(thin.physics().isValid());
        var thinBudget = budget(thin, orbit(thin, true), .20);
        assertTrue(thinBudget.propellantFits());
        assertTrue(thinBudget.impulseFits());
        var armored = draft("mod_hydrolox_rocket", 120000, 2500, 1);
        assertFalse(budget(armored, orbit(armored, true), .20).propellantFits());
        assertNull(ShipComponentCatalog.module("draft_freighter_tank"), "Study equipment must not become live hardware accidentally.");
    }

    @Test void selectedDraftElectricalForecastMatchesRepeatedEclipseAccounting() throws Exception {
        var draft = draft("mod_hydrolox_rocket", 120000, 2500, .5);
        var profile = draft.design().powerProfile();
        var ship = draft.fleet().ships().getFirst();
        var environment = ShipSolarEnvironment.at(draft.state(), draft.fleet(), FleetLocation.Site.orbit("mars"));
        double essential = profile.essentialKw(ship, draft.design()), cargo = ShipPowerProcessor.cargoKw(profile, ship, draft.design());
        var forecast = ShipLocalPowerForecast.check(profile, ship.powerState(), 710 * 24, environment, essential, cargo, profile.driveKw());
        assertTrue(forecast.ready());
        var actual = ship.powerState();
        for (int day = 0; day < 710; day++) {
            actual = actual.withChargeInputToday(0);
            for (int cycle = 0; cycle < 2; cycle++) {
                var light = ShipPowerProcessor.interval(profile, actual, 10, ShipPowerProcessor.solarKw(profile, actual, environment), essential, cargo, profile.driveKw());
                assertTrue(light.supplied());
                var dark = ShipPowerProcessor.interval(profile, light.state(), 2, 0, essential, cargo, profile.driveKw());
                assertTrue(dark.supplied());
                actual = dark.state();
            }
        }
        assertEquals(forecast.state().batteryChargeKwh(), actual.batteryChargeKwh(), 1e-6);
        assertEquals(Map.of(), actual.generatorMaterialsKg());
        assertEquals(120000, ship.currentFuelKg(), "Power previews cannot consume the draft main tank.");
    }
}
