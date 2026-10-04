package com.spaceconquest.control.command;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.FleetSupplySimulation;
import com.spaceconquest.engine.ship.ShipSupplyTransfer;
/** Reserves one explicit delivery for the next sublight coasting phase. */
public record ScheduleFleetSupplyCommand(String supplierShipId, String receiverShipId, String fuelId,
                                         double quantityKg, double coastDelayHours,
                                         ShipSupplyTransfer.Destination destination) implements GameCommand {
    public ScheduleFleetSupplyCommand(String supplierShipId, String receiverShipId, String fuelId,
                                       double quantityKg, double coastDelayHours) {
        this(supplierShipId, receiverShipId, fuelId, quantityKg, coastDelayHours, ShipSupplyTransfer.Destination.ELECTRICAL_FUEL);
    }
    @Override public boolean validate(GameState state) { return apply(state) != state; }
    @Override public GameState apply(GameState state) {
        return FleetSupplySimulation.configure(state, supplierShipId, receiverShipId, fuelId, quantityKg, coastDelayHours, destination);
    }
}
