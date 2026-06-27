package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.combat.fx.SmokingWreck;
import com.dillon.starsectormarines.battle.air.AirAppearance;
import com.dillon.starsectormarines.battle.air.Shuttle;
import com.dillon.starsectormarines.battle.air.engine.EngineFxRenderer;
import com.dillon.starsectormarines.battle.air.engine.EngineSlotResolver;
import com.dillon.starsectormarines.battle.air.engine.EngineVoice;
import com.dillon.starsectormarines.battle.air.engine.EngineVoiceResolver;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.command.reinforcement.ReinforcementRequest;
import com.dillon.starsectormarines.battle.command.reinforcement.ReinforcementService;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.ui.BattleHud;
import com.dillon.starsectormarines.battle.ui.BattleUiContext;
import com.dillon.starsectormarines.battle.ui.compound.CompoundProgressPanel;
import com.dillon.starsectormarines.battle.ui.panel.DebugTogglesPanel;
import com.dillon.starsectormarines.battle.ui.panel.TurretAuthorPanel;
import com.dillon.starsectormarines.battle.ui.panel.SquadDetailPanel;
import com.dillon.starsectormarines.battle.ui.panel.SquadOverviewPanel;
import com.dillon.starsectormarines.battle.ui.panel.SquadPlanDebugPanel;
import com.dillon.starsectormarines.battle.ui.panel.TickProfileDebugPanel;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.highlight.SelectionHighlightPublisher;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.ui.picking.WorldPicker;
import com.dillon.starsectormarines.battle.combat.fx.EffectsService;
import com.dillon.starsectormarines.battle.combat.fx.ImpactDecals;
import com.dillon.starsectormarines.battle.combat.fx.ImpactProfile;
import com.dillon.starsectormarines.battle.combat.fx.WeaponLights;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.ops.battleview.BattleSprites;
import com.dillon.starsectormarines.render2d.LightKernel;
import com.dillon.starsectormarines.ui.ButtonWidget;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.LabelWidget;
import com.dillon.starsectormarines.ui.WidgetRoot;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import org.apache.log4j.Logger;
import org.lwjgl.opengl.Display;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.List;

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
 * <p>Back returns to {@link ScreenId#MISSION_SELECT}. A dedicated RESULTS screen
 * (casualties, XP, payout) comes after MVP — for now the player just sees the
 * banner and backs out.
 */
public class BattleScreen implements Screen, BattleUiContext {

    private static final Logger LOG = Global.getLogger(BattleScreen.class);

    private static final Color HEADER_COLOR   = new Color(0xC8, 0xE0, 0xFF);
    private static final Color ACTIVE_SPEED   = new Color(0xFF, 0xB8, 0x00);
    private static final Color BANNER_BG      = new Color(0x10, 0x14, 0x1E);
    private static final Color VICTORY_COLOR  = new Color(0x80, 0xE0, 0x80);
    private static final Color DEFEAT_COLOR   = new Color(0xE0, 0x60, 0x60);
    private static final float SPEED_BTN_W    = 60f;
    private static final float SPEED_BTN_H    = 32f;
    private static final float SPEED_BTN_GAP  = 6f;
    private static final float SPEED_MARK_H   = 3f;

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
    private static final String SFX_RIFLE     = "marines_smallarms_rifle";
    private static final String SFX_VOICE_DEAD = "marines_voice_dead";
    private static final String SFX_DISTANT_BOOM = "marines_explosion_muffled";
    private static final String SFX_NEAR_EXPLOSION = "marines_explosion";
    /** Crossfade duration (seconds, whole numbers required) for entering / leaving the battle music. */
    private static final int MUSIC_FADE_SECS  = 2;
    /** Pitch lerp endpoints for the shuttle engine loop: idle on the ground → full at cruise. */
    private static final float ENGINE_PITCH_IDLE   = 0.7f;
    private static final float ENGINE_PITCH_CRUISE = 1.0f;
    /** Half-width of the rifle pitch jitter — ±10% on top of 1.0, so the 2-clip pool feels richer than 2 clips. */
    private static final float RIFLE_PITCH_JITTER = 0.10f;
    private static final float RIFLE_VOLUME       = 0.5f;
    /** Cells → OpenAL world units, for positional SFX. Must match {@code FlybyOverlay.AUDIO_WORLD_UNITS_PER_CELL}. */
    private static final float AUDIO_WORLD_UNITS_PER_CELL = 30f;
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

    /** Volume scalar on a shuttle's engine loop, multiplied by {@link Shuttle#engineIntensity()} and the per-clip base in sounds.json. */
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
    private static final float[] SPEED_OPTIONS = {0f, 1f, 2f, 4f};
    private static final String[] SPEED_KEYS   = {
            "battleSpeedPause", "battleSpeed1x", "battleSpeed2x", "battleSpeed4x"
    };

    private final WidgetRoot widgets = new WidgetRoot();
    /** Battle HUD — squad overview/detail panels today; mini-map + objectives later. Lazy-built once {@link #layout} and {@link #camera} are ready, then reused across rebuilds. */
    private BattleHud hud;
    /** Shared selection state read by HUD panels (and, later, a world-picker). Survives across attach()/rebuild() cycles; self-heals when the selected squad disappears. */
    private final Selection selection = new Selection();
    /** Shared debug cell-highlight overlay — populated by HUD panels, rendered between the grid pass and the unit sprites. */
    private final HighlightOverlay highlights = new HighlightOverlay();

    private PositionAPI position;
    private MarineOpsContext ctx;
    private BattleLayout layout;
    private BattleCamera camera;
    /**
     * Inherited dialog scissor box (= dialog rect in framebuffer px), cached to
     * derive the UI→FB scale instead of reading {@code GL_SCISSOR_BOX} every
     * frame: a {@code glGet*} forces a synchronous round-trip that stalls
     * async-renderer bridge mods (e.g. genir's). The box is in framebuffer px, so
     * we re-sample whenever the drawable size changes (window resize / fullscreen
     * toggle), keyed off {@link Display#getWidth()}/{@link Display#getHeight()} —
     * cached LWJGL values, NOT a GL readback. {@code uiSampledFbW < 0} = unsampled.
     */
    private int uiSampledFbW = -1;
    private int uiSampledFbH = -1;
    private int uiDialogFbX, uiDialogFbY, uiDialogFbW, uiDialogFbH;
    /** Pan-drag state: true while RMB is held (without shift, which routes to debug damage). */
    private boolean panDragging;
    private int lastDragX;
    private int lastDragY;
    /** Held-key pan state, polled in advance() so WASD/arrow holds keep panning between key events. */
    private boolean panKeyW, panKeyA, panKeyS, panKeyD;
    /** Cells per second of keyboard-pan, in world cells (the camera converts to pixels per its zoom). */
    private static final float KEY_PAN_CELLS_PER_SEC = 18f;
    private float speedMultiplier = 1f;
    /** Pixel x-center of each speed button, captured at layout time for the active-marker dot. */
    private final float[] speedBtnCenterX = new float[SPEED_OPTIONS.length];
    private float speedBtnBottomY;
    /** Tracks the last-seen sim completion flag so we can rebuild widgets when it flips. */
    private boolean lastSimComplete;
    /** Owns all loaded sprite sheets, frame data, and ensure/load methods. */
    private final BattleSprites sprites = new BattleSprites();
    /** World-layer render pipeline — owns tile batches, FX systems, and all render/draw methods. */
    private final com.dillon.starsectormarines.ops.battleview.BattleRenderer renderer = new com.dillon.starsectormarines.ops.battleview.BattleRenderer(sprites);
    /**
     * Real-time {@code dt} from the most recent {@link #advance} call. Passed
     * into {@link com.dillon.starsectormarines.ops.battleview.RenderContext} so
     * the renderer can age contrails on real time during sim pause.
     */
    private float lastAdvanceDt = 0f;
    /** Debug toggle (Z) — tints each navigation zone with a stable per-id color so the partitioning + new portals from wall breaches are eyeball-verifiable. */
    @DebugOnly
    private boolean debugZonesVisible;
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

    @Override
    public void attach(PositionAPI position, MarineOpsContext ctx, Runnable dismissDialog) {
        this.position = position;
        this.ctx = ctx;
        this.speedMultiplier = 1f;
        sprites.ensureUnitSheets();
        sprites.ensureVehicleSheets();
        sprites.ensureTurretSprites();
        sprites.ensureMarineSecondarySprites();
        sprites.ensureDecalSheet();
        sprites.ensureTileSheet();
        sprites.ensureRoadSheet();
        sprites.ensureFloorsSheet();
        sprites.ensureNatureSheet();
        sprites.ensureWaterSheet();
        sprites.ensureUrbanTile3Sheet();
        sprites.ensureShuttleSprites();
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
     * Suspend the campaign music player and crossfade into our looping battle track.
     * Guarded so re-entry from a dialog resize (attach() is documented as idempotent)
     * doesn't restart the music mid-battle. The ticking-clock loop itself is started
     * lazily on the first {@link #advance(float)} — {@code playUILoop} must be called
     * every frame anyway, so there's no benefit to kicking it off here.
     */
    private void startBattleAudio() {
        if (audioActive) return;
        audioActive = true;
        Global.getSoundPlayer().setSuspendDefaultMusicPlayback(true);
        String track = BATTLE_MUSIC_POOL[audioRng.nextInt(BATTLE_MUSIC_POOL.length)];
        Global.getSoundPlayer().playCustomMusic(MUSIC_FADE_SECS, MUSIC_FADE_SECS, track, true);
        BattleSimulation sim = ctx != null ? ctx.getBattleSimulation() : null;
        int gridW = sim != null ? sim.getGrid().getWidth()  : BattleSetup.GRID_W;
        int gridH = sim != null ? sim.getGrid().getHeight() : BattleSetup.GRID_H;
        pickAmbient(gridW, gridH);
        distantBoomTimer = nextDistantBoomGap();
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
        widgets.clear();
        if (position == null || ctx == null) return;

        BattleSimulation sim = ctx.getBattleSimulation();
        int gridW = sim != null ? sim.getGrid().getWidth()  : BattleSetup.GRID_W;
        int gridH = sim != null ? sim.getGrid().getHeight() : BattleSetup.GRID_H;
        layout = new BattleLayout(position, gridW, gridH);
        // Camera survives rebuilds (so the player's pan/zoom isn't lost when the
        // sim transitions to complete and we rebuild widgets). Only construct
        // it the first time, then refresh the viewport rect on subsequent rebuilds.
        if (camera == null || camera.worldCellsW() != gridW || camera.worldCellsH() != gridH) {
            camera = new BattleCamera(gridW, gridH);
        }
        camera.setViewport(layout.gridX, layout.gridY, layout.gridW, layout.gridH, layout.cellSize);
        ensureHud();
        lastSimComplete = sim != null && sim.isComplete();

        // Bottom-left action button — Back when in-progress, Continue when done.
        String actionLabelKey = lastSimComplete ? "battleContinue" : "actionBack";
        ButtonWidget actionBtn = new ButtonWidget(layout.backX, layout.backY,
                BattleLayout.BACK_W, BattleLayout.BACK_H,
                () -> onBackOrContinue());
        widgets.add(actionBtn);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                Strings.get(actionLabelKey),
                layout.backX + 12f, layout.backY + BattleLayout.BACK_H - 6f, HEADER_COLOR));

        // Speed buttons (top-right of controls strip)
        float rowW = SPEED_OPTIONS.length * SPEED_BTN_W
                + (SPEED_OPTIONS.length - 1) * SPEED_BTN_GAP;
        float startX = layout.controlsX + layout.controlsW - rowW;
        float btnY = layout.controlsY + (layout.controlsH - SPEED_BTN_H) / 2f;
        speedBtnBottomY = btnY;
        for (int i = 0; i < SPEED_OPTIONS.length; i++) {
            float bx = startX + i * (SPEED_BTN_W + SPEED_BTN_GAP);
            final float target = SPEED_OPTIONS[i];
            ButtonWidget btn = new ButtonWidget(bx, btnY, SPEED_BTN_W, SPEED_BTN_H,
                    () -> speedMultiplier = target);
            widgets.add(btn);
            // Center the label inside the button.
            String label = Strings.get(SPEED_KEYS[i]);
            float labelW = Fonts.ORBITRON_20.measureWidth(label);
            float labelX = bx + (SPEED_BTN_W - labelW) / 2f;
            widgets.add(new LabelWidget(Fonts.ORBITRON_20, label,
                    labelX, btnY + SPEED_BTN_H - 6f, HEADER_COLOR));
            speedBtnCenterX[i] = bx + SPEED_BTN_W / 2f;
        }

    }

    @Override
    public void advance(float dt) {
        lastAdvanceDt = dt;
        widgets.advance(dt);
        // HUD ticks on real dt (not sim-scaled) so panel snapshots and hover
        // state still update when the sim is paused. Panels' update() just
        // refreshes their cached views over the sim — cheap even at every frame.
        if (hud != null) hud.update(dt);
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
        // Keyboard pan integrates on real dt (not sim-time) so the camera still
        // moves while the sim is paused — the player should be able to look
        // around the map without unpausing. Diagonal holds aren't normalized;
        // pressing two axes just sums them, which gives a slightly faster
        // diagonal pan and feels right for a top-down map.
        if (camera != null) {
            float dx = (panKeyD ? 1f : 0f) - (panKeyA ? 1f : 0f);
            float dy = (panKeyW ? 1f : 0f) - (panKeyS ? 1f : 0f);
            if (dx != 0f || dy != 0f) {
                camera.panByCells(dx * KEY_PAN_CELLS_PER_SEC * dt,
                                  dy * KEY_PAN_CELLS_PER_SEC * dt);
            }
        }
        // playUILoop is documented as "must be called every frame or the loop will fade out" —
        // re-arming it every advance is how Starsector expects loops to be driven. When this
        // screen stops being current, advance() stops firing and all loops fade automatically.
        if (audioActive) {
            Global.getSoundPlayer().playUILoop(LOOP_TICKING, 1f, 1f);
            driveAmbientBackground(dt);
        }
        BattleSimulation sim = ctx != null ? ctx.getBattleSimulation() : null;
        if (sim == null) return;
        // Rebuild ephemeral vision sources (shuttles + strafing fighters)
        // each frame so the fog bitmap always reflects the latest positions.
        // Cleared + re-pushed every frame; VisionService only processes them
        // on vision-tick frames (every 3rd sim tick).
        com.dillon.starsectormarines.battle.vision.VisionService vis = sim.getVision();
        vis.clearEphemeralSources();
        renderer.getFlybyOverlay().pushFighterVision(vis, sim.getVisionState());
        for (com.dillon.starsectormarines.battle.air.Shuttle s : sim.getShuttles()) {
            if (!s.isVisible()) continue;
            if (!sim.getVisionState().isContributor(s.faction)) continue;
            vis.addEphemeralSource((int) s.body.x, (int) s.body.y, 50, 3.5f);
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
        // Drain wreck smoke puffs the sim queued this tick — each entry is
        // {x, y, radiusCells}. Same scaled clock means wrecks stop smoking
        // when the sim pauses.
        for (float[] puff : sim.getSmokePuffsThisFrame()) {
            renderer.getImpactFx().spawnAmbientSmoke(puff[0], puff[1], puff[2]);
        }
        for (float[] burst : sim.getFireBurstsThisFrame()) {
            renderer.getImpactFx().spawnAmbientFire(burst[0], burst[1], burst[2]);
        }
        renderer.getImpactFx().advance(dt * speedMultiplier);
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
        // Light pass — tick transient lights and re-assert persistent lights
        // for every emitter that lives across frames (burning wrecks, air
        // vehicle engines). All persistent ids accumulate into seenLightIds;
        // retainPersistent at the end evicts anything that disappeared this
        // tick.
        renderer.getLightAccumulator().advance(dt * speedMultiplier);
        java.util.HashSet<Long> seenLightIds = new java.util.HashSet<>();
        for (SmokingWreck w : sim.getSmokingWrecks()) {
            float age = w.totalLifetime - w.remainingLifetime;
            if (age >= EffectsService.WRECK_BURN_DURATION) continue;
            float burnRemaining = EffectsService.WRECK_BURN_DURATION - age;
            float intensity = (burnRemaining < EffectsService.WRECK_FIRE_FADE_DURATION)
                    ? burnRemaining / EffectsService.WRECK_FIRE_FADE_DURATION
                    : 1f;
            long id = ((long) w.cellX << 32) | (w.cellY & 0xffffffffL);
            seenLightIds.add(id);
            renderer.getLightAccumulator().putPersistent(id, w.cellX + 0.5f, w.cellY + 0.5f,
                    2.8f, LightKernel.WRECK_FIRE,
                    1.0f, 0.55f, 0.20f, intensity);
        }
        // Engine glow halos per live shuttle/valk. Lights track the world
        // position (no altitude offset), so cruise altitude doesn't lift the
        // halo off the ground — the shuttle stays a flying spotlight.
        World world = sim.world();
        for (Shuttle s : sim.getShuttles()) {
            if (s.mission.state == Shuttle.State.PENDING
                    || s.mission.state == Shuttle.State.GONE) continue;
            float altitudeT = world.altitudeT(s.entityId);
            EngineFxRenderer.emitLights(
                    EngineSlotResolver.resolve(s.type),
                    s.body.x, s.body.y,
                    s.body.facingDegrees,
                    AirAppearance.scaleMult(altitudeT, world.flightPhase(s.entityId)),
                    AirAppearance.visualAltitudeOffsetCells(altitudeT),
                    AirAppearance.engineIntensity(true, altitudeT),
                    renderer.getLightAccumulator(),
                    ((long) System.identityHashCode(s)) << 16,
                    seenLightIds,
                    sim.getThrusterGlow(s));
        }
        // Fighter engine halos — FlybyOverlay owns the fighter list, so it
        // pumps directly into our seen-id set.
        renderer.getFlybyOverlay().pumpEngineLights(seenLightIds);
        renderer.getLightAccumulator().retainPersistent(seenLightIds);
        // Roof alpha lerp runs on real dt (not sim-scaled) so the fog-of-war
        // fade keeps animating even when the sim is paused — matches how the
        // HUD ticks on real dt for the same reason.
        advanceRoofAlphaLerp(sim, dt);
        if (sim != null) sim.getVision().advanceFade(dt);
        driveShuttleEngineLoops(sim);
        playCombatEventSounds(sim);
        // Rebuild widgets when the sim transitions to complete so the bottom
        // action button swaps from Back to Continue.
        if (sim.isComplete() != lastSimComplete) {
            lastSimComplete = sim.isComplete();
            rebuild();
        }
    }

    @Override
    public void detach() {
        // Release the decal accumulator's FBO + color texture. Without this, an
        // attach/detach cycle leaks one FBO per battle — fine for a single
        // session, ugly across a multi-mission run.
        renderer.getDecalAccumulator().dispose();
        renderer.getLightAccumulator().dispose();

        if (!audioActive) return;
        audioActive = false;
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
        // Command-power bar (bottom-center) + click-to-target. Added after
        // WorldPicker so reverse-order input lets it claim the button click and
        // the targeting world-click before the picker turns them into a squad
        // selection; when not targeting, world clicks fall through to the picker.
        hud.addPanel(new com.dillon.starsectormarines.battle.ui.panel.CommandPowerPanel(this));
        hud.addPanel(new SquadOverviewPanel(this));
        hud.addPanel(new SquadDetailPanel(this));
        // Per-squad GOAP plan readout. Compact when nothing is selected; full
        // plan + predicate grid when WorldPicker (or the Overview rows) put a
        // squad id into Selection.
        hud.addPanel(new SquadPlanDebugPanel(this));
        // Per-phase tick wall-time profile (top-left). DevConfig-gated; informs
        // the upcoming DoD / ECS refactor by showing which tick phases are
        // actually expensive at peak unit counts.
        hud.addPanel(new TickProfileDebugPanel(this));
        // Debug toggles + actions (top-center, collapsed by default). Replaces
        // the prior DebugTogglesWidget which was attached to the screen's
        // widget root rather than the hud — moved into the hud so input +
        // render ordering match the rest of the debug panels.
        DebugTogglesPanel debugPanel = new DebugTogglesPanel(this);
        debugPanel.addToggle("Docking paths",
                () -> com.dillon.starsectormarines.ops.battleview.BattleRenderer.DEBUG_RENDER_DOCKING_PATHS,
                () -> com.dillon.starsectormarines.ops.battleview.BattleRenderer.DEBUG_RENDER_DOCKING_PATHS =
                        !com.dillon.starsectormarines.ops.battleview.BattleRenderer.DEBUG_RENDER_DOCKING_PATHS);
        debugPanel.addAction("Force reinforcement", this::forceDefenderReinforcement);
        TurretAuthorPanel turretAuthor = new TurretAuthorPanel(this);
        debugPanel.addToggle("Turret author",
                () -> turretAuthor.active,
                () -> turretAuthor.active = !turretAuthor.active);
        hud.addPanel(debugPanel);
        hud.addPanel(turretAuthor);
        // Compound-progress strip (Conquest-only — auto-hides when no
        // compounds are registered). Sibling to the world-anchored
        // CompoundMarkerRenderer instance constructed in this screen; the
        // panel handles the at-a-glance "captured / total" read.
        hud.addPanel(new CompoundProgressPanel(this));
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
     * position every frame. The shuttle itself is the {@code playingEntity} key, so three
     * shuttles landing at once stay on three distinct voices even when they share a clip id.
     *
     * <p>Volume scales by {@link AirAppearance#engineIntensity(boolean, float)} so on-ground idle reads quiet and
     * cruise reads loud; pitch sweeps {@link #ENGINE_PITCH_IDLE} → {@link #ENGINE_PITCH_CRUISE}
     * across that range plus a small per-shuttle deterministic offset (from the identity hash,
     * stable frame-to-frame) so two craft on the same clip don't phase-lock. Velocity feeds
     * OpenAL Doppler as a shuttle banks over the camera. When no shuttles are visible we skip
     * the call and the loops self-fade over Starsector's default loop hold.
     */
    private void driveShuttleEngineLoops(BattleSimulation sim) {
        World world = sim.world();
        for (Shuttle s : sim.getShuttles()) {
            if (!s.isVisible()) continue;
            float intensity = AirAppearance.engineIntensity(true, world.altitudeT(s.entityId));
            if (intensity <= 0f) continue;
            EngineVoice voice = EngineVoiceResolver.resolve(s.type.renderHullId());
            int idHash = System.identityHashCode(s);
            // Deterministic ±jitter from the hash so the offset doesn't change frame-to-frame.
            float pitchOffset = (((idHash >> 8) & 0xff) / 255f * 2f - 1f) * SHUTTLE_ENGINE_PITCH_JITTER;
            float pitch = ENGINE_PITCH_IDLE + (ENGINE_PITCH_CRUISE - ENGINE_PITCH_IDLE) * intensity + pitchOffset;
            Vector2f loc = new Vector2f(s.body.x * AUDIO_WORLD_UNITS_PER_CELL,
                                        s.body.y * AUDIO_WORLD_UNITS_PER_CELL);
            Vector2f vel = shuttleVelocity(s);
            Global.getSoundPlayer().playLoop(voice.loopSoundId, s, pitch,
                    SHUTTLE_ENGINE_VOLUME * intensity, loc, vel);
        }
    }

    /** Per-frame velocity for {@link #driveShuttleEngineLoops} Doppler — reads the AirBody directly. Returns zero on the ground / off-screen so audio stays parked. */
    private static Vector2f shuttleVelocity(Shuttle s) {
        if (s.mission.state != Shuttle.State.INCOMING && s.mission.state != Shuttle.State.DEPARTING) {
            return new Vector2f(0f, 0f);
        }
        return new Vector2f(s.body.vx * AUDIO_WORLD_UNITS_PER_CELL,
                            s.body.vy * AUDIO_WORLD_UNITS_PER_CELL);
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
     *   <li>{@code shotsThisFrame} for null-kind shots (marine / militia / alien
     *       rifle fire). The line tracer is drawn full-length the moment a
     *       shot fires, so the impact should appear immediately at the endpoint
     *       too — otherwise the puff lags ~150ms behind a visually-instant tracer.</li>
     *   <li>{@code shotsExpiredThisFrame} for turret shots (non-null kind). The
     *       projectile sprite travels visibly over its lifetime; the impact
     *       should appear when the sprite reaches the endpoint, not when the
     *       turret fires.</li>
     * </ul>
     *
     * <p>HE shells (mortar) additionally play a positional explosion clip at
     * impact. Kinetic shells stay silent — the fire SFX already covers them
     * and a second clip per shot is sonic clutter.
     */
    private void spawnImpactFx(BattleSimulation sim) {
        java.util.Random rng = java.util.concurrent.ThreadLocalRandom.current();
        Vector2f zeroVel = new Vector2f(0f, 0f);
        NavigationGrid grid = sim.getGrid();
        // Line-tracer shots spawn their impact at fire — the line covers the
        // whole travel instantly. Anything with a projectile sprite (turret,
        // rocket, SMG bullet) waits for arrival in the second pass.
        for (ShotEvent s : sim.getShotsThisFrame()) {
            // Every shooting marine / militia / alien ejects a casing where
            // they're standing (skip rockets — tube-launched, no brass).
            if (s.marineSecondary == null && s.turretKind == null) {
                ImpactDecals.spawnShellCasing(sim, rng, s.fromX, s.fromY);
            }
            // Mech chaingun particle muzzle flash — bright additive pop at
            // the shooter's cell, sized to read over the mech sprite. Only
            // the chaingun gets one; rockets are tube-launched and the
            // launch animation is already the projectile sprite leaving
            // the mount.
            if (s.mechWeapon == com.dillon.starsectormarines.battle.mech.MechWeapon.CHAINGUN) {
                renderer.getImpactFx().spawnMuzzleFlash(s.fromX, s.fromY, 0.55f, 0.08f);
            }
            // SAM-site launch backblast — kinds flagged hasLaunchBackblast
            // emit a directional cone of smoke puffs out the back of the
            // launcher as the missile leaves the tube. Computed off the
            // (from → to) firing vector so the cone always points away from
            // the target. Fires at the moment of launch, before the projectile
            // sprite even leaves the mount.
            if (s.turretKind != null && s.turretKind.hasLaunchBackblast()) {
                float fireBearing = bearingDeg(s.fromX, s.fromY, s.toX, s.toY);
                renderer.getImpactFx().spawnLaunchBackblast(s.fromX, s.fromY, fireBearing);
            }
            // Light emission — turret / mech chaingun / marine small arms /
            // generic infantry line tracer, all dispatched in one place.
            boolean projectile = hasProjectileSprite(s);
            WeaponLights.shotMuzzleFlash(renderer.getLightAccumulator(), s, projectile);
            if (projectile) continue;
            boolean isWall = isWallAt(grid, s.toX, s.toY);
            ImpactProfile profile = (s.marineWeapon != null)
                    ? s.marineWeapon.impactProfile : ImpactProfile.RIFLE;
            renderer.getImpactFx().spawnImpact(profile, s.toX, s.toY, isWall);
            WeaponLights.impactBurst(renderer.getLightAccumulator(), profile, s.toX, s.toY);
            ImpactDecals.spawnImpact(sim, rng, profile, s.toX, s.toY, isWall);
            // Line-tracer beam path — stamp the tracer color along the
            // shot line so the multiply pass doesn't darken pulse-rifle /
            // railgun / militia tracer beams. Marines use their per-weapon
            // tracerColor; other factions fall back to the same palette
            // BattleRenderer.drawTracers reads.
            Color tracerColor = (s.marineWeapon != null)
                    ? s.marineWeapon.tracerColor
                    : com.dillon.starsectormarines.ops.battleview.ShotFx.defaultTracerColor(s.shooterFaction);
            WeaponLights.laserPath(renderer.getLightAccumulator(), s.fromX, s.fromY, s.toX, s.toY, tracerColor);
        }
        for (ShotEvent s : sim.getShotsExpiredThisFrame()) {
            if (!hasProjectileSprite(s)) continue;
            boolean isWall = isWallAt(grid, s.toX, s.toY);
            ImpactProfile profile;
            if (s.turretKind != null) {
                profile = s.turretKind.impactProfile();
                renderer.getImpactFx().spawnImpact(profile, s.toX, s.toY, isWall);
                WeaponLights.impactBurst(renderer.getLightAccumulator(), profile, s.toX, s.toY);
                // Any HE-profile turret round (mortar, grenade launcher,
                // LOCUST artillery) pairs the flame plume with the explosion
                // clip — matches the mech HE branch below. Previously gated
                // on HEAVY_MORTAR only, so LOCUST salvos landed silently
                // despite spawning a full HE detonation visual.
                if (profile == ImpactProfile.HE) {
                    float pitch = 0.9f + rng.nextFloat() * 0.2f;
                    Vector2f loc = new Vector2f(
                            s.toX * AUDIO_WORLD_UNITS_PER_CELL,
                            s.toY * AUDIO_WORLD_UNITS_PER_CELL);
                    Global.getSoundPlayer().playSound(SFX_NEAR_EXPLOSION, pitch, 0.55f, loc, zeroVel);
                }
            } else if (s.marineSecondary != null) {
                profile = s.marineSecondary.impactProfile();
                renderer.getImpactFx().spawnImpact(profile, s.toX, s.toY, isWall);
                WeaponLights.impactBurst(renderer.getLightAccumulator(), profile, s.toX, s.toY);
                float pitch = 0.9f + rng.nextFloat() * 0.2f;
                Vector2f loc = new Vector2f(
                        s.toX * AUDIO_WORLD_UNITS_PER_CELL,
                        s.toY * AUDIO_WORLD_UNITS_PER_CELL);
                Global.getSoundPlayer().playSound(s.marineSecondary.impactSoundId, pitch, 0.70f, loc, zeroVel);
            } else if (s.marineWeapon != null) {
                profile = s.marineWeapon.impactProfile;
                renderer.getImpactFx().spawnImpact(profile, s.toX, s.toY, isWall);
                WeaponLights.impactBurst(renderer.getLightAccumulator(), profile, s.toX, s.toY);
            } else if (s.mechWeapon != null) {
                // Mech rounds — HE entries (SRM, LRM) also play the explosion
                // clip on arrival; chainguns are kinetic, no extra audio (the
                // burst itself is loud enough at fire time).
                profile = s.mechWeapon.impactProfile;
                renderer.getImpactFx().spawnImpact(profile, s.toX, s.toY, isWall);
                WeaponLights.impactBurst(renderer.getLightAccumulator(), profile, s.toX, s.toY);
                if (profile == ImpactProfile.HE) {
                    float pitch = 0.9f + rng.nextFloat() * 0.2f;
                    Vector2f loc = new Vector2f(
                            s.toX * AUDIO_WORLD_UNITS_PER_CELL,
                            s.toY * AUDIO_WORLD_UNITS_PER_CELL);
                    Global.getSoundPlayer().playSound(SFX_NEAR_EXPLOSION, pitch, 0.65f, loc, zeroVel);
                }
            } else {
                profile = ImpactProfile.RIFLE;
            }
            ImpactDecals.spawnImpact(sim, rng, profile, s.toX, s.toY, isWall);
        }
    }

    /** True when the shot is rendered as a traveling sprite (turret kinetic, marine rocket, SMG bullet, any mech round) rather than an instant line tracer — drives whether impact FX fire at launch or arrival. */
    private static boolean hasProjectileSprite(ShotEvent s) {
        return s.turretKind != null
                || s.marineSecondary != null
                || (s.marineWeapon != null && s.marineWeapon.projectileSpritePath != null)
                || (s.mechWeapon != null && s.mechWeapon.projectileSpritePath != null);
    }

    /** True when the endpoint cell is non-walkable (wall / vehicle / turret mount) and the impact should read as a chip on solid material rather than a kick of floor dust. */
    private static boolean isWallAt(NavigationGrid grid, float x, float y) {
        int cx = (int) Math.floor(x);
        int cy = (int) Math.floor(y);
        if (!grid.inBounds(cx, cy)) return false;
        return !grid.isWalkable(cx, cy);
    }

    private void playCombatEventSounds(BattleSimulation sim) {
        java.util.Random rng = java.util.concurrent.ThreadLocalRandom.current();
        Vector2f zeroVel = new Vector2f(0f, 0f);
        for (ShotEvent s : sim.getShotsThisFrame()) {
            float pitch = 1f + (rng.nextFloat() * 2f - 1f) * RIFLE_PITCH_JITTER;
            Vector2f loc = new Vector2f(
                    s.fromX * AUDIO_WORLD_UNITS_PER_CELL,
                    s.fromY * AUDIO_WORLD_UNITS_PER_CELL);
            // Per-weapon fire sound dispatch. All four sources (turret, marine
            // secondary, marine primary, raw rifle) play through positional
            // playSound — vanilla weapon SFX are mono, which the spatial pipeline
            // requires; distance attenuation does the volume-falloff work.
            if (s.turretKind != null) {
                Global.getSoundPlayer().playSound(s.turretKind.fireSoundId, pitch, 1.0f, loc, zeroVel);
            } else if (s.marineSecondary != null) {
                Global.getSoundPlayer().playSound(s.marineSecondary.fireSoundId, pitch, 1.0f, loc, zeroVel);
            } else if (s.marineWeapon != null) {
                Global.getSoundPlayer().playSound(s.marineWeapon.fireSoundId, pitch, 0.85f, loc, zeroVel);
            } else if (s.mechWeapon != null) {
                // Mech chassis weapons — chaingun_fire / annihilator_fire /
                // pilum_lrm_fire. All play at full volume; the chaingun burst
                // cadence is the *point*, so the brrt should dominate.
                Global.getSoundPlayer().playSound(s.mechWeapon.fireSoundId, pitch, 1.0f, loc, zeroVel);
            } else {
                Global.getSoundPlayer().playSound(SFX_RIFLE, pitch, RIFLE_VOLUME, loc, zeroVel);
            }
        }
        for (Entity u : sim.getDeathsThisFrame()) {
            if (u.faction == Faction.MARINE) {
                Vector2f loc = new Vector2f(
                        sim.world().renderX(u.entityId) * AUDIO_WORLD_UNITS_PER_CELL,
                        sim.world().renderY(u.entityId) * AUDIO_WORLD_UNITS_PER_CELL);
                Global.getSoundPlayer().playSound(SFX_VOICE_DEAD, 1f, 1f, loc, zeroVel);
                break;  // one voice per frame
            }
        }
    }

    private void onBackOrContinue() {
        if (ctx == null) return;
        BattleSimulation sim = ctx.getBattleSimulation();
        if (sim != null && sim.isComplete()) {
            // Compute + apply outcome once, then hand off to RESULTS.
            Mission mission = ctx.getSelectedMission();
            MissionOutcome outcome = MissionResolver.compute(sim, mission, ctx.getSelectedCaptain());
            MissionResolver.apply(outcome);
            ctx.setLastOutcome(outcome);
            ctx.goTo(ScreenId.RESULTS);
        } else {
            // Abandon mid-battle — no resolution, no penalty.
            ctx.goTo(ScreenId.MISSION_SELECT);
        }
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        widgets.processInput(events);
        // HUD gets first crack after widgets so a click on a squad row doesn't
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
        if (events == null || camera == null) return;
        for (InputEventAPI e : events) {
            if (e.isConsumed()) continue;
            // Mouse wheel — zoom-to-cursor when over the grid area, ignored elsewhere
            // so scrolling outside the play area doesn't fight other dialogs.
            if (e.isMouseScrollEvent()) {
                if (!camera.containsScreen(e.getX(), e.getY())) continue;
                // Wheel deltas come through as raw LWJGL values (typically ±120 per
                // notch on Windows; ±1 on some Linux builds). Normalize to ±1 notch
                // so zoom magnitude is consistent regardless of platform conventions.
                int raw = e.getEventValue();
                float notches = raw > 0 ? 1f : (raw < 0 ? -1f : 0f);
                camera.zoomAt(notches, e.getX(), e.getY());
                e.consume();
                continue;
            }
            // RMB-drag pan (no shift — shift+RMB is the debug damage gesture).
            if (e.isRMBDownEvent() && !e.isShiftDown()) {
                if (!camera.containsScreen(e.getX(), e.getY())) continue;
                panDragging = true;
                lastDragX = e.getX();
                lastDragY = e.getY();
                e.consume();
                continue;
            }
            if (e.isRMBUpEvent()) {
                panDragging = false;
                continue;
            }
            if (panDragging && e.isMouseMoveEvent()) {
                int x = e.getX();
                int y = e.getY();
                // Pan opposite to the mouse delta — dragging right pulls the world
                // right (i.e. the camera moves left over the world). panByPixels
                // already negates internally, so we pass the raw mouse delta.
                camera.panByPixels(x - lastDragX, y - lastDragY);
                lastDragX = x;
                lastDragY = y;
                e.consume();
                continue;
            }
            // Keyboard pan — WASD + arrow keys, both directions. We just track
            // the held state here; advance() integrates the pan per dt so a held
            // key doesn't depend on key-repeat firing rate.
            if (e.isKeyDownEvent() || e.isKeyUpEvent()) {
                boolean down = e.isKeyDownEvent();
                int key = e.getEventValue();
                if (key == org.lwjgl.input.Keyboard.KEY_W || key == org.lwjgl.input.Keyboard.KEY_UP)    { panKeyW = down; }
                else if (key == org.lwjgl.input.Keyboard.KEY_S || key == org.lwjgl.input.Keyboard.KEY_DOWN)  { panKeyS = down; }
                else if (key == org.lwjgl.input.Keyboard.KEY_A || key == org.lwjgl.input.Keyboard.KEY_LEFT)  { panKeyA = down; }
                else if (key == org.lwjgl.input.Keyboard.KEY_D || key == org.lwjgl.input.Keyboard.KEY_RIGHT) { panKeyD = down; }
            }
        }
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
                int vIdx = getSelection().getSelectedVehicleIdx();
                com.dillon.starsectormarines.battle.sim.BattleSimulation bsim = getSim();
                if (vIdx >= 0 && bsim != null && vIdx < bsim.getConvoyVehicles().size()) {
                    com.dillon.starsectormarines.battle.ui.debug.VehicleStateDumper.dump(
                            bsim.getConvoyVehicles().get(vIdx), bsim.getGrid());
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
            // glScissor takes FRAMEBUFFER pixels, but layout.gridX/Y/W/H are
            // in Starsector's UI-space units (which scale with the user's UI
            // scale setting and the framebuffer DPI). Feeding UI units to
            // glScissor directly clips at the wrong place — visible as the
            // top of the play area going black at zoom.
            //
            // Starsector hands us a scissor already enabled around the dialog
            // rect (see gl_state_gotchas), so we can sample it to recover the
            // UI-to-FB scale: query the inherited scissor box (= dialog rect
            // in FB px), compare against the dialog rect in UI units (from
            // position), and apply the same scale to the grid sub-rect.
            // Push SCISSOR_BIT so we restore both the prior box and the
            // enable state on pop.
            glPushAttrib(GL_SCISSOR_BIT);
            // Sample the inherited dialog scissor box once per drawable size (re-
            // sampled on resize); a per-frame glGet* here stalls async-renderer
            // bridge mods.
            int fbW = Display.getWidth(), fbH = Display.getHeight();
            if (fbW != uiSampledFbW || fbH != uiSampledFbH) {
                java.nio.IntBuffer scissorBuf = org.lwjgl.BufferUtils.createIntBuffer(16);
                org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_SCISSOR_BOX, scissorBuf);
                uiDialogFbX = scissorBuf.get(0);
                uiDialogFbY = scissorBuf.get(1);
                uiDialogFbW = scissorBuf.get(2);
                uiDialogFbH = scissorBuf.get(3);
                uiSampledFbW = fbW; uiSampledFbH = fbH;
            }
            float uiDialogW = Math.max(0.001f, position.getWidth());
            float uiDialogH = Math.max(0.001f, position.getHeight());
            float sx = uiDialogFbW / uiDialogW;
            float sy = uiDialogFbH / uiDialogH;
            int gridFbX = uiDialogFbX + Math.round((layout.gridX - position.getX()) * sx);
            int gridFbY = uiDialogFbY + Math.round((layout.gridY - position.getY()) * sy);
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

        renderSpeedMarker(alphaMult);

        // HUD sits above the world layer but below the victory/defeat banner — a
        // mid-battle squad-select shouldn't be visually competing with the
        // end-of-battle takeover. Drawn after the speed-marker so the marker's
        // tiny indicator dot never gets clipped by a panel beneath it.
        if (hud != null) hud.render(alphaMult);

        if (sim != null && sim.isComplete()) {
            renderBanner(sim.getWinner(), alphaMult);
        }

        widgets.render(alphaMult);
    }

    // ---- rendering (world-layer methods moved to BattleRenderer) -----------

    /** Starsector sprite-angle convention: 0° = +Y (north), positive clockwise. Used by spawnImpactFx. */
    private static float bearingDeg(float fromX, float fromY, float toX, float toY) {
        float dx = toX - fromX;
        float dy = toY - fromY;
        if (dx == 0f && dy == 0f) return 0f;
        return (float) Math.toDegrees(Math.atan2(dy, dx)) - 90f;
    }

    /**
     * Lerps each building's {@code currentAlpha → targetAlpha} on real dt so
     * the fade is decoupled from sim tick rate. Called from {@link #advance}.
     */
    private void advanceRoofAlphaLerp(BattleSimulation sim, float dt) {
        if (sim == null) return;
        com.dillon.starsectormarines.battle.world.model.Buildings buildings = sim.getBuildings();
        if (buildings == null || buildings.isEmpty()) return;
        float lerpAmount = Math.min(1f, dt * 3f);
        for (com.dillon.starsectormarines.battle.world.model.Building b : buildings.all()) {
            b.currentAlpha += (b.targetAlpha - b.currentAlpha) * lerpAmount;
        }
    }

    /** Amber underline under whichever speed button is currently active. */
    private void renderSpeedMarker(float alphaMult) {
        int activeIdx = -1;
        for (int i = 0; i < SPEED_OPTIONS.length; i++) {
            if (SPEED_OPTIONS[i] == speedMultiplier) { activeIdx = i; break; }
        }
        if (activeIdx < 0) return;
        float w = SPEED_BTN_W - 16f;
        float markX = speedBtnCenterX[activeIdx] - w / 2f;
        float markY = speedBtnBottomY - SPEED_MARK_H - 2f;
        fillRect(markX, markY, w, SPEED_MARK_H, ACTIVE_SPEED, alphaMult);
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
