package com.dillon.starsectormarines.battle.world.gen.elevation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * The houses a sheltered town builds into the foot of its walls ({@code voids.md}, the crevasse town): terrace rows
 * and blocks along each wall's foot, each one's blind back against the bank and its doors and windows facing the
 * floor. GL-free; the landscape pass asks it once after raising the walls and stamps what it returns.
 *
 * <p>Along each wall the foot is walked from end to end; at each step the longest building that fits is set there,
 * back to the bank, else the next step is tried. A building stands only where the wall is deeper than
 * {@link #MIN_FOOT} under all of it, so never in a pass. Its back is set at the bank's shallowest foot under it and
 * where the foot wanders deeper the building is dug into the bank, no more than {@link #MAX_DIG}, so it stands
 * half in the wall like a house cut into a cliff. The rest of its footprint and the threshold in front must be open
 * ground. Then it is proved as the walls are: every stretch
 * of walkable ground connected before stays connected, small pockets ({@link #MAX_POCKET} cells or fewer) taken into
 * the wall, anything else refusing that building.
 */
public final class CrevasseTerraces {

    /** A terrace building's depth from its back to its front, in cells. */
    public static final int DEPTH = 5;
    /** The deepest a building is dug into the bank where its foot wanders under it: its front row always stands out. */
    static final int MAX_DIG = DEPTH - 1;
    /** The shallowest wall a building is set into: a pass is never built across. */
    static final int MIN_FOOT = 8;
    /** The most cells of the map's margin between the edge and a wall. */
    static final int EDGE_MARGIN = 4;
    /** Cells left open between neighbouring buildings along the foot: an alley through. UNTUNED. */
    static final int SPACING = 3;
    /** The largest pocket of open ground a building may close off and give to the wall. */
    static final int MAX_POCKET = 40;

    private CrevasseTerraces() { }

    /**
     * A building set into a wall's foot.
     *
     * @param length the building's length along the wall (one of the lengths asked for)
     * @param left   its footprint's lowest x
     * @param bottom its footprint's lowest y
     * @param turns  counterclockwise quarter turns from a building whose front faces the low y, so its front faces the
     *               floor
     * @param fill   cells, row-major, taken into the wall: any pocket of open ground it closes off
     * @param dig    cells, row-major, of the wall its footprint is dug out of
     */
    public record Site(int length, int left, int bottom, int turns, boolean[] fill, boolean[] dig) { }

    /**
     * The buildings set into the walls' feet over a {@code w} by {@code h} map.
     *
     * @param along    whether the walls stand along the low-y and high-y edges, else the low-x and high-x
     * @param walls    the walls' cells, row-major
     * @param lengths  the buildings' lengths along the wall, longest first
     * @param open     cells a building may stand on: open ground nothing is reserved on
     * @param walkable cells anything stands on now
     */
    public static List<Site> plan(int w, int h, boolean along, boolean[] walls, int[] lengths, IntPredicate open,
                                  IntPredicate walkable) {
        List<Site> out = new ArrayList<>();
        boolean[] blocked = new boolean[w * h];
        IntPredicate free = i -> !blocked[i] && open.test(i);
        IntPredicate walk = i -> !blocked[i] && walkable.test(i);
        int length = along ? w : h, span = along ? h : w;
        for (int side = 0; side < 2; side++) {
            int[] foot = new int[length];
            for (int t = 0; t < length; t++) {
                // The wall may stand a cell or two in from the edge, where the map's margin is kept.
                int d = 0;
                while (d < EDGE_MARGIN && !walls[cell(w, h, along, side, t, d)]) d++;
                if (d == EDGE_MARGIN) { foot[t] = 0; continue; }
                while (d < span && walls[cell(w, h, along, side, t, d)]) d++;
                foot[t] = d;
            }
            int t = 1;
            while (t < length - 1) {
                Site site = null;
                for (int l : lengths) {
                    site = fit(w, h, along, side, foot, t, l, free, walk);
                    if (site != null) break;
                }
                if (site == null) { t++; continue; }
                out.add(site);
                for (int i = 0; i < blocked.length; i++) if (site.fill()[i]) blocked[i] = true;
                markFootprint(w, h, along, side, foot, t, site.length(), blocked);
                t += site.length() + SPACING;
            }
        }
        return out;
    }

    private static Site fit(int w, int h, boolean along, int side, int[] foot, int t, int l, IntPredicate free,
                            IntPredicate walkable) {
        int length = along ? w : h, span = along ? h : w;
        if (t + l > length - 2) return null;
        int lo = Integer.MAX_VALUE, hi = 0;
        for (int s = t; s < t + l; s++) { lo = Math.min(lo, foot[s]); hi = Math.max(hi, foot[s]); }
        if (lo < MIN_FOOT || hi - lo > MAX_DIG || lo + DEPTH + 1 >= span - 1) return null;
        // Its back at the bank's shallowest foot under it: the open part of its footprint and its threshold open
        // ground, the rest dug out of the bank.
        boolean[] taken = new boolean[w * h], dig = new boolean[w * h];
        for (int s = t; s < t + l; s++) {
            for (int d = lo; d <= lo + DEPTH; d++) {
                int i = cell(w, h, along, side, s, d);
                if (d < foot[s]) { dig[i] = true; continue; }
                if (!free.test(i)) return null;
                if (d < lo + DEPTH) taken[i] = true;
            }
        }
        // The ends: open ground, or the bank.
        for (int s : new int[]{t - 1, t + l}) {
            for (int d = lo; d < lo + DEPTH; d++) {
                int i = cell(w, h, along, side, s, d);
                if (d >= foot[s] && !free.test(i)) return null;
            }
        }
        boolean[] proved = NaturalChasm.proved(w, h, taken, OpenPit.label(w, h, walkable), free, walkable, MAX_POCKET);
        if (proved == null) return null;
        // The wall takes only what the building closes off; the building takes its footprint.
        for (int i = 0; i < proved.length; i++) if (taken[i]) proved[i] = false;
        int left, bottom, turns;
        if (along) {
            left = t;
            bottom = side == 0 ? lo : h - lo - DEPTH;
            turns = side == 0 ? 2 : 0;
        } else {
            bottom = t;
            left = side == 0 ? lo : w - lo - DEPTH;
            turns = side == 0 ? 1 : 3;
        }
        return new Site(l, left, bottom, turns, proved, dig);
    }

    private static void markFootprint(int w, int h, boolean along, int side, int[] foot, int t, int l,
                                      boolean[] blocked) {
        int lo = Integer.MAX_VALUE;
        for (int s = t; s < t + l; s++) lo = Math.min(lo, foot[s]);
        for (int s = t; s < t + l; s++) {
            for (int d = lo; d < lo + DEPTH; d++) blocked[cell(w, h, along, side, s, d)] = true;
        }
    }

    /** The cell {@code d} in from the edge on {@code side}, {@code t} along it. */
    static int cell(int w, int h, boolean along, int side, int t, int d) {
        if (along) return (side == 0 ? d : h - 1 - d) * w + t;
        return t * w + (side == 0 ? d : w - 1 - d);
    }
}
