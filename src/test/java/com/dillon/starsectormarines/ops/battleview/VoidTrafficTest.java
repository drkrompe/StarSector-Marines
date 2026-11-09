package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How the void backdrop's traffic is scheduled and flown along its paths. */
class VoidTrafficTest {

    @Test
    void cruisingCraftKeepNearTheirPathWeavingNoFurtherThanTheWeave() {
        VoidTraffic traffic = new VoidTraffic();
        traffic.add(new float[][]{{0, 0, 100}, {200, 0, 100}, {400, 0, 100}}, 10f, 20f, VoidTraffic.Pace.CRUISE, 1);
        List<float[]> out = new ArrayList<>();
        for (float t = 0f; t < 600f; t += 7f) traffic.place(t, out);
        assertTrue(out.size() > 10, "craft are flying");
        for (float[] c : out) {
            assertTrue(Math.abs(c[1]) <= VoidTraffic.WEAVE_METERS + 1e-3f, "off the line by no more than the weave");
            assertTrue(c[0] >= -1e-3f && c[0] <= 400f + 1e-3f);
            assertEquals(100f, c[2], 1e-3f);
        }
    }

    @Test
    void launchesFollowNoFixedInterval() {
        VoidTraffic traffic = new VoidTraffic();
        // A short path flown fast, so each launch shows as a craft near its start for one sample.
        traffic.add(new float[][]{{0, 0, 0}, {40, 0, 0}}, 40f, 30f, VoidTraffic.Pace.CRUISE, 9);
        List<Float> launches = new ArrayList<>();
        List<float[]> out = new ArrayList<>();
        for (float t = 0f; t < 3000f; t += .25f) {
            out.clear();
            traffic.place(t, out);
            for (float[] c : out) if (c[0] < 10f && (launches.isEmpty() || t - launches.get(launches.size() - 1) > 1f)) {
                launches.add(t);
            }
        }
        List<Float> gaps = new ArrayList<>();
        for (int k = 1; k < launches.size(); k++) gaps.add(launches.get(k) - launches.get(k - 1));
        float mean = 0f;
        for (float g : gaps) mean += g / gaps.size();
        float spread = 0f;
        for (float g : gaps) spread += (g - mean) * (g - mean) / gaps.size();
        assertTrue(gaps.size() > 40, "a craft roughly every interval: " + gaps.size());
        assertTrue(Math.sqrt(spread) > mean * .3f, "the gaps between launches vary widely");
    }

    @Test
    void anArrivalSlowsIntoTheEndOfItsPath() {
        VoidTraffic traffic = new VoidTraffic();
        traffic.add(new float[][]{{0, 0, 0}, {100, 0, 0}}, 10f, 1000f, VoidTraffic.Pace.ARRIVE, 3);
        // Follow the first craft that flies: its steps shrink as it nears the end.
        List<Float> xs = new ArrayList<>();
        List<float[]> out = new ArrayList<>();
        for (float t = -2000f; t < 4000f && xs.size() < 200; t += .5f) {
            out.clear();
            traffic.place(t, out);
            if (!out.isEmpty()) xs.add(out.get(0)[0]);
            else if (!xs.isEmpty()) break;
        }
        assertTrue(xs.size() > 10);
        float early = xs.get(1) - xs.get(0), late = xs.get(xs.size() - 1) - xs.get(xs.size() - 2);
        assertTrue(late < early * .3f, "slower at the mouth than out in the open");
    }
}
