package com.dillon.starsectormarines.battle.appearance;

import com.dillon.starsectormarines.battle.appearance.LiveAppearance.EightWayFacing;
import com.dillon.starsectormarines.battle.appearance.LiveAppearance.Facing;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Golden-table coverage for {@link LiveAppearance} — every expected value below
 * is <b>hand-written</b> against {@code ops.battleview.UnitRenderService}'s
 * former per-frame derivation ({@code pickFrame}/{@code pickFrameEightWay}/
 * {@code facingFromDelta}/{@code eightWayFromDelta}/{@code weaponUp}), not
 * computed by calling the code under test. Pins the tables against the old
 * renderer's behavior through Phase 2, when the renderer's private copies are
 * deleted and this class becomes the only implementation.
 */
public class LiveAppearanceTest {

    @Test
    public void pickFrameIdlePoses() {
        assertEquals(0, LiveAppearance.pickFrame(Facing.WEST, false));
        assertEquals(1, LiveAppearance.pickFrame(Facing.NORTH, false));
        assertEquals(2, LiveAppearance.pickFrame(Facing.EAST, false));
        assertEquals(3, LiveAppearance.pickFrame(Facing.SOUTH, false));
    }

    @Test
    public void pickFrameWeaponUpPoses() {
        assertEquals(4, LiveAppearance.pickFrame(Facing.WEST, true));
        assertEquals(6, LiveAppearance.pickFrame(Facing.NORTH, true));
        assertEquals(5, LiveAppearance.pickFrame(Facing.EAST, true));
        assertEquals(6, LiveAppearance.pickFrame(Facing.SOUTH, true));
    }

    @Test
    public void flipVOnlyForSouthWeaponUp() {
        assertTrue(LiveAppearance.flipV(Facing.SOUTH, true));
        assertFalse(LiveAppearance.flipV(Facing.SOUTH, false));
        assertFalse(LiveAppearance.flipV(Facing.WEST, true));
        assertFalse(LiveAppearance.flipV(Facing.NORTH, true));
        assertFalse(LiveAppearance.flipV(Facing.EAST, true));
    }

    @Test
    public void pickFrameEightWayTable() {
        assertEquals(0, LiveAppearance.pickFrameEightWay(EightWayFacing.W));
        assertEquals(1, LiveAppearance.pickFrameEightWay(EightWayFacing.NW));
        assertEquals(2, LiveAppearance.pickFrameEightWay(EightWayFacing.SE));
        assertEquals(3, LiveAppearance.pickFrameEightWay(EightWayFacing.S));
        assertEquals(4, LiveAppearance.pickFrameEightWay(EightWayFacing.SW));
        assertEquals(5, LiveAppearance.pickFrameEightWay(EightWayFacing.NE));
        assertEquals(6, LiveAppearance.pickFrameEightWay(EightWayFacing.E));
        assertEquals(1, LiveAppearance.pickFrameEightWay(EightWayFacing.N), "N borrows NW's frame");
    }

    @Test
    public void facingFromDeltaTable() {
        assertEquals(Facing.EAST, LiveAppearance.facingFromDelta(1, 0));
        assertEquals(Facing.WEST, LiveAppearance.facingFromDelta(-1, 0));
        assertEquals(Facing.NORTH, LiveAppearance.facingFromDelta(0, 1));
        assertEquals(Facing.SOUTH, LiveAppearance.facingFromDelta(0, -1));
        assertEquals(Facing.EAST, LiveAppearance.facingFromDelta(2, 1));
        assertEquals(Facing.NORTH, LiveAppearance.facingFromDelta(1, 2));
        assertEquals(Facing.WEST, LiveAppearance.facingFromDelta(-2, -1));
        assertEquals(Facing.SOUTH, LiveAppearance.facingFromDelta(-1, -2));
    }

    @Test
    public void eightWayFromDeltaCardinals() {
        assertEquals(EightWayFacing.E, LiveAppearance.eightWayFromDelta(1, 0));
        assertEquals(EightWayFacing.W, LiveAppearance.eightWayFromDelta(-1, 0));
        assertEquals(EightWayFacing.N, LiveAppearance.eightWayFromDelta(0, 1));
        assertEquals(EightWayFacing.S, LiveAppearance.eightWayFromDelta(0, -1));
    }

    @Test
    public void eightWayFromDeltaPureDiagonals() {
        assertEquals(EightWayFacing.NE, LiveAppearance.eightWayFromDelta(1, 1));
        assertEquals(EightWayFacing.SE, LiveAppearance.eightWayFromDelta(1, -1));
        assertEquals(EightWayFacing.NW, LiveAppearance.eightWayFromDelta(-1, 1));
        assertEquals(EightWayFacing.SW, LiveAppearance.eightWayFromDelta(-1, -1));
    }

    @Test
    public void eightWayFromDeltaThreshold() {
        // min*1000 >= max*414: (4,10) -> 4000 < 4140 -> cardinal E; (5,10) -> 5000 >= 4140 -> diagonal NE.
        assertEquals(EightWayFacing.E, LiveAppearance.eightWayFromDelta(10, 4));
        assertEquals(EightWayFacing.NE, LiveAppearance.eightWayFromDelta(10, 5));
        // Mirrored negatives exercise the other three diagonal quadrants at the same threshold.
        assertEquals(EightWayFacing.W, LiveAppearance.eightWayFromDelta(-10, 4));
        assertEquals(EightWayFacing.NW, LiveAppearance.eightWayFromDelta(-10, 5));
        assertEquals(EightWayFacing.SE, LiveAppearance.eightWayFromDelta(10, -5));
        assertEquals(EightWayFacing.SW, LiveAppearance.eightWayFromDelta(-10, -5));
    }

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

    @Test
    public void southIdleFrameMatchesBothFrameTables() {
        assertEquals(3, LiveAppearance.SOUTH_IDLE_FRAME);
        assertEquals(LiveAppearance.SOUTH_IDLE_FRAME, LiveAppearance.pickFrame(Facing.SOUTH, false));
        assertEquals(LiveAppearance.SOUTH_IDLE_FRAME, LiveAppearance.pickFrameEightWay(EightWayFacing.S));
    }
}
