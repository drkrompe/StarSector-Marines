package com.dillon.starsectormarines.battle.unit;

/**
 * The nearest cell a body can be put down in, when the one it was handed is
 * already holding somebody.
 *
 * <p>Two bodies in one cell is not a drawing nuisance. Occupancy, separation,
 * cover and the pathfinder all read a cell as holding at most one thing, so a
 * pair sharing one are each absent from the other's picture for as long as they
 * stay there — and one of them was never routed anywhere, because nothing ever
 * told it to move. The rule that prevents it belongs at the seam that mints
 * bodies ({@link UnitRosterService}); this is the geometry that rule needs, and
 * deliberately nothing else: who has to move, and whether moving them is
 * allowed, are decisions the seam and its callers make.
 *
 * <p>Rings outward rather than sampling a disc, so one cell over is both the
 * usual answer and the cheapest one to find, and the walk stops at the first
 * ring that answers at all. The scan order within a ring is fixed, because a
 * battle is replayed for evidence and an arrival that lands north on one run
 * and east on the next makes two traces of one battle.
 *
 * <p>The two predicates are separate on purpose. <em>Standable</em> is about
 * the ground — in bounds, walkable, outside whatever the caller is stamping —
 * and <em>taken</em> is about the bodies on it. A caller usually knows the
 * first cheaply and the second only by asking, and splitting them lets the
 * cheap test reject most of a ring before anything is scanned.
 */
public final class StandingRoom {

    /** What {@link #nearest} returns when nothing within reach will take the body. */
    public static final long NOWHERE = Long.MIN_VALUE;

    /** Whether the ground at a cell is somewhere this body could stand at all. */
    @FunctionalInterface
    public interface Standable {
        boolean at(int x, int y);
    }

    /** Whether some other body is already standing in a cell. */
    @FunctionalInterface
    public interface Taken {
        boolean at(int x, int y);
    }

    private StandingRoom() {
    }

    /**
     * The nearest standable, unoccupied cell to ({@code fromX}, {@code fromY}),
     * within {@code radius} Chebyshev rings and never the starting cell itself.
     *
     * @return the cell packed for {@link #cellX} / {@link #cellY}, or
     *         {@link #NOWHERE} when no ring answers
     */
    public static long nearest(int fromX, int fromY, int radius,
                               Standable standable, Taken taken) {
        for (int r = 1; r <= radius; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int x = fromX + dx;
                    int y = fromY + dy;
                    if (!standable.at(x, y)) continue;
                    if (taken.at(x, y)) continue;
                    return pack(x, y);
                }
            }
        }
        return NOWHERE;
    }

    public static int cellX(long cell) { return (int) (cell >> 32); }

    public static int cellY(long cell) { return (int) cell; }

    private static long pack(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }
}
