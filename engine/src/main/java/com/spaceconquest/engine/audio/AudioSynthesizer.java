package com.spaceconquest.engine.audio;

import java.util.ArrayList;
import java.util.List;

/**
 * Procedural audio synthesizer and sound event coordinator providing UI, combat and transit audio cues.
 */
public class AudioSynthesizer {

    public record AudioCue(
            String eventName,
            double baseFrequencyHz,
            double endFrequencyHz,
            int durationMillis,
            double volume
    ) {}

    public interface AudioListener {
        void onAudioCue(AudioCue cue);
    }

    public static final String EVENT_UI_CLICK = "UI_CLICK";
    public static final String EVENT_WARP_TRANSIT = "WARP_TRANSIT";
    public static final String EVENT_COMBAT_FIRE = "COMBAT_FIRE";
    public static final String EVENT_LASER_FIRE = "LASER_FIRE";
    public static final String EVENT_KINETIC_FIRE = "KINETIC_FIRE";
    public static final String EVENT_TORPEDO_LAUNCH = "TORPEDO_LAUNCH";
    public static final String EVENT_SHIELD_DEFLECT = "SHIELD_DEFLECT";
    public static final String EVENT_EXPLOSION = "EXPLOSION";
    public static final String EVENT_FACILITY_BUILD = "FACILITY_BUILD";
    public static final String EVENT_TECH_BREAKTHROUGH = "TECH_BREAKTHROUGH";
    public static final String EVENT_TURN_ADVANCE = "TURN_ADVANCE";
    public static final String EVENT_VICTORY_FANFARE = "VICTORY_FANFARE";
    public static final String EVENT_SENATE_GAVEL = "SENATE_GAVEL";
    public static final String EVENT_TERRAFORM_COMPLETE = "TERRAFORM_COMPLETE";
    public static final String EVENT_MEGASTRUCTURE_STAGE = "MEGASTRUCTURE_STAGE";
    public static final String EVENT_SPACE_AMBIENT = "SPACE_AMBIENT";

    private double masterVolume = 0.8;
    private double sfxVolume = 0.8;
    private double musicVolume = 0.6;
    private boolean isMuted = false;
    private boolean isAudioHardwareEnabled = true;
    private final List<AudioListener> listeners = new ArrayList<>();

