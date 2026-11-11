package com.dillon.starsectormarines.battle.world.gen.layout;

import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A line's nests stand only where they see out over open ground ahead of the line, laid on its front. */
class LineNestTest {

    private static final int W = 90;
    private static final int H = 50;

    @Test
    void aNestWithAHouseAcrossItsArcIsNotPlaced() {
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topology = new CellTopology(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.DIRT);
            }
        }
        // A house four cells in front of the line's east end.
        for (int x = 64; x <= 80; x++) {
            for (int y = 24; y <= 28; y++) {
                grid.setWalkable(x, y, false);
                topology.setWall(x, y, true);
            }
        }
        GenContext ctx = new GenContext(grid, topology, new Random(3L), W, H, 3L);
        int[] front = {0, 1};
        Line line = new Line("line", List.of(new int[]{10, 20}, new int[]{70, 20}), LineStyle.SANDBAG, front, 0, 0,
                true, false);
        MissionLayoutStage.lay(ctx, new LayoutGround(ctx), line, null);

        assertEquals(1, ctx.defensePosts.size(), "the end with the house in front of it took a nest");
        DefensePost nest = ctx.defensePosts.get(0);
        assertTrue(nest.anchorX < 40, "the nest stood at the east end, against the house");
        assertEquals(LayeredAppearance.facingDegrees(front[0], front[1]), nest.restFacing, 0.01f,
                "the nest is laid on the line's front");
    }
}
