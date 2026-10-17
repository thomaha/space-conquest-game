package com.spaceconquest.control;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.PowerGridState;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AIWeaponMissionRecoveryTest {
    @TempDir Path directory;
    private final AICombatServiceTest fixture = new AICombatServiceTest();
    private final ServiceAICombatFleetCommand service = new ServiceAICombatFleetCommand("ai", "fleet");
    private Fleet fleet(GameState state) { return FleetOrganization.find(state, "fleet"); }
    private ShipInstance ship(GameState state) { return fleet(state).ships().getFirst(); }
    private GameState guard(String station) {
        double upkeep = 2 + CombatModuleCatalog.module(CombatModuleCatalog.LASER).powerDrawKw();
        var state = fixture.world(CombatModuleCatalog.LASER, upkeep, FleetLocation.Site.docked(station));
        return new GuardObjectCommand("ai", "fleet", FleetGuardOrder.TargetType.STATION, station).apply(state);
    }
    private GameState arrive(GameState state) {
        int ticks = 0;
        while (fleet(state).location().inTransit() && ticks++ < 10) {
            state = state.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state),
                    state.orbitalStations(), state.diplomaticRelations())).withTurn(state.turn() + 1);
        }
        assertTrue(ticks < 10); assertFalse(fleet(state).location().inTransit());
        return state;
    }

    @Test void guardDetourPaysForTravelAndChargeBeforeReturningToTheOriginalMission() {
        var initial = guard("home"); var guard = fleet(initial).guardOrder(); assertNotNull(guard);
        var queue = new CommandQueue(); var receipt = queue.submitTracked(service);
        assertNull(fleet(initial).interceptOrder());
        var departed = queue.drainAndExecute(initial);
        assertEquals(CommandOutcome.EXECUTED, receipt.join()); assertTrue(fleet(departed).location().inTransit());
        var assignment = fleet(departed).interceptOrder().protection();
        assertTrue(assignment.weaponService()); assertEquals(guard, assignment.repairIntent().guard());
        assertEquals("port", assignment.serviceTargetId()); assertNull(fleet(departed).guardOrder());
        assertEquals(initial.powerGrids(), departed.powerGrids()); assertEquals(initial.empires(), departed.empires());
        var arrived = arrive(departed);
        assertEquals(FleetLocation.Site.docked("port"), fleet(arrived).location().current());
        assertTrue(ship(arrived).currentFuelKg() < ship(initial).currentFuelKg());
        var paid = service.apply(arrived);
        assertTrue(paid.powerGrids().getFirst().currentStoredKwh() < initial.powerGrids().getFirst().currentStoredKwh());
        assertFalse(FleetWeaponRecovery.needsBeforeReturn(paid, fleet(paid)));
        var returning = FleetProtectionReturn.advance(paid); assertTrue(fleet(returning).location().inTransit());
        var completed = FleetProtectionReturn.advance(arrive(returning));
        assertEquals(guard, fleet(completed).guardOrder()); assertNull(fleet(completed).interceptOrder());
        assertFalse(FleetWeaponRecovery.needs(completed, fleet(completed)));
        assertTrue(ship(completed).currentFuelKg() < ship(arrived).currentFuelKg());
    }

    @Test void lostSuppliesDuringTheJourneyHoldSavedIntentUntilActualServiceReturns() {
        var initial = guard("home"); var originalGrid = initial.powerGrids();
        var interrupted = service.apply(initial).toBuilder().powerGrids(List.of()).build();
        var waiting = FleetProtectionReturn.advance(arrive(interrupted));
        assertEquals(FleetProtectionAssignment.Stage.RESUPPLYING, fleet(waiting).interceptOrder().protection().stage());
        assertFalse(fleet(waiting).location().inTransit()); assertNull(fleet(waiting).guardOrder());
        assertSame(waiting, service.apply(waiting));
        assertEquals(ship(waiting), ship(FleetProtectionReturn.advance(waiting)));
        var restoredSupply = waiting.toBuilder().powerGrids(originalGrid).build();
        var paid = service.apply(restoredSupply);
        var completed = FleetProtectionReturn.advance(arrive(FleetProtectionReturn.advance(paid)));
        assertEquals(fleet(initial).guardOrder(), fleet(completed).guardOrder());
        assertTrue(completed.powerGrids().getFirst().currentStoredKwh() < originalGrid.getFirst().currentStoredKwh());
    }

    @Test void cancelingAPausedGuardKeepsPaidMotionButCannotRestoreTheCanceledMission() {
        var initial = guard("home"); var departed = service.apply(initial);
        var canceled = new CancelGuardObjectCommand("ai", "fleet").apply(departed);
        assertEquals(fleet(departed).location(), fleet(canceled).location()); assertEquals(ship(departed), ship(canceled));
        assertNull(fleet(canceled).interceptOrder().protection().repairIntent().guard());
        var paid = service.apply(arrive(canceled));
        var completed = FleetProtectionReturn.advance(arrive(FleetProtectionReturn.advance(paid)));
        assertNull(fleet(completed).guardOrder()); assertNull(fleet(completed).interceptOrder());
        assertEquals("PASSIVE", fleet(completed).fleetStance());
        assertFalse(new CancelGuardObjectCommand("foreign", "fleet").validate(departed));
    }

    @Test void unavailableStockPausesAnEmptyGuardWithoutGrantingAmmunition() {
        var initial = fixture.world(CombatModuleCatalog.MASS_DRIVER, 10000, FleetLocation.Site.surface("earth"));
        initial = new GuardObjectCommand("ai", "fleet", FleetGuardOrder.TargetType.BODY, "earth").apply(initial);
        initial = initial.toBuilder().commercialHubs(List.of()).build();
        var waiting = service.apply(initial);
        assertTrue(FleetWeaponRecovery.active(fleet(waiting))); assertNull(fleet(waiting).guardOrder());
        assertEquals(ship(initial), ship(waiting)); assertEquals(initial.empires(), waiting.empires());
        assertFalse(fleet(waiting).location().inTransit()); assertSame(waiting, service.apply(waiting));
        assertNull(fleet(FleetProtectionReturn.advance(waiting)).guardOrder());
    }

    private GameState pursuit() throws Exception {
        var initial = fixture.world(CombatModuleCatalog.LASER, 2 + CombatModuleCatalog.module(CombatModuleCatalog.LASER).powerDrawKw(),
                FleetLocation.Site.surface("earth"));
        var mapper = new ObjectMapper(); ObjectNode blueprint = mapper.valueToTree(initial.shipDesigns().getFirst());
        ((com.fasterxml.jackson.databind.node.ArrayNode) blueprint.get("equippedModuleIds")).add(SensorModuleCatalog.BASIC);
        blueprint.set("sensorProfile", mapper.valueToTree(ShipSensorProfile.capture(List.of(SensorModuleCatalog.module(SensorModuleCatalog.BASIC)))));
        initial = initial.withShipDesigns(List.of(mapper.treeToValue(blueprint, ShipDesign.class)));
        var hunter = fleet(initial).withInterceptOrder(new FleetInterceptOrder("ai", "enemy", "sol"));
        var target = new Fleet("enemy", "Enemy", "other", "sol", "", 0, 0, 0, false, "PASSIVE",
                List.of(new ShipInstance("enemy-ship", "design", "other", 1000, 0, 100, Map.of())),
                FleetLocation.at(FleetLocation.Site.surface("earth")));
        return initial.withFleets(List.of(hunter, target)).toBuilder()
                .diplomaticRelations(List.of(new DiplomaticRelation("ai", "other", "TOTAL_WAR", 0)))
                .powerGrids(List.of(new PowerGridState("earth", 100, 0, 100, 1000, 200, false))).build();
    }

    @Test void pursuitRestoresOnlyWithCurrentContactAndCancellationRemovesSavedTarget() throws Exception {
        var initial = pursuit(); assertTrue(FleetInterceptProcessor.contact(initial, fleet(initial), FleetOrganization.find(initial, "enemy")));
        var paid = service.apply(initial);
        assertEquals("enemy", fleet(paid).interceptOrder().protection().repairIntent().intercept().targetFleetId());
        var resumed = FleetProtectionReturn.advance(paid);
        assertEquals("enemy", fleet(resumed).interceptOrder().targetFleetId()); assertNull(fleet(resumed).interceptOrder().protection());
        var canceled = new CancelInterceptFleetCommand("ai", "fleet").apply(paid);
        assertEquals(ship(paid), ship(canceled));
        assertNull(fleet(FleetProtectionReturn.advance(canceled)).interceptOrder());
        var peace = paid.toBuilder().diplomaticRelations(List.of()).build();
        assertNull(fleet(FleetProtectionReturn.advance(peace)).interceptOrder());
        var lost = paid.withFleets(List.of(fleet(paid)));
        assertNull(fleet(FleetProtectionReturn.advance(lost)).interceptOrder());
    }

    @Test void escortMembershipRestoresAndDetachmentDoesNotReviveIt() {
        var initial = guard("port"); var escortShip = new ShipInstance("protected", "design", "ai", 1000, 0, 100, Map.of())
                .withPowerState(ship(initial).powerState());
        var escort = new FleetEscortOrder("ai", "escort", "Escort", "PASSIVE", List.of("ship"));
        initial = initial.withFleets(List.of(fleet(initial).withGuardOrder(null).withShips(List.of(ship(initial), escortShip))
                .withStance("ESCORT").withEscortOrder(escort)));
        var first = service.apply(initial); assertEquals(escort, fleet(first).interceptOrder().protection().repairIntent().escort());
        var paid = service.apply(first);
        assertFalse(FleetWeaponRecovery.needs(paid, fleet(paid)));
        var resumed = FleetProtectionReturn.advance(paid); assertEquals(escort, fleet(resumed).escortOrder());
        var canceled = new CancelEscortFleetCommand("ai", "fleet").apply(first);
        assertNotNull(FleetOrganization.find(canceled, "escort"));
        var finished = FleetProtectionReturn.advance(canceled);
        assertNull(fleet(finished).escortOrder()); assertNull(FleetOrganization.find(finished, "escort").escortOrder());
        assertEquals(ship(first), FleetOrganization.find(finished, "escort").ships().getFirst());
    }

    @Test void escapeAndSaveReloadRetainServicePurposeAndMissingFlagsDefaultOff() throws Exception {
        var departed = service.apply(guard("home")); var assignment = fleet(departed).interceptOrder().protection();
        assertTrue(assignment.afterEscape().weaponService()); assertEquals(assignment.repairIntent(), assignment.afterEscape().repairIntent());
        var manager = new SaveGameManager(directory); var file = directory.resolve("weapon-service.scsave").toFile();
        manager.save(file, departed, 1, "2027-01-01T08:00:00");
        var save = manager.load(file); assertEquals(SaveGame.CURRENT_VERSION, save.version());
        var loaded = save.toGameState(departed.turn(), "RUNNING");
        assertEquals(assignment, fleet(loaded).interceptOrder().protection()); assertEquals(ship(departed), ship(loaded));
        var paid = service.apply(arrive(loaded));
        var completed = FleetProtectionReturn.advance(arrive(FleetProtectionReturn.advance(paid)));
        assertEquals(assignment.repairIntent().guard(), fleet(completed).guardOrder());
        var mapper = new ObjectMapper(); ObjectNode legacy = (ObjectNode) mapper.readTree(file);
        legacy.put("version", com.spaceconquest.engine.SaveGame.CURRENT_VERSION);
        var oldAssignment = (ObjectNode) legacy.get("fleets").get(0).get("interceptOrder").get("protection");
        oldAssignment.remove("weaponService"); oldAssignment.put("stage", "RETURNING");
        var oldFile = directory.resolve("legacy.scsave").toFile(); mapper.writeValue(oldFile, legacy);
        var old = manager.load(oldFile).toGameState(departed.turn(), "RUNNING");
        assertFalse(fleet(old).interceptOrder().protection().weaponService());
    }

    @Test void aReadyOpeningSalvoDoesNotResumeUntilTheReturnJourneyIsAlsoFunded() throws Exception {
        var initial = guard("home"); var recovering = FleetWeaponRecovery.pause(fleet(initial));
        initial = FleetReturnTravel.replace(initial, recovering.withLocation(FleetLocation.at(FleetLocation.Site.docked("port"))));
        var mapper = new ObjectMapper(); ObjectNode blueprint = mapper.valueToTree(initial.shipDesigns().getFirst());
        ((ObjectNode) blueprint.get("powerProfile")).put("fissionKw", initial.shipDesigns().getFirst().powerProfile().hotelKw() - 1);
        initial = initial.withShipDesigns(List.of(mapper.treeToValue(blueprint, ShipDesign.class)));
        var power = ship(initial).powerState();
        var charge = new ShipPowerState(power.generatorMaterialsKg(), power.chemicalMixture(), power.reactorFuel(),
                10, false, 1, 1, 0, 0, 0, 0, 0);
        initial = ShipPowerResupply.replace(initial, ship(initial).withPowerState(charge));
        assertFalse(FleetWeaponRecovery.needs(initial, fleet(initial)));
        assertTrue(FleetWeaponRecovery.needsBeforeReturn(initial, fleet(initial)));
        var waiting = FleetProtectionReturn.advance(initial); assertFalse(fleet(waiting).location().inTransit());
        assertNull(fleet(waiting).guardOrder());
        var paid = service.apply(waiting);
        assertTrue(ship(paid).powerState().batteryChargeKwh() > ship(waiting).powerState().batteryChargeKwh());
        assertFalse(FleetWeaponRecovery.needsBeforeReturn(paid, fleet(paid)));
        var completed = FleetProtectionReturn.advance(arrive(FleetProtectionReturn.advance(paid)));
        assertNotNull(fleet(completed).guardOrder()); assertFalse(FleetWeaponRecovery.needs(completed, fleet(completed)));
    }
}
