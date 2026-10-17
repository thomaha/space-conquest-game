package com.spaceconquest.engine.economy;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.market.*;
import java.util.*;

/** Bounded commercial staffing forecasts. Allocated equipment and worker settings remain unchanged. */
public final class BusinessOperatingPlan {
    public record Decision(int requestedWorkers, boolean operate, String reason, double expectedReceipts, double requiredReceipts,
                           List<ProductionConstraints.Constraint> constraints) {
        public Decision(int requestedWorkers, boolean operate, String reason, double expectedReceipts, double requiredReceipts) {
            this(requestedWorkers, operate, reason, expectedReceipts, requiredReceipts, List.of());
        }
        public Decision {
            constraints = List.copyOf(constraints);
            if (requestedWorkers < 0 || !operate && requestedWorkers != 0 || reason == null
                    || !Double.isFinite(expectedReceipts) || expectedReceipts < 0 || !Double.isFinite(requiredReceipts) || requiredReceipts < 0)
                throw new IllegalArgumentException("Invalid business operating decision");
        }
    }
    private BusinessOperatingPlan() {}
    public static GameState prepare(GameState state) {
        var forecast = GridSupplyPricing.forecast(state); var decisions = new HashMap<String, Decision>();
        for (var facility : state.industrialFacilities()) {
            if (!IndustrialFacility.PRIVATE_CORPORATE.equals(facility.ownershipType())) continue;
            if (PowerPlantCatalog.find(facility.applicationId()) != null) {
                boolean active = forecast.tariffs().containsKey(facility.planetId()) && PowerGenerationProcessor.environmentFactor(state.solarSystems(), facility) > 0;
                decisions.put(facility.id(), new Decision(active ? facility.allocatedWorkers() : 0, active,
                        active ? "Local electricity service" : "No available local electricity sales", 0, 0,
                        ProductionConstraints.inspect(state, facility, forecast)));
                continue;
            }
            var hub = state.commercialHubs().stream().filter(h -> h.entityId().equals(facility.planetId())).findFirst().orElse(null);
            if (hub == null || IndustryRecipeCatalog.find(facility.applicationId()) == null) continue;
            Decision decision = new Decision(0, false, "No funded profitable demand or available inputs", 0, 0);
            Decision committedWork = null;
            for (int divisor : new int[]{1, 2, 4}) {
                int workers = facility.allocatedWorkers() / divisor;
                var preview = new IndustrialFacility(facility.id(), facility.planetId(), facility.applicationId(), facility.ownerEntityId(),
                        facility.ownershipType(), facility.tier(), workers, facility.workerProfessionId(), facility.isUndergoingExpansion(),
                        facility.expansionProgress(), facility.productionEquipment());
                var quoteState = state.toBuilder().industrialFacilities(state.industrialFacilities().stream()
                        .map(f -> f.id().equals(facility.id()) ? preview : f).toList()).build();
                var offers = FacilitySupplyPricing.offers(quoteState, hub, forecast.tariffs().getOrDefault(hub.entityId(), .02), forecast.productionEnergyKwh(), facility.id());
                double expected = 0, required = 0, spot = 0;
                for (var offer : offers) {
                    required += offer.quantityKg() * offer.retailPricePerKg() * IndustryMarketProcessor.WHOLESALE_SHARE;
                    double remaining = offer.quantityKg();
                    for (var contract : state.businessLedger().contracts()) {
                        if (!contract.openAt(state.turn()) || !contract.facilityId().equals(facility.id())
                                || !contract.supplierOwnerId().equals(facility.ownerEntityId()) || !contract.materialId().equals(offer.materialId())) continue;
                        double committed = Math.min(remaining, contract.remainingKg() / Math.max(1, offer.deliveryDays()));
                        expected += committed * contract.pricePerKg(); remaining -= committed;
                    }
                    var order = hub.activeOrders().get(offer.materialId());
                    double wanted = order == null ? 0 : Math.max(0, order.demandKg() * 30 - order.supplyKg());
                    if (order != null) spot += Math.min(remaining, wanted) * order.pricePerKg() * IndustryMarketProcessor.WHOLESALE_SHARE;
                }
                double buyerCash = state.marketAccounts().stream().filter(a -> a.hubId().equals(hub.id()))
                        .mapToDouble(MarketAccount::unsettledSalesCredits).findFirst().orElse(0);
                expected += Math.min(spot, buyerCash);
                if (divisor == 1 && required > 0 && state.businessLedger().contracts().stream().anyMatch(c -> c.openAt(state.turn())
                        && c.facilityId().equals(facility.id()) && c.supplierOwnerId().equals(facility.ownerEntityId())))
                    committedWork = new Decision(workers, true, "Fulfilling funded contract; current margin below target", expected, required);
                if (required > 0 && expected + Math.max(1e-7, required * 1e-9) >= required) {
                    decision = new Decision(workers, true, divisor == 1 ? "Funded target margin" : "Reduced to funded profitable capacity", expected, required);
                    break;
                }
                if (required > 0) decision = new Decision(0, false, "Expected receipts do not cover costs, tax and replacement", expected, required);
            }
            if (!decision.operate() && committedWork != null) decision = committedWork;
            var constraints = new ArrayList<>(ProductionConstraints.inspect(state, facility, forecast));
            if (!decision.operate() && decision.requiredReceipts() > decision.expectedReceipts())
                constraints.add(new ProductionConstraints.Constraint("Funded demand", "credits/day", decision.requiredReceipts(), decision.expectedReceipts()));
            decisions.put(facility.id(), new Decision(decision.requestedWorkers(), decision.operate(), decision.reason(),
                    decision.expectedReceipts(), decision.requiredReceipts(), constraints));
        }
        return state.toBuilder().businessLedger(state.businessLedger().withDecisions(decisions)).build();
    }
    public static boolean allowed(GameState state, IndustrialFacility facility) {
        var decision = state.businessLedger().operatingDecisions().get(facility.id());
        return decision == null || decision.operate();
    }
}
