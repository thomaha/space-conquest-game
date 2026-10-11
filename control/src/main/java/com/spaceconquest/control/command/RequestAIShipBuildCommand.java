package com.spaceconquest.control.command;

import com.spaceconquest.control.ai.EmpireFleetPlanner;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.ConstructionMaterials;
import com.spaceconquest.engine.ship.ShipConstructionRequirements;

/** Rechecks demand and full material affordability before staging one ordinary owned build. */
public record RequestAIShipBuildCommand(String ownerEntityId, String designId, String systemId) implements GameCommand {
    @Override public boolean validate(GameState state) {
        if (state == null || ownerEntityId == null || designId == null || systemId == null) return false;
        var empire = state.empires().stream().filter(item -> ownerEntityId.equals(item.id())).findFirst().orElse(null);
        var design = state.shipDesigns().stream().filter(item -> designId.equals(item.id())
                && ownerEntityId.equals(item.ownerEntityId())).findFirst().orElse(null);
        if (empire == null || design == null || !EmpireFleetPlanner.needsShip(state, empire, design)
                || state.shipConstructionOrders().stream().anyMatch(order -> ownerEntityId.equals(order.ownerEntityId()))) return false;
        var build = new QueueShipBuildCommand(ownerEntityId, designId, systemId);
        if (!build.validate(state)) return false;
        String yard = build.resolveYardEntity(state);
        var bill = ShipConstructionRequirements.estimate(design).materialsKg();
        var purchase = state.orbitalStations().stream().anyMatch(station -> yard.equals(station.id()))
                ? ConstructionMaterials.buyOrbitalUpTo(state, systemId, yard, ownerEntityId, bill, Double.MAX_VALUE)
                : ConstructionMaterials.buyUpTo(state, yard, ownerEntityId, bill);
        return purchase.covers(bill);
    }

    @Override public GameState apply(GameState state) {
        return validate(state) ? new QueueShipBuildCommand(ownerEntityId, designId, systemId).apply(state) : state;
    }
}
