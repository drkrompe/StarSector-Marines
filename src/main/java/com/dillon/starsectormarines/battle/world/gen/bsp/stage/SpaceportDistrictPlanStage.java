package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.MapDistrictTheme;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.Bsp;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.Compound;
import com.dillon.starsectormarines.battle.world.gen.bsp.DistrictMap;
import com.dillon.starsectormarines.battle.world.gen.bsp.LeafAdjacency;
import com.dillon.starsectormarines.battle.world.gen.bsp.TrunkPlan;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reserves one contiguous multi-leaf campus for a campaign-backed civilian
 * spaceport. The ordinary label pass may scatter individual apron leaves;
 * this pass replaces that scatter with the largest connected component in
 * the authored HARBOR_PORT zoning pocket and publishes it as one compound.
 */
public final class SpaceportDistrictPlanStage implements GenStage {

    private static final int PAD_MIN_SIDE = 5;
    private static final int TIER_ONE_MEMBERS = 6; // four pads + terminal + service yard
    private static final int MEGAPORT_MEMBERS = 8; // six pads + terminal + service yard

    private static final Comparator<BlockLeaf> BY_POSITION = Comparator
            .comparingInt((BlockLeaf leaf) -> leaf.top)
            .thenComparingInt(leaf -> leaf.left);

    @Override
    public void run(GenContext ctx) {
        TargetProfile profile = ctx.get(BspKeys.MARKET_PROFILE);
        DistrictMap districtMap = ctx.get(BspKeys.DISTRICT_MAP);
        Bsp.Partition partition = ctx.get(BspKeys.PARTITION);
        if (profile == null || profile.spaceportTier() <= 0
                || districtMap == null || partition == null) return;

        // A campaign port gets exactly one authored campus. Remove incidental
        // HARBOR_PORT pad rolls before reserving the connected campus below.
        for (BlockLeaf leaf : partition.leaves) {
            if (leaf.kind == BlockKind.SPACEPORT_PAD) leaf.kind = BlockKind.INDUSTRIAL_YARD;
        }

        TrunkPlan.Plan trunkPlan = ctx.get(BspKeys.TRUNK_PLAN);
        int portX = ctx.width / 8;
        int portY = 5 * ctx.height / 8;
        int portDistrictX = portX / districtMap.districtCellWidth();
        int portDistrictY = portY / districtMap.districtCellHeight();
        Set<BlockLeaf> candidates = Collections.newSetFromMap(new IdentityHashMap<>());
        for (BlockLeaf leaf : partition.leaves) {
            int dx = leaf.centerX() / districtMap.districtCellWidth();
            int dy = leaf.centerY() / districtMap.districtCellHeight();
            boolean inReservedBlock = dx >= portDistrictX && dx <= portDistrictX + 1
                    && dy >= portDistrictY && dy <= portDistrictY + 1;
            boolean sameTrunkQuadrant = trunkPlan == null
                    || sameSide(leaf.centerX(), portX,
                            (trunkPlan.intersection.x0 + trunkPlan.intersection.x1) / 2)
                    && sameSide(leaf.centerY(), portY,
                            (trunkPlan.intersection.y0 + trunkPlan.intersection.y1) / 2);
            if (leaf.width() >= PAD_MIN_SIDE && leaf.height() >= PAD_MIN_SIDE
                    && inReservedBlock && sameTrunkQuadrant) {
                candidates.add(leaf);
            }
        }
        if (candidates.isEmpty()) return;

        Map<BlockLeaf, List<BlockLeaf>> adjacency =
                LeafAdjacency.compute(partition.leaves, ctx.width, ctx.height);
        List<BlockLeaf> component = largestComponent(candidates, adjacency);
        int targetMembers = profile.spaceportTier() >= 2
                ? MEGAPORT_MEMBERS : TIER_ONE_MEMBERS;
        List<BlockLeaf> members = compactMembers(component, adjacency,
                Math.min(targetMembers, component.size()));
        if (members.isEmpty()) return;

        BlockLeaf seed = members.get(0);
        seed.kind = BlockKind.SPACEPORT_PAD;
        Map<BlockLeaf, Compound.Role> roles = new IdentityHashMap<>();
        roles.put(seed, Compound.Role.COMMAND);
        Compound.Role[] supportRoles = {
                Compound.Role.BARRACKS, Compound.Role.ARMORY, Compound.Role.VEHICLE_BAY
        };
        for (int i = 1; i < members.size(); i++) {
            BlockLeaf member = members.get(i);
            member.kind = BlockKind.COMPOUND_MEMBER;
            roles.put(member, supportRoles[(i - 1) % supportRoles.length]);
        }
        ctx.put(BspKeys.SPACEPORT_DISTRICT,
                new Compound(BlockKind.SPACEPORT_PAD, seed, members, roles, null));
    }

