package com.spaceconquest.frontend;

import javafx.event.Event;
import javafx.scene.control.Button;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class MenubarViewTest {

    @org.junit.jupiter.api.BeforeAll
    public static void initJavaFx() {
        try {
            javafx.application.Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Toolkit already initialized
        }
    }

    @Test
    public void testMenubarControllerAndViews() {
        Menubar menubar = new Menubar();
        assertNotNull(menubar.getHumanController(), "HumanController should be initialized");
        assertEquals("terran_confederation", menubar.getPlayerEmpireId());

        menubar.setPlayerEmpireId("vulkan_forge");
        assertEquals("vulkan_forge", menubar.getPlayerEmpireId());

        assertNull(menubar.getEmpireView(), "EmpireView unbuilt before build(mainApp)");
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

    @Test
    public void testOverlayEventInterceptionConsumesMouseAndScrollEvents() {
        VBox dialogRoot = new VBox();
        Menubar.setupOverlayEventInterception(dialogRoot);
        assertTrue(dialogRoot.isPickOnBounds());

        java.util.concurrent.atomic.AtomicBoolean clickConsumed = new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicBoolean pressConsumed = new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicBoolean releaseConsumed = new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicBoolean scrollConsumed = new java.util.concurrent.atomic.AtomicBoolean(false);

        dialogRoot.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> clickConsumed.set(e.isConsumed()));
        dialogRoot.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> pressConsumed.set(e.isConsumed()));
        dialogRoot.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> releaseConsumed.set(e.isConsumed()));
        dialogRoot.addEventHandler(ScrollEvent.SCROLL, e -> scrollConsumed.set(e.isConsumed()));

        // Test mouse click directly on dialog root
        MouseEvent clickEvent = new MouseEvent(
                MouseEvent.MOUSE_CLICKED,
                50, 50, 50, 50,
                MouseButton.PRIMARY, 1,
                false, false, false, false,
                true, false, false, false, false, false, null
        );
        Event.fireEvent(dialogRoot, clickEvent);
        assertTrue(clickConsumed.get(), "Mouse click inside dialogue must be consumed");

        // Test mouse pressed
        MouseEvent pressEvent = new MouseEvent(
                MouseEvent.MOUSE_PRESSED,
                50, 50, 50, 50,
                MouseButton.PRIMARY, 1,
                false, false, false, false,
                true, false, false, false, false, false, null
        );
        Event.fireEvent(dialogRoot, pressEvent);
        assertTrue(pressConsumed.get(), "Mouse press inside dialogue must be consumed");

        // Test mouse released
        MouseEvent releaseEvent = new MouseEvent(
                MouseEvent.MOUSE_RELEASED,
                50, 50, 50, 50,
                MouseButton.PRIMARY, 1,
                false, false, false, false,
                true, false, false, false, false, false, null
        );
        Event.fireEvent(dialogRoot, releaseEvent);
        assertTrue(releaseConsumed.get(), "Mouse release inside dialogue must be consumed");

        // Test scroll
        ScrollEvent scrollEvent = new ScrollEvent(
                ScrollEvent.SCROLL,
                50, 50, 50, 50,
                false, false, false, false,
                false, false, 0, 10, 0, 10,
                ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                ScrollEvent.VerticalTextScrollUnits.NONE, 0,
                0, null
        );
        Event.fireEvent(dialogRoot, scrollEvent);
        assertTrue(scrollConsumed.get(), "Scroll event inside dialogue must be consumed");
    }

    @Test
    public void testChildEventBubblingIsConsumedByOverlayRoot() {
        VBox parentContainer = new VBox();
        VBox dialogRoot = new VBox();
        Button childButton = new Button("Click me");
        dialogRoot.getChildren().add(childButton);
        parentContainer.getChildren().add(dialogRoot);

        Menubar.setupOverlayEventInterception(dialogRoot);

        java.util.concurrent.atomic.AtomicBoolean parentReceivedEvent = new java.util.concurrent.atomic.AtomicBoolean(false);
        parentContainer.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> parentReceivedEvent.set(true));

        MouseEvent childClick = new MouseEvent(
                MouseEvent.MOUSE_CLICKED,
                10, 10, 10, 10,
                MouseButton.PRIMARY, 1,
                false, false, false, false,
                true, false, false, false, false, false, null
        );
        Event.fireEvent(childButton, childClick);
        assertFalse(parentReceivedEvent.get(), "Mouse click on child inside dialogue must NOT bubble to parent container");
    }

    @Test
    public void testIsAnyOverlayVisible() {
        Menubar menubar = new Menubar();
        assertFalse(menubar.isAnyOverlayVisible(), "No overlay should be visible initially");
    }

    @Test
    public void testDialogueOverlayFullViewportSizingAndPositioning() {
        VBox overlayNode = new VBox();
        Menubar.setupOverlayEventInterception(overlayNode);
        assertTrue(overlayNode.isPickOnBounds());

        double scale = ScreenSettingsManager.getInstance().getUiScale();
        assertTrue(scale > 0.0);

        double targetWidth = Math.max(300, (1920.0 / scale) - 20);
        double targetHeight = Math.max(200, (1080.0 / scale) - 80);
        overlayNode.setPrefSize(targetWidth, targetHeight);
        overlayNode.setMinSize(targetWidth, targetHeight);
        overlayNode.setMaxSize(targetWidth, targetHeight);
        overlayNode.setTranslateX(10 * scale);
        overlayNode.setTranslateY(70 * scale);

        assertEquals(targetWidth, overlayNode.getPrefWidth(), 0.001);
        assertEquals(targetHeight, overlayNode.getPrefHeight(), 0.001);
        assertEquals(10 * scale, overlayNode.getTranslateX(), 0.001);
        assertEquals(70 * scale, overlayNode.getTranslateY(), 0.001);
    }

    @Test
    public void testUpdateAllViewsAndPlayerEmpirePropagation() throws Exception {
        Menubar menubar = new Menubar();
        com.spaceconquest.engine.GalaxyGenerator generator = new com.spaceconquest.engine.GalaxyGenerator();
        com.spaceconquest.engine.GameState newState = generator.generateGameState(4, com.spaceconquest.engine.GameStartScenario.PRE_SPACE_FLIGHT);

        assertDoesNotThrow(() -> menubar.updateAllViews(newState));
        assertDoesNotThrow(() -> menubar.setPlayerEmpireId("vulkan_forge"));
        assertEquals("vulkan_forge", menubar.getPlayerEmpireId());
    }

    @Test
    public void testPlanetDetailViewMegastructureDataIngestion() {
        PlanetDetailView view = new PlanetDetailView(null);
        com.spaceconquest.engine.megastructure.Megastructure dyson = new com.spaceconquest.engine.megastructure.Megastructure(
                "mega_dyson_sol", "Sol Dyson Swarm", com.spaceconquest.engine.megastructure.Megastructure.TYPE_DYSON_SWARM,
                "sol", "earth", "terran_confederation",
                2, 2, 10.0, 10.0, true, 50000000.0, java.util.Map.of(), 0
        );

        view.updateData(java.util.List.of(), java.util.List.of(), java.util.List.of(dyson));
        assertEquals(1, view.getMegastructures().size());
        assertEquals("mega_dyson_sol", view.getMegastructures().get(0).id());
        assertTrue(view.getMegastructures().get(0).isOperational());
    }
}
