package com.dillon.starsectormarines.battle.world.model;

import com.dillon.starsectormarines.ops.OperationTier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MapScaleTest {

    @Test
    void aRaidGetsMoreGroundThanItsTierAtEveryTier() {
        for (OperationTier tier : OperationTier.values()) {
            MapScale band = MapScale.forTier(tier), raid = MapScale.forRaid(tier);
            assertTrue(raid.width > band.width && raid.height > band.height, tier + ": " + raid + " vs " + band);
        }
        assertTrue(MapScale.forRaid(null).width > MapScale.forTier(null).width);
    }
}
