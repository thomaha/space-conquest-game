package com.spaceconquest.control;

import com.fasterxml.jackson.databind.*;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.governance.DiplomaticPact;
import com.spaceconquest.engine.logistics.TradeRoute;
import com.spaceconquest.engine.macrostructure.*;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProtectionInterceptTest {
    private Fleet fleet(GameState state, String id) { return FleetOrganization.find(state, id); }
    private ShipInstance copy(ShipInstance ship, String id, double fuel) {
        return new ShipInstance(id, ship.designId(), ship.ownerEntityId(), ship.currentHullHealth(), ship.currentShieldHealth(),
                fuel, ship.storedCargoKg()).withPowerState(ship.powerState());
    }
    GameState world() throws Exception {
        var state = new OrbitalRendezvousTest().world();
        var guard = fleet(state, "hunter").withLocation(FleetLocation.at(FleetLocation.Site.orbit("earth", 500)));
        var ship = guard.ships().getFirst();
        guard = guard.withShips(List.of(ship, copy(ship, "reinforcement", ship.currentFuelKg())));
        var target = fleet(state, "target").withLocation(FleetLocation.at(FleetLocation.Site.orbit("earth", 1000)));
        return new GuardObjectCommand("owner", "hunter", FleetGuardOrder.TargetType.BODY, "earth")
                .apply(state.withFleets(List.of(guard, target)));
    }
    private GameState day(GameState state) {
        return state.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state), state.orbitalStations(), List.of()))
                .withTurn(state.turn() + 1);
    }
    @Test void guardCommitsPaidPursuitAndRetainsItThroughReloadAndArrival() throws Exception {
        var initial = world(); var guard = fleet(initial, "hunter");
        assertEquals(FleetProtectionProcessor.Outcome.INTERCEPTING, FleetProtectionProcessor.decide(initial, guard, List.of()).outcome());
        var state = FleetProtectionProcessor.advance(initial, List.of());
        assertNull(fleet(state, "hunter").guardOrder()); assertEquals("target", fleet(state, "hunter").interceptOrder().targetFleetId());
        assertTrue(fleet(state, "hunter").location().inTransit());
        assertEquals(guard.ships().getFirst().currentFuelKg(), fleet(state, "hunter").ships().getFirst().currentFuelKg());
        var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        state = mapper.readValue(mapper.writeValueAsString(SaveGame.fromGameState(state, "now", 1, "now")), SaveGame.class).toGameState(0, "RUNNING");
        for (int tick = 0; tick < 10 && fleet(state, "hunter").location().inTransit(); tick++) state = day(state);
        assertTrue(fleet(state, "hunter").location().isAt(fleet(state, "target").location().current()));
        assertTrue(fleet(state, "hunter").ships().getFirst().currentFuelKg() < guard.ships().getFirst().currentFuelKg());
        assertEquals(FleetProtectionAssignment.Stage.RETURNING,
                fleet(FleetInterceptProcessor.finishContacts(state), "hunter").interceptOrder().protection().stage());
    }
    @Test void dryGuardPeaceAndLostContactPreserveAssignment() throws Exception {
        var state = world(); var guard = fleet(state, "hunter");
        var dry = guard.withShips(guard.ships().stream().map(ship -> copy(ship, ship.id(), 0)).toList());
        var unfunded = state.withFleets(List.of(dry, fleet(state, "target")));
        assertEquals(FleetProtectionProcessor.Outcome.HOLD, FleetProtectionProcessor.decide(unfunded, dry, List.of()).outcome());
        assertEquals(unfunded, FleetProtectionProcessor.advance(unfunded, List.of()));
        var peace = state.toBuilder().diplomaticRelations(List.of()).build();
        assertEquals(peace, FleetProtectionProcessor.advance(peace, List.of()));
        var lost = state.withFleets(List.of(guard, fleet(state, "target").withLocation(FleetLocation.at(FleetLocation.Site.orbit("mars", 1000)))));
        assertEquals(lost, FleetProtectionProcessor.advance(lost, List.of()));
        var localFight = state.withFleets(List.of(guard, fleet(state, "target").withLocation(guard.location())));
        assertEquals(localFight, FleetProtectionProcessor.advance(localFight, List.of()), "A guard remains for a threat already at its site.");
    }
    @Test void overwhelmingForceEscapesAndPartialSupportCannotAuthorizeSoloPursuit() throws Exception {
        var state = world(); var enemy = fleet(state, "target"); var ship = enemy.ships().getFirst();
        var many = new ArrayList<ShipInstance>(); for (int i = 0; i < 20; i++) many.add(copy(ship, "enemy-" + i, ship.currentFuelKg()));
        state = state.withFleets(List.of(fleet(state, "hunter"), enemy.withShips(many)));
        // Neither cannon fleet is currently in range, but closing against the larger potential force is rejected.
        assertEquals(FleetProtectionProcessor.Outcome.HOLD, FleetProtectionProcessor.decide(state, fleet(state, "hunter"), List.of()).outcome());
        assertEquals(state, FleetProtectionProcessor.advance(state, List.of()));
        var close = state.withFleets(List.of(fleet(state, "hunter"), enemy.withShips(many).withLocation(fleet(state, "hunter").location())));
        assertEquals(FleetProtectionProcessor.Outcome.ESCAPING, FleetProtectionProcessor.decide(close, fleet(close, "hunter"), List.of()).outcome());
        var guard = fleet(state, "hunter");
        var support = new Fleet("support", "Support", "owner", "sol", "", 0, 0, 0, false, "PASSIVE",
                List.of(copy(guard.ships().getFirst(), "support-ship", guard.ships().getFirst().currentFuelKg())), guard.location());
        var three = enemy.withShips(many.subList(0, 3));
        var reinforced = state.withFleets(List.of(guard, support, three));
        assertEquals(FleetProtectionProcessor.Outcome.HOLD, FleetProtectionProcessor.decide(reinforced, guard, List.of()).outcome());
        assertEquals(reinforced, FleetProtectionProcessor.advance(reinforced, List.of()));
    }
    @Test void idleEscortDetachesOnlyForFundedPursuitAndCommittedFreightKeepsConvoy() throws Exception {
        var state = world(); var guard = fleet(state, "hunter").withGuardOrder(null);
        var protectedShip = copy(guard.ships().getFirst(), "protected", guard.ships().getFirst().currentFuelKg());
        var ships = new ArrayList<>(guard.ships()); ships.add(protectedShip);
        var escortIds = guard.ships().stream().map(ShipInstance::id).toList();
        var convoy = guard.withShips(ships).withStance("ESCORT")
                .withEscortOrder(new FleetEscortOrder("owner", "escort", "Escort", "PASSIVE", escortIds));
        state = state.withFleets(List.of(convoy, fleet(state, "target")));
        var departed = FleetProtectionProcessor.advance(state, List.of());
        assertNotNull(fleet(departed, "escort").interceptOrder()); assertTrue(fleet(departed, "escort").location().inTransit());
        assertEquals(List.of(protectedShip), fleet(departed, "hunter").ships());
        assertEquals(convoy.location(), fleet(departed, "hunter").location()); assertEquals("PASSIVE", fleet(departed, "hunter").fleetStance());
        assertNull(fleet(departed, "hunter").escortOrder());
        var paid = new TradeRoute("trade", "Trade", "owner", "source", "buyer", "steel", 10, 0, 100,
                List.of(protectedShip.id()), 0, true).withLoadedCargo(10, 30);
        var committed = state.withTradeRoutes(List.of(paid));
        assertEquals(committed, FleetProtectionProcessor.advance(committed, List.of()));
        var canceledFreight = state.withTradeRoutes(List.of(paid.withActive(false)));
        assertEquals(canceledFreight, FleetProtectionProcessor.advance(canceledFreight, List.of()));
        var passenger = new ShipInstance(protectedShip.id(), protectedShip.designId(), protectedShip.ownerEntityId(), 100, 0,
                protectedShip.currentFuelKg(), protectedShip.storedCargoKg(), 1, "human", "CONSCIOUS", protectedShip.powerState(), protectedShip.supplyState());
        var passengerConvoy = state.withFleets(List.of(convoy.withShips(List.of(guard.ships().getFirst(), guard.ships().getLast(), passenger)), fleet(state, "target")));
        assertEquals(passengerConvoy, FleetProtectionProcessor.advance(passengerConvoy, List.of()));
        var dry = convoy.withShips(convoy.ships().stream().map(ship -> escortIds.contains(ship.id()) ? copy(ship, ship.id(), 0) : ship).toList());
        var unfunded = state.withFleets(List.of(dry, fleet(state, "target")));
        assertEquals(unfunded, FleetProtectionProcessor.advance(unfunded, List.of()));
    }

    @Test void sharedCorporateSensorGrantEnablesPursuitWithoutSharingCombatForcesOrBlueprints() throws Exception {
        var state = world();
        state = state.withShipDesigns(state.shipDesigns().stream().map(design -> !"owner".equals(design.ownerEntityId()) ? design
                : new ShipDesign(design.id(), design.name(), design.ownerEntityId(), design.role(), design.hullMaterialId(),
                design.equippedModuleIds(), design.armorMaterialId(), design.armorThicknessCm(), design.totalDryMassKg(),
                design.maxCargoMassKg(), design.fuelCapacityKg(), design.powerBalanceKw(), design.calculatedStructuralIntegrity(),
                design.minLaunchThrustRequiredN(), design.totalThrustN(), design.isValidForLaunch(), design.isProprietaryCorporateDesign(),
                design.manufacturingProfile(), design.powerProfile(), new ShipSensorProfile(0, 0))).toList());
        var corporation = new Corporation("global", "Global", "", "earth", "TRANSPORT", 1000, List.of(), List.of(), List.of());
        var module = new StationModule("sensor", "Sensor", SensorModuleCatalog.BASIC, 1, 100, 1, 0, Map.of(), "engineer", 0, true);
        var observer = new OrbitalStation("observer", "Observer", "sol", "earth", "global", OrbitalStation.OWNERSHIP_PRIVATE_CORPORATE,
                10, List.of(module), Map.of(), 100, 1, 0, 0, 100, 100, "steel", 0, true).withParkingAltitudeKm(500.0);
        state = state.toBuilder().corporations(List.of(corporation)).orbitalStations(List.of(observer)).build();
        assertEquals(FleetProtectionProcessor.Outcome.NO_CONTACT, FleetProtectionProcessor.decide(state, fleet(state, "hunter"), List.of()).outcome());
        var designs = state.shipDesigns();
        state = new ProposeDiplomaticPactCommand("global", "owner", DiplomaticPact.SENSOR_DATA_SHARING).apply(state);
        var grant = state.diplomaticProposals().getLast();
        state = new ResolveDiplomaticProposalCommand(grant.id(), "owner", true).apply(state);
        assertEquals(FleetProtectionProcessor.Outcome.INTERCEPTING, FleetProtectionProcessor.decide(state, fleet(state, "hunter"), List.of()).outcome());
        assertEquals(List.of("hunter"), FleetThreatAssessment.assess(state, fleet(state, "hunter")).friendlyFleetIds());
        assertEquals(designs, FleetProtectionProcessor.advance(state, List.of()).shipDesigns());
        var revoked = new RevokeSensorSharingCommand(grant.id(), "owner").apply(state);
        assertEquals(revoked, FleetProtectionProcessor.advance(revoked, List.of()));
        var departed = FleetProtectionProcessor.advance(state, List.of());
        var blind = new RevokeSensorSharingCommand(grant.id(), "owner").apply(departed);
        var canceled = FleetInterceptProcessor.advance(blind);
        assertEquals(FleetProtectionAssignment.Stage.RETURNING, fleet(canceled, "hunter").interceptOrder().protection().stage());
        assertEquals(fleet(blind, "hunter").location(), fleet(canceled, "hunter").location());
        assertEquals(fleet(blind, "hunter").ships(), fleet(canceled, "hunter").ships());
    }
}
