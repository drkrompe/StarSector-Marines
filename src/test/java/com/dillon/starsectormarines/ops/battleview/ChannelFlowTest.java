package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelFlowTest {

    static final int W = 40, H = 12;

    /** A channel across the field in rows {@code y0..y1}. */
    static boolean[] straight(int y0, int y1) {
        boolean[] open = new boolean[W * H];
        for (int y = y0; y <= y1; y++) for (int x = 0; x < W; x++) open[y * W + x] = true;
        return open;
    }

    static float[] even(float metres) {
        float[] d = new float[W * H];
        Arrays.fill(d, metres);
        return d;
    }

    static float[] flowAt(ChannelFlow flow, float x, float y) {
        float[] out = new float[5];
        flow.at(x, y, out);
        return out;
    }

    @Test
    void anEvenChannelRunsOneCellAlongACellAndLaysItsAcrossOnStreamlines() {
        ChannelFlow flow = new ChannelFlow(W, H, straight(3, 8), even(10f));
        assertTrue(flow.any);
        float[] mid = flowAt(flow, 20f, 6f);
        assertEquals(1f, mid[2], 1e-2f, "runs east");
        assertEquals(1f, mid[4], .1f, "about a cell along a cell");
        assertTrue(flowAt(flow, 30f, 6f)[0] > mid[0] + 9f, "along climbs downstream");
        float north = flowAt(flow, 10f, 3.5f)[1], south = flowAt(flow, 10f, 8.5f)[1];
        assertTrue(Math.abs(north - south) > 4f, "across spans the channel: " + north + " " + south);
        assertEquals(north, flowAt(flow, 30f, 3.5f)[1], .2f, "the same along a streamline");
    }

    @Test
    void theFlowRunsFasterThroughANarrowsAndAShallow() {
        boolean[] open = straight(2, 9);
        for (int x = 18; x <= 22; x++) for (int y = 2; y <= 9; y++) open[y * W + x] = y >= 5 && y <= 6;
        ChannelFlow narrows = new ChannelFlow(W, H, open, even(10f));
        assertTrue(flowAt(narrows, 20f, 6f)[4] > 2.5f * flowAt(narrows, 6f, 6f)[4], "squeezed through the narrows");

        float[] depth = even(10f);
        for (int x = 18; x <= 22; x++) for (int y = 0; y < H; y++) depth[y * W + x] = 2.5f;
        ChannelFlow shallows = new ChannelFlow(W, H, straight(2, 9), depth);
        assertTrue(flowAt(shallows, 20f, 6f)[4] > 2.5f * flowAt(shallows, 6f, 6f)[4], "faster over the shallow");
    }

    @Test
    void aChannelRunsTowardItsDeeperEnd() {
        float[] depth = even(10f);
        for (int y = 0; y < H; y++) for (int x = 0; x < W / 2; x++) depth[y * W + x] = 30f;
        ChannelFlow flow = new ChannelFlow(W, H, straight(3, 8), depth);
        assertTrue(flowAt(flow, 20f, 6f)[2] < -.99f, "runs west, to the deep end");
    }

    @Test
    void aFlowFollowsABend() {
        // In from the west along rows 2..6, turning south down columns 30..34 to the field's foot.
        boolean[] open = new boolean[W * H];
        for (int y = 2; y <= 6; y++) for (int x = 0; x <= 34; x++) open[y * W + x] = true;
        for (int y = 2; y < H; y++) for (int x = 30; x <= 34; x++) open[y * W + x] = true;
        ChannelFlow flow = new ChannelFlow(W, H, open, even(10f));
        assertTrue(flowAt(flow, 10f, 4.5f)[2] > .95f, "east along the reach");
        assertTrue(flowAt(flow, 32.5f, 10f)[3] > .95f, "south down the turn");
    }

    @Test
    void aChannelThatNeverLeavesTheFieldIsStill() {
        boolean[] open = new boolean[W * H];
        for (int y = 3; y <= 8; y++) for (int x = 5; x <= 30; x++) open[y * W + x] = true;
        ChannelFlow flow = new ChannelFlow(W, H, open, even(10f));
        assertFalse(flow.any);
        float[] still = flowAt(flow, 10f, 5f);
        assertEquals(0f, still[4]);
        assertEquals(10f, still[0], "a still floor lies on the map's own axes");
    }
}
