package com.dillon.starsectormarines.battle.scene;

import com.dillon.starsectormarines.battle.scene.OrderTrace.Sample;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each factory on a hand-counted trace, both ways round — and in every case
 * that the failing detail carries the number it was judged from, because a FAIL
 * line that omits the measurement sends the next reader back to re-play the
 * scene.
 */
class VerdictsTest {

    private static final int SQUAD = 3;

    private static Sample sample(int tick, String goal, boolean planless, int alive) {
        return new Sample(tick, "CLEAR_ZONE zone:4", "CLEAR_ZONE zone:4", "",
                goal, "MISSION", "Move", planless, alive, tick, 0f);
    }

    /** Goal A for ticks 0-2, then B; plan-less on ticks 1 and 5; wiped on tick 5. */
    private static OrderTrace trace() {
        OrderTrace trace = new OrderTrace();
        trace.track("assault", SQUAD);
        trace.sample(0, SQUAD, sample(0, "A", false, 6));
        trace.sample(1, SQUAD, sample(1, "A", true, 6));
        trace.sample(2, SQUAD, sample(2, "A", false, 5));
        trace.sample(3, SQUAD, sample(3, "B", false, 5));
        trace.sample(4, SQUAD, sample(4, "B", false, 2));
        trace.sample(5, SQUAD, sample(5, "B", true, 0));
        return trace;
    }

    @Test
    void planlessAtMostPassesWithinItsAllowanceAndQuotesTheCount() {
        Verdict pass = Verdicts.planlessAtMost("floor", trace(), SQUAD, 0, 5, 2);
        assertTrue(pass.pass());
        assertTrue(pass.detail().contains("2 tick(s)"), pass.detail());

        Verdict fail = Verdicts.planlessAtMost("floor", trace(), SQUAD, 0, 5, 1);
        assertFalse(fail.pass());
        assertTrue(fail.detail().contains("2 tick(s)"), fail.detail());
        assertTrue(fail.detail().contains("allowance 1"), fail.detail());
    }

    @Test
    void heldFailsWithTheRunItActuallyRecorded() {
        Verdict pass = Verdicts.held("order", trace(), SQUAD, 0, 2,
                s -> "A".equals(s.goal()), "goal A");
        assertTrue(pass.pass());

        Verdict fail = Verdicts.held("order", trace(), SQUAD, 0, 5,
                s -> "A".equals(s.goal()), "goal A");
        assertFalse(fail.pass());
        assertTrue(fail.detail().contains("A×3 → B×3"), fail.detail());
    }

    @Test
    void heldFailsOnAWindowTheRecordingNeverReached() {
        Verdict fail = Verdicts.held("order", trace(), SQUAD, 90, 99, s -> true, "anything");
        assertFalse(fail.pass(), "a verdict that passes by measuring nothing is worse than none");
    }

    @Test
    void firstTickWithinNamesTheTickOrSaysItNeverHappened() {
        Verdict pass = Verdicts.firstTickWithin("switch", trace(), SQUAD,
                s -> "B".equals(s.goal()), 2, 4, "switched to B");
        assertTrue(pass.pass());
        assertTrue(pass.detail().contains("tick 3"), pass.detail());

        Verdict early = Verdicts.firstTickWithin("switch", trace(), SQUAD,
                s -> "B".equals(s.goal()), 4, 5, "switched to B");
        assertFalse(early.pass());
        assertTrue(early.detail().contains("tick 3"), early.detail());

        Verdict never = Verdicts.firstTickWithin("switch", trace(), SQUAD,
                s -> "C".equals(s.goal()), 0, 5, "switched to C");
        assertFalse(never.pass());
        assertTrue(never.detail().contains("never"), never.detail());
    }

    @Test
    void latencyMeasuresTheDelayFromTheTickThatCausedIt() {
        Verdict pass = Verdicts.latency("handback", trace(), SQUAD, 2,
                s -> "B".equals(s.goal()), 1, "goal handed back");
        assertTrue(pass.pass());
        assertTrue(pass.detail().contains("1 tick(s)"), pass.detail());

        Verdict slow = Verdicts.latency("handback", trace(), SQUAD, 0,
                s -> "B".equals(s.goal()), 1, "goal handed back");
        assertFalse(slow.pass());
        assertTrue(slow.detail().contains("3 tick(s)"), slow.detail());

        Verdict never = Verdicts.latency("handback", trace(), SQUAD, 4,
                s -> "A".equals(s.goal()), 5, "goal handed back");
        assertFalse(never.pass(), "a predicate satisfied only before fromTick is not a latency");
        assertTrue(never.detail().contains("never"), never.detail());
    }

    @Test
    void aliveQuotesTheStrengthAtTheTickAsked() {
        Verdict pass = Verdicts.alive("survivors", trace(), SQUAD, 4, 2);
        assertTrue(pass.pass());
        assertTrue(pass.detail().contains("2 alive at tick 4"), pass.detail());

        Verdict fail = Verdicts.alive("survivors", trace(), SQUAD, 5, 1);
        assertFalse(fail.pass());
        assertTrue(fail.detail().contains("0 alive at tick 5"), fail.detail());

        Verdict absent = Verdicts.alive("survivors", trace(), SQUAD, 99, 1);
        assertFalse(absent.pass());
        assertTrue(absent.detail().contains("no sample at tick 99"), absent.detail());
    }

    @Test
    void aVerdictKeepsTheNameItWasAskedUnder() {
        assertEquals("floor", Verdicts.planlessAtMost("floor", trace(), SQUAD, 0, 5, 0).name());
    }
}
