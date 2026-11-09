package com.dillon.starsectormarines.battle.combat.fx;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.fx.WeaponFxDef;

import java.util.Random;
import java.util.function.Consumer;

/**
 * Static spawn helpers for persistent impact decals: the marks rounds leave on the ground and on walls, shell
 * casings and muzzle soot. Lives in the fx package so every caller that puts a mark on the ground reaches the same
 * recipes.
 *
 * <p>A weapon's round leaves the mark of its {@link ImpactMark}: what calibre and what kind of weapon struck. Each
 * mark has a recipe for the ground and one for a wall, drawn from the {@link DecalKind} families; a directional
 * family is laid along the round's line of flight.
 *
 * <p>The decals themselves are stored on {@link BattleSimulation} so they
 * persist with the battle and reset cleanly on new missions.
 */
public final class ImpactDecals {

    private ImpactDecals() {}

    /**
     * The mark a weapon's rounds leave: its authored {@code render.impactMark}, otherwise one read off what it is
     * and how its impact is drawn, so a weapon that names none still marks the ground like its kind. A beam burns, an
     * energy bolt scorches, a shell-burst (explosion or ring layer) craters, a fire layer blasts, a lingering smoke
     * layer is a heavy round, and anything else with an impact is small arms.
     */
    public static ImpactMark markOf(WeaponDef weapon, WeaponFxDef fx) {
        if (weapon != null && weapon.impactMark() != null) return weapon.impactMark();
        if (weapon != null && weapon.hitscanBeam()) return ImpactMark.BEAM;
        if (weapon != null && weapon.energy) return ImpactMark.PULSE;
        if (fx == null) return ImpactMark.NONE;
        if (fx.hasHeavyImpact()) return ImpactMark.SHELL;
        if (fx.hasExplosiveImpact()) return ImpactMark.ROCKET;
        if (fx.hasKineticImpact()) return ImpactMark.HEAVY_ROUND;
        return ImpactMark.SMALL_ARMS;
    }

    /**
     * Drops the decals of a weapon's mark ({@link #markOf}) where its round landed at ({@code x}, {@code y}), having
     * flown from ({@code fromX}, {@code fromY}).
     */
    public static void spawnWeaponImpact(BattleSimulation sim, Random rng, WeaponDef weapon, WeaponFxDef fx,
                                         float fromX, float fromY, float x, float y, boolean isWall) {
        float heading = (float) Math.toDegrees(Math.atan2(y - fromY, x - fromX));
        float splash = weapon != null ? weapon.aoeRadius : 0f;
        spawnMark(sim::addDecal, rng, markOf(weapon, fx), x, y, isWall, heading, splash);
    }

    /**
     * Hands {@code out} the decals of one {@link ImpactMark} at ({@code x}, {@code y}): on a wall cell when {@code isWall}, else
     * on the ground. {@code headingDeg} is the round's line of flight (degrees, cell space, counter-clockwise from
     * +x), which directional marks lie along; {@code splashCells} is its splash radius, which explosive marks are
     * sized to.
     */
    public static void spawnMark(Consumer<Decal> out, Random rng, ImpactMark mark, float x, float y,
                                 boolean isWall, float headingDeg, float splashCells) {
        switch (mark) {
            case NONE -> { }
            case SMALL_ARMS -> {
                if (isWall) lay(out, rng, DecalKind.SMALL_ARMS_WALL, x, y, headingDeg, .5f);
                else if (rng.nextFloat() < SMALL_ARMS_GROUND_CHANCE)
                    lay(out, rng, DecalKind.SMALL_ARMS_GROUND, x, y, headingDeg, .5f);
            }
            case HEAVY_ROUND -> lay(out, rng, isWall ? DecalKind.HEAVY_WALL : DecalKind.HEAVY_GOUGE, x, y, headingDeg,
                    isWall ? .8f : 1.1f);
            case PULSE -> lay(out, rng, DecalKind.PULSE_SCORCH, x, y, headingDeg, .5f);
            case BEAM -> {
                if (rng.nextFloat() < BEAM_CHANCE) lay(out, rng, DecalKind.BEAM_BURN, x, y, headingDeg, .9f);
            }
            case MELT -> lay(out, rng, DecalKind.MELT_CRATER, x, y, headingDeg, Math.max(.8f, splashCells * .9f));
            case FRAG -> lay(out, rng, DecalKind.FRAG, x, y, headingDeg, Math.max(1f, splashCells * 1.2f));
            case FLAK -> lay(out, rng, DecalKind.FRAG, x, y, headingDeg, .9f);
            case ROCKET -> {
                float size = Math.max(.9f, splashCells);
                lay(out, rng, DecalKind.BLAST, x, y, headingDeg, size);
                if (!isWall && rng.nextFloat() < .25f) layNear(out, rng, DecalKind.DEBRIS, x, y, size * .6f);
            }
            case SHELL -> shell(out, rng, x, y, isWall, headingDeg, Math.max(1f, splashCells * .95f), .6f, 0f);
            case HEAVY_SHELL -> shell(out, rng, x, y, isWall, headingDeg, Math.max(1.3f, splashCells), 1f, .35f);
            case INCENDIARY -> lay(out, rng, DecalKind.FIRE_SCORCH, x, y, headingDeg,
                    Math.max(1f, splashCells * 1.15f));
            case FLAME -> {
                if (rng.nextFloat() < FLAME_CHANCE)
                    lay(out, rng, DecalKind.FLAME_SWEEP, x, y, headingDeg, Math.max(1f, splashCells));
            }
            case ACID -> lay(out, rng, rng.nextBoolean() ? DecalKind.ACID_SPLASH : DecalKind.ACID_SPLASH_LONG, x, y,
                    headingDeg, Math.max(.5f, splashCells * .6f));
            case UPHEAVAL -> lay(out, rng, DecalKind.DEBRIS, x, y, headingDeg, Math.max(.8f, splashCells * 1.1f));
        }
    }

