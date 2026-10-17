package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;

/** One discarded installment quote; never commissions a ship or commits materials and fees. */
public final class ShipConstructionReadiness {
    private ShipConstructionReadiness() {}
    public record Quote(boolean ready, double workPerDay, double remainingDays, String reason) {}
    public static Quote quote(GameState state, ShipConstructionOrder order) {
        var design = state.shipDesigns().stream().filter(item -> order.designId().equals(item.id()) && order.ownerEntityId().equals(item.ownerEntityId())).findFirst().orElse(null);
        String provider = ShipyardAccess.provider(order);
        if (design == null) return blocked("Blueprint unavailable");
        String problem = order.retirement() != null ? order.retirement().problem(state, order, design)
                : order.refit() != null ? order.refit().problem(state, order, design) : null;
        if (problem != null) return blocked(problem);
        if (!ShipyardAccess.permitted(state, order.ownerEntityId(), provider, order.systemId())) return blocked("Yard access unavailable");
        if (ShipManufacturingCapacity.forYard(state, provider, order.systemId(), order.yardBodyId()) < ShipManufacturingCapacity.requiredComplexity(design)) return blocked("Waiting for manufacturing capability");
        double capacity = ShipyardWorkCapacity.forYard(state, provider, order.systemId(), order.yardBodyId()).workPerDay();
        if (capacity <= 0) return blocked("Missing paid workforce or operational yard");
        boolean orbital = state.orbitalStations().stream().anyMatch(station -> order.yardBodyId().equals(station.id()));
        var step = ContractShipConstruction.advance(state, order, capacity, orbital);
        double fundedWork = step.progress().workHours() - order.accumulatedWorkHours();
        if (fundedWork <= 0) return blocked("Waiting for materials or funds");
        return new Quote(true, fundedWork, Math.max(0, order.requiredWorkHours() - order.accumulatedWorkHours()) / fundedWork, "Ready for paid work");
    }
    private static Quote blocked(String reason) { return new Quote(false, 0, Double.POSITIVE_INFINITY, reason); }
}
