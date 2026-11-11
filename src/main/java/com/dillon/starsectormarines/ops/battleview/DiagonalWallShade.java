package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.Arrays;
import java.util.function.IntConsumer;

/**
 * The height field's diagonal freestanding walls, so their sun shadow runs along the wall as it is drawn
 * ({@link FreestandingWallFaces}) rather than stepping cell by cell. The field ({@link MacroReliefField}) stands every
 * wall cell a full block, so a diagonal casts a staircase; the height pass lowers each cell only a diagonal stands on
 * back to its floor and raises the diagonal's own slanted footprint ({@link FreestandingWalls#corners}) to the wall's
 * height over it. A cell a straight wall or a pier also stands on stays a block, as it is drawn. A sealed town's
 * envelope is drawn cell by cell and stays so.
 *
 * <p>Gathered once per topology change; the height pass draws it every frame, as it does the plinths' feet
 * ({@link PlinthFoot}).
 */
final class DiagonalWallShade {

    /** Floats per raised footprint: its four corners in cells, then the metres its top stands at. */
    static final int QUAD = 9;

    private CellTopology of;
    private long at = -1;
    private int[] low = new int[16];
    private float[] lowMeters = new float[16];
    private int lowCount;
    private float[] quads = new float[QUAD * 4];
    private int quadCount;

    /** Where a diagonal's footprint is raised: its four corners in cells, and the cell it starts on. */
    interface Sink {
        void raise(float[] corners, int cell);
    }

    /**
     * The cells of {@code strokes} ({@link FreestandingWalls#strokes}) that only a diagonal stands on, to
     * {@code lower}, and each diagonal's footprint with the cell it starts on, to {@code raise}.
     */
    static void shade(float[] strokes, int gridW, IntConsumer lower, Sink raise) {
        int[] square = new int[8];
        int squares = 0;
        for (int o = 0; o < strokes.length; o += FreestandingWalls.STROKE) {
            if (diagonal(strokes, o)) continue;
            int x0 = (int) strokes[o], y0 = (int) strokes[o + 1], x1 = (int) strokes[o + 2], y1 = (int) strokes[o + 3];
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
                    if (squares == square.length) square = Arrays.copyOf(square, squares * 2);
                    square[squares++] = x + y * gridW;
                }
            }
        }
        Arrays.sort(square, 0, squares);
        float[] q = new float[8];
        for (int o = 0; o < strokes.length; o += FreestandingWalls.STROKE) {
            if (!diagonal(strokes, o)) continue;
            int x0 = (int) strokes[o], y0 = (int) strokes[o + 1], x1 = (int) strokes[o + 2], y1 = (int) strokes[o + 3];
            int dx = Integer.signum(x1 - x0), dy = Integer.signum(y1 - y0), len = Math.abs(x1 - x0);
            for (int k = 0; k <= len; k++) {
                int cell = x0 + k * dx + (y0 + k * dy) * gridW;
                if (Arrays.binarySearch(square, 0, squares, cell) < 0) lower.accept(cell);
            }
            FreestandingWalls.corners(strokes, o, FreestandingWalls.HALF, q);
            raise.raise(q, x0 + y0 * gridW);
        }
    }

    private static boolean diagonal(float[] strokes, int o) {
        return strokes[o] != strokes[o + 2] && strokes[o + 1] != strokes[o + 3];
    }

    /** Gathers the map's diagonal walls, once per topology change. */
    void gather(BattleSimulation sim) {
        CellTopology topology = sim.getTopology();
        if (topology == null || topology == of && topology.changeCount() == at) return;
        of = topology;
        at = topology.changeCount();
        lowCount = 0;
        quadCount = 0;
        int gridW = topology.getWidth();
        MacroReliefField relief = null;
        for (FreestandingWalls.Run run : FreestandingWallFaces.runs(topology)) {
            FreestandingWalls.Kind kind = run.kind();
            if (kind == FreestandingWalls.Kind.ENVELOPE || kind == FreestandingWalls.Kind.ENVELOPE_PLINTH) continue;
            if (relief == null) {
                relief = new MacroReliefField(topology, sim.getGrid(), sim.getBuildings(),
                        GenMappingRegistry.installed());
            }
            MacroReliefField field = relief;
            shade(run.strokes(), gridW, cell -> {
                if (lowCount == low.length) {
                    low = Arrays.copyOf(low, lowCount * 2);
                    lowMeters = Arrays.copyOf(lowMeters, lowCount * 2);
                }
                low[lowCount] = cell;
                lowMeters[lowCount++] = field.floorMeters(cell % gridW, cell / gridW);
            }, (corners, cell) -> {
                if ((quadCount + 1) * QUAD > quads.length) quads = Arrays.copyOf(quads, quads.length * 2);
                System.arraycopy(corners, 0, quads, quadCount * QUAD, 8);
                quads[quadCount * QUAD + 8] = field.wallTopMeters(cell % gridW, cell / gridW);
                quadCount++;
            });
        }
    }

    int lowCount() { return lowCount; }

    /** The lowered cells ({@code x + y * gridW}); read the first {@link #lowCount()}. */
    int[] low() { return low; }

    /** Each lowered cell's floor, metres. */
    float[] lowMeters() { return lowMeters; }

    int quadCount() { return quadCount; }

    /** The raised footprints, {@link #QUAD} floats each; read the first {@link #quadCount()}. */
    float[] quads() { return quads; }
}
