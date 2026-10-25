package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * The {@link District.Shape#QUARTER} claim ({@code set-piece-districts.md}): a block of leaves, two rows of two
 * or three, and the cross streets between them. The cordoned facility stands on one: a wall round the
 * outside, gatehouses where the outer streets meet it, and the cross streets inside as its internal streets.
 *
 * <p>A quarter is a rectangle the members and their streets tile exactly. Its rows lie either side of one
 * <b>main cross street</b> running the quarter's whole length; each row's members share its full depth and
 * stand along it with a cross street between neighbours that runs the row's depth. The rows' members need not
 * line up, so a row of two may face a row of three. Found read-only, in a frame like {@link SpineClaim}'s:
 * {@code along} runs the main cross street's length and {@code across} crosses it, so one pass serves quarters
 * whose main street runs along x and along y.
 */
public final class QuarterClaim {

    /**
     * What a program asks of a quarter: the most rows and the most members in a row (at least two of each), the
     * widest inner street, and the least side of a member.
     */
    public record Spec(int rows, int columns, int maxStreetWidth, int minSide) {
    }

    /** An inner street, inclusive bounds. */
    public record Street(int left, int top, int right, int bottom) {
        public boolean contains(int x, int y) { return x >= left && x <= right && y >= top && y <= bottom; }
        public int width() { return right - left + 1; }
        public int height() { return bottom - top + 1; }
    }

    /** A claimed quarter. */
    public static final class Quarter implements DistrictClaim {
        /** Whether the main cross street runs along x, so the rows are stacked in y. */
        public final boolean horizontal;
        /** The members row by row across the main cross street, each row in order along it. */
        public final List<List<BlockLeaf>> rows;
        /**
         * The cross streets between the members, each whole: first the main cross streets between the rows, in
         * order across, then each row's cross streets between neighbours, row by row.
         */
        public final List<Street> streets;
        private final List<BlockLeaf> members;

        public Quarter(boolean horizontal, List<List<BlockLeaf>> rows, List<Street> streets) {
            this.horizontal = horizontal;
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

        /** The row a member stands in, or -1. */
        public int rowOf(BlockLeaf leaf) {
            for (int r = 0; r < rows.size(); r++) if (rows.get(r).contains(leaf)) return r;
            return -1;
        }

        public int area() {
            int[] b = bounds();
            return (b[2] - b[0] + 1) * (b[3] - b[1] + 1);
        }

        @Override
        public String toString() {
            int[] b = bounds();
            StringBuilder s = new StringBuilder(horizontal ? "H" : "V").append(" quarter ")
                    .append(b[0]).append(',').append(b[1]).append("..").append(b[2]).append(',').append(b[3]);
            for (List<BlockLeaf> row : rows) s.append(' ').append(row.size());
            return s.append(" members, ").append(streets.size()).append(" streets").toString();
        }
    }

    private QuarterClaim() { }

    /**
     * The best quarter over {@code leaves}, or null. {@code road} is the street mask the partition laid;
     * {@code fixed} marks the cells no district may own as street (the trunks), or is null. A member must pass
     * {@code eligible} and the spec's least side; every inner street is road and no wider than the spec allows.
     * {@code pocket} weighs members as {@link SpineClaim#find} does: a quarter stands in its pocket when its
     * members' weights add up to at least their count. The best stands deepest in its pocket, then has the most
     * members, then the largest ground, then was found first (main streets along x before y, then by leaf order).
     */
    public static Quarter find(List<BlockLeaf> leaves, boolean[][] road, boolean[][] fixed,
                               Predicate<BlockLeaf> eligible, ToIntFunction<BlockLeaf> pocket, Spec spec) {
        Quarter best = null;
        for (boolean horizontal : new boolean[]{true, false}) {
            Frame f = new Frame(horizontal, road, fixed);
            for (BlockLeaf start : leaves) {
                if (!member(start, eligible, spec)) continue;
                for (List<BlockLeaf> first : chains(start, leaves, f, eligible, spec)) {
                    List<List<BlockLeaf>> rows = new ArrayList<>();
                    rows.add(first);
                    best = stack(rows, leaves, f, eligible, pocket, spec, best);
                }
            }
        }
        return best;
    }

    /** Every quarter that stacks further rows under {@code rows}, kept against {@code best}. */
    private static Quarter stack(List<List<BlockLeaf>> rows, List<BlockLeaf> leaves, Frame f,
                                 Predicate<BlockLeaf> eligible, ToIntFunction<BlockLeaf> pocket, Spec spec,
                                 Quarter best) {
        if (rows.size() >= spec.rows) return best;
        List<BlockLeaf> last = rows.get(rows.size() - 1);
        BlockLeaf head = last.get(0), tail = last.get(last.size() - 1);
        for (BlockLeaf next : leaves) {
            int gap = f.cLo(next) - f.cHi(head) - 1;
            if (f.aLo(next) != f.aLo(head) || gap < Bsp.ROAD_WIDTH_MIN || gap > spec.maxStreetWidth) continue;
            if (!member(next, eligible, spec) || !f.street(f.aLo(head), f.aHi(tail), f.cHi(head) + 1, f.cLo(next) - 1)) {
                continue;
            }
            for (List<BlockLeaf> row : chains(next, leaves, f, eligible, spec)) {
                if (f.aHi(row.get(row.size() - 1)) != f.aHi(tail)) continue;
                List<List<BlockLeaf>> more = new ArrayList<>(rows);
                more.add(row);
                Quarter quarter = quarter(more, f);
                if (weight(quarter, pocket) >= quarter.members().size() && better(quarter, best, pocket)) best = quarter;
                best = stack(more, leaves, f, eligible, pocket, spec, best);
            }
        }
        return best;
    }

    /**
     * The rows of two up to {@code spec.columns} members that start at {@code start}: neighbours sharing its
     * full depth across, each a street's width along from the one before with that street all road.
     */
    private static List<List<BlockLeaf>> chains(BlockLeaf start, List<BlockLeaf> leaves, Frame f,
                                                Predicate<BlockLeaf> eligible, Spec spec) {
        List<List<BlockLeaf>> out = new ArrayList<>();
        List<BlockLeaf> chain = new ArrayList<>();
        chain.add(start);
        BlockLeaf current = start;
        while (chain.size() < spec.columns) {
            BlockLeaf next = null;
            for (BlockLeaf leaf : leaves) {
                int gap = f.aLo(leaf) - f.aHi(current) - 1;
                if (f.cLo(leaf) != f.cLo(start) || f.cHi(leaf) != f.cHi(start)) continue;
                if (gap < Bsp.ROAD_WIDTH_MIN || gap > spec.maxStreetWidth) continue;
                next = leaf;
                break;
            }
            if (next == null || !member(next, eligible, spec)
                    || !f.street(f.aHi(current) + 1, f.aLo(next) - 1, f.cLo(start), f.cHi(start))) {
                break;
            }
            chain.add(next);
            current = next;
            out.add(List.copyOf(chain));
        }
        return out;
    }

    private static Quarter quarter(List<List<BlockLeaf>> rows, Frame f) {
        List<Street> streets = new ArrayList<>();
        List<BlockLeaf> first = rows.get(0);
        int aLo = f.aLo(first.get(0)), aHi = f.aHi(first.get(first.size() - 1));
        for (int r = 0; r + 1 < rows.size(); r++) {
            streets.add(f.rect(aLo, aHi, f.cHi(rows.get(r).get(0)) + 1, f.cLo(rows.get(r + 1).get(0)) - 1));
        }
        for (List<BlockLeaf> row : rows) {
            for (int i = 0; i + 1 < row.size(); i++) {
                BlockLeaf a = row.get(i), b = row.get(i + 1);
                streets.add(f.rect(f.aHi(a) + 1, f.aLo(b) - 1, f.cLo(a), f.cHi(a)));
            }
        }
        return new Quarter(f.horizontal, rows, streets);
    }

    private static boolean member(BlockLeaf leaf, Predicate<BlockLeaf> eligible, Spec spec) {
        return leaf.width() >= spec.minSide && leaf.height() >= spec.minSide && eligible.test(leaf);
    }

    private static boolean better(Quarter a, Quarter b, ToIntFunction<BlockLeaf> pocket) {
        if (b == null) return true;
        int pa = weight(a, pocket), pb = weight(b, pocket);
        if (pa != pb) return pa > pb;
        if (a.members().size() != b.members().size()) return a.members().size() > b.members().size();
        return a.area() > b.area();
    }

    private static int weight(Quarter quarter, ToIntFunction<BlockLeaf> pocket) {
        int sum = 0;
        for (BlockLeaf leaf : quarter.members()) sum += pocket.applyAsInt(leaf);
        return sum;
    }

    /** The partition seen in a main cross street's frame. */
    private record Frame(boolean horizontal, boolean[][] road, boolean[][] fixed) {
        int aLo(BlockLeaf l) { return horizontal ? l.left : l.top; }
        int aHi(BlockLeaf l) { return horizontal ? l.right : l.bottom; }
        int cLo(BlockLeaf l) { return horizontal ? l.top : l.left; }
        int cHi(BlockLeaf l) { return horizontal ? l.bottom : l.right; }

        Street rect(int a0, int a1, int c0, int c1) {
            return horizontal ? new Street(a0, c0, a1, c1) : new Street(c0, a0, c1, a1);
        }

        /** Whether the block {@code [a0, a1] x [c0, c1]} is all road a district may own. */
        boolean street(int a0, int a1, int c0, int c1) {
            for (int a = a0; a <= a1; a++) {
                for (int c = c0; c <= c1; c++) {
                    int x = horizontal ? a : c, y = horizontal ? c : a;
                    if (x < 0 || y < 0 || x >= road.length || y >= road[0].length || !road[x][y]) return false;
                    if (fixed != null && fixed[x][y]) return false;
                }
            }
            return true;
        }
    }
}
