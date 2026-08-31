package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.MapDistrictTheme;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.BiomeMap;
import com.dillon.starsectormarines.battle.world.gen.bsp.Bsp;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.bsp.DistrictMap;
import com.dillon.starsectormarines.battle.world.gen.bsp.LeafAdjacency;
import com.dillon.starsectormarines.battle.world.gen.bsp.TrunkPlan;
import org.apache.log4j.Logger;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Step 1c — lay down the zoning overlay. In conquest mode
 * ({@link BspKeys#AXIS} bound) a {@link BiomeMap} takes precedence — biome
 * bands run along the traversal axis and fully drive theme picks. In legacy
 * mode a {@link DistrictMap} scatters themes uniformly with a CIVIC nudge at
 * the trunk crossing.
 *
 * <p>Reads {@link BspKeys#AXIS} (presence selects mode), {@link BspKeys#TRUNK_PLAN}
 * (intersection center for the CIVIC nudge) and {@link BspKeys#PARTITION} (for
 * the log line). Binds exactly one of {@link BspKeys#BIOME_MAP} /
 * {@link BspKeys#DISTRICT_MAP}.
 */
public final class ZoningOverlayStage implements GenStage {

    /** Coarse district cells the port pocket spans on a side. */
    private static final int PORT_POCKET_SPAN = 2;

    /**
     * Reserves the port pocket on the district block that can actually hold a
     * campus, rather than at a fixed corner of the map.
     *
     * <p>The pocket used to be pinned to {@code (width/8, 5*height/8)}, which
     * put every port in the south-west of every map whatever else was there,
     * and {@code SpaceportDistrictPlanStage} re-derived the same constant to
     * search in. Two places encoding one decision disagree eventually; here
     * they disagreed with the map instead, and a trunk roll that left too few
     * leaves in that corner produced a port short of berths with nothing
     * reporting it.
     *
     * <p>Every candidate block is scored by the largest group of pad-sized
     * leaves inside it that are actually connected to each other, which is the
     * quantity the campus is built from — counting leaves alone would happily
     * pick a block whose leaves sit either side of a trunk. Ties go to the
     * lowest block index so the choice stays seed-stable.
     */
    static void placePortPocket(DistrictMap districtMap, Bsp.Partition partition,
                                        int width, int height) {
        Map<BlockLeaf, List<BlockLeaf>> adjacency =
                LeafAdjacency.compute(partition.leaves, width, height);
        int bestX = 0, bestY = 0, bestScore = -1;
        for (int dy = 0; dy + PORT_POCKET_SPAN <= districtMap.districtsY(); dy++) {
            for (int dx = 0; dx + PORT_POCKET_SPAN <= districtMap.districtsX(); dx++) {
                int score = largestConnectedGroup(partition, adjacency, districtMap, dx, dy);
                if (score > bestScore) {
                    bestScore = score;
                    bestX = dx;
                    bestY = dy;
                }
            }
        }
        for (int dx = 0; dx < PORT_POCKET_SPAN; dx++) {
            for (int dy = 0; dy < PORT_POCKET_SPAN; dy++) {
                districtMap.forceThemeAt(
                        (bestX + dx) * districtMap.districtCellWidth(),
                        (bestY + dy) * districtMap.districtCellHeight(),
                        MapDistrictTheme.HARBOR_PORT);
            }
        }
    }

    /**
     * Size of the largest connected run of pad-sized leaves centred inside this
     * block, counting only leaves that can actually end up in port zoning.
     *
     * <p>{@link DistrictMap#forceThemeAt} silently declines to overwrite a
     * WATERFRONT district, so a block scored on all its leaves promises a
     * campus the pocket will not deliver: the coast keeps its theme and those
     * leaves are never candidates. Excluding them here is what makes the score
     * a prediction of the outcome rather than of the request.
     */
    private static int largestConnectedGroup(Bsp.Partition partition,
                                             Map<BlockLeaf, List<BlockLeaf>> adjacency,
                                             DistrictMap districtMap, int blockX, int blockY) {
        Set<BlockLeaf> inside = Collections.newSetFromMap(new IdentityHashMap<>());
        for (BlockLeaf leaf : partition.leaves) {
            if (leaf.width() < PORT_PAD_MIN_SIDE || leaf.height() < PORT_PAD_MIN_SIDE) continue;
            int dx = leaf.centerX() / districtMap.districtCellWidth();
            int dy = leaf.centerY() / districtMap.districtCellHeight();
            if (dx < blockX || dx >= blockX + PORT_POCKET_SPAN
                    || dy < blockY || dy >= blockY + PORT_POCKET_SPAN) continue;
            if (districtMap.themeAtDistrict(dx, dy) == MapDistrictTheme.WATERFRONT) continue;
            inside.add(leaf);
        }
        Set<BlockLeaf> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        int best = 0;
        for (BlockLeaf leaf : partition.leaves) {
            if (!inside.contains(leaf) || seen.contains(leaf)) continue;
            int size = 0;
            Deque<BlockLeaf> stack = new ArrayDeque<>();
            stack.push(leaf);
            seen.add(leaf);
            while (!stack.isEmpty()) {
                BlockLeaf at = stack.pop();
                size++;
                for (BlockLeaf next : adjacency.getOrDefault(at, List.of())) {
                    if (inside.contains(next) && seen.add(next)) stack.push(next);
                }
            }
            best = Math.max(best, size);
        }
        return best;
    }

    /** Smallest leaf side a spaceport pad needs; mirrors the plan stage's gate. */
    private static final int PORT_PAD_MIN_SIDE = 5;


    private static final Logger LOG = Logger.getLogger(ZoningOverlayStage.class);

    @Override
    public void run(GenContext ctx) {
        TraversalAxis axis = ctx.get(BspKeys.AXIS);
        TrunkPlan.Plan plan = ctx.get(BspKeys.TRUNK_PLAN);
        Bsp.Partition partition = ctx.get(BspKeys.PARTITION);
        TargetProfile profile = ctx.get(BspKeys.MARKET_PROFILE);
        Set<EconomicFunction> functions = profile != null
                ? profile.functions() : EnumSet.noneOf(EconomicFunction.class);
        if (axis != null) {
            BiomeMap biomeMap = new BiomeMap(ctx.width, ctx.height, axis, ctx.rng, functions);
            ctx.put(BspKeys.BIOME_MAP, biomeMap);
            LOG.debug("BspCityGenerator: " + partition.leaves.size() + " leaves on "
                    + ctx.width + "x" + ctx.height + " grid, "
                    + plan.trunks.size() + " trunk(s), biome axis=" + axis
                    + ", econ=" + functions);
        } else {
            DistrictMap districtMap = new DistrictMap(ctx.width, ctx.height, ctx.rng, functions);
            int ixCenterX = (plan.intersection.x0 + plan.intersection.x1) / 2;
            int ixCenterY = (plan.intersection.y0 + plan.intersection.y1) / 2;
            districtMap.forceThemeAt(ixCenterX, ixCenterY, MapDistrictTheme.CIVIC);
            // A real campaign spaceport gets a coherent port district on the
            // marine half of ordinary urban maps. The profile previously died
            // at the launch boundary for non-conquest missions, leaving its
            // spaceport tier unused.
            if (profile != null && profile.spaceportTier() > 0) {
                placePortPocket(districtMap, partition, ctx.width, ctx.height);
            }
            ctx.put(BspKeys.DISTRICT_MAP, districtMap);
            LOG.debug("BspCityGenerator: " + partition.leaves.size() + " leaves on "
                    + ctx.width + "x" + ctx.height + " grid, "
                    + plan.trunks.size() + " trunk(s), "
                    + districtMap.districtsX() + "x" + districtMap.districtsY() + " districts");
        }
    }
}
