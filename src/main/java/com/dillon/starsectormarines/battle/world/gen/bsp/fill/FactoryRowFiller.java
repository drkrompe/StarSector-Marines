package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.Bsp;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.Compound;
import com.dillon.starsectormarines.battle.world.gen.bsp.CompoundFiller;

/**
 * The factory row compound ({@code set-piece-blocks.md}): the {@link FactoryRow} where the claimed ground
 * holds it, standing on the members and the street frames round them. A member the row covers keeps what
 * the row leaves of it as open working ground; a member it misses, and every member of a compound that
 * cannot hold the row, is an ordinary industrial lot.
 */
public final class FactoryRowFiller implements CompoundFiller {

    private static final BuildingIndustrialFiller LOT = new BuildingIndustrialFiller();

    @Override
    public BlockKind kind() {
        return BlockKind.FACTORY_ROW;
    }

    @Override
    public void fill(Compound compound, GenContext ctx) {
        FactoryRow.Pose pose = choose(compound, ctx);
        for (BlockLeaf member : compound.members) {
            if (pose != null && covers(pose, member)) IndustrialYardFiller.paintOpenDirt(member, ctx.grid, ctx.topology);
            else LOT.fill(member, ctx);
        }
        if (pose != null) FactoryRow.place(ctx, pose);
    }

    private static FactoryRow.Pose choose(Compound compound, GenContext ctx) {
        Bsp.Partition partition = ctx.get(BspKeys.PARTITION);
        if (partition == null) return null;
        int w = ctx.grid.getWidth(), h = ctx.grid.getHeight();
        boolean[][] memberCells = new boolean[w][h];
        boolean[][] leafCells = new boolean[w][h];
        for (BlockLeaf leaf : partition.leaves) {
            boolean member = compound.members.contains(leaf);
            for (int y = leaf.top; y <= leaf.bottom; y++) {
                for (int x = leaf.left; x <= leaf.right; x++) {
                    leafCells[x][y] = true;
                    if (member) memberCells[x][y] = true;
                }
            }
        }
        return FactoryRow.choose(ctx, compound, (x, y) -> memberCells[x][y] || !leafCells[x][y]);
    }

    private static boolean covers(FactoryRow.Pose pose, BlockLeaf leaf) {
        return pose.left() <= leaf.right && pose.left() + pose.width() - 1 >= leaf.left
                && pose.bottom() <= leaf.bottom && pose.bottom() + pose.height() - 1 >= leaf.top;
    }
}
