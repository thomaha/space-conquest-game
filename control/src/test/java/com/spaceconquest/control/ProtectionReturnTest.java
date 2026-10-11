package com.spaceconquest.control;

import com.fasterxml.jackson.databind.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.MarketAccount;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProtectionReturnTest {
    private Fleet fleet(GameState state, String id) { return FleetOrganization.find(state, id); }
    private ShipInstance fuel(ShipInstance ship, String id, double kg) {
        return new ShipInstance(id, ship.designId(), ship.ownerEntityId(), ship.currentHullHealth(), ship.currentShieldHealth(),
                kg, ship.storedCargoKg()).withPowerState(ship.powerState()).withSupplyState(ship.supplyState());
    }
    private GameState world(double... tanks) throws Exception {
        var state = new OrbitalRendezvousTest().world(); var guard = fleet(state, "hunter"); var prototype = guard.ships().getFirst();
        var ships = new ArrayList<ShipInstance>();
        for (int i = 0; i < tanks.length; i++) ships.add(fuel(prototype, "ship-" + i, tanks[i]));
        var origin = FleetLocation.Site.orbit("earth", 8000);
        var assignment = new FleetProtectionAssignment(new FleetGuardOrder(FleetGuardOrder.TargetType.BODY, "earth", "sol", origin),
                "", "hunter", origin, FleetProtectionAssignment.Stage.RETURNING, "");
        guard = guard.withShips(ships).withLocation(FleetLocation.at(FleetLocation.Site.orbit("earth", 1000)))
                .withInterceptOrder(new FleetInterceptOrder("owner", "old-target", "sol", assignment));
        return state.withFleets(List.of(guard)).toBuilder().diplomaticRelations(List.of()).build();
    }
    private GameState day(GameState state) {
        return state.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state), state.orbitalStations(), List.of()))
                .withTurn(state.turn() + 1);
    }
    private double fuel(GameState state) { return state.fleets().stream().flatMap(fleet -> fleet.ships().stream()).mapToDouble(ShipInstance::currentFuelKg).sum(); }
    private double cash(GameState state) { return state.empires().stream().mapToDouble(Empire::treasuryCredits).sum()
            + state.marketAccounts().stream().mapToDouble(MarketAccount::unsettledSalesCredits).sum(); }
    private GameState reload(GameState state) throws Exception {
        var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper.readValue(mapper.writeValueAsString(SaveGame.fromGameState(state, "now", 1, "now")), SaveGame.class).toGameState(state.turn(), "RUNNING");
    }
    @Test void compatibleSharingFundsWholeReturnWithoutSplittingAndResumesGuardAfterSave() throws Exception {
        var initial = world(15000, 0); var state = FleetProtectionReturn.advance(initial);
        assertEquals(1, state.fleets().size()); assertTrue(fleet(state, "hunter").location().inTransit());
        assertEquals(fuel(initial), fuel(state), .000001); assertEquals(cash(initial), cash(state));
        assertTrue(fleet(state, "hunter").ships().getLast().currentFuelKg() > 0);
        state = reload(state);
        for (int tick = 0; tick < 10 && fleet(state, "hunter").guardOrder() == null; tick++) state = FleetProtectionReturn.advance(day(state));
        assertNotNull(fleet(state, "hunter").guardOrder()); assertNull(fleet(state, "hunter").interceptOrder());
        assertTrue(fuel(state) < fuel(initial)); assertEquals(2, fleet(state, "hunter").ships().size());
    }
    @Test void severalDonorsPoolSurplusWithoutStrandingEitherDonor() throws Exception {
        var initial = world(1300, 1300, 0);
        var state = FleetProtectionReturn.advance(initial);
        assertEquals(1, state.fleets().size()); assertTrue(fleet(state, "hunter").location().inTransit());
        assertEquals(fuel(initial), fuel(state), .000001);
        for (var ship : fleet(state, "hunter").ships()) assertTrue(ship.currentFuelKg() > 750);
    }
    @Test void insufficientSharingSplitsReadyShipsAndPersistsStrandedRecovery() throws Exception {
        var initial = world(1000, 0);
        assertNotNull(FleetReturnTravel.plan(initial, fleet(initial, "hunter").withShips(List.of(fleet(initial, "hunter").ships().getFirst())),
                FleetLocation.Site.orbit("earth", 8000)));
        var state = FleetProtectionReturn.advance(initial);
        assertEquals(2, state.fleets().size()); assertTrue(fleet(state, "hunter").location().inTransit());
        var stranded = state.fleets().stream().filter(fleet -> !fleet.id().equals("hunter")).findFirst().orElseThrow();
        assertEquals(0, stranded.ships().getFirst().currentFuelKg()); assertEquals(fleet(initial, "hunter").location(), stranded.location());
        assertEquals(FleetProtectionAssignment.Stage.RETURNING, stranded.interceptOrder().protection().stage());
        assertEquals(fuel(initial), fuel(state), .000001);
        assertEquals(state.fleets(), reload(state).fleets());
        var repeated = FleetProtectionReturn.advance(state);
        assertEquals(state, repeated); assertEquals(2, repeated.fleets().size());
    }
    @Test void nearestReachableStockedBaseRefuelsWithRealPaymentThenReturns() throws Exception {
        var initial = world(800);
        double phase = Integer.toUnsignedLong("earth:parking".hashCode()) / 4294967296.0 * 2 * Math.PI;
        var station = new com.spaceconquest.engine.macrostructure.OrbitalStation("refuel", "Refuel", "sol", "earth", "owner", "PUBLIC_STATE",
                10, List.of(new com.spaceconquest.engine.macrostructure.StationModule("control", "Control", "CONTROL",
                1, 1000, 0, 100, Map.of(), "technician", 0, true)), Map.of(), 100, 0, 0, 0, 100, 100, "steel", 0, true)
                .withParkingAltitudeKm(1000.0).withOrbitalPhaseRadians(phase + .0001);
        var orders = Map.of("hydrogen_gas", new MarketOrder("hydrogen_gas", 1000000, 0, .001, 0),
                "fusion_fuel_pellets", new MarketOrder("fusion_fuel_pellets", 10000, 0, .001, 0),
                "refined_uranium", new MarketOrder("refined_uranium", 1000, 0, .001, 0));
        var hub = new CommercialHub("fuel", station.id(), 0, 2000000, 1011000, 10, orders);
        initial = initial.toBuilder().orbitalStations(List.of(station)).commercialHubs(List.of(hub))
                .marketAccounts(List.of(new MarketAccount("fuel", 1000))).build();
        assertNull(FleetReturnTravel.plan(initial, fleet(initial, "hunter"), FleetLocation.Site.orbit("earth", 8000)));
        var state = FleetProtectionReturn.advance(initial);
        assertEquals(FleetProtectionAssignment.Stage.REFUELING, fleet(state, "hunter").interceptOrder().protection().stage());
        assertEquals(initial.marketAccounts(), state.marketAccounts()); assertEquals(initial.commercialHubs(), state.commercialHubs());
        for (int tick = 0; tick < 15 && fleet(state, "hunter").guardOrder() == null; tick++) state = FleetProtectionReturn.advance(day(state));
        assertNotNull(fleet(state, "hunter").guardOrder()); assertEquals(cash(initial), cash(state), .000001);
        assertTrue(state.commercialHubs().getFirst().activeOrders().get("hydrogen_gas").supplyKg() < 1000000);
        assertTrue(state.empires().getFirst().treasuryCredits() < initial.empires().getFirst().treasuryCredits());
        var docked = fleet(initial, "hunter").withLocation(FleetLocation.at(FleetLocation.Site.docked(station.id())));
        var home = FleetLocation.Site.orbit("earth", 8000);
        var anchor = new Fleet("anchor", "Anchor", "owner", "sol", "", 0, 0, 0, false, "PASSIVE",
                List.of(fuel(docked.ships().getFirst(), "anchor-ship", 15000)), FleetLocation.at(home));
        var saved = new FleetProtectionAssignment(new FleetGuardOrder(FleetGuardOrder.TargetType.FLEET, "anchor", "sol", home),
                "", "hunter", home, FleetProtectionAssignment.Stage.RETURNING, "");
        docked = docked.withInterceptOrder(docked.interceptOrder().withProtection(saved));
        var engine = new SpaceConquestEngine(); engine.applyGameState(initial.withFleets(List.of(docked, anchor))); engine.stepTurn();
        assertTrue(engine.getGameState().commercialHubs().getFirst().activeOrders().get("hydrogen_gas").supplyKg() < 1000000,
                "The engine tick retains the physical fuel purchase.");
        assertTrue(engine.getGameState().empires().getFirst().treasuryCredits() < initial.empires().getFirst().treasuryCredits(),
                "The engine tick retains the corresponding payment.");
    }

    @Test void nearbyFuelTransportMakesPaidPhysicalContactBeforeSupplyingAndReturning() throws Exception {
        var initial = world(0);
        var modules = new ArrayList<>(ShipComponentCatalog.workbenchModules("mod_fusion_drive", false, ShipComponentCatalog.FISSION_REACTOR_ID));
        modules.remove("mod_cargo_vault");
        modules.addAll(List.of(ShipSupplyCatalog.CRYOGENIC_TANK, ShipSupplyCatalog.REACTOR_MAGAZINE, ShipSupplyCatalog.TRANSFER_PUMP));
        var evaluated = ShipBlueprintFactory.evaluate(initial, new ShipDesignSpecification("tanker", "Tanker", "owner", ShipRole.CARGO_TRANSPORT,
                "steel", modules, "steel", .5)); assertTrue(evaluated.valid(), evaluated.errors().toString());
        var designs = new ArrayList<>(initial.shipDesigns()); designs.add(evaluated.design());
        var prototype = fleet(initial, "hunter").ships().getFirst();
        var ship = new ShipInstance("supplier", "tanker", "owner", 100, 0, evaluated.design().fuelCapacityKg(), Map.of("fusion_fuel_pellets", 200.0))
                .withPowerState(prototype.powerState()).withSupplyState(new ShipSupplyState(Map.of("hydrogen_gas", 4000.0,
                        "fusion_fuel_pellets", 50.0, "refined_uranium", 5.0), null, ""));
        var tanker = new Fleet("tanker", "Tanker", "owner", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(ship),
                FleetLocation.at(FleetLocation.Site.orbit("earth", 500)));
        initial = initial.withShipDesigns(designs).withFleets(List.of(fleet(initial, "hunter"), tanker));
        var state = FleetProtectionReturn.advance(initial);
        assertEquals(FleetProtectionAssignment.Stage.SUPPLYING, fleet(state, "tanker").interceptOrder().protection().stage());
        assertTrue(fleet(state, "tanker").location().inTransit()); assertEquals(0, fleet(state, "hunter").ships().getFirst().currentFuelKg());
        assertEquals(ship.supplyState().materialsKg(), fleet(state, "tanker").ships().getFirst().supplyState().materialsKg());
        assertEquals(state, FleetProtectionReturn.advance(state), "A pending transport prevents duplicate requests.");
        state = reload(state);
        for (int tick = 0; tick < 15 && (fleet(state, "hunter").guardOrder() == null || fleet(state, "tanker").interceptOrder() != null); tick++)
            state = FleetProtectionReturn.advance(day(state));
        assertNotNull(fleet(state, "hunter").guardOrder()); assertNull(fleet(state, "tanker").interceptOrder());
        assertEquals(tanker.location(), fleet(state, "tanker").location()); assertEquals(cash(initial), cash(state));
        assertTrue(fleet(state, "tanker").ships().getFirst().supplyState().materialsKg().get("hydrogen_gas") < 4000);
        assertTrue(fleet(state, "tanker").ships().getFirst().currentFuelKg() < ship.currentFuelKg());
    }

    @Test void escortReturnsToMovedConvoyAndRejoinsWithSurvivingIdentity() throws Exception {
        var state = world(15000); var escort = fleet(state, "hunter"); var ship = escort.ships().getFirst();
        var protectedFleet = new Fleet("convoy", "Convoy", "owner", "sol", "", 0, 0, 0, false, "PASSIVE",
                List.of(fuel(ship, "protected", 15000)), FleetLocation.at(FleetLocation.Site.orbit("earth", 2000)));
        var assignment = new FleetProtectionAssignment(null, "convoy", "hunter", FleetLocation.Site.orbit("earth", 500),
                FleetProtectionAssignment.Stage.RETURNING, "");
        escort = escort.withInterceptOrder(escort.interceptOrder().withProtection(assignment));
        state = state.withFleets(List.of(escort, protectedFleet));
        state = FleetProtectionReturn.advance(state);
        assertEquals(protectedFleet.location().current(), fleet(state, "hunter").location().destination());
        for (int tick = 0; tick < 10 && fleet(state, "hunter") != null; tick++) state = FleetProtectionReturn.advance(day(state));
        assertNull(fleet(state, "hunter")); assertEquals(2, fleet(state, "convoy").ships().size());
        assertEquals("hunter", fleet(state, "convoy").escortOrder().escortFleetId());
        assertEquals(List.of(ship.id()), fleet(state, "convoy").escortOrder().shipIds());
        assertEquals(protectedFleet.ships().getFirst().storedCargoKg(), fleet(state, "convoy").ships().stream()
                .filter(item -> item.id().equals("protected")).findFirst().orElseThrow().storedCargoKg());
    }
    @Test void paidDriveRepairRetainsGuardAndEscortIntentUntilFundedPhysicalReturnAfterReload() throws Exception {
        for (boolean escorting : List.of(false, true)) {
            var state = world(15000); var guard = fleet(state, "hunter"); var original = guard.ships().getFirst();
            var design = FleetSupplySimulation.design(state, original);
            int drive = design.equippedModuleIds().indexOf("mod_fusion_drive");
            int gun = design.equippedModuleIds().indexOf(CombatModuleCatalog.GUN);
            var damaged = new ShipInstance(original.id(), original.designId(), original.ownerEntityId(), 1000, 0,
                    original.currentFuelKg(), original.storedCargoKg(), original.passengerCount(), original.passengerRaceId(),
                    original.transitMode(), original.powerState(), original.supplyState(), new ShipModuleDamage(Map.of(drive, 100.0, gun, 10.0)));
            var port = new com.spaceconquest.engine.macrostructure.OrbitalStation("repair", "Repair", "sol", "earth", "owner",
                    "PUBLIC_STATE", 30, List.of(BaseRepairCatalog.module().withPayroll(2, 0)), Map.of(),
                    100, 50, 0, 0, 100, 100, "steel", 0, true);
            var stations = new ArrayList<>(state.orbitalStations()); stations.add(port);
            var hub = new CommercialHub("repair-hub", "repair", 0, 10000, 1200, 10, Map.of(
                    "steel", new MarketOrder("steel", 1000, 0, 1, 0),
                    "refined_copper", new MarketOrder("refined_copper", 200, 0, 2, 0)));
            guard = guard.withShips(List.of(damaged)).withLocation(FleetLocation.at(FleetLocation.Site.docked("repair")));
            Fleet convoy = null;
            if (escorting) {
                convoy = new Fleet("convoy", "Convoy", "owner", "sol", "", 0, 0, 0, false, "PASSIVE",
                        List.of(fuel(original, "protected", 15000)), FleetLocation.at(FleetLocation.Site.orbit("earth", 2000)));
                var assignment = new FleetProtectionAssignment(null, "convoy", "hunter", FleetLocation.Site.orbit("earth", 500),
                        FleetProtectionAssignment.Stage.RETURNING, "");
                guard = guard.withInterceptOrder(guard.interceptOrder().withProtection(assignment));
            }
            state = state.toBuilder().orbitalStations(stations).commercialHubs(List.of(hub))
                    .fleets(escorting ? List.of(guard, convoy) : List.of(guard)).build();
            var saved = guard.interceptOrder().protection(); double startFuel = guard.ships().getFirst().currentFuelKg();
            var priority = ShipRepairPriority.context(state, guard);
            assertTrue(priority.mobility().getOrDefault(damaged.id(), Set.of()).contains(drive));
            assertFalse(FleetReturnTravel.ready(state, guard, FleetProtectionReturn.destination(state, guard)));
            for (int day = 0; day < 3; day++) state = BaseRepairProcessor.advanceDay(state);
            assertEquals(ShipModuleDamage.empty(), fleet(state, "hunter").ships().getFirst().moduleDamage());
            assertEquals(saved, fleet(state, "hunter").interceptOrder().protection());
            assertEquals(startFuel, fleet(state, "hunter").ships().getFirst().currentFuelKg());
            assertTrue(state.empires().getFirst().treasuryCredits() < 1e6);
            state = reload(state); state = FleetProtectionReturn.advance(state);
            assertTrue(fleet(state, "hunter").location().inTransit());
            for (int day = 0; day < 10 && (escorting ? fleet(state, "hunter") != null : fleet(state, "hunter").guardOrder() == null); day++)
                state = FleetProtectionReturn.advance(day(state));
            if (escorting) {
                assertNull(fleet(state, "hunter"));
                assertEquals(List.of(damaged.id()), fleet(state, "convoy").escortOrder().shipIds());
                assertEquals("hunter", fleet(state, "convoy").escortOrder().escortFleetId());
            } else {
                assertEquals(saved.guard(), fleet(state, "hunter").guardOrder());
                assertNull(fleet(state, "hunter").interceptOrder());
                assertTrue(fleet(state, "hunter").ships().getFirst().currentFuelKg() < startFuel);
            }
        }
    }
}
