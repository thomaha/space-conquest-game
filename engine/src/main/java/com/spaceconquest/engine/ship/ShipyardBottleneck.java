package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.ConstructionMaterials;
import java.util.*;

/** Discarded paid work previews distinguish a busy yard from unfunded demand. */
public final class ShipyardBottleneck {
    private ShipyardBottleneck() {}
    public record Demand(double work, double fees, boolean strategic) {}
    public static Demand fundedDemand(GameState state, String provider, String system, String yard, double maximumWork) {
        return fundedDemand(state, provider, system, yard, maximumWork, false);
    }
    public static Demand fundedDemand(GameState state, String provider, String system, String yard, double maximumWork, boolean onlyOwner) {
        var current = state; double work = 0, fees = 0; boolean strategic = false;
        boolean orbital = state.orbitalStations().stream().anyMatch(station -> yard.equals(station.id()));
        for (var order : state.shipConstructionOrders()) {
            if (!yard.equals(order.yardBodyId()) || !system.equals(order.systemId()) || !provider.equals(ShipyardAccess.provider(order))) continue;
            if (onlyOwner && !provider.equals(order.ownerEntityId())) continue;
            var design = state.shipDesigns().stream().filter(item -> order.designId().equals(item.id()) && order.ownerEntityId().equals(item.ownerEntityId())).findFirst().orElse(null);
            if (design == null || !ShipyardAccess.permitted(current, order.ownerEntityId(), provider, system)
                    || ShipManufacturingCapacity.forYard(current, provider, system, yard) < ShipManufacturingCapacity.requiredComplexity(design)) continue;
            // Partial material mass can support early progress. Investment requires the complete remaining bill,
            // so a missing late component cannot masquerade as sustained demand for a larger yard.
            var bill = new HashMap<String, Double>();
            order.requiredMaterialsKg().forEach((material, kg) -> {
                double missing = Math.max(0, kg - order.consumedMaterialsKg().getOrDefault(material, 0.0));
                if (missing > 0) bill.put(material, missing);
            });
            var held = ShipyardPayments.pay(current, order.ownerEntityId(), order.yardContract(),
                    Math.max(0, order.requiredWorkHours() - order.accumulatedWorkHours()));
            if (held == null) continue;
            double mass = bill.values().stream().mapToDouble(Double::doubleValue).sum();
            var materials = orbital ? ConstructionMaterials.buyOrbitalUpTo(held, system, yard, order.ownerEntityId(), bill, mass)
                    : ConstructionMaterials.buyUpTo(held, yard, order.ownerEntityId(), bill, mass);
            if (!materials.covers(bill)) continue;
            var step = ContractShipConstruction.advance(current, order, Math.max(0, maximumWork - work), orbital);
            double used = step.progress().workHours() - order.accumulatedWorkHours();
            current = step.progress().state(); work += used;
            fees += used * (order.yardContract() == null ? 0 : order.yardContract().feeCreditsPerWorkUnit());
            strategic |= used > 0 && provider.equals(order.ownerEntityId());
            if (work >= maximumWork) break;
        }
        return new Demand(work, fees, strategic);
    }
    public static boolean saturated(GameState state, String provider, String system, String yard,
                                    double capacity, int turns, double minimumUtilization) {
        // Actual receipts survive saves and controller reconstruction. Missing days break the observation.
        for (int offset = 0; offset < turns; offset++) {
            long turn = state.turn() - offset;
            double work = state.shipyardLedger().history().stream().filter(receipt -> receipt.turn() == turn
                    && provider.equals(receipt.providerId()) && system.equals(receipt.systemId()) && yard.equals(receipt.yardId()))
                    .mapToDouble(ShipyardReceipt::workUnits).sum();
            if (work < capacity * minimumUtilization) return false;
        }
        return true;
    }
}
