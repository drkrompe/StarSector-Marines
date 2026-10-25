package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * The {@link District.Shape#QUARTER} claim ({@code set-piece-districts.md}): a block of leaves, two rows by two
 * or three, and the cross streets between them. The cordoned facility stands on one: a wall round the
 * outside, gatehouses where the outer streets meet it, and the cross streets inside as its internal streets.
 *
 * <p>The shape and its interface are settled; the search is not built yet. {@link #find} answers null, so a
 * program asking for a quarter is never planned and its leaves fill as they do today. The program that
 * builds it fills in {@link #find} against this contract: every member passes {@code eligible}, the members
 * tile the block's rows and columns with only road between them, and {@link #streets} are those roads,
 * whole, so {@link DistrictStreets} can prove a rewrite of them.
 */
public final class QuarterClaim {

    /** What a program asks of a quarter: the rows and columns of leaves, the widest inner street, the least member side. */
    public record Spec(int rows, int columns, int maxStreetWidth, int minSide) {
    }

    /** An inner street, inclusive bounds. */
    public record Street(int left, int top, int right, int bottom) {
        public boolean contains(int x, int y) { return x >= left && x <= right && y >= top && y <= bottom; }
    }

    /** A claimed quarter. */
    public static final class Quarter implements DistrictClaim {
        /** The members row by row, each row in order along x. */
        public final List<List<BlockLeaf>> rows;
        /** The cross streets between the members, each whole. */
        public final List<Street> streets;
        private final List<BlockLeaf> members;

        public Quarter(List<List<BlockLeaf>> rows, List<Street> streets) {
            List<List<BlockLeaf>> copy = new ArrayList<>();
            List<BlockLeaf> all = new ArrayList<>();
            for (List<BlockLeaf> row : rows) {
                copy.add(List.copyOf(row));
                all.addAll(row);
            }
            this.rows = List.copyOf(copy);
            this.streets = List.copyOf(streets);
            this.members = List.copyOf(all);
        }

        @Override public List<BlockLeaf> members() { return members; }

        @Override
        public boolean owns(int x, int y) {
            for (BlockLeaf leaf : members) if (leaf.contains(x, y)) return true;
            for (Street street : streets) if (street.contains(x, y)) return true;
            return false;
        }

        @Override
        public int[] bounds() {
            int l = Integer.MAX_VALUE, t = Integer.MAX_VALUE, r = Integer.MIN_VALUE, b = Integer.MIN_VALUE;
            for (BlockLeaf leaf : members) {
                l = Math.min(l, leaf.left);
                t = Math.min(t, leaf.top);
                r = Math.max(r, leaf.right);
                b = Math.max(b, leaf.bottom);
            }
            return new int[]{l, t, r, b};
        }
    }

    private QuarterClaim() { }

    /**
     * The best quarter over {@code leaves}, or null, weighing members by {@code pocket} as
     * {@link SpineClaim#find} does. Not built yet: always null (see the class comment).
     */
    public static Quarter find(List<BlockLeaf> leaves, boolean[][] road, Predicate<BlockLeaf> eligible,
                               ToIntFunction<BlockLeaf> pocket, Spec spec) {
        return null;
    }
}
