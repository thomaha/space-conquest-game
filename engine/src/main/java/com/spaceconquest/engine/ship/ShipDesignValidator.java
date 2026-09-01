package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Validates modular spaceship blueprints against physics, power grid and structural integrity constraints.
 */
public class ShipDesignValidator {

    public static final double MIN_STRUCTURAL_INTEGRITY_THRESHOLD = 0.50;

    public record ValidationResult(
            boolean isValid,
            boolean isLaunchCapable,
            double structuralIntegrity,
            double powerBalanceKw,
            double totalDryMassKg,
            double maxCargoMassKg,
            double totalThrustN,
            double minLaunchThrustRequiredN,
            List<String> validationErrors
    ) {}

    private record ModuleAggregation(
            int totalSlots,
            double dryMass,
            double powerDraw,
            double powerOutput,
            double thrust,
            double maxCargo,
            double troopCapacity,
            double colonizationCapacity,
            double miningRate,
            int maxComplexity
    ) {}

    /**
     * Evaluates and builds a validated ShipDesign instance with computed physical variables.
     */
    public ShipDesign buildAndValidate(
            String id,
            String name,
            String ownerEntityId,
            String role,
            ShipHullFrame frame,
            List<ShipModule> modules,
            Material hullMaterial,
            Material armorMaterial,
            double armorThicknessCm,
            double homePlanetGravity,
            double homeAtmospherePressure,
            int maxNanotechTier,
            boolean isProprietaryCorporate
    ) {
        ValidationResult res = validate(
                role, frame, modules, hullMaterial, armorMaterial,
                armorThicknessCm, homePlanetGravity, homeAtmospherePressure, maxNanotechTier
        );

        return new ShipDesign(
                id,
                name,
                ownerEntityId,
                role,
                (hullMaterial != null) ? hullMaterial.id() : (frame != null ? frame.structuralMaterialId() : "steel"),
                modules.stream().map(ShipModule::id).toList(),
                (armorMaterial != null) ? armorMaterial.id() : "steel",
                armorThicknessCm,
                res.totalDryMassKg(),
                res.maxCargoMassKg(),
                res.powerBalanceKw(),
                res.structuralIntegrity(),
                res.minLaunchThrustRequiredN(),
                res.totalThrustN(),
                res.isLaunchCapable() && res.isValid(),
                isProprietaryCorporate
        );
    }

    /**
     * Executes the comprehensive physics validation pass.
     */
    public ValidationResult validate(
            String role,
            ShipHullFrame frame,
            List<ShipModule> modules,
            Material hullMaterial,
            Material armorMaterial,
            double armorThicknessCm,
            double homePlanetGravity,
            double homeAtmospherePressure,
            int maxNanotechTier
    ) {
        List<String> errors = new ArrayList<>();
        ModuleAggregation agg = aggregateModules(modules);

        int maxFrameSlots = (frame != null) ? frame.totalSlots() : 100;
        if (agg.totalSlots() > maxFrameSlots) {
            errors.add("Total allocated module slots (" + agg.totalSlots() + ") exceeds hull frame limit (" + maxFrameSlots + ")");
        }

        double armorMass = agg.totalSlots() * Math.max(0.0, armorThicknessCm) * 150.0;
        double frameDryMass = (frame != null) ? frame.frameMassKg() : 5000.0;
        double totalDryMass = frameDryMass + agg.dryMass() + armorMass;

        double structuralIntegrity = calculateStructuralIntegrity(frame, hullMaterial, agg.totalSlots(), totalDryMass);
        if (structuralIntegrity < MIN_STRUCTURAL_INTEGRITY_THRESHOLD) {
            errors.add(String.format("Calculated structural integrity (%.2f) fails minimum ceiling (%.2f)",
                    structuralIntegrity, MIN_STRUCTURAL_INTEGRITY_THRESHOLD));
        }

        double powerBalance = agg.powerOutput() - agg.powerDraw();
        if (powerBalance < 0.0) {
            errors.add(String.format("Power grid deficit: output (%.1f kW) is less than total draw (%.1f kW)",
                    agg.powerOutput(), agg.powerDraw()));
        }

        int allowedTier = Math.max(1, maxNanotechTier);
        if (agg.maxComplexity() > allowedTier) {
            errors.add(String.format("Equipped module complexity (%d) exceeds imperial nanotechnology tier (%d)",
                    agg.maxComplexity(), allowedTier));
        }

        validateRoleRequirements(role, agg, errors);

        double gravity = Math.max(0.1, homePlanetGravity);
        double atmosphere = Math.max(0.0, homeAtmospherePressure);
        double maxLaunchMass = totalDryMass + agg.maxCargo();
        double minLaunchThrustRequiredN = maxLaunchMass * gravity * (1.0 + atmosphere);
        boolean isLaunchCapable = agg.thrust() >= minLaunchThrustRequiredN;

        return new ValidationResult(
                errors.isEmpty(),
                isLaunchCapable,
                structuralIntegrity,
                powerBalance,
                totalDryMass,
                agg.maxCargo(),
                agg.thrust(),
                minLaunchThrustRequiredN,
                errors
        );
    }

