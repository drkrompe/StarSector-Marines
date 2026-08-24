package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.marine.FireTeamBillet;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;

import java.awt.Color;
import java.util.List;
import java.util.function.Supplier;

/** Compact four-soldier formation preview used by Armory template selectors. */
public final class ArmoryFireTeamPreviewCanvas implements CanvasProducer {

    private final Supplier<List<FireTeamBillet>> billets;
    private final ArmoryLoadoutPreviewComposer.Assets assets;

    public ArmoryFireTeamPreviewCanvas(
            Supplier<List<FireTeamBillet>> billets,
            ArmoryLoadoutPreviewComposer.Assets assets) {
        if (billets == null) throw new IllegalArgumentException("billets are required");
        if (assets == null) throw new IllegalArgumentException("preview assets are required");
        this.billets = billets;
        this.assets = assets;
    }

    @Override
    public void draw(CanvasContext context) {
        ArmoryLoadoutPreviewComposer.composeFireTeam(
                new CanvasSink(context), assets, billets.get(),
                context.metrics().surfaceWidth(), context.metrics().surfaceHeight());
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
