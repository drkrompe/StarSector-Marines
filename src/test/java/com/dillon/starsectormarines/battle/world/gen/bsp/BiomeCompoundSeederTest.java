package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.world.gen.BiomeKind;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for {@link BiomeCompoundSeeder} — the forced seeding pass that
 * guarantees compound spread across PORT / CITY / FORTRESS biomes.
 */
public class BiomeCompoundSeederTest {

    private static final int W = 80;
    private static final int H = 60;

    private static BiomeMap makeBiomeMap() {
        return new BiomeMap(W, H, TraversalAxis.SOUTH_TO_NORTH, new Random(42));
    }

    /**
     * Find a leaf placement where the leaf's {@code centerX/centerY} (as
     * computed by {@link BlockLeaf}) falls in the target biome. Matches
     * the check {@link BiomeCompoundSeeder} uses.
     */
    private static BlockLeaf leafInBiome(BiomeMap biome, BiomeKind target, int size) {
        for (int y = 0; y < H - size; y++) {
            for (int x = 0; x < W - size; x++) {
                BlockLeaf candidate = new BlockLeaf(x, y, x + size - 1, y + size - 1, false);
                if (biome.biomeAt(candidate.centerX(), candidate.centerY()) == target) {
                    return candidate;
                }
            }
        }
        return null;
    }

    @Test
    public void seedsOneCompoundPerTargetBiome() {
        BiomeMap biome = makeBiomeMap();
        List<BlockLeaf> leaves = new ArrayList<>();

        // Place one large BUILDING_RESIDENTIAL leaf in each biome.
        for (BiomeKind bk : new BiomeKind[]{BiomeKind.PORT, BiomeKind.CITY, BiomeKind.FORTRESS_DISTRICT}) {
            BlockLeaf leaf = leafInBiome(biome, bk, 8);
            if (leaf != null) {
                leaf.kind = BlockKind.BUILDING_RESIDENTIAL;
                leaves.add(leaf);
            }
        }
        assertTrue(leaves.size() >= 3, "need at least one leaf per target biome");

        int forced = BiomeCompoundSeeder.seed(leaves, biome);

        assertEquals(3, forced, "should force-seed one compound per target biome");
        for (BlockLeaf leaf : leaves) {
            assertEquals(BlockKind.MILITARY_BASE, leaf.kind,
                    "all target-biome leaves should be force-labeled MILITARY_BASE");
        }
    }

    @Test
    public void skipsBeachBiome() {
        BiomeMap biome = makeBiomeMap();
        List<BlockLeaf> leaves = new ArrayList<>();
        BlockLeaf beachLeaf = leafInBiome(biome, BiomeKind.BEACH, 8);
        if (beachLeaf != null) {
            beachLeaf.kind = BlockKind.BUILDING_RESIDENTIAL;
            leaves.add(beachLeaf);
        }

        int forced = BiomeCompoundSeeder.seed(leaves, biome);

        assertEquals(0, forced, "BEACH should not get a forced compound seed");
        if (beachLeaf != null) {
            assertEquals(BlockKind.BUILDING_RESIDENTIAL, beachLeaf.kind,
                    "beach leaf should remain unchanged");
        }
    }

    @Test
    public void skipsBiomeWithExistingCompoundSeed() {
        BiomeMap biome = makeBiomeMap();
        List<BlockLeaf> leaves = new ArrayList<>();

        // Place a natural MILITARY_BASE seed in FORTRESS.
        BlockLeaf fortressLeaf = leafInBiome(biome, BiomeKind.FORTRESS_DISTRICT, 8);
        if (fortressLeaf != null) {
            fortressLeaf.kind = BlockKind.MILITARY_BASE;
            leaves.add(fortressLeaf);
        }

        // Place a residential leaf in PORT and CITY for forcing.
        for (BiomeKind bk : new BiomeKind[]{BiomeKind.PORT, BiomeKind.CITY}) {
            BlockLeaf leaf = leafInBiome(biome, bk, 8);
            if (leaf != null) {
                leaf.kind = BlockKind.BUILDING_RESIDENTIAL;
                leaves.add(leaf);
            }
        }

        int forced = BiomeCompoundSeeder.seed(leaves, biome);

        assertEquals(2, forced,
                "should only force-seed PORT + CITY; FORTRESS already has a natural seed");
    }

    @Test
    public void skipsLeafsTooSmall() {
        BiomeMap biome = makeBiomeMap();
        List<BlockLeaf> leaves = new ArrayList<>();

        // Place a tiny leaf (3x3) in PORT — below MIN_SEED_DIM.
        BlockLeaf smallPortLeaf = leafInBiome(biome, BiomeKind.PORT, 3);
        if (smallPortLeaf != null) {
            smallPortLeaf.kind = BlockKind.BUILDING_RESIDENTIAL;
            leaves.add(smallPortLeaf);
        }

        int forced = BiomeCompoundSeeder.seed(leaves, biome);

        if (smallPortLeaf != null) {
            assertEquals(BlockKind.BUILDING_RESIDENTIAL, smallPortLeaf.kind,
                    "undersized leaf should not be force-seeded");
        }
    }
}
