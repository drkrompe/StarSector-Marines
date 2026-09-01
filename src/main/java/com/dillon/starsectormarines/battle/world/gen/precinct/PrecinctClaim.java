package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Which precinct owns which cell, once several of them have grown into one
 * another.
 *
 * <p>Growth only says who painted each <em>road</em> cell. The ground between
 * the roads has to be attributed too, because that is where the buildings go
 * and a precinct cannot be filled, walled or reported on without knowing its
 * own extent.
 *
 * <p><b>This is deliberately a policy rather than one answer.</b> Whether a
 * place should be allowed to take every cell nearer to it than to anyone else,
 * or should be capped at the ground it was budgeted, is a question about how
 * maps should feel rather than about what is correct — and the honest position
 * is that it is not yet known which produces better maps, or whether the answer
 * is the same for a fortress and for a hamlet. Both are implemented so they can
 * be measured against each other rather than argued about.
 */
public interface PrecinctClaim {

    /**
     * Assigns every cell to a precinct index, or {@link GrownTrunkPlan#UNOWNED}
     * for ground no precinct claims — open country, which is a real answer and
     * not a failure.
     *
     * @param owner  per-cell seed index for road cells, from {@link GrownTrunkPlan.Grown}
     * @param budget maximum cells each precinct may claim, by index; ignored by
     *               policies that do not cap
     */
    int[][] assign(int[][] owner, int width, int height, int[] budget);

    /**
     * Every cell goes to whichever precinct grew the nearest road.
     *
     * <p>The whole map is partitioned and nothing is left over. Simplest and
     * most organic; the risk it carries is that a precinct whose growth stalled
     * hands its ground to whoever is beside it, with nothing capping the result.
     */
    static PrecinctClaim nearest() {
        return (owner, width, height, budget) -> flood(owner, width, height, null);
    }

    /**
     * Nearest road wins, but a precinct stops accepting cells once it has taken
     * the ground it was budgeted.
     *
     * <p>What is left over stays unowned, which is open country rather than an
     * error. This is what keeps "how big is this place" an authored number
     * instead of an outcome of how its neighbours happened to grow.
     */
    static PrecinctClaim budgeted() {
        return (owner, width, height, budget) -> flood(owner, width, height, budget);
    }

    /**
     * Ground pooled around each precinct seed rather than spread along its
     * streets.
     *
     * <p><b>A programmed precinct needs this and cannot use the others.</b>
     * Expanding outward from every road cell gives each arm a collar a couple
     * of cells deep, which is the right shape for a town — buildings line
     * streets — and useless for a fortress, whose program is a handful of large
     * footprints. Measured on a grown garrison claim: the buildable ground came
     * out as a ribbon with a largest inscribed square of five cells and a best
     * rectangle of 107x5, against a program owing a 31x16 vehicle shed. Nothing
     * was placed at all.
     *
     * <p>Growing from the seed instead pools the same allowance into one blob.
     * It is still grown and still irregular — where it meets a neighbour it
     * stops, so its outline is a fact about what is around it — but it is
     * compact enough to hold what the place is for. The precinct's own arms
     * then run through the blob as circulation, which is the shipped ward's
     * arrangement arrived at from the other direction.
     *
     * @param seedX per-precinct seed cell
     * @param seedY per-precinct seed cell
     */
    static PrecinctClaim compact(int[] seedX, int[] seedY) {
        return (owner, width, height, budget) ->
                pool(seedX, seedY, owner, width, height, budget);
    }

