package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Production-size seed scan for the mode-defining Conquest keep invariant. */
public class ConquestMapInvariantTest {

    private static final int SEEDS_PER_AXIS = 100;

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
            }
        }
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
