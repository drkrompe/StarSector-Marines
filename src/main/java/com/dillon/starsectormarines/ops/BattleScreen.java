package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.battle.audio.BattleMusicPlaylist;
import com.dillon.starsectormarines.battle.audio.BattleRadioChatter;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.ui.debug.VehicleStateDumper;
import com.dillon.starsectormarines.battle.ui.debug.CommanderTraceDumper;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.vision.BuildingVisibilityPass;
import com.dillon.starsectormarines.battle.air.AirAppearance;
import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.air.engine.EngineVoice;
import com.dillon.starsectormarines.battle.air.engine.EngineVoiceResolver;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.appearance.SystemFxService;
import it.unimi.dsi.fastutil.longs.LongList;
import com.dillon.starsectormarines.battle.command.reinforcement.ReinforcementRequest;
import com.dillon.starsectormarines.battle.command.reinforcement.ReinforcementService;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.ui.BattleHud;
import com.dillon.starsectormarines.battle.ui.BattleUiContext;
import com.dillon.starsectormarines.battle.ui.panel.BattleCommsPanel;
import com.dillon.starsectormarines.battle.ui.panel.CommandPowerTargetingPanel;
import com.dillon.starsectormarines.battle.ui.panel.DebugTogglesPanel;
import com.dillon.starsectormarines.battle.ui.panel.TurretAuthorPanel;
import com.dillon.starsectormarines.battle.ui.panel.TaskForceStatusPanel;
import com.dillon.starsectormarines.battle.ui.panel.SquadPlanDebugPanel;
import com.dillon.starsectormarines.battle.ui.panel.TickProfileDebugPanel;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.highlight.CommanderInfluenceOverlayPublisher;
import com.dillon.starsectormarines.battle.ui.highlight.ConquestCommanderOverlayPublisher;
import com.dillon.starsectormarines.battle.ui.highlight.AssaultCommanderOverlayPublisher;
import com.dillon.starsectormarines.battle.ui.highlight.SabotageCommanderOverlayPublisher;
import com.dillon.starsectormarines.battle.ui.highlight.RaidCommanderOverlayPublisher;
import com.dillon.starsectormarines.battle.ui.highlight.ExtractionCommanderOverlayPublisher;
import com.dillon.starsectormarines.battle.ui.highlight.SelectionHighlightPublisher;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.ui.picking.WorldPicker;
import com.dillon.starsectormarines.battle.mech.MechFamilyDebugSpawner;
import com.dillon.starsectormarines.battle.combat.fx.ImpactDecals;
import com.dillon.starsectormarines.battle.turret.TurretImpactAudio;
import com.dillon.starsectormarines.battle.weapon.fx.WeaponFxDef;
import com.dillon.starsectormarines.battle.weapon.fx.WeaponFxRuntime;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.vision.FogOfWarService;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.CameraControls;
import com.dillon.starsectormarines.ops.battleview.BattleRenderer;
import com.dillon.starsectormarines.ops.battleview.BattleShotAudio;
import com.dillon.starsectormarines.ops.battleview.BattleSprites;
import com.dillon.starsectormarines.ops.battleview.GroundParallaxPipeline;
import com.dillon.starsectormarines.ops.battleview.ShotFx;
import com.dillon.starsectormarines.ops.loot.LootGenerator;
import com.dillon.starsectormarines.ui.Fonts;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import org.apache.log4j.Logger;
import org.lwjgl.opengl.Display;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_SCISSOR_BIT;
import static org.lwjgl.opengl.GL11.GL_SCISSOR_TEST;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glColor4f;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glPopAttrib;
import static org.lwjgl.opengl.GL11.glPushAttrib;
import static org.lwjgl.opengl.GL11.glScissor;
import static org.lwjgl.opengl.GL11.glVertex2f;

/**
 * Top-down 2D auto-battler screen. Owns a layout + speed control state and
 * reads the active {@link BattleSimulation} from {@link MarineOpsContext}.
 *
 * <p>Loop: {@link #advance(float)} multiplies real dt by the player's speed
 * multiplier (pause / 1x / 2x / 4x) and feeds it to the sim, which catches up
 * in 1/30s ticks. {@link #render(float)} draws floor + walls + units + HP bars,
 * plus a centered Victory/Defeat banner when the sim completes.
 *
 * <p>Retreat abandons an active battle without producing an outcome and returns
 * to {@link ScreenId#MISSION_SELECT}. Once the battle completes, Continue resolves
 * the outcome and advances to the debrief.
 */
public class BattleScreen implements Screen, BattleUiContext {

    private static final Logger LOG = Global.getLogger(BattleScreen.class);

    private static final Color BANNER_BG      = new Color(0x10, 0x14, 0x1E);
    private static final Color VICTORY_COLOR  = new Color(0x80, 0xE0, 0x80);
    private static final Color DEFEAT_COLOR   = new Color(0xE0, 0x60, 0x60);

    /** Sound IDs declared in mod/data/config/sounds.json. */
    private static final String[] BATTLE_MUSIC_POOL = {
            "marines_battle_music",
            "marines_battle_music_02",
            "marines_battle_music_03",
            "marines_battle_music_04",
            "marines_battle_music_05",
            "marines_battle_music_06",
            "marines_battle_music_07",
            "marines_battle_music_08",
            "marines_battle_music_09",
            "marines_battle_music_10",
    };
    private static final String LOOP_TICKING  = "marines_ticking_clock";
    private static final String SFX_VOICE_DEAD = "marines_voice_dead";
    /** A suit's integral system raising a screen. Mono, so it plays positionally. */
    private static final String SFX_SYSTEM_SCREEN_UP = "marines_system_screen_up";
    /** A suit's integral system running without raising a screen. */
    private static final String SFX_SYSTEM_ENGAGED = "marines_system_engaged";
    private static final String SFX_DISTANT_BOOM = "marines_explosion_muffled";
    private static final String SFX_NEAR_EXPLOSION = "marines_explosion";
    /** Crossfade duration (seconds, whole numbers required) for entering / leaving the battle music. */
    private static final int MUSIC_FADE_SECS  = 2;
    /** Pitch lerp endpoints for the shuttle engine loop: idle on the ground → full at cruise. */
    private static final float ENGINE_PITCH_IDLE   = 0.7f;
    private static final float ENGINE_PITCH_CRUISE = 1.0f;
    /** Cells → OpenAL world units, for positional SFX. Must match {@code FlybyOverlay.AUDIO_WORLD_UNITS_PER_CELL}. */
    private static final float AUDIO_WORLD_UNITS_PER_CELL = BattleShotAudio.WORLD_UNITS_PER_CELL;
    /** Radius, in cells, of the burst drawn where a point-defence emplacement stopped a warhead. Presentation only; nothing is damaged. */
    private static final float INTERCEPT_BURST_CELLS = 0.9f;
    /** OpenAL distance the distant-boom emitter sits from the camera focus. Far enough to attenuate noticeably (read as "off in the distance") but close enough to remain audible. */
    private static final float DISTANT_BOOM_EMITTER_DISTANCE = 600f;

    /**
     * Pool of ambient loop sound-ids the battle picks 1-2 from at attach time for environmental
     * background. Subway-train and wind-up-long are intentionally excluded — they feel more like
     * situational cues than ambient bed; trivial to flip in if a battle wants the urban-rail or
     * winding-mechanism vibe. Vehicle engines no longer draw from this bed — each shuttle / fighter
     * plays its own {@link EngineVoice} clip from the base game's {@code sfx_engines/} set.
     */
    private static final String[] AMBIENT_LOOP_POOL = {
            "marines_ambient_fan_noise",
            "marines_ambient_motor_1",
            "marines_ambient_motor_2",
            "marines_ambient_loudmotor_3",
            "marines_ambient_radiator_1",
            "marines_ambient_helicopter_2",
    };

