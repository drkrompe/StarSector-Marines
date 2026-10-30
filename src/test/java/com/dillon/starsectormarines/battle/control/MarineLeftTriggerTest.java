package com.dillon.starsectormarines.battle.control;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Marine left button's cycle and manual contract, as pure state. */
class MarineLeftTriggerTest {
    private static final float DT = BattleSimulation.TICK_DT;

    @Test
    void onlyCarriedDirectFireSpecialsThatSpendAmmunitionJoinTheCycle() {
        assertEquals(List.of(), MarineLeftTrigger.specials(null));
        assertEquals(List.of(), MarineLeftTrigger.specials(
                SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SMOKE_GRENADE_ID)));
        for (String id : List.of(SpecialEquipmentRegistry.SRM_1_ID,
                SpecialEquipmentRegistry.SRM_1_QS_ID, SpecialEquipmentRegistry.ANTI_MATERIEL_RIFLE_ID)) {
            var special = SpecialEquipmentRegistry.require(id);
            assertEquals(List.of(special), MarineLeftTrigger.specials(special), id);
        }
        var flamer = SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.FLAMER_ID);
        assertTrue(flamer.isHeldStream());
        assertEquals(List.of(flamer), MarineLeftTrigger.specials(flamer));
        assertFalse(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.FRAG_GRENADE_ID).isHeldStream());
    }

    @Test
    void aHeldStreamKeepsFiringEachTimeItCanUntilReleased() {
        MarineLeftTrigger trigger = new MarineLeftTrigger();
        trigger.pick(1, 2, 2 * DT);
        int fired = 0;
        for (int i = 0; i < 12; i++) {
            // The flamer's own cooldown allows a charge on every fourth tick.
            if (trigger.tick(true, false, i % 4 == 3, true, DT)) {
                fired++;
                trigger.fired();
            }
        }
        assertEquals(3, fired);
        assertFalse(trigger.tick(false, false, true, true, DT));
        assertFalse(trigger.tick(false, false, true, true, DT), "a released button sends nothing more");
    }

    @Test
    void cycleWrapsBackToThePrimaryAndRefusesAListOfOne() {
        MarineLeftTrigger trigger = new MarineLeftTrigger();
        assertFalse(trigger.cycle(1, 1f));
        assertTrue(trigger.cycle(2, 1f));
        assertTrue(trigger.onSpecial());
        assertTrue(trigger.cycle(2, 1f));
        assertEquals(MarineLeftTrigger.PRIMARY, trigger.pick());
    }

    @Test
    void aPressDuringTheWindUpFiresOnceWhenReadyAndAHeldButtonNeverFiresAgain() {
        MarineLeftTrigger trigger = new MarineLeftTrigger();
        trigger.pick(1, 2, 10 * DT);
        assertFalse(trigger.ready());
        int fired = 0;
        for (int i = 0; i < 40; i++) {
            if (trigger.tick(true, false, true, DT)) {
                fired++;
                trigger.fired();
                assertTrue(i >= 9, "fired before the wind-up was over, at tick " + i);
            }
        }
        assertEquals(1, fired);
        assertTrue(trigger.ready());
        assertFalse(trigger.tick(false, false, true, DT));
        assertTrue(trigger.tick(true, false, true, DT), "a fresh press fires at once when ready");
    }

    @Test
    void releasingBeforeReadyDropsThePress() {
        MarineLeftTrigger trigger = new MarineLeftTrigger();
        trigger.pick(1, 2, 3 * DT);
        assertFalse(trigger.tick(true, false, true, DT));
        assertFalse(trigger.tick(false, false, true, DT));
        for (int i = 0; i < 5; i++) assertFalse(trigger.tick(false, false, true, DT));
    }

    @Test
    void anEmptySpecialKeepsThePickAndFiresNothing() {
        MarineLeftTrigger trigger = new MarineLeftTrigger();
        trigger.pick(1, 2, 0f);
        for (int i = 0; i < 10; i++) {
            assertFalse(trigger.tick(i % 2 == 0, false, false, DT));
        }
        assertEquals(1, trigger.pick());
    }

    @Test
    void sprintingLowersTheSpecialAndItIsReadiedAgain() {
        MarineLeftTrigger trigger = new MarineLeftTrigger();
        trigger.pick(1, 2, 4 * DT);
        for (int i = 0; i < 5; i++) trigger.tick(false, false, true, DT);
        assertTrue(trigger.ready());
        trigger.tick(false, true, true, DT);
        assertFalse(trigger.ready());
        assertEquals(0f, trigger.readiness(), 1e-6f);
    }

    @Test
    void resetReturnsToThePrimaryWithNothingArmed() {
        MarineLeftTrigger trigger = new MarineLeftTrigger();
        trigger.pick(1, 2, 0f);
        trigger.tick(true, false, false, DT);
        trigger.reset();
        assertEquals(MarineLeftTrigger.PRIMARY, trigger.pick());
        assertEquals(1f, trigger.readiness());
        trigger.pick(1, 2, 0f);
        assertTrue(trigger.tick(true, false, true, DT), "a button held across the reset is a fresh press");
    }
}
