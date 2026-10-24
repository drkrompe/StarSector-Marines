package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.mech.MechWeaponComponent.HardpointType;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
    void aFlamersTankIsACellAChargeLitWhileItIsLeft() {
        ChargeGauge tank = ChargeGauge.fuel(8, 3);
        assertEquals(8, tank.cells());
        assertEquals("fuel", tank.iconKind());
        assertEquals(ChargeGauge.Cell.CHARGED, tank.cell(2));
        assertEquals(ChargeGauge.Cell.EMPTY, tank.cell(3));
        assertEquals(ChargeGauge.Cell.EMPTY, ChargeGauge.fuel(8, 0).cell(0));
    }

    @Test
    void anUncountedGunShowsTheBurstAClickSendsAndRechambersIt() {
        ChargeGauge firing = ChargeGauge.burst(6, 4, 0f);
        assertEquals(6, firing.icons());
        assertEquals("rifle", firing.iconKind());
        assertEquals("live", firing.iconState(3));
        assertEquals("spent", firing.iconState(4));
        assertEquals("loading", ChargeGauge.burst(6, 0, .55f).iconState(3));
        assertNull(ChargeGauge.burst(1, 0, 1f), "a single-shot gun has no burst to show");
    }

    @Test
    void aMechHardpointNamesItsOrdnance() {
        assertEquals(Ordnance.MISSILE, Ordnance.of(HardpointType.MISSILE));
        assertEquals(Ordnance.ENERGY, Ordnance.of(HardpointType.ENERGY));
        assertEquals(Ordnance.BALLISTIC, Ordnance.of((WeaponDef) null));
    }
}
