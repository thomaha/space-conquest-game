package com.spaceconquest.control.command;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.combat.*;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AuxiliaryConstructionTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path saveDirectory;
    private final String chassis = "strike_craft_chassis", emplacement = "surface_weapon_emplacements";
    private String weaponApp() { return CombatModuleCatalog.application(CombatModuleCatalog.MASS_DRIVER).id(); }
    private BuildAuxiliaryWeaponCommand wing(int units) { return new BuildAuxiliaryWeaponCommand("own", "sol", "earth", "carrier", chassis, weaponApp(), units); }
    private BuildAuxiliaryWeaponCommand battery() { return new BuildAuxiliaryWeaponCommand("own", "sol", "earth", null, emplacement, weaponApp(), 1); }
    GameState world() throws Exception {
        var technologies = DataModelLoader.loadTechnologies(); var unlocked = new ArrayList<String>();
        technologies.forEach(t -> { unlocked.add(t.id()); t.applications().forEach(a -> unlocked.add(a.id())); });
        unlocked.remove(ShipyardWorkCapacity.TECH_AUTOMATED_ASSEMBLY);
        var empire = new Empire("own", "Own", "human", "Individualist", 1e9, 0, List.of("sol"), List.of(), Map.of(), unlocked, List.of());
        var earth = new Planet("earth", "Earth", "", 5.97e24, 9.81, 149600000, 0, 12742, "terrestrial", "", true, 0, List.of(), List.of(), List.of());
        var system = new SolarSystem("sol", "Sol", "", 0, 0, 0, 1.989e30, 1392700, "yellow", List.of(earth), List.of());
        var design = new ShipDesign("carrier-design", "Carrier", "own", ShipRole.CARRIER_SHIP, "steel", List.of("mod_carrier_hangar"),
                "steel", 0, 10000, 1000, 0, 0, 100, 0, 0, false, false);
        var carrier = new ShipInstance("carrier", design.id(), "own", 1000, 0, 0, Map.of());
        var fleet = new Fleet("fleet", "Fleet", "own", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(carrier), FleetLocation.at(FleetLocation.Site.surface("earth")));
        var orders = new HashMap<String, MarketOrder>();
        DataModelLoader.loadMaterials().forEach(m -> orders.put(m.id(), new MarketOrder(m.id(), 1e6, 0, 1, 0)));
        var hub = new CommercialHub("hub", "earth", 0, 1e9, 1e8, 10, orders);
        var yard = new IndustrialFacility("yard", "earth", "surface_shipyard", "own", "PUBLIC_STATE", 4, 100, "industrial_worker", false, 0);
        return GameState.builder().empires(List.of(empire)).solarSystems(List.of(system)).shipDesigns(List.of(design)).fleets(List.of(fleet))
                .commercialHubs(List.of(hub)).industrialFacilities(List.of(yard)).build();
    }
    @Test void queuedCraftRequireResearchAHostAndReservedHangarCapacity() throws Exception {
        var state = world(); assertTrue(wing(1).validate(state)); assertFalse(wing(100).validate(state));
        var queue = new CommandQueue(); int capacity = AuxiliaryApplicationCatalog.hangarCapacity(state, state.fleets().getFirst(), state.fleets().getFirst().ships().getFirst());
        var first = queue.submitTracked(wing(capacity)); var second = queue.submitTracked(wing(1)); var queued = queue.drainAndExecute(state);
        assertEquals(CommandOutcome.EXECUTED, first.join()); assertEquals(CommandOutcome.REJECTED, second.join());
        assertTrue(queued.auxiliaryForces().wings().isEmpty()); assertEquals(1, queued.auxiliaryForces().orders().size());
        var emptyResearch = new Empire("own", "Own", "human", "Individualist", 1e9, 0, List.of("sol"), List.of(), Map.of(), List.of(), List.of());
        assertFalse(wing(1).validate(state.withEmpires(List.of(emptyResearch))));
        assertFalse(wing(1).validate(state.withFleets(List.of())));
        var damaged = state.fleets().getFirst().ships().getFirst().withModuleDamage(new ShipModuleDamage(Map.of(0, 100.0)));
        assertFalse(wing(1).validate(state.withFleets(List.of(state.fleets().getFirst().withShips(List.of(damaged))))));
    }
    @Test void assemblyRequiresPaidWorkAndMaterialsAndStartsWithEmptySupplies() throws Exception {
        var state = wing(1).apply(world()); var processor = new ShipConstructionProcessor();
        assertEquals(0, processor.process(state, Map.of()).auxiliaryForces().orders().getFirst().completedHours());
        var dry = state.withCommercialHubs(List.of());
        assertEquals(0, processor.process(dry, Map.of("yard", 100)).auxiliaryForces().orders().getFirst().completedHours());
        var receipt = state.auxiliaryForces().orders().getFirst(); var result = state;
        for (int day = 0; day < 100 && !result.auxiliaryForces().orders().isEmpty(); day++) result = processor.process(result, Map.of("yard", 100));
        assertTrue(result.auxiliaryForces().orders().isEmpty()); assertEquals(1, result.auxiliaryForces().wings().size());
        var built = result.auxiliaryForces().wings().getFirst(); assertEquals(receipt.hardware(), built.hardware());
        assertEquals(receipt.weapon(), built.weapon()); assertEquals(AuxiliaryWeaponStores.empty(), built.stores());
        assertEquals(receipt.hardware().value("hullHealth"), built.hullPerCraft());
        double paid = state.empires().getFirst().treasuryCredits() - result.empires().getFirst().treasuryCredits();
        assertEquals(receipt.requiredKg().values().stream().mapToDouble(Double::doubleValue).sum(), paid, 1e-5);
    }
    @Test void auxiliaryAssemblySharesDailyCapacityWithFullShipsAndSurfaceOrdersReserveSlots() throws Exception {
        var state = wing(1).apply(world());
        var shipOrder = new ShipConstructionOrder("full", "own", "carrier-design", "sol", "earth", 0, 10000, Map.of("steel", 10000.0), Map.of());
        var result = new ShipConstructionProcessor().process(state.withShipConstructionOrders(List.of(shipOrder)), Map.of("yard", 100));
        assertTrue(result.shipConstructionOrders().getFirst().accumulatedWorkHours() > 0);
        assertEquals(0, result.auxiliaryForces().orders().getFirst().completedHours());
        var batteries = world(); int slots = (int) AuxiliaryApplicationCatalog.baseline("BATTERY").value("bodyDefenseSlots");
        for (int i = 0; i < slots; i++) { assertTrue(battery().validate(batteries)); batteries = battery().apply(batteries); }
        assertFalse(battery().validate(batteries));
        var saved = SaveGame.fromGameState(batteries, "now", 1, GameClock.START_TIME.toString());
        assertEquals(batteries.auxiliaryForces().orders(), saved.toGameState(0, "STOPPED").auxiliaryForces().orders());
        var manager = new SaveGameManager(saveDirectory); var file = manager.save("orders", batteries, 1, GameClock.START_TIME.toString());
        assertEquals(batteries.auxiliaryForces(), manager.load(file.toFile()).toGameState(0, "STOPPED").auxiliaryForces());
    }
    @Test void developmentChangesNewReceiptsWhileQueuedHardwareAndStorageLimitsRemainCaptured() throws Exception {
        var state = wing(1).apply(world()); var captured = state.auxiliaryForces().orders().getFirst();
        var upgraded = state.withApplicationOptimizations(List.of(new com.spaceconquest.engine.technology.ApplicationOptimization("own", chassis,
                "PERFORMANCE", new com.spaceconquest.engine.technology.ResearchVarianceResult("TEST", 2, .5, 0))));
        var fresh = AuxiliaryConstruction.order(upgraded, "own", "sol", "earth", "carrier", chassis, weaponApp(), 1);
        assertEquals(2 * captured.hardware().value("energyCapacityKwh"), fresh.hardware().value("energyCapacityKwh"));
        assertEquals(captured.hardware(), upgraded.auxiliaryForces().orders().getFirst().hardware());
        assertTrue(fresh.requiredHours() < captured.requiredHours()); assertEquals(chassis, fresh.hardware().applicationId());
        assertThrows(IllegalArgumentException.class, () -> captured.hardware().checkStores(new AuxiliaryWeaponStores(Map.of(),
                captured.hardware().value("energyCapacityKwh") + 1), 1));
        assertThrows(IllegalArgumentException.class, () -> captured.hardware().checkStores(new AuxiliaryWeaponStores(Map.of("round",
                captured.hardware().value("magazineKg") + 1), 0), 1));
        assertThrows(UnsupportedOperationException.class, () -> captured.hardware().stats().clear());
    }
}
