package com.dillon.starsectormarines.battle.ui.comms;

import com.dillon.starsectormarines.battle.command.reinforcement.CounterattackSystem;
import com.dillon.starsectormarines.battle.world.gen.BiomeKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleCommsFeedTest {

    @Test
    void newestDispatchReplacesCurrentAndExpiresOnRealTime() {
        BattleCommsFeed feed = new BattleCommsFeed();
        BattleCommsFeed.Notice warning = new BattleCommsFeed.Notice(
                "WARNING", "First dispatch", BattleCommsFeed.Tone.WARNING, 8f);
        BattleCommsFeed.Notice outcome = new BattleCommsFeed.Notice(
                "CLEAR", "Second dispatch", BattleCommsFeed.Tone.GOOD_NEWS, 3f);

        feed.post(warning);
        feed.update(2f);
        assertSame(warning, feed.activeNotice());
        assertEquals(6f, feed.remainingSec(), 0.0001f);

        feed.post(outcome);
        assertSame(outcome, feed.activeNotice(), "new state must not queue behind an obsolete warning");
        assertEquals(3f, feed.remainingSec(), 0.0001f);

        feed.update(3f);
        assertNull(feed.activeNotice());
        assertEquals(0f, feed.remainingSec(), 0.0001f);
    }

    @Test
    void counterattackCopyNamesTheDistrictAndReportsEveryDisposition() {
        BattleCommsFeed.Notice warning = CounterattackCommsPresenter.warning(BiomeKind.CITY);
        BattleCommsFeed.Notice launch = CounterattackCommsPresenter.launch(BiomeKind.CITY);
        BattleCommsFeed.Notice success = CounterattackCommsPresenter.outcome(
                BiomeKind.CITY, CounterattackSystem.Resolution.SUCCESS);
        BattleCommsFeed.Notice failure = CounterattackCommsPresenter.outcome(
                BiomeKind.CITY, CounterattackSystem.Resolution.FAILURE);
        BattleCommsFeed.Notice aborted = CounterattackCommsPresenter.outcome(
                BiomeKind.CITY, CounterattackSystem.Resolution.ABORTED);

        assertTrue(warning.body().contains("city district"));
        assertTrue(launch.body().contains("city district"));
        assertTrue(success.body().contains("front has shifted"));
        assertTrue(failure.body().contains("reserve is spent"));
        assertTrue(aborted.body().contains("line is holding"));
        assertEquals(BattleCommsFeed.Tone.DANGER, success.tone());
        assertEquals(BattleCommsFeed.Tone.GOOD_NEWS, failure.tone());
        assertEquals(BattleCommsFeed.Tone.STATUS, aborted.tone());
    }
}
