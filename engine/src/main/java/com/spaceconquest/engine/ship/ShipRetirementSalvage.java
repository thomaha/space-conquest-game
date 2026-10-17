package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.MarketAccount;
import com.spaceconquest.engine.industry.IndustryMarketProcessor;
import java.util.*;

/** Captured hardware recovery and a fully funded physical sale; no speculative salvage cash. */
public final class ShipRetirementSalvage {
    private ShipRetirementSalvage() {}
    public record Sale(String hubId, Map<String, Double> materialsKg, double credits, List<String> blockers,
                       double buyerPaymentCredits, String tariffRecipient, double tariffCredits) {
        public Sale { materialsKg = Map.copyOf(materialsKg); blockers = List.copyOf(blockers); }
        public Sale(String hubId, Map<String, Double> materialsKg, double credits, List<String> blockers) {
            this(hubId, materialsKg, credits, blockers, credits, null, 0);
        }
        public boolean ready() { return blockers.isEmpty(); }
    }
    public static Map<String, Double> materials(ShipDesign design, ShipInstance ship, ShipRetirementTechnology rules) {
        var result = new HashMap<String, Double>(); var assembly = design.assembly();
        double hull = Math.clamp(ship.currentHullHealth() / ShipConstructionProcessor.COMMISSIONED_HULL_HEALTH, 0, 1);
        add(result, assembly.frame().materialsKg(), rules.frameRecoveryFraction() * hull);
        add(result, assembly.armor().materialsKg(), rules.armorRecoveryFraction() * hull);
        for (int mount = 0; mount < assembly.installations().size(); mount++)
            add(result, assembly.installations().get(mount).production().materialsKg(), rules.componentRecoveryFraction() * ship.moduleDamage().condition(mount));
        add(result, ship.storedCargoKg(), 1); add(result, ship.supplyState().materialsKg(), 1);
        add(result, ShipPowerProcessor.reserves(ship).generatorMaterialsKg(), 1);
        var drive = PropulsionCatalog.mainDrive(design.equippedModuleIds());
        if (drive != null) add(result, drive.propellantMaterials(ship.currentFuelKg()), 1);
        result.values().removeIf(kg -> kg <= 0); return Map.copyOf(result);
    }
    private static void add(Map<String, Double> target, Map<String, Double> source, double factor) {
        source.forEach((id, kg) -> target.merge(id, kg * factor, Double::sum));
    }
    public static Sale quote(GameState state, String yard, Map<String, Double> materials) {
        var hub = state.commercialHubs().stream().filter(item -> yard.equals(item.entityId())).findFirst().orElse(null);
        var blockers = new ArrayList<String>(); double credits = 0;
        if (hub == null) return new Sale("", materials, 0, List.of("A local market is required for recovered materials."));
        double mass = materials.values().stream().mapToDouble(Double::doubleValue).sum();
        if (!Double.isFinite(mass) || !Double.isFinite(hub.storageCapacityKg()) || !Double.isFinite(hub.currentStoredWeightKg())
                || mass > hub.storageCapacityKg() - hub.currentStoredWeightKg() + 1e-6) blockers.add("Market storage cannot accept all recovered materials.");
        if (!Double.isFinite(hub.transactionTariffRate())) blockers.add("Market tariff is invalid.");
        for (var entry : materials.entrySet()) {
            var order = hub.activeOrders().get(entry.getKey());
            if (order == null || !Double.isFinite(order.pricePerKg()) || order.pricePerKg() < 0) blockers.add("No valid recovery price for " + entry.getKey() + ".");
            else credits += entry.getValue() * order.pricePerKg() * IndustryMarketProcessor.WHOLESALE_SHARE;
        }
        double cash = state.marketAccounts().stream().filter(account -> hub.id().equals(account.hubId())).mapToDouble(MarketAccount::unsettledSalesCredits).findFirst().orElse(0);
        if (!Double.isFinite(credits) || credits > cash + 1e-6) blockers.add("Market funds cannot buy the complete recovery.");
        String system = com.spaceconquest.engine.industry.ConstructionMaterials.systemForBody(state, yard);
        String controller = system == null ? null : state.empires().stream().filter(empire -> empire.controlledSystemIds().contains(system)).map(Empire::id).findFirst().orElse(null);
        double tariff = controller == null || !Double.isFinite(hub.transactionTariffRate()) ? 0 : credits * Math.clamp(hub.transactionTariffRate(), 0, 1);
        return new Sale(hub.id(), materials, credits - tariff, blockers, credits, controller, tariff);
    }
    public static GameState settle(GameState state, String owner, Sale sale) {
        if (!sale.ready()) throw new IllegalArgumentException("Unfunded salvage sale");
        var hub = state.commercialHubs().stream().filter(item -> sale.hubId().equals(item.id())).findFirst().orElseThrow();
        var orders = new HashMap<>(hub.activeOrders());
        sale.materialsKg().forEach((id, kg) -> { var old = orders.get(id); orders.put(id, new MarketOrder(id, old.supplyKg() + kg, old.demandKg(), old.pricePerKg(), old.shortcomingScore())); });
        var updated = hub.withInventory(state.turn(), hub.currentStoredWeightKg() + sale.materialsKg().values().stream().mapToDouble(Double::doubleValue).sum(), orders);
        var settled = ShipyardPayments.change(state.withCommercialHubs(state.commercialHubs().stream().map(item -> hub.id().equals(item.id()) ? updated : item).toList())
                .withMarketAccounts(state.marketAccounts().stream().map(account -> sale.hubId().equals(account.hubId())
                        ? new MarketAccount(account.hubId(), Math.max(0, account.unsettledSalesCredits() - sale.buyerPaymentCredits())) : account).toList()), owner, sale.credits());
        return sale.tariffRecipient() == null ? settled : ShipyardPayments.change(settled, sale.tariffRecipient(), sale.tariffCredits());
    }
}
