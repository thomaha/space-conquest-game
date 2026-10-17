package com.spaceconquest.control;

import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.*;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.technology.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Paid discovery and construction with real payroll, finite inputs and supplied settled energy budgets. */
class ManufacturingApplicationLifecycleTest {
    @TempDir Path directory;
    @Test void pooledEquipmentExpansionChargesEveryInstalledLineAndKeepsItsCapturedEquipment() throws Exception {
        var state = build(List.of("consumer_goods_mfg"), Map.of("bio_polymers", 1000.0, "refined_rare_earths", 10.0));
        var old = state.industrialFacilities().getFirst(); var equipment = old.productionEquipment();
        var pool = new ProductionEquipment(equipment.id(), equipment.processType(), equipment.workUnitsPerDay(), equipment.referenceOperators(),
                equipment.operatorProfession(), equipment.autonomousFraction(), equipment.operatorSkill(), 4);
        var installed = new IndustrialFacility(old.id(), old.planetId(), old.applicationId(), old.ownerEntityId(), old.ownershipType(), old.tier(),
                old.allocatedWorkers(), old.workerProfessionId(), false, 0, pool);
        state = state.withIndustrialFacilities(List.of(installed));
        var expanded = new ExpandFacilityCommand(installed.id(), installed.tier() + 1, 0).apply(state);
        var project = expanded.expansionProjects().getFirst();
        var perLine = ConstructionMaterialCatalog.facility(state, installed.ownerEntityId(), installed.applicationId(), 1);
        perLine.forEach((id, kg) -> assertEquals(kg * 4, project.requiredMaterialsKg().get(id), 1e-8));
        assertEquals(pool, expanded.industrialFacilities().getFirst().productionEquipment());
    }
    @Test void consumerGoodsDiscoveryBuildsAnApplicationDefinedFactoryAndPaysForMarketDelivery() throws Exception {
        var state = build(List.of("consumer_goods_mfg"), Map.of("bio_polymers", 4990.0, "refined_rare_earths", 10.0));
        var result = produce(state, true);
        assertTrue(stock(result, "consumer_goods") > 0);
        assertTrue(stock(result, "bio_polymers") < stock(state, "bio_polymers"));
        var receipt = result.industryAccounts().stream().filter(a -> a.producedKg().containsKey("consumer_goods")).findFirst().orElseThrow();
        assertTrue(receipt.inputCostsCredits() > 0); assertTrue(receipt.wageCostsCredits() > 0); assertTrue(receipt.salesCredits() > 0);
        assertEquals(result.industryAccounts(), reload(result).industryAccounts());
    }
    @Test void propellantDiscoveryCannotLiquefyWithoutItsInputOrSettledProcessElectricity() throws Exception {
        var state = build(List.of("hydrogen_liquefaction"), Map.of("hydrogen_gas", 10000.0));
        assertEquals(0, stock(produce(state, false), "liquid_hydrogen"));
        var result = produce(state, true);
        assertTrue(stock(result, "liquid_hydrogen") > 0);
        assertTrue(stock(result, "hydrogen_gas") < stock(state, "hydrogen_gas"));
    }
    @Test void minedTungstenBecomesMixedRefineryProductsRetainedResidueAndManufacturedAmmunitionThroughReload() throws Exception {
        var state = build(List.of("tungsten_ore_extraction", "tungsten_refining", "mass_driver_ammunition_mfg"), Map.of("steel", 1000.0));
        state = state.toBuilder().geologicalDeposits(List.of(new GeologicalDeposit("tungsten", "research-world", "tungsten_ore",
                5000, 5000, 1, true, "corp"))).build();
        assertEquals(0, stock(state, "refined_tungsten"));
        var result = produce(reload(state), true);
        assertTrue(result.geologicalDeposits().getFirst().remainingVolumeKg() < 5000);
        assertTrue(stock(result, "mass_driver_slug") > 0);
        var refinery = result.industryAccounts().stream().filter(a -> a.refineryState().latestReceipt() != null).findFirst().orElseThrow();
        assertTrue(refinery.producedKg().get("refined_tungsten") > 0);
        assertTrue(refinery.producedKg().get("refined_manganese") > 0);
        assertTrue(refinery.refineryState().storedKg() > 0);
        var receipt = refinery.refineryState().latestReceipt();
        assertEquals(receipt.intake().massKg(), receipt.productsKg().values().stream().mapToDouble(Double::doubleValue).sum()
                + receipt.residue().massKg(), 1e-6);
        assertEquals(result.commercialHubs(), reload(result).commercialHubs());
    }
    private GameState build(List<String> goals, Map<String, Double> inputs) throws Exception {
        var known = new ArrayList<String>();
        DataModelLoader.loadTechnologies().forEach(t -> { known.add(t.id()); t.applications().stream()
                .filter(a -> !goals.contains(a.id())).forEach(a -> known.add(a.id())); });
        var owner = new Empire("owner", "Owner", "human", "Individualist", 1e9, 0, List.of("sol"), List.of(), Map.of(), known, List.of());
        var state = ResearchTestWorld.staffed(GameState.builder().empires(List.of(owner)).corporations(List.of(new Corporation("corp", "Factory owner",
                "owner", "research-world", "INDUSTRY", 1e9, List.of(), List.of(), List.of()))).build());
        var orders = new HashMap<String, MarketOrder>();
        for (String material : List.of("refined_iron", "refined_aluminum", "refined_copper", "silicon"))
            orders.put(material, new MarketOrder(material, 1e5, 1e6, 1, 0));
        inputs.forEach((id, kg) -> orders.put(id, new MarketOrder(id, kg, 1e6, 1, 0)));
        for (String goal : goals) IndustryRecipeCatalog.find(goal).outputsKg().keySet().forEach(id ->
                orders.putIfAbsent(id, new MarketOrder(id, 0, 1e6, 10, 0)));
        state = state.toBuilder().commercialHubs(List.of(new CommercialHub("hub", "research-world", 0, 1e9,
                orders.values().stream().mapToDouble(MarketOrder::supplyKg).sum(), 10, orders)))
                .marketAccounts(List.of(new MarketAccount("hub", 1e9))).build();
        var queue = new CommandQueue();
        var research = new ResearchProcessor(new Random() { @Override public double nextDouble() { return .01; } });
        for (String goal : goals) {
            var work = ProductionWork.definition(goal);
            var build = new BuildFacilityCommand("research-world", goal, "corp", IndustrialFacility.PRIVATE_CORPORATE,
                    work.equipment().referenceOperators(), work.equipment().operatorProfession());
            assertFalse(build.validate(state), "Application discovery must precede construction");
            var start = new StartResearchCommand("owner", goal, true, 30); assertTrue(start.validate(state)); queue.submit(start);
            state = queue.drainAndExecute(state);
            for (int day = 0; day < 200 && !state.researchProjects().isEmpty(); day++) {
                var result = new ResearchTickProcessor().process(state, List.of(), research);
                state = state.toBuilder().empires(result.empires()).researchProjects(result.projects()).applicationOptimizations(result.optimizations()).build();
            }
            assertTrue(state.empires().getFirst().unlockedTechIds().contains(goal));
            state = new ResolveApplicationResearchCommand("owner", goal, true).apply(state);
            assertTrue(build.validate(state)); queue.submit(build); state = queue.drainAndExecute(state);
        }
        double cash = state.corporations().getFirst().liquidCapitalReserves();
        for (int day = 0; day < 40 && !state.expansionProjects().isEmpty(); day++) state = new IndustryProcessor().processIndustrialProduction(state);
        assertTrue(state.expansionProjects().isEmpty()); assertTrue(state.corporations().getFirst().liquidCapitalReserves() < cash);
        return reload(state);
    }
    private GameState produce(GameState state, boolean power) throws Exception {
        var paid = new HouseholdEconomyProcessor().process(state, DataModelLoader.loadRaces());
        state = state.toBuilder().empires(paid.empires()).corporations(paid.corporations()).householdAccounts(paid.householdAccounts())
                .commercialHubs(paid.commercialHubs()).marketAccounts(paid.marketAccounts()).industryAccounts(paid.industryAccounts()).build();
        var budgets = new HashMap<String, ProductionWork.EnergyBudget>();
        if (power) state.industrialFacilities().forEach(f -> budgets.put(f.id(), new ProductionWork.EnergyBudget(1e7, 0)));
        var result = new IndustryMarketProcessor().process(state, paid.paidWorkersByFacility(), paid.wagesByFacility(), paid.paidWorkersByFacility(), budgets);
        return state.toBuilder().empires(result.empires()).corporations(result.corporations()).industryAccounts(result.industryAccounts())
                .commercialHubs(result.hubs()).marketAccounts(result.marketAccounts()).geologicalDeposits(result.deposits()).build();
    }
    private GameState reload(GameState state) throws Exception {
        var saves = new SaveGameManager(directory); var file = saves.save("manufacturing", state, 1, GameClock.START_TIME.toString());
        return saves.load(file.toFile()).toGameState(state.turn(), state.status());
    }
    private double stock(GameState state, String id) {
        var order = state.commercialHubs().getFirst().activeOrders().get(id); return order == null ? 0 : order.supplyKg();
    }
}
