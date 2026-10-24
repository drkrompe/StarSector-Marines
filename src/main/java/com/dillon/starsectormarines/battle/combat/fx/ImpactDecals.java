package com.dillon.starsectormarines.battle.combat.fx;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.fx.WeaponFxDef;

import java.util.Random;

/**
 * Static spawn helpers for persistent impact decals: bullet holes, craters,
 * rubble, shell casings. Lives in the fx package so every caller that puts a
 * mark on the ground reaches the same recipes.
 *
 * <p>The decals themselves are stored on {@link BattleSimulation} so they
 * persist with the battle and reset cleanly on new missions.
 */
public final class ImpactDecals {

    private ImpactDecals() {}

    /** A weapon's authored mark is this share of its splash's radius across, never smaller than the minimum, cells. */
    private static final float AUTHORED_SPLASH_SHARE = 0.6f, AUTHORED_MIN_CELLS = 0.5f;

    /**
     * Drops a persistent decal at a weapon impact: the weapon's own authored mark
     * ({@link WeaponDef#impactDecal()}) where it names one, otherwise the mark its
     * effect layers pick ({@link #spawnWeaponImpact(BattleSimulation, Random, WeaponFxDef, float, float, boolean)}).
     */
    public static void spawnWeaponImpact(BattleSimulation sim, Random rng, WeaponDef weapon,
                                         WeaponFxDef fx, float x, float y, boolean isWall) {
        if (!spawnAuthored(sim, rng, weapon, x, y)) spawnWeaponImpact(sim, rng, fx, x, y, isWall);
    }

    /**
     * Drops the weapon's authored mark ({@link WeaponDef#impactDecal()}) at an impact, turned at random and sized to
     * its splash; a splash of Spitter acid lands as either of its two spatters. Returns false, dropping
     * nothing, when the weapon names no mark.
     */
    public static boolean spawnAuthored(BattleSimulation sim, Random rng, WeaponDef weapon, float x, float y) {
        DecalKind kind = weapon != null ? weapon.impactDecal() : null;
        if (kind == null) return false;
        if (kind == DecalKind.ACID_SPLASH && rng.nextBoolean()) kind = DecalKind.ACID_SPLASH_LONG;
        float size = Math.max(AUTHORED_MIN_CELLS, weapon.aoeRadius * AUTHORED_SPLASH_SHARE);
        sim.addDecal(new Decal(x, y, kind.index, rng.nextFloat() * 360f, size));
        return true;
    }

    /**
     * Drops a persistent decal at a weapon impact. The authored primitives
     * supply the impact character: smoke-only strikes receive kinetic marks,
     * fire receives explosive aftermath, and explosion/ring layers receive
     * the heavier cannon scale.
     */
    public static void spawnWeaponImpact(BattleSimulation sim, Random rng,
                                         WeaponFxDef fx, float x, float y,
                                         boolean isWall) {
        if (fx == null) return;
        if (fx.hasHeavyImpact()) {
            spawnExplosiveImpact(sim, rng, x, y, true);
        } else if (fx.hasExplosiveImpact()) {
            spawnExplosiveImpact(sim, rng, x, y, false);
        } else if (fx.hasKineticImpact()) {
            spawnKineticImpact(sim, rng, x, y, isWall);
        } else if (isWall) {
            sim.addDecal(new Decal(x, y, DecalKind.BULLET_HOLE_SINGLE.index,
                    rng.nextFloat() * 360f, 0.55f));
        }
    }

    /** Explicit kinetic decal recipe for non-weapon aerial strafing effects. */
    public static void spawnKineticImpact(BattleSimulation sim, Random rng,
                                          float x, float y, boolean isWall) {
        float rot = rng.nextFloat() * 360f;
        if (isWall) {
            int idx = rng.nextBoolean()
                    ? DecalKind.BULLET_HOLE_LARGE_SINGLE.index
                    : DecalKind.BULLET_HOLE_LARGE_MULTI.index;
            sim.addDecal(new Decal(x, y, idx, rot, 0.75f));
        } else {
            int idx = rng.nextBoolean()
                    ? DecalKind.CRATER_SMALL.index
                    : DecalKind.CRATER_SMALL_ALT.index;
            sim.addDecal(new Decal(x, y, idx, rot, 0.75f));
        }
    }

    /** Explicit explosive decal recipe for non-weapon aerial detonations. */
    public static void spawnExplosiveImpact(BattleSimulation sim, Random rng,
                                            float x, float y, boolean heavy) {
        float rotation = rng.nextFloat() * 360f;
        if (heavy) {
            spawnHeAftermath(sim, rng, x, y, rotation,
                    1.65f, 1.35f, 0.45f, 0.35f);
        } else {
            spawnHeAftermath(sim, rng, x, y, rotation,
                    1.30f, 1.10f, 0.35f, 0.20f);
        }
    }

