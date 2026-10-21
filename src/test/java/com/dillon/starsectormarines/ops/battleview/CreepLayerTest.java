package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.creep.CreepField;
import com.dillon.starsectormarines.battle.creep.CreepGrowth;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
