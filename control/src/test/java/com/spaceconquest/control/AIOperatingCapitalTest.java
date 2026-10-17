package com.spaceconquest.control;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spaceconquest.control.ai.*;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.*;
import com.spaceconquest.engine.industry.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AIOperatingCapitalTest {
    @TempDir Path directory;
    private GameState staffedSnapshot(GameState state) throws Exception {
        var payroll = new HouseholdEconomyProcessor().process(state, DataModelLoader.loadRaces());
        return state.toBuilder().empires(payroll.empires()).corporations(payroll.corporations()).householdAccounts(payroll.householdAccounts())
                .commercialHubs(payroll.commercialHubs()).marketAccounts(payroll.marketAccounts()).industryAccounts(payroll.industryAccounts()).build();
    }
    private GameState plant() throws Exception {
        var state = new AIPowerInvestmentTest().world("combustion_power", "none", false);
        return staffedSnapshot(state.toBuilder().industrialFacilities(List.of(new IndustrialFacility("plant", "world", "combustion_power", "owner",
                IndustrialFacility.PUBLIC_STATE, 1, 100, "technician", false, 0)))
                .industryAccounts(List.of(IndustryAccount.empty("plant"))).build());
    }
    private GameState factory() throws Exception {
        var state = new AIPowerInvestmentTest().world("cannon_ammunition_mfg", "breathable", true); var owner = state.empires().getFirst();
        var knowledge = new ArrayList<>(owner.unlockedTechIds()); knowledge.addAll(List.of("ship_cannons", "solar_power"));
        var stock = new HashMap<>(state.commercialHubs().getFirst().activeOrders());
        stock.put("steel", new MarketOrder("steel", 1000, 0, 2, 0)); stock.put("cannon_round", new MarketOrder("cannon_round", 0, 1000, 100, 1));
        var hub = state.commercialHubs().getFirst().withInventory(0, 51000, stock);
        state = state.toBuilder().empires(List.of(new Empire(owner.id(), owner.name(), owner.raceId(), owner.societyStructure(), owner.treasuryCredits(), 0,
                owner.controlledSystemIds(), owner.ministries(), owner.systemGovernorAssignments(), knowledge, owner.activeShipDesignIds())))
                .commercialHubs(List.of(hub)).marketAccounts(List.of(new MarketAccount(hub.id(), 100000)))
                .industrialFacilities(List.of(new IndustrialFacility("factory", "world", "cannon_ammunition_mfg", "owner", IndustrialFacility.PUBLIC_STATE, 1, 1, "industrial_worker", false, 0),
                        new IndustrialFacility("solar", "world", "solar_power", "owner", IndustrialFacility.PUBLIC_STATE, 1, 100, "technician", false, 0)))
                .industryAccounts(List.of(IndustryAccount.empty("factory"), IndustryAccount.empty("solar").withOperatingCash(10000))).build();
        var paid = new HouseholdEconomyProcessor().process(state, DataModelLoader.loadRaces()); state = staffedSnapshot(state);
        // Recovery needs available capacity, including output not yet dispatched to the cash-starved factory.
        var generation = new PowerGenerationProcessor().process(state, paid.paidWorkersByFacility(), paid.wagesByFacility(), false);
        var balance = new PowerProcessor().balanceDay(state, generation.generationKw(), paid.paidWorkersByFacility());
        return state.toBuilder().industryAccounts(generation.industryAccounts()).powerGrids(balance.grids()).build();
    }
    private FacilityOperatingReadiness quote(GameState state, String id) { return FacilityOperatingReadiness.inspect(state, "owner", id); }
    @Test void treasuryTransferRestartsPaidGenerationAndSurvivesReloadWithoutRepeating() throws Exception {
        var state = plant(); var readiness = quote(state, "plant"); assertEquals(FacilityOperatingReadiness.Status.CASH_LOW, readiness.status());
        assertTrue(readiness.potentialWorkers() > 0); assertEquals(0, new PowerGenerationProcessor().process(state, Map.of(), Map.of()).generationKw().get("world"));
        double treasury = state.empires().getFirst().treasuryCredits(); var stock = state.commercialHubs();
        var queue = new CommandQueue(); var command = new FundAIFacilityOperationsCommand("owner", "plant"); queue.submit(command); queue.submit(command);
        state = queue.drainAndExecute(state); var account = state.industryAccounts().getFirst();
        assertEquals(readiness.targetCashCredits(), account.operatingCashCredits(), .001);
        assertEquals(treasury, state.empires().getFirst().treasuryCredits() + account.operatingCashCredits(), .001);
        assertEquals(stock, state.commercialHubs()); assertEquals(readiness.transferCredits(), account.subsidyCredits()); assertFalse(account.publicSubsidyEnabled());
        var file = directory.resolve("operating.scsave").toFile(); var manager = new SaveGameManager(directory); manager.save(file, state, 1, GameClock.START_TIME.toString());
        state = manager.load(file).toGameState(state.turn(), state.status()); assertFalse(command.validate(state)); assertSame(state, command.apply(state));
        var paid = new HouseholdEconomyProcessor().process(state, DataModelLoader.loadRaces()); assertTrue(paid.paidWorkersByFacility().get("plant") > 0);
        state = staffedSnapshot(state); var generated = new PowerGenerationProcessor().process(state, paid.paidWorkersByFacility(), paid.wagesByFacility(), false);
        assertTrue(generated.generationKw().get("world") > 0); assertTrue(generated.accounts().getFirst().inputCostsCredits() > 0);
        assertTrue(generated.accounts().getFirst().wageCostsCredits() > 0);
        assertTrue(generated.hubs().getFirst().activeOrders().get("hydrocarbons").supplyKg() < stock.getFirst().activeOrders().get("hydrocarbons").supplyKg());
        assertFalse(new PowerProcessor().balanceDay(state, generated.generationKw(), paid.paidWorkersByFacility()).grids().getFirst().isDeficitBrownoutActive());
    }
    @Test void fundedFactoryPaysForWorkersInputsAndElectricityBeforeProducingAndSelling() throws Exception {
        var state = factory(); assertEquals(FacilityOperatingReadiness.Status.CASH_LOW, quote(state, "factory").status());
        var before = state.commercialHubs().getFirst().activeOrders().get("steel").supplyKg();
        var command = new FundAIFacilityOperationsCommand("owner", "factory"); state = command.apply(state);
        var paid = new HouseholdEconomyProcessor().process(state, DataModelLoader.loadRaces()); state = staffedSnapshot(state);
        assertEquals(1, paid.paidWorkersByFacility().get("factory"));
        var generated = new PowerGenerationProcessor().process(state, paid.paidWorkersByFacility(), paid.wagesByFacility());
        state = state.toBuilder().industryAccounts(generated.industryAccounts()).commercialHubs(generated.hubs()).build();
        var balance = new PowerProcessor().balanceDay(state, generated.generationKw(), paid.paidWorkersByFacility());
        var billed = new PowerBillingProcessor().process(state, balance, generated.accounts(), paid.paidWorkersByFacility());
        assertTrue(billed.productionEnergy().get("factory").pricedKwh() > 0);
        state = state.toBuilder().industryAccounts(billed.industryAccounts()).householdAccounts(billed.households()).build();
        var production = new IndustryMarketProcessor().process(state, billed.poweredWorkers(), paid.wagesByFacility(), paid.paidWorkersByFacility(), billed.productionEnergy());
        var result = production.industryAccounts().stream().filter(account -> account.facilityId().equals("factory")).findFirst().orElseThrow();
        assertTrue(result.producedKg().getOrDefault("cannon_round", 0.0) > 0); assertTrue(result.soldKg().getOrDefault("cannon_round", 0.0) > 0);
        assertTrue(result.inputCostsCredits() > 0); assertTrue(result.wageCostsCredits() > 0);
        assertTrue(result.powerCostsCredits() > 0);
        assertTrue(production.hubs().getFirst().activeOrders().get("steel").supplyKg() < before);
    }
    @Test void blockedOperationsReceiveNoCashAndStaleCommandsRevalidate() throws Exception {
        var state = plant(); var command = new FundAIFacilityOperationsCommand("owner", "plant");
        var facility = state.industrialFacilities().getFirst(); var owner = state.empires().getFirst();
        var variants = Map.of(
                FacilityOperatingReadiness.Status.NO_WORKERS, state.toBuilder().householdAccounts(List.of()).build(),
                FacilityOperatingReadiness.Status.NO_INPUTS, state.withCommercialHubs(List.of(state.commercialHubs().getFirst().withInventory(0, 0, Map.of()))),
                FacilityOperatingReadiness.Status.NO_DEMAND, state.withPowerGrids(List.of()),
                FacilityOperatingReadiness.Status.NOT_OPERATIONAL, state.withIndustrialFacilities(List.of(new IndustrialFacility(facility.id(), facility.planetId(), facility.applicationId(), facility.ownerEntityId(), facility.ownershipType(), 0, 100, "technician", true, 0))),
                FacilityOperatingReadiness.Status.NOT_PUBLIC, state.withIndustrialFacilities(List.of(new IndustrialFacility(facility.id(), facility.planetId(), facility.applicationId(), "other", IndustrialFacility.PRIVATE_CORPORATE, 1, 100, "technician", false, 0))),
                FacilityOperatingReadiness.Status.NO_CONTROL, state.withEmpires(List.of(new Empire(owner.id(), owner.name(), owner.raceId(), owner.societyStructure(), owner.treasuryCredits(), 0, List.of(), List.of(), Map.of(), owner.unlockedTechIds(), List.of()))),
                FacilityOperatingReadiness.Status.NO_RESEARCH, state.withEmpires(List.of(new Empire(owner.id(), owner.name(), owner.raceId(), owner.societyStructure(), owner.treasuryCredits(), 0, owner.controlledSystemIds(), List.of(), Map.of(), List.of("electricity"), List.of()))),
                FacilityOperatingReadiness.Status.TREASURY_LOW, state.withEmpires(List.of(new Empire(owner.id(), owner.name(), owner.raceId(), owner.societyStructure(), 0, 0, owner.controlledSystemIds(), List.of(), Map.of(), owner.unlockedTechIds(), List.of()))));
        for (var variant : variants.entrySet()) {
            assertEquals(variant.getKey(), quote(variant.getValue(), "plant").status()); assertFalse(command.validate(variant.getValue())); assertSame(variant.getValue(), command.apply(variant.getValue()));
        }
        var factory = factory(); var dark = factory.withPowerGrids(List.of()); assertEquals(FacilityOperatingReadiness.Status.NO_POWER, quote(dark, "factory").status());
        assertSame(dark, new FundAIFacilityOperationsCommand("owner", "factory").apply(dark));
    }
    @Test void applicationPolicyValuesChangeReserveQuotesAndTheTreasuryFloor() throws Exception {
        var state = plant(); var baseline = quote(state, "plant");
        var app = DataModelLoader.loadTechnologies().stream().flatMap(technology -> technology.applications().stream())
                .filter(value -> value.id().equals("combustion_power")).findFirst().orElseThrow();
        var mapper = new ObjectMapper(); ObjectNode data = mapper.valueToTree(app); var policyData = (ObjectNode) data.get("operatingReserve");
        policyData.put("targetDays", 5); var policy = mapper.treeToValue(data, TechnicalApplication.class).operatingReserve();
        var changed = FacilityOperatingReadiness.inspect(state, "owner", "plant", policy);
        assertEquals(baseline.dailyCostCredits() * 5, changed.targetCashCredits(), .001); assertTrue(changed.transferCredits() > baseline.transferCredits());
        policyData.put("treasuryReserveCredits", state.empires().getFirst().treasuryCredits());
        assertEquals(FacilityOperatingReadiness.Status.TREASURY_LOW, FacilityOperatingReadiness.inspect(state, "owner", "plant", mapper.treeToValue(data, TechnicalApplication.class).operatingReserve()).status());
    }
    @Test void recoveryPrecedesNewPowerConstructionAndPlannerDoesNotRepeatWithinTheTurn() throws Exception {
        var state = plant(); var planner = new EmpireOperatingCapitalPlanner(); var empire = state.empires().getFirst();
        assertEquals(1, planner.plan(state, empire).size()); assertTrue(planner.plan(state, empire).isEmpty());
        var queue = new CommandQueue(); var controller = new EmpireAIController("owner", queue);
        controller.onGameStateUpdate(state); controller.onGameStateUpdate(state.withTurn(1));
        var funded = queue.drainAndExecute(state.withTurn(1)); assertEquals(1, funded.industrialFacilities().size()); assertTrue(funded.expansionProjects().isEmpty());
        assertEquals(FacilityOperatingReadiness.Status.FUNDED, quote(funded, "plant").status());
        controller.onGameStateUpdate(funded); funded = queue.drainAndExecute(funded); assertTrue(funded.expansionProjects().isEmpty());
    }
    @Test void competingTransfersRespectTheTreasuryFloorAndNegativeFacilityCashIsRepaid() throws Exception {
        var state = plant(); var first = state.industrialFacilities().getFirst();
        var second = new IndustrialFacility("second", first.planetId(), first.applicationId(), first.ownerEntityId(), first.ownershipType(), 1, 100, first.workerProfessionId(), false, 0);
        state = state.toBuilder().industrialFacilities(List.of(first, second)).industryAccounts(List.of(IndustryAccount.empty("plant").withOperatingCash(-80), IndustryAccount.empty("second"))).build();
        double requested = quote(state, "plant").transferCredits(); var policy = OperatingReservePolicy.forApplication(first.applicationId()); var owner = state.empires().getFirst();
        state = state.withEmpires(List.of(new Empire(owner.id(), owner.name(), owner.raceId(), owner.societyStructure(), requested + policy.treasuryReserveCredits(), 0,
                owner.controlledSystemIds(), owner.ministries(), owner.systemGovernorAssignments(), owner.unlockedTechIds(), owner.activeShipDesignIds())));
        var queue = new CommandQueue(); queue.submit(new FundAIFacilityOperationsCommand("owner", "plant")); queue.submit(new FundAIFacilityOperationsCommand("owner", "second"));
        var funded = queue.drainAndExecute(state);
        assertEquals(policy.treasuryReserveCredits(), funded.empires().getFirst().treasuryCredits(), .001);
        assertEquals(0, funded.industryAccounts().stream().filter(account -> account.facilityId().equals("second")).findFirst().orElseThrow().operatingCashCredits());
        double before = state.empires().getFirst().treasuryCredits() + state.industryAccounts().stream().mapToDouble(IndustryAccount::operatingCashCredits).sum();
        assertEquals(before, funded.empires().getFirst().treasuryCredits() + funded.industryAccounts().stream().mapToDouble(IndustryAccount::operatingCashCredits).sum(), .001);
    }
    @Test void aLoadShedBrownoutRetainsUsefulDemandAndUnpaidWorkersDoNotReservePower() throws Exception {
        var state = plant().withPowerGrids(List.of(new PowerGridState("world", 0, 0, 0, 0, 0, true)));
        assertEquals(FacilityOperatingReadiness.Status.CASH_LOW, quote(state, "plant").status());
        var factory = factory();
        factory = factory.toBuilder().powerGrids(List.of(new PowerGridState("world", 100, 75, 25, 0, 0, false)))
                .industryAccounts(factory.industryAccounts().stream().map(account -> account.facilityId().equals("factory") ? account.withPaidWorkers(1) : account).toList()).build();
        assertEquals(FacilityOperatingReadiness.Status.NO_POWER, quote(factory, "factory").status());
        assertEquals(FacilityOperatingReadiness.Status.RESERVE_TOO_SMALL,
                FacilityOperatingReadiness.inspect(state, "owner", "plant", new OperatingReservePolicy(3, 1, 1, 10000, 80)).status());
    }
}
