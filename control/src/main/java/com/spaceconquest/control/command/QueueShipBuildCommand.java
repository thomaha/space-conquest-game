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
        Empire owner = state.empires().stream()
                .filter(empire -> ownerEntityId.equals(empire.id())
                        && empire.controlledSystemIds().contains(systemId))
                .findFirst().orElse(null);
        if (owner == null || resolveYardEntity(state) == null) return false;
        return state.shipDesigns().stream().anyMatch(design -> design.id().equals(designId)
                && ownerEntityId.equals(design.ownerEntityId())
                && (!design.equippedModuleIds().contains(PassengerStasis.MODULE_ID)
                || owner.unlockedTechIds().contains(PassengerStasis.TECHNOLOGY_ID))
                && PropulsionCatalog.researched(design.equippedModuleIds(),
                owner.unlockedTechIds())
                && PropulsionCatalog.validConfiguration(design.equippedModuleIds(),
                design.fuelCapacityKg())
                && !design.isProprietaryCorporateDesign());
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
                estimate.materialsKg(), Map.of()));
        return state.withShipConstructionOrders(orders);
    }

    public String resolveYardEntity(GameState state) {
        if (state == null || systemId == null || ownerEntityId == null || designId == null) return null;
        ShipDesign design = state.shipDesigns().stream().filter(item -> designId.equals(item.id()))
                .findFirst().orElse(null);
        int complexity = ShipManufacturingCapacity.requiredComplexity(design);
        String orbital = state.orbitalStations().stream()
                .filter(station -> systemId.equals(station.systemId())
                        && ownerEntityId.equals(station.ownerEntityId())
                        && station.isOperational()
                        && (station.hasModuleType(StationModule.TYPE_SHIPYARD_GRID)
                        || station.hasModuleType(StationModule.TYPE_CAPITAL_SLIPWAY))
                        && ConstructionMaterials.orbitalHubEntity(state, systemId,
                        station.id()) != null)
                .filter(station -> ShipManufacturingCapacity.forYard(state, ownerEntityId, systemId, station.id()) >= complexity)
                .map(station -> station.id()).findFirst().orElse(null);
        if (orbital != null) return orbital;
        return state.solarSystems().stream().filter(system -> systemId.equals(system.id()))
                .flatMap(system -> system.planets().stream()
                        .flatMap(planet -> java.util.stream.Stream.concat(
                                java.util.stream.Stream.of(planet.id()),
                                planet.moons().stream().map(moon -> moon.id()))))
                .filter(body -> ConstructionMaterials.bodyForSystem(state, systemId, body) != null)
                .filter(body -> ShipManufacturingCapacity.forYard(state, ownerEntityId, systemId, body) >= complexity)
                .filter(body -> state.industrialFacilities().stream().anyMatch(facility ->
                        ShipyardWorkCapacity.SURFACE_SHIPYARD_APPLICATION_ID
                                .equals(facility.applicationId())
                                && body.equals(facility.planetId())
                                && ownerEntityId.equals(facility.ownerEntityId())
                                && facility.tier() > 0))
                .findFirst().orElse(null);
    }
}
