package com.dillon.starsectormarines.battle.mapgen.bsp;

import com.dillon.starsectormarines.battle.map.CellTopology.GroundKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Pre-BSP trunk-road planner. Lays down two wide arterial roads on the map
 * before BSP partitions the remaining interior. The trunks read as the city's
 * main streets — a primary boulevard plus a perpendicular secondary cross-
 * street — and the parcels (BSP leaves) infill the four quadrants between
 * them.
 *
 * <p>This is the fix for "every road in the map is a side-effect of a BSP
 * cut" — by writing trunk cells into a shared road mask <em>before</em>
 * {@link Bsp#partitionRect} runs, the trunk network becomes a deliberately
 * authored skeleton rather than emergent from parcel boundaries.
 *
 * <p>V1 layout: one horizontal {@link TrunkKind#PRIMARY} trunk (width 7 —
 * 2-cell {@link GroundKind#SIDEWALK} flank on each side around a 3-cell
 * {@link GroundKind#STREET} core) and one perpendicular vertical
 * {@link TrunkKind#SECONDARY} trunk (width 5, all {@link GroundKind#STREET}
 * — the render path's wall-adjacency sidewalk picker is enough at this
 * width). The crossing point — exposed as {@link Plan#intersection} — is
 * where the orchestrator forces a
 * {@link com.dillon.starsectormarines.battle.mapgen.MapDistrictTheme#CIVIC}
 * bias so the city center reads as a downtown hub.
 *
 * <p>Pipeline contract: {@link #generate} writes the 1-cell perimeter plus
 * both trunk bands into the returned road mask. Callers must <em>not</em>
 * re-reserve perimeter (that would double-paint and confuses debug logs).
 * Sub-rects are inclusive bounds on the BSP-able interior between trunks
 * and edges; they exclude perimeter and trunk cells.
 *
 * <p>Trunks are added to {@link Plan#trunks} in paint-order: SECONDARY first,
 * PRIMARY second. Callers that overwrite per-cell GroundKind in iteration
 * order get the right outcome at the intersection (the primary boulevard's
 * TILE ground reads continuously across the crossing).
 */
public final class TrunkPlan {

    /** Width of a primary trunk (paved boulevard, {@link GroundKind#TILE}). */
    public static final int PRIMARY_WIDTH = 7;
    /** Width of a secondary trunk (cross-street, {@link GroundKind#STREET}). */
    public static final int SECONDARY_WIDTH = 5;

    /** Inclusive lower bound on the perpendicular-axis offset, as a fraction of map dim. */
    private static final float OFFSET_LO = 0.35f;
    /** Inclusive upper bound on the perpendicular-axis offset, as a fraction of map dim. */
    private static final float OFFSET_HI = 0.65f;

    /** Max anti-clustering rerolls when picking trunk offsets before falling back to map center. */
    private static final int MAX_RETRIES = 4;

    /** Per-sub-rect minimum dimension required to host at least one BSP split. */
    private static final int SUBRECT_MIN_DIM = 2 * Bsp.LEAF_MIN + Bsp.ROAD_WIDTH_MIN;

    /**
     * A trunk's role in the hierarchy. Drives both its rendered surface
     * decomposition and its width tier. Wide trunks (PRIMARY) carry a
     * {@link #sidewalkFlankWidth}-cell {@link GroundKind#SIDEWALK} flank on
     * each side framing an interior {@link #roadGround} core. Narrow trunks
     * (SECONDARY) set {@code sidewalkFlankWidth=0} and let the render path's
     * wall-adjacency picker stamp a 1-cell sidewalk against any flanking
     * building automatically.
     */
    public enum TrunkKind {
        /** Width-7 wide boulevard — 2-cell {@link GroundKind#SIDEWALK} flank on each side around a 3-cell {@link GroundKind#STREET} core. */
        PRIMARY(PRIMARY_WIDTH, GroundKind.STREET, 2),
        /** Width-5 cross-street. All {@link GroundKind#STREET}; render-time wall adjacency handles the 1-thick sidewalk where it meets a building. */
        SECONDARY(SECONDARY_WIDTH, GroundKind.STREET, 0);

        /** Cells wide along the band's short axis. */
        public final int width;
        /** Ground kind painted on the interior road core (after subtracting the sidewalk flanks). */
        public final GroundKind roadGround;
        /** Cells of {@link GroundKind#SIDEWALK} painted on each flank of the band. Zero leaves the whole band as {@link #roadGround}. */
        public final int sidewalkFlankWidth;

        TrunkKind(int width, GroundKind roadGround, int sidewalkFlankWidth) {
            this.width = width;
            this.roadGround = roadGround;
            this.sidewalkFlankWidth = sidewalkFlankWidth;
        }
    }

    /** One trunk band, axis-aligned. Inclusive bounds on both axes. */
    public static final class TrunkSegment {
        public final int left, top, right, bottom;
        public final TrunkKind kind;
        public final boolean horizontal;

        public TrunkSegment(int left, int top, int right, int bottom, TrunkKind kind, boolean horizontal) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.kind = kind;
            this.horizontal = horizontal;
        }
    }

    /** Inclusive axis-aligned rect — a region of the interior BSP should partition. */
    public static final class SubRect {
        public final int x0, y0, x1, y1;
        public SubRect(int x0, int y0, int x1, int y1) { this.x0 = x0; this.y0 = y0; this.x1 = x1; this.y1 = y1; }
        public int width()  { return x1 - x0 + 1; }
        public int height() { return y1 - y0 + 1; }
    }

    /** Output of one planning pass. */
    public static final class Plan {
        public final boolean[][] roadCells;
        public final List<SubRect> subRects;
        public final List<TrunkSegment> trunks;
        /** Rect where the two trunks cross. Used to bias the district overlay toward CIVIC at the city center. */
        public final SubRect intersection;
        public final int width;
        public final int height;

        public Plan(boolean[][] roadCells, List<SubRect> subRects, List<TrunkSegment> trunks,
                    SubRect intersection, int width, int height) {
            this.roadCells = roadCells;
            this.subRects = subRects;
            this.trunks = trunks;
            this.intersection = intersection;
            this.width = width;
            this.height = height;
        }
    }

    private TrunkPlan() {}

    /**
     * Plan one map's trunk skeleton. Picks jittered offsets in the
     * {@link #OFFSET_LO}..{@link #OFFSET_HI} band for both trunks, retries up
     * to {@link #MAX_RETRIES} times if any of the four resulting sub-rects
     * would be too small to BSP, paints both trunk bands plus perimeter into
     * a fresh road mask, and returns four flanking sub-rects plus the
     * intersection rect.
     */
    public static Plan generate(int width, int height, Random rng) {
        boolean[][] road = new boolean[width][height];

        for (int x = 0; x < width; x++)  { road[x][0] = true; road[x][height - 1] = true; }
        for (int y = 0; y < height; y++) { road[0][y] = true; road[width  - 1][y] = true; }

        int hTop  = -1;
        int vLeft = -1;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            int candHTop  = pickOffset(height, TrunkKind.PRIMARY.width,   rng);
            int candVLeft = pickOffset(width,  TrunkKind.SECONDARY.width, rng);
            if (subRectsViable(width, height, candHTop, candVLeft)) {
                hTop  = candHTop;
                vLeft = candVLeft;
                break;
            }
        }
        if (hTop < 0) {
            // Geometric-center fallback. Won't satisfy the viability check on
            // very small maps but keeps the pipeline running; BSP gracefully
            // degrades to emitting a too-small sub-rect as a single leaf.
            hTop  = (height - TrunkKind.PRIMARY.width)   / 2;
            vLeft = (width  - TrunkKind.SECONDARY.width) / 2;
        }
        int hBot   = hTop  + TrunkKind.PRIMARY.width   - 1;
        int vRight = vLeft + TrunkKind.SECONDARY.width - 1;

        // Paint the primary horizontal trunk.
        for (int y = hTop; y <= hBot; y++) {
            for (int x = 0; x < width; x++) road[x][y] = true;
        }
        // Paint the secondary vertical trunk.
        for (int x = vLeft; x <= vRight; x++) {
            for (int y = 0; y < height; y++) road[x][y] = true;
        }

        // SECONDARY first, PRIMARY second — see class javadoc on paint order.
        List<TrunkSegment> trunks = new ArrayList<>(2);
        trunks.add(new TrunkSegment(vLeft, 0, vRight, height - 1, TrunkKind.SECONDARY, false));
        trunks.add(new TrunkSegment(0, hTop, width - 1, hBot, TrunkKind.PRIMARY, true));

        List<SubRect> subRects = new ArrayList<>(4);
        subRects.add(new SubRect(1,         1,        vLeft - 1,   hTop - 1));      // TL
        subRects.add(new SubRect(vRight + 1, 1,        width - 2,   hTop - 1));     // TR
        subRects.add(new SubRect(1,         hBot + 1, vLeft - 1,   height - 2));    // BL
        subRects.add(new SubRect(vRight + 1, hBot + 1, width - 2,   height - 2));   // BR

        SubRect intersection = new SubRect(vLeft, hTop, vRight, hBot);

        return new Plan(road, subRects, trunks, intersection, width, height);
    }

    /**
     * Pick the starting offset (top for horizontal trunk, left for vertical)
     * along the given map span. The trunk band is {@code trunkWidth} cells
     * wide and must fit entirely between the perimeter and the opposite edge.
     */
    private static int pickOffset(int span, int trunkWidth, Random rng) {
        int lo = (int) Math.round(span * OFFSET_LO);
        int hi = (int) Math.round(span * OFFSET_HI) - trunkWidth;
        if (hi < lo) hi = lo;
        return lo + rng.nextInt(hi - lo + 1);
    }

    /**
     * Anti-clustering check: every quadrant produced by the trunk crossing
     * must have at least one BSP-splittable dim. With {@link #OFFSET_LO}=.35
     * and {@link #OFFSET_HI}=.65 on an 80×80 map this always holds on the
     * first roll; the check matters on smaller maps and as a safety guard
     * when offset bounds change.
     */
    private static boolean subRectsViable(int width, int height, int hTop, int vLeft) {
        int hBot   = hTop  + TrunkKind.PRIMARY.width   - 1;
        int vRight = vLeft + TrunkKind.SECONDARY.width - 1;
        int leftW  = (vLeft - 1) - 1 + 1;
        int rightW = (width - 2) - (vRight + 1) + 1;
        int topH   = (hTop - 1) - 1 + 1;
        int botH   = (height - 2) - (hBot + 1) + 1;
        return Math.max(leftW,  rightW) >= SUBRECT_MIN_DIM
            && Math.max(topH,   botH)   >= SUBRECT_MIN_DIM
            && leftW > 0 && rightW > 0 && topH > 0 && botH > 0;
    }
}