    /** Volume scalar on a shuttle's engine loop, multiplied by {@link AirAppearance#engineIntensity(boolean, float)} and the per-clip base in sounds.json. */
    private static final float SHUTTLE_ENGINE_VOLUME = 0.9f;
    /** ±range of the per-shuttle pitch offset, so simultaneous shuttles playing the same engine clip don't beat against each other in lockstep. */
    private static final float SHUTTLE_ENGINE_PITCH_JITTER = 0.08f;
    /** Volume for ambient loops — quiet bed, well under foreground SFX. Multiplied with the per-clip base in sounds.json. */
    private static final float AMBIENT_VOLUME = 0.2f;
    /** Real-time gap (seconds) between sporadic distant explosions. Range is rolled each time. */
    private static final float DISTANT_BOOM_MIN_GAP = 4f;
    private static final float DISTANT_BOOM_MAX_GAP = 12f;
    /** Volume for the dedicated muffled-distant explosion clip. */
    private static final float DISTANT_BOOM_VOLUME  = 0.4f;
    /** When repurposing a pool explosion as a distant boom: drop pitch + volume to fake distance. */
    private static final float NEAR_AS_DISTANT_PITCH  = 0.6f;
    private static final float NEAR_AS_DISTANT_VOLUME = 0.3f;
    /** Pitch jitter (±) on each distant boom so the same clip doesn't read as the same blast each time. */
    private static final float DISTANT_BOOM_PITCH_JITTER = 0.15f;
    /** Probability that a distant-boom event uses the dedicated muffled clip; otherwise pull from the pool and pitch-down. */
    private static final float DISTANT_BOOM_MUFFLED_CHANCE = 0.6f;
    /** Battle HUD — squad overview/detail panels today; mini-map + objectives later. Lazy-built once {@link #layout} and {@link #camera} are ready, then reused across rebuilds. */
    private BattleHud hud;
    /** MLX-authored player-facing command chrome: time controls and capture state. */
    private BattleHudOverlay retainedOverlay;
    /** MLX-authored 3x4 selected-squad roster with hover loadout detail. */
    private BattleSquadOverlay retainedSquadOverlay;
    /** MLX-authored doctrine control for one exactly selected player mech. */
    private BattleMechOverlay retainedMechOverlay;
    /** MLX-authored compact command-power deck at bottom-center. */
    private BattlePowerOverlay retainedPowerOverlay;
    /** MLX-authored, confirmation-gated battle exit at bottom-left. */
    private BattleRetreatOverlay retainedRetreatOverlay;
    /** World click/reticle half of the power flow; card selection lives in MLX. */
    private CommandPowerTargetingPanel commandPowerTargeting;
    /** Shared selection state read by HUD panels (and, later, a world-picker). Survives across attach()/rebuild() cycles; self-heals when the selected squad disappears. */
    private final Selection selection = new Selection();
    /** Shared debug cell-highlight overlay — populated by HUD panels, rendered between the grid pass and the unit sprites. */
    private final HighlightOverlay highlights = new HighlightOverlay();

    private PositionAPI position;
    private MarineOpsContext ctx;
    private BattleLayout layout;
    private BattleCamera camera;
    /**
     * The hand on the camera. Shared with every other screen that lets the
     * player look around a world; shift plus right-drag is reserved here
     * because it is the debug damage gesture.
     */
    private final CameraControls cameraControls = new CameraControls(true);
    private float speedMultiplier = 1f;
    /** Owns all loaded sprite sheets, frame data, and ensure/load methods. */
    private final BattleSprites sprites = new BattleSprites();
    /** World-layer render pipeline — owns tile batches, FX systems, and all render/draw methods. */
    private final BattleRenderer renderer = new BattleRenderer(sprites);
    /**
     * Real-time {@code dt} from the most recent {@link #advance} call. Passed
     * into {@link com.dillon.starsectormarines.ops.battleview.RenderContext} so
     * the renderer can age contrails on real time during sim pause.
     */
    private float lastAdvanceDt = 0f;
    /** Debug toggle (Z) — tints each navigation zone with a stable per-id color so the partitioning + new portals from wall breaches are eyeball-verifiable. */
    @DebugOnly
    private boolean debugZonesVisible;
    private boolean debugMarineFriendlyInfluence;
    private boolean debugMarineHostileInfluence;
    private boolean debugDefenderFriendlyInfluence;
    private boolean debugDefenderHostileInfluence;
    /** The one faction-local Conquest command picture currently projected into the world. */
    private Faction debugConquestPerspective;
    /**
     * True while this screen owns the audio side effects (custom music + ticking-clock loop
     * + suspended default playback). Guarded so attach() re-runs from dialog resizes don't
     * restart the music mid-battle, and detach() doesn't double-stop on already-cleaned exits.
     */
    private boolean audioActive;
    /** Ambient loop ids picked at battle start. Re-rolled on each fresh attach so revisits get new flavor. */
    private String[] activeAmbientLoops = new String[0];
    /** OpenAL world-space anchor for each entry in {@link #activeAmbientLoops}. Positional loops attenuate by distance to the listener, so panning the camera near an anchor swells that loop and across-map fades it — gives the bed real spatial depth instead of a flat mono mix. */
    private Vector2f[] activeAmbientAnchors = new Vector2f[0];
    /** Real-time countdown (seconds) until the next sporadic distant explosion. */
    private float distantBoomTimer;
    /** RNG for audio variety — separate from sim.rng so audio randomness doesn't perturb sim determinism. */
    private final java.util.Random audioRng = new java.util.Random();
    /** Shuffled music order and end-of-track observation, retained across battles to avoid repeats. */
    private final BattleMusicPlaylist battleMusic =
            new BattleMusicPlaylist(List.of(BATTLE_MUSIC_POOL), audioRng);
    /** Shared squad-event policy for sparse positional marine radio calls. */
    private final BattleRadioChatter radioChatter = new BattleRadioChatter();

    @Override
    public void attach(PositionAPI position, MarineOpsContext ctx, Runnable dismissDialog) {
        this.position = position;
        this.ctx = ctx;
        this.speedMultiplier = 1f;
        sprites.ensureUnitSheets();
        sprites.ensureLayeredUnitSprites();
        sprites.ensureLayeredMechSprites();
        sprites.ensureTurretSprites();
        sprites.ensureMarineSecondarySprites();
        sprites.ensureDecalSheet();
        sprites.ensureTileSheet();
        sprites.ensureRoadSheet();
        sprites.ensureFloorsSheet();
        sprites.ensureNatureSheet();
        sprites.ensureWaterSheet();
        sprites.ensureUrbanTile3Sheet();
        sprites.ensureDoodadSheet();
        sprites.ensureAirframeSprites();
        sprites.ensureConvoySprites();
        sprites.ensureDroneSprite();
        sprites.ensureDroneHubSprite();
        sprites.ensureEngineFxSprites();
        sprites.ensureObjectiveIcons();
        renderer.buildTileBatches();
        renderer.onAttach();
        startBattleAudio();
        rebuild();
    }

    /**
     * Suspend the campaign music player and crossfade into our shuffled battle playlist.
     * Guarded so re-entry from a dialog resize (attach() is documented as idempotent)
     * doesn't restart the music mid-battle. The ticking-clock loop itself is started
     * lazily on the first {@link #advance(float)} — {@code playUILoop} must be called
     * every frame anyway, so there's no benefit to kicking it off here.
     */
    private void startBattleAudio() {
        if (audioActive) return;
        audioActive = true;
        Global.getSoundPlayer().setSuspendDefaultMusicPlayback(true);
        String track = battleMusic.start();
        Global.getSoundPlayer().playCustomMusic(MUSIC_FADE_SECS, MUSIC_FADE_SECS, track, false);
        BattleSimulation sim = ctx != null ? ctx.getBattleSimulation() : null;
        int gridW = sim != null ? sim.getGrid().getWidth()  : BattleSetup.GRID_W;
        int gridH = sim != null ? sim.getGrid().getHeight() : BattleSetup.GRID_H;
        pickAmbient(gridW, gridH);
        distantBoomTimer = nextDistantBoomGap();
        radioChatter.reset();
    }

