package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.MapDistrictTheme;
import com.dillon.starsectormarines.battle.world.gen.WeightedTable;
import com.dillon.starsectormarines.battle.world.gen.bsp.DistrictMap;

import java.util.Random;

/**
 * Asks the district map per precinct rather than once per map.
 *
 * <p>{@link DistrictMap} themes the whole map on one grid of twenty-cell
 * blocks, rolled by where a block is — coast at the edge, civic in the middle.
 * That is right for a map that is one city and wrong for one that is four
 * places: a hamlet's parcels were cut from its own claim and then themed by a
 * scatter that had never heard of it, so every place came out the same mix.
 *
 * <p>This lays one layer per zoned precinct over that map. Each layer covers
 * the blocks the precinct's claim touches, is rolled from the precinct's own
 * {@link PrecinctCharacter}, is smoothed only against itself so a cluster never
 * crosses a border, and has its centre forced at the precinct's own seed. The
 * map answers {@code themeAt} from the layer wherever a precinct claims the
 * ground and from the map-wide roll everywhere else — which, on a precinct map,
 * is hinterland nobody partitions, so open country holds no themed content.
 *
 * <p>Runs only when a map has precincts. Every other recipe never reaches this
 * and takes no draw it did not take before.
 */
public final class PrecinctZoning {

    private PrecinctZoning() {}

    /**
     * Chance a block adopts a neighbour's theme, so a character reads as a
     * couple of clustered blocks rather than confetti. The map-wide overlay's
     * value, for the same reason it has one.
     */
    static final float SMOOTH_PROBABILITY = 0.45f;

    /** Lays every zoned precinct's own themes over the map. */
    public static void apply(DistrictMap map, PrecinctPlan plan, int[][] claim, Random rng) {
        for (int i = 0; i < plan.precincts().size(); i++) {
            Precinct precinct = plan.precincts().get(i);
            if (precinct.isProgrammed()) continue;
            map.overlay(claim, i, roll(map, claim, i, precinct, rng));
        }
    }

    /**
     * One precinct's blocks, rolled from its character.
     *
     * <p>Only the blocks its claim touches are rolled; the rest of the layer
     * stays {@code null} and the map answers from its own roll there.
     */
    static MapDistrictTheme[][] roll(DistrictMap map, int[][] claim, int who,
                                     Precinct precinct, Random rng) {
        int blocksX = map.districtsX();
        int blocksY = map.districtsY();
        boolean[][] touched = new boolean[blocksX][blocksY];
        for (int x = 0; x < claim.length; x++) {
            for (int y = 0; y < claim[x].length; y++) {
                if (claim[x][y] == who) touched[map.districtX(x)][map.districtY(y)] = true;
            }
        }

        PrecinctCharacter character = precinct.character();
        WeightedTable<MapDistrictTheme> table = character.table();
        MapDistrictTheme[][] out = new MapDistrictTheme[blocksX][blocksY];
        for (int dx = 0; dx < blocksX; dx++) {
            for (int dy = 0; dy < blocksY; dy++) {
                if (!touched[dx][dy]) continue;
                out[dx][dy] = inland(table.pick(rng), map, dx, dy);
            }
        }
        smooth(out, touched, map, rng);

        if (character.hasCentre()) {
            int[] block = nearestTouched(touched, map.districtX(precinct.seedX()),
                    map.districtY(precinct.seedY()));
            if (block != null) out[block[0]][block[1]] = character.centre();
        }
        return out;
    }

    /**
     * The coast stays at the map edge, which is the map-wide overlay's law
     * carried into the layers. No shipped character rolls waterfront, so this
     * bites only on an authored one that does.
     */
    private static MapDistrictTheme inland(MapDistrictTheme theme, DistrictMap map,
                                           int dx, int dy) {
        if (theme == MapDistrictTheme.WATERFRONT && !map.isEdgeDistrict(dx, dy)) {
            return MapDistrictTheme.OUTSKIRTS;
        }
        return theme;
    }

    /**
     * Each block may adopt a neighbour's theme, from within this precinct only:
     * a cluster is a feature of one place and must not reach across a border
     * into the next one.
     */
    private static void smooth(MapDistrictTheme[][] out, boolean[][] touched,
                               DistrictMap map, Random rng) {
        for (int dx = 0; dx < out.length; dx++) {
            for (int dy = 0; dy < out[dx].length; dy++) {
                if (!touched[dx][dy]) continue;
                if (rng.nextFloat() >= SMOOTH_PROBABILITY) continue;
                MapDistrictTheme neighbour = randomNeighbour(out, touched, dx, dy, rng);
                if (neighbour == null) continue;
                out[dx][dy] = inland(neighbour, map, dx, dy);
            }
        }
    }

    private static MapDistrictTheme randomNeighbour(MapDistrictTheme[][] out,
                                                    boolean[][] touched,
                                                    int dx, int dy, Random rng) {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int i = dirs.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int[] tmp = dirs[i];
            dirs[i] = dirs[j];
            dirs[j] = tmp;
        }
        for (int[] d : dirs) {
            int nx = dx + d[0];
            int ny = dy + d[1];
            if (nx < 0 || nx >= out.length || ny < 0 || ny >= out[nx].length) continue;
            if (touched[nx][ny]) return out[nx][ny];
        }
        return null;
    }

    /**
     * The block a centre goes on. The seed's own block nearly always — a seed
     * is inside its claim — but a claim policy is free to leave the seed on a
     * road it does not own, so the nearest block the precinct does touch is
     * the honest fallback.
     */
    private static int[] nearestTouched(boolean[][] touched, int seedDx, int seedDy) {
        int[] best = null;
        int bestGap = Integer.MAX_VALUE;
        for (int dx = 0; dx < touched.length; dx++) {
            for (int dy = 0; dy < touched[dx].length; dy++) {
                if (!touched[dx][dy]) continue;
                int gap = Math.abs(dx - seedDx) + Math.abs(dy - seedDy);
                if (gap < bestGap) {
                    bestGap = gap;
                    best = new int[]{dx, dy};
                }
            }
        }
        return best;
    }
}
