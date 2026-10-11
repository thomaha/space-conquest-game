package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.MarketAccount;
import com.spaceconquest.engine.macrostructure.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ShipCommissioningTest {
    private GameState order(String owner, String role, boolean fuel, boolean materials) {
        var empires = List.of(empire("player"), empire("ai"));
        var corporation = new Corporation("corp", "Carrier", "ai", "yard", "TRANSPORT", 1000,
                List.of(), List.of(), List.of());
        var module = new StationModule("slipway", "Slipway", StationModule.TYPE_CAPITAL_SLIPWAY,
                4, 10000, 10, 0, Map.of(), "engineer", 0, true);
        module = module.withPayroll(ShipyardWorkCapacity.requiredWorkers(module), 0);
        var yard = new OrbitalStation("yard", "Yard", "sol", "earth", owner, OrbitalStation.OWNERSHIP_PUBLIC_STATE,
                20, List.of(module), Map.of(), 100, 10, 0, 0, 100, 100, "steel", 0, true);
        var design = new ShipDesign("design", "Ship", owner, role, "steel",
                List.of("mod_chemical_rocket", PropulsionCatalog.FUEL_TANK_MODULE_ID), "steel", .5,
                1000, 50, 200, 0, 1000, 0, 1000, true, false);
        var orders = new HashMap<String, MarketOrder>();
        orders.put("steel", new MarketOrder("steel", materials ? 2 : 0, 0, 1, 0));
        PropulsionCatalog.drive("mod_chemical_rocket").propellantMaterials(fuel ? 100 : 0)
                .forEach((id, kg) -> orders.put(id, new MarketOrder(id, kg, 0, 1, 0)));
        var hub = new CommercialHub("market", "yard", 0, 1000,
                orders.values().stream().mapToDouble(MarketOrder::supplyKg).sum(), 10, orders);
        var build = new ShipConstructionOrder("order", owner, design.id(), "sol", "yard", 0, 1,
                Map.of("steel", 2.0), Map.of());
        return GameState.builder().empires(empires).corporations(List.of(corporation)).shipDesigns(List.of(design))
                .orbitalStations(List.of(yard)).commercialHubs(List.of(hub)).marketAccounts(List.of(new MarketAccount(hub.id(), 0)))
                .shipConstructionOrders(List.of(build)).build();
    }

    private Empire empire(String id) {
        return new Empire(id, id, "human", "Individualist", 1000, 0, List.of("sol"), List.of(), Map.of(), List.of("rocketry"), List.of());
    }

    private double money(GameState state) {
        return state.empires().stream().mapToDouble(Empire::treasuryCredits).sum()
                + state.corporations().stream().mapToDouble(Corporation::liquidCapitalReserves).sum()
                + state.marketAccounts().stream().mapToDouble(MarketAccount::unsettledSalesCredits).sum();
    }

    @Test void commissionsOwnedTradersAndWarshipsWithPaidFuelFromTheirActualYard() {
        for (String owner : List.of("player", "ai", "corp")) for (String role : List.of(ShipRole.CARGO_TRANSPORT, ShipRole.COMBAT_SHIP)) {
            var before = order(owner, role, true, true);
            var after = new ShipConstructionProcessor().process(before);
            assertTrue(after.shipConstructionOrders().isEmpty());
            var fleet = after.fleets().getFirst();
            var ship = fleet.ships().getFirst();
            assertEquals(owner, fleet.ownerEntityId());
            assertEquals(owner, ship.ownerEntityId());
            assertTrue(fleet.location().isAt(FleetLocation.Site.docked("yard")));
            assertEquals(100, ship.currentFuelKg());
            assertEquals(0, ship.currentShieldHealth(), "A hull without a shield emitter receives no shield charge.");
            assertEquals(money(before), money(after), 1e-6);
            assertEquals(102, after.marketAccounts().getFirst().unsettledSalesCredits(), 1e-6);
            after.commercialHubs().getFirst().activeOrders().values().forEach(stock -> assertEquals(0, stock.supplyKg(), 1e-6));
            if (owner.equals("corp")) assertEquals(List.of(ship.id()), after.corporations().getFirst().ownedShipIds());
            var repeated = new ShipConstructionProcessor().process(after);
            assertEquals(after.fleets(), repeated.fleets());
            assertEquals(after.marketAccounts(), repeated.marketAccounts());
        }
    }

    @Test void missingFuelDoesNotInventStockOrPreventACompletedHullFromCommissioning() {
        var before = order("player", ShipRole.COMBAT_SHIP, false, true);
        var after = new ShipConstructionProcessor().process(before);
        assertTrue(after.shipConstructionOrders().isEmpty());
        assertEquals(0, after.fleets().getFirst().ships().getFirst().currentFuelKg());
        assertEquals(2, after.marketAccounts().getFirst().unsettledSalesCredits());
        assertEquals(money(before), money(after));
    }

    @Test void unpaidConstructionMaterialsLeaveTheOrderPendingWithoutCreatingAShip() {
        var before = order("corp", ShipRole.CARGO_TRANSPORT, true, false);
        var after = new ShipConstructionProcessor().process(before);
        assertEquals(1, after.shipConstructionOrders().size());
        assertTrue(after.fleets().isEmpty());
        assertTrue(after.corporations().getFirst().ownedShipIds().isEmpty());
        assertEquals(before.commercialHubs(), after.commercialHubs());
        assertEquals(before.marketAccounts(), after.marketAccounts());
    }
}