    /**
     * Picks 1-2 random ambient loop ids from {@link #AMBIENT_LOOP_POOL} and gives each a random
     * world-space anchor cell. The anchor doesn't need to be a walkable cell — it's just a point
     * the positional loop emits from, so the player hears the bed pan and attenuate as they move
     * the camera around the map.
     */
    private void pickAmbient(int gridW, int gridH) {
        int count = 1 + audioRng.nextInt(2); // 1 or 2
        java.util.List<String> shuffled = new java.util.ArrayList<>(java.util.Arrays.asList(AMBIENT_LOOP_POOL));
        java.util.Collections.shuffle(shuffled, audioRng);
        int n = Math.min(count, shuffled.size());
        activeAmbientLoops = shuffled.subList(0, n).toArray(new String[0]);
        activeAmbientAnchors = new Vector2f[n];
        for (int i = 0; i < n; i++) {
            activeAmbientAnchors[i] = new Vector2f(
                    audioRng.nextInt(Math.max(1, gridW)) * AUDIO_WORLD_UNITS_PER_CELL,
                    audioRng.nextInt(Math.max(1, gridH)) * AUDIO_WORLD_UNITS_PER_CELL);
        }
    }

    private float nextDistantBoomGap() {
        return DISTANT_BOOM_MIN_GAP + audioRng.nextFloat() * (DISTANT_BOOM_MAX_GAP - DISTANT_BOOM_MIN_GAP);
    }

    private void rebuild() {
        if (position == null || ctx == null) return;

        BattleSimulation sim = ctx.getBattleSimulation();
        int gridW = sim != null ? sim.getGrid().getWidth()  : BattleSetup.GRID_W;
        int gridH = sim != null ? sim.getGrid().getHeight() : BattleSetup.GRID_H;
        layout = new BattleLayout(position, gridW, gridH);
        // Camera survives rebuilds so the player's pan/zoom is not lost. Only
        // construct it the first time, then refresh the viewport rect later.
        if (camera == null || camera.worldCellsW() != gridW || camera.worldCellsH() != gridH) {
            camera = new BattleCamera(gridW, gridH);
        }
        camera.setViewport(layout.gridX, layout.gridY, layout.gridW, layout.gridH, layout.cellSize);
        ensureHud();
        ensureRetainedOverlay(sim);
    }

    @Override
    public void advance(float dt) {
        lastAdvanceDt = dt;
        // HUD ticks on real dt (not sim-scaled) so panel snapshots and hover
        // state still update when the sim is paused. Panels' update() just
        // refreshes their cached views over the sim — cheap even at every frame.
        if (hud != null) hud.update(dt);
        if (retainedOverlay != null) {
            retainedOverlay.update(dt, ctx != null ? ctx.getBattleSimulation() : null,
                    speedMultiplier);
        }
        if (retainedSquadOverlay != null) {
            retainedSquadOverlay.update(dt,
                    ctx != null ? ctx.getBattleSimulation() : null);
        }
        if (retainedMechOverlay != null) {
            retainedMechOverlay.update(dt,
                    ctx != null ? ctx.getBattleSimulation() : null);
        }
        if (retainedPowerOverlay != null) {
            retainedPowerOverlay.update(dt,
                    ctx != null ? ctx.getBattleSimulation() : null);
        }
        if (retainedRetreatOverlay != null) {
            BattleSimulation current = ctx != null ? ctx.getBattleSimulation() : null;
            retainedRetreatOverlay.update(dt, current != null && current.isComplete());
        }
        // Park the OpenAL listener at the camera focus every frame so positional SFX (gunfire,
        // explosions, ambient loops, death VO) pan + attenuate around what the player is looking
        // at. setListenerPosOverrideOneFrame is a one-frame override, so it has to be re-armed
        // each tick — same pattern as playUILoop. We set it here (not just in FlybyOverlay.advance)
        // so the listener is still correct during sim-pause when FlybyOverlay bails on dt=0.
        if (camera != null) {
            Global.getSoundPlayer().setListenerPosOverrideOneFrame(new Vector2f(
                    camera.panCellX() * AUDIO_WORLD_UNITS_PER_CELL,
                    camera.panCellY() * AUDIO_WORLD_UNITS_PER_CELL));
        }
        // On real dt, not sim-time, so the player can still look around the map
        // while the simulation is paused.
        cameraControls.advance(dt, camera);
        // playUILoop is documented as "must be called every frame or the loop will fade out" —
        // re-arming it every advance is how Starsector expects loops to be driven. When this
        // screen stops being current, advance() stops firing and all loops fade automatically.
        if (audioActive) {
            String nextTrack = battleMusic.advance(Global.getSoundPlayer().getCurrentMusicId());
            if (nextTrack != null) {
                Global.getSoundPlayer().playCustomMusic(0, 0, nextTrack, false);
            }
            Global.getSoundPlayer().playUILoop(LOOP_TICKING, 1f, 1f);
            driveAmbientBackground(dt);
        }
        BattleSimulation sim = ctx != null ? ctx.getBattleSimulation() : null;
        if (sim == null) return;
        // Rebuild ephemeral vision sources (shuttles + strafing fighters)
        // each frame so the fog bitmap always reflects the latest positions.
        // Cleared + re-pushed every frame; VisionService only processes them
        // on vision-tick frames (every 3rd sim tick).
        FogOfWarService vis = sim.getFogOfWar();
        vis.clearEphemeralSources();
        renderer.getFlybyOverlay().pushFighterVision(vis, sim.getVisionState());
        World airWorld = sim.world();
        for (long id : sim.getAirEntityIds()) {
            ShuttleMission mission = airWorld.mission(id);
            if (mission == null || !mission.isVisible()) continue;
            if (!sim.getVisionState().isContributor(airWorld.airFaction(id))) continue;
            AirBody body = airWorld.kinematics(id);
            vis.addEphemeralSource((int) Math.floor(body.x), (int) Math.floor(body.y), 50, 3.5f);
        }
        // Player recon-ping reveals — same ephemeral-source seam as shuttles.
        // The sim owns the ping list + time-to-live; we just project the live
        // ones into the fog each frame (airLosRadius 0 = walls block, so the
        // reveal respects line of sight via the existing shadowcast).
        for (com.dillon.starsectormarines.battle.power.CommandPowerService.ActivePing p
                : sim.getCommandPowerService().getActivePings()) {
            vis.addEphemeralSource(p.cellX, p.cellY, p.radius, 0f);
        }
        // Always tick — dt=0 makes the sim a no-op but still clears the per-frame event lists,
        // so a paused caller doesn't keep replaying the previous frame's shot/death sounds.
        sim.advance(dt * speedMultiplier);
        // Flyby fighters run on the same scaled clock as the sim so pause / 1x / 2x / 4x
        // applies uniformly — spawning, strafing, and dogfighting all freeze on pause.
        renderer.getFlybyOverlay().advance(dt * speedMultiplier, sim, camera);
        // Impact FX: spawn at the moment the shot's visual reaches its endpoint
        // (instant for marine line tracers, on lifetime expiry for projectile
        // sprites), then advance particles on the same scaled clock.
        spawnImpactFx(sim);
        for (float[] impact : sim.getHeavyImpactsThisFrame()) {
            renderer.getImpactFx().spawnHeavyImpact(impact[0], impact[1], impact[2]);
            renderer.getGroundLights().spawnHeavyImpact(impact[0], impact[1], impact[2]);
            Vector2f loc = new Vector2f(
                    impact[0] * AUDIO_WORLD_UNITS_PER_CELL,
                    impact[1] * AUDIO_WORLD_UNITS_PER_CELL);
            Global.getSoundPlayer().playSound(SFX_NEAR_EXPLOSION,
                    0.72f, 1.0f, loc, new Vector2f(0f, 0f));
        }
        // Drain wreck smoke puffs the sim queued this tick — each entry is
        // {x, y, radiusCells}. Same scaled clock means wrecks stop smoking
        // when the sim pauses.
        for (float[] puff : sim.getSmokePuffsThisFrame()) {
            renderer.getImpactFx().spawnAmbientSmoke(puff[0], puff[1], puff[2]);
        }
        for (float[] burst : sim.getFireBurstsThisFrame()) {
            renderer.getImpactFx().spawnAmbientFire(burst[0], burst[1], burst[2]);
            renderer.getGroundLights().spawnFire(burst[0], burst[1], burst[2]);
        }
        renderer.getImpactFx().advance(dt * speedMultiplier);
        renderer.getGroundLights().advance(dt * speedMultiplier);
        renderer.getGroundLights().syncBoltLights(sim.getActiveShots());
        // Contrail trails — push the leading-edge sample for each in-flight
        // contrail shot and age the lot. Real (unscaled) dt, not sim-time, so
        // trails keep dissipating during sim pause (matches the old render-frame
        // aging). Ticked here, after sim.advance, so samples read post-tick
        // positions; the render pass just emits the ribbons.
        renderer.getContrailFx().tick(sim.getActiveShots(), dt);
        // Compound markers pulse on wall-clock so a paused sim still
        // visibly throbs at contested compounds — the player keeps reading
        // state during pauses (mirrors the charge-site marker behaviour).
        renderer.getCompoundMarkers().update(dt);
        // Selected-squad highlight — production cue (works without the debug
        // panel). Republished each frame off post-tick unit positions so the
        // green cells track members as they move; clears itself when the
        // selection drops or the squad is wiped out.
        SelectionHighlightPublisher.publish(selection, sim, highlights);
        CommanderInfluenceOverlayPublisher.publish(sim, highlights,
                debugMarineFriendlyInfluence, debugMarineHostileInfluence,
                debugDefenderFriendlyInfluence, debugDefenderHostileInfluence);
        if (debugConquestPerspective != null) {
            ConquestCommanderOverlayPublisher.publish(highlights,
                    sim.getCommanderSnapshot(debugConquestPerspective),
                    sim.getGrid().getWidth(), sim.getGrid().getHeight(),
                    selection.getSelectedSquadId());
            AssaultCommanderOverlayPublisher.publish(highlights,
                    sim.getCommanderSnapshot(debugConquestPerspective),
                    selection.getSelectedSquadId());
            SabotageCommanderOverlayPublisher.publish(highlights,
                    sim.getCommanderSnapshot(debugConquestPerspective),
                    selection.getSelectedSquadId());
            RaidCommanderOverlayPublisher.publish(highlights,
                    sim.getCommanderSnapshot(debugConquestPerspective),
                    selection.getSelectedSquadId());
            ExtractionCommanderOverlayPublisher.publish(highlights,
                    sim.getCommanderSnapshot(debugConquestPerspective),
                    selection.getSelectedSquadId(),
                    sim.getSwarmPressureSnapshot());
        } else {
            ConquestCommanderOverlayPublisher.clear(highlights);
            AssaultCommanderOverlayPublisher.clear(highlights);
            SabotageCommanderOverlayPublisher.clear(highlights);
            RaidCommanderOverlayPublisher.clear(highlights);
            ExtractionCommanderOverlayPublisher.clear(highlights);
        }
        // Roof alpha lerp runs on real dt (not sim-scaled) so the fog-of-war
        // fade keeps animating even when the sim is paused — matches how the
        // HUD ticks on real dt for the same reason.
        advanceRoofAlphaLerp(sim, dt);
        if (sim != null) sim.getFogOfWar().advanceFade(dt);
        driveShuttleEngineLoops(sim);
        playCombatEventSounds(sim);
        if (speedMultiplier > 0f) playRadioChatter(sim, dt);
    }