    private ModuleAggregation aggregateModules(List<ShipModule> modules) {
        int totalSlots = 0;
        double dryMass = 0.0;
        double powerDraw = 0.0;
        double powerOutput = 0.0;
        double thrust = 0.0;
        double maxCargo = 0.0;
        double troopCapacity = 0.0;
        double colonizationCapacity = 0.0;
        double miningRate = 0.0;
        int highestModuleComplexity = 1;

        if (modules != null) {
            for (ShipModule mod : modules) {
                totalSlots += mod.slotCost();
                dryMass += mod.dryMassKg();
                powerDraw += mod.powerDrawKw();
                powerOutput += mod.powerOutputKw();
                thrust += mod.thrustOutputN();
                highestModuleComplexity = Math.max(highestModuleComplexity, mod.complexityLevel());

                Map<String, Double> stats = mod.operationalStats();
                if (stats.containsKey("cargoCapacityKg")) maxCargo += stats.get("cargoCapacityKg");
                if (stats.containsKey("troopCapacity")) troopCapacity += stats.get("troopCapacity");
                if (stats.containsKey("colonizationCapacity")) colonizationCapacity += stats.get("colonizationCapacity");
                if (stats.containsKey("miningRateKgPerTurn")) miningRate += stats.get("miningRateKgPerTurn");
            }
        }
        return new ModuleAggregation(
                totalSlots, dryMass, powerDraw, powerOutput, thrust,
                maxCargo, troopCapacity, colonizationCapacity, miningRate, highestModuleComplexity
        );
    }

    private double calculateStructuralIntegrity(ShipHullFrame frame, Material hullMaterial, int totalSlotsAllocated, double totalDryMass) {
        double materialStrength = 50.0;
        if (hullMaterial != null && hullMaterial.strength() > 0) {
            materialStrength = hullMaterial.strength();
        } else if (frame != null && frame.baseStructuralStrength() > 0) {
            materialStrength = frame.baseStructuralStrength();
        }
        double dryMassMod = Math.min(0.85, totalDryMass / 1000000.0);
        return (materialStrength / Math.max(1, totalSlotsAllocated)) * (1.0 - dryMassMod);
    }

    private void validateRoleRequirements(String role, ModuleAggregation agg, List<String> errors) {
        if (ShipRole.CARGO_TRANSPORT.equalsIgnoreCase(role) && agg.maxCargo() <= 0.0) {
            errors.add("Cargo transport role requires at least one cargo vault module");
        }
        if (ShipRole.TROOP_TRANSPORT.equalsIgnoreCase(role) && agg.troopCapacity() <= 0.0) {
            errors.add("Troop transport role requires at least one troop transport bay module");
        }
        if (ShipRole.COLONY_SHIP.equalsIgnoreCase(role) && agg.colonizationCapacity() <= 0.0) {
            errors.add("Colony ship role requires a planetary colonization module");
        }
        if (ShipRole.MINING_SHIP.equalsIgnoreCase(role) && agg.miningRate() <= 0.0) {
            errors.add("Mining ship role requires an extraction/mining array module");
        }
    }

