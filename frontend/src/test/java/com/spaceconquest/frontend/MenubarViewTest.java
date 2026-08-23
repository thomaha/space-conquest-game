package com.spaceconquest.frontend;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class MenubarViewTest {

    @Test
    public void testMenubarControllerAndViews() {
        Menubar menubar = new Menubar();
        assertNotNull(menubar.getHumanController(), "HumanController should be initialized");
        assertEquals("terran_confederation", menubar.getPlayerEmpireId());

        menubar.setPlayerEmpireId("vulkan_forge");
        assertEquals("vulkan_forge", menubar.getPlayerEmpireId());
    }

    @Test
    public void testAudioPlaybackManagerAndSynthesizer() {
        com.spaceconquest.engine.audio.AudioSynthesizer synth = new com.spaceconquest.engine.audio.AudioSynthesizer();
        synth.setAudioHardwareEnabled(false); // test headless

        AudioPlaybackManager manager = new AudioPlaybackManager(synth);
        assertTrue(manager.isSoundEnabled());

        synth.setMasterVolume(0.5);
        assertEquals(0.5, synth.getMasterVolume(), 0.001);

        synth.setSfxVolume(0.7);
        assertEquals(0.7, synth.getSfxVolume(), 0.001);

        synth.setMusicVolume(0.4);
        assertEquals(0.4, synth.getMusicVolume(), 0.001);

        manager.playUiClick();
        manager.playCombatFire();
        manager.playWarpTransit();
        manager.playTechBreakthrough();
        manager.playFacilityBuild();

        assertFalse(manager.getRecentAudioLog().isEmpty());

        manager.setSoundEnabled(false);
        assertFalse(manager.isSoundEnabled());
        assertTrue(synth.isMuted());
    }

    @Test
    public void testCampaignScenarioSetupDefaults() {
        com.spaceconquest.engine.scenario.CampaignSetup setup = com.spaceconquest.engine.scenario.CampaignSetup.createDefault();
        assertNotNull(setup);
        assertEquals(12, setup.starSystemCount());
        assertEquals(com.spaceconquest.engine.scenario.CampaignSetup.AI_BALANCED, setup.aiPersonalityDistribution());
        assertEquals(com.spaceconquest.engine.scenario.CampaignSetup.VICTORY_DOMINATION, setup.victoryConditionType());
    }

    @Test
    public void testToggleGameMenu() {
        Menubar menubar = new Menubar();
        assertDoesNotThrow(menubar::toggleGameMenu);
    }
}
