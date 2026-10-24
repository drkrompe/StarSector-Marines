package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.combat.fx.ImpactDecals;
import com.dillon.starsectormarines.battle.combat.fx.ImpactFx;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretMountDef;
import com.dillon.starsectormarines.battle.turret.TurretShieldService;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

import java.util.Random;

/**
 * What a map turret does to the air around it besides the round it fires (`ground-turret-tiers.md`): brass thrown
 * beside a gun that ejects casings, soot laid on the ground ahead of a heavy gun's muzzle, smoke drifting off a barrel once a burst stops, and smoke and sparks off a turret
 * that is badly hurt. Presentation only: it reads the simulation and writes particles, casing and scorch decals, and draws
 * from its own random stream, so the battle plays the same with or without it. The live screen and the review
 * frames feed it the same way, once per simulation step: {@link #onShot} for each shot, then {@link #advance}.
 */
public final class TurretFx {

    /** A barrel stops smoking this long after its last round, so a burst in progress shows its flash, not haze. */
    private static final float QUIET_BEFORE_SMOKE = .22f;
    /** Most heat a barrel holds, in rounds: a long burst smokes longer than a short one, up to this. */
    private static final float MAX_HEAT = 8f;
    /** Below this share of its structure a turret smokes; below {@link #SPARK_BELOW} it also throws sparks. */
    private static final float SMOKE_BELOW = .5f;
    private static final float SPARK_BELOW = .25f;

    /** A break older than this, in seconds, is not vented when first seen: the moment has passed. */
    private static final float VENT_WINDOW = .5f;
    /** Turrets whose current break has vented. */
    private final LongOpenHashSet vented = new LongOpenHashSet();

    /** How far each flux bank stands from the gun, west and east, in cells. */
    private static final float BANK_OFFSET = 1.2f;

    private final Random rng;
    private final Long2ObjectOpenHashMap<Barrel> barrels = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<float[]> damageClocks = new Long2ObjectOpenHashMap<>();

    public TurretFx(long seed) {
        this.rng = new Random(seed);
    }

    /** A barrel that has fired recently: where its last round left and how much smoke it still has in it. */
    private static final class Barrel {
        float x, y, radius, heat, quiet, nextPuff;
    }

    /**
     * Records a turret's round: warms its barrel, lays soot ahead of a gun that scorches the ground
     * ({@code render.scorch}) and, for a gun that ejects brass, throws a casing to its right.
     */
    public void onShot(ShotEvent shot, BattleSimulation sim, ImpactFx fx) {
        StructureDef structure = shot.turretStructureDef;
        if (structure == null) return;
        TurretMountDef mount = structure.mount;
        Barrel barrel = barrels.get(shot.shooterId);
        if (barrel == null) {
            barrel = new Barrel();
            barrels.put(shot.shooterId, barrel);
        }
        barrel.x = shot.fromX;
        barrel.y = shot.fromY;
        barrel.radius = Math.max(.1f, mount.visualCells * .11f);
        barrel.heat = Math.min(MAX_HEAT, barrel.heat + 1f);
        barrel.quiet = 0f;
        barrel.nextPuff = 0f;
        if ((!mount.ejectsCasings && mount.scorchCells <= 0f) || !sim.world().isAlive(shot.shooterId)) return;
        float dx = shot.toX - shot.fromX;
        float dy = shot.toY - shot.fromY;
        float length = (float) Math.hypot(dx, dy);
        if (length < 1e-4f) return;
        float fx0 = dx / length;
        float fy0 = dy / length;
        float cx = sim.world().x(shot.shooterId);
        float cy = sim.world().y(shot.shooterId);
        if (mount.scorchCells > 0f) {
            // On the ground under the muzzle, not at the round's origin: a raised gun's shot leaves from its height.
            ImpactDecals.spawnMuzzleScorch(sim, rng, cx + fx0 * mount.muzzleOffsetCells,
                    cy + fy0 * mount.muzzleOffsetCells, (float) Math.toDegrees(Math.atan2(fy0, fx0)), mount.scorchCells);
        }
        if (!mount.ejectsCasings) return;
        float rightX = fy0;
        float rightY = -fx0;
        float speed = 2.2f + rng.nextFloat() * 1.2f;
        float spread = (rng.nextFloat() - .5f) * .8f;
        fx.spawnCasingGlint(cx + rightX * .3f, cy + rightY * .3f,
                (rightX - fx0 * spread) * speed, (rightY - fy0 * spread) * speed);
        float out = mount.visualCells * .5f + .25f;
        ImpactDecals.spawnShellCasing(sim, rng, cx + rightX * out - fx0 * .2f, cy + rightY * out - fy0 * .2f);
    }

