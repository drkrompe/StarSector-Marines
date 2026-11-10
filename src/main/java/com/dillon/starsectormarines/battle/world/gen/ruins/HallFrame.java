package com.dillon.starsectormarines.battle.world.gen.ruins;

import java.util.ArrayList;
import java.util.List;

/**
 * The bones every works hall shares, across its width (west to east) and down its length (dispatch to head):
 * <pre>
 *   walkway | rail (feed) | west aisle | west stations | line aisle | LINE | line aisle | east stations | east aisle | lane | walkway
 * </pre>
 * The feed comes up the west rail from the intake to the head; the work runs down the line from the head to the
 * dispatch end; the east lane carries the hall's waste or return. Every aisle is a frame's way wide
 * ({@link WorksHallPlan#FRAME_WAY}), the stations take the width that is left, and bays repeat down the line every
 * {@link #PERIOD} rows with a cross aisle between. The hall's middle is kept clear, where its corridor arrives.
 */
final class HallFrame {

    /** Rows from one bay to the next down the line: a bay of {@link #BAY} rows and a cross aisle. */
    static final int PERIOD = 12, BAY = 8;
    /** Rows the dispatch end keeps clear of the line, and the head takes. */
    static final int DISPATCH = 6, HEAD = 18;

    final WorksHallPlan plan;
    final int w, l, seed;
    /** The line's west column (it is two wide), and the stations' columns either side. */
    final int cx, westL, westR, eastL, eastR;
    /** The feed rail's west column and the east lane's west column, each two wide. */
    final int railX, laneX;
    /** The first row of the head. */
    final int headY;

    HallFrame(WorksHall kind, int width, int length, long seed) {
        plan = new WorksHallPlan(kind, width, length);
        w = width;
        l = length;
        this.seed = (int) (seed ^ (seed >>> 32));
        cx = width / 2 - 1;
        railX = 2;
        laneX = width - 4;
        westL = railX + 2 + WorksHallPlan.FRAME_WAY;
        westR = cx - 1 - WorksHallPlan.FRAME_WAY;
        eastL = cx + 2 + WorksHallPlan.FRAME_WAY;
        eastR = laneX - 1 - WorksHallPlan.FRAME_WAY;
        headY = length - HEAD;
    }

    /** Width of each side's stations. */
    int stationWidth() { return westR - westL + 1; }

    /**
     * The first row of each bay down the line, from the head toward dispatch: as many as fit a period apart between
     * the head and the dispatch end's presses, spread evenly so no stretch of floor is left over at either end.
     */
    List<Integer> bays() {
        return bays(DISPATCH + 4);
    }

    /** {@link #bays()}, the lowest no lower than row {@code lo}. */
    List<Integer> bays(int lo) {
        int hi = headY - 2 - BAY;
        List<Integer> out = new ArrayList<>();
        if (hi < lo) return out;
        int n = (hi - lo) / PERIOD + 1;
        for (int k = 0; k < n; k++) out.add(n == 1 ? hi : hi - Math.round((float) k * (hi - lo) / (n - 1)));
        return out;
    }

    /** Whether a cell is the hall's middle, kept clear where the corridor arrives. */
    boolean middle(int x, int y) { return Math.abs(x - w / 2) <= 2 && Math.abs(y - l / 2) <= 2; }

    /** Stands a piece unless it would cover the hall's middle or its edge ring. */
    boolean stand(String id, String standIn, int x, int y, int fw, int fh, String station) {
        if (x < 1 || y < 1 || x + fw > w - 1 || y + fh > l - 1) return false;
        for (int cy = y; cy < y + fh; cy++) for (int cx2 = x; cx2 < x + fw; cx2++) if (middle(cx2, cy)) return false;
        return plan.stand(id, standIn, x, y, fw, fh, station);
    }

    /** A non-negative hash of a cell and the hall's seed: picks variants and wear, never the layout. */
    int hash(int x, int y, int salt) {
        long v = x * 73_856_093L ^ y * 19_349_663L ^ (seed + salt) * 83_492_791L;
        v ^= (v >>> 13);
        v *= 0x5bd1e995L;
        v ^= (v >>> 15);
        return (int) Math.floorMod(v, 1_000_003L);
    }

    /** Smooth value noise in [0, 1) over cells, about {@code scale} cells across a blob. */
    float noise(int x, int y, int scale, int salt) {
        float fx = (float) x / scale, fy = (float) y / scale;
        int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
        float tx = fx - x0, ty = fy - y0;
        tx = tx * tx * (3 - 2 * tx);
        ty = ty * ty * (3 - 2 * ty);
        float a = corner(x0, y0, salt), b = corner(x0 + 1, y0, salt), c = corner(x0, y0 + 1, salt),
                d = corner(x0 + 1, y0 + 1, salt);
        return (a + (b - a) * tx) * (1 - ty) + (c + (d - c) * tx) * ty;
    }

    private float corner(int x, int y, int salt) { return hash(x, y, salt) / 1_000_003f; }

    // ---- shared pieces ------------------------------------------------------------------------

