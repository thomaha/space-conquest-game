package com.spaceconquest.control;

import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.ship.*;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ShipAssemblyTest {
    @TempDir Path directory;
    private GameState world() throws Exception {
        var known = new ArrayList<String>();
        for (var technology : DataModelLoader.loadTechnologies()) { known.add(technology.id()); technology.applications().forEach(app -> known.add(app.id())); }
        return new SensorModuleTest().world(known);
    }
    private List<String> modules() {
        var modules = new ArrayList<>(ShipComponentCatalog.workbenchModules("mod_chemical_rocket", false));
        modules.add(ShipComponentCatalog.BATTERY_ID); modules.add(CombatModuleCatalog.GUN); return modules;
    }
    private ShipDesign design(GameState state, String id, List<String> modules, List<String> identities) {
        var evaluated = ShipBlueprintFactory.evaluate(state, new ShipDesignSpecification(id, id, "owner", ShipRole.COMBAT_SHIP,
                "steel", modules, "steel", .5, 30, identities));
        assertTrue(evaluated.valid(), evaluated.errors().toString()); return evaluated.design();
    }
    @Test void repeatedComponentsKeepIndividualDamageAfterExplicitReordering() throws Exception {
        var state = world(); var source = design(state, "old", modules(), List.of());
        var ids = new ArrayList<>(source.assembly().identities()); var types = new ArrayList<>(source.equippedModuleIds());
        assertEquals(ids.size(), new HashSet<>(ids).size()); Collections.reverse(ids); Collections.reverse(types);
        var revised = design(state, "reordered", types, ids);
        int firstBattery = source.equippedModuleIds().indexOf(ShipComponentCatalog.BATTERY_ID);
        int secondBattery = source.equippedModuleIds().lastIndexOf(ShipComponentCatalog.BATTERY_ID);
        var damage = new ShipModuleDamage(Map.of(firstBattery, 90.0, secondBattery, 40.0));
        var remapped = damage.remap(source, revised);
        assertEquals(90, remapped.damageByMount().get(ids.indexOf(source.assembly().identities().get(firstBattery))));
        assertEquals(40, remapped.damageByMount().get(ids.indexOf(source.assembly().identities().get(secondBattery))));
        assertEquals(source.assembly().frame(), revised.assembly().frame()); assertEquals(source.assembly().armor(), revised.assembly().armor());
        assertEquals(source.assembly().total(), revised.assembly().total());
    }
    @Test void weaponSwapRetainsStructureAndArmorReceiptsAndDoesNotHealUnchangedEquipment() throws Exception {
        var state = world(); var source = design(state, "old", modules(), List.of());
        var types = new ArrayList<>(source.equippedModuleIds()); int gun = types.indexOf(CombatModuleCatalog.GUN); types.set(gun, CombatModuleCatalog.LASER);
        var revised = design(state, "new", types, source.assembly().identities());
        assertEquals(source.assembly().frame(), revised.assembly().frame()); assertEquals(source.assembly().armor(), revised.assembly().armor());
        var laser = revised.assembly().installations().get(gun);
        assertEquals(CombatModuleCatalog.module(CombatModuleCatalog.LASER).materialCostsKg(), laser.production().materialsKg());
        int battery = types.indexOf(ShipComponentCatalog.BATTERY_ID);
        var damage = new ShipModuleDamage(Map.of(gun, 100.0, battery, 75.0)).remap(source, revised);
        assertEquals(1, damage.condition(gun)); assertEquals(.25, damage.condition(battery));
        assertEquals(source.assembly().installations().get(battery).id(), revised.assembly().installations().get(battery).id());
    }
    @Test void researchCostChangesOnlyItsComponentReceiptAndOldEstimateStaysFrozen() throws Exception {
        var state = world(); var old = design(state, "old", modules(), List.of()); state = state.withShipDesigns(List.of(old));
        var initialBill = ShipConstructionRequirements.estimate(old);
        String application = CombatModuleCatalog.application(CombatModuleCatalog.GUN).id();
        var researched = new SelectOptimizationPathCommand("owner", application, "PATH_B").apply(state);
        var revised = design(researched, "new", old.equippedModuleIds(), old.assembly().identities());
        assertEquals(initialBill, ShipConstructionRequirements.estimate(researched.shipDesigns().getFirst()));
        assertEquals(old.assembly().frame(), revised.assembly().frame()); assertEquals(old.assembly().armor(), revised.assembly().armor());
        int gun = old.equippedModuleIds().indexOf(CombatModuleCatalog.GUN);
        var oldReceipt = old.assembly().installations().get(gun).production(); var newReceipt = revised.assembly().installations().get(gun).production();
        assertTrue(newReceipt.workHours() < oldReceipt.workHours());
        int battery = old.equippedModuleIds().indexOf(ShipComponentCatalog.BATTERY_ID);
        var retainedDamage = new ShipModuleDamage(Map.of(gun, 90.0, battery, 60.0)).remap(old, revised);
        assertEquals(1, retainedDamage.condition(gun)); assertEquals(.4, retainedDamage.condition(battery), 1e-9);
        var oldTotal = old.assembly().total(); var newTotal = revised.assembly().total();
        for (String material : oldTotal.materialsKg().keySet()) assertEquals(newReceipt.materialsKg().getOrDefault(material, 0.0) - oldReceipt.materialsKg().getOrDefault(material, 0.0),
                newTotal.materialsKg().getOrDefault(material, 0.0) - oldTotal.materialsKg().get(material), 1e-6);
    }
    @Test void capturedLegacyWeaponReceiptIsUsedInsteadOfTheLiveCatalogReceipt() throws Exception {
        var source = design(world(), "source", modules(), List.of()); var gun = CombatModuleCatalog.module(CombatModuleCatalog.GUN);
        var materials = new HashMap<>(gun.materialCostsKg()); materials.merge("refined_copper", 37.0, Double::sum);
        var captured = new ShipModule(gun.id(), gun.name(), gun.slotSize(), gun.slotCost(), gun.dryMassKg(), gun.powerDrawKw(), gun.powerOutputKw(),
                gun.thrustOutputN(), gun.complexityLevel(), materials, gun.operationalStats(), gun.ammunitionId());
        var baseline = legacy(source, Map.of(gun.id(), gun)); var changed = legacy(source, Map.of(gun.id(), captured));
        assertEquals(37, ShipConstructionRequirements.estimate(changed).materialsKg().get("refined_copper")
                - ShipConstructionRequirements.estimate(baseline).materialsKg().get("refined_copper"), 1e-6);
    }
    private ShipDesign legacy(ShipDesign source, Map<String, ShipModule> captured) {
        return new ShipDesign(source.id(), source.name(), source.ownerEntityId(), source.role(), source.hullMaterialId(), source.equippedModuleIds(),
                source.armorMaterialId(), source.armorThicknessCm(), source.totalDryMassKg(), source.maxCargoMassKg(), source.fuelCapacityKg(), source.powerBalanceKw(),
                source.calculatedStructuralIntegrity(), source.minLaunchThrustRequiredN(), source.totalThrustN(), source.isValidForLaunch(), false,
                source.manufacturingProfile(), source.powerProfile(), source.sensorProfile(), captured);
    }
    @Test void partialPaidBuildAndItsCapturedReceiptsSurviveDiskReload() throws Exception {
        var state = world(); var blueprint = design(state, "build", modules(), List.of()); var yard = state.orbitalStations().getFirst();
        var module = yard.modules().getFirst().withPayroll(ShipyardWorkCapacity.requiredWorkers(yard.modules().getFirst()), 0);
        yard = new OrbitalStation(yard.id(), yard.name(), yard.systemId(), yard.planetOrbitId(), yard.ownerEntityId(), yard.ownershipType(), yard.totalSlots(), List.of(module),
                Map.of(), 100, 0, 0, 0, 100, 100, "steel", 0, true);
        var stock = new HashMap<String, MarketOrder>(); var bill = ShipConstructionRequirements.estimate(blueprint);
        bill.materialsKg().forEach((id, kg) -> stock.put(id, new MarketOrder(id, kg * 2, 0, 1, 0)));
        state = state.toBuilder().shipDesigns(List.of(blueprint)).orbitalStations(List.of(yard)).commercialHubs(List.of(new CommercialHub("hub", "yard", 0, 10000000, 10000000, 10, stock))).build();
        var command = new QueueShipBuildCommand("owner", "build", "sol"); assertTrue(command.validate(state)); var queued = command.apply(state);
        assertEquals(bill.materialsKg(), queued.shipConstructionOrders().getFirst().requiredMaterialsKg());
        var partial = new ShipConstructionProcessor().process(queued); assertFalse(partial.shipConstructionOrders().isEmpty());
        assertTrue(partial.shipConstructionOrders().getFirst().accumulatedWorkHours() > 0); assertTrue(partial.empires().getFirst().treasuryCredits() < state.empires().getFirst().treasuryCredits());
        var file = directory.resolve("assembly.scsave").toFile(); var saves = new SaveGameManager(directory); saves.save(file, partial, 1, GameClock.START_TIME.toString());
        var restored = saves.load(file).toGameState(0, partial.status()); assertEquals(blueprint.assembly(), restored.shipDesigns().getFirst().assembly());
        assertEquals(partial.shipConstructionOrders(), restored.shipConstructionOrders());
        var complete = restored;
        for (int day = 1; day < 30 && !complete.shipConstructionOrders().isEmpty(); day++) complete = new ShipConstructionProcessor().process(complete.withTurn(day));
        assertTrue(complete.shipConstructionOrders().isEmpty()); assertEquals("build", complete.fleets().getFirst().ships().getFirst().designId());
        assertEquals(blueprint.assembly(), complete.shipDesigns().getFirst().assembly());
    }
    @Test void malformedIdentitiesAreRejectedWithoutRegisteringAFreeBlueprint() throws Exception {
        var state = world(); var modules = modules(); var ids = Collections.nCopies(modules.size(), "duplicate");
        var command = new DesignShipCommand(new ShipDesignSpecification("bad", "Bad", "owner", ShipRole.COMBAT_SHIP, "steel", modules, "steel", .5, 30, ids));
        assertFalse(command.validate(state)); assertSame(state, command.apply(state));
        var shortIds = new ShipDesignSpecification("bad", "Bad", "owner", ShipRole.COMBAT_SHIP, "steel", modules, "steel", .5, 30, List.of("one"));
        assertFalse(ShipBlueprintFactory.evaluate(state, shortIds).valid());
    }
    @Test void enlargingHullChangesStructuralReceiptsWhileKeepingUnchangedInstallations() throws Exception {
        var state = world(); var source = design(state, "old", modules(), List.of());
        var specification = new ShipDesignSpecification("larger", "Larger", "owner", ShipRole.COMBAT_SHIP, "steel", source.equippedModuleIds(),
                "steel", .5, 61, ShipBlueprintAssembly.identitiesFor(source, source.equippedModuleIds(), "larger"));
        var evaluation = ShipBlueprintFactory.evaluate(state, specification); assertTrue(evaluation.valid(), evaluation.errors().toString());
        var enlarged = evaluation.design(); assertEquals(source.assembly().installations(), enlarged.assembly().installations());
        assertTrue(enlarged.assembly().frame().materialsKg().get("steel") > source.assembly().frame().materialsKg().get("steel"));
        assertTrue(enlarged.assembly().frame().workHours() > source.assembly().frame().workHours());
        assertTrue(enlarged.assembly().armor().workHours() > source.assembly().armor().workHours());
    }
    @Test void structuralCostRefinementIsDetectedWithoutChangingExistingFrameOrEquipment() throws Exception {
        var state = world(); var old = design(state, "old", modules(), List.of()); state = state.withShipDesigns(List.of(old));
        var improved = new SelectOptimizationPathCommand("owner", old.hullFrame().applicationId(), "PATH_B").apply(state);
        assertTrue(ShipApplicationProduction.developmentChanged(improved, old));
        var revised = design(improved, "new", old.equippedModuleIds(), old.assembly().identities());
        assertEquals(old.hullFrame(), revised.hullFrame()); assertEquals(old.assembly().installations(), revised.assembly().installations());
        assertTrue(revised.assembly().frame().workHours() < old.assembly().frame().workHours());
        assertEquals(old, improved.shipDesigns().getFirst());
    }
}
