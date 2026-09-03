package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.model.Buildings;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.tiles.SheetTexture;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.QuadBatch;
import com.dillon.starsectormarines.render2d.VisibleCellRect;
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
 * <p>The RGBA channel contract is: encoded macro height, raw derived micro
 * height, water identity, and shoreline proximity. Keeping the authoring
 * signals separate lets the composite tune structural relief, surface relief,
 * and water motion independently instead of baking them into one ambiguous
 * scalar. A missing sheet or shader failure degrades micro height to neutral
 * while retaining the semantic channels.
 *
 * <h2>The field is resident</h2>
 * <p>Baked once per battle into {@link ReliefFieldMesh} and patched from the
 * topology's change log, rather than rasterised again every frame. The loop in
 * {@code render} is what a bake resolves through and what the pass falls back
 * to when residency is off or has failed — one resolution and two sinks, so the
 * two cannot disagree about what a cell stands at. The macro relief and the
 * shore distances are built alongside a bake or a patch for the same reason:
 * they are derived from the same cells and nothing else reads them, so gathering
 * roofs off the building registry once per change rather than once per frame is
 * the whole difference.
 *
 * <h2>Macro height is metres</h2>
 * <p>Macro height is authored in metres above a ground datum — one cell is one
 * metre ({@link com.dillon.starsectormarines.battle.air.AirScale#METERS_PER_CELL}),
 * so the two axes and the height axis finally share a unit. The target is an
 * RGBA8 texture, so the red channel carries those metres
 * {@linkplain #encodeMacroMeters encoded} into {@code 0..1}: the datum sits at
 * {@link #MACRO_DATUM} and {@link #MACRO_METERS_SPAN} metres map across the
 * full channel, which leaves room below the datum for water and craters.
 * Quantization is {@code span/255} — about 12 cm — which is under a tenth of
 * the shortest thing that casts a shadow.
 */
final class GroundHeightPass {

    static final float MICRO_SCALE = 0.25f;
    static final int SHORE_RADIUS_CELLS = 3;

    /**
     * Encoded channel value of the ground datum (0 m). Below-datum surfaces get
     * {@code MACRO_DATUM * MACRO_METERS_SPAN} = 4 m of headroom, which no
     * authored surface comes close to needing but costs nothing to keep.
     */
    static final float MACRO_DATUM = 0.125f;

    /** Metres spanned by the full red channel. Sets both the ceiling (28 m above datum) and the ~12 cm quantization. */
    static final float MACRO_METERS_SPAN = 32f;

    /** Metres above the ground datum, encoded into the {@code 0..1} red channel. Inverse of {@link #decodeMacroMeters}. */
    static float encodeMacroMeters(float meters) {
        return Math.max(0f, Math.min(1f, MACRO_DATUM + meters / MACRO_METERS_SPAN));
    }

    /** The red channel back to metres above the ground datum. Mirrors the composite shader's decode. */
    static float decodeMacroMeters(float channel) {
        return (channel - MACRO_DATUM) * MACRO_METERS_SPAN;
    }

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

    /** The same field, resident; see {@link ReliefFieldMesh}. */
    private final ReliefFieldMesh mesh = new ReliefFieldMesh("height");
    private final ResidentCells residentCells = new ResidentCells();
    private boolean meshTextured;
    private boolean meshTexturedKnown;

    GroundHeightPass(GroundMicroHeightSampler resolver) {
        this.resolver = resolver;
    }

    /**
     * @param buildings   who owns which cells, which is where intact roofs come
     *                    from. The pass builds its own {@link MacroReliefField}
     *                    rather than being handed one, so a resident field pays
     *                    for that gather only when something has actually
     *                    changed.
     * @param marginCells cells to emit beyond the viewport on every side. Larger
     *                    than the other ground passes' halo because this target
     *                    is also the sun-shadow occluder field: a wall standing
     *                    just off the sun-ward edge has to be in the texture, or
     *                    its shadow pops into the view as the camera pans. The
     *                    resident field has no margin to choose: it holds the
     *                    whole map and the projection clips it.
     */
    void render(BattleCamera cam, NavigationGrid grid, CellTopology topology,
                Buildings buildings, GenMappingRegistry mapping, int marginCells) {
        boolean textured = shader.ensure();
        if (mesh.isUsable() && renderResident(cam, grid, topology, buildings, mapping, textured)) {
            return;
        }
        MacroReliefField relief = new MacroReliefField(topology, grid, buildings, mapping);
        float cellPx = cam.cellPxSize();
        float[] currentShoreFactors = waterShoreFactors(topology);
        VisibleCellRect view = cam.visibleCells(marginCells, grid.getWidth(), grid.getHeight());

        for (int y = view.minY(); y <= view.maxY(); y++) {
            for (int x = view.minX(); x <= view.maxX(); x++) {
                float macro = encodeMacroMeters(relief.metersAt(x, y));
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
        mesh.dispose();
        meshTexturedKnown = false;
    }

    /**
     * Draws the field from its resident buffers, re-resolving only what the
     * topology says has moved.
     *
     * <p>The macro relief and the shore distances are rebuilt alongside, because
     * both are derived from the same cells and neither is read anywhere else:
     * gathering roofs off the building registry and running a bounded distance
     * transform once per <em>change</em> is the whole difference from doing it
     * once per frame.
     *
     * @return whether the field was drawn; false hands the frame back to the
     *         per-cell path with the same picture
     */
    private boolean renderResident(BattleCamera cam, NavigationGrid grid, CellTopology topology,
                                   Buildings buildings, GenMappingRegistry mapping, boolean textured) {
        if (meshTexturedKnown && textured != meshTextured) {
            // The compose shader arriving or going away changes every cell at
            // once, and that is a rebuild rather than anything the change log
            // could describe.
            mesh.dispose();
            meshTexturedKnown = false;
        }
        if (mesh.isBehind(topology)) {
            residentCells.bind(grid, topology, textured,
                    new MacroReliefField(topology, grid, buildings, mapping),
                    waterShoreFactors(topology));
        }
        if (!mesh.sync(topology, residentCells)) return false;
        meshTextured = textured;
        meshTexturedKnown = true;
        ShaderProgram.useNone();
        mesh.draw(cam, this::bindComposeShader, ShaderProgram::useNone);
        return true;
    }

    private void bindComposeShader() {
        glActiveTexture(GL_TEXTURE0);
        shader.use();
        shader.set1i("heightSheet", 0);
    }

    /**
     * One cell of the resident field, resolved the same way the per-frame loop
     * resolves it.
     *
     * <p>Deliberately the same three reads in the same order rather than a
     * second derivation: a resident field that disagreed with the one it
     * replaced would be a wrong picture that runs fast.
     */
    private final class ResidentCells implements ReliefFieldMesh.CellResolver {

        private NavigationGrid grid;
        private CellTopology topology;
        private boolean textured;
        private MacroReliefField relief;
        private float[] shore;

        void bind(NavigationGrid grid, CellTopology topology, boolean textured,
                  MacroReliefField relief, float[] shore) {
            this.grid = grid;
            this.topology = topology;
            this.textured = textured;
            this.relief = relief;
            this.shore = shore;
        }

        @Override
        public void resolve(int gridX, int gridY, ReliefFieldMesh.CellSink sink) {
            float macro = encodeMacroMeters(relief.metersAt(gridX, gridY));
            float water = isWaterSurface(topology, gridX, gridY) ? 1f : 0f;
            float shoreFactor = shore[topology.index(gridX, gridY)];
            GroundMicroHeightSampler.Sample sample =
                    textured ? resolver.resolve(grid, topology, gridX, gridY) : null;
            AtlasBatch atlas = sample == null ? null : atlas(sample.heightSheetPath);
            if (atlas == null || !atlas.ensureLoaded()) {
                sink.solid(macro, 0.5f, water, shoreFactor);
                return;
            }
            sink.quad(atlas.texture.sprite(), atlas.texture.pxW(), atlas.texture.pxH(),
                    sample.srcX, sample.srcY, sample.srcW, sample.srcH,
                    macro, water, shoreFactor, 1f);
        }
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
