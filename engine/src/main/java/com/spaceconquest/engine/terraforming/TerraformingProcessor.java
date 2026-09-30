package com.spaceconquest.engine.terraforming;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.ConstructionMaterials;
import com.spaceconquest.engine.industry.ConstructionProgress;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simulates planetary atmospheric modifications, geoengineering macro-projects
 * and biological terraforming succession cycles.
 */
public class TerraformingProcessor {

    /** Applies only the daily terraforming work supported by purchased inputs. */
    public GameState processConstruction(GameState state) {
        GameState current = state;
        List<GeoengineeringProject> updated = new ArrayList<>();
        for (GeoengineeringProject project : state.terraformingProjects()) {
            if (project.isCompleted()) {
                updated.add(project);
                continue;
            }
            String systemId = ConstructionMaterials.systemForBody(current, project.planetId());
            String body = ConstructionMaterials.bodyForSystem(current, systemId, project.planetId());
            ConstructionProgress.Step step = ConstructionProgress.advance(current, body,
                    project.ownerEmpireId(), project.requiredMaterialsKg(),
                    project.consumedMaterialsKg(), project.accumulatedProgress(),
                    project.requiredProgress(), 1.0);
            current = step.state();
            GeoengineeringProject supplied = new GeoengineeringProject(project.id(),
                    project.planetId(), project.ownerEmpireId(), project.projectType(),
                    project.accumulatedProgress(), project.requiredProgress(),
                    project.targetPressureAtm(), project.targetTemperatureK(),
                    project.targetGasRatios(), project.requiredMaterialsKg(),
                    step.consumedKg(), false);
            AtmosphericComposition atmosphere = new AtmosphericComposition(project.planetId(),
                    Map.of("oxygen_gas", 0.05, "nitrogen_gas", 0.60,
                            "carbon_dioxide", 0.25, "toxic_aerosols", 0.10),
                    project.targetPressureAtm() > 0 ? project.targetPressureAtm() * 0.8 : 0.5,
                    project.targetTemperatureK() > 0 ? project.targetTemperatureK() * 0.9 : 250.0,
                    1.2, 25.0, AtmosphericComposition.BIOME_BARREN, false);
            double work = Math.max(0.0, step.workHours() - project.accumulatedProgress());
            updated.addAll(processPlanetTerraforming(atmosphere, List.of(supplied),
                    Map.of(project.id(), work)).updatedProjects());
        }
        return current.withTerraformingProjects(updated);
    }

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
        return processPlanetTerraforming(atmosphere, projects, Map.of());
    }

    public TerraformingTurnResult processPlanetTerraforming(
            AtmosphericComposition atmosphere, List<GeoengineeringProject> projects,
            Map<String, Double> workByProject
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

        List<GeoengineeringProject> remainingProjects = processProjects(projects, currentGases,
                climate, workByProject);
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
            MutableClimate climate,
            Map<String, Double> workByProject
    ) {
        List<GeoengineeringProject> remainingProjects = new ArrayList<>();
        for (GeoengineeringProject proj : projects) {
            if (proj.isCompleted()) {
                remainingProjects.add(proj);
                continue;
            }

            double work = Math.clamp(workByProject.getOrDefault(proj.id(), 1.0), 0.0, 1.0);
            double nextProgress = Math.min(proj.requiredProgress(), proj.accumulatedProgress() + work);
            boolean isNowCompleted = nextProgress >= proj.requiredProgress();

            if (work > 0.0) applyProjectEffect(proj, currentGases, climate, work);

            remainingProjects.add(new GeoengineeringProject(
                    proj.id(), proj.planetId(), proj.ownerEmpireId(),
                    proj.projectType(), nextProgress, proj.requiredProgress(),
                    proj.targetPressureAtm(), proj.targetTemperatureK(),
                    proj.targetGasRatios(), proj.requiredMaterialsKg(),
                    proj.consumedMaterialsKg(), isNowCompleted
            ));
        }
        return remainingProjects;
    }

    private void applyProjectEffect(GeoengineeringProject proj, Map<String, Double> gases,
                                    MutableClimate climate, double work) {
        switch (proj.projectType()) {
            case GeoengineeringProject.TYPE_SOLAR_MIRROR -> {
                if (climate.tempK < proj.targetTemperatureK()) {
                    climate.tempK = Math.min(proj.targetTemperatureK(), climate.tempK + 2.0 * work);
                }
            }
            case GeoengineeringProject.TYPE_SOLAR_SHADE -> {
                if (climate.tempK > proj.targetTemperatureK()) {
                    climate.tempK = Math.max(proj.targetTemperatureK(), climate.tempK - 2.0 * work);
                }
            }
            case GeoengineeringProject.TYPE_GREENHOUSE_FACTORY -> {
                climate.greenhouse = Math.min(2.5, climate.greenhouse + 0.05 * work);
                climate.pressure = Math.min(proj.targetPressureAtm(), climate.pressure + 0.02 * work);
                climate.tempK += 1.5 * work;
            }
            case GeoengineeringProject.TYPE_CARBON_SEQUESTRATION -> applyCarbonSequestration(proj, gases, climate, work);
            case GeoengineeringProject.TYPE_MAGNETIC_FIELD_GENERATOR -> climate.radiation = Math.max(2.0, climate.radiation - 10.0 * work);
            case GeoengineeringProject.TYPE_CYANOBACTERIA_SEEDING -> applyCyanobacteria(gases, work);
            case GeoengineeringProject.TYPE_EXTREMOPHILE_ALGAE_SEEDING -> applyExtremophileAlgae(gases, work);
            case GeoengineeringProject.TYPE_LICHEN_SOIL_SEEDING -> applyLichenSeeding(proj, gases, climate, work);
        }
    }

    private void applyCarbonSequestration(GeoengineeringProject proj, Map<String, Double> gases,
                                          MutableClimate climate, double work) {
        double co2 = gases.getOrDefault("carbon_dioxide", 0.0);
        if (co2 > 0.01) {
            double removed = Math.min(0.02 * work, co2 - 0.005);
            gases.put("carbon_dioxide", Math.max(0.005, co2 - removed));
            climate.greenhouse = Math.max(1.0, climate.greenhouse - 0.04 * work);
            climate.tempK = Math.max(proj.targetTemperatureK(), climate.tempK - work);
        }
    }

    private void applyCyanobacteria(Map<String, Double> gases, double work) {
        double co2 = gases.getOrDefault("carbon_dioxide", 0.0);
        double o2 = gases.getOrDefault("oxygen_gas", 0.0);
        double n2 = gases.getOrDefault("nitrogen_gas", 0.0);
        if (co2 > 0.02) {
            gases.put("carbon_dioxide", Math.max(0.01, co2 - 0.02 * work));
            gases.put("oxygen_gas", Math.min(0.22, o2 + 0.02 * work));
            if (n2 < 0.70) {
                gases.put("nitrogen_gas", Math.min(0.78, n2 + 0.01 * work));
            }
        }
    }

    private void applyExtremophileAlgae(Map<String, Double> gases, double work) {
        double toxic = gases.getOrDefault("toxic_aerosols", 0.0);
        double o2 = gases.getOrDefault("oxygen_gas", 0.0);
        if (toxic > 0.0) {
            gases.put("toxic_aerosols", Math.max(0.0, toxic - 0.03 * work));
            gases.put("oxygen_gas", Math.min(0.21, o2 + 0.015 * work));
        }
    }

    private void applyLichenSeeding(GeoengineeringProject proj, Map<String, Double> gases,
                                     MutableClimate climate, double work) {
        double n2 = gases.getOrDefault("nitrogen_gas", 0.0);
        gases.put("nitrogen_gas", Math.min(0.78, n2 + 0.02 * work));
        if (climate.pressure < proj.targetPressureAtm()) {
            climate.pressure = Math.min(proj.targetPressureAtm(), climate.pressure + 0.01 * work);
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
