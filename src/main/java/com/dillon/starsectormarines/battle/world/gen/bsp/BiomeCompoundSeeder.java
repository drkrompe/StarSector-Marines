package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.world.gen.BiomeKind;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Canonical seeding pass that reserves exactly one {@link BlockKind#MILITARY_BASE}
 * compound seed per target biome (PORT, CITY, FORTRESS). Runs after
 * {@code labelLeaves} and before {@link CompoundClaim#claim} in the
 * {@link BspCityGenerator} pipeline.
 *
 * <p>Natural theme rolls are deliberately not authoritative here. All natural
 * military-base seeds are first demoted, then the largest eligible leaf in
 * each target biome is reserved. This prevents an unrelated compound seed
 * from satisfying the band, prevents duplicate fortress keeps, and keeps the
 * three supply tiers deterministic. The existing {@link CompoundClaim} pass
 * then BFS-grows the three reserved bases as usual.
 *
 * <p>BEACH is excluded: no defender supply structures at the marine
 * landing zone.
 */
public final class BiomeCompoundSeeder {

    private static final Set<BiomeKind> TARGET_BIOMES = EnumSet.of(
            BiomeKind.PORT, BiomeKind.CITY, BiomeKind.FORTRESS_DISTRICT);

    private static final Set<BlockKind> INELIGIBLE_FOR_FORCE_SEED = EnumSet.of(
            BlockKind.COMPOUND_MEMBER, BlockKind.WATERFRONT, BlockKind.LANDING_ZONE,
            BlockKind.SPACEPORT_PAD, BlockKind.NATURE_WETLAND, BlockKind.NATURE_BEACH);

    private static final int MIN_SEED_DIM = 6;

    private BiomeCompoundSeeder() {}

    /**
     * Reserve exactly one military-base seed per target biome when each biome
     * has an eligible leaf. Mutates {@code leaf.kind} in place. The production
     * generator validates the resulting keep after all stages and fails closed
     * if a malformed partition offered no fortress candidate.
     *
     * @return the number of target-biome seeds reserved
     */
    public static int seed(List<BlockLeaf> leaves, BiomeMap biomeMap) {
        if (biomeMap == null) return 0;

        // Theme rolls may place several bases in one band (or in BEACH / OUTSKIRTS).
        // Reset them all before choosing the three mission-authored supply anchors.
        for (BlockLeaf leaf : leaves) {
            if (leaf.kind == BlockKind.MILITARY_BASE) {
                leaf.kind = BlockKind.FORTIFIED_POST;
            }
        }

        int reserved = 0;
        for (BiomeKind biome : TARGET_BIOMES) {
            BlockLeaf best = largestEligible(leaves, biomeMap, biome);
            if (best == null) continue;
            best.kind = BlockKind.MILITARY_BASE;
            reserved++;
        }
        return reserved;
    }

    private static BlockLeaf largestEligible(List<BlockLeaf> leaves,
                                              BiomeMap biomeMap, BiomeKind biome) {
        BlockLeaf best = null;
        int bestArea = -1;
        for (BlockLeaf leaf : leaves) {
            if (biomeMap.biomeAt(leaf.centerX(), leaf.centerY()) != biome) continue;
            if (INELIGIBLE_FOR_FORCE_SEED.contains(leaf.kind)) continue;
            if (leaf.width() < MIN_SEED_DIM || leaf.height() < MIN_SEED_DIM) continue;
            if (leaf.area() > bestArea) {
                bestArea = leaf.area();
                best = leaf;
            }
        }
        return best;
    }
}
