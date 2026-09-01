package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.turret.DefensePostKind;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.DefensePostStamper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Puts a walled precinct's emplacements on the ground.
 *
 * <p>The wall is the part of a fortification that is easiest to see and the
 * least of what makes it hard. A wall is a delay and a detour; what turns a
 * place into a problem is what shoots at the ground in front of it, so
 * {@link Fortification#posts} is the dial that decides the fight and this is
 * where it lands.
 *
 * <p>Two bands, and the difference between them is what the emplacement is for:
 *
 * <ul>
 *   <li><b>Overwatching the gates.</b> The perimeter tiers are seeded a short
 *       way inside each open crossing, widest gate first, so the heaviest guns
 *       cover the way most of the attack will come. A gate nobody is watching is
 *       a door; a gate under a heavy post is a decision.
 *   <li><b>Deep inside.</b> Artillery and drone hubs are seeded on the cells
 *       furthest from the outline, because their whole point is reaching past
 *       the wall from somewhere the attacker has to get through the wall to
 *       reach. Placed on the perimeter they are heavy weapons with no standoff,
 *       which is the one thing they are not.
 * </ul>
 *
 * <p>This is the same two-band shape the conquest fortress has always had — a
 * kill zone in front and a rear battery band behind — derived from the
 * precinct's own claimed outline instead of from a biome's bounding box, which
 * is the substitution the whole precinct model is.
 */
public final class PrecinctDefence {

    private PrecinctDefence() {}

    /**
     * How far inside a gate its overwatch sits.
     *
     * <p>Far enough that the post is behind the wall rather than in the opening
     * — a post stamped across the crossing seals it, and the placer's own
     * partition guard would refuse it, costing a gun rather than moving it.
     * Close enough that the gate is well within the turret's reach.
     */
    private static final int GATE_OVERWATCH_SETBACK = 6;

    /**
     * How far from the outline a deep emplacement wants to be.
     *
     * <p>A soft preference rather than a rule: the seeds are drawn from cells at
     * least this deep, and a precinct with no such cell falls back to the random
     * picks like anything else. A garrison too small to have an inside is not a
     * garrison that should be denied its battery.
     */
    private static final int DEEP_SETBACK = 10;

    /** What the fortification asked for, and what the ground actually took. */
    public record Result(Map<DefensePostKind, Integer> placed,
                         Map<DefensePostKind, Integer> unplaced) {

        public int placedCount() {
            return total(placed);
        }

        public int unplacedCount() {
            return total(unplaced);
        }

        private static int total(Map<DefensePostKind, Integer> counts) {
            int sum = 0;
            for (int count : counts.values()) sum += count;
            return sum;
        }
    }

    /**
     * Stamp every emplacement {@code precinct}'s fortification asks for, and say
     * what could not be placed.
     *
     * <p>Runs after the wall, and that order is load-bearing twice over. The
     * gates are not known until the wall decides which crossings stay open, and
     * the placer validates a footprint against the grid — so with the wall down
     * a perimeter post happily straddles the line it is meant to sit behind.
     */
    public static Result stamp(GenContext ctx, Precinct precinct,
                               int[][] claim, int[][] road, int who) {
        Map<DefensePostKind, Integer> placed = new EnumMap<>(DefensePostKind.class);
        Map<DefensePostKind, Integer> unplaced = new EnumMap<>(DefensePostKind.class);
        Fortification fortification = precinct.fortification();
        if (fortification == null || fortification.postCount() == 0) {
            return new Result(Map.copyOf(placed), Map.copyOf(unplaced));
        }
        int[] bbox = bounds(claim, who, ctx.width, ctx.height);
        if (bbox == null) return new Result(Map.copyOf(placed), Map.copyOf(unplaced));

        List<int[]> gateSeeds = gateSeeds(ctx, claim, road, who, fortification);
        List<int[]> deepSeeds = deepSeeds(ctx, claim, who);
        DefensePostStamper.Ground ground = (x, y) ->
                x >= 0 && x < ctx.width && y >= 0 && y < ctx.height && claim[x][y] == who;

        for (DefensePostKind kind : Fortification.PRECEDENCE) {
            int wanted = fortification.posts(kind);
            if (wanted == 0) continue;
            int got = DefensePostStamper.stampInto(ctx.grid, ctx.topology, ground,
                    ctx.get(BspKeys.ROAD_RESERVATION), ctx.doodads, ctx.tactical,
                    ctx.defensePosts, ctx.rng, kind, wanted,
                    isDeep(kind) ? deepSeeds : gateSeeds,
                    bbox[0], bbox[1], bbox[2], bbox[3]);
            if (got > 0) placed.put(kind, got);
            if (got < wanted) unplaced.put(kind, wanted - got);
        }
        return new Result(Map.copyOf(placed), Map.copyOf(unplaced));
    }

    /** Whether this tier's reason for existing is standoff rather than overwatch. */
    private static boolean isDeep(DefensePostKind kind) {
        return kind == DefensePostKind.ARTILLERY || kind == DefensePostKind.DRONE_HUB;
    }

    /**
     * One anchor per open gate, set back inside the precinct, widest gate first.
     *
     * <p>Inward is toward the claim's centroid, which is right for the shapes a
     * grown claim actually takes and wrong for none of them badly: a seed that
     * lands somewhere unusable slides, and a seed the slide cannot rescue costs
     * a gate its dedicated post rather than losing the post.
     */
    private static List<int[]> gateSeeds(GenContext ctx, int[][] claim, int[][] road,
                                         int who, Fortification fortification) {
        List<int[]> seeds = new ArrayList<>();
        int[] centre = centroid(claim, who, ctx.width, ctx.height);
        if (centre == null) return seeds;
        for (PrecinctBoundary.Gate gate : PrecinctBoundary.openGates(
                claim, road, who, ctx.width, ctx.height, fortification.gates())) {
            int gx = 0;
            int gy = 0;
            for (int[] cell : gate.cells()) {
                gx += cell[0];
                gy += cell[1];
            }
            gx /= gate.cells().size();
            gy /= gate.cells().size();
            double dx = centre[0] - gx;
            double dy = centre[1] - gy;
            double len = Math.hypot(dx, dy);
            if (len < 1e-3) {
                seeds.add(new int[]{gx, gy});
                continue;
            }
            seeds.add(new int[]{
                    (int) Math.round(gx + dx / len * GATE_OVERWATCH_SETBACK),
                    (int) Math.round(gy + dy / len * GATE_OVERWATCH_SETBACK)});
        }
        return seeds;
    }

    /**
     * The precinct's own cells furthest from anything that is not the precinct,
     * shuffled.
     *
     * <p>A multi-source flood from outside the claim, so "deep" means deep in the
     * shape the precinct actually grew rather than near the middle of its
     * bounding box — a claim shaped like an L has no cells near the middle of its
     * box at all.
     */
    private static List<int[]> deepSeeds(GenContext ctx, int[][] claim, int who) {
        int w = ctx.width;
        int h = ctx.height;
        int[][] depth = new int[w][h];
        Deque<int[]> frontier = new ArrayDeque<>();
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (claim[x][y] == who) {
                    depth[x][y] = -1;
                } else {
                    depth[x][y] = 0;
                    frontier.add(new int[]{x, y});
                }
            }
        }
        while (!frontier.isEmpty()) {
            int[] cell = frontier.poll();
            for (int[] step : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = cell[0] + step[0];
                int ny = cell[1] + step[1];
                if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                if (depth[nx][ny] != -1) continue;
                depth[nx][ny] = depth[cell[0]][cell[1]] + 1;
                frontier.add(new int[]{nx, ny});
            }
        }
        List<int[]> seeds = new ArrayList<>();
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (depth[x][y] >= DEEP_SETBACK) seeds.add(new int[]{x, y});
            }
        }
        Collections.shuffle(seeds, ctx.rng);
        return seeds;
    }

    /** Bounding box of the precinct's claim, or null when it claimed nothing. */
    private static int[] bounds(int[][] claim, int who, int w, int h) {
        int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE, bottom = Integer.MIN_VALUE;
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (claim[x][y] != who) continue;
                if (x < left) left = x;
                if (x > right) right = x;
                if (y < top) top = y;
                if (y > bottom) bottom = y;
            }
        }
        return left == Integer.MAX_VALUE ? null : new int[]{left, top, right, bottom};
    }

    /** Mean of the claimed cells, or null when it claimed nothing. */
    private static int[] centroid(int[][] claim, int who, int w, int h) {
        long sumX = 0;
        long sumY = 0;
        long count = 0;
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (claim[x][y] != who) continue;
                sumX += x;
                sumY += y;
                count++;
            }
        }
        return count == 0 ? null : new int[]{(int) (sumX / count), (int) (sumY / count)};
    }
}
