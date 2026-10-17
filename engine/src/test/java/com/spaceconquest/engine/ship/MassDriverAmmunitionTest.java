package com.spaceconquest.engine.ship;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.combat.TacticalCombatProcessor;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MassDriverAmmunitionTest {
    private final ShipPointDefenseTest fixture = new ShipPointDefenseTest();
    private static final double HOURS = 1.0 / 60;
    private final ShipModule weapon = CombatModuleCatalog.module(CombatModuleCatalog.MASS_DRIVER);
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
                d.sensorProfile(), Map.of(module.id(), module));
    }

    @Test void wholeSharedSlugsLimitDuplicateMountsAndResidualMassCannotFire() {
        var d = fixture.design("gunner", 10000, weapon.id(), weapon.id());
        var source = ship(d, 39);
        var paid = pay(d, source);
        assertEquals(1, paid.missileLaunches().size()); assertEquals(220, paid.firepower());
        assertEquals(12, paid.paidWeaponKwh()); assertEquals(19, paid.ship().storedCargoKg().get(weapon.ammunitionId()));
        assertEquals(39, source.storedCargoKg().get(weapon.ammunitionId()));
        var empty = pay(d, paid.ship());
        assertEquals(0, empty.firepower()); assertEquals(0, empty.requestedWeaponKwh());
        assertTrue(empty.missileLaunches().isEmpty()); assertEquals(0, ShipWeaponRange.maximumRange(d, paid.ship()));
        assertEquals(0, ShipWeaponRange.capability(d, paid.ship(), "weaponDamage", 0));
    }

    @Test void ammunitionAndWholeLaunchEnergyAreBothRequired() {
        var d = fixture.design("gunner", 100, weapon.id());
        var source = ship(d, 20);
        var unfunded = pay(d, source);
        assertEquals(12, unfunded.requestedWeaponKwh()); assertEquals(0, unfunded.paidWeaponKwh());
        assertTrue(unfunded.missileLaunches().isEmpty()); assertEquals(source.storedCargoKg(), unfunded.ship().storedCargoKg());
        var funded = fixture.design("gunner", 10000, weapon.id());
        for (double kg : List.of(0.0, 19.0)) {
            var empty = pay(funded, ship(funded, kg));
            assertEquals(0, empty.firepower()); assertEquals(0, empty.requestedWeaponKwh());
        }
        var disabled = pay(funded, ship(funded, 20).withModuleDamage(new ShipModuleDamage(Map.of(0, 100.0))));
        assertEquals(20, disabled.ship().storedCargoKg().get(weapon.ammunitionId())); assertEquals(0, disabled.firepower());
        var paid = pay(funded, ship(funded, 20));
        assertFalse(paid.ship().storedCargoKg().containsKey(weapon.ammunitionId()));
        assertEquals(10 - (62 * HOURS + 12) / 6000, paid.ship().generatorFuelMassKg(), 1e-12);
    }

    @Test void configuredSlugMassControlsConsumptionAndSavedBlueprintsKeepIt() throws Exception {
        var mapper = new ObjectMapper();
        ObjectNode app = mapper.valueToTree(CombatModuleCatalog.application(weapon.id()));
        ((ObjectNode) app.get("combatModule").get("operationalStats")).put("ammunitionMassKg", 10);
        var custom = mapper.treeToValue(app, TechnicalApplication.class).combatModule();
        var d = capture(fixture.design("gunner", 10000, weapon.id(), weapon.id()), custom);
        var paid = pay(d, ship(d, 25));
        assertEquals(2, paid.missileLaunches().size()); assertEquals(5, paid.ship().storedCargoKg().get(weapon.ammunitionId()));
        var state = GameState.builder().shipDesigns(List.of(d)).build();
        var restored = mapper.readValue(mapper.writeValueAsString(SaveGame.fromGameState(state, "now", 1, "now")), SaveGame.class).toGameState(0, "RUNNING");
        assertEquals(d, restored.shipDesigns().getFirst());
        assertEquals(10, CombatModuleCatalog.module(restored.shipDesigns().getFirst(), weapon.id()).operationalStats().get("ammunitionMassKg"));
    }

    @Test void paidMissesConsumeSlugsAndEnergyWithoutDamageOrRefund() throws Exception {
        var mapper = new ObjectMapper();
        ObjectNode app = mapper.valueToTree(CombatModuleCatalog.application(weapon.id()));
        ((ObjectNode) app.get("combatModule").get("operationalStats")).put("accuracy", 0);
        var custom = mapper.treeToValue(app, TechnicalApplication.class).combatModule();
        var d = capture(fixture.design("gunner", 10000, weapon.id()), custom);
        var target = fixture.design("target", 10000);
        var result = new TacticalCombatProcessor(new Random(42)).resolveFleetEngagement(
                fixture.fleet("gunner", List.of(ship(d, 20))), fixture.fleet("target", List.of(fixture.ship("target", target, 0))),
                List.of(d, target), null, (a, b) -> true, (a, b) -> 0);
        assertTrue(result.roundReports().stream().allMatch(r -> r.attackerDamageDealt() == 0));
        assertTrue(result.roundReports().stream().flatMap(r -> r.detailedActions().stream()).anyMatch(a -> a.phase().equals("WEAPON_MISS")));
        var after = result.survivingAttackerFleet().ships().getFirst();
        assertFalse(after.storedCargoKg().containsKey(weapon.ammunitionId()));
        assertEquals(10 - (62 * HOURS * 10 + 12) / 6000, after.generatorFuelMassKg(), 1e-12);
        assertEquals(1000, result.survivingDefenderFleet().ships().getFirst().currentHullHealth());
    }

    @Test void currentSavesPreserveRequiredSlugsAndCapturedWeaponStats() throws Exception {
        var mapper = new ObjectMapper(); var d = fixture.design("gunner", 10000, weapon.id());
        var state = GameState.builder().shipDesigns(List.of(d)).build();
        ObjectNode oldSave = mapper.valueToTree(SaveGame.fromGameState(state, "now", 1, "now"));
        ObjectNode module = (ObjectNode) oldSave.get("shipDesigns").get(0).get("combatModules").get(weapon.id());
        ((ObjectNode) module.get("operationalStats")).put("weaponDamage", 333);
        var restored = mapper.treeToValue(oldSave, SaveGame.class).toGameState(0, "RUNNING").shipDesigns().getFirst();
        assertEquals(weapon.ammunitionId(), CombatModuleCatalog.module(restored, weapon.id()).ammunitionId());
        assertEquals(333, CombatModuleCatalog.module(restored, weapon.id()).operationalStats().get("weaponDamage"));
        assertEquals(0, pay(restored, ship(restored, 0)).firepower());
        assertEquals(333, pay(restored, ship(restored, 20)).firepower());
    }
}
