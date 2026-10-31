package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Road's staging area ({@code cordon-nouns.md}, Cut-off town): the open ground inside the town's compound by the
 * gate the convoys load at, where the trapped townspeople wait and the trucks take them aboard. Holding it is the
 * Road's first objective (Hold Ain Tafrant); defenders stand round it, never on it, so it stays clear for the people
 * walking out to the trucks.
 *
 * <p>Found at setup: from the compound's open cell nearest the gate, every open cell inside the compound's wall it
 * reaches without leaving that ground, within {@link #REACH} cells of it.
 */
public final class CordonStagingArea {

    /** Cells from its anchor (just inside the gate) the staging area runs, each way. */
    public static final int REACH = 6;

    /** Whether a cell can be stood on. */
    public interface Open {
        boolean at(int x, int y);
    }

    private final List<int[]> cells;
    private final Set<Long> keys = new HashSet<>();
    private final int centreX, centreY;

    private CordonStagingArea(List<int[]> cells) {
        this.cells = List.copyOf(cells);
        long sx = 0, sy = 0;
        for (int[] c : cells) {
            keys.add(key(c[0], c[1]));
            sx += c[0];
            sy += c[1];
        }
        // The cell of the area nearest its mean, so the centre is always ground a body can stand on.
        float mx = cells.isEmpty() ? 0 : sx / (float) cells.size(), my = cells.isEmpty() ? 0 : sy / (float) cells.size();
        int[] best = cells.isEmpty() ? new int[]{0, 0} : cells.get(0);
        for (int[] c : cells) {
            if (Math.hypot(c[0] - mx, c[1] - my) < Math.hypot(best[0] - mx, best[1] - my)) best = c;
        }
        centreX = best[0];
        centreY = best[1];
    }

    /**
     * The staging area of a compound whose inside runs {@code left..right} by {@code top..bottom} (inclusive), its
     * loading gate at {@code (gateX, gateY)}: the open cells inside it reached from the open inside cell nearest the
     * gate, within {@link #REACH} cells of that cell. Empty when nothing inside is open. Pure.
     */
    public static CordonStagingArea of(Open open, int left, int top, int right, int bottom, int gateX, int gateY) {
        int[] anchor = null;
        int best = Integer.MAX_VALUE;
        for (int y = top; y <= bottom; y++) {
            for (int x = left; x <= right; x++) {
                if (!open.at(x, y)) continue;
                int d = Math.abs(x - gateX) + Math.abs(y - gateY);
                if (d < best) {
                    best = d;
                    anchor = new int[]{x, y};
                }
            }
        }
        List<int[]> out = new ArrayList<>();
        if (anchor == null) return new CordonStagingArea(out);
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(anchor);
        seen.add(key(anchor[0], anchor[1]));
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            out.add(c);
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = c[0] + d[0], ny = c[1] + d[1];
                if (nx < left || nx > right || ny < top || ny > bottom) continue;
                if (Math.max(Math.abs(nx - anchor[0]), Math.abs(ny - anchor[1])) > REACH) continue;
                if (!open.at(nx, ny) || !seen.add(key(nx, ny))) continue;
                queue.add(new int[]{nx, ny});
            }
        }
        return new CordonStagingArea(out);
    }

    /**
     * The staging area of the compound round {@code compound} on the live map, its loading gate at {@code gate} (the
     * corridor's last cell), or {@code null} where no compound wall is found there.
     */
    public static CordonStagingArea at(BattleSimulation sim, Precinct compound, int[] gate) {
        if (compound == null || gate == null) return null;
        int[] box = CordonGarrisons.perimeterBox(sim.getTopology(), compound);
        if (box == null) return null;
        NavigationGrid grid = sim.getGrid();
        CordonStagingArea area = of((x, y) -> grid.inBounds(x, y) && grid.isWalkable(x, y) && !grid.isDoorway(x, y),
                box[0] + 1, box[1] + 1, box[2] - 1, box[3] - 1, gate[0], gate[1]);
        return area.isEmpty() ? null : area;
    }

    /** The area's cells, nearest the gate first. */
    public List<int[]> cells() { return cells; }

    /** A cell of the area at its middle. */
    public int centreX() { return centreX; }

    public int centreY() { return centreY; }

    public boolean contains(int x, int y) { return keys.contains(key(x, y)); }

    /** Whether a cell is within {@code margin} cells (Chebyshev) of the area. */
    public boolean near(int x, int y, int margin) {
        for (int dy = -margin; dy <= margin; dy++) {
            for (int dx = -margin; dx <= margin; dx++) if (contains(x + dx, y + dy)) return true;
        }
        return false;
    }

    public boolean isEmpty() { return cells.isEmpty(); }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }
}
