package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.mech.MechWeaponComponent.HardpointType;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** An energy weapon's charge cells, and which loads draw as cells at all. */
class ChargeGaugeTest {

    @Test
    void aBurstEmptiesACellABoltAndASingleShotFillsAsABar() {
        ChargeGauge burst = ChargeGauge.of(4, 2, 0f, false);
        assertEquals(4, burst.cells());
        assertEquals(ChargeGauge.Cell.CHARGED, burst.cell(1));
        assertEquals(ChargeGauge.Cell.EMPTY, burst.cell(2));
        ChargeGauge single = ChargeGauge.of(1, 0, .5f, false);
        assertEquals(ChargeGauge.SINGLE_SHOT_CELLS, single.cells());
        assertEquals(ChargeGauge.Cell.CHARGED, single.cell(1));
        assertEquals(ChargeGauge.Cell.CHARGING, single.cell(2));
        assertEquals(ChargeGauge.Cell.HELD, ChargeGauge.of(4, 0, 1f, true).cell(0));
    }

    @Test
    void aMechHardpointNamesItsOrdnance() {
        assertEquals(Ordnance.MISSILE, Ordnance.of(HardpointType.MISSILE));
        assertEquals(Ordnance.ENERGY, Ordnance.of(HardpointType.ENERGY));
        assertEquals(Ordnance.BALLISTIC, Ordnance.of((WeaponDef) null));
    }
}
