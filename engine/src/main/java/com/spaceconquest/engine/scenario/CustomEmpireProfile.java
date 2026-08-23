package com.spaceconquest.engine.scenario;

import java.util.List;

/**
 * Encapsulates full customization specifications for a player or AI empire.
 */
public record CustomEmpireProfile(
        String empireId,
        String empireName,
        String primaryColorHex,
        String flagInsignia,
        String governmentForm,
        String speciesId,
        String speciesName,
        String biochemicalType,
        double optimalTemperatureKelvin,
        double optimalPressureAtm,
        double optimalGravity,
        List<String> selectedTraitIds,
        IdeologicalEthics ethics,
        double startingTreasuryBonus
) {
    public static final String GOV_DEMOCRACY = "DEMOCRACY";
    public static final String GOV_AUTOCRACY = "AUTOCRACY";
    public static final String GOV_CORPORATE_OLIGARCHY = "CORPORATE_OLIGARCHY";
    public static final String GOV_HIVE_MIND = "HIVE_MIND";

    public static final String BIO_CARBON_HUMANOID = "CARBON_HUMANOID";
    public static final String BIO_CARBON_AVIAN = "CARBON_AVIAN";
    public static final String BIO_CARBON_REPTILIAN = "CARBON_REPTILIAN";
    public static final String BIO_SILICON_LITHOVORE = "SILICON_LITHOVORE";
    public static final String BIO_GASEOUS_BREATHER = "GASEOUS_BREATHER";

    public CustomEmpireProfile {
        if (selectedTraitIds == null) selectedTraitIds = List.of();
        if (ethics == null) ethics = IdeologicalEthics.balancedDefault();
    }

    /**
     * Backward-compatible alias for optimalGravity.
     *
     * @return optimal gravity in m/s²
     */
    public double optimalGravityG() {
        return optimalGravity;
    }

    public static CustomEmpireProfile createDefault() {
        return new CustomEmpireProfile(
                "custom_solar_alliance",
                "Solar Pioneer Alliance",
                "#3498db",
                "INSIGNIA_SOLAR_CREST",
                GOV_DEMOCRACY,
                "species_custom_pioneers",
                "Homo Novus",
                BIO_CARBON_HUMANOID,
                288.15,
                1.0,
                9.81,
                List.of(SpeciesTrait.TRAIT_INTELLIGENT, SpeciesTrait.TRAIT_ADAPTIVE),
                IdeologicalEthics.balancedDefault(),
                50000.0
        );
    }
}
