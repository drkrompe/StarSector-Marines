package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UnitRenderServiceScaleTest {

    @Test
    void roomAndBattleConsumersSharePhysicalLayeredScaleMath() {
        float cell = 48f;
        float bulwark = UnitRenderService.layeredMechHullWidth(
                cell, MechVariant.BULWARK.renderScale);
        float hound = UnitRenderService.layeredMechHullWidth(
                cell, MechVariant.HOUND.renderScale);

        assertEquals(cell * MechVariant.BULWARK.renderScale * 0.82f * 1.40f,
                bulwark, 0.001f);
        assertEquals(MechVariant.BULWARK.renderScale / MechVariant.HOUND.renderScale,
                bulwark / hound, 0.001f);
        assertEquals(cell * UnitType.ENGINEER.renderScale * 0.60f,
                UnitRenderService.layeredInfantryShoulderWidth(
                        cell, UnitType.ENGINEER.renderScale), 0.001f);
    }
}
