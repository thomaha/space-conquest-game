package com.spaceconquest.engine.combat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spaceconquest.engine.TechnicalApplication;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WeaponApplicationFlightTest {
    @Test void flightTimingAndGuidanceFollowTheLaunchedApplicationDefinition() throws Exception {
        var mapper = new ObjectMapper();
        ObjectNode application = mapper.valueToTree(CombatModuleCatalog.application(CombatModuleCatalog.MISSILE));
        ObjectNode stats = (ObjectNode) application.get("combatModule").get("operationalStats");
        stats.put("projectileSpeedMetersPerSecond", 20_000); stats.put("requiresLiveGuidance", 0);
        var custom = mapper.treeToValue(application, TechnicalApplication.class).combatModule();
        var original = CombatModuleCatalog.module(CombatModuleCatalog.MISSILE);
        var flight = new GuidedMissileFlight(); var logs = new ArrayList<String>();
        var actions = new ArrayList<TacticalCombatProcessor.CombatActionReport>();
        flight.launch(List.of(paid(custom), paid(original)), 1_200_000, 1, "target", logs, actions);
        assertEquals(List.of(0.0, 600_000.0), flight.threats(Double.POSITIVE_INFINITY, true));
        assertEquals(custom.operationalStats().get("weaponDamage"), flight.advance(false, "target", logs, actions));
        assertTrue(flight.threats(Double.POSITIVE_INFINITY, true).isEmpty());
        assertTrue(logs.stream().anyMatch(log -> log.contains("live guidance contact lost")));
        assertTrue(actions.stream().anyMatch(action -> action.phase().equals("GUIDANCE_LOST")
                && action.description().contains("launch costs remain spent")));
    }
    private ShipCombatPower.Result paid(ShipModule module) {
        var stats = module.operationalStats();
        var launch = new ShipMissileWeapons.Launch(0, stats.get("weaponDamage"), stats.get("weaponSalvoKwh"),
                stats.get("projectileSpeedMetersPerSecond"), stats.get("requiresLiveGuidance") > 0,
                module.ammunitionId(), stats.getOrDefault("ammunitionMassKg", 0.0), module.id(), module);
        var ship = new ShipInstance("launcher", "design", "owner", 100, 0, 0, Map.of());
        return new ShipCombatPower.Result(ship, launch.damage(), launch.energyKwh(), launch.energyKwh(), 0,
                List.of(launch), 0, 0, List.of());
    }

    @Test void flightKeepsBypassAndElectronicVulnerabilityAndInterceptionUsesTheAssignedTarget() {
        var flight = new GuidedMissileFlight(); var logs = new ArrayList<String>();
        var actions = new ArrayList<TacticalCombatProcessor.CombatActionReport>();
        var slug = CombatModuleCatalog.module(CombatModuleCatalog.MASS_DRIVER);
        var missile = CombatModuleCatalog.module(CombatModuleCatalog.MISSILE);
        flight.launch(List.of(paid(slug), paid(missile)), 0, 1, "target", logs, actions);
        var threats = flight.incomingThreats(Double.POSITIVE_INFINITY, true);
        assertFalse(threats.getFirst().hasElectronics()); assertTrue(threats.getLast().hasElectronics());
        flight.intercept(List.of(new ShipPointDefense.Shot(CombatModuleCatalog.ION_POINT_DEFENSE, 40000, 2, "", 0,
                threats.getLast().id(), true)), "target", logs, actions);
        var impacts = flight.advanceHits(true, "target", logs, actions);
        assertEquals(1, impacts.size()); assertEquals(slug, impacts.getFirst().weapon());
        assertEquals(.35, impacts.getFirst().stat("shieldBypassFraction", 0));
        assertTrue(impacts.getFirst().accuracy(0) > impacts.getFirst().accuracy(10));
    }
    @Test void pendingSnapshotsFollowFiniteFlightInterceptionArrivalAndLostGuidance() {
        var flight = new GuidedMissileFlight(); var logs = new ArrayList<String>();
        var actions = new ArrayList<TacticalCombatProcessor.CombatActionReport>();
        flight.launch(List.of(paid(CombatModuleCatalog.module(CombatModuleCatalog.MISSILE)),
                paid(CombatModuleCatalog.module(CombatModuleCatalog.MASS_DRIVER))), 0, 1, "target", logs, actions);
        var pending = flight.pending(true); assertEquals(2, pending.size());
        assertThrows(UnsupportedOperationException.class, pending::clear);
        assertEquals(1, flight.pending(false).size(), "Unguided slugs keep their forecasts without contact");
        var threats = flight.incomingThreats(Double.POSITIVE_INFINITY, true);
        flight.intercept(List.of(new ShipPointDefense.Shot(CombatModuleCatalog.LASER_POINT_DEFENSE, Double.MAX_VALUE, 2,
                "", 0, threats.getFirst().id(), false)), "target", logs, actions);
        assertEquals(1, flight.pending(true).size()); assertEquals(2, pending.size(), "Old snapshot stays immutable");
        assertEquals(1, flight.advanceHits(true, "target", logs, actions).size()); assertTrue(flight.pending(true).isEmpty());
        flight.launch(List.of(paid(CombatModuleCatalog.module(CombatModuleCatalog.MISSILE))), 1200000, 1, "target", logs, actions);
        assertTrue(flight.advanceHits(false, "target", logs, actions).isEmpty()); assertTrue(flight.pending(true).isEmpty());
    }
}
