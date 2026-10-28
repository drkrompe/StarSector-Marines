package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.Basin;
import com.dillon.starsectormarines.battle.world.gen.SurfaceField;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasinEdgeTest {

    private static final int W = 80, H = 60;

    /** A map laid as its field says: the floor's open ground on the floor, the rim's on the rim. */
    private static CellTopology laid(SurfaceField field) {
        CellTopology topology = new CellTopology(W, H);
        for (int x = 0; x < W; x++) for (int y = 0; y < H; y++) {
            topology.setGroundKind(x, y, field.rim(x, y) ? CellTopology.GroundKind.SAND : CellTopology.GroundKind.GRASS);
        }
        topology.setSurfaceField(field);
        return topology;
    }

    @Test
    void cellsWhereTheSidesMeetBlendAndTheirTwoSidesTileTheCell() {
        SurfaceField field = new SurfaceField(SurfacePalette.ARID, new Basin(SurfacePalette.VERDANT, .6f, .6f), W, H, 5);
        CellTopology topology = laid(field);
        BasinEdge edge = BasinEdge.of(topology);
        int blending = 0;
        for (int x = 1; x + 1 < W; x++) for (int y = 1; y + 1 < H; y++) {
            boolean mixed = false;
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) mixed |= field.rim(x + dx, y + dy) != field.rim(x, y);
            if (mixed) assertTrue(edge.blends(x, y), "a cell beside the other side blends, at " + x + "," + y);
            if (!edge.blends(x, y)) continue;
            blending++;
            float area = 0f;
            for (boolean floor : new boolean[]{true, false}) for (int t = 0; t < 2; t++) area += area(edge.piece(x, y, floor, t));
            assertEquals(1f, area, 1e-4f, "floor and rim pieces cover the cell once, at " + x + "," + y);
        }
        assertTrue(blending > 0);
        assertFalse(edge.blends(W / 2, H / 2), "deep in the floor nothing blends");
    }

    @Test
    void groundOfNeitherSideIsLeftAlone() {
        SurfaceField field = new SurfaceField(SurfacePalette.ARID, new Basin(SurfacePalette.VERDANT, .6f, .6f), W, H, 5);
        CellTopology topology = laid(field);
        for (int x = 0; x < W; x++) for (int y = 0; y < H; y++) topology.setGroundKind(x, y, CellTopology.GroundKind.DIRT);
        BasinEdge edge = BasinEdge.of(topology);
        for (int x = 0; x < W; x++) for (int y = 0; y < H; y++) assertFalse(edge.blends(x, y));
        assertNull(BasinEdge.of(new CellTopology(W, H)), "a map with no basin has no edge");
    }

    private static float area(float[] v) {
        if (v == null) return 0f;
        float sum = 0f;
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            sum += v[i * 2] * v[j * 2 + 1] - v[j * 2] * v[i * 2 + 1];
        }
        return Math.abs(sum) / 2f;
    }
}
