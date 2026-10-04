package com.spaceconquest.control.command;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.FleetSupplySimulation;
import com.spaceconquest.engine.ship.FleetSupplyOrder;
public record CancelFleetSupplyCommand(String supplierShipId, FleetSupplyOrder selected) implements GameCommand {
    public CancelFleetSupplyCommand(String supplierShipId) { this(supplierShipId, null); }
    @Override public boolean validate(GameState state) { return apply(state) != state; }
    @Override public GameState apply(GameState state) { return FleetSupplySimulation.cancel(state, supplierShipId, selected); }
}
