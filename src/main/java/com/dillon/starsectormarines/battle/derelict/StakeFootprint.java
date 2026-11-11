package com.dillon.starsectormarines.battle.derelict;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * How a survey stake occupies the ground (`fallen-hull-nouns.md`, Survey stake): at planting it claims one fixed square
 * footprint, {@link #SIDE} cells across, sized for its established form, and that ground is cleared and kept for it.
 * Every part of the stake stands in its own slot inside the footprint, and a stage grows by filling more of its slots,
 * never by dropping a part on another:
 *
 * <pre>
 *   +-----------------------------+
 *   | CRADLE |            | DRONE |    north
 *   |        +------------+       |
 *   | POST_W |   SPIRE    | POST_E|
 *   |        +------------+       |
 *   |        |   STORE    |       |    south edge
 *   +-----------------------------+
 * </pre>
 *
 * Planted: the spire (the folded pylon). Raised: the spire opens into the node mast, and the flock's cradle and the
 * repair drone's pad are filled. Established: the store on the south edge and the two folded posts.
 */
public final class StakeFootprint {

    /** Cells from the footprint's centre to its edge. */
    public static final int HALF = 5;
    /** The footprint's side, cells. */
    public static final int SIDE = 2 * HALF + 1;

    public enum Part { SPIRE, CRADLE, DRONE, POST_WEST, POST_EAST, STORE }

    /** A part's slot, in cells relative to the footprint's centre, edges inclusive. */
    public record Slot(Part part, int left, int bottom, int right, int top) {
        /** The slot on the ground for a footprint centred at {@code (cx, cy)}. */
        public FallenHullSite.Rect at(int cx, int cy) {
            return new FallenHullSite.Rect(cx + left, cy + bottom, cx + right, cy + top);
        }

        /** The slot's centre cell for a footprint centred at {@code (cx, cy)}. */
        public int[] centre(int cx, int cy) {
            return new int[] {cx + (left + right) / 2, cy + (bottom + top) / 2};
        }
    }

    /** Every slot of the footprint: the spire at the centre, the rest round it. */
    public static final List<Slot> SLOTS = List.of(
            new Slot(Part.SPIRE, -2, -2, 2, 2),
            new Slot(Part.CRADLE, -5, 3, -3, 5),
            new Slot(Part.DRONE, 3, 3, 5, 5),
            new Slot(Part.POST_WEST, -5, -1, -3, 1),
            new Slot(Part.POST_EAST, 3, -1, 5, 1),
            new Slot(Part.STORE, -1, -5, 1, -3));

    private StakeFootprint() { }

    /** The slot a part stands in. */
    public static Slot slot(Part part) {
        for (Slot s : SLOTS) if (s.part() == part) return s;
        throw new IllegalArgumentException("No slot for " + part);
    }

    /** The parts standing at a stage: each stage's are the stage before's and more. */
    public static Set<Part> parts(SurveyStake.Stage stage) {
        return switch (stage) {
            case PLANTED -> EnumSet.of(Part.SPIRE);
            case RAISED -> EnumSet.of(Part.SPIRE, Part.CRADLE, Part.DRONE);
            case ESTABLISHED -> EnumSet.allOf(Part.class);
            case SCRAP -> EnumSet.noneOf(Part.class);
        };
    }

    /** The footprint centred at {@code (cx, cy)}. */
    public static FallenHullSite.Rect footprint(int cx, int cy) {
        return new FallenHullSite.Rect(cx - HALF, cy - HALF, cx + HALF, cy + HALF);
    }

    /** Every cell of the footprint centred at {@code (cx, cy)}, row by row. */
    public static List<int[]> cells(int cx, int cy) {
        List<int[]> out = new ArrayList<>(SIDE * SIDE);
        for (int y = cy - HALF; y <= cy + HALF; y++) {
            for (int x = cx - HALF; x <= cx + HALF; x++) out.add(new int[] {x, y});
        }
        return out;
    }

    /**
     * Where a stake planted at its lattice point stands: the nearest centre to {@code (x, y)}, ring by ring out to
     * {@code search} cells, whose whole footprint {@code buildable} allows (ground a stake may clear and stand on:
     * walkable, no wall or building, no fixed prop, no other stake's); null when none does.
     */
    public static int[] seat(int x, int y, int search, BiPredicate<Integer, Integer> buildable) {
        for (int r = 0; r <= search; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    if (clear(x + dx, y + dy, buildable)) return new int[] {x + dx, y + dy};
                }
            }
        }
        return null;
    }

    private static boolean clear(int cx, int cy, BiPredicate<Integer, Integer> buildable) {
        for (int[] c : cells(cx, cy)) if (!buildable.test(c[0], c[1])) return false;
        return true;
    }
}