    @Override
    public void detach() {
        // Release the decal accumulator's FBO + color texture. Without this, an
        // attach/detach cycle leaks one FBO per battle — fine for a single
        // session, ugly across a multi-mission run.
        renderer.getDecalAccumulator().dispose();
        // Same leak concern as the decal accumulator, for the S2/S3 ground FBO set.
        renderer.getGroundParallax().dispose();
        renderer.getGroundLights().clear();
        if (retainedOverlay != null) retainedOverlay.detach();
        if (retainedSquadOverlay != null) retainedSquadOverlay.detach();
        if (retainedMechOverlay != null) retainedMechOverlay.detach();
        if (retainedPowerOverlay != null) retainedPowerOverlay.detach();
        if (retainedRetreatOverlay != null) retainedRetreatOverlay.detach();

        if (!audioActive) return;
        audioActive = false;
        battleMusic.stop();
        // Fade out our track without queuing a replacement, then let the campaign music resume.
        Global.getSoundPlayer().playCustomMusic(MUSIC_FADE_SECS, 0, null);
        Global.getSoundPlayer().setSuspendDefaultMusicPlayback(false);
    }

    // ---- BattleUiContext --------------------------------------------------

    /** Lazy-init the HUD once layout + camera are in place. Reused across rebuilds so panel state (hover, snapshots) survives sim-completion swaps. */
    private void ensureHud() {
        if (hud != null) return;
        hud = new BattleHud(this);
        // WorldPicker registered first — input order is reverse of add order, so
        // the dock panels (Overview / Detail / PlanDebug) see clicks first and
        // claim their own rows via consume(); WorldPicker only fires on the
        // leftover unconsumed clicks that landed in the world rect.
        hud.addPanel(new WorldPicker(this));
        // The MLX power tray owns cards; this small world-layer partner owns
        // only its reticle and next-click targeting. Added after WorldPicker so
        // an armed power claims the map click before squad selection sees it.
        commandPowerTargeting = new CommandPowerTargetingPanel(this);
        hud.addPanel(commandPowerTargeting);
        hud.addPanel(new TaskForceStatusPanel(this));
        // Per-squad GOAP plan readout. It has no all-squad overview: the
        // diagnostic opens only while WorldPicker has a squad in Selection.
        hud.addPanel(new SquadPlanDebugPanel(this));
        // Per-phase tick wall-time profile (top-center, beside DEBUG).
        // DevConfig-gated; informs
        // the upcoming DoD / ECS refactor by showing which tick phases are
        // actually expensive at peak unit counts.
        hud.addPanel(new TickProfileDebugPanel(this));
        // Debug toggles + actions (top-center beside Tick Profile, collapsed
        // by default). Replaces
        // the prior DebugTogglesWidget which was attached to the screen's
        // widget root rather than the hud — moved into the hud so input +
        // render ordering match the rest of the debug panels.
        DebugTogglesPanel debugPanel = new DebugTogglesPanel(this);
        debugPanel.addToggle("Docking paths",
                () -> BattleRenderer.DEBUG_RENDER_DOCKING_PATHS,
                () -> BattleRenderer.DEBUG_RENDER_DOCKING_PATHS =
                        !BattleRenderer.DEBUG_RENDER_DOCKING_PATHS);
        debugPanel.addToggle("Marine friendly field",
                () -> debugMarineFriendlyInfluence,
                () -> debugMarineFriendlyInfluence = !debugMarineFriendlyInfluence);
        debugPanel.addToggle("Marine hostile field",
                () -> debugMarineHostileInfluence,
                () -> debugMarineHostileInfluence = !debugMarineHostileInfluence);
        debugPanel.addToggle("Defender friendly field",
                () -> debugDefenderFriendlyInfluence,
                () -> debugDefenderFriendlyInfluence = !debugDefenderFriendlyInfluence);
        debugPanel.addToggle("Defender hostile field",
                () -> debugDefenderHostileInfluence,
                () -> debugDefenderHostileInfluence = !debugDefenderHostileInfluence);
        debugPanel.addToggle("Marine commander picture",
                () -> debugConquestPerspective == Faction.MARINE,
                () -> toggleConquestPicture(Faction.MARINE));
        debugPanel.addToggle("Defender commander picture",
                () -> debugConquestPerspective == Faction.DEFENDER,
                () -> toggleConquestPicture(Faction.DEFENDER));
        debugPanel.addDial("Structure relief",
                () -> renderer.getGroundParallax().parallaxStrength(),
                value -> renderer.getGroundParallax().setParallaxStrength((float) value),
                GroundParallaxPipeline.MIN_STRENGTH,
                GroundParallaxPipeline.MAX_STRENGTH,
                // Cubic response keeps sub-0.1 tuning usable despite the
                // intentionally unconstrained high-end experiment range.
                3.0);
        debugPanel.addDial("Surface relief",
                () -> renderer.getGroundParallax().surfaceStrength(),
                value -> renderer.getGroundParallax().setSurfaceStrength((float) value),
                GroundParallaxPipeline.MIN_SURFACE_STRENGTH,
                GroundParallaxPipeline.MAX_SURFACE_STRENGTH,
                3.0);
        debugPanel.addDial("Water waves",
                () -> renderer.getGroundParallax().waterWaveAmplitude(),
                value -> renderer.getGroundParallax().setWaterWaveAmplitude((float) value),
                GroundParallaxPipeline.MIN_WATER_WAVE_AMPLITUDE,
                GroundParallaxPipeline.MAX_WATER_WAVE_AMPLITUDE,
                2.0);
        debugPanel.addDial("Bump lighting",
                () -> renderer.getGroundParallax().lightingStrength(),
                value -> renderer.getGroundParallax().setLightingStrength((float) value),
                GroundParallaxPipeline.MIN_LIGHTING_STRENGTH,
                GroundParallaxPipeline.MAX_LIGHTING_STRENGTH,
                2.0);
        debugPanel.addAction("Force reinforcement", this::forceDefenderReinforcement);
        debugPanel.addToggle("Capture commander trace",
                () -> getSim() != null && getSim().isCommandTraceEnabled(),
                this::toggleCommanderTrace);
        debugPanel.addAction("Dump commander trace",
                () -> CommanderTraceDumper.dump(getSim()));
        debugPanel.addAction("Spawn mech family", () -> MechFamilyDebugSpawner.spawn(getSim()));
        TurretAuthorPanel turretAuthor = new TurretAuthorPanel(this);
        debugPanel.addToggle("Turret author",
                () -> turretAuthor.active,
                () -> turretAuthor.active = !turretAuthor.active);
        hud.addPanel(debugPanel);
        hud.addPanel(turretAuthor);
        // Player-facing battle dispatch surface. Added last so urgent comms
        // plates and the counterattack signpost paint above ordinary HUD
        // chrome; the panel is read-only and never consumes input.
        hud.addPanel(new BattleCommsPanel(this));
    }

