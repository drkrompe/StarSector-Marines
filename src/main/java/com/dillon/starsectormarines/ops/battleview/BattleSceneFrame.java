package com.dillon.starsectormarines.ops.battleview;

import java.util.EnumSet;

/** One prepared embedded-simulation frame shared by live and headless drains. */
public record BattleSceneFrame(RenderContext context, EnumSet<RenderLayer> layers) {

    public BattleSceneFrame {
        if (context == null || layers == null) {
            throw new IllegalArgumentException("context and layers are required");
        }
        layers = EnumSet.copyOf(layers);
    }

    @Override
    public EnumSet<RenderLayer> layers() {
        return EnumSet.copyOf(layers);
    }
}
