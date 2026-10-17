package com.spaceconquest.control;

import com.spaceconquest.control.ai.*;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.market.HubStockTarget;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AISupplierLifecycleTest {
    @TempDir Path directory;
    private GameState world(String application) throws Exception {
        var state = new AISupplyChainCampaignTest().world(true); var hub = state.commercialHubs().getFirst();
        var orders = new HashMap<>(hub.activeOrders()); orders.replaceAll((id, o) -> new MarketOrder(id, 1e9, 0, 1, 0));
        orders.put("purified_water", new MarketOrder("purified_water", 0, 10000, 10, 10));
        var equipment = ProductionWork.initialEquipment(application);
        var supplier = new IndustrialFacility("supplier", hub.entityId(), application, "owner", IndustrialFacility.PUBLIC_STATE,
                1, Math.toIntExact(equipment.referenceCrew()), equipment.operatorProfession(), false, 0);
        var plant = new IndustrialFacility("plant", hub.entityId(), "combustion_power", "owner", IndustrialFacility.PUBLIC_STATE, 1, 100, "technician", false, 0);
        return state.toBuilder().industrialFacilities(List.of(supplier, plant))
                .industryAccounts(List.of(IndustryAccount.empty(supplier.id()).withOperatingCash(50000).withPaidWorkers(supplier.allocatedWorkers()),
                        IndustryAccount.empty(plant.id()).withOperatingCash(1000000).withPaidWorkers(100)))
                .commercialHubs(List.of(new CommercialHub(hub.id(), hub.entityId(), 0, 1e14, 1e11, 10, orders)
                        .withStockTargets(Map.of("purified_water", new HubStockTarget(10000, 20000))))).build();
    }
    @Test void undersizedSupplierExpandsPaidEquipmentAndDeliversMoreAcrossReload() throws Exception {
        var state = world("surface_water_treatment"); var original = state.industrialFacilities().getFirst();
        double before = ProductionWork.quote(state, original, original.allocatedWorkers()).batchesPerDay();
        var command = assertInstanceOf(RequestAISupplyExpansionCommand.class, SupplyChainInvestmentPlanner.plan(state, state.empires().getFirst()).getFirst());
        assertTrue(command.validate(state)); var built = command.apply(state);
        assertEquals(2, built.industrialFacilities().size()); assertFalse(command.validate(built));
        assertEquals(ConstructionMaterialCatalog.facility(state, original, 1), built.expansionProjects().getFirst().requiredMaterialsKg());
        var saves = new SaveGameManager(directory);
        state = saves.load(saves.save("supplier", built, 1, GameClock.START_TIME.toString()).toFile()).toGameState(built.turn(), built.status());
        for (int day = 0; day < 12 && !state.expansionProjects().isEmpty(); day++) state = new IndustryProcessor().processIndustrialProduction(state);
        assertTrue(state.expansionProjects().isEmpty());
        var expanded = state.industrialFacilities().getFirst(); assertEquals(2, expanded.tier());
        assertEquals(original.productionEquipment(), expanded.productionEquipment());
        assertTrue(ProductionWork.quote(state, expanded, expanded.allocatedWorkers()).batchesPerDay() > before);
        var engine = new SpaceConquestEngine(42); engine.applyGameState(state); engine.stepTurn();
        var account = engine.getGameState().industryAccounts().stream().filter(a -> a.facilityId().equals(original.id())).findFirst().orElseThrow();
        assertTrue(account.producedKg().getOrDefault("purified_water", 0.0) > before * 1000);
        assertTrue(account.salesCredits() > 0); assertTrue(account.wageCostsCredits() > 0); assertTrue(account.powerCostsCredits() > 0);
    }
    @Test void changedDemandCashAndPowerRejectExpansionWithoutSpending() throws Exception {
        var state = world("surface_water_treatment"); var owner = state.empires().getFirst();
        var command = assertInstanceOf(RequestAISupplyExpansionCommand.class, SupplyChainInvestmentPlanner.plan(state, owner).getFirst());
        var unfunded = state.toBuilder().marketAccounts(List.of()).build(); assertFalse(command.validate(unfunded)); assertSame(unfunded, command.apply(unfunded));
        assertTrue(SupplyInvestmentStatus.inspect(unfunded, "owner").rows().stream().anyMatch(r -> r.reason().contains("No funded marketplace buyer")));
        assertTrue(SupplyInvestmentStatus.inspect(state, "foreign").rows().isEmpty());
        var dark = state.toBuilder().industryAccounts(state.industryAccounts().stream().map(a -> a.facilityId().equals("plant") ? a.withPaidWorkers(0) : a).toList()).build();
        assertFalse(command.validate(dark));
        var hub = state.commercialHubs().getFirst(); var orders = new HashMap<>(hub.activeOrders());
        orders.put("purified_water", new MarketOrder("purified_water", 10000, 10000, 10, 0));
        var supplied = state.withCommercialHubs(List.of(hub.withInventory(0, hub.currentStoredWeightKg() + 10000, orders)));
        assertFalse(command.validate(supplied)); assertSame(supplied, command.apply(supplied));
        var broke = state.withEmpires(List.of(new Empire(owner.id(), owner.name(), owner.raceId(), owner.societyStructure(), 0,
                owner.corporateTaxRate(), owner.controlledSystemIds(), owner.ministries(), owner.systemGovernorAssignments(), owner.unlockedTechIds(), owner.activeShipDesignIds())));
        assertFalse(command.validate(broke)); assertSame(broke, command.apply(broke));
    }
    @Test void exhaustedFeedstockSelectsAnotherResearchedProcessInsteadOfExpandingStrandedMachinery() throws Exception {
        var state = world("ice_water_treatment"); var hub = state.commercialHubs().getFirst(); var orders = new HashMap<>(hub.activeOrders());
        orders.put("water_ice", new MarketOrder("water_ice", 0, 0, 1, 0));
        state = state.toBuilder().commercialHubs(List.of(hub.withInventory(0, 1e11, orders)))
                .geologicalDeposits(List.of(new GeologicalDeposit("ice", hub.entityId(), "water_ice", 1000, 0, 1, true, "owner"))).build();
        var command = assertInstanceOf(RequestAISupplyInvestmentCommand.class, SupplyChainInvestmentPlanner.plan(state, state.empires().getFirst()).getFirst());
        assertEquals("surface_water_treatment", command.applicationId());
        var built = command.apply(state); assertEquals(3, built.industrialFacilities().size());
        assertTrue(built.expansionProjects().stream().noneMatch(p -> p.facilityId().equals("supplier")));
        assertEquals(0, built.geologicalDeposits().getFirst().remainingVolumeKg());
    }
    @Test void expansionTransfersRealWorkingCapitalAndDoesNotGrowAlreadyAdequateCapacity() throws Exception {
        var state = world("surface_water_treatment");
        state = state.toBuilder().industryAccounts(state.industryAccounts().stream().map(a -> a.facilityId().equals("supplier") ? a.withOperatingCash(0) : a).toList()).build();
        var command = assertInstanceOf(RequestAISupplyExpansionCommand.class, SupplyChainInvestmentPlanner.plan(state, state.empires().getFirst()).getFirst());
        var built = command.apply(state);
        double transfer = built.industryAccounts().stream().filter(a -> a.facilityId().equals("supplier")).findFirst().orElseThrow().operatingCashCredits();
        assertTrue(transfer > 0);
        assertEquals(state.empires().getFirst().treasuryCredits(), built.empires().getFirst().treasuryCredits() + transfer, 1e-6);
        var hub = state.commercialHubs().getFirst(); var orders = new HashMap<>(hub.activeOrders());
        orders.put("purified_water", new MarketOrder("purified_water", 0, 500, 10, 1));
        var adequate = state.withCommercialHubs(List.of(hub.withInventory(0, hub.currentStoredWeightKg(), orders)));
        assertFalse(command.validate(adequate));
        assertTrue(SupplyInvestmentStatus.inspect(adequate, "owner").rows().stream().anyMatch(r -> r.reason().contains("Installed output covers demand")));
    }
}
