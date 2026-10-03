package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.habitation.PassengerStasis;
import com.spaceconquest.engine.industry.ConstructionMaterials;
import com.spaceconquest.engine.macrostructure.StationModule;

import java.io.IOException;
import java.io.UncheckedIOException;

/** Provisional, catalog-configured complexity limits for operational shipyards. */
public final class ShipManufacturingCapacity {
    public record YardLimit(String yardType, int baseComplexity, int complexityPerTier) {}

    private ShipManufacturingCapacity() {}

    private static int limit(String type, int tier) {
        try {
            return DataModelLoader.loadShipyardManufacturingLimits().stream()
                    .filter(rule -> type.equals(rule.yardType()))
                    .mapToInt(rule -> rule.baseComplexity() + Math.max(0, tier - 1) * rule.complexityPerTier())
                    .max().orElse(0);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load shipyard manufacturing limits", e);
        }
    }

    public static int forYard(GameState state, String ownerId, String systemId, String yardId) {
        if (state == null || yardId == null) return 0;
        var station = state.orbitalStations().stream().filter(item -> yardId.equals(item.id())
                && ownerId.equals(item.ownerEntityId()) && systemId.equals(item.systemId()))
                .findFirst().orElse(null);
        if (station != null) {
            if (!station.isOperational()) return 0;
            return station.modules().stream().filter(StationModule::isOnline)
                    .mapToInt(module -> limit(module.type(), 1)).max().orElse(0);
        }
        if (!systemId.equals(ConstructionMaterials.systemForBody(state, yardId))) return 0;
        return state.industrialFacilities().stream().filter(facility -> yardId.equals(facility.planetId())
                && ownerId.equals(facility.ownerEntityId()) && facility.tier() > 0
                && ShipyardWorkCapacity.SURFACE_SHIPYARD_APPLICATION_ID.equals(facility.applicationId()))
                .mapToInt(facility -> limit(facility.applicationId(), facility.tier())).max().orElse(0);
    }

    /** A blueprint can be planned at the basic surface limit before its first yard is built. */
    public static int forOwner(GameState state, String ownerId) {
        int capacity = limit(ShipyardWorkCapacity.SURFACE_SHIPYARD_APPLICATION_ID, 1);
        if (state == null) return capacity;
        for (var station : state.orbitalStations()) {
            if (!ownerId.equals(station.ownerEntityId()) || !station.isOperational()) continue;
            for (StationModule module : station.modules()) {
                if (module.isOnline()) capacity = Math.max(capacity, limit(module.type(), 1));
            }
        }
        for (var facility : state.industrialFacilities()) {
            if (ownerId.equals(facility.ownerEntityId()) && facility.tier() > 0
                    && ShipyardWorkCapacity.SURFACE_SHIPYARD_APPLICATION_ID.equals(facility.applicationId()))
                capacity = Math.max(capacity, limit(facility.applicationId(), facility.tier()));
        }
        return capacity;
    }

    public static int requiredComplexity(ShipDesign design) {
        if (design == null) return 0;
        if (design.manufacturingProfile().requiredComplexity() > 0)
            return design.manufacturingProfile().requiredComplexity();
        int complexity = design.equippedModuleIds().contains(PassengerStasis.MODULE_ID) ? 7 : 0;
        for (String id : design.equippedModuleIds()) {
            ShipModule module = PropulsionCatalog.module(id);
            if (module != null) complexity = Math.max(complexity, module.complexityLevel());
        }
        return complexity;
    }
}
