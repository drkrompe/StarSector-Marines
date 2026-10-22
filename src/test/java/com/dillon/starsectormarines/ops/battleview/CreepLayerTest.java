package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.creep.CreepField;
import com.dillon.starsectormarines.battle.creep.CreepGrowth;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreepLayerTest {

    private static final int W = 80, H = 40;

    /** The mirror is patched only where tiles moved, so it must still equal the field after growth and dying-back. */
    @Test
    void tilePatchedMirrorTracksTheFieldThroughGrowthAndWithering() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        CreepField field = new CreepField(grid, new CellTopology(W, H), 21L);
        CreepLayer layer = new CreepLayer();
        field.putSource(1L, 30, 20, new CreepGrowth(5f, 14f));
        int tick = 0;
        for (; tick < 240; tick++) {
            field.tick(tick);
            if (tick % 7 == 0) layer.catchUp(field, grid);
        }
        layer.catchUp(field, grid);
        assertMirrors(field, layer);
        field.removeSource(1L);
        for (; tick < 900; tick++) {
            field.tick(tick);
            if (tick % 11 == 0) layer.catchUp(field, grid);
        }
        layer.catchUp(field, grid);
        assertMirrors(field, layer);
    }

    /** Blight reaches a few cells past the edge over grass, fading out, and never onto pavement. */
    @Test
    void blightSpreadsAheadOfTheEdgeOverGrassOnly() {
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topology = new CellTopology(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, y < H / 2 ? CellTopology.GroundKind.GRASS : CellTopology.GroundKind.STREET);
            }
        }
        CreepField field = new CreepField(grid, topology, 21L);
        CreepLayer layer = new CreepLayer();
        field.putSource(1L, 30, 20, new CreepGrowth(5f, 10f));
        for (int tick = 0; tick < 600; tick++) field.tick(tick);
        layer.catchUp(field, grid, topology);
        int grassAhead = 0, streetAhead = 0, farGrass = 0;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (field.coverageAt(x, y) > 0) continue;
                int blight = layer.groundAt(x, y, 0);
                if (blight == 0) continue;
                if (y < H / 2) grassAhead++;
                else streetAhead++;
                if (nearestCovered(field, x, y) > CreepLayer.BLIGHT_CELLS) farGrass++;
            }
        }
        assertTrue(grassAhead > 0, "grass ahead of the edge is blighted");
        assertEquals(0, streetAhead, "pavement keeps a clean edge");
        assertEquals(0, farGrass, "blight stops " + CreepLayer.BLIGHT_CELLS + " cells out");
    }

    private static int nearestCovered(CreepField field, int x, int y) {
        int best = Integer.MAX_VALUE;
        for (int cy = 0; cy < H; cy++) {
            for (int cx = 0; cx < W; cx++) {
                if (field.coverageAt(cx, cy) > 0) best = Math.min(best, Math.max(Math.abs(cx - x), Math.abs(cy - y)));
            }
        }
        return best;
    }

    private static void assertMirrors(CreepField field, CreepLayer layer) {
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int idx = y * W + x;
                assertEquals(field.coverageAt(idx), layer.mirrorAt(x, y, 0), "coverage at " + x + "," + y);
                assertEquals(field.vitalityAt(idx), layer.mirrorAt(x, y, 1), "vitality at " + x + "," + y);
            }
        }
    }
}
