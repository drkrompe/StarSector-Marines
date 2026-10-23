package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.weapon.WeaponDef;

import java.util.Random;

/**
 * The rules a player's trigger at a cursor point obeys, whichever carrier holds the weapon: which weapons can be
 * aimed at ground at all, how near an arcing one may drop, and where a thrown arcing round lands. A mech's
 * {@link HeavyWeapons} and a vehicle turret's {@code TurretFireSystem} both read them, so a rack fires to the
 * same contract on either hull.
 */
public final class ManualPointFire {

    /**
     * The nearest a pilot may drop an arcing round, in cells from the shooter: closer than this the round is
     * still climbing, and the trigger is refused rather than lobbed onto the shooter's own head.
     */
    public static final float MIN_ARC_RANGE_CELLS = 8f;

    private ManualPointFire() { }

    /** Any ranged weapon, flat or arcing, can be fired at a cursor point. Ammunition is readiness, not eligibility. */
    public static boolean supportsPointFire(WeaponDef weapon) {
        return weapon != null && Float.isFinite(weapon.range) && weapon.range > 0f;
    }

    /** A round that rises over the point rather than flying a lane to it. */
    public static boolean arcs(WeaponDef weapon) {
        return weapon.arcHeight != 0f || weapon.indirectFire;
    }

    /**
     * The nearest point, in cells from the shooter, a player may aim this weapon at: its authored minimum for a
     * flat-fire weapon, at least {@link #MIN_ARC_RANGE_CELLS} for an arcing one, zero for one that cannot point-fire.
     */
    public static float minRangeCells(WeaponDef weapon) {
        if (!supportsPointFire(weapon)) return 0f;
        float authored = Math.max(0f, weapon.minRange);
        return arcs(weapon) ? Math.max(authored, MIN_ARC_RANGE_CELLS) : authored;
    }

    /** Whether a point {@code distance} cells from the shooter is inside this weapon's manual minimum. */
    public static boolean tooClose(WeaponDef weapon, float distance) {
        return Float.isFinite(distance) && distance < minRangeCells(weapon);
    }

    /** A rocket with a warhead: a round a player throws at ground rather than along a bearing. */
    public static boolean bursts(WeaponDef weapon) {
        return weapon.interceptableProjectile && weapon.aoeRadius > 0f;
    }

    /**
     * The radius, in cells, of the disk a player's round lands in when aimed {@code distance} cells from the
     * shooter, with the shooter's own {@code accuracy}; a point nearer than {@code standoffCells} is thrown at that
     * distance. Negative for a weapon whose rounds do not burst where they are thrown (a gun). Smoke on a rocket's
     * lane widens its disk further when it fires; this is the clear-air figure.
     */
    public static float scatterRadiusCells(WeaponDef weapon, float accuracy, float distance, float standoffCells) {
        if (!supportsPointFire(weapon) || !arcs(weapon) && !bursts(weapon)) return -1f;
        float thrown = Math.min(Math.max(distance, standoffCells),
                weapon.range * BallisticResolver.MAX_GROUND_BURST_RANGE_MULTIPLIER);
        return GroundBurstAim.errorRadius(accuracy, weapon.hitSpread, weapon.range, thrown);
    }

    /**
     * Where a thrown arcing round lands: the aim point, its distance from {@code (originX, originY)} clamped to
     * {@link BallisticResolver#MAX_GROUND_BURST_RANGE_MULTIPLIER} times the weapon's range, scattered about by
     * {@link GroundBurstAim}. An arcing round passes over what stands between, so nothing on the lane stops it.
     */
    public static ShotEndpoint.Endpoint arcEndpoint(float originX, float originY, float aimX, float aimY,
                                                    float accuracy, WeaponDef weapon, Random rng) {
        float dx = aimX - originX;
        float dy = aimY - originY;
        float distance = (float) Math.hypot(dx, dy);
        float thrown = Math.min(distance, weapon.range * BallisticResolver.MAX_GROUND_BURST_RANGE_MULTIPLIER);
        GroundBurstAim.Offset scatter = GroundBurstAim.sample(accuracy, weapon.hitSpread, weapon.range, thrown, rng);
        float scale = distance > 1e-6f ? thrown / distance : 0f;
        return new ShotEndpoint.Endpoint(originX + dx * scale + scatter.dx(), originY + dy * scale + scatter.dy());
    }
}