    /**
     * A hardpoint's flux hardware, physically: near breaking, sparks jump off its capacitor banks; when its shield
     * breaks, the banks vent a burst of vapour and sparks ({@link TurretFluxHeat} draws the radiators' heat).
     */
    private void ventBrokenShields(BattleSimulation sim, ImpactFx fx, float dt) {
        TurretShieldService shields = sim.turretShields();
        for (int i = 0, n = shields.size(); i < n; i++) {
            TurretShieldService.Fitted fitted = shields.at(i);
            if (!sim.world().isAlive(fitted.id)) continue;
            float x = sim.world().x(fitted.id);
            float y = sim.world().y(fitted.id);
            // Vent once per break, on the first step that sees it, so a feed that starts late still shows it.
            boolean broke = false;
            if (fitted.shield.overloaded()) {
                broke = fitted.shield.sinceBreak() < VENT_WINDOW && vented.add(fitted.id);
            } else {
                vented.remove(fitted.id);
            }
            float strain = TurretFluxHeat.strain(fitted.shield);
            for (int side = -1; side <= 1; side += 2) {
                // The banks stand on the west and east walls, the length of the plinth.
                float bx = x + side * BANK_OFFSET;
                float by = y + (rng.nextFloat() - .5f) * 1.4f;
                if (broke) {
                    for (int k = 0; k < 3; k++) {
                        fx.spawnAmbientSmoke(bx + (rng.nextFloat() - .5f) * .4f, y + (k - 1) * .6f,
                                .45f + rng.nextFloat() * .3f);
                    }
                    fx.spawnAmbientFire(bx, by, .15f);
                } else if (strain > 0f && rng.nextFloat() < strain * .12f) {
                    fx.spawnAmbientFire(bx, by, .08f + .06f * strain);
                }
            }
        }
    }

    /** Advances barrel smoke and damage smoke by one step of {@code dt} simulation seconds. */
    public void advance(BattleSimulation sim, ImpactFx fx, float dt) {
        for (ObjectIterator<Long2ObjectOpenHashMap.Entry<Barrel>> it = barrels.long2ObjectEntrySet().fastIterator();
             it.hasNext(); ) {
            Barrel barrel = it.next().getValue();
            barrel.quiet += dt;
            if (barrel.quiet < QUIET_BEFORE_SMOKE) continue;
            barrel.nextPuff -= dt;
            if (barrel.nextPuff > 0f) continue;
            float jitter = barrel.radius * .4f;
            fx.spawnAmbientSmoke(barrel.x + (rng.nextFloat() - .5f) * jitter, barrel.y + (rng.nextFloat() - .5f) * jitter,
                    barrel.radius * (.8f + rng.nextFloat() * .5f));
            barrel.heat -= .7f;
            barrel.nextPuff = .1f + rng.nextFloat() * .1f;
            if (barrel.heat <= 0f) it.remove();
        }
        World world = sim.world();
        ventBrokenShields(sim, fx, dt);
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long id = sim.liveUnitAt(i);
            if (!sim.identity().type(id).isTurret() || !world.isAlive(id)) continue;
            float max = world.maxHp(id);
            float share = max > 0f ? world.hp(id) / max : 1f;
            if (share >= SMOKE_BELOW) continue;
            float[] clock = damageClocks.get(id);
            if (clock == null) {
                clock = new float[] {rng.nextFloat() * .3f};
                damageClocks.put(id, clock);
            }
            clock[0] -= dt;
            if (clock[0] > 0f) continue;
            StructureDef structure = sim.turretState().structure(id);
            float size = structure != null ? Math.max(.6f, structure.mount.visualCells * .5f) : .6f;
            float hurt = 1f - share / SMOKE_BELOW;
            float x = world.x(id) + (rng.nextFloat() - .5f) * size * .5f;
            float y = world.y(id) + (rng.nextFloat() - .5f) * size * .5f;
            fx.spawnAmbientSmoke(x, y, size * (.35f + .25f * hurt));
            if (share < SPARK_BELOW && rng.nextFloat() < .45f) fx.spawnAmbientFire(x, y, size * .18f);
            clock[0] = .55f - .4f * hurt + rng.nextFloat() * .15f;
        }
    }
}
