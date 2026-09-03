package com.dillon.starsectormarines.battle.vision;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The bookkeeping a consumer of the reveal bitmap's changed extent relies on. */
final class RevealChangeLogTest {

    private final int[] out = new int[4];

    @Test
    void anUpdateWithNothingNotedIsNotAnUpdate() {
        RevealChangeLog log = new RevealChangeLog();
        log.init(8, 8);

        log.seal();
        log.seal();

        assertEquals(0L, log.changeCount(),
                "a vision tick that moved no cell must leave a caught-up reader idle");
    }

    @Test
    void oneSealedUpdateBoundsWhatWasNoted() {
        RevealChangeLog log = new RevealChangeLog();
        log.init(8, 8);

        log.note(2 * 8 + 3);
        log.note(5 * 8 + 1);
        log.seal();

        assertEquals(1L, log.changeCount());
        assertTrue(log.unionSince(0L, out));
        assertArrayEquals(new int[]{1, 2, 3, 5}, out);
    }

    @Test
    void aReaderSeesTheUnionOfEveryUpdateItMissed() {
        RevealChangeLog log = new RevealChangeLog();
        log.init(8, 8);

        log.note(0);
        log.seal();
        long afterFirst = log.changeCount();
        log.note(7 * 8 + 7);
        log.seal();

        assertTrue(log.unionSince(0L, out));
        assertArrayEquals(new int[]{0, 0, 7, 7}, out);

        assertTrue(log.unionSince(afterFirst, out),
                "a reader that saw the first update must be told only about the second");
        assertArrayEquals(new int[]{7, 7, 7, 7}, out);
    }

    @Test
    void aCaughtUpReaderIsToldNothingChanged() {
        RevealChangeLog log = new RevealChangeLog();
        log.init(8, 8);
        log.note(9);
        log.seal();

        assertTrue(log.unionSince(log.changeCount(), out));
        assertTrue(out[2] < out[0], "an empty union has to be distinguishable from a real one");
    }

    @Test
    void aReaderThatFellOffTheEndOfTheRingIsToldToRebuild() {
        RevealChangeLog log = new RevealChangeLog();
        log.init(8, 8);
        for (int i = 0; i <= RevealChangeLog.capacity(); i++) {
            log.note(i % 64);
            log.seal();
        }

        assertFalse(log.unionSince(0L, out),
                "a reader further back than the ring cannot be answered from it");
        assertTrue(log.unionSince(log.changeCount() - RevealChangeLog.capacity(), out),
                "exactly the ring's depth is still answerable");
    }

    @Test
    void aWholesaleChangeBoundsTheGrid() {
        RevealChangeLog log = new RevealChangeLog();
        log.init(5, 3);

        log.noteAll();
        log.seal();

        assertTrue(log.unionSince(0L, out));
        assertArrayEquals(new int[]{0, 0, 4, 2}, out);
    }

    @Test
    void bindingToANewGridForgetsTheOldOne() {
        RevealChangeLog log = new RevealChangeLog();
        log.init(8, 8);
        log.note(3);
        log.seal();

        log.init(4, 4);

        assertEquals(0L, log.changeCount());
        assertTrue(log.unionSince(0L, out));
        assertTrue(out[2] < out[0]);
    }
}