    private static void spawnHeAftermath(BattleSimulation sim, Random rng,
                                         float x, float y, float rotation,
                                         float craterScale, float rubbleScale,
                                         float rubbleJitter, float fireChance) {
        int craterIdx = rng.nextBoolean()
                ? DecalKind.CRATER_MEDIUM_A.index
                : DecalKind.CRATER_MEDIUM_B.index;
        sim.addDecal(new Decal(x, y, craterIdx, rotation, craterScale));
        float jx = x + (rng.nextFloat() * 2f - 1f) * rubbleJitter;
        float jy = y + (rng.nextFloat() * 2f - 1f) * rubbleJitter;
        int rubbleIdx;
        float fireRoll = rng.nextFloat();
        if (fireRoll < fireChance) {
            rubbleIdx = DecalKind.RUBBLE_FIRE.index;
        } else if (fireRoll < fireChance + (1f - fireChance) * 0.5f) {
            rubbleIdx = DecalKind.RUBBLE.index;
        } else {
            rubbleIdx = DecalKind.RUBBLE_ALT.index;
        }
        sim.addDecal(new Decal(jx, jy, rubbleIdx,
                rng.nextFloat() * 360f, rubbleScale));
    }

    /**
     * Drops a small spent shell casing at the shooter's position with a bit
     * of jitter so successive shots from the same cell don't all land on the
     * same pixel. Random pose (4 vs 5) and rotation give the eye some variety
     * across burst clusters.
     *
     * <p>Bails out when the shooter's cell already has {@link #CASINGS_PER_CELL_CAP}
     * casings — long SMG suppression runs would otherwise carpet the floor.
     * Casings render at the same z-layer as other decals so they sit on the
     * ground under units + vehicles.
     */
    public static void spawnShellCasing(BattleSimulation sim, Random rng, float x, float y) {
        int cellX = (int) Math.floor(x);
        int cellY = (int) Math.floor(y);
        int count = 0;
        for (Decal d : sim.getDecals()) {
            if (d.decalIndex != DecalKind.SHELL_CASING.index
                    && d.decalIndex != DecalKind.SHELL_CASING_ALT.index) continue;
            if ((int) Math.floor(d.x) != cellX || (int) Math.floor(d.y) != cellY) continue;
            if (++count >= CASINGS_PER_CELL_CAP) return;
        }
        int idx = rng.nextBoolean() ? DecalKind.SHELL_CASING.index : DecalKind.SHELL_CASING_ALT.index;
        float jx = x + (rng.nextFloat() * 2f - 1f) * 0.25f;
        float jy = y + (rng.nextFloat() * 2f - 1f) * 0.25f;
        float rot = rng.nextFloat() * 360f;
        sim.addDecal(new Decal(jx, jy, idx, rot, CASING_SCALE_CELLS));
    }

    /**
     * Lays soot on the ground ahead of a heavy gun's muzzle as a round leaves: a {@link DecalKind#MUZZLE_SCORCH}
     * fan about {@code lengthCells} long, laid along {@code headingDeg} (degrees, cell space, counter-clockwise from
     * +x) with its narrow end at the muzzle. Each round's fan is turned and sized a little differently, so a long
     * engagement builds an irregular mark that darkens as the stamps overlap; once {@link #SCORCH_STACK_CAP} fans lie
     * round the same spot, further rounds add nothing there, so the ground in front of a gun is never carpeted.
     */
    public static void spawnMuzzleScorch(BattleSimulation sim, Random rng, float muzzleX, float muzzleY,
                                         float headingDeg, float lengthCells) {
        float rotation = headingDeg + (rng.nextFloat() * 2f - 1f) * SCORCH_SPREAD_DEG;
        float length = lengthCells * (.9f + rng.nextFloat() * .2f);
        double radians = Math.toRadians(rotation);
        float cx = muzzleX + (float) Math.cos(radians) * length * .5f;
        float cy = muzzleY + (float) Math.sin(radians) * length * .5f;
        float crowd = lengthCells * SCORCH_CROWD_RADIUS;
        float crowdSq = crowd * crowd;
        int count = 0;
        for (Decal d : sim.getDecals()) {
            if (d.decalIndex != DecalKind.MUZZLE_SCORCH.index) continue;
            float dx = d.x - cx;
            float dy = d.y - cy;
            if (dx * dx + dy * dy > crowdSq) continue;
            if (++count >= SCORCH_STACK_CAP) return;
        }
        sim.addDecal(new Decal(cx, cy, DecalKind.MUZZLE_SCORCH.index, rotation, length));
    }

    /** How far either side of the gun's bearing a round's soot fan may lie, in degrees. */
    private static final float SCORCH_SPREAD_DEG = 6f;
    /** Fans centred within this share of their length of one another count as the same mark for the cap. */
    private static final float SCORCH_CROWD_RADIUS = .3f;
    /** Most soot fans stacked on one spot: enough to darken, too few to blacken. */
    private static final int SCORCH_STACK_CAP = 5;

    /** Visual size of a shell casing in cells. Smaller than other decals — a 9mm next to a marine sprite should read as a fleck, not a log. */
    private static final float CASING_SCALE_CELLS = 0.18f;
    /** Max casings allowed on any single cell — once a marine holds a position long enough to saturate it, new casings drop instead of stacking infinitely. */
    private static final int CASINGS_PER_CELL_CAP = 4;
}