    public void addListener(AudioListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(AudioListener listener) {
        listeners.remove(listener);
    }

    public AudioCue triggerCue(String eventName) {
        if (isMuted || masterVolume <= 0.0) {
            return null;
        }

        AudioCue cue = switch (eventName) {
            case EVENT_UI_CLICK -> new AudioCue(EVENT_UI_CLICK, 800.0, 800.0, 50, masterVolume * sfxVolume);
            case EVENT_WARP_TRANSIT -> new AudioCue(EVENT_WARP_TRANSIT, 220.0, 440.0, 300, masterVolume * sfxVolume);
            case EVENT_COMBAT_FIRE -> new AudioCue(EVENT_COMBAT_FIRE, 120.0, 80.0, 150, masterVolume * sfxVolume);
            case EVENT_LASER_FIRE -> new AudioCue(EVENT_LASER_FIRE, 1200.0, 400.0, 120, masterVolume * sfxVolume);
            case EVENT_KINETIC_FIRE -> new AudioCue(EVENT_KINETIC_FIRE, 180.0, 60.0, 180, masterVolume * sfxVolume);
            case EVENT_TORPEDO_LAUNCH -> new AudioCue(EVENT_TORPEDO_LAUNCH, 300.0, 600.0, 250, masterVolume * sfxVolume);
            case EVENT_SHIELD_DEFLECT -> new AudioCue(EVENT_SHIELD_DEFLECT, 900.0, 1100.0, 140, masterVolume * sfxVolume);
            case EVENT_EXPLOSION -> new AudioCue(EVENT_EXPLOSION, 100.0, 40.0, 350, masterVolume * sfxVolume);
            case EVENT_FACILITY_BUILD -> new AudioCue(EVENT_FACILITY_BUILD, 440.0, 554.37, 180, masterVolume * sfxVolume);
            case EVENT_TECH_BREAKTHROUGH -> new AudioCue(EVENT_TECH_BREAKTHROUGH, 523.25, 1046.50, 400, masterVolume * musicVolume);
            case EVENT_TURN_ADVANCE -> new AudioCue(EVENT_TURN_ADVANCE, 523.25, 523.25, 100, masterVolume * sfxVolume);
            case EVENT_VICTORY_FANFARE -> new AudioCue(EVENT_VICTORY_FANFARE, 440.0, 880.0, 500, masterVolume * musicVolume);
            case EVENT_SENATE_GAVEL -> new AudioCue(EVENT_SENATE_GAVEL, 150.0, 100.0, 80, masterVolume * sfxVolume);
            case EVENT_TERRAFORM_COMPLETE -> new AudioCue(EVENT_TERRAFORM_COMPLETE, 659.25, 783.99, 250, masterVolume * sfxVolume);
            case EVENT_MEGASTRUCTURE_STAGE -> new AudioCue(EVENT_MEGASTRUCTURE_STAGE, 330.0, 660.0, 400, masterVolume * sfxVolume);
            case EVENT_SPACE_AMBIENT -> new AudioCue(EVENT_SPACE_AMBIENT, 55.0, 55.0, 1000, masterVolume * musicVolume * 0.3);
            default -> new AudioCue(eventName, 440.0, 440.0, 100, masterVolume * sfxVolume);
        };

        for (AudioListener l : listeners) {
            try {
                l.onAudioCue(cue);
            } catch (Exception ignored) {}
        }

        if (isAudioHardwareEnabled) {
            playSynthesizedToneAsync(cue);
        }

        return cue;
    }

    /**
     * Synthesizes and plays a procedural PCM audio wave on a background thread.
     */
    public void playSynthesizedToneAsync(AudioCue cue) {
        if (cue == null || cue.volume() <= 0.0) return;

        Thread audioThread = new Thread(() -> {
            try {
                float sampleRate = 22050f;
                int numSamples = (int) (cue.durationMillis() * (sampleRate / 1000f));
                if (numSamples <= 0) return;

                byte[] buffer = new byte[numSamples];
                double freqStart = cue.baseFrequencyHz();
                double freqEnd = cue.endFrequencyHz();
                double vol = Math.max(0.0, Math.min(1.0, cue.volume()));

                for (int i = 0; i < numSamples; i++) {
                    double progress = (double) i / numSamples;
                    double currentFreq = freqStart + (freqEnd - freqStart) * progress;
                    double angle = 2.0 * Math.PI * i / (sampleRate / Math.max(1.0, currentFreq));
                    double sample = Math.sin(angle);

                    // Add slight noise texture for explosions/kinetics
                    if (EVENT_EXPLOSION.equals(cue.eventName()) || EVENT_KINETIC_FIRE.equals(cue.eventName())) {
                        sample = sample * 0.6 + (Math.random() * 2.0 - 1.0) * 0.4;
                    }

                    // Apply linear attack/decay envelope to prevent clicks
                    double envelope = 1.0;
                    if (progress < 0.1) envelope = progress / 0.1;
                    else if (progress > 0.8) envelope = (1.0 - progress) / 0.2;

                    buffer[i] = (byte) (sample * envelope * vol * 127.0);
                }

                javax.sound.sampled.AudioFormat format = new javax.sound.sampled.AudioFormat(
                        sampleRate, 8, 1, true, false
                );
                javax.sound.sampled.DataLine.Info info = new javax.sound.sampled.DataLine.Info(
                        javax.sound.sampled.SourceDataLine.class, format
                );

                if (javax.sound.sampled.AudioSystem.isLineSupported(info)) {
                    javax.sound.sampled.SourceDataLine line = (javax.sound.sampled.SourceDataLine) javax.sound.sampled.AudioSystem.getLine(info);
                    line.open(format);
                    line.start();
                    line.write(buffer, 0, buffer.length);
                    line.drain();
                    line.close();
                }
            } catch (Throwable ignored) {
                // Silently fallback if audio hardware is unavailable or in headless test environment
            }
        });
        audioThread.setDaemon(true);
        audioThread.setName("AudioSynthesizer-Worker");
        audioThread.start();
    }

    public boolean isAudioHardwareEnabled() {
        return isAudioHardwareEnabled;
    }

    public void setAudioHardwareEnabled(boolean audioHardwareEnabled) {
        isAudioHardwareEnabled = audioHardwareEnabled;
    }

    public double getMasterVolume() {
        return masterVolume;
    }

    public void setMasterVolume(double masterVolume) {
        this.masterVolume = Math.max(0.0, Math.min(1.0, masterVolume));
    }

    public double getSfxVolume() {
        return sfxVolume;
    }

    public void setSfxVolume(double sfxVolume) {
        this.sfxVolume = Math.max(0.0, Math.min(1.0, sfxVolume));
    }

    public double getMusicVolume() {
        return musicVolume;
    }

    public void setMusicVolume(double musicVolume) {
        this.musicVolume = Math.max(0.0, Math.min(1.0, musicVolume));
    }

    public boolean isMuted() {
        return isMuted;
    }

    public void setMuted(boolean muted) {
        isMuted = muted;
    }
}
