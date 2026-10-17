package com.spaceconquest.control.ai;

import com.spaceconquest.control.command.ExpandFacilityCommand;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.EconomicOwnership;
import com.spaceconquest.engine.industry.*;
import java.util.*;

/** Conditional public supplier expansion; the existing machine pool and paid crew are preserved. */
public record SupplyExpansionQuote(ExpandFacilityCommand command, String bodyId, String applicationId,
                                   double operatingCapitalCredits) {
    public record Assessment(SupplyExpansionQuote quote, String reason, double additionalPowerKw) {}
    private static Assessment blocked(String reason) { return new Assessment(null, reason, 0); }
    public static Assessment assess(GameState state, Empire empire, SupplyChainNeeds.Goal goal) {
        var candidates = state.industrialFacilities().stream().filter(f -> goal.bodyId().equals(f.planetId())
                && goal.applicationId().equals(f.applicationId()) && empire.id().equals(f.ownerEntityId())
                && IndustrialFacility.PUBLIC_STATE.equals(f.ownershipType())).sorted(Comparator.comparing(IndustrialFacility::id)).toList();
        if (candidates.isEmpty()) return blocked("Existing supplier is privately operated");
        if (state.industrialFacilities().stream().anyMatch(f -> goal.bodyId().equals(f.planetId()) && goal.applicationId().equals(f.applicationId())
                && (f.tier() <= 0 || f.isUndergoingExpansion() || state.expansionProjects().stream().anyMatch(p -> p.facilityId().equals(f.id())))))
            return blocked("Supplier construction or expansion is pending");
        var hub = state.commercialHubs().stream().filter(h -> h.entityId().equals(goal.bodyId())).findFirst().orElse(null);
        var order = hub == null ? null : hub.activeOrders().get(goal.materialId());
        if (order == null || order.demandKg() <= order.supplyKg()) return blocked("No remaining material shortage");
        double capacity = state.industrialFacilities().stream().filter(f -> goal.bodyId().equals(f.planetId())
                && (empire.id().equals(f.ownerEntityId()) || state.corporations().stream().anyMatch(c -> c.id().equals(f.ownerEntityId()) && empire.id().equals(c.empireId()))))
                .mapToDouble(f -> output(state, f, goal.materialId())).sum();
        if (capacity + 1e-6 >= order.demandKg() - order.supplyKg()) return blocked("Installed output covers demand; restore operation or deliver stock");
        Assessment result = blocked("No expandable supplier");
        for (var facility : candidates) {
            result = assessFacility(state, empire, goal, hub, facility);
            if (result.quote() != null) return result;
        }
        return result;
    }
    private static Assessment assessFacility(GameState state, Empire empire, SupplyChainNeeds.Goal goal, CommercialHub hub, IndustrialFacility facility) {
        var recipe = IndustryRecipeCatalog.forFacility(state, facility);
        var policy = OperatingReservePolicy.forApplication(facility.applicationId());
        if (!PowerInvestmentQuote.controls(state, empire, facility.planetId()) || recipe == null || policy == null
                || !IndustryRecipeCatalog.isUnlocked(recipe, empire)) return blocked("Research or site requirements are not met");
        var account = state.industryAccounts().stream().filter(a -> a.facilityId().equals(facility.id())).findFirst().orElse(IndustryAccount.empty(facility.id()));
        if (account.paidWorkers() < facility.allocatedWorkers()) return blocked("Restore paid supplier staffing before expansion");
        var preview = atTier(facility, Math.max(facility.tier() + 1, FacilityManufacturingCapacity.minimumTier(state, empire.id(), facility.applicationId())));
        var work = ProductionWork.quote(state, preview, preview.allocatedWorkers());
        if (work.batchesPerDay() <= 0 || output(state, preview, goal.materialId()) <= output(state, facility, goal.materialId()) + 1e-6)
            return blocked("Physical source or installed equipment cannot support more output");
        if (ProcessInput.maximumBatches(state, goal.bodyId(), recipe.inputSources(), state.geologicalDeposits()) + 1e-6 < work.batchesPerDay())
            return blocked("Remaining source cannot support an expanded operating period");
        if (recipe.inputsKg().entrySet().stream().anyMatch(e -> SupplyChainNeeds.stock(hub, e.getKey()) < e.getValue() * work.batchesPerDay()
                && !SupplyInvestmentQuote.fundedSupplier(state, empire, goal.bodyId(), e.getKey()))) return blocked("Restore feedstock before expanding the supplier");
        double extraLoad = Math.max(0, SupplyInvestmentQuote.load(state, preview) - SupplyInvestmentQuote.load(state, facility));
        if (extraLoad > SupplyInvestmentQuote.freePower(state, goal.bodyId()) + 1e-6)
            return new Assessment(null, "Insufficient uncommitted generation for expansion", extraLoad);
        double inputs = recipe.inputsKg().entrySet().stream().mapToDouble(e -> e.getValue() * work.batchesPerDay()
                * (hub.activeOrders().containsKey(e.getKey()) ? hub.activeOrders().get(e.getKey()).pricePerKg() : 0)).sum();
        double daily = inputs + preview.allocatedWorkers() * Profession.getBaseWageForProfession(preview.workerProfessionId())
                + work.energyKwh() * GridSupplyPricing.priceWithFacility(state, preview, policy.maximumCashCredits())
                + policy.maintenanceCreditsPerTier() * preview.tier();
        double reserve = Math.min(policy.maximumCashCredits(), daily * policy.targetDays());
        if (!Double.isFinite(daily) || reserve < daily) return blocked("Expanded operating period exceeds the reserve limit");
        var bill = ConstructionMaterialCatalog.facility(state, facility, preview.tier() - facility.tier());
        var purchase = ConstructionMaterials.buyUpTo(state, goal.bodyId(), empire.id(), bill);
        if (!purchase.covers(bill) || EconomicOwnership.cash(purchase.state(), empire.id())
                < Math.max(0, reserve - account.operatingCashCredits()) + policy.treasuryReserveCredits())
            return blocked("Expansion materials or working capital are unavailable");
        var command = new ExpandFacilityCommand(facility.id(), preview.tier(), 0);
        return command.validate(state) ? new Assessment(new SupplyExpansionQuote(command, goal.bodyId(), goal.applicationId(), reserve), "Ready to expand", 0)
                : blocked("An active process or site requirement blocks expansion");
    }
    public static IndustrialFacility atTier(IndustrialFacility f, int tier) {
        return new IndustrialFacility(f.id(), f.planetId(), f.applicationId(), f.ownerEntityId(), f.ownershipType(), tier,
                f.allocatedWorkers(), f.workerProfessionId(), false, 0, f.productionEquipment());
    }
    private static double output(GameState state, IndustrialFacility f, String material) {
        var recipe = IndustryRecipeCatalog.forFacility(state, f);
        if (recipe == null || f.tier() <= 0 || !recipe.outputsKg().containsKey(material)) return 0;
        return recipe.outputsKg().get(material) * Math.min(ProductionWork.quote(state, f, f.allocatedWorkers()).batchesPerDay(),
                ProcessInput.maximumBatches(state, f.planetId(), recipe.inputSources(), state.geologicalDeposits()));
    }
}
