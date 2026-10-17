package com.spaceconquest.control;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.*;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.logistics.*;
import com.spaceconquest.engine.market.SupplyPricingPolicy;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LaunchStaffingLifecycleTest {
    @TempDir Path directory;

    @Test void actualPayrollFundsFiniteLaunchWorkAndReloadDoesNotRestoreUsedCapacity() throws Exception {
        var opening = world(50);
        assertNull(LaunchService.choose(opening, "research-world", "owner", 1, false, false, 0));
        double money = opening.corporations().getFirst().liquidCapitalReserves();
        var state = payroll(opening);
        var account = state.industryAccounts().getFirst();
        assertEquals(50, account.paidWorkers());
        assertTrue(account.wageCostsCredits() > 0);
        assertEquals(money - account.wageCostsCredits(), state.corporations().getFirst().liquidCapitalReserves(), 1e-6);
        var quote = LaunchServiceWork.quote(state, state.industrialFacilities().getFirst());
        assertEquals(1000, quote.capacityKg(), 1e-6);
        var plan = LaunchService.choose(state, "research-world", "owner", quote.capacityKg(), false, false, 0);
        assertNotNull(plan);
        assertTrue(plan.serviceFeeCredits() >= SupplyPricingPolicy.current().requiredReceipts(
                quote.payrollCredits() + quote.maintenanceCredits(), quote.capitalReserveCredits(), .2));
        double payerCash = state.empires().getFirst().treasuryCredits();
        state = LaunchService.settle(state, "owner", plan);
        assertEquals(payerCash - plan.serviceFeeCredits(), state.empires().getFirst().treasuryCredits(), 1e-6);
        var saves = new SaveGameManager(directory);
        state = saves.load(saves.save("launch-work", state, 1, GameClock.START_TIME.toString()).toFile()).toGameState(state.turn(), state.status());
        assertNull(LaunchService.choose(state, "research-world", "owner", 1, false, false, 0));
        assertSame(state, LaunchService.settle(state, "owner", plan));
    }

    @Test void partialPaidCrewLimitsWorkAndUnfundedPayrollCannotReuseAnEarlierQuote() throws Exception {
        var state = payroll(world(25));
        var quote = LaunchServiceWork.quote(state, state.industrialFacilities().getFirst());
        assertEquals(500, quote.capacityKg(), 1e-6);
        assertNull(LaunchService.choose(state, "research-world", "owner", 501, false, false, 0));
        var plan = LaunchService.choose(state, "research-world", "owner", 500, false, false, 0);
        assertNotNull(plan);
        var company = state.corporations().getFirst();
        state = payroll(state.withCorporations(List.of(new Corporation(company.id(), company.name(), company.empireId(),
                company.headquartersEntityId(), company.marketOrientation(), 0, company.ownedFacilityIds(), company.ownedShipIds(), company.claimedVeinIds()))));
        assertEquals(0, state.industryAccounts().getFirst().paidWorkers());
        assertSame(state, LaunchService.settle(state, "owner", plan));
    }

    @Test void skillAndAutomationUseCapturedMachineryAndDoNotInventAdditionalPhysicalCapacity() throws Exception {
        var state = payroll(world(25));
        var facility = state.industrialFacilities().getFirst();
        var old = facility.productionEquipment();
        var skilled = new ProductionEquipment(old.id(), old.processType(), old.workUnitsPerDay(), old.referenceOperators(),
                old.operatorProfession(), 0, 2, old.parallelLines());
        state = state.withIndustrialFacilities(List.of(copy(facility, 25, skilled)));
        assertEquals(1000, LaunchServiceWork.capacity(state, facility.id()), 1e-6);
        var automatic = new ProductionEquipment(old.id(), old.processType(), old.workUnitsPerDay(), old.referenceOperators(),
                old.operatorProfession(), 1, 1, old.parallelLines());
        state = state.withIndustrialFacilities(List.of(copy(facility, 0, automatic))).toBuilder()
                .industryAccounts(List.of(IndustryAccount.empty(facility.id()))).build();
        assertEquals(1000, LaunchServiceWork.capacity(state, facility.id()), 1e-6);
        assertNotNull(LaunchService.choose(state, "research-world", "owner", 1000, false, false, 0));
        assertNull(LaunchService.choose(state, "research-world", "owner", 1001, false, false, 0));
    }

    @Test void fullServiceReceiptsFundActualMaintenanceTaxAndReplacementReserveAfterPayroll() throws Exception {
        var state = payroll(world(50));
        var account = state.industryAccounts().getFirst();
        var plan = LaunchService.choose(state, "research-world", "owner", 1000, false, false, 0);
        assertNotNull(plan);
        state = LaunchService.settle(state, "owner", plan);
        var day = new IndustryMarketProcessor().process(state, Map.of("driver", account.paidWorkers()), Map.of("driver", account.wageCostsCredits()));
        state = state.toBuilder().industryAccounts(day.industryAccounts()).corporations(day.corporations()).empires(day.empires()).build();
        double beforeClose = state.corporations().getFirst().liquidCapitalReserves();
        var closed = BusinessSettlement.closeDay(state).state();
        double maintenance = closed.industryAccounts().getFirst().maintenanceCostsCredits();
        double tax = closed.corporateTaxAccounts().getFirst().paidTaxCredits();
        double reserve = closed.businessLedger().account("provider").replacementReserveCredits();
        assertTrue(maintenance > 0); assertTrue(tax > 0); assertTrue(reserve > 0);
        assertEquals(beforeClose - maintenance - tax - reserve, closed.corporations().getFirst().liquidCapitalReserves(), 1e-6);
    }

    private IndustrialFacility copy(IndustrialFacility f, int workers, ProductionEquipment equipment) {
        return new IndustrialFacility(f.id(), f.planetId(), f.applicationId(), f.ownerEntityId(), f.ownershipType(), f.tier(),
                workers, f.workerProfessionId(), false, 0, equipment);
    }
    private GameState world(int workers) throws Exception {
        var known = new ArrayList<String>();
        DataModelLoader.loadTechnologies().forEach(t -> { known.add(t.id()); t.applications().forEach(a -> known.add(a.id())); });
        var owner = new Empire("owner", "Owner", "human", "Individualist", 1e9, .2, List.of("sol"), List.of(), Map.of(), known, List.of());
        var state = ResearchTestWorld.staffed(GameState.builder().empires(List.of(owner)).build());
        return state.toBuilder().corporations(List.of(new Corporation("provider", "Launch company", "owner", "research-world", "TRANSPORT", 1e8,
                        List.of("driver"), List.of(), List.of())))
                .industrialFacilities(List.of(new IndustrialFacility("driver", "research-world", "mass_driver", "provider",
                        IndustrialFacility.PRIVATE_CORPORATE, 1, workers, "technician", false, 0)))
                .commercialHubs(List.of(new CommercialHub("hub", "research-world", 0, 1e8, 0, 10, Map.of())))
                .powerGrids(List.of(new PowerGridState("research-world", 10000, 0, 10000, 0, 0, false))).build();
    }
    private GameState payroll(GameState state) throws Exception {
        var paid = new HouseholdEconomyProcessor().process(state, DataModelLoader.loadRaces());
        state = state.toBuilder().empires(paid.empires()).corporations(paid.corporations()).householdAccounts(paid.householdAccounts())
                .commercialHubs(paid.commercialHubs()).marketAccounts(paid.marketAccounts()).industryAccounts(paid.industryAccounts()).build();
        var day = new IndustryMarketProcessor().process(state, paid.paidWorkersByFacility(), paid.wagesByFacility());
        return state.toBuilder().industryAccounts(day.industryAccounts()).corporations(day.corporations()).empires(day.empires()).build();
    }
}