    /**
     * Breadth-first from the seeds outward, all precincts advancing together so
     * that where two of them are growing for the same ground the nearer seed
     * takes it.
     *
     * <p>A precinct absorbs its own road as it goes and pays for it, because
     * road inside the blob is ground the place has taken; road outside stays
     * where it is and is what leaves the precinct — the artery out, and where a
     * wall will find its gate.
     */
    private static int[][] pool(int[] seedX, int[] seedY, int[][] owner,
                                int width, int height, int[] budget) {
        int[][] claim = new int[width][height];
        for (int[] column : claim) java.util.Arrays.fill(column, GrownTrunkPlan.UNOWNED);
        int[] taken = new int[seedX.length];

        Deque<int[]> queue = new ArrayDeque<>();
        for (int i = 0; i < seedX.length; i++) {
            int x = Math.max(0, Math.min(width - 1, seedX[i]));
            int y = Math.max(0, Math.min(height - 1, seedY[i]));
            if (claim[x][y] != GrownTrunkPlan.UNOWNED) continue;
            claim[x][y] = i;
            taken[i]++;
            queue.add(new int[]{x, y});
        }

        List<int[]> steps = List.of(new int[]{1, 0}, new int[]{-1, 0},
                new int[]{0, 1}, new int[]{0, -1});
        while (!queue.isEmpty()) {
            int[] at = queue.poll();
            int who = claim[at[0]][at[1]];
            if (budget != null && who < budget.length && taken[who] >= budget[who]) continue;
            for (int[] step : steps) {
                int nx = at[0] + step[0];
                int ny = at[1] + step[1];
                if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                if (claim[nx][ny] != GrownTrunkPlan.UNOWNED) continue;
                claim[nx][ny] = who;
                taken[who]++;
                queue.add(new int[]{nx, ny});
            }
        }
        return claim;
    }

    /**
     * Multi-source breadth-first expansion from the road cells outward.
     *
     * <p>BFS from every road cell at once gives each cell the nearest owner by
     * walking distance, which is what "nearest road" has to mean on a grid — a
     * straight-line nearest would hand cells across a wall to whatever is on
     * the other side of it.
     *
     * <p>Ties are settled by whichever source reached the cell first, and the
     * seeding order is the cell order, so the result is a function of the
     * growth rather than of iteration luck.
     */
    private static int[][] flood(int[][] owner, int width, int height, int[] budget) {
        int[][] claim = new int[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) claim[x][y] = GrownTrunkPlan.UNOWNED;
        }
        int precincts = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) precincts = Math.max(precincts, owner[x][y] + 1);
        }
        int[] taken = new int[Math.max(1, precincts)];

        Deque<int[]> queue = new ArrayDeque<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (owner[x][y] == GrownTrunkPlan.UNOWNED) continue;
                claim[x][y] = owner[x][y];
                taken[owner[x][y]]++;
                queue.add(new int[]{x, y});
            }
        }

        List<int[]> steps = List.of(new int[]{1, 0}, new int[]{-1, 0},
                new int[]{0, 1}, new int[]{0, -1});
        while (!queue.isEmpty()) {
            int[] at = queue.poll();
            int who = claim[at[0]][at[1]];
            if (budget != null && who < budget.length && taken[who] >= budget[who]) continue;
            for (int[] step : steps) {
                int nx = at[0] + step[0];
                int ny = at[1] + step[1];
                if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                if (claim[nx][ny] != GrownTrunkPlan.UNOWNED) continue;
                claim[nx][ny] = who;
                taken[who]++;
                queue.add(new int[]{nx, ny});
            }
        }
        return claim;
    }

    /** Cells each precinct ended up with, by index. */
    static int[] sizes(int[][] claim, int precincts) {
        int[] out = new int[precincts];
        for (int[] column : claim) {
            for (int who : column) {
                if (who >= 0 && who < precincts) out[who]++;
            }
        }
        return out;
    }

    /**
     * Cells where two precincts meet — one precinct with a differently-owned
     * orthogonal neighbour.
     *
     * <p>The border is a derived fact rather than a drawn line, which is the
     * point: it follows where the growth actually met.
     */
    static List<int[]> border(int[][] claim, int width, int height) {
        List<int[]> out = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int who = claim[x][y];
                if (who == GrownTrunkPlan.UNOWNED) continue;
                if ((x + 1 < width && other(claim[x + 1][y], who))
                        || (y + 1 < height && other(claim[x][y + 1], who))) {
                    out.add(new int[]{x, y});
                }
            }
        }
        return out;
    }

    private static boolean other(int neighbour, int who) {
        return neighbour != GrownTrunkPlan.UNOWNED && neighbour != who;
    }
}
