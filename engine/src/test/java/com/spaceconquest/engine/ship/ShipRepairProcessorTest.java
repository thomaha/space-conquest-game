package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.combat.ShipCombatStats;
import com.spaceconquest.engine.logistics.TradeRoute;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ShipRepairProcessorTest {
    @TempDir Path directory;
    private ShipDesign design(String id, String... hardware) {
        var modules = new ArrayList<>(List.of(hardware)); modules.add(ShipComponentCatalog.FISSION_REACTOR_ID);
        var profile = ShipPowerProfile.capture(modules.stream().map(ShipComponentCatalog::module).toList());
        return new ShipDesign(id, id, "owner", ShipRole.COMBAT_SHIP, "steel", modules, "steel", 0,
                1000, 10000, 1000, 500, 1, 0, 1000, false, false, ShipManufacturingProfile.baseline(), profile);
    }
    private ShipInstance ship(String id, ShipDesign design, double hull, Map<String, Double> cargo, Map<Integer, Double> damage) {
        return new ShipInstance(id, design.id(), "owner", hull, 0, 100, cargo)
                .withPowerState(new ShipPowerState(Map.of("refined_uranium", 1.0), "rp1", "uranium", 0, true, 1, 1, 0, 0, 0, 0, 0))
                .withModuleDamage(new ShipModuleDamage(damage));
    }
    private Fleet fleet(String id, ShipInstance... ships) {
        return new Fleet(id, id, "owner", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(ships));
    }
    private GameState state(List<ShipDesign> designs, Fleet... fleets) {
        return GameState.builder().shipDesigns(designs).fleets(List.of(fleets)).build();
    }
    private GameState day(GameState state) { return state.withFleets(ShipPowerProcessor.advanceDay(state)).withTurn(state.turn() + 1); }
    private ShipInstance find(GameState state, String id) {
        return state.fleets().stream().flatMap(fleet -> fleet.ships().stream()).filter(ship -> ship.id().equals(id)).findFirst().orElseThrow();
    }

    @Test void repairsSelfThenFleetUsingOneSharedCapacityAndActualHostMaterialsAndPower() {
        var hostDesign = design("host", ShipRepairModuleCatalog.WORKSHOP, CombatModuleCatalog.GUN);
        var targetDesign = design("target", CombatModuleCatalog.GUN);
        var host = ship("host", hostDesign, 990, Map.of("steel", 100.0), Map.of(1, 5.0));
        var target = ship("target", targetDesign, 1000, Map.of(), Map.of(0, 30.0));
        var initial = state(List.of(hostDesign, targetDesign), fleet("fleet", target, host));
        var after = day(initial); var repairedHost = find(after, "host"); var repairedTarget = find(after, "target");
        assertEquals(1000, repairedHost.currentHullHealth()); assertEquals(ShipModuleDamage.empty(), repairedHost.moduleDamage());
        assertEquals(25, repairedTarget.moduleDamage().damageByMount().get(0));
        assertEquals(80, repairedHost.storedCargoKg().get("steel"));
        assertEquals(1 - 24 * 23.9 / 6_000_000,
                repairedHost.generatorFuelMassKg(), 1e-12);
        assertEquals(target.currentFuelKg(), repairedTarget.currentFuelKg());
        assertTrue(ShipCombatStats.of(targetDesign, repairedTarget).firepower() > ShipCombatStats.of(targetDesign, target).firepower());
        assertEquals(990, host.currentHullHealth()); assertEquals(100, host.storedCargoKg().get("steel"));
    }

    @Test void limitingIngredientsBoundShieldRepairsAndNoShieldChargeIsCreated() {
        var hostDesign = design("host", ShipRepairModuleCatalog.WORKSHOP); var targetDesign = design("target", CombatModuleCatalog.SHIELD);
        var host = ship("host", hostDesign, 1000, Map.of("superconducting_cuprates", 1.0, "refined_titanium", 1.0), Map.of());
        var target = ship("target", targetDesign, 1000, Map.of(), Map.of(0, 20.0));
        var initial = state(List.of(hostDesign, targetDesign), fleet("fleet", host, target));
        var after = initial.withFleets(ShipRepairProcessor.advanceDay(initial, initial.fleets()));
        assertEquals(19, find(after, "target").moduleDamage().damageByMount().get(0));
        assertEquals(0, find(after, "target").currentShieldHealth());
        assertEquals(Map.of("superconducting_cuprates", .5), find(after, "host").storedCargoKg());
        assertEquals(find(after, "target").moduleDamage(), find(day(after), "target").moduleDamage());
        var powered = day(initial); var poweredTarget = find(powered, "target");
        assertEquals(160, poweredTarget.currentShieldHealth()); // Recharge precedes the one-point emitter repair.
        assertEquals(19, poweredTarget.moduleDamage().damageByMount().get(0));
        assertEquals(1 - (26 * 24 + 160 * ShipShieldRecharge.KWH_PER_POINT) / 6_000_000,
                poweredTarget.generatorFuelMassKg(), 1e-12);
    }

    @Test void workshopConditionLimitsCapacityAndDisabledWorkshopsCannotRepairThemselves() {
        var design = design("host", ShipRepairModuleCatalog.WORKSHOP);
        var materials = Map.of("steel", 250.0, "refined_copper", 50.0, "silicon", 50.0);
        var damaged = ship("host", design, 1000, materials, Map.of(0, 50.0));
        var after = day(state(List.of(design), fleet("fleet", damaged)));
        assertEquals(40, find(after, "host").moduleDamage().damageByMount().get(0));
        assertEquals(225, find(after, "host").storedCargoKg().get("steel"));
        var disabled = damaged.withModuleDamage(new ShipModuleDamage(Map.of(0, 100.0)));
        var blocked = day(state(List.of(design), fleet("fleet", disabled)));
        assertEquals(disabled.moduleDamage(), find(blocked, "host").moduleDamage());
        assertEquals(materials, find(blocked, "host").storedCargoKg());
        assertTrue(ShipModuleDamage.empty().hit(design, 100, null).damageByMount().containsKey(0));
    }

    @Test void twoWorkshopsStackButRestoringADisabledHostDoesNotAddWorkInTheSameTick() {
        var twin = design("twin", ShipRepairModuleCatalog.WORKSHOP, ShipRepairModuleCatalog.WORKSHOP);
        var targetDesign = design("target", CombatModuleCatalog.GUN);
        var host = ship("host", twin, 1000, Map.of("steel", 100.0), Map.of());
        var target = ship("target", targetDesign, 1000, Map.of(), Map.of(0, 80.0));
        var after = day(state(List.of(twin, targetDesign), fleet("fleet", host, target)));
        assertEquals(40, find(after, "target").moduleDamage().damageByMount().get(0));
        var single = design("single", ShipRepairModuleCatalog.WORKSHOP);
        var active = ship("active", single, 1000, Map.of("steel", 100.0, "refined_copper", 20.0, "silicon", 20.0), Map.of());
        var disabled = ship("disabled", single, 1000, Map.of("steel", 100.0), Map.of(0, 100.0));
        after = day(state(List.of(single, targetDesign), fleet("fleet", active, disabled, target)));
        assertEquals(80, find(after, "disabled").moduleDamage().damageByMount().get(0));
        assertEquals(target.moduleDamage(), find(after, "target").moduleDamage());
    }

    @Test void missingPowerMaterialsHardwareAndCommittedFreightPreventUnfundedRepairs() {
        var hostDesign = design("host", ShipRepairModuleCatalog.WORKSHOP); var targetDesign = design("target", CombatModuleCatalog.GUN);
        var host = ship("host", hostDesign, 1000, Map.of("steel", 100.0), Map.of());
        var target = ship("target", targetDesign, 1000, Map.of("steel", 100.0), Map.of(0, 20.0));
        for (var blockedHost : List.of(host.withPowerState(ShipPowerState.empty()),
                ship("host", hostDesign, 1000, Map.of(), Map.of()), ship("host", targetDesign, 1000, Map.of("steel", 100.0), Map.of()))) {
            var after = day(state(List.of(hostDesign, targetDesign), fleet("fleet", blockedHost, target)));
            assertEquals(target.moduleDamage(), find(after, "target").moduleDamage());
            assertEquals(target.storedCargoKg(), find(after, "target").storedCargoKg());
        }
        var route = new TradeRoute("route", "Route", "owner", "a", "b", "steel", 100, 0, 100,
                List.of("host"), 0, true);
        var initial = state(List.of(hostDesign, targetDesign), fleet("fleet", host, target)).withTradeRoutes(List.of(route));
        var after = day(initial);
        assertEquals(host.storedCargoKg(), find(after, "host").storedCargoKg());
        assertEquals(target.moduleDamage(), find(after, "target").moduleDamage());
    }

    @Test void repairsStayWithinOwnedFleetAndDoNotReviveDestroyedShips() {
        var hostDesign = design("host", ShipRepairModuleCatalog.WORKSHOP); var targetDesign = design("target", CombatModuleCatalog.GUN);
        var host = ship("host", hostDesign, 1000, Map.of("steel", 100.0), Map.of());
        var outside = ship("outside", targetDesign, 900, Map.of(), Map.of(0, 20.0));
        var dead = ship("dead", targetDesign, 0, Map.of(), Map.of(0, 20.0));
        var foreign = new ShipInstance("foreign", targetDesign.id(), "foreign", 900, 0, 100, Map.of())
                .withModuleDamage(new ShipModuleDamage(Map.of(0, 20.0)));
        var after = day(state(List.of(hostDesign, targetDesign), fleet("fleet", host, dead, foreign), fleet("outside", outside)));
        for (var original : List.of(outside, dead, foreign)) {
            assertEquals(original.currentHullHealth(), find(after, original.id()).currentHullHealth());
            assertEquals(original.moduleDamage(), find(after, original.id()).moduleDamage());
        }
        assertEquals(host.storedCargoKg(), find(after, "host").storedCargoKg());
    }

    @Test void partialRepairAndMaterialsSurviveSaveReloadAndContinueIdentically() throws Exception {
        var hostDesign = design("host", ShipRepairModuleCatalog.WORKSHOP); var targetDesign = design("target", CombatModuleCatalog.GUN);
        var host = ship("host", hostDesign, 1000, Map.of("steel", 100.0), Map.of());
        var target = ship("target", targetDesign, 1000, Map.of(), Map.of(0, 30.0));
        var partial = day(state(List.of(hostDesign, targetDesign), fleet("fleet", host, target)));
        assertEquals(10, find(partial, "target").moduleDamage().damageByMount().get(0));
        var manager = new SaveGameManager(directory); var file = directory.resolve("repair.scsave").toFile();
        manager.save(file, partial, 1, "2027-01-01T08:00:00");
        var restored = manager.load(file).toGameState(partial.turn(), "RUNNING");
        assertEquals(partial.fleets(), restored.fleets()); assertEquals(day(partial).fleets(), day(restored).fleets());
        var finished = day(restored); assertEquals(ShipModuleDamage.empty(), find(finished, "target").moduleDamage());
        assertEquals(70, find(finished, "host").storedCargoKg().get("steel"));
    }
}
