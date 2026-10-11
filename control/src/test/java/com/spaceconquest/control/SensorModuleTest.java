package com.spaceconquest.control;

import com.fasterxml.jackson.databind.*;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.galaxy.*;
import com.spaceconquest.engine.macrostructure.*;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SensorModuleTest {
    GameState world(List<String> technologies) {
        var owner = new Empire("owner", "Owner", "human", "Individualist", 1000000, 0,
                List.of("sol"), List.of(), Map.of(), technologies, List.of());
        var yard = new OrbitalStation("yard", "Yard", "sol", "earth", "owner", OrbitalStation.OWNERSHIP_PUBLIC_STATE,
                30, List.of(new StationModule("slipway", "Slipway", StationModule.TYPE_CAPITAL_SLIPWAY, 4, 10000,
                0, 0, Map.of(), "engineer", 0, true)), Map.of(), 100, 0, 0, 0, 100, 100, "steel", 0, true);
        var earth = new Planet("earth", "Earth", "", 5.97e24, 9.81, 149600000, 0, 12742, "rock", "", false, 0,
                List.of(), List.of(), List.of());
        var mars = new Planet("mars", "Mars", "", 6e23, 3.7, 227900000, 0, 6792, "rock", "", false, 0,
                List.of(), List.of(), List.of());
        var system = new SolarSystem("sol", "Sol", "", 0, 0, 0, 1.989e30, 1392700, "yellow", List.of(earth, mars), List.of());
        return GameState.builder().empires(List.of(owner)).solarSystems(List.of(system)).orbitalStations(List.of(yard)).build();
    }
    private List<String> research() { return List.of("rocketry", "electricity", "space_stations", "energy_fields", "scanners"); }
    private ShipDesignSpecification specification(String id, String sensor) {
        var modules = new ArrayList<>(ShipComponentCatalog.workbenchModules("mod_chemical_rocket", false));
        if (sensor != null) modules.add(sensor);
        return new ShipDesignSpecification(id, id, "owner", ShipRole.EXPLORER, "steel", modules, "steel", .5);
    }
    private ShipDesign blueprint(GameState state, String id, String sensor) {
        var result = ShipBlueprintFactory.evaluate(state, specification(id, sensor));
        assertTrue(result.valid(), result.errors().toString());
        return result.design();
    }
    private Fleet fleet(String id, String owner, ShipDesign design, String body) {
        return new Fleet(id, id, owner, "sol", "", 0, 0, 0, false, "PASSIVE",
                List.of(new ShipInstance(id + "-ship", design.id(), owner, 100, 0, 100, Map.of())),
                FleetLocation.at(FleetLocation.Site.orbit(body, 500)));
    }

    @Test void researchUnlocksInstalledHardwareAndRoleAloneHasNoSensors() {
        var primitive = world(List.of("rocketry"));
        assertFalse(ShipBlueprintFactory.evaluate(primitive, specification("basic", SensorModuleCatalog.BASIC)).valid());
        var electric = world(List.of("rocketry", "electricity"));
        var basic = blueprint(electric, "basic", SensorModuleCatalog.BASIC);
        assertEquals(2_000_000, basic.sensorProfile().rangeKm());
        assertFalse(ShipBlueprintFactory.evaluate(electric, specification("advanced", SensorModuleCatalog.ADVANCED)).valid());
        var advanced = blueprint(world(research()), "advanced", SensorModuleCatalog.ADVANCED);
        assertTrue(advanced.sensorProfile().rangeKm() > basic.sensorProfile().rangeKm());
        var none = blueprint(electric, "none", null);
        assertEquals(0, none.sensorProfile().rangeKm());
        assertEquals(0, new FleetProcessor().calculateFleetScannerRange(fleet("empty", "owner", none, "earth"), List.of(none)));
        assertTrue(basic.totalDryMassKg() > none.totalDryMassKg());
        assertTrue(basic.powerBalanceKw() < none.powerBalanceKw());
    }

    @Test void researchImprovesNewBuildsAndSavedHardwareRetainsItsQuotedCapability() throws Exception {
        var state = world(research());
        var old = blueprint(state, "old", SensorModuleCatalog.ADVANCED);
        var command = new SelectOptimizationPathCommand("owner", "scanners", "PATH_A");
        assertTrue(command.validate(state));
        var researched = command.apply(state.withShipDesigns(List.of(old)));
        var improved = blueprint(researched, "improved", SensorModuleCatalog.ADVANCED);
        assertTrue(improved.sensorProfile().rangeKm() > old.sensorProfile().rangeKm());
        assertEquals(old.sensorProfile(), researched.shipDesigns().getFirst().sensorProfile());
        var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        var loaded = mapper.readValue(mapper.writeValueAsString(SaveGame.fromGameState(researched, "now", 1, "now")), SaveGame.class);
        assertEquals(old.sensorProfile(), loaded.toGameState(0, "RUNNING").shipDesigns().getFirst().sensorProfile());
    }

    @Test void currentInstalledRangeGovernsContactAndExplorationDoesNotKeepTargetsVisible() {
        var state = world(research());
        var basic = blueprint(state, "basic", SensorModuleCatalog.BASIC);
        var advanced = blueprint(state, "advanced", SensorModuleCatalog.ADVANCED);
        var target = fleet("target", "enemy", basic, "mars");
        var basicObserver = fleet("observer", "owner", basic, "earth");
        var advancedObserver = fleet("observer", "owner", advanced, "earth");
        var ready = state.toBuilder().shipDesigns(List.of(basic, advanced)).fleets(List.of(basicObserver, target)).build();
        assertFalse(SensorCoverage.detects(ready, "owner", target));
        ready = ready.withFleets(List.of(advancedObserver, target));
        assertTrue(SensorCoverage.detects(ready, "owner", target));
        var processor = new SensorProcessor();
        var detected = processor.updateSensorCoverage(ready.empires(), ready.solarSystems(), ready.fleets(), ready.shipDesigns(),
                ready.orbitalStations(), List.of(), List.of(), List.of());
        assertEquals(List.of("target"), detected.getFirst().detectedFleetIds());
        var lost = processor.updateSensorCoverage(ready.empires(), ready.solarSystems(), List.of(target), ready.shipDesigns(),
                ready.orbitalStations(), List.of(), List.of(), detected);
        assertTrue(lost.getFirst().detectedFleetIds().isEmpty());
        assertTrue(lost.getFirst().isSystemExplored("sol"));
        assertFalse(new ScanSystemCommand("owner", "sol").validate(ready.withFleets(List.of(target))));
    }

    @Test void stationSensorConstructionUsesResearchedCatalogCostsAndSlots() {
        var electric = world(List.of("electricity", "space_stations"));
        var basic = new AddStationModuleCommand("yard", "Override", SensorModuleCatalog.BASIC, 0, 0, 0, 100000, "none", 0);
        assertTrue(basic.validate(electric));
        var built = basic.apply(electric);
        var module = built.constructionProjects().getFirst().plannedModule();
        assertEquals(SensorModuleCatalog.BASIC, module.type());
        assertEquals(200, module.dryMassKg());
        assertEquals(10, module.powerDrawKw());
        assertEquals(0, module.powerOutputKw());
        assertEquals(1, module.slotSize());
        assertFalse(built.constructionProjects().getFirst().requiredMaterialsKg().isEmpty());
        var advanced = new AddStationModuleCommand("yard", "Advanced", SensorModuleCatalog.ADVANCED, 1, 1, 0, 0, "none", 0);
        assertFalse(advanced.validate(electric));
        assertTrue(advanced.validate(world(research())));
    }

    @Test void sensorOutagesRemoveContactsAndOlderDesignsRequireActualModules() throws Exception {
        var state = world(research());
        var basic = blueprint(state, "basic", SensorModuleCatalog.BASIC);
        var observer = fleet("observer", "owner", basic, "earth");
        var target = fleet("target", "enemy", basic, "earth");
        state = state.withShipDesigns(List.of(basic)).withFleets(List.of(observer, target));
        assertTrue(SensorCoverage.detects(state, "owner", target));
        var outage = new ShipPowerState(Map.of(), "rp1", "uranium", 0, true, 1, 1, 0, 0, 0, 1, 0);
        observer = observer.withShips(List.of(observer.ships().getFirst().withPowerState(outage)));
        state = state.withFleets(List.of(observer, target));
        assertFalse(SensorCoverage.detects(state, "owner", target));
        assertFalse(new ScanSystemCommand("owner", "sol").validate(state));
        var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        var old = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(basic);
        old.remove("sensorProfile");
        assertEquals(basic.sensorProfile(), SensorModuleCatalog.profile(mapper.treeToValue(old, ShipDesign.class)));
        old.set("equippedModuleIds", mapper.valueToTree(List.of("mod_chemical_rocket")));
        assertEquals(0, SensorModuleCatalog.profile(mapper.treeToValue(old, ShipDesign.class)).rangeKm());
    }

    @Test void onlyOperationalStationsWithOnlineInstalledArraysProvideCoverage() {
        var state = world(research());
        var basic = blueprint(state, "basic", SensorModuleCatalog.BASIC);
        var target = fleet("target", "enemy", basic, "earth");
        state = state.withShipDesigns(List.of(basic)).withFleets(List.of(target));
        assertFalse(SensorCoverage.detects(state, "owner", target));
        for (boolean operational : List.of(true, false)) for (boolean online : List.of(true, false)) {
            var catalog = SensorModuleCatalog.stationModule(SensorModuleCatalog.BASIC);
            var module = new StationModule(catalog.id(), catalog.name(), catalog.type(), catalog.slotSize(),
                    catalog.dryMassKg(), catalog.powerDrawKw(), 0, Map.of(), "technician", 1, online);
            var station = new OrbitalStation("yard", "Yard", "sol", "earth", "owner", OrbitalStation.OWNERSHIP_PUBLIC_STATE,
                    30, List.of(module), Map.of(), 100, 10, 0, 0, 100, 100, "steel", 0, operational);
            var covered = state.toBuilder().orbitalStations(List.of(station)).build();
            assertEquals(operational && online, SensorCoverage.detects(covered, "owner", target));
            assertEquals(operational && online, new ScanSystemCommand("owner", "sol").validate(covered));
        }
    }
}
