package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.tiles.SheetTexture;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.QuadBatch;
import com.dillon.starsectormarines.render2d.VisibleCellRect;
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
 *
 * <p>The field is {@linkplain ReliefFieldMesh resident}: baked once per battle
 * and patched from the topology's change log, rather than rasterised again
 * every frame. The loop below is what a bake resolves through, and what the
 * pass falls back to when residency is off or has failed — one resolution, two
 * sinks, so the two cannot disagree about what a cell samples.
 */
final class GroundNormalPass {

    /** The encoded flat normal a cell with no derived art writes. */
    private static final float FLAT_R = 0.5f;
    private static final float FLAT_G = 0.5f;
    private static final float FLAT_B = 1f;

    private final GroundMicroHeightSampler resolver;
    private final SolidQuadBatch flatBatch = new SolidQuadBatch(4096);
    private final Map<String, AtlasBatch> atlases = new LinkedHashMap<>();

    /** The same field, resident; see {@link ReliefFieldMesh}. */
    private final ReliefFieldMesh mesh = new ReliefFieldMesh("normal");
    private final ResidentCells residentCells = new ResidentCells();

    GroundNormalPass(GroundMicroHeightSampler resolver) {
        this.resolver = resolver;
    }

    void render(BattleCamera camera, NavigationGrid grid, CellTopology topology) {
        if (mesh.isUsable() && renderResident(camera, grid, topology)) return;
        float cellPx = camera.cellPxSize();
        VisibleCellRect view = camera.visibleCells(
                VisibleCellRect.GEOMETRY_MARGIN_CELLS, grid.getWidth(), grid.getHeight());
        for (int y = view.minY(); y <= view.maxY(); y++) {
            for (int x = view.minX(); x <= view.maxX(); x++) {
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
        mesh.dispose();
    }

    /**
     * Draws the field from its resident buffers, re-resolving only the cells the
     * topology says have moved. Textured cells carry white, so the fixed-function
     * modulate leaves the derived normal exactly as the atlas holds it.
     */
    private boolean renderResident(BattleCamera camera, NavigationGrid grid, CellTopology topology) {
        if (mesh.isBehind(topology)) residentCells.bind(grid, topology);
        if (!mesh.sync(topology, residentCells)) return false;
        ShaderProgram.useNone();
        mesh.draw(camera, () -> glActiveTexture(GL_TEXTURE0), null);
        return true;
    }

    /** One cell of the resident field, resolved the way the per-frame loop resolves it. */
    private final class ResidentCells implements ReliefFieldMesh.CellResolver {

        private NavigationGrid grid;
        private CellTopology topology;

        void bind(NavigationGrid grid, CellTopology topology) {
            this.grid = grid;
            this.topology = topology;
        }

        @Override
        public void resolve(int gridX, int gridY, ReliefFieldMesh.CellSink sink) {
            GroundMicroHeightSampler.Sample sample = resolver.resolve(grid, topology, gridX, gridY);
            AtlasBatch atlas = sample == null ? null : atlas(sample.normalSheetPath);
            if (atlas == null || !atlas.ensureLoaded()) {
                sink.solid(FLAT_R, FLAT_G, FLAT_B, 1f);
                return;
            }
            sink.quad(atlas.texture.sprite(), atlas.texture.pxW(), atlas.texture.pxH(),
                    sample.srcX, sample.srcY, sample.srcW, sample.srcH, 1f, 1f, 1f, 1f);
        }
    }

    private void appendFlat(float cx, float cy, float cellPx) {
        float half = cellPx * 0.5f;
        flatBatch.appendRect(cx - half, cy - half, cx + half, cy + half,
                FLAT_R, FLAT_G, FLAT_B, 1f);
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
