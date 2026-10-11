package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.tiles.GridBlockDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

/** Material pairing for damage overlays, shared by streamed and resident ground rendering. */
final class GroundDamageTiles {
    private GroundDamageTiles() {}

    static GridBlockDef skin(TileRegistry registry, CellTopology topology, int x, int y) {
        if (registry == null || topology.isWall(x, y) || topology.isRubble(x, y)
                || !topology.hasTag(x, y, CellTopology.Tag.GROUND_DAMAGED)) return null;
        String id = topology.getSurfaceId(x, y);
        GridBlockDef material = id == null ? null : registry.block(id);
        return material == null || material.damageBlockId == null ? null : registry.block(material.damageBlockId);
    }
}