    /** Installs or relayouts the retained overlay without rebuilding its reactive tree. */
    private void ensureRetainedOverlay(BattleSimulation sim) {
        if (retainedOverlay == null) {
            retainedOverlay = new BattleHudOverlay(value -> speedMultiplier = value);
        }
        retainedOverlay.attach(position, sim, speedMultiplier);
        if (retainedSquadOverlay == null) {
            retainedSquadOverlay = new BattleSquadOverlay(selection);
        }
        retainedSquadOverlay.attach(position, sim);
        if (retainedMechOverlay == null) {
            retainedMechOverlay = new BattleMechOverlay(selection);
        }
        retainedMechOverlay.attach(position, sim);
        if (retainedPowerOverlay == null) {
            retainedPowerOverlay = new BattlePowerOverlay(
                    commandPowerTargeting::toggle,
                    commandPowerTargeting::targetingPowerId);
        }
        retainedPowerOverlay.attach(position, sim);
        if (retainedRetreatOverlay == null) {
            retainedRetreatOverlay = new BattleRetreatOverlay(
                    this::retreatFromBattle, this::continueFromBattle);
        }
        retainedRetreatOverlay.attach(position, sim != null && sim.isComplete());
    }

    private void toggleConquestPicture(Faction perspective) {
        debugConquestPerspective = debugConquestPerspective == perspective
                ? null : perspective;
    }

    private void toggleCommanderTrace() {
        BattleSimulation sim = getSim();
        if (sim == null) return;
        BattleFixture fixture = getBattleFixture();
        sim.setCommandTraceEnabled(!sim.isCommandTraceEnabled(),
                fixture != null ? fixture.kind() : null);
    }

    /**
     * Debug action: post a defender {@link ReinforcementRequest} to the
     * service so the active means picks it up on its next slow-tick.
     * Rally = nearest defender compound to map center (matches the
     * GarrisonDepletedTrigger rally shape); falls back to map center
     * when no compound exists. No-op when no sim is bound.
     */
    private void forceDefenderReinforcement() {
        BattleSimulation sim = getSim();
        if (sim == null) return;
        ReinforcementService rs = sim.getReinforcementService();
        if (rs == null) return;
        int gw = sim.getGrid().getWidth();
        int gh = sim.getGrid().getHeight();
        int rallyX = gw / 2;
        int rallyY = gh / 2;
        TacticalMap tactical = sim.getTacticalMap();
        if (tactical != null) {
            List<TacticalNode> near = tactical.nearest(rallyX, rallyY, 1,
                    java.util.EnumSet.of(TacticalNode.Kind.COMMAND_POST,
                            TacticalNode.Kind.BARRACKS,
                            TacticalNode.Kind.ARMORY));
            if (!near.isEmpty()) {
                TacticalNode node = near.get(0);
                rallyX = node.centerX();
                rallyY = node.centerY();
            }
        }
        rs.post(new ReinforcementRequest(
                Faction.DEFENDER,
                ReinforcementRequest.Reason.SCRIPTED_TIMER,
                ReinforcementRequest.Strength.SMALL,
                rallyX, rallyY));
    }

    @Override
    public BattleSimulation getSim() {
        return ctx != null ? ctx.getBattleSimulation() : null;
    }

    @Override
    public BattleFixture getBattleFixture() {
        return ctx != null ? ctx.getBattleFixture() : null;
    }

    @Override
    public BattleCamera getCamera() {
        return camera;
    }

    @Override
    public BattleLayout getLayout() {
        return layout;
    }

    @Override
    public Selection getSelection() {
        return selection;
    }

    @Override
    public HighlightOverlay getHighlights() {
        return highlights;
    }

    /**
     * Drives the per-battle ambient bed and the sporadic distant-explosion atmosphere. Ambient
     * loops are re-armed every frame at low volume — sets a sonic floor without dominating the
     * mix. Distant booms tick on real-time dt (not sim time): one fires every
     * {@link #DISTANT_BOOM_MIN_GAP}..{@link #DISTANT_BOOM_MAX_GAP} seconds, alternating between
     * the dedicated muffled clip and a pool-explosion pitched + attenuated to read as far away.
     *
     * <p>Using real dt means the atmosphere keeps going during sim pause — pausing to inspect
     * the map shouldn't make the world go silent. Same reason the music doesn't pause.
     */
    private void driveAmbientBackground(float dt) {
        Vector2f zeroVel = new Vector2f(0f, 0f);
        for (int i = 0; i < activeAmbientLoops.length; i++) {
            // Loop id doubles as the playingEntity key — distinct entities per loop so the sound
            // system doesn't fold two simultaneous loops into a single voice.
            String id = activeAmbientLoops[i];
            Global.getSoundPlayer().playLoop(id, id, 1f, AMBIENT_VOLUME, activeAmbientAnchors[i], zeroVel);
        }
        distantBoomTimer -= dt;
        if (distantBoomTimer <= 0f) {
            playDistantBoom();
            distantBoomTimer = nextDistantBoomGap();
        }
    }

    /**
     * Plays one off-screen explosion. The emitter is placed at a random direction from the
     * camera focus at a fixed distance ({@link #DISTANT_BOOM_EMITTER_DISTANCE}), so each boom
     * has a clear left/right/front/back cue and gets attenuated by the engine into reading as
     * "somewhere out there" rather than "right here."
     */
    private void playDistantBoom() {
        float originX = camera != null ? camera.panCellX() * AUDIO_WORLD_UNITS_PER_CELL : 0f;
        float originY = camera != null ? camera.panCellY() * AUDIO_WORLD_UNITS_PER_CELL : 0f;
        float angle = audioRng.nextFloat() * (float) (Math.PI * 2.0);
        Vector2f loc = new Vector2f(
                originX + (float) Math.cos(angle) * DISTANT_BOOM_EMITTER_DISTANCE,
                originY + (float) Math.sin(angle) * DISTANT_BOOM_EMITTER_DISTANCE);
        Vector2f vel = new Vector2f(0f, 0f);
        float jitter = 1f + (audioRng.nextFloat() * 2f - 1f) * DISTANT_BOOM_PITCH_JITTER;
        if (audioRng.nextFloat() < DISTANT_BOOM_MUFFLED_CHANCE) {
            Global.getSoundPlayer().playSound(SFX_DISTANT_BOOM, jitter, DISTANT_BOOM_VOLUME, loc, vel);
        } else {
            // Pool explosion + sub-1 pitch + low volume reads as a far-off blast (the muffled clip
            // is great but having only one source for distant booms gets repetitive).
            Global.getSoundPlayer().playSound(SFX_NEAR_EXPLOSION,
                    NEAR_AS_DISTANT_PITCH * jitter, NEAR_AS_DISTANT_VOLUME, loc, vel);
        }
    }

