package com.dillon.starsectormarines.battle.evacuation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Where a fed bore buds: a block or two further in toward the town's heart, apart from what stands. */
class BudSiteTest {

    /** The heart in the west; the bore out east at (200, 100). */
    private static final float HX = 50.5f, HY = 100.5f, PX = 200.5f, PY = 100.5f;

    @Test
    void itBudsAsFarInAsItReaches() {
        int[] behind = {230, 100}, tooNear = {190, 100}, tooFar = {120, 100}, inward = {180, 100}, further = {160, 105};
        assertArrayEquals(further, SwarmBores.budSite(List.of(behind, tooNear, tooFar, inward, further), PX, PY, HX, HY,
                List.of(), 10));
    }

    @Test
    void itKeepsItsSpacingFromWhatStands() {
        int[] crowded = {170, 100}, apart = {175, 125};
        assertArrayEquals(apart, SwarmBores.budSite(List.of(crowded, apart), PX, PY, HX, HY,
                List.of(new float[]{165.5f, 100.5f}), 12));
    }

    @Test
    void sidewaysIsNotInward() {
        int[] sideways = {200, 130};
        assertNull(SwarmBores.budSite(List.of(sideways), PX, PY, HX, HY, List.of(), 10));
    }
}
