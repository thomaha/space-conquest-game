package com.spaceconquest.engine.ship;

import java.util.*;

/** Immutable saved mining orders indexed by stable ship identity. */
public record ShipMiningState(Map<String, ShipMiningOrder> orders) {
    public ShipMiningState { orders = orders == null ? Map.of() : Map.copyOf(orders); }
    public static ShipMiningState empty() { return new ShipMiningState(Map.of()); }
    public ShipMiningState with(String shipId, ShipMiningOrder order) {
        var next = new HashMap<>(orders); if (order == null) next.remove(shipId); else next.put(shipId, order);
        return new ShipMiningState(next);
    }
}
