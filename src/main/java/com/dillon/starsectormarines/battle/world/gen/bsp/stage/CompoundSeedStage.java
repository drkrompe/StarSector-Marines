package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.BiomeCompoundSeeder;
import com.dillon.starsectormarines.battle.world.gen.bsp.BiomeMap;
import com.dillon.starsectormarines.battle.world.gen.bsp.Bsp;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import org.apache.log4j.Logger;

/**
 * Step 2a — forced compound seeding across biomes. Guarantees one
 * MILITARY_BASE seed per target biome (PORT, CITY, FORTRESS) so compounds
 * spread along the traversal axis instead of clustering in the fortress
 * district. Skips biomes that already have a seed from the natural theme roll.
 * Conquest-only — a no-op when {@link BspKeys#BIOME_MAP} is unbound.
 */
public final class CompoundSeedStage implements GenStage {

    private static final Logger LOG = Logger.getLogger(CompoundSeedStage.class);

    @Override
    public void run(GenContext ctx) {
        Bsp.Partition partition = ctx.get(BspKeys.PARTITION);
        BiomeMap biomeMap = ctx.get(BspKeys.BIOME_MAP);
        int forcedSeeds = BiomeCompoundSeeder.seed(partition.leaves, biomeMap);
        if (forcedSeeds > 0) {
            LOG.info("BspCityGenerator: force-seeded " + forcedSeeds + " compound(s) across biomes");
        }
    }
}
