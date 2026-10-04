package com.spaceconquest.engine.ship;

import java.util.List;

/** One reserved delivery completing after a delay and pumping time in the coasting phase. */
public record FleetSupplyOrder(String fleetId, String ownerEntityId, String supplierShipId, String receiverShipId,
                               List<String> memberShipIds, String fuelId, double quantityKg, double coastDelayHours,
                               ShipSupplyTransfer.Destination destination) {
    public FleetSupplyOrder(String fleetId, String ownerEntityId, String supplierShipId, String receiverShipId,
                            List<String> memberShipIds, String fuelId, double quantityKg, double coastDelayHours) {
        this(fleetId, ownerEntityId, supplierShipId, receiverShipId, memberShipIds, fuelId, quantityKg,
                coastDelayHours, ShipSupplyTransfer.Destination.ELECTRICAL_FUEL);
    }
    public FleetSupplyOrder {
        memberShipIds = memberShipIds == null ? List.of() : List.copyOf(memberShipIds);
        if (destination == null) destination = ShipSupplyTransfer.Destination.ELECTRICAL_FUEL;
    }
}
