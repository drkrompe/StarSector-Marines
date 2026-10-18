package com.dillon.starsectormarines.cinematic;

import com.dillon.starsectormarines.cinematic.CinematicShot.Caption;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptionTrackTest {
    // Approach speaks 3-6 (two takes back to back), Castle answers at 6.6, a long silence, Approach again at 20.
    // A key resolves to itself and is far narrower than the 100-unit width, so each take is one page.
    private final CaptionTrack track = new CaptionTrack(List.of(
            new Caption(3.0, 4.5, "approach", "one"), new Caption(4.5, 6.0, "approach", "two"),
            new Caption(6.6, 8.0, "castle", "three"), new Caption(20.0, 21.0, "approach", "four")),
            key -> key, String::length, 100);

    @Test void nothingShowsBeforeTheFirstTransmission() {
        assertTrue(track.at(1.0).empty());
        assertNull(track.at(1.0).tagSpeaker());
    }

    @Test void aPageShowsFullyInsideItsWindowWithItsSpeakerTag() {
        var state = track.at(3.8);
        assertEquals(1, state.pages().size());
        assertEquals("one", state.pages().get(0).text());
        assertEquals(1.0, state.pages().get(0).alpha(), 1e-9);
        assertEquals("approach", state.tagSpeaker());
        assertEquals(1.0, state.tagAlpha(), 1e-9);
    }

    @Test void pagesOfOneTransmissionKeepTheTagUpAcrossTheTurn() {
        assertEquals(1.0, track.at(4.5).tagAlpha(), 1e-9);
        assertEquals("approach", track.at(4.5).tagSpeaker());
    }

    @Test void theFieldBridgesAShortGapAndFadesOverALongOne() {
        assertEquals(1.0, track.at(6.3).fieldAlpha(), 1e-9);
        assertEquals(0.0, track.at(12.0).fieldAlpha(), 1e-9);
    }

    @Test void aTakesPagesShareItsWindowInProportionToTheirLength() {
        // Two sentences of equal length, each two rows at 10 units, so they cannot share a page, split 4.0-8.0 evenly.
        var split = new CaptionTrack(List.of(new Caption(4.0, 8.0, "castle", "Aaaa bbbb cccc. Dddd eeee ffff.")),
                key -> key, String::length, 10);
        assertEquals("Aaaa bbbb cccc.", split.at(5.0).pages().get(0).text());
        assertEquals("Dddd eeee ffff.", split.at(7.0).pages().get(0).text());
        assertEquals("castle", split.at(6.0).tagSpeaker());
        assertEquals(1.0, split.at(6.0).tagAlpha(), 1e-9);
    }

    @Test void speakerChangesSwitchTheTag() {
        assertEquals("castle", track.at(7.0).tagSpeaker());
    }
}
