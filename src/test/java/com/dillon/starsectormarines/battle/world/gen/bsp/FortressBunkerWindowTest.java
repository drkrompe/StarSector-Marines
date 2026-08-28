package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.decision.TacticalNode.StandPosition;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Structural and consumer coverage for authored Conquest bunker firing cells. */
public class FortressBunkerWindowTest {

    private static final int SEEDS_PER_AXIS = 8;

    @Test
    public void forwardBunkersAuthorUsableWindowPositionsForBothAxes() {
        BspCityGenerator generator = new BspCityGenerator();
        for (TraversalAxis axis : TraversalAxis.values()) {
            int observed = 0;
            for (long seed = 0; seed < SEEDS_PER_AXIS; seed++) {
                MapResult map = generator.generate(
                        BattleSetup.CONQUEST_GRID_W,
                        BattleSetup.CONQUEST_GRID_H,
                        seed, axis);
                List<TacticalNode> bunkers = map.tacticalMap.ofKind(
                        TacticalNode.Kind.FORWARD_BUNKER);
                observed += bunkers.size();

                ZoneGraph zones = new ZoneGraph(map.grid);
                zones.rebuild();
                for (TacticalNode bunker : bunkers) {
                    assertBunkerGeometry(map, bunker, axis, seed);
                    assertAllocatorUsesAuthoredCells(map, zones, bunker, axis, seed);
                }
            }
            assertTrue(observed > 0, "axis=" + axis + ": seed batch produced no forward bunkers");
        }
    }

    private static void assertBunkerGeometry(MapResult map, TacticalNode bunker,
                                             TraversalAxis axis, long seed) {
        List<StandPosition> stands = bunker.standPositions();
        assertEquals(bunker.garrisonSize, stands.size(),
                context(axis, seed, "stand-position count"));
        assertFalse(map.grid.isWalkable(bunker.anchorX, bunker.anchorY),
                context(axis, seed, "turret anchor must be blocked"));
        assertTrue(map.topology.isVehicle(bunker.anchorX, bunker.anchorY),
                context(axis, seed, "turret anchor must carry the mount tag"));

        int frontDx = axis == TraversalAxis.WEST_TO_EAST ? -1 : 0;
        int frontDy = axis == TraversalAxis.SOUTH_TO_NORTH ? -1 : 0;
        Set<Long> unique = new HashSet<>();
        for (StandPosition stand : stands) {
            assertTrue(unique.add(key(stand.x(), stand.y())),
                    context(axis, seed, "duplicate stand position"));
            assertTrue(map.grid.isWalkable(stand.x(), stand.y()),
                    context(axis, seed, "stand position must be walkable at "
                            + stand.x() + "," + stand.y()
                            + " [wall=" + map.topology.isWall(stand.x(), stand.y())
                            + ", fixture=" + map.topology.isFixture(stand.x(), stand.y())
                            + ", vehicle=" + map.topology.isVehicle(stand.x(), stand.y())
                            + ", window=" + map.topology.isWindow(stand.x(), stand.y())
                            + ", ground=" + map.topology.getGroundKind(stand.x(), stand.y()) + "]"));
            assertTrue(GridPathfinder.findPath(map.grid,
                            map.marineSpawnX, map.marineSpawnY,
                            stand.x(), stand.y()).length > 0,
                    context(axis, seed, "stand position must remain reachable"));
            assertTrue(stand.x() >= bunker.left && stand.x() <= bunker.right
                            && stand.y() >= bunker.top && stand.y() <= bunker.bottom,
                    context(axis, seed, "stand position outside bunker bounds"));

            int windowX = stand.x() + frontDx;
            int windowY = stand.y() + frontDy;
            assertTrue(map.topology.isWindow(windowX, windowY),
                    context(axis, seed, "missing firing window"));
            assertTrue(map.topology.isWall(windowX, windowY),
                    context(axis, seed, "window must remain structural wall"));
            assertFalse(map.grid.isWalkable(windowX, windowY),
                    context(axis, seed, "window wall must be non-walkable"));
            assertTrue(map.grid.isSeeThrough(windowX, windowY),
                    context(axis, seed, "window must pass sight and projectiles"));

            int outsideX = stand.x() + frontDx * 2;
            int outsideY = stand.y() + frontDy * 2;
            assertTrue(map.grid.hasLineOfSight(
                            stand.x(), stand.y(), outsideX, outsideY),
                    context(axis, seed, "stand cell cannot see through its window"));
        }
    }

    private static void assertAllocatorUsesAuthoredCells(
            MapResult map, ZoneGraph zones, TacticalNode bunker,
            TraversalAxis axis, long seed) {
        List<int[]> picked = BattleSetup.pickCellsForNode(
                map.grid, zones, bunker, 5, bunker.garrisonSize);
        assertEquals(bunker.standPositions().size(), picked.size(),
                context(axis, seed, "allocator did not fill bunker garrison"));
        for (int i = 0; i < picked.size(); i++) {
            StandPosition expected = bunker.standPositions().get(i);
            assertEquals(expected.x(), picked.get(i)[0],
                    context(axis, seed, "allocator ignored authored x"));
            assertEquals(expected.y(), picked.get(i)[1],
                    context(axis, seed, "allocator ignored authored y"));
        }
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    private static String context(TraversalAxis axis, long seed, String detail) {
        return "axis=" + axis + ", seed=" + seed + ": " + detail;
    }
}
