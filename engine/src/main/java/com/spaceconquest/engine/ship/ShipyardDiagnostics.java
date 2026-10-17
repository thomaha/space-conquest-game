package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.economy.EconomicOwnership;
import java.util.*;

/** Snapshot-only queue diagnostics; no purchases, travel forecasts or state mutation. */
public final class ShipyardDiagnostics {
    private ShipyardDiagnostics() {}
    public record Order(String orderId, String customer, String provider, String yard, double progressPercent, double paidFees, String status) {}
    public static List<Order> orders(GameState state, String empireId) {
        var result = new ArrayList<Order>();
        for (var order : state.shipConstructionOrders()) {
            var home = EconomicOwnership.empireFor(state, order.ownerEntityId()); var providerHome = EconomicOwnership.empireFor(state, ShipyardAccess.provider(order));
            if (!(home != null && empireId.equals(home.id()) || providerHome != null && empireId.equals(providerHome.id()))) continue;
            var receipt = state.shipyardLedger().history().reversed().stream().filter(row -> order.id().equals(row.orderId())).findFirst().orElse(null);
            String status = receipt == null ? "Awaiting construction tick" : receipt.status();
            var design = state.shipDesigns().stream().filter(item -> order.designId().equals(item.id()) && order.ownerEntityId().equals(item.ownerEntityId())).findFirst().orElse(null);
            int capability = ShipManufacturingCapacity.forYard(state, ShipyardAccess.provider(order), order.systemId(), order.yardBodyId());
            if (order.yardContract() != null && !ShipyardAccess.permitted(state, order.ownerEntityId(), ShipyardAccess.provider(order), order.systemId())) status = "Yard access unavailable";
            else if (design != null && home != null && (!CombatModuleCatalog.researched(design.equippedModuleIds(), home.unlockedTechIds())
                    || !PropulsionCatalog.researched(design.equippedModuleIds(), home.unlockedTechIds())
                    || !ShipComponentCatalog.powerResearched(design.equippedModuleIds(), home.unlockedTechIds())
                    || !SensorModuleCatalog.researched(design.equippedModuleIds(), home.unlockedTechIds()))) status = "Missing component research";
            else if (design != null && ShipManufacturingCapacity.requiredComplexity(design) > capability)
                status = "Waiting for manufacturing capability: needs " + ShipManufacturingCapacity.requiredComplexity(design) + ", yard supports " + capability;
            else if (ShipyardWorkCapacity.forYard(state, ShipyardAccess.provider(order), order.systemId(), order.yardBodyId()).workPerDay() <= 0) status = "Missing paid workforce or operational yard";
            if (order.priority() != null) status += " | " + order.priority().reason();
            if (order.deployment() != null) status += " | Planned " + order.deployment().kind().name().toLowerCase(java.util.Locale.ROOT) + " " + order.deployment().targetId();
            if (order.refit() != null) status += " | Refitting " + order.refit().shipId();
            if (order.retirement() != null) status += " | Retiring " + order.retirement().shipId();
            if (order.replacement() != null) status += " | Replacing " + order.replacement().sourceShipId();
            result.add(new Order(order.id(), name(state, order.ownerEntityId()), name(state, ShipyardAccess.provider(order)), order.yardBodyId(),
                    Math.min(100, 100 * order.accumulatedWorkHours() / Math.max(1, order.requiredWorkHours())), order.yardContract() == null ? 0 : order.yardContract().paidFeesCredits(), status));
        }
        return List.copyOf(result);
    }
    public static String name(GameState state, String id) {
        return state.empires().stream().filter(owner -> id.equals(owner.id())).map(owner -> owner.name()).findFirst()
                .orElseGet(() -> state.corporations().stream().filter(corp -> id.equals(corp.id())).map(corp -> corp.name()).findFirst().orElse(id));
    }
}
