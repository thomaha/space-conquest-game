package com.spaceconquest.control.command;

import com.spaceconquest.control.ai.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.EconomicOwnership;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.ship.*;
import java.util.*;

/** Joint discarded upgrade and ship forecast. The completed yard exists only inside the quote. */
record ShipyardCapabilityQuote(GameCommand upgrade, ShipDesign design, ShipConstructionOrder newOrder) {
    static ShipyardCapabilityQuote evaluate(GameState state, String provider, String designId, String system, String yard) {
        if (state == null || provider == null || designId == null || system == null || yard == null) return null;
        var source = state.shipDesigns().stream().filter(item -> designId.equals(item.id())).findFirst().orElse(null);
        var home = EconomicOwnership.empireFor(state, provider);
        if (source == null || home == null || !ShipyardAccess.permitted(state, source.ownerEntityId(), provider, system)) return null;
        boolean publicYard = provider.equals(home.id());
        if (publicYard && !provider.equals(source.ownerEntityId())) return null;
        var existing = state.shipConstructionOrders().stream().filter(order -> designId.equals(order.designId())
                && source.ownerEntityId().equals(order.ownerEntityId()) && system.equals(order.systemId())
                && yard.equals(order.yardBodyId()) && provider.equals(ShipyardAccess.provider(order))).findFirst().orElse(null);
        int required = ShipManufacturingCapacity.requiredComplexity(source);
        if (existing == null && ShipApplicationProduction.developmentChanged(state, source))
            required = source.equippedModuleIds().stream().map(ShipComponentCatalog::module).filter(Objects::nonNull)
                    .map(module -> ShipApplicationProduction.optimize(state, source.ownerEntityId(), module)).mapToInt(ShipModule::complexityLevel).max().orElse(required);
        var upgrade = ShipyardCapabilityUpgrade.plan(state, provider, system, yard, required);
        if (upgrade == null || !upgrade.command().validate(state)) return null;
        var design = existing == null ? AIShipResearchRevision.current(upgrade.completed(), source) : source;
        if (ShipManufacturingCapacity.requiredComplexity(design) <= ShipManufacturingCapacity.forYard(state, provider, system, yard)
                || !CombatModuleCatalog.researched(design.equippedModuleIds(), home.unlockedTechIds())
                || !ShipRepairModuleCatalog.researched(design.equippedModuleIds(), home.unlockedTechIds())) return null;
        var registered = AIShipResearchRevision.register(upgrade.completed(), design);
        var build = new QueueShipBuildCommand(design.ownerEntityId(), design.id(), system);
        if (!build.validate(registered)) return null;
        if (existing == null && (state.shipConstructionOrders().stream().anyMatch(order -> design.ownerEntityId().equals(order.ownerEntityId()))
                || alternativeYard(state, design, home) || !yard.equals(build.resolveYardEntity(registered)))) return null;
        var policy = ShipyardInvestmentPolicy.current();
        double mass = upgrade.materials().values().stream().mapToDouble(Double::doubleValue).sum();
        var purchase = upgrade.orbital() ? ConstructionMaterials.buyOrbitalUpTo(registered, system, yard, provider, upgrade.materials(), mass)
                : ConstructionMaterials.buyUpTo(registered, yard, provider, upgrade.materials(), mass);
        if (!purchase.covers(upgrade.materials())) return null;
        double cost = EconomicOwnership.cash(state, provider) - EconomicOwnership.cash(purchase.state(), provider);
        double floor = publicYard ? policy.empireReserveCredits() : policy.corporateReserveCredits();
        if (cost > policy.maximumInvestmentCredits() || EconomicOwnership.cash(purchase.state(), provider)
                < floor + upgrade.dailyWages() * policy.payrollReserveDays()) return null;
        var funded = purchase.state();
        if (existing == null && !needed(funded, state, design, home, system, yard)) return null;
        var proposed = existing == null ? build.apply(funded).shipConstructionOrders().getLast() : existing;
        double work = Math.max(0, proposed.requiredWorkHours() - proposed.accumulatedWorkHours());
        var feesHeld = ShipyardPayments.pay(funded, proposed.ownerEntityId(), proposed.yardContract(), work);
        if (feesHeld == null) return null;
        var bill = new HashMap<String, Double>();
        proposed.requiredMaterialsKg().forEach((material, kg) -> { double missing = kg - proposed.consumedMaterialsKg().getOrDefault(material, 0.0); if (missing > 0) bill.put(material, missing); });
        var shipPurchase = upgrade.orbital() ? ConstructionMaterials.buyOrbitalUpTo(feesHeld, system, yard, proposed.ownerEntityId(), bill, Double.MAX_VALUE)
                : ConstructionMaterials.buyUpTo(feesHeld, yard, proposed.ownerEntityId(), bill);
        if (!shipPurchase.covers(bill) || EconomicOwnership.cash(shipPurchase.state(), provider) < floor + upgrade.dailyWages() * policy.payrollReserveDays()) return null;
        double capacity = ShipyardWorkCapacity.forYard(registered, provider, system, yard).workPerDay();
        double queuedWork = state.shipConstructionOrders().stream().filter(order -> system.equals(order.systemId()) && yard.equals(order.yardBodyId()))
                .mapToDouble(order -> Math.max(0, order.requiredWorkHours() - order.accumulatedWorkHours())).sum() + (existing == null ? work : 0);
        double days = upgrade.constructionDays() + queuedWork / capacity;
        double fees = proposed.yardContract() == null ? 0 : work * proposed.yardContract().feeCreditsPerWorkUnit();
        double profit = fees * (1 - Math.clamp(home.corporateTaxRate(), 0, 1)) - cost - upgrade.dailyWages() * days;
        if (days > policy.maximumForecastDays() || !publicYard && profit < policy.minimumProfitCredits()) return null;
        return new ShipyardCapabilityQuote(upgrade.command(), design, existing == null ? proposed : null);
    }
    private static boolean alternativeYard(GameState state, ShipDesign design, Empire home) {
        var registered = AIShipResearchRevision.register(state, design);
        return home.controlledSystemIds().stream().anyMatch(system -> {
            var command = new QueueShipBuildCommand(design.ownerEntityId(), design.id(), system);
            if (!command.validate(registered)) return false;
            var offer = command.resolveYard(registered);
            return ShipyardWorkCapacity.forYard(registered, offer.providerOwnerId(), system, offer.yardId()).workPerDay() > 0;
        });
    }
    private static boolean needed(GameState funded, GameState original, ShipDesign design, Empire home, String system, String yard) {
        if (home.id().equals(design.ownerEntityId()) && EmpireFleetPlanner.needsShip(original, home, design)) return true;
        int previews = 0;
        for (var need : FacilitySupplyNeeds.shortages(original, home, LogisticsPolicy.current())) {
            if (previews++ >= ShipyardInvestmentPolicy.current().maximumYardPreviews()) break;
            var quote = FacilityTransportBuildQuote.quote(funded, design.ownerEntityId(), design.id(), system, need.hubId(), need.materialId());
            if (quote != null && yard.equals(quote.build().resolveYardEntity(quote.registered()))) return true;
        }
        return false;
    }
}
