package com.dillon.starsectormarines.battle.mech;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A trigger's burst spreads its rounds over time, and the trigger cannot come round before its burst has gone. */
class MechBurstCadenceTest {

    @Test
    void aTriggerWaitsOutItsOwnBurst() {
        assertEquals(1.8f, MechWeaponComponent.triggerInterval(1.8f, 0.39f));
        assertEquals(0.5f, MechWeaponComponent.triggerInterval(0.2f, 0.5f), "a cycle shorter than its burst is the burst");
    }

    @Test
    void aBurstSpansItsRoundsAtTheWeaponsSpacing() {
        MechWeaponComponent laser = MechWeaponComponent.PL_3;
        assertEquals((laser.projectilesPerTrigger - 1) * laser.weaponDef().burstSpacing, laser.burstSeconds(), 1e-6f);
        assertTrue(laser.burstSeconds() > 0f, "a burst laser's pulses leave one after another, not at once");
        assertEquals(0f, MechWeaponComponent.AC_6.burstSeconds(), "a rotary gun has no trigger burst");
        for (MechWeaponComponent component : MechWeaponComponent.values()) {
            assertTrue(component.cycleSeconds() >= component.burstSeconds(), component.id);
        }
    }

    @Test
    void eachMountNumbersItsOwnRounds() {
        MechWeaponMount left = new MechWeaponMount(MechMountSlot.ARMS, MechWeaponComponent.CHAINGUN_HF);
        MechWeaponMount right = new MechWeaponMount(MechMountSlot.ARMS, MechWeaponComponent.CHAINGUN_HF);
        assertEquals(0, left.releaseRound());
        assertEquals(1, left.releaseRound());
        assertEquals(0, right.releaseRound(), "a second gun's count is its own");
        assertEquals(2, left.releaseRound());
    }

    @Test
    void aClickFiresOnePulseAndQueuesTheRestASpacingApart() {
        MechWeaponMount mount = new MechWeaponMount(MechMountSlot.ARMS, MechWeaponComponent.PL_2);
        mount.commitTrigger(7L, null);
        assertEquals(mount.component.projectilesPerTrigger - 1, mount.burstRemaining);
        assertEquals(mount.weaponDef().burstSpacing, mount.burstTimer, 1e-6f);
        assertEquals(7L, mount.burstTargetId, "the AI's pulses stay on the body the click was fired at");
        mount.burstRemaining = 0;
        mount.advanceClocks(mount.component.cycleSeconds());
        assertTrue(mount.triggerReady(), "a held trigger pulls again once the cycle has run");
    }
}
