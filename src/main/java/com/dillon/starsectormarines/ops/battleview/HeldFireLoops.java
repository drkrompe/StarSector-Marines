package com.dillon.starsectormarines.ops.battleview;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The held tone of a sustained-beam weapon, one voice per shooter. A weapon
 * that authors {@code audio.loopSound} fires many short pulses a second; one
 * clip per pulse stutters, so the screen plays the weapon's fire sound once as
 * the lance starts and keeps its loop alive while pulses keep arriving. A
 * shooter that has not fired its loop weapon within {@link #HOLD_SECONDS} lets
 * the loop lapse, and its next pulse starts a new lance.
 *
 * <p>Presentation only: it reads shot events and owns no simulation state.
 * Not thread-safe; one per audio host.
 */
public final class HeldFireLoops {

    /** How long a voice survives without a pulse; longer than a lance's pulse spacing, shorter than its cooldown. */
    static final float HOLD_SECONDS = 0.15f;

    private final Map<Long, Voice> voices = new HashMap<>();

    /**
     * Rearms {@code shooterId}'s loop at the muzzle. Returns true when this
     * pulse starts a new lance — no voice was held — so the caller plays the
     * intro clip once.
     */
    public boolean arm(long shooterId, String loopSoundId, float volume, float x, float y) {
        Voice voice = voices.get(shooterId);
        boolean started = voice == null || !voice.loopSoundId.equals(loopSoundId);
        if (voice == null) {
            voice = new Voice();
            voices.put(shooterId, voice);
        }
        voice.loopSoundId = loopSoundId;
        voice.volume = volume;
        voice.hold = HOLD_SECONDS;
        voice.x = x;
        voice.y = y;
        return started;
    }

    /** Runs the held voices down by real seconds and drops the ones that lapsed. */
    public void advance(float dt) {
        if (voices.isEmpty() || dt <= 0f) return;
        Iterator<Voice> it = voices.values().iterator();
        while (it.hasNext()) {
            Voice voice = it.next();
            voice.hold -= dt;
            if (voice.hold <= 0f) it.remove();
        }
    }

    /** The voices to keep playing this frame. */
    public Collection<Voice> held() {
        return Collections.unmodifiableCollection(voices.values());
    }

    public boolean isHeld(long shooterId) {
        return voices.containsKey(shooterId);
    }

    /** Drops every held voice — for a host tearing a battle down. */
    public void clear() {
        voices.clear();
    }

    /** One shooter's held loop; the object itself is the sound player's loop key. */
    public static final class Voice {
        String loopSoundId;
        float volume;
        float hold;
        float x;
        float y;

        public String loopSoundId() { return loopSoundId; }
        public float volume() { return volume; }
        public float x() { return x; }
        public float y() { return y; }
    }
}
