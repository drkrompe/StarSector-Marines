package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.marine.FireTeamBillet;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;

import java.awt.Color;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/** Soldier-first portrait used by the Fleet Armory's named marine viewer. */
public final class ArmoryMarinePreviewCanvas implements CanvasProducer {

    private final Supplier<FireTeamBillet> billet;
    private final ArmoryLoadoutPreviewComposer.Assets assets;
    private final DoubleSupplier idleSeconds;

    public ArmoryMarinePreviewCanvas(
            Supplier<FireTeamBillet> billet,
            ArmoryLoadoutPreviewComposer.Assets assets) {
        this(billet, assets, () -> 0d);
    }

    /** Uses a live time source to cycle the selected marine's authored idle clip. */
    public ArmoryMarinePreviewCanvas(
            Supplier<FireTeamBillet> billet,
            ArmoryLoadoutPreviewComposer.Assets assets,
            DoubleSupplier idleSeconds) {
        if (billet == null) throw new IllegalArgumentException("billet is required");
        if (assets == null) throw new IllegalArgumentException("preview assets are required");
        if (idleSeconds == null) throw new IllegalArgumentException("idle time is required");
        this.billet = billet;
        this.assets = assets;
        this.idleSeconds = idleSeconds;
    }

    @Override
    public void draw(CanvasContext context) {
        ArmoryLoadoutPreviewComposer.composeMarinePortrait(
                new CanvasSink(context), assets, billet.get(),
                context.metrics().surfaceWidth(), context.metrics().surfaceHeight(),
                (float) idleSeconds.getAsDouble());
    }

    private record CanvasSink(CanvasContext context)
            implements ArmoryLoadoutPreviewComposer.Sink {
        @Override
        public void fillRect(float x, float y, float width, float height, Color color) {
            context.fillRect(x, y, width, height, color);
        }

        @Override
        public void strokeRect(float x, float y, float width, float height,
                               Color color, float strokeWidth) {
            context.strokeRect(x, y, width, height, color, strokeWidth);
        }

        @Override
        public void line(float x1, float y1, float x2, float y2,
                         Color color, float strokeWidth) {
            context.line(x1, y1, x2, y2, color, strokeWidth);
        }

        @Override
        public void sprite(LayeredSpriteCache sprite, float centerX, float centerY,
                           float width, float height, float angleDegrees, Color tint) {
            context.sprite(sprite.sourcePath, sprite.sprite, centerX, centerY,
                    width, height, angleDegrees, tint);
        }
    }
}
