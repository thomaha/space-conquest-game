package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.macrostructure.*;
import java.util.List;
import java.util.Map;

/** Provisional staffed base workshop: construction is researched and work is limited per station. */
public final class BaseRepairCatalog {
    public static final double POINTS_PER_DAY = 50;
    public static final double LABOR_CREDITS_PER_POINT = 2;
    private BaseRepairCatalog() {}
    public static StationModule module() {
        return new StationModule("repair_workshop", "Base repair workshop", StationModule.TYPE_REPAIR_WORKSHOP,
                4, 5000, 50, 0, Map.of(), "engineer", 2, true);
    }
    public static boolean researched(String type, List<String> technologies) {
        return !StationModule.TYPE_REPAIR_WORKSHOP.equals(type)
                || technologies.containsAll(List.of("electricity", "industrial_production", "space_stations"));
    }
    public static double capacity(GameState state, OrbitalStation station) {
        if (!station.isOperational() || station.currentHullHealth() <= 0
                || station.currentPowerGenerationKw() + 1e-9 < station.currentPowerDemandKw()) return 0;
        return station.modules().stream().filter(module -> StationModule.TYPE_REPAIR_WORKSHOP.equals(module.type()) && module.isOnline())
                .mapToDouble(module -> POINTS_PER_DAY * Math.min(1, (double) module.paidWorkers()
                        / Math.max(1, ShipyardWorkCapacity.requiredWorkers(state, station.ownerEntityId(), module)))).sum();
    }
    public static boolean accessible(GameState state, OrbitalStation station, String owner) {
        if (owner.equals(station.ownerEntityId())) return true;
        String buyer = empire(state, owner), provider = empire(state, station.ownerEntityId());
        if (buyer.equals(provider)) return true;
        return state.diplomaticRelations().stream().anyMatch(relation -> relation.tier() != null
                && (relation.tier().contains("ALLIANCE") || relation.tier().contains("FEDERATION"))
                && (buyer.equals(relation.empireAId()) && provider.equals(relation.empireBId())
                || buyer.equals(relation.empireBId()) && provider.equals(relation.empireAId())));
    }
    private static String empire(GameState state, String owner) {
        return state.corporations().stream().filter(corporation -> owner.equals(corporation.id()))
                .map(com.spaceconquest.engine.Corporation::empireId).findFirst().orElse(owner);
    }
}
