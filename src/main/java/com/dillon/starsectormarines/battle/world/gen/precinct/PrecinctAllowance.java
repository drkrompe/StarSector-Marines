package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * How much ground each precinct may claim, derived rather than chosen.
 *
 * <p>A flat number does not work, and the measurement that says so is in
 * {@code precincts.md}: roads alone are around twenty thousand cells on a
 * 560x336 map, so allowances that look generous are spent almost entirely on
 * the arms and leave precincts that are their own street plan with no ground in
 * them. An allowance has to be about what the place is, which means there are
 * two derivations rather than one number.
 *
 * <p><b>A programmed precinct is as big as what it holds.</b> Its allowance is
 * the ground its program needs — the same {@code envelopeArea} that already
 * sizes a fortress ward from its buildings and lots — plus the road that grew
 * through it. This is {@code compound-programs.md}'s law unchanged: the program
 * sizes the place, and a place given arbitrary ground packs badly in both
 * directions, pinning buildings to a boundary when it is too large and leaving
 * them unplaced when it is too small.
 *
 * <p><b>A zoned precinct is as big as the ground along its streets.</b> There is
 * no program to size it, so the honest measure is frontage: the cells within
 * {@code frontageDepth} of its own arms are the ones somebody would build along,
 * and the rest is open country. That is the same rule the shipped hinterland
 * already uses to decide what is settled, applied per precinct instead of once
 * for the whole map.
 *
 * <p>Both are counts in the end, because {@link PrecinctClaim#budgeted()} caps
 * by count. Frontage is measured as a distance and then counted, rather than
 * used as a mask, so that a town competing with a neighbour for the same ground
 * yields it by nearness like everything else instead of carving a fixed collar
 * out of its neighbour.
 */
public final class PrecinctAllowance {

    private PrecinctAllowance() {}

    /**
     * Ground allowance per precinct, in cells, including the road it grew.
     *
     * @param precincts the places, in the order their seeds were grown
     * @param owner     per-cell seed index for road cells, from {@link GrownTrunkPlan.Grown}
     */
    public static int[] derive(List<Precinct> precincts, int[][] owner, int width, int height) {
        int[] roads = new int[precincts.size()];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int who = owner[x][y];
                if (who >= 0 && who < roads.length) roads[who]++;
            }
        }
        int[] out = new int[precincts.size()];
        for (int i = 0; i < precincts.size(); i++) {
            Precinct precinct = precincts.get(i);
            out[i] = precinct.isProgrammed()
                    ? roads[i] + precinct.program().envelopeArea()
                    : roads[i] + frontage(owner, width, height, i,
                            precinct.growth().frontageDepth);
        }
        return out;
    }

    /**
     * Cells within {@code depth} of this precinct's own arms, not counting the
     * arms themselves.
     *
     * <p>Measured from the arms rather than from the precinct's claim, because
     * the claim is what this is being derived to bound — and measured per
     * precinct rather than from every road on the map, because a town beside a
     * fortress should not be sized by the fortress's streets.
     */
    private static int frontage(int[][] owner, int width, int height, int who, int depth) {
        int[][] dist = new int[width][height];
        for (int[] column : dist) java.util.Arrays.fill(column, Integer.MAX_VALUE);
        Deque<int[]> queue = new ArrayDeque<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (owner[x][y] != who) continue;
                dist[x][y] = 0;
                queue.add(new int[]{x, y});
            }
        }
        int within = 0;
        while (!queue.isEmpty()) {
            int[] at = queue.poll();
            int d = dist[at[0]][at[1]];
            if (d >= depth) continue;
            for (int[] step : STEPS) {
                int nx = at[0] + step[0];
                int ny = at[1] + step[1];
                if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                if (dist[nx][ny] != Integer.MAX_VALUE) continue;
                dist[nx][ny] = d + 1;
                within++;
                queue.add(new int[]{nx, ny});
            }
        }
        return within;
    }

    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
}
