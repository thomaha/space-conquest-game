package com.spaceconquest.control.command;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.ship.*;
import java.util.*;

/** Physical unloading into a funded local buyer; cash and storage are rechecked on execution. */
public record SellShipCargoCommand(String ownerId, String shipId, String hubId, String materialId, double quantityKg) implements GameCommand {
    @Override public boolean validate(GameState state) {
        if (state == null || ownerId == null || shipId == null || hubId == null || materialId == null || !Double.isFinite(quantityKg) || quantityKg <= 0) return false;
        var fleet = ShipMiningProcessor.fleet(state, shipId); var hub = state.commercialHubs().stream().filter(item -> hubId.equals(item.id())).findFirst().orElse(null);
        if (fleet == null || hub == null || !ownerId.equals(fleet.ownerEntityId()) || !FleetPositioning.atHub(state, fleet, hub) || ShipRefitPlan.reserved(state, shipId)
                || state.tradeRoutes().stream().anyMatch(route -> route.carrierCommitted() && route.assignedFreighterIds().contains(shipId))) return false;
        var ship = fleet.ships().stream().filter(item -> shipId.equals(item.id())).findFirst().orElseThrow();
        return ownerId.equals(ship.ownerEntityId()) && ship.storedCargoKg().getOrDefault(materialId, 0.0) >= quantityKg
                && ShipRetirementSalvage.quote(state, hub.entityId(), Map.of(materialId, quantityKg)).ready();
    }
    @Override public GameState apply(GameState state) {
        if (!validate(state)) return state;
        var fleet = ShipMiningProcessor.fleet(state, shipId); var ship = fleet.ships().stream().filter(item -> shipId.equals(item.id())).findFirst().orElseThrow();
        var hub = state.commercialHubs().stream().filter(item -> hubId.equals(item.id())).findFirst().orElseThrow();
        var settled = ShipRetirementSalvage.settle(state, ownerId, ShipRetirementSalvage.quote(state, hub.entityId(), Map.of(materialId, quantityKg)));
        var cargo = new HashMap<>(ship.storedCargoKg()); cargo.compute(materialId, (id, kg) -> kg - quantityKg); cargo.values().removeIf(kg -> kg <= 0);
        var unloaded = new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), ship.currentHullHealth(), ship.currentShieldHealth(), ship.currentFuelKg(), cargo,
                ship.passengerCount(), ship.passengerRaceId(), ship.transitMode(), ship.powerState(), ship.supplyState(), ship.moduleDamage());
        return FleetReturnTravel.replace(settled, fleet.withShips(fleet.ships().stream().map(item -> shipId.equals(item.id()) ? unloaded : item).toList()));
    }
}
