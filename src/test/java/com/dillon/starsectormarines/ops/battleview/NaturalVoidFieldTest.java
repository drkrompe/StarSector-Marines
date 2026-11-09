package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.NaturalVoid;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NaturalVoidFieldTest {

    static final int W = 30, H = 20;

    /** A void running west to east across rows 6..13, crossed by ground in columns 14..16. */
    static boolean[] gorge() {
        boolean[] v = new boolean[W * H];
        for (int y = 6; y <= 13; y++) for (int x = 0; x < W; x++) v[y * W + x] = x < 14 || x > 16;
        return v;
    }

    static NaturalVoid look() {
        return new NaturalVoid(NaturalVoid.Style.GORGE_RIVER,
                List.of(new NaturalVoid.Step(10f, .7f), new NaturalVoid.Step(20f, 1.8f)), 160f, null, 14f, 3);
    }

    @Test
    void narrowGroundAcrossTheVoidIsABridgeAndTheBankIsNot() {
        boolean[] bridge = NaturalVoidField.bridges(W, H, gorge());
        assertTrue(bridge[9 * W + 15], "the crossing over the void");
        assertFalse(bridge[3 * W + 15], "the bank north of it");
        assertFalse(bridge[9 * W + 5], "the void itself");
    }

    @Test
    void aSpurOfTheBankIsNotABridge() {
        boolean[] v = gorge();
        // Three cells of bank pushing two rows into the void from the north: narrow, but joined on one side.
        for (int y = 6; y <= 7; y++) for (int x = 4; x <= 6; x++) v[y * W + x] = false;
        boolean[] bridge = NaturalVoidField.bridges(W, H, v);
        assertFalse(bridge[7 * W + 5], "the spur's tip");
        assertTrue(bridge[9 * W + 15], "the crossing still spans");
    }

    @Test
    void theDropNeverRisesAndTheGroundStandsAtTheSurface() {
        NaturalVoidField field = new NaturalVoidField(W, H, gorge(), look(), -.7f, -.7f, .8f);
        for (int k = 1; k < field.steps(); k++) {
            for (int s = 0; s < field.reach[k].length; s++) {
                assertTrue(field.reach[k][s] >= field.reach[k + 1][s], "step " + k + " nests the one below");
            }
        }
        assertEquals(0f, field.depthAt(5.5f, 2.5f), "the bank");
        assertEquals(20f, field.depthAt(5.5f, 10f), "the middle of the void reaches the floor");
    }

    @Test
    void theDropRunsOnUnderABridgeInItsShade() {
        // The sun straight overhead from the north: the floor under the bridge is shaded by the deck.
        NaturalVoidField field = new NaturalVoidField(W, H, gorge(), look(), 0f, -1f, 8f);
        assertEquals(20f, field.depthAt(15.5f, 10f), "the floor under the bridge");
        assertEquals(0f, field.sunlit(15.5f, 10f, 20f, 0f, -1f, 8f), "under the deck");
        assertEquals(1f, field.sunlit(5.5f, 10f, 20f, 0f, -1f, 8f), "the open floor");
    }
}
