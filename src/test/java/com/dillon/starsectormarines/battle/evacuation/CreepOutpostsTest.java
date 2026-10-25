package com.dillon.starsectormarines.battle.evacuation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where creep seeds an outpost: inside the creep, open ground, and a spacing clear of everything standing. */
class CreepOutpostsTest {

    /** Open ground everywhere; creep within {@code reach} of (50, 50), less any cell a test closes. */
    private static final class Disc implements CreepOutposts.Ground {
        final float reach;
        final List<int[]> closed = new ArrayList<>();

        Disc(float reach) {
            this.reach = reach;
        }

        @Override public boolean creeped(int x, int y) {
            float dx = x + 0.5f - 50.5f, dy = y + 0.5f - 50.5f;
            return dx * dx + dy * dy <= reach * reach;
        }

        @Override public boolean open(int x, int y) {
            for (int[] c : closed) if (c[0] == x && c[1] == y) return false;
            return true;
        }
    }

    private static List<float[]> boreOnly() {
        List<float[]> anchors = new ArrayList<>();
        anchors.add(new float[]{50.5f, 50.5f});
        return anchors;
    }

    @Test
    void nothingRootsUntilTheCreepHasRunPastTheSpacing() {
        // At 18 cells of spacing the outpost needs creep two cells beyond 18 in every direction round it.
        assertNull(CreepOutposts.choose(new Disc(19f), 50, 50, 18f, 30f, 0f, boreOnly()));
        int[] cell = CreepOutposts.choose(new Disc(21f), 50, 50, 18f, 30f, 0f, boreOnly());
        assertNotNull(cell);
        float d = (float) Math.hypot(cell[0] + 0.5 - 50.5, cell[1] + 0.5 - 50.5);
        assertTrue(d >= 18f && d <= 19f, "the nearest ring that qualifies, at the spacing: " + d);
    }

    @Test
    void anOutpostKeepsItsSpacingFromEveryColonyAlreadyStanding() {
        List<float[]> anchors = boreOnly();
        int[] first = CreepOutposts.choose(new Disc(40f), 50, 50, 18f, 40f, 0f, anchors);
        assertNotNull(first);
        anchors.add(new float[]{first[0] + 0.5f, first[1] + 0.5f});
        int[] second = CreepOutposts.choose(new Disc(40f), 50, 50, 18f, 40f, 0f, anchors);
        assertNotNull(second);
        for (float[] a : anchors) {
            assertTrue(Math.hypot(a[0] - (second[0] + 0.5f), a[1] - (second[1] + 0.5f)) >= 18f);
        }
    }

    @Test
    void closedGroundIsPassedOver() {
        Disc disc = new Disc(25f);
        int[] open = CreepOutposts.choose(disc, 50, 50, 18f, 25f, 0f, boreOnly());
        disc.closed.add(open);
        int[] next = CreepOutposts.choose(disc, 50, 50, 18f, 25f, 0f, boreOnly());
        assertNotNull(next);
        assertTrue(next[0] != open[0] || next[1] != open[1]);
    }

    @Test
    void whereAnOutpostFellIsAnAnchorForTheNext() {
        Map<Long, float[]> rootedAt = Map.of(1L, new float[]{10.5f, 10.5f}, 2L, new float[]{30.5f, 10.5f});
        List<float[]> fallen = CreepOutposts.fallen(List.of(1L, 2L, 3L), id -> id == 2L, rootedAt);
        assertEquals(1, fallen.size(), "only the dead outpost with a known cell");
        assertEquals(10.5f, fallen.get(0)[0]);
    }
}
