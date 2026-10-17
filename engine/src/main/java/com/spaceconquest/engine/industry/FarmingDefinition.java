package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.GameState;
import java.util.List;

/** Application-owned biological production on renewable land, separate from factory processing. */
public record FarmingDefinition(double hectaresPerLine, int growingDays, double usableLandFraction,
                                boolean requiresLiquidWater, List<String> allowedAtmospheres,
                                FactoryDefinition infrastructure, ControlledCultivation controlled) {
    public FarmingDefinition(double hectaresPerLine, int growingDays, double usableLandFraction,
                             boolean requiresLiquidWater, List<String> allowedAtmospheres, FactoryDefinition infrastructure) {
        this(hectaresPerLine, growingDays, usableLandFraction, requiresLiquidWater, allowedAtmospheres, infrastructure, null);
    }
    public FarmingDefinition {
        allowedAtmospheres = List.copyOf(allowedAtmospheres);
        if (!Double.isFinite(hectaresPerLine) || hectaresPerLine <= 0 || growingDays <= 0
                || !Double.isFinite(usableLandFraction) || usableLandFraction <= 0 || usableLandFraction > 1
                || controlled == null && allowedAtmospheres.isEmpty() || infrastructure == null)
            throw new IllegalArgumentException("Invalid cultivation definition");
    }

    public double availableHectares(GameState state, String body) {
        for (var system : state.solarSystems()) for (var planet : system.planets()) {
            if (planet.id().equals(body)) return controlled != null && !controlled.allowedBodyTypes().contains(planet.type()) ? 0
                    : land(planet.diameter(), planet.waterLevel(), planet.hasLiquidWater(), planet.atmosphere());
            for (var moon : planet.moons()) if (moon.id().equals(body))
                return controlled != null && !controlled.allowedBodyTypes().contains("moon") ? 0
                        : land(moon.diameter(), moon.waterLevel(), moon.hasLiquidWater(), moon.atmosphere());
        }
        return 0;
    }

    private double land(double diameterKm, double waterLevel, boolean liquidWater, String atmosphere) {
        if (requiresLiquidWater && !liquidWater || controlled == null && !allowedAtmospheres.contains(atmosphere)
                || !Double.isFinite(diameterKm) || diameterKm <= 0 || !Double.isFinite(waterLevel)) return 0;
        return Math.PI * diameterKm * diameterKm * 100 * (1 - Math.clamp(waterLevel, 0, 1)) * usableLandFraction;
    }

    public double requiredHectares(IndustrialFacility facility) {
        return hectaresPerLine * facility.productionEquipment().parallelLines()
                * Math.pow(infrastructure.tierCapacityMultiplier(), Math.max(0, facility.tier() - 1));
    }

    private double reservedHectares(GameState state, IndustrialFacility facility) {
        return (controlled == null ? hectaresPerLine : controlled.footprintHectaresPerLine()) * CultivationSpace.scale(state, facility);
    }

    public static boolean canInstall(GameState state, String application, String body, String id, int tier,
                                     ProductionEquipment equipment) {
        var recipe = IndustryRecipeCatalog.find(application);
        if (recipe == null || recipe.farming() == null) return true;
        return recipe.farming().permits(state, new IndustrialFacility(id, body, application, "", "", tier,
                0, recipe.work().equipment().operatorProfession(), false, 0, equipment));
    }

    /** Existing farms and pending construction reserve land before any daily work is quoted. */
    public boolean permits(GameState state, IndustrialFacility candidate) {
        var station = state.orbitalStations().stream().filter(s -> s.id().equals(candidate.planetId())).findFirst().orElse(null);
        if (station != null) return controlled != null && infrastructure.permitsSite(state, station.id())
                && CultivationSpace.permits(state, station, candidate);
        double available = availableHectares(state, candidate.planetId());
        if (available <= 0 || !infrastructure.permitsSite(state, candidate.planetId())) return false;
        double occupiedFraction = reservedHectares(state, candidate) / available;
        for (var other : state.industrialFacilities()) {
            if (other.id().equals(candidate.id()) || !other.planetId().equals(candidate.planetId())) continue;
            var recipe = IndustryRecipeCatalog.find(other.applicationId());
            if (recipe == null || recipe.farming() == null) continue;
            double otherAvailable = recipe.farming().availableHectares(state, other.planetId());
            if (otherAvailable > 0) occupiedFraction += recipe.farming().reservedHectares(state, other) / otherAvailable;
        }
        return occupiedFraction <= 1 + 1e-10;
    }
}
