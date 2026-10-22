package com.dillon.starsectormarines.battle.evacuation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroundColoniesTest {

    /** Creep over x in [0, creepTo), open everywhere. */
    private static GroundColonies.Ground creepUpTo(int creepTo) {
        return new GroundColonies.Ground() {
            @Override public boolean creeped(int x, int y) { return x >= 0 && x < creepTo && y >= 0 && y < 40; }

            @Override public boolean open(int x, int y) { return true; }
        };
    }

    @Test
    void rootsOnCreepWithinReachOfTheGunNearestTheBore() {
        float[] near = {22.5f, 20.5f}, far = {22.5f, 5.5f};
        int[] cell = GroundColonies.choose(creepUpTo(20), 5, 20, 30f, List.of(far, near), 4.8f, List.of());
        assertNotNull(cell);
        float dx = cell[0] + 0.5f - near[0], dy = cell[1] + 0.5f - near[1];
        assertTrue(dx * dx + dy * dy <= 4.8f * 4.8f, "within reach of the nearer gun");
        assertTrue(cell[0] < 19, "inside the creep with creep round it, never on its edge");
    }

    @Test
    void noCreepInReachNoColony() {
        assertNull(GroundColonies.choose(creepUpTo(10), 5, 20, 30f, List.<float[]>of(new float[]{30.5f, 20.5f}), 4.8f, List.of()));
    }

    @Test
    void keepsItsSpacingFromWhatAlreadyStands() {
        float[] gun = {22.5f, 20.5f};
        int[] first = GroundColonies.choose(creepUpTo(24), 5, 20, 30f, List.of(gun), 4.8f, List.of());
        assertNotNull(first);
        float[] standing = {first[0] + 0.5f, first[1] + 0.5f};
        int[] second = GroundColonies.choose(creepUpTo(24), 5, 20, 30f, List.of(gun), 4.8f, List.of(standing));
        assertNotNull(second);
        float dx = second[0] - first[0], dy = second[1] - first[1];
        assertTrue(dx * dx + dy * dy >= GroundColonies.SPACING * GroundColonies.SPACING);
    }
}
