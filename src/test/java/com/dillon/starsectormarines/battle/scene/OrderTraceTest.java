package com.dillon.starsectormarines.battle.scene;

import com.dillon.starsectormarines.battle.scene.OrderTrace.Sample;
import com.dillon.starsectormarines.battle.scene.OrderTrace.Transition;
import com.dillon.starsectormarines.battle.ui.debug.SquadOrderRecorder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The trace's arithmetic on a synthetic stream: no world, no battle, and a
 * stream short enough that every expected number can be counted by hand.
 */
class OrderTraceTest {

    private static final int SQUAD = 7;

    /** One sample with everything but the layer under test held constant. */
    private static Sample sample(int tick, String goal, boolean planless, int alive,
                                 float x, float y) {
        return new Sample(tick, "ATTACK_MOVE cell:20,6", "ATTACK_MOVE cell:20,6", "",
                goal, "MISSION", "Move", planless, alive, x, y);
    }

    private static OrderTrace traceOf(Sample... samples) {
        OrderTrace trace = new OrderTrace();
        for (Sample s : samples) trace.sample(s.tick(), SQUAD, s);
        return trace;
    }

    @Test
    void countsPlanlessTicksAcrossTheRecordingAndInsideAWindow() {
        OrderTrace trace = traceOf(
                sample(0, "A", true, 6, 0f, 0f),
                sample(1, "A", false, 6, 0f, 0f),
                sample(2, "A", true, 6, 0f, 0f),
                sample(3, "A", true, 6, 0f, 0f),
                sample(4, "A", false, 6, 0f, 0f));

        assertEquals(5, trace.ticks());
        assertEquals(3, trace.planlessTicks(SQUAD));
        assertEquals(2, trace.planlessTicks(SQUAD, 2, 4));
        assertEquals(0, trace.planlessTicks(SQUAD, 4, 4));
        assertEquals(0, trace.planlessTicks(SQUAD, 10, 20), "a window past the end counts nothing");
    }

    @Test
    void findsTheFirstAndLastTickSatisfyingAPredicate() {
        OrderTrace trace = traceOf(
                sample(0, "A", false, 6, 0f, 0f),
                sample(1, "B", false, 6, 0f, 0f),
                sample(2, "A", false, 6, 0f, 0f),
                sample(3, "B", false, 6, 0f, 0f));

        assertEquals(OptionalInt.of(1), trace.firstTick(SQUAD, s -> "B".equals(s.goal())));
        assertEquals(OptionalInt.of(3), trace.lastTick(SQUAD, s -> "B".equals(s.goal())));
        assertEquals(OptionalInt.empty(), trace.firstTick(SQUAD, s -> "C".equals(s.goal())));
    }

    @Test
    void holdsIsTrueOnlyWhenEverySampleInTheWindowAgrees() {
        OrderTrace trace = traceOf(
                sample(0, "A", false, 6, 0f, 0f),
                sample(1, "A", false, 6, 0f, 0f),
                sample(2, "B", false, 6, 0f, 0f));

        assertTrue(trace.holds(SQUAD, 0, 1, s -> "A".equals(s.goal())));
        assertFalse(trace.holds(SQUAD, 0, 2, s -> "A".equals(s.goal())));
    }

    @Test
    void anEmptyWindowDoesNotPassVacuously() {
        OrderTrace trace = traceOf(sample(0, "A", false, 6, 0f, 0f));

        assertFalse(trace.holds(SQUAD, 50, 60, s -> true),
                "a window the recording never reached measured nothing and must not pass");
        assertFalse(trace.holds(SQUAD, 5, 1, s -> true), "an inverted window is empty too");
    }

    @Test
    void transitionsReportEachChangeOfTheLayerAndNotItsFirstValue() {
        OrderTrace trace = traceOf(
                sample(0, "A", false, 6, 0f, 0f),
                sample(1, "A", false, 6, 0f, 0f),
                sample(2, "B", false, 6, 0f, 0f),
                sample(3, "A", false, 6, 0f, 0f));

        List<Transition> transitions = trace.transitions(SQUAD, Sample::goal);
        assertEquals(List.of(new Transition(2, "A", "B"), new Transition(3, "B", "A")), transitions);
    }

    @Test
    void cellsTravelledSumsTheCentroidPathAndSkipsTicksWithNobodyAlive() {
        OrderTrace trace = traceOf(
                sample(0, "A", false, 6, 0f, 0f),
                sample(1, "A", false, 6, 3f, 4f),
                sample(2, "A", false, 0, 99f, 99f),
                sample(3, "A", false, 6, 3f, 8f));

        // 5 for the 3-4-5 leg, 4 for the last, and nothing at all for the tick
        // whose centroid is undefined because nobody was alive to have one.
        assertEquals(9f, trace.cellsTravelled(SQUAD), 1e-4f);
    }

    @Test
    void runsCollapseTheLayerIntoAQuotableLine() {
        OrderTrace trace = traceOf(
                sample(0, "AttackMoveGoal", false, 6, 0f, 0f),
                sample(1, "AttackMoveGoal", false, 6, 0f, 0f),
                sample(2, "AttackMoveGoal", false, 6, 0f, 0f),
                sample(3, "ClearAssignedZoneGoal", false, 6, 0f, 0f));

        assertEquals("AttackMoveGoal×3 → ClearAssignedZoneGoal×1", trace.runs(SQUAD, Sample::goal));
        assertEquals("(no samples)", new OrderTrace().track("gone", 1).runs(1, Sample::goal));
    }

    @Test
    void aMissingSquadIsRecordedRatherThanSkipped() {
        Sample missing = Sample.missing(9);

        assertEquals(0, missing.alive());
        assertTrue(missing.planless());
        assertEquals("", missing.goal());
        assertFalse(missing.playerOrdered());

        OrderTrace trace = traceOf(sample(8, "A", false, 6, 0f, 0f), missing);
        assertEquals("A×1 → (gone)×1", trace.runs(SQUAD, Sample::goal));
    }

    @Test
    void playerOrderedReadsTheRecordersOwnPrefix() {
        Sample commanded = sample(0, "A", false, 6, 0f, 0f);
        Sample ordered = new Sample(1, "CLEAR_ZONE zone:3",
                SquadOrderRecorder.PLAYER_ORDER_PREFIX + "ATTACK_MOVE cell:20,6",
                "", "A", "MISSION", "Move", false, 6, 0f, 0f);

        assertFalse(commanded.playerOrdered());
        assertTrue(ordered.playerOrdered());
    }

    @Test
    void queriesRefuseASquadTheTraceNeverSaw() {
        OrderTrace trace = traceOf(sample(0, "A", false, 6, 0f, 0f));

        assertThrows(IllegalArgumentException.class, () -> trace.samples(99));
        assertThrows(IllegalArgumentException.class, () -> trace.planlessTicks(99));
        assertThrows(IllegalArgumentException.class, () -> trace.label(99));
        assertNull(trace.at(SQUAD, 42), "a tick the squad has no sample for is absent, not unknown");
    }
}
