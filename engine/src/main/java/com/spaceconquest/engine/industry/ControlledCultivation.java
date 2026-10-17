package com.spaceconquest.engine.industry;

import java.util.List;
import java.util.Map;

/** Application-owned enclosure, utility requirements and internal water recycling. */
public record ControlledCultivation(double footprintHectaresPerLine, double enclosedVolumeM3PerLine,
                                    int stationSlotsPerLine, List<String> allowedBodyTypes,
                                    String waterMaterialId, double workingWaterKgPerLine,
                                    double circulatedWaterKgPerBatch, double waterRecoveryFraction,
                                    Map<String, Double> energyKwhPerBatch, Map<String, Double> environmentalLossesKg) {
    public ControlledCultivation {
        allowedBodyTypes = List.copyOf(allowedBodyTypes);
        energyKwhPerBatch = Map.copyOf(energyKwhPerBatch);
        environmentalLossesKg = Map.copyOf(environmentalLossesKg);
        for (double amount : new double[]{footprintHectaresPerLine, enclosedVolumeM3PerLine, workingWaterKgPerLine, circulatedWaterKgPerBatch})
            if (!Double.isFinite(amount) || amount <= 0) throw new IllegalArgumentException("Invalid enclosed cultivation capacity");
        if (stationSlotsPerLine < 0 || waterMaterialId == null || waterMaterialId.isBlank()
                || !Double.isFinite(waterRecoveryFraction) || waterRecoveryFraction < 0 || waterRecoveryFraction >= 1
                || energyKwhPerBatch.isEmpty() || allowedBodyTypes.isEmpty())
            throw new IllegalArgumentException("Invalid controlled environment definition");
        for (var amounts : List.of(energyKwhPerBatch, environmentalLossesKg))
            amounts.forEach((id, kg) -> { if (id == null || id.isBlank() || !Double.isFinite(kg) || kg <= 0)
                throw new IllegalArgumentException("Invalid cultivation utility or loss"); });
    }
    public double makeupWaterKgPerBatch() { return circulatedWaterKgPerBatch * (1 - waterRecoveryFraction); }

    public void validateRecipe(IndustryRecipeCatalog.Recipe recipe) {
        double energy = energyKwhPerBatch.values().stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(energy - recipe.work().energyKwhPerBatch()) > 1e-6
                || Math.abs(recipe.inputsKg().getOrDefault(waterMaterialId, 0.0) - makeupWaterKgPerBatch()) > 1e-6
                || recipe.infrastructure().constructionMaterialsKg().getOrDefault(waterMaterialId, 0.0) < workingWaterKgPerLine
                || recipe.inputSources().stream().anyMatch(i -> i.source() != ProcessInput.Source.MARKET))
            throw new IllegalArgumentException("Enclosure utility requirements disagree with recipe: " + recipe.applicationId());
    }
}
