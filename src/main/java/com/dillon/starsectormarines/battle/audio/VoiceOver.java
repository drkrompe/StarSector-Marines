package com.dillon.starsectormarines.battle.audio;

import com.dillon.starsectormarines.campaign.backend.CampaignBackend;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Company HQ's voice-over switch: whether the mod's recorded speech plays. It covers narration, the
 * radio, marine barks and film dialogue; subtitles and every other sound stay, and a silenced line
 * keeps its subtitle for the line's duration. Saved with the game; on until the player turns it off,
 * and on with no campaign running.
 */
public final class VoiceOver {
    private static final String OFF_KEY = "marines_voice_over_off";
    /** A film's spoken takes and the squelch that frames them; its other cues are score and effects. */
    private static final Pattern FILM_SPEECH =
            Pattern.compile("asharu_film_d\\d+|jangala_arrival_(castle|hail)_.+|jangala_squelch_.+");

    private VoiceOver() { }

    public static boolean enabled() {
        return enabled(CampaignBackend.savedData());
    }

    public static void setEnabled(boolean enabled) {
        setEnabled(CampaignBackend.savedData(), enabled);
    }

    /** Whether a film may play this cue now. */
    public static boolean filmCuePlays(String soundId) {
        return filmCuePlays(soundId, enabled());
    }

    static boolean enabled(Map<String, Object> saved) {
        return saved == null || !Boolean.TRUE.equals(saved.get(OFF_KEY));
    }

    static void setEnabled(Map<String, Object> saved, boolean enabled) {
        if (saved != null) saved.put(OFF_KEY, !enabled);
    }

    static boolean filmCuePlays(String soundId, boolean voiceOver) {
        return voiceOver || !FILM_SPEECH.matcher(soundId).matches();
    }
}
