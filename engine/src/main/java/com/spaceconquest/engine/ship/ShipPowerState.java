package com.spaceconquest.engine.ship;

import java.util.Map;

/** Physical electrical reserves, independent of freight and main-drive propellant. */
public record ShipPowerState(Map<String, Double> generatorMaterialsKg, String chemicalMixture,
                             String reactorFuel, double batteryChargeKwh, boolean arraysDeployed,
                             double arrayCondition, double orientationFraction,
                             double unmetEssentialHours, double unmetDriveKwh, double unmetCargoKwh,
                             double lastUnmetEssentialKwh, double chargedInputKwhToday,
                             CargoPreservationState cargoPreservation, RescueStatus rescueStatus) {
    public ShipPowerState {
        generatorMaterialsKg = generatorMaterialsKg == null ? Map.of() : Map.copyOf(generatorMaterialsKg);
        if (chemicalMixture == null) chemicalMixture = "rp1";
        if (reactorFuel == null) reactorFuel = "uranium";
        if (cargoPreservation == null) cargoPreservation = CargoPreservationState.empty();
        for (double value : new double[]{batteryChargeKwh, unmetEssentialHours, unmetDriveKwh, unmetCargoKwh,
                lastUnmetEssentialKwh, chargedInputKwhToday})
            if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid electrical reserve");
        if (!Double.isFinite(arrayCondition) || arrayCondition < 0 || arrayCondition > 1
                || !Double.isFinite(orientationFraction) || orientationFraction < 0 || orientationFraction > 1
                || generatorMaterialsKg.values().stream().anyMatch(value -> !Double.isFinite(value) || value < 0))
            throw new IllegalArgumentException("Invalid electrical equipment state");
    }

    public ShipPowerState(Map<String, Double> generatorMaterialsKg, String chemicalMixture,
                          String reactorFuel, double batteryChargeKwh, boolean arraysDeployed,
                          double arrayCondition, double orientationFraction, double unmetEssentialHours,
                          double unmetDriveKwh, double unmetCargoKwh, double lastUnmetEssentialKwh,
                          double chargedInputKwhToday, CargoPreservationState cargoPreservation) {
        this(generatorMaterialsKg, chemicalMixture, reactorFuel, batteryChargeKwh, arraysDeployed,
                arrayCondition, orientationFraction, unmetEssentialHours, unmetDriveKwh, unmetCargoKwh,
                lastUnmetEssentialKwh, chargedInputKwhToday, cargoPreservation, null);
    }

    public ShipPowerState(Map<String, Double> generatorMaterialsKg, String chemicalMixture,
                          String reactorFuel, double batteryChargeKwh, boolean arraysDeployed,
                          double arrayCondition, double orientationFraction, double unmetEssentialHours,
                          double unmetDriveKwh, double unmetCargoKwh, double lastUnmetEssentialKwh,
                          double chargedInputKwhToday) {
        this(generatorMaterialsKg, chemicalMixture, reactorFuel, batteryChargeKwh, arraysDeployed,
                arrayCondition, orientationFraction, unmetEssentialHours, unmetDriveKwh, unmetCargoKwh,
                lastUnmetEssentialKwh, chargedInputKwhToday, CargoPreservationState.empty());
    }

    public ShipPowerState withCargoPreservation(CargoPreservationState state) {
        return new ShipPowerState(generatorMaterialsKg, chemicalMixture, reactorFuel, batteryChargeKwh,
                arraysDeployed, arrayCondition, orientationFraction, unmetEssentialHours,
                unmetDriveKwh, unmetCargoKwh, lastUnmetEssentialKwh, chargedInputKwhToday, state, rescueStatus);
    }

    public ShipPowerState withRescueStatus(RescueStatus state) {
        return new ShipPowerState(generatorMaterialsKg, chemicalMixture, reactorFuel, batteryChargeKwh,
                arraysDeployed, arrayCondition, orientationFraction, unmetEssentialHours,
                unmetDriveKwh, unmetCargoKwh, lastUnmetEssentialKwh, chargedInputKwhToday, cargoPreservation, state);
    }

    public static ShipPowerState empty() {
        return new ShipPowerState(Map.of(), "rp1", "uranium", 0, true, 1, 1, 0, 0, 0, 0, 0);
    }

    public double fuelMassKg() { return generatorMaterialsKg.values().stream().mapToDouble(Double::doubleValue).sum(); }

    public ShipPowerState withChargeInputToday(double input) {
        return new ShipPowerState(generatorMaterialsKg, chemicalMixture, reactorFuel, batteryChargeKwh,
                arraysDeployed, arrayCondition, orientationFraction, unmetEssentialHours,
                unmetDriveKwh, unmetCargoKwh, lastUnmetEssentialKwh, input, cargoPreservation, rescueStatus);
    }

    public ShipPowerState resetPassengerOutage() {
        return new ShipPowerState(generatorMaterialsKg, chemicalMixture, reactorFuel, batteryChargeKwh,
                arraysDeployed, arrayCondition, orientationFraction, 0, unmetDriveKwh,
                unmetCargoKwh, 0, chargedInputKwhToday, cargoPreservation, rescueStatus);
    }
}
