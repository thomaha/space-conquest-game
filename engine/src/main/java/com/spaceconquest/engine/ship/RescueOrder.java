package com.spaceconquest.engine.ship;

/** Persisted supply request. Transfer is revalidated against physical contact and live reserves. */
public record RescueOrder(String targetFleetId, String donorShipId, String receiverShipId,
                          ShipSupplyTransfer.Source source, ShipSupplyTransfer.Destination destination,
                          String fuelId, double quantityKg) {
    public RescueOrder {
        if (targetFleetId == null || targetFleetId.isBlank() || donorShipId == null || receiverShipId == null
                || donorShipId.equals(receiverShipId) || source == null || destination == null || fuelId == null
                || !Double.isFinite(quantityKg) || quantityKg <= 0)
            throw new IllegalArgumentException("Invalid rescue supply request");
    }
}
