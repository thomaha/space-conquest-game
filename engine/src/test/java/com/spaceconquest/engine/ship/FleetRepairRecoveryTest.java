package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.macrostructure.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FleetRepairRecoveryTest {
    @TempDir Path directory;
    private ShipDesign design(boolean workshop) {
        var modules = new ArrayList<>(List.of(CombatModuleCatalog.GUN, ShipComponentCatalog.FISSION_REACTOR_ID, "mod_chemical_rocket"));
        if (workshop) modules.add(ShipRepairModuleCatalog.WORKSHOP);
        return new ShipDesign("design", "Design", "owner", ShipRole.COMBAT_SHIP, "steel", modules, "steel", 0,
                1000, 10000, 1000, 500, 1, 0, 1000, false, false, ShipManufacturingProfile.baseline(),
                ShipPowerProfile.capture(modules.stream().map(ShipComponentCatalog::module).toList()));
    }
    private OrbitalStation station(String id, boolean repair) {
        return new OrbitalStation(id, id, "sol", "earth", "owner", "PUBLIC_STATE", 30,
                repair ? List.of(BaseRepairCatalog.module().withPayroll(2, 0)) : List.of(),
                Map.of(), 100, repair ? 50 : 0, 0, 0, 100, 100, "steel", 0, true);
    }
    private GameState world(boolean workshop, String current, String home, double damage, double cash, Map<String, Double> cargo) {
        var ship = new ShipInstance("ship", "design", "owner", 1000, 0, 100, cargo)
                .withPowerState(new ShipPowerState(Map.of("refined_uranium", 1.0), "rp1", "uranium", 0, true, 1, 1, 0, 0, 0, 0, 0))
                .withModuleDamage(new ShipModuleDamage(Map.of(0, damage)));
        var site = FleetLocation.Site.docked(home);
        var guard = new FleetGuardOrder(FleetGuardOrder.TargetType.STATION, home, "sol", site);
        var assignment = new FleetProtectionAssignment(guard, "", "fleet", site, FleetProtectionAssignment.Stage.RETURNING, "");
        var fleet = new Fleet("fleet", "Fleet", "owner", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(ship),
                FleetLocation.at(FleetLocation.Site.docked(current)))
                .withInterceptOrder(new FleetInterceptOrder("owner", "absent", "sol", assignment));
        var empire = new Empire("owner", "Owner", "human", "Individualist", cash, 0, List.of("sol"), List.of(), Map.of(), List.of(), List.of());
        return GameState.builder().empires(List.of(empire)).shipDesigns(List.of(design(workshop))).fleets(List.of(fleet))
                .orbitalStations(List.of(station("port", true), station("home", false)))
                .commercialHubs(List.of(new CommercialHub("hub", "port", 0, 10000, 1000, 10,
                        Map.of("steel", new MarketOrder("steel", 1000, 0, 1, 0))))).build();
    }
    private Fleet fleet(GameState state) { return FleetOrganization.find(state, "fleet"); }
    private ShipInstance ship(GameState state) { return fleet(state).ships().getFirst(); }

    @Test void onboardWorkWinsWithoutSpendingForecastMaterialsAndGuardRestoresAfterActualRepair() {
        var initial = world(true, "port", "port", 10, 1000, Map.of("steel", 20.0));
        var waiting = FleetProtectionReturn.advance(initial);
        assertEquals("", fleet(waiting).interceptOrder().protection().serviceTargetId());
        assertEquals(FleetProtectionAssignment.Stage.REPAIRING, fleet(waiting).interceptOrder().protection().stage());
        assertEquals(initial.empires(), waiting.empires()); assertEquals(initial.commercialHubs(), waiting.commercialHubs());
        assertEquals(initial.fleets().getFirst().ships(), fleet(waiting).ships());
        var worked = waiting.withFleets(ShipPowerProcessor.advanceDay(waiting));
        var restored = FleetProtectionReturn.advance(worked);
        assertNull(fleet(restored).interceptOrder()); assertNotNull(fleet(restored).guardOrder());
        assertEquals(ShipModuleDamage.empty(), ship(restored).moduleDamage());
        assertEquals(10, ship(restored).storedCargoKg().get("steel"));
    }

    @Test void paidBaseWaitPersistsAndActualSharedDailyWorkResumesOriginalGuard() throws Exception {
        var initial = world(false, "port", "port", 70, 1000, Map.of());
        var waiting = FleetProtectionReturn.advance(initial);
        assertEquals("port", fleet(waiting).interceptOrder().protection().serviceTargetId());
        assertEquals(initial.empires(), waiting.empires()); assertEquals(initial.commercialHubs(), waiting.commercialHubs());
        var partial = BaseRepairProcessor.advanceDay(waiting);
        assertEquals(20, ship(partial).moduleDamage().damageByMount().get(0));
        var manager = new SaveGameManager(directory); var file = directory.resolve("recovery.scsave").toFile();
        manager.save(file, partial, 1, "2027-01-01T08:00:00");
        var loaded = manager.load(file).toGameState(partial.turn(), "RUNNING");
        var continued = FleetProtectionReturn.advance(loaded);
        assertEquals(FleetProtectionAssignment.Stage.REPAIRING, fleet(continued).interceptOrder().protection().stage());
        assertEquals(partial.empires(), continued.empires());
        var finished = FleetProtectionReturn.advance(BaseRepairProcessor.advanceDay(continued));
        assertNull(fleet(finished).interceptOrder()); assertNotNull(fleet(finished).guardOrder());
        assertEquals(790, finished.empires().getFirst().treasuryCredits());
    }

    @Test void detourCommitsOnlyFundedOutboundMotionAndRetainsOriginalAssignment() {
        var initial = world(false, "home", "home", 10, 1000, Map.of());
        var departing = FleetProtectionReturn.advance(initial);
        assertTrue(fleet(departing).location().inTransit());
        assertEquals(FleetLocation.Site.docked("port"), fleet(departing).location().destination());
        assertEquals(FleetProtectionAssignment.Stage.REPAIRING, fleet(departing).interceptOrder().protection().stage());
        assertEquals(fleet(initial).interceptOrder().protection().guard(), fleet(departing).interceptOrder().protection().guard());
        assertTrue(ship(departing).currentFuelKg() < ship(initial).currentFuelKg());
        assertEquals(initial.empires(), departing.empires()); assertEquals(initial.commercialHubs(), departing.commercialHubs());
        assertEquals(ship(initial).moduleDamage(), ship(departing).moduleDamage());
        var journey = departing;
        for (int day = 0; day < 6 && fleet(journey).interceptOrder() != null; day++) {
            journey = journey.withFleets(ShipPowerProcessor.advanceDay(journey));
            journey = journey.withFleets(new FleetProcessor().processFleetMovements(journey.fleets(), journey.orbitalStations(), journey.diplomaticRelations()));
            journey = FleetProtectionReturn.advance(BaseRepairProcessor.advanceDay(journey)).withTurn(journey.turn() + 1);
        }
        assertNull(fleet(journey).interceptOrder()); assertNotNull(fleet(journey).guardOrder());
        assertTrue(fleet(journey).location().stationaryAt(FleetLocation.Site.docked("home")));
        assertEquals(ShipModuleDamage.empty(), ship(journey).moduleDamage());
        assertEquals(970, journey.empires().getFirst().treasuryCredits());
    }

    @Test void exhaustedCashMissingMaterialsAndLostBaseReleaseRepairWaitWithoutFreeWork() {
        var waiting = FleetProtectionReturn.advance(world(false, "port", "port", 10, 1000, Map.of()));
        for (var unavailable : List.of(waiting.withCommercialHubs(List.of()),
                waiting.withOrbitalStations(List.of(station("port", false))),
                waiting.withEmpires(world(false, "port", "port", 10, 0, Map.of()).empires()))) {
            var released = FleetProtectionReturn.advance(unavailable);
            assertNull(fleet(released).interceptOrder()); assertNotNull(fleet(released).guardOrder());
            assertEquals(ship(waiting).moduleDamage(), ship(released).moduleDamage());
            assertEquals(unavailable.empires(), released.empires());
        }
    }

    @Test void completedBaseRepairRestoresEscortIdentityThroughPhysicalTransfer() {
        var initial = world(false, "port", "port", 10, 1000, Map.of()); var escort = fleet(initial);
        var assignment = new FleetProtectionAssignment(null, "convoy", "originalEscort", escort.location().current(),
                FleetProtectionAssignment.Stage.RETURNING, "");
        escort = escort.withInterceptOrder(escort.interceptOrder().withProtection(assignment));
        var cargoShip = new ShipInstance("cargoShip", "design", "owner", 1000, 0, 100, Map.of());
        var convoy = new Fleet("convoy", "Convoy", "owner", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(cargoShip), escort.location());
        initial = initial.withFleets(List.of(escort, convoy));
        var waiting = FleetProtectionReturn.advance(initial);
        assertNotNull(fleet(waiting));
        var restored = FleetProtectionReturn.advance(BaseRepairProcessor.advanceDay(waiting));
        assertNull(fleet(restored));
        var joined = FleetOrganization.find(restored, "convoy");
        assertEquals("originalEscort", joined.escortOrder().escortFleetId()); assertEquals(2, joined.ships().size());
    }

    @Test void dryFleetCannotDetourAndExplicitInterceptionIsNotOverridden() {
        var initial = world(false, "home", "home", 10, 1000, Map.of()); var old = ship(initial);
        var dry = new ShipInstance(old.id(), old.designId(), old.ownerEntityId(), old.currentHullHealth(), 0, 0,
                old.storedCargoKg(), 0, "", "CONSCIOUS", old.powerState(), old.supplyState(), old.moduleDamage());
        initial = initial.withFleets(List.of(fleet(initial).withShips(List.of(dry))));
        var restored = FleetProtectionReturn.advance(initial);
        assertFalse(fleet(restored).location().inTransit()); assertNotNull(fleet(restored).guardOrder());
        assertEquals(dry.moduleDamage(), ship(restored).moduleDamage());
        var explicit = initial.withFleets(List.of(fleet(initial).withInterceptOrder(new FleetInterceptOrder("owner", "enemy", "sol"))));
        assertEquals(explicit, FleetProtectionReturn.advance(explicit));
    }

    @Test void hullRecoveryStopsWaitingAtProvisionalThresholdWithoutDemandingFullRestoration() {
        var initial = world(false, "port", "port", 0, 1000, Map.of()); var old = ship(initial);
        var damaged = new ShipInstance(old.id(), old.designId(), old.ownerEntityId(), 790, 0, old.currentFuelKg(),
                old.storedCargoKg(), 0, "", "CONSCIOUS", old.powerState(), old.supplyState(), ShipModuleDamage.empty());
        initial = initial.withFleets(List.of(fleet(initial).withShips(List.of(damaged))));
        var waiting = FleetProtectionReturn.advance(initial);
        assertEquals(FleetProtectionAssignment.Stage.REPAIRING, fleet(waiting).interceptOrder().protection().stage());
        var worked = BaseRepairProcessor.advanceDay(waiting);
        assertEquals(840, ship(worked).currentHullHealth());
        var restored = FleetProtectionReturn.advance(worked);
        assertNull(fleet(restored).interceptOrder()); assertNotNull(fleet(restored).guardOrder());
        assertEquals(worked.empires(), restored.empires());
    }

    @Test void ordinaryIdleFleetRepairsOnCommonOrderTickAndResumesItsStance() {
        var initial = world(true, "port", "port", 10, 1000, Map.of("steel", 20.0));
        initial = initial.withFleets(List.of(fleet(initial).withInterceptOrder(null).withStance("AGGRESSIVE")));
        var waiting = FleetInterceptProcessor.advance(initial);
        assertEquals("AGGRESSIVE", fleet(waiting).interceptOrder().protection().repairIntent().stance());
        assertEquals(initial.empires(), waiting.empires()); assertEquals(ship(initial).storedCargoKg(), ship(waiting).storedCargoKg());
        var restored = FleetInterceptProcessor.advance(waiting.withFleets(ShipPowerProcessor.advanceDay(waiting)));
        assertNull(fleet(restored).interceptOrder()); assertEquals("AGGRESSIVE", fleet(restored).fleetStance());
        assertEquals(ShipModuleDamage.empty(), ship(restored).moduleDamage());
    }

    @Test void ordinaryGuardDetourSavesItsOrderAndResumesOnlyAfterPhysicalReturn() throws Exception {
        var initial = world(false, "home", "home", 10, 1000, Map.of());
        var guard = fleet(initial).interceptOrder().protection().guard();
        initial = initial.withFleets(List.of(fleet(initial).withInterceptOrder(null).withGuardOrder(guard)));
        var journey = FleetInterceptProcessor.advance(initial);
        assertNull(fleet(journey).guardOrder()); assertTrue(fleet(journey).location().inTransit());
        assertEquals(guard, fleet(journey).interceptOrder().protection().repairIntent().guard());
        var manager = new SaveGameManager(directory); var file = directory.resolve("ordinary-guard.scsave").toFile();
        manager.save(file, journey, 1, "2027-01-01T08:00:00"); journey = manager.load(file).toGameState(journey.turn(), "RUNNING");
        for (int day = 0; day < 6 && fleet(journey).interceptOrder() != null; day++) {
            journey = journey.withFleets(ShipPowerProcessor.advanceDay(journey));
            journey = journey.withFleets(new FleetProcessor().processFleetMovements(journey.fleets(), journey.orbitalStations(), journey.diplomaticRelations()));
            journey = FleetInterceptProcessor.advance(BaseRepairProcessor.advanceDay(journey)).withTurn(journey.turn() + 1);
        }
        assertEquals(guard, fleet(journey).guardOrder()); assertNull(fleet(journey).interceptOrder());
        assertEquals(970, journey.empires().getFirst().treasuryCredits());
    }

    @Test void ordinaryConvoyRetainsEscortMembershipThroughWholeFleetRepair() {
        var initial = world(false, "port", "port", 10, 1000, Map.of());
        var second = new ShipInstance("protected", "design", "owner", 1000, 0, 100, Map.of());
        var escort = new FleetEscortOrder("owner", "escortIdentity", "Escort", "PASSIVE", List.of("ship"));
        var convoy = fleet(initial).withInterceptOrder(null).withShips(List.of(ship(initial), second)).withStance("ESCORT").withEscortOrder(escort);
        initial = initial.withFleets(List.of(convoy));
        var waiting = FleetInterceptProcessor.advance(initial);
        assertNull(fleet(waiting).escortOrder());
        assertEquals(escort, fleet(waiting).interceptOrder().protection().repairIntent().escort());
        assertEquals(convoy.ships(), fleet(waiting).ships());
        var restored = FleetInterceptProcessor.advance(BaseRepairProcessor.advanceDay(waiting));
        assertEquals(escort, fleet(restored).escortOrder()); assertEquals("ESCORT", fleet(restored).fleetStance());
        assertEquals(2, fleet(restored).ships().size());
    }

    @Test void committedFreightPassengersAndPaidJourneysAreNotPausedForOrdinaryRepair() {
        var initial = world(false, "home", "home", 10, 1000, Map.of());
        initial = initial.withFleets(List.of(fleet(initial).withInterceptOrder(null)));
        var route = new com.spaceconquest.engine.logistics.TradeRoute("route", "Route", "owner", "a", "b", "steel", 100, 0, 100,
                List.of("ship"), 0, true);
        var freight = initial.withTradeRoutes(List.of(route)); assertEquals(freight, FleetRepairOrders.advance(freight));
        var loaded = initial.withTradeRoutes(List.of(route.withLoadedCargo(10, 30).withActive(false)));
        assertEquals(loaded, FleetRepairOrders.advance(loaded));
        var old = ship(initial);
        var passenger = new ShipInstance(old.id(), old.designId(), old.ownerEntityId(), old.currentHullHealth(), 0, old.currentFuelKg(),
                old.storedCargoKg(), 1, "human", "CONSCIOUS", old.powerState(), old.supplyState(), old.moduleDamage());
        var booked = initial.withFleets(List.of(fleet(initial).withShips(List.of(passenger))));
        assertEquals(booked, FleetRepairOrders.advance(booked));
        var supply = new FleetSupplyOrder("fleet", "owner", "ship", "receiver", List.of("ship"), "uranium", 1, 0);
        var supplying = initial.withFleets(List.of(fleet(initial).withShips(List.of(old.withSupplyState(
                new ShipSupplyState(Map.of(), supply, ""))))));
        assertEquals(supplying, FleetRepairOrders.advance(supplying));
        var plan = FleetReturnTravel.localPlan(initial, fleet(initial), FleetLocation.Site.docked("port")); assertNotNull(plan);
        var traveling = initial.withFleets(List.of(LocalTravel.depart(fleet(initial), FleetLocation.Site.docked("port"), plan)));
        assertEquals(traveling, FleetRepairOrders.advance(traveling));
        var unavailable = initial.withCommercialHubs(List.of()); assertEquals(unavailable, FleetRepairOrders.advance(unavailable));
        var rescueOrder = new RescueOrder("fleet", "donorShip", "ship", ShipSupplyTransfer.Source.SUPPLY_TANK,
                ShipSupplyTransfer.Destination.PROPELLANT, "mod_chemical_rocket", 1);
        var status = new RescueStatus("donor", rescueOrder, RescueStatus.Phase.APPROACHING, 0, "Approaching");
        var donor = new Fleet("donor", "Donor", "owner", "sol", "", 0, 0, 0, false, "PASSIVE",
                List.of(new ShipInstance("donorShip", "design", "owner", 1000, 0, 100, Map.of())
                    .withPowerState(ShipPowerState.empty().withRescueStatus(status))), fleet(initial).location());
        var rescuing = initial.withFleets(List.of(fleet(initial), donor));
        assertEquals(rescuing, FleetRepairOrders.advance(rescuing));
    }

    @Test void ordinaryPursuitIntentIsDroppedWhenTheOriginalTargetIsNoLongerDetected() {
        var initial = world(false, "port", "port", 10, 1000, Map.of());
        var order = new FleetInterceptOrder("owner", "removed", "sol");
        initial = initial.withFleets(List.of(fleet(initial).withInterceptOrder(order)));
        var waiting = FleetInterceptProcessor.advance(initial);
        assertEquals(order, fleet(waiting).interceptOrder().protection().repairIntent().intercept());
        var restored = FleetInterceptProcessor.advance(BaseRepairProcessor.advanceDay(waiting));
        assertNull(fleet(restored).interceptOrder()); assertFalse(fleet(restored).location().inTransit());
    }

    @Test void validDetectedWartimePursuitResumesAndEscapeKeepsTheSavedOrdinaryIntent() {
        var initial = world(false, "port", "port", 10, 1000, Map.of());
        var order = new FleetInterceptOrder("owner", "enemy", "sol");
        initial = initial.withFleets(List.of(fleet(initial).withInterceptOrder(order)));
        var waiting = FleetInterceptProcessor.advance(initial);
        var assignment = fleet(waiting).interceptOrder().protection();
        assertEquals(assignment.repairIntent(), assignment.afterEscape().repairIntent());
        assertTrue(assignment.afterEscape().escaped());
        var repaired = BaseRepairProcessor.advanceDay(waiting);
        var sensorPort = new OrbitalStation("port", "Port", "sol", "earth", "owner", "PUBLIC_STATE", 30,
                List.of(BaseRepairCatalog.module().withPayroll(2, 0), SensorModuleCatalog.stationModule(SensorModuleCatalog.BASIC)),
                Map.of(), 100, 60, 0, 0, 100, 100, "steel", 0, true);
        var enemy = new Fleet("enemy", "Enemy", "enemyOwner", "sol", "", 0, 0, 0, false, "PASSIVE",
                List.of(new ShipInstance("enemyShip", "design", "enemyOwner", 1000, 0, 100, Map.of())), fleet(repaired).location());
        repaired = repaired.withOrbitalStations(List.of(sensorPort)).withFleets(List.of(fleet(repaired), enemy))
                .toBuilder().diplomaticRelations(List.of(new DiplomaticRelation("owner", "enemyOwner", "TOTAL_WAR", 0))).build();
        assertTrue(FleetInterceptProcessor.contact(repaired, fleet(repaired), enemy));
        var resumed = FleetInterceptProcessor.advance(repaired);
        assertEquals(order, fleet(resumed).interceptOrder());
        var lost = repaired.toBuilder().diplomaticRelations(List.of()).build();
        assertNull(fleet(FleetInterceptProcessor.advance(lost)).interceptOrder());
    }

    @Test void olderSavedRepairAssignmentWithoutOrdinaryIntentStillLoads() throws Exception {
        var assignment = new com.fasterxml.jackson.databind.ObjectMapper().readValue("""
                {"guard":null,"convoyFleetId":"","homeFleetId":"fleet",
                 "origin":{"kind":"DOCKED","entityId":"port","parkingAltitudeKm":null},
                 "stage":"REPAIRING","serviceTargetId":"port","escaped":false}
                """, FleetProtectionAssignment.class);
        assertNull(assignment.repairIntent()); assertEquals(FleetProtectionAssignment.Stage.REPAIRING, assignment.stage());
        assertEquals("port", assignment.serviceTargetId());
    }
}
