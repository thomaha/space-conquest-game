package com.spaceconquest.engine.industry;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Executes stochastic geological prospecting discovery rolls and manages subterranean mineral extraction.
 */
public class ProspectingProcessor {

    public static final double DEFAULT_SCARCITY_EXPONENT_ALPHA = 1.5;
    public static final double DEFAULT_PLANET_ESTIMATED_VOLUME_KG = 10_000_000.0;

    private final Random random;

    public ProspectingProcessor() {
        this(new Random());
    }

    public ProspectingProcessor(Random random) {
        this.random = random;
    }

    /**
     * Calculates the probability of uncovering a subterranean mineral deposit on a planet.
     * Formula: Base Tech Efficiency * Staff Training Modifier * (1.0 - (Sum Discovered Volumes / Planet Volume))^alpha
     */
    public double calculateDiscoveryProbability(
            double baseTechEfficiency,
            double staffTrainingModifier,
            List<GeologicalDeposit> depositsOnPlanet,
            double totalPlanetVolumeKg,
            double alpha
    ) {
        double techEff = Math.max(0.05, Math.min(1.0, baseTechEfficiency));
        double staffMod = Math.max(0.1, staffTrainingModifier);
        double planetVolume = (totalPlanetVolumeKg <= 0.0) ? DEFAULT_PLANET_ESTIMATED_VOLUME_KG : totalPlanetVolumeKg;
        double expAlpha = (alpha <= 0.0) ? DEFAULT_SCARCITY_EXPONENT_ALPHA : alpha;

        double sumDiscoveredVolumes = 0.0;
        if (depositsOnPlanet != null) {
            for (GeologicalDeposit dep : depositsOnPlanet) {
                if (dep.isDiscovered()) {
                    sumDiscoveredVolumes += dep.initialVolumeKg();
                }
            }
        }

        double depletionRatio = Math.min(0.99, sumDiscoveredVolumes / planetVolume);
        double scarcityFactor = Math.pow(Math.max(0.01, 1.0 - depletionRatio), expAlpha);

        return Math.max(0.01, Math.min(1.0, techEff * staffMod * scarcityFactor));
    }

    /**
     * Executes a prospecting survey mission on a planetary body.
     */
    public List<GeologicalDeposit> executeProspectingSurvey(
            String planetId,
            String surveyorEntityId,
            double baseTechEfficiency,
            double staffTrainingModifier,
            List<GeologicalDeposit> allDeposits
    ) {
        List<GeologicalDeposit> planetDeposits = new ArrayList<>();
        List<GeologicalDeposit> otherDeposits = new ArrayList<>();

        for (GeologicalDeposit dep : allDeposits) {
            if (dep.planetId().equals(planetId)) {
                planetDeposits.add(dep);
            } else {
                otherDeposits.add(dep);
            }
        }

        double pDiscover = calculateDiscoveryProbability(
                baseTechEfficiency,
                staffTrainingModifier,
                planetDeposits,
                DEFAULT_PLANET_ESTIMATED_VOLUME_KG,
                DEFAULT_SCARCITY_EXPONENT_ALPHA
        );

        boolean discoveredNew = random.nextDouble() < pDiscover;

        if (discoveredNew) {
            boolean uncoveredExisting = false;
            List<GeologicalDeposit> updatedPlanetDeposits = new ArrayList<>();

            for (GeologicalDeposit dep : planetDeposits) {
                if (!dep.isDiscovered() && !uncoveredExisting) {
                    updatedPlanetDeposits.add(new GeologicalDeposit(
                            dep.id(),
                            dep.planetId(),
                            dep.materialId(),
                            dep.initialVolumeKg(),
                            dep.remainingVolumeKg(),
                            dep.concentrationModifier(),
                            true,
                            surveyorEntityId
                    ));
                    uncoveredExisting = true;
                } else {
                    updatedPlanetDeposits.add(dep);
                }
            }

            if (!uncoveredExisting) {
                // Procedurally generate a new discovered deposit
                String newId = "dep_" + UUID.randomUUID().toString().substring(0, 8);
                updatedPlanetDeposits.add(new GeologicalDeposit(
                        newId,
                        planetId,
                        "refined_iron",
                        500_000.0,
                        500_000.0,
                        1.25,
                        true,
                        surveyorEntityId
                ));
            }

            planetDeposits = updatedPlanetDeposits;
        }

        List<GeologicalDeposit> combined = new ArrayList<>(otherDeposits);
        combined.addAll(planetDeposits);
        return combined;
    }

    /**
     * Calculates work hours and electricity costs for crust-average background mining without discovered veins.
     */
    public BackgroundMiningCost calculateBackgroundMiningCost(double requestedKg, double elementRarityModifier) {
        double rarity = Math.max(0.05, elementRarityModifier);
        double inverseRarity = 1.0 / rarity;
        double workHours = requestedKg * inverseRarity * 0.1;
        double electricityKw = requestedKg * inverseRarity * 0.5;
        return new BackgroundMiningCost(workHours, electricityKw);
    }

    public record BackgroundMiningCost(double requiredWorkHours, double electricityKw) {}
}
