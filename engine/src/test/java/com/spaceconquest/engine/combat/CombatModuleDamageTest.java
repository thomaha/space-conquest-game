package com.spaceconquest.engine.combat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.macrostructure.*;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CombatModuleDamageTest {
    @TempDir Path directory;
    private ShipDesign design(String id, String... modules) {
        return new ShipDesign(id, id, "owner", ShipRole.COMBAT_SHIP, "steel", List.of(modules), "steel", 0,
                1000, 0, 1000, 100, 1, 0, 1000, true, false);
    }
    private ShipInstance ship(ShipDesign design, double shield) {
        return new ShipInstance(design.id() + "-ship", design.id(), "owner", 10000, shield, 500, Map.of("cannon_round", 1000.0));
    }
    private Fleet fleet(ShipInstance ship) {
        return new Fleet(ship.id(), ship.id(), "owner", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(ship));
    }
    private TacticalCombatProcessor.CombatEngagementResult combat(ShipDesign a, ShipInstance attacker,
                                                                 ShipDesign d, ShipInstance defender, String target) {
        return new TacticalCombatProcessor(new Random(42)).resolveFleetEngagementWithCarrierWings(
                fleet(attacker), fleet(defender).withStance(TacticalCombatProcessor.STANCE_EVASIVE_RETREAT),
                List.of(a, d), List.of(), List.of(), null, target);
    }

    @Test void duplicateMountConditionsIndependentlyReduceAssessmentAndActualFire() {
        var design = design("armed", CombatModuleCatalog.GUN, CombatModuleCatalog.GUN,
                CombatModuleCatalog.SHIELD, CombatModuleCatalog.SHIELD);
        var full = ship(design, 400);
        var damaged = full.withModuleDamage(new ShipModuleDamage(Map.of(0, 100.0, 1, 50.0, 2, 100.0, 3, 50.0)));
        assertEquals(40, ShipCombatStats.of(design, damaged).firepower());
        assertEquals(100, ShipCombatStats.of(design, damaged).shields());
        var state = GameState.builder().shipDesigns(List.of(design)).build();
        assertTrue(FleetThreatAssessment.strength(state, List.of(fleet(damaged)))
                < FleetThreatAssessment.strength(state, List.of(fleet(full))));
        var target = design("target");
        var before = combat(design, full, target, ship(target, 0), "ALL");
        var after = combat(design, damaged, target, ship(target, 0), "ALL");
        assertEquals(before.roundReports().getFirst().attackerDamageDealt() / 4,
                after.roundReports().getFirst().attackerDamageDealt(), 1e-9);
        assertThrows(UnsupportedOperationException.class, () -> damaged.moduleDamage().damageByMount().put(0, 0.0));
        assertEquals(1, full.moduleDamage().condition(0));
    }

    @Test void targetedHullHitsDisableOnlyTheSelectedHardwareAndSurviveTheEncounter() {
        var attacker = design("attacker", CombatModuleCatalog.GUN);
        var target = design("target", CombatModuleCatalog.GUN, CombatModuleCatalog.GUN,
                CombatModuleCatalog.SHIELD, CombatModuleCatalog.SHIELD);
        for (String subsystem : List.of("WEAPONS", "SHIELDS")) {
            var result = combat(attacker, ship(attacker, 0), target, ship(target, 0), subsystem);
            var survivor = result.survivingDefenderFleet().ships().getFirst();
            assertTrue(survivor.currentHullHealth() > 0);
            assertTrue(survivor.currentHullHealth() < 10000);
            int selected = subsystem.equals("WEAPONS") ? 0 : 2;
            int other = subsystem.equals("WEAPONS") ? 2 : 0;
            assertEquals(0, survivor.moduleDamage().condition(selected));
            assertEquals(0, survivor.moduleDamage().condition(selected + 1));
            assertEquals(1, survivor.moduleDamage().condition(other));
            assertEquals(1, survivor.moduleDamage().condition(other + 1));
            assertEquals(subsystem.equals("WEAPONS") ? 0 : 160, ShipCombatStats.of(target, survivor).firepower());
        }
    }

    @Test void shieldsBlockHardwareDamageAndGeneralHullHitsSpreadCollateral() {
        var attacker = design("attacker", CombatModuleCatalog.GUN);
        var target = design("shielded", CombatModuleCatalog.SHIELD, CombatModuleCatalog.SHIELD,
                CombatModuleCatalog.SHIELD, CombatModuleCatalog.SHIELD, CombatModuleCatalog.SHIELD);
        var result = combat(attacker, ship(attacker, 0), target, ship(target, 1000), "WEAPONS");
        var protectedShip = result.survivingDefenderFleet().ships().getFirst();
        assertEquals(10000, protectedShip.currentHullHealth());
        assertEquals(ShipModuleDamage.empty(), protectedShip.moduleDamage());
        var mixed = design("mixed", CombatModuleCatalog.GUN, CombatModuleCatalog.SHIELD);
        var damage = ShipModuleDamage.empty().hit(mixed, 80, null);
        assertEquals(.9, damage.condition(0)); assertEquals(.9, damage.condition(1));
        var concentrated = damage.hit(mixed, 1000, "weaponDamage");
        assertEquals(0, concentrated.condition(0)); assertEquals(.9, concentrated.condition(1));
        assertEquals(concentrated, concentrated.hit(mixed, 1000, "weaponDamage"));
    }

    @Test void saveReloadPreservesMountDamageAndOlderSavesDefaultToHealthyHardware() throws Exception {
        var design = design("armed", CombatModuleCatalog.GUN, CombatModuleCatalog.SHIELD);
        var damaged = ship(design, 100).withModuleDamage(new ShipModuleDamage(Map.of(0, 75.0, 1, 25.0)));
        var state = GameState.builder().shipDesigns(List.of(design)).fleets(List.of(fleet(damaged))).build();
        var manager = new SaveGameManager(directory); var file = directory.resolve("modules.scsave").toFile();
        manager.save(file, state, 1, "2027-01-01T08:00:00");
        var restored = manager.load(file).toGameState(0, "RUNNING").fleets().getFirst().ships().getFirst();
        assertEquals(damaged, restored); assertEquals(ShipCombatStats.of(design, damaged), ShipCombatStats.of(design, restored));
        var mapper = new ObjectMapper(); var json = (ObjectNode) mapper.readTree(file);
        json.put("version", com.spaceconquest.engine.SaveGame.CURRENT_VERSION);
        ((ObjectNode) json.get("fleets").get(0).get("ships").get(0)).remove("moduleDamage");
        mapper.writeValue(file, json);
        var legacy = manager.load(file).toGameState(0, "RUNNING").fleets().getFirst().ships().getFirst();
        assertEquals(ShipModuleDamage.empty(), legacy.moduleDamage()); assertEquals(80, ShipCombatStats.of(design, legacy).firepower());
    }

    @Test void powerTravelTransferAndLegacyHangarCannotRestoreDamage() {
        var design = design("armed", CombatModuleCatalog.GUN, CombatModuleCatalog.SHIELD);
        var damaged = new ShipInstance("ship", design.id(), "owner", 50, 0, 500, Map.of())
                .withModuleDamage(new ShipModuleDamage(Map.of(0, 100.0, 1, 50.0)));
        var fleet = fleet(damaged); var state = GameState.builder().shipDesigns(List.of(design)).fleets(List.of(fleet)).build();
        var moving = LocalTravel.depart(fleet, FleetLocation.Site.orbit("earth"), new LocalTravel.Plan(1, Map.of("ship", 10.0), Map.of()));
        var arrived = new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state.withFleets(List.of(moving))),
                List.of(), List.of()).getFirst();
        assertEquals(490, arrived.ships().getFirst().currentFuelKg());
        assertEquals(damaged.moduleDamage(), arrived.ships().getFirst().moduleDamage());
        var receiver = new ShipInstance("receiver", design.id(), "owner", 100, 0, 500, Map.of());
        var receiving = new Fleet("receiving", "Receiving", "owner", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(receiver))
                .withLocation(arrived.location());
        var transferred = FleetOrganization.transfer(state.withFleets(List.of(arrived, receiving)), "owner",
                arrived.id(), receiving.id(), List.of(damaged.id()));
        var regrouped = transferred.fleets().stream().filter(item -> item.id().equals(receiving.id())).findFirst().orElseThrow();
        assertEquals(arrived.ships().getFirst(), regrouped.ships().stream().filter(item -> item.id().equals(damaged.id())).findFirst().orElseThrow());
        var hangar = new StationModule("hangar", "Hangar", StationModule.TYPE_MILITARY_HANGAR, 1, 1, 0, 0, Map.of(), "", 0, true, 0, 0);
        var station = new OrbitalStation("port", "Port", "sol", "earth", "owner", "PUBLIC_STATE", 10,
                List.of(hangar), Map.of(), 0, 0, 0, 0, 100, 100, "steel", 0, true);
        var docked = regrouped.withLocation(FleetLocation.at(FleetLocation.Site.docked("port")));
        var repaired = new FleetProcessor().processFleetMovements(List.of(docked), List.of(station), List.of()).getFirst().ships()
                .stream().filter(item -> item.id().equals(damaged.id())).findFirst().orElseThrow();
        assertEquals(damaged.currentHullHealth(), repaired.currentHullHealth());
        assertEquals(damaged.moduleDamage(), repaired.moduleDamage()); assertEquals(0, ShipCombatStats.of(design, repaired).firepower());
    }
}
