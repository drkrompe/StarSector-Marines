package com.dillon.starsectormarines.battle.world.gen.fortress;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a mission wants its forts, as pure geometry: given the two ends of the battle (the landing and the keep), the
 * ground a fort should be nearest to. It owns no ground rules; {@code FortStage} finds the nearest legal site to each
 * target under the usual prior-claim, margin and separation rules. A fort that is a barrier has to stand where the
 * attack goes, which leftover ground never does, so this is the mission-owned half of placement.
 *
 * <p>The figures here are UNTUNED first passes (see {@code forts.md}): they say where a fort stands relative to the
 * battle and are meant to be changed against play, not defended.
 */
public final class FortSiting {

    /** Cells between neighbouring battery forts across the approach: wider than a fort, so each covers its own lane. */
    static final int BATTERY_LATERAL_SPACING = 96;
    /** How far along the way from the keep to the landing a battery fort stands (a fraction of that distance). */
    static final float BATTERY_APPROACH_FRACTION = 0.5f;
    /** Alternate battery forts stand this much nearer and further than the mid-point, so they do not form one rank. */
    static final float BATTERY_STAGGER = 0.1f;
    /** Targets are kept this far inside the map edge, in cells. */
    static final int EDGE_MARGIN = 45;

    private FortSiting() {}

    /** A cell. */
    public record Point(int x, int y) {}

    /** What a siting needs of the battle: its two ends and the map it is on. */
    public record Anchors(Point keep, Point landing, int width, int height) {
        public Anchors {
            if (keep == null || landing == null) throw new IllegalArgumentException("a siting needs a keep and a landing");
        }
    }

    /** A place the garrison values, to be covered by a dome: a cell and how much it matters. */
    public record Valued(int x, int y, int weight) {}

    /**
     * Targets for {@code count} battery forts: on the line from the keep out to the landing, about half way, spread
     * evenly across it and staggered in depth, so the force that crosses must pass inside the envelope of one of them.
     * A single battery stands on the line itself.
     */
    public static List<Point> batteryTargets(Anchors a, int count) {
        List<Point> out = new ArrayList<>();
        if (count <= 0) return out;
        double dx = a.landing().x() - a.keep().x();
        double dy = a.landing().y() - a.keep().y();
        double d = Math.hypot(dx, dy);
        if (d < 1) {
            dx = 1;
            dy = 0;
            d = 1;
        }
        double ux = dx / d;
        double uy = dy / d;
        double vx = -uy;
        double vy = ux;
        for (int i = 0; i < count; i++) {
            double lateral = (i - (count - 1) / 2.0) * BATTERY_LATERAL_SPACING;
            double fraction = BATTERY_APPROACH_FRACTION + (count > 1 ? (i % 2 == 0 ? -1 : 1) * BATTERY_STAGGER : 0);
            double along = d * fraction;
            int x = (int) Math.round(a.keep().x() + ux * along + vx * lateral);
            int y = (int) Math.round(a.keep().y() + uy * along + vy * lateral);
            out.add(new Point(clamp(x, a.width()), clamp(y, a.height())));
        }
        return out;
    }

    /** The ground a shield fort wants to stand on: the keep itself, so its dome is over what the garrison holds most dear. */
    public static Point shieldTarget(Anchors a) {
        return a.keep();
    }


    /** Sites outside this stretch of the way from the keep (0) to the landing (1) are not on the approach at all. */
    static final float APPROACH_NEAR_KEEP = 0.15f;
    static final float APPROACH_NEAR_LANDING = 0.9f;
    /** Moving a fort off the line of advance costs this many times as much as sliding it along the line. */
    static final int LATERAL_WEIGHT = 2;
    /** What a site off the approach costs: more than any site on the map, so it is taken only when nothing else fits. */
    public static final long OFF_APPROACH = 1_000_000L;

    /**
     * How far a fort at {@code (x, y)} is from standing where a battery fort was aimed ({@code target}), preferring to
     * slide along the line of advance over leaving it: a fort that cannot have its exact ground stays on the approach
     * rather than dropping to the flank. A site behind the landing or beyond the keep costs {@link #OFF_APPROACH} and so
     * is chosen only when no ground on the approach will take a fort.
     */
    public static long approachCost(Anchors a, Point target, int x, int y) {
        double dx = a.landing().x() - a.keep().x();
        double dy = a.landing().y() - a.keep().y();
        double d = Math.hypot(dx, dy);
        if (d < 1) return (long) (Math.hypot(x - target.x(), y - target.y()));
        double ux = dx / d;
        double uy = dy / d;
        double along = ((x - a.keep().x()) * ux + (y - a.keep().y()) * uy) / d;
        double dAlong = (x - target.x()) * ux + (y - target.y()) * uy;
        double dPerp = -(x - target.x()) * uy + (y - target.y()) * ux;
        long cost = Math.round(Math.abs(dAlong) + LATERAL_WEIGHT * Math.abs(dPerp));
        if (along < APPROACH_NEAR_KEEP || along > APPROACH_NEAR_LANDING) cost += OFF_APPROACH;
        return cost;
    }

    /** The weight of the garrison's places within {@code radius} of the cell: how much of what is held a dome would cover. */
    public static int domeCoverage(List<Valued> garrison, int x, int y, float radius) {
        long limit = (long) (radius * radius);
        int covered = 0;
        for (Valued v : garrison) {
            long dx = v.x() - x;
            long dy = v.y() - y;
            if (dx * dx + dy * dy <= limit) covered += v.weight();
        }
        return covered;
    }

