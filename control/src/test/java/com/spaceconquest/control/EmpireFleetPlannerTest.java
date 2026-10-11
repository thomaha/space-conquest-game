package com.spaceconquest.control;

import com.spaceconquest.control.ai.*;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.MarketAccount;
import com.spaceconquest.engine.governance.DiplomacyProcessor;
import com.spaceconquest.engine.macrostructure.*;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EmpireFleetPlannerTest {
    private ShipDesign design(String owner, String role) {
        return new ShipDesign(owner + role, "Ship", owner, role, "steel",
                List.of("mod_chemical_rocket", PropulsionCatalog.FUEL_TANK_MODULE_ID), "steel", .5,
                1000, 50, 200, 0, 1000, 0, 1000, true, false);
    }

    private GameState world(String role, boolean war) {
        var empire = new Empire("ai", "AI", "human", "Individualist", 1000000, 0,
                List.of("sol"), List.of(), Map.of(), List.of("rocketry"), List.of());
        var blueprint = design("ai", role);
        var module = new StationModule("slipway", "Slipway", StationModule.TYPE_CAPITAL_SLIPWAY,
                4, 10000, 10, 0, Map.of(), "engineer", 0, true);
        module = module.withPayroll(ShipyardWorkCapacity.requiredWorkers(module), 0);
        var yard = new OrbitalStation("yard", "Yard", "sol", "earth", "ai", OrbitalStation.OWNERSHIP_PUBLIC_STATE,
                20, List.of(module), Map.of(), 100, 10, 0, 0, 100, 100, "steel", 0, true);
        var destination = new OrbitalStation("destination", "Destination", "sol", "earth", "ai",
                OrbitalStation.OWNERSHIP_PUBLIC_STATE, 20, List.of(), Map.of(), 100, 10, 0, 0, 100, 100, "steel", 0, true);
        var stock = new HashMap<String, MarketOrder>();
        ShipConstructionRequirements.estimate(blueprint).materialsKg()
                .forEach((id, kg) -> stock.put(id, new MarketOrder(id, kg + 1000, 0, 1, 0)));
        PropulsionCatalog.drive("mod_chemical_rocket").propellantMaterials(100)
                .forEach((id, kg) -> stock.put(id, new MarketOrder(id, kg, 0, 1, 0)));
        var seller = new CommercialHub("seller", "yard", 0, 100000,
                stock.values().stream().mapToDouble(MarketOrder::supplyKg).sum(), 10, stock);
        var buyer = new CommercialHub("buyer", "destination", 0, 100000, 0, 10,
                Map.of("steel", new MarketOrder("steel", 0, 1000, 3, 0)));
        return GameState.builder().empires(List.of(empire)).shipDesigns(List.of(blueprint))
                .orbitalStations(List.of(yard, destination)).commercialHubs(List.of(seller, buyer))
                .marketAccounts(List.of(new MarketAccount("seller", 0), new MarketAccount("buyer", 10000)))
                .diplomaticRelations(war ? List.of(new DiplomaticRelation("ai", "other", DiplomacyProcessor.TOTAL_WAR, 0)) : List.of())
                .build();
    }

    private GameState executePlan(GameState state, boolean twice) {
        var queue = new CommandQueue();
        var commands = EmpireFleetPlanner.plan(state, state.empires().getFirst());
        commands.forEach(queue::submit);
        if (twice) commands.forEach(queue::submit);
        return queue.drainAndExecute(state);
    }

    @Test void ownTraderBuildsOnceAndEntersRoamingServiceThroughTheQueue() {
        var initial = world(ShipRole.CARGO_TRANSPORT, false);
        var queue = new CommandQueue();
        new EmpireAIController("ai", queue).onGameStateUpdate(initial);
        assertTrue(initial.shipConstructionOrders().isEmpty());
        var ordered = queue.drainAndExecute(initial);
        assertEquals(1, ordered.shipConstructionOrders().size());
        assertEquals(initial.empires().getFirst().treasuryCredits(), ordered.empires().getFirst().treasuryCredits());
        assertEquals(1, executePlan(ordered, true).shipConstructionOrders().size());
        var completed = new ShipConstructionProcessor().process(ordered);
        assertTrue(completed.shipConstructionOrders().isEmpty());
        assertEquals("ai", completed.fleets().getFirst().ships().getFirst().ownerEntityId());
        var assigned = executePlan(completed, true);
        assertEquals(1, assigned.tradeRoutes().size());
        assertTrue(assigned.tradeRoutes().getFirst().isActive());
        assertTrue(assigned.tradeRoutes().getFirst().roaming());
        assertEquals(initial.shipDesigns(), assigned.shipDesigns());
        assertTrue(EmpireFleetPlanner.plan(assigned, assigned.empires().getFirst()).isEmpty());
    }

    @Test void foreignEmpireAndCorporationBlueprintsCannotBeUsedOrCopied() {
        var initial = world(ShipRole.CARGO_TRANSPORT, false);
        var foreign = List.of(design("other", ShipRole.CARGO_TRANSPORT), design("corp", ShipRole.CARGO_TRANSPORT));
        var state = initial.toBuilder().shipDesigns(foreign).build();
        assertTrue(EmpireFleetPlanner.plan(state, state.empires().getFirst()).isEmpty());
        for (var blueprint : foreign) {
            var command = new RequestAIShipBuildCommand("ai", blueprint.id(), "sol");
            assertFalse(command.validate(state));
            assertSame(state, command.apply(state));
        }
        var mixed = initial.toBuilder().shipDesigns(List.of(foreign.getFirst(), initial.shipDesigns().getFirst(), foreign.getLast())).build();
        var ordered = executePlan(mixed, true);
        assertEquals(1, ordered.shipConstructionOrders().size());
        assertEquals(initial.shipDesigns().getFirst().id(), ordered.shipConstructionOrders().getFirst().designId());
        assertEquals(mixed.shipDesigns(), ordered.shipDesigns());
    }

    @Test void militaryNeedRequiresWarAndCompletedWarshipWaitsForExplicitOrders() {
        var peace = world(ShipRole.COMBAT_SHIP, false);
        assertTrue(EmpireFleetPlanner.plan(peace, peace.empires().getFirst()).isEmpty());
        var war = world(ShipRole.COMBAT_SHIP, true);
        var completed = new ShipConstructionProcessor().process(executePlan(war, true));
        var assigned = executePlan(completed, true);
        assertEquals("PASSIVE", assigned.fleets().getFirst().fleetStance());
        assertTrue(assigned.tradeRoutes().isEmpty());
        assertTrue(assigned.shipConstructionOrders().isEmpty());
        assertTrue(EmpireFleetPlanner.plan(assigned, assigned.empires().getFirst()).isEmpty());
    }

    @Test void queuedCommandsRecheckLiveStockWarBlueprintOwnershipAndManualStance() {
        var war = world(ShipRole.COMBAT_SHIP, true);
        var build = EmpireFleetPlanner.plan(war, war.empires().getFirst()).getFirst();
        var queue = new CommandQueue();
        queue.submit(build);
        assertTrue(queue.drainAndExecute(war.toBuilder().diplomaticRelations(List.of()).build()).shipConstructionOrders().isEmpty());
        queue.submit(build);
        assertTrue(queue.drainAndExecute(war.toBuilder().commercialHubs(List.of()).build()).shipConstructionOrders().isEmpty());
        var completed = new ShipConstructionProcessor().process(executePlan(war, false));
        var service = new AssignAIShipServiceCommand("ai", completed.fleets().getFirst().id());
        assertFalse(service.validate(completed));
        var foreignBlueprint = design("other", ShipRole.COMBAT_SHIP);
        var unavailable = completed.toBuilder().shipDesigns(List.of(foreignBlueprint)).build();
        queue.submit(service);
        assertEquals("PASSIVE", queue.drainAndExecute(unavailable).fleets().getFirst().fleetStance());
        var manual = new SetFleetStanceCommand(completed.fleets().getFirst().id(), "AGGRESSIVE").apply(completed);
        queue.submit(service);
        assertEquals("AGGRESSIVE", queue.drainAndExecute(manual).fleets().getFirst().fleetStance());
    }
}
