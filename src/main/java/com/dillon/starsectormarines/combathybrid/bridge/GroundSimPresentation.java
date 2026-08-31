package com.dillon.starsectormarines.combathybrid.bridge;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.battle.audio.BattleRadioChatter;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.combat.fx.ImpactFx;
import com.dillon.starsectormarines.battle.combat.fx.OrdnanceRelease;
import com.dillon.starsectormarines.battle.weapon.fx.WeaponFxRuntime;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import it.unimi.dsi.fastutil.longs.LongList;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vision.BuildingVisibilityPass;
import com.dillon.starsectormarines.ops.battleview.BattleRenderer;
import com.dillon.starsectormarines.ops.battleview.OrdnanceAudio;
import com.dillon.starsectormarines.ops.battleview.OrdnanceFxRuntime;
import com.dillon.starsectormarines.ops.battleview.OrdnanceTraceFxService;
import com.dillon.starsectormarines.ops.battleview.ShotFx;
import com.dillon.starsectormarines.ops.battleview.ShotImpactAudio;
import com.fs.starfarer.api.Global;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;
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
 *   <li><b>No decals.</b> The standalone interleaves {@code ImpactDecals} (DECALS) spawns with the
 *       particle spawns. The bridge draws no DECALS pass ({@link GroundBattleConfig#DEFAULT_SCENE_LAYERS}
 *       omits it — it is a persistent FBO accumulator awaiting a projection/residency contract), so we feed
 *       only the camera-projected {@link ImpactFx} particle system + the {@code SHOTS}/contrail FX that
 *       draw with it. (The night-battle lightmap is gone entirely — removed 2026-06-29.)</li>
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
    private final BattleRadioChatter radioChatter = new BattleRadioChatter();
    private final Vector2f scratch = new Vector2f();
    private final Vector2f zeroVel = new Vector2f(0f, 0f);
    /** Weapon and impact cues for rounds delivered onto the ground. Host parity with BattleScreen. */
    private final OrdnanceAudio ordnanceAudio = new OrdnanceAudio();

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
        sim.getFogOfWar().advanceFade(dt);

        ImpactFx fx = renderer.getImpactFx();
        NavigationGrid grid = sim.getGrid();
        Random rng = ThreadLocalRandom.current();

        renderer.getBeamFx().advance(dt);
        for (ShotEvent shot : sim.getShotsThisFrame()) renderer.getBeamFx().spawn(shot);
        spawnFireFx(fx, sim, grid, rng);
        spawnImpactFxAndSounds(fx, sim, grid, rng);
        playFireSounds(sim, rng);
        playDeathVoice(sim);
        playRadioChatter(sim, dt);
        spawnAmbientFx(fx, sim);

        driveOrdnanceFx(renderer, sim, dt);
        fx.advance(dt);
        renderer.getContrailFx().tick(sim.getActiveShots(), dt);
    }

    /**
     * Rounds delivered onto the ground this frame — the flash and the weapon
     * where they left, the round drawn on its way down, and the crater when it
     * gets there. Same drive as {@code BattleScreen}: a delivery that is
     * silent and invisible in one host and not the other is the host-parity
     * gap this class already exists to close.
     */
    private void driveOrdnanceFx(BattleRenderer renderer, BattleSimulation sim, float dt) {
        OrdnanceTraceFxService traces = renderer.getOrdnanceTraceFx();
        List<OrdnanceRelease> releases = sim.getOrdnanceReleasesThisFrame();
        for (int i = 0, n = releases.size(); i < n; i++) {
            OrdnanceRelease release = releases.get(i);
            traces.spawn(release);
            OrdnanceFxRuntime.spawnRelease(
                    renderer.getImpactFx(), renderer.getGroundLights(), release);
        }
        traces.advance(dt);
        List<OrdnanceRelease> arrivals = traces.arrivalsThisFrame();
        for (int i = 0, n = arrivals.size(); i < n; i++) {
            OrdnanceFxRuntime.spawnArrival(
                    renderer.getImpactFx(), renderer.getGroundLights(), arrivals.get(i));
        }
        ordnanceAudio.update(releases, arrivals, dt);
    }

    /** Line-tracer impacts land instantly (the beam covers its whole travel at fire), plus per-shot
     *  muzzle/backblast flourishes. Traveling bodies defer their impact to arrival. */
    private void spawnFireFx(ImpactFx fx, BattleSimulation sim, NavigationGrid grid, Random rng) {
        for (ShotEvent s : sim.getShotsThisFrame()) {
            WeaponFxRuntime.spawnMuzzle(fx, s);
            if (ShotFx.of(s).travels()) continue;
            if (!s.impacts()) continue;
            WeaponFxRuntime.spawnImpactAndAftermath(
                    fx, s, isWallAt(grid, s.toX, s.toY));
            playImpactCue(s, rng);
        }
    }

    /** Traveling-body shots that reached their endpoint this frame: spawn the impact particle and,
     *  for HE / secondary rounds, the paired explosion clip. */
    private void spawnImpactFxAndSounds(ImpactFx fx, BattleSimulation sim, NavigationGrid grid, Random rng) {
        for (ShotEvent s : sim.getShotsExpiredThisFrame()) {
            if (!ShotFx.of(s).travels()) continue;
            if (!s.impacts()) continue;
            boolean isWall = isWallAt(grid, s.toX, s.toY);
            WeaponFxRuntime.spawnImpactAndAftermath(fx, s, isWall);
            playImpactCue(s, rng);
        }
    }

    private void playImpactCue(ShotEvent shot, Random rng) {
        ShotImpactAudio.Cue cue = ShotImpactAudio.resolve(shot, SFX_NEAR_EXPLOSION);
        if (cue == null) return;
        playAtCell(cue.soundId(), 0.9f + rng.nextFloat() * 0.2f,
                cue.volume(), shot.toX, shot.toY);
    }

    /** Per-weapon fire SFX, positional at the shooter cell. Mirrors the standalone's source dispatch
     *  (the sound ids live on the weapon enums, so this reads, never authors, them). */
    private void playFireSounds(BattleSimulation sim, Random rng) {
        for (ShotEvent s : sim.getShotsThisFrame()) {
            float pitch = 1f + (rng.nextFloat() * 2f - 1f) * RIFLE_PITCH_JITTER;
            if (s.turretStructureDef != null) {
                playAtCell(s.turretStructureDef.mount.weapon.fireSoundId,
                        pitch, 1.0f, s.fromX, s.fromY);
            } else if (s.specialEquipmentDef != null) {
                playAtCell(s.specialEquipmentDef.fireSoundId(), pitch, 1.0f, s.fromX, s.fromY);
            } else if (s.primaryWeaponDef != null) {
                playAtCell(s.primaryWeaponDef.fireSoundId, pitch, 0.85f, s.fromX, s.fromY);
            } else if (s.mechWeaponDef != null) {
                playAtCell(s.mechWeaponDef.fireSoundId, pitch, 1.0f, s.fromX, s.fromY);
            } else {
                playAtCell(SFX_RIFLE, pitch, RIFLE_VOLUME, s.fromX, s.fromY);
            }
        }
    }

    /** One marine death cry per frame — the same one-voice-per-frame budget the standalone keeps. */
    private void playDeathVoice(BattleSimulation sim) {
        LongList deaths = sim.getDeathsThisFrame();
        for (int i = 0, n = deaths.size(); i < n; i++) {
            long u = deaths.getLong(i);
            if (sim.identity().faction(u) == Faction.MARINE) {
                playAtCell(SFX_VOICE_DEAD, 1f, 1f,
                        sim.world().renderX(u), sim.world().renderY(u));
                break;
            }
        }
    }

    /** Shared squad-event policy, projected through the bridge's combat-world audio frame. */
    private void playRadioChatter(BattleSimulation sim, float dt) {
        BattleRadioChatter.Emission emission = radioChatter.advance(dt, sim);
        if (emission == null) return;
        playAtCell(emission.cue().soundId(), 1f, emission.cue().volume(),
                emission.cellX(), emission.cellY());
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

    /** Play {@code soundId} positioned at a sim cell, projected into the shared combat-world frame. */
    private void playAtCell(String soundId, float pitch, float volume, float cellX, float cellY) {
        Vector2f loc = new Vector2f(
                (cellX - cfg.gridW() * 0.5f) * cfg.worldUnitsPerCell(),
                (cellY - cfg.gridH() * 0.5f) * cfg.worldUnitsPerCell());
        Global.getSoundPlayer().playSound(soundId, pitch, volume, loc, zeroVel);
    }

    private static boolean isWallAt(NavigationGrid grid, float x, float y) {
        int cx = (int) Math.floor(x);
        int cy = (int) Math.floor(y);
        return grid.inBounds(cx, cy) && !grid.isWalkable(cx, cy);
    }

    /** Starsector sprite-angle convention: 0° = +Y (north), positive clockwise. */
}
