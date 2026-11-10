package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.NaturalRise;
import com.dillon.starsectormarines.battle.world.model.NaturalVoid;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void theBankAtABridgesEndIsRoadNotLip() {
        NaturalVoidField field = new NaturalVoidField(W, H, gorge(), look(), -.7f, -.7f, .8f);
        assertTrue(field.byBridge(15.5f, 5.5f), "the bank where the bridge leaves it");
        assertFalse(field.byBridge(5.5f, 5.5f), "the bank above the drop");
        assertTrue(field.nearBridge(13.5f, 10f, 1f), "air over the drop beside the deck");
        assertFalse(field.nearBridge(5.5f, 10f, 1f), "air over the open drop");
        for (int q = 0; q < field.fray.count; q++) {
            float x = field.fray.attrs[q * 8], y = field.fray.attrs[q * 8 + 1];
            assertFalse(x > 14.6f && x < 16.4f && y > 4f && y < 6f, "no lip frays across the road onto the deck");
        }
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

    /** The same band as a ridge: rise cells in rows 6..13, a pass through it in columns 14..16. */
    static NaturalVoidField ridge(float sunX, float sunY, float rise) {
        NaturalRise look = new NaturalRise(NaturalRise.Style.RAVINE_WALL,
                List.of(new NaturalRise.Step(10f, .7f), new NaturalRise.Step(20f, 1.8f)), 120f, null, 14f, 3);
        return new NaturalVoidField(W, H, gorge(), NaturalVoidField.Shape.of(look), sunX, sunY, rise);
    }

    @Test
    void aRiseClimbsToItsTopAndAPassThroughItIsNoBridge() {
        NaturalVoidField field = ridge(-.7f, -.7f, .8f);
        assertTrue(field.rising);
        assertEquals(-20f, field.depthAt(5.5f, 10f), "the middle of the ridge stands at its top");
        assertEquals(0f, field.depthAt(5.5f, 2.5f), "the floor beside it");
        assertFalse(field.bridge[9 * W + 15], "the pass is floor, not a span");
    }

    @Test
    void aRiseShadesTheFloorOnItsSideAwayFromTheSun() {
        // The sun from the north at 45 degrees: the floor south of the ridge lies in its shadow.
        NaturalVoidField field = ridge(0f, -1f, 1f);
        assertEquals(0f, field.sunlit(5.5f, 16f, 0f, 0f, -1f, 1f), "the floor under the ridge's south face");
        assertEquals(1f, field.sunlit(5.5f, 3f, 0f, 0f, -1f, 1f), "the floor north of it, toward the sun");
        assertTrue(field.floorShade.count > 0, "the shadow is laid on the floor");
    }

    @Test
    void aDropWithoutMarksIsAsDeepEverywhereAsItsSteps() {
        NaturalVoidField field = new NaturalVoidField(W, H, gorge(), look(), -.7f, -.7f, .8f);
        assertNull(field.gain);
        assertEquals(1f, field.gainAt(3f, 10f));
        assertEquals(20f, field.stepDepth(2, 25f, 10f));
    }

    @Test
    void marksDeepenAndShallowTheDropAlongItsLengthKeepingEachLedgesShare() {
        NaturalVoid marked = look().withMarks(List.of(
                new NaturalVoid.DepthMark(0f, 10f, 30f), new NaturalVoid.DepthMark(30f, 10f, 4f)));
        NaturalVoidField field = new NaturalVoidField(W, H, gorge(), marked, -.7f, -.7f, .8f);
        float west = field.depthAt(2.5f, 10f), east = field.depthAt(27.5f, 10f);
        assertTrue(west > 24f && west <= 30f, "deep near the deep mark: " + west);
        assertTrue(east >= 4f && east < 8f, "shallow near the shallow mark: " + east);
        assertTrue(field.depthAt(10f, 10f) > field.depthAt(20f, 10f), "shallowing between them");
        for (float x = 1f; x < W; x += 3f) {
            assertEquals(.5f, field.stepDepth(1, x, 10f) / field.stepDepth(2, x, 10f), 1e-5f, "the ledge's share at " + x);
        }
        NaturalVoidField.Band floor = field.bands.get(2);
        assertEquals(floor.count * 5, floor.z.length, "every floor piece carries its own depth");
        boolean sloped = false;
        for (NaturalVoidField.Wall wall : field.walls.get(1)) sloped |= wall.g0() != wall.g1();
        assertTrue(sloped, "a face runs deeper at one end than the other");
    }

    @Test
    void aRiseClimbsAndSinksAlongItsLength() {
        NaturalRise look = new NaturalRise(NaturalRise.Style.RAVINE_WALL,
                List.of(new NaturalRise.Step(10f, .7f), new NaturalRise.Step(20f, 1.8f)), 120f, null, 14f, 3)
                .withMarks(List.of(new NaturalRise.HeightMark(0f, 10f, 40f), new NaturalRise.HeightMark(30f, 10f, 6f)));
        NaturalVoidField field = new NaturalVoidField(W, H, gorge(), NaturalVoidField.Shape.of(look), -.7f, -.7f, .8f);
        assertTrue(field.depthAt(2.5f, 10f) < field.depthAt(27.5f, 10f) - 20f, "higher at the west");
        assertTrue(field.top <= field.depthAt(2.5f, 10f), "the top is the highest anywhere");
    }

    @Test
    void anOpenPitsBenchesStayLevelAndARiseMarkMustStayUnderTheCamera() {
        NaturalVoid pit = new NaturalVoid(NaturalVoid.Style.OPEN_PIT, look().steps(), 160f, null, 14f, 3)
                .withMarks(List.of(new NaturalVoid.DepthMark(0f, 0f, 5f)));
        assertEquals(0, NaturalVoidField.Shape.of(pit).marks().length);
        NaturalRise rise = new NaturalRise(NaturalRise.Style.RAVINE_WALL, List.of(new NaturalRise.Step(20f, 1f)), 120f,
                null, 14f, 3);
        assertThrows(IllegalArgumentException.class,
                () -> rise.withMarks(List.of(new NaturalRise.HeightMark(0f, 0f, 130f))));
    }
}
