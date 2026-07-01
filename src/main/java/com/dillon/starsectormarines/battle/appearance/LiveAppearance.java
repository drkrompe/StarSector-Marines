package com.dillon.starsectormarines.battle.appearance;

/**
 * Pure facing/frame derivations for a live sheet-drawn unit — the facing→frame
 * and weapon-up math {@code FacingSystem} calls to author the {@code SPRITE}
 * component each tick. Ported verbatim from {@code
 * ops.battleview.UnitRenderService}'s former per-frame derivation
 * ({@code computeFacing}/{@code computeEightWayFacing}/{@code pickFrame}/
 * {@code pickFrameEightWay}/{@code facingFromDelta}/{@code eightWayFromDelta}) —
 * same tables, same thresholds, no "improvements". Since Phase 2 flipped the
 * renderer to read the authored {@code SPRITE} columns, this class is the
 * <em>only</em> copy of the derivation.
 *
 * <p>Stateless; the {@link com.dillon.starsectormarines.battle.air.AirAppearance AirAppearance} sibling —
 * a system advances authored component scalars into the world each tick, and a
 * stateless helper here turns them into the concrete draw parameters, unit-
 * testable off the system. {@code LiveAppearanceTest} pins these tables against
 * hand-written golden values carried over from the deleted renderer copies.
 */
public final class LiveAppearance {

    /** Window (s) after a unit fires during which the weapon-up pose shows. */
    public static final float WEAPON_UP_TIME = 0.25f;

    /** {@code SPRITE_SHEET} selector: the type's base sheet. */
    public static final int SHEET_BASE = 0;
    /** {@code SPRITE_SHEET} selector: the secondary-aim sheet (in effect while aiming a secondary weapon). */
    public static final int SHEET_SECONDARY_AIM = 1;

    /**
     * The south-idle frame — {@code pickFrame(SOUTH, false) == 3} and the
     * 8-way {@code S} frame is also 3. The spawn seed
     * {@code UnitRosterService.allocate} writes into {@code SPRITE_INDEX} so a
     * unit spawned between this tick's facing pass and render still draws
     * sanely.
     */
    public static final int SOUTH_IDLE_FRAME = 3;

    private LiveAppearance() {}

    /** Four-way facing — the {@link com.dillon.starsectormarines.battle.unit.UnitType.FrameLayout#WNES_WEAPON_UP} convention. */
    public enum Facing { WEST, NORTH, EAST, SOUTH }

    /** Eight-way facing — the {@link com.dillon.starsectormarines.battle.unit.UnitType.FrameLayout#EIGHT_WAY_NO_WEAPON_UP} convention. */
    public enum EightWayFacing { W, NW, N, NE, E, SE, S, SW }

    /**
     * Whether the weapon-up pose shows: while aiming a secondary ({@code inAim}),
     * or — for a combatant — during the trailing {@link #WEAPON_UP_TIME} window
     * after a primary shot (the cooldown timer freshly reset and still counting
     * down from it). A non-combatant with no aim in progress is never weapon-up.
     */
    public static boolean weaponUp(boolean inAim, boolean combatant, float cooldownTimer, float attackCooldown) {
        return inAim || (combatant && cooldownTimer > attackCooldown - WEAPON_UP_TIME && cooldownTimer > 0f);
    }

    /** Four-way facing from a cell delta — ties go to N/S (the axis compared last). */
    public static Facing facingFromDelta(int dx, int dy) {
        if (Math.abs(dx) > Math.abs(dy)) return dx > 0 ? Facing.EAST : Facing.WEST;
        return dy > 0 ? Facing.NORTH : Facing.SOUTH;
    }

    /**
     * Eight-way facing from a cell delta. A delta counts as diagonal when the
     * smaller axis is at least {@code 0.414} (tan 22.5°) of the larger —
     * {@code min*1000 >= max*414} is that ratio scaled to integer math.
     */
    public static EightWayFacing eightWayFromDelta(int dx, int dy) {
        int adx = Math.abs(dx);
        int ady = Math.abs(dy);
        boolean diag = Math.min(adx, ady) * 1000 >= Math.max(adx, ady) * 414;
        if (diag) {
            if (dx > 0 && dy > 0) return EightWayFacing.NE;
            if (dx > 0 && dy < 0) return EightWayFacing.SE;
            if (dx < 0 && dy > 0) return EightWayFacing.NW;
            return EightWayFacing.SW;
        }
        if (adx > ady) return dx > 0 ? EightWayFacing.E : EightWayFacing.W;
        return dy > 0 ? EightWayFacing.N : EightWayFacing.S;
    }

    /**
     * Four-way frame index for {@code facing} — {@code WNES_WEAPON_UP}: idle is
     * 0=W, 1=N, 2=E, 3=S; weapon-up is 4=W, 5=E, 6=N, 6=S (S reuses the N
     * weapon-up frame; the caller applies a vertical mirror via {@link
     * #flipV}).
     */
    public static int pickFrame(Facing facing, boolean weaponUp) {
        if (weaponUp) {
            switch (facing) {
                case WEST:  return 4;
                case EAST:  return 5;
                case NORTH: return 6;
                case SOUTH: return 6; // vertical mirror applied by the caller via flipV
            }
        } else {
            switch (facing) {
                case WEST:  return 0;
                case NORTH: return 1;
                case EAST:  return 2;
                case SOUTH: return 3;
            }
        }
        return 3;
    }

    /**
     * Eight-way frame index for {@code f} — {@code EIGHT_WAY_NO_WEAPON_UP}: 0=W,
     * 1=NW, 2=SE, 3=S, 4=SW, 5=NE, 6=E; {@code N} has no dedicated frame and
     * borrows NW's.
     */
    public static int pickFrameEightWay(EightWayFacing f) {
        switch (f) {
            case W:  return 0;
            case NW: return 1;
            case SE: return 2;
            case S:  return 3;
            case SW: return 4;
            case NE: return 5;
            case E:  return 6;
            case N:  return 1; // no dedicated N — borrow NW
        }
        return 3;
    }

    /** Whether the weapon-up frame needs the vertical mirror — true only for a SOUTH-facing weapon-up pose. */
    public static boolean flipV(Facing facing, boolean weaponUp) {
        return weaponUp && facing == Facing.SOUTH;
    }
}
