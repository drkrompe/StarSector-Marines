package com.dillon.starsectormarines.battle.mapgen.bsp;

import com.dillon.starsectormarines.battle.mapgen.BlockKind;
import com.dillon.starsectormarines.battle.mapgen.BlockLeaf;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Post-label claim pass. Walks the BSP leaf list looking for seed leaves
 * (e.g. {@link BlockKind#MILITARY_BASE}), BFS-grows each one over the
 * {@link LeafAdjacency leaf-adjacency graph} into a 2-3 member
 * {@link Compound}, and rewrites the absorbed leaves to
 * {@link BlockKind#COMPOUND_MEMBER} so the per-leaf filler dispatch can
 * skip them — the compound's dedicated filler handles them as a group.
 *
 * <p>Hard cap: at most {@link #MAX_COMPOUNDS_PER_MAP} per map. Bases are
 * landmarks; more than one diluted the readability of "this is THE base".
 * Excess seed leaves are demoted to {@link BlockKind#FORTIFIED_POST} so the
 * existing per-leaf filler can paint them.
 */
public final class CompoundClaim {

    /** Single landmark compound per map keeps the visual hierarchy clear. Bump for larger conquest maps later. */
    private static final int MAX_COMPOUNDS_PER_MAP = 1;
    /** Target member count for a freshly claimed compound. */
    private static final int TARGET_MEMBERS = 3;
    /** Minimum members for the compound to survive. Below this, the seed is demoted to FORTIFIED_POST. */
    private static final int MIN_MEMBERS = 2;
    /** Max members; beyond this the compound's footprint overlaps the trunk and stops reading as one parcel. */
    private static final int MAX_MEMBERS = 4;
    /** Minimum side length on the seed leaf — smaller and there isn't room for a command building. */
    private static final int SEED_MIN_DIM = 6;

    private CompoundClaim() {}

    /**
     * Run the claim pass and return the list of compounds produced. Mutates
     * leaf kinds in place: seed leaves keep their {@link BlockKind} (e.g.
     * {@link BlockKind#MILITARY_BASE}); claimed members are rewritten to
     * {@link BlockKind#COMPOUND_MEMBER}; failed seeds are demoted to
     * {@link BlockKind#FORTIFIED_POST}.
     */
    public static List<Compound> claim(List<BlockLeaf> leaves,
                                       Map<BlockLeaf, List<BlockLeaf>> adjacency,
                                       Random rng) {
        List<Compound> out = new ArrayList<>();
        for (BlockLeaf seed : leaves) {
            if (seed.kind != BlockKind.MILITARY_BASE) continue;
            if (out.size() >= MAX_COMPOUNDS_PER_MAP) {
                seed.kind = BlockKind.FORTIFIED_POST;
                continue;
            }
            if (seed.width() < SEED_MIN_DIM || seed.height() < SEED_MIN_DIM) {
                seed.kind = BlockKind.FORTIFIED_POST;
                continue;
            }

            List<BlockLeaf> members = growMilitaryBase(seed, adjacency, rng);
            if (members.size() < MIN_MEMBERS) {
                seed.kind = BlockKind.FORTIFIED_POST;
                continue;
            }

            Map<BlockLeaf, Compound.Role> roles = assignRoles(seed, members);
            for (BlockLeaf m : members) {
                if (m != seed) m.kind = BlockKind.COMPOUND_MEMBER;
            }
            out.add(new Compound(BlockKind.MILITARY_BASE, seed, members, roles));
        }
        return out;
    }

    /**
     * BFS over the adjacency graph from {@code seed}. Stops when target size
     * is reached or no eligible neighbor remains. Eligible = original kind
     * isn't WATERFRONT / LANDING_ZONE / COMPOUND_MEMBER / MILITARY_BASE,
     * already-claimed-as-member is excluded, and the neighbor isn't already
     * in another compound (checked via kind == COMPOUND_MEMBER).
     */
    private static List<BlockLeaf> growMilitaryBase(BlockLeaf seed,
                                                    Map<BlockLeaf, List<BlockLeaf>> adjacency,
                                                    Random rng) {
        LinkedHashSet<BlockLeaf> members = new LinkedHashSet<>();
        members.add(seed);

        while (members.size() < TARGET_MEMBERS) {
            BlockLeaf next = pickNextNeighbor(members, adjacency, rng);
            if (next == null) break;
            members.add(next);
            if (members.size() >= MAX_MEMBERS) break;
        }
        return new ArrayList<>(members);
    }

    /**
     * Pick an eligible neighbor of any current member. Slight randomness so
     * the same seed leaf doesn't always grow in the same direction across
     * adjacent runs. Returns null if no candidate qualifies.
     */
    private static BlockLeaf pickNextNeighbor(LinkedHashSet<BlockLeaf> members,
                                              Map<BlockLeaf, List<BlockLeaf>> adjacency,
                                              Random rng) {
        List<BlockLeaf> candidates = new ArrayList<>();
        for (BlockLeaf m : members) {
            for (BlockLeaf n : adjacency.get(m)) {
                if (members.contains(n)) continue;
                if (!isEligible(n)) continue;
                if (!candidates.contains(n)) candidates.add(n);
            }
        }
        if (candidates.isEmpty()) return null;
        return candidates.get(rng.nextInt(candidates.size()));
    }

    private static boolean isEligible(BlockLeaf leaf) {
        if (leaf.kind == BlockKind.WATERFRONT)      return false;
        if (leaf.kind == BlockKind.LANDING_ZONE)    return false;
        if (leaf.kind == BlockKind.COMPOUND_MEMBER) return false;
        if (leaf.kind == BlockKind.MILITARY_BASE)   return false; // would compete for seeding
        return true;
    }

    /**
     * Assign roles by leaf area, with the seed always taking COMMAND.
     * Largest non-seed leaf becomes BARRACKS, next ARMORY, smallest
     * VEHICLE_BAY. For a 2-member compound only COMMAND + BARRACKS are
     * needed; extra roles are reserved for 3-4 member compounds.
     */
    private static Map<BlockLeaf, Compound.Role> assignRoles(BlockLeaf seed, List<BlockLeaf> members) {
        Map<BlockLeaf, Compound.Role> roles = new IdentityHashMap<>(members.size() * 2);
        roles.put(seed, Compound.Role.COMMAND);

        List<BlockLeaf> nonSeed = new ArrayList<>(members.size() - 1);
        for (BlockLeaf m : members) {
            if (m != seed) nonSeed.add(m);
        }
        nonSeed.sort((a, b) -> Integer.compare(b.area(), a.area())); // largest first

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
