package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.Bsp;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

/**
 * Carve one room per BSP leaf out of the solid hull. Each leaf's full inclusive
 * rect is flagged walkable {@link GroundKind#INDOOR}; the road strips BSP
 * reserved between leaves are left solid, so adjacent rooms are separated by a
 * 3–5-cell hull wall until {@link CorridorStage} punches a passage through it.
 *
 * <p>Leaf = room for this first slice (no inset, no subdivision) — a deliberate
 * v1 simplification; varied room sizing / sub-rooms is a later lever. Reads
 * {@link BspKeys#PARTITION}.
 */
public final class RoomCarveStage implements GenStage {

    @Override
    public void run(GenContext ctx) {
        Bsp.Partition partition = ctx.get(BspKeys.PARTITION);
        for (BlockLeaf leaf : partition.leaves) {
            for (int y = leaf.top; y <= leaf.bottom; y++) {
                for (int x = leaf.left; x <= leaf.right; x++) {
                    ctx.grid.setWalkableFloor(x, y);
                    ctx.topology.setGroundKind(x, y, GroundKind.INDOOR);
                }
            }
        }
    }
}