    /**
     * Per-shuttle positional engine loop — every visible shuttle emits its
     * {@link EngineVoice} clip (a base-game {@code sfx_engines} loop chosen from the hull's
     * tech tier + size, resolved once per hull by {@link EngineVoiceResolver}) at its world
     * position every frame. The craft's {@link AirBody} is the {@code playingEntity} key (a stable per-entity instance), so three
     * shuttles landing at once stay on three distinct voices even when they share a clip id.
     *
     * <p>Volume scales by {@link AirAppearance#engineIntensity(boolean, float)} so on-ground idle reads quiet and
     * cruise reads loud; pitch sweeps {@link #ENGINE_PITCH_IDLE} → {@link #ENGINE_PITCH_CRUISE}
     * across that range plus a small per-shuttle deterministic offset (from the entity id,
     * stable frame-to-frame) so two craft on the same clip don't phase-lock. Velocity feeds
     * OpenAL Doppler as a shuttle banks over the camera. When no shuttles are visible we skip
     * the call and the loops self-fade over Starsector's default loop hold.
     */
    private void driveShuttleEngineLoops(BattleSimulation sim) {
        World world = sim.world();
        for (long id : sim.getAirEntityIds()) {
            ShuttleMission mission = world.mission(id);
            if (mission == null || !mission.isVisible()) continue;
            float intensity = AirAppearance.engineIntensity(true, world.altitudeT(id));
            if (intensity <= 0f) continue;
            AirBody body = world.kinematics(id);
            EngineVoice voice = EngineVoiceResolver.resolve(world.airType(id).renderHullId());
            // Deterministic ±jitter from the entity id so the offset doesn't change frame-to-frame.
            float pitchOffset = (((id >> 8) & 0xffL) / 255f * 2f - 1f) * SHUTTLE_ENGINE_PITCH_JITTER;
            float pitch = ENGINE_PITCH_IDLE + (ENGINE_PITCH_CRUISE - ENGINE_PITCH_IDLE) * intensity + pitchOffset;
            Vector2f loc = new Vector2f(body.x * AUDIO_WORLD_UNITS_PER_CELL,
                                        body.y * AUDIO_WORLD_UNITS_PER_CELL);
            Vector2f vel = shuttleVelocity(mission, body);
            // The AirBody instance is the stable per-entity loop-voice key (same
            // instance every frame and across sorties), so concurrent craft on the
            // same clip stay on distinct voices.
            Global.getSoundPlayer().playLoop(voice.loopSoundId, body, pitch,
                    SHUTTLE_ENGINE_VOLUME * intensity, loc, vel);
        }
    }

    /** Per-frame velocity for {@link #driveShuttleEngineLoops} Doppler — reads the AirBody directly. Returns zero on the ground / off-screen so audio stays parked. */
    private static Vector2f shuttleVelocity(ShuttleMission mission, AirBody body) {
        if (mission.state != ShuttleState.INCOMING && mission.state != ShuttleState.DEPARTING) {
            return new Vector2f(0f, 0f);
        }
        return new Vector2f(body.vx * AUDIO_WORLD_UNITS_PER_CELL,
                            body.vy * AUDIO_WORLD_UNITS_PER_CELL);
    }

    /**
     * One-shot SFX for events the sim emitted during the last tick: every shot plays a
     * rifle clip with pitch jitter (random pick from the 2-file pool + ±10% pitch makes
     * the variety read as much richer than 2 distinct samples), every marine death plays
     * one voice-dead clip.
     *
     * <p>Death audio is capped at one clip per frame — multiple marines dropping in the
     * same tick all trigger the pool, but only the first plays. Five overlapping screams
     * read as garbage; one is dramatic.
     *
     * <p>Defenders are silent on death for now: the voice pool is recorded as marines and
     * playing a "noooo" for an enemy would feel wrong. We can wire a separate defender /
     * alien death pool here when those clips exist.
     */
    /**
     * Emits impact FX (and HE impact sounds) keyed off the sim's per-frame
     * shot lists. Two emit windows:
     * <ul>
     *   <li>{@code shotsThisFrame} for full-line {@link ShotFx.Tracer} bodies.
     *       The line covers its whole path immediately, so its impact belongs at
     *       launch too.</li>
     *   <li>{@code shotsExpiredThisFrame} for traveling {@link ShotFx.Sprite}
     *       and {@link ShotFx.Bolt} bodies. Their impact belongs at the endpoint
     *       arrival, not at launch.</li>
     * </ul>
     *
     * <p>Explosive rounds additionally play a positional explosion clip at
     * impact, with gun-launched heavy HE mixed above rocket HE. Kinetic shells
     * stay silent — the fire SFX already covers them and a second clip per shot
     * is sonic clutter.
     */
    private void spawnImpactFx(BattleSimulation sim) {
        java.util.Random rng = java.util.concurrent.ThreadLocalRandom.current();
        Vector2f zeroVel = new Vector2f(0f, 0f);
        NavigationGrid grid = sim.getGrid();
        // Line-tracer shots spawn their impact at fire — the line covers the
        // whole travel instantly. Traveling bodies (sprites and bolts) wait
        // for arrival in the second pass.
        for (ShotEvent s : sim.getShotsThisFrame()) {
            renderer.getGroundLights().spawnMuzzle(s);
            // Every shooting marine / militia / alien ejects a casing where
            // they're standing (skip rockets — tube-launched, no brass).
            if (s.specialEquipmentDef == null && s.turretStructureDef == null) {
                ImpactDecals.spawnShellCasing(sim, rng, s.fromX, s.fromY);
            }
            WeaponFxRuntime.spawnMuzzle(renderer.getImpactFx(), s);
            // Line tracers (no projectile sprite) land their impact instantly;
            // projectile-sprite shots defer it to arrival (handled below).
            if (ShotFx.of(s).travels()) continue;
            if (!s.impacts()) continue;
            boolean isWall = isWallAt(grid, s.toX, s.toY);
            WeaponFxDef fx = WeaponFxRuntime.definition(s);
            WeaponFxRuntime.spawnImpactAndAftermath(renderer.getImpactFx(), s, isWall);
            renderer.getGroundLights().spawnImpact(fx, s.toX, s.visualToY());
            ImpactDecals.spawnWeaponImpact(sim, rng, fx, s.toX, s.toY, isWall);
        }
        for (ShotEvent s : sim.getShotsExpiredThisFrame()) {
            if (!ShotFx.of(s).travels()) continue;
            if (!s.impacts()) continue;
            boolean isWall = isWallAt(grid, s.toX, s.toY);
            WeaponFxDef fx = WeaponFxRuntime.definition(s);
            WeaponFxRuntime.spawnImpactAndAftermath(renderer.getImpactFx(), s, isWall);
            if (s.turretStructureDef != null) {
                TurretImpactAudio.Cue cue = TurretImpactAudio.resolve(
                        s.turretStructureDef, SFX_NEAR_EXPLOSION);
                if (cue != null) {
                    float pitch = 0.9f + rng.nextFloat() * 0.2f;
                    Vector2f loc = new Vector2f(
                            s.toX * AUDIO_WORLD_UNITS_PER_CELL,
                            s.toY * AUDIO_WORLD_UNITS_PER_CELL);
                    Global.getSoundPlayer().playSound(
                            cue.soundId(), pitch, cue.volume(), loc, zeroVel);
                }
            } else if (s.specialEquipmentDef != null) {
                float pitch = 0.9f + rng.nextFloat() * 0.2f;
                Vector2f loc = new Vector2f(
                        s.toX * AUDIO_WORLD_UNITS_PER_CELL,
                        s.toY * AUDIO_WORLD_UNITS_PER_CELL);
                if (s.specialEquipmentDef.impactSoundId() != null) {
                    Global.getSoundPlayer().playSound(s.specialEquipmentDef.impactSoundId(),
                            pitch, 0.70f, loc, zeroVel);
                }
            } else if (s.mechWeaponDef != null) {
                if (fx.hasExplosiveImpact()) {
                    float pitch = 0.9f + rng.nextFloat() * 0.2f;
                    Vector2f loc = new Vector2f(
                            s.toX * AUDIO_WORLD_UNITS_PER_CELL,
                            s.toY * AUDIO_WORLD_UNITS_PER_CELL);
                    float volume = fx.hasHeavyImpact() ? 0.86f : 0.65f;
                    Global.getSoundPlayer().playSound(SFX_NEAR_EXPLOSION, pitch, volume, loc, zeroVel);
                }
            }
            ImpactDecals.spawnWeaponImpact(sim, rng, fx, s.toX, s.toY, isWall);
            renderer.getGroundLights().spawnImpact(fx, s.toX, s.visualToY());
        }
        // Warheads a point-defence emplacement stopped come apart in the air.
        // The mount's own burst already flashed on the way in; this is the
        // payload going off where it was hit, which is what makes an intercept
        // read as a kill rather than as a missile that quietly stopped
        // existing. Deliberately not routed through the damage path: the
        // detonation is presentation, and hurts nobody.
        List<float[]> interceptPoints = sim.getShots().getInterceptPointsThisFrame();
        for (int i = 0, n = interceptPoints.size(); i < n; i++) {
            float[] point = interceptPoints.get(i);
            renderer.getImpactFx().spawnHeavyImpact(point[0], point[1], INTERCEPT_BURST_CELLS);
            renderer.getGroundLights().spawnImpact(null, point[0], point[1]);
        }
    }

