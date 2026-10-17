package com.spaceconquest.control;

import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.MarketAccount;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.technology.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RefineryLifecycleTest {
    @TempDir Path directory;
    @Test void paidScienceUnlocksBetterRecoveryAndQueuedReprocessingConsumesOnlySavedResidue() throws Exception {
        var state = world();
        var initial = produce(state); state = apply(state, initial);
        var original = RefineryProcess.inventory(state, "refinery");
        var lot = original.tailings().getFirst();
        assertEquals(.5, original.latestReceipt().productsKg().get("silver"), 1e-9);
        var queue = new CommandQueue();
        queue.submit(new StartResearchCommand("owner", "rare_earth_byproduct_recovery", true, 30));
        state = queue.drainAndExecute(state);
        var research = new ResearchProcessor(new Random() { @Override public double nextDouble() { return .01; } });
        for (int tick = 0; tick < 200 && !state.researchProjects().isEmpty(); tick++) {
            var result = new ResearchTickProcessor().process(state, List.of(), research);
            state = state.toBuilder().empires(result.empires()).researchProjects(result.projects()).applicationOptimizations(result.optimizations()).build();
        }
        assertTrue(state.empires().getFirst().unlockedTechIds().contains("rare_earth_byproduct_recovery"));
        state = new ResolveApplicationResearchCommand("owner", "rare_earth_byproduct_recovery", true).apply(state);
        assertEquals(original, RefineryProcess.inventory(state, "refinery"));
        var saves = new SaveGameManager(directory); var file = saves.save("refinery", state, 1, GameClock.START_TIME.toString());
        state = saves.load(file.toFile()).toGameState(state.turn(), state.status());
        var select = new SetRefineryInputCommand("owner", "refinery", null, lot.id());
        assertTrue(select.validate(state)); assertFalse(new SetRefineryInputCommand("foreign", "refinery", null, lot.id()).validate(state));
        assertFalse(new SetRefineryInputCommand("owner", "refinery", null, "foreign_lot").validate(state));
        assertFalse(new SetRefineryInputCommand("owner", "refinery", "gold", null).validate(state));
        queue.submit(select); assertNull(RefineryProcess.inventory(state, "refinery").selectedTailingsId());
        state = queue.drainAndExecute(state); assertEquals(lot.id(), RefineryProcess.inventory(state, "refinery").selectedTailingsId());
        double ore = state.commercialHubs().getFirst().activeOrders().get("rare_earth_fluorides").supplyKg();
        var reprocessed = produce(state); state = apply(state, reprocessed);
        var receipt = RefineryProcess.inventory(state, "refinery").latestReceipt();
        assertEquals(1.125, receipt.productsKg().get("silver"), 1e-9);
        assertEquals(ore, state.commercialHubs().getFirst().activeOrders().get("rare_earth_fluorides").supplyKg());
        assertFalse(select.validate(state));
        assertTrue(RefineryProcess.inventory(state, "refinery").tailings().stream().noneMatch(l -> l.id().equals(lot.id())));
        var after = state; assertSame(after, select.apply(after));
        assertTrue(new SetRefineryInputCommand("owner", "refinery", "rare_earth_fluorides", null).validate(state));
    }
    @Test void corporateSelectionUsesTheAffiliatedEmpireAndCannotBorrowAnotherFacilitysResidue() {
        var state = world();
        var lot = new RefineryLot("other_residue", "other", Map.of("Ag", 1.0, "F", 99.0));
        var accounts = new ArrayList<>(state.industryAccounts());
        accounts.add(IndustryAccount.empty("other").withRefineryState(new RefineryState(null, null, List.of(lot), null)));
        state = state.toBuilder().industryAccounts(accounts).build();
        assertFalse(new SetRefineryInputCommand("owner", "refinery", null, lot.id()).validate(state));
        var old = state.industrialFacilities().getFirst();
        var corporate = new IndustrialFacility(old.id(), old.planetId(), old.applicationId(), "corp", IndustrialFacility.PRIVATE_CORPORATE,
                old.tier(), old.allocatedWorkers(), old.workerProfessionId(), false, 0, old.productionEquipment());
        state = state.toBuilder().industrialFacilities(List.of(corporate)).corporations(List.of(new Corporation("corp", "Corp", "owner", "research-world",
                "INDUSTRY", 100000, List.of(old.id()), List.of(), List.of()))).build();
        assertTrue(new SetRefineryInputCommand("owner", "refinery", "rare_earth_fluorides", null).validate(state));
        assertFalse(new SetRefineryInputCommand("corp", "refinery", "rare_earth_fluorides", null).validate(state));
    }
    private GameState world() {
        var owner = new Empire("owner", "Owner", "human", "Individualist", 1e7, 0, List.of("sol"), List.of(), Map.of(),
                List.of("electricity", "industrial_production", "rare_earth_refining"), List.of());
        var state = ResearchTestWorld.staffed(GameState.builder().empires(List.of(owner)).build());
        var orders = new HashMap<String, MarketOrder>();
        for (String id : List.of("rare_earth_fluorides", "purified_water", "carbon")) orders.put(id, new MarketOrder(id, 10000, 100000, 1, 0));
        for (String id : List.of("refined_rare_earths", "silver", "gold")) orders.put(id, new MarketOrder(id, 0, 100000, 10, 0));
        return state.toBuilder().industrialFacilities(List.of(new IndustrialFacility("refinery", "research-world", "rare_earth_refining", "owner",
                        IndustrialFacility.PUBLIC_STATE, 1, 10, "industrial_worker", false, 0)))
                .industryAccounts(List.of(IndustryAccount.empty("refinery").withOperatingCash(1e6)))
                .commercialHubs(List.of(new CommercialHub("hub", "research-world", 0, 1e7, 30000, 10, orders)))
                .marketAccounts(List.of(new MarketAccount("hub", 1e7))).build();
    }
    private IndustryMarketProcessor.TurnResult produce(GameState state) {
        return new IndustryMarketProcessor().process(state, Map.of("refinery", 10), Map.of(), Map.of("refinery", 10),
                Map.of("refinery", new ProductionWork.EnergyBudget(7500, 0)));
    }
    private GameState apply(GameState state, IndustryMarketProcessor.TurnResult result) {
        return state.toBuilder().empires(result.empires()).corporations(result.corporations()).industryAccounts(result.industryAccounts())
                .commercialHubs(result.hubs()).marketAccounts(result.marketAccounts()).build();
    }
}
