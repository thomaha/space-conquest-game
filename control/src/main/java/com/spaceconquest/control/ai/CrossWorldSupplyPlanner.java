package com.spaceconquest.control.ai;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.logistics.*;
import com.spaceconquest.engine.ship.*;
import java.util.*;

/** Bounded source development from actual funded demand, not speculative factory output. */
public final class CrossWorldSupplyPlanner {
    public record Candidate(String sourceHubId, String destinationHubId, String materialId, double quantityKg, double buyerFundingCredits) {}
    private CrossWorldSupplyPlanner() {}
    public static List<com.spaceconquest.control.command.GameCommand> commands(GameState state, Empire empire) {
        for (var plan : state.supplyDevelopmentPlans()) if (plan.empireId().equals(empire.id()) && !plan.closed()) {
            var command = new com.spaceconquest.control.command.AdvanceSupplyDevelopmentCommand(empire.id(), plan.id());
            if (command.validate(state)) return List.of(command);
        }
        var next = next(state, empire);
        return next == null ? List.of() : List.of(new com.spaceconquest.control.command.StartSupplyDevelopmentCommand(empire.id(), next.sourceHubId(), next.destinationHubId(), next.materialId()));
    }
    public static Candidate next(GameState state, Empire empire) {
        if (empire.societyStructure() != null && empire.societyStructure().toLowerCase(Locale.ROOT).contains("hive")) return null;
        var hubs = state.commercialHubs().stream().filter(h -> PowerInvestmentQuote.controls(state, empire, h.entityId()))
                .sorted(Comparator.comparing(CommercialHub::id)).limit(8).toList();
        if (state.supplyDevelopmentPlans().stream().filter(p -> p.empireId().equals(empire.id()) && !p.closed()).count() >= 4) return null;
        for (var destination : hubs) for (var order : destination.activeOrders().values().stream()
                .filter(o -> (destination.stockTargets().containsKey(o.resourceId())
                        ? HubReplenishment.requestedKg(state, destination, o.resourceId(), null) > 1e-6 : o.demandKg() > o.supplyKg())
                        && o.pricePerKg() > 0 && Double.isFinite(o.pricePerKg()))
                .sorted(Comparator.comparing(MarketOrder::resourceId)).limit(12).toList()) {
            if (SupplyChainNeeds.recipe(state, empire, destination, order.resourceId()) != null
                    || HubReplenishment.inboundKg(state, destination, order.resourceId(), null) > 0
                    || state.supplyDevelopmentPlans().stream().anyMatch(p -> !p.closed() && p.empireId().equals(empire.id())
                    && p.destinationHubId().equals(destination.id()) && p.materialId().equals(order.resourceId()))) continue;
            double buyer = state.marketAccounts().stream().filter(a -> a.hubId().equals(destination.id())).mapToDouble(a -> a.unsettledSalesCredits()).findFirst().orElse(0);
            double required = destination.stockTargets().containsKey(order.resourceId()) ? HubReplenishment.requestedKg(state, destination, order.resourceId(), null)
                    : order.demandKg() - order.supplyKg();
            double quantity = Math.min(required, buyer / (order.pricePerKg() * IndustryMarketProcessor.WHOLESALE_SHARE));
            if (!Double.isFinite(quantity) || quantity <= 1e-6) continue;
            for (var source : hubs) {
                if (source.entityId().equals(destination.entityId())) continue;
                var recipe = SupplyChainNeeds.recipe(state, empire, source, order.resourceId());
                if (recipe == null) continue;
                var policy = OperatingReservePolicy.forApplication(recipe.applicationId()); if (policy == null) continue;
                double price = source.activeOrders().containsKey(order.resourceId()) ? source.activeOrders().get(order.resourceId()).pricePerKg()
                        : com.spaceconquest.engine.market.MarketProcessor.basePricePerKg(order.resourceId());
                double funding = Math.max(policy.maximumCashCredits(), quantity * Math.max(0, price));
                double construction = ConstructionMaterialCatalog.facility(state, empire.id(), recipe.applicationId(),
                        FacilityManufacturingCapacity.minimumTier(state, empire.id(), recipe.applicationId())).entrySet().stream()
                        .mapToDouble(e -> e.getValue() * (source.activeOrders().containsKey(e.getKey()) ? source.activeOrders().get(e.getKey()).pricePerKg()
                                : com.spaceconquest.engine.market.MarketProcessor.basePricePerKg(e.getKey()))).sum();
                if (Double.isFinite(funding) && empire.treasuryCredits() >= funding + construction + policy.maximumCashCredits() + policy.treasuryReserveCredits())
                    return new Candidate(source.id(), destination.id(), order.resourceId(), quantity, funding);
            }
        }
        return null;
    }
    public static Set<String> reservedFleets(GameState state, Empire empire) {
        var result = new HashSet<String>();
        state.supplyDevelopmentPlans().stream().filter(p -> p.empireId().equals(empire.id()) && !p.closed() && !p.fleetId().isBlank()).forEach(p -> result.add(p.fleetId()));
        if (state.supplyDevelopmentPlans().stream().anyMatch(p -> p.empireId().equals(empire.id()) && !p.closed() && p.fleetId().isBlank()) || next(state, empire) != null)
            state.fleets().stream().filter(f -> empire.id().equals(f.ownerEntityId()) && FacilitySupplyShipment.available(state, empire, f))
                    .sorted(Comparator.comparing(Fleet::id)).findFirst().ifPresent(f -> result.add(f.id()));
        return Set.copyOf(result);
    }
}
