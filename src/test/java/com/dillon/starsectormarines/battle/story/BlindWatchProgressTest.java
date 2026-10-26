package com.dillon.starsectormarines.battle.story;

import com.dillon.starsectormarines.battle.audio.MusicState;
import com.dillon.starsectormarines.battle.audio.QuietBed;
import com.dillon.starsectormarines.battle.story.BlindWatchProgress.Alarm;
import com.dillon.starsectormarines.battle.story.BlindWatchProgress.Facts;
import com.dillon.starsectormarines.battle.story.BlindWatchProgress.Warning;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlindWatchProgressTest {
    private static final Facts QUIET = Facts.quiet();

    @Test void quietHoldsUntilATriggerAndTheFirstStands() {
        var p = new BlindWatchProgress(10f, false);
        p.advance(30f, QUIET);
        assertTrue(p.quiet());
        assertEquals(MusicState.QUIET, p.musicState());
        assertEquals(QuietBed.SEQUENCER_PULSE, p.quietBed());
        assertEquals(Warning.WAITING, p.warning());
        p.advance(1f, QUIET.withMastSighted().withSighted());
        assertEquals(Alarm.SIGHTED, p.alarm());
        assertEquals(31f, p.alarmAt());
        assertEquals(MusicState.COMBAT, p.musicState());
        p.advance(1f, QUIET.withShot());
        assertEquals(Alarm.SIGHTED, p.alarm());
    }

    @Test void eachTriggerRaisesTheAlarmButAFallenMastSeesNothing() {
        var shot = new BlindWatchProgress(10f, false);
        shot.advance(1f, QUIET.withShot());
        assertEquals(Alarm.SHOTS, shot.alarm());
        var mast = new BlindWatchProgress(10f, false);
        mast.advance(1f, QUIET.withMastSighted());
        assertEquals(Alarm.MAST, mast.alarm());
        var blind = new BlindWatchProgress(10f, false);
        blind.advance(1f, QUIET.withMastDown());
        blind.advance(1f, QUIET.withMastSighted());
        assertTrue(blind.quiet());
        assertFalse(blind.mastStanding());
    }

    @Test void theWarningRunsFromTheAlarmAndReachesGarnir() {
        var p = new BlindWatchProgress(10f, false);
        p.advance(1f, QUIET.withShot());
        assertEquals(Warning.SENDING, p.warning());
        p.advance(4f, QUIET);
        assertEquals(.5f, p.sentShare(), 1e-4);
        assertEquals(5f, p.warningRemaining(), 1e-4);
        p.advance(6f, QUIET);
        assertEquals(Warning.SENT, p.warning());
        assertTrue(p.garnirWarned());
        p.advance(1f, QUIET.withUplinkDown());
        assertTrue(p.garnirWarned(), "blowing the uplink after the warning went changes nothing");
    }

    @Test void theUplinkChargeStopsTheWarningEvenOnTheTickItWouldFill() {
        var p = new BlindWatchProgress(10f, false);
        p.advance(1f, QUIET.withShot());
        p.advance(8f, QUIET);
        p.advance(2f, QUIET.withUplinkDown());
        assertEquals(Warning.STOPPED, p.warning());
        assertFalse(p.garnirWarned());
        assertEquals(.9f, p.sentShare(), 1e-4, "the share sent stays reported");
        var early = new BlindWatchProgress(10f, false);
        early.advance(1f, QUIET.withUplinkDown());
        early.advance(1f, QUIET.withShot());
        assertEquals(Warning.STOPPED, early.warning(), "blown before the alarm, it never starts");
        assertEquals(0f, early.sentShare());
    }

    @Test void theFalseScheduleHoldsOnlyWhileTheUplinkStandsAndTheWarningIsUnsent() {
        var p = new BlindWatchProgress(10f, false);
        p.advance(1f, QUIET.withShot());
        p.advance(3f, QUIET);
        p.falseSchedule();
        assertEquals(Warning.FALSE_SCHEDULE, p.warning());
        p.advance(20f, QUIET);
        assertFalse(p.garnirWarned(), "the warning can no longer complete");
        p.advance(1f, QUIET.withUplinkDown());
        assertEquals(Warning.FALSE_SCHEDULE, p.warning());

        var down = new BlindWatchProgress(10f, false);
        down.advance(1f, QUIET.withUplinkDown());
        down.falseSchedule();
        assertEquals(Warning.STOPPED, down.warning());
        var sent = new BlindWatchProgress(10f, false);
        sent.advance(1f, QUIET.withShot());
        sent.advance(10f, QUIET);
        sent.falseSchedule();
        assertTrue(sent.garnirWarned());
    }

    @Test void anEscapedTeagueStandsThePostTo() {
        assertTrue(BlindWatchProgress.stoodTo(Set.<String>of()::contains));
        assertFalse(BlindWatchProgress.stoodTo(Set.of(BlindWatchProgress.TEAGUE_DELIVERED)::contains));
        assertEquals(6, BlindWatchProgress.sentries(3, true));
        assertEquals(3, BlindWatchProgress.sentries(3, false));
        assertTrue(new BlindWatchProgress(10f, true).sightScale() > 1f);
        assertEquals(1f, new BlindWatchProgress(10f, false).sightScale());
    }

    @Test void theSitesFallIndependently() {
        var p = new BlindWatchProgress(10f, false);
        p.advance(1f, QUIET.withDishesDown());
        assertFalse(p.dishesStanding());
        assertTrue(p.mastStanding());
        assertTrue(p.uplinkStanding());
    }
}
