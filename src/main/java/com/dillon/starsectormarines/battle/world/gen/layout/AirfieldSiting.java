package com.dillon.starsectormarines.battle.world.gen.layout;

import java.util.ArrayList;
import java.util.List;

/**
 * Where an {@link StructureKind#AIRFIELD airfield} stands on open country, and the few buildings that grew up round it
 * ({@code mission-layout.md}, Structure). Mission-neutral: a mission names the ground it wants a field on and what to
 * keep clear, and gets back the field and its hamlet as ordinary structures.
 *
 * <p>The rules. A field never stands on a way (a road or a farm track) and never cuts one, and neither it nor its
 * hamlet stands in the layout's column clearance ({@link LayoutGround#inColumnClearance}). It stands beside a way: the
 * gate in its fence on the apron side (the side away from its strip), or failing that on one of its short sides or by
 * a dogleg from the apron gate, opens onto a way off the clearance, and a short access track joins them
 * ({@link #access}). It stands on
 * ground nobody farms where there is any, and as near the ground the mission wanted as those allow. Its strip runs
 * along x, so it suits a front across x; it may face either way along y, whichever puts its apron toward the way.
 *
 * <p>Its {@link #hamlet hamlet} is a handful of buildings on its flanks within its own depth, clustered toward its gate: crew
 * quarters, a store shed, a farmstead, a pump hut and a granary. They keep off the field and its access track, farmed ground, the strip's
 * run-up past either end, and off the ways, so the strip, the bays and the gate stay clear.
 */
public final class AirfieldSiting {

    /** Cells from a gate to the way it opens on, at most: the access track's longest run. */
    public static final int ACCESS_REACH = 14;
    /** Cells past either end of the strip, on the strip's half of the field, kept clear for the run-up and approach. */
    public static final int RUN_UP = 30;
    /** Rows of the field from its strip-side edge that the strip and its shoulders take: the run-up's width. */
    static final int STRIP_BAND = 8;
    /** Cost per cell a hamlet building stands from the nearest one already up, against its pull toward the gate. */
    static final float CLUSTER_COST = 2f;
    /** Cells a hamlet's buildings stand out from the field's short ends, at most, and the gap they keep. */
    static final int HAMLET_REACH = 44, HAMLET_GAP = 2;
    /** What a hamlet raises, in order, while there is room. */
    static final List<StructureKind> HAMLET = List.of(StructureKind.CREW_QUARTERS, StructureKind.STORE_SHED,
            StructureKind.FARMSTEAD, StructureKind.PUMP_HUT, StructureKind.GRANARY);
    /** Cost per cell of farmed ground a footprint takes, per cell of access track, for a short-side gate, per cell off. */
    static final float FARMED_COST = 1f, ACCESS_COST = 6f, FLANK_COST = 20f, DISTANCE_COST = 0.4f;
    /** Cost of a field with no way within reach of any gate: still laid where nothing better is, but last. */
    static final float NO_ACCESS_COST = 4000f;

    private AirfieldSiting() {}

    /** A field's gate and the cells of its access track, verge to way; no track when no way is within reach. */
    public record Access(int gateX, int gateY, List<int[]> track) {
        public boolean reaches() {
            return !track.isEmpty();
        }
    }

