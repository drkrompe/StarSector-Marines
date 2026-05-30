package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

/**
 * Step 0 — initialize every cell as walkable {@link GroundKind#STREET}. Fillers
 * overwrite both the ground kind and walkability inside their leaves; the road
 * frame keeps these defaults.
 */
public final class InitFloorStage implements GenStage {

    @Override
    public void run(GenContext ctx) {
        for (int y = 0; y < ctx.height; y++) {
            for (int x = 0; x < ctx.width; x++) {
                ctx.grid.setWalkableFloor(x, y);
                ctx.topology.setGroundKind(x, y, GroundKind.STREET);
            }
        }
    }
}
