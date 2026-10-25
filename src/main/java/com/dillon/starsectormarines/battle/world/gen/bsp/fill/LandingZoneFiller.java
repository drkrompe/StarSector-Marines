package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.gen.BlockFiller;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

/**
 * Filler for {@link BlockKind#LANDING_ZONE} leaves. Open touchdown pad: no
 * perimeter wall, no interior carving — the whole leaf is walkable marked
 * apron, outlined where it meets the city. The touchdown cell at the center
 * is apron like the rest: a landing zone needs no tile of its own.
 *
 * <p>Reads as a deliberate clearing inside the city: the marked pad signals
 * "shuttle apron" against the surrounding gray road. The touchdown cell is
 * published as a {@link LandingPad} rather than marked on the ground.
 *
 * <p>No POI emitted — landing zones aren't landmark buildings, they're a
 * spatial role consumed by spawn-anchor logic instead.
 */
public final class LandingZoneFiller implements BlockFiller {

    @Override
    public BlockKind kind() { return BlockKind.LANDING_ZONE; }

    @Override
    public void fill(BlockLeaf leaf, GenContext ctx) {
        NavigationGrid grid = ctx.grid;
        CellTopology topology = ctx.topology;
        // Whole leaf is a walkable apron. Walkability was set true by the
        // orchestrator initial pass; we leave it that way (no perimeter wall)
        // and overwrite the ground kind so the renderer paints marked apron
        // instead of road.
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                topology.setGroundKind(x, y, GroundKind.STRIPED);
            }
        }

        // Center cell: the touchdown berth, published when the leaf fits it.
        int cx = leaf.centerX();
        int cy = leaf.centerY();
        if (grid.inBounds(cx, cy)) {
            if (leaf.width() >= 5 && leaf.height() >= 5) {
                ctx.landingPads.add(LandingPad.civilian(cx, cy, LandingPad.Approach.NORTH));
            }
        }
    }
}
