package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.combat.ShipCombatStats;
import com.spaceconquest.engine.combat.TacticalCombatProcessor;
import com.spaceconquest.engine.galaxy.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Ion combat and finite onboard repairs exercised through the authoritative daily engine turn. */
class IonCampaignIntegrationTest {
    private static final List<String> TARGET_MODULES = List.of(ShipRepairModuleCatalog.WORKSHOP,
            ShipComponentCatalog.FISSION_REACTOR_ID, "mod_chemical_rocket", SensorModuleCatalog.BASIC,
            CombatModuleCatalog.ION, PropulsionCatalog.FUEL_TANK_MODULE_ID);

    private ShipDesign design(String id, String owner, List<String> modules) {
        var hardware = modules.stream().map(ShipComponentCatalog::module).toList();
        return new ShipDesign(id, id, owner, ShipRole.COMBAT_SHIP, "steel", modules, "steel", 0,
                20000, 10000, 1000, 500, 10, 0,
                hardware.stream().mapToDouble(ShipModule::thrustOutputN).sum(), false, false,
                ShipManufacturingProfile.baseline(), ShipPowerProfile.capture(hardware));
    }

    private ShipInstance ship(String id, ShipDesign design, Map<String, Double> cargo) {
        return new ShipInstance(id, design.id(), design.ownerEntityId(), 1000, 0, 100, cargo)
                .withPowerState(new ShipPowerState(Map.of("refined_uranium", 1.0), "rp1", "uranium",
                        0, false, 1, 1, 0, 0, 0, 0, 0));
    }

    private Fleet fleet(String id, ShipInstance... ships) {
        return new Fleet(id, id, ships[0].ownerEntityId(), "sol", "", 0, 0, 0, false, "PASSIVE", List.of(ships))
                .withShieldPolicy(FleetShieldPolicy.Mode.CONSERVE_ENERGY);
    }

    private Empire empire(String id) {
        return new Empire(id, id, "human", "Individualist", 1000, 0, List.of(), List.of(), Map.of(), List.of(), List.of());
    }

    private GameState opening() {
        var attackModules = new ArrayList<>(Collections.nCopies(4, ShipComponentCatalog.FISSION_REACTOR_ID));
        attackModules.addAll(Collections.nCopies(3, CombatModuleCatalog.ION)); attackModules.add(SensorModuleCatalog.BASIC);
        var attacker = design("ion-attacker", "enemy", attackModules);
        var target = design("target", "owner", TARGET_MODULES);
        return GameState.builder().empires(List.of(empire("owner"), empire("enemy")))
                .shipDesigns(List.of(attacker, target))
                .solarSystems(List.of(new SolarSystem("sol", "Sol", "", 0, 0, 0, 1.989e30, 1392700, "yellow", List.of(), List.of())))
                .diplomaticRelations(List.of(new DiplomaticRelation("owner", "enemy", "TOTAL_WAR", 0)))
                .fleets(List.of(fleet("enemy", ship("attacker", attacker, Map.of())),
                        fleet("own", ship("target", target, Map.of())).withStance(TacticalCombatProcessor.STANCE_EVASIVE_RETREAT)))
                .build();
    }

    private SpaceConquestEngine engine(GameState state) {
        var engine = new SpaceConquestEngine(); engine.applyGameState(state); return engine;
    }
    private ShipInstance find(GameState state, String id) {
        return state.fleets().stream().flatMap(f -> f.ships().stream()).filter(s -> s.id().equals(id)).findFirst().orElseThrow();
    }
    private ShipDesign targetDesign(GameState state) {
        return state.shipDesigns().stream().filter(d -> d.id().equals("target")).findFirst().orElseThrow();
    }
    private Fleet own(GameState state) { return FleetOrganization.find(state, "own"); }

    private void assertDisabled(GameState state) {
        var target = find(state, "target"); var design = targetDesign(state);
        assertEquals(1000, target.currentHullHealth());
        for (int mount = 0; mount < 5; mount++) assertEquals(0, target.moduleDamage().condition(mount), "Active mount " + mount);
        assertEquals(1, target.moduleDamage().condition(5), "Passive tank survives");
        assertEquals(0, design.powerProfile(target).fissionKw());
        assertEquals(0, PropulsionDamage.thrust(design, target));
        assertFalse(PropulsionDamage.canBurn(design, target, 1, 1));
        assertEquals(0, SensorModuleCatalog.profile(design, target).rangeKm());
        assertEquals(0, ShipCombatStats.of(design, target).firepower());
        assertEquals(0, ShipCombatPower.round(target, design, 1.0 / 60, true, false).firepower());
        assertEquals(0, ShipRepairProcessor.capacity(state, own(state), target));
    }

    @Test void normalCampaignCombatDisablesActiveSystemsWithoutHullDamageAndTheFollowingDayCannotSelfRepair() {
        var opening = opening(); var engine = engine(opening);
        assertTrue(SensorCoverage.detects(opening, "owner", FleetOrganization.find(opening, "enemy")));
        engine.stepTurn(); var damaged = engine.getGameState();
        assertEquals(1, damaged.turn()); assertFalse(damaged.fleetEngagements().isEmpty());
        assertDisabled(damaged);
        assertFalse(SensorCoverage.detects(damaged, "owner", FleetOrganization.find(damaged, "enemy")));
        assertTrue(damaged.fogOfWarStates().stream().filter(view -> view.empireId().equals("owner"))
                .allMatch(view -> view.detectedFleetIds().isEmpty()));
        var before = find(damaged, "target");
        engine.stepTurn(); var next = engine.getGameState();
        assertDisabled(next);
        var after = find(next, "target");
        assertEquals(before.moduleDamage(), after.moduleDamage());
        assertEquals(before.generatorFuelMassKg(), after.generatorFuelMassKg());
        assertEquals(before.currentFuelKg(), after.currentFuelKg());
        assertEquals(before.storedCargoKg(), after.storedCargoKg());
        assertTrue(after.powerState().lastUnmetEssentialKwh() > 0);
        assertTrue(find(opening, "target").moduleDamage().damageByMount().isEmpty());
    }

