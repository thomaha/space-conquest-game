package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.MarketAccount;
import com.spaceconquest.engine.macrostructure.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BaseRepairProcessorTest {
    @TempDir Path directory;
    private Empire empire(String owner, double cash) {
        return new Empire(owner, owner, "human", "Individualist", cash, 0, List.of("sol"), List.of(), Map.of(), List.of(), List.of());
    }
    private ShipDesign design(String owner) {
        return new ShipDesign("design", "Design", owner, ShipRole.COMBAT_SHIP, "steel",
                List.of(CombatModuleCatalog.GUN, CombatModuleCatalog.SHIELD, ShipRepairModuleCatalog.WORKSHOP), "steel", 0,
                1000, 1000, 1000, 100, 1, 0, 1000, true, false);
    }
    private ShipInstance ship(String id, String owner, double hull, Map<Integer, Double> damage) {
        return new ShipInstance(id, "design", owner, hull, 0, 100, Map.of("steel", 20.0))
                .withModuleDamage(new ShipModuleDamage(damage));
    }
    private Fleet fleet(String id, ShipInstance... ships) {
        return new Fleet(id, id, ships[0].ownerEntityId(), "sol", "", 0, 0, 0, false, "PASSIVE", List.of(ships),
                FleetLocation.at(FleetLocation.Site.docked("port")));
    }
    private OrbitalStation station(int workers, boolean online, double generation) {
        var workshop = BaseRepairCatalog.module();
        workshop = new StationModule(workshop.id(), workshop.name(), workshop.type(), workshop.slotSize(), workshop.dryMassKg(),
                workshop.powerDrawKw(), 0, Map.of(), workshop.workforceProfessionId(), workshop.requiredWorkers(), online).withPayroll(workers, 0);
        return new OrbitalStation("port", "Port", "sol", "earth", "owner", "PUBLIC_STATE", 30, List.of(workshop),
                Map.of(), generation, 50, 0, 0, 100, 100, "steel", 0, true);
    }
    private GameState world(Fleet... fleets) {
        var orders = Map.of("steel", new MarketOrder("steel", 200, 0, 1, 0),
                "refined_titanium", new MarketOrder("refined_titanium", 50, 0, 4, 0),
                "superconducting_cuprates", new MarketOrder("superconducting_cuprates", 25, 0, 2, 0),
                "refined_copper", new MarketOrder("refined_copper", 50, 0, 2, 0),
                "silicon", new MarketOrder("silicon", 50, 0, 2, 0));
        return GameState.builder().empires(List.of(empire("owner", 1000))).shipDesigns(List.of(design("owner")))
                .orbitalStations(List.of(station(2, true, 100))).fleets(List.of(fleets))
                .commercialHubs(List.of(new CommercialHub("hub", "port", 0, 10000, 375, 10, orders))).build();
    }
    private ShipInstance find(GameState state, String id) {
        return state.fleets().stream().flatMap(fleet -> fleet.ships().stream()).filter(ship -> id.equals(ship.id())).findFirst().orElseThrow();
    }
    private double sales(GameState state) { return state.marketAccounts().stream().mapToDouble(MarketAccount::unsettledSalesCredits).sum(); }
    private double stock(GameState state, String material) { return state.commercialHubs().getFirst().activeOrders().get(material).supplyKg(); }

    @Test void stationCapacityIsSharedAcrossFleetsAndEveryIncrementPaysForMaterialsAndLabor() {
        var first = ship("first", "owner", 1000, Map.of(0, 70.0)); var second = ship("second", "owner", 1000, Map.of(0, 30.0));
        var initial = world(fleet("first", first), fleet("second", second));
        var after = BaseRepairProcessor.advanceDay(initial);
        assertEquals(20, find(after, "first").moduleDamage().damageByMount().get(0));
        assertEquals(second.moduleDamage(), find(after, "second").moduleDamage());
        assertEquals(150, stock(after, "steel")); assertEquals(850, after.empires().getFirst().treasuryCredits());
        assertEquals(150, sales(after)); assertEquals(325, after.commercialHubs().getFirst().currentStoredWeightKg());
        assertEquals(first.storedCargoKg(), find(after, "first").storedCargoKg()); assertEquals(100, find(after, "first").currentFuelKg());
        var finished = BaseRepairProcessor.advanceDay(after);
        assertEquals(ShipModuleDamage.empty(), find(finished, "first").moduleDamage());
        assertEquals(ShipModuleDamage.empty(), find(finished, "second").moduleDamage());
        assertEquals(700, finished.empires().getFirst().treasuryCredits()); assertEquals(300, sales(finished));
        assertEquals(initial.fleets().getFirst().ships().getFirst().moduleDamage(), first.moduleDamage());
    }

    @Test void baseUsesWorkshopPriorityAndPaysTheExactRecipeBeforeEarlierWeaponDamage() {
        var initial = world(fleet("fleet", ship("target", "owner", 1000, Map.of(0, 10.0, 2, 100.0))));
        var status = FleetRepairStatus.inspect(initial, initial.fleets().getFirst());
        assertEquals(50, status.base().quotedPoints());
        assertEquals(325, status.base().quotedCredits());
        assertEquals(1000, initial.empires().getFirst().treasuryCredits());
        var after = BaseRepairProcessor.advanceDay(initial); var repaired = find(after, "target");
        assertEquals(10, repaired.moduleDamage().damageByMount().get(0));
        assertEquals(50, repaired.moduleDamage().damageByMount().get(2));
        assertEquals(75, stock(after, "steel"));
        assertEquals(25, stock(after, "refined_copper"));
        assertEquals(25, stock(after, "silicon"));
        assertEquals(675, after.empires().getFirst().treasuryCredits());
        assertEquals(325, sales(after));
        assertEquals(0, repaired.currentShieldHealth());
    }

    @Test void statusExplainsMissingLocalStockAndFundsUsingTheSamePaymentQuote() {
        var initial = world(fleet("fleet", ship("target", "owner", 1000, Map.of(0, 10.0))));
        var poor = initial.withEmpires(List.of(empire("owner", 0)));
        var status = FleetRepairStatus.inspect(poor, poor.fleets().getFirst());
        assertEquals(0, status.base().quotedPoints());
        assertTrue(status.base().blockers().contains("Insufficient repair funds"));
        var hub = initial.commercialHubs().getFirst(); var orders = new HashMap<>(hub.activeOrders());
        orders.put("steel", new MarketOrder("steel", 0, 0, 1, 0));
        var empty = initial.withCommercialHubs(List.of(hub.withInventory(0, hub.currentStoredWeightKg(), orders)));
        status = FleetRepairStatus.inspect(empty, empty.fleets().getFirst());
        assertEquals(0, status.base().quotedPoints());
        assertEquals(Map.of("steel", 10.0), status.base().shortages());
        assertTrue(status.base().blockers().getFirst().contains("Missing local material"));
        assertEquals(empty.fleets(), BaseRepairProcessor.advanceDay(empty).fleets());
    }

    @Test void materialsAndLaborMustBeJointlyAffordableAndTheScarcestIngredientBoundsWork() {
        var shielded = ship("shielded", "owner", 1000, Map.of(1, 10.0));
        var initial = world(fleet("fleet", shielded)).withEmpires(List.of(empire("owner", 7)));
        var after = BaseRepairProcessor.advanceDay(initial);
        assertEquals(9, find(after, "shielded").moduleDamage().damageByMount().get(1));
        assertEquals(0, after.empires().getFirst().treasuryCredits()); assertEquals(7, sales(after));
        assertEquals(49, stock(after, "refined_titanium")); assertEquals(24.5, stock(after, "superconducting_cuprates"));
        assertEquals(0, find(after, "shielded").currentShieldHealth());
        assertEquals(after.fleets(), BaseRepairProcessor.advanceDay(after).fleets());
        var orders = new HashMap<>(initial.commercialHubs().getFirst().activeOrders());
        orders.put("refined_titanium", new MarketOrder("refined_titanium", .5, 0, 4, 0));
        initial = world(fleet("fleet", shielded)).withCommercialHubs(List.of(initial.commercialHubs().getFirst().withInventory(0, 325.5, orders)));
        after = BaseRepairProcessor.advanceDay(initial);
        assertEquals(9.5, find(after, "shielded").moduleDamage().damageByMount().get(1)); assertEquals(3.5, sales(after));
    }

    @Test void restoresHullAndDisabledWorkshopWithoutCreatingShieldChargeOrExtraWork() {
        var target = ship("target", "owner", 990, Map.of(0, 5.0, 2, 20.0));
        var after = BaseRepairProcessor.advanceDay(world(fleet("fleet", target)));
        assertEquals(1000, find(after, "target").currentHullHealth());
        assertEquals(ShipModuleDamage.empty(), find(after, "target").moduleDamage());
        // Gun: 5 kg steel. Workshop: 50 kg steel plus 10 kg each copper and silicon. Hull: 10 kg steel.
        assertEquals(135, stock(after, "steel")); assertEquals(40, stock(after, "refined_copper")); assertEquals(40, stock(after, "silicon"));
        assertEquals(175, sales(after)); assertEquals(825, after.empires().getFirst().treasuryCredits());
        assertEquals(0, find(after, "target").currentShieldHealth());
    }

    @Test void offlineUnstaffedUnpoweredAndMissingRecipeOrHubCannotChargeOrRepair() {
        var initial = world(fleet("fleet", ship("target", "owner", 1000, Map.of(1, 10.0))));
        var missing = new HashMap<>(initial.commercialHubs().getFirst().activeOrders()); missing.remove("refined_titanium");
        for (var blocked : List.of(initial.withOrbitalStations(List.of(station(2, false, 100))),
                initial.withOrbitalStations(List.of(station(0, true, 100))), initial.withOrbitalStations(List.of(station(2, true, 0))),
                initial.withCommercialHubs(List.of()), initial.withCommercialHubs(List.of(initial.commercialHubs().getFirst().withInventory(0, 325, missing))))) {
            var after = BaseRepairProcessor.advanceDay(blocked);
            assertEquals(blocked.fleets(), after.fleets()); assertEquals(blocked.empires(), after.empires());
            assertEquals(blocked.commercialHubs(), after.commercialHubs()); assertEquals(blocked.marketAccounts(), after.marketAccounts());
        }
        var understaffed = BaseRepairProcessor.advanceDay(world(fleet("fleet", ship("target", "owner", 1000, Map.of(0, 50.0))))
                .withOrbitalStations(List.of(station(1, true, 100))));
        assertEquals(25, find(understaffed, "target").moduleDamage().damageByMount().get(0));
    }

    @Test void requiresPhysicalDockingAuthorizedOwnershipAndALivingShipWithAKnownDesign() {
        var ship = ship("target", "owner", 1000, Map.of(0, 50.0)); var fleet = fleet("fleet", ship);
        var initial = world(fleet);
        var foreign = fleet("foreign", ship("target", "foreign", 1000, Map.of(0, 50.0)));
        for (var blocked : List.of(initial.withFleets(List.of(fleet.withLocation(FleetLocation.at(FleetLocation.Site.orbit("earth"))))),
                initial.withFleets(List.of(fleet.withLocation(fleet.location().depart(FleetLocation.Site.orbit("earth"), 2)))),
                initial.withFleets(List.of(fleet("dead", ship("target", "owner", 0, Map.of(0, 50.0))))),
                initial.withShipDesigns(List.of()), initial.withFleets(List.of(foreign)).withEmpires(List.of(empire("owner", 1000), empire("foreign", 1000))))) {
            var after = BaseRepairProcessor.advanceDay(blocked);
            assertEquals(blocked.fleets(), after.fleets()); assertEquals(blocked.empires(), after.empires()); assertEquals(0, sales(after));
        }
    }

    @Test void corporateShipsUseTheirOwnFundsAndEmpireAffiliationWithoutBlueprintSharing() {
        var initial = world(fleet("corp", ship("target", "corp", 1000, Map.of(0, 10.0))))
                .withShipDesigns(List.of(design("corp"))).withCorporations(List.of(new Corporation("corp", "Corp", "owner", "port", "GENERAL",
                        30, List.of(), List.of("target"), List.of())));
        var after = BaseRepairProcessor.advanceDay(initial);
        assertEquals(ShipModuleDamage.empty(), find(after, "target").moduleDamage()); assertEquals(0, after.corporations().getFirst().liquidCapitalReserves());
        assertEquals(1000, after.empires().getFirst().treasuryCredits()); assertEquals(30, sales(after));
        assertEquals(initial.shipDesigns(), after.shipDesigns());
    }

    @Test void unfinishedRepairsResumeAfterReloadAndDepartureStopsFurtherCharges() throws Exception {
        var partial = BaseRepairProcessor.advanceDay(world(fleet("fleet", ship("target", "owner", 1000, Map.of(0, 70.0)))));
        var manager = new SaveGameManager(directory); var file = directory.resolve("base-repair.scsave").toFile();
        manager.save(file, partial, 1, "2027-01-01T08:00:00"); var restored = manager.load(file).toGameState(0, "RUNNING");
        var completed = BaseRepairProcessor.advanceDay(restored); var expected = BaseRepairProcessor.advanceDay(partial);
        assertEquals(expected.fleets(), completed.fleets()); assertEquals(expected.empires(), completed.empires());
        assertEquals(expected.commercialHubs(), completed.commercialHubs()); assertEquals(expected.marketAccounts(), completed.marketAccounts());
        var departed = restored.withFleets(List.of(restored.fleets().getFirst().withLocation(FleetLocation.at(FleetLocation.Site.orbit("earth")))));
        var after = BaseRepairProcessor.advanceDay(departed);
        assertEquals(departed.fleets(), after.fleets()); assertEquals(departed.empires(), after.empires()); assertEquals(departed.marketAccounts(), after.marketAccounts());
    }

    @Test void engineFleetPhaseRetainsBothRepairResultsAndPaidEconomyState() throws Exception {
        var initial = world(fleet("fleet", ship("target", "owner", 1000, Map.of(0, 10.0))));
        var engine = new SpaceConquestEngine(); engine.applyGameState(initial);
        // Exercise the real fleet phase without introducing unrelated economic fixture balancing.
        var phase = SpaceConquestEngine.class.getDeclaredMethod("updateFleets"); phase.setAccessible(true); phase.invoke(engine);
        var after = engine.getGameState();
        assertEquals(ShipModuleDamage.empty(), find(after, "target").moduleDamage()); assertEquals(970, after.empires().getFirst().treasuryCredits());
        assertEquals(190, stock(after, "steel")); assertEquals(30, sales(after));
    }
    @Test void paidBaseRestoresUsefulGenerationBeforeSensorsAndFallsThroughWhenCopperRunsOut() {
        var modules = List.of(CombatModuleCatalog.GUN, SensorModuleCatalog.BASIC, ShipComponentCatalog.FISSION_REACTOR_ID);
        var p = ShipPowerProfile.capture(modules.stream().map(ShipComponentCatalog::module).toList());
        var design = new ShipDesign("design", "Electrical", "owner", ShipRole.COMBAT_SHIP, "steel", modules,
                "steel", 0, 1000, 1000, 1000, 100, 1, 0, 1000, true, false, ShipManufacturingProfile.baseline(), p);
        var target = ship("target", "owner", 1000, Map.of(0, 10.0, 1, 50.0, 2, 100.0))
                .withPowerState(new ShipPowerState(Map.of("refined_uranium", 1.0), "rp1", "uranium", 0,
                        false, 1, 1, 0, 0, 0, 0, 0));
        var initial = world(fleet("fleet", target)).withShipDesigns(List.of(design));
        var status = FleetRepairStatus.inspect(initial, initial.fleets().getFirst());
        assertEquals("Fission reactor tier 2", status.ships().getFirst().mounts().getFirst().name());
        assertEquals(50.0 / 1.2, status.base().quotedPoints(), 1e-9);
        var after = BaseRepairProcessor.advanceDay(initial); var repaired = find(after, "target");
        assertEquals(100 - 50.0 / 1.2, repaired.moduleDamage().damageByMount().get(2), 1e-9);
        assertEquals(50, repaired.moduleDamage().damageByMount().get(1));
        assertEquals(10 - (50 - 50.0 / 1.2), repaired.moduleDamage().damageByMount().get(0), 1e-9);
        assertEquals(0, stock(after, "refined_copper"), 1e-9);
        assertEquals(200 - (50.0 / 1.2 * 3 + 50 - 50.0 / 1.2), stock(after, "steel"), 1e-9);
        assertEquals(1000 - 1000.0 / 3, after.empires().getFirst().treasuryCredits(), 1e-9);
        assertEquals(1000.0 / 3, sales(after), 1e-9);
        assertEquals(target.powerState(), repaired.powerState());
        assertEquals(100, target.moduleDamage().damageByMount().get(2));
    }
}
