package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ShoreWaterMaskTest {

    /** A brook down columns 4..6 between grass banks, forded over rows 4..6 by a stony bed in shallows. */
    private static CellTopology fordedBrook() {
        CellTopology topology = new CellTopology(11, 11);
        for (int y = 0; y < 11; y++) {
            for (int x = 0; x < 11; x++) {
                boolean brook = x >= 4 && x <= 6, ford = brook && y >= 4 && y <= 6;
                topology.setGroundKind(x, y, ford ? GroundKind.STONE : brook ? GroundKind.WATER : GroundKind.GRASS);
                if (ford) topology.setTag(x, y, CellTopology.Tag.SHALLOW_WATER, true);
            }
        }
        return topology;
    }

    @Test
    void aFordsBedIsFordAndWaterWhileOpenWaterAndBanksAreNoFord() {
        ShoreWaterMask mask = ShoreWaterMask.of(fordedBrook());
        assertNotNull(mask);
        // The middle of the ford: the creep's body is kept off it, its veins are not.
        assertEquals(1f, mask.sample(5.5f, 5.5f), 0.01f);
        assertEquals(1f, mask.sampleFord(5.5f, 5.5f), 0.01f);
        // Open water up the brook: no creep and no veins.
        assertEquals(1f, mask.sample(5.5f, 1.5f), 0.01f);
        assertEquals(0f, mask.sampleFord(5.5f, 1.5f), 0.01f);
        // A bank beside open water, and the land well back from the ford: no ford.
        assertEquals(0f, mask.sampleFord(3.5f, 1.5f), 0.01f);
        assertEquals(0f, mask.sampleFord(1.5f, 5.5f), 0.01f);
        assertEquals(0f, mask.sample(1.5f, 5.5f), 0.01f);
    }
}
