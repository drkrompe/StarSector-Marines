package com.dillon.starsectormarines.battle.mapgen.bsp;

import com.dillon.starsectormarines.battle.mapgen.BiomeKind;
import com.dillon.starsectormarines.battle.mapgen.BlockKind;
import com.dillon.starsectormarines.battle.mapgen.BlockLeaf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Post-label claim pass. Walks the BSP leaf list looking for seed leaves
 * (e.g. {@link BlockKind#MILITARY_BASE}), BFS-grows each one over the
 * {@link LeafAdjacency leaf-adjacency graph} into a {@link Compound}, and
 * rewrites the absorbed leaves to {@link BlockKind#COMPOUND_MEMBER} so the
 * per-leaf filler dispatch can skip them — the compound's dedicated filler
 * handles them as a group.
 *
 * <p>Driven by a list of {@link ClaimSpec} so multiple compound kinds
 * (MILITARY_BASE, GATED_HOUSING, DENSE_QUARTER) can run in one pass with
 * different size budgets / per-map caps / demotion targets. Specs are
 * processed in the order given; once a leaf is claimed (by any spec) it
 * can't seed another compound.
 */
public final class CompoundClaim {

    /** Per-compound-kind configuration for the claim pass. */
    public static final class ClaimSpec {
        public final BlockKind seedKind;
        public final BlockKind demoteTo;
        public final int maxPerMap;
        public final int targetMembers;
        public final int minMembers;
        public final int maxMembers;
        public final int seedMinDim;
        /** Minimum dimension (both axes) for a neighbor leaf to be absorbed. A member leaf of dimension N produces a sub-building of N-2 (1-cell inset); that sub-building needs ≥5 on each axis for a 3x3 interior with doors. */
        public final int memberMinDim;
        /** Block kinds that cannot be absorbed as a neighbor (e.g. WATERFRONT, LANDING_ZONE). */
        public final Set<BlockKind> ineligibleNeighbors;

        public ClaimSpec(BlockKind seedKind, BlockKind demoteTo,
                         int maxPerMap, int targetMembers, int minMembers, int maxMembers,
                         int seedMinDim, Set<BlockKind> ineligibleNeighbors) {
            this(seedKind, demoteTo, maxPerMap, targetMembers, minMembers, maxMembers,
                    seedMinDim, seedMinDim, ineligibleNeighbors);
        }

        public ClaimSpec(BlockKind seedKind, BlockKind demoteTo,
                         int maxPerMap, int targetMembers, int minMembers, int maxMembers,
                         int seedMinDim, int memberMinDim, Set<BlockKind> ineligibleNeighbors) {
            this.seedKind = seedKind;
            this.demoteTo = demoteTo;
            this.maxPerMap = maxPerMap;
            this.targetMembers = targetMembers;
            this.minMembers = minMembers;
            this.maxMembers = maxMembers;
            this.seedMinDim = seedMinDim;
            this.memberMinDim = memberMinDim;
            this.ineligibleNeighbors = ineligibleNeighbors;
        }
    }

    /** Default spec set — non-Conquest missions. One compound per kind max. */
    public static final List<ClaimSpec> DEFAULT_SPECS = Arrays.asList(
            new ClaimSpec(BlockKind.MILITARY_BASE, BlockKind.FORTIFIED_POST,
                    1, 3, 2, 4, 6, 7,
                    EnumSet.of(BlockKind.WATERFRONT, BlockKind.LANDING_ZONE)),
            new ClaimSpec(BlockKind.GATED_HOUSING, BlockKind.BUILDING_RESIDENTIAL,
                    1, 3, 2, 4, 5,
                    EnumSet.of(BlockKind.WATERFRONT, BlockKind.LANDING_ZONE)),
            new ClaimSpec(BlockKind.DENSE_QUARTER, BlockKind.BUILDING_COMMERCIAL,
                    1, 3, 2, 4, 5,
                    EnumSet.of(BlockKind.WATERFRONT, BlockKind.LANDING_ZONE)));

    /** Conquest spec set — {@link BiomeCompoundSeeder} force-seeds up to 3 MILITARY_BASE leaves (one per biome band). */
    public static final List<ClaimSpec> CONQUEST_SPECS = Arrays.asList(
            new ClaimSpec(BlockKind.MILITARY_BASE, BlockKind.FORTIFIED_POST,
                    3, 3, 2, 4, 6, 7,
                    EnumSet.of(BlockKind.WATERFRONT, BlockKind.LANDING_ZONE)),
            new ClaimSpec(BlockKind.GATED_HOUSING, BlockKind.BUILDING_RESIDENTIAL,
                    1, 3, 2, 4, 5,
                    EnumSet.of(BlockKind.WATERFRONT, BlockKind.LANDING_ZONE)),
            new ClaimSpec(BlockKind.DENSE_QUARTER, BlockKind.BUILDING_COMMERCIAL,
                    1, 3, 2, 4, 5,
                    EnumSet.of(BlockKind.WATERFRONT, BlockKind.LANDING_ZONE)));

    private CompoundClaim() {}

    /**
     * Run the claim pass for every {@link ClaimSpec} and return the combined
     * compound list. Each spec processes the leaf list independently; once a
     * leaf is claimed it's no longer eligible to seed another spec.
     */
    public static List<Compound> claim(List<BlockLeaf> leaves,
                                       Map<BlockLeaf, List<BlockLeaf>> adjacency,
                                       List<ClaimSpec> specs,
                                       Random rng) {
        return claim(leaves, adjacency, specs, null, rng);
    }

    public static List<Compound> claim(List<BlockLeaf> leaves,
                                       Map<BlockLeaf, List<BlockLeaf>> adjacency,
                                       List<ClaimSpec> specs,
                                       BiomeMap biomeMap,
                                       Random rng) {
        List<Compound> out = new ArrayList<>();
        Map<BlockKind, Integer> claimedPerKind = new HashMap<>();
        for (BlockLeaf seed : leaves) {
            ClaimSpec spec = findSpec(seed.kind, specs);
            if (spec == null) continue;
            int claimed = claimedPerKind.getOrDefault(spec.seedKind, 0);
            if (claimed >= spec.maxPerMap) {
                seed.kind = spec.demoteTo;
                continue;
            }
            if (seed.width() < spec.seedMinDim || seed.height() < spec.seedMinDim) {
                seed.kind = spec.demoteTo;
                continue;
            }
            List<BlockLeaf> members = grow(seed, adjacency, spec, rng);
            if (members.size() < spec.minMembers) {
                seed.kind = spec.demoteTo;
                continue;
            }
            Map<BlockLeaf, Compound.Role> roles = assignRoles(seed, members);
            for (BlockLeaf m : members) {
                if (m != seed) m.kind = BlockKind.COMPOUND_MEMBER;
            }
            BiomeKind biome = (biomeMap != null)
                    ? biomeMap.biomeAt(seed.centerX(), seed.centerY()) : null;
            out.add(new Compound(spec.seedKind, seed, members, roles, biome));
            claimedPerKind.merge(spec.seedKind, 1, Integer::sum);
        }
        return out;
    }

    private static ClaimSpec findSpec(BlockKind kind, List<ClaimSpec> specs) {
        for (ClaimSpec s : specs) {
            if (s.seedKind == kind) return s;
        }
        return null;
    }

    /**
     * BFS-grow over the adjacency graph from {@code seed} per spec rules.
     * Stops when target size is reached or no eligible neighbor remains.
     */
    private static List<BlockLeaf> grow(BlockLeaf seed,
                                        Map<BlockLeaf, List<BlockLeaf>> adjacency,
                                        ClaimSpec spec, Random rng) {
        LinkedHashSet<BlockLeaf> members = new LinkedHashSet<>();
        members.add(seed);
        while (members.size() < spec.targetMembers) {
            BlockLeaf next = pickNextNeighbor(members, adjacency, spec, rng);
            if (next == null) break;
            members.add(next);
            if (members.size() >= spec.maxMembers) break;
        }
        return new ArrayList<>(members);
    }

    private static BlockLeaf pickNextNeighbor(LinkedHashSet<BlockLeaf> members,
                                              Map<BlockLeaf, List<BlockLeaf>> adjacency,
                                              ClaimSpec spec, Random rng) {
        List<BlockLeaf> candidates = new ArrayList<>();
        for (BlockLeaf m : members) {
            for (BlockLeaf n : adjacency.get(m)) {
                if (members.contains(n)) continue;
                if (!isEligible(n, spec)) continue;
                if (!candidates.contains(n)) candidates.add(n);
            }
        }
        if (candidates.isEmpty()) return null;
        return candidates.get(rng.nextInt(candidates.size()));
    }

    private static boolean isEligible(BlockLeaf leaf, ClaimSpec spec) {
        if (leaf.kind == BlockKind.COMPOUND_MEMBER) return false;
        if (leaf.kind == spec.seedKind)             return false;
        for (ClaimSpec other : DEFAULT_SPECS) {
            if (other.seedKind != spec.seedKind && leaf.kind == other.seedKind) return false;
        }
        if (spec.ineligibleNeighbors.contains(leaf.kind)) return false;
        if (leaf.width() < spec.memberMinDim || leaf.height() < spec.memberMinDim) return false;
        return true;
    }

    /**
     * Assign roles by leaf area, with the seed always taking COMMAND. The
     * fillers interpret the role for their own flavor — a {@code BARRACKS}
     * role in a gated-housing compound paints a secondary house, in a
     * military compound paints a barracks. Generic ordinal-by-size mapping.
     */
    private static Map<BlockLeaf, Compound.Role> assignRoles(BlockLeaf seed, List<BlockLeaf> members) {
        Map<BlockLeaf, Compound.Role> roles = new IdentityHashMap<>(members.size() * 2);
        roles.put(seed, Compound.Role.COMMAND);

        List<BlockLeaf> nonSeed = new ArrayList<>(members.size() - 1);
        for (BlockLeaf m : members) {
            if (m != seed) nonSeed.add(m);
        }
        nonSeed.sort((a, b) -> Integer.compare(b.area(), a.area()));

        Compound.Role[] order = {
                Compound.Role.BARRACKS,
                Compound.Role.ARMORY,
                Compound.Role.VEHICLE_BAY
        };
        for (int i = 0; i < nonSeed.size(); i++) {
            roles.put(nonSeed.get(i), order[Math.min(i, order.length - 1)]);
        }
        return roles;
    }
}
