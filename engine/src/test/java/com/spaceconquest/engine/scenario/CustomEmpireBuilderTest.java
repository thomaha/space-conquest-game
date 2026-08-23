package com.spaceconquest.engine.scenario;

import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.Race;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CustomEmpireBuilderTest {

    private CustomEmpireBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new CustomEmpireBuilder();
    }

    @Test
    void testValidateProfileWithinBudget() {
        CustomEmpireProfile validProfile = new CustomEmpireProfile(
                "sol_dominion",
                "Sol Dominion",
                "#3498db",
                "INSIGNIA_CREST",
                CustomEmpireProfile.GOV_DEMOCRACY,
                "species_stellaris",
                "Homo Stellaris",
                CustomEmpireProfile.BIO_CARBON_HUMANOID,
                290.0,
                1.0,
                1.0,
                List.of(SpeciesTrait.TRAIT_INTELLIGENT, SpeciesTrait.TRAIT_RESILIENT), // 2 + 1 = 3 pts <= 5
                new IdeologicalEthics(1, 1, 1, 0), // 3 pts <= 3
                25000.0
        );

        assertTrue(builder.validateProfile(validProfile));
    }

    @Test
    void testRejectProfileOverTraitOrEthicsBudget() {
        // Over trait budget: 2 + 2 + 2 = 6 pts > 5
        CustomEmpireProfile overTraitProfile = new CustomEmpireProfile(
                "sol_dominion",
                "Sol Dominion",
                "#3498db",
                "INSIGNIA_CREST",
                CustomEmpireProfile.GOV_DEMOCRACY,
                "species_stellaris",
                "Homo Stellaris",
                CustomEmpireProfile.BIO_CARBON_HUMANOID,
                290.0,
                1.0,
                1.0,
                List.of(SpeciesTrait.TRAIT_INTELLIGENT, SpeciesTrait.TRAIT_INDUSTRIOUS, SpeciesTrait.TRAIT_RAPID_BREEDERS),
                new IdeologicalEthics(1, 0, 0, 0),
                25000.0
        );

        assertFalse(builder.validateProfile(overTraitProfile));

        // Over ethics budget: 2 + 2 = 4 pts > 3
        CustomEmpireProfile overEthicsProfile = new CustomEmpireProfile(
                "sol_dominion",
                "Sol Dominion",
                "#3498db",
                "INSIGNIA_CREST",
                CustomEmpireProfile.GOV_DEMOCRACY,
                "species_stellaris",
                "Homo Stellaris",
                CustomEmpireProfile.BIO_CARBON_HUMANOID,
                290.0,
                1.0,
                1.0,
                List.of(SpeciesTrait.TRAIT_INTELLIGENT),
                new IdeologicalEthics(2, 2, 0, 0),
                25000.0
        );

        assertFalse(builder.validateProfile(overEthicsProfile));
    }

    @Test
    void testBuildRaceAndEmpireGeneration() {
        CustomEmpireProfile profile = new CustomEmpireProfile(
                "lithovore_collective",
                "Crystalline Conclave",
                "#9b59b6",
                "INSIGNIA_CRYSTAL",
                CustomEmpireProfile.GOV_HIVE_MIND,
                "species_litho",
                "Silica Lithovore",
                CustomEmpireProfile.BIO_SILICON_LITHOVORE,
                350.0,
                2.0,
                1.5,
                List.of(SpeciesTrait.TRAIT_INDUSTRIOUS),
                new IdeologicalEthics(0, 1, 1, -1),
                50000.0
        );

        Race race = builder.buildRace(profile);
        assertNotNull(race);
        assertEquals("species_litho", race.id());
        assertEquals("Rock", race.nutrientType());
        assertEquals("Silicon", race.chemicalComposition());
        assertEquals("Hive mind", race.societyStructure());
        assertTrue(race.intelligence() >= 1.1);

        Empire empire = builder.buildEmpire(profile);
        assertNotNull(empire);
        assertEquals("lithovore_collective", empire.id());
        assertEquals("Hive Mind", empire.societyStructure());
        assertEquals(100000.0, empire.treasuryCredits());

        GameState baseState = new GameState();
        GameState updatedState = builder.applyToGameState(baseState, profile);

        assertNotNull(updatedState);
        assertTrue(updatedState.empires().stream().anyMatch(e -> e.id().equals("lithovore_collective")));
    }
}
