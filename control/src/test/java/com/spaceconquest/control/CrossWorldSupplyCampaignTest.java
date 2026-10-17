package com.spaceconquest.control;

import com.spaceconquest.control.ai.*;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.*;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.logistics.*;
import com.spaceconquest.engine.market.HubStockTarget;
import com.spaceconquest.engine.ship.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CrossWorldSupplyCampaignTest {
    @TempDir Path directory;
    private GameState world() throws Exception {
        var state = new AIFacilityLogisticsTest().world(); var old = state.empires().getFirst(); var known = new ArrayList<String>();
        DataModelLoader.loadTechnologies().forEach(t -> { known.add(t.id()); t.applications().forEach(a -> known.add(a.id())); });
        known.remove("mining_outpost");
        var owner = new Empire(old.id(), old.name(), old.raceId(), old.societyStructure(), 1e10, 0, old.controlledSystemIds(), old.ministries(), old.systemGovernorAssignments(), known, old.activeShipDesignIds());
        var hubs = new ArrayList<CommercialHub>();
        for (String id : List.of("source", "destination")) {
            var orders = new HashMap<String, MarketOrder>();
            for (var material : DataModelLoader.loadMaterials()) orders.put(material.id(), new MarketOrder(material.id(), 1e9, 0, 1, 0));
            orders.put("iron_ore", new MarketOrder("iron_ore", 0, id.equals("source") ? 0 : 100, id.equals("source") ? 1 : 8, 10));
            var hub = new CommercialHub(id, id.equals("source") ? "planet-a" : "planet-b", 0, 1e14, 1e12, 10, orders);
            if (id.equals("destination")) hub = hub.withStockTargets(Map.of("iron_ore", new HubStockTarget(100, 100)));
            hubs.add(hub);
        }
        var exhausted = new IndustrialFacility("old-mine", "planet-b", "mining_outpost", "owner", IndustrialFacility.PUBLIC_STATE, 1, 100, "miner", false, 0);
        var plant = new IndustrialFacility("power", "planet-a", "combustion_power", "owner", IndustrialFacility.PUBLIC_STATE, 1, 100, "technician", false, 0);
        var fleet = state.fleets().getFirst().withLocation(FleetLocation.at(FleetLocation.Site.surface("planet-a")));
        state = state.toBuilder().empires(List.of(owner)).commercialHubs(hubs).fleets(List.of(fleet)).industrialFacilities(List.of(exhausted, plant))
                .systemEconomies(List.of("a", "b").stream().map(id -> new SystemEconomy(id, "owner", .2, .2, .2, .2, .2, 100000, 0,
                        1, 1, 1, 1, 1, 50, 30, 15, 10, 20, 30, 25, 100, .1)).toList())
                .industryAccounts(List.of(IndustryAccount.empty("old-mine").withOperatingCash(50000).withPaidWorkers(100), IndustryAccount.empty("power").withOperatingCash(1000000).withPaidWorkers(100)))
                .marketAccounts(List.of(new MarketAccount("source", 0), new MarketAccount("destination", 1e7)))
                .geologicalDeposits(List.of(new GeologicalDeposit("empty", "planet-b", "iron_ore", 1000, 0, 1, true, "owner"),
                        new GeologicalDeposit("fresh", "planet-a", "iron_ore", 1e9, 1e9, 1, true, "owner"))).build();
        var paid = new HouseholdEconomyProcessor().process(state, DataModelLoader.loadRaces());
        return state.toBuilder().empires(paid.empires()).householdAccounts(paid.householdAccounts()).industryAccounts(paid.industryAccounts()).build();
    }
    @Test void depletedWorldDevelopsAnotherSourceAndReceivesOnlyPaidProducedOreAcrossReload() throws Exception {
        var state = world(); var owner = state.empires().getFirst(); var candidate = CrossWorldSupplyPlanner.next(state, owner); assertNotNull(candidate);
        var destination = state.commercialHubs().getLast(); var requests = new HashMap<>(destination.activeOrders());
        requests.put("iron_ore", new MarketOrder("iron_ore", 0, 1500, 8, 10));
        state = state.withCommercialHubs(List.of(state.commercialHubs().getFirst(), new CommercialHub(destination.id(), destination.entityId(), 0, 1e14, 1e12, 10, requests)
                .withStockTargets(Map.of("iron_ore", new HubStockTarget(1500, 1500)))));
        candidate = CrossWorldSupplyPlanner.next(state, owner); assertNotNull(candidate);
        assertEquals("source", candidate.sourceHubId()); assertEquals("destination", candidate.destinationHubId());
        var command = new StartSupplyDevelopmentCommand(owner.id(), candidate.sourceHubId(), candidate.destinationHubId(), candidate.materialId());
        double treasury = owner.treasuryCredits(); state = command.apply(state); var plan = state.supplyDevelopmentPlans().getFirst();
        assertEquals(treasury, state.empires().getFirst().treasuryCredits() + plan.buyerFundingCredits(), 1e-6);
        assertEquals(plan.buyerFundingCredits(), state.marketAccounts().stream().filter(a -> a.hubId().equals("source")).findFirst().orElseThrow().unsettledSalesCredits());
        assertFalse(command.validate(state)); assertEquals(0, SupplyChainNeeds.stock(state.commercialHubs().getFirst(), "iron_ore"));
        var exhausted = state.industrialFacilities().getFirst(); var release = new ReleaseExhaustedWorkersCommand(owner.id()); assertTrue(release.validate(state));
        state = release.apply(state); assertEquals(0, state.industrialFacilities().getFirst().allocatedWorkers());
        assertEquals(exhausted.productionEquipment(), state.industrialFacilities().getFirst().productionEquipment());
        var operations = new EmpireOperatingCapitalPlanner(); var engine = new SpaceConquestEngine(42); var queue = new CommandQueue();
        var ordered = new HashSet<String>(); var saves = new SaveGameManager(directory); boolean reloaded = false;
        double produced = 0, sales = 0; double deposit = state.geologicalDeposits().getLast().remainingVolumeKg();
        for (int day = 0; day < 160; day++) {
            owner = state.empires().getFirst(); CrossWorldSupplyPlanner.commands(state, owner).forEach(queue::submit);
            EmpireResearchPlanner.plan(state, owner).forEach(queue::submit);
            operations.plan(state, owner).forEach(queue::submit);
            for (var investment : SupplyChainInvestmentPlanner.plan(state, owner)) {
                if (investment instanceof RequestAISupplyInvestmentCommand build) ordered.add(build.applicationId());
                queue.submit(investment);
            }
            state = queue.drainAndExecute(state);
            if (!reloaded && state.tradeRoutes().stream().anyMatch(r -> r.id().equals(plan.routeId()) && r.onboardKg() > 0)
                    && (state.fleets().getFirst().location().inTransit() || state.fleets().getFirst().hasInterstellarOrder())) {
                var restored = saves.load(saves.save("remote-source", state, 1, GameClock.START_TIME.toString()).toFile()).toGameState(state.turn(), state.status());
                assertEquals(state.supplyDevelopmentPlans(), restored.supplyDevelopmentPlans()); assertEquals(state.fleets(), restored.fleets()); state = restored; reloaded = true;
            }
            engine.applyGameState(state); engine.stepTurn(); state = engine.getGameState();
            produced += state.industryAccounts().stream().mapToDouble(a -> a.producedKg().getOrDefault("iron_ore", 0.0)).sum();
            sales += state.industryAccounts().stream().filter(a -> !a.facilityId().equals("old-mine")).mapToDouble(IndustryAccount::salesCredits).sum();
            if (state.tradeRoutes().stream().anyMatch(r -> r.id().equals(plan.routeId()) && r.totalVolumeMovedKg() >= plan.quantityKg())) break;
        }
        assertTrue(reloaded, () -> "No funded departure: " + SupplyDevelopmentProgress.next(engine.getGameState(), engine.getGameState().supplyDevelopmentPlans().getFirst()).reason()
                + "; orders " + ordered + "; research " + engine.getGameState().researchProjects() + "; history " + engine.getGameState().researchHistory()
                + "; scientists " + engine.getGameState().householdAccounts().stream().filter(a -> a.professionId().equals("scientist")).toList());
        assertTrue(ordered.contains("mining_outpost"));
        assertTrue(state.researchHistory().stream().anyMatch(e -> e.targetId().equals("mining_outpost")
                && e.kind() == com.spaceconquest.engine.technology.ResearchEvent.Kind.COMPLETED));
        assertTrue(ordered.stream().anyMatch(id -> InfrastructureService.find(id) != null));
        assertTrue(produced >= 1500); assertTrue(sales > 0);
        assertTrue(state.tradeRoutes().stream().anyMatch(r -> r.id().equals(plan.routeId()) && r.totalVolumeMovedKg() >= 1500),
                () -> engine.getGameState().tradeRoutes() + "; " + SupplyDevelopmentProgress.next(engine.getGameState(), engine.getGameState().supplyDevelopmentPlans().getFirst()).reason()
                        + "; fleet " + engine.getGameState().fleets().getFirst().location());
        assertEquals(deposit - produced, state.geologicalDeposits().getLast().remainingVolumeKg(), 1e-4);
        assertEquals(0, state.geologicalDeposits().getFirst().remainingVolumeKg());
        assertTrue(SupplyChainNeeds.stock(state.commercialHubs().stream().filter(h -> h.id().equals("destination")).findFirst().orElseThrow(), "iron_ore") >= 1500);
        state = new AdvanceSupplyDevelopmentCommand("owner", plan.id()).apply(state);
        assertTrue(state.supplyDevelopmentPlans().getFirst().closed());
    }
    @Test void staleControlFundingAndSourceAvailabilityPreventNewPlansWithoutSpending() throws Exception {
        var state = world(); var owner = state.empires().getFirst(); var q = CrossWorldSupplyPlanner.next(state, owner); assertNotNull(q);
        var command = new StartSupplyDevelopmentCommand(owner.id(), q.sourceHubId(), q.destinationHubId(), q.materialId());
        var unfunded = state.toBuilder().marketAccounts(List.of()).build(); assertFalse(command.validate(unfunded)); assertSame(unfunded, command.apply(unfunded));
        var depleted = state.withGeologicalDeposits(state.geologicalDeposits().stream().map(d -> new GeologicalDeposit(d.id(), d.planetId(), d.materialId(), d.initialVolumeKg(), 0, d.concentrationModifier(), true, d.ownerEntityId())).toList());
        assertFalse(command.validate(depleted)); assertSame(depleted, command.apply(depleted));
        var lost = state.withEmpires(List.of(new Empire(owner.id(), owner.name(), owner.raceId(), owner.societyStructure(), owner.treasuryCredits(), 0,
                List.of("b"), owner.ministries(), owner.systemGovernorAssignments(), owner.unlockedTechIds(), owner.activeShipDesignIds())));
        assertFalse(command.validate(lost)); assertSame(lost, command.apply(lost));
    }
    @Test void exhaustedCrewFillsLocalReplacementJobsWithoutMovingPeopleOrInventingPaidWork() throws Exception {
        var state = world(); var owner = state.empires().getFirst();
        var knowledge = new ArrayList<>(owner.unlockedTechIds()); knowledge.add("mining_outpost");
        state = state.withEmpires(List.of(new Empire(owner.id(), owner.name(), owner.raceId(), owner.societyStructure(), owner.treasuryCredits(), 0,
                owner.controlledSystemIds(), owner.ministries(), owner.systemGovernorAssignments(), knowledge, owner.activeShipDesignIds())));
        var q = CrossWorldSupplyPlanner.next(state, state.empires().getFirst());
        state = new StartSupplyDevelopmentCommand("owner", q.sourceHubId(), q.destinationHubId(), q.materialId()).apply(state);
        var exhausted = new IndustrialFacility("copper", "planet-a", "copper_ore_mining", "owner", IndustrialFacility.PUBLIC_STATE, 1, 100, "miner", false, 0);
        var replacement = new IndustrialFacility("iron", "planet-a", "mining_outpost", "owner", IndustrialFacility.PUBLIC_STATE, 1, 0, "miner", false, 0);
        var facilities = new ArrayList<>(state.industrialFacilities()); facilities.add(exhausted); facilities.add(replacement);
        var accounts = new ArrayList<>(state.industryAccounts()); accounts.add(IndustryAccount.empty("copper").withOperatingCash(50000).withPaidWorkers(100));
        accounts.add(IndustryAccount.empty("iron").withOperatingCash(50000));
        var deposits = new ArrayList<>(state.geologicalDeposits()); deposits.add(new GeologicalDeposit("spent-copper", "planet-a", "copper_ore", 1000, 0, 1, true, "owner"));
        state = state.toBuilder().industrialFacilities(facilities).industryAccounts(accounts).geologicalDeposits(deposits).build();
        var released = new ReleaseExhaustedWorkersCommand("owner").apply(state);
        assertEquals(0, released.industrialFacilities().stream().filter(f -> f.id().equals("copper")).findFirst().orElseThrow().allocatedWorkers());
        assertEquals(100, released.industrialFacilities().stream().filter(f -> f.id().equals("iron")).findFirst().orElseThrow().allocatedWorkers());
        assertEquals(state.solarSystems(), released.solarSystems()); assertEquals(state.householdAccounts(), released.householdAccounts());
        assertEquals(replacement.productionEquipment(), released.industrialFacilities().stream().filter(f -> f.id().equals("iron")).findFirst().orElseThrow().productionEquipment());
        assertEquals(0, released.industryAccounts().stream().filter(a -> a.facilityId().equals("iron")).findFirst().orElseThrow().paidWorkers());
        var payroll = new HouseholdEconomyProcessor().process(released, DataModelLoader.loadRaces());
        assertTrue(payroll.paidWorkersByFacility().getOrDefault("iron", 0) > 0); assertEquals(0, payroll.paidWorkersByFacility().getOrDefault("copper", 0));
    }
    @Test void savedProcurementNeedCanBuildAMissingCarrierAndPreservesLocalSourceReserves() throws Exception {
        var before = new AITransportConstructionTest().world(false);
        var plan = new SupplyDevelopmentPlan("need", "owner", "source", "destination", "steel", 1000, 10000, 0, "", false);
        var state = SupplyDevelopmentDemand.refresh(before.toBuilder().supplyDevelopmentPlans(List.of(plan))
                .industrialFacilities(before.industrialFacilities().stream().filter(f -> !f.id().equals("factory")).toList()).build(), before);
        var need = FacilitySupplyNeeds.shortages(state, state.empires().getFirst(), LogisticsPolicy.current()).stream()
                .filter(n -> n.materialId().equals("steel")).findFirst().orElseThrow();
        assertEquals(1000, need.targetKg());
        assertEquals(before.commercialHubs().getFirst().activeOrders().get("steel").demandKg() + 1000,
                state.commercialHubs().getFirst().activeOrders().get("steel").demandKg());
        var planner = new TransportConstructionPlanner(); List<GameCommand> commands = List.of();
        for (int day = 0; day < TransportConstructionPolicy.current().observationTurns(); day++)
            commands = planner.plan(state.withTurn(day), state.empires().getFirst(), false);
        assertFalse(commands.isEmpty());
        var command = assertInstanceOf(RequestAIFacilityTransportBuildCommand.class, commands.getFirst());
        state = state.withTurn(TransportConstructionPolicy.current().observationTurns() - 1);
        assertTrue(command.validate(state)); assertEquals(1, command.apply(state).shipConstructionOrders().size());
    }
    @Test void controllerReservesAnExistingCarrierAndLostControlClosesThePlanWithoutRefundingSpentFunding() throws Exception {
        var initial = world(); var queue = new CommandQueue(); new EmpireAIController("owner", queue).onGameStateUpdate(initial);
        var state = queue.drainAndExecute(initial); var plan = state.supplyDevelopmentPlans().getFirst();
        assertEquals("fleet", plan.fleetId()); assertTrue(CrossWorldSupplyPlanner.reservedFleets(state, state.empires().getFirst()).contains("fleet"));
        assertFalse(FacilitySupplyShipment.available(state, state.empires().getFirst(), state.fleets().getFirst()));
        var owner = state.empires().getFirst();
        var lost = state.withEmpires(List.of(new Empire(owner.id(), owner.name(), owner.raceId(), owner.societyStructure(), owner.treasuryCredits(), 0,
                List.of("b"), owner.ministries(), owner.systemGovernorAssignments(), owner.unlockedTechIds(), owner.activeShipDesignIds())));
        var closed = new AdvanceSupplyDevelopmentCommand("owner", plan.id()).apply(lost);
        assertTrue(closed.supplyDevelopmentPlans().getFirst().closed());
        assertEquals(lost.empires(), closed.empires()); assertEquals(lost.marketAccounts(), closed.marketAccounts()); assertEquals(lost.fleets(), closed.fleets());
        assertTrue(SupplyDevelopmentDemand.requests(closed, "source").isEmpty());
    }
}
