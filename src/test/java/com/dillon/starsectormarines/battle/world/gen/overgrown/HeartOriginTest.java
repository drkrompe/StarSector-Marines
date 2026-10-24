package com.dillon.starsectormarines.battle.world.gen.overgrown;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeartOriginTest {

    @Test
    void breachesSpreadAlongTheFence() {
        // A fence along one row: three breaches take both ends and the middle, whichever cell comes first.
        List<int[]> fence = new ArrayList<>();
        for (int x = 0; x <= 40; x++) fence.add(new int[]{x, 0});
        List<int[]> picked = HeartOrigin.spread(fence, 3, new SplittableRandom(7));
        assertEquals(3, picked.size());
        for (int i = 0; i < picked.size(); i++) {
            for (int j = i + 1; j < picked.size(); j++) {
                assertTrue(Math.abs(picked.get(i)[0] - picked.get(j)[0]) >= 13, "breaches bunch together");
            }
        }
        assertEquals(1, HeartOrigin.spread(List.of(new int[]{4, 4}), 3, new SplittableRandom(7)).size());
    }
}
