package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.tiles.SheetTexture;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.QuadBatch;
import com.dillon.starsectormarines.render2d.ShaderProgram;
import com.dillon.starsectormarines.render2d.SolidQuadBatch;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;

/**
 * Composes S1 tangent normals into screen space using the exact terrain atlas
 * rectangles selected by {@link GroundMicroHeightSampler}. Unsupported or
 * missing sheets write the encoded flat normal {@code (0.5, 0.5, 1.0)}.
 */
final class GroundNormalPass {

    private final GroundMicroHeightSampler resolver;
    private final SolidQuadBatch flatBatch = new SolidQuadBatch(4096);
    private final Map<String, AtlasBatch> atlases = new LinkedHashMap<>();

    GroundNormalPass(GroundMicroHeightSampler resolver) {
        this.resolver = resolver;
    }

    void render(BattleCamera camera, NavigationGrid grid, CellTopology topology) {
        float cellPx = camera.cellPxSize();
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                float cx = camera.cellToScreenX(x + 0.5f);
                float cy = camera.cellToScreenY(y + 0.5f);
                GroundMicroHeightSampler.Sample sample = resolver.resolve(grid, topology, x, y);
                AtlasBatch atlas = sample == null ? null : atlas(sample.normalSheetPath);
                if (atlas == null || !atlas.ensureLoaded()) {
                    appendFlat(cx, cy, cellPx);
                    continue;
                }
                atlas.batch.append(sample.srcX, sample.srcY, sample.srcW, sample.srcH,
                        cx, cy, cellPx, cellPx, 1f, 1f, 1f, 1f);
            }
        }

        glDisable(GL_BLEND);
        ShaderProgram.useNone();
        flatBatch.flush();
        glActiveTexture(GL_TEXTURE0);
        for (AtlasBatch atlas : atlases.values()) {
            if (atlas.batch != null) atlas.batch.flush();
        }
    }

    void dispose() {
        atlases.clear();
    }

    private void appendFlat(float cx, float cy, float cellPx) {
        float half = cellPx * 0.5f;
        flatBatch.appendRect(cx - half, cy - half, cx + half, cy + half,
                0.5f, 0.5f, 1f, 1f);
    }

    private AtlasBatch atlas(String normalSheetPath) {
        return atlases.computeIfAbsent(normalSheetPath, AtlasBatch::new);
    }

    private static final class AtlasBatch {
        final SheetTexture texture;
        QuadBatch batch;

        AtlasBatch(String normalSheetPath) {
            this.texture = SheetTexture.grid(normalSheetPath);
        }

        boolean ensureLoaded() {
            if (batch != null) return true;
            texture.ensureLoaded();
            if (!texture.isLoaded()) return false;
            batch = new QuadBatch(texture.sprite(), texture.pxW(), texture.pxH(), 4096);
            return true;
        }
    }
}
