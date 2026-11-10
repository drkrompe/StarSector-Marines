package com.dillon.starsectormarines.battle.world.gen.road;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A road network is read as straight streets meeting at typed junctions, and its planning faults are counted. */
class StreetGraphTest {

    private static final int W = 60, H = 40;

    private static void fill(boolean[][] road, int x0, int y0, int x1, int y1) {
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) road[x][y] = true;
    }

    @Test
    void streetsThatCrossMakeACrossAndOneThatStopsMakesATee() {
        boolean[][] road = new boolean[W][H];
        fill(road, 0, 10, W - 1, 12);    // a street across the map
        fill(road, 20, 0, 22, H - 1);    // crossed by one up the map
        fill(road, 40, 13, 42, 30);      // and met from above by one that ends there
        StreetGraph g = StreetGraph.of(road);
        assertEquals(3, g.streets().size());
        assertEquals(1, g.junctions().stream().filter(j -> j.kind() == StreetGraph.Kind.CROSS).count());
        assertEquals(1, g.junctions().stream().filter(j -> j.kind() == StreetGraph.Kind.TEE).count());
        assertEquals(1, g.deadEnds(), "the side street's far end goes nowhere");
    }

    @Test
    void aCornerIsABend() {
        boolean[][] road = new boolean[W][H];
        fill(road, 0, 10, 22, 12);
        fill(road, 20, 10, 22, H - 1);
        StreetGraph g = StreetGraph.of(road);
        assertEquals(1, g.junctions().size());
        assertEquals(StreetGraph.Kind.BEND, g.junctions().get(0).kind());
        assertEquals(0, g.deadEnds(), "both streets run off the map");
    }

    @Test
    void aCrossingPulledApartIsTwoOffsetTees() {
        boolean[][] road = new boolean[W][H];
        fill(road, 0, 10, W - 1, 12);
        fill(road, 20, 13, 22, H - 1);   // up from x 21 ...
        fill(road, 26, 0, 28, 9);        // ... and down from x 27: a cross pulled six cells apart
        StreetGraph g = StreetGraph.of(road);
        assertEquals(1, g.offsetTees(8));
        assertEquals(0, g.offsetTees(4), "farther apart than asked is two streets, not one crossing");
    }

    @Test
    void aStreetThatChangesWidthIsAWidthStep() {
        boolean[][] road = new boolean[W][H];
        fill(road, 0, 10, 30, 12);       // three wide ...
        fill(road, 31, 9, W - 1, 13);    // ... then five
        assertEquals(1, StreetGraph.of(road).widthSteps());
        boolean[][] even = new boolean[W][H];
        fill(even, 0, 10, W - 1, 12);
        assertEquals(0, StreetGraph.of(even).widthSteps());
    }
}
