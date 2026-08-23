package com.spaceconquest.engine.terraforming;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simulates planetary atmospheric modifications, geoengineering macro-projects
 * and biological terraforming succession cycles.
 */
public class TerraformingProcessor {

    public record TerraformingTurnResult(
            AtmosphericComposition updatedAtmosphere,
            List<GeoengineeringProject> updatedProjects,
            boolean biomeTransformed,
            String previousBiome,
            String newBiome
    ) {}

    /**
     * Advances geoengineering projects and calculates ecological and atmospheric shifts on a celestial body.
     */
    public TerraformingTurnResult processPlanetTerraforming(
            AtmosphericComposition atmosphere,
            List<GeoengineeringProject> projects
    ) {
        if (atmosphere == null) {
            return new TerraformingTurnResult(null, List.of(), false, "", "");
        }
        if (projects == null || projects.isEmpty()) {
            return new TerraformingTurnResult(atmosphere, List.of(), false, atmosphere.biomeType(), atmosphere.biomeType());
        }

        Map<String, Double> currentGases = new HashMap<>(atmosphere.gasRatios());
        double currentPressure = atmosphere.surfacePressureAtm();
        double currentTempK = atmosphere.surfaceTemperatureK();
        double currentGreenhouse = atmosphere.greenhouseFactor();
        double currentRadiation = atmosphere.radiationLevelRad();
        String currentBiome = atmosphere.biomeType();

        List<GeoengineeringProject> remainingProjects = new ArrayList<>();

        for (GeoengineeringProject proj : projects) {
            if (proj.isCompleted()) {
                remainingProjects.add(proj);
                continue;
            }

            double progressIncrement = 1.0;
            double nextProgress = proj.accumulatedProgress() + progressIncrement;
            boolean isNowCompleted = nextProgress >= proj.requiredProgress();

            switch (proj.projectType()) {
                case GeoengineeringProject.TYPE_SOLAR_MIRROR -> {
                    // Warms frozen celestial bodies towards target
                    if (currentTempK < proj.targetTemperatureK()) {
                        currentTempK = Math.min(proj.targetTemperatureK(), currentTempK + 2.0);
                    }
                }
                case GeoengineeringProject.TYPE_SOLAR_SHADE -> {
                    // Cools superheated inner bodies towards target
                    if (currentTempK > proj.targetTemperatureK()) {
                        currentTempK = Math.max(proj.targetTemperatureK(), currentTempK - 2.0);
                    }
                }
                case GeoengineeringProject.TYPE_GREENHOUSE_FACTORY -> {
                    // Releases dense warming gases, elevating surface pressure and heat retention
                    currentGreenhouse = Math.min(2.5, currentGreenhouse + 0.05);
                    currentPressure = Math.min(proj.targetPressureAtm(), currentPressure + 0.02);
                    currentTempK += 1.5;
                }
                case GeoengineeringProject.TYPE_CARBON_SEQUESTRATION -> {
                    // Scrubs greenhouse CO2 from atmosphere
                    double co2 = currentGases.getOrDefault("carbon_dioxide", 0.0);
                    if (co2 > 0.01) {
                        double removed = Math.min(0.02, co2 - 0.005);
                        currentGases.put("carbon_dioxide", Math.max(0.005, co2 - removed));
                        currentGreenhouse = Math.max(1.0, currentGreenhouse - 0.04);
                        currentTempK = Math.max(proj.targetTemperatureK(), currentTempK - 1.0);
                    }
                }
                case GeoengineeringProject.TYPE_MAGNETIC_FIELD_GENERATOR -> {
                    // Shields planet from stellar winds and reduces surface radiation
                    currentRadiation = Math.max(2.0, currentRadiation - 10.0);
                }
                case GeoengineeringProject.TYPE_CYANOBACTERIA_SEEDING -> {
                    // Converts CO2 into breathable oxygen
                    double co2 = currentGases.getOrDefault("carbon_dioxide", 0.0);
                    double o2 = currentGases.getOrDefault("oxygen_gas", 0.0);
                    double n2 = currentGases.getOrDefault("nitrogen_gas", 0.0);
                    if (co2 > 0.02) {
                        currentGases.put("carbon_dioxide", Math.max(0.01, co2 - 0.02));
                        currentGases.put("oxygen_gas", Math.min(0.22, o2 + 0.02));
                        if (n2 < 0.70) {
                            currentGases.put("nitrogen_gas", Math.min(0.78, n2 + 0.01));
                        }
                    }
                }
                case GeoengineeringProject.TYPE_EXTREMOPHILE_ALGAE_SEEDING -> {
                    // Cleans toxic aerosols and generates foundational organic atmospheric vapor
                    double toxic = currentGases.getOrDefault("toxic_aerosols", 0.0);
                    double o2 = currentGases.getOrDefault("oxygen_gas", 0.0);
                    if (toxic > 0.0) {
                        currentGases.put("toxic_aerosols", Math.max(0.0, toxic - 0.03));
                        currentGases.put("oxygen_gas", Math.min(0.21, o2 + 0.015));
                    }
                }
                case GeoengineeringProject.TYPE_LICHEN_SOIL_SEEDING -> {
                    // Stabilizes atmospheric nitrogen and prepares fertile hydrosphere
                    double n2 = currentGases.getOrDefault("nitrogen_gas", 0.0);
                    currentGases.put("nitrogen_gas", Math.min(0.78, n2 + 0.02));
                    if (currentPressure < proj.targetPressureAtm()) {
                        currentPressure = Math.min(proj.targetPressureAtm(), currentPressure + 0.01);
                    }
                }
            }

            remainingProjects.add(new GeoengineeringProject(
                    proj.id(), proj.planetId(), proj.ownerEmpireId(),
                    proj.projectType(), nextProgress, proj.requiredProgress(),
                    proj.targetPressureAtm(), proj.targetTemperatureK(),
                    proj.targetGasRatios(), isNowCompleted
            ));
        }

        // Normalize gas fractions so sum equals 1.0 (if non-empty)
        double totalGas = currentGases.values().stream().mapToDouble(Double::doubleValue).sum();
        if (totalGas > 0.0) {
            for (Map.Entry<String, Double> e : currentGases.entrySet()) {
                currentGases.put(e.getKey(), Math.round((e.getValue() / totalGas) * 1000.0) / 1000.0);
            }
        }

        // Determine updated biome classification and breathability
        double oxygenRatio = currentGases.getOrDefault("oxygen_gas", 0.0);
        double co2Ratio = currentGases.getOrDefault("carbon_dioxide", 0.0);
        double toxicRatio = currentGases.getOrDefault("toxic_aerosols", 0.0);

        boolean isBreathable = oxygenRatio >= 0.17 && oxygenRatio <= 0.26
                && co2Ratio <= 0.05
                && toxicRatio <= 0.01
                && currentPressure >= 0.7 && currentPressure <= 1.6
                && currentTempK >= 265.0 && currentTempK <= 315.0
                && currentRadiation <= 15.0;

        String evaluatedBiome = currentBiome;
        if (isBreathable) {
            evaluatedBiome = AtmosphericComposition.BIOME_BREATHABLE_TERRESTRIAL;
        } else if (toxicRatio > 0.05) {
            evaluatedBiome = AtmosphericComposition.BIOME_TOXIC;
        } else if (currentTempK < 245.0) {
            evaluatedBiome = AtmosphericComposition.BIOME_FROZEN;
        } else if (currentTempK > 335.0 || currentGreenhouse > 1.8) {
            evaluatedBiome = AtmosphericComposition.BIOME_GREENHOUSE;
        } else if (currentPressure < 0.3) {
            evaluatedBiome = AtmosphericComposition.BIOME_BARREN;
        }

        boolean biomeTransformed = !evaluatedBiome.equalsIgnoreCase(currentBiome);

        AtmosphericComposition updatedAtmosphere = new AtmosphericComposition(
                atmosphere.planetId(),
                currentGases,
                Math.round(currentPressure * 100.0) / 100.0,
                Math.round(currentTempK * 10.0) / 10.0,
                Math.round(currentGreenhouse * 100.0) / 100.0,
                Math.round(currentRadiation * 10.0) / 10.0,
                evaluatedBiome,
                isBreathable
        );

        return new TerraformingTurnResult(updatedAtmosphere, remainingProjects, biomeTransformed, currentBiome, evaluatedBiome);
    }
}
