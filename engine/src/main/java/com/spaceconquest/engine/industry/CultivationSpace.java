package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.macrostructure.OrbitalStation;

/** Shared station-space reservations for installed farms, pending farms and ordinary modules. */
public final class CultivationSpace {
    private CultivationSpace() {}
    public static int targetTier(GameState state, IndustrialFacility facility) {
        return Math.max(facility.tier(), state.expansionProjects().stream().filter(p -> p.facilityId().equals(facility.id()))
                .mapToInt(FacilityExpansionProject::targetTier).max().orElse(facility.tier()));
    }
    public static double scale(GameState state, IndustrialFacility facility) {
        var recipe = IndustryRecipeCatalog.find(facility.applicationId());
        return facility.productionEquipment().parallelLines()
                * Math.pow(recipe.infrastructure().tierCapacityMultiplier(), Math.max(0, targetTier(state, facility) - 1));
    }
    public static long farmSlots(GameState state, String stationId, String excludingId) {
        long slots = 0;
        for (var facility : state.industrialFacilities()) {
            if (!facility.planetId().equals(stationId) || facility.id().equals(excludingId)) continue;
            var recipe = IndustryRecipeCatalog.find(facility.applicationId());
            if (recipe != null && recipe.farming() != null && recipe.farming().controlled() != null)
                slots += requiredSlots(state, facility);
        }
        return slots;
    }
    public static long requiredSlots(GameState state, IndustrialFacility facility) {
        var definition = IndustryRecipeCatalog.find(facility.applicationId()).farming().controlled();
        double slots = definition.stationSlotsPerLine() * scale(state, facility);
        return !Double.isFinite(slots) || slots > Integer.MAX_VALUE ? Integer.MAX_VALUE : (long) Math.ceil(slots);
    }
    public static long moduleSlots(GameState state, OrbitalStation station) {
        return station.modules().stream().mapToLong(com.spaceconquest.engine.macrostructure.StationModule::slotSize).sum() + state.constructionProjects().stream()
                .filter(p -> !p.isCompleted() && station.id().equals(p.targetStationId()) && p.plannedModule() != null)
                .mapToLong(p -> p.plannedModule().slotSize()).sum();
    }
    public static boolean permits(GameState state, OrbitalStation station, IndustrialFacility candidate) {
        var controlled = IndustryRecipeCatalog.find(candidate.applicationId()).farming().controlled();
        return controlled != null && controlled.stationSlotsPerLine() > 0 && station.isOperational()
                && (candidate.ownerEntityId().isEmpty() || canAccess(state, station.id(), candidate.ownerEntityId()))
                && requiredSlots(state, candidate) + farmSlots(state, station.id(), candidate.id()) + moduleSlots(state, station) <= station.totalSlots();
    }
    public static boolean canAccess(GameState state, String body, String owner) {
        var station = state.orbitalStations().stream().filter(s -> s.id().equals(body)).findFirst().orElse(null);
        return station == null || affiliation(state, station.ownerEntityId()).equals(affiliation(state, owner));
    }
    private static String affiliation(GameState state, String owner) {
        return state.corporations().stream().filter(c -> c.id().equals(owner)).map(com.spaceconquest.engine.Corporation::empireId)
                .findFirst().orElse(owner);
    }
}
