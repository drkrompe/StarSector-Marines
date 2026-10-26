package com.dillon.starsectormarines.battle.story;

import com.dillon.starsectormarines.battle.story.DustLedgerProgress.Alarm;
import com.dillon.starsectormarines.battle.story.DustLedgerProgress.Broker;
import com.dillon.starsectormarines.battle.story.DustLedgerProgress.Facts;
import com.dillon.starsectormarines.battle.story.DustLedgerProgress.Grade;
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

    @Test void theBrokerFleesAndSurrendersAndHisDeliveryIsTheSideObjectives() {
        var p = new DustLedgerProgress(100f, 4f);
        p.advance(1f, QUIET.withShot());
        assertEquals(Broker.FLEEING, p.broker());
        p.advance(1f, QUIET.withContact());
        assertEquals(Broker.HELD, p.broker());
        p.advance(1f, QUIET.withAtPad());
        assertEquals(Broker.HELD, p.broker(), "a captive walked past the pad does not board");
        p.advance(1f, QUIET.withContact());
        assertEquals(Broker.HELD, p.broker(), "held until the broker side objective sees him to the apron");
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
}
