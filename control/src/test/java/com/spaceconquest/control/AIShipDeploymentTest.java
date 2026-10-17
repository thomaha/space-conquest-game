package com.spaceconquest.control;

import com.spaceconquest.control.ai.*;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.ship.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AIShipDeploymentTest {
    @TempDir Path directory;
    private GameState pending(GameState state, ShipDeploymentIntent.Kind kind, String target) {
        return state.toBuilder().shipyardLedger(state.shipyardLedger().deployment("ship",
                new ShipDeploymentIntent("ai", kind, target, "", state.turn(), "Awaiting deployment"))).build();
    }
    private GameState military() {
        var state = new AICombatServiceTest().world(CombatModuleCatalog.MASS_DRIVER, 10000, FleetLocation.Site.docked("port"));
        var hub = state.commercialHubs().getLast(); var stock = new HashMap<>(hub.activeOrders()); var weapon = CombatModuleCatalog.module(CombatModuleCatalog.MASS_DRIVER);
        stock.put(weapon.ammunitionId(), new MarketOrder(weapon.ammunitionId(), 1000, 0, 2, 0));
        stock.put("rp1_kerosene", new MarketOrder("rp1_kerosene", 10000, 0, 1, 0)); stock.put("liquid_oxygen", new MarketOrder("liquid_oxygen", 10000, 0, 1, 0));
        state = state.withCommercialHubs(List.of(state.commercialHubs().getFirst(), hub.withInventory(0, 30000, stock)));
        return pending(state, ShipDeploymentIntent.Kind.DEFENSE, "port");
    }
    @Test void combatPreparationPaysForFiniteStoresBeforeAssigningSavedDefense() throws Exception {
        var state = military(); var command = new DeployAIShipCommand("ai", "ship"); double opening = state.empires().getFirst().treasuryCredits();
        assertTrue(EmpireFleetPlanner.plan(state, state.empires().getFirst()).isEmpty());
        state = command.apply(state); assertNull(state.fleets().getFirst().guardOrder()); assertTrue(state.shipyardLedger().deployments().containsKey("ship"));
        assertTrue(state.empires().getFirst().treasuryCredits() < opening);
        var file = directory.resolve("preparing.scsave").toFile(); var saves = new SaveGameManager(directory);
        saves.save(file, state, 1, GameClock.START_TIME.toString()); state = saves.load(file).toGameState(0, state.status());
        for (int step = 0; step < 8 && state.shipyardLedger().deployments().containsKey("ship"); step++) state = command.apply(state);
        var fleet = state.fleets().getFirst(); assertNotNull(fleet.guardOrder()); assertEquals("port", fleet.guardOrder().targetId());
        assertFalse(FleetWeaponRecovery.needs(state, fleet)); assertTrue(fleet.ships().getFirst().currentFuelKg() >= 250);
        assertTrue(fleet.ships().getFirst().storedCargoKg().getOrDefault(CombatModuleCatalog.module(CombatModuleCatalog.MASS_DRIVER).ammunitionId(), 0.0) > 0);
        assertTrue(state.shipyardLedger().deployments().isEmpty()); assertSame(state, command.apply(state));
    }
    @Test void unavailableSuppliesKeepTheMissionReservedWithoutFreeReadiness() {
        var state = military().withCommercialHubs(List.of()); var command = new DeployAIShipCommand("ai", "ship");
        var waiting = command.apply(state); assertTrue(waiting.shipyardLedger().deployments().containsKey("ship"));
        assertEquals(state.empires(), waiting.empires()); assertEquals(state.fleets(), waiting.fleets()); assertNull(waiting.fleets().getFirst().guardOrder());
        assertTrue(FleetWeaponRecovery.needs(waiting, waiting.fleets().getFirst()));
        assertTrue(AIShipDeploymentPlanner.plan(waiting, waiting.empires().getFirst()).isEmpty());
    }
    @Test void changedFriendlyTargetFallsBackToOwnedDefenseAndExplicitOrdersWin() {
        var state = pending(military(), ShipDeploymentIntent.Kind.REINFORCEMENT, "gone"); var command = new DeployAIShipCommand("ai", "ship");
        for (int step = 0; step < 10 && state.shipyardLedger().deployments().containsKey("ship"); step++) state = command.apply(state);
        assertEquals("port", state.fleets().getFirst().guardOrder().targetId()); assertTrue(state.shipyardLedger().deployments().isEmpty());
        var explicit = new GuardObjectCommand("ai", "fleet", FleetGuardOrder.TargetType.STATION, "port").apply(military());
        var respected = command.apply(explicit); assertEquals(explicit.fleets(), respected.fleets()); assertEquals(explicit.empires(), respected.empires());
        assertTrue(respected.shipyardLedger().deployments().isEmpty());
    }
    @Test void publicAndCorporateBuildIntentSurvivesCommissioningAndPaidSupplyDeparture() throws Exception {
        for (boolean corporate : List.of(false, true)) {
            String owner = corporate ? "corp" : "owner"; String design = corporate ? "corporate-design" : "design";
            var initial = new AITransportConstructionTest().world(corporate).withTurn(3);
            var build = new RequestAIFacilityTransportBuildCommand(owner, design, "a", "destination", "steel", 0); assertTrue(build.validate(initial));
            var ordered = build.apply(initial); assertEquals(ShipDeploymentIntent.Kind.SUPPLY, ordered.shipConstructionOrders().getFirst().deployment().kind());
            var engine = new SpaceConquestEngine(42); engine.applyGameState(ordered);
            for (int day = 0; day < 10 && !engine.getGameState().shipConstructionOrders().isEmpty(); day++) engine.stepTurn();
            var commissioned = engine.getGameState(); var ship = commissioned.fleets().getFirst().ships().getFirst();
            assertTrue(commissioned.shipyardLedger().deployments().containsKey(ship.id())); assertSame(commissioned, build.apply(commissioned));
            assertFalse(new AssignAIShipServiceCommand(owner, commissioned.fleets().getFirst().id()).validate(commissioned));
            var file = directory.resolve(owner + ".scsave").toFile(); var saves = new SaveGameManager(directory);
            saves.save(file, commissioned, 1, GameClock.START_TIME.plusDays(commissioned.turn()).toString()); var restored = saves.load(file).toGameState(commissioned.turn(), commissioned.status());
            var queue = new CommandQueue(); var deploy = new DeployAIShipCommand(owner, ship.id()); queue.submit(deploy); queue.submit(deploy);
            var dispatched = queue.drainAndExecute(restored); assertEquals(1, dispatched.tradeRoutes().size()); assertTrue(dispatched.shipyardLedger().deployments().isEmpty());
            assertEquals(owner, dispatched.tradeRoutes().getFirst().ownerEntityId()); assertTrue(com.spaceconquest.engine.economy.EconomicOwnership.cash(dispatched, owner)
                    < com.spaceconquest.engine.economy.EconomicOwnership.cash(restored, owner));
            engine.applyGameState(dispatched); double output = 0;
            for (int day = 0; day < 25; day++) { engine.stepTurn(); output += engine.getGameState().industryAccounts().stream().mapToDouble(account -> account.producedKg().getOrDefault("cannon_round", 0.0)).sum(); }
            assertTrue(output > 0); assertFalse(engine.getGameState().tradeRoutes().getFirst().carrierCommitted());
        }
    }
    @Test void readyReinforcementsAndEscortsJoinTheirOwnedTargetsWithoutDuplicatingShips() {
        for (var kind : List.of(ShipDeploymentIntent.Kind.REINFORCEMENT, ShipDeploymentIntent.Kind.ESCORT)) {
            var state = military(); var fleet = state.fleets().getFirst(); var ship = fleet.ships().getFirst();
            var targetShip = new ShipInstance("target-ship", ship.designId(), "ai", 1000, 0, 100, Map.of());
            var target = new Fleet("target", "Target", "ai", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(targetShip), fleet.location());
            state = pending(state.withFleets(List.of(fleet, target)), kind, "target"); var command = new DeployAIShipCommand("ai", "ship");
            for (int step = 0; step < 10 && state.shipyardLedger().deployments().containsKey("ship"); step++) state = command.apply(state);
            assertEquals(1, state.fleets().size()); assertEquals("target", state.fleets().getFirst().id()); assertEquals(2, state.fleets().getFirst().ships().size());
            assertTrue(state.shipyardLedger().deployments().isEmpty()); assertSame(state, command.apply(state));
            if (kind == ShipDeploymentIntent.Kind.ESCORT) assertNotNull(state.fleets().getFirst().escortOrder());
        }
    }
    @Test void resolvedSupplyNeedRevalidatesToAnActuallyPaidTradingDeparture() throws Exception {
        var state = new AITransportConstructionTest().world(true).withTurn(3);
        state = new RequestAIFacilityTransportBuildCommand("corp", "corporate-design", "a", "destination", "steel", 0).apply(state);
        var engine = new SpaceConquestEngine(42); engine.applyGameState(state);
        for (int day = 0; day < 10 && !engine.getGameState().shipConstructionOrders().isEmpty(); day++) engine.stepTurn();
        state = engine.getGameState().toBuilder().industrialFacilities(List.of()).build(); var ship = state.fleets().getFirst().ships().getFirst();
        var command = new DeployAIShipCommand("corp", ship.id()); var retargeted = command.apply(state);
        assertEquals(ShipDeploymentIntent.Kind.TRADE, retargeted.shipyardLedger().deployments().get(ship.id()).kind()); assertEquals(state.fleets(), retargeted.fleets());
        var dispatched = command.apply(retargeted); assertTrue(dispatched.shipyardLedger().deployments().isEmpty()); assertEquals(1, dispatched.tradeRoutes().size());
        assertTrue(dispatched.tradeRoutes().getFirst().onboardKg() > 0);
        assertTrue(com.spaceconquest.engine.economy.EconomicOwnership.cash(dispatched, "corp") < com.spaceconquest.engine.economy.EconomicOwnership.cash(state, "corp"));
    }
}