    private Map<String, Double> repairMaterials(ShipDesign target) {
        var materials = new HashMap<String, Double>();
        for (String id : target.equippedModuleIds()) {
            var module = ShipModuleDamage.damageableModule(target, id);
            if (module != null) module.materialCostsKg().forEach((material, kg) -> materials.merge(material, kg * 2, Double::sum));
        }
        return Map.copyOf(materials);
    }

    @Test void poweredSupportPaysActualComponentMaterialsAndRestoresSystemsOnLaterEngineTicks() {
        var engine = engine(opening()); engine.stepTurn(); var damaged = engine.getGameState();
        assertDisabled(damaged);
        var modules = new ArrayList<>(Collections.nCopies(6, ShipRepairModuleCatalog.WORKSHOP));
        modules.add(ShipComponentCatalog.FISSION_REACTOR_ID);
        var supportDesign = design("support", "owner", modules);
        var materials = repairMaterials(targetDesign(damaged));
        var support = ship("support", supportDesign, materials);
        var designs = new ArrayList<>(damaged.shipDesigns()); designs.add(supportDesign);
        var ready = damaged.toBuilder().shipDesigns(designs).diplomaticRelations(List.of())
                .fleets(List.of(FleetOrganization.find(damaged, "enemy"), own(damaged).withShips(List.of(find(damaged, "target"), support))))
                .build();
        engine.applyGameState(ready);
        engine.stepTurn(); var first = engine.getGameState();
        assertTrue(find(first, "target").moduleDamage().condition(0) > 0, "External workshop restores disabled workshop first");
        assertEquals(1000, find(first, "target").currentHullHealth());
        assertEquals(Map.of(), find(first, "target").storedCargoKg(), "Repair inputs come from the support ship");
        for (int day = 0; day < 5; day++) engine.stepTurn();
        var restored = engine.getGameState(); var target = find(restored, "target"); var design = targetDesign(restored);
        assertEquals(ShipModuleDamage.empty(), target.moduleDamage());
        assertEquals(1000, target.currentHullHealth()); assertEquals(100, target.currentFuelKg());
        assertTrue(design.powerProfile(target).fissionKw() > 0);
        assertTrue(PropulsionDamage.thrust(design, target) > 0);
        assertTrue(PropulsionDamage.canBurn(design, target, 1, 1));
        assertTrue(SensorModuleCatalog.profile(design, target).rangeKm() > 0);
        assertTrue(SensorCoverage.detects(restored, "owner", FleetOrganization.find(restored, "enemy")));
        assertTrue(restored.fogOfWarStates().stream().filter(view -> view.empireId().equals("owner"))
                .anyMatch(view -> view.detectedFleetIds().contains("enemy")));
        assertEquals(0, target.powerState().lastUnmetEssentialKwh(), 1e-9);
        assertTrue(target.generatorFuelMassKg() < find(damaged, "target").generatorFuelMassKg(), "Restored reactor pays daily operating fuel");
        assertTrue(ShipCombatPower.round(target, design, 1.0 / 60, true, false).firepower() > 0);
        assertTrue(ShipRepairProcessor.capacity(restored, own(restored), target) > 0);
        var remaining = find(restored, "support").storedCargoKg();
        var spent = new HashMap<String, Double>();
        for (int mount = 0; mount < 5; mount++) ShipModuleDamage.damageableModule(design, TARGET_MODULES.get(mount))
                .materialCostsKg().forEach((material, kg) -> spent.merge(material, kg, Double::sum));
        spent.forEach((material, kg) -> assertEquals(materials.get(material) - kg, remaining.getOrDefault(material, 0.0), 1e-6, material));
        assertTrue(find(restored, "support").generatorFuelMassKg() < support.generatorFuelMassKg());
        assertEquals(materials, support.storedCargoKg());
        assertDisabled(damaged);
    }

    @Test void externalRepairStillRequiresProviderPowerAndPhysicalInputsOnTheNormalTick() {
        var source = engine(opening()); source.stepTurn(); var damaged = source.getGameState();
        var supportDesign = design("support", "owner", List.of(ShipRepairModuleCatalog.WORKSHOP, ShipComponentCatalog.FISSION_REACTOR_ID));
        var designs = new ArrayList<>(damaged.shipDesigns()); designs.add(supportDesign);
        var materials = repairMaterials(targetDesign(damaged));
        for (boolean powered : List.of(true, false)) {
            var support = ship("support", supportDesign, powered ? Map.of() : materials);
            if (!powered) support = support.withPowerState(ShipPowerState.empty());
            var state = damaged.toBuilder().shipDesigns(designs).diplomaticRelations(List.of())
                    .fleets(List.of(FleetOrganization.find(damaged, "enemy"),
                            own(damaged).withShips(List.of(find(damaged, "target"), support)))).build();
            var engine = engine(state); engine.stepTurn(); var after = engine.getGameState();
            assertDisabled(after);
            assertEquals(find(damaged, "target").moduleDamage(), find(after, "target").moduleDamage());
            assertEquals(support.storedCargoKg(), find(after, "support").storedCargoKg());
        }
    }
}
