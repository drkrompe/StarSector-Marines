package com.dillon.starsectormarines.battle.weapon;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * How a weapon's held loop ({@code audio.loopSound}) lives on after its last pulse: the burn holds at full for
 * {@link #sustain()}, then dies away over {@link #fadeOut()} seconds, its pitch sagging by up to
 * {@link #pitchDrop()} as it goes, like a coil or capacitor spinning down. {@link #fadeIn()} is how long the loop
 * takes to come up as a lance starts. A pulse that arrives in the tail carries the same loop on, back up to full.
 *
 * <p>Authored in a weapon's {@code audio} block as {@code loopTail} (the whole time after the last pulse,
 * {@code sustain + fadeOut}), {@code loopFadeOut}, {@code loopFadeIn} and {@code loopPitchDrop}. A weapon that
 * authors none of them keeps {@link #DEFAULT}: full for {@link #DEFAULT_TAIL} seconds, then let go, the sound engine's
 * own fade taking it out.
 */
public record HeldLoopEnvelope(float tail, float fadeOut, float fadeIn, float pitchDrop, boolean authored) {

    /** How long an unauthored loop survives without a pulse: longer than a lance's pulse spacing. */
    public static final float DEFAULT_TAIL = 0.15f;
    /**
     * The least an authored loop holds at full after a pulse, so a burst's own pulse spacing never dips it: the
     * unauthored hold.
     */
    public static final float MIN_SUSTAIN = DEFAULT_TAIL;
    /** The engine's fade-in for an authored loop that names none. */
    public static final float DEFAULT_FADE_IN = 0.05f;
    /** The deepest pitch sag an authored tail may ask for. */
    public static final float MAX_PITCH_DROP = 0.5f;

    public static final HeldLoopEnvelope DEFAULT = new HeldLoopEnvelope(DEFAULT_TAIL, 0f, 0f, 0f, false);

    /** Seconds the loop holds at full after a pulse before its fade begins. */
    public float sustain() {
        return tail - fadeOut;
    }

    /**
     * Reads the loop keys of {@code audio} (null for none). Throws on a key without a {@code loopSound}, a tail that
     * would not hold at full for {@link #MIN_SUSTAIN}, or a pitch drop past {@link #MAX_PITCH_DROP}.
     */
    public static HeldLoopEnvelope parse(JSONObject audio, String weaponId) throws JSONException {
        if (audio == null) return DEFAULT;
        boolean any = audio.has("loopTail") || audio.has("loopFadeOut") || audio.has("loopFadeIn")
                || audio.has("loopPitchDrop");
        if (!any) return DEFAULT;
        String loop = audio.optString("loopSound", "");
        if (loop.isEmpty()) {
            throw new JSONException("Weapon '" + weaponId + "' authors a loop tail without audio.loopSound");
        }
        float fadeOut = (float) audio.optDouble("loopFadeOut", 0.0);
        float tail = (float) audio.optDouble("loopTail", MIN_SUSTAIN + fadeOut);
        float fadeIn = (float) audio.optDouble("loopFadeIn", DEFAULT_FADE_IN);
        float pitchDrop = (float) audio.optDouble("loopPitchDrop", 0.0);
        if (!(fadeOut >= 0f) || !(fadeIn >= 0f) || fadeIn > 1f) {
            throw new JSONException("Weapon '" + weaponId + "' audio.loopFadeOut and loopFadeIn must be 0..1 s");
        }
        if (!(tail - fadeOut >= MIN_SUSTAIN - 1e-4f)) {
            throw new JSONException("Weapon '" + weaponId + "' audio.loopTail " + tail + " must hold at full for "
                    + MIN_SUSTAIN + " s before its " + fadeOut + " s fade");
        }
        if (!(pitchDrop >= 0f) || pitchDrop > MAX_PITCH_DROP) {
            throw new JSONException("Weapon '" + weaponId + "' audio.loopPitchDrop must be 0.." + MAX_PITCH_DROP);
        }
        return new HeldLoopEnvelope(tail, fadeOut, fadeIn, pitchDrop, true);
    }

    /**
     * The loop's level {@code since} seconds after the last pulse: 1 through the sustain, then a cosine fall to 0 at
     * the tail's end.
     */
    public float level(float since) {
        if (since <= sustain()) return 1f;
        if (since >= tail || fadeOut <= 0f) return 0f;
        float u = (since - sustain()) / fadeOut;
        return 0.5f * (1f + (float) Math.cos(Math.PI * u));
    }

    /** The loop's pitch at {@code level}: down by {@link #pitchDrop} as the level reaches 0. */
    public float pitch(float level) {
        return 1f - pitchDrop * (1f - level);
    }
}