    // ------------------------------------------------------------------------------------------------- slide

    /** Candidate centres are tried on this lattice, in cells, when a target's own ground is not free. */
    static final int SLIDE_STEP = 6;
    /** How far a battery fort's target may slide along the line of advance, and across it, in cells. */
    static final int SLIDE_ALONG = 96;
    static final int SLIDE_ACROSS = 60;
    /** How far a shield fort's target may move from the keep in any direction, in cells. */
    static final int SLIDE_SHIELD = 120;
    /** Reserved fort centres stay at least this far apart, as the stage's own separation demands. */
    public static final int SEPARATION = 70;

    /** Whether the square of half-extent {@code half} about a cell is ground nobody may reserve. */
    @FunctionalInterface
    public interface Blocked {
        boolean box(int x, int y, int half);
    }

    /**
     * Where a fort aimed at {@code target} can have its ground, when {@code target} itself is spoken for. A battery fort
     * slides along the line of advance before it leaves it (moving across costs {@link #LATERAL_WEIGHT} times as much and
     * never leaves the approach); a shield fort takes the nearest free ground to its target. The nearest candidate on a
     * lattice whose box is not {@code blocked}, in the map and clear of {@code others}, is taken; the target itself when
     * its own box is free or when nothing is. Deterministic, and it draws no random numbers.
     */
    public static Point slide(Anchors a, Point target, int half, boolean batteryFort, Blocked blocked, List<Point> others) {
        if (fits(a, target, half, blocked, others, batteryFort)) return target;
        double dx = a.landing().x() - a.keep().x();
        double dy = a.landing().y() - a.keep().y();
        double d = Math.hypot(dx, dy);
        double ux = d < 1 ? 1 : dx / d;
        double uy = d < 1 ? 0 : dy / d;
        int along = batteryFort ? SLIDE_ALONG : SLIDE_SHIELD;
        int across = batteryFort ? SLIDE_ACROSS : SLIDE_SHIELD;
        List<int[]> offsets = new ArrayList<>();
        for (int da = -along; da <= along; da += SLIDE_STEP) {
            for (int dl = -across; dl <= across; dl += SLIDE_STEP) offsets.add(new int[]{da, dl});
        }
        offsets.sort((p, q) -> {
            int byCost = Double.compare(slideCost(p, batteryFort), slideCost(q, batteryFort));
            if (byCost != 0) return byCost;
            if (p[0] != q[0]) return Integer.compare(p[0], q[0]);
            return Integer.compare(p[1], q[1]);
        });
        for (int[] o : offsets) {
            int x = (int) Math.round(target.x() + ux * o[0] - uy * o[1]);
            int y = (int) Math.round(target.y() + uy * o[0] + ux * o[1]);
            if (fits(a, new Point(x, y), half, blocked, others, batteryFort)) return new Point(x, y);
        }
        return target;
    }

    private static double slideCost(int[] offset, boolean batteryFort) {
        return batteryFort ? Math.abs(offset[0]) + LATERAL_WEIGHT * Math.abs(offset[1]) : Math.hypot(offset[0], offset[1]);
    }

    private static boolean fits(Anchors a, Point at, int half, Blocked blocked, List<Point> others, boolean batteryFort) {
        if (at.x() < half + 1 || at.y() < half + 1 || at.x() > a.width() - half - 2 || at.y() > a.height() - half - 2) return false;
        if (batteryFort && approachCost(a, at, at.x(), at.y()) >= OFF_APPROACH) return false;
        for (Point other : others) {
            if (Math.hypot(at.x() - other.x(), at.y() - other.y()) < SEPARATION) return false;
        }
        return !blocked.box(at.x(), at.y(), half);
    }

    // -------------------------------------------------------------------------------------------------- dome

    /** UNTUNED: the widest dome a shield fort declares, in cells; a keep out of this reach is simply not covered. */
    public static final float MAX_DOME_RADIUS = 160f;

    /**
     * How far a shield fort's dome reaches, in cells: over its own works at least ({@code worksRadius}) and, where the
     * fort was sited to protect the keep, over the whole of the keep's footprint ({@code keepBox} as left, top, right,
     * bottom), measured from the generator to the keep's farthest corner. Never more than {@link #MAX_DOME_RADIUS}.
     * {@code keepBox} may be {@code null} when no keep is being protected.
     */
    public static float domeRadius(Point generator, float worksRadius, int[] keepBox) {
        float reach = worksRadius;
        if (keepBox != null) {
            double farthest = 0;
            for (int x : new int[]{keepBox[0], keepBox[2] + 1}) {
                for (int y : new int[]{keepBox[1], keepBox[3] + 1}) {
                    farthest = Math.max(farthest, Math.hypot(x - generator.x(), y - generator.y()));
                }
            }
            reach = Math.max(reach, (float) Math.ceil(farthest + 1));
        }
        return Math.min(MAX_DOME_RADIUS, reach);
    }

    private static int clamp(int v, int extent) {
        int margin = Math.min(EDGE_MARGIN, extent / 2);
        return Math.max(margin, Math.min(extent - 1 - margin, v));
    }
}
