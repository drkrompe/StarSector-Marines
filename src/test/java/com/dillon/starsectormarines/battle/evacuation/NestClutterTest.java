package com.dillon.starsectormarines.battle.evacuation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NestClutterTest {

    private static NestTrails.Trail root(float reach, float thick) {
        return new NestTrails.Trail(10f, 10f, 40f, 10f, 0.6f, 0.3f, 77L, reach, thick);
    }

    private static List<NestClutter.Item> along(NestTrails.Trail t) {
        List<NestClutter.Item> out = new ArrayList<>();
        NestClutter.along(t, out);
        return out;
    }

    @Test
    void aRootCarriesClutterOnlyOnceItIsRope() {
        assertTrue(along(root(0.6f, 0f)).isEmpty(), "a vein still digging carries none");
        assertTrue(along(root(1f, 0.2f)).isEmpty(), "nor a rope barely begun");
        assertFalse(along(root(1f, 1f)).isEmpty(), "a grown rope does");
    }

    @Test
    void itLiesBesideTheRopeAndClearOfItsEnds() {
        NestTrails.Trail t = root(1f, 1f);
        float[] pts = NestTrails.centreline(t);
        for (NestClutter.Item it : along(t)) {
            float nearest = Float.MAX_VALUE;
            for (int i = 0; i < pts.length; i += 2) nearest = Math.min(nearest, (float) Math.hypot(pts[i] - it.x(), pts[i + 1] - it.y()));
            assertTrue(nearest > 0.6f, "off the rope, " + nearest);
            assertTrue(Math.hypot(it.x() - t.x0(), it.y() - t.y0()) > 3f, "clear of the bore");
            assertTrue(Math.hypot(it.x() - t.x1(), it.y() - t.y1()) > 3f, "clear of the growth");
        }
    }

    @Test
    void theMouthsPiecesKeepClearOfItsColoniesAndRoots() {
        List<float[]> colonies = List.of(new float[]{23f, 20f}, new float[]{17f, 20f});
        List<NestTrails.Trail> roots = List.of(new NestTrails.Trail(21f, 20f, 20f, 40f, 0.6f, 0.3f, 9L, 1f, 1f));
        List<NestClutter.Item> out = new ArrayList<>();
        NestClutter.mouth(20f, 20f, 5L, 4, colonies, roots, out);
        assertEquals(4, out.size());
        for (NestClutter.Item it : out) {
            for (float[] c : colonies) assertTrue(Math.hypot(c[0] - it.x(), c[1] - it.y()) >= NestClutter.COLONY_CLEAR);
            assertFalse(NestClutter.onRoot(it.x(), it.y(), roots), "off its roots");
        }
    }

    @Test
    void theSameNestLiesTheSameWay() {
        assertEquals(along(root(1f, 1f)), along(root(1f, 1f)));
    }
}
