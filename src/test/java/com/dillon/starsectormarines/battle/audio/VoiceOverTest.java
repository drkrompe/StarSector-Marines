package com.dillon.starsectormarines.battle.audio;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VoiceOverTest {
    @Test
    void onUntilTurnedOffAndWithNoCampaign() {
        Map<String, Object> saved = new HashMap<>();
        assertTrue(VoiceOver.enabled(saved));
        assertTrue(VoiceOver.enabled(null));

        VoiceOver.setEnabled(saved, false);
        assertFalse(VoiceOver.enabled(saved));

        VoiceOver.setEnabled(saved, true);
        assertTrue(VoiceOver.enabled(saved));
    }

    @Test
    void turningItOffWithNoCampaignDoesNothing() {
        assertDoesNotThrow(() -> VoiceOver.setEnabled(null, false));
    }

    @Test
    void silencedFilmKeepsEverythingButSpeech() {
        assertFalse(VoiceOver.filmCuePlays("asharu_film_d04", false));
        assertFalse(VoiceOver.filmCuePlays("jangala_arrival_hail_01", false));
        assertFalse(VoiceOver.filmCuePlays("jangala_squelch_open", false));
        assertTrue(VoiceOver.filmCuePlays("asharu_film_engines_s1", false));
        assertTrue(VoiceOver.filmCuePlays("jangala_arrival_score_march", false));
        assertTrue(VoiceOver.filmCuePlays("asharu_film_d04", true));
    }
}
