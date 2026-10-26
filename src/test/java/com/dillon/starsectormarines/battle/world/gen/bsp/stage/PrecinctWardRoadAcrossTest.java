package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A gate's road across a walled place's glacis is laid as the street it leaves by, not left as the skeleton's grass. */
class PrecinctWardRoadAcrossTest {

    private static final int W = 12, H = 5;

    @Test
    void theRoadAcrossTheGlacisIsLaidAsTheStreetOutside() {
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topology = new CellTopology(W, H);
        GenContext ctx = new GenContext(grid, topology, new Random(1), W, H, 1);
        int asphalt = topology.surfaceIndex("pavement.asphalt.worn");
        int[][] claim = new int[W][H];
        boolean[][] strip = new boolean[W][H];
        boolean[][] paved = new boolean[W][H];
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.GRASS);
                // The place to x 6, its glacis x 4..6; the street past it from x 7, its gate road on row 2.
                claim[x][y] = x <= 6 ? 0 : GrownTrunkPlan.UNOWNED;
                strip[x][y] = x >= 4 && x <= 6;
                paved[x][y] = y == 2 && x <= 6;
                if (x >= 7) {
                    topology.setGroundKind(x, y, GroundKind.STREET);
                    topology.setSurface(x, y, asphalt);
                }
            }
        }

        PrecinctWardStage.roadAcross(ctx, claim, 0, strip, paved);

        for (int x = 4; x <= 6; x++) {
            assertEquals(GroundKind.STREET, topology.getGroundKind(x, 2), "glacis road cell " + x + " ground");
            assertEquals(asphalt, topology.getSurface(x, 2), "glacis road cell " + x + " surface");
            assertEquals(GroundKind.GRASS, topology.getGroundKind(x, 1), "glacis cell off the road " + x);
        }
        assertEquals(GroundKind.GRASS, topology.getGroundKind(3, 2), "the road inside the wall is the place's to pave");
    }
}
