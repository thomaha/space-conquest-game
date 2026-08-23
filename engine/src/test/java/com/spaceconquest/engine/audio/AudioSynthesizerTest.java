package com.spaceconquest.engine.audio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AudioSynthesizerTest {

    private AudioSynthesizer synthesizer;

    @BeforeEach
    void setUp() {
        synthesizer = new AudioSynthesizer();
        synthesizer.setAudioHardwareEnabled(false); // disable hardware wave output for tests
    }

    @Test
    void testAudioCueGenerationAndVolumeScaling() {
        AudioSynthesizer.AudioCue clickCue = synthesizer.triggerCue(AudioSynthesizer.EVENT_UI_CLICK);
        assertNotNull(clickCue);
        assertEquals(AudioSynthesizer.EVENT_UI_CLICK, clickCue.eventName());
        assertEquals(800.0, clickCue.baseFrequencyHz());
        assertTrue(clickCue.volume() > 0.0);

        AudioSynthesizer.AudioCue laserCue = synthesizer.triggerCue(AudioSynthesizer.EVENT_LASER_FIRE);
        assertNotNull(laserCue);
        assertEquals(AudioSynthesizer.EVENT_LASER_FIRE, laserCue.eventName());
        assertEquals(1200.0, laserCue.baseFrequencyHz());

        AudioSynthesizer.AudioCue explodeCue = synthesizer.triggerCue(AudioSynthesizer.EVENT_EXPLOSION);
        assertNotNull(explodeCue);
        assertEquals(AudioSynthesizer.EVENT_EXPLOSION, explodeCue.eventName());
    }

    @Test
    void testListenerEventCallbacks() {
        List<AudioSynthesizer.AudioCue> receivedCues = new ArrayList<>();
        synthesizer.addListener(receivedCues::add);

        synthesizer.triggerCue(AudioSynthesizer.EVENT_COMBAT_FIRE);
        synthesizer.triggerCue(AudioSynthesizer.EVENT_TECH_BREAKTHROUGH);

        assertEquals(2, receivedCues.size());
        assertEquals(AudioSynthesizer.EVENT_COMBAT_FIRE, receivedCues.get(0).eventName());
        assertEquals(AudioSynthesizer.EVENT_TECH_BREAKTHROUGH, receivedCues.get(1).eventName());
    }

    @Test
    void testMuteAndVolumeControl() {
        synthesizer.setMuted(true);
        AudioSynthesizer.AudioCue cue = synthesizer.triggerCue(AudioSynthesizer.EVENT_UI_CLICK);
        assertNull(cue);

        synthesizer.setMuted(false);
        synthesizer.setMasterVolume(0.5);
        assertEquals(0.5, synthesizer.getMasterVolume(), 0.001);

        AudioSynthesizer.AudioCue halfVolCue = synthesizer.triggerCue(AudioSynthesizer.EVENT_UI_CLICK);
        assertNotNull(halfVolCue);
        assertEquals(0.5 * 0.8, halfVolCue.volume(), 0.001);
    }
}
