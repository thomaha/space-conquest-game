package com.spaceconquest.control;

import com.spaceconquest.control.ai.*;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.galaxy.FogOfWarState;
import com.spaceconquest.engine.ship.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AIInterstellarDeploymentTest {
    @TempDir Path directory;
    private GameState world(boolean warp) {
        var state = new AICombatServiceTest().world(CombatModuleCatalog.MASS_DRIVER, 10000, FleetLocation.Site.deepSpace());
        var empire = state.empires().getFirst(); var research = warp ? List.of("warp") : List.<String>of();
        state = state.withEmpires(List.of(new Empire("ai", empire.name(), empire.raceId(), empire.societyStructure(), 1000000, 0, List.of("sol", "b"),
                empire.ministries(), empire.systemGovernorAssignments(), research, List.of())));
        var colony = new Planet("frontier", "Frontier colony", "", 1, 9.81, 1, 0, 12742, "terrestrial", "breathable", true, 1, List.of(), List.of(), List.of(new Population("human", Map.of(25, 1000L))));
        var systems = new ArrayList<>(state.solarSystems()); systems.add(new SolarSystem("b", "Frontier", "", 1e6 / InterstellarTravel.METERS_PER_LIGHT_YEAR, 0, 0, 1, 1, "Yellow", List.of(colony), List.of()));
        var source = state.fleets().getFirst(); var ship = source.ships().getFirst(); var module = CombatModuleCatalog.module(CombatModuleCatalog.MASS_DRIVER);
        ship = new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), ship.currentHullHealth(), ship.currentShieldHealth(), 1000,
                Map.of(module.ammunitionId(), 1000.0)).withPowerState(new ShipPowerState(Map.of("refined_uranium", 10.0), "rp1", "uranium", 100, false, 1, 1, 0, 0, 0, 0, 0));
        source = source.withShips(List.of(ship));
        var targetShip = new ShipInstance("target-ship", "design", "ai", 1000, 0, 1000, Map.of(module.ammunitionId(), 1000.0)).withPowerState(ship.powerState());
        var target = new Fleet("target", "Frontier fleet", "ai", "b", "", 0, 0, 0, false, "PASSIVE", List.of(targetShip), FleetLocation.at(FleetLocation.Site.deepSpace()));
        var enemy = new Fleet("enemy-fleet", "Detected enemy", "enemy", "b", "", 0, 0, 0, false, "PASSIVE", List.of(new ShipInstance("enemy-ship", "enemy-design", "enemy", 1000, 0, 0, Map.of())));
        return state.toBuilder().solarSystems(systems).fleets(List.of(source, target, enemy))
                .systemEconomies(List.of(com.spaceconquest.engine.economy.SystemEconomy.createDefault("b", "ai", 1000)))
                .diplomaticRelations(List.of(new DiplomaticRelation("ai", "enemy", "TOTAL_WAR", 0)))
                .fogOfWarStates(List.of(new FogOfWarState("ai", List.of("sol", "b"), List.of(), List.of("enemy-fleet"), List.of(), List.of()))).build();
    }
    private GameState pending(GameState state) {
        return state.toBuilder().shipyardLedger(state.shipyardLedger().deployment("ship", new ShipDeploymentIntent("ai", ShipDeploymentIntent.Kind.REINFORCEMENT,
                "target", "", 0, "Planned frontier reinforcement"))).build();
    }
    @Test void knownFrontierNeedOutranksLocalDefenseAndCommittedShipsReduceFurtherDemand() {
        var state = world(true); var design = state.shipDesigns().getFirst();
        var selected = AIShipDeploymentIntent.choose(state, design, "sol", "port"); assertEquals("target", selected.targetId());
        var ledger = state.shipyardLedger();
        for (int count = 0; count < 4; count++) ledger = ledger.deployment("incoming-" + count, new ShipDeploymentIntent("ai", ShipDeploymentIntent.Kind.REINFORCEMENT, "target", "", 0, "Committed"));
        var covered = state.toBuilder().shipyardLedger(ledger).build();
        assertNotEquals("target", AIShipDeploymentIntent.choose(covered, design, "sol", "port").targetId());
    }
    @Test void warpDeploymentPreservesPaidStoresAndMissionThroughDiskReloadAndActualArrival() throws Exception {
        var initial = world(true);
        initial = pending(initial.withFleets(initial.fleets().stream().filter(fleet -> !fleet.id().equals("enemy-fleet")).toList())); var command = new DeployAIShipCommand("ai", "ship");
        assertFalse(FleetWeaponRecovery.needs(initial, initial.fleets().getFirst()));
        var departed = command.apply(initial); assertEquals("b", FleetOrganization.find(departed, "fleet").targetSystemId());
        assertEquals("b", departed.shipyardLedger().deployments().get("ship").transitSystemId());
        var engine = new SpaceConquestEngine(42); engine.applyGameState(departed); engine.stepTurn();
        var crossing = engine.getGameState(); assertTrue(FleetOrganization.find(crossing, "fleet").hasInterstellarOrder());
        crossing = command.apply(crossing); assertTrue(crossing.shipyardLedger().deployments().containsKey("ship"));
        var file = directory.resolve("crossing.scsave").toFile(); var saves = new SaveGameManager(directory);
        saves.save(file, crossing, 1, GameClock.START_TIME.plusDays(crossing.turn()).toString()); var restored = saves.load(file).toGameState(crossing.turn(), crossing.status());
        assertEquals(crossing.shipyardLedger(), restored.shipyardLedger()); engine.applyGameState(restored);
        for (int day = 0; day < 8 && FleetOrganization.find(engine.getGameState(), "fleet").hasInterstellarOrder(); day++) engine.stepTurn();
        var arrived = engine.getGameState(); assertEquals("b", FleetOrganization.find(arrived, "fleet").currentSystemId());
        assertTrue(arrived.empires().getFirst().controlledSystemIds().contains("b"), arrived.empires().toString());
        assertTrue(AIShipDeploymentIntent.idle(FleetOrganization.find(arrived, "target")), FleetOrganization.find(arrived, "target").toString());
        for (int step = 0; step < 4 && arrived.shipyardLedger().deployments().containsKey("ship"); step++) arrived = command.apply(arrived);
        assertTrue(arrived.shipyardLedger().deployments().isEmpty(), arrived.shipyardLedger().deployments().toString()); assertNull(FleetOrganization.find(arrived, "fleet"));
        var reinforced = FleetOrganization.find(arrived, "target"); assertEquals(2, reinforced.ships().size()); assertFalse(FleetWeaponRecovery.needs(arrived, reinforced));
        assertTrue(reinforced.ships().stream().filter(ship -> ship.id().equals("ship")).findFirst().orElseThrow().generatorFuelMassKg()
                < initial.fleets().getFirst().ships().getFirst().generatorFuelMassKg());
    }
    @Test void unreadyArrivalPreventsDepartureWithoutBuyingOrGrantingStores() {
        var state = pending(world(true)); var source = state.fleets().getFirst(); var ship = source.ships().getFirst();
        var power = ship.powerState(); ship = ship.withPowerState(new ShipPowerState(Map.of("refined_uranium", .1), power.chemicalMixture(), power.reactorFuel(), 0, false, 1, 1, 0, 0, 0, 0, 0));
        state = FleetReturnTravel.replace(state, source.withShips(List.of(ship))).withCommercialHubs(List.of());
        var waiting = new DeployAIShipCommand("ai", "ship").apply(state);
        assertFalse(FleetOrganization.find(waiting, "fleet").hasInterstellarOrder()); assertEquals(state.fleets(), waiting.fleets()); assertEquals(state.empires(), waiting.empires());
        assertTrue(waiting.shipyardLedger().deployments().containsKey("ship"));
    }
    @Test void crossingPreparationBuysFiniteGeneratorStoresBeforeDeparture() {
        var state = pending(world(true)); var fleet = state.fleets().getFirst(); var ship = fleet.ships().getFirst();
        ship = ship.withPowerState(new ShipPowerState(Map.of("refined_uranium", .5), "rp1", "uranium", 100, false, 1, 1, 0, 0, 0, 0, 0));
        fleet = new Fleet(fleet.id(), fleet.name(), fleet.ownerEntityId(), "sol", "", 0, 0, 0, false, "PASSIVE", List.of(ship), FleetLocation.at(FleetLocation.Site.docked("port")));
        state = FleetReturnTravel.replace(state, fleet); var command = new DeployAIShipCommand("ai", "ship");
        var prepared = command.apply(state);
        assertTrue(prepared.empires().getFirst().treasuryCredits() < state.empires().getFirst().treasuryCredits());
        double purchased = FleetOrganization.find(prepared, "fleet").ships().getFirst().generatorFuelMassKg() - .5;
        assertTrue(purchased > 0);
        assertEquals(10 - purchased, prepared.commercialHubs().getLast().activeOrders().get("refined_uranium").supplyKg(), 1e-6);
        assertEquals(purchased * 2, state.empires().getFirst().treasuryCredits() - prepared.empires().getFirst().treasuryCredits(), 1e-6);
        assertFalse(FleetOrganization.find(prepared, "fleet").hasInterstellarOrder());
        assertTrue(FleetOrganization.find(command.apply(prepared), "fleet").hasInterstellarOrder());
    }
    @Test void changedOwnershipAndManualRetargetingDoNotAssignTheOldMission() {
        var state = pending(world(true)); var command = new DeployAIShipCommand("ai", "ship"); var departed = command.apply(state);
        var target = FleetOrganization.find(departed, "target");
        var captured = new Fleet(target.id(), target.name(), "enemy", target.currentSystemId(), "", 0, 0, 0, false, "PASSIVE", target.ships());
        departed = departed.withFleets(departed.fleets().stream().map(fleet -> fleet.id().equals("target") ? captured : fleet).toList());
        var inTransit = command.apply(departed); assertTrue(inTransit.shipyardLedger().deployments().containsKey("ship"));
        // An explicit replacement itinerary is authoritative and releases the AI's commissioning reservation.
        var old = FleetOrganization.find(inTransit, "fleet");
        var overridden = new Fleet(old.id(), old.name(), old.ownerEntityId(), old.currentSystemId(), "other", 0, 0, 0, true, "PASSIVE", old.ships(), old.location(),
                Fleet.MODE_WARP, 4, 0, 0, 0, 0, Map.of(), null);
        var explicit = FleetReturnTravel.replace(inTransit, overridden); var released = command.apply(explicit);
        assertTrue(released.shipyardLedger().deployments().isEmpty()); assertEquals(explicit.fleets(), released.fleets());
    }
    @Test void sublightCrossingUsesPhysicalPropellantAndAnInterruptedItineraryKeepsItsMission() {
        var state = pending(world(false)); var command = new DeployAIShipCommand("ai", "ship"); var departed = command.apply(state);
        var fleet = FleetOrganization.find(departed, "fleet"); assertTrue(fleet.hasInterstellarOrder()); assertEquals(Fleet.MODE_SUBLIGHT, fleet.interstellarMode());
        assertTrue(fleet.interstellarFuelBudgetKg().get("ship") > 0);
        var paused = new Fleet(fleet.id(), fleet.name(), fleet.ownerEntityId(), fleet.currentSystemId(), fleet.targetSystemId(), fleet.coordinateX(), fleet.coordinateY(), fleet.transitProgress(), false,
                fleet.fleetStance(), fleet.ships(), fleet.location(), Fleet.MODE_POWER_INTERRUPTED, fleet.interstellarTravelDays(), fleet.interstellarDistanceMeters(),
                fleet.interstellarAccelerationMps2(), fleet.interstellarElapsedDays(), fleet.interstellarPeakSpeedMps(), fleet.interstellarFuelBudgetKg(), null);
        var interrupted = FleetReturnTravel.replace(departed, paused); var retained = command.apply(interrupted);
        assertTrue(retained.shipyardLedger().deployments().get("ship").status().contains("interrupted")); assertEquals(interrupted.fleets(), retained.fleets());
    }
    @Test void targetLostDuringCrossingIsRevalidatedAfterActualArrival() {
        var initial = world(true);
        initial = pending(initial.withFleets(initial.fleets().stream().filter(fleet -> !fleet.id().equals("enemy-fleet")).toList()));
        var command = new DeployAIShipCommand("ai", "ship"); var departed = command.apply(initial);
        // The destination fleet disappears while the physical crossing is underway.
        departed = departed.withFleets(departed.fleets().stream().filter(fleet -> !fleet.id().equals("target")).toList());
        var engine = new SpaceConquestEngine(42); engine.applyGameState(departed);
        for (int day = 0; day < 8 && FleetOrganization.find(engine.getGameState(), "fleet").hasInterstellarOrder(); day++) engine.stepTurn();
        var arrived = engine.getGameState(); assertEquals("b", FleetOrganization.find(arrived, "fleet").currentSystemId());
        assertTrue(arrived.empires().getFirst().controlledSystemIds().contains("b"), arrived.empires().toString());
        var marked = command.apply(arrived); var revalidated = command.apply(marked);
        assertTrue(revalidated.shipyardLedger().deployments().containsKey("ship"));
        assertNotEquals("target", revalidated.shipyardLedger().deployments().get("ship").targetId(), revalidated.shipyardLedger().deployments().toString());
        assertEquals(arrived.fleets(), revalidated.fleets());
        assertEquals(arrived.empires(), revalidated.empires());
    }
}
