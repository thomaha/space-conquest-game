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

    private static class MutableClimate {
        double pressure;
        double tempK;
        double greenhouse;
        double radiation;

        MutableClimate(double pressure, double tempK, double greenhouse, double radiation) {
            this.pressure = pressure;
            this.tempK = tempK;
            this.greenhouse = greenhouse;
            this.radiation = radiation;
        }
    }

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
        MutableClimate climate = new MutableClimate(
                atmosphere.surfacePressureAtm(),
                atmosphere.surfaceTemperatureK(),
                atmosphere.greenhouseFactor(),
                atmosphere.radiationLevelRad()
        );
        String currentBiome = atmosphere.biomeType();

        List<GeoengineeringProject> remainingProjects = processProjects(projects, currentGases, climate);
        normalizeGasFractions(currentGases);

        boolean isBreathable = evaluateBreathability(currentGases, climate);
        String evaluatedBiome = evaluateBiomeType(currentGases, climate, currentBiome, isBreathable);
        boolean biomeTransformed = !evaluatedBiome.equalsIgnoreCase(currentBiome);

        AtmosphericComposition updatedAtmosphere = new AtmosphericComposition(
                atmosphere.planetId(),
                currentGases,
                Math.round(climate.pressure * 100.0) / 100.0,
                Math.round(climate.tempK * 10.0) / 10.0,
                Math.round(climate.greenhouse * 100.0) / 100.0,
                Math.round(climate.radiation * 10.0) / 10.0,
                evaluatedBiome,
                isBreathable
        );

        return new TerraformingTurnResult(updatedAtmosphere, remainingProjects, biomeTransformed, currentBiome, evaluatedBiome);
    }

    private List<GeoengineeringProject> processProjects(
            List<GeoengineeringProject> projects,
            Map<String, Double> currentGases,
            MutableClimate climate
    ) {
        List<GeoengineeringProject> remainingProjects = new ArrayList<>();
        for (GeoengineeringProject proj : projects) {
            if (proj.isCompleted()) {
                remainingProjects.add(proj);
                continue;
            }

            double nextProgress = proj.accumulatedProgress() + 1.0;
            boolean isNowCompleted = nextProgress >= proj.requiredProgress();

            applyProjectEffect(proj, currentGases, climate);

            remainingProjects.add(new GeoengineeringProject(
                    proj.id(), proj.planetId(), proj.ownerEmpireId(),
                    proj.projectType(), nextProgress, proj.requiredProgress(),
                    proj.targetPressureAtm(), proj.targetTemperatureK(),
                    proj.targetGasRatios(), isNowCompleted
            ));
        }
        return remainingProjects;
    }

    private void applyProjectEffect(GeoengineeringProject proj, Map<String, Double> gases, MutableClimate climate) {
        switch (proj.projectType()) {
            case GeoengineeringProject.TYPE_SOLAR_MIRROR -> {
                if (climate.tempK < proj.targetTemperatureK()) {
                    climate.tempK = Math.min(proj.targetTemperatureK(), climate.tempK + 2.0);
                }
            }
            case GeoengineeringProject.TYPE_SOLAR_SHADE -> {
                if (climate.tempK > proj.targetTemperatureK()) {
                    climate.tempK = Math.max(proj.targetTemperatureK(), climate.tempK - 2.0);
                }
            }
            case GeoengineeringProject.TYPE_GREENHOUSE_FACTORY -> {
                climate.greenhouse = Math.min(2.5, climate.greenhouse + 0.05);
                climate.pressure = Math.min(proj.targetPressureAtm(), climate.pressure + 0.02);
                climate.tempK += 1.5;
            }
            case GeoengineeringProject.TYPE_CARBON_SEQUESTRATION -> applyCarbonSequestration(proj, gases, climate);
            case GeoengineeringProject.TYPE_MAGNETIC_FIELD_GENERATOR -> climate.radiation = Math.max(2.0, climate.radiation - 10.0);
            case GeoengineeringProject.TYPE_CYANOBACTERIA_SEEDING -> applyCyanobacteria(gases);
            case GeoengineeringProject.TYPE_EXTREMOPHILE_ALGAE_SEEDING -> applyExtremophileAlgae(gases);
            case GeoengineeringProject.TYPE_LICHEN_SOIL_SEEDING -> applyLichenSeeding(proj, gases, climate);
        }
    }

    private void applyCarbonSequestration(GeoengineeringProject proj, Map<String, Double> gases, MutableClimate climate) {
        double co2 = gases.getOrDefault("carbon_dioxide", 0.0);
        if (co2 > 0.01) {
            double removed = Math.min(0.02, co2 - 0.005);
            gases.put("carbon_dioxide", Math.max(0.005, co2 - removed));
            climate.greenhouse = Math.max(1.0, climate.greenhouse - 0.04);
            climate.tempK = Math.max(proj.targetTemperatureK(), climate.tempK - 1.0);
        }
    }

    private void applyCyanobacteria(Map<String, Double> gases) {
        double co2 = gases.getOrDefault("carbon_dioxide", 0.0);
        double o2 = gases.getOrDefault("oxygen_gas", 0.0);
        double n2 = gases.getOrDefault("nitrogen_gas", 0.0);
        if (co2 > 0.02) {
            gases.put("carbon_dioxide", Math.max(0.01, co2 - 0.02));
            gases.put("oxygen_gas", Math.min(0.22, o2 + 0.02));
            if (n2 < 0.70) {
                gases.put("nitrogen_gas", Math.min(0.78, n2 + 0.01));
            }
        }
    }

    private void applyExtremophileAlgae(Map<String, Double> gases) {
        double toxic = gases.getOrDefault("toxic_aerosols", 0.0);
        double o2 = gases.getOrDefault("oxygen_gas", 0.0);
        if (toxic > 0.0) {
            gases.put("toxic_aerosols", Math.max(0.0, toxic - 0.03));
            gases.put("oxygen_gas", Math.min(0.21, o2 + 0.015));
        }
    }

    private void applyLichenSeeding(GeoengineeringProject proj, Map<String, Double> gases, MutableClimate climate) {
        double n2 = gases.getOrDefault("nitrogen_gas", 0.0);
        gases.put("nitrogen_gas", Math.min(0.78, n2 + 0.02));
        if (climate.pressure < proj.targetPressureAtm()) {
            climate.pressure = Math.min(proj.targetPressureAtm(), climate.pressure + 0.01);
        }
    }

    private void normalizeGasFractions(Map<String, Double> gases) {
        double totalGas = gases.values().stream().mapToDouble(Double::doubleValue).sum();
        if (totalGas > 0.0) {
            for (Map.Entry<String, Double> e : gases.entrySet()) {
                gases.put(e.getKey(), Math.round((e.getValue() / totalGas) * 1000.0) / 1000.0);
            }
        }
    }

    private boolean evaluateBreathability(Map<String, Double> gases, MutableClimate climate) {
        double oxygenRatio = gases.getOrDefault("oxygen_gas", 0.0);
        double co2Ratio = gases.getOrDefault("carbon_dioxide", 0.0);
        double toxicRatio = gases.getOrDefault("toxic_aerosols", 0.0);

        return oxygenRatio >= 0.17 && oxygenRatio <= 0.26
                && co2Ratio <= 0.05
                && toxicRatio <= 0.01
                && climate.pressure >= 0.7 && climate.pressure <= 1.6
                && climate.tempK >= 265.0 && climate.tempK <= 315.0
                && climate.radiation <= 15.0;
    }

    private String evaluateBiomeType(Map<String, Double> gases, MutableClimate climate, String currentBiome, boolean isBreathable) {
        if (isBreathable) {
            return AtmosphericComposition.BIOME_BREATHABLE_TERRESTRIAL;
        }
        double toxicRatio = gases.getOrDefault("toxic_aerosols", 0.0);
        if (toxicRatio > 0.05) {
            return AtmosphericComposition.BIOME_TOXIC;
        } else if (climate.tempK < 245.0) {
            return AtmosphericComposition.BIOME_FROZEN;
        } else if (climate.tempK > 335.0 || climate.greenhouse > 1.8) {
            return AtmosphericComposition.BIOME_GREENHOUSE;
        } else if (climate.pressure < 0.3) {
            return AtmosphericComposition.BIOME_BARREN;
        }
        return currentBiome;
    }
}
