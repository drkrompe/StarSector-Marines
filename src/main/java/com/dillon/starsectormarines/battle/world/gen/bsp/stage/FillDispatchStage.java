package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.BlockFiller;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.bsp.Bsp;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.Compound;
import com.dillon.starsectormarines.battle.world.gen.bsp.CompoundFiller;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Step 3 — dispatch fillers. Each per-leaf filler owns its leaf's cells (NOT
 * the road frame around it); compound fillers own the union of their member
 * leaves plus the bridged road between them. Output accumulators live on the
 * context ({@code ctx.pois} / {@code ctx.doodads} / {@code ctx.tactical} /
 * {@code ctx.defensePosts}); fillers append to them via {@code ctx}.
 *
 * <p>Holds the {@link BlockKind}-keyed filler registries by reference — the
 * generator owns the maps and may swap entries after construction, so this
 * stage reads them live at dispatch time. Reads {@link BspKeys#PARTITION} and
 * {@link BspKeys#COMPOUNDS}.
 */
public final class FillDispatchStage implements GenStage {

    private final Map<BlockKind, BlockFiller> fillers;
    private final Map<BlockKind, CompoundFiller> compoundFillers;

    public FillDispatchStage(Map<BlockKind, BlockFiller> fillers,
                             Map<BlockKind, CompoundFiller> compoundFillers) {
        this.fillers = fillers;
        this.compoundFillers = compoundFillers;
    }

    @Override
    public void run(GenContext ctx) {
        Bsp.Partition partition = ctx.get(BspKeys.PARTITION);
        List<Compound> compounds = ctx.get(BspKeys.COMPOUNDS);
        Map<BlockLeaf, Compound> compoundBySeed = new IdentityHashMap<>();
        for (Compound c : compounds) compoundBySeed.put(c.seed, c);

        for (BlockLeaf leaf : partition.leaves) {
            if (leaf.kind == BlockKind.COMPOUND_MEMBER) continue;
            Compound compound = compoundBySeed.get(leaf);
            if (compound != null) {
                CompoundFiller cFiller = compoundFillers.get(compound.kind);
                if (cFiller != null) {
                    cFiller.fill(compound, ctx);
                }
                continue;
            }
            BlockFiller filler = fillers.get(leaf.kind);
            filler.fill(leaf, ctx);
        }
    }
}
