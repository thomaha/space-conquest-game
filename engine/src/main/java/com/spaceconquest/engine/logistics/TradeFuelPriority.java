package com.spaceconquest.engine.logistics;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.PropulsionCatalog;
import com.spaceconquest.engine.ship.ShipPowerProfile;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure ranking of configured fuel shortages; never authorizes a purchase or unsafe voyage. */
final class TradeFuelPriority {
    private static final Set<String> MATERIALS = materials();
    private TradeFuelPriority() {}

    private static Set<String> materials() {
        var materials = new HashSet<String>();
        for (String id : PropulsionCatalog.MAIN_DRIVE_IDS) {
            materials.addAll(PropulsionCatalog.drive(id).propellantMaterials(1).keySet());
            PropulsionCatalog.reactorFuels(id).forEach(fuel -> materials.add(fuel.materialId()));
        }
        ShipPowerProfile.capture(List.of()).fuels().values()
                .forEach(fuel -> materials.addAll(fuel.materials(1).keySet()));
        return Set.copyOf(materials);
    }

    static double urgency(GameState state, CommercialHub hub, String material, String routeId) {
        var target = hub.stockTargets().get(material);
        if (!MATERIALS.contains(material) || target == null || target.reorderThresholdKg() <= 0
                || HubReplenishment.requestedKg(state, hub, material, routeId) <= 0) return 0;
        double stock = hub.activeOrders().get(material).supplyKg()
                + HubReplenishment.inboundKg(state, hub, material, routeId);
        return Math.clamp((target.reorderThresholdKg() - stock) / target.reorderThresholdKg(), 0, 1);
    }

    /** Net public stock added after paying for protected destination fuel. */
    static double relief(GameState state, LogisticsProcessor.ShipmentPreview shipment) {
        return relief(state, shipment.state(), shipment.route());
    }

    static double relief(GameState state, GameState prepared, TradeRoute shipment) {
        var hub = state.commercialHubs().stream().filter(item -> item.id().equals(shipment.destinationEntityId()))
                .findFirst().orElse(null);
        if (hub == null) return 0;
        var after = prepared.commercialHubs().stream().filter(item -> item.id().equals(hub.id())).findFirst().orElseThrow();
        return hub.stockTargets().keySet().stream().mapToDouble(material -> {
            double urgency = urgency(state, hub, material, shipment.id());
            if (urgency <= 0) return 0;
            var lot = shipment.cargoManifest().get(material);
            double withdrawal = Math.max(0, hub.activeOrders().get(material).supplyKg()
                    - after.activeOrders().get(material).supplyKg());
            double net = (lot == null ? 0 : lot.massKg()) - withdrawal;
            return Math.min(urgency, net / hub.stockTargets().get(material).reorderThresholdKg());
        }).sum();
    }

    static boolean consumesUrgentStock(GameState state, LogisticsProcessor.ShipmentPreview shipment) {
        var hub = state.commercialHubs().stream().filter(item -> item.id().equals(shipment.route().destinationEntityId()))
                .findFirst().orElseThrow();
        var after = shipment.state().commercialHubs().stream().filter(item -> item.id().equals(hub.id())).findFirst().orElseThrow();
        return hub.stockTargets().keySet().stream().anyMatch(material ->
                urgency(state, hub, material, shipment.route().id()) > 0
                        && hub.activeOrders().get(material).supplyKg() - after.activeOrders().get(material).supplyKg() > 1e-6);
    }

    static boolean better(GameState state, TradeShipmentSizing.Selection candidate, TradeShipmentSizing.Selection best) {
        if (candidate == null || candidate.profit() <= 1e-6) return false;
        if (best == null || best.profit() <= 1e-6) return true;
        double relief = relief(state, candidate.shipment()), previous = relief(state, best.shipment());
        return relief > previous + 1e-9 || Math.abs(relief - previous) <= 1e-9
                && candidate.profit() / candidate.days() > best.profit() / best.days();
    }
}
