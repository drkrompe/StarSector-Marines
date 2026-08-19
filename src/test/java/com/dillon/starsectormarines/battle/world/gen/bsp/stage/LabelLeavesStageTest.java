package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LabelLeavesStageTest {

    @Test
    void civicHeadquartersRequireBothLongAndShortLotMinimums() {
        assertEquals(BlockKind.BUILDING_CIVIC,
                LabelLeavesStage.constrainKindForSize(BlockKind.BUILDING_CIVIC, 13, 11));
        assertEquals(BlockKind.BUILDING_CIVIC,
                LabelLeavesStage.constrainKindForSize(BlockKind.BUILDING_CIVIC, 11, 13));
        assertEquals(BlockKind.BUILDING_COMMERCIAL,
                LabelLeavesStage.constrainKindForSize(BlockKind.BUILDING_CIVIC, 12, 11));
        assertEquals(BlockKind.BUILDING_COMMERCIAL,
                LabelLeavesStage.constrainKindForSize(BlockKind.BUILDING_CIVIC, 13, 10));
    }
}