    /**
     * The airfield called {@code name} sited inside the inclusive {@code region} [x0, y0, x1, y1] nearest
     * {@code (wantX, wantY)} by the rules above, on cells {@code open} passes (ground it may stand on, not a way);
     * {@code null} when no footprint fits.
     */
    public static Structure site(LayoutGround ground, String name, int[] region, int wantX, int wantY,
                                 LineCourse.CellTest open) {
        int w = StructureKind.AIRFIELD.along, h = StructureKind.AIRFIELD.deep;
        int x0 = Math.max(1, region[0]), y0 = Math.max(1, region[1]);
        int x1 = Math.min(ground.width() - 2, region[2]), y1 = Math.min(ground.height() - 2, region[3]);
        if (x1 - x0 + 1 < w || y1 - y0 + 1 < h) return null;
        // Prefix sums of cells a footprint may not take and of farmed cells, so every candidate is costed at once.
        int cw = x1 - x0 + 2, ch = y1 - y0 + 2;
        int[] blocked = new int[cw * ch], farmed = new int[cw * ch];
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                int i = (y - y0 + 1) * cw + (x - x0 + 1);
                boolean way = ground.isWay(x, y) || ground.inColumnClearance(x, y);
                blocked[i] = (way || !open.test(x, y) ? 1 : 0) + blocked[i - 1] + blocked[i - cw] - blocked[i - cw - 1];
                farmed[i] = (ground.farmed(x, y) ? 1 : 0) + farmed[i - 1] + farmed[i - cw] - farmed[i - cw - 1];
            }
        }
        Structure best = null;
        float bestCost = Float.MAX_VALUE;
        for (int y = y0; y + h - 1 <= y1; y++) {
            for (int x = x0; x + w - 1 <= x1; x++) {
                if (sum(blocked, cw, x - x0, y - y0, w, h) > 0) continue;
                float base = FARMED_COST * sum(farmed, cw, x - x0, y - y0, w, h)
                        + DISTANCE_COST * (float) Math.hypot(x + w / 2f - wantX, y + h / 2f - wantY);
                if (base >= bestCost) continue;
                for (int turns = 0; turns <= 2; turns += 2) {
                    Structure s = new Structure(name, StructureKind.AIRFIELD, x, y, turns, false);
                    float cost = base + accessCost(ground, s);
                    if (cost < bestCost) {
                        bestCost = cost;
                        best = s;
                    }
                }
            }
        }
        return best;
    }

    private static int sum(int[] table, int cw, int x, int y, int w, int h) {
        return table[(y + h) * cw + x + w] - table[y * cw + x + w] - table[(y + h) * cw + x] + table[y * cw + x];
    }

    private static float accessCost(LayoutGround ground, Structure s) {
        Access apron = gateAccess(ground, s);
        if (apron.reaches()) return ACCESS_COST * apron.track().size() / 3f;
        Access other = sideAccess(ground, s);
        if (other.reaches()) return FLANK_COST + ACCESS_COST * other.track().size() / 3f;
        return NO_ACCESS_COST;
    }

    /**
     * How {@code field} is reached: its apron gate's track straight out where a way is in reach; else the shorter of
     * a short side's track and a dogleg from the apron gate (out past the fence, along past the field's end, then on
     * toward the strip's side); else none. No track enters the column clearance ({@link LayoutGround#inColumnClearance}):
     * a field never joins a column's way where the column forms up, only a way off it.
     */
    public static Access access(LayoutGround ground, Structure field) {
        Access apron = gateAccess(ground, field);
        if (apron.reaches()) return apron;
        Access other = sideAccess(ground, field);
        return other.reaches() ? other : apron;
    }

    /** +1 when the field's apron faces north (its strip on the south side), -1 when it faces south. */
    static int apronStep(Structure field) {
        return field.turns() == 2 ? 1 : -1;
    }

    /** The apron gate's middle row: the fence line across the verge from the footprint's apron edge. */
    private static int fenceY(Structure f) {
        return apronStep(f) < 0 ? f.y() + StructureFit.AIRFIELD_VERGE : f.y() + f.height() - 1 - StructureFit.AIRFIELD_VERGE;
    }

    private static Access gateAccess(LayoutGround ground, Structure f) {
        // From the fence, across the verge, out to the way: the gate is three cells wide, and so is its track.
        int gx = f.x() + f.width() / 2, gy = fenceY(f);
        List<int[]> cells = new ArrayList<>();
        return leg(ground, cells, gx, gy, 0, apronStep(f), ACCESS_REACH + StructureFit.AIRFIELD_VERGE) == Leg.REACHED
                ? new Access(gx, gy, cells) : new Access(gx, gy, List.of());
    }

    /** The shortest of the short sides' straight tracks and the apron gate's two doglegs. */
    private static Access sideAccess(LayoutGround ground, Structure f) {
        List<Access> found = new ArrayList<>();
        int verge = StructureFit.AIRFIELD_VERGE, gy = f.y() + f.height() / 2;
        for (int side = -1; side <= 1; side += 2) {
            // A short side's gate stands at the field's middle across.
            int sx = side < 0 ? f.x() + verge : f.x() + f.width() - 1 - verge;
            List<int[]> cells = new ArrayList<>();
            if (leg(ground, cells, sx, gy, side, 0, ACCESS_REACH + verge) == Leg.REACHED) found.add(new Access(sx, gy, cells));
            found.add(dogleg(ground, f, side));
        }
        Access best = new Access(f.x() + f.width() / 2, fenceY(f), List.of());
        for (Access a : found) {
            if (a.reaches() && (!best.reaches() || a.track().size() < best.track().size())) best = a;
        }
        return best;
    }

    /** Cells a dogleg runs clear of the footprint before it turns. */
    static final int DOGLEG_CLEAR = 3;

    /** Out of the apron gate past the footprint, along it toward {@code side} past its end, then back toward the strip. */
    private static Access dogleg(LayoutGround ground, Structure f, int side) {
        int step = apronStep(f), gx = f.x() + f.width() / 2, gy = fenceY(f);
        int outY = step < 0 ? f.y() - 1 - DOGLEG_CLEAR : f.y() + f.height() + DOGLEG_CLEAR;
        int endX = side < 0 ? f.x() - 1 - DOGLEG_CLEAR : f.x() + f.width() + DOGLEG_CLEAR;
        List<int[]> cells = new ArrayList<>();
        Access none = new Access(gx, gy, List.of());
        Leg out = leg(ground, cells, gx, gy, 0, step, Math.abs(outY - gy));
        if (out == Leg.REACHED) return new Access(gx, gy, cells);
        if (out == Leg.BLOCKED) return none;
        Leg along = leg(ground, cells, gx, outY, side, 0, Math.abs(endX - gx));
        if (along == Leg.REACHED) return new Access(gx, gy, cells);
        if (along == Leg.BLOCKED) return none;
        Leg back = leg(ground, cells, endX, outY, 0, -step, f.height() + 2 * DOGLEG_CLEAR + ACCESS_REACH);
        return back == Leg.REACHED ? new Access(gx, gy, cells) : none;
    }

    private enum Leg { REACHED, OPEN, BLOCKED }

    /**
     * One straight leg of a three-wide track from {@code (x, y)}, {@code n} cells heading {@code (dx, dy)}, its cells
     * added to {@code cells}: {@code REACHED} at a way, {@code BLOCKED} at ground it cannot cross (unwalkable, or in
     * the column clearance), else {@code OPEN} at its end.
     */
    private static Leg leg(LayoutGround ground, List<int[]> cells, int x, int y, int dx, int dy, int n) {
        for (int k = 1; k <= n; k++) {
            int cx = x + dx * k, cy = y + dy * k;
            boolean way = false, blocked = false;
            for (int o = -1; o <= 1; o++) {
                int ox = dx == 0 ? cx + o : cx, oy = dx == 0 ? cy : cy + o;
                if (ground.inColumnClearance(ox, oy)) blocked = true;
                else if (ground.isWay(ox, oy)) way = true;
                else if (!ground.walkable(ox, oy)) blocked = true;
            }
            if (blocked) return Leg.BLOCKED;
            if (way) return Leg.REACHED;
            for (int o = -1; o <= 1; o++) cells.add(dx == 0 ? new int[]{cx + o, cy} : new int[]{cx, cy + o});
        }
        return Leg.OPEN;
    }


    /**
     * The hamlet round {@code field}: up to {@link #HAMLET}'s buildings, named {@code field.name() + ".<n>"}, on cells
     * {@code open} passes, along the apron's way and toward the gate, clear of the field, its access track, its strip's
     * run-up and the ways. Fewer, or none, where the ground has no room.
     */
    public static List<Structure> hamlet(LayoutGround ground, Structure field, LineCourse.CellTest open) {
        int fw = field.width(), fh = field.height();
        int rx0 = Math.max(1, field.x() - HAMLET_REACH), rx1 = Math.min(ground.width() - 2, field.x() + fw - 1 + HAMLET_REACH);
        int step = apronStep(field);
        // On its flanks, within the field's own depth: never out past the apron edge, where its way runs.
        int ry0 = field.y(), ry1 = field.y() + fh - 1;
        int gw = rx1 - rx0 + 1, gh = ry1 - ry0 + 1;
        if (gw <= 0 || gh <= 0) return List.of();
        boolean[] kept = new boolean[gw * gh];
        // The field and its gap; the strip's own rows out past both ends for the run-up and the approach.
        int stripY0 = step > 0 ? field.y() : field.y() + fh - STRIP_BAND;
        int stripY1 = step > 0 ? field.y() + STRIP_BAND - 1 : field.y() + fh - 1;
        keep(kept, rx0, ry0, gw, gh, field.x() - HAMLET_GAP, field.y() - HAMLET_GAP,
                field.x() + fw - 1 + HAMLET_GAP, field.y() + fh - 1 + HAMLET_GAP);
        keep(kept, rx0, ry0, gw, gh, field.x() - RUN_UP, stripY0, field.x() + fw - 1 + RUN_UP, stripY1);
        for (int[] c : access(ground, field).track()) keep(kept, rx0, ry0, gw, gh, c[0] - 1, c[1] - 1, c[0] + 1, c[1] + 1);
        for (int y = ry0; y <= ry1; y++) {
            for (int x = rx0; x <= rx1; x++) {
                if (!open.test(x, y) || ground.isWay(x, y) || ground.inColumnClearance(x, y) || ground.farmed(x, y)) kept[(y - ry0) * gw + (x - rx0)] = true;
                else if (ground.isWay(x - 1, y) || ground.isWay(x + 1, y) || ground.isWay(x, y - 1) || ground.isWay(x, y + 1)) {
                    kept[(y - ry0) * gw + (x - rx0)] = true;
                }
            }
        }
        // Doors toward the way the apron faces.
        int turns = step > 0 ? 2 : 0;
        int gateX = field.x() + fw / 2, edgeY = step > 0 ? field.y() + fh - 1 : field.y();
        List<Structure> out = new ArrayList<>();
        int n = 0;
        for (StructureKind kind : HAMLET) {
            Structure probe = new Structure(field.name() + "." + (n + 1), kind, 0, 0, turns, false);
            int w = probe.width(), h = probe.height();
            int[] best = null;
            float bestCost = Float.MAX_VALUE;
            for (int y = ry0; y + h - 1 <= ry1; y++) {
                for (int x = rx0; x + w - 1 <= rx1; x++) {
                    if (!clear(kept, rx0, ry0, gw, x, y, w, h)) continue;
                    // Toward the gate along the way, and out to the apron's edge: a hamlet on its road.
                    float cost = Math.abs(x + w / 2f - gateX) + 2f * Math.abs((step > 0 ? y + h - 1 : y) - edgeY);
                    // And beside the buildings already up, so they read as one cluster rather than a scatter.
                    float near = Float.MAX_VALUE;
                    for (Structure o : out) {
                        near = Math.min(near, (float) Math.hypot(x + w / 2f - o.x() - o.width() / 2f,
                                y + h / 2f - o.y() - o.height() / 2f));
                    }
                    if (!out.isEmpty()) cost += CLUSTER_COST * near;
                    if (cost < bestCost) {
                        bestCost = cost;
                        best = new int[]{x, y};
                    }
                }
            }
            if (best == null) continue;
            Structure s = new Structure(field.name() + "." + (++n), kind, best[0], best[1], turns, false);
            out.add(s);
            keep(kept, rx0, ry0, gw, gh, s.x() - HAMLET_GAP, s.y() - HAMLET_GAP,
                    s.x() + w - 1 + HAMLET_GAP, s.y() + h - 1 + HAMLET_GAP);
        }
        return out;
    }

    private static void keep(boolean[] kept, int rx0, int ry0, int gw, int gh, int x0, int y0, int x1, int y1) {
        for (int y = Math.max(ry0, y0); y <= Math.min(ry0 + gh - 1, y1); y++) {
            for (int x = Math.max(rx0, x0); x <= Math.min(rx0 + gw - 1, x1); x++) kept[(y - ry0) * gw + (x - rx0)] = true;
        }
    }

    private static boolean clear(boolean[] kept, int rx0, int ry0, int gw, int x, int y, int w, int h) {
        for (int cy = y; cy < y + h; cy++) {
            for (int cx = x; cx < x + w; cx++) if (kept[(cy - ry0) * gw + (cx - rx0)]) return false;
        }
        return true;
    }
}
