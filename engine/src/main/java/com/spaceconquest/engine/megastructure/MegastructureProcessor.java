package com.spaceconquest.engine.megastructure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simulates turn-by-turn assembly, stage progression and operational yields
 * of stellar megastructures and galactic hyperlane gateways.
 */
public class MegastructureProcessor {

    public record MegastructureTurnResult(
            List<Megastructure> updatedMegastructures,
            Map<String, Double> totalEnergyYieldByEmpireKw,
            Map<String, Map<String, Double>> harvestedMaterialsByEmpireKg,
            List<String> activeGatewaySystemIds
    ) {}

    /**
     * Advances megastructure construction stages and calculates energy and material production yields.
     */
    public MegastructureTurnResult processMegastructures(List<Megastructure> megastructures) {
        if (megastructures == null || megastructures.isEmpty()) {
            return new MegastructureTurnResult(List.of(), Map.of(), Map.of(), List.of());
        }

        List<Megastructure> updatedList = new ArrayList<>();
        Map<String, Double> energyYieldByEmpire = new HashMap<>();
        Map<String, Map<String, Double>> materialsByEmpire = new HashMap<>();
        List<String> gatewaySystems = new ArrayList<>();

        for (Megastructure mega : megastructures) {
            Megastructure current = mega;

            // 1. Advance construction if not yet at final stage
            if (current.currentStage() < current.totalStages()) {
                double nextProgress = current.currentStageProgress() + 1.0;
                if (nextProgress >= current.requiredStageProgress()) {
                    int nextStage = current.currentStage() + 1;
                    double newEnergy = calculateEnergyYield(current.type(), nextStage);
                    Map<String, Double> newHarvest = calculateMaterialYield(current.type(), nextStage);
                    int newHab = calculateHabitableCapacity(current.type(), nextStage);

                    current = new Megastructure(
                            current.id(), current.name(), current.type(),
                            current.systemId(), current.targetCelestialId(), current.ownerEmpireId(),
                            nextStage, current.totalStages(),
                            0.0, current.requiredStageProgress(),
                            true, newEnergy, newHarvest, newHab
                    );
                } else {
                    current = new Megastructure(
                            current.id(), current.name(), current.type(),
                            current.systemId(), current.targetCelestialId(), current.ownerEmpireId(),
                            current.currentStage(), current.totalStages(),
                            nextProgress, current.requiredStageProgress(),
                            current.isOperational(), current.energyYieldKw(),
                            current.materialHarvestYieldKgPerTurn(), current.habitableCapacity()
                    );
                }
            }

            updatedList.add(current);

            // 2. Yield processing if operational
            if (current.isOperational() && current.currentStage() > 0) {
                if (current.energyYieldKw() > 0.0) {
                    energyYieldByEmpire.merge(current.ownerEmpireId(), current.energyYieldKw(), Double::sum);
                }

                if (!current.materialHarvestYieldKgPerTurn().isEmpty()) {
                    Map<String, Double> empMaterials = materialsByEmpire.computeIfAbsent(current.ownerEmpireId(), k -> new HashMap<>());
                    for (Map.Entry<String, Double> e : current.materialHarvestYieldKgPerTurn().entrySet()) {
                        empMaterials.merge(e.getKey(), e.getValue(), Double::sum);
                    }
                }

                if (Megastructure.TYPE_HYPERLANE_GATEWAY.equalsIgnoreCase(current.type())) {
                    if (!gatewaySystems.contains(current.systemId())) {
                        gatewaySystems.add(current.systemId());
                    }
                }
            }
        }

        return new MegastructureTurnResult(updatedList, energyYieldByEmpire, materialsByEmpire, gatewaySystems);
    }

    /**
     * Checks if instantaneous gateway warp transit is possible between two solar systems.
     */
    public boolean canInstantTransitViaGateway(String fromSystemId, String toSystemId, List<Megastructure> megastructures) {
        if (fromSystemId == null || toSystemId == null || fromSystemId.equalsIgnoreCase(toSystemId)) {
            return false;
        }
        if (megastructures == null || megastructures.isEmpty()) {
            return false;
        }

        boolean hasFromGateway = megastructures.stream().anyMatch(m ->
                Megastructure.TYPE_HYPERLANE_GATEWAY.equalsIgnoreCase(m.type())
                        && m.isOperational() && m.currentStage() >= m.totalStages()
                        && m.systemId().equalsIgnoreCase(fromSystemId));

        boolean hasToGateway = megastructures.stream().anyMatch(m ->
                Megastructure.TYPE_HYPERLANE_GATEWAY.equalsIgnoreCase(m.type())
                        && m.isOperational() && m.currentStage() >= m.totalStages()
                        && m.systemId().equalsIgnoreCase(toSystemId));

        return hasFromGateway && hasToGateway;
    }

    private double calculateEnergyYield(String type, int stage) {
        return switch (type) {
            case Megastructure.TYPE_DYSON_SWARM -> stage * 25000.0;
            case Megastructure.TYPE_DYSON_SPHERE -> stage * 250000.0;
            case Megastructure.TYPE_ORBITAL_HABITAT -> stage * 2000.0;
            default -> 0.0;
        };
    }

    private Map<String, Double> calculateMaterialYield(String type, int stage) {
        if (Megastructure.TYPE_STAR_LIFTER.equalsIgnoreCase(type)) {
            return Map.of(
                    "iron_ore", stage * 2000.0,
                    "copper_ore", stage * 1000.0,
                    "rare_earth_elements", stage * 400.0,
                    "fissile_radioisotopes", stage * 100.0
            );
        }
        return Map.of();
    }

    private int calculateHabitableCapacity(String type, int stage) {
        return switch (type) {
            case Megastructure.TYPE_RINGWORLD -> stage * 2000000;
            case Megastructure.TYPE_ORBITAL_HABITAT -> stage * 250000;
            default -> 0;
        };
    }
}
