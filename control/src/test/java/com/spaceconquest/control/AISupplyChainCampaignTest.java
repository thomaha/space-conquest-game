package com.spaceconquest.control;

import com.spaceconquest.control.ai.*;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.MarketAccount;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.market.HubStockTarget;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AISupplyChainCampaignTest {
    @TempDir Path directory;
    @Test void aiDiscoversBuildsPowersAndDeliversACompleteLocalChainAcrossReload() throws Exception {
        var state = world(false);
        var engine = new SpaceConquestEngine(42); engine.applyGameState(state);
        var queue = new CommandQueue(); var ordered = new HashSet<String>();
        var operations = new EmpireOperatingCapitalPlanner();
        boolean reloaded = false; double paidSales = 0;
        for (int day = 0; day < 260 && stock(engine.getGameState(), "liquid_hydrogen") <= 0; day++) {
            state = engine.getGameState(); var owner = state.empires().getFirst();
            EmpireResearchPlanner.plan(state, owner).forEach(queue::submit);
            operations.plan(state, owner).forEach(queue::submit);
            for (var command : SupplyChainInvestmentPlanner.plan(state, owner)) {
                assertTrue(command.validate(state));
                if (command instanceof RequestAISupplyInvestmentCommand build) ordered.add(build.applicationId());
                queue.submit(command);
            }
            state = queue.drainAndExecute(state);
            if (!reloaded && !state.expansionProjects().isEmpty()) {
                var saves = new SaveGameManager(directory);
                var restored = saves.load(saves.save("ai-chain", state, 1, GameClock.START_TIME.toString()).toFile()).toGameState(state.turn(), state.status());
                assertEquals(state.expansionProjects(), restored.expansionProjects());
                assertEquals(state.industryAccounts(), restored.industryAccounts()); state = restored; reloaded = true;
            }
            engine.applyGameState(state); engine.stepTurn();
            paidSales += engine.getGameState().industryAccounts().stream().mapToDouble(IndustryAccount::salesCredits).sum();
        }
        state = engine.getGameState();
        assertTrue(reloaded, "The AI must construct a real project");
        assertTrue(ordered.contains("hydrogen_liquefaction"), ordered + "; goals: " + SupplyChainNeeds.goals(state, state.empires().getFirst())
                + "; upstream assessment: " + SupplyInvestmentQuote.assess(state, state.empires().getFirst(), "research-world", "water_electrolysis")
                + "; facilities: " + state.industrialFacilities());
        assertTrue(ordered.stream().anyMatch(id -> PowerPlantCatalog.find(id) != null), ordered.toString());
        assertTrue(ordered.stream().anyMatch(id -> IndustryRecipeCatalog.find(id) != null
                && IndustryRecipeCatalog.find(id).outputsKg().containsKey("hydrogen_gas")), ordered.toString());
        assertTrue(stock(state, "liquid_hydrogen") > 0, () -> "No delivered product. Projects: " + engine.getGameState().expansionProjects()
                + "; facilities: " + engine.getGameState().industrialFacilities() + "; research: " + engine.getGameState().researchProjects());
        assertTrue(paidSales > 0);
        assertTrue(stock(state, "purified_water") < stock(world(false), "purified_water"));
        assertTrue(state.industryAccounts().stream().anyMatch(a -> a.generatedKwh() > 0));
        assertTrue(state.industryAccounts().stream().anyMatch(a -> a.wageCostsCredits() > 0));
    }
    @Test void missingCashConstructionStockAndWorkersBlockInvestmentAndStaleCommandsDoNotSpend() throws Exception {
        var state = world(true); var owner = state.empires().getFirst();
        var quote = SupplyChainInvestmentPlanner.next(state, owner); assertNotNull(quote);
        var command = new RequestAISupplyInvestmentCommand(owner.id(), quote.command().planetId(), quote.command().applicationId());
        var empty = state.toBuilder().marketAccounts(List.of()).build();
        assertFalse(command.validate(empty)); assertSame(empty, command.apply(empty));
        var poor = state.withEmpires(List.of(new Empire(owner.id(), owner.name(), owner.raceId(), owner.societyStructure(), 0,
                owner.corporateTaxRate(), owner.controlledSystemIds(), owner.ministries(), owner.systemGovernorAssignments(), owner.unlockedTechIds(), owner.activeShipDesignIds())));
        assertFalse(command.validate(poor)); assertSame(poor, command.apply(poor));
        var hub = state.commercialHubs().getFirst(); var orders = new HashMap<>(hub.activeOrders());
        orders.replaceAll((id, o) -> new MarketOrder(id, 0, o.demandKg(), o.pricePerKg(), o.shortcomingScore()));
        var missing = state.withCommercialHubs(List.of(new CommercialHub(hub.id(), hub.entityId(), 0, 1e14, 0, 10, orders)));
        assertFalse(command.validate(missing)); assertSame(missing, command.apply(missing));
        var noWorkers = state.toBuilder().householdAccounts(List.of()).build();
        assertNull(SupplyChainInvestmentPlanner.next(noWorkers, owner));
        var built = command.apply(state); assertEquals(1, built.expansionProjects().size());
        assertFalse(command.validate(built));
    }
    @Test void controllerQueuesConstructionAndPowerPlanningRequiresPaidCrewAndFuel() throws Exception {
        var state = world(true); var queue = new CommandQueue();
        new EmpireAIController("owner", queue).onGameStateUpdate(state);
        assertFalse(queue.drainAndExecute(state).expansionProjects().isEmpty());
        var plant = new IndustrialFacility("plant", "research-world", "combustion_power", "owner",
                IndustrialFacility.PUBLIC_STATE, 1, 100, "technician", false, 0);
        state = state.toBuilder().industrialFacilities(List.of(plant))
                .industryAccounts(List.of(IndustryAccount.empty("plant").withOperatingCash(1000000))).build();
        assertEquals(0, SupplyInvestmentQuote.freePower(state, plant.planetId()));
        state = state.toBuilder().industryAccounts(List.of(state.industryAccounts().getFirst().withPaidWorkers(100))).build();
        assertTrue(SupplyInvestmentQuote.freePower(state, plant.planetId()) > 0);
        var hub = state.commercialHubs().getFirst(); var orders = new HashMap<>(hub.activeOrders()); orders.remove("hydrocarbons");
        assertEquals(0, SupplyInvestmentQuote.freePower(state.withCommercialHubs(List.of(hub.withInventory(0, 0, orders))), plant.planetId()));
    }
    @Test void aiBuildsAResearchDefinedLaunchProviderForAnExistingCarrierAndFundedRemoteBuyer() throws Exception {
        var state = world(true); var hub = state.commercialHubs().getFirst();
        var orders = new HashMap<>(hub.activeOrders());
        orders.replaceAll((id, o) -> new MarketOrder(id, 1e9, 0, o.pricePerKg(), 0));
        var local = new CommercialHub(hub.id(), hub.entityId(), 0, 1e14, orders.size() * 1e9, 10, orders);
        var remote = new CommercialHub("remote", "offworld", 0, 1e8, 0, 10,
                Map.of("refined_iron", new MarketOrder("refined_iron", 0, 1000, 10, 10)));
        var design = new com.spaceconquest.engine.ship.ShipDesign("freighter", "Freighter", "owner",
                com.spaceconquest.engine.ship.ShipRole.CARGO_TRANSPORT, "steel", List.of("mod_chemical_rocket"), "steel", 0,
                1000, 100, 1000, 0, 1, 0, 500000, true, false);
        var ship = new com.spaceconquest.engine.ship.ShipInstance("ship", design.id(), "owner", 100, 0, 0, Map.of());
        var fleet = new com.spaceconquest.engine.ship.Fleet("fleet", "Carrier", "owner", "sol", "", 0, 0, 0, false, "PASSIVE",
                List.of(ship), com.spaceconquest.engine.ship.FleetLocation.at(com.spaceconquest.engine.ship.FleetLocation.Site.orbit(hub.entityId())));
        state = state.toBuilder().commercialHubs(List.of(local, remote)).shipDesigns(List.of(design)).fleets(List.of(fleet))
                .marketAccounts(List.of(new MarketAccount(hub.id(), 1e12), new MarketAccount(remote.id(), 1e8))).build();
        var quote = SupplyChainInvestmentPlanner.next(state, state.empires().getFirst()); assertNotNull(quote);
        assertNotNull(com.spaceconquest.engine.logistics.InfrastructureService.find(quote.command().applicationId()));
        var command = new RequestAISupplyInvestmentCommand("owner", hub.entityId(), quote.command().applicationId());
        var unfunded = state.toBuilder().marketAccounts(List.of(new MarketAccount(hub.id(), 1e12))).build();
        assertFalse(command.validate(unfunded));
        state = command.apply(state);
        for (int day = 0; day < 30 && !state.expansionProjects().isEmpty(); day++) state = new IndustryProcessor().processIndustrialProduction(state);
        assertTrue(state.expansionProjects().isEmpty());
        var paid = new com.spaceconquest.engine.economy.HouseholdEconomyProcessor().process(state, DataModelLoader.loadRaces());
        state = state.toBuilder().empires(paid.empires()).corporations(paid.corporations()).industryAccounts(paid.industryAccounts())
                .householdAccounts(paid.householdAccounts()).commercialHubs(paid.commercialHubs()).marketAccounts(paid.marketAccounts()).build();
        var day = new IndustryMarketProcessor().process(state, paid.paidWorkersByFacility(), paid.wagesByFacility());
        state = state.toBuilder().industryAccounts(day.industryAccounts()).empires(day.empires()).commercialHubs(day.hubs()).marketAccounts(day.marketAccounts()).build();
        var load = new LoadOrbitalCargoCommand(ship.id(), hub.entityId(), "refined_iron", 10);
        assertTrue(load.validate(state));
        var delivered = load.apply(state);
        assertEquals(10, delivered.fleets().getFirst().ships().getFirst().storedCargoKg().get("refined_iron"));
        assertTrue(delivered.launchUsageKg().values().stream().mapToDouble(Double::doubleValue).sum() > 0);
    }
    GameState world(boolean researched) throws Exception {
        var known = new ArrayList<String>();
        DataModelLoader.loadTechnologies().forEach(t -> { known.add(t.id()); if (researched) t.applications().forEach(a -> known.add(a.id())); });
        var owner = new Empire("owner", "Owner", "human", "Individualist", 1e10, .2, List.of("sol"), List.of(), Map.of(), known, List.of());
        var state = ResearchTestWorld.staffed(GameState.builder().empires(List.of(owner)).build());
        var orders = new HashMap<String, MarketOrder>();
        for (var material : DataModelLoader.loadMaterials()) orders.put(material.id(), new MarketOrder(material.id(), 1e9, 0, 1, 0));
        orders.put("liquid_hydrogen", new MarketOrder("liquid_hydrogen", 0, 10000, 10, 10));
        orders.put("hydrogen_gas", new MarketOrder("hydrogen_gas", 0, 0, 1, 0));
        var hub = new CommercialHub("hub", "research-world", 0, 1e14,
                orders.values().stream().mapToDouble(MarketOrder::supplyKg).sum(), 10, orders)
                .withStockTargets(Map.of("liquid_hydrogen", new HubStockTarget(5000, 10000)));
        return state.toBuilder().commercialHubs(List.of(hub)).marketAccounts(List.of(new MarketAccount("hub", 1e12)))
                .powerGrids(List.of()).build();
    }
    private double stock(GameState state, String material) { return state.commercialHubs().getFirst().activeOrders().get(material).supplyKg(); }
}
