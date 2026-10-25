package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * The {@link District.Shape#SPINE} claim ({@code set-piece-districts.md}): one straight stretch of street with
 * member leaves fronting it on both sides, the street itself and the cross streets between neighbouring
 * members. A spine starts and ends on a member's edge, every leaf fronting the stretch is a member, and the
 * carriageway is road from end to end. Found read-only; nothing here changes the partition.
 *
 * <p>The search works in a street frame: {@code along} runs the length of the street and {@code across}
 * crosses it, so one pass serves streets running along x and along y. The {@code near} side is the one with
 * the smaller {@code across}.
 */
public final class SpineClaim {

    /**
     * What a program asks of a spine: members per side, the shortest street, the widest carriageway it will
     * take, and the least depth (away from the street) and frontage (along it) a member must have.
     */
    public record Spec(int minPerSide, int maxPerSide, int minLength, int maxStreetWidth,
                       int minDepth, int minFront) {
    }

    /** A cross street between two neighbouring members on one side, all road: {@code along} and {@code across} ranges, inclusive. */
    public record Gap(boolean near, int from, int to, int lo, int hi) {
        public int width() { return to - from + 1; }
    }

    /** A claimed spine. */
    public static final class Spine implements DistrictClaim {
        /** Whether the street runs along x. */
        public final boolean horizontal;
        /** The stretch of street claimed, along it, inclusive. */
        public final int from, to;
        /** The carriageway across the street, inclusive. */
        public final int lo, hi;
        /** The members fronting each side, in order along the street. */
        public final List<BlockLeaf> near, far;
        /** The cross streets between neighbouring members, in order along the street, near side first. */
        public final List<Gap> gaps;
        private final List<BlockLeaf> members;

        Spine(boolean horizontal, int from, int to, int lo, int hi,
              List<BlockLeaf> near, List<BlockLeaf> far, List<Gap> gaps) {
            this.horizontal = horizontal;
            this.from = from;
            this.to = to;
            this.lo = lo;
            this.hi = hi;
            this.near = List.copyOf(near);
            this.far = List.copyOf(far);
            this.gaps = List.copyOf(gaps);
            List<BlockLeaf> all = new ArrayList<>(near);
            all.addAll(far);
            all.sort(Comparator.comparingInt(this::alongLo).thenComparingInt(this::acrossLo));
            this.members = List.copyOf(all);
        }

        public int length() { return to - from + 1; }
        public int streetWidth() { return hi - lo + 1; }

        public int x(int along, int across) { return horizontal ? along : across; }
        public int y(int along, int across) { return horizontal ? across : along; }
        public int along(int x, int y) { return horizontal ? x : y; }
        public int across(int x, int y) { return horizontal ? y : x; }
        public int alongLo(BlockLeaf leaf) { return horizontal ? leaf.left : leaf.top; }
        public int alongHi(BlockLeaf leaf) { return horizontal ? leaf.right : leaf.bottom; }
        public int acrossLo(BlockLeaf leaf) { return horizontal ? leaf.top : leaf.left; }
        public int acrossHi(BlockLeaf leaf) { return horizontal ? leaf.bottom : leaf.right; }
        public boolean onNearSide(BlockLeaf leaf) { return near.contains(leaf); }

        @Override public List<BlockLeaf> members() { return members; }

        @Override
        public boolean owns(int x, int y) {
            int a = along(x, y), c = across(x, y);
            if (a >= from && a <= to && c >= lo && c <= hi) return true;
            for (BlockLeaf leaf : members) if (leaf.contains(x, y)) return true;
            for (Gap gap : gaps) if (a >= gap.from && a <= gap.to && c >= gap.lo && c <= gap.hi) return true;
            return false;
        }

        @Override
        public int[] bounds() {
            int minC = lo, maxC = hi;
            for (BlockLeaf leaf : members) {
                minC = Math.min(minC, acrossLo(leaf));
                maxC = Math.max(maxC, acrossHi(leaf));
            }
            return horizontal ? new int[]{from, minC, to, maxC} : new int[]{minC, from, maxC, to};
        }

        @Override
        public String toString() {
            return (horizontal ? "H" : "V") + " street " + from + ".." + to + " x " + lo + ".." + hi
                    + ", " + near.size() + "+" + far.size() + " members, " + gaps.size() + " cross streets";
        }
    }

    private SpineClaim() { }

    /**
     * The best spine over {@code leaves}, or null. {@code road} is the street mask the partition laid; a member
     * must pass {@code eligible} and the spec's size limits. {@code pocket} weighs how well a member sits in
     * the program's zoning pocket (0 outside it); a spine stands in its pocket when its members' weights add
     * up to at least their count. The best stands deepest in its pocket, then has the most members, then the
     * longest street, then was found first (streets along x before y, then by position).
     */
    public static Spine find(List<BlockLeaf> leaves, boolean[][] road, Predicate<BlockLeaf> eligible,
                             ToIntFunction<BlockLeaf> pocket, Spec spec) {
        Spine best = null;
        for (boolean horizontal : new boolean[]{true, false}) {
            Frame f = new Frame(horizontal, road);
            for (int[] band : bands(leaves, f, spec.maxStreetWidth)) {
                Spine spine = bestOnBand(leaves, f, band[0], band[1], eligible, pocket, spec);
                if (spine != null && better(spine, best, pocket)) best = spine;
            }
        }
        return best;
    }

    private static boolean better(Spine a, Spine b, ToIntFunction<BlockLeaf> pocket) {
        if (b == null) return true;
        int pa = weight(a, pocket), pb = weight(b, pocket);
        if (pa != pb) return pa > pb;
        if (a.members().size() != b.members().size()) return a.members().size() > b.members().size();
        return a.length() > b.length();
    }

    private static int weight(Spine spine, ToIntFunction<BlockLeaf> pocket) {
        int sum = 0;
        for (BlockLeaf leaf : spine.members()) sum += pocket.applyAsInt(leaf);
        return sum;
    }

    /** Every carriageway {@code {lo, hi}} that runs between a leaf on one side and a leaf on the other. */
    private static TreeSet<int[]> bands(List<BlockLeaf> leaves, Frame f, int maxWidth) {
        TreeSet<int[]> bands = new TreeSet<>(Comparator.<int[]>comparingInt(b -> b[0]).thenComparingInt(b -> b[1]));
        for (BlockLeaf a : leaves) {
            int lo = f.cHi(a) + 1;
            for (BlockLeaf b : leaves) {
                int width = f.cLo(b) - lo;
                if (width < Bsp.ROAD_WIDTH_MIN || width > maxWidth) continue;
                int from = Math.max(f.aLo(a), f.aLo(b)), to = Math.min(f.aHi(a), f.aHi(b));
                if (from > to) continue;
                boolean open = true;
                for (int s = from; s <= to && open; s++) open = f.street(s, lo, lo + width - 1);
                if (open) bands.add(new int[]{lo, lo + width - 1});
            }
        }
        return bands;
    }

    /** The best window of members along the carriageway {@code [lo, hi]}, or null. */
    private static Spine bestOnBand(List<BlockLeaf> leaves, Frame f, int lo, int hi,
                                    Predicate<BlockLeaf> eligible, ToIntFunction<BlockLeaf> pocket, Spec spec) {
        List<BlockLeaf> fronting = new ArrayList<>();
        for (BlockLeaf leaf : leaves) {
            if (f.cHi(leaf) == lo - 1 || f.cLo(leaf) == hi + 1) fronting.add(leaf);
        }
        fronting.sort(Comparator.comparingInt(f::aLo).thenComparingInt(f::cLo));
        Spine best = null;
        for (BlockLeaf first : fronting) {
            int from = f.aLo(first);
            for (BlockLeaf last : fronting) {
                int to = f.aHi(last);
                if (to - from + 1 < spec.minLength) continue;
                Spine spine = window(fronting, f, lo, hi, from, to, eligible, spec);
                if (spine == null || weight(spine, pocket) < spine.members().size()) continue;
                if (better(spine, best, pocket)) best = spine;
            }
        }
        return best;
    }

    private static Spine window(List<BlockLeaf> fronting, Frame f, int lo, int hi, int from, int to,
                                Predicate<BlockLeaf> eligible, Spec spec) {
        List<BlockLeaf> near = new ArrayList<>(), far = new ArrayList<>();
        for (BlockLeaf leaf : fronting) {
            if (f.aHi(leaf) < from || f.aLo(leaf) > to) continue;
            // A leaf fronting the stretch belongs to it whole, or the stretch is not a district's.
            if (f.aLo(leaf) < from || f.aHi(leaf) > to) return null;
            if (!eligible.test(leaf)) return null;
            if (f.cHi(leaf) - f.cLo(leaf) + 1 < spec.minDepth || f.aHi(leaf) - f.aLo(leaf) + 1 < spec.minFront) {
                return null;
            }
            (f.cHi(leaf) == lo - 1 ? near : far).add(leaf);
        }
        if (near.size() < spec.minPerSide || far.size() < spec.minPerSide) return null;
        if (near.size() > spec.maxPerSide || far.size() > spec.maxPerSide) return null;
        for (int s = from; s <= to; s++) if (!f.street(s, lo, hi)) return null;
        List<Gap> gaps = new ArrayList<>();
        if (!gaps(near, f, true, lo, gaps) || !gaps(far, f, false, hi, gaps)) return null;
        return new Spine(f.horizontal, from, to, lo, hi, near, far, gaps);
    }

    /**
     * The cross streets between neighbouring members on one side, reaching from the carriageway as deep as
     * both members go while the whole width stays road. False where two members stand closer than a street.
     */
    private static boolean gaps(List<BlockLeaf> side, Frame f, boolean near, int edge, List<Gap> out) {
        for (int i = 0; i + 1 < side.size(); i++) {
            BlockLeaf a = side.get(i), b = side.get(i + 1);
            int from = f.aHi(a) + 1, to = f.aLo(b) - 1;
            if (to - from + 1 < Bsp.ROAD_WIDTH_MIN) return false;
            int deepest = near ? Math.max(f.cLo(a), f.cLo(b)) : Math.min(f.cHi(a), f.cHi(b));
            int step = near ? -1 : 1, start = near ? edge - 1 : edge + 1, reach = start - step;
            for (int c = start; near ? c >= deepest : c <= deepest; c += step) {
                if (!f.street(from, to, c, c)) break;
                reach = c;
            }
            if (reach == start - step) continue;
            out.add(near ? new Gap(true, from, to, reach, start) : new Gap(false, from, to, start, reach));
        }
        return true;
    }

    /** The partition seen in a street's frame. */
    private record Frame(boolean horizontal, boolean[][] road) {
        int aLo(BlockLeaf l) { return horizontal ? l.left : l.top; }
        int aHi(BlockLeaf l) { return horizontal ? l.right : l.bottom; }
        int cLo(BlockLeaf l) { return horizontal ? l.top : l.left; }
        int cHi(BlockLeaf l) { return horizontal ? l.bottom : l.right; }

        boolean road(int a, int c) {
            int x = horizontal ? a : c, y = horizontal ? c : a;
            return x >= 0 && y >= 0 && x < road.length && y < road[0].length && road[x][y];
        }

        /** Whether the cells at {@code along = a} from {@code lo} to {@code hi} across are all road. */
        boolean street(int a, int lo, int hi) {
            for (int c = lo; c <= hi; c++) if (!road(a, c)) return false;
            return true;
        }

        /** Whether the block {@code [a0, a1] x [c0, c1]} is all road. */
        boolean street(int a0, int a1, int c0, int c1) {
            for (int a = a0; a <= a1; a++) if (!street(a, c0, c1)) return false;
            return true;
        }
    }
}
