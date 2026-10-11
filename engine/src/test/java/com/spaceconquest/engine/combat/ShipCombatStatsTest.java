package com.spaceconquest.engine.combat;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ShipCombatStatsTest {
    private ShipDesign design(String id, double mass, String... modules) {
        return new ShipDesign(id, id, "owner", ShipRole.COMBAT_SHIP, "steel", List.of(modules), "steel", 2,
                mass, 0, 1000, 100, 1, 0, 1000, true, false);
    }
    private ShipInstance ship(ShipDesign design, double hull, double shields) {
        return new ShipInstance(design.id() + "-ship", design.id(), "owner", hull, shields, 100, Map.of());
    }
    private Fleet fleet(ShipDesign design, ShipInstance ship) {
        return new Fleet(design.id(), design.id(), "owner", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(ship));
    }
    @Test void installedWeaponsSupplyFirepowerAndNeitherMassNorRoleInventsWeapons() {
        var unarmed = design("unarmed", 1e9); var armed = design("armed", 1000, CombatModuleCatalog.GUN);
        var a = ship(unarmed, 100, 500); var b = ship(armed, 100, 0);
        assertEquals(0, ShipCombatStats.of(unarmed, a).firepower()); assertEquals(0, ShipCombatStats.of(unarmed, a).shields());
        assertEquals(80, ShipCombatStats.of(armed, b).firepower()); assertEquals(0, ShipCombatStats.of(null, b).firepower());
        var state = GameState.builder().shipDesigns(List.of(unarmed, armed)).build();
        assertEquals(0, FleetThreatAssessment.strength(state, List.of(fleet(unarmed, a))));
        assertTrue(FleetThreatAssessment.strength(state, List.of(fleet(armed, b))) > 0);
        var result = new TacticalCombatProcessor(new Random(42)).resolveFleetEngagement(fleet(unarmed, a), fleet(armed, b), List.of(unarmed, armed));
        assertTrue(result.roundReports().stream().allMatch(round -> round.attackerDamageDealt() == 0));
        assertTrue(result.roundReports().getFirst().defenderDamageDealt() > 0);
    }
    @Test void hullDamageAndElectricalFailureReduceBothAssessmentAndActualFiring() {
        var design = design("armed", 1000, CombatModuleCatalog.GUN, CombatModuleCatalog.SHIELD); var full = ship(design, 100, 200);
        var damaged = ship(design, 25, 200);
        assertEquals(ShipCombatStats.of(design, full).firepower() / 4, ShipCombatStats.of(design, damaged).firepower());
        var state = GameState.builder().shipDesigns(List.of(design)).build();
        assertTrue(FleetThreatAssessment.strength(state, List.of(fleet(design, damaged))) < FleetThreatAssessment.strength(state, List.of(fleet(design, full))));
        var failed = full.withPowerState(new ShipPowerState(Map.of(), "rp1", "uranium", 0, false, 1, 1, 1, 0, 0, 1, 0));
        assertEquals(0, ShipCombatStats.of(design, failed).firepower()); assertEquals(0, ShipCombatStats.of(design, failed).shields());
        var result = new TacticalCombatProcessor(new Random(42)).resolveFleetEngagement(fleet(design, failed), fleet(design, full), List.of(design));
        assertEquals(0, result.roundReports().getFirst().attackerDamageDealt());
    }
    @Test void shieldHardwareBoundsCurrentDefenseAndDepletionLowersStrength() {
        var design = design("shielded", 1000, CombatModuleCatalog.GUN, CombatModuleCatalog.SHIELD);
        var full = ship(design, 100, 9999); var empty = ship(design, 100, 0);
        assertEquals(200, ShipCombatStats.of(design, full).shields()); assertEquals(0, ShipCombatStats.of(design, empty).shields());
        var state = GameState.builder().shipDesigns(List.of(design)).build();
        assertTrue(FleetThreatAssessment.strength(state, List.of(fleet(design, full))) > FleetThreatAssessment.strength(state, List.of(fleet(design, empty))));
        assertEquals(1 / .9, ShipCombatStats.of(design, full).endurance() / 300, 1e-9);
    }
    @Test void moduleCountAndStanceAreSharedWithoutRandomAssessmentOrBlueprintMutation() {
        var design = design("armed", 1000, CombatModuleCatalog.GUN, CombatModuleCatalog.GUN); var ship = ship(design, 100, 0);
        assertEquals(160, ShipCombatStats.of(design, ship).firepower());
        var state = GameState.builder().shipDesigns(List.of(design)).build(); var fleet = fleet(design, ship);
        double strength = FleetThreatAssessment.strength(state, List.of(fleet));
        assertEquals(strength * Math.sqrt(1.25), FleetThreatAssessment.strength(state, List.of(fleet.withStance(TacticalCombatProcessor.STANCE_AGGRESSIVE_BRAWL))), 1e-9);
        assertEquals(List.of(design), state.shipDesigns()); assertEquals(strength, FleetThreatAssessment.strength(state, List.of(fleet)));
    }
}
