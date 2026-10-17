package com.spaceconquest.control.command;

import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.habitation.PassengerStasis;
import com.spaceconquest.engine.industry.ConstructionMaterials;
import com.spaceconquest.engine.ship.ShipConstructionOrder;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.PropulsionCatalog;
import com.spaceconquest.engine.ship.ShipConstructionRequirements;
import com.spaceconquest.engine.ship.ShipyardWorkCapacity;
import com.spaceconquest.engine.ship.ShipManufacturingCapacity;
import com.spaceconquest.engine.ship.ShipComponentCatalog;
import com.spaceconquest.engine.ship.ChemicalFreighterCatalog;
import com.spaceconquest.engine.macrostructure.StationModule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Stages a ship build; daily work consumes local materials before commissioning a hull. */
public record QueueShipBuildCommand(String ownerEntityId, String designId,
                                    String systemId) implements GameCommand {
    @Override
    public boolean validate(GameState state) {
        if (state == null || ownerEntityId == null || designId == null || systemId == null) {
            return false;
        }
        Empire owner = com.spaceconquest.engine.economy.EconomicOwnership.empireFor(state, ownerEntityId);
        if (owner == null || !owner.controlledSystemIds().contains(systemId) || resolveYardEntity(state) == null
                || !ownerEntityId.equals(owner.id()) && !com.spaceconquest.engine.economy.EconomicOwnership.corporationsAllowed(owner)) return false;
        return state.shipDesigns().stream().anyMatch(design -> design.id().equals(designId)
                && ownerEntityId.equals(design.ownerEntityId())
                && com.spaceconquest.engine.ship.HullTechnology.canBuild(state, ownerEntityId, design.hullFrame())
                && (!design.equippedModuleIds().contains(PassengerStasis.MODULE_ID)
                || owner.unlockedTechIds().contains(PassengerStasis.TECHNOLOGY_ID))
                && PropulsionCatalog.researched(design.equippedModuleIds(),
                owner.unlockedTechIds())
                && ShipComponentCatalog.powerResearched(design.equippedModuleIds(), owner.unlockedTechIds())
                && com.spaceconquest.engine.ship.ShipMiningModuleCatalog.researched(design.equippedModuleIds(), owner.unlockedTechIds())
                && com.spaceconquest.engine.ship.SensorModuleCatalog.researched(design.equippedModuleIds(), owner.unlockedTechIds())
                && ChemicalFreighterCatalog.researched(design.equippedModuleIds(), owner.unlockedTechIds())
                && ChemicalFreighterCatalog.compatible(design.equippedModuleIds())
                && PropulsionCatalog.validConfiguration(design.equippedModuleIds(),
                design.fuelCapacityKg())
                && (!design.isProprietaryCorporateDesign() || !ownerEntityId.equals(owner.id())));
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) return state;
        ShipDesign design = state.shipDesigns().stream()
                .filter(item -> designId.equals(item.id())).findFirst().orElseThrow();
        String bodyId = resolveYardEntity(state);
        ShipConstructionRequirements.Estimate estimate = ShipConstructionRequirements.estimate(design);
        List<ShipConstructionOrder> orders = new ArrayList<>(state.shipConstructionOrders());
        orders.add(new ShipConstructionOrder("ship_order_" + UUID.randomUUID(),
                ownerEntityId, designId, systemId, bodyId, 0.0, estimate.workUnits(),
                estimate.materialsKg(), Map.of(), resolveYard(state).contract()));
        return state.withShipConstructionOrders(orders);
    }

    public com.spaceconquest.engine.ship.ShipyardAccess.Offer resolveYard(GameState state) {
        if (state == null || systemId == null || ownerEntityId == null || designId == null) return null;
        var design = state.shipDesigns().stream().filter(item -> designId.equals(item.id()) && ownerEntityId.equals(item.ownerEntityId())).findFirst().orElse(null);
        return com.spaceconquest.engine.ship.ShipyardAccess.select(state, ownerEntityId, systemId, design);
    }
    public String resolveYardEntity(GameState state) {
        var offer = resolveYard(state); return offer == null ? null : offer.yardId();
    }
}
