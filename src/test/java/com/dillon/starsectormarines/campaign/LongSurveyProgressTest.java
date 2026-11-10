package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.campaign.LongSurveyProgress.Chapter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LongSurveyProgressTest {

    private static int play(LongSurveyProgress p, String id, boolean victory) {
        assertTrue(p.begin(id));
        return p.settle(id, victory, victory ? LongSurveyProgress.fee(id) : 0);
    }

    @Test
    void aLossLeavesTheChapterOnOfferAndOnlyAVictoryUnlocksTheNext() {
        var p = new LongSurveyProgress();
        assertEquals(Chapter.THE_FALL, p.current());
        assertFalse(p.matches(p.missionId(Chapter.STAKES, false)));

        assertEquals(0, play(p, p.missionId(Chapter.THE_FALL, false), false));
        assertEquals(Chapter.THE_FALL, p.current());
        assertTrue(p.lastLost(Chapter.THE_FALL));
        assertEquals(0, p.paid());

        assertEquals(Chapter.THE_FALL.fee, play(p, p.missionId(Chapter.THE_FALL, false), true));
        assertEquals(Chapter.STAKES, p.current());
        assertFalse(p.lastLost(Chapter.THE_FALL));
    }

    @Test
    void aChapterNotBuiltIsNeverLaunched() {
        var p = new LongSurveyProgress();
        play(p, p.missionId(Chapter.THE_FALL, false), true);
        assertFalse(Chapter.STAKES.built);
        assertEquals(Chapter.STAKES, p.current());
        assertFalse(p.begin(p.missionId(Chapter.STAKES, false)));
    }

    @Test
    void aReplayPaysHalfAndNeverMovesTheArc() {
        var p = new LongSurveyProgress();
        assertFalse(p.matches(p.missionId(Chapter.THE_FALL, true)));
        play(p, p.missionId(Chapter.THE_FALL, false), true);

        String replay = p.missionId(Chapter.THE_FALL, true);
        assertEquals(Chapter.THE_FALL, LongSurveyProgress.chapterOf(replay));
        assertEquals(Chapter.THE_FALL.fee / 2, play(p, replay, true));
        assertEquals(0, play(p, p.missionId(Chapter.THE_FALL, true), false));
        assertEquals(1, p.won());
        assertEquals(1, p.replaysWon());
        assertFalse(p.lastLost(Chapter.THE_FALL));
        assertEquals(Chapter.STAKES, p.current());
    }

    @Test
    void onlyTheActiveAttemptSettlesAndOnlyOnce() {
        var p = new LongSurveyProgress();
        String id = p.missionId(Chapter.THE_FALL, false);
        assertEquals(-1, p.settle(id, true, Chapter.THE_FALL.fee));
        assertTrue(p.begin(id));
        assertFalse(p.begin(id));
        assertEquals(-1, p.settle(id, true, 1));
        assertEquals(-1, p.settle(id, false, Chapter.THE_FALL.fee));
        assertEquals(Chapter.THE_FALL.fee, p.settle(id, true, Chapter.THE_FALL.fee));
        assertEquals(-1, p.settle(id, true, Chapter.THE_FALL.fee));
        assertEquals(Chapter.THE_FALL.fee, p.paid());
    }

    @Test
    void anAbandonedLaunchCanLaunchAgain() {
        var p = new LongSurveyProgress();
        String id = p.missionId(Chapter.THE_FALL, false);
        assertTrue(p.begin(id));
        assertTrue(p.abandon(id));
        assertFalse(p.active());
        assertTrue(p.begin(id));
    }
}
