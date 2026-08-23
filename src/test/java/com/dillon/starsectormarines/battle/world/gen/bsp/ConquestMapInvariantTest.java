package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Production-size seed scan for the mode-defining Conquest keep invariant. */
public class ConquestMapInvariantTest {

    private static final int SEEDS_PER_AXIS = 100;
    private static final int MIN_OUTER_WARD_CLEARANCE = 6;

    @Test
    public void everyCanonicalMapContainsExactlyOneCentralKeep() {
        BspCityGenerator generator = new BspCityGenerator();

        for (TraversalAxis axis : TraversalAxis.values()) {
            for (long seed = 0; seed < SEEDS_PER_AXIS; seed++) {
                MapResult map = generator.generate(
                        BattleSetup.CONQUEST_GRID_W,
                        BattleSetup.CONQUEST_GRID_H,
                        seed,
                        axis);

                assertEquals(1,
                        map.tacticalMap.ofKind(TacticalNode.Kind.COMMAND_POST).size(),
                        "seed=" + seed + ", axis=" + axis);
                assertTrue(hasKeepThrone(map),
                        "central keep must stamp a throne room: seed=" + seed + ", axis=" + axis);
                assertOuterWardWrapsKeep(map, axis, seed);
            }
        }
    }

    private static void assertOuterWardWrapsKeep(MapResult map, TraversalAxis axis, long seed) {
        TacticalNode keep = map.tacticalMap.ofKind(TacticalNode.Kind.COMMAND_POST).get(0);
        List<TacticalNode> towers = map.tacticalMap.ofKind(TacticalNode.Kind.HEAVY_TOWER);
        assertTrue(towers.size() >= 2,
                "outer ward must have corner towers: seed=" + seed + ", axis=" + axis);

        if (axis == TraversalAxis.SOUTH_TO_NORTH) {
            int wallLeft = towers.stream().mapToInt(n -> n.anchorX).min().orElseThrow();
            int wallRight = towers.stream().mapToInt(n -> n.anchorX).max().orElseThrow();
            int wallFront = towers.stream()
                    .max(Comparator.comparingInt(n -> countTowersOnRow(towers, n.anchorY)))
                    .orElseThrow().anchorY + 1;
            assertTrue(keep.compoundLeft() - wallLeft >= MIN_OUTER_WARD_CLEARANCE,
                    enclosureMessage(seed, axis, keep, wallLeft, wallFront, wallRight));
            assertTrue(wallRight - keep.compoundRight() >= MIN_OUTER_WARD_CLEARANCE,
                    enclosureMessage(seed, axis, keep, wallLeft, wallFront, wallRight));
            assertTrue(keep.compoundTop() - wallFront >= MIN_OUTER_WARD_CLEARANCE,
                    enclosureMessage(seed, axis, keep, wallLeft, wallFront, wallRight));
        } else {
            int wallBottom = towers.stream().mapToInt(n -> n.anchorY).min().orElseThrow();
            int wallTop = towers.stream().mapToInt(n -> n.anchorY).max().orElseThrow();
            int wallFront = towers.stream()
                    .max(Comparator.comparingInt(n -> countTowersOnColumn(towers, n.anchorX)))
                    .orElseThrow().anchorX - 1;
            assertTrue(keep.compoundTop() - wallBottom >= MIN_OUTER_WARD_CLEARANCE,
                    enclosureMessage(seed, axis, keep, wallFront, wallBottom, wallTop));
            assertTrue(wallTop - keep.compoundBottom() >= MIN_OUTER_WARD_CLEARANCE,
                    enclosureMessage(seed, axis, keep, wallFront, wallBottom, wallTop));
            assertTrue(keep.compoundLeft() - wallFront >= MIN_OUTER_WARD_CLEARANCE,
                    enclosureMessage(seed, axis, keep, wallFront, wallBottom, wallTop));
        }
    }

    private static int countTowersOnRow(List<TacticalNode> towers, int y) {
        return (int) towers.stream().filter(n -> n.anchorY == y).count();
    }

    private static int countTowersOnColumn(List<TacticalNode> towers, int x) {
        return (int) towers.stream().filter(n -> n.anchorX == x).count();
    }

    private static String enclosureMessage(long seed, TraversalAxis axis, TacticalNode keep,
                                           int wallA, int wallFront, int wallB) {
        return "outer ward does not wrap keep compound: seed=" + seed + ", axis=" + axis
                + ", keep=" + keep.compoundLeft() + "," + keep.compoundTop()
                + ".." + keep.compoundRight() + "," + keep.compoundBottom()
                + ", wall=" + wallA + "," + wallFront + ".." + wallB;
    }

    private static boolean hasKeepThrone(MapResult map) {
        for (int y = 0; y < map.grid.getHeight(); y++) {
            for (int x = 0; x < map.grid.getWidth(); x++) {
                if (map.topology.getRoomPurpose(x, y) == RoomPurpose.KEEP_THRONE) return true;
            }
        }
        return false;
    }
}
