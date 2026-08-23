package com.spaceconquest.engine.industry.refinement;

import com.spaceconquest.engine.Population;
import com.spaceconquest.engine.Race;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes chemical/metallurgical multi-stage refinement recipes and standard of living satisfaction.
 */
public class RefinementProcessor {

    public static final List<RefinementRecipe> STANDARD_RECIPES = List.of(
            new RefinementRecipe(
                    "pyro_iron_smelting", "Pyrometallurgical Iron Smelting", "PYROMETALLURGY",
                    RefinementRecipe.ENV_GRAVITY_LOCKED, 0.1, 10.0,
                    Map.of("iron_ore", 1000.0, "carbon_monoxide_ice", 200.0), 4000.0,
                    Map.of("refined_iron", 700.0), Map.of("oxygen_gas", 300.0),
                    1, "industrial_worker"
            ),
            new RefinementRecipe(
                    "zero_g_platinum", "Zero-G Magnetic Refining", "ZERO_G",
                    RefinementRecipe.ENV_MICROGRAVITY_NATIVE, 0.0, 0.05,
                    Map.of("platinum_ore", 1000.0), 12000.0,
                    Map.of("refined_platinum", 500.0), Map.of("refined_iron", 300.0, "refined_sulfur", 200.0),
                    2, "industrial_worker"
            ),
            new RefinementRecipe(
                    "alloy_steel", "Heavy Steel Alloying", "ALLOYING",
                    RefinementRecipe.ENV_UNIVERSAL, 0.0, 10.0,
                    Map.of("refined_iron", 980.0, "refined_carbon", 20.0), 8000.0,
                    Map.of("steel", 1000.0), Map.of(),
                    1, "industrial_worker"
            ),
            new RefinementRecipe(
                    "alloy_inconel", "Inconel Superalloy Synthesis", "ALLOYING",
                    RefinementRecipe.ENV_UNIVERSAL, 0.0, 10.0,
                    Map.of("refined_nickel", 600.0, "refined_chromium", 200.0, "refined_iron", 200.0), 10000.0,
                    Map.of("inconel_alloy", 1000.0), Map.of(),
                    2, "industrial_worker"
            ),
            new RefinementRecipe(
                    "alloy_titanium_aluminide", "Titanium Aluminide Alloying", "ALLOYING",
                    RefinementRecipe.ENV_UNIVERSAL, 0.0, 10.0,
                    Map.of("titanium", 500.0, "refined_aluminum", 500.0), 11000.0,
                    Map.of("titanium_aluminide", 1000.0), Map.of(),
                    2, "industrial_worker"
            ),
            new RefinementRecipe(
                    "superconducting_cuprates", "Superconducting Cuprate Synthesis", "ALLOYING",
                    RefinementRecipe.ENV_UNIVERSAL, 0.0, 10.0,
                    Map.of("refined_copper", 600.0, "rare_earth_elements", 300.0, "oxygen_gas", 100.0), 14000.0,
                    Map.of("superconducting_cuprates", 1000.0), Map.of(),
                    3, "engineer"
            ),
            new RefinementRecipe(
                    "fuel_fusion_pellets", "Synthetic Fusion Fuel Pellet Synthesis", "SYNTHETIC_FUEL",
                    RefinementRecipe.ENV_UNIVERSAL, 0.0, 10.0,
                    Map.of("deuterium_gas", 500.0, "helium_3", 500.0), 15000.0,
                    Map.of("fusion_fuel_pellets", 1000.0), Map.of(),
                    3, "engineer"
            ),
            new RefinementRecipe(
                    "consumer_goods_mfg", "Consumer Electronics and Goods", "CONSUMER_GOODS",
                    RefinementRecipe.ENV_UNIVERSAL, 0.0, 10.0,
                    Map.of("bio_polymers", 250.0, "refined_aluminum", 250.0, "refined_copper", 250.0, "refined_silicon", 250.0), 6000.0,
                    Map.of("consumer_goods", 1000.0), Map.of(),
                    1, "industrial_worker"
            ),
            new RefinementRecipe(
                    "synthesis_metamaterials", "Metamaterial Composite Synthesis", "ADVANCED_COMPOSITE",
                    RefinementRecipe.ENV_UNIVERSAL, 0.0, 10.0,
                    Map.of("graphene", 400.0, "titanium_aluminide", 300.0, "gold", 300.0), 18000.0,
                    Map.of("metamaterial_composites", 1000.0), Map.of(),
                    3, "engineer"
            ),
            new RefinementRecipe(
                    "synthesis_antimatter_cells", "Antimatter Containment Cell Manufacturing", "WARP_WEAPONRY",
                    RefinementRecipe.ENV_UNIVERSAL, 0.0, 10.0,
                    Map.of("refined_tungsten", 500.0, "superconducting_cuprates", 300.0, "inconel_alloy", 200.0), 25000.0,
                    Map.of("antimatter_containment_cell", 1000.0), Map.of(),
                    4, "engineer"
            ),
            new RefinementRecipe(
                    "synthesis_quantum_spin_glass", "Quantum Spin Glass Fabrication", "QUANTUM_COMPONENTS",
                    RefinementRecipe.ENV_UNIVERSAL, 0.0, 10.0,
                    Map.of("superconducting_cuprates", 400.0, "refined_gallium", 300.0, "refined_manganese", 300.0), 16000.0,
                    Map.of("quantum_spin_glass", 1000.0), Map.of(),
                    3, "engineer"
            ),
            new RefinementRecipe(
                    "synthesis_metallic_hydrogen", "High-Pressure Metallic Hydrogen Synthesis", "DEGENERATE_MATTER",
                    RefinementRecipe.ENV_GRAVITY_LOCKED, 0.5, 10.0,
                    Map.of("hydrogen_gas", 1000.0), 30000.0,
                    Map.of("stabilized_metallic_hydrogen", 1000.0), Map.of(),
                    4, "engineer"
            ),
            new RefinementRecipe(
                    "synthesis_monomolecular_wire", "Monomolecular Wire Polymerization", "TETHER_MACROSTRUCTURES",
                    RefinementRecipe.ENV_UNIVERSAL, 0.0, 10.0,
                    Map.of("carbon_nanotubes", 500.0, "graphene", 500.0), 20000.0,
                    Map.of("monomolecular_wire", 1000.0), Map.of(),
                    3, "engineer"
            ),
            new RefinementRecipe(
                    "synthesis_boron_nitride", "Crystalline Boron Nitride Crystallization", "ALLOYING",
                    RefinementRecipe.ENV_UNIVERSAL, 0.0, 10.0,
                    Map.of("boron_ore", 600.0, "nitrogen_gas", 400.0), 14000.0,
                    Map.of("crystalline_boron_nitride", 1000.0), Map.of(),
                    2, "industrial_worker"
            )
    );

