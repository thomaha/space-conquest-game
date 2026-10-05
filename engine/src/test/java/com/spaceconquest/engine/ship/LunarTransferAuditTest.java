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

/** Catalog comparison seeds are deliberately separate from paid voyage acceptance tests. */
class LunarTransferAuditTest {
    private GameState seed(String drive) throws Exception {
        boolean thermal = drive.equals("mod_fission_thruster");
        var ids = new ArrayList<>(thermal ? ShipComponentCatalog.workbenchModules(drive, false,
                ShipComponentCatalog.FISSION_REACTOR_ID, false) : ChemicalFreighterCatalog.modules(drive));
        if (thermal) ids.add(PropulsionCatalog.FUEL_TANK_MODULE_ID);
        var modules = ids.stream().map(ShipComponentCatalog::module).toList();
        var steel = DataModelLoader.loadMaterials().stream().filter(item -> item.id().equals("steel")).findFirst().orElseThrow();
        var physics = new ShipDesignValidator().validate(ShipRole.CARGO_TRANSPORT, ShipComponentCatalog.mediumFrame("steel"),
                modules, steel, steel, .5, 9.81, 1, 10);
        assertTrue(physics.isValid(), physics.validationErrors().toString());
        var design = new ShipDesign("design", drive, "owner", ShipRole.CARGO_TRANSPORT, "steel", ids, "steel", .5,
                physics.totalDryMassKg(), physics.maxCargoMassKg(), physics.fuelCapacityKg(), physics.powerBalanceKw(),
                physics.structuralIntegrity(), physics.minLaunchThrustRequiredN(), physics.totalThrustN(), physics.isLaunchCapable(),
                false, new ShipManufacturingProfile(1, modules.stream().mapToInt(ShipModule::complexityLevel).max().orElseThrow()),
                ShipPowerProfile.capture(modules));
        var cargo = thermal ? Map.of("refined_uranium", 50.0, "steel", 1250.0, "food_matrix", 1250.0)
                : Map.of("steel", 1250.0, "food_matrix", 1250.0);
        var power = new ShipPowerState(thermal ? Map.of("refined_uranium", 100.0) : Map.of(),
                "rp1", "uranium", 500, !thermal, 1, 1, 0, 0, 0, 0, 0);
        var ship = new ShipInstance("ship", "design", "owner", 100, 0, design.fuelCapacityKg(), cargo).withPowerState(power);
        var fleet = new Fleet("fleet", drive, "owner", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(ship),
                FleetLocation.at(FleetLocation.Site.orbit("earth", 2000)));
        return GameState.builder().solarSystems(DataModelLoader.loadSolarSystems()).shipDesigns(List.of(design)).fleets(List.of(fleet)).build();
    }

    @Test void catalogLoadsMeasureBothDirectionsAltitudesAndProtectedReserves() throws Exception {
        var report = new StringBuilder("# Lunar transfer comparison\n\n")
                .append("Read-only full-tank catalog seeds carry 2,500 kg of mixed goods. Thermal reference carries an additional 50 kg of drive feed and 100 kg of electrical feed. ")
                .append("No purchases or voyage electricity are simulated. A passing maneuver screen does not authorize departure.\n\n")
                .append("| Drive | Direction | Earth altitude km | Moon altitude km | Reserve | Wait days | Coast days | Departure m/s | Capture m/s | Main kg | Propellant fits | Feed fits | Impulse fits |\n")
                .append("|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---|---|---|\n");
        int rows = 0, passes = 0;
        for (var drive : List.of("mod_chemical_rocket", "mod_methalox_rocket", "mod_hydrolox_rocket", "mod_fission_thruster")) {
            var seed = seed(drive); var system = seed.solarSystems().getFirst();
            var earth = system.planets().stream().filter(body -> body.id().equals("earth")).findFirst().orElseThrow();
            var moon = earth.moons().stream().filter(body -> body.id().equals("moon")).findFirst().orElseThrow();
            for (boolean outward : List.of(true, false)) for (double earthAltitude : new double[]{500, 2000, 100000})
                for (double moonAltitude : new double[]{100, 500, 8000}) for (double reserve : new double[]{.05, .10, .20}) {
                    var fleet = seed.fleets().getFirst().withFuelPolicy(new FleetFuelPolicy(reserve, reserve, reserve))
                            .withLocation(FleetLocation.at(FleetLocation.Site.orbit(outward ? "earth" : "moon", outward ? earthAltitude : moonAltitude)));
                    var state = seed.withFleets(List.of(fleet));
                    var orbit = LunarTransferComparison.compare(system, earth, moon, 0, earthAltitude, moonAltitude, outward).orbit();
                    var budget = PlanetaryTransferComparison.budget(state, fleet, fleet.ships().getFirst(), orbit);
                    rows++; if (budget.propellantFits() && budget.reactorFeedFits() && budget.impulseFits()) passes++;
                    report.append(String.format(Locale.ROOT, "| %s | %s | %.0f | %.0f | %.0f%% | %.4f | %.4f | %.1f | %.1f | %.1f | %s | %s | %s |%n",
                            drive, outward ? "Outward" : "Inward", earthAltitude, moonAltitude, reserve * 100, orbit.waitDays(), orbit.coastDays(),
                            orbit.departureDeltaVMps(), orbit.captureDeltaVMps(), budget.requiredPropellantKg(), budget.propellantFits(),
                            budget.reactorFeedFits(), budget.impulseFits()));
                }
        }
        assertEquals(216, rows); assertTrue(passes > 0 && passes < rows);
        report.append(String.format(Locale.ROOT, "\n%d of %d rows pass all maneuver screens. Paid voyage tests separately verify full electricity and approach readiness.\n", passes, rows));
        var path = Path.of("target/lunar-transfer-report.md"); Files.createDirectories(path.getParent());
        Files.writeString(path, report, StandardCharsets.UTF_8);
    }
}
