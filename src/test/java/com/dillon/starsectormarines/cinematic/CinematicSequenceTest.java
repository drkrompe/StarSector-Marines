package com.dillon.starsectormarines.cinematic;

import com.dillon.starsectormarines.cinematic.CinematicShot.Cue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CinematicSequenceTest {
    private static CinematicShot shot(String id, int frames, List<Cue> cues) {
        return new CinematicShot(id, "v/" + id + ".mp4", 24, 1920, 1080, frames, frames / 24.0,
                Map.of(), List.of(), List.of(), cues);
    }

    private final CinematicSequence sequence = new CinematicSequence(List.of(
            shot("a", 48, List.of(new Cue(1.0, "hail", 1f, Double.NaN, 0, 0),
                    new Cue(0.0, "bed", 0.8f, 3.0, 1f, 1f))),
            shot("b", 24, List.of(new Cue(0.5, "later", 1f, Double.NaN, 0, 0)))));

    @Test void shotsAreLaidEndToEndAndCuesMoveWithThem() {
        assertEquals(3.0, sequence.duration(), 1e-9);
        assertEquals(2.0, sequence.offset(1), 1e-9);
        var starts = sequence.cues().stream().map(c -> c.cue().sound() + "@" + c.start()).toList();
        assertEquals(List.of("bed@0.0", "hail@1.0", "later@2.5"), starts);
    }

    @Test void theClockPicksTheShotAndAFrameWithinItsRange() {
        assertEquals(0, sequence.shotAt(1.99));
        assertEquals(1, sequence.shotAt(2.0));
        assertEquals(47, sequence.frameAt(0, 10.0));
        assertEquals(12, sequence.frameAt(1, 2.5));
        assertEquals(0, sequence.frameAt(1, 1.0));
    }

    @Test void aLoopedCueRampsInAndOutAndIsSilentOutsideItsSpan() {
        var bed = sequence.cues().get(0);
        assertEquals(0.5f * 0.8f, bed.loopVolume(0.5), 1e-6f);
        assertEquals(0.8f, bed.loopVolume(1.5), 1e-6f);
        assertEquals(0.5f * 0.8f, bed.loopVolume(2.5), 1e-6f);
        assertEquals(0f, bed.loopVolume(3.0), 0f);
    }

    @Test void anEmptySequenceIsEmpty() {
        assertTrue(new CinematicSequence(List.of()).isEmpty());
    }
}
