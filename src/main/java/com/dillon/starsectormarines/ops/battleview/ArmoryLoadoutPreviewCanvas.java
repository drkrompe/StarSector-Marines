package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.marine.FireTeamBillet;
import com.dillon.starsectormarines.marine.MarineArmorPattern;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Immersive Fleet Armory preview: equipment sockets beside the same layered
 * actor composition used by battlefield infantry.
 */
public final class ArmoryLoadoutPreviewCanvas implements CanvasProducer {

    private final Supplier<FireTeamBillet> selectedBillet;
    private final ArmoryLoadoutPreviewComposer.Assets injectedAssets;
    private final BattleSprites sprites = new BattleSprites();
    private final Map<String, LayeredSpriteCache> catalogIcons = new LinkedHashMap<>();
    private final ArmoryLoadoutPreviewComposer.Assets previewAssets =
            new ArmoryLoadoutPreviewComposer.Assets() {
                @Override
                public LayeredUnitAssets layered(MarineArmorPattern armor) {
                    return assetsFor(armor);
                }

                @Override
                public LayeredSpriteCache icon(String path) {
                    return ArmoryLoadoutPreviewCanvas.this.icon(path);
                }
            };
    private boolean loadAttempted;

    public ArmoryLoadoutPreviewCanvas(Supplier<FireTeamBillet> selectedBillet) {
        this(selectedBillet, null);
    }

    /** Uses caller-supplied assets for a non-Starsector paint backend. */
    public ArmoryLoadoutPreviewCanvas(
            Supplier<FireTeamBillet> selectedBillet,
            ArmoryLoadoutPreviewComposer.Assets injectedAssets) {
        if (selectedBillet == null) throw new IllegalArgumentException("selected billet is required");
        this.selectedBillet = selectedBillet;
        this.injectedAssets = injectedAssets;
    }

    @Override
    public void draw(CanvasContext context) {
        if (injectedAssets == null) ensureLoaded();
        ArmoryLoadoutPreviewComposer.compose(new CanvasSink(context),
                injectedAssets != null ? injectedAssets : previewAssets,
                selectedBillet.get(), context.metrics().surfaceWidth(),
                context.metrics().surfaceHeight());
    }

    private void ensureLoaded() {
        if (loadAttempted) return;
        loadAttempted = true;
        sprites.ensureLayeredUnitSprites();
    }

    private LayeredUnitAssets assetsFor(MarineArmorPattern armor) {
        return sprites.layeredUnitSprites().get(
                ArmoryLoadoutPreviewComposer.armorFamily(armor));
    }

    private LayeredSpriteCache icon(String path) {
        if (path == null) return null;
        return catalogIcons.computeIfAbsent(path, sprites::loadLayeredSprite);
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
