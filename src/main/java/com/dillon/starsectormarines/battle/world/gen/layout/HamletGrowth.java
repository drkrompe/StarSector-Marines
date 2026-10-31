package com.dillon.starsectormarines.battle.world.gen.layout;

import java.util.ArrayList;
import java.util.List;

/**
 * A hamlet grown round a placed structure ({@code mission-layout.md}, Hamlet): a short lane of its own, joined at one
 * end to a way, with up to {@link #MAX} buildings on fenced or hedged yards fronting it either side, their gates and
 * doors on the lane. Mission-neutral and pure: the caller says which cells a yard or the lane may take, which cells
 * the lane may join, and near what the hamlet belongs; the lane and the buildings come back as layout pieces to lay.
 *
 * <p>A hamlet stands on wild ground or a fallow parcel, never on a planted crop ({@link #unplanted}).
 *
 * <p>Of every lane that fits, the one that seats the most buildings wins, a lane that joins a way counting for a
 * building and a half, then the one nearest the structure. So a hamlet stands on whichever side of its structure has the most open ground, and is as
 * small as the ground makes it where room is short.
 */
public final class HamletGrowth {

    /** Buildings a hamlet raises at most, and what it raises, in order, while there is room. */
    public static final int MAX = 6;
    static final List<StructureKind> KINDS = List.of(StructureKind.CREW_QUARTERS, StructureKind.MESS_HALL,
            StructureKind.STORE_SHED, StructureKind.PUMP_HUT, StructureKind.HOMESTEAD, StructureKind.RADIO_HUT);
    /** The lane's width, the lengths tried, the verge between lane and yard, the gap between yards. */
    static final int LANE = 3, VERGE = 1, GAP = 2;
    static final int[] LENGTHS = {20, 28, 36, 44, 52, 60};
    /** Cells past the last yard the lane runs on, its turning space. */
    static final int LANE_END = 3;
    /** What a lane that opens onto a way is worth against a building more (1000): a joined lane wins by one building. */
    static final float JOINED = 1500f;

    private HamletGrowth() {}

    /**
     * Whether a hamlet may take the cell as far as the farmland goes: anything but a planted crop. Wild ground and a
     * fallow parcel, worked but unplanted, are open to a hamlet; a row crop, grain, pasture or a farm's yard is not.
     */
    public static boolean unplanted(LayoutGround ground, int x, int y) {
        return !ground.planted(x, y);
    }

    /** The hamlet's buildings, named {@code <prefix>.<n>}, and its lane's cells ({@code null} lane when none). */
    public record Hamlet(List<Structure> buildings, List<int[]> lane) {
        public static final Hamlet NONE = new Hamlet(List.of(), List.of());
    }

    /**
     * The hamlet inside the inclusive {@code region} [x0, y0, x1, y1] nearest {@code (nearX, nearY)}. {@code lane}:
     * cells the lane may take; {@code yard}: cells a yard may take; {@code join}: cells the lane's end may open onto
     * (a way, a track).
     */
    public static Hamlet grow(String prefix, int[] region, int nearX, int nearY,
                              LineCourse.CellTest lane, LineCourse.CellTest yard, LineCourse.CellTest join) {
        int rx0 = region[0], ry0 = region[1], gw = region[2] - rx0 + 1, gh = region[3] - ry0 + 1;
        if (gw <= 0 || gh <= 0) return Hamlet.NONE;
        int[] laneSum = new int[(gw + 1) * (gh + 1)], yardSum = new int[(gw + 1) * (gh + 1)];
        for (int y = 0; y < gh; y++) {
            for (int x = 0; x < gw; x++) {
                int i = (y + 1) * (gw + 1) + x + 1;
                laneSum[i] = (lane.test(rx0 + x, ry0 + y) ? 0 : 1) + laneSum[i - 1] + laneSum[i - gw - 1] - laneSum[i - gw - 2];
                yardSum[i] = (yard.test(rx0 + x, ry0 + y) ? 0 : 1) + yardSum[i - 1] + yardSum[i - gw - 1] - yardSum[i - gw - 2];
            }
        }
        Grid g = new Grid(rx0, ry0, gw, gh, laneSum, yardSum);
        Hamlet best = Hamlet.NONE;
        float bestScore = Float.NEGATIVE_INFINITY;
        for (boolean alongX : new boolean[]{true, false}) {
            for (int len : LENGTHS) {
                int lw = alongX ? len : LANE, lh = alongX ? LANE : len;
                for (int y = ry0; y + lh - 1 < ry0 + gh; y++) {
                    for (int x = rx0; x + lw - 1 < rx0 + gw; x++) {
                        if (!g.clear(g.lane, x, y, lw, lh)) continue;
                        // Which end opens onto a way: the low end, the high end, or neither.
                        boolean low = joins(join, alongX, x, y, lw, lh, true), high = joins(join, alongX, x, y, lw, lh, false);
                        Hamlet h = seat(prefix, g, alongX, x, y, len, low || !high);
                        if (h.buildings().isEmpty()) continue;
                        int[] mid = h.lane().get(h.lane().size() / 2);
                        float score = 1000f * h.buildings().size() + (low || high ? JOINED : 0f)
                                - (float) Math.hypot(mid[0] - nearX, mid[1] - nearY) - 0.5f * h.lane().size() / LANE;
                        if (score > bestScore) {
                            bestScore = score;
                            best = h;
                        }
                    }
                }
            }
        }
        return best;
    }

