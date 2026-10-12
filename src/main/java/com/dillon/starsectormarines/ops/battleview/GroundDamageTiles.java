package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.tiles.GridBlockDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

/** Material pairing for damage overlays, shared by streamed and resident ground rendering. */
final class GroundDamageTiles {
    private GroundDamageTiles() {}

    static GridBlockDef skin(TileRegistry registry, CellTopology topology, int x, int y) {
        if (registry == null || topology.isWall(x, y) || topology.isRubble(x, y)
                || !topology.hasTag(x, y, CellTopology.Tag.GROUND_DAMAGED)) return null;
        var kind = topology.getGroundKind(x, y);
        if (kind == CellTopology.GroundKind.VOID || kind == CellTopology.GroundKind.WATER) return null;
        String id = topology.getSurfaceId(x, y);
        GridBlockDef material = id == null ? null : registry.block(id);
        if (material != null && material.damageBlockId != null) return registry.block(material.damageBlockId);
        GenMappingRegistry mapping = GenMappingRegistry.installed();
        if (mapping == null) return null;
        // A cell without an explicit surface still has a mapped base material.
        material = registry.block(mapping.groundBlockId(kind));
        if (material != null && material.damageBlockId != null) return registry.block(material.damageBlockId);
        return registry.block(mapping.groundDamageBlockId(kind));
    }
}
