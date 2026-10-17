package com.spaceconquest.control;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.combat.TacticalCombatProcessor;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FleetServiceHistoryTest {
    @TempDir Path directory;
    private final AICombatServiceTest fixture = new AICombatServiceTest();
    private Fleet fleet(GameState state) { return FleetOrganization.find(state, "fleet"); }
    private GameState arrive(GameState state) {
        int ticks = 0;
        while (fleet(state).location().inTransit() && ticks++ < 10)
            state = state.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state),
                    state.orbitalStations(), state.diplomaticRelations())).withTurn(state.turn() + 1);
        assertTrue(ticks < 10); assertFalse(fleet(state).location().inTransit()); return state;
    }
    @Test void boundedCombatDepletionPaidServiceAndMissionRecoveryHaveCommittedHistory() throws Exception {
        var module = CombatModuleCatalog.module(CombatModuleCatalog.LASER);
        var initial = fixture.world(module.id(), 2 + module.powerDrawKw(), FleetLocation.Site.docked("home"));
        var original = fleet(initial).ships().getFirst();
        var power = original.powerState();
        initial = ShipPowerResupply.replace(initial, original.withPowerState(new ShipPowerState(power.generatorMaterialsKg(),
                power.chemicalMixture(), power.reactorFuel(), 20, false, 1, 1, 0, 0, 0, 0, 0)));
        initial = new GuardObjectCommand("ai", "fleet", FleetGuardOrder.TargetType.STATION, "home").apply(initial);
        var guard = fleet(initial).guardOrder();
        var targetDesign = new ShipDesign("target", "Target", "other", ShipRole.COMBAT_SHIP, "steel", List.of(),
                "steel", 0, 1000, 0, 100, 0, 10000000, 0, 0, false, false);
        var target = new Fleet("target", "Target", "other", "sol", "", 0, 0, 0, false, "PASSIVE",
                List.of(new ShipInstance("target", "target", "other", 1000000000, 0, 0, Map.of())),
                FleetLocation.at(FleetLocation.Site.docked("home")));
        int[] rounds = {0};
        var combat = new TacticalCombatProcessor(new Random(42)).resolveFleetEngagement(fleet(initial), target,
                List.of(initial.shipDesigns().getFirst(), targetDesign),
                (a, b) -> new TacticalCombatProcessor.Withdrawal(a, b, ++rounds[0] >= 10), (a, b) -> true, (a, b) -> 0);
        assertTrue(combat.roundReports().stream().anyMatch(round -> round.attackerDamageDealt() > 0));
        var depleted = initial.withFleets(List.of(combat.survivingAttackerFleet()));
        assertTrue(FleetWeaponRecovery.needs(depleted, fleet(depleted)));
        var service = new ServiceAICombatFleetCommand("ai", "fleet");
        assertTrue(service.validate(depleted)); assertTrue(depleted.fleetServiceEvents().isEmpty());
        var queue = new CommandQueue(); queue.submit(service); var departing = queue.drainAndExecute(depleted);
        assertEquals(List.of(FleetServiceEvent.Kind.MISSION_PAUSED, FleetServiceEvent.Kind.SERVICE_DEPARTURE),
                departing.fleetServiceEvents().stream().map(FleetServiceEvent::kind).toList());
        var arrived = arrive(departing); queue.submit(service); var paid = queue.drainAndExecute(arrived);
        var receipt = paid.fleetServiceEvents().getLast(); assertEquals(FleetServiceEvent.Kind.SUPPLIES_PURCHASED, receipt.kind());
        assertTrue(receipt.gridInputKwh() > 0); assertTrue(receipt.resourcesKg().isEmpty());
        assertEquals(initial.powerGrids().getFirst().currentStoredKwh() - paid.powerGrids().getFirst().currentStoredKwh(), receipt.gridInputKwh(), 1e-9);
        var completed = FleetProtectionReturn.advance(arrive(FleetProtectionReturn.advance(paid)));
        assertEquals(guard, fleet(completed).guardOrder()); assertFalse(FleetWeaponRecovery.needs(completed, fleet(completed)));
        assertEquals(List.of(FleetServiceEvent.Kind.MISSION_PAUSED, FleetServiceEvent.Kind.SERVICE_DEPARTURE,
                FleetServiceEvent.Kind.SUPPLIES_PURCHASED, FleetServiceEvent.Kind.RETURN_STARTED, FleetServiceEvent.Kind.MISSION_RESUMED),
                completed.fleetServiceEvents().stream().map(FleetServiceEvent::kind).toList());
        var engine = new SpaceConquestEngine(); engine.applyGameState(completed);
        assertEquals(completed.fleetServiceEvents(), engine.getGameState().fleetServiceEvents());
        var manager = new SaveGameManager(directory); var file = directory.resolve("service.scsave").toFile();
        manager.save(file, completed, 1, "2027-01-01T08:00:00");
        var loaded = manager.load(file).toGameState(completed.turn(), "RUNNING");
        assertEquals(completed.fleetServiceEvents(), loaded.fleetServiceEvents());
        assertThrows(UnsupportedOperationException.class, () -> loaded.fleetServiceEvents().clear());
        ObjectNode legacy = (ObjectNode) new ObjectMapper().readTree(file); legacy.put("version", com.spaceconquest.engine.SaveGame.CURRENT_VERSION); legacy.remove("fleetServiceEvents");
        var oldFile = directory.resolve("legacy.scsave").toFile(); new ObjectMapper().writeValue(oldFile, legacy);
        assertTrue(manager.load(oldFile).toGameState(completed.turn(), "RUNNING").fleetServiceEvents().isEmpty());
    }
    @Test void playerQueuedPurchaseRecordsOneFiniteReceiptAndFailedOrQuotedRequestsRecordNothing() {
        var state = fixture.world(CombatModuleCatalog.MASS_DRIVER, 10000, FleetLocation.Site.surface("earth"));
        var quote = AmmunitionResupplyQuote.inspect(state, "ai", "ship", CombatModuleCatalog.MASS_DRIVER);
        assertTrue(quote.available()); assertTrue(state.fleetServiceEvents().isEmpty());
        var queue = new CommandQueue(); queue.submit(quote.command()); queue.submit(quote.command());
        var paid = queue.drainAndExecute(state); assertEquals(1, paid.fleetServiceEvents().size());
        var receipt = paid.fleetServiceEvents().getFirst(); assertEquals(FleetServiceEvent.Kind.SUPPLIES_PURCHASED, receipt.kind());
        assertEquals(Map.of(quote.ammunitionId(), quote.quantityKg()), receipt.resourcesKg());
        assertEquals(quote.purchaseCredits(), receipt.netCreditsSpent(), 1e-9);
        var status = FleetServiceStatus.inspect(paid, fleet(paid), "ai"); assertEquals(paid.fleetServiceEvents(), status.history());
        assertEquals("No paused weapon service", status.phase());
        assertThrows(UnsupportedOperationException.class, () -> receipt.resourcesKg().clear());
        queue.submit(quote.command()); assertSame(paid, queue.drainAndExecute(paid));
    }
    @Test void statusExplainsMissingAmmunitionGridRepairMaterialsAndReturnTravelWithoutMutatingState() {
        var state = fixture.world(CombatModuleCatalog.MASS_DRIVER, 10000, FleetLocation.Site.surface("earth"));
        state = state.toBuilder().commercialHubs(List.of()).build();
        var status = FleetServiceStatus.inspect(state, fleet(state), "ai");
        assertTrue(status.blockers().stream().anyMatch(text -> text.contains("commercial hub")));
        var energy = fixture.world(CombatModuleCatalog.LASER, 36, FleetLocation.Site.docked("home"));
        energy = new GuardObjectCommand("ai", "fleet", FleetGuardOrder.TargetType.STATION, "home").apply(energy);
        energy = energy.toBuilder().powerGrids(List.of()).build();
        var waiting = new ServiceAICombatFleetCommand("ai", "fleet").apply(energy);
        var diagnostic = FleetServiceStatus.inspect(waiting, fleet(waiting), "ai");
        assertEquals("Guard home", diagnostic.mission());
        assertTrue(diagnostic.blockers().contains("Grid charge: no stored local electricity"));
        assertFalse(waiting.fleetServiceEvents().isEmpty());
        var stranded = fleet(waiting).withLocation(FleetLocation.at(FleetLocation.Site.docked("port")))
                .withShips(List.of(new ShipInstance("ship", "design", "ai", 1000, 0, 0, Map.of())
                        .withPowerState(fleet(waiting).ships().getFirst().powerState())));
        var unfunded = waiting.withFleets(List.of(stranded));
        assertTrue(FleetServiceStatus.inspect(unfunded, stranded, "ai").blockers().stream()
                .anyMatch(text -> text.startsWith("Return travel is not funded")));
        assertEquals(waiting.fleetServiceEvents(), unfunded.fleetServiceEvents());
        var damaged = fixture.world(CombatModuleCatalog.GUN, 10000, FleetLocation.Site.docked("port"));
        damaged = ShipPowerResupply.replace(damaged, fleet(damaged).ships().getFirst().withModuleDamage(new ShipModuleDamage(Map.of(0, 100.0))));
        damaged = damaged.toBuilder().commercialHubs(List.of()).build();
        assertTrue(FleetServiceStatus.inspect(damaged, fleet(damaged), "ai").blockers().stream().anyMatch(text -> text.contains("repair") && text.contains("commercial hub")));
        assertTrue(FleetServiceStatus.inspect(damaged, fleet(damaged), "foreign").history().isEmpty());
    }
}