    /**
     * The feed: a mag-rail up the west aisle from the dispatch end to a stop at the head, where the charge goes in;
     * a pod waiting at the stop, another derailed half way. Its cells are a lane.
     */
    void feedRail(String station, int stopY) {
        for (int y = DISPATCH + 2; y + 1 <= stopY; y += 2) {
            boolean end = y + 3 > stopY;
            plan.floor(end ? "doodad.ruins-mag-rail-end" : "doodad.ruins-mag-rail", null, railX, y, 2, 2, station);
        }
        plan.zone(railX, DISPATCH + 2, railX + 1, stopY, WorksHallPlan.Zone.LANE);
        stand("doodad.ruins-feed-pod", "doodad.industrial-crate-stack", railX, stopY - 4, 2, 3, station);
        stand("doodad.ruins-feed-pod-derailed", "doodad.industrial-crate-stack", railX + 2, l / 2 + 4, 2, 3,
                WorksHallPlan.COLLAPSE);
    }

    /** The east lane: a mag-rail from the head's waste to the dispatch end, a skid stopped on it. */
    void wasteLane(String station, String skid, String skidStandIn) {
        for (int y = DISPATCH + 2; y + 1 <= headY + 4; y += 2) {
            plan.floor("doodad.ruins-mag-rail", null, laneX, y, 2, 2, station);
        }
        plan.zone(laneX, DISPATCH + 2, laneX + 1, headY + 5, WorksHallPlan.Zone.LANE);
        if (skid != null) stand(skid, skidStandIn, laneX - 1, l / 2 - 8, 3, 3, station);
    }

    /**
     * The service drops along both long walls, every six rows: the cabinets that fed each bay its power and the
     * theme's service (coolant, nutrient, air), between the walkway and the rail on the west and beside the lane on
     * the east. They are what a marine running the wall aisles has to put his back to.
     */
    void serviceDrops(String id, String standIn, String station) {
        for (int y = HallFrame.DISPATCH + 2; y + 1 <= l - 3; y += 6) {
            stand(id, standIn, 1, y, 1, 2, station);
            stand(id, standIn, w - 2, y + 3, 1, 2, station);
        }
    }

    /** Crane runways down both long walls, overhead. */
    void runways() {
        for (int y = 1; y + 3 <= l - 2; y += 4) {
            plan.overhead("doodad.ruins-crane-runway", null, 0, y, 1, 4);
            plan.overhead("doodad.ruins-crane-runway", null, w - 1, y, 1, 4);
        }
    }

    /** A tray of cable or pipe overhead down column {@code x}, from {@code from} to {@code to}, a length of it fallen. */
    void tray(String id, String fallen, int x, int from, int to, int fallAt) {
        for (int y = from; y + 1 <= to; y += 2) {
            if (y == fallAt && stand(fallen, "doodad.ruins-cable-tray-fallen", x + 1, y, 1, 4, WorksHallPlan.COLLAPSE)) {
                y += 2;
                continue;
            }
            plan.overhead(id, "doodad.ruins-cable-tray", x, y, 1, 2);
        }
    }

    /** The dispatch end: pallet bays along it, the line's trench under covers, a skid's thrust wash. */
    void dispatch(String station) {
        for (int k = 0; k < 2; k++) {
            plan.floor("doodad.ruins-pallet-bay", null, westL + 4 * k, 1, 3, 3, station);
            plan.floor("doodad.ruins-pallet-bay", null, eastR - 2 - 4 * k, 1, 3, 3, station);
        }
        plan.zone(westL, 1, westL + 6, 3, WorksHallPlan.Zone.BAY);
        plan.zone(eastR - 6, 1, eastR, 3, WorksHallPlan.Zone.BAY);
        plan.wear("doodad.ruins-thrust-wash", cx - 4, 1, 2, 3);
        plan.wear("doodad.ruins-oil-trail", cx + 3, 1, 1, 3);
    }

    /**
     * Banks a skin against the hall's walls, {@code min} to {@code max} cells deep by noise, as storm drift banks
     * snow outdoors.
     */
    void bank(String surface, int min, int max, int salt) {
        for (int y = 0; y < l; y++) {
            for (int x = 0; x < w; x++) {
                int edge = Math.min(Math.min(x, w - 1 - x), Math.min(y, l - 1 - y));
                int depth = min + Math.round(noise(x + y, x - y, 5, salt) * (max - min));
                if (edge < depth) plan.skin(x, y, surface);
            }
        }
    }

    /**
     * Spreads a skin out from sources: each cell takes it where noise beats its distance from the nearest source over
     * {@code reach}, so it is densest at the source and thins into bare floor.
     */
    void spread(String surface, List<int[]> sources, int reach, int salt) {
        for (int y = 0; y < l; y++) {
            for (int x = 0; x < w; x++) {
                float best = Float.MAX_VALUE;
                for (int[] s : sources) best = Math.min(best, (float) Math.hypot(x - s[0], y - s[1]));
                if (best > reach) continue;
                if (noise(x, y, 4, salt) * 1.2f > best / reach) plan.skin(x, y, surface);
            }
        }
    }
}
