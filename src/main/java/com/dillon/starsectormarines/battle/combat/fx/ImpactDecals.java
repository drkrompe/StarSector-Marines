package com.dillon.starsectormarines.battle.combat.fx;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;

import java.util.Random;

/**
 * Static spawn helpers for persistent impact decals — bullet holes, craters,
 * rubble, shell casings. Lives in the fx package so both
 * {@link com.dillon.starsectormarines.ops.BattleScreen} (ground combat
 * impacts) and {@code com.dillon.starsectormarines.battle.flyby.FlybyOverlay}
 * (aerial strafe + missile impacts) can call the same recipes.
 *
 * <p>The decals themselves are stored on {@link BattleSimulation} so they
 * persist with the battle and reset cleanly on new missions.
 */
public final class ImpactDecals {

    private ImpactDecals() {}

    /**
     * Drops a persistent decal at the impact endpoint, keyed off the visual
     * impact profile and the surface type (wall vs floor). Rifle/kinetic
     * profiles drop bullet holes on walls and small craters on floors; HE
     * profiles drop a medium crater plus a rubble pile (with a small chance
     * of the fire variant).
     */
    public static void spawnImpact(BattleSimulation sim, Random rng,
                                   ImpactProfile profile, float x, float y, boolean isWall) {
        if (profile == null) return;
        float rot = rng.nextFloat() * 360f;
        switch (profile) {
            case RIFLE:
                if (isWall) {
                    sim.addDecal(new Decal(x, y, DecalKind.BULLET_HOLE_SINGLE.index, rot, 0.55f));
                }
                break;
            case KINETIC:
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
                break;
            case HE:
                int craterIdx = rng.nextBoolean()
                        ? DecalKind.CRATER_MEDIUM_A.index
                        : DecalKind.CRATER_MEDIUM_B.index;
                sim.addDecal(new Decal(x, y, craterIdx, rot, 1.30f));
                float jx = x + (rng.nextFloat() * 2f - 1f) * 0.35f;
                float jy = y + (rng.nextFloat() * 2f - 1f) * 0.35f;
                int rubbleIdx;
                float fireRoll = rng.nextFloat();
                if (fireRoll < 0.20f) {
                    rubbleIdx = DecalKind.RUBBLE_FIRE.index;
                } else if (fireRoll < 0.60f) {
                    rubbleIdx = DecalKind.RUBBLE.index;
                } else {
                    rubbleIdx = DecalKind.RUBBLE_ALT.index;
                }
                sim.addDecal(new Decal(jx, jy, rubbleIdx, rng.nextFloat() * 360f, 1.10f));
                break;
        }
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

    /** Visual size of a shell casing in cells. Smaller than other decals — a 9mm next to a marine sprite should read as a fleck, not a log. */
    private static final float CASING_SCALE_CELLS = 0.18f;
    /** Max casings allowed on any single cell — once a marine holds a position long enough to saturate it, new casings drop instead of stacking infinitely. */
    private static final int CASINGS_PER_CELL_CAP = 4;
}
