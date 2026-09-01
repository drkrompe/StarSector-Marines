package com.dillon.starsectormarines.battle.squad;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The freshness rule behind the shared flank aim. */
class FlankAimMemoTest {

    @Test
    @DisplayName("nothing is fresh before anything is stored")
    void neverFreshBeforeStore() {
        FlankAimMemo memo = new FlankAimMemo();
        assertFalse(memo.isFresh(7L, 3, 4, 0), "empty memo answers nothing");
        assertEquals(-1, memo.computedTick(), "no tick to report yet");
    }

    @Test
    @DisplayName("the same question inside the window gets the stored answer")
    void freshOnSameKeyWithinWindow() {
        FlankAimMemo memo = new FlankAimMemo();
        memo.store(7L, 3, 4, 100, 12, 15, false);

        assertTrue(memo.isFresh(7L, 3, 4, 100), "same tick");
        assertTrue(memo.isFresh(7L, 3, 4, 100 + FlankAimMemo.REFRESH_TICKS - 1),
                "last tick inside the window");
        assertEquals(12, memo.aimX());
        assertEquals(15, memo.aimY());
        assertFalse(memo.refused());
        assertEquals(100, memo.computedTick());
    }

    @Test
    @DisplayName("the answer goes stale after the refresh window")
    void staleAfterRefreshTicks() {
        FlankAimMemo memo = new FlankAimMemo();
        memo.store(7L, 3, 4, 100, 12, 15, false);

        assertFalse(memo.isFresh(7L, 3, 4, 100 + FlankAimMemo.REFRESH_TICKS),
                "first tick past the window");
    }

    @Test
    @DisplayName("a different contact is a different question")
    void staleOnContactChange() {
        FlankAimMemo memo = new FlankAimMemo();
        memo.store(7L, 3, 4, 100, 12, 15, false);

        assertFalse(memo.isFresh(8L, 3, 4, 100));
    }

    @Test
    @DisplayName("a contact that moved a cell is a different question")
    void staleOnRawCellChange() {
        FlankAimMemo memo = new FlankAimMemo();
        memo.store(7L, 3, 4, 100, 12, 15, false);

        assertFalse(memo.isFresh(7L, 4, 4, 100), "raw x moved");
        assertFalse(memo.isFresh(7L, 3, 5, 100), "raw y moved");
    }

    @Test
    @DisplayName("a refusal is memoised too, so it costs the search once")
    void refusalIsRemembered() {
        FlankAimMemo memo = new FlankAimMemo();
        memo.store(7L, 3, 4, 100, 20, 20, true);

        assertTrue(memo.isFresh(7L, 3, 4, 105));
        assertTrue(memo.refused());
    }
}