    /**
     * A shell's burst: on the ground, a crater with thrown debris ({@code debrisChance}) over a burn
     * ({@code burnChance}); on a wall, a blast scorch round a punched hole.
     */
    private static void shell(Consumer<Decal> out, Random rng, float x, float y, boolean isWall, float headingDeg,
                              float size, float debrisChance, float burnChance) {
        if (isWall) {
            lay(out, rng, DecalKind.BLAST, x, y, headingDeg, size);
            lay(out, rng, DecalKind.HEAVY_WALL, x, y, headingDeg, size * .55f);
            return;
        }
        if (rng.nextFloat() < burnChance) lay(out, rng, DecalKind.FIRE_SCORCH, x, y, headingDeg, size * 1.2f);
        lay(out, rng, DecalKind.SHELL_CRATER, x, y, headingDeg, size);
        if (rng.nextFloat() < debrisChance) layNear(out, rng, DecalKind.DEBRIS, x, y, size * .85f);
    }

    /** Lays one frame of {@code kind}, a little over or under {@code size} cells, turned to the heading or at random. */
    private static void lay(Consumer<Decal> out, Random rng, DecalKind kind, float x, float y, float headingDeg,
                            float size) {
        float rotation = kind.directional
                ? headingDeg + (rng.nextFloat() * 2f - 1f) * DIRECTIONAL_SPREAD_DEG
                : rng.nextFloat() * 360f;
        float scale = size * (1f + (rng.nextFloat() * 2f - 1f) * SIZE_JITTER);
        out.accept(new Decal(x, y, kind.frame(rng), rotation, scale));
    }

    /** Lays one frame of {@code kind} just off ({@code x}, {@code y}), so debris does not sit dead on its crater. */
    private static void layNear(Consumer<Decal> out, Random rng, DecalKind kind, float x, float y, float size) {
        float reach = size * .25f;
        lay(out, rng, kind, x + (rng.nextFloat() * 2f - 1f) * reach, y + (rng.nextFloat() * 2f - 1f) * reach, 0f,
                size);
    }

    /**
     * Drops a small spent shell casing at the shooter's position with a bit
     * of jitter so successive shots from the same cell don't all land on the
     * same pixel. Random pose and rotation give the eye some variety across
     * burst clusters.
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

    /** Share of small-arms rounds into the ground that leave a pock: a long burst marks, without carpeting. */
    private static final float SMALL_ARMS_GROUND_CHANCE = .6f;
    /** Share of a beam's pulses that sear a line: a beam pulses many times a second over much the same spot. */
    private static final float BEAM_CHANCE = .12f;
    /** Share of a flame thrower's rounds that leave a sweep: its jet lands many times over the same ground. */
    private static final float FLAME_CHANCE = .35f;
    /** How far either side of the line of flight a directional mark may lie, in degrees. */
    private static final float DIRECTIONAL_SPREAD_DEG = 8f;
    /** How much over or under its recipe's size a mark may be drawn, as a share of it. */
    private static final float SIZE_JITTER = .12f;
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
