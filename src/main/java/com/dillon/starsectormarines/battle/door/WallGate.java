package com.dillon.starsectormarines.battle.door;

import java.util.List;

/**
 * A gate in a walled place's wall ({@code precincts.md}, Gates are leaf doors): the run of wall-line cells a road
 * crosses, hung with a pair of sliding leaves that part for whoever comes through and close behind them, as a locked
 * door's or a ship's compartment door's do. Presentation only: the way through is an opening the wall left, and the
 * grid owns its passage and sight; nobody waits on the leaves.
 *
 * <p>The leaves span the gate along {@code spanX ? x : y} over the cells {@code min..max} and stand on the wall line
 * at cell {@code across}; {@code (outX, outY)} is the step out of the place. A gate is <b>forced</b> once the wall
 * either side of it, the jamb its leaves withdraw into, has been breached: a leaf has gone with it.
 */
public record WallGate(boolean spanX, int min, int max, int across, int outX, int outY) {

    public WallGate {
        if (max < min) throw new IllegalArgumentException("a gate has width");
        if (Math.abs(outX) + Math.abs(outY) != 1) throw new IllegalArgumentException("outward step " + outX + "," + outY);
        if (spanX != (outY != 0)) throw new IllegalArgumentException("the leaves span the wall, across the way out");
    }

    /** The gate over the wall-line {@code cells} a road crosses, leading out by {@code (outX, outY)}. */
    public static WallGate of(List<int[]> cells, int outX, int outY) {
        if (cells.isEmpty()) throw new IllegalArgumentException("a gate needs its cells");
        boolean spanX = outY != 0;
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        long across = 0;
        for (int[] c : cells) {
            int along = spanX ? c[0] : c[1];
            min = Math.min(min, along);
            max = Math.max(max, along);
            across += spanX ? c[1] : c[0];
        }
        return new WallGate(spanX, min, max, (int) Math.round(across / (double) cells.size()), outX, outY);
    }

    /** Cells the gate spans. */
    public int width() {
        return max - min + 1;
    }

    /** The wall cells either side of the gate on its line, its jambs: {@code {{x, y}, {x, y}}}, low end first. */
    public int[][] jambs() {
        return spanX ? new int[][]{{min - 1, across}, {max + 1, across}}
                : new int[][]{{across, min - 1}, {across, max + 1}};
    }

    /** Whether a jamb has been breached, {@code open} telling which cells stand open. */
    public boolean forced(Open open) {
        for (int[] j : jambs()) if (open.at(j[0], j[1])) return true;
        return false;
    }

    /** Which cells stand open (walkable). */
    public interface Open {
        boolean at(int x, int y);
    }
}
