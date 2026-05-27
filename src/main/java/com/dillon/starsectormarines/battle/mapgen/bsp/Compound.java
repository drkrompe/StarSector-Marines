package com.dillon.starsectormarines.battle.mapgen.bsp;

import com.dillon.starsectormarines.battle.mapgen.BiomeKind;
import com.dillon.starsectormarines.battle.mapgen.BlockKind;
import com.dillon.starsectormarines.battle.mapgen.BlockLeaf;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Multi-leaf cluster claimed by {@link CompoundClaim} and painted by a
 * compound-aware filler (e.g. {@code MilitaryBaseFiller}). A compound owns
 * the union of its member leaves plus the inter-leaf road frames between
 * them, which the filler typically encloses with a perimeter wall and
 * repaints as courtyard / parade ground.
 *
 * <p>The {@link #seed} leaf is the BSP leaf the labeler originally tagged
 * with the seeding {@link BlockKind} (e.g. {@link BlockKind#MILITARY_BASE});
 * the {@link #members} list includes the seed plus every leaf BFS-grew into.
 * Member leaves are mutated to {@link BlockKind#COMPOUND_MEMBER} so the
 * per-leaf filler dispatch skips them.
 *
 * <p>For role assignment, callers can use {@link #roles}, an
 * {@link IdentityHashMap} keyed on the {@link BlockLeaf} identity. Roles are
 * compound-kind specific — a {@code MILITARY_BASE} compound uses
 * {@link Role#COMMAND} / {@link Role#BARRACKS} / {@link Role#ARMORY} /
 * {@link Role#VEHICLE_BAY}. Future compound kinds will reuse the same enum
 * or add more values as needed.
 */
public final class Compound {

    /** Per-member-leaf functional role inside a compound. */
    public enum Role {
        /** Seed leaf — gets the COMMS POI and reads as the HQ / command post. */
        COMMAND,
        /** Largest non-seed leaf — residential floor, cot/locker doodads. */
        BARRACKS,
        /** Mid-sized — striped floor, crate doodads. */
        ARMORY,
        /** Smallest non-seed — dirt floor, vehicle decals (currently rendered as a yard with one parked vehicle). */
        VEHICLE_BAY
    }

    /** Top-level compound classification. Drives which filler claims this compound. */
    public final BlockKind kind;
    /** The originally-labeled seed leaf. Also appears in {@link #members}. */
    public final BlockLeaf seed;
    /** Seed leaf plus all BFS-claimed neighbors. Order: seed first, then claim order. */
    public final List<BlockLeaf> members;
    /** Per-leaf role assignment for sub-building flavoring. */
    public final Map<BlockLeaf, Role> roles;
    /** Biome the seed leaf sits in. Null on non-conquest maps (no BiomeMap). Used by {@code MilitaryBaseFiller} to emit biome-appropriate tactical node kinds. */
    public final BiomeKind biome;
    /** Union bounding rect (inclusive). Convenience for renderers / preview overlays — true membership is {@link #members}. */
    public final int left, top, right, bottom;

    public Compound(BlockKind kind, BlockLeaf seed, List<BlockLeaf> members,
                    Map<BlockLeaf, Role> roles, BiomeKind biome) {
        this.kind = kind;
        this.seed = seed;
        this.members = Collections.unmodifiableList(members);
        this.roles = Collections.unmodifiableMap(roles);
        this.biome = biome;

        int l = Integer.MAX_VALUE, t = Integer.MAX_VALUE;
        int r = Integer.MIN_VALUE, b = Integer.MIN_VALUE;
        for (BlockLeaf m : members) {
            if (m.left   < l) l = m.left;
            if (m.top    < t) t = m.top;
            if (m.right  > r) r = m.right;
            if (m.bottom > b) b = m.bottom;
        }
        this.left = l;
        this.top = t;
        this.right = r;
        this.bottom = b;
    }

    public int width()  { return right - left + 1; }
    public int height() { return bottom - top + 1; }
}
