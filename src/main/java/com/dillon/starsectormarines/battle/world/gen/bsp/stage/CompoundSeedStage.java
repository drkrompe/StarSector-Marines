package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.BiomeCompoundSeeder;
import com.dillon.starsectormarines.battle.world.gen.bsp.BiomeMap;
import com.dillon.starsectormarines.battle.world.gen.bsp.Bsp;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import org.apache.log4j.Logger;

/**
 * Step 2a — canonical compound seeding across biomes. Reserves exactly one
 * MILITARY_BASE seed per target biome (PORT, CITY, FORTRESS) so compounds
 * spread along the traversal axis instead of clustering in the fortress
 * district. Natural MILITARY_BASE rolls are normalized away first so only the
 * three mission-authored seeds reach the claim pass.
 * Conquest-only — a no-op when {@link BspKeys#BIOME_MAP} is unbound.
 */
public final class CompoundSeedStage implements GenStage {

    private static final Logger LOG = Logger.getLogger(CompoundSeedStage.class);

    @Override
    public void run(GenContext ctx) {
        Bsp.Partition partition = ctx.get(BspKeys.PARTITION);
        BiomeMap biomeMap = ctx.get(BspKeys.BIOME_MAP);
        int reservedSeeds = BiomeCompoundSeeder.seed(partition.leaves, biomeMap);
        if (reservedSeeds > 0) {
            LOG.debug("BspCityGenerator: reserved " + reservedSeeds
                    + " military-base compound seed(s) across biomes");
        }
    }
}
