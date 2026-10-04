package com.spaceconquest.control.command;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.ship.ShipSupplyStorage;
/** Purchases real hub stock into compatible dedicated supply compartments. */
public record BuyShipSupplyFuelCommand(String shipId, String bodyId, String materialId, double quantityKg) implements GameCommand {
    @Override public boolean validate(GameState state) { return apply(state) != state; }
    @Override public GameState apply(GameState state) { return ShipSupplyStorage.buy(state, shipId, bodyId, materialId, quantityKg); }
}
