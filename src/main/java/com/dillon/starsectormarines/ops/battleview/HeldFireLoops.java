package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.weapon.HeldLoopEnvelope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The held tone of a sustained-beam weapon, one voice per shooter. A weapon
 * that authors {@code audio.loopSound} fires many short pulses a second; one
 * clip per pulse stutters, so the screen plays the weapon's fire sound once as
 * the lance starts and keeps its loop alive while pulses keep arriving.
 *
 * <p>After the last pulse the voice lives on for its weapon's
 * {@link HeldLoopEnvelope}: full through the sustain, then faded out with its
 * pitch sagging, a coil spinning down. A pulse within that tail carries the
 * same voice on, rising back to full over {@link #RISE_SECONDS} with no new
 * intro clip; once the tail has run out the next pulse starts a new lance. An
 * unauthored weapon holds at full for {@link #HOLD_SECONDS} and is let go.
 *
 * <p>At most {@link #MAX_VOICES} are held. A new lance past the cap takes the
 * slot of the quietest voice already in its tail; if every voice is still
 * firing, the new lance is not heard (no loop, no intro clip) until one frees.
 *
 * <p>Presentation only: it reads shot events and owns no simulation state.
 * Allocates only when a lance starts. Not thread-safe; one per audio host.
 */
public final class HeldFireLoops {

    /** How long an unauthored voice survives without a pulse; longer than a lance's pulse spacing, shorter than its cooldown. */
    static final float HOLD_SECONDS = HeldLoopEnvelope.DEFAULT_TAIL;
    /** How long a voice caught in its tail by a fresh pulse takes to come back to full. */
    static final float RISE_SECONDS = 0.05f;
    /** The most weapon loops held at once. */
    static final int MAX_VOICES = 16;
    /** The engine fade after an authored voice is let go: a de-click, its envelope has already faded it. */
    static final float RELEASE_SECONDS = 0.03f;

    private final Map<Long, Voice> byShooter = new HashMap<>();
    private final List<Voice> voices = new ArrayList<>();
    private final List<Voice> heldView = Collections.unmodifiableList(voices);

    /** {@link #arm(long, String, HeldLoopEnvelope, float, float, float)} with the unauthored hold. */
    public boolean arm(long shooterId, String loopSoundId, float volume, float x, float y) {
        return arm(shooterId, loopSoundId, HeldLoopEnvelope.DEFAULT, volume, x, y);
    }

    /**
     * Rearms {@code shooterId}'s loop at the muzzle. Returns true when this
     * pulse starts a new lance — no voice was held — so the caller plays the
     * intro clip once. False for a pulse carried by a held voice, including one
     * in its tail, and for a lance refused at {@link #MAX_VOICES}.
     */
    public boolean arm(long shooterId, String loopSoundId, HeldLoopEnvelope envelope, float volume,
                       float x, float y) {
        Voice voice = byShooter.get(shooterId);
        boolean started = voice == null || !voice.loopSoundId.equals(loopSoundId);
        if (voice == null) {
            if (voices.size() >= MAX_VOICES && !evictQuietestTail()) return false;
            voice = new Voice(shooterId);
            byShooter.put(shooterId, voice);
            voices.add(voice);
        }
        if (started) voice.level = 1f;
        voice.loopSoundId = loopSoundId;
        voice.envelope = envelope;
        voice.baseVolume = volume;
        voice.since = 0f;
        voice.x = x;
        voice.y = y;
        voice.settle(0f);
        return started;
    }

    /** Runs the held voices on by real seconds: their tails fade, and the ones that ran out are dropped. */
    public void advance(float dt) {
        if (voices.isEmpty() || dt <= 0f) return;
        for (int i = voices.size() - 1; i >= 0; i--) {
            Voice voice = voices.get(i);
            voice.since += dt;
            if (voice.since >= voice.envelope.tail()) {
                remove(i);
            } else {
                voice.settle(dt);
            }
        }
    }

    /** The voices to keep playing this frame, at their levels and pitches. */
    public List<Voice> held() {
        return heldView;
    }

    public boolean isHeld(long shooterId) {
        return byShooter.containsKey(shooterId);
    }

    /** Drops every held voice — for a host tearing a battle down. */
    public void clear() {
        voices.clear();
        byShooter.clear();
    }

    /** Frees the quietest voice past its sustain; false when every voice is still at full. */
    private boolean evictQuietestTail() {
        int quietest = -1;
        for (int i = 0; i < voices.size(); i++) {
            Voice voice = voices.get(i);
            if (voice.since <= voice.envelope.sustain()) continue;
            if (quietest < 0 || voice.level < voices.get(quietest).level) quietest = i;
        }
        if (quietest < 0) return false;
        remove(quietest);
        return true;
    }

    private void remove(int index) {
        Voice voice = voices.get(index);
        int last = voices.size() - 1;
        voices.set(index, voices.get(last));
        voices.remove(last);
        byShooter.remove(voice.shooterId);
    }

    /** One shooter's held loop; the object itself is the sound player's loop key. */
    public static final class Voice {
        final long shooterId;
        String loopSoundId;
        HeldLoopEnvelope envelope = HeldLoopEnvelope.DEFAULT;
        float baseVolume;
        /** Seconds since the last pulse. */
        float since;
        /** The envelope's level now, 0..1. */
        float level = 1f;
        float x;
        float y;

        Voice(long shooterId) {
            this.shooterId = shooterId;
        }

        /** Follows the envelope down at once, and back up over {@link #RISE_SECONDS}. */
        void settle(float dt) {
            float target = envelope.level(since);
            level = target < level ? target : Math.min(target, level + dt / RISE_SECONDS);
        }

        public String loopSoundId() { return loopSoundId; }
        /** The level to play this frame: the shot's volume under the envelope. */
        public float volume() { return baseVolume * level; }
        public float pitch() { return envelope.pitch(level); }
        public float level() { return level; }
        /** True when the weapon authored its tail; the host then passes {@link #fadeIn()} and {@link #fadeOut()} to the engine. */
        public boolean authored() { return envelope.authored(); }
        public float fadeIn() { return envelope.fadeIn(); }
        /** The engine's own fade once the voice is let go: short, the envelope having already taken it down. */
        public float fadeOut() { return RELEASE_SECONDS; }
        public float x() { return x; }
        public float y() { return y; }
    }
}
