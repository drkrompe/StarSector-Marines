package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.PatchField;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.PriorityQueue;

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
     * Each precinct claimed the way its own kind wants — need first, frontage
     * second.
     *
     * <p>The two shapes are not a map-wide choice — a fortress and a town on the
     * same map want different ones, which is the one place this model does not
     * collapse to a single rule. The difference is entirely in <em>where the
     * expansion starts</em>: a programmed precinct grows from its seed cell and
     * pools, a zoned one grows from all of its road at once and hugs its
     * streets. Everything after that — advancing together, the nearer source
     * winning contested ground, stopping at an allowance — is the same for both.
     *
     * <p><b>Programmed places claim first, and that is a statement about what
     * the two allowances mean rather than an accident of ordering.</b> A
     * programmed precinct's allowance is a <em>need</em>: the ground its
     * buildings and lots have to stand on, and short of it the place is not a
     * smaller version of itself but an installation missing its keep. A zoned
     * precinct's allowance is a <em>frontage measure</em>: how much ground lies
     * along the streets it happened to grow, which is a description of a place
     * rather than a requirement of one, and which yields gracefully. Need goes
     * before frontage.
     *
     * <p>Order <em>within</em> a pass still does not matter, which is what the
     * single frontier is for: every programmed precinct is seeded before the
     * first of them advances, so two garrisons contest ground by nearness and
     * not by list position, and the zoned pass then floods together into
     * whatever is left.
     *
     * <p>Seeding both kinds into one frontier is only fair while the sources are
     * comparable in number, and on a production map they are not. A settlement's
     * arms span the whole map, so it enters with thousands of cost-zero sources
     * against a garrison's one, and takes the ground before the garrison's
     * single source can reach it. Measured for a size-6, rating-5 world at seed
     * 42: at 144x80 the garrison was allowed 4592 cells and claimed 2832 with
     * seven buildings unplaced including the keep, and at 112x64 it was allowed
     * 3031 and claimed 647, again seven unplaced — while at 560x336, where the
     * places are far enough apart for the race not to happen, it claimed its
     * whole allowance and built everything.
     */
    static PrecinctClaim byKind(List<Precinct> precincts) {
        return (owner, width, height, budget) -> {
            int[][] claim = new int[width][height];
            for (int[] column : claim) Arrays.fill(column, GrownTrunkPlan.UNOWNED);
            int[] taken = new int[precincts.size()];
            PatchField[] shape = shapeFields(precincts.size());

            PriorityQueue<long[]> pooling = frontier();
            for (int i = 0; i < precincts.size(); i++) {
                if (!precincts.get(i).isProgrammed()) continue;
                Precinct precinct = precincts.get(i);
                int x = Math.max(0, Math.min(width - 1, precinct.seedX()));
                int y = Math.max(0, Math.min(height - 1, precinct.seedY()));
                if (claim[x][y] != GrownTrunkPlan.UNOWNED) continue;
                claim[x][y] = i;
                taken[i]++;
                pooling.add(new long[]{0L, x, y});
            }
            expand(pooling, claim, taken, budget, width, height, shape);

            PriorityQueue<long[]> fronting = frontier();
            for (int i = 0; i < precincts.size(); i++) {
                if (precincts.get(i).isProgrammed()) continue;
                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < height; y++) {
                        if (owner[x][y] != i || claim[x][y] != GrownTrunkPlan.UNOWNED) {
                            continue;
                        }
                        claim[x][y] = i;
                        taken[i]++;
                        fronting.add(new long[]{0L, x, y});
                    }
                }
            }
            expand(fronting, claim, taken, budget, width, height, shape);
            return claim;
        };
    }

    /**
     * Feature size of the noise that keeps a claim from coming out geometric.
     *
     * <p>Large enough to bend the outline into lobes and bays rather than
     * roughen it a cell at a time, which would read as a jagged circle rather
     * than as a place.
     */
    float SHAPE_FEATURE_CELLS = 18f;

    /**
     * How far the noise may push the boundary, as a share of the claim's reach.
     * At zero the shapes are exact and geometric; too high and a precinct sends
     * tendrils across the map instead of being somewhere.
     */
    float SHAPE_STRENGTH = 0.45f;

    /**
     * The shared frontier advance both shapes use once they are seeded.
     *
     * <p><b>Cost-ordered rather than breadth-first, and the cost is noisy.</b>
     * A plain four-neighbour flood expands by Manhattan distance, so a claim
     * grown from a single seed comes out a perfect diamond — which is exactly
     * what a rendered garrison looked like, and reads as a generated shape
     * rather than as a place. Eight-neighbour would trade the diamond for a
     * square, which is no better.
     *
     * <p>Perturbing the cost with a coherent field instead makes the boundary
     * wander: the same allowance, spent further in the directions the field
     * happens to favour. Coherent rather than per-cell random, or the boundary
     * would be a fringe on a diamond instead of a different shape — the same
     * distinction {@code PatchField} exists for, reused here because a claim
     * outline and a scatter of dirt want the same thing from noise.
     *
     * <p>The field is keyed on the precinct index so two places do not bulge in
     * the same directions.
     */
    private static void expand(PriorityQueue<long[]> queue, int[][] claim,
                               int[] taken, int[] budget, int width, int height,
                               PatchField[] shape) {
        List<int[]> steps = List.of(new int[]{1, 0}, new int[]{-1, 0},
                new int[]{0, 1}, new int[]{0, -1});
        while (!queue.isEmpty()) {
            long[] at = queue.poll();
            int x = (int) at[1];
            int y = (int) at[2];
            int who = claim[x][y];
            if (budget != null && who < budget.length && taken[who] >= budget[who]) continue;
            for (int[] step : steps) {
                int nx = x + step[0];
                int ny = y + step[1];
                if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                if (claim[nx][ny] != GrownTrunkPlan.UNOWNED) continue;
                claim[nx][ny] = who;
                taken[who]++;
                float bias = shape == null ? 0f
                        : (shape[who].sample(nx, ny) - 0.5f) * 2f * SHAPE_STRENGTH;
                long cost = at[0] + Math.round(1000 * (1f + bias));
                queue.add(new long[]{cost, nx, ny});
            }
        }
    }

    /** One shape field per precinct, so two places do not bulge alike. */
    private static PatchField[] shapeFields(int precincts) {
        PatchField[] out = new PatchField[precincts];
        for (int i = 0; i < precincts; i++) {
            out[i] = new PatchField(0x9E3779B9L * (i + 1), SHAPE_FEATURE_CELLS);
        }
        return out;
    }

    /** Frontier entry: {cost, x, y}, cheapest first. */
    private static PriorityQueue<long[]> frontier() {
        return new PriorityQueue<>((a, b) -> Long.compare(a[0], b[0]));
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
