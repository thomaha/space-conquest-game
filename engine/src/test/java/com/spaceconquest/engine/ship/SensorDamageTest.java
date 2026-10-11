package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.combat.TacticalCombatProcessor;
import com.spaceconquest.engine.galaxy.*;
import com.spaceconquest.engine.macrostructure.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SensorDamageTest {
    private ShipDesign design(String id, ShipSensorProfile sensors, String... modules) {
        return new ShipDesign(id, id, "owner", ShipRole.COMBAT_SHIP, "steel", List.of(modules), "steel", 0,
                1000, 1000, 1000, 100, 10, 0, 1000, false, false, ShipManufacturingProfile.baseline(), null, sensors);
    }
    private ShipInstance ship(String id, ShipDesign design, Map<Integer, Double> damage) {
        return new ShipInstance(id, design.id(), "owner", 1000, 0, 100, Map.of()).withModuleDamage(new ShipModuleDamage(damage));
    }
    private Fleet fleet(String id, String owner, ShipInstance ship) {
        return new Fleet(id, id, owner, "sol", "", 0, 0, 0, false, "PASSIVE", List.of(ship));
    }
    private Empire empire(String id) {
        return new Empire(id, id, "human", "Individualist", 0, 0, List.of(), List.of(), Map.of(), List.of(), List.of());
    }
    private GameState world(ShipDesign design, ShipInstance observer) {
        var own = fleet("own", "owner", observer);
        var enemy = fleet("enemy", "enemy", ship("enemy-ship", design, Map.of()));
        return GameState.builder().empires(List.of(empire("owner"), empire("enemy"))).shipDesigns(List.of(design))
                .solarSystems(List.of(new SolarSystem("sol", "Sol", "", 0, 0, 0, 1.989e30, 1392700, "yellow", List.of(), List.of())))
                .diplomaticRelations(List.of(new DiplomaticRelation("owner", "enemy", "TOTAL_WAR", 0)))
                .fleets(List.of(own, enemy)).build();
    }

    @Test void liveConditionScalesFrozenDevelopmentAndDuplicateArraysUseTheStrongestWorkingMount() {
        var design = design("developed", new ShipSensorProfile(400_000_000, 50),
                SensorModuleCatalog.ADVANCED, SensorModuleCatalog.BASIC, SensorModuleCatalog.ADVANCED);
        var damaged = ship("ship", design, Map.of(0, 50.0, 2, 100.0));
        assertEquals(new ShipSensorProfile(200_000_000, 25), SensorModuleCatalog.profile(design, damaged));
        var fallback = damaged.withModuleDamage(new ShipModuleDamage(Map.of(0, 100.0, 2, 100.0)));
        assertEquals(new ShipSensorProfile(4_000_000, 20), SensorModuleCatalog.profile(design, fallback));
        var fleet = fleet("own", "owner", fallback);
        assertEquals(20, new FleetProcessor().calculateFleetScannerRange(fleet, List.of(design)));
        assertEquals(new ShipSensorProfile(400_000_000, 50), design.sensorProfile());
        assertEquals(50, damaged.moduleDamage().damageByMount().get(0));
    }

    @Test void destroyedUnpoweredUnknownAndDisabledSensorsContributeNothingAndLegacyAliasesRemainSupported() {
        var design = design("legacy", null, "scanner_array_01"); var full = ship("ship", design, Map.of());
        assertEquals(new ShipSensorProfile(200_000_000, 25), SensorModuleCatalog.profile(design, full));
        assertEquals(new ShipSensorProfile(100_000_000, 12.5), SensorModuleCatalog.profile(design,
                full.withModuleDamage(new ShipModuleDamage(Map.of(0, 50.0)))));
        var cold = full.withPowerState(new ShipPowerState(Map.of(), "rp1", "uranium", 0, false, 1, 1, 0, 0, 0, 1, 0));
        var dead = new ShipInstance("dead", design.id(), "owner", 0, 0, 0, Map.of());
        for (var ship : List.of(cold, dead, full.withModuleDamage(new ShipModuleDamage(Map.of(0, 100.0))))) {
            assertEquals(new ShipSensorProfile(0, 0), SensorModuleCatalog.profile(design, ship));
            assertEquals(0, new FleetProcessor().calculateFleetScannerRange(fleet("own", "owner", ship), List.of(design)));
        }
        var unknown = design("unknown", new ShipSensorProfile(400_000_000, 50), "unknown");
        assertEquals(new ShipSensorProfile(0, 0), SensorModuleCatalog.profile(unknown, ship("unknown", unknown, Map.of())));
    }

    @Test void failedOwnSensorsLoseContactsWhileOtherShipsAndOnlineStationsMaintainTheCommonView() {
        var design = design("sensor", null, SensorModuleCatalog.BASIC, CombatModuleCatalog.GUN);
        var disabled = ship("own-ship", design, Map.of(0, 100.0)); var initial = world(design, disabled);
        var own = initial.fleets().getFirst(); var enemy = initial.fleets().getLast();
        assertFalse(SensorCoverage.detects(initial, "owner", enemy));
        assertEquals(FleetThreatAssessment.Action.NO_CONTACT, FleetThreatAssessment.assess(initial, own).action());
        var backup = fleet("backup", "owner", ship("backup", design, Map.of()));
        var supported = initial.withFleets(List.of(own, enemy, backup));
        assertTrue(SensorCoverage.detects(supported, "owner", enemy));
        assertEquals(FleetThreatAssessment.Action.HOLD, FleetThreatAssessment.assess(supported, own).action());
        var sharedView = new SensorProcessor().updateSensorCoverage(supported, List.of());
        assertEquals(List.of("enemy"), sharedView.stream().filter(view -> view.empireId().equals("owner")).findFirst().orElseThrow().detectedFleetIds());
        var module = SensorModuleCatalog.stationModule(SensorModuleCatalog.BASIC);
        var station = new OrbitalStation("observer", "Observer", "sol", "", "owner", "PUBLIC_STATE", 10,
                List.of(module), Map.of(), 100, 1, 0, 0, 100, 100, "steel", 0, true);
        supported = initial.toBuilder().orbitalStations(List.of(station)).build();
        assertTrue(SensorCoverage.hasSensor(supported, "owner", "sol"));
        var viewed = new SensorProcessor().updateSensorCoverage(initial, List.of());
        assertTrue(viewed.stream().filter(view -> view.empireId().equals("owner")).findFirst().orElseThrow().detectedFleetIds().isEmpty());
        assertFalse(SensorCoverage.hasSensor(initial, "owner", "sol"));
    }

    @Test void realHullPenetratingCombatHitsDamageSensorsWithoutChangingBlueprintCapability() {
        var attacker = design("attacker", null, CombatModuleCatalog.GUN);
        var target = design("target", null, SensorModuleCatalog.BASIC);
        var defender = ship("target", target, Map.of());
        var result = new TacticalCombatProcessor(new Random(42)).resolveFleetEngagement(
                fleet("attack", "owner", ship("attack", attacker, Map.of())), fleet("target", "enemy", defender), List.of(attacker, target));
        var survivor = result.survivingDefenderFleet().ships().getFirst();
        assertTrue(survivor.moduleDamage().damageByMount().get(0) > 0);
        assertTrue(SensorModuleCatalog.profile(target, survivor).rangeKm() < SensorModuleCatalog.profile(target).rangeKm());
        assertEquals(2_000_000, SensorModuleCatalog.profile(target).rangeKm());
    }

    @Test void onboardRepairPaysCopperAndRestoresOnlyTheRepairedSensorCapability() {
        var modules = List.of(ShipRepairModuleCatalog.WORKSHOP, SensorModuleCatalog.BASIC, ShipComponentCatalog.FISSION_REACTOR_ID);
        var power = ShipPowerProfile.capture(modules.stream().map(ShipComponentCatalog::module).toList());
        var design = new ShipDesign("repair", "Repair", "owner", ShipRole.COMBAT_SHIP, "steel", modules, "steel", 0,
                1000, 1000, 1000, 100, 10, 0, 1000, false, false, ShipManufacturingProfile.baseline(), power);
        var host = new ShipInstance("host", design.id(), "owner", 1000, 0, 100, Map.of("refined_copper", 4.0))
                .withPowerState(new ShipPowerState(Map.of("refined_uranium", 1.0), "rp1", "uranium", 0, false, 1, 1, 0, 0, 0, 0, 0))
                .withModuleDamage(new ShipModuleDamage(Map.of(1, 100.0)));
        var initial = world(design, host); var own = initial.fleets().getFirst();
        var repaired = ShipRepairProcessor.advanceDay(initial, List.of(own)).getFirst().ships().getFirst();
        assertEquals(90, repaired.moduleDamage().damageByMount().get(1));
        assertFalse(repaired.storedCargoKg().containsKey("refined_copper"));
        assertEquals(200_000, SensorModuleCatalog.profile(design, repaired).rangeKm(), 1e-6);
        assertEquals(1, SensorModuleCatalog.profile(design, repaired).scanStrength(), 1e-9);
        assertEquals(4, host.storedCargoKg().get("refined_copper"));
    }
}
