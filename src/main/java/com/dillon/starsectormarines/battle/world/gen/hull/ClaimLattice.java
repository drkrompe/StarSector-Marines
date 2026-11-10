package com.dillon.starsectormarines.battle.world.gen.hull;

import com.dillon.starsectormarines.battle.world.gen.hull.HullSite.LatticePoint;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Cell;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The claim lattice ({@code fallen-hull-nouns.md}, Claim lattice): points on a regular grid about the hull's middle, a
 * zone's perimeter apart, each where a stake can stand. Laid by the ground, never moved by play. A grid point whose
 * neighbourhood holds no ground for a stake is a hole: the lattice keeps its regular spacing rather than crowding a
 * point onto the nearest open patch.
 */
public final class ClaimLattice {

    /**
     * Cells between neighbouring lattice points: two stake perimeters of 20, so neighbouring zones' perimeters meet and
     * their relays link. UNTUNED; follows the stake zone's perimeter if the hull's rules settle another.
     */
    public static final int SPACING = 40;
    /** A stake's ground: this many cells square, open the whole way. */
    public static final int STAKE = 5;
    /** Cells kept clear of the map's edge round a stake's ground. */
    static final int EDGE = 3;

    private ClaimLattice() { }

    /** Whether a stake's ground may take this cell: open outdoor ground nothing else holds. */
    @FunctionalInterface
    public interface Ground {
        boolean open(int x, int y);
    }

    /**
     * The lattice on a {@code width} by {@code height} map about {@code (cx, cy)}: each grid point {@code spacing} apart
     * but the middle one, moved to the nearest cell within a quarter spacing whose {@link #STAKE}-square ground is all
     * open, or left out. In ring order, then row, then column; each point's index is its place in that order.
     */
    public static List<LatticePoint> lay(int width, int height, int cx, int cy, int spacing, Ground ground) {
        if (spacing < STAKE * 2) throw new IllegalArgumentException("a lattice of " + spacing + " crowds its stakes");
        int reach = spacing / 4, half = STAKE / 2;
        int cols = width / spacing + 2, rows = height / spacing + 2;
        record Found(int col, int row, int x, int y) { }
        List<Found> found = new ArrayList<>();
        for (int row = -rows; row <= rows; row++) {
            for (int col = -cols; col <= cols; col++) {
                if (col == 0 && row == 0) continue;
                int nx = cx + col * spacing, ny = cy + row * spacing;
                if (nx < -reach || ny < -reach || nx >= width + reach || ny >= height + reach) continue;
                int[] at = nearest(width, height, nx, ny, reach, half, ground);
                if (at != null) found.add(new Found(col, row, at[0], at[1]));
            }
        }
        found.sort(Comparator.comparingInt((Found f) -> Math.max(Math.abs(f.col()), Math.abs(f.row())))
                .thenComparingInt(Found::row).thenComparingInt(Found::col));
        List<LatticePoint> out = new ArrayList<>(found.size());
        for (Found f : found) {
            out.add(new LatticePoint(out.size(), f.col(), f.row(), Math.max(Math.abs(f.col()), Math.abs(f.row())),
                    new Cell(f.x(), f.y())));
        }
        return out;
    }

    /** The open stake ground nearest {@code (nx, ny)} within {@code reach}, by distance then row then column; or null. */
    private static int[] nearest(int width, int height, int nx, int ny, int reach, int half, Ground ground) {
        int[] best = null;
        long bestD = Long.MAX_VALUE;
        for (int y = ny - reach; y <= ny + reach; y++) {
            for (int x = nx - reach; x <= nx + reach; x++) {
                if (x - half < EDGE || y - half < EDGE || x + half >= width - EDGE || y + half >= height - EDGE) continue;
                long d = (long) (x - nx) * (x - nx) + (long) (y - ny) * (y - ny);
                if (d >= bestD || d > (long) reach * reach) continue;
                if (!stands(x, y, half, ground)) continue;
                bestD = d;
                best = new int[]{x, y};
            }
        }
        return best;
    }

    private static boolean stands(int x, int y, int half, Ground ground) {
        for (int dy = -half; dy <= half; dy++) {
            for (int dx = -half; dx <= half; dx++) if (!ground.open(x + dx, y + dy)) return false;
        }
        return true;
    }
}
