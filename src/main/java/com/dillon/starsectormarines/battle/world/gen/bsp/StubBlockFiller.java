package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.gen.BlockFiller;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.List;
import java.util.Random;

/**
 * Default {@link BlockFiller} used until a per-kind implementation drops in.
 * Paints every cell of the leaf as {@link GroundKind#INDOOR} so the BSP
 * segmentation is visible against the surrounding {@link GroundKind#STREET}
 * road frame, but emits no POIs / doodads and carves no walls.
 *
 * <p>The orchestrator registers this filler for every {@link BlockKind} at
 * startup. As real per-kind fillers land they replace the entry in the
 * filler map; until then those kinds render as plain beige floor — visually
 * distinct from the gray road, so you can still see the partition shape.
 */
public final class StubBlockFiller implements BlockFiller {

    private final BlockKind kind;

    public StubBlockFiller(BlockKind kind) {
        this.kind = kind;
    }

    @Override
    public BlockKind kind() { return kind; }

    @Override
    public void fill(BlockLeaf leaf, GenContext ctx) {
        NavigationGrid grid = ctx.grid;
        CellTopology topology = ctx.topology;
        List<PointOfInterest> pois = ctx.pois;
        List<Doodad> doodads = ctx.doodads;
        Random rng = ctx.rng;
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                topology.setGroundKind(x, y, GroundKind.INDOOR);
            }
        }
    }
}
