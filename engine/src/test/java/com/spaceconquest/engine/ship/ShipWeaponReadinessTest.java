package com.spaceconquest.engine.ship;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ShipWeaponReadinessTest {
    private final ShipPointDefenseTest fixture = new ShipPointDefenseTest();
    private ShipInstance ship(ShipDesign design, double ammo) {
        var source = fixture.ship("ship", design, 0);
        return new ShipInstance(source.id(), design.id(), design.ownerEntityId(), 1000, 0, 0,
                Map.of("cannon_round", ammo)).withPowerState(source.powerState());
    }
    @Test void duplicateMountsReportOneSharedMagazineAndAnImmutableNonSpendingQuote() {
        var d = fixture.design("owner", 10000, CombatModuleCatalog.GUN, CombatModuleCatalog.GUN);
        var source = ship(d, 9); var original = source.storedCargoKg();
        var status = ShipWeaponReadiness.inspect(d, source, true);
        var gun = status.weapons().getFirst();
        assertEquals(1, gun.wholeRounds()); assertEquals(List.of(1, 2), gun.sharedMounts());
        assertEquals(1, gun.fundedShots()); assertTrue(gun.status().contains("shared ammunition"));
        assertEquals(status, ShipWeaponReadiness.inspect(d, source, true));
        assertEquals(original, source.storedCargoKg()); assertEquals(10, source.generatorFuelMassKg());
        assertThrows(UnsupportedOperationException.class, () -> gun.sharedMounts().add(3));
    }
    @Test void emptyPowerDisabledAndWithdrawalBlockersRemainDistinct() {
        var healthy = fixture.design("owner", 10000, CombatModuleCatalog.GUN);
        assertTrue(ShipWeaponReadiness.inspect(healthy, ship(healthy, 4), true).weapons().getFirst().status().contains("Empty magazine"));
        var scarce = fixture.design("owner", 34, CombatModuleCatalog.GUN);
        var power = ShipWeaponReadiness.inspect(scarce, ship(scarce, 5), true).weapons().getFirst();
        assertEquals(0, power.fundedShots()); assertTrue(power.status().contains("Insufficient firing power"));
        var duplicateScarce = fixture.design("owner", 36, CombatModuleCatalog.GUN, CombatModuleCatalog.GUN);
        assertTrue(ShipWeaponReadiness.inspect(duplicateScarce, ship(duplicateScarce, 5), true).weapons().getFirst().status().contains("Insufficient firing power"));
        var disabled = ship(healthy, 5).withModuleDamage(new ShipModuleDamage(Map.of(0, 100.0)));
        var hardware = ShipWeaponReadiness.inspect(healthy, disabled, true).weapons().getFirst();
        assertEquals("Disabled hardware", hardware.status()); assertEquals(0, hardware.mounts().getFirst().condition());
        assertTrue(ShipWeaponReadiness.inspect(healthy, ship(healthy, 5), false).weapons().getFirst().status().contains("withdrawing"));
        assertEquals("Ready for quoted round", ShipWeaponReadiness.inspect(healthy, ship(healthy, 5), true).weapons().getFirst().status());
    }
    @Test void damagedAndEnergyLimitedBeamsReportReducedSalvosWithoutInventingAmmunition() {
        var d = fixture.design("owner", 72, CombatModuleCatalog.LASER);
        var status = ShipWeaponReadiness.inspect(d, fixture.ship("ship", d, 0), true).weapons().getFirst();
        assertTrue(status.status().contains("Reduced salvo")); assertEquals(16, status.fundedShots());
        assertEquals("", status.ammunitionId()); assertEquals(0, status.wholeRounds());
        var legacy = new ShipDesign("legacy", "Legacy", "owner", ShipRole.COMBAT_SHIP, "steel", List.of(CombatModuleCatalog.GUN),
                "steel", 0, 1000, 1000, 0, 1, 0, 0, true, false);
        assertTrue(ShipWeaponReadiness.inspect(legacy, ship(legacy, 5), true).weapons().getFirst().status().contains("unverified"));
    }
    @Test void defenseReservesSharedRoundsBeforeOffenseUsingCapturedApplicationRequirements() throws Exception {
        var d = fixture.design("owner", 10000, CombatModuleCatalog.KINETIC_POINT_DEFENSE, CombatModuleCatalog.GUN);
        var mapper = new ObjectMapper(); ObjectNode snapshot = mapper.valueToTree(d);
        var defense = (ObjectNode) snapshot.get("combatModules").get(CombatModuleCatalog.KINETIC_POINT_DEFENSE);
        defense.put("ammunitionId", "cannon_round");
        ((ObjectNode) defense.get("operationalStats")).put("ammunitionMassKg", 2);
        d = mapper.treeToValue(snapshot, ShipDesign.class);
        var source = ship(d, 6); var status = ShipWeaponReadiness.inspect(d, source, true);
        assertEquals(3, status.weapons().getFirst().wholeRounds()); assertEquals(3, status.weapons().getFirst().fundedShots());
        var gun = status.weapons().getLast(); assertEquals(0, gun.fundedShots());
        assertEquals(List.of(1, 2), gun.sharedMounts()); assertTrue(gun.status().contains("shared ammunition"));
        assertEquals(6, source.storedCargoKg().get("cannon_round"));
    }
}
