package com.spaceconquest.engine.economy;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BusinessSettlementTest {
    @TempDir Path directory;
    private GameState transaction(double cash) throws Exception {
        var state = BusinessTestWorld.create("industrial_soil_cultivation");
        var firm = state.corporations().getFirst();
        return state.toBuilder().industrialFacilities(List.of(state.industrialFacilities().getFirst())).corporations(List.of(new Corporation(firm.id(), firm.name(),
                firm.empireId(), firm.headquartersEntityId(), firm.marketOrientation(), cash, firm.ownedFacilityIds(), firm.ownedShipIds(), firm.claimedVeinIds())))
                .industryAccounts(List.of(new IndustryAccount("producer", Map.of(), Map.of(), Map.of(), 100, 100, 1200, 0, 0, 0))).build();
    }
    @Test void maintenanceIsPaidAndDeductedBeforeTaxWhileCapitalIsHeldAfterTaxExactlyOnce() throws Exception {
        var state = transaction(10000); var result = BusinessSettlement.closeDay(state); var end = result.state();
        var account = end.industryAccounts().getFirst(); var book = end.businessLedger().account("producer-owner");
        assertEquals(80, account.maintenanceCostsCredits()); assertEquals(920, account.realizedResultCredits());
        assertEquals(230, end.corporateTaxAccounts().getFirst().paidTaxCredits());
        assertTrue(book.replacementReserveCredits() > 0); assertEquals(920, book.operatingResultToday());
        assertEquals(10000, end.corporations().getFirst().liquidCapitalReserves() + book.replacementReserveCredits()
                + end.businessLedger().serviceClearingCredits() + result.collectedByBody().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-8);
        assertEquals(end, BusinessSettlement.closeDay(end).state());
        var saves = new SaveGameManager(directory); var file = directory.resolve("business.scsave").toFile();
        saves.save(file, end, 1, GameClock.START_TIME.toString());
        assertEquals(end.businessLedger(), saves.load(file).toGameState(end.turn(), end.status()).businessLedger());
    }
    @Test void anUnfundedInvoiceRemainsDueAndDoesNotCreateCashOrAReserve() throws Exception {
        var state = transaction(10); var end = BusinessSettlement.closeDay(state).state();
        assertEquals(0, end.corporations().getFirst().liquidCapitalReserves());
        assertEquals(10, end.businessLedger().serviceClearingCredits());
        assertEquals(70, end.businessLedger().unpaidExpenses().get(BusinessServices.facilityBill("producer-owner", "producer")));
        assertEquals(0, end.businessLedger().account("producer-owner").replacementReserveCredits());
        assertEquals(10, end.industryAccounts().getFirst().maintenanceCostsCredits());
    }
    @Test void sellingAnAssetDoesNotTransferItsFormerOwnersUnpaidBills() throws Exception {
        var poor = BusinessSettlement.closeDay(transaction(0).withIndustryAccounts(List.of(IndustryAccount.empty("producer")))).state();
        var old = poor.corporations().getFirst();
        var funded = new Corporation(old.id(), old.name(), old.empireId(), old.headquartersEntityId(), old.marketOrientation(), 200,
                List.of(), List.of(), List.of());
        var settled = BusinessSettlement.closeDay(poor.toBuilder().turn(1).industrialFacilities(List.of()).industryAccounts(List.of())
                .corporations(List.of(funded)).build()).state();
        assertEquals(120, settled.corporations().getFirst().liquidCapitalReserves());
        assertEquals(80, settled.businessLedger().serviceClearingCredits());
        assertTrue(settled.businessLedger().unpaidExpenses().isEmpty());
        assertEquals(80, settled.businessLedger().account(old.id()).externalServicesPaidToday());
    }
    @Test void aLaterSaleCanFundTheReplacementAllowanceAccruedDuringALossMakingPeriod() throws Exception {
        var state = transaction(10000);
        var growing = BusinessSettlement.closeDay(state.withIndustryAccounts(List.of(IndustryAccount.empty("producer")))).state();
        var first = growing.businessLedger().account("producer-owner");
        assertEquals(0, first.replacementReserveCredits()); assertTrue(first.replacementFundingTargetCredits() > 0);
        var next = BusinessSettlement.closeDay(growing.toBuilder().turn(1).industryAccounts(state.industryAccounts()).build()).state();
        var funded = next.businessLedger().account("producer-owner");
        assertEquals(first.replacementFundingTargetCredits() * 2, funded.replacementReserveCredits(), 1e-8);
        assertEquals(funded.replacementFundingTargetCredits(), funded.replacementReserveCredits(), 1e-8);
    }
}
