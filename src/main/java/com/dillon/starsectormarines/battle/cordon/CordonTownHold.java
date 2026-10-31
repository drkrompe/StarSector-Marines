package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.world.gen.layout.Laid;
import com.dillon.starsectormarines.battle.world.gen.layout.LaidLayout;

import java.util.List;
import java.util.function.IntPredicate;

/**
 * The cut-off town's held ground ({@code cordon-nouns.md}, Cut-off town): the compound and its warren as the creep
 * finds them at setup, kept clear by the townspeople while the front's creep stood weeks round them, so the streets
 * read when the company lands. The front's bores retake it over the battle
 * ({@link com.dillon.starsectormarines.battle.creep.CreepField#mature(IntPredicate)}); the town's own bores, opening
 * later, grow over it at their own pace.
 *
 * <p>The ground is the box round the warren's buildings and the compound, {@link #MARGIN} cells wider, with a ragged
 * edge: a cell is held once it lies deeper inside the box than a seeded threshold of up to {@link #EDGE_BAND} cells,
 * so the creep reaches in in fingers and tongues rather than stopping at a ruled line. Ground within
 * a seeded 0.6 to 1.4 times {@link #BORE_KEEP} cells of a standing bore is never held: a bore keeps the creep round its mouth.
 */
final class CordonTownHold {

    /** Cells past the warren's outermost buildings the held ground reaches. UNTUNED. */
    static final int MARGIN = 6;
    /** Deepest the ragged edge lets the creep in from the box's side, in cells. UNTUNED. */
    static final int EDGE_BAND = 22;
    /** Cells round a standing bore's mouth never held. UNTUNED. */
    static final int BORE_KEEP = 12;
    /** Cells between the edge noise's lattice points: the width of a finger of creep. */
    static final int GRAIN = 9;
    private static final long SALT = 0x544F574E484F4C44L;

    private CordonTownHold() {}

    /**
     * The box {left, top, right, bottom} round the warren's laid buildings and the compound's middle, {@link #MARGIN}
     * wider; {@code null} when no warren was laid (open country, a city, the Line).
     */
    static int[] extent(LaidLayout layout, int compoundX, int compoundY) {
        int l = compoundX, t = compoundY, r = compoundX, b = compoundY;
        boolean any = false;
        for (Laid piece : layout.pieces()) {
            if (piece.kind() != Laid.Kind.STRUCTURE || !piece.isLaid() || !piece.name().startsWith(CordonTownWarren.NAME)) {
                continue;
            }
            for (int[] c : piece.cells()) {
                l = Math.min(l, c[0]);
                t = Math.min(t, c[1]);
                r = Math.max(r, c[0]);
                b = Math.max(b, c[1]);
                any = true;
            }
        }
        return any ? new int[]{l - MARGIN, t - MARGIN, r + MARGIN, b + MARGIN} : null;
    }

    /** Held ground as cell indices on a map {@code width} wide: {@link #held(int, int, int[], List, long)}. */
    static IntPredicate held(int width, int[] box, List<int[]> bores, long seed) {
        return idx -> held(idx % width, idx / width, box, bores, seed);
    }

    /**
     * Whether a cell is held: inside {@code box} deeper than its seeded edge threshold, and more than
     * {@link #BORE_KEEP} cells from every bore in {@code bores} ({x, y}).
     */
    static boolean held(int x, int y, int[] box, List<int[]> bores, long seed) {
        int depth = Math.min(Math.min(x - box[0], box[2] - x), Math.min(y - box[1], box[3] - y));
        if (depth < 0) return false;
        if (depth < EDGE_BAND && depth < EDGE_BAND * edge(x, y, seed)) return false;
        // A bore's keep is ragged too: from a little over half its radius to half again, by the same noise.
        double keep = BORE_KEEP * (0.6 + 0.8 * edge(x, y, seed ^ 0x4B454550L));
        for (int[] bore : bores) {
            long dx = bore[0] - x, dy = bore[1] - y;
            if (dx * dx + dy * dy <= keep * keep) return false;
        }
        return true;
    }

    /** The edge's seeded depth at a cell, 0 to 1: smooth over {@link #GRAIN} cells, with a little grain on top. */
    static double edge(int x, int y, long seed) {
        int gx = Math.floorDiv(x, GRAIN), gy = Math.floorDiv(y, GRAIN);
        double fx = (x - gx * GRAIN) / (double) GRAIN, fy = (y - gy * GRAIN) / (double) GRAIN;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        double a = hash01(gx, gy, seed), b = hash01(gx + 1, gy, seed);
        double c = hash01(gx, gy + 1, seed), d = hash01(gx + 1, gy + 1, seed);
        double smooth = (a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy;
        // Stretched about the middle so the edge runs from right at the box's side to deep in, not hovering half way.
        double stretched = Math.max(0, Math.min(1, (smooth - 0.5) * 2.2 + 0.5));
        return 0.85 * stretched + 0.15 * hash01(x * 31 + 7, y * 17 + 3, seed ^ 0x5EEDL);
    }

    private static double hash01(int x, int y, long seed) {
        long z = seed ^ SALT ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 11) * 0x1.0p-53;
    }
}
