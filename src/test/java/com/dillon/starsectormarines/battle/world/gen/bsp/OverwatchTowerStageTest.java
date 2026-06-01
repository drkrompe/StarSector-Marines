package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.turret.DefensePostKind;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Acceptance for {@link OverwatchTowerStage} — the first taxonomy consumer.
 * Drives the real conquest recipe and checks the map gains corner-tower guns.
 *
 * <p>Overwatch towers are distinguishable from {@link DefensePostStamper}'s
 * beach {@link DefensePostKind#LIGHT} posts without a marker: a beach LIGHT post
 * rings its single turret with vents on all four cardinals, so the turret cell
 * has no walkable cardinal neighbor. An overwatch tower mounts its turret
 * against a wall with the field of fire left open, so the turret cell has at
 * least one walkable cardinal (the arc) <em>and</em> at least one non-walkable
 * cardinal (the back wall). That signature isolates the stage's output.
 */
public class OverwatchTowerStageTest {

    private static final long[] CONQUEST_SEEDS = { 1L, 42L, 100L, 777L };

    @Test
    void conquestMapsGainCornerTowers() {
        BspCityGenerator gen = new BspCityGenerator();
        int total = 0;
        for (long seed : CONQUEST_SEEDS) {
            MapResult map = gen.generate(240, 160, seed, TraversalAxis.SOUTH_TO_NORTH);
            List<DefensePost> towers = towers(map);
            System.out.println("seed=" + seed + " overwatch towers=" + towers.size()
                    + " (of " + map.defensePosts.size() + " total posts)");
            for (DefensePost t : towers) {
                int x = t.anchorX, y = t.anchorY;
                assertFalse(map.grid.isWalkable(x, y),
                        () -> "tower mount should be non-walkable at " + x + "," + y);
                assertTrue(map.topology.getGroundKind(x, y) == GroundKind.STONE,
                        () -> "tower mount should be a STONE pad at " + x + "," + y);
            }
            total += towers.size();
        }
        assertTrue(total > 0, "conquest batch produced no overwatch towers");
    }

    @Test
    void deterministicFromSeed() {
        BspCityGenerator gen = new BspCityGenerator();
        int a = towers(gen.generate(240, 160, 777L, TraversalAxis.SOUTH_TO_NORTH)).size();
        int b = towers(gen.generate(240, 160, 777L, TraversalAxis.SOUTH_TO_NORTH)).size();
        assertTrue(a == b, "tower count not reproducible from seed (" + a + " vs " + b + ")");
    }

    /** LIGHT single-turret posts mounted against a wall with an open arc — the stage's signature (see class doc). */
    private static List<DefensePost> towers(MapResult map) {
        NavigationGrid grid = map.grid;
        List<DefensePost> out = new ArrayList<>();
        for (DefensePost p : map.defensePosts) {
            if (p.tier != DefensePostKind.LIGHT || p.turrets.size() != 1) continue;
            int x = p.anchorX, y = p.anchorY;
            boolean openArc = walkable(grid, x + 1, y) || walkable(grid, x - 1, y)
                    || walkable(grid, x, y + 1) || walkable(grid, x, y - 1);
            boolean backWall = nonWalkable(grid, x + 1, y) || nonWalkable(grid, x - 1, y)
                    || nonWalkable(grid, x, y + 1) || nonWalkable(grid, x, y - 1);
            if (openArc && backWall) out.add(p);
        }
        return out;
    }

    private static boolean walkable(NavigationGrid grid, int x, int y) {
        return grid.inBounds(x, y) && grid.isWalkable(x, y);
    }

    private static boolean nonWalkable(NavigationGrid grid, int x, int y) {
        return grid.inBounds(x, y) && !grid.isWalkable(x, y);
    }
}
