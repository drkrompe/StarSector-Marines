package com.dillon.starsectormarines.battle.power;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The barrage's timetable: each line fires from its opening second until it lifts, and lines never overlap. */
class WalkingBarrageTest {

    private static final WalkingBarrage.Shell SHELL = new WalkingBarrage.Shell(3f, 70f, 20f, 60, 3f);

    @Test
    void eachLineFiresFromItsOpeningUntilItLifts() {
        WalkingBarrage.Plan plan = new WalkingBarrage.Plan(List.of(
                new WalkingBarrage.Line(0.3f, 10f, 20f, 400),
                new WalkingBarrage.Line(0.4f, 30f, 15f, 0),
                new WalkingBarrage.Line(0.5f, 50f, 15f, 0)), SHELL, 2f, 0.01f);
        assertEquals(-1, plan.firingAt(9.9f), "nothing before the first line opens");
        assertEquals(0, plan.firingAt(10f));
        assertEquals(1, plan.firingAt(30f), "the next line takes over as the first lifts");
        assertEquals(-1, plan.firingAt(47f), "a pause between lines is a pause");
        assertEquals(2, plan.firingAt(64.9f));
        assertEquals(-1, plan.firingAt(65f), "nothing past the last line");
        assertEquals(65f, plan.endSeconds());
    }

    @Test
    void aLineMayNotOpenBeforeTheLastLifts() {
        assertThrows(IllegalArgumentException.class, () -> new WalkingBarrage.Plan(List.of(
                new WalkingBarrage.Line(0.3f, 10f, 20f, 0),
                new WalkingBarrage.Line(0.4f, 25f, 15f, 0)), SHELL, 2f, 0.01f));
    }
}
