package com.spaceconquest.frontend;

import com.spaceconquest.engine.audio.AudioSynthesizer;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages audio feedback, sound effects and procedural acoustic cues across UI panels and combat views.
 */
public class AudioPlaybackManager implements AudioSynthesizer.AudioListener {

    private final AudioSynthesizer synthesizer;
    private final List<String> recentAudioLog = new ArrayList<>();
    private boolean soundEnabled = true;

    public AudioPlaybackManager(AudioSynthesizer synthesizer) {
        this.synthesizer = synthesizer != null ? synthesizer : new AudioSynthesizer();
        this.synthesizer.addListener(this);
    }

    public AudioSynthesizer getSynthesizer() {
        return synthesizer;
    }

    @Override
    public void onAudioCue(AudioSynthesizer.AudioCue cue) {
        if (!soundEnabled || cue == null) return;
        recentAudioLog.add(String.format("[%s] freq: %.1f-%.1fHz vol: %.2f dur: %dms",
                cue.eventName(), cue.baseFrequencyHz(), cue.endFrequencyHz(), cue.volume(), cue.durationMillis()));
        if (recentAudioLog.size() > 50) {
            recentAudioLog.remove(0);
        }
    }

    public void playUiClick() {
        if (synthesizer != null) {
            synthesizer.triggerCue(AudioSynthesizer.EVENT_UI_CLICK);
        }
    }

    public void playCombatFire() {
        if (synthesizer != null) {
            synthesizer.triggerCue(AudioSynthesizer.EVENT_COMBAT_FIRE);
        }
    }

    public void playWarpTransit() {
        if (synthesizer != null) {
            synthesizer.triggerCue(AudioSynthesizer.EVENT_WARP_TRANSIT);
        }
    }

    public void playTechBreakthrough() {
        if (synthesizer != null) {
            synthesizer.triggerCue(AudioSynthesizer.EVENT_TECH_BREAKTHROUGH);
        }
    }

    public void playFacilityBuild() {
        if (synthesizer != null) {
            synthesizer.triggerCue(AudioSynthesizer.EVENT_FACILITY_BUILD);
        }
    }

    public boolean isSoundEnabled() {
        return soundEnabled;
    }

    public void setSoundEnabled(boolean soundEnabled) {
        this.soundEnabled = soundEnabled;
        if (synthesizer != null) {
            synthesizer.setMuted(!soundEnabled);
        }
    }

    public List<String> getRecentAudioLog() {
        return List.copyOf(recentAudioLog);
    }
}
