package com.dillon.starsectormarines.battle.weapon.fx;

import java.awt.Color;

/**
 * Fully resolved, backend-neutral particle spawn. A runtime or preview backend
 * maps {@link #kind} to its sprite/flipbook and honors {@link #delaySeconds}.
 */
public record FxParticleCommand(
        FxLayerKind kind,
        float delaySeconds,
        float x,
        float y,
        float velocityX,
        float velocityY,
        float radiusCells,
        float radiusGrowthPerSecond,
        float lifetimeSeconds,
        Color color,
        FxBlend blend,
        float angleDegrees,
        int variantIndex) {
}
