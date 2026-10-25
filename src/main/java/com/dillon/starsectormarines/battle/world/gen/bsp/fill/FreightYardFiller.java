package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.gen.BlockFiller;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;

/**
 * Fills a {@link BlockKind#FREIGHT_YARD} parcel with the largest freight yard it holds
 * ({@code set-piece-blocks.md}), its gates onto the street round the parcel. What the yard leaves of
 * the parcel is open working ground. A parcel whose streets give no gate a hauler can turn out of
 * is an ordinary industrial yard: a smaller thing, never a hole.
 */
public final class FreightYardFiller implements BlockFiller {

    private final IndustrialYardFiller fallback = new IndustrialYardFiller();

    @Override
    public BlockKind kind() { return BlockKind.FREIGHT_YARD; }

    @Override
    public void fill(BlockLeaf leaf, GenContext ctx) {
        FreightYard.Pose pose = FreightYard.choose(ctx, leaf.left, leaf.top, leaf.right, leaf.bottom, leaf::contains);
        if (pose == null) {
            fallback.fill(leaf, ctx);
            return;
        }
        IndustrialYardFiller.paintOpenDirt(leaf, ctx.grid, ctx.topology);
        FreightYard.place(ctx, pose);
    }
}
