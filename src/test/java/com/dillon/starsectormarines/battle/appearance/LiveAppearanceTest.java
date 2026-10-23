package com.dillon.starsectormarines.battle.appearance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Coverage for {@link LiveAppearance#weaponUp}. */
public class LiveAppearanceTest {

    @Test
    public void weaponUpInAimAlwaysTrue() {
        assertTrue(LiveAppearance.weaponUp(true, true, 0f, 0f));
        assertTrue(LiveAppearance.weaponUp(true, false, 0f, 0f));
    }

    @Test
    public void weaponUpCombatantCooldownWindow() {
        // attackCooldown 1.0, WEAPON_UP_TIME 0.25 -> the window is (0.75, 1.0].
        assertTrue(LiveAppearance.weaponUp(false, true, 0.8f, 1.0f), "inside the window");
        assertTrue(LiveAppearance.weaponUp(false, true, 1.0f, 1.0f), "at the top of the window (just reset)");
        assertFalse(LiveAppearance.weaponUp(false, true, 0.75f, 1.0f), "exactly at the lower bound (not >)");
        assertFalse(LiveAppearance.weaponUp(false, true, 0.5f, 1.0f), "below the window");
    }

    @Test
    public void weaponUpZeroCooldownTimerAlwaysFalse() {
        // attackCooldown small enough that cooldownTimer=0 would clear the
        // "> attackCooldown - WEAPON_UP_TIME" bound on its own — isolates the
        // separate "cooldownTimer > 0f" guard.
        assertFalse(LiveAppearance.weaponUp(false, true, 0f, 0.1f));
    }

    @Test
    public void weaponUpNonCombatantNonAimAlwaysFalse() {
        assertFalse(LiveAppearance.weaponUp(false, false, 1.0f, 1.0f));
        assertFalse(LiveAppearance.weaponUp(false, false, 0f, 0f));
    }

}
