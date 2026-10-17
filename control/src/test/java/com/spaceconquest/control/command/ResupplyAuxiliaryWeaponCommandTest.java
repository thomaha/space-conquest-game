package com.spaceconquest.control.command;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.combat.*;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ResupplyAuxiliaryWeaponCommandTest {
    private final ShipModule weapon = CombatModuleCatalog.module(CombatModuleCatalog.MASS_DRIVER);
    private double mass() { return weapon.operationalStats().get("ammunitionMassKg"); }
    private GameState world() {
        var ship = new ShipInstance("carrier", "design", "own", 1000, 0, 0, Map.of(weapon.ammunitionId(), mass()))
                .withPowerState(new ShipPowerState(Map.of(), "rp1", "uranium", 20, false, 1, 1, 0, 0, 0, 0, 0));
        var fleet = new Fleet("fleet", "Fleet", "own", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(ship),
                FleetLocation.at(FleetLocation.Site.surface("earth")));
        var wing = new CarrierWing("wing", "carrier", "INTERCEPTOR_FIGHTER", 1, 1, 100,
                CombatModuleCatalog.application(weapon.id()).id(), "CAPITAL_SHIPS");
        var battery = new PlanetaryDefenseBattery("battery", "earth", wing.weaponApplicationId(), 1, true);
        var empire = new Empire("own", "Own", "human", "Individualist", 100, 0, List.of(), List.of(), Map.of(), List.of(), List.of());
        var hub = new CommercialHub("hub", "earth", 0, 10000, mass(), 10,
                Map.of(weapon.ammunitionId(), new MarketOrder(weapon.ammunitionId(), mass(), 0, 2, 0)));
        return GameState.builder().empires(List.of(empire)).fleets(List.of(fleet)).commercialHubs(List.of(hub))
                .auxiliaryForces(new AuxiliaryForces(List.of(wing), List.of(new AuxiliaryForces.OwnedBattery("own", battery)))).build();
    }
    @Test void competingQueuedWingTransfersCommitOnlyOneFiniteLoad() {
        var state = world(); var queue = new CommandQueue();
        var command = new ResupplyAuxiliaryWeaponCommand("own", ResupplyAuxiliaryWeaponCommand.Kind.LOAD_WING, "wing", "carrier", 1, 13);
        assertTrue(command.validate(state));
        var first = queue.submitTracked(command); var second = queue.submitTracked(command);
        assertFalse(first.isDone()); assertTrue(state.auxiliaryForces().wings().getFirst().stores().ammunitionKg().isEmpty());
        var result = queue.drainAndExecute(state);
        assertEquals(CommandOutcome.EXECUTED, first.join()); assertEquals(CommandOutcome.REJECTED, second.join());
        assertEquals(mass(), result.auxiliaryForces().wings().getFirst().stores().ammunitionKg().get(weapon.ammunitionId()));
        assertTrue(result.fleets().getFirst().ships().getFirst().storedCargoKg().isEmpty());
        assertEquals(7, result.fleets().getFirst().ships().getFirst().powerState().batteryChargeKwh());
        assertFalse(new ResupplyAuxiliaryWeaponCommand("foreign", command.kind(), "wing", "carrier", 1, 1).validate(state));
        var missingHost = state.withFleets(List.of()); assertSame(missingHost, command.apply(missingHost));
    }
    @Test void batteryPurchaseAndChargeCommitOwnedRecordsAndRejectStaleSupplyOrLocation() {
        var state = world(); var queue = new CommandQueue();
        var buy = new ResupplyAuxiliaryWeaponCommand("own", ResupplyAuxiliaryWeaponCommand.Kind.BUY_BATTERY_AMMUNITION, "battery", null, 1, 0);
        var charge = new ResupplyAuxiliaryWeaponCommand("own", ResupplyAuxiliaryWeaponCommand.Kind.CHARGE_BATTERY, "battery", "carrier", 0, 13);
        var first = queue.submitTracked(buy); var stale = queue.submitTracked(buy); queue.submit(charge);
        var result = queue.drainAndExecute(state);
        assertEquals(CommandOutcome.EXECUTED, first.join()); assertEquals(CommandOutcome.REJECTED, stale.join());
        var stores = result.auxiliaryForces().batteries().getFirst().battery().stores();
        assertEquals(mass(), stores.ammunitionKg().get(weapon.ammunitionId())); assertEquals(13, stores.energyKwh());
        assertEquals(100 - 2 * mass(), result.empires().getFirst().treasuryCredits());
        assertEquals(0, result.commercialHubs().getFirst().activeOrders().get(weapon.ammunitionId()).supplyKg());
        assertEquals(7, result.fleets().getFirst().ships().getFirst().powerState().batteryChargeKwh());
        assertFalse(new ResupplyAuxiliaryWeaponCommand("foreign", buy.kind(), "battery", null, 1, 0).validate(state));
        var orbit = state.withFleets(List.of(state.fleets().getFirst().withLocation(FleetLocation.at(FleetLocation.Site.orbit("earth")))));
        assertFalse(charge.validate(orbit)); assertSame(orbit, charge.apply(orbit));
        var shortStock = result.toBuilder().auxiliaryForces(state.auxiliaryForces()).build();
        assertSame(shortStock, buy.apply(shortStock));
        assertFalse(new ResupplyAuxiliaryWeaponCommand("own", charge.kind(), "battery", "carrier", 0, Double.NaN).validate(state));
    }
    @Test void capturedMagazineAndEnergyLimitsRejectTransfersAndPurchasesAtomically() {
        var state = world(); var forces = state.auxiliaryForces(); var wing = forces.wings().getFirst();
        var fullWing = wing.withStores(new AuxiliaryWeaponStores(Map.of(weapon.ammunitionId(), wing.hardware().value("magazineKg")),
                wing.hardware().value("energyCapacityKwh")));
        var full = state.toBuilder().auxiliaryForces(forces.withWing(fullWing)).build();
        var transfer = new ResupplyAuxiliaryWeaponCommand("own", ResupplyAuxiliaryWeaponCommand.Kind.LOAD_WING, "wing", "carrier", 1, 1);
        assertFalse(transfer.validate(full)); assertSame(full, transfer.apply(full));
        var battery = forces.batteries().getFirst().battery();
        var fullBattery = battery.withStores(new AuxiliaryWeaponStores(Map.of(weapon.ammunitionId(), battery.hardware().value("magazineKg")),
                battery.hardware().value("energyCapacityKwh")));
        var fullSurface = state.toBuilder().auxiliaryForces(forces.withBattery(fullBattery)).build();
        var buy = new ResupplyAuxiliaryWeaponCommand("own", ResupplyAuxiliaryWeaponCommand.Kind.BUY_BATTERY_AMMUNITION, "battery", null, 1, 0);
        var charge = new ResupplyAuxiliaryWeaponCommand("own", ResupplyAuxiliaryWeaponCommand.Kind.CHARGE_BATTERY, "battery", "carrier", 0, 1);
        assertFalse(buy.validate(fullSurface)); assertSame(fullSurface, buy.apply(fullSurface));
        assertFalse(charge.validate(fullSurface)); assertSame(fullSurface, charge.apply(fullSurface));
        assertEquals(100, fullSurface.empires().getFirst().treasuryCredits());
        assertEquals(20, fullSurface.fleets().getFirst().ships().getFirst().powerState().batteryChargeKwh());
    }
}
