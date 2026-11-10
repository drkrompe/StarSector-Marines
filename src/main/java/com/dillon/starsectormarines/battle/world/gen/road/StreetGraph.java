package com.dillon.starsectormarines.battle.world.gen.road;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A road network read as a planner reads it: straight streets, each one width along its length, meeting at typed
 * junctions ({@code mapgen-nouns.md}, Authored roads). It is a reading, not a plan: it changes no ground, and serves
 * as the measure a street rule is judged by (how many dead ends, offset tees and width steps a network has) and as
 * the input a network-level street plan would start from.
 *
 * <p>A <em>street</em> is a maximal straight band of road at least three cells broad and at least
 * {@link #STREET_LENGTH} breadths long, held by no other band. A <em>junction</em> is where a horizontal street and a
 * vertical one share cells; its kind is how many arms leave it (a street running on past the other's band on that
 * side, or ending beyond it): two arms a bend, three a tee, four a cross. A street's end that meets no crossing
 * street, no street running on from it and not the map's edge is a <em>dead end</em>.
 */
public final class StreetGraph {

    /** Breadths a band must run to be a street rather than a patch of paving. */
    public static final int STREET_LENGTH = 2;

    /** A straight street: its inclusive cell bounds. */
    public record Street(int left, int bottom, int right, int top) {
        public boolean horizontal() { return right - left >= top - bottom; }
        public int breadth() { return horizontal() ? top - bottom + 1 : right - left + 1; }
        public int length() { return horizontal() ? right - left + 1 : top - bottom + 1; }
        boolean overlaps(Street o) { return left <= o.right && right >= o.left && bottom <= o.top && top >= o.bottom; }
        boolean contains(Street o) { return left <= o.left && right >= o.right && bottom <= o.bottom && top >= o.top; }
    }

    /** How many arms leave a junction. */
    public enum Kind { BEND, TEE, CROSS }

    /** Where a horizontal and a vertical street meet: the middle of their shared cells, and its kind. */
    public record Junction(int x, int y, Kind kind, Street across, Street along) {}

    private final List<Street> streets;
    private final List<Junction> junctions;
    private final int deadEnds;

    private StreetGraph(List<Street> streets, List<Junction> junctions, int deadEnds) {
        this.streets = streets;
        this.junctions = junctions;
        this.deadEnds = deadEnds;
    }

    public List<Street> streets() { return streets; }

    public List<Junction> junctions() { return junctions; }

    /** Street ends that meet nothing: no crossing street, no street running on, not the map's edge. */
    public int deadEnds() { return deadEnds; }

    /** Reads {@code road} as streets and junctions. */
    public static StreetGraph of(boolean[][] road) {
        int w = road.length, h = road[0].length;
        Set<Street> found = new LinkedHashSet<>();
        bands(road, false, found);
        bands(road, true, found);
        List<Street> streets = new ArrayList<>();
        for (Street s : found) {
            if (s.length() < STREET_LENGTH * s.breadth()) continue;
            boolean held = false;
            for (Street o : found) held |= o != s && !o.equals(s) && o.contains(s);
            if (!held) streets.add(s);
        }
        List<Junction> junctions = new ArrayList<>();
        for (Street a : streets) for (Street b : streets) {
            if (!a.horizontal() || b.horizontal() || !a.overlaps(b)) continue;
            int x0 = Math.max(a.left, b.left), x1 = Math.min(a.right, b.right);
            int y0 = Math.max(a.bottom, b.bottom), y1 = Math.min(a.top, b.top);
            int arms = (a.left < b.left ? 1 : 0) + (a.right > b.right ? 1 : 0)
                    + (b.bottom < a.bottom ? 1 : 0) + (b.top > a.top ? 1 : 0);
            if (arms < 2) continue;
            Kind kind = arms == 2 ? Kind.BEND : arms == 3 ? Kind.TEE : Kind.CROSS;
            junctions.add(new Junction((x0 + x1) / 2, (y0 + y1) / 2, kind, a, b));
        }
        int dead = 0;
        for (Street s : streets) for (boolean first : new boolean[]{true, false}) if (!met(s, first, streets, w, h)) dead++;
        return new StreetGraph(List.copyOf(streets), List.copyOf(junctions), dead);
    }

    /**
     * Pairs of tees on one street, a cell or more and at most {@code within} cells apart along it, whose side streets
     * leave on opposite sides: a crossing pulled apart into two, which a planner lays as one cross.
     */
    public int offsetTees(int within) {
        int pairs = 0;
        for (int i = 0; i < junctions.size(); i++) for (int j = i + 1; j < junctions.size(); j++) {
            Junction a = junctions.get(i), b = junctions.get(j);
            if (a.kind() != Kind.TEE || b.kind() != Kind.TEE) continue;
            Street common = a.across().equals(b.across()) ? a.across() : a.along().equals(b.along()) ? a.along() : null;
            if (common == null) continue;
            Street sideA = common == a.across() ? a.along() : a.across();
            Street sideB = common == b.across() ? b.along() : b.across();
            int apart = common.horizontal() ? Math.abs(a.x() - b.x()) : Math.abs(a.y() - b.y());
            if (apart < 1 || apart > within) continue;
            if (side(common, sideA) != 0 && side(common, sideA) == -side(common, sideB)) pairs++;
        }
        return pairs;
    }

    /**
     * Places where a street runs on into another running the same way, overlapping its line, at a different
     * breadth: a street changing width along its length.
     */
    public int widthSteps() {
        int steps = 0;
        for (int i = 0; i < streets.size(); i++) for (int j = i + 1; j < streets.size(); j++) {
            Street a = streets.get(i), b = streets.get(j);
            if (a.horizontal() != b.horizontal() || a.breadth() == b.breadth()) continue;
            boolean touching = a.left <= b.right + 1 && a.right >= b.left - 1 && a.bottom <= b.top + 1 && a.top >= b.bottom - 1;
            boolean inLine = a.horizontal() ? a.bottom <= b.top && a.top >= b.bottom : a.left <= b.right && a.right >= b.left;
            if (touching && inLine) steps++;
        }
        return steps;
    }

    /** Which side of {@code street} the crossing street {@code side} leaves: -1 below or left, 1 above or right. */
    private static int side(Street street, Street side) {
        boolean lo = street.horizontal() ? side.bottom < street.bottom : side.left < street.left;
        boolean hi = street.horizontal() ? side.top > street.top : side.right > street.right;
        return lo == hi ? 0 : lo ? -1 : 1;
    }

    private static boolean met(Street s, boolean first, List<Street> streets, int w, int h) {
        boolean horizontal = s.horizontal();
        int end = horizontal ? (first ? s.left : s.right) : (first ? s.bottom : s.top);
        if (end == 0 || horizontal && end == w - 1 || !horizontal && end == h - 1) return true;
        Street cap = horizontal ? new Street(end, s.bottom, end, s.top) : new Street(s.left, end, s.right, end);
        int step = first ? -1 : 1;
        Street past = horizontal ? new Street(end + step, s.bottom, end + step, s.top)
                : new Street(s.left, end + step, s.right, end + step);
        for (Street o : streets) {
            if (o == s) continue;
            if (o.horizontal() != horizontal && (o.overlaps(cap) || o.overlaps(past))) return true;
            if (o.horizontal() == horizontal && o.overlaps(past)) return true;
        }
        return false;
    }

    /** Maximal bands of {@code road} three or more cells broad, one per run widened as far as it stays whole. */
    private static void bands(boolean[][] road, boolean transpose, Set<Street> out) {
        int length = transpose ? road[0].length : road.length;
        int rows = transpose ? road.length : road[0].length;
        for (int row = 0; row < rows; row++) for (int start = 0; start < length;) {
            if (!at(road, transpose, start, row)) { start++; continue; }
            int end = start;
            while (end + 1 < length && at(road, transpose, end + 1, row)) end++;
            int lo = row, hi = row;
            while (lo > 0 && full(road, transpose, start, end, lo - 1)) lo--;
            while (hi + 1 < rows && full(road, transpose, start, end, hi + 1)) hi++;
            if (hi - lo + 1 >= 3 && end - start + 1 >= hi - lo + 1)
                out.add(transpose ? new Street(lo, start, hi, end) : new Street(start, lo, end, hi));
            start = end + 1;
        }
    }

    private static boolean full(boolean[][] road, boolean transpose, int start, int end, int row) {
        for (int x = start; x <= end; x++) if (!at(road, transpose, x, row)) return false;
        return true;
    }

    private static boolean at(boolean[][] road, boolean transpose, int x, int y) {
        return transpose ? road[y][x] : road[x][y];
    }
}
