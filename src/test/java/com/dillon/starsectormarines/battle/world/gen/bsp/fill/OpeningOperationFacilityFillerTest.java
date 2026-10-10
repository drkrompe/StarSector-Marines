package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpeningOperationFacilityFillerTest {
    @Test void furnishedPostPublishesReachableFloorForEveryFrontage() {
        // Isolated seven-cell facility: exercise furniture variation without
        // generating cities or running a battle in the unit suite.
        for (var frontage : OpeningOperationFacilityFiller.Frontage.values()) {
            for (int seed = 0; seed < 16; seed++) {
                NavigationGrid grid = new NavigationGrid(11, 11);
                for (int y = 0; y < 11; y++) for (int x = 0; x < 11; x++) grid.setWalkableFloor(x, y);
                GenContext ctx = new GenContext(grid, new CellTopology(11, 11), new Random(seed), 11, 11, seed);
                OpeningOperationFacilityFiller.fill(PointOfInterest.Kind.COMMS,
                        new BlockLeaf(2, 2, 8, 8, false), ctx, frontage);
                PointOfInterest post = ctx.pois.get(0);
                assertTrue(grid.isWalkable(post.interiorAnchorX, post.interiorAnchorY), frontage + " seed " + seed);
                assertTrue(post.interiorAnchorX > post.left && post.interiorAnchorX < post.right);
                assertTrue(post.interiorAnchorY > post.top && post.interiorAnchorY < post.bottom);
                assertFalse(Paths.isEmpty(GridPathfinder.findPath(grid, post.anchorCellX, post.anchorCellY,
                        post.interiorAnchorX, post.interiorAnchorY)), frontage + " seed " + seed);
            }
        }
    }
}
