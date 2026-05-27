package com.dillon.starsectormarines.battle.mapgen.bsp;

import com.dillon.starsectormarines.battle.mapgen.BiomeKind;
import com.dillon.starsectormarines.battle.mapgen.BlockKind;
import com.dillon.starsectormarines.battle.mapgen.BlockLeaf;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Forced seeding pass that guarantees one {@link BlockKind#MILITARY_BASE}
 * compound seed per target biome (PORT, CITY, FORTRESS). Runs after
 * {@code labelLeaves} and before {@link CompoundClaim#claim} in the
 * {@link BspCityGenerator} pipeline.
 *
 * <p>If the natural theme roll already placed a compound seed in a biome,
 * that seed wins — the seeder skips the biome. Otherwise the seeder picks
 * the largest eligible leaf in the biome and force-labels it
 * {@code MILITARY_BASE}. The existing {@link CompoundClaim} pass then
 * BFS-grows compounds from all seeds as usual.
 *
 * <p>BEACH is excluded: no defender supply structures at the marine
 * landing zone.
 */
public final class BiomeCompoundSeeder {

    private static final Set<BiomeKind> TARGET_BIOMES = EnumSet.of(
            BiomeKind.PORT, BiomeKind.CITY, BiomeKind.FORTRESS_DISTRICT);

    private static final Set<BlockKind> EXISTING_COMPOUND_SEEDS = EnumSet.of(
            BlockKind.MILITARY_BASE, BlockKind.GATED_HOUSING, BlockKind.DENSE_QUARTER);

    private static final Set<BlockKind> INELIGIBLE_FOR_FORCE_SEED = EnumSet.of(
            BlockKind.COMPOUND_MEMBER, BlockKind.WATERFRONT, BlockKind.LANDING_ZONE,
            BlockKind.NATURE_WETLAND, BlockKind.NATURE_BEACH);

    private static final int MIN_SEED_DIM = 6;

    private BiomeCompoundSeeder() {}

    /**
     * Ensure at least one compound seed per target biome. Mutates
     * {@code leaf.kind} in-place for force-seeded leaves.
     *
     * @return the number of leaves force-seeded (0 if natural rolls
     *         already covered every target biome)
     */
    public static int seed(List<BlockLeaf> leaves, BiomeMap biomeMap) {
        if (biomeMap == null) return 0;
        int forced = 0;
        for (BiomeKind biome : TARGET_BIOMES) {
            if (hasCompoundSeedInBiome(leaves, biomeMap, biome)) continue;
            BlockLeaf best = largestEligible(leaves, biomeMap, biome);
            if (best == null) continue;
            best.kind = BlockKind.MILITARY_BASE;
            forced++;
        }
        return forced;
    }

    private static boolean hasCompoundSeedInBiome(List<BlockLeaf> leaves,
                                                   BiomeMap biomeMap, BiomeKind biome) {
        for (BlockLeaf leaf : leaves) {
            if (!EXISTING_COMPOUND_SEEDS.contains(leaf.kind)) continue;
            if (biomeMap.biomeAt(leaf.centerX(), leaf.centerY()) == biome) return true;
        }
        return false;
    }

    private static BlockLeaf largestEligible(List<BlockLeaf> leaves,
                                              BiomeMap biomeMap, BiomeKind biome) {
        BlockLeaf best = null;
        int bestArea = -1;
        for (BlockLeaf leaf : leaves) {
            if (biomeMap.biomeAt(leaf.centerX(), leaf.centerY()) != biome) continue;
            if (INELIGIBLE_FOR_FORCE_SEED.contains(leaf.kind)) continue;
            if (EXISTING_COMPOUND_SEEDS.contains(leaf.kind)) continue;
            if (leaf.width() < MIN_SEED_DIM || leaf.height() < MIN_SEED_DIM) continue;
            if (leaf.area() > bestArea) {
                bestArea = leaf.area();
                best = leaf;
            }
        }
        return best;
    }
}
