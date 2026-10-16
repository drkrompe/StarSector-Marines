package com.dillon.starsectormarines.battle.world.gen.fortress;

import java.util.function.BiPredicate;

/**
 * Where a bastion's guns stand. A bastion is a salient of the works: its gun has to see <em>out</em> of it, and a gun
 * standing in the open floor of the bastion's pocket sees nothing but the wall that surrounds it. So a nest stands in
 * an embrasure on the wall's own outer face, and a missile battery stands in the pocket behind it, which is the one
 * place a launcher that fires over its own works wants to be.
 *
 * <p>Pure geometry over a plan's {@code inside} grid, so each kind of fort can ask it of its own works.
 */
public final class BastionSites {

    private BastionSites() {}

    /** Whether the cell is inside the works. */
    private static boolean in(boolean[][] inside, int x, int y) {
        return x >= 0 && y >= 0 && x < inside.length && y < inside[0].length && inside[x][y];
    }

    /** Whether the cell is inside the works and has a side open to the outside: the wall's outer face. */
    public static boolean isOuterFace(boolean[][] inside, int x, int y) {
        return in(inside, x, y) && (!in(inside, x + 1, y) || !in(inside, x - 1, y)
                || !in(inside, x, y + 1) || !in(inside, x, y - 1));
    }

    /**
     * The embrasure of the bastion centred on {@code (cx, cy)} that looks along the direction {@code (dx, dy)}: the cell of
     * the outer face reached by walking that way out from the centre until the works end. Where a diagonal leaves
     * through a corner of two cells (a round tower's staircase), the face cell beside the last cell on it is taken.
     *
     * @return the footprint cell, or {@code null} when the centre is not inside the works
     */
    public static int[] embrasure(boolean[][] inside, int cx, int cy, int dx, int dy) {
        if (!in(inside, cx, cy)) return null;
        int x = cx, y = cy;
        while (in(inside, x + dx, y + dy)) {
            x += dx;
            y += dy;
        }
        int[][] candidates = {{x, y}, {x + dx, y}, {x, y + dy}, {x - dx, y}, {x, y - dy}};
        for (int[] c : candidates) {
            if (isOuterFace(inside, c[0], c[1])) return c;
        }
        return new int[]{x, y};
    }

    /** The middle of a three-by-three launcher pocket {@code steps} cells from the bastion centre toward its tip, along the diagonal. */
    public static int[] pocket(int cx, int cy, int dx, int dy, int steps) {
        return new int[]{cx + dx * steps, cy + dy * steps};
    }

    /** Reach of a mount's concrete collar from its middle: the three-by-three body and one ring about it. */
    private static final int COLLAR_REACH = 2;

    /**
     * Like {@link #pocket}, but walks back toward the bastion centre, a cell at a time, from {@code maxSteps} until the
     * mount's whole collar (the cells within two of its middle) is clear of the works' wall, so the wall never swallows a
     * piece of it. Where none clears, it keeps {@code maxSteps}.
     */
    public static int[] pocketClearOfWall(int cx, int cy, int dx, int dy, int maxSteps, BiPredicate<Integer, Integer> isWall) {
        for (int steps = maxSteps; steps >= 0; steps--) {
            int[] at = pocket(cx, cy, dx, dy, steps);
            if (collarClear(at, isWall)) return at;
        }
        return pocket(cx, cy, dx, dy, maxSteps);
    }

    private static boolean collarClear(int[] middle, BiPredicate<Integer, Integer> isWall) {
        for (int x = middle[0] - COLLAR_REACH; x <= middle[0] + COLLAR_REACH; x++) {
            for (int y = middle[1] - COLLAR_REACH; y <= middle[1] + COLLAR_REACH; y++) {
                if (isWall.test(x, y)) return false;
            }
        }
        return true;
    }
}
