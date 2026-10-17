package com.spaceconquest.control;

import com.spaceconquest.control.ai.*;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.*;
import com.spaceconquest.engine.ship.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ShipRetirementTest {
    @TempDir Path directory;
    private GameState world() throws Exception {
        var state = new ShipRefitQuoteTest().world(); var hub = state.commercialHubs().getFirst();
        return state.withCommercialHubs(List.of(new CommercialHub(hub.id(), hub.entityId(), 0, 100000000, hub.currentStoredWeightKg(), hub.logisticsRangeUnits(), hub.activeOrders())))
                .withMarketAccounts(List.of(new MarketAccount(hub.id(), 2000000)));
    }
    private ShipInstance ship(GameState state) { return ShipRetirementPlan.fleet(state, "ship").ships().stream().filter(ship -> "ship".equals(ship.id())).findFirst().orElseThrow(); }
    private GameState replace(GameState state, ShipInstance ship) { return FleetReturnTravel.replace(state, ShipRetirementPlan.fleet(state, ship.id()).withShips(List.of(ship))); }
    private GameState finish(GameState state) {
        for (int turn = 1; turn < 100 && !state.shipConstructionOrders().isEmpty(); turn++) state = new ShipConstructionProcessor().process(state.withTurn(turn));
        assertTrue(state.shipConstructionOrders().isEmpty()); return state;
    }
    private GameState ahead(GameState state, double work) {
        var order = new ShipConstructionOrder("ahead", "owner", "old", "sol", "yard", 0, work, Map.of(), Map.of());
        return state.withShipConstructionOrders(List.of(order));
    }
    @Test void cancellationKeepsPaidConsumptionAndLiveDamageThroughReloadWithoutRefundOrReplay() throws Exception {
        var state = world(); double daily = ShipyardWorkCapacity.forYard(state, "owner", "sol", "yard").workPerDay();
        double work = ShipRefitQuote.inspect(state, "owner", "ship", "laser").comparison().total().workHours();
        state = ahead(state, Math.max(1, daily - work / 2)); state = new QueueShipRefitCommand("owner", "ship", "laser").apply(state);
        state = new ShipConstructionProcessor().process(state);
        var order = state.shipConstructionOrders().stream().filter(item -> item.refit() != null).findFirst().orElseThrow();
        assertTrue(order.accumulatedWorkHours() > 0); assertFalse(order.consumedMaterialsKg().isEmpty());
        state = replace(state, ship(state).withModuleDamage(new ShipModuleDamage(Map.of(0, 80.0))));
        var saves = new SaveGameManager(directory); var file = directory.resolve("cancel.scsave").toFile(); saves.save(file, state, 1, GameClock.START_TIME.toString());
        var restored = saves.load(file).toGameState(state.turn(), state.status()); var before = ship(restored);
        var cancel = new CancelShipYardWorkCommand("owner", order.id()); assertFalse(new CancelShipYardWorkCommand("other", order.id()).validate(restored));
        var cancelled = cancel.apply(restored); assertEquals(before, ship(cancelled)); assertEquals(restored.empires(), cancelled.empires());
        assertEquals(restored.commercialHubs(), cancelled.commercialHubs()); assertFalse(ShipRefitPlan.reserved(cancelled, "ship")); assertSame(cancelled, cancel.apply(cancelled));
        assertTrue(cancelled.shipyardLedger().history().getLast().status().contains("consumed materials"));
        saves.save(file, cancelled, 1, GameClock.START_TIME.toString()); var loaded = saves.load(file).toGameState(cancelled.turn(), cancelled.status());
        assertEquals(cancelled.shipyardLedger(), loaded.shipyardLedger()); assertFalse(cancel.validate(loaded));
    }
    @Test void recoveryUsesCapturedDamageAndApplicationValuesInsteadOfInventingHardwareOrChargingTwice() throws Exception {
        var state = world(); var design = state.shipDesigns().getFirst(); var original = ship(state);
        var rules = HullTechnology.retirementTechnology(design.hullFrame()); var healthy = original.withModuleDamage(ShipModuleDamage.empty());
        var lost = original.withModuleDamage(new ShipModuleDamage(Map.of(0, 100.0)));
        double healthyMass = ShipRetirementSalvage.materials(design, healthy, rules).values().stream().mapToDouble(Double::doubleValue).sum();
        double damagedMass = ShipRetirementSalvage.materials(design, lost, rules).values().stream().mapToDouble(Double::doubleValue).sum(); assertTrue(damagedMass < healthyMass);
        var noRecovery = new ShipRetirementTechnology(rules.dismantlingWorkFraction(), 0, 0, 0);
        assertTrue(ShipRetirementSalvage.materials(design, original, noRecovery).isEmpty());
        var quote = ShipRetirementQuote.inspect(state, "owner", "ship"); assertTrue(quote.ready(), quote.blockers().toString());
        assertTrue(state.shipConstructionOrders().isEmpty()); var queued = new QueueShipRetirementCommand("owner", "ship").apply(state);
        assertTrue(ShipRefitPlan.reserved(queued, "ship")); assertFalse(new QueueShipRetirementCommand("owner", "ship").validate(queued));
        var completed = finish(queued); assertNull(ShipRetirementPlan.fleet(completed, "ship")); assertTrue(completed.fleets().isEmpty());
        assertEquals(state.empires().getFirst().treasuryCredits() + quote.sale().credits(), completed.empires().getFirst().treasuryCredits(), 1e-6);
        assertEquals(state.marketAccounts().getFirst().unsettledSalesCredits() - quote.sale().credits(), completed.marketAccounts().getFirst().unsettledSalesCredits(), 1e-6);
        quote.sale().materialsKg().forEach((id, kg) -> assertEquals(state.commercialHubs().getFirst().activeOrders().get(id).supplyKg() + kg,
                completed.commercialHubs().getFirst().activeOrders().get(id).supplyKg(), 1e-6));
        assertSame(completed, new QueueShipRetirementCommand("owner", "ship").apply(completed));
    }
    @Test void savedPartialRetirementPausesForBuyerFundsAndCancellationReleasesWithoutSalvage() throws Exception {
        var state = world(); var quote = ShipRetirementQuote.inspect(state, "owner", "ship"); double daily = ShipyardWorkCapacity.forYard(state, "owner", "sol", "yard").workPerDay();
        state = new QueueShipRetirementCommand("owner", "ship").apply(ahead(state, Math.max(1, daily - quote.workHours() / 2)));
        var partial = new ShipConstructionProcessor().process(state); var order = partial.shipConstructionOrders().stream().filter(item -> item.retirement() != null).findFirst().orElseThrow();
        assertTrue(order.accumulatedWorkHours() > 0); assertEquals(0, order.requiredMaterialsKg().size());
        var saves = new SaveGameManager(directory); var file = directory.resolve("retire.scsave").toFile(); saves.save(file, partial, 1, GameClock.START_TIME.toString());
        var restored = saves.load(file).toGameState(partial.turn(), partial.status()); assertEquals(partial.shipConstructionOrders(), restored.shipConstructionOrders());
        var poorBuyer = restored.withMarketAccounts(List.of(new MarketAccount("hub", 0))); var paused = new ShipConstructionProcessor().process(poorBuyer.withTurn(1));
        assertEquals(order.accumulatedWorkHours(), paused.shipConstructionOrders().getFirst().accumulatedWorkHours()); assertEquals(ship(poorBuyer), ship(paused));
        var cancelled = new CancelShipYardWorkCommand("owner", order.id()).apply(paused); assertEquals(paused.marketAccounts(), cancelled.marketAccounts());
        assertEquals(paused.commercialHubs(), cancelled.commercialHubs()); assertEquals(ship(paused), ship(cancelled)); assertFalse(ShipRefitPlan.reserved(cancelled, "ship"));
        var complete = finish(restored); assertNull(ShipRetirementPlan.fleet(complete, "ship"));
    }
    @Test void ownershipCommitmentsMarketSpaceAndPassengersBlockRetirement() throws Exception {
        var state = world(); assertFalse(ShipRetirementQuote.inspect(state, "other", "ship").ready());
        assertFalse(ShipRetirementQuote.inspect(state.withMarketAccounts(List.of()), "owner", "ship").ready());
        assertFalse(ShipRetirementQuote.inspect(new ShipRefitQuoteTest().world(), "owner", "ship").ready());
        var original = ship(state); var passenger = new ShipInstance(original.id(), original.designId(), original.ownerEntityId(), original.currentHullHealth(), 0, 0, Map.of(), 1, "human", original.transitMode(), original.powerState(), original.supplyState(), original.moduleDamage());
        assertFalse(ShipRetirementQuote.inspect(replace(state, passenger), "owner", "ship").ready());
        var fleet = state.fleets().getFirst().withLocation(FleetLocation.at(FleetLocation.Site.deepSpace()));
        assertFalse(ShipRetirementQuote.inspect(state.withFleets(List.of(fleet)), "owner", "ship").ready());
        var hub = state.commercialHubs().getFirst();
        var reserved = hub.withReservedTradeFuel(hub.currentStoredWeightKg() + 10, Map.of("ship", new com.spaceconquest.engine.logistics.ReservedTradeFuel("owner", "old", Map.of("liquid_oxygen", 10.0))));
        assertTrue(ShipRetirementQuote.inspect(state.withCommercialHubs(List.of(reserved)), "owner", "ship").blockers().stream().anyMatch(reason -> reason.contains("prepaid")));
        var invalidTariff = new CommercialHub(hub.id(), hub.entityId(), Double.NaN, hub.storageCapacityKg(), hub.currentStoredWeightKg(), hub.logisticsRangeUnits(), hub.activeOrders());
        assertFalse(ShipRetirementQuote.inspect(state.withCommercialHubs(List.of(invalidTariff)), "owner", "ship").ready());
    }
    @Test void corporateRetirementPaysSharedYardFeesAndCreditsOnlyTheActualShipOwner() throws Exception {
        var state = world(); var fixture = new ShipRefitQuoteTest(); var original = ship(state);
        var client = new Corporation("corp", "Client", "owner", "yard", "TRANSPORT", 1000000, List.of(), List.of("ship"), List.of());
        var corporate = new ShipInstance("ship", "old", "corp", original.currentHullHealth(), 0, 0, Map.of()).withPowerState(original.powerState()).withModuleDamage(original.moduleDamage());
        var fleet = new Fleet("fleet", "Fleet", "corp", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(corporate), state.fleets().getFirst().location());
        state = state.toBuilder().corporations(List.of(client)).shipDesigns(state.shipDesigns().stream().map(fixture::corporate).toList()).fleets(List.of(fleet)).build();
        var hub = state.commercialHubs().getFirst(); state = state.withCommercialHubs(List.of(new CommercialHub(hub.id(), hub.entityId(), .25,
                hub.storageCapacityKg(), hub.currentStoredWeightKg(), hub.logisticsRangeUnits(), hub.activeOrders())));
        var quote = ShipRetirementQuote.inspect(state, "corp", "ship"); assertTrue(quote.ready(), quote.blockers().toString()); assertTrue(quote.serviceFeeCredits() > 0);
        var complete = finish(new QueueShipRetirementCommand("corp", "ship").apply(state)); assertTrue(complete.corporations().getFirst().ownedShipIds().isEmpty());
        assertEquals(client.liquidCapitalReserves() + quote.sale().credits() - quote.serviceFeeCredits(), complete.corporations().getFirst().liquidCapitalReserves(), 1e-6);
        assertEquals(state.empires().getFirst().treasuryCredits() + quote.serviceFeeCredits() + quote.sale().tariffCredits(), complete.empires().getFirst().treasuryCredits(), 1e-6);
        assertEquals(state.marketAccounts().getFirst().unsettledSalesCredits() - quote.sale().buyerPaymentCredits(), complete.marketAccounts().getFirst().unsettledSalesCredits(), 1e-6);
    }
    @Test void cancellationDoesNotRefundEarnedCorporateServiceFeesOrConsumedMaterials() throws Exception {
        var state = world(); var fixture = new ShipRefitQuoteTest(); var original = ship(state);
        var client = new Corporation("corp", "Client", "owner", "yard", "TRANSPORT", 1000000, List.of(), List.of("ship"), List.of());
        var corporate = new ShipInstance("ship", "old", "corp", original.currentHullHealth(), 0, 0, Map.of()).withPowerState(original.powerState()).withModuleDamage(original.moduleDamage());
        var fleet = new Fleet("fleet", "Fleet", "corp", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(corporate), state.fleets().getFirst().location());
        state = state.toBuilder().corporations(List.of(client)).shipDesigns(state.shipDesigns().stream().map(fixture::corporate).toList()).fleets(List.of(fleet)).build();
        double daily = ShipyardWorkCapacity.forYard(state, "owner", "sol", "yard").workPerDay();
        var refit = ShipRefitQuote.inspect(state, "corp", "ship", "laser"); assertTrue(refit.ready());
        var ahead = new ShipConstructionOrder("ahead", "corp", "old", "sol", "yard", 0, Math.max(1, daily - refit.comparison().total().workHours() / 2), Map.of(), Map.of(),
                new ShipyardContract("owner", ShipyardAccessPolicy.current().feeCreditsPerWorkUnit(), 0));
        var ordered = new QueueShipRefitCommand("corp", "ship", "laser").apply(state.withShipConstructionOrders(List.of(ahead)));
        var partial = new ShipConstructionProcessor().process(ordered); var order = partial.shipConstructionOrders().stream().filter(item -> item.refit() != null).findFirst().orElseThrow();
        assertTrue(order.yardContract().paidFeesCredits() > 0); assertFalse(order.consumedMaterialsKg().isEmpty());
        var cancelled = new CancelShipYardWorkCommand("corp", order.id()).apply(partial);
        assertEquals(partial.empires(), cancelled.empires()); assertEquals(partial.corporations(), cancelled.corporations());
        assertEquals(partial.commercialHubs(), cancelled.commercialHubs()); assertEquals(partial.shipyardLedger().cumulativeProfit(), cancelled.shipyardLedger().cumulativeProfit());
        assertEquals(ship(partial), ship(cancelled)); assertFalse(ShipRefitPlan.reserved(cancelled, "ship"));
    }
    @Test void actualRecoveryIncludesRemainingFuelAndSuppliesAndDamageReceivedAfterTheQuote() throws Exception {
        var state = world(); var old = ship(state);
        var stored = new ShipInstance(old.id(), old.designId(), old.ownerEntityId(), old.currentHullHealth(), 0, 100, Map.of("steel", 10.0),
                0, "", old.transitMode(), old.powerState(), new ShipSupplyState(Map.of("refined_copper", 20.0), null, ""), old.moduleDamage());
        state = replace(state, stored); var quote = ShipRetirementQuote.inspect(state, "owner", "ship"); assertTrue(quote.ready());
        var queued = new QueueShipRetirementCommand("owner", "ship").apply(state);
        int weapon = state.shipDesigns().getFirst().equippedModuleIds().indexOf(CombatModuleCatalog.GUN);
        var damage = new HashMap<>(ship(queued).moduleDamage().damageByMount()); damage.put(weapon, 100.0);
        queued = replace(queued, ship(queued).withModuleDamage(new ShipModuleDamage(damage)));
        var expected = ShipRetirementSalvage.materials(state.shipDesigns().getFirst(), ship(queued), quote.rules());
        assertTrue(expected.values().stream().mapToDouble(Double::doubleValue).sum() < quote.sale().materialsKg().values().stream().mapToDouble(Double::doubleValue).sum());
        assertTrue(expected.get("refined_copper") >= 20); assertTrue(expected.get("rp1_kerosene") > 0);
        var completed = finish(queued); var opening = state;
        expected.forEach((id, kg) -> assertEquals(opening.commercialHubs().getFirst().activeOrders().get(id).supplyKg() + kg,
                completed.commercialHubs().getFirst().activeOrders().get(id).supplyKg(), 1e-6));
    }
    private GameState withReplacement() throws Exception {
        var state = world(); var source = state.fleets().getFirst(); var design = state.shipDesigns().getFirst();
        var gun = CombatModuleCatalog.module(design, CombatModuleCatalog.GUN);
        var replacement = new ShipInstance("replacement", "old", "owner", 1000, 0, 100, Map.of(gun.ammunitionId(), gun.operationalStats().get("ammunitionMassKg") * 100));
        var fleet = new Fleet("replacement-fleet", "Replacement", "owner", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(replacement.withPowerState(ShipPowerState.empty())), source.location());
        state = state.toBuilder().fleets(List.of(source, fleet)).shipyardLedger(state.shipyardLedger().replacement("ship", "replacement")).build();
        state = ShipPowerResupply.buy(state, "replacement", "yard", "rp1", 1000);
        assertTrue(ship(state).currentHullHealth() > 0); return state;
    }
    @Test void aiWaitsForReadyReplacementAndPreservesCoverageThenUsesSavedPaidRetirement() throws Exception {
        var state = withReplacement(); var request = new RequestAIShipRetirementCommand("owner", "ship"); assertTrue(request.validate(state));
        var war = state.toBuilder().diplomaticRelations(List.of(new DiplomaticRelation("owner", "enemy", "TOTAL_WAR", 0))).build();
        assertFalse(request.validate(war), "Retiring one of two defenders would create a wartime reserve shortage");
        var planned = ShipLifecyclePlanner.plan(state, state.empires().getFirst(), Set.of()); assertInstanceOf(RequestAIShipRetirementCommand.class, planned.commands().getFirst());
        var queued = request.apply(state); assertEquals("replacement", queued.shipConstructionOrders().getFirst().retirement().replacementShipId());
        var repeated = ShipLifecyclePlanner.plan(queued, queued.empires().getFirst(), Set.of());
        assertTrue(repeated.commands().isEmpty(), repeated.commands().toString());
        var saves = new SaveGameManager(directory); var file = directory.resolve("ai-retire.scsave").toFile(); saves.save(file, queued, 1, GameClock.START_TIME.toString());
        var restored = saves.load(file).toGameState(queued.turn(), queued.status()); assertEquals(queued.shipConstructionOrders(), restored.shipConstructionOrders());
        var completed = finish(restored); assertNull(ShipRetirementPlan.fleet(completed, "ship")); assertNotNull(ShipRetirementPlan.fleet(completed, "replacement"));
        assertTrue(completed.shipyardLedger().replacements().isEmpty());
    }
    @Test void aiCancelsPendingRetirementWhenReplacementIsLostOrCoverageChanges() throws Exception {
        var state = withReplacement(); var request = new RequestAIShipRetirementCommand("owner", "ship");
        var pending = request.apply(state); assertFalse(pending.shipConstructionOrders().isEmpty());
        var lost = pending.withFleets(List.of(pending.fleets().getFirst())); var paused = new ShipConstructionProcessor().process(lost);
        assertEquals(0, paused.shipConstructionOrders().getFirst().accumulatedWorkHours());
        var cleanup = ShipLifecyclePlanner.plan(paused, paused.empires().getFirst(), Set.of()); assertInstanceOf(CancelShipYardWorkCommand.class, cleanup.commands().getFirst());
        var cancelled = cleanup.commands().getFirst().apply(paused); assertEquals(ship(paused), ship(cancelled)); assertTrue(cancelled.shipConstructionOrders().isEmpty());
        var war = pending.toBuilder().diplomaticRelations(List.of(new DiplomaticRelation("owner", "enemy", "TOTAL_WAR", 0))).build();
        assertInstanceOf(CancelShipYardWorkCommand.class, ShipLifecyclePlanner.plan(war, war.empires().getFirst(), Set.of()).commands().getFirst());
        var replacementFleet = state.fleets().getLast(); var replacement = replacementFleet.ships().getFirst();
        var unavailable = state.withFleets(List.of(state.fleets().getFirst(), replacementFleet.withShips(List.of(replacement.withModuleDamage(new ShipModuleDamage(Map.of(0, 100.0)))))));
        assertFalse(request.validate(unavailable));
    }
    @Test void completedReplacementDoesNotPreventServingTheOriginalWhenCoverageStillNeedsIt() throws Exception {
        var state = withReplacement(); state = replace(state, ship(state).withModuleDamage(ShipModuleDamage.empty()));
        state = ShipPowerResupply.buy(state, "ship", "yard", "rp1", 1000);
        state = state.toBuilder().diplomaticRelations(List.of(new DiplomaticRelation("owner", "enemy", "TOTAL_WAR", 0))).build();
        assertFalse(new RequestAIShipRetirementCommand("owner", "ship").validate(state));
        var service = new ServiceAICombatFleetCommand("owner", "fleet"); assertTrue(service.validate(state));
        assertTrue(EmpireFleetPlanner.plan(state, state.empires().getFirst(), Set.of()).contains(service));
        assertTrue(ShipReplacementPlan.committed(state, "ship"), "The completed link still prevents another replacement build");
    }
}
