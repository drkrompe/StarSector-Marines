package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.gen.BlockFiller;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.Bsp;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

/**
 * Fills the planned {@link BlockKind#MARKET_SQUARE} lot with the market square set piece
 * ({@code set-piece-blocks.md}). The square may reach onto the kerb of the street round the lot; what it leaves
 * of the lot is more paving. A lot the square cannot stand in is an ordinary plaza.
 */
public final class MarketSquareFiller implements BlockFiller {

    private final PlazaFiller fallback = new PlazaFiller();

    @Override public BlockKind kind() { return BlockKind.MARKET_SQUARE; }

    @Override
    public void fill(BlockLeaf leaf, GenContext ctx) {
        Bsp.Partition partition = ctx.get(BspKeys.PARTITION);
        boolean[][] leafCells = new boolean[ctx.grid.getWidth()][ctx.grid.getHeight()];
        if (partition != null) {
            for (BlockLeaf other : partition.leaves) {
                for (int y = other.top; y <= other.bottom; y++) {
                    for (int x = other.left; x <= other.right; x++) leafCells[x][y] = true;
                }
            }
        }
        MarketSquare.Placement market = MarketSquare.choose(ctx, leaf.left, leaf.top, leaf.right, leaf.bottom,
                (x, y) -> leaf.contains(x, y) || !leafCells[x][y], leaf::contains,
                MarketSquare.preference(leaf.left + leaf.top));
        if (market == null) {
            fallback.fill(leaf, ctx);
            return;
        }
        boolean[][] footprint = MarketSquare.footprint(market, ctx.grid.getWidth(), ctx.grid.getHeight());
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                if (footprint[x][y]) continue;
                ctx.grid.setWalkableFloor(x, y);
                ctx.topology.setGroundKind(x, y, GroundKind.BRICK);
            }
        }
        MarketSquare.place(ctx, market);
    }
}
