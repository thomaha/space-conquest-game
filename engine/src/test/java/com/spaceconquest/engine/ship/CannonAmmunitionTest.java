package com.spaceconquest.engine.ship;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.combat.TacticalCombatProcessor;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CannonAmmunitionTest {
    private final ShipPointDefenseTest fixture = new ShipPointDefenseTest();
    private static final double HOURS = 1.0 / 60;
    private final ShipModule weapon = CombatModuleCatalog.module(CombatModuleCatalog.GUN);
    private ShipInstance ship(ShipDesign design, double kg) {
        var source = fixture.ship("gunner", design, 0);
        return new ShipInstance(source.id(), design.id(), design.ownerEntityId(), 1000, 0, 0,
                kg > 0 ? Map.of(weapon.ammunitionId(), kg) : Map.of()).withPowerState(source.powerState());
    }
    private ShipCombatPower.Result pay(ShipDesign design, ShipInstance ship) {
        return ShipCombatPower.round(ship, design, HOURS, true, false, 0);
    }
    private ShipDesign capture(ShipDesign d, ShipModule module) {
        return new ShipDesign(d.id(), d.name(), d.ownerEntityId(), d.role(), d.hullMaterialId(), d.equippedModuleIds(),
                d.armorMaterialId(), d.armorThicknessCm(), d.totalDryMassKg(), d.maxCargoMassKg(), d.fuelCapacityKg(),
                d.powerBalanceKw(), d.calculatedStructuralIntegrity(), d.minLaunchThrustRequiredN(), d.totalThrustN(),
                d.isValidForLaunch(), d.isProprietaryCorporateDesign(), d.manufacturingProfile(), d.powerProfile(),
                d.sensorProfile(), merged(d, module));
    }
    private Map<String, ShipModule> merged(ShipDesign d, ShipModule module) {
        var modules = new HashMap<>(d.combatModules()); modules.put(module.id(), module); return modules;
    }
    private ShipModule configured(String id, String ammo, double mass, double energy) throws Exception {
        var mapper = new ObjectMapper();
        ObjectNode app = mapper.valueToTree(CombatModuleCatalog.application(id));
        ((ObjectNode) app.get("combatModule")).put("ammunitionId", ammo);
        var stats = (ObjectNode) app.get("combatModule").get("operationalStats");
        stats.put("ammunitionMassKg", mass); stats.put("weaponSalvoKwh", energy);
        return mapper.treeToValue(app, TechnicalApplication.class).combatModule();
    }

    @Test void pulseCountDefinesWholeRoundsAndEachShotsEnergy() throws Exception {
        var mapper = new ObjectMapper();
        ObjectNode app = mapper.valueToTree(CombatModuleCatalog.application(weapon.id()));
        ((ObjectNode) app.get("combatModule").get("operationalStats")).put("shotsPerRound", 2);
        var d = capture(fixture.design("gunner", 10000, weapon.id()), mapper.treeToValue(app, TechnicalApplication.class).combatModule());
        var paid = pay(d, ship(d, 9));
        assertEquals(1, paid.directHits().size()); assertEquals(40, paid.firepower()); assertEquals(.5, paid.paidWeaponKwh());
        assertEquals(4, paid.ship().storedCargoKg().get(weapon.ammunitionId()));
        var full = pay(d, ship(d, 10));
        assertEquals(2, full.directHits().size()); assertEquals(80, full.firepower()); assertEquals(1, full.paidWeaponKwh());
    }

    @Test void energyOnlySalvosKeepPartialFundingAndDoNotSpendUnfundedCannonRounds() {
        double output = 2 + weapon.powerDrawKw() + CombatModuleCatalog.module(CombatModuleCatalog.LASER).powerDrawKw() + .5 / HOURS;
        var d = fixture.design("gunner", output, CombatModuleCatalog.LASER, weapon.id());
        var source = ship(d, 5); var paid = pay(d, source);
        assertEquals(.5, paid.paidWeaponKwh(), 1e-9); assertEquals(20, paid.firepower(), 1e-9);
        assertEquals(source.storedCargoKg(), paid.ship().storedCargoKg());
        assertTrue(paid.directHits().stream().allMatch(hit -> hit.weapon().id().equals(CombatModuleCatalog.LASER)));
    }

    @Test void defenseAndProjectileLaunchesCannotSpendCannonsSharedRoundsTwice() throws Exception {
        var missile = configured(CombatModuleCatalog.MISSILE, weapon.ammunitionId(), 5, 2);
        var d = capture(fixture.design("gunner", 10000, missile.id(), weapon.id()), missile);
        var paid = pay(d, ship(d, 5));
        assertEquals(80, paid.directFirepower()); assertTrue(paid.missileLaunches().isEmpty());
        assertFalse(paid.ship().storedCargoKg().containsKey(weapon.ammunitionId()));
        var defense = configured(CombatModuleCatalog.KINETIC_POINT_DEFENSE, weapon.ammunitionId(), 5, 0);
        d = capture(fixture.design("gunner", 10000, defense.id(), weapon.id()), defense);
        paid = ShipCombatPower.round(ship(d, 10), d, HOURS, true, false, 0, 2);
        assertEquals(2, paid.pointDefenseInterceptions()); assertEquals(0, paid.directFirepower());
        assertFalse(paid.ship().storedCargoKg().containsKey(weapon.ammunitionId()));
    }

    @Test void anUnaffordableMountLeavesSharedRoundsForAnAffordableMount() throws Exception {
        var cheaper = configured(CombatModuleCatalog.PARTICLE_BEAM, weapon.ammunitionId(), 5, .25);
        double output = 2 + weapon.powerDrawKw() + cheaper.powerDrawKw() + .5 / HOURS;
        var d = capture(fixture.design("gunner", output, weapon.id(), cheaper.id()), cheaper);
        var paid = pay(d, ship(d, 5));
        assertEquals(.25, paid.paidWeaponKwh(), 1e-9); assertEquals(180, paid.firepower());
        assertEquals(cheaper.id(), paid.directHits().getFirst().weapon().id());
        assertFalse(paid.ship().storedCargoKg().containsKey(weapon.ammunitionId()));
    }

    @Test void zeroEnergyApplicationShotsStillNeedAmmunitionAndFiringAuthorization() throws Exception {
        var custom = configured(weapon.id(), weapon.ammunitionId(), 5, 0);
        var d = capture(fixture.design("gunner", 4, weapon.id()), custom);
        var source = ship(d, 5);
        var paid = pay(d, source);
        assertEquals(80, paid.firepower()); assertEquals(0, paid.paidWeaponKwh());
        assertFalse(paid.ship().storedCargoKg().containsKey(weapon.ammunitionId()));
        var held = ShipCombatPower.round(source, d, HOURS, false, false);
        assertEquals(0, held.firepower()); assertEquals(source.storedCargoKg(), held.ship().storedCargoKg());
        assertEquals(0, pay(d, ship(d, 0)).firepower());
    }

    @Test void wholeSharedRoundsLimitDuplicateMountsAndResidualMassCannotFire() {
        var d = fixture.design("gunner", 10000, weapon.id(), weapon.id());
        var source = ship(d, 9);
        var paid = pay(d, source);
        assertEquals(1, paid.directHits().size()); assertEquals(80, paid.firepower());
        assertEquals(1, paid.paidWeaponKwh()); assertEquals(4, paid.ship().storedCargoKg().get(weapon.ammunitionId()));
        assertEquals(9, source.storedCargoKg().get(weapon.ammunitionId()));
        var empty = pay(d, paid.ship());
        assertEquals(0, empty.firepower()); assertEquals(0, empty.requestedWeaponKwh());
        assertTrue(empty.directHits().isEmpty()); assertEquals(0, ShipWeaponRange.maximumRange(d, paid.ship()));
        assertEquals(0, ShipWeaponRange.capability(d, paid.ship(), "weaponDamage", 0));
    }

    @Test void ammunitionAndWholeLaunchEnergyAreBothRequired() {
        var d = fixture.design("gunner", 34, weapon.id());
        var source = ship(d, 5);
        var unfunded = pay(d, source);
        assertEquals(1, unfunded.requestedWeaponKwh()); assertEquals(0, unfunded.paidWeaponKwh());
        assertTrue(unfunded.directHits().isEmpty()); assertEquals(source.storedCargoKg(), unfunded.ship().storedCargoKg());
        var funded = fixture.design("gunner", 10000, weapon.id());
        for (double kg : List.of(0.0, 4.0)) {
            var empty = pay(funded, ship(funded, kg));
            assertEquals(0, empty.firepower()); assertEquals(0, empty.requestedWeaponKwh());
        }
        var disabled = pay(funded, ship(funded, 5).withModuleDamage(new ShipModuleDamage(Map.of(0, 100.0))));
        assertEquals(5, disabled.ship().storedCargoKg().get(weapon.ammunitionId())); assertEquals(0, disabled.firepower());
        var paid = pay(funded, ship(funded, 5));
        assertFalse(paid.ship().storedCargoKg().containsKey(weapon.ammunitionId()));
        assertEquals(10 - (4 * HOURS + 1) / 6000, paid.ship().generatorFuelMassKg(), 1e-12);
    }

    @Test void configuredRoundMassControlsConsumptionAndSavedBlueprintsKeepIt() throws Exception {
        var mapper = new ObjectMapper().configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        ObjectNode app = mapper.valueToTree(CombatModuleCatalog.application(weapon.id()));
        ((ObjectNode) app.get("combatModule").get("operationalStats")).put("ammunitionMassKg", 2);
        var custom = mapper.treeToValue(app, TechnicalApplication.class).combatModule();
        var d = capture(fixture.design("gunner", 10000, weapon.id(), weapon.id()), custom);
        var paid = pay(d, ship(d, 5));
        assertEquals(2, paid.directHits().size()); assertEquals(1, paid.ship().storedCargoKg().get(weapon.ammunitionId()));
        var state = GameState.builder().shipDesigns(List.of(d)).fleets(List.of(fixture.fleet("gunner", List.of(paid.ship())))).build();
        var restored = mapper.readValue(mapper.writeValueAsString(SaveGame.fromGameState(state, "now", 1, "now")), SaveGame.class).toGameState(0, "RUNNING");
        assertEquals(d, restored.shipDesigns().getFirst());
        assertEquals(2, CombatModuleCatalog.module(restored.shipDesigns().getFirst(), weapon.id()).operationalStats().get("ammunitionMassKg"));
        var savedShip = restored.fleets().getFirst().ships().getFirst();
        assertEquals(paid.ship(), savedShip);
        assertEquals(pay(d, paid.ship()), pay(restored.shipDesigns().getFirst(), savedShip));
    }

    @Test void paidMissesConsumeRoundsAndEnergyWithoutDamageOrRefund() throws Exception {
        var mapper = new ObjectMapper();
        ObjectNode app = mapper.valueToTree(CombatModuleCatalog.application(weapon.id()));
        ((ObjectNode) app.get("combatModule").get("operationalStats")).put("accuracy", 0);
        var custom = mapper.treeToValue(app, TechnicalApplication.class).combatModule();
        var d = capture(fixture.design("gunner", 10000, weapon.id()), custom);
        var target = fixture.design("target", 10000);
        var result = new TacticalCombatProcessor(new Random(42)).resolveFleetEngagement(
                fixture.fleet("gunner", List.of(ship(d, 5))), fixture.fleet("target", List.of(fixture.ship("target", target, 0))),
                List.of(d, target), null, (a, b) -> true, (a, b) -> 0);
        assertTrue(result.roundReports().stream().allMatch(r -> r.attackerDamageDealt() == 0));
        assertTrue(result.roundReports().stream().flatMap(r -> r.detailedActions().stream()).anyMatch(a -> a.phase().equals("WEAPON_MISS")));
        var after = result.survivingAttackerFleet().ships().getFirst();
        assertFalse(after.storedCargoKg().containsKey(weapon.ammunitionId()));
        assertEquals(10 - (4 * HOURS * 10 + 1) / 6000, after.generatorFuelMassKg(), 1e-12);
        assertEquals(1000, result.survivingDefenderFleet().ships().getFirst().currentHullHealth());
    }

    @Test void currentSavesPreserveRequiredRoundsAndCapturedWeaponStats() throws Exception {
        var mapper = new ObjectMapper(); var d = fixture.design("gunner", 10000, weapon.id());
        var state = GameState.builder().shipDesigns(List.of(d)).build();
        ObjectNode oldSave = mapper.valueToTree(SaveGame.fromGameState(state, "now", 1, "now"));
        ObjectNode module = (ObjectNode) oldSave.get("shipDesigns").get(0).get("combatModules").get(weapon.id());
        ((ObjectNode) module.get("operationalStats")).put("weaponDamage", 333);
        var restored = mapper.treeToValue(oldSave, SaveGame.class).toGameState(0, "RUNNING").shipDesigns().getFirst();
        assertEquals(weapon.ammunitionId(), CombatModuleCatalog.module(restored, weapon.id()).ammunitionId());
        assertEquals(333, CombatModuleCatalog.module(restored, weapon.id()).operationalStats().get("weaponDamage"));
        assertEquals(0, pay(restored, ship(restored, 0)).firepower());
        assertEquals(333, pay(restored, ship(restored, 5)).firepower());
    }
}
