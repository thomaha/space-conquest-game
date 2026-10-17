package com.spaceconquest.control;

import com.spaceconquest.control.command.QueueShipBuildCommand;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.*;
import com.spaceconquest.engine.ship.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ShipyardAccountingTest {
    @TempDir Path directory;
    private GameState taxed() throws Exception {
        var state = new SharedShipyardTest().world("yard-corp"); var owner = state.empires().getFirst();
        return state.withEmpires(List.of(new Empire(owner.id(), owner.name(), owner.raceId(), owner.societyStructure(), owner.treasuryCredits(), .2,
                owner.controlledSystemIds(), owner.ministries(), owner.systemGovernorAssignments(), owner.unlockedTechIds(), owner.activeShipDesignIds())));
    }
    @Test void normalPaidWorkHistoryAndTaxContinueThroughCheckpointAndCommissioning() throws Exception {
        var state = new QueueShipBuildCommand("corp", "corporate-design", "a").apply(taxed()); var order = state.shipConstructionOrders().getFirst();
        // A separately captured commercial tariff supplies a profitable bounded accounting scenario.
        var contract = new ShipyardContract("yard-corp", 100, 0);
        state = state.withShipConstructionOrders(List.of(new ShipConstructionOrder(order.id(), order.ownerEntityId(), order.designId(), order.systemId(), order.yardBodyId(),
                order.accumulatedWorkHours(), order.requiredWorkHours(), order.requiredMaterialsKg(), order.consumedMaterialsKg(), contract)));
        var engine = new SpaceConquestEngine(42); engine.applyGameState(state); SpaceConquestEngine restored = null; double tax = 0;
        for (int day = 0; day < 8; day++) {
            engine.stepTurn(); var current = engine.getGameState();
            tax += current.corporateTaxAccounts().stream().filter(account -> account.corporationId().equals("yard-corp")).mapToDouble(CorporateTaxAccount::assessedTaxCredits).sum();
            assertEquals(0, current.corporateTaxAccounts().stream().filter(account -> account.corporationId().equals("corp")).findFirst().orElseThrow().assessedTaxCredits(), .001);
            if (restored != null) {
                restored.stepTurn(); assertEquals(current.shipyardLedger(), restored.getGameState().shipyardLedger());
                assertEquals(current.corporateTaxAccounts(), restored.getGameState().corporateTaxAccounts());
                assertEquals(current.corporations(), restored.getGameState().corporations());
            }
            if (day == 1) {
                var saves = new SaveGameManager(directory); var file = directory.resolve("accounting.scsave").toFile();
                saves.save(file, current, 1, GameClock.START_TIME.plusDays(current.turn()).toString()); restored = new SpaceConquestEngine(42);
                restored.applyGameState(saves.load(file).toGameState(current.turn(), current.status()));
            }
        }
        var end = engine.getGameState(); assertTrue(tax > 0, end.shipyardLedger().toString()); assertTrue(end.shipConstructionOrders().isEmpty());
        assertTrue(end.shipyardLedger().history().stream().anyMatch(ShipyardReceipt::completed));
        assertEquals(order.requiredWorkHours() * contract.feeCreditsPerWorkUnit(), end.shipyardLedger().history().stream().mapToDouble(ShipyardReceipt::feeCredits).sum(), .001);
        assertTrue(end.shipyardLedger().history().stream().mapToDouble(ShipyardReceipt::wageCostsCredits).sum() > 0);
    }
    @Test void settledHistoryIsNotRetaxedAfterReloadAndTrimmingDoesNotLoseEarnings() throws Exception {
        var state = taxed(); var ledger = ShipyardLedger.empty(); int count = ShipyardAccessPolicy.current().maximumHistoryEntries() + 5;
        for (int index = 0; index < count; index++) ledger = ledger.append(new ShipyardReceipt(0, "order-" + index, "corp", "yard-corp", "a", "port-a", 1, 10, 0, true, "Commissioned"));
        state = state.toBuilder().shipyardLedger(ledger).build(); var processor = new CorporateProfitTaxProcessor(); var result = processor.process(state);
        var account = result.accounts().stream().filter(value -> value.corporationId().equals("yard-corp")).findFirst().orElseThrow();
        assertEquals(count * 10 * .2, account.assessedTaxCredits(), .001); assertEquals(ShipyardAccessPolicy.current().maximumHistoryEntries(), ledger.history().size());
        var settled = state.toBuilder().corporations(result.corporations()).corporateTaxAccounts(result.accounts()).build();
        var saves = new SaveGameManager(directory); var file = directory.resolve("settled.scsave").toFile(); saves.save(file, settled, 1, GameClock.START_TIME.toString());
        var loaded = saves.load(file).toGameState(settled.turn(), settled.status()); var again = processor.process(loaded);
        assertEquals(0, again.accounts().stream().filter(value -> value.corporationId().equals("yard-corp")).findFirst().orElseThrow().assessedTaxCredits());
    }
    @Test void surfaceCostsAreDeductedOnceAndOrbitalLossesCarryForward() throws Exception {
        var state = taxed(); var facilities = new ArrayList<>(state.industrialFacilities()); facilities.add(new com.spaceconquest.engine.industry.IndustrialFacility("surface-yard", "planet-a", "surface_shipyard", "yard-corp", "PRIVATE_CORPORATE", 1, 100, "industrial_worker", false, 0));
        var accounts = new ArrayList<>(state.industryAccounts()); accounts.add(new com.spaceconquest.engine.industry.IndustryAccount("surface-yard", Map.of(), Map.of(), Map.of(), 0, 20, 0, 0, 0, 0));
        var ledger = ShipyardLedger.empty().append(new ShipyardReceipt(0, "surface-order", "corp", "yard-corp", "a", "planet-a", 50, 100, 0, true, "Commissioned"));
        state = state.toBuilder().industrialFacilities(facilities).industryAccounts(accounts).shipyardLedger(ledger).build();
        var result = new CorporateProfitTaxProcessor().process(state); var tax = result.accounts().stream().filter(value -> value.corporationId().equals("yard-corp")).findFirst().orElseThrow();
        assertEquals(80, tax.taxableProfitCredits(), .001); assertEquals(16, tax.assessedTaxCredits(), .001);
        var loss = taxed().toBuilder().shipyardLedger(ShipyardLedger.empty().append(new ShipyardReceipt(0, "", "", "yard-corp", "a", "port-a", 0, 0, 100, false, "Paid yard payroll"))).build();
        var first = new CorporateProfitTaxProcessor().process(loss);
        var later = loss.toBuilder().corporations(first.corporations()).corporateTaxAccounts(first.accounts())
                .shipyardLedger(loss.shipyardLedger().append(new ShipyardReceipt(1, "work", "corp", "yard-corp", "a", "port-a", 50, 150, 0, true, "Commissioned"))).build();
        var second = new CorporateProfitTaxProcessor().process(later);
        assertEquals(10, second.accounts().stream().filter(value -> value.corporationId().equals("yard-corp")).findFirst().orElseThrow().assessedTaxCredits(), .001);
    }
    @Test void stoppedWorkRecordsABlockerWithoutInventingIncome() throws Exception {
        var ordered = new QueueShipBuildCommand("corp", "corporate-design", "a").apply(taxed()); var blocked = ordered.withCommercialHubs(List.of());
        var result = new ShipConstructionProcessor().process(blocked); var row = result.shipyardLedger().history().getLast();
        assertEquals(0, row.workUnits()); assertEquals(0, row.feeCredits()); assertFalse(row.completed()); assertTrue(row.status().contains("materials"));
        assertEquals(blocked.corporations(), result.corporations());
    }
}
