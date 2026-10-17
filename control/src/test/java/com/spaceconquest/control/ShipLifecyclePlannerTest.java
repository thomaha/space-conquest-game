package com.spaceconquest.control;

import com.spaceconquest.control.ai.*;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.ship.*;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ShipLifecyclePlannerTest {
    @TempDir Path directory;
    private GameState world(double hull, boolean improved) throws Exception {
        var state = new ShipRefitQuoteTest().world(); var source = state.shipDesigns().getFirst();
        var ship = new ShipInstance("ship", source.id(), "owner", hull, 0, 0, Map.of()).withPowerState(ShipPowerState.empty());
        var models = new ArrayList<ShipDesign>(); models.add(source);
        if (improved) {
            var modules = new ArrayList<>(source.equippedModuleIds()); modules.add(CombatModuleCatalog.GUN);
            var result = ShipBlueprintFactory.evaluate(state, new ShipDesignSpecification("improved", "Improved", "owner", source.role(), source.hullMaterialId(),
                    modules, source.armorMaterialId(), source.armorThicknessCm(), source.hullFrame().totalSlots(), ShipBlueprintAssembly.identitiesFor(source, modules, "improved")));
            assertTrue(result.valid(), result.errors().toString()); models.add(result.design());
        }
        return state.withShipDesigns(models).withFleets(List.of(state.fleets().getFirst().withShips(List.of(ship))));
    }
    private GameState repairBase(GameState state) {
        var yard = state.orbitalStations().getFirst(); var modules = new ArrayList<>(yard.modules()); modules.add(BaseRepairCatalog.module().withPayroll(2, 20));
        var station = new OrbitalStation(yard.id(), yard.name(), yard.systemId(), yard.planetOrbitId(), yard.ownerEntityId(), yard.ownershipType(), yard.totalSlots(), modules,
                yard.storedCargoKg(), 10000, 0, 0, 0, 100, 100, "steel", 0, true);
        return state.withOrbitalStations(List.of(station));
    }
    @Test void healthyShipChoosesAffordableRefitAndPlanningDoesNotMutate() throws Exception {
        var state = world(1000, true); var quote = AIShipLifecycleQuote.inspect(state, "owner", "ship"); assertNotNull(quote);
        assertEquals(AIShipLifecycleQuote.Action.REFIT, quote.action()); assertEquals("improved", quote.target().id());
        assertTrue(state.shipConstructionOrders().isEmpty()); assertEquals("old", state.fleets().getFirst().ships().getFirst().designId());
        var planned = ShipLifecyclePlanner.plan(state, state.empires().getFirst(), Set.of());
        assertEquals(1, planned.commands().size()); assertEquals(Set.of("fleet"), planned.reservedFleetIds());
        var queue = new CommandQueue(); planned.commands().forEach(queue::submit); var committed = queue.drainAndExecute(state);
        assertNotNull(committed.shipConstructionOrders().getFirst().refit()); assertTrue(ShipLifecyclePlanner.plan(committed, committed.empires().getFirst(), Set.of()).commands().isEmpty());
        assertEquals(state.empires(), committed.empires());
    }
    @Test void paidRepairWinsOverReplacingARecoverableHull() throws Exception {
        var state = repairBase(world(400, false)); var quote = AIShipLifecycleQuote.inspect(state, "owner", "ship"); assertNotNull(quote);
        assertEquals(AIShipLifecycleQuote.Action.REPAIR, quote.action()); assertTrue(quote.estimatedCredits() > 0);
        var ordered = new RequestAIShipLifecycleCommand("owner", "ship").apply(state);
        assertTrue(ordered.shipConstructionOrders().isEmpty()); assertNotNull(ordered.fleets().getFirst().interceptOrder());
        assertEquals(400, ordered.fleets().getFirst().ships().getFirst().currentHullHealth());
        var paid = BaseRepairProcessor.advanceDay(ordered);
        assertTrue(paid.fleets().getFirst().ships().getFirst().currentHullHealth() > 400); assertTrue(paid.empires().getFirst().treasuryCredits() < state.empires().getFirst().treasuryCredits());
    }
    @Test void replacementPreservesOriginalShipAndSavedLinkPreventsRepeatedBuilds() throws Exception {
        var state = world(100, false); var original = state.fleets().getFirst().ships().getFirst(); var quote = AIShipLifecycleQuote.inspect(state, "owner", "ship"); assertNotNull(quote);
        assertEquals(AIShipLifecycleQuote.Action.REPLACE, quote.action()); var command = new RequestAIShipLifecycleCommand("owner", "ship");
        var queued = command.apply(state); assertEquals("ship", queued.shipConstructionOrders().getFirst().replacement().sourceShipId());
        assertFalse(command.validate(queued)); assertSame(queued, command.apply(queued));
        var first = new ShipConstructionProcessor().process(queued); var saves = new SaveGameManager(directory); var file = directory.resolve("lifecycle.scsave").toFile();
        saves.save(file, first, 1, GameClock.START_TIME.toString()); var restored = saves.load(file).toGameState(first.turn(), first.status());
        assertEquals(first.shipConstructionOrders(), restored.shipConstructionOrders());
        var complete = restored;
        for (int turn = 1; turn < 100 && !complete.shipConstructionOrders().isEmpty(); turn++) complete = new ShipConstructionProcessor().process(complete.withTurn(turn));
        assertTrue(complete.shipConstructionOrders().isEmpty()); assertEquals(original, FleetOrganization.find(complete, "fleet").ships().getFirst());
        assertEquals(2, complete.fleets().size()); assertNotNull(complete.shipyardLedger().replacements().get("ship")); assertFalse(command.validate(complete));
        saves.save(file, complete, 1, GameClock.START_TIME.toString()); assertEquals(complete.shipyardLedger(), saves.load(file).toGameState(complete.turn(), complete.status()).shipyardLedger());
    }
    @Test void budgetsReservedFleetsAndOwnershipAreRecheckedAtTheTick() throws Exception {
        var state = world(1000, true); assertTrue(ShipLifecyclePlanner.plan(state, state.empires().getFirst(), Set.of("fleet")).commands().isEmpty());
        assertNull(AIShipLifecycleQuote.inspect(state, "other", "ship"));
        var owner = state.empires().getFirst(); var poor = state.withEmpires(List.of(new Empire(owner.id(), owner.name(), owner.raceId(), owner.societyStructure(), 0,
                owner.corporateTaxRate(), owner.controlledSystemIds(), owner.ministries(), owner.systemGovernorAssignments(), owner.unlockedTechIds(), owner.activeShipDesignIds())));
        var queue = new CommandQueue(); var receipt = queue.submitTracked(new RequestAIShipLifecycleCommand("owner", "ship"));
        assertSame(poor, queue.drainAndExecute(poor)); assertEquals(CommandOutcome.REJECTED, receipt.join());
        assertNull(AIShipLifecycleQuote.inspect(state.toBuilder().commercialHubs(List.of()).build(), "owner", "ship"));
    }
    @Test void policyChangesDecisionsWithoutChangingTheChosenHardware() throws Exception {
        var state = world(1000, true); var normal = ShipLifecyclePolicy.current();
        var preferBuild = new ShipLifecyclePolicy(normal.maximumShipPreviews(), normal.maximumModelPreviews(), normal.treasuryReserveCredits(), normal.minimumGainFraction(),
                normal.minimumRefitHullFraction(), .001, normal.maximumDowntimeDays(), normal.downtimeCreditsPerDay(), normal.utilityFloor(), normal.capabilityWeights());
        var refit = AIShipLifecycleQuote.inspect(state, "owner", "ship", normal); var build = AIShipLifecycleQuote.inspect(state, "owner", "ship", preferBuild);
        assertEquals(AIShipLifecycleQuote.Action.REFIT, refit.action()); assertEquals(AIShipLifecycleQuote.Action.REPLACE, build.action()); assertEquals(refit.target(), build.target());
    }
    @Test void collectiveIndustryNeedsActualMaterialsButNoMonetaryTreasuryFloor() throws Exception {
        var state = world(1000, true); var owner = state.empires().getFirst();
        var hive = new Empire(owner.id(), owner.name(), owner.raceId(), "Hive Mind", 0, owner.corporateTaxRate(),
                owner.controlledSystemIds(), owner.ministries(), owner.systemGovernorAssignments(), owner.unlockedTechIds(), owner.activeShipDesignIds());
        state = state.withEmpires(List.of(hive));
        assertNotNull(AIShipLifecycleQuote.inspect(state, "owner", "ship"));
        assertNull(AIShipLifecycleQuote.inspect(state.toBuilder().commercialHubs(List.of()).build(), "owner", "ship"));
    }
    @Test void affiliatedCorporationPaysForItsOwnDecisionAndIncompatibleSocietyIsRejected() throws Exception {
        var state = world(1000, true); var fixture = new ShipRefitQuoteTest();
        var client = new Corporation("corp", "Client", "owner", "yard", "TRANSPORT", 1000000, List.of(), List.of("ship"), List.of());
        var fleet = new Fleet("corporate", "Corporate", "corp", "sol", "", 0, 0, 0, false, "PASSIVE",
                List.of(new ShipInstance("ship", "old", "corp", 1000, 0, 0, Map.of()).withPowerState(ShipPowerState.empty())), state.fleets().getFirst().location());
        state = state.toBuilder().corporations(List.of(client)).shipDesigns(state.shipDesigns().stream().map(fixture::corporate).toList()).fleets(List.of(fleet)).build();
        var quote = AIShipLifecycleQuote.inspect(state, "corp", "ship"); assertNotNull(quote); assertEquals(AIShipLifecycleQuote.Action.REFIT, quote.action());
        var ordered = new RequestAIShipLifecycleCommand("corp", "ship").apply(state); assertEquals("corp", ordered.shipConstructionOrders().getFirst().ownerEntityId());
        assertEquals("owner", ordered.shipConstructionOrders().getFirst().yardContract().providerOwnerId()); assertEquals(state.empires(), ordered.empires());
        var owner = state.empires().getFirst(); var hive = new Empire(owner.id(), owner.name(), owner.raceId(), "Hive Mind", owner.treasuryCredits(), owner.corporateTaxRate(),
                owner.controlledSystemIds(), owner.ministries(), owner.systemGovernorAssignments(), owner.unlockedTechIds(), owner.activeShipDesignIds());
        assertNull(AIShipLifecycleQuote.inspect(state.withEmpires(List.of(hive)), "corp", "ship"));
    }
}
