package com.spaceconquest.control;

import com.spaceconquest.control.ai.*;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.IndustryRecipeCatalog;
import com.spaceconquest.engine.logistics.*;
import com.spaceconquest.engine.ship.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AIFacilityRepositionTest {
    @TempDir Path directory;
    private GameState opening() throws Exception {
        var state = new AIFacilityLogisticsTest().world(); var old = state.fleets().getFirst();
        var fleet = new Fleet(old.id(), old.name(), old.ownerEntityId(), "b", "", 0, 0, 0, false, "PASSIVE", old.ships(), FleetLocation.at(FleetLocation.Site.docked("port-b")));
        return state.withFleets(List.of(fleet));
    }
    private GameState staged(GameState state) {
        var planner = new EmpireLogisticsPlanner(); var owner = state.empires().getFirst();
        var first = planner.plan(state, owner); assertTrue(first.commands().isEmpty()); assertEquals(Set.of("fleet"), first.reservedFleetIds());
        assertTrue(planner.plan(state, owner).commands().isEmpty());
        state = state.withTurn(state.turn() + 1); var command = planner.plan(state, owner).commands().getFirst();
        assertInstanceOf(RepositionAIFacilitySupplyCommand.class, command);
        var queue = new CommandQueue(); queue.submit(command); queue.submit(command); return queue.drainAndExecute(state);
    }
    private double steel(GameState state) {
        return state.commercialHubs().stream().mapToDouble(hub -> hub.activeOrders().get("steel").supplyKg()).sum()
                + state.fleets().stream().flatMap(fleet -> fleet.ships().stream()).mapToDouble(ship -> ship.storedCargoKg().getOrDefault("steel", 0.0)).sum();
    }
    private GameState arrive(GameState state) {
        var engine = new SpaceConquestEngine(42); engine.applyGameState(state);
        for (int day = 0; day < 15; day++) {
            if (new LoadAIFacilitySupplyCommand("owner", state.tradeRoutes().getFirst().id()).validate(state)) return state;
            engine.stepTurn(); state = engine.getGameState();
        }
        fail("Carrier reaches its supply source"); return state;
    }
    @Test void savedEmptyTripLoadsActualGoodsProducesAndReturnsWithoutInventingStock() throws Exception {
        var initial = opening(); var dispatched = staged(initial); var route = dispatched.tradeRoutes().getFirst();
        assertEquals(1, dispatched.tradeRoutes().size()); assertEquals(TradeRoute.SUPPLY_REPOSITIONING, route.phase());
        assertEquals(0, route.onboardKg()); assertTrue(dispatched.fleets().getFirst().ships().getFirst().storedCargoKg().isEmpty());
        assertEquals(steel(initial), steel(dispatched), .000001); assertTrue(route.carrierCommitted());
        assertEquals(12, FacilitySupplyStaging.claimedSourceKg(dispatched, "source", "steel"));
        assertTrue(FacilitySupplyNeeds.shortages(dispatched, dispatched.empires().getFirst(), LogisticsPolicy.current()).isEmpty());
        assertFalse(new CreateTradeRouteCommand("owner", "Competing", "source", "destination", "steel", 12, 60, 12, List.of("ship")).validate(dispatched));
        var saves = new SaveGameManager(directory); var file = directory.resolve("staging.scsave").toFile();
        saves.save(file, dispatched, 1, GameClock.START_TIME.plusDays(dispatched.turn()).toString());
        var loaded = saves.load(file).toGameState(dispatched.turn(), dispatched.status()); assertEquals(route, loaded.tradeRoutes().getFirst());
        var uninterrupted = arrive(dispatched); var resumed = arrive(loaded);
        assertEquals(uninterrupted.tradeRoutes(), resumed.tradeRoutes()); assertEquals(uninterrupted.fleets(), resumed.fleets());
        assertTrue(resumed.fleets().getFirst().ships().getFirst().currentFuelKg() < initial.fleets().getFirst().ships().getFirst().currentFuelKg());
        var planner = new EmpireLogisticsPlanner(); var commands = planner.plan(resumed, resumed.empires().getFirst()).commands();
        assertInstanceOf(LoadAIFacilitySupplyCommand.class, commands.getFirst());
        var queue = new CommandQueue(); commands.forEach(queue::submit); commands.forEach(queue::submit);
        var paid = queue.drainAndExecute(resumed); assertEquals(1, paid.tradeRoutes().size());
        assertEquals(TradeRoute.DELIVERING, paid.tradeRoutes().getFirst().phase()); assertNull(paid.tradeRoutes().getFirst().supplyReservation());
        assertEquals(steel(initial), steel(paid), .000001); assertEquals(0, FacilitySupplyStaging.claimedSourceKg(paid, "source", "steel"));
        var engine = new SpaceConquestEngine(42); engine.applyGameState(paid); double produced = 0;
        for (int day = 0; day < 25; day++) {
            engine.stepTurn(); produced += engine.getGameState().industryAccounts().stream().filter(account -> account.facilityId().equals("factory"))
                    .mapToDouble(account -> account.producedKg().getOrDefault("cannon_round", 0.0)).sum();
        }
        var end = engine.getGameState(); var recipe = IndustryRecipeCatalog.find("cannon_ammunition_mfg");
        assertTrue(produced > 0); assertEquals(steel(initial) - produced * recipe.inputsKg().get("steel") / recipe.outputsKg().get("cannon_round"), steel(end), .001);
        assertEquals(TradeRoute.LOADING, end.tradeRoutes().getFirst().phase()); assertFalse(end.tradeRoutes().getFirst().carrierCommitted());
        assertTrue(FleetPositioning.atHub(end, end.fleets().getFirst(), end.commercialHubs().getFirst()));
    }
    @Test void changedSourceStockAtArrivalReleasesTheClaimWithoutBuyingAnything() throws Exception {
        var arrived = arrive(staged(opening())); var source = arrived.commercialHubs().getFirst(); var orders = new HashMap<>(source.activeOrders());
        var old = orders.get("steel"); orders.put("steel", new MarketOrder("steel", 0, old.demandKg(), old.pricePerKg(), 0));
        var empty = source.withInventory(arrived.turn(), source.currentStoredWeightKg() - old.supplyKg(), orders);
        var changed = arrived.withCommercialHubs(List.of(empty, arrived.commercialHubs().getLast()));
        var result = new LoadAIFacilitySupplyCommand("owner", changed.tradeRoutes().getFirst().id()).apply(changed);
        assertEquals(changed.empires(), result.empires()); assertEquals(changed.commercialHubs(), result.commercialHubs()); assertEquals(changed.fleets(), result.fleets());
        assertFalse(result.tradeRoutes().getFirst().carrierCommitted()); assertNull(result.tradeRoutes().getFirst().supplyReservation());
        assertEquals(0, FacilitySupplyStaging.claimedDestinationKg(result, "destination", "steel"));
    }
    @Test void expirationReleasesBothClaimsAndRetainsActualPaidMotion() throws Exception {
        var state = staged(opening()); var fleet = state.fleets().getFirst(); var route = state.tradeRoutes().getFirst();
        var expired = state.withTurn(route.supplyReservation().expiresTurn() + 1);
        var result = new LogisticsProcessor().processTradeRoutes(expired).state();
        assertEquals(fleet, result.fleets().getFirst()); assertFalse(result.tradeRoutes().getFirst().carrierCommitted());
        assertEquals(0, FacilitySupplyStaging.claimedSourceKg(result, "source", "steel"));
        assertEquals(0, FacilitySupplyStaging.claimedDestinationKg(result, "destination", "steel"));
    }
    @Test void anotherCarrierCannotClaimTheSameDestinationOrOverbookSourceSurplus() throws Exception {
        var initial = opening(); var staged = staged(initial); var other = new ShipInstance("other-ship", "design", "owner", 100, 0, 1000, Map.of())
                .withPowerState(initial.fleets().getFirst().ships().getFirst().powerState());
        var fleet = new Fleet("other-fleet", "Other", "owner", "b", "", 0, 0, 0, false, "PASSIVE", List.of(other), FleetLocation.at(FleetLocation.Site.docked("port-b")));
        var fleets = new ArrayList<>(staged.fleets()); fleets.add(fleet); var state = staged.withFleets(fleets);
        assertSame(state, new RepositionAIFacilitySupplyCommand("owner", "destination", "steel", "other-ship", "source", 0).apply(state));
        var source = state.commercialHubs().getFirst(); var orders = new HashMap<>(source.activeOrders()); var old = orders.get("steel");
        orders.put("steel", new MarketOrder("steel", 72, 20, old.pricePerKg(), 0));
        state = state.withCommercialHubs(List.of(source.withInventory(state.turn(), source.currentStoredWeightKg() - old.supplyKg() + 72, orders), state.commercialHubs().getLast()));
        var competingNeed = new FacilitySupplyNeeds.Need("destination", "steel", 4, 0, 12, false);
        assertNotNull(FacilitySupplyReposition.quote(state.withTradeRoutes(List.of()), state.empires().getFirst(), competingNeed, "other-ship", "source", "preview", LogisticsPolicy.current()));
        assertNull(FacilitySupplyReposition.quote(state, state.empires().getFirst(), competingNeed, "other-ship", "source", "preview", LogisticsPolicy.current()));
    }
    @Test void treasuryOrOwnershipChangesAtArrivalReleaseInsteadOfSpending() throws Exception {
        var arrived = arrive(staged(opening())); var owner = arrived.empires().getFirst();
        for (var replacement : List.of(
                new Empire(owner.id(), owner.name(), owner.raceId(), owner.societyStructure(), 0, 0, owner.controlledSystemIds(), List.of(), Map.of(), owner.unlockedTechIds(), List.of()),
                new Empire(owner.id(), owner.name(), owner.raceId(), owner.societyStructure(), owner.treasuryCredits(), 0, List.of("a"), List.of(), Map.of(), owner.unlockedTechIds(), List.of()))) {
            var changed = arrived.withEmpires(List.of(replacement));
            var result = new LoadAIFacilitySupplyCommand("owner", arrived.tradeRoutes().getFirst().id()).apply(changed);
            assertEquals(changed.empires(), result.empires()); assertEquals(changed.commercialHubs(), result.commercialHubs());
            assertEquals(changed.fleets(), result.fleets()); assertFalse(result.tradeRoutes().getFirst().carrierCommitted());
        }
    }
    @Test void emptyTripAndItsProspectiveShipmentRespectConfiguredLimits() throws Exception {
        var state = opening(); var empire = state.empires().getFirst(); var need = FacilitySupplyNeeds.shortages(state, empire, LogisticsPolicy.current()).getFirst();
        var tooShort = new LogisticsPolicy(2, 1, 3, 3, 1, 50000, 10000, 8, 1);
        assertNull(FacilitySupplyReposition.quote(state, empire, need, "ship", "source", "preview", tooShort));
        var tooExpensive = new LogisticsPolicy(2, 1, 3, 3, 1, 1, 10000, 8, 30);
        assertNull(FacilitySupplyReposition.quote(state, empire, need, "ship", "source", "preview", tooExpensive));
        assertTrue(state.tradeRoutes().isEmpty()); assertEquals(0, state.fleets().getFirst().ships().getFirst().storedCargoKg().size());
    }
}
