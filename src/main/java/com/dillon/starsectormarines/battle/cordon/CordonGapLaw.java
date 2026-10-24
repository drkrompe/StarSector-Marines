package com.dillon.starsectormarines.battle.cordon;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The gap law ({@code cordon-map-intent.md}, The line on the high ground): no
 * stretch of the line between two neighbouring redoubts is ground neither side's
 * guns reach. Measured gun to gun along the line, from a gun tower of one
 * redoubt through the picket nests dug between them to a gun tower of the
 * next, and the widest step on the best such chain must be at most twice a
 * gun's reach plus {@link #MARGIN}: two guns that far apart still put fire on
 * the middle of the ground between them.
 *
 * <p>Every gun on the line is the same heavy machine gun
 * ({@code weapon.turret-heavy-mg}: range 24, hit spread 2, splash radius 0.8),
 * the towers' and the picket nests' alike. A redoubt's towers alone are a
 * hundred and more cells from the next redoubt's, which no gun on the map
 * covers; the picket nests are what close the gap, so the law is asked of the
 * chain, and the straight tower-to-tower distance is a reading.
 *
 * <p>Pure: positions in, a verdict out.
 */
public final class CordonGapLaw {

    private CordonGapLaw() {}

    /** The line's gun's range, in cells: {@code weapon.turret-heavy-mg}. */
    public static final double REACH = 24.0;
    /** How far past its aim the gun's rounds fall: that weapon's hit spread. */
    static final double HIT_SPREAD = 2.0;
    /** The gun's splash radius. */
    static final double SPLASH = 0.8;
    /**
     * Cells two guns may stand beyond twice their range and still meet in the middle: each gun's fire at the edge
     * of its range lands up to its spread further on and splashes its radius beyond that, so each covers
     * {@code HIT_SPREAD + SPLASH} cells past its range.
     */
    public static final double MARGIN = 2 * (HIT_SPREAD + SPLASH);
    /** The widest step the law allows between two guns on the line. */
    public static final double LIMIT = 2 * REACH + MARGIN;

    /**
     * One neighbouring pair measured: the widest step on the best chain of guns from one redoubt's towers to the
     * next's ({@code Double.POSITIVE_INFINITY} when either has none), and the nearest tower-to-tower distance.
     */
    public record Gap(int west, int east, double widestStep, double towerToTower) {
        public boolean holds() { return widestStep <= LIMIT; }
    }

    /**
     * The gap between two neighbouring redoubts.
     *
     * @param west    the western redoubt's gun towers, {x, y} cells
     * @param east    the eastern redoubt's gun towers
     * @param between the guns dug on the line between them: the picket course's nests
     */
    public static Gap measure(int westNumber, int eastNumber, List<int[]> west, List<int[]> east,
                              List<int[]> between) {
        double direct = Double.POSITIVE_INFINITY;
        for (int[] a : west) for (int[] b : east) direct = Math.min(direct, distance(a, b));
        return new Gap(westNumber, eastNumber, widestStep(west, east, between), direct);
    }

    /**
     * The smallest, over every chain of guns from one of {@code from} to one of {@code to} through any of
     * {@code between}, of the chain's widest step: a minimax path over the guns.
     */
    static double widestStep(List<int[]> from, List<int[]> to, List<int[]> between) {
        if (from.isEmpty() || to.isEmpty()) return Double.POSITIVE_INFINITY;
        List<int[]> guns = new ArrayList<>(from);
        guns.addAll(between);
        int targets = guns.size();
        guns.addAll(to);
        int n = guns.size();
        double[] best = new double[n];
        boolean[] done = new boolean[n];
        Arrays.fill(best, Double.POSITIVE_INFINITY);
        for (int i = 0; i < from.size(); i++) best[i] = 0;
        for (int round = 0; round < n; round++) {
            int next = -1;
            for (int i = 0; i < n; i++) if (!done[i] && (next < 0 || best[i] < best[next])) next = i;
            if (next < 0 || best[next] == Double.POSITIVE_INFINITY) break;
            if (next >= targets) return best[next];
            done[next] = true;
            for (int i = 0; i < n; i++) {
                if (done[i]) continue;
                best[i] = Math.min(best[i], Math.max(best[next], distance(guns.get(next), guns.get(i))));
            }
        }
        return Double.POSITIVE_INFINITY;
    }

    /** The first gap that breaks the law, or {@code null} when every neighbouring pair holds. */
    public static Gap broken(List<Gap> gaps) {
        for (Gap gap : gaps) if (!gap.holds()) return gap;
        return null;
    }

    private static double distance(int[] a, int[] b) {
        return Math.hypot(a[0] - b[0], a[1] - b[1]);
    }
}
