package com.dillon.starsectormarines.tools.mapauthoring;

import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Crossing;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.CrossingKind;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Drop;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Pt;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A bridge wherever a drop cuts a road, and none where a crossing already stands. */
class RoadCrossingsTest {

    private static final int W = 30, H = 12;

    /** Two roads running north and south at x 6..7 and x 20..22. */
    private static boolean[] roads() {
        boolean[] road = new boolean[W * H];
        for (int y = 0; y < H; y++) for (int x : new int[]{6, 7, 20, 21, 22}) road[y * W + x] = true;
        return road;
    }

    @Test
    void eachRoadTheDropCutsGetsOneBridgeOverIt() {
        Drop drop = new Drop("d", List.of(new Pt(0, 6), new Pt(30, 6)), 4, List.of(20f, 20f), 2, List.of());
        List<Crossing> crossings = RoadCrossings.add(drop, roads(), W, H);
        assertEquals(2, crossings.size());
        assertEquals(7f / 30f, crossings.get(0).at(), .01f);
        assertEquals(21.5f / 30f, crossings.get(1).at(), .01f);
        SketchRaster.Cut cut = SketchRaster.cut(W, H, new MapSketch("t", List.of(drop.withCrossings(crossings)), List.of(), List.of(), List.of(), List.of()));
        boolean[] road = roads();
        for (int i = 0; i < road.length; i++) if (road[i]) assertEquals(false, cut.drop()[i], "road cell " + i % W + "," + i / W + " left cut");
    }

    @Test
    void aRoadAlreadyCrossedIsLeftAlone() {
        Drop drop = new Drop("d", List.of(new Pt(0, 6), new Pt(30, 6)), 4, List.of(20f, 20f), 2,
                List.of(new Crossing(7f / 30f, 6, CrossingKind.LAND_BRIDGE)));
        List<Crossing> crossings = RoadCrossings.add(drop, roads(), W, H);
        assertEquals(2, crossings.size());
        assertEquals(CrossingKind.LAND_BRIDGE, crossings.get(0).kind());
    }
}
