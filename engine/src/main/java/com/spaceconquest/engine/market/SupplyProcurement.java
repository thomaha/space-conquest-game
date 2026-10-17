package com.spaceconquest.engine.market;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.MarketAccount;
import com.spaceconquest.engine.industry.*;
import java.util.*;

/** Funded local procurement. Creating or expiring a promise never creates inventory. */
public final class SupplyProcurement {
    private SupplyProcurement() {}
    public static GameState accept(GameState state, String id, String facilityId, String hubId, String material,
                                   double quantity, double wholesalePrice, long deadline) {
        if (id == null || id.isBlank() || !Double.isFinite(quantity) || quantity <= 0 || !Double.isFinite(wholesalePrice)
                || wholesalePrice <= 0 || deadline < state.turn() || deadline - state.turn() > 360
                || state.businessLedger().contracts().stream().anyMatch(c -> c.id().equals(id))) return state;
        var hub = state.commercialHubs().stream().filter(h -> h.id().equals(hubId)).findFirst().orElse(null);
        var facility = state.industrialFacilities().stream().filter(f -> f.id().equals(facilityId)).findFirst().orElse(null);
        if (hub == null || facility == null || !hub.entityId().equals(facility.planetId())
                || IndustrialFacility.HIVE_GRID.equals(facility.ownershipType())) return state;
        var quoteOrders = new HashMap<>(hub.activeOrders()); var order = quoteOrders.get(material);
        quoteOrders.put(material, new MarketOrder(material, order == null ? 0 : order.supplyKg(), Math.max(order == null ? 0 : order.demandKg(),
                quantity / (deadline - state.turn() + 1)), order == null ? MarketProcessor.basePricePerKg(material) : order.pricePerKg(), 0));
        var quoteHub = hub.withInventory(state.turn(), hub.currentStoredWeightKg(), quoteOrders);
        var forecast = GridSupplyPricing.forecast(state);
        var offer = FacilitySupplyPricing.offers(state, quoteHub, forecast.tariffs().getOrDefault(hub.entityId(), .02),
                        forecast.productionEnergyKwh(), facilityId, material).stream()
                .filter(o -> o.supplierId().equals(facilityId) && o.materialId().equals(material)).findFirst().orElse(null);
        if (offer == null || wholesalePrice + 1e-8 < offer.retailPricePerKg() * IndustryMarketProcessor.WHOLESALE_SHARE
                || deadline < state.turn() + Math.ceil(offer.deliveryDays()) - 1) return state;
        var open = state.businessLedger().contracts().stream().filter(c -> c.openAt(state.turn())).toList();
        double committed = open.stream().filter(c -> c.facilityId().equals(facilityId) && c.materialId().equals(material))
                .mapToDouble(SupplyContract::remainingKg).sum();
        double room = hub.storageCapacityKg() - hub.currentStoredWeightKg() - open.stream()
                .filter(c -> c.hubId().equals(hubId)).mapToDouble(SupplyContract::remainingKg).sum();
        double cash = state.marketAccounts().stream().filter(a -> a.hubId().equals(hubId))
                .mapToDouble(MarketAccount::unsettledSalesCredits).findFirst().orElse(0);
        double payment = quantity * wholesalePrice;
        if (!Double.isFinite(payment) || payment > cash || quantity > room
                || committed + quantity > offer.quantityKg() * (deadline - state.turn() + 1) + 1e-6) return state;
        var contracts = new ArrayList<>(state.businessLedger().contracts());
        contracts.add(new SupplyContract(id, facilityId, facility.ownerEntityId(), hubId, material, quantity, 0,
                wholesalePrice, payment, state.turn(), deadline, SupplyContract.OPEN));
        return state.toBuilder().businessLedger(state.businessLedger().withContracts(contracts)).marketAccounts(
                state.marketAccounts().stream().map(a -> !a.hubId().equals(hubId) ? a
                        : new MarketAccount(hubId, a.unsettledSalesCredits() - payment)).toList()).build();
    }
    public static GameState cancel(GameState state, String id) {
        var contract = state.businessLedger().contracts().stream().filter(c -> c.id().equals(id)
                && SupplyContract.OPEN.equals(c.status())).findFirst().orElse(null);
        return contract == null ? state : refund(state, contract, SupplyContract.CANCELLED);
    }
    private static GameState refund(GameState state, SupplyContract contract, String reason) {
        var accounts = new LinkedHashMap<String, MarketAccount>();
        state.marketAccounts().forEach(a -> accounts.put(a.hubId(), a));
        double cash = accounts.getOrDefault(contract.hubId(), new MarketAccount(contract.hubId(), 0)).unsettledSalesCredits();
        accounts.put(contract.hubId(), new MarketAccount(contract.hubId(), cash + contract.escrowCredits()));
        return state.toBuilder().marketAccounts(List.copyOf(accounts.values())).businessLedger(state.businessLedger().withContracts(
                state.businessLedger().contracts().stream().map(c -> c.id().equals(contract.id()) ? c.closed(reason) : c).toList())).build();
    }
    public static GameState prepare(GameState state) {
        long turn = state.turn();
        for (var contract : state.businessLedger().contracts()) {
            if (!SupplyContract.OPEN.equals(contract.status())) continue;
            var current = state;
            boolean valid = state.industrialFacilities().stream().anyMatch(f -> f.id().equals(contract.facilityId())
                    && f.ownerEntityId().equals(contract.supplierOwnerId()) && current.commercialHubs().stream()
                    .anyMatch(h -> h.id().equals(contract.hubId()) && h.entityId().equals(f.planetId())));
            if (!valid || !contract.openAt(state.turn())) state = refund(state, contract, SupplyContract.EXPIRED);
        }
        // Long-lead production needs a funded order before present stock runs out.
        var forecast = GridSupplyPricing.forecast(state);
        for (var hub : state.commercialHubs()) {
            var leadTimes = SupplyLeadTimes.local(state, hub);
            var inputMaterials = new HashSet<String>();
            for (var f : state.industrialFacilities()) {
                if (!f.planetId().equals(hub.entityId())) continue;
                var recipe = IndustryRecipeCatalog.forFacility(state, f);
                if (IndustryRecipeCatalog.isUnlocked(recipe, com.spaceconquest.engine.economy.EconomicOwnership.empireFor(state, f.ownerEntityId())))
                    inputMaterials.addAll(recipe.inputsKg().keySet());
            }
            var offers = candidates(state, hub, forecast, inputMaterials)
                    .stream().sorted(Comparator.comparingDouble(SupplyOffer::retailPricePerKg).thenComparing(SupplyOffer::supplierId)).toList();
            int accepted = 0;
            for (var offer : offers) {
                var target = hub.stockTargets().get(offer.materialId()); var order = hub.activeOrders().get(offer.materialId());
                double lead = Math.max(offer.deliveryDays(), leadTimes.getOrDefault(offer.materialId(), 0.0));
                boolean forward = lead > 1 || inputMaterials.contains(offer.materialId());
                if (order == null || target == null && (!forward || order.demandKg() <= 0) || accepted >= 2
                        || !forward && offer.retailPricePerKg() > order.pricePerKg() + 1e-6) continue;
                double threshold = target == null ? order.demandKg() * (30 + lead) : target.reorderThresholdKg();
                double targetStock = target == null ? threshold : target.targetStockKg();
                if (order.supplyKg() >= threshold) continue;
                double committed = state.businessLedger().contracts().stream().filter(c -> c.openAt(turn)
                        && c.hubId().equals(hub.id()) && c.materialId().equals(offer.materialId())).mapToDouble(SupplyContract::remainingKg).sum();
                double days = Math.min(360, Math.max(1, Math.ceil(lead)));
                double quantity = Math.min(targetStock - order.supplyKg() - committed, offer.quantityKg() * days);
                double cash = state.marketAccounts().stream().filter(a -> a.hubId().equals(hub.id()))
                        .mapToDouble(MarketAccount::unsettledSalesCredits).findFirst().orElse(0);
                quantity = Math.min(quantity, Math.max(0, Math.nextDown(cash)) / (offer.retailPricePerKg() * IndustryMarketProcessor.WHOLESALE_SHARE));
                var next = accept(state, "procurement-" + state.turn() + "-" + hub.id() + "-" + offer.supplierId() + "-" + offer.materialId(),
                        offer.supplierId(), hub.id(), offer.materialId(), quantity, offer.retailPricePerKg() * IndustryMarketProcessor.WHOLESALE_SHARE,
                        state.turn() + Math.min(360, (long) days + 7));
                if (next != state) { state = next; accepted++; }
            }
        }
        return state;
    }
    private static List<SupplyOffer> candidates(GameState state, CommercialHub hub, GridSupplyPricing.Forecast forecast, Set<String> inputs) {
        double price = forecast.tariffs().getOrDefault(hub.entityId(), .02);
        var result = new LinkedHashMap<String, SupplyOffer>();
        for (var offer : FacilitySupplyPricing.offers(state, hub, price, forecast.productionEnergyKwh()))
            result.put(offer.supplierId() + ":" + offer.materialId(), offer);
        for (var facility : state.industrialFacilities()) {
            var recipe = IndustryRecipeCatalog.forFacility(state, facility);
            if (!facility.planetId().equals(hub.entityId()) || recipe == null || recipe.infrastructure() == null) continue;
            for (String material : recipe.infrastructure().byproductMaterialIds()) {
                var order = hub.activeOrders().get(material);
                if (!inputs.contains(material) || order == null || order.demandKg() <= 0 || order.supplyKg() >= order.demandKg() * 30) continue;
                for (var offer : FacilitySupplyPricing.offers(state, hub, price, forecast.productionEnergyKwh(), facility.id(), material))
                    if (offer.materialId().equals(material)) result.putIfAbsent(offer.supplierId() + ":" + material, offer);
            }
        }
        return List.copyOf(result.values());
    }
}
