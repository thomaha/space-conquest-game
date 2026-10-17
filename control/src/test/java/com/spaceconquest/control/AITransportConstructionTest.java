package com.spaceconquest.control;

import com.spaceconquest.control.ai.*;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.*;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.macrostructure.*;
import com.spaceconquest.engine.ship.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AITransportConstructionTest {
    @TempDir Path directory;
    GameState world(boolean corporate) throws Exception {
        var state = new AIFacilityLogisticsTest().world().withFleets(List.of()); var base = state.shipDesigns().getFirst();
        var corp = new Corporation("corp", "Carrier company", "owner", "port-a", "TRANSPORT", corporate ? 1000000 : 0, List.of(), List.of(), List.of());
        var design = new ShipDesign("corporate-design", base.name(), "corp", base.role(), base.hullMaterialId(), base.equippedModuleIds(), base.armorMaterialId(),
                base.armorThicknessCm(), base.totalDryMassKg(), base.maxCargoMassKg(), base.fuelCapacityKg(), base.powerBalanceKw(), base.calculatedStructuralIntegrity(),
                base.minLaunchThrustRequiredN(), base.totalThrustN(), base.isValidForLaunch(), true, base.manufacturingProfile(), base.powerProfile());
        var port = state.orbitalStations().getFirst(); var modules = new ArrayList<>(port.modules());
        var yard = new StationModule("yard-module", "Shipyard", StationModule.TYPE_SHIPYARD_GRID, 1, 100, 0, 0, Map.of(), "engineer", 100, true).withPayroll(100, 0);
        modules.add(yard);
        var station = new OrbitalStation(port.id(), port.name(), port.systemId(), port.planetOrbitId(), corporate ? "corp" : "owner",
                corporate ? OrbitalStation.OWNERSHIP_PRIVATE_CORPORATE : OrbitalStation.OWNERSHIP_PUBLIC_STATE, 300,
                modules, Map.of(), 0, 0, 0, 0, 100, 100, "steel", 0, true).withPopulations(List.of(new Population("human", Map.of(25, 20000L))));
        var source = state.commercialHubs().getFirst(); var stock = new HashMap<>(source.activeOrders());
        ShipConstructionRequirements.estimate(base).materialsKg().forEach((id, kg) -> stock.put(id, new MarketOrder(id, Math.max(kg * 5, stock.containsKey(id) ? stock.get(id).supplyKg() : 0), 0, 1, 0)));
        var target = state.commercialHubs().getLast(); var demand = new HashMap<>(target.activeOrders()); demand.put("steel", new MarketOrder("steel", 0, 4, 20, 1));
        state = state.toBuilder().corporations(List.of(corp)).shipDesigns(List.of(base, design)).orbitalStations(List.of(station, state.orbitalStations().getLast()))
                .commercialHubs(List.of(source.withInventory(0, stock.values().stream().mapToDouble(MarketOrder::supplyKg).sum(), stock), target.withInventory(0, target.currentStoredWeightKg(), demand)))
                .marketAccounts(List.of(new MarketAccount("source", 20000), new MarketAccount("destination", 1000000))).build();
        return new SetHubStockTargetCommand("owner", "destination", "steel", 100, 1000).apply(state);
    }
    private RequestAIFacilityTransportBuildCommand request(GameState state) {
        var planner = new TransportConstructionPlanner(); var owner = state.empires().getFirst(); var policy = TransportConstructionPolicy.current();
        for (int day = 0; day < policy.observationTurns() - 1; day++) {
            assertTrue(planner.plan(state.withTurn(day), owner, false).isEmpty()); assertTrue(planner.plan(state.withTurn(day), owner, false).isEmpty());
        }
        return (RequestAIFacilityTransportBuildCommand) planner.plan(state.withTurn(policy.observationTurns() - 1), owner, false).getFirst();
    }
    private double steel(GameState state) {
        return state.commercialHubs().stream().mapToDouble(hub -> hub.activeOrders().get("steel").supplyKg()).sum()
                + state.fleets().stream().flatMap(fleet -> fleet.ships().stream()).mapToDouble(ship -> ship.storedCargoKg().getOrDefault("steel", 0.0)).sum();
    }
    private void lifecycle(boolean corporate) throws Exception {
        var opening = world(corporate); var command = request(opening); String owner = corporate ? "corp" : "owner";
        assertEquals(owner, command.ownerEntityId()); var initial = opening.withTurn(TransportConstructionPolicy.current().observationTurns() - 1);
        var queue = new CommandQueue(); queue.submit(command); queue.submit(command); var ordered = queue.drainAndExecute(initial);
        assertEquals(1, ordered.shipConstructionOrders().size()); assertEquals(owner, ordered.shipConstructionOrders().getFirst().ownerEntityId());
        assertEquals(initial.empires(), ordered.empires()); assertEquals(initial.corporations(), ordered.corporations());
        var order = ordered.shipConstructionOrders().getFirst(); var saves = new SaveGameManager(directory); var file = directory.resolve(owner + ".scsave").toFile();
        saves.save(file, ordered, 1, GameClock.START_TIME.plusDays(ordered.turn()).toString()); var restored = saves.load(file).toGameState(ordered.turn(), ordered.status());
        assertSame(restored, command.apply(restored)); assertTrue(new TransportConstructionPlanner().plan(restored, restored.empires().getFirst(), false).isEmpty());
        var engine = new SpaceConquestEngine(42); engine.applyGameState(restored);
        for (int day = 0; day < 20 && !engine.getGameState().shipConstructionOrders().isEmpty(); day++) engine.stepTurn();
        var commissioned = engine.getGameState(); assertTrue(commissioned.shipConstructionOrders().isEmpty()); assertEquals(1, commissioned.fleets().size());
        assertEquals(owner, commissioned.fleets().getFirst().ownerEntityId()); assertTrue(EconomicOwnership.cash(commissioned, owner) < EconomicOwnership.cash(initial, owner));
        if (corporate) assertTrue(commissioned.corporations().getFirst().ownedShipIds().contains(commissioned.fleets().getFirst().ships().getFirst().id()));
        assertSame(commissioned, command.apply(commissioned));
        var planner = new EmpireLogisticsPlanner(); assertTrue(planner.plan(commissioned, commissioned.empires().getFirst()).commands().isEmpty());
        var next = commissioned.withTurn(commissioned.turn() + 1);
        var service = new DeployAIShipCommand(owner, next.fleets().getFirst().ships().getFirst().id()); assertTrue(service.validate(next));
        var dispatched = service.apply(next); assertEquals(owner, dispatched.tradeRoutes().getFirst().ownerEntityId());
        if (corporate) assertEquals(next.empires(), dispatched.empires()); else assertEquals(next.corporations(), dispatched.corporations());
        assertTrue(EconomicOwnership.cash(dispatched, owner) < EconomicOwnership.cash(next, owner));
        engine.applyGameState(dispatched); double produced = 0;
        for (int day = 0; day < 25; day++) {
            engine.stepTurn(); produced += engine.getGameState().industryAccounts().stream().filter(account -> account.facilityId().equals("factory"))
                    .mapToDouble(account -> account.producedKg().getOrDefault("cannon_round", 0.0)).sum();
        }
        var end = engine.getGameState(); var recipe = IndustryRecipeCatalog.find("cannon_ammunition_mfg"); assertTrue(produced > 0);
        assertEquals(steel(initial) - order.requiredMaterialsKg().getOrDefault("steel", 0.0) - produced * recipe.inputsKg().get("steel") / recipe.outputsKg().get("cannon_round"), steel(end), .001);
        assertFalse(end.tradeRoutes().getFirst().carrierCommitted());
    }
    @Test void corporateConstructionUsesCorporateFundsAndRestoresPaidProductionAfterReload() throws Exception { lifecycle(true); }
    @Test void publicConstructionCoversTheShortageWhenTheCompanyCannotInvest() throws Exception { lifecycle(false); }
    @Test void aTransportCompanyCanQuoteAndRegisterItsFirstCapturedModelAtTheCommandTick() throws Exception {
        var state = world(true).withShipDesigns(List.of()); var source = state.commercialHubs().getFirst();
        var owner = state.empires().getFirst(); var known = new ArrayList<String>();
        for (var technology : DataModelLoader.loadTechnologies()) { known.add(technology.id()); technology.applications().forEach(app -> known.add(app.id())); }
        state = state.withEmpires(List.of(new Empire(owner.id(), owner.name(), owner.raceId(), owner.societyStructure(), owner.treasuryCredits(),
                owner.corporateTaxRate(), owner.controlledSystemIds(), owner.ministries(), owner.systemGovernorAssignments(), known, owner.activeShipDesignIds())));
        var stock = new HashMap<>(source.activeOrders());
        DataModelLoader.loadMaterials().forEach(material -> {
            var existing = stock.get(material.id());
            stock.put(material.id(), new MarketOrder(material.id(), 1000000, existing == null ? 0 : existing.demandKg(), existing == null ? 1 : existing.pricePerKg(), 0));
        });
        var destination = state.commercialHubs().getLast(); var arrivalStock = new HashMap<>(destination.activeOrders());
        DataModelLoader.loadMaterials().forEach(material -> arrivalStock.putIfAbsent(material.id(), new MarketOrder(material.id(), 1000000, 0, 1, 0)));
        arrivalStock.put("steel", new MarketOrder("steel", 0, 4, 50, 1));
        state = state.withCommercialHubs(List.of(new CommercialHub(source.id(), source.entityId(), 0, 1000000000,
                stock.values().stream().mapToDouble(MarketOrder::supplyKg).sum(), source.logisticsRangeUnits(), stock),
                new CommercialHub(destination.id(), destination.entityId(), 0, 1000000000,
                        arrivalStock.values().stream().mapToDouble(MarketOrder::supplyKg).sum(), destination.logisticsRangeUnits(), arrivalStock).withStockTargets(destination.stockTargets())));
        assertNotNull(CorporateShipDesigner.choose(state, "corp", ShipRole.CARGO_TRANSPORT, 1000));
        var command = request(state); assertEquals("corp", command.ownerEntityId()); assertEquals("", command.designId());
        assertTrue(state.shipDesigns().isEmpty(), "Discarded AI previews must not register candidate assets");
        state = state.withTurn(TransportConstructionPolicy.current().observationTurns() - 1);
        var queued = command.apply(state); assertEquals(1, queued.shipDesigns().size()); assertEquals(1, queued.shipConstructionOrders().size());
        var design = queued.shipDesigns().getFirst(); assertNotNull(design.hullFrame()); assertNotNull(design.assembly());
        assertEquals("corp", design.ownerEntityId()); assertTrue(design.isProprietaryCorporateDesign());
        assertEquals(design.assembly().total().materialsKg(), queued.shipConstructionOrders().getFirst().requiredMaterialsKg());
        assertEquals(state.corporations(), queued.corporations()); assertSame(queued, command.apply(queued));
        var improved = queued.withShipConstructionOrders(List.of()).toBuilder().applicationOptimizations(List.of(
                new com.spaceconquest.engine.technology.ApplicationOptimization("owner", "fission_engines", "PATH_A",
                        new com.spaceconquest.engine.technology.ResearchProcessor().evaluateOptimizationPath("PATH_A", 1)))).build();
        var revisionQuote = FacilityTransportBuildQuote.quote(improved, "corp", design.id(), "a", "destination", "steel");
        assertNotNull(revisionQuote); var revised = revisionQuote.registered().shipDesigns().getLast();
        assertNotEquals(design.id(), revised.id()); assertTrue(revised.totalThrustN() > design.totalThrustN());
        assertTrue(revised.isProprietaryCorporateDesign()); assertNotNull(revised.assembly());
        assertEquals(design.assembly().identities(), revised.assembly().identities());
        assertEquals(design, revisionQuote.registered().shipDesigns().getFirst());
        assertEquals(List.of(design), improved.shipDesigns(), "Research quoting must leave the existing model frozen");
    }
    @Test void incompatibleSocietyAndUnprofitableCompanyCannotInvestOrUsePublicCash() throws Exception {
        var initial = world(true); var owner = initial.empires().getFirst(); var command = request(initial); var state = initial.withTurn(3);
        var hive = new Empire(owner.id(), owner.name(), owner.raceId(), "Hive Mind", owner.treasuryCredits(), 0, owner.controlledSystemIds(), List.of(), Map.of(), owner.unlockedTechIds(), List.of());
        var excluded = state.withEmpires(List.of(hive)); assertSame(excluded, command.apply(excluded));
        assertFalse(new QueueShipBuildCommand("corp", "corporate-design", "a").validate(excluded));
        var target = state.commercialHubs().getLast(); var orders = new HashMap<>(target.activeOrders()); orders.put("steel", new MarketOrder("steel", 0, 4, 1, 1));
        var poorMargin = state.withCommercialHubs(List.of(state.commercialHubs().getFirst(), target.withInventory(state.turn(), target.currentStoredWeightKg(), orders)));
        assertSame(poorMargin, command.apply(poorMargin)); assertEquals(state.empires(), poorMargin.empires());
        assertTrue(EconomicOwnership.corporationsAllowed(owner)); assertFalse(EconomicOwnership.corporationsAllowed(hive));
        var collectivist = new Empire(owner.id(), owner.name(), owner.raceId(), "Collectivist", owner.treasuryCredits(), 0, owner.controlledSystemIds(), List.of(), Map.of(), owner.unlockedTechIds(), List.of());
        assertTrue(EconomicOwnership.corporationsAllowed(collectivist)); assertTrue(command.validate(state.withEmpires(List.of(collectivist))));
    }
    @Test void staleMaterialStockAndPendingCorporateOrdersRejectDuplicatePublicInvestment() throws Exception {
        var state = world(true); var command = request(state); state = state.withTurn(3); var ordered = command.apply(state);
        var publicBuild = new RequestAIFacilityTransportBuildCommand("owner", "design", "a", "destination", "steel", 0);
        assertSame(ordered, publicBuild.apply(ordered));
        var source = state.commercialHubs().getFirst(); var orders = new HashMap<>(source.activeOrders()); orders.put("steel", new MarketOrder("steel", 0, 0, 1, 1));
        var empty = state.withCommercialHubs(List.of(source.withInventory(state.turn(), source.currentStoredWeightKg() - source.activeOrders().get("steel").supplyKg(), orders), state.commercialHubs().getLast()));
        assertSame(empty, command.apply(empty));
        assertFalse(new QueueShipBuildCommand("owner", "corporate-design", "a").validate(state));
    }
    private GameState carrier(GameState state, String system, String port) throws Exception {
        var template = new AIFacilityLogisticsTest().world().fleets().getFirst().ships().getFirst();
        var ship = new ShipInstance("corporate-ship", "corporate-design", "corp", 1000, 0, template.currentFuelKg(), Map.of()).withPowerState(template.powerState());
        return state.withFleets(List.of(new Fleet("corporate-fleet", "Carrier", "corp", system, "", 0, 0, 0, false, "PASSIVE", List.of(ship), FleetLocation.at(FleetLocation.Site.docked(port)))));
    }
    @Test void aUsefulExistingCorporateCarrierPreventsAStaleConstructionOrder() throws Exception {
        var initial = world(true); var command = request(initial); var ready = carrier(initial.withTurn(3), "a", "port-a");
        var need = FacilitySupplyNeeds.shortages(ready, ready.empires().getFirst(), LogisticsPolicy.current()).getFirst();
        assertNotNull(FacilitySupplyShipment.quote(ready, ready.empires().getFirst(), need, "corporate-ship", "preview", LogisticsPolicy.current()));
        assertSame(ready, command.apply(ready));
        var insufficient = initial.withTurn(3).toBuilder().corporations(List.of(new Corporation("corp", "Carrier company", "owner", "port-a", "TRANSPORT", 0, List.of(), List.of(), List.of()))).build();
        assertSame(insufficient, command.apply(insufficient)); assertEquals(initial.empires(), insufficient.empires());
    }
    @Test void corporateEmptyTravelAndArrivalLoadingKeepTheirOwnPayerAfterReload() throws Exception {
        var state = carrier(world(true), "b", "port-b"); var planner = new EmpireLogisticsPlanner();
        assertEquals(Set.of("corporate-fleet"), planner.plan(state, state.empires().getFirst()).reservedFleetIds());
        state = state.withTurn(1); var command = planner.plan(state, state.empires().getFirst()).commands().getFirst();
        assertInstanceOf(RepositionAIFacilitySupplyCommand.class, command); var staged = command.apply(state);
        assertEquals("corp", staged.tradeRoutes().getFirst().ownerEntityId()); assertEquals(state.empires(), staged.empires());
        var saves = new SaveGameManager(directory); var file = directory.resolve("corporate-staging.scsave").toFile();
        saves.save(file, staged, 1, GameClock.START_TIME.plusDays(1).toString()); var restored = saves.load(file).toGameState(1, staged.status());
        var engine = new SpaceConquestEngine(42); engine.applyGameState(restored); var loading = new LoadAIFacilitySupplyCommand("corp", restored.tradeRoutes().getFirst().id());
        for (int day = 0; day < 15 && !loading.validate(engine.getGameState()); day++) engine.stepTurn();
        var arrived = engine.getGameState(); assertTrue(loading.validate(arrived)); var paid = loading.apply(arrived);
        assertEquals("corp", paid.tradeRoutes().getFirst().ownerEntityId()); assertNull(paid.tradeRoutes().getFirst().supplyReservation());
        assertEquals(arrived.empires(), paid.empires()); assertTrue(EconomicOwnership.cash(paid, "corp") < EconomicOwnership.cash(arrived, "corp"));
    }
    @Test void observationGapsRestartAndTheAffiliatedCarrierLimitIsEnforced() throws Exception {
        var state = world(true); var planner = new TransportConstructionPlanner(); var owner = state.empires().getFirst();
        assertTrue(planner.plan(state, owner, false).isEmpty());
        int window = TransportConstructionPolicy.current().observationTurns();
        for (int turn = 5; turn < 5 + window - 1; turn++) assertTrue(planner.plan(state.withTurn(turn), owner, false).isEmpty());
        assertEquals(1, planner.plan(state.withTurn(5 + window - 1), owner, false).size());
        var ships = new ArrayList<ShipInstance>();
        for (int index = 0; index < TransportConstructionPolicy.current().maximumCarriersPerEmpire(); index++) ships.add(new ShipInstance("busy-" + index, "corporate-design", "corp", 1000, 0, 0, Map.of()));
        var fleet = new Fleet("busy", "Busy", "corp", "a", "", 0, 0, 0, false, "PASSIVE", ships, FleetLocation.at(FleetLocation.Site.docked("port-a")));
        var limited = state.withTurn(window - 1).withFleets(List.of(fleet));
        assertSame(limited, request(state).apply(limited));
    }
}
