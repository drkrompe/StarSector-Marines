package com.dillon.starsectormarines.campaign.polity;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroundProductionQualityTest {

    @Test
    void theLadderIsReadOffTheIndustryTheColonyHas() {
        assertEquals(GroundProductionQuality.NONE,
                GroundProductionQuality.of(false, false, false, false));
        assertEquals(GroundProductionQuality.BASIC,
                GroundProductionQuality.of(true, false, false, false));
        assertEquals(GroundProductionQuality.ADVANCED_FULL,
                GroundProductionQuality.of(false, true, false, false));
        assertEquals(GroundProductionQuality.ADVANCED_FULL,
                GroundProductionQuality.of(true, true, false, false));
    }

    @Test
    void anyDeficitPullsTheResultOneStepDownAndTwoPullNoFurther() {
        assertEquals(GroundProductionQuality.ADVANCED,
                GroundProductionQuality.of(false, true, true, false));
        assertEquals(GroundProductionQuality.ADVANCED,
                GroundProductionQuality.of(false, true, false, true));
        assertEquals(GroundProductionQuality.ADVANCED,
                GroundProductionQuality.of(false, true, true, true));
        assertEquals(GroundProductionQuality.NONE,
                GroundProductionQuality.of(true, false, true, false));
    }

    @Test
    void theStepDownIsFlooredRatherThanRunningOffTheBottom() {
        assertEquals(GroundProductionQuality.NONE,
                GroundProductionQuality.of(false, false, true, true));
        assertEquals(GroundProductionQuality.NONE, GroundProductionQuality.NONE.lowered());
        assertEquals(GroundProductionQuality.ADVANCED,
                GroundProductionQuality.ADVANCED_FULL.lowered());
    }

    @Test
    void eachStepSaysWhatItCanManufacture() {
        assertEquals(EquipmentGrade.SURPLUS, GroundProductionQuality.NONE.highestGrade());
        assertEquals(EquipmentGrade.SERVICE, GroundProductionQuality.BASIC.highestGrade());
        assertEquals(EquipmentGrade.MILSPEC, GroundProductionQuality.ADVANCED.highestGrade());
        assertEquals(EquipmentGrade.MASTERWORK,
                GroundProductionQuality.ADVANCED_FULL.highestGrade());

        assertEquals(1, GroundProductionQuality.NONE.highestArmorTier());
        assertEquals(2, GroundProductionQuality.BASIC.highestArmorTier());
        assertEquals(3, GroundProductionQuality.ADVANCED.highestArmorTier());
        assertEquals(4, GroundProductionQuality.ADVANCED_FULL.highestArmorTier());

        assertFalse(GroundProductionQuality.NONE.canFabricateMech());
        assertTrue(GroundProductionQuality.BASIC.canFabricateMech());
        assertTrue(GroundProductionQuality.ADVANCED.canFabricateMech());
        assertTrue(GroundProductionQuality.ADVANCED_FULL.canFabricateMech());
    }

    @Test
    void admittanceIsCappedAtTheStepRatherThanOpenEnded() {
        assertTrue(GroundProductionQuality.NONE.admits(EquipmentGrade.SURPLUS));
        assertFalse(GroundProductionQuality.NONE.admits(EquipmentGrade.SERVICE));
        assertTrue(GroundProductionQuality.ADVANCED.admits(EquipmentGrade.MILSPEC));
        assertFalse(GroundProductionQuality.ADVANCED.admits(EquipmentGrade.MASTERWORK));
        assertFalse(GroundProductionQuality.NONE.admitsArmorTier(2));
        assertTrue(GroundProductionQuality.BASIC.admitsArmorTier(2));
        assertFalse(GroundProductionQuality.BASIC.admitsArmorTier(3));
    }
}
