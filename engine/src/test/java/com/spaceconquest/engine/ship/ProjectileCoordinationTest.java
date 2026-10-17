package com.spaceconquest.engine.ship;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.combat.*;
import com.spaceconquest.engine.galaxy.SensorCoverage;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectileCoordinationTest {
    private final ShipPointDefenseTest fixture = new ShipPointDefenseTest();
    private final ObjectMapper mapper = new ObjectMapper();
    private ShipModule application(String id, Map<String, Double> edits) throws Exception {
        ObjectNode json = mapper.valueToTree(CombatModuleCatalog.application(id));
        var stats = (ObjectNode) json.get("combatModule").get("operationalStats"); edits.forEach(stats::put);
        return mapper.treeToValue(json, TechnicalApplication.class).combatModule();
    }
    private ShipDesign design(String id, String owner, ShipModule weapon, String... extras) throws Exception {
        var modules = new ArrayList<String>(); if (weapon != null) modules.add(weapon.id()); modules.addAll(List.of(extras));
        ObjectNode json = mapper.valueToTree(fixture.design(id, 10000, modules.toArray(String[]::new)));
        json.put("ownerEntityId", owner); json.put("calculatedStructuralIntegrity", 1); json.put("totalThrustN", 0);
        if (weapon != null) ((ObjectNode) json.get("combatModules")).set(weapon.id(), mapper.valueToTree(weapon));
        return mapper.treeToValue(json, ShipDesign.class);
    }
    private ShipDesign gun(String id, double damage) throws Exception {
        return design(id, "enemy", application(CombatModuleCatalog.PARTICLE_BEAM, Map.of("weaponDamage", damage)));
    }
    private ShipInstance ship(ShipDesign design, double hull, int rounds) {
        var ammo = new HashMap<String, Double>();
        design.combatModules().values().stream().filter(module -> !module.ammunitionId().isBlank())
                .forEach(module -> ammo.put(module.ammunitionId(), rounds * module.operationalStats().get("ammunitionMassKg")));
        return new ShipInstance(design.id(), design.id(), design.ownerEntityId(), hull, 0, 0, ammo)
                .withPowerState(fixture.ship(design.id(), design, 0).powerState());
    }
    private WeaponHit beam() { return new WeaponHit(CombatModuleCatalog.module(CombatModuleCatalog.PARTICLE_BEAM), 60, 0); }
    private ProjectileTargetingContext context(double seconds) {
        var slug = new WeaponHit(CombatModuleCatalog.module(CombatModuleCatalog.MASS_DRIVER), 220, 0);
        return new ProjectileTargetingContext(List.of(new ProjectileTargetingContext.PendingImpact(slug, seconds, false)), List.of(), targets -> false);
    }
    @Test void nearCommittedFireRedirectsBeamsWhileLongDelaysAndDataWeightsReduceReservationConfidence() throws Exception {
        var fragile = gun("fragile", 200); var cruiser = gun("cruiser", 220); var designs = List.of(fragile, cruiser);
        var targets = List.of(ship(fragile, 100, 0), ship(cruiser, 1000, 0)); var doctrine = CombatTargetingDoctrine.defaults();
        assertEquals("fragile", targets.get(new CombatTargetSelector(designs, doctrine).choose(targets, beam(), "ALL").index()).id());
        var choice = new CombatTargetSelector(designs, doctrine, context(60)).choose(targets, beam(), "ALL");
        assertEquals("cruiser", targets.get(choice.index()).id());
        assertEquals("fragile", targets.get(new CombatTargetSelector(designs, doctrine, context(6000)).choose(targets, beam(), "ALL").index()).id());
        ObjectNode json = mapper.valueToTree(doctrine); json.put("committedFireWeight", 0);
        var retuned = mapper.treeToValue(json, CombatTargetingDoctrine.class);
        assertEquals("fragile", targets.get(new CombatTargetSelector(designs, retuned, context(60)).choose(targets, beam(), "ALL").index()).id());
        assertEquals(100, targets.getFirst().currentHullHealth()); assertEquals(ShipModuleDamage.empty(), targets.getFirst().moduleDamage());
        assertThrows(UnsupportedOperationException.class, () -> context(60).committed().clear());
    }
    @Test void projectilesArrivingInThisVolleyReserveDamageBeforeBeamsAndAreNotCountedAgainstTheirOwnImpact() throws Exception {
        var fragile = gun("fragile", 200); var cruiser = gun("cruiser", 220);
        var slug = new WeaponHit(CombatModuleCatalog.module(CombatModuleCatalog.MASS_DRIVER), 220, 0);
        var actions = new ArrayList<TacticalCombatProcessor.CombatActionReport>();
        var result = WeaponImpactResolver.apply(List.of(ship(fragile, 100, 0), ship(cruiser, 1000, 0)),
                List.of(beam(), beam(), slug), List.of(fragile, cruiser), "ALL", new Random(42), actions);
        assertEquals(List.of("cruiser", "cruiser", "fragile"), actions.stream().filter(action -> action.phase().equals("WEAPON_HIT"))
                .map(TacticalCombatProcessor.CombatActionReport::targetEntityId).toList());
        assertEquals(1, result.ships().size()); assertEquals(880, result.ships().getFirst().currentHullHealth());
    }
    @Test void paidMixedSalvosRequoteReservationsAndRetargetWhenBeamFireDisablesTheFirstTarget() throws Exception {
        var beamDesign = design("beam", "own", application(CombatModuleCatalog.PARTICLE_BEAM,
                Map.of("weaponDamage", 120.0, "shotsPerRound", 2.0)));
        var launcher = design("launcher", "own", CombatModuleCatalog.module(CombatModuleCatalog.MASS_DRIVER));
        var fragile = gun("fragile", 20); var cruiser = gun("cruiser", 22);
        var own = fixture.fleet("own", List.of(ship(beamDesign, 1000, 0), ship(launcher, 1000, 1)));
        var enemy = fixture.fleet("enemy", List.of(ship(fragile, 100, 0), ship(cruiser, 1000, 0)));
        int[] rounds = {0};
        var result = new TacticalCombatProcessor(new Random(42)).resolveFleetEngagement(own, enemy,
                List.of(beamDesign, launcher, fragile, cruiser),
                (a, b) -> new TacticalCombatProcessor.Withdrawal(a, b, ++rounds[0] >= 2), (a, b) -> true, (a, b) -> 0);
        assertEquals(2, result.roundReports().size());
        var firstHits = result.roundReports().getFirst().detailedActions().stream().filter(action -> action.phase().equals("WEAPON_HIT")
                && action.description().startsWith(CombatModuleCatalog.module(CombatModuleCatalog.PARTICLE_BEAM).name())
                && (action.targetEntityId().equals("fragile") || action.targetEntityId().equals("cruiser"))).toList();
        assertEquals(List.of("cruiser", "cruiser"), firstHits.stream().map(TacticalCombatProcessor.CombatActionReport::targetEntityId).toList());
        assertTrue(result.roundReports().get(1).detailedActions().stream().anyMatch(action -> action.phase().equals("WEAPON_HIT")
                && action.description().startsWith(CombatModuleCatalog.module(CombatModuleCatalog.MASS_DRIVER).name()) && action.targetEntityId().equals("fragile")));
        var survivingLauncher = result.survivingAttackerFleet().ships().stream().filter(item -> item.id().equals("launcher")).findFirst().orElseThrow();
        assertEquals(0, ShipWeaponAmmunition.rounds(survivingLauncher.storedCargoKg(), CombatModuleCatalog.module(CombatModuleCatalog.MASS_DRIVER)));
        assertTrue(survivingLauncher.generatorFuelMassKg() < own.ships().get(1).generatorFuelMassKg());
        var disabled = ship(fragile, 1000, 0).withModuleDamage(new ShipModuleDamage(Map.of(0, 100.0)));
        var actions = new ArrayList<TacticalCombatProcessor.CombatActionReport>();
        WeaponImpactResolver.apply(List.of(disabled, ship(cruiser, 1000, 0)), List.of(new WeaponHit(
                CombatModuleCatalog.module(CombatModuleCatalog.MASS_DRIVER), 220, 0)), List.of(fragile, cruiser), "ALL", new Random(42), actions);
        assertTrue(actions.stream().anyMatch(action -> action.phase().equals("WEAPON_HIT") && action.targetEntityId().equals("cruiser")));
    }
    private TacticalCombatProcessor.CombatEngagementResult guidanceBattle(boolean externalRelay, boolean guided) throws Exception {
        var ion = design("ion", "own", CombatModuleCatalog.module(CombatModuleCatalog.ION), SensorModuleCatalog.BASIC);
        var launcher = design("launcher", "enemy", application(CombatModuleCatalog.MISSILE,
                Map.of("weaponDamage", 1000.0, "projectileSpeedMetersPerSecond", 1000.0, "requiresLiveGuidance", guided ? 1.0 : 0.0)));
        var relay = design("relay", "enemy", null, SensorModuleCatalog.BASIC); var gun = gun("gun", 100);
        var external = design("external", "enemy", null, SensorModuleCatalog.BASIC);
        var own = fixture.fleet("own", List.of(ship(ion, 1000, 0)));
        var enemy = fixture.fleet("enemy", List.of(ship(launcher, 1000, 1), ship(relay, 1000, 0), ship(gun, 1000, 0)));
        var support = new Fleet("external", "External", "enemy", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(ship(external, 1000, 0)));
        var designs = List.of(ion, launcher, relay, gun, external); int[] rounds = {0};
        var owners = List.of("own", "enemy").stream().map(id -> new Empire(id, id, "human", "Individualist", 1000,
                0, List.of("sol"), List.of(), Map.of(), List.of(), List.of())).toList();
        return new TacticalCombatProcessor(new Random(42)).resolveFleetEngagement(own, enemy, designs,
                (a, b) -> new TacticalCombatProcessor.Withdrawal(a, b, ++rounds[0] >= 3),
                (observer, target) -> SensorCoverage.detects(GameState.builder().shipDesigns(designs).empires(owners)
                        .fleets(externalRelay ? List.of(observer, target, support) : List.of(observer, target)).build(), observer.ownerEntityId(), target),
                (a, b) -> 120000);
    }
    @Test void actualGuidedFlightMakesIonDisableTheCriticalRelayAndGuidanceLossSpendsTheLaunch() throws Exception {
        var result = guidanceBattle(false, true);
        var first = result.roundReports().getFirst().detailedActions();
        assertTrue(first.stream().anyMatch(action -> action.phase().equals("WEAPON_HIT") && action.targetEntityId().equals("relay")));
        assertTrue(first.stream().anyMatch(action -> action.phase().equals("TARGET_SELECTION") && action.targetEntityId().equals("relay")
                && !action.description().contains("guidance threat 0.000")));
        assertTrue(result.roundReports().stream().flatMap(round -> round.detailedActions().stream())
                .anyMatch(action -> action.phase().equals("GUIDANCE_LOST") && action.description().contains("launch costs remain spent")));
        assertFalse(result.roundReports().stream().flatMap(round -> round.detailedActions().stream())
                .anyMatch(action -> action.phase().equals("MISSILE_IMPACT")));
        assertTrue(result.survivingDefenderFleet().ships().stream().allMatch(item -> item.currentHullHealth() == 1000));
        var launcher = result.survivingDefenderFleet().ships().stream().filter(item -> item.id().equals("launcher")).findFirst().orElseThrow();
        assertEquals(0, ShipWeaponAmmunition.rounds(launcher.storedCargoKg(), CombatModuleCatalog.module(CombatModuleCatalog.MISSILE)));
    }
    @Test void externalSharedCoverageAndApplicationDefinedUnguidedFlightDoNotGrantCriticalRelayPriority() throws Exception {
        for (var result : List.of(guidanceBattle(true, true), guidanceBattle(false, false))) {
            assertTrue(result.roundReports().getFirst().detailedActions().stream()
                    .anyMatch(action -> action.phase().equals("WEAPON_HIT") && action.targetEntityId().equals("gun")));
            assertFalse(result.roundReports().stream().flatMap(round -> round.detailedActions().stream())
                    .anyMatch(action -> action.phase().equals("GUIDANCE_LOST")));
            assertTrue(result.roundReports().stream().flatMap(round -> round.detailedActions().stream())
                    .anyMatch(action -> action.phase().equals("MISSILE_IMPACT")));
        }
    }
    @Test void paidProjectileRetargetsAStillDangerousShipAfterIonDisablesTheOriginalThreatBeforeArrival() throws Exception {
        var ionModule = CombatModuleCatalog.module(CombatModuleCatalog.ION);
        ObjectNode json = mapper.valueToTree(design("ion", "own", ionModule));
        var profile = (ObjectNode) json.get("powerProfile"); profile.put("fissionKw", profile.get("hotelKw").asDouble());
        profile.put("dischargeKw", 1000);
        var ion = mapper.treeToValue(json, ShipDesign.class);
        var launcher = design("launcher", "own", application(CombatModuleCatalog.MASS_DRIVER,
                Map.of("projectileSpeedMetersPerSecond", 1000.0)));
        var high = gun("high", 2000); var low = gun("low", 100);
        var source = ship(ion, 10000000, 0);
        var p = source.powerState(); source = source.withPowerState(new ShipPowerState(p.generatorMaterialsKg(),
                p.chemicalMixture(), p.reactorFuel(), WeaponSalvo.of(ionModule).energyKwh(), false, 1, 1, 0, 0, 0, 0, 0));
        var own = fixture.fleet("own", List.of(source, ship(launcher, 10000000, 1)));
        var enemy = fixture.fleet("enemy", List.of(ship(high, 1000, 0), ship(low, 1000, 0))); int[] rounds = {0};
        var result = new TacticalCombatProcessor(new Random(42)).resolveFleetEngagement(own, enemy,
                List.of(ion, launcher, high, low),
                (a, b) -> new TacticalCombatProcessor.Withdrawal(a, b, ++rounds[0] >= 3), (a, b) -> true, (a, b) -> 120000);
        assertEquals(3, result.roundReports().size());
        assertTrue(result.roundReports().getFirst().detailedActions().stream().anyMatch(action -> action.phase().equals("WEAPON_HIT")
                && action.description().startsWith(ionModule.name()) && action.targetEntityId().equals("high")));
        assertFalse(result.roundReports().get(1).detailedActions().stream().anyMatch(action -> action.phase().equals("MISSILE_IMPACT")));
        assertTrue(result.roundReports().get(2).detailedActions().stream().anyMatch(action -> action.phase().equals("WEAPON_HIT")
                && action.description().startsWith(CombatModuleCatalog.module(CombatModuleCatalog.MASS_DRIVER).name())
                && action.targetEntityId().equals("low")));
        var disabled = result.survivingDefenderFleet().ships().stream().filter(item -> item.id().equals("high")).findFirst().orElseThrow();
        assertEquals(1000, disabled.currentHullHealth()); assertEquals(0, disabled.moduleDamage().condition(0));
        assertEquals(WeaponSalvo.of(ionModule).energyKwh(), source.powerState().batteryChargeKwh());
    }
    @Test void actualPaidInterceptionRemovesTheReservationBeforeTheNextBeamVolley() throws Exception {
        var beamDesign = design("beam", "own", application(CombatModuleCatalog.PARTICLE_BEAM,
                Map.of("weaponDamage", 120.0, "shotsPerRound", 2.0)));
        var launcher = design("launcher", "own", CombatModuleCatalog.module(CombatModuleCatalog.MASS_DRIVER));
        var fragile = design("fragile", "enemy", application(CombatModuleCatalog.PARTICLE_BEAM,
                Map.of("weaponDamage", 20.0)), CombatModuleCatalog.LASER_POINT_DEFENSE);
        var cruiser = gun("cruiser", 22);
        var own = fixture.fleet("own", List.of(ship(beamDesign, 1000, 0), ship(launcher, 1000, 1)));
        var enemy = fixture.fleet("enemy", List.of(ship(fragile, 100, 0), ship(cruiser, 1000, 0))); int[] rounds = {0};
        var result = new TacticalCombatProcessor(new Random(42)).resolveFleetEngagement(own, enemy,
                List.of(beamDesign, launcher, fragile, cruiser),
                (a, b) -> new TacticalCombatProcessor.Withdrawal(a, b, ++rounds[0] >= 2), (a, b) -> true, (a, b) -> 0);
        var second = result.roundReports().get(1).detailedActions();
        assertTrue(second.stream().anyMatch(action -> action.phase().equals("POINT_DEFENSE")));
        assertTrue(second.stream().anyMatch(action -> action.phase().equals("WEAPON_HIT") && action.targetEntityId().equals("fragile")
                && action.description().startsWith(CombatModuleCatalog.module(CombatModuleCatalog.PARTICLE_BEAM).name())));
        assertFalse(second.stream().anyMatch(action -> action.phase().equals("WEAPON_HIT")
                && action.description().startsWith(CombatModuleCatalog.module(CombatModuleCatalog.MASS_DRIVER).name())));
        var paid = result.survivingAttackerFleet().ships().stream().filter(item -> item.id().equals("launcher")).findFirst().orElseThrow();
        assertEquals(0, ShipWeaponAmmunition.rounds(paid.storedCargoKg(), CombatModuleCatalog.module(CombatModuleCatalog.MASS_DRIVER)));
    }
}
