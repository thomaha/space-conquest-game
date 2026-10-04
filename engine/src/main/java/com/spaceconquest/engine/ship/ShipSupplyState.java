package com.spaceconquest.engine.ship;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/** Physical supply inventory, timed deliveries and voyage clock, independent of cargo and working tanks. */
public record ShipSupplyState(Map<String, Double> materialsKg, FleetSupplyOrder order, String outcome,
                              CargoPreservationState preservation, List<FleetSupplyOrder> additionalOrders,
                              FleetSupplyTimeline timeline) {
    public ShipSupplyState {
        materialsKg = materialsKg == null ? Map.of() : Map.copyOf(materialsKg);
        if (materialsKg.values().stream().anyMatch(value -> !Double.isFinite(value) || value < 0))
            throw new IllegalArgumentException("Invalid supply inventory");
        outcome = outcome == null ? "" : outcome;
        if (preservation == null) preservation = CargoPreservationState.empty();
        additionalOrders = additionalOrders == null ? List.of() : List.copyOf(additionalOrders);
        if (order == null && !additionalOrders.isEmpty()) {
            order = additionalOrders.getFirst();
            additionalOrders = List.copyOf(additionalOrders.subList(1, additionalOrders.size()));
        }
    }
    public ShipSupplyState(Map<String, Double> materials, FleetSupplyOrder order, String outcome, CargoPreservationState preservation) {
        this(materials, order, outcome, preservation, List.of(), null);
    }
    public ShipSupplyState(Map<String, Double> materials, FleetSupplyOrder order, String outcome) {
        this(materials, order, outcome, CargoPreservationState.empty());
    }
    public static ShipSupplyState empty() { return new ShipSupplyState(Map.of(), null, ""); }
    public double massKg() { return materialsKg.values().stream().mapToDouble(Double::doubleValue).sum(); }
    public List<FleetSupplyOrder> orders() {
        var values = new ArrayList<FleetSupplyOrder>();
        if (order != null) values.add(order);
        values.addAll(additionalOrders);
        return List.copyOf(values);
    }
    public ShipSupplyState withMaterials(Map<String, Double> materials) {
        return new ShipSupplyState(materials, order, outcome, preservation, additionalOrders, timeline);
    }
    public ShipSupplyState withOrder(FleetSupplyOrder planned, String message) {
        return withOrders(planned == null ? List.of() : List.of(planned), message);
    }
    public ShipSupplyState withOrders(List<FleetSupplyOrder> values, String message) {
        return new ShipSupplyState(materialsKg, values.isEmpty() ? null : values.getFirst(), message, preservation,
                values.isEmpty() ? List.of() : values.subList(1, values.size()), timeline);
    }
    public ShipSupplyState withTimeline(FleetSupplyTimeline value) {
        return new ShipSupplyState(materialsKg, order, outcome, preservation, additionalOrders, value);
    }
    public ShipSupplyState withPreservation(CargoPreservationState value) {
        return new ShipSupplyState(materialsKg, order, outcome, value, additionalOrders, timeline);
    }
}
