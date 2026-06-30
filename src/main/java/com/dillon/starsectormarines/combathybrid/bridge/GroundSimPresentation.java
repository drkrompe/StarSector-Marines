package com.dillon.starsectormarines.combathybrid.bridge;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.combat.fx.ImpactFx;
import com.dillon.starsectormarines.battle.combat.fx.ImpactProfile;
import com.dillon.starsectormarines.battle.mech.MechWeapon;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vision.BuildingVisibilityPass;
import com.dillon.starsectormarines.ops.battleview.BattleRenderer;
import com.fs.starfarer.api.Global;
import org.lwjgl.util.vector.Vector2f;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Per-frame combat presentation for the bridge sim: spawns + ages shot-impact particles, plays the
 * matching positional combat audio, and parks the OpenAL listener over the ground band. This is the
 * piece that was missing under the fleet — the standalone {@code BattleScreen.advance} does all of
 * this inline, but the bridge host never wired it, so a ground battle ran silent and impact-FX-less
 * below the ships.
 *
 * <p><b>Deliberately a slimmer driver than the standalone, not a shared one.</b> The bridge renders
 * a different subset of layers and lives in a different audio frame, so a verbatim reuse wouldn't
 * fit:
 * <ul>
 *   <li><b>No lights, no decals.</b> The standalone interleaves {@code WeaponLights} (LIGHTING) and
 *       {@code ImpactDecals} (DECALS) spawns with the particle spawns. The bridge draws neither pass
 *       ({@link GroundBattleConfig#DEFAULT_SCENE_LAYERS} omits them — they're screen-space FBO
 *       accumulators awaiting projection-retarget, S3j; LIGHTING is also slated for removal), so we
 *       feed only the camera-projected {@link ImpactFx} particle system + the {@code SHOTS}/contrail
 *       FX that draw with it.</li>
 *   <li><b>Combat-world audio frame.</b> The standalone positions SFX in an abstract {@code cell×30}
 *       frame against a self-driven listener. Here the sim shares the vanilla combat world, so SFX
 *       are positioned in that frame ({@code cellToWorld}, the same projection the proxies use) and
 *       the listener is parked at the ground-band centroid — so ground audio and the fleet's own
 *       weapon audio share one consistent spatial scale.</li>
 * </ul>
 *
 * <p>Driven by {@link SimProxyMirror} immediately after its per-frame {@code sim.advance()}, so the
 * per-frame event lists ({@code getShotsThisFrame} / {@code getShotsExpiredThisFrame} /
 * {@code getDeathsThisFrame}) are read in the same frame they're produced, before the next tick
 * clears them. A no-op until the backdrop's renderer has loaded (a couple of frames in).
 *
 * <p>Throwaway dev scaffolding; gated by {@code DevConfig.S0_COMBAT_PROBE}.
 */
@DebugOnly
public final class GroundSimPresentation {

    private static final String SFX_RIFLE          = "marines_smallarms_rifle";
    private static final String SFX_VOICE_DEAD     = "marines_voice_dead";
    private static final String SFX_NEAR_EXPLOSION = "marines_explosion";
    private static final float RIFLE_PITCH_JITTER  = 0.10f;
    private static final float RIFLE_VOLUME        = 0.5f;

    private final GroundBattleConfig cfg;
    private final Vector2f scratch = new Vector2f();
    private final Vector2f zeroVel = new Vector2f(0f, 0f);

    public GroundSimPresentation(GroundBattleConfig cfg) {
        this.cfg = cfg;
    }

    /**
     * One presentation frame. {@code dt} is the real combat frame time (no sim speed-multiplier in
     * real-time combat). Reads the sim's just-produced event lists; spawns/ages FX on {@code
     * renderer}'s systems and plays positional audio.
     */
    public void advance(BattleRenderer renderer, BattleSimulation sim, float dt) {
        if (renderer == null || sim == null) return;

        parkListener(sim);

        // Real-dt vision fades: the sim's vision tick decides the target state (building targetAlpha,
        // per-unit VIS_FADING), but the smooth fade toward it is a render-host job that lived only in
        // BattleScreen.advance. The bridge never ran it, so roofs stayed frozen opaque (interiors
        // never revealed) and out-of-LoS units stuck mid-fade instead of hiding. Drive both off the
        // real combat frame dt. (bridge-host-parity)
        BuildingVisibilityPass.advanceAlpha(sim.getBuildings(), dt);
        sim.getVision().advanceFade(dt);

        ImpactFx fx = renderer.getImpactFx();
        NavigationGrid grid = sim.getGrid();
        Random rng = ThreadLocalRandom.current();

        spawnFireFx(fx, sim, grid);
        spawnImpactFxAndSounds(fx, sim, grid, rng);
        playFireSounds(sim, rng);
        playDeathVoice(sim);
        spawnAmbientFx(fx, sim);

        fx.advance(dt);
        renderer.getContrailFx().tick(sim.getActiveShots(), dt);
    }

    /** Line-tracer impacts land instantly (the beam covers its whole travel at fire), plus per-shot
     *  muzzle/backblast flourishes. Projectile-sprite shots defer their impact to arrival. */
    private void spawnFireFx(ImpactFx fx, BattleSimulation sim, NavigationGrid grid) {
        for (ShotEvent s : sim.getShotsThisFrame()) {
            if (s.mechWeapon == MechWeapon.CHAINGUN) {
                fx.spawnMuzzleFlash(s.fromX, s.fromY, 0.55f, 0.08f);
            }
            if (s.turretKind != null && s.turretKind.hasLaunchBackblast()) {
                fx.spawnLaunchBackblast(s.fromX, s.fromY, bearingDeg(s.fromX, s.fromY, s.toX, s.toY));
            }
            if (hasProjectileSprite(s)) continue;
            ImpactProfile profile = (s.marineWeapon != null) ? s.marineWeapon.impactProfile : ImpactProfile.RIFLE;
            fx.spawnImpact(profile, s.toX, s.toY, isWallAt(grid, s.toX, s.toY));
        }
    }

    /** Projectile-sprite shots that reached their endpoint this frame: spawn the impact particle and,
     *  for HE / secondary rounds, the paired explosion clip. */
    private void spawnImpactFxAndSounds(ImpactFx fx, BattleSimulation sim, NavigationGrid grid, Random rng) {
        for (ShotEvent s : sim.getShotsExpiredThisFrame()) {
            if (!hasProjectileSprite(s)) continue;
            boolean isWall = isWallAt(grid, s.toX, s.toY);
            if (s.turretKind != null) {
                ImpactProfile profile = s.turretKind.impactProfile();
                fx.spawnImpact(profile, s.toX, s.toY, isWall);
                if (profile == ImpactProfile.HE) playExplosion(s.toX, s.toY, 0.55f, rng);
            } else if (s.marineSecondary != null) {
                fx.spawnImpact(s.marineSecondary.impactProfile(), s.toX, s.toY, isWall);
                playAtCell(s.marineSecondary.impactSoundId, 0.9f + rng.nextFloat() * 0.2f, 0.70f, s.toX, s.toY);
            } else if (s.marineWeapon != null) {
                fx.spawnImpact(s.marineWeapon.impactProfile, s.toX, s.toY, isWall);
            } else if (s.mechWeapon != null) {
                ImpactProfile profile = s.mechWeapon.impactProfile;
                fx.spawnImpact(profile, s.toX, s.toY, isWall);
                if (profile == ImpactProfile.HE) playExplosion(s.toX, s.toY, 0.65f, rng);
            }
        }
    }

    /** Per-weapon fire SFX, positional at the shooter cell. Mirrors the standalone's source dispatch
     *  (the sound ids live on the weapon enums, so this reads, never authors, them). */
    private void playFireSounds(BattleSimulation sim, Random rng) {
        for (ShotEvent s : sim.getShotsThisFrame()) {
            float pitch = 1f + (rng.nextFloat() * 2f - 1f) * RIFLE_PITCH_JITTER;
            if (s.turretKind != null) {
                playAtCell(s.turretKind.fireSoundId, pitch, 1.0f, s.fromX, s.fromY);
            } else if (s.marineSecondary != null) {
                playAtCell(s.marineSecondary.fireSoundId, pitch, 1.0f, s.fromX, s.fromY);
            } else if (s.marineWeapon != null) {
                playAtCell(s.marineWeapon.fireSoundId, pitch, 0.85f, s.fromX, s.fromY);
            } else if (s.mechWeapon != null) {
                playAtCell(s.mechWeapon.fireSoundId, pitch, 1.0f, s.fromX, s.fromY);
            } else {
                playAtCell(SFX_RIFLE, pitch, RIFLE_VOLUME, s.fromX, s.fromY);
            }
        }
    }

    /** One marine death cry per frame — the same one-voice-per-frame budget the standalone keeps. */
    private void playDeathVoice(BattleSimulation sim) {
        for (Entity u : sim.getDeathsThisFrame()) {
            if (u.faction == Faction.MARINE) {
                playAtCell(SFX_VOICE_DEAD, 1f, 1f,
                        sim.world().renderX(u.entityId), sim.world().renderY(u.entityId));
                break;
            }
        }
    }

    /** Burning-wreck smoke + flame puffs the sim queued this tick. */
    private void spawnAmbientFx(ImpactFx fx, BattleSimulation sim) {
        for (float[] puff : sim.getSmokePuffsThisFrame()) fx.spawnAmbientSmoke(puff[0], puff[1], puff[2]);
        for (float[] burst : sim.getFireBurstsThisFrame()) fx.spawnAmbientFire(burst[0], burst[1], burst[2]);
    }

    /**
     * Park the OpenAL listener at the ground-band centroid (the structure cluster the fight revolves
     * around) so positional SFX pan/attenuate around the battlefield rather than wherever vanilla's
     * spectator listener happens to sit. One-frame override, re-armed every frame.
     *
     * <p>Whether this override survives <em>inside</em> a running {@code CombatEngine} (vanilla may
     * re-assert its own listener each frame) is unverified — {@code setListenerPosOverrideOneFrame}
     * is only confirmed outside combat. If vanilla wins, audio still plays: the grid is centered on
     * the world origin where a spectator listener already sits, so band-local sources stay audible.
     */
    private void parkListener(BattleSimulation sim) {
        cfg.targetableCentroid(scratch);
        Global.getSoundPlayer().setListenerPosOverrideOneFrame(new Vector2f(scratch.x, scratch.y));
    }

    private void playExplosion(float cellX, float cellY, float volume, Random rng) {
        playAtCell(SFX_NEAR_EXPLOSION, 0.9f + rng.nextFloat() * 0.2f, volume, cellX, cellY);
    }

    /** Play {@code soundId} positioned at a sim cell, projected into the shared combat-world frame. */
    private void playAtCell(String soundId, float pitch, float volume, float cellX, float cellY) {
        Vector2f loc = new Vector2f(
                (cellX - cfg.gridW() * 0.5f) * cfg.worldUnitsPerCell(),
                (cellY - cfg.gridH() * 0.5f) * cfg.worldUnitsPerCell());
        Global.getSoundPlayer().playSound(soundId, pitch, volume, loc, zeroVel);
    }

    /** Traveling-sprite shots (turret kinetic, rocket, SMG bullet, mech round) defer impact FX to
     *  arrival; instant line tracers fire it at launch. Mirrors {@code BattleScreen.hasProjectileSprite}. */
    private static boolean hasProjectileSprite(ShotEvent s) {
        return s.turretKind != null
                || s.marineSecondary != null
                || (s.marineWeapon != null && s.marineWeapon.projectileSpritePath != null)
                || (s.mechWeapon != null && s.mechWeapon.projectileSpritePath != null);
    }

    private static boolean isWallAt(NavigationGrid grid, float x, float y) {
        int cx = (int) Math.floor(x);
        int cy = (int) Math.floor(y);
        return grid.inBounds(cx, cy) && !grid.isWalkable(cx, cy);
    }

    /** Starsector sprite-angle convention: 0° = +Y (north), positive clockwise. */
    private static float bearingDeg(float fromX, float fromY, float toX, float toY) {
        float dx = toX - fromX;
        float dy = toY - fromY;
        if (dx == 0f && dy == 0f) return 0f;
        return (float) Math.toDegrees(Math.atan2(dy, dx)) - 90f;
    }
}