    public record RefinementExecutionResult(
            boolean isSuccessful,
            Map<String, Double> consumedInputsKg,
            Map<String, Double> producedOutputsKg,
            Map<String, Double> producedByproductsKg,
            double powerConsumedKw,
            String failureReason
    ) {}

    public List<RefinementRecipe> getStandardRecipes() {
        return STANDARD_RECIPES;
    }

    public record StandardOfLivingResult(
            double satisfactionIndex,
            double happinessModifier,
            double crimeModifier,
            double immigrationModifier
    ) {}

    /**
     * Attempts to execute a refinement recipe against a local material cache and planetary gravity.
     */
    public RefinementExecutionResult processRecipeExecution(
            RefinementRecipe recipe,
            Map<String, Double> availableMaterialsKg,
            double availablePowerKw,
            double localGravityG,
            int facilityTier
    ) {
        if (recipe == null) {
            return new RefinementExecutionResult(false, Map.of(), Map.of(), Map.of(), 0.0, "Recipe is null");
        }

        if (facilityTier < recipe.minFacilityTier()) {
            return new RefinementExecutionResult(false, Map.of(), Map.of(), Map.of(), 0.0,
                    "Facility tier " + facilityTier + " below required tier " + recipe.minFacilityTier());
        }

        // Gravity check
        if (RefinementRecipe.ENV_GRAVITY_LOCKED.equalsIgnoreCase(recipe.operationalEnvironment())
                && localGravityG < recipe.minGravityG()) {
            return new RefinementExecutionResult(false, Map.of(), Map.of(), Map.of(), 0.0,
                    "Local gravity " + localGravityG + "g below required minimum " + recipe.minGravityG() + "g");
        }
        if (RefinementRecipe.ENV_MICROGRAVITY_NATIVE.equalsIgnoreCase(recipe.operationalEnvironment())
                && localGravityG > recipe.maxGravityG()) {
            return new RefinementExecutionResult(false, Map.of(), Map.of(), Map.of(), 0.0,
                    "Local gravity " + localGravityG + "g exceeds microgravity ceiling " + recipe.maxGravityG() + "g");
        }

        if (availablePowerKw < recipe.powerDrawKw()) {
            return new RefinementExecutionResult(false, Map.of(), Map.of(), Map.of(), 0.0, "Insufficient power grid capacity");
        }

        // Verify inputs
        Map<String, Double> inputs = availableMaterialsKg != null ? availableMaterialsKg : Map.of();
        for (Map.Entry<String, Double> req : recipe.inputMaterialsKg().entrySet()) {
            if (inputs.getOrDefault(req.getKey(), 0.0) < req.getValue()) {
                return new RefinementExecutionResult(false, Map.of(), Map.of(), Map.of(), 0.0,
                        "Missing required input: " + req.getKey());
            }
        }

        return new RefinementExecutionResult(
                true,
                recipe.inputMaterialsKg(),
                recipe.outputMaterialsKg(),
                recipe.byproductMaterialsKg(),
                recipe.powerDrawKw(),
                ""
        );
    }

