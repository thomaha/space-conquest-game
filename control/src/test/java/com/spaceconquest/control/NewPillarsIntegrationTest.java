package com.spaceconquest.control;

import com.spaceconquest.control.command.CreateCustomEmpireCommand;
import com.spaceconquest.control.command.PlaceFacilityOnTileCommand;
import com.spaceconquest.control.command.TargetSubsystemCommand;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.combat.TacticalCombatProcessor;
import com.spaceconquest.engine.scenario.CustomEmpireProfile;
import com.spaceconquest.engine.scenario.IdeologicalEthics;
import com.spaceconquest.engine.scenario.SpeciesTrait;
import com.spaceconquest.engine.ship.Fleet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NewPillarsIntegrationTest {

    private GameState initialState;

    @BeforeEach
    void setUp() {
        Empire terran = new Empire(
                "terran_confederation", "Terran Confederation", "human", "Individualist",
                100000.0, 0.12, List.of("sol"), List.of(), java.util.Map.of(), List.of(), List.of()
        );

        Fleet testFleet = new Fleet(
                "fleet_alpha", "1st Taskforce", "terran_confederation",
                "sol", "", 0, 0, 0, false, "AGGRESSIVE_BRAWL", List.of()
        );

        initialState = new GameState(
                1, "RUNNING", List.of(), List.of(terran), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(testFleet),
                List.of(), List.of(), List.of(), List.of()
        );
    }

    @Test
    void testPlaceFacilityOnTileCommand() {
        PlaceFacilityOnTileCommand cmd = new PlaceFacilityOnTileCommand(
                "earth", 5, "solar_power_array", "terran_confederation", "PUBLIC_STATE", 50, "technician"
        );

        assertTrue(cmd.validate(initialState));
        GameState nextState = cmd.apply(initialState);

        assertNotNull(nextState);
        assertEquals(1, nextState.industrialFacilities().size());
        assertEquals("earth", nextState.industrialFacilities().get(0).planetId());
        assertEquals("solar_power_array", nextState.industrialFacilities().get(0).applicationId());
    }

    @Test
    void testTargetSubsystemCommand() {
        TargetSubsystemCommand cmd = new TargetSubsystemCommand(
                "fleet_alpha", TacticalCombatProcessor.TARGET_SUBSYSTEM_WARP
        );

        assertTrue(cmd.validate(initialState));
        GameState nextState = cmd.apply(initialState);
        assertNotNull(nextState);
    }

    @Test
    void testCreateCustomEmpireCommand() {
        CustomEmpireProfile profile = new CustomEmpireProfile(
                "aurora_covenant",
                "Aurora Covenant",
                "#2ecc71",
                "INSIGNIA_AURORA",
                CustomEmpireProfile.GOV_DEMOCRACY,
                "species_auroran",
                "Auroran Avian",
                CustomEmpireProfile.BIO_CARBON_AVIAN,
                295.0,
                1.0,
                0.9,
                List.of(SpeciesTrait.TRAIT_ADAPTIVE, SpeciesTrait.TRAIT_INTELLIGENT),
                new IdeologicalEthics(1, 0, 1, 1),
                30000.0
        );

        CreateCustomEmpireCommand cmd = new CreateCustomEmpireCommand(profile);
        assertTrue(cmd.validate(initialState));

        GameState nextState = cmd.apply(initialState);
        assertNotNull(nextState);
        assertEquals(2, nextState.empires().size());
        assertTrue(nextState.empires().stream().anyMatch(e -> e.id().equals("aurora_covenant")));
    }
}