    private static boolean joins(LineCourse.CellTest join, boolean alongX, int x, int y, int lw, int lh, boolean low) {
        for (int o = 0; o < LANE; o++) {
            int cx = alongX ? (low ? x - 1 : x + lw) : x + o, cy = alongX ? y + o : (low ? y - 1 : y + lh);
            if (join.test(cx, cy)) return true;
        }
        return false;
    }

    /**
     * The buildings seated along the lane at {@code (x, y)}, {@code len} long, from its joined end ({@code fromLow}:
     * the low end), both sides, in {@link #KINDS} order; the lane trimmed to its last yard and a turning space.
     */
    private static Hamlet seat(String prefix, Grid g, boolean alongX, int x, int y, int len, boolean fromLow) {
        List<StructureKind> left = new ArrayList<>(KINDS);
        List<Structure> out = new ArrayList<>();
        int reach = 0;
        for (int side = 0; side < 2 && out.size() < MAX; side++) {
            // Side 0 is the high side (north or east), its doors on the lane at its low edge.
            int turns = alongX ? (side == 0 ? 0 : 2) : (side == 0 ? 1 : 3);
            int pos = 0;
            while (pos < len && out.size() < MAX && !left.isEmpty()) {
                boolean placed = false;
                for (StructureKind kind : left) {
                    Structure probe = new Structure("probe", kind, 0, 0, turns, false);
                    int w = probe.width(), h = probe.height(), run = alongX ? w : h;
                    if (pos + run > len) continue;
                    int at = fromLow ? pos : len - pos - run;
                    int bx, by;
                    if (alongX) {
                        bx = x + at;
                        by = side == 0 ? y + LANE + VERGE : y - VERGE - h;
                    } else {
                        by = y + at;
                        bx = side == 0 ? x + LANE + VERGE : x - VERGE - w;
                    }
                    if (!g.clear(g.yard, bx, by, w, h)) continue;
                    out.add(new Structure(prefix + "." + (out.size() + 1), kind, bx, by, turns, false));
                    left.remove(kind);
                    pos += run + GAP;
                    reach = Math.max(reach, pos - GAP);
                    placed = true;
                    break;
                }
                if (!placed) pos++;
            }
        }
        if (out.isEmpty()) return Hamlet.NONE;
        int keep = Math.min(len, reach + LANE_END);
        List<int[]> cells = new ArrayList<>();
        for (int k = 0; k < keep; k++) {
            int at = fromLow ? k : len - 1 - k;
            for (int o = 0; o < LANE; o++) cells.add(alongX ? new int[]{x + at, y + o} : new int[]{x + o, y + at});
        }
        return new Hamlet(List.copyOf(out), List.copyOf(cells));
    }

    /** Prefix sums of the cells a lane and a yard may not take, over the region. */
    private record Grid(int rx0, int ry0, int gw, int gh, int[] lane, int[] yard) {
        boolean clear(int[] table, int x, int y, int w, int h) {
            int lx = x - rx0, ly = y - ry0;
            if (lx < 0 || ly < 0 || lx + w > gw || ly + h > gh) return false;
            int s = table[(ly + h) * (gw + 1) + lx + w] - table[ly * (gw + 1) + lx + w]
                    - table[(ly + h) * (gw + 1) + lx] + table[ly * (gw + 1) + lx];
            return s == 0;
        }
    }
}
