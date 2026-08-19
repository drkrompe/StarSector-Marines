package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
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
 * Composes the ground height target from per-cell macro height and the actual
 * S1-derived height texels selected by {@link GroundMicroHeightSampler}.
 *
 * <p>Macro-only cells are emitted as solid grayscale quads. Cells with derived
 * data are batched by height sheet and drawn through a tiny shader:
 * {@code clamp(macro + (micro - 0.5) * MICRO_SCALE, 0, 1)}. Macro travels in
 * vertex color, while micro comes from the sampled height atlas, preserving
 * brick/crack/ripple detail all the way into the screen-space parallax pass.
 * A missing sheet or shader failure degrades that cell/pass to macro-only.
 */
final class GroundHeightPass {

    static final float MICRO_SCALE = 0.25f;

    private static final String VERTEX_SRC = ""
            + "#version 120\n"
            + "varying vec2 vUv;\n"
            + "varying float vMacro;\n"
            + "void main() {\n"
            + "    vUv = gl_MultiTexCoord0.xy;\n"
            + "    vMacro = gl_Color.r;\n"
            + "    gl_Position = ftransform();\n"
            + "}\n";

    private static final String FRAGMENT_SRC = ""
            + "#version 120\n"
            + "uniform sampler2D heightSheet;\n"
            + "uniform float microScale;\n"
            + "varying vec2 vUv;\n"
            + "varying float vMacro;\n"
            + "void main() {\n"
            + "    float micro = texture2D(heightSheet, vUv).r;\n"
            + "    float height = clamp(vMacro + (micro - 0.5) * microScale, 0.0, 1.0);\n"
            + "    gl_FragColor = vec4(height, height, height, 1.0);\n"
            + "}\n";

    private final GroundMicroHeightSampler resolver;
    private final ShaderProgram shader = new ShaderProgram("GroundHeightCompose", VERTEX_SRC, FRAGMENT_SRC);
    private final SolidQuadBatch solidBatch = new SolidQuadBatch(4096);
    private final Map<String, AtlasBatch> atlases = new LinkedHashMap<>();

    GroundHeightPass(GroundMicroHeightSampler resolver) {
        this.resolver = resolver;
    }

    void render(BattleCamera cam, NavigationGrid grid, CellTopology topology,
                GenMappingRegistry mapping) {
        boolean textured = shader.ensure();
        float cellPx = cam.cellPxSize();
        float wallHeight = mapping != null
                ? mapping.wallMacroHeight() : GenMappingRegistry.DEFAULT_WALL_MACRO_HEIGHT;

        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                float macro = topology.isWall(x, y) ? wallHeight
                        : (mapping != null ? mapping.macroHeight(topology.getGroundKind(x, y)) : 0.5f);
                float cx = cam.cellToScreenX(x + 0.5f);
                float cy = cam.cellToScreenY(y + 0.5f);
                GroundMicroHeightSampler.Sample sample = textured ? resolver.resolve(grid, topology, x, y) : null;
                AtlasBatch atlas = sample == null ? null : atlas(sample.heightSheetPath);
                if (atlas == null || !atlas.ensureLoaded()) {
                    appendMacro(cx, cy, cellPx, macro);
                    continue;
                }
                atlas.batch.append(sample.srcX, sample.srcY, sample.srcW, sample.srcH,
                        cx, cy, cellPx, cellPx, macro, macro, macro, 1f);
            }
        }

        glDisable(GL_BLEND);
        ShaderProgram.useNone();
        solidBatch.flush();
        if (!textured) return;

        glActiveTexture(GL_TEXTURE0);
        shader.use();
        shader.set1i("heightSheet", 0);
        shader.set1f("microScale", MICRO_SCALE);
        try {
            for (AtlasBatch atlas : atlases.values()) {
                if (atlas.batch != null) atlas.batch.flush();
            }
        } finally {
            ShaderProgram.useNone();
        }
    }

    void dispose() {
        shader.dispose();
        atlases.clear();
        resolver.invalidate();
    }

    static float compose(float macro, float micro) {
        float value = macro + (micro - 0.5f) * MICRO_SCALE;
        return value < 0f ? 0f : (value > 1f ? 1f : value);
    }

    private void appendMacro(float cx, float cy, float cellPx, float macro) {
        float half = cellPx * 0.5f;
        solidBatch.appendRect(cx - half, cy - half, cx + half, cy + half,
                macro, macro, macro, 1f);
    }

    private AtlasBatch atlas(String heightSheetPath) {
        return atlases.computeIfAbsent(heightSheetPath, AtlasBatch::new);
    }

    private static final class AtlasBatch {
        final SheetTexture texture;
        QuadBatch batch;

        AtlasBatch(String heightSheetPath) {
            this.texture = SheetTexture.grid(heightSheetPath);
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