    /**
     * Calculates the Maneuvering Agility score of a spaceship platform in deep space.
     * Formula: Agility = RCS Thruster Impulse Yields / (Total Dry Mass + Stored Cargo Weight)
     */
    public double calculateManeuveringAgility(double totalDryMassKg, double storedCargoKg, double totalRcsImpulseN) {
        double totalMass = Math.max(100.0, totalDryMassKg + storedCargoKg);
        return totalRcsImpulseN / totalMass;
    }

    /**
     * Calculates the sub-light flight speed penalty and power draw multiplier inside planetary atmospheres.
     */
    public double calculateAtmosphericSpeedMultiplier(double atmosphericPressure, double dragCoefficient) {
        if (atmosphericPressure <= 0.0) return 1.0;
        double drag = Math.max(0.1, dragCoefficient);
        return 1.0 / (1.0 + (drag * atmosphericPressure));
    }

    /**
     * Resolves sensor detection range and stealth signature observability.
     */
    public double calculateSensorObservability(double baseRadarCrossSectionM2, double stealthCoatingAbsorptivity) {
        double absorptivity = Math.max(0.0, Math.min(0.95, stealthCoatingAbsorptivity));
        return baseRadarCrossSectionM2 * (1.0 - absorptivity);
    }

    /**
     * Calculates additional power draw from atmospheric flight engine thrusting against fluid resistance.
     */
    public double calculateAtmosphericPowerDrawMultiplier(double atmosphericPressure) {
        if (atmosphericPressure <= 0.0) return 1.0;
        return 1.0 + (atmosphericPressure * 0.50);
    }

    /**
     * Evaluates atmospheric entry heating and calculates hull/armor thermal stress.
     */
    public ThermalStressResult calculateAtmosphericEntryThermalStress(
            String hullMaterialId,
            double entryVelocityKmh,
            double atmosphericPressure,
            boolean hasHeatShield
    ) {
        if (atmosphericPressure <= 0.0) {
            return new ThermalStressResult(300.0, 2000.0, hasHeatShield, false, 0.0);
        }

        double normalizedV = Math.max(1000.0, entryVelocityKmh);
        double entryTempK = 300.0 + ((normalizedV * normalizedV) * atmosphericPressure * 0.000005);

        double meltingPointK = 1600.0;
        String mat = (hullMaterialId != null) ? hullMaterialId.toLowerCase() : "steel";

        if (mat.contains("aluminum")) {
            meltingPointK = 933.0;
        } else if (mat.contains("steel") || mat.contains("iron")) {
            meltingPointK = 1600.0;
        } else if (mat.contains("carbide") || mat.contains("silicon")) {
            meltingPointK = 3000.0;
        } else if (mat.contains("tungsten")) {
            meltingPointK = 3695.0;
        } else if (mat.contains("nanotubes") || mat.contains("carbon")) {
            meltingPointK = 3800.0;
        } else if (mat.contains("graphene")) {
            meltingPointK = 4500.0;
        }

        if (hasHeatShield) {
            meltingPointK += 1500.0;
        }

        boolean suffersFailure = entryTempK > meltingPointK;
        double damage = 0.0;
        if (suffersFailure) {
            double excessK = entryTempK - meltingPointK;
            damage = Math.min(100.0, (excessK / meltingPointK) * 100.0);
        }

        return new ThermalStressResult(entryTempK, meltingPointK, hasHeatShield, suffersFailure, damage);
    }

    /**
     * Calculates the surface blast-off gravity launch tax in credits.
     */
    public double calculateLaunchGravityTax(
            double totalDryMassKg,
            double cargoMassKg,
            double planetaryGravity,
            double atmosphericPressure
    ) {
        double totalMass = totalDryMassKg + cargoMassKg;
        double gravity = Math.max(0.1, planetaryGravity);
        double pressure = Math.max(0.0, atmosphericPressure);

        return totalMass * gravity * (1.0 + pressure) * 0.001;
    }
}
