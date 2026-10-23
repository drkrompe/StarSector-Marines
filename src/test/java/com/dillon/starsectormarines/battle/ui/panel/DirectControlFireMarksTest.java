package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.combat.ManualPointFire;
import com.dillon.starsectormarines.battle.combat.PointAimDispersion;
import com.dillon.starsectormarines.battle.ui.panel.DirectControlFireMarks.Mark;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectControlFireMarksTest {
    @Test
    void aMarkReadsTheDistanceAgainstOptimalAndMaximumRange() {
        assertEquals(Mark.GOOD, DirectControlFireMarks.of(true, 10f, 15f, 30f));
        assertEquals(Mark.GOOD, DirectControlFireMarks.of(true, 15f, 15f, 30f), "optimal itself is still good");
        assertEquals(Mark.FALLING_OFF, DirectControlFireMarks.of(true, 20f, 15f, 30f));
        assertEquals(Mark.FALLING_OFF, DirectControlFireMarks.of(true, 30f, 15f, 30f), "maximum itself still fires");
        assertEquals(Mark.WASTED, DirectControlFireMarks.of(true, 31f, 15f, 30f));
    }

    @Test
    void aMountThatWouldNotFireIsHeldAtAnyDistance() {
        assertEquals(Mark.HELD, DirectControlFireMarks.of(false, 5f, 15f, 30f));
        assertEquals(Mark.HELD, DirectControlFireMarks.of(false, 50f, 15f, 30f));
    }

    @Test
    void linkedShowsTheBestOfItsMountsAndIsHeldOnlyWhenNoneWouldFire() {
        assertEquals(Mark.GOOD, Mark.HELD.best(Mark.GOOD));
        assertEquals(Mark.FALLING_OFF, Mark.WASTED.best(Mark.FALLING_OFF));
        assertEquals(Mark.WASTED, Mark.HELD.best(Mark.WASTED));
        assertEquals(Mark.HELD, Mark.NONE.best(Mark.HELD).best(Mark.HELD));
        assertEquals(Mark.NONE, Mark.NONE.best(Mark.NONE), "an empty button draws nothing");
    }

    @Test
    void theScatterRingIsGreyTooCloseAndRedPastRange() {
        assertEquals(Mark.HELD, new DirectControlScatter.Ring(0.1f, false, true, false).mark());
        assertEquals(Mark.WASTED, new DirectControlScatter.Ring(3f, true, false, true).mark());
        assertEquals(Mark.FALLING_OFF, new DirectControlScatter.Ring(1f, false, false, true).mark());
        assertEquals(Mark.GOOD, new DirectControlScatter.Ring(0.5f, false, false, false).mark());
    }

    @Test
    void optimalRangeKeepsEightyPercentOfPointBlankAgainstOneBody() {
        float range = 30f;
        float optimal = ManualPointFire.optimalRangeCells(range, 0.55f, 1.2f, 0.6f, PointAimDispersion.WEAPON);
        // The disk at weapon range: base 0.2 + spread 1.2 + 2 cells x (1 - 0.55); the body plus blast is 1.1.
        float atRange = 0.2f + 1.2f + 2f * 0.45f;
        float disk = atRange * optimal / range;
        float covered = (1.1f / disk) * (1.1f / disk);
        assertEquals(ManualPointFire.OPTIMAL_HIT_SHARE, covered, 1e-4f);
        assertTrue(optimal > 10f && optimal < range);
        assertEquals(range, ManualPointFire.optimalRangeCells(range, 1f, 0f, 2f, PointAimDispersion.WEAPON),
                "a weapon that stays on target to maximum range has no falling-off band");
        assertTrue(ManualPointFire.optimalRangeCells(range, 0.3f, 2f, 0f, PointAimDispersion.WEAPON) < optimal,
                "a less accurate weapon falls off sooner");
    }
}
