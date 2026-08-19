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

    @Test
    void industrialCompoundSeedsRequireTacticalFactoryDimensions() {
        assertEquals(BlockKind.INDUSTRIAL_COMPOUND,
                LabelLeavesStage.constrainKindForSize(BlockKind.INDUSTRIAL_COMPOUND, 15, 12));
        assertEquals(BlockKind.INDUSTRIAL_COMPOUND,
                LabelLeavesStage.constrainKindForSize(BlockKind.INDUSTRIAL_COMPOUND, 12, 15));
        assertEquals(BlockKind.BUILDING_INDUSTRIAL,
                LabelLeavesStage.constrainKindForSize(BlockKind.INDUSTRIAL_COMPOUND, 14, 12));
        assertEquals(BlockKind.BUILDING_INDUSTRIAL,
                LabelLeavesStage.constrainKindForSize(BlockKind.INDUSTRIAL_COMPOUND, 15, 11));
    }

    @Test
    void gatedHousingSeedsRequireInsetApartmentDimensions() {
        assertEquals(BlockKind.GATED_HOUSING,
                LabelLeavesStage.constrainKindForSize(BlockKind.GATED_HOUSING, 14, 12));
        assertEquals(BlockKind.GATED_HOUSING,
                LabelLeavesStage.constrainKindForSize(BlockKind.GATED_HOUSING, 12, 14));
        assertEquals(BlockKind.BUILDING_RESIDENTIAL,
                LabelLeavesStage.constrainKindForSize(BlockKind.GATED_HOUSING, 13, 12));
        assertEquals(BlockKind.BUILDING_RESIDENTIAL,
                LabelLeavesStage.constrainKindForSize(BlockKind.GATED_HOUSING, 14, 11));
    }
}
