package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared geometry for the concentric / diamond station layouts — the nested
 * centered rectangles and the per-band room subdivision. Pure arithmetic (no
 * grid, no graph), so both {@link ConcentricLayoutStage} and
 * {@link DiamondLayoutStage} derive their rooms from one source and can't drift.
 *
 * <p>A station is a stack of nested rectangles inset by {@link #RING_THICKNESS}
 * from a {@link #HULL_MARGIN} perimeter, down to a central core. The frame
 * between two consecutive rectangles is a ring <em>band</em>, subdivided into 8
 * rooms — 4 corner strongpoints + 4 edge galleries — in the fixed cyclic order
 * {@code TL, TOP, TR, RIGHT, BR, BOTTOM, BL, LEFT}. Corners are the even slots,
 * galleries the odd slots; {@link #galleryIndex(int)} maps a side (0=top, 1=right,
 * 2=bottom, 3=left) to its gallery slot.
 */
final class RingGeometry {

    /** Solid hull cells reserved at the map perimeter (the outer ring sits just inside). */
    static final int HULL_MARGIN = 1;
    /** Radial thickness of each ring band, in cells. */
    static final int RING_THICKNESS = 10;
    /** Stop adding rings once the remaining center would be smaller than this — that center becomes the core. */
    static final int CORE_MIN = 12;

    private RingGeometry() {}

    /**
     * Nested inset values, outermost first: {@code [0] == HULL_MARGIN}, each
     * {@code + RING_THICKNESS}, the last being the core rect's inset. The number
     * of ring bands is {@code size() - 1}.
     */
    static List<Integer> insets(int w, int h) {
        List<Integer> insets = new ArrayList<>();
        insets.add(HULL_MARGIN);
        int d = HULL_MARGIN;
        while (Math.min(w, h) - 2 * (d + RING_THICKNESS) >= CORE_MIN) {
            d += RING_THICKNESS;
            insets.add(d);
        }
        return insets;
    }

    /** Inclusive rect {@code [left, top, right, bottom]} at the given inset from every edge. */
    static int[] rectAt(int w, int h, int inset) {
        return new int[]{ inset, inset, w - 1 - inset, h - 1 - inset };
    }

    /**
     * The 8 sub-room rects of the band between {@code outerInset} and
     * {@code innerInset}, each inclusive {@code [left, top, right, bottom]}, in
     * cyclic order {@code TL, TOP, TR, RIGHT, BR, BOTTOM, BL, LEFT}. They tile
     * the band frame exactly (no overlap, no gap).
     */
    static int[][] bandRects(int w, int h, int outerInset, int innerInset) {
        int oL = outerInset, oT = outerInset, oR = w - 1 - outerInset, oB = h - 1 - outerInset;
        int iL = innerInset, iT = innerInset, iR = w - 1 - innerInset, iB = h - 1 - innerInset;
        return new int[][]{
                { oL,     oT,     iL - 1, iT - 1 }, // 0 TL
                { iL,     oT,     iR,     iT - 1 }, // 1 TOP
                { iR + 1, oT,     oR,     iT - 1 }, // 2 TR
                { iR + 1, iT,     oR,     iB     }, // 3 RIGHT
                { iR + 1, iB + 1, oR,     oB     }, // 4 BR
                { iL,     iB + 1, iR,     oB     }, // 5 BOTTOM
                { oL,     iB + 1, iL - 1, oB     }, // 6 BL
                { oL,     iT,     iL - 1, iB     }, // 7 LEFT
        };
    }

    /** Cyclic slot (into {@link #bandRects}) of the gallery on {@code side} — 0=top, 1=right, 2=bottom, 3=left. */
    static int galleryIndex(int side) {
        return side * 2 + 1;
    }
}
