package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredMechAppearance;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;

import java.awt.Color;
import java.util.function.Supplier;

/** Static, weapon-free silhouette of one fabricable chassis pattern. */
public final class MechChassisPreviewCanvas implements CanvasProducer {

    private static final float LARGEST_RENDER_SCALE = 1.60f;
    private static final Color BACKGROUND = new Color(0x08, 0x12, 0x1B);
    private static final Color PAD = new Color(0x0D, 0x20, 0x2C);
    private static final Color GUIDE = new Color(0x25, 0x4A, 0x5D, 150);

    private final MechVariant variant;
    private final Supplier<LayeredMechAssets> assets;

    public MechChassisPreviewCanvas(MechVariant variant,
                                    Supplier<LayeredMechAssets> assets) {
        if (variant == null || assets == null) {
            throw new IllegalArgumentException("variant and mech assets are required");
        }
        this.variant = variant;
        this.assets = assets;
    }

    @Override
    public void draw(CanvasContext context) {
        LayeredMechAssets sprites = assets.get();
        if (sprites == null) return;

        float width = context.metrics().surfaceWidth();
        float height = context.metrics().surfaceHeight();
        float inset = Math.max(5f, Math.min(width, height) * 0.055f);
        context.fillRect(0f, 0f, width, height, BACKGROUND);
        context.fillRect(inset, inset, width - inset * 2f, height - inset * 2f, PAD);
        context.line(width * 0.5f, inset, width * 0.5f, height - inset,
                GUIDE, Math.max(1f, width * 0.006f));
        context.line(inset, height * 0.5f, width - inset, height * 0.5f,
                GUIDE, Math.max(1f, width * 0.006f));

        float largestHull = Math.min(width * 0.78f, height * 0.68f);
        float hullWidth = largestHull * variant.renderScale / LARGEST_RENDER_SCALE;
        LayeredMechComposer.emit(new CanvasSink(context, height), sprites,
                width * 0.5f, height * 0.5f, hullWidth,
                0f, 0f, 0f, 0f, 0f, 0f, 0,
                variant.chassisAppearance,
                LayeredMechAppearance.ARMS_NONE,
                LayeredMechAppearance.POD_NONE,
                LayeredMechAppearance.POD_NONE,
                1f);
    }

    private record CanvasSink(CanvasContext context, float surfaceHeight)
            implements LayeredMechComposer.Sink {
        @Override
        public void sprite(LayeredSpriteCache sprite, float centerX, float centerY,
                           float width, float height, float angleDegrees, float alpha) {
            context.sprite(sprite.sourcePath, sprite.sprite,
                    centerX, surfaceHeight - centerY, width, height,
                    angleDegrees, new Color(1f, 1f, 1f, alpha));
        }
    }
}
