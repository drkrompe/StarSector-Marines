package com.dillon.starsectormarines.cinematic;

import com.dillon.starsectormarines.cinematic.CinematicShot.Caption;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test void oneSpeakerAtATimeIsOneTransmission() {
        assertEquals(1, track.at(3.8).transmissions().size());
        assertFalse(track.at(4.5).overlapping());
        assertEquals("castle", track.at(7.0).transmissions().get(0).speaker());
    }

    @Test void overlappingTransmissionsEachKeepTheirOwnTagAndTextEarliestFirst() {
        // Caravel 1-6 in two back-to-back takes, Raider keys up over it 2-4, Warden over both 3-5.
        var busy = new CaptionTrack(List.of(
                new Caption(1.0, 3.5, "caravel", "c1"), new Caption(3.5, 6.0, "caravel", "c2"),
                new Caption(2.0, 4.0, "raider", "r1"), new Caption(3.0, 5.0, "warden", "w1")),
                key -> key, String::length, 100);
        var three = busy.at(3.2);
        assertTrue(three.overlapping());
        assertEquals(List.of("caravel", "raider", "warden"),
                three.transmissions().stream().map(CaptionTrack.Transmission::speaker).toList());
        assertEquals("c1", three.transmissions().get(0).pages().get(0).text());
        assertEquals("r1", three.transmissions().get(1).pages().get(0).text());
        assertEquals("w1", three.transmissions().get(2).pages().get(0).text());
        // Caravel's second take continues its transmission across the others: its tag holds.
        var later = busy.at(4.5);
        assertEquals(2, later.transmissions().size());
        assertEquals("c2", later.transmissions().get(0).pages().get(0).text());
        assertEquals(1.0, later.transmissions().get(0).tagAlpha(), 1e-9);
        assertEquals(1, busy.at(5.5).transmissions().size());
    }
}
