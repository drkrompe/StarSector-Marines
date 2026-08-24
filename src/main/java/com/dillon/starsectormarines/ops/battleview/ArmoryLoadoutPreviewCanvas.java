package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.marine.FireTeamBillet;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;

import java.awt.Color;
import java.util.function.Supplier;

/**
 * Immersive Fleet Armory preview: equipment sockets beside the same layered
 * actor composition used by battlefield infantry.
 */
public final class ArmoryLoadoutPreviewCanvas implements CanvasProducer {

    private final Supplier<FireTeamBillet> selectedBillet;
    private final ArmoryLoadoutPreviewComposer.Assets assets;
    private final boolean mannequin;

    public ArmoryLoadoutPreviewCanvas(Supplier<FireTeamBillet> selectedBillet) {
        this(selectedBillet, new ArmoryPreviewAssets(), false);
    }

    /** Uses the portrait mannequin composition for one billet in a four-unit template view. */
    public ArmoryLoadoutPreviewCanvas(
            Supplier<FireTeamBillet> selectedBillet, boolean mannequin) {
        this(selectedBillet, new ArmoryPreviewAssets(), mannequin);
    }

    /** Uses caller-supplied assets for a non-Starsector paint backend. */
    public ArmoryLoadoutPreviewCanvas(
            Supplier<FireTeamBillet> selectedBillet,
            ArmoryLoadoutPreviewComposer.Assets injectedAssets) {
        this(selectedBillet, injectedAssets, false);
    }

    /** Uses caller-supplied assets and the requested composition shape. */
    public ArmoryLoadoutPreviewCanvas(
            Supplier<FireTeamBillet> selectedBillet,
            ArmoryLoadoutPreviewComposer.Assets injectedAssets,
            boolean mannequin) {
        if (selectedBillet == null) throw new IllegalArgumentException("selected billet is required");
        if (injectedAssets == null) throw new IllegalArgumentException("preview assets are required");
        this.selectedBillet = selectedBillet;
        this.assets = injectedAssets;
        this.mannequin = mannequin;
    }

    @Override
    public void draw(CanvasContext context) {
        ArmoryLoadoutPreviewComposer.Sink sink = new CanvasSink(context);
        if (mannequin) {
            ArmoryLoadoutPreviewComposer.composeMannequin(sink, assets,
                    selectedBillet.get(), context.metrics().surfaceWidth(),
                    context.metrics().surfaceHeight());
        } else {
            ArmoryLoadoutPreviewComposer.compose(sink, assets,
                    selectedBillet.get(), context.metrics().surfaceWidth(),
                    context.metrics().surfaceHeight());
        }
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
