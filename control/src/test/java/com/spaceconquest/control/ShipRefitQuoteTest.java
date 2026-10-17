package com.spaceconquest.control;

import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.ship.*;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShipRefitQuoteTest {
    GameState world() throws Exception {
        var known = new ArrayList<String>();
        for (var technology : DataModelLoader.loadTechnologies()) { known.add(technology.id()); technology.applications().forEach(app -> known.add(app.id())); }
        var state = new SensorModuleTest().world(known); var source = design(state, "old", modules(), 30, List.of());
        var types = new ArrayList<>(source.equippedModuleIds()); types.set(types.indexOf(CombatModuleCatalog.GUN), CombatModuleCatalog.LASER);
        var target = design(state, "laser", types, 30, source.assembly().identities());
        var yard = state.orbitalStations().getFirst(); var module = yard.modules().getFirst().withPayroll(ShipyardWorkCapacity.requiredWorkers(yard.modules().getFirst()), 300);
        yard = new OrbitalStation(yard.id(), yard.name(), yard.systemId(), yard.planetOrbitId(), yard.ownerEntityId(), yard.ownershipType(), yard.totalSlots(), List.of(module),
                Map.of(), 100, 0, 0, 0, 100, 100, "steel", 0, true);
        var stock = new HashMap<String, MarketOrder>(); DataModelLoader.loadMaterials().forEach(material -> stock.put(material.id(), new MarketOrder(material.id(), 1000000, 0, 1, 0)));
        var ship = new ShipInstance("ship", "old", "owner", 800, 0, 0, Map.of()).withPowerState(ShipPowerState.empty())
                .withModuleDamage(new ShipModuleDamage(Map.of(source.equippedModuleIds().indexOf(ShipComponentCatalog.BATTERY_ID), 50.0)));
        return state.toBuilder().orbitalStations(List.of(yard)).shipDesigns(List.of(source, target)).commercialHubs(List.of(new CommercialHub("hub", "yard", 0, 10000000, 10000000, 10, stock)))
                .fleets(List.of(new Fleet("fleet", "Fleet", "owner", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(ship), FleetLocation.at(FleetLocation.Site.docked("yard"))))).build();
    }
    private List<String> modules() { var result = new ArrayList<>(ShipComponentCatalog.workbenchModules("mod_chemical_rocket", false)); result.add(CombatModuleCatalog.GUN); return result; }
    private ShipDesign design(GameState state, String id, List<String> modules, int capacity, List<String> identities) {
        var result = ShipBlueprintFactory.evaluate(state, new ShipDesignSpecification(id, id, "owner", ShipRole.COMBAT_SHIP, "steel", modules, "steel", .5, capacity, identities));
        assertTrue(result.valid(), result.errors().toString()); return result.design();
    }
    private GameState add(GameState state, ShipDesign design) { var designs = new ArrayList<>(state.shipDesigns()); designs.add(design); return state.withShipDesigns(designs); }
    @Test void weaponReplacementBuysOnlyNewEquipmentAndKeepsDamageWithoutSpendingTheQuote() throws Exception {
        var state = world(); double cash = state.empires().getFirst().treasuryCredits(); var stock = state.commercialHubs().getFirst().activeOrders(); var ship = state.fleets().getFirst().ships().getFirst();
        var quote = ShipRefitQuote.inspect(state, "owner", "ship", "laser"); assertTrue(quote.ready(), quote.blockers().toString());
        var change = quote.comparison().components().stream().filter(item -> item.kind() == ShipModelComparison.Kind.REPLACED).findFirst().orElseThrow();
        assertEquals(change.target().production().materialsKg(), quote.comparison().total().materialsKg());
        assertEquals(0, quote.comparison().structure().workHours()); assertEquals(0, quote.comparison().armor().workHours());
        assertEquals(quote.comparison().total().materialsKg().values().stream().mapToDouble(Double::doubleValue).sum(), quote.materialCostCredits(), 1e-6);
        int battery = state.shipDesigns().getLast().equippedModuleIds().indexOf(ShipComponentCatalog.BATTERY_ID); assertEquals(.5, quote.retainedDamage().condition(battery));
        assertEquals(cash, state.empires().getFirst().treasuryCredits()); assertEquals(stock, state.commercialHubs().getFirst().activeOrders());
        assertEquals(ship, state.fleets().getFirst().ships().getFirst()); assertTrue(state.shipConstructionOrders().isEmpty());
    }
    @Test void enlargementCostsMoreStructuralWorkAsTheDegreeOfChangeIncreases() throws Exception {
        var state = world(); var old = state.shipDesigns().getFirst();
        var near = design(state, "near", old.equippedModuleIds(), 45, old.assembly().identities()); var far = design(state, "far", old.equippedModuleIds(), 61, old.assembly().identities());
        state = add(add(state, near), far); var smaller = ShipRefitQuote.inspect(state, "owner", "ship", "near"); var larger = ShipRefitQuote.inspect(state, "owner", "ship", "far");
        assertTrue(smaller.ready(), smaller.blockers().toString()); assertTrue(larger.ready(), larger.blockers().toString());
        assertTrue(larger.comparison().structuralChangeFraction() > smaller.comparison().structuralChangeFraction());
        assertTrue(larger.comparison().structure().workHours() > smaller.comparison().structure().workHours()); assertTrue(larger.workDays() > smaller.workDays());
        assertEquals(far.assembly().frame().materialsKg(), larger.comparison().structure().materialsKg());
        assertTrue(larger.comparison().components().stream().allMatch(item -> item.kind() == ShipModelComparison.Kind.RETAINED));
    }
    @Test void identicalHardwareAcrossIndependentlyNamedModelsIsRetainedAndCostsNoRefit() throws Exception {
        var state = world(); var old = state.shipDesigns().getFirst(); var renamed = design(state, "renamed", old.equippedModuleIds(), 30, List.of());
        var quote = ShipRefitQuote.inspect(add(state, renamed), "owner", "ship", "renamed"); assertTrue(quote.comparison().noChange());
        assertEquals(0, quote.comparison().total().workHours()); assertTrue(quote.comparison().total().materialsKg().isEmpty());
        int battery = renamed.equippedModuleIds().indexOf(ShipComponentCatalog.BATTERY_ID); assertEquals(.5, quote.retainedDamage().condition(battery));
        assertFalse(quote.ready());
    }
    @Test void unavailableFundsStockResearchOrOwnershipRejectFeasibilityWithoutChangingState() throws Exception {
        var state = world(); var owner = state.empires().getFirst();
        var poor = state.withEmpires(List.of(new Empire(owner.id(), owner.name(), owner.raceId(), owner.societyStructure(), 1, owner.corporateTaxRate(), owner.controlledSystemIds(),
                owner.ministries(), owner.systemGovernorAssignments(), owner.unlockedTechIds(), owner.activeShipDesignIds())));
        var unfunded = ShipRefitQuote.inspect(poor, "owner", "ship", "laser"); assertFalse(unfunded.ready()); assertNull(unfunded.materialCostCredits()); assertEquals(1, poor.empires().getFirst().treasuryCredits());
        assertFalse(ShipRefitQuote.inspect(state.withCommercialHubs(List.of()), "owner", "ship", "laser").ready());
        var known = new ArrayList<>(owner.unlockedTechIds()); known.remove(CombatModuleCatalog.application(CombatModuleCatalog.LASER).id());
        var lost = state.withEmpires(List.of(new Empire(owner.id(), owner.name(), owner.raceId(), owner.societyStructure(), owner.treasuryCredits(), owner.corporateTaxRate(), owner.controlledSystemIds(),
                owner.ministries(), owner.systemGovernorAssignments(), known, owner.activeShipDesignIds())));
        assertTrue(ShipRefitQuote.inspect(lost, "owner", "ship", "laser").blockers().stream().anyMatch(reason -> reason.contains("research")));
        assertFalse(ShipRefitQuote.inspect(state, "other", "ship", "laser").ready());
    }
    @Test void cargoAndChargedBatteryRemovalRequireRealUnloadingOrDischarge() throws Exception {
        var state = world(); var old = state.shipDesigns().getFirst(); var types = new ArrayList<>(old.equippedModuleIds()); types.remove("mod_cargo_vault"); types.remove(ShipComponentCatalog.BATTERY_ID);
        var target = design(state, "stripped", types, 30, ShipBlueprintAssembly.identitiesFor(old, types, "stripped")); state = add(state, target);
        var ship = state.fleets().getFirst().ships().getFirst(); var power = new ShipPowerState(Map.of(), "rp1", "uranium", 100, false, 1, 1, 0, 0, 0, 0, 0);
        ship = new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), 800, 0, 0, Map.of("steel", 100.0)).withPowerState(power);
        state = state.withFleets(List.of(state.fleets().getFirst().withShips(List.of(ship)))); var quote = ShipRefitQuote.inspect(state, "owner", "ship", "stripped");
        assertFalse(quote.ready()); assertTrue(quote.blockers().stream().anyMatch(reason -> reason.contains("cargo"))); assertTrue(quote.blockers().stream().anyMatch(reason -> reason.contains("battery")));
        assertEquals(100, state.fleets().getFirst().ships().getFirst().powerState().batteryChargeKwh()); assertEquals(100, state.fleets().getFirst().ships().getFirst().storedCargoKg().get("steel"));
    }
    @Test void incompatibleDriveFuelAndPendingDeploymentBlockRefitAndYardWorkloadIsReported() throws Exception {
        var state = world(); var old = state.shipDesigns().getFirst(); var types = new ArrayList<>(old.equippedModuleIds()); types.set(types.indexOf("mod_chemical_rocket"), "mod_methalox_rocket");
        var target = design(state, "methane", types, 30, ShipBlueprintAssembly.identitiesFor(old, types, "methane")); state = add(state, target);
        var ship = state.fleets().getFirst().ships().getFirst(); ship = new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), 800, 0, 100, Map.of()).withPowerState(ShipPowerState.empty());
        state = state.withFleets(List.of(state.fleets().getFirst().withShips(List.of(ship))));
        assertTrue(ShipRefitQuote.inspect(state, "owner", "ship", "methane").blockers().stream().anyMatch(reason -> reason.contains("propellant"))); assertEquals(100, ship.currentFuelKg());
        var pending = state.toBuilder().shipyardLedger(state.shipyardLedger().deployment("ship", new ShipDeploymentIntent("owner", ShipDeploymentIntent.Kind.DEFENSE, "yard", "", 0, "Pending"))).build();
        assertTrue(ShipRefitQuote.inspect(pending, "owner", "ship", "laser").blockers().stream().anyMatch(reason -> reason.contains("deployment")));
        var queued = state.withShipConstructionOrders(List.of(new ShipConstructionOrder("queued", "owner", "old", "sol", "yard", 100, 1000, Map.of(), Map.of())));
        assertTrue(ShipRefitQuote.inspect(queued, "owner", "ship", "laser").queuedDays() > 0);
    }
    @Test void duplicateExplicitIdentitiesAreReservedBeforeMatchingUnrelatedTargetIds() throws Exception {
        var state = world(); var types = modules(); types.add(ShipComponentCatalog.BATTERY_ID); var source = design(state, "two", types, 30, List.of());
        var ids = new ArrayList<>(source.assembly().identities()); int first = types.indexOf(ShipComponentCatalog.BATTERY_ID), last = types.lastIndexOf(ShipComponentCatalog.BATTERY_ID);
        ids.set(first, "new-model-battery"); var target = design(state, "target", types, 30, ids); var changes = ShipModelComparison.compare(source, target);
        assertEquals(source.assembly().identities().get(last), changes.retainedIdentities().get(target.assembly().identities().get(last)));
        assertEquals(source.assembly().identities().get(first), changes.retainedIdentities().get("new-model-battery"));
        assertThrows(IllegalArgumentException.class, () -> ShipModuleDamage.empty().remap(source, target, Map.of(ids.get(first), ids.get(last), ids.get(last), ids.get(last))));
    }
    @Test void compatibleCorporateClientQuotesPublicYardFeesWithItsOwnFunds() throws Exception {
        var state = world(); var models = state.shipDesigns().stream().map(model -> corporate(model)).toList();
        var client = new Corporation("corp", "Client", "owner", "yard", "TRANSPORT", 1000000, List.of(), List.of("ship"), List.of());
        var ship = new ShipInstance("ship", "old", "corp", 800, 0, 0, Map.of()).withPowerState(ShipPowerState.empty());
        var fleet = new Fleet("corporate", "Corporate", "corp", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(ship), FleetLocation.at(FleetLocation.Site.docked("yard")));
        state = state.toBuilder().corporations(List.of(client)).shipDesigns(models).fleets(List.of(fleet)).build();
        var quote = ShipRefitQuote.inspect(state, "corp", "ship", "laser"); assertTrue(quote.ready(), quote.blockers().toString());
        assertEquals("owner", quote.providerId()); assertTrue(quote.serviceFeeCredits() > 0); assertEquals(0, quote.estimatedYardOperatingCredits());
        assertEquals(1000000, state.corporations().getFirst().liquidCapitalReserves());
        var owner = state.empires().getFirst(); var hive = new Empire(owner.id(), owner.name(), owner.raceId(), "Hive Mind", owner.treasuryCredits(), owner.corporateTaxRate(), owner.controlledSystemIds(),
                owner.ministries(), owner.systemGovernorAssignments(), owner.unlockedTechIds(), owner.activeShipDesignIds());
        assertFalse(ShipRefitQuote.inspect(state.withEmpires(List.of(hive)), "corp", "ship", "laser").ready());
    }
    ShipDesign corporate(ShipDesign model) {
        return new ShipDesign(model.id(), model.name(), "corp", model.role(), model.hullMaterialId(), model.equippedModuleIds(), model.armorMaterialId(), model.armorThicknessCm(),
                model.totalDryMassKg(), model.maxCargoMassKg(), model.fuelCapacityKg(), model.powerBalanceKw(), model.calculatedStructuralIntegrity(), model.minLaunchThrustRequiredN(), model.totalThrustN(),
                model.isValidForLaunch(), true, model.manufacturingProfile(), model.powerProfile(), model.sensorProfile(), model.combatModules(), model.hullFrame(), model.assembly());
    }
    @Test void applicationWorkCoefficientsChangeEffortWithoutChangingEquipmentMaterialBill() throws Exception {
        var state = world(); var old = state.shipDesigns().getFirst(); var larger = design(state, "larger", old.equippedModuleIds(), 61, old.assembly().identities());
        var policy = HullTechnology.refitTechnology(larger.hullFrame()); var original = ShipModelComparison.compare(old, larger, policy);
        var adjusted = new ShipRefitTechnology(policy.removalWorkFraction(), policy.installationWorkMultiplier(), policy.structuralWorkMultiplier() * 2,
                policy.structuralGrowthExponent(), policy.armorRemovalWorkFraction(), policy.systemsReconnectionMultiplier());
        var changed = ShipModelComparison.compare(old, larger, adjusted); assertTrue(changed.structure().workHours() > original.structure().workHours());
        assertEquals(original.total().materialsKg(), changed.total().materialsKg()); assertEquals(original.retainedIdentities(), changed.retainedIdentities());
    }
    @Test void materialsOnAReservedCarrierCannotFundAnotherRefit() throws Exception {
        var state = world(); var source = state.shipDesigns().getFirst();
        var cargo = design(state, "cargo", source.equippedModuleIds(), 30, List.of());
        cargo = new ShipDesign(cargo.id(), cargo.name(), cargo.ownerEntityId(), ShipRole.CARGO_TRANSPORT, cargo.hullMaterialId(), cargo.equippedModuleIds(), cargo.armorMaterialId(), cargo.armorThicknessCm(),
                cargo.totalDryMassKg(), cargo.maxCargoMassKg(), cargo.fuelCapacityKg(), cargo.powerBalanceKw(), cargo.calculatedStructuralIntegrity(), cargo.minLaunchThrustRequiredN(), cargo.totalThrustN(),
                cargo.isValidForLaunch(), false, cargo.manufacturingProfile(), cargo.powerProfile(), cargo.sensorProfile(), cargo.combatModules(), cargo.hullFrame(), cargo.assembly());
        var bill = ShipModelComparison.compare(source, state.shipDesigns().getLast()).total().materialsKg();
        var carrier = new Fleet("carrier", "Carrier", "owner", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(new ShipInstance("cargo-ship", "cargo", "owner", 1000, 0, 0, bill)), FleetLocation.at(FleetLocation.Site.docked("yard")));
        state = add(state.withCommercialHubs(List.of()).withFleets(List.of(state.fleets().getFirst(), carrier)), cargo);
        var available = ShipRefitQuote.inspect(state, "owner", "ship", "laser"); assertTrue(available.ready(), available.blockers().toString()); assertEquals(0, available.materialCostCredits());
        var reserved = state.toBuilder().shipyardLedger(state.shipyardLedger().deployment("cargo-ship", new ShipDeploymentIntent("owner", ShipDeploymentIntent.Kind.TRADE, "", "", 0, "Pending service"))).build();
        assertFalse(ShipRefitQuote.inspect(reserved, "owner", "ship", "laser").ready());
        assertEquals(bill, reserved.fleets().getLast().ships().getFirst().storedCargoKg());
    }
}
