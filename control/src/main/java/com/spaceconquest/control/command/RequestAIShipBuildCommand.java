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
        var current = AIShipResearchRevision.current(state, design); var preview = AIShipResearchRevision.register(state, current);
        var build = new QueueShipBuildCommand(ownerEntityId, current.id(), systemId);
        if (!build.validate(preview)) return false;
        String yard = build.resolveYardEntity(preview);
        var bill = ShipConstructionRequirements.estimate(current).materialsKg();
        var purchase = preview.orbitalStations().stream().anyMatch(station -> yard.equals(station.id()))
                ? ConstructionMaterials.buyOrbitalUpTo(preview, systemId, yard, ownerEntityId, bill, Double.MAX_VALUE)
                : ConstructionMaterials.buyUpTo(preview, yard, ownerEntityId, bill);
        return purchase.covers(bill);
    }

    @Override public GameState apply(GameState state) {
        if (!validate(state)) return state;
        var source = state.shipDesigns().stream().filter(design -> designId.equals(design.id()) && ownerEntityId.equals(design.ownerEntityId())).findFirst().orElseThrow();
        var current = AIShipResearchRevision.current(state, source); var registered = AIShipResearchRevision.register(state, current);
        return com.spaceconquest.control.ai.AIShipDeploymentIntent.capture(new QueueShipBuildCommand(ownerEntityId, current.id(), systemId).apply(registered), ownerEntityId);
    }
}
