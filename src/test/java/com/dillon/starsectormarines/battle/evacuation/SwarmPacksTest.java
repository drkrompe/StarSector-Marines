package com.dillon.starsectormarines.battle.evacuation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SwarmPacksTest {

    @Test
    void aPackStartsAtEachSplitOfAPulse() {
        boolean[] starts = new boolean[16];
        for (int i = 0; i < starts.length; i++) starts[i] = SwarmPacks.startsPack(i, 16);
        for (int i = 0; i < starts.length; i++) assertEquals(i == 0 || i == 8, starts[i], "body " + i);
    }

    @Test
    void aPulseSplitsIntoAsFewEvenPacksAsFit() {
        assertArrayEquals(new int[] {6}, SwarmPacks.packSizes(6));
        assertArrayEquals(new int[] {8}, SwarmPacks.packSizes(8));
        assertArrayEquals(new int[] {5, 4}, SwarmPacks.packSizes(9));
        assertArrayEquals(new int[] {8, 8}, SwarmPacks.packSizes(16));
        assertArrayEquals(new int[] {6, 6, 5}, SwarmPacks.packSizes(17));
        assertArrayEquals(new int[0], SwarmPacks.packSizes(0));
    }

    @Test
    void ranksFillTheFanOutwardOnAlternateSides() {
        int[] ranks = new int[7];
        for (int k = 0; k < ranks.length; k++) ranks[k] = SwarmPackPlans.rank(k);
        assertArrayEquals(new int[] {1, -1, 2, -2, 3, -3, 4}, ranks);
    }

    @Test
    void theGaitSwingOnlyEverSlowsAMember() {
        SwarmPackPlans.Plan pack = new SwarmPackPlans.Plan(1, 7L);
        pack.gaitPeriod = new float[] {1.5f};
        pack.gaitOffset = new float[] {0.3f};
        float low = 1f;
        float high = 0f;
        for (int tick = 0; tick < 90; tick++) {
            float pace = SwarmPackMovement.gait(pack, 0, tick);
            low = Math.min(low, pace);
            high = Math.max(high, pace);
        }
        assertEquals(1f - SwarmPackMovement.GAIT_SWING, low, 0.01f);
        assertEquals(1f, high, 0.01f);
    }
}
