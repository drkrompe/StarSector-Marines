package com.dillon.starsectormarines.battle.story;

import com.dillon.starsectormarines.battle.story.DustLedgerProgress.Alarm;
import com.dillon.starsectormarines.battle.story.DustLedgerProgress.Broker;
import com.dillon.starsectormarines.battle.story.DustLedgerProgress.Facts;
import com.dillon.starsectormarines.battle.story.DustLedgerProgress.Grade;
import com.dillon.starsectormarines.battle.story.DustLedgerProgress.Side;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DustLedgerProgressTest {
    private static final Facts QUIET = Facts.quiet();

    @Test void quietHoldsUntilAListedTrigger() {
        var p = new DustLedgerProgress(10f, 4f);
        p.advance(9.5f, QUIET);
        assertTrue(p.quiet());
        assertEquals(.5f, p.clockRemaining(), 1e-4);
        assertEquals(0f, p.burnedShare());
        assertEquals(Broker.IN_OFFICE, p.broker());
    }

    @Test void eachTriggerRaisesTheAlarm() {
        var shots = new DustLedgerProgress(10f, 4f);
        shots.advance(1f, QUIET.withShot());
        assertEquals(Alarm.SHOTS, shots.alarm());
        var seen = new DustLedgerProgress(10f, 4f);
        seen.advance(1f, QUIET.withSighted());
        assertEquals(Alarm.SIGHTED, seen.alarm());
        var clock = new DustLedgerProgress(10f, 4f);
        clock.advance(10f, QUIET);
        assertEquals(Alarm.CLOCK, clock.alarm());
        assertEquals(0f, clock.clockRemaining());
        // The first trigger stands.
        shots.advance(20f, QUIET.withSighted());
        assertEquals(Alarm.SHOTS, shots.alarm());
        assertEquals(1f, shots.alarmAt());
    }

    @Test void theClockTakesTheBrokerAwayButNotOneAlreadyHeld() {
        var gone = new DustLedgerProgress(10f, 4f);
        gone.advance(10f, QUIET);
        assertEquals(Broker.ESCAPED, gone.broker());
        var held = new DustLedgerProgress(10f, 4f);
        held.advance(1f, QUIET.withContact());
        held.advance(10f, QUIET);
        assertEquals(Alarm.CLOCK, held.alarm());
        assertEquals(Broker.HELD, held.broker());
    }

    @Test void theBurnRunsFromTheAlarmAndStopsAtSeizureWithItsGrade() {
        var p = new DustLedgerProgress(100f, 4f);
        p.advance(1f, QUIET.withShot());
        assertTrue(p.burning());
        assertEquals(.25f, p.burnedShare(), 1e-4);
        assertNull(p.grade());
        p.advance(1f, QUIET.withSeized());
        assertTrue(p.seized());
        assertEquals(.75f, p.unburnedShare(), 1e-4);
        assertEquals(Grade.WHOLE, p.grade());
        p.advance(10f, QUIET);
        assertEquals(.25f, p.burnedShare(), 1e-4, "seizure stops the burn for good");
        assertFalse(p.burning());
    }

    @Test void aFullyBurnedLedgerIsStillSeizedAsAshes() {
        var p = new DustLedgerProgress(100f, 4f);
        p.advance(1f, QUIET.withShot());
        p.advance(2f, QUIET);
        assertEquals(.75f, p.burnedShare(), 1e-4);
        p.advance(5f, QUIET);
        assertEquals(1f, p.burnedShare());
        p.advance(1f, QUIET.withSeized());
        assertEquals(Grade.ASHES, p.grade());
        assertEquals(0f, p.unburnedShare());
    }

    @Test void aLedgerSeizedBeforeTheAlarmIsWhole() {
        var p = new DustLedgerProgress(100f, 4f);
        p.advance(1f, QUIET.withSeized());
        assertTrue(p.quiet());
        assertEquals(1f, p.unburnedShare());
        p.advance(1f, QUIET.withShot());
        assertEquals(0f, p.burnedShare(), "nothing left to burn once the room is taken");
    }

    @Test void theBrokerFleesSurrendersFollowsAndIsDelivered() {
        var p = new DustLedgerProgress(100f, 4f);
        p.advance(1f, QUIET.withShot());
        assertEquals(Broker.FLEEING, p.broker());
        p.advance(1f, QUIET.withContact());
        assertEquals(Broker.HELD, p.broker());
        p.advance(1f, QUIET.withAtPad());
        assertEquals(Broker.HELD, p.broker(), "a captive walked past the pad does not board");
        p.advance(1f, QUIET.withAtApron());
        assertEquals(Broker.DELIVERED, p.broker());
        p.advance(1f, QUIET.withBrokerDead());
        assertEquals(Broker.DELIVERED, p.broker());
    }

    @Test void theBrokerEscapesAtThePadOrDiesOnTheWay() {
        var boards = new DustLedgerProgress(100f, 4f);
        boards.advance(1f, QUIET.withShot());
        boards.advance(1f, QUIET.withAtPad());
        assertEquals(Broker.ESCAPED, boards.broker());
        boards.advance(1f, QUIET.withContact());
        assertEquals(Broker.ESCAPED, boards.broker());
        var killed = new DustLedgerProgress(100f, 4f);
        killed.advance(1f, QUIET.withShot());
        killed.advance(1f, QUIET.withContact());
        killed.advance(1f, QUIET.withBrokerDead());
        assertEquals(Broker.DEAD, killed.broker());
    }

    @Test void theCouplingPaysOnlyIfDeliveredBeforeTheAlarm() {
        var early = new DustLedgerProgress(100f, 4f);
        early.advance(1f, QUIET.withCrateInYard());
        assertEquals(Side.PAID, early.coupling());
        early.advance(1f, QUIET.withShot());
        assertTrue(early.salvageTeamJoins());

        var sameTick = new DustLedgerProgress(100f, 4f);
        sameTick.advance(1f, QUIET.withCrateInYard().withShot());
        assertTrue(sameTick.salvageTeamJoins());

        var late = new DustLedgerProgress(100f, 4f);
        late.advance(1f, QUIET.withShot());
        assertEquals(Side.LAPSED, late.coupling());
        late.advance(1f, QUIET.withCrateInYard());
        assertEquals(Side.LAPSED, late.coupling());
        assertFalse(late.salvageTeamJoins());
    }

    @Test void theWardensNodNeedsAnUnbrokenStayBeforeTheAlarm() {
        float half = DustLedgerProgress.NOD_SECONDS / 2;
        var p = new DustLedgerProgress(100f, 4f);
        p.advance(half, QUIET.withLeaderAtWell());
        p.advance(.1f, QUIET);
        assertEquals(0f, p.nodProgress(), "walking off restarts the wait");
        p.advance(half, QUIET.withLeaderAtWell());
        assertEquals(Side.OPEN, p.warden());
        assertTrue(p.drinkersJoin());
        p.advance(half, QUIET.withLeaderAtWell());
        assertEquals(Side.PAID, p.warden());
        assertTrue(p.spottersUp());
        assertFalse(p.drinkersJoin());
        p.advance(1f, QUIET.withShot());
        assertFalse(p.spottersUp(), "the spotters stand down at the alarm");
        assertFalse(p.drinkersJoin(), "the watch still holds the cantina");

        var interrupted = new DustLedgerProgress(100f, 4f);
        interrupted.advance(half, QUIET.withLeaderAtWell());
        interrupted.advance(.1f, QUIET.withLeaderAtWell().withShot());
        assertEquals(Side.LAPSED, interrupted.warden(), "a nod still under way at the alarm pays nothing");
        assertTrue(interrupted.drinkersJoin());
    }

    @Test void cuttingTheFeedNeedsAnUnbrokenStintBeforeTheAlarmAndDarkensTheNests() {
        float half = DustLedgerProgress.FEED_SECONDS / 2;
        var cut = new DustLedgerProgress(100f, 4f);
        cut.advance(half, QUIET.withAtFeed());
        cut.advance(.1f, QUIET);
        assertEquals(0f, cut.feedProgress(), "leaving the junction restarts the work");
        cut.advance(half, QUIET.withAtFeed());
        assertEquals(Side.OPEN, cut.feed());
        assertTrue(cut.nestsPowered());
        cut.advance(half, QUIET.withAtFeed());
        assertEquals(Side.PAID, cut.feed());
        assertFalse(cut.nestsPowered());
        cut.advance(1f, QUIET.withShot());
        assertFalse(cut.nestsPowered(), "a cut feed stays cut through the alarm");

        var late = new DustLedgerProgress(100f, 4f);
        late.advance(half, QUIET.withAtFeed());
        late.advance(.1f, QUIET.withAtFeed().withShot());
        assertEquals(Side.LAPSED, late.feed(), "work under way at the alarm pays nothing");
        late.advance(DustLedgerProgress.FEED_SECONDS, QUIET.withAtFeed());
        assertEquals(Side.LAPSED, late.feed());
        assertTrue(late.nestsPowered());
    }
}
