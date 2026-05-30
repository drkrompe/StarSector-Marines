package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.BiomeMap;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.bsp.Bsp;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.Compound;
import com.dillon.starsectormarines.battle.world.gen.bsp.CompoundClaim;
import com.dillon.starsectormarines.battle.world.gen.bsp.LeafAdjacency;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.Map;

/**
 * Step 2b — claim multi-leaf compounds (e.g. military bases). Each compound's
 * seed leaf keeps its {@code BlockKind}; absorbed neighbor leaves are rewritten
 * to {@code COMPOUND_MEMBER} so per-leaf dispatch skips them. The conquest spec
 * set is used when {@link BspKeys#BIOME_MAP} is bound, the default set
 * otherwise.
 *
 * <p>Reads {@link BspKeys#PARTITION} / {@link BspKeys#BIOME_MAP}; binds the
 * claimed-compound list under {@link BspKeys#COMPOUNDS}.
 */
public final class CompoundClaimStage implements GenStage {

    private static final Logger LOG = Logger.getLogger(CompoundClaimStage.class);

    @Override
    public void run(GenContext ctx) {
        Bsp.Partition partition = ctx.get(BspKeys.PARTITION);
        BiomeMap biomeMap = ctx.get(BspKeys.BIOME_MAP);
        Map<BlockLeaf, List<BlockLeaf>> adjacency =
                LeafAdjacency.compute(partition.leaves, ctx.width, ctx.height);
        List<Compound> compounds = CompoundClaim.claim(partition.leaves, adjacency,
                biomeMap != null ? CompoundClaim.CONQUEST_SPECS : CompoundClaim.DEFAULT_SPECS,
                biomeMap, ctx.rng);
        ctx.put(BspKeys.COMPOUNDS, compounds);
        if (!compounds.isEmpty()) {
            LOG.info("BspCityGenerator: " + compounds.size() + " compound(s) claimed");
        }
    }
}