    /** True when the endpoint cell is non-walkable (wall / vehicle / turret mount) and the impact should read as a chip on solid material rather than a kick of floor dust. */
    private static boolean isWallAt(NavigationGrid grid, float x, float y) {
        int cx = (int) Math.floor(x);
        int cy = (int) Math.floor(y);
        if (!grid.inBounds(cx, cy)) return false;
        return !grid.isWalkable(cx, cy);
    }

    private void playCombatEventSounds(BattleSimulation sim) {
        BattleShotAudio.playPositional(sim.getShotsThisFrame());
        Vector2f zeroVel = new Vector2f(0f, 0f);
        playSystemActivationCues(sim, zeroVel);
        LongList deaths = sim.getDeathsThisFrame();
        for (int i = 0, n = deaths.size(); i < n; i++) {
            long u = deaths.getLong(i);
            if (sim.identity().faction(u) == Faction.MARINE) {
                Vector2f loc = new Vector2f(
                        sim.world().renderX(u) * AUDIO_WORLD_UNITS_PER_CELL,
                        sim.world().renderY(u) * AUDIO_WORLD_UNITS_PER_CELL);
                Global.getSoundPlayer().playSound(SFX_VOICE_DEAD, 1f, 1f, loc, zeroVel);
                break;  // one voice per frame
            }
        }
    }

    /**
     * One positional cue per integral system spent this frame, at the wearer.
     *
     * <p>Reads the presentation-owned activation list rather than watching for a
     * change in what is on screen: the edge was already detected once, in the
     * system that authors the treatment, and detecting it a second time here
     * would be a second answer to the same question. Which cue plays keys on
     * the capability — a screen went up, or a system is simply running — never
     * on which armour pattern produced it.
     */
    private void playSystemActivationCues(BattleSimulation sim, Vector2f zeroVel) {
        LongList activations = sim.getSystemActivationsThisFrame();
        SystemFxService fx = sim.getRoster().systemFx();
        for (int i = 0, n = activations.size(); i < n; i++) {
            long unit = activations.getLong(i);
            Vector2f loc = new Vector2f(
                    sim.world().renderX(unit) * AUDIO_WORLD_UNITS_PER_CELL,
                    sim.world().renderY(unit) * AUDIO_WORLD_UNITS_PER_CELL);
            String cue = fx.arcDegrees(unit) > 0f
                    ? SFX_SYSTEM_SCREEN_UP : SFX_SYSTEM_ENGAGED;
            Global.getSoundPlayer().playSound(cue, 1f, 1f, loc, zeroVel);
        }
    }

    /** Plays at most one quiet radio cue at the speaking squad's current centroid. */
    private void playRadioChatter(BattleSimulation sim, float dt) {
        BattleRadioChatter.Emission emission = radioChatter.advance(dt, sim);
        if (emission == null) return;
        Vector2f loc = new Vector2f(
                emission.cellX() * AUDIO_WORLD_UNITS_PER_CELL,
                emission.cellY() * AUDIO_WORLD_UNITS_PER_CELL);
        Global.getSoundPlayer().playSound(
                emission.cue().soundId(), 1f, emission.cue().volume(), loc,
                new Vector2f(0f, 0f));
    }

    private void retreatFromBattle() {
        if (ctx == null) return;
        BattleSimulation sim = ctx.getBattleSimulation();
        if (sim == null || sim.isComplete()) return;
        // Retreat is an abandonment, not a defeat: release the live simulation
        // and return without resolving rewards, casualties, or campaign effects.
        ctx.setBattleSimulation(null);
        ctx.goTo(ScreenId.MISSION_SELECT);
    }

    private void continueFromBattle() {
        if (ctx == null) return;
        BattleSimulation sim = ctx.getBattleSimulation();
        if (sim != null && sim.isComplete()) {
            // Compute + apply outcome once, then hand off to RESULTS.
            Mission mission = ctx.getSelectedMission();
            Set<String> deployedFireteams = mission.source.isDebug()
                    || mission.source == MissionSource.STATIONING
                    ? Collections.emptySet()
                    : ctx.getSelectedMarineSquadIds();
            MissionOutcome outcome = MissionResolver.compute(sim, mission,
                    ctx.getSelectedCaptain(), deployedFireteams);
            MissionResolver.apply(outcome);
            ctx.setLastOutcome(outcome);
            ctx.setLootManifest(LootGenerator.generate(outcome));
            ctx.goTo(ScreenId.RESULTS);
        }
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        // Retained command surfaces claim only their compact corner/tray
        // rectangles. Everywhere else input continues to the debug HUD and
        // battlefield picker.
        if (retainedOverlay != null) retainedOverlay.processInput(events);
        if (retainedSquadOverlay != null) retainedSquadOverlay.processInput(events);
        if (retainedMechOverlay != null) retainedMechOverlay.processInput(events);
        if (retainedPowerOverlay != null) retainedPowerOverlay.processInput(events);
        if (retainedRetreatOverlay != null) retainedRetreatOverlay.processInput(events);
        // HUD gets first crack after retained chrome so a click on a squad row doesn't
        // also pan the camera or hit a future world-picker on the cells the
        // panel overlays. Panels self-consume claimed events.
        if (hud != null) hud.processInput(events);
        // Debug damage runs BEFORE pan-drag so shift+RMB consumes the event
        // and the plain-RMB pan handler never sees it. Plain RMB (no shift)
        // is unclaimed and falls through to pan.
        handleDebugDamageInput(events);
        handleCameraInput(events);
        handleDebugZoneToggle(events);
    }

    /**
     * Camera input: wheel-to-zoom (zoom-to-cursor — the world point under the
     * mouse stays under the mouse), RMB-drag-to-pan (no shift; shift+RMB is
     * the debug wall-damage gesture and gets consumed earlier), and
     * WASD/arrow-key pan (the key flags are flipped here on down/up events
     * and the actual pan integration happens in {@link #advance} so a held
     * key keeps panning between events).
     */
    private void handleCameraInput(List<InputEventAPI> events) {
        cameraControls.process(events, camera);
    }