    /**
     * Evaluates colony standard of living satisfaction from consumer goods distribution.
     */
    public StandardOfLivingResult evaluateStandardOfLiving(
            Population population,
            Race race,
            double suppliedConsumerGoodsKg
    ) {
        if (population == null || race == null) {
            return new StandardOfLivingResult(1.0, 0.0, 0.0, 1.0);
        }

        if ("Hive mind".equalsIgnoreCase(race.societyStructure())
                || "Hive Mind".equalsIgnoreCase(race.societyStructure())) {
            // Hive minds have zero consumer goods demand
            return new StandardOfLivingResult(1.0, 0.0, 0.0, 1.0);
        }

        long totalPop = population.ageGroups().values().stream().mapToLong(Long::longValue).sum();
        if (totalPop <= 0) {
            return new StandardOfLivingResult(1.0, 0.0, 0.0, 1.0);
        }

        double demandKg = (totalPop / 1000.0) * 50.0; // 50 kg consumer goods per 1,000 citizens
        double satisfaction = Math.min(2.0, suppliedConsumerGoodsKg / Math.max(1.0, demandKg));

        double happinessMod;
        double crimeMod;
        double immigrationMod;

        if (satisfaction >= 1.0) {
            happinessMod = 0.15 * Math.min(1.5, satisfaction);
            crimeMod = -0.10 * Math.min(1.5, satisfaction);
            immigrationMod = 1.25;
        } else {
            double deficit = 1.0 - satisfaction;
            happinessMod = -0.20 * deficit;
            crimeMod = 0.15 * deficit;
            immigrationMod = 0.75;
        }

        return new StandardOfLivingResult(satisfaction, happinessMod, crimeMod, immigrationMod);
    }
}
