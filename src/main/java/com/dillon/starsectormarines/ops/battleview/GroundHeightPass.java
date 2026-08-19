package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.tiles.SheetTexture;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.QuadBatch;
import com.dillon.starsectormarines.render2d.ShaderProgram;
import com.dillon.starsectormarines.render2d.SolidQuadBatch;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;

/**
 * Writes the material/height target consumed by the ground composite.
 *
 * <p>The RGBA channel contract is: macro height, raw derived micro height,
 * water identity, and shoreline proximity. Keeping the authoring signals
 * separate lets the composite tune structural relief, surface relief, and
 * water motion independently instead of baking them into one ambiguous scalar.
 * A missing sheet or shader failure degrades micro height to neutral while
 * retaining the semantic channels.
 */
final class GroundHeightPass {

    static final float MICRO_SCALE = 0.25f;
    static final int SHORE_RADIUS_CELLS = 3;

    private static final String VERTEX_SRC = ""
            + "#version 120\n"
            + "varying vec2 vUv;\n"
            + "varying vec4 vMeta;\n"
            + "void main() {\n"
            + "    vUv = gl_MultiTexCoord0.xy;\n"
            + "    vMeta = gl_Color;\n"
            + "    gl_Position = ftransform();\n"
            + "}\n";

    private static final String FRAGMENT_SRC = ""
            + "#version 120\n"
            + "uniform sampler2D heightSheet;\n"
            + "varying vec2 vUv;\n"
            + "varying vec4 vMeta;\n"
            + "void main() {\n"
            + "    float micro = texture2D(heightSheet, vUv).r;\n"
            + "    gl_FragColor = vec4(vMeta.r, micro, vMeta.g, vMeta.b);\n"
            + "}\n";

    private final GroundMicroHeightSampler resolver;
    private final ShaderProgram shader = new ShaderProgram("GroundHeightCompose", VERTEX_SRC, FRAGMENT_SRC);
    private final SolidQuadBatch solidBatch = new SolidQuadBatch(4096);
    private final Map<String, AtlasBatch> atlases = new LinkedHashMap<>();
    private int[] shoreDistance = new int[0];
    private int[] shoreQueue = new int[0];
    private float[] shoreFactors = new float[0];

    GroundHeightPass(GroundMicroHeightSampler resolver) {
        this.resolver = resolver;
    }

    void render(BattleCamera cam, NavigationGrid grid, CellTopology topology,
                GenMappingRegistry mapping) {
        boolean textured = shader.ensure();
        float cellPx = cam.cellPxSize();
        float wallHeight = mapping != null
                ? mapping.wallMacroHeight() : GenMappingRegistry.DEFAULT_WALL_MACRO_HEIGHT;
        float[] currentShoreFactors = waterShoreFactors(topology);

        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                float macro = topology.isWall(x, y) ? wallHeight
                        : (mapping != null ? mapping.macroHeight(topology.getGroundKind(x, y)) : 0.5f);
                float water = isWaterSurface(topology, x, y) ? 1f : 0f;
                float shore = currentShoreFactors[topology.index(x, y)];
                float cx = cam.cellToScreenX(x + 0.5f);
                float cy = cam.cellToScreenY(y + 0.5f);
                GroundMicroHeightSampler.Sample sample = textured ? resolver.resolve(grid, topology, x, y) : null;
                AtlasBatch atlas = sample == null ? null : atlas(sample.heightSheetPath);
                if (atlas == null || !atlas.ensureLoaded()) {
                    appendMetadata(cx, cy, cellPx, macro, water, shore);
                    continue;
                }
                atlas.batch.append(sample.srcX, sample.srcY, sample.srcW, sample.srcH,
                        cx, cy, cellPx, cellPx, macro, water, shore, 1f);
            }
        }

        glDisable(GL_BLEND);
        ShaderProgram.useNone();
        solidBatch.flush();
        if (!textured) return;

        glActiveTexture(GL_TEXTURE0);
        shader.use();
        shader.set1i("heightSheet", 0);
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

    static float microRelief(float micro) {
        return (micro - 0.5f) * MICRO_SCALE;
    }

    static float waterShoreFactor(CellTopology topology, int x, int y) {
        if (!isWaterSurface(topology, x, y)) return 0f;
        int nearest = SHORE_RADIUS_CELLS + 1;
        for (int dy = -SHORE_RADIUS_CELLS; dy <= SHORE_RADIUS_CELLS; dy++) {
            for (int dx = -SHORE_RADIUS_CELLS; dx <= SHORE_RADIUS_CELLS; dx++) {
                int distance = Math.abs(dx) + Math.abs(dy);
                if (distance == 0 || distance > SHORE_RADIUS_CELLS) continue;
                if (!isWaterSurface(topology, x + dx, y + dy)) {
                    nearest = Math.min(nearest, distance);
                }
            }
        }
        return nearest <= SHORE_RADIUS_CELLS
                ? (SHORE_RADIUS_CELLS - nearest + 1f) / SHORE_RADIUS_CELLS : 0f;
    }

    /** Bounded Manhattan distance transform: one linear pass plus a tiny BFS. */
    private float[] waterShoreFactors(CellTopology topology) {
        int width = topology.getWidth();
        int height = topology.getHeight();
        int count = width * height;
        if (shoreDistance.length != count) {
            shoreDistance = new int[count];
            shoreQueue = new int[count];
            shoreFactors = new float[count];
        } else {
            Arrays.fill(shoreDistance, 0);
            Arrays.fill(shoreFactors, 0f);
        }
        int head = 0;
        int tail = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!isWaterSurface(topology, x, y)) continue;
                if (!isWaterSurface(topology, x - 1, y)
                        || !isWaterSurface(topology, x + 1, y)
                        || !isWaterSurface(topology, x, y - 1)
                        || !isWaterSurface(topology, x, y + 1)) {
                    int index = topology.index(x, y);
                    shoreDistance[index] = 1;
                    shoreQueue[tail++] = index;
                }
            }
        }

        int[] stepX = {-1, 1, 0, 0};
        int[] stepY = {0, 0, -1, 1};
        while (head < tail) {
            int index = shoreQueue[head++];
            int currentDistance = shoreDistance[index];
            if (currentDistance >= SHORE_RADIUS_CELLS) continue;
            int x = index % width;
            int y = index / width;
            for (int direction = 0; direction < 4; direction++) {
                int nx = x + stepX[direction];
                int ny = y + stepY[direction];
                if (!isWaterSurface(topology, nx, ny)) continue;
                int neighbor = topology.index(nx, ny);
                if (shoreDistance[neighbor] != 0) continue;
                shoreDistance[neighbor] = currentDistance + 1;
                shoreQueue[tail++] = neighbor;
            }
        }

        for (int index = 0; index < count; index++) {
            if (shoreDistance[index] > 0) {
                shoreFactors[index] = (SHORE_RADIUS_CELLS - shoreDistance[index] + 1f)
                        / SHORE_RADIUS_CELLS;
            }
        }
        return shoreFactors;
    }

    private static boolean isWaterSurface(CellTopology topology, int x, int y) {
        return topology.inBounds(x, y) && !topology.isWall(x, y) && topology.isWater(x, y);
    }

    private void appendMetadata(float cx, float cy, float cellPx,
                                float macro, float water, float shore) {
        float half = cellPx * 0.5f;
        solidBatch.appendRect(cx - half, cy - half, cx + half, cy + half,
                macro, 0.5f, water, shore);
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
