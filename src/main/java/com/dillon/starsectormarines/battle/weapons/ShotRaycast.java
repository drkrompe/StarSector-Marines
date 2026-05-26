package com.dillon.starsectormarines.battle.weapons;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;

/**
 * Wall-aware shot-endpoint resolution. Ground-deployed area-spread weapons
 * (turret {@code HEAVY_MG} / {@code VULCAN}, mech {@code CHAINGUN}) want a
 * scattered round to splatter on the first wall in its path rather than
 * fly through and hit marines in cover. This helper performs that snap:
 * given the projectile's intended endpoint, it returns the wall cell's
 * center if the line of flight crosses one, plus a flipped hit flag so
 * the caller treats the shot as a miss against the locked target.
 *
 * <p>Used by both {@link com.dillon.starsectormarines.battle.sim.BattleSimulation#fireShotFrom}
 * and {@link HeavyWeapons#fireMechWeapon} so a single change to the
 * raycast convention picks both up.
 *
 * <p>Air-mounted variants pass {@code raycastShots = false}: from altitude
 * they fire over intervening walls, so wall-snap doesn't apply.
 */
public final class ShotRaycast {

    /** Endpoint + hit flag after wall-snap resolution. Returned as a record so the call site reads as a single expression. */
    public record Result(float toX, float toY, boolean hit) {}

    private ShotRaycast() {}

    /**
     * Returns the input endpoint unchanged when {@code raycastShots} is
     * false or no wall sits between origin and endpoint. Otherwise snaps
     * the endpoint to the wall cell's center and flips {@code hit} to
     * false — a wall-blocked round didn't reach the locked target, and an
     * AoE detonation should register at the wall (its damage will be cut
     * off behind the wall by the per-detonation LOS check in
     * {@link Detonations}).
     */
    public static Result resolve(NavigationGrid grid, boolean raycastShots,
                                 float fromX, float fromY,
                                 float toX, float toY,
                                 boolean hit) {
        if (!raycastShots) return new Result(toX, toY, hit);
        int originCx = (int) Math.floor(fromX);
        int originCy = (int) Math.floor(fromY);
        int endCx = (int) Math.floor(toX);
        int endCy = (int) Math.floor(toY);
        long packed = grid.firstWallOnLine(originCx, originCy, endCx, endCy);
        int wx = (int) (packed & 0xFFFFFFFFL);
        int wy = (int) ((packed >>> 32) & 0xFFFFFFFFL);
        if (wx == -1 && wy == -1) return new Result(toX, toY, hit);
        return new Result(wx + 0.5f, wy + 0.5f, false);
    }
}
