package com.spaceconquest.control;

import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.ship.*;
import com.spaceconquest.engine.combat.TacticalCombatProcessor;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ShipRefitOrderTest {
    @TempDir Path directory;
    private GameState world() throws Exception { return new ShipRefitQuoteTest().world(); }
    private ShipInstance ship(GameState state) { return FleetOrganization.find(state, "fleet").ships().getFirst(); }
    private GameState finish(GameState state) {
        for (int day = 1; day < 100 && !state.shipConstructionOrders().isEmpty(); day++) state = new ShipConstructionProcessor().process(state.withTurn(day));
        assertTrue(state.shipConstructionOrders().isEmpty()); return state;
    }
    @Test void tickRevalidatesAndCompletesTheSameShipWithoutFreeStoresOrRepairs() throws Exception {
        var state = world(); var original = ship(state); double cash = state.empires().getFirst().treasuryCredits();
        var quote = ShipRefitQuote.inspect(state, "owner", "ship", "laser"); var command = new QueueShipRefitCommand("owner", "ship", "laser");
        var queue = new CommandQueue(); var receipt = queue.submitTracked(command);
        assertTrue(state.shipConstructionOrders().isEmpty()); assertFalse(receipt.isDone());
        var ordered = queue.drainAndExecute(state); assertEquals(CommandOutcome.EXECUTED, receipt.join());
        assertEquals(original, ship(ordered)); assertEquals(cash, ordered.empires().getFirst().treasuryCredits());
        assertFalse(command.validate(ordered)); assertSame(ordered, command.apply(ordered));
        var finished = finish(ordered); var upgraded = ship(finished);
        assertEquals(1, finished.fleets().size()); assertEquals("ship", upgraded.id()); assertEquals("laser", upgraded.designId());
        assertEquals(original.currentHullHealth(), upgraded.currentHullHealth()); assertEquals(original.currentShieldHealth(), upgraded.currentShieldHealth());
        assertEquals(original.currentFuelKg(), upgraded.currentFuelKg()); assertEquals(original.storedCargoKg(), upgraded.storedCargoKg());
        assertEquals(original.powerState(), upgraded.powerState()); assertEquals(original.supplyState(), upgraded.supplyState());
        assertEquals(quote.retainedDamage(), upgraded.moduleDamage());
        assertEquals(quote.materialCostCredits(), cash - finished.empires().getFirst().treasuryCredits(), 1e-6);
        assertTrue(finished.shipyardLedger().history().stream().anyMatch(row -> row.status().equals("Refit completed")));
    }
    @Test void sharedYardPartialWorkAndReservationSurviveReload() throws Exception {
        var state = world(); double capacity = ShipyardWorkCapacity.forYard(state, "owner", "sol", "yard").workPerDay();
        assertTrue(capacity > 1);
        var preceding = new ShipConstructionOrder("preceding", "owner", "old", "sol", "yard", 0, capacity - 1, Map.of(), Map.of());
        state = state.withShipConstructionOrders(List.of(preceding));
        var ordered = new QueueShipRefitCommand("owner", "ship", "laser").apply(state);
        assertEquals(2, ordered.shipConstructionOrders().size());
        var partial = new ShipConstructionProcessor().process(ordered);
        var order = partial.shipConstructionOrders().getFirst(); assertNotNull(order.refit());
        assertEquals(1, order.accumulatedWorkHours(), 1e-6); assertFalse(order.consumedMaterialsKg().isEmpty()); assertEquals("old", ship(partial).designId());
        var file = directory.resolve("refit.scsave").toFile(); var saves = new SaveGameManager(directory);
        saves.save(file, partial, 1, GameClock.START_TIME.toString());
        var restored = saves.load(file).toGameState(0, partial.status()); assertEquals(partial.shipConstructionOrders(), restored.shipConstructionOrders());
        assertTrue(ShipRefitPlan.reserved(restored, "ship"));
        var source = restored.shipDesigns().getFirst(); int battery = source.equippedModuleIds().indexOf(ShipComponentCatalog.BATTERY_ID);
        int weapon = source.equippedModuleIds().indexOf(CombatModuleCatalog.GUN);
        restored = FleetReturnTravel.replace(restored, FleetOrganization.find(restored, "fleet").withShips(List.of(ship(restored)
                .withModuleDamage(new ShipModuleDamage(Map.of(battery, 80.0, weapon, 80.0))))));
        var finished = finish(restored); assertEquals("laser", ship(finished).designId()); assertEquals(2, finished.fleets().size());
        assertEquals(.2, ship(finished).moduleDamage().condition(battery), 1e-6);
        assertEquals(1, ship(finished).moduleDamage().condition(weapon), 1e-6);
    }
    @Test void shortagesPauseAndInvalidCommitmentsNeverPurchaseWork() throws Exception {
        var state = world(); var command = new QueueShipRefitCommand("owner", "ship", "laser");
        assertFalse(new QueueShipRefitCommand("other", "ship", "laser").validate(state));
        var ordered = command.apply(state); var empty = ordered.toBuilder().commercialHubs(List.of()).build();
        double cash = empty.empires().getFirst().treasuryCredits(); var paused = new ShipConstructionProcessor().process(empty);
        assertEquals(0, paused.shipConstructionOrders().getFirst().accumulatedWorkHours()); assertEquals(cash, paused.empires().getFirst().treasuryCredits());
        assertEquals("old", ship(paused).designId()); assertTrue(ShipRefitPlan.reserved(paused, "ship"));
        var recovered = paused.toBuilder().commercialHubs(state.commercialHubs()).build(); assertEquals("laser", ship(finish(recovered)).designId());
        var owner = state.empires().getFirst();
        var poor = state.withEmpires(List.of(new Empire(owner.id(), owner.name(), owner.raceId(), owner.societyStructure(), 0, owner.corporateTaxRate(),
                owner.controlledSystemIds(), owner.ministries(), owner.systemGovernorAssignments(), owner.unlockedTechIds(), owner.activeShipDesignIds())));
        assertFalse(command.validate(poor)); assertSame(poor, command.apply(poor));
    }
    @Test void refittingShipCannotMoveReceiveStoresJoinMissionsOrFire() throws Exception {
        var initial = world(); var old = ship(initial); var laser = initial.shipDesigns().getLast();
        var supplied = new ShipInstance(old.id(), laser.id(), old.ownerEntityId(), old.currentHullHealth(), 0, 0, Map.of(),
                0, "", ShipInstance.MODE_CONSCIOUS, new ShipPowerState(Map.of(), "rp1", "uranium", laser.powerProfile(old).batteryKwh(), false, 1, 1, 0, 0, 0, 0, 0), old.supplyState(), old.moduleDamage());
        assertTrue(ShipCombatPower.fundedFirepower(supplied, laser, TacticalCombatProcessor.SECONDS_PER_ROUND / 3600, true) > 0);
        initial = FleetReturnTravel.replace(initial, initial.fleets().getFirst().withShips(List.of(supplied)));
        var state = new QueueShipRefitCommand("owner", "ship", "old").apply(initial); var fleet = FleetOrganization.find(state, "fleet");
        assertTrue(ShipRefitPlan.reserved(state, "ship"));
        assertNull(LocalTravel.plan(state, fleet, FleetLocation.Site.deepSpace()));
        assertNull(InterstellarTravel.plan(state, fleet, "other"));
        assertNotNull(FleetOrganization.renameProblem(state, "owner", "fleet", "Renamed"));
        assertFalse(new LoadDockedCargoCommand("ship", "yard", "steel", 1).validate(state));
        assertSame(state, ShipPowerResupply.buy(state, "ship", "yard", "fuel", 1));
        assertFalse(FleetInterceptProcessor.militaryAvailable(state, fleet));
        var fighting = fleet.withStance("AGGRESSIVE_BRAWL");
        var enemy = new Fleet("enemy", "Enemy", "other", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(new ShipInstance("enemy-ship", "old", "other", 1000, 0, 0, Map.of())), fleet.location());
        var result = new TacticalCombatProcessor(new Random(7)).forState(state).resolveFleetEngagement(fighting, enemy, state.shipDesigns());
        assertTrue(result.roundReports().stream().flatMap(round -> round.detailedActions().stream()).noneMatch(action -> action.sourceEntityId() != null && action.sourceEntityId().equals("ship") && action.paidShots() > 0));
        assertEquals(1000, result.survivingDefenderFleet().ships().getFirst().currentHullHealth());
    }
    @Test void corporateRefitSettlesActualFeesWithoutAddingAnotherShip() throws Exception {
        var state = world(); var fixture = new ShipRefitQuoteTest();
        var client = new Corporation("corp", "Client", "owner", "yard", "TRANSPORT", 1000000, List.of(), List.of("ship"), List.of());
        var fleet = new Fleet("corporate", "Corporate", "corp", "sol", "", 0, 0, 0, false, "PASSIVE",
                List.of(new ShipInstance("ship", "old", "corp", 800, 0, 0, Map.of()).withPowerState(ShipPowerState.empty())), state.fleets().getFirst().location());
        state = state.toBuilder().corporations(List.of(client)).shipDesigns(state.shipDesigns().stream().map(fixture::corporate).toList()).fleets(List.of(fleet)).build();
        double providerCash = state.empires().getFirst().treasuryCredits(); var quote = ShipRefitQuote.inspect(state, "corp", "ship", "laser");
        assertTrue(quote.ready(), quote.blockers().toString());
        var complete = finish(new QueueShipRefitCommand("corp", "ship", "laser").apply(state));
        assertEquals(List.of("ship"), complete.corporations().getFirst().ownedShipIds());
        assertEquals("laser", complete.fleets().getFirst().ships().getFirst().designId());
        assertEquals(quote.serviceFeeCredits(), complete.empires().getFirst().treasuryCredits() - providerCash, 1e-6);
        assertEquals(quote.materialCostCredits() + quote.serviceFeeCredits(), 1000000 - complete.corporations().getFirst().liquidCapitalReserves(), 1e-6);
    }
    @Test void structuralUpgradePaysItsCapturedBillAndPreservesHullCondition() throws Exception {
        var state = world(); var source = state.shipDesigns().getFirst();
        var evaluation = ShipBlueprintFactory.evaluate(state, new ShipDesignSpecification("larger", "Larger", "owner", source.role(), source.hullMaterialId(),
                source.equippedModuleIds(), source.armorMaterialId(), source.armorThicknessCm(), 45, source.assembly().identities()));
        assertTrue(evaluation.valid(), evaluation.errors().toString());
        var designs = new ArrayList<>(state.shipDesigns()); designs.add(evaluation.design()); state = state.withShipDesigns(designs);
        var quote = ShipRefitQuote.inspect(state, "owner", "ship", "larger"); assertTrue(quote.ready(), quote.blockers().toString());
        assertTrue(quote.comparison().structuralChange()); double initialCash = state.empires().getFirst().treasuryCredits();
        var ordered = new QueueShipRefitCommand("owner", "ship", "larger").apply(state);
        assertEquals(quote.comparison().total().materialsKg(), ordered.shipConstructionOrders().getFirst().requiredMaterialsKg());
        assertEquals(quote.comparison().total().workHours(), ordered.shipConstructionOrders().getFirst().requiredWorkHours());
        var complete = finish(ordered); assertEquals("larger", ship(complete).designId()); assertEquals(800, ship(complete).currentHullHealth());
        assertEquals(quote.retainedDamage(), ship(complete).moduleDamage());
        assertEquals(quote.materialCostCredits(), initialCash - complete.empires().getFirst().treasuryCredits(), 1e-6);
    }
}
