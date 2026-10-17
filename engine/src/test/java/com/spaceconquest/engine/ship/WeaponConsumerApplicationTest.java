package com.spaceconquest.engine.ship;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.combat.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WeaponConsumerApplicationTest {
    private final ShipPointDefenseTest fixture = new ShipPointDefenseTest();
    private final ObjectMapper mapper = new ObjectMapper();

    private ShipModule application(String id, Map<String, Double> changes) throws Exception {
        ObjectNode json = mapper.valueToTree(CombatModuleCatalog.application(id));
        var stats = (ObjectNode) json.get("combatModule").get("operationalStats");
        changes.forEach(stats::put);
        return mapper.treeToValue(json, TechnicalApplication.class).combatModule();
    }

    private TacticalCombatProcessor.CombatEngagementResult auxiliary(ShipModule weapon) {
        var attacker = fixture.design("attacker", 10000, SensorModuleCatalog.BASIC);
        var defender = fixture.design("defender", 10000, SensorModuleCatalog.BASIC);
        String app = CombatModuleCatalog.application(weapon.id()).id();
        var wing = new CarrierWing("wing", "attacker", CarrierWing.TYPE_INTERCEPTOR_FIGHTER, 2, 2, 100,
                app, CarrierWing.TARGET_CAPITAL_SHIPS, weapon).withStores(funded(weapon));
        var battery = new PlanetaryDefenseBattery("battery", "earth", app, 1, true, weapon).withStores(funded(weapon));
        return new TacticalCombatProcessor(new Random(42)).resolveFleetEngagementWithCarrierWings(
                fixture.fleet("attacker", List.of(fixture.ship("attacker", attacker, 0))),
                fixture.fleet("defender", List.of(fixture.ship("defender", defender, 0))),
                List.of(attacker, defender), List.of(wing), List.of(), battery, "ALL");
    }
    private AuxiliaryWeaponStores funded(ShipModule weapon) {
        return new AuxiliaryWeaponStores(weapon.ammunitionId().isBlank() ? Map.of()
                : Map.of(weapon.ammunitionId(), 100 * weapon.operationalStats().get("ammunitionMassKg")), 10000);
    }

    @Test void auxiliaryProjectileTotalsAndActualDelayedDeliveryUseConfiguredLaunchCounts() throws Exception {
        var custom = application(CombatModuleCatalog.MISSILE, Map.of("weaponDamage", 40.0, "weaponSalvoKwh", 6.0,
                "launchesPerRound", 3.0, "shieldBypassFraction", .5, "accuracy", 1.0));
        var salvo = WeaponSalvo.of(custom);
        assertEquals(new WeaponSalvo(3, 120, 18), salvo);
        var result = auxiliary(custom); var first = result.roundReports().getFirst();
        assertEquals(0, first.attackerDamageDealt()); assertEquals(0, first.defenderDamageDealt());
        assertEquals(240, first.detailedActions().stream().filter(a -> a.phase().equals("STRIKE_WING")).findFirst().orElseThrow().damageAmount());
        assertEquals(120, first.detailedActions().stream().filter(a -> a.phase().equals("SURFACE_BATTERY")).findFirst().orElseThrow().damageAmount());
        assertEquals(9, first.detailedActions().stream().filter(a -> a.phase().equals("PROJECTILE_LAUNCH")).count());
        assertEquals(240, result.roundReports().get(1).attackerDamageDealt());
        assertEquals(120, result.roundReports().get(1).defenderDamageDealt());
        assertTrue(result.roundReports().get(1).detailedActions().stream().filter(a -> a.phase().equals("WEAPON_HIT"))
                .allMatch(a -> a.description().contains("shield bypass 20.000")));
        assertEquals(1, CombatModuleCatalog.module(CombatModuleCatalog.MISSILE).operationalStats().get("launchesPerRound"));
        assertTrue(CombatModuleCatalog.description(custom).contains("6.0 kWh per launch"));
    }

    @Test void auxiliaryEnergyPulsesAccuracyAndIonEffectsComeFromTheirCapturedApplications() throws Exception {
        var miss = application(CombatModuleCatalog.PARTICLE_BEAM, Map.of("weaponDamage", 40.0,
                "shotsPerRound", 4.0, "accuracy", 0.0));
        assertEquals(40, WeaponSalvo.of(miss).damage(), "Pulse count divides the salvo instead of multiplying it");
        var missed = auxiliary(miss).roundReports().getFirst();
        assertEquals(0, missed.attackerDamageDealt()); assertEquals(0, missed.defenderDamageDealt());
        assertEquals(12, missed.detailedActions().stream().filter(a -> a.phase().equals("WEAPON_MISS")).count());
        var ion = application(CombatModuleCatalog.ION, Map.of("weaponDamage", 40.0, "electronicDamageMultiplier", 2.0));
        var overloaded = auxiliary(ion).roundReports().getFirst();
        assertEquals(80, overloaded.attackerDamageDealt()); assertEquals(40, overloaded.defenderDamageDealt());
        assertTrue(overloaded.detailedActions().stream().filter(a -> a.phase().equals("WEAPON_HIT"))
                .allMatch(a -> a.description().contains("hull damage 0.000")));
        assertTrue(overloaded.detailedActions().stream().anyMatch(a -> a.description().contains("electronics damage 80.000")));
    }

    @Test void fundedFleetEstimateRespondsToApplicationDamageLaunchCountEnergyAndFrozenHardware() throws Exception {
        var custom = application(CombatModuleCatalog.MISSILE, Map.of("weaponDamage", 40.0, "weaponSalvoKwh", 6.0,
                "launchesPerRound", 3.0, "ammunitionMassKg", 2.0));
        var base = fixture.design("launcher", 10000, custom.id());
        ObjectNode json = mapper.valueToTree(base);
        ((ObjectNode) json.get("combatModules")).set(custom.id(), mapper.valueToTree(custom));
        var design = mapper.treeToValue(json, ShipDesign.class);
        var ship = new ShipInstance("launcher", design.id(), design.ownerEntityId(), 1000, 0, 0,
                Map.of(custom.ammunitionId(), 6.0)).withPowerState(fixture.ship("launcher", design, 0).powerState());
        var fleet = fixture.fleet("launcher", List.of(ship));
        var state = GameState.builder().shipDesigns(List.of(design)).fleets(List.of(fleet)).build();
        assertEquals(Math.sqrt(120 * 1000), FleetThreatAssessment.strength(state, List.of(fleet)), 1e-9);
        assertEquals(18, ShipCombatPower.round(ship, design, 1.0 / 60, true, false).paidWeaponKwh());
        assertEquals(6, ship.storedCargoKg().get(custom.ammunitionId()));
        assertEquals(CombatModuleCatalog.module(custom.id()), CombatModuleCatalog.module(base, custom.id()));
        assertNotEquals(WeaponSalvo.of(custom), WeaponSalvo.of(CombatModuleCatalog.module(base, custom.id())));
    }
}