    private static List<BlockLeaf> largestComponent(
            Set<BlockLeaf> candidates, Map<BlockLeaf, List<BlockLeaf>> adjacency) {
        Set<BlockLeaf> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<BlockLeaf> best = List.of();
        int bestArea = -1;
        List<BlockLeaf> starts = new ArrayList<>(candidates);
        starts.sort(BY_POSITION);
        for (BlockLeaf start : starts) {
            if (!seen.add(start)) continue;
            List<BlockLeaf> component = new ArrayList<>();
            ArrayDeque<BlockLeaf> queue = new ArrayDeque<>();
            queue.add(start);
            int area = 0;
            while (!queue.isEmpty()) {
                BlockLeaf leaf = queue.removeFirst();
                component.add(leaf);
                area += leaf.area();
                for (BlockLeaf neighbor : adjacency.getOrDefault(leaf, List.of())) {
                    if (candidates.contains(neighbor) && seen.add(neighbor)) queue.addLast(neighbor);
                }
            }
            component.sort(BY_POSITION);
            if (component.size() > best.size()
                    || (component.size() == best.size() && area > bestArea)) {
                best = component;
                bestArea = area;
            }
        }
        return best;
    }

    /** Selects a compact connected subset, with the most-connected parcel as seed. */
    private static List<BlockLeaf> compactMembers(List<BlockLeaf> component,
                                                   Map<BlockLeaf, List<BlockLeaf>> adjacency,
                                                   int target) {
        if (component.isEmpty() || target <= 0) return List.of();
        Set<BlockLeaf> componentSet = Collections.newSetFromMap(new IdentityHashMap<>());
        componentSet.addAll(component);
        BlockLeaf seed = component.stream().max(Comparator
                .comparingInt((BlockLeaf leaf) -> candidateDegree(leaf, componentSet, adjacency))
                .thenComparingInt(BlockLeaf::area)
                .thenComparingInt(leaf -> -leaf.top)
                .thenComparingInt(leaf -> -leaf.left)).orElse(component.get(0));

        LinkedHashSet<BlockLeaf> selected = new LinkedHashSet<>();
        selected.add(seed);
        while (selected.size() < target) {
            Set<BlockLeaf> frontierSet = Collections.newSetFromMap(new IdentityHashMap<>());
            for (BlockLeaf member : selected) {
                for (BlockLeaf neighbor : adjacency.getOrDefault(member, List.of())) {
                    if (componentSet.contains(neighbor) && !selected.contains(neighbor)) {
                        frontierSet.add(neighbor);
                    }
                }
            }
            if (frontierSet.isEmpty()) break;
            List<BlockLeaf> frontier = new ArrayList<>(frontierSet);
            frontier.sort(Comparator
                    .comparingInt((BlockLeaf leaf) -> -selectedDegree(leaf, selected, adjacency))
                    .thenComparingInt(leaf -> centerDistance(leaf, seed))
                    .thenComparingInt((BlockLeaf leaf) -> -leaf.area())
                    .thenComparing(BY_POSITION));
            selected.add(frontier.get(0));
        }
        return new ArrayList<>(selected);
    }

    private static int candidateDegree(BlockLeaf leaf, Set<BlockLeaf> candidates,
                                       Map<BlockLeaf, List<BlockLeaf>> adjacency) {
        int count = 0;
        for (BlockLeaf neighbor : adjacency.getOrDefault(leaf, List.of())) {
            if (candidates.contains(neighbor)) count++;
        }
        return count;
    }

    private static int selectedDegree(BlockLeaf leaf, Set<BlockLeaf> selected,
                                      Map<BlockLeaf, List<BlockLeaf>> adjacency) {
        int count = 0;
        for (BlockLeaf neighbor : adjacency.getOrDefault(leaf, List.of())) {
            if (selected.contains(neighbor)) count++;
        }
        return count;
    }

    private static int centerDistance(BlockLeaf a, BlockLeaf b) {
        return Math.abs(a.centerX() - b.centerX()) + Math.abs(a.centerY() - b.centerY());
    }

    private static boolean sameSide(int value, int anchor, int divider) {
        return anchor < divider ? value < divider : value > divider;
    }
}