    /** Debug-only: Z toggles {@link #debugZonesVisible}. Used to eyeball-verify the zone graph after wall breaches. */
    @DebugOnly
    private void handleDebugZoneToggle(List<InputEventAPI> events) {
        if (events == null) return;
        for (InputEventAPI e : events) {
            if (e.isConsumed()) continue;
            if (e.isKeyDownEvent() && e.getEventValue() == org.lwjgl.input.Keyboard.KEY_Z) {
                debugZonesVisible = !debugZonesVisible;
                e.consume();
            }
            if (e.isKeyDownEvent() && e.getEventValue() == org.lwjgl.input.Keyboard.KEY_F5) {
                long vId = getSelection().getSelectedVehicleId();
                BattleSimulation bsim = getSim();
                if (vId != 0L && bsim != null) {
                    // dump no-ops if the id no longer resolves to a live vehicle (has-gated).
                    VehicleStateDumper.dump(vId, bsim.convoy(), bsim.getGrid());
                    e.consume();
                }
            }
        }
    }

    /**
     * Debug-only: Shift + right-click in the grid area destroys the wall cell
     * under the cursor (one shot levels it to rubble — bypasses HP). Used to
     * validate {@link NavigationGrid#damageCell} flow and the rubble rendering
     * without an aerial-strike entity wired up.
     *
     * <p>RMB instead of LMB so widget clicks aren't shadowed; Shift gate so a
     * stray right-click can't accidentally rearrange the map.
     */
    @DebugOnly
    private void handleDebugDamageInput(List<InputEventAPI> events) {
        if (events == null || layout == null || camera == null || ctx == null) return;
        BattleSimulation sim = ctx.getBattleSimulation();
        if (sim == null) return;
        NavigationGrid grid = sim.getGrid();
        for (InputEventAPI e : events) {
            if (e.isConsumed()) continue;
            if (!e.isRMBDownEvent() || !e.isShiftDown()) continue;
            float px = e.getX();
            float py = e.getY();
            if (!camera.containsScreen(px, py)) continue;
            int cx = (int) Math.floor(camera.screenToCellX(px));
            int cy = (int) Math.floor(camera.screenToCellY(py));
            if (!grid.inBounds(cx, cy)) continue;
            // One-shot demolition — pass the cell's full HP so any wall hit
            // flips to rubble regardless of starting durability. Routed
            // through the sim so the zone graph rebuilds on a successful hit.
            int hp = grid.getWallHp(cx, cy);
            if (hp > 0) sim.damageCell(cx, cy, hp);
            e.consume();
        }
    }

    @Override
    public void render(float alphaMult) {
        if (layout == null) return;
        BattleSimulation sim = ctx != null ? ctx.getBattleSimulation() : null;

        if (sim != null) {
            // Bracket the world layer with a scissor clip locked to the grid
            // viewport — under zoom, content (units near the edge, the world
            // floor quad, tracers, fighter shadows) projects outside the grid
            // rect and would otherwise bleed over the speed-button strip and
            // Back button.
            //
            // glScissor takes WINDOW pixels, but layout.gridX/Y/W/H are in
            // Starsector's UI-space units (which scale with the user's UI
            // scale setting and the framebuffer DPI). Feeding UI units to
            // glScissor directly clips at the wrong place — visible as the
            // top of the play area going black at zoom.
            //
            // Both spaces are Y-up with origin at the screen's bottom-left and
            // the UI ortho spans getScreenWidth()×getScreenHeight(), so one
            // ratio per axis converts absolute UI coords straight to window px
            // — no dialog-origin term, and BattleLayout builds gridX/gridY off
            // position.getX()/getY() so they are already absolute. Both inputs
            // are cached LWJGL/settings values, NOT a GL readback: a glGet*
            // here forces a synchronous round-trip that stalls async-renderer
            // bridge mods (same ratio as BridgeRenderer.scaleX/scaleY).
            //
            // Push SCISSOR_BIT so we restore both the prior box and the
            // enable state on pop.
            glPushAttrib(GL_SCISSOR_BIT);
            float sx = Display.getWidth()  / Math.max(1f, Global.getSettings().getScreenWidth());
            float sy = Display.getHeight() / Math.max(1f, Global.getSettings().getScreenHeight());
            int gridFbX = Math.round(layout.gridX * sx);
            int gridFbY = Math.round(layout.gridY * sy);
            int gridFbW = Math.round(layout.gridW * sx);
            int gridFbH = Math.round(layout.gridH * sy);
            glEnable(GL_SCISSOR_TEST);
            glScissor(gridFbX, gridFbY, gridFbW, gridFbH);
            com.dillon.starsectormarines.ops.battleview.RenderContext rc =
                    new com.dillon.starsectormarines.ops.battleview.RenderContext(
                            sim, camera, layout, alphaMult, lastAdvanceDt,
                            debugZonesVisible, highlights, selection);
            renderer.renderWorld(rc);
            glPopAttrib();
        }

        // HUD sits above the world layer but below the victory/defeat banner — a
        // mid-battle squad-select shouldn't be visually competing with the
        // end-of-battle takeover.
        if (hud != null) hud.render(alphaMult);

        // Player-facing MLX chrome paints above the debug HUD. Its root is
        // transparent, so only the compact command surfaces touch the canvas.
        if (retainedOverlay != null) retainedOverlay.render(alphaMult);
        if (retainedSquadOverlay != null) retainedSquadOverlay.render(alphaMult);
        if (retainedMechOverlay != null) retainedMechOverlay.render(alphaMult);
        if (retainedPowerOverlay != null) retainedPowerOverlay.render(alphaMult);
        if (retainedRetreatOverlay != null) retainedRetreatOverlay.render(alphaMult);

        if (sim != null && sim.isComplete()) {
            renderBanner(sim.getWinner(), alphaMult);
        }

    }

    // ---- rendering (world-layer methods moved to BattleRenderer) -----------

    /**
     * Lerps each building's {@code currentAlpha → targetAlpha} on real dt so
     * the fade is decoupled from sim tick rate. Called from {@link #advance}.
     */
    private void advanceRoofAlphaLerp(BattleSimulation sim, float dt) {
        if (sim == null) return;
        BuildingVisibilityPass.advanceAlpha(sim.getBuildings(), dt);
    }

    private void renderBanner(Faction winner, float alphaMult) {
        boolean victory = winner == Faction.MARINE;
        String text = Strings.get(victory ? "battleVictory" : "battleDefeat");
        Color color = victory ? VICTORY_COLOR : DEFEAT_COLOR;

        float textW = Fonts.ORBITRON_24_BOLD.measureWidth(text);
        float textH = Fonts.ORBITRON_24_BOLD.getLineHeight();
        float padX = 24f;
        float padY = 12f;
        float boxW = textW + 2 * padX;
        float boxH = textH + 2 * padY;
        // Centered on the viewport (the on-screen grid rect), NOT on the world
        // rect — under pan the world rect slides around inside the viewport,
        // and the victory/defeat banner should sit still where the player
        // expects it: dead-center over the play area.
        float boxX = layout.gridX + (layout.gridW - boxW) / 2f;
        float boxY = layout.gridY + (layout.gridH - boxH) / 2f;

        fillRect(boxX, boxY, boxW, boxH, BANNER_BG, 0.92f * alphaMult);
        // Color-tinted border
        outlineRect(boxX, boxY, boxW, boxH, color, alphaMult);

        Fonts.ORBITRON_24_BOLD.drawString(text,
                boxX + padX, boxY + padY + textH, color, alphaMult);
    }

    // ---- raw-GL helpers ----------------------------------------------------

    private static void fillRect(float rx, float ry, float rw, float rh, Color c, float alpha) {
        if (rw <= 0f || rh <= 0f) return;
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, alpha);
        glBegin(GL_QUADS);
        glVertex2f(rx,      ry);
        glVertex2f(rx + rw, ry);
        glVertex2f(rx + rw, ry + rh);
        glVertex2f(rx,      ry + rh);
        glEnd();
    }

    private static void outlineRect(float rx, float ry, float rw, float rh, Color c, float alpha) {
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, alpha);
        org.lwjgl.opengl.GL11.glLineWidth(1.5f);
        glBegin(org.lwjgl.opengl.GL11.GL_LINE_LOOP);
        glVertex2f(rx,      ry);
        glVertex2f(rx + rw, ry);
        glVertex2f(rx + rw, ry + rh);
        glVertex2f(rx,      ry + rh);
        glEnd();
    }
}
