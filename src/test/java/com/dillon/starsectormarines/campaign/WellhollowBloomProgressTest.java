package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.campaign.WellhollowBloomProgress.Chapter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WellhollowBloomProgressTest {

    private static int play(WellhollowBloomProgress p, String id, boolean victory) {
        assertTrue(p.begin(id));
        return p.settle(id, victory, victory ? WellhollowBloomProgress.fee(id) : 0);
    }

    @Test
    void chaptersUnlockInOrderOnVictoryOnly() {
        var p = new WellhollowBloomProgress();
        assertEquals(Chapter.COLONY_BREACH, p.current());
        assertFalse(p.matches(p.missionId(Chapter.CORDON_LINE, false)));

        assertEquals(0, play(p, p.missionId(Chapter.COLONY_BREACH, false), false));
        assertEquals(Chapter.COLONY_BREACH, p.current());
        assertTrue(p.lastLost(Chapter.COLONY_BREACH));

        for (Chapter chapter : Chapter.values()) {
            assertEquals(chapter, p.current());
            assertEquals(chapter.fee, play(p, p.missionId(chapter, false), true));
        }
        assertTrue(p.complete());
        assertNull(p.current());
        assertFalse(p.lastLost(Chapter.COLONY_BREACH));
    }

    @Test
    void aReplayPaysHalfAndNeverMovesTheArc() {
        var p = new WellhollowBloomProgress();
        assertFalse(p.matches(p.missionId(Chapter.COLONY_BREACH, true)));
        play(p, p.missionId(Chapter.COLONY_BREACH, false), true);

        String replay = p.missionId(Chapter.COLONY_BREACH, true);
        assertTrue(WellhollowBloomProgress.replay(replay));
        assertEquals(Chapter.COLONY_BREACH, WellhollowBloomProgress.chapterOf(replay));
        assertEquals(Chapter.COLONY_BREACH.fee / 2, play(p, replay, true));
        assertEquals(1, p.won());
        assertEquals(1, p.replaysWon());
        assertEquals(Chapter.CORDON_LINE, p.current());
        assertFalse(p.matches(p.missionId(Chapter.CORDON_LINE, true)));
    }

    @Test
    void onlyTheActiveAttemptSettlesAndOnlyOnce() {
        var p = new WellhollowBloomProgress();
        String id = p.missionId(Chapter.COLONY_BREACH, false);
        assertEquals(-1, p.settle(id, true, Chapter.COLONY_BREACH.fee));
        assertTrue(p.begin(id));
        assertFalse(p.begin(id));
        assertEquals(-1, p.settle(id, true, 1));
        assertEquals(Chapter.COLONY_BREACH.fee, p.settle(id, true, Chapter.COLONY_BREACH.fee));
        assertEquals(-1, p.settle(id, true, Chapter.COLONY_BREACH.fee));
        assertEquals(Chapter.COLONY_BREACH.fee, p.paid());
    }

    @Test
    void anAbandonedLaunchCanLaunchAgain() {
        var p = new WellhollowBloomProgress();
        String id = p.missionId(Chapter.COLONY_BREACH, false);
        assertTrue(p.begin(id));
        assertTrue(p.abandon(id));
        assertFalse(p.active());
        assertTrue(p.begin(id));
    }
}
