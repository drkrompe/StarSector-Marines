package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.evacuation.SwarmBombards;
import com.dillon.starsectormarines.battle.squad.FrontageLine;
import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.DevConfig;
import com.dillon.starsectormarines.battle.audio.BattleMusicDirector;
import com.dillon.starsectormarines.battle.audio.BattleMusicFacts;
import com.dillon.starsectormarines.battle.audio.BattleRadioChatter;
import com.dillon.starsectormarines.battle.audio.CompanyOfficer;
import com.dillon.starsectormarines.battle.audio.OperationRadio;
import com.dillon.starsectormarines.battle.audio.MachineAudio;
import com.dillon.starsectormarines.battle.audio.MechFootstepAudio;
import com.dillon.starsectormarines.battle.audio.StoryNarrationPlayer;
import com.dillon.starsectormarines.battle.audio.WaylineNarration;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.warship.WarshipSpotting;
import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.ui.debug.VehicleStateDumper;
import com.dillon.starsectormarines.battle.ui.debug.CommanderTraceDumper;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.combat.FireTrigger;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.combat.PointFireAim;
import com.dillon.starsectormarines.battle.control.DirectControlAbility;
import com.dillon.starsectormarines.battle.vision.BuildingVisibilityPass;
import com.dillon.starsectormarines.battle.air.AirAppearance;
import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
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
import com.dillon.starsectormarines.battle.ui.panel.PlacementPause;
import com.dillon.starsectormarines.battle.ui.panel.QueuedDropMarkerPanel;
import com.dillon.starsectormarines.battle.ui.panel.ShipOrderTargetingPanel;
import com.dillon.starsectormarines.battle.ui.panel.DebugHudSwitchPanel;
import com.dillon.starsectormarines.battle.ui.panel.DebugTogglesPanel;
import com.dillon.starsectormarines.battle.ui.panel.DebugHudLayout;
import com.dillon.starsectormarines.battle.ui.panel.DirectControlPanel;
import com.dillon.starsectormarines.battle.ui.panel.TurretAuthorPanel;
import com.dillon.starsectormarines.battle.ui.panel.SquadPlanDebugPanel;
import com.dillon.starsectormarines.battle.ui.panel.WarshipDebugPanel;
import com.dillon.starsectormarines.battle.ui.panel.OrderIntentCursorPanel;
import com.dillon.starsectormarines.battle.ui.panel.SquadOrderTargetingPanel;
import com.dillon.starsectormarines.battle.ui.panel.TickProfileDebugPanel;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.highlight.CommanderInfluenceOverlayPublisher;
import com.dillon.starsectormarines.battle.ui.highlight.ConquestCommanderOverlayPublisher;
import com.dillon.starsectormarines.battle.ui.highlight.AssaultCommanderOverlayPublisher;
import com.dillon.starsectormarines.battle.ui.highlight.SabotageCommanderOverlayPublisher;
import com.dillon.starsectormarines.battle.ui.highlight.RaidCommanderOverlayPublisher;
import com.dillon.starsectormarines.battle.ui.highlight.ExtractionCommanderOverlayPublisher;
import com.dillon.starsectormarines.battle.ui.highlight.FightingPositionHighlightPublisher;
import com.dillon.starsectormarines.battle.ui.highlight.SquadMoveOrderHighlightPublisher;
import com.dillon.starsectormarines.battle.ui.highlight.VehicleMoveOrderHighlightPublisher;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.ui.picking.WorldPicker;
import com.dillon.starsectormarines.battle.ui.picking.SelectionOrders;
import com.dillon.starsectormarines.battle.mech.MechFamilyDebugSpawner;
import com.dillon.starsectormarines.battle.combat.fx.ImpactDecals;
import com.dillon.starsectormarines.battle.weapon.fx.WeaponFxDef;
import com.dillon.starsectormarines.battle.weapon.fx.WeaponFxRuntime;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.vision.FogOfWarService;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.CameraControls;
import com.dillon.starsectormarines.ops.battleview.BattleRenderer;
import com.dillon.starsectormarines.ops.detachment.CampaignCommandPowerResources;
import com.dillon.starsectormarines.ops.battleview.ArmoryRangeTrial;
import com.dillon.starsectormarines.ops.battleview.BattleRenderHostProfile;
import com.dillon.starsectormarines.ops.battleview.RenderLayer;
import com.dillon.starsectormarines.ops.battleview.RenderContext;
import com.dillon.starsectormarines.ops.battleview.BattleShotAudio;
import com.dillon.starsectormarines.battle.combat.fx.OrdnanceRelease;
import com.dillon.starsectormarines.ops.battleview.HeldFireLoops;
import com.dillon.starsectormarines.ops.battleview.OrdnanceAudio;
import com.dillon.starsectormarines.ops.battleview.WarshipAudio;
import com.dillon.starsectormarines.ops.battleview.BoreAudio;
import com.dillon.starsectormarines.ops.battleview.CreepAudio;
import com.dillon.starsectormarines.ops.battleview.SwarmAudio;
import com.dillon.starsectormarines.ops.battleview.TenderAudio;
import com.dillon.starsectormarines.ops.battleview.OrdnanceFxRuntime;
import com.dillon.starsectormarines.ops.battleview.OrdnanceTraceFxService;
import com.dillon.starsectormarines.ops.battleview.BattleSprites;
import com.dillon.starsectormarines.ops.battleview.TextureLoadQueue;
import com.dillon.starsectormarines.ops.battleview.BattleTerrainSheets;
import com.dillon.starsectormarines.ops.battleview.BattleTextureSet;
import com.dillon.starsectormarines.render2d.texture.TextureLease;
import com.dillon.starsectormarines.render2d.texture.TextureLedger;
import com.dillon.starsectormarines.ops.battleview.GroundParallaxPipeline;
import com.dillon.starsectormarines.ops.battleview.SunLight;
import com.dillon.starsectormarines.ops.battleview.ShotFx;
import com.dillon.starsectormarines.ops.battleview.ShotImpactAudio;
import com.dillon.starsectormarines.ops.loot.LootGenerator;
import com.dillon.starsectormarines.ui.Fonts;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import org.apache.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_LINE_LOOP;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_TRIANGLE_FAN;
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
import static org.lwjgl.opengl.GL11.glLineWidth;
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
 * <p>Confirmed Retreat completes an active battle as defeat and settles through
 * the same Results path as Continue after objective completion.
 */
public class BattleScreen implements Screen, BattleUiContext {

    private static final Logger LOG = Global.getLogger(BattleScreen.class);
    /** How far around a dragged-off friend the player still sees, in cells: enough to show the Runner and the ground ahead of it. */
    private static final int HELD_BODY_SIGHT = 5;

    private static final Color BANNER_BG      = new Color(0x10, 0x14, 0x1E);
    private static final Color VICTORY_COLOR  = new Color(0x80, 0xE0, 0x80);
    private static final Color DEFEAT_COLOR   = new Color(0xE0, 0x60, 0x60);
    private static final Color EXTRACTION_COLOR = new Color(0x8F, 0xD8, 0xC4);

    private static final String SFX_VOICE_DEAD = "marines_voice_dead";
    /** A suit's integral system raising a screen. Mono, so it plays positionally. */
    private static final String SFX_SYSTEM_SCREEN_UP = "marines_system_screen_up";
    /** A suit's integral system running without raising a screen. */
    private static final String SFX_SYSTEM_ENGAGED = "marines_system_engaged";
    private static final String SFX_DISTANT_BOOM = "marines_explosion_muffled";
    private static final String SFX_NEAR_EXPLOSION = "marines_explosion";
    /** Crossfade duration (seconds, whole numbers required) for leaving the battle music. */
    private static final int MUSIC_FADE_SECS  = 2;
    /** Pitch lerp endpoints for the shuttle engine loop: idle on the ground → full at cruise. */
    private static final float ENGINE_PITCH_IDLE   = 0.7f;
    private static final float ENGINE_PITCH_CRUISE = 1.0f;
    /** Cells → OpenAL world units, for positional SFX. */
    private static final float AUDIO_WORLD_UNITS_PER_CELL = BattleShotAudio.WORLD_UNITS_PER_CELL;
    /** Radius, in cells, of the burst drawn where a point-defence emplacement stopped a warhead. Presentation only; nothing is damaged. */
    public static final float INTERCEPT_BURST_CELLS = 0.9f;
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
    private TickProfileDebugPanel tickProfilePanel;
    private DebugTogglesPanel debugPanel;
    private BattleCommsPanel battleComms;
    /** MLX-authored player-facing command chrome: time controls and capture state. */
    private BattleHudOverlay retainedOverlay;
    private BattleDirectControlOverlay directControlOverlay;
    private BattleManualObjectiveOverlay manualObjectives;
    private final BattleDirectControlInput directControlInput = new BattleDirectControlInput();
    private BattleSimulation directControlSimulation;
    private boolean directControlHudActive;
    private final BattleActionCamera actionCamera = new BattleActionCamera();
    private BattleSimulation attachedSimulation;
    private WorldPicker worldPicker;
    private TurretAuthorPanel turretAuthor;
    private boolean authoringChromeActive;
    /** MLX-authored 3x4 selected-squad roster with hover loadout detail. */
    private BattleSquadOverlay retainedSquadOverlay;
    /** MLX-authored doctrine control for one exactly selected player mech. */
    private BattleMechOverlay retainedMechOverlay;
    /** MLX-authored compact command-power deck at bottom-center. */
    private BattlePowerOverlay retainedPowerOverlay;
    /** MLX-authored read-only air support pool plate under the top-right command rail. */
    private BattleAirOverlay retainedAirOverlay;
    private BattleOrderOverlay retainedOrderOverlay;
    /** MLX-authored, confirmation-gated battle exit at bottom-left. */
    private BattleRetreatOverlay retainedRetreatOverlay;
    /** World click/reticle half of the power flow; card selection lives in MLX. */
    private CommandPowerTargetingPanel commandPowerTargeting;
    private ShipOrderTargetingPanel shipOrderTargeting;
    private BattleShipCommandController shipCommandController;
    private BattleShipCommandOverlay shipCommandOverlay;
    /** World placement for the contextual tactical-order bar. */
    private SquadOrderTargetingPanel squadOrderTargeting;
    /** Says what a right-click would do at the cell under the pointer. */
    private OrderIntentCursorPanel orderIntentCursor;
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
    /** The lines a right drag in progress would give, or null when no drag is under way. */
    private List<HighlightOverlay.LineMark> linePreview;
    /** The drag's cells the preview was last worked out for; a move inside the same cells keeps it. */
    private long linePreviewKey = Long.MIN_VALUE;
    /** The destination and heading a right drag in progress would give a vehicle or mech, or null. */
    private HighlightOverlay.FacingMark facingPreview;
    private float speedMultiplier = 1f;
    /** The speed Space returns to: the last running speed, so a pause at 2x resumes at 2x. */
    private float resumeSpeed = 1f;
    /** How long a fresh battle's loading screen stays up at least, so a fast load does not flicker. */
    private static final float LOADING_MINIMUM_SECONDS = 0.5f;
    /** GL upload time a loading frame may spend before the loading screen draws again. */
    private static final long LOADING_FRAME_BUDGET_NANOS = 12_000_000L;
    /** A load step longer than this stops the loading screen's spinner long enough to notice. */
    private static final long SLOW_LOAD_STEP_NANOS = 150_000_000L;
    /** The battle's texture loads, run a frame's budget at a time; null before the first attach. */
    private TextureLoadQueue loadQueue;
    private BattleLoadingOverlay loadingOverlay;
    private float loadingShownSeconds;
    /** The simulation whose textures the last attach loaded, to tell a fresh battle from a re-attach. */
    private BattleSimulation loadedSimulation;
    /**
     * The textures this battle loaded ({@code battle-textures.md}): opened when a battle attaches,
     * released when it detaches, at which point {@link #sprites} and {@link #renderer} are dead
     * and the next attach makes new ones. Null while no battle holds any.
     */
    private TextureLease battleLease;
    /** Owns all loaded sprite sheets, frame data, and ensure/load methods; one per battle lease. */
    private BattleSprites sprites = new BattleSprites();
    /** World-layer render pipeline — owns tile batches, FX systems, and all render/draw methods. */
    private BattleRenderer renderer = new BattleRenderer(sprites);
    /** Weapon and impact cues for rounds an aircraft delivers onto the ground. */
    private final OrdnanceAudio ordnanceAudio = new OrdnanceAudio();
    private final HeldFireLoops heldFireLoops = new HeldFireLoops();
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
     * Whether the developer HUD draws. Off by default; the DEBUG button flips it. Static so one
     * choice carries across battles in a session, like the renderer's other debug switches.
     */
    @DebugOnly
    private static boolean debugHudVisible;
    private boolean debugMarineFriendlyInfluence;
    private boolean debugMarineHostileInfluence;
    private boolean debugDefenderFriendlyInfluence;
    private boolean debugDefenderHostileInfluence;
    /** The one faction-local Conquest command picture currently projected into the world. */
    private Faction debugConquestPerspective;
    /**
     * True while this screen owns the audio side effects (custom music + ambient loops
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
    /**
     * Picks quiet, alert, combat or aftermath music from the battle's facts. Its per-state shuffles
     * are retained across battles to avoid repeats. Quiet and aftermath have no tracks yet: silence
     * under the ambient loops.
     */
    private final BattleMusicDirector battleMusic = BattleMusicDirector.shipped(audioRng);
    /** Shared squad-event policy for sparse positional marine radio calls. */
    private final BattleRadioChatter radioChatter = new BattleRadioChatter();
    private final MechFootstepAudio mechFootsteps = new MechFootstepAudio();
    private final MachineAudio machineAudio = new MachineAudio();
    private final StorySubtitleOverlay storySubtitles = new StorySubtitleOverlay();
    private StoryNarrationPlayer storyNarration;
    private final CompanyOfficer companyOfficer = new CompanyOfficer();
    private final OperationRadio operationRadio = new OperationRadio();

    private final ArmoryRangeTrial rangeTrial;
    private final MarineOpsContext rangeContext;
    private boolean rangeFinished;
    private boolean rangeEntered;

    public BattleScreen() {
        rangeTrial = null;
        rangeContext = null;
    }

    /** Uses battle input, rendering and weapon feedback without a campaign operation. */
    BattleScreen(ArmoryRangeTrial trial) {
        rangeTrial = trial;
        rangeContext = new MarineOpsContext(null);
        rangeContext.setBattleSimulation(trial.simulation());
    }

    boolean rangeFinished() { return rangeFinished; }

    @Override
    public boolean releasesCompanyDeckTextures() {
        return true;
    }

    @Override
    public void attach(PositionAPI position, MarineOpsContext ctx, Runnable dismissDialog) {
        if (rangeContext != null) ctx = rangeContext;
        BattleSimulation nextSimulation = ctx != null ? ctx.getBattleSimulation() : null;
        if (attachedSimulation != nextSimulation) {
            exitDirectControl();
            speedMultiplier = 1f;
            resumeSpeed = 1f;
            attachedSimulation = nextSimulation;
            if (storyNarration != null) storyNarration.stop();
            storySubtitles.detach();
            radioChatter.reset();
            companyOfficer.reset();
            operationRadio.reset();
            mechFootsteps.reset();
            machineAudio.reset();
            storyNarration = new StoryNarrationPlayer(WaylineNarration.builtins(),
                    WaylineNarration.narrationMissionId(ctx != null ? ctx.getSelectedMission() : null,
                            nextSimulation), line -> {
                        companyOfficer.stop();
                        operationRadio.stop();
                        radioChatter.reserveVoice(line.durationSeconds());
                    });
        }
        boolean freshBattle = loadedSimulation != nextSimulation;
        loadedSimulation = nextSimulation;
        if (freshBattle || battleLease == null) openBattleTextures();
        this.position = position;
        this.ctx = ctx;
        configureMechLiveries();
        beginLoading(freshBattle);
        storySubtitles.attach(position);
        if (rangeTrial == null) startBattleAudio();
        rebuild();
        if (rangeTrial != null && !rangeEntered) {
            selection.selectUnit(Selection.NONE, rangeTrial.marine());
            toggleDirectControl();
            rangeEntered = true;
            rangeFinished = !directControlInput.active();
        }
    }

    /**
     * Queues the battle's texture loads behind the loading screen ({@code battle-textures.md}).
     * Every step is idempotent, so a re-attach of a battle whose textures are resident runs
     * through the queue in a frame; only a fresh battle holds the screen for its minimum time.
     */
    private void beginLoading(boolean freshBattle) {
        // Only what this battle places, spawns and can bring in.
        BattleSimulation sim = ctx != null ? ctx.getBattleSimulation() : null;
        BattleTextureSet set = sim != null ? BattleTextureSet.of(sim) : null;
        Set<String> terrain = sim != null ? BattleTerrainSheets.of(sim) : null;
        List<TextureLoadQueue.Step> steps = new ArrayList<>(sprites.battleLoadSteps(set, terrain, Strings::get));
        steps.add(new TextureLoadQueue.Step(Strings.get("battleLoadingAtlases"), () -> {
            renderer.buildTileBatches();
            renderer.onAttach();
        }));
        loadQueue = new TextureLoadQueue(steps);
        if (DevConfig.DEV_MODE) {
            loadQueue.onSlowStep(SLOW_LOAD_STEP_NANOS, (step, nanos) -> LOG.info("battle textures: a "
                    + step.stage() + " step held the GL thread " + nanos / 1_000_000L
                    + " ms, freezing the loading spinner; split it"));
        }
        loadingShownSeconds = freshBattle ? 0f : LOADING_MINIMUM_SECONDS;
        if (loadingOverlay == null) loadingOverlay = new BattleLoadingOverlay();
        Mission mission = ctx != null ? ctx.getSelectedMission() : null;
        loadingOverlay.attach(position, mission != null ? mission.name : null,
                mission != null ? mission.targetPlanetName : null);
    }

    /**
     * A new lease, and a sprite set and renderer that load under it. Whatever a previous battle
     * still held is released first, so two battles' textures are never resident together.
     */
    private void openBattleTextures() {
        releaseBattleTextures();
        battleLease = TextureLedger.shared().lease("battle");
        sprites = new BattleSprites(battleLease);
        renderer = new BattleRenderer(sprites);
        if (DevConfig.DEV_MODE) {
            LOG.info("battle textures: " + megabytes(TextureLedger.shared().residentBytes())
                    + " MB of mod textures resident before the battle loads");
        }
    }

    /**
     * Gives the battle's textures back. The ledger unloads, and deletes on the card, every one no
     * other screen holds; the sprites that referred to them go with the sprite set.
     */
    private void releaseBattleTextures() {
        if (battleLease == null) return;
        TextureLedger.Release release = battleLease.release();
        battleLease = null;
        if (DevConfig.DEV_MODE) {
            LOG.info("battle textures: released " + release.unloaded() + " textures, "
                    + megabytes(release.bytesFreed()) + " MB freed (" + release.kept()
                    + " kept, still held by other screens or the game's own); "
                    + megabytes(TextureLedger.shared().residentBytes()) + " MB of mod textures resident after");
        }
    }

    private static long megabytes(long bytes) {
        return Math.round(bytes / (1024.0 * 1024.0));
    }

    /** Whether the battle is still behind its loading screen. */
    private boolean loading() {
        return loadQueue != null && (!loadQueue.done() || loadingShownSeconds < LOADING_MINIMUM_SECONDS);
    }

    /** Runs a frame's worth of the load queue and keeps the loading screen current. */
    private void advanceLoading(float realDt) {
        boolean wasDone = loadQueue.done();
        loadQueue.advance(LOADING_FRAME_BUDGET_NANOS);
        if (!wasDone && loadQueue.done() && DevConfig.DEV_MODE && battleLease != null) {
            LOG.info("battle textures: loaded; the battle holds " + megabytes(battleLease.bytesHeld())
                    + " MB, " + megabytes(TextureLedger.shared().residentBytes())
                    + " MB of mod textures resident in all");
        }
        loadingShownSeconds += realDt;
        loadingOverlay.update(realDt, loadQueue);
    }

    /**
     * Campaign identity enters the renderer here and nowhere in the battle
     * simulation. The employer paints marine support; the effective target
     * faction (including a debug comparison override) paints defenders.
     */
    private void configureMechLiveries() {
        Client employer = ctx != null ? ctx.getSelectedClient() : null;
        Mission mission = ctx != null ? ctx.getSelectedMission() : null;
        String marineFactionId = employer != null ? employer.factionId : null;
        String defenderFactionId = mission == null ? null
                : mission.defenderFactionOverride != null
                        ? mission.defenderFactionOverride
                        : mission.targetFactionId;
        sprites.configureMechLiveries(marineFactionId, defenderFactionId);
    }

    /**
     * Suspend the campaign music player and crossfade into the music director's opening state.
     * Guarded so re-entry from a dialog resize (attach() is documented as idempotent)
     * doesn't restart the music mid-battle. Ambient loops are driven from
     * {@link #advance(float)}, since {@code playUILoop} must be called every frame.
     */
    private void startBattleAudio() {
        if (audioActive) return;
        audioActive = true;
        Global.getSoundPlayer().setSuspendDefaultMusicPlayback(true);
        BattleSimulation sim = ctx != null ? ctx.getBattleSimulation() : null;
        playMusic(battleMusic.start(sim != null ? BattleMusicFacts.of(sim)
                : new BattleMusicDirector.Facts(null, false, false)));
        int gridW = sim != null ? sim.getGrid().getWidth()  : BattleSetup.GRID_W;
        int gridH = sim != null ? sim.getGrid().getHeight() : BattleSetup.GRID_H;
        pickAmbient(gridW, gridH);
        distantBoomTimer = nextDistantBoomGap();
        radioChatter.reset();
    }

    /** Plays the director's cue, if any, and logs a change of music state with its reason. */
    private void playMusic(BattleMusicDirector.Cue cue) {
        if (cue == null) return;
        if (cue.stateChange()) {
            LOG.info("Battle music: " + (cue.from() == null ? "opens in " + cue.to()
                    : cue.from() + " -> " + cue.to()) + " (" + cue.reason() + ")"
                    + (cue.play() ? ", playing " + (cue.track() != null ? cue.track() : "silence") : ""));
        }
        if (!cue.play()) return;
        if (cue.track() == null) Global.getSoundPlayer().playCustomMusic(cue.fadeOut(), cue.fadeIn(), null);
        else Global.getSoundPlayer().playCustomMusic(cue.fadeOut(), cue.fadeIn(), cue.track(), false);
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
        boolean openingCamera = camera == null || camera.worldCellsW() != gridW || camera.worldCellsH() != gridH;
        if (openingCamera) camera = new BattleCamera(gridW, gridH);
        // A small indoor range must frame both shooter and targets at entry.
        float cellSize = rangeTrial == null ? layout.cellSize
                : Math.min(layout.gridW / gridW, (layout.gridH - 180f) / gridH);
        camera.setViewport(layout.gridX, layout.gridY, layout.gridW, layout.gridH, Math.max(1f, cellSize));
        cameraControls.edgeScroll(position.getX(), position.getY(), position.getWidth(), position.getHeight());
        if (openingCamera && sim != null && sim.companyStartsOnGround())
            camera.centerOn(sim.companyGroundStartX(), sim.companyGroundStartY());
        ensureHud();
        ensureRetainedOverlay(sim);
    }

    @Override
    public void advance(float dt) {
        lastAdvanceDt = dt;
        if (loading()) {
            advanceLoading(dt);
            return;
        }
        syncDirectControl();
        if (rangeFinished) return;
        // HUD ticks on real dt (not sim-scaled) so panel snapshots and hover
        // state still update when the sim is paused. Panels' update() just
        // refreshes their cached views over the sim — cheap even at every frame.
        if (hud != null) hud.update(dt);
        if (retainedOverlay != null) {
            retainedOverlay.update(dt, ctx != null ? ctx.getBattleSimulation() : null,
                    speedMultiplier);
        }
        if (retainedAirOverlay != null && retainedOverlay != null) {
            retainedAirOverlay.update(dt, ctx != null ? ctx.getBattleSimulation() : null, position,
                    retainedOverlay.rightRailBottom());
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
            retainedPowerOverlay.suppressed(selection.hasWarshipSelection());
            retainedPowerOverlay.update(dt,
                    ctx != null ? ctx.getBattleSimulation() : null);
        }
        if (retainedOrderOverlay != null) {
            retainedOrderOverlay.update(dt, getSim(),
                    retainedPowerOverlay == null ? 0f : retainedPowerOverlay.occupiedHeight());
        }
        if (shipCommandOverlay != null && !directControlInput.active()) {
            shipCommandOverlay.heroBottomInset(shipHeroBottomInset());
            shipCommandOverlay.update(dt);
        }
        if (retainedRetreatOverlay != null) {
            BattleSimulation current = ctx != null ? ctx.getBattleSimulation() : null;
            retainedRetreatOverlay.update(dt, exitPhase(current), retryOffer());
        }
        // Park the OpenAL listener at the camera focus every frame so positional SFX (gunfire,
        // explosions, ambient loops, death VO) pan + attenuate around what the player is looking
        // at. setListenerPosOverrideOneFrame is a one-frame override, so it has to be re-armed
        // each tick — same pattern as playUILoop. Set here rather than inside any
        // one FX pass, so the listener is still correct while the sim is paused.
        if (camera != null) {
            Global.getSoundPlayer().setListenerPosOverrideOneFrame(new Vector2f(
                    camera.panCellX() * AUDIO_WORLD_UNITS_PER_CELL,
                    camera.panCellY() * AUDIO_WORLD_UNITS_PER_CELL));
        }
        // On real dt, not sim-time, so the player can still look around the map
        // while the simulation is paused.
        if (!directControlInput.active() && (turretAuthor == null || !turretAuthor.active)) cameraControls.advance(dt, camera);
        // playUILoop is documented as "must be called every frame or the loop will fade out" —
        // re-arming it every advance is how Starsector expects loops to be driven. When this
        // screen stops being current, advance() stops firing and all loops fade automatically.
        if (audioActive) driveAmbientBackground(dt);
        BattleSimulation sim = ctx != null ? ctx.getBattleSimulation() : null;
        if (sim == null) return;
        // Rebuild ephemeral vision sources (every air craft over the battle)
        // each frame so the fog bitmap always reflects the latest positions.
        // Cleared + re-pushed every frame; VisionService only processes them
        // on vision-tick frames (every 3rd sim tick).
        FogOfWarService vis = sim.getFogOfWar();
        vis.clearEphemeralSources();
        World airWorld = sim.world();
        for (long id : sim.getAirEntityIds()) {
            ShuttleMission mission = airWorld.mission(id);
            if (mission == null || !mission.isOverTheBattle()) continue;
            if (!sim.getVisionState().isContributor(airWorld.airFaction(id))) continue;
            AirBody body = airWorld.kinematics(id);
            vis.addEphemeralSource((int) Math.floor(body.x), (int) Math.floor(body.y), 50, 3.5f);
        }
        // Wayline's earlier convoy reports only its immediate surroundings while its cab survives.
        if (sim.getThunderRun() != null) {
            long hauler = sim.getThunderRun().advanceHauler();
            if (sim.convoy().isTargetable(hauler)) {
                var body = sim.convoy().body(hauler);
                vis.addEphemeralSource((int) Math.floor(body.x), (int) Math.floor(body.y), 24, 0f);
            }
        }
        // Player recon-ping reveals use the same ephemeral-source seam as shuttles.
        // The sim owns the ping list + time-to-live; we just project the live
        // ones into the fog each frame (airLosRadius 0 = walls block, so the
        // reveal respects line of sight via the existing shadowcast).
        for (com.dillon.starsectormarines.battle.power.CommandPowerService.ActivePing p
                : sim.getCommandPowerService().getActivePings()) {
            vis.addEphemeralSource(p.cellX, p.cellY, p.radius, 0f);
        }
        // A Bombard's launch gives it away as a muzzle flash does, for a few seconds.
        for (SwarmBombards.Reveal reveal : sim.swarmBombards().launchReveals()) {
            vis.addEphemeralSource(reveal.cellX(), reveal.cellY(), SwarmBombards.LAUNCH_REVEAL_RANGE, 0f);
        }
        // Anyone dragged off alive by a Runner (a marine, an ally, a colonist) keeps a small bubble of
        // sight around them, so the player watches them hauled to the bore rather than vanishing into
        // the fog with their carrier. Colonists see nothing of their own, so without it they just disappear.
        for (long body : sim.swarmHarvest().loads().values()) {
            if (!airWorld.isAlive(body) || sim.identity().faction(body) == Faction.DEFENDER) continue;
            vis.addEphemeralSource(airWorld.cellX(body), airWorld.cellY(body), HELD_BODY_SIGHT, 0f);
        }
        // A friendly warship is a spotter: its hull, the ground it is ordered onto and
        // where its rounds fall are observed, so the player can see what it does.
        for (WarshipSpotting.Source s : WarshipSpotting.sources(sim)) {
            vis.addEphemeralSource(s.cellX(), s.cellY(), s.range(), s.wallClearance());
        }
        // Always tick — dt=0 makes the sim a no-op but still clears the per-frame event lists,
        // so a paused caller doesn't keep replaying the previous frame's shot/death sounds.
        // Handback during this advance can restore another playback rate. This
        // frame's presentation must keep the rate its simulation actually used.
        float frameSimDt = dt * speedMultiplier;
        submitDirectControlIntent();
        sim.advance(BattleSimulation.frameBudget(frameSimDt));
        if (audioActive) playMusic(battleMusic.update(frameSimDt, dt, BattleMusicFacts.of(sim),
                Global.getSoundPlayer().getCurrentMusicId()));
        syncDirectControl();
        followControlledUnit(dt);
        submitDirectControlIntent();
        // Wall-collapse dust. Queued by whatever brought the wall down and
        // drained once here, so a collapse looks the same however it happened.
        for (float[] dust : sim.getWallDustsThisFrame()) {
            renderer.getImpactFx().spawnWallCollapse(dust[0], dust[1], dust.length > 2 ? dust[2] : 1f);
        }
        // Impact FX: spawn at the moment the shot's visual reaches its endpoint
        // (instant for marine line tracers, on lifetime expiry for projectile
        // sprites), then advance particles on the same scaled clock.
        renderer.getBeamFx().advance(frameSimDt);
        spawnImpactFx(sim);
        for (float[] impact : sim.getHeavyImpactsThisFrame()) {
            Color tint = impact.length >= 6
                    ? new Color(impact[3], impact[4], impact[5]) : null;
            renderer.getImpactFx().spawnHeavyImpact(impact[0], impact[1], impact[2], tint);
            renderer.getGroundLights().spawnHeavyImpact(impact[0], impact[1], impact[2], tint);
            Vector2f loc = new Vector2f(
                    impact[0] * AUDIO_WORLD_UNITS_PER_CELL,
                    impact[1] * AUDIO_WORLD_UNITS_PER_CELL);
            Global.getSoundPlayer().playSound(SFX_NEAR_EXPLOSION,
                    0.72f, 1.0f, loc, new Vector2f(0f, 0f));
        }
        for (var blast : sim.getRollingBlastsThisFrame()) {
            renderer.getImpactFx().spawnRollingBlast(blast);
            renderer.getGroundLights().spawnHeavyImpact(blast.x(), blast.y(), blast.radius(), null);
        }
        // Drain wreck smoke puffs the sim queued this tick — each entry is
        // {x, y, radiusCells}. Same scaled clock means wrecks stop smoking
        // when the sim pauses.
        for (float[] puff : sim.getSmokePuffsThisFrame()) {
            renderer.getImpactFx().spawnAmbientSmoke(puff[0], puff[1], puff[2]);
        }
        for (float[] dust : sim.getGroundDustThisFrame()) {
            renderer.getImpactFx().spawnGroundDust(dust[0], dust[1], dust[2], dust[3], dust[4], (int) dust[5]);
        }
        for (float[] s : sim.getClawStrikesThisFrame()) {
            renderer.getImpactFx().spawnClawStrike(s[0], s[1], s[2], s[3], s[4]);
        }
        for (float[] h : sim.getSwarmHitsThisFrame()) renderer.getImpactFx().spawnSwarmHit(h);
        for (float[] burst : sim.getFireBurstsThisFrame()) {
            renderer.getImpactFx().spawnAmbientFire(burst[0], burst[1], burst[2]);
            renderer.getGroundLights().spawnFire(burst[0], burst[1], burst[2]);
        }
        renderer.getTurretFx().advance(sim, renderer.getImpactFx(), frameSimDt);
        driveOrdnanceFx(sim, frameSimDt);
        renderer.getProjectileTrailFx().tick(sim.getActiveShots(), renderer.getImpactFx());
        renderer.getImpactFx().advance(frameSimDt);
        renderer.getGroundLights().advance(frameSimDt);
        renderer.getGroundLights().syncBoltLights(sim.getActiveShots());
        renderer.getGroundLights().syncFireLights(sim.fireFieldIfPresent());
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
        selection.retainSquads(id -> {
            Squad squad = sim.getSquad(id);
            return squad != null && squad.aliveMembers > 0;
        });
        selection.retainVehicles(id -> sim.convoy().mission(id) != null);
        Squad primarySquad = sim.getSquad(selection.getSelectedSquadId());
        long primaryMember = selection.getSelectedUnitEntityId();
        if (primarySquad != null && (primaryMember != 0L && sim.resolveUnit(primaryMember) == 0L
                || primaryMember == 0L && primarySquad.isMechSquad())) {
            long replacement = 0L;
            for (int i = 0; i < sim.squadMemberCount(primarySquad.id); i++) {
                replacement = sim.resolveUnit(sim.squadMemberAt(primarySquad.id, i));
                if (replacement != 0L) break;
            }
            selection.selectGroup(selection.getSelectedSquadIds(), selection.getSelectedVehicleIds(),
                    primarySquad.id, replacement, 0L, false);
        }
        if (worldPicker != null) {
            worldPicker.updateHover(directControlInput.pointerX(), directControlInput.pointerY(),
                    directControlInput.pointerKnown()
                            && !battleChromeBlocksWorldPointer(directControlInput.pointerX(),
                            directControlInput.pointerY()));
        }
        SquadMoveOrderHighlightPublisher.publish(selection, sim, highlights);
        SquadMoveOrderHighlightPublisher.publishLinePreview(linePreview, highlights);
        highlights.putFacings(HighlightOverlay.SRC_FACING_PREVIEW,
                facingPreview == null ? null : List.of(facingPreview));
        FightingPositionHighlightPublisher.publish(selection, sim, highlights,
                camera != null ? camera.visibleCells() : null);
        VehicleMoveOrderHighlightPublisher.publish(selection, sim, highlights);
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
        playCombatEventSounds(sim, dt);
        for (MechFootstepAudio.Cue cue : mechFootsteps.advance(
                sim, camera.panCellX(), camera.panCellY())) {
            Global.getSoundPlayer().playSound(cue.soundId(), cue.pitch(), cue.volume(),
                    new Vector2f(cue.cellX() * AUDIO_WORLD_UNITS_PER_CELL,
                            cue.cellY() * AUDIO_WORLD_UNITS_PER_CELL), new Vector2f());
        }
        playMachinery(sim);
        if (rangeTrial == null) advanceVoices(sim, dt);
    }

    @Override
    public void detach() {
        companyOfficer.stop();
        operationRadio.stop();
        if (storyNarration != null) storyNarration.stop();
        storySubtitles.detach();
        exitDirectControl();
        cameraControls.release();
        if (worldPicker != null) worldPicker.cancel();
        if (directControlOverlay != null) directControlOverlay.detach();
        if (shipOrderTargeting != null) shipOrderTargeting.cancel();
        if (shipCommandOverlay != null) shipCommandOverlay.detach();
        if (manualObjectives != null) manualObjectives.detach();
        // Release the decal accumulator's FBO + color texture. Without this, an
        // attach/detach cycle leaks one FBO per battle — fine for a single
        // session, ugly across a multi-mission run.
        renderer.getDecalAccumulator().dispose();
        // Same leak concern as the decal accumulator, for the S2/S3 ground FBO set.
        renderer.getGroundParallax().dispose();
        // The resident ground's vertex buffers belong to this battle's map; the
        // next battle is a different grid and bakes its own.
        renderer.getGroundMesh().dispose();
        // The resident roofs belong to this battle's buildings.
        renderer.getRoofMesh().dispose();
        // The resident fog texture is this battle's grid too.
        renderer.getFogField().dispose();
        renderer.disposeLighting();
        // The composited ground atlas holds this session's sheets; a reattach
        // reloads them and composites again.
        renderer.getGroundAtlas().dispose();
        // And the composited unit atlas, for the same reason.
        renderer.getUnitAtlas().dispose();
        // And this battle's props, which were cut from this battle's placements.
        renderer.disposeDecorationAtlas();
        // And every texture the battle loaded that no other screen holds. A re-attach opens a
        // new lease and loads again behind the loading screen.
        releaseBattleTextures();
        loadQueue = null;
        renderer.getGroundLights().clear();
        if (retainedOverlay != null) retainedOverlay.detach();
        if (retainedSquadOverlay != null) retainedSquadOverlay.detach();
        if (retainedMechOverlay != null) retainedMechOverlay.detach();
        if (retainedPowerOverlay != null) retainedPowerOverlay.detach();
        if (retainedOrderOverlay != null) retainedOrderOverlay.detach();
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
        worldPicker = new WorldPicker(this);
        hud.addPanel(worldPicker);
        hud.addPanel(new DirectControlPanel(this));
        // The MLX power tray owns cards; this small world-layer partner owns
        // only its reticle and next-click targeting. Added after WorldPicker so
        // an armed power claims the map click before squad selection sees it.
        hud.addPanel(new QueuedDropMarkerPanel(this));
        commandPowerTargeting = new CommandPowerTargetingPanel(this, new PlacementPause.Clock() {
            @Override public boolean paused() { return speedMultiplier == 0f; }
            @Override public void setPaused(boolean paused) { setBattleSpeed(paused ? 0f : resumeSpeed); }
        });
        hud.addPanel(commandPowerTargeting);
        shipOrderTargeting = new ShipOrderTargetingPanel(this, this::battleChromeBlocksWorldPointer);
        hud.addPanel(shipOrderTargeting);
        // Added above the picker and power targeter. Infantry squads and Mech
        // lances share this Defend Area placement owner. Arming either
        // targeting family cancels the other, so only one world click owner
        // is live.
        squadOrderTargeting = new SquadOrderTargetingPanel(this);
        orderIntentCursor = new OrderIntentCursorPanel(this);
        hud.addPanel(squadOrderTargeting);
        hud.addPanel(orderIntentCursor);
        // Per-squad GOAP plan readout. It has no all-squad overview: the
        // diagnostic opens only while WorldPicker has a squad in Selection.
        hud.addPanel(new SquadPlanDebugPanel(this));
        // Shows/hides every developer panel below; it is the only one that stays up when they are hidden.
        // A player build has no developer HUD at all (DevConfig.DEV_MODE).
        if (DevConfig.DEV_MODE) hud.addPanel(new DebugHudSwitchPanel(this));
        // WARSHIP DEBUG readout + F6/F7 orders; invisible unless a debug ship is over the battle.
        if (DevConfig.DEBUG_WARSHIP_PICKER) hud.addPanel(new WarshipDebugPanel(this));
        // Per-phase tick wall-time profile (top-center, beside DEBUG).
        // DevConfig-gated; informs
        // the upcoming DoD / ECS refactor by showing which tick phases are
        // actually expensive at peak unit counts.
        tickProfilePanel = new TickProfileDebugPanel(this);
        hud.addPanel(tickProfilePanel);
        // Debug toggles + actions (top-center beside Tick Profile, collapsed
        // by default). Replaces
        // the prior DebugTogglesWidget which was attached to the screen's
        // widget root rather than the hud — moved into the hud so input +
        // render ordering match the rest of the debug panels.
        debugPanel = new DebugTogglesPanel(this);
        debugPanel.addToggle("Auto spike dumps",
                tickProfilePanel::isAutoDumpEnabled, tickProfilePanel::toggleAutoDump);
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
        debugPanel.addDial("Sun shadows",
                () -> renderer.getSun().shadowStrength(),
                value -> renderer.getSun().setShadowStrength((float) value),
                SunLight.MIN_SHADOW_STRENGTH, SunLight.MAX_SHADOW_STRENGTH, 1.0);
        // Both sun dials are linear: they are angles, and a reader dragging one
        // is looking for a bearing or a time of day, not tuning a coefficient.
        debugPanel.addDial("Sun azimuth",
                () -> renderer.getSun().azimuthDegrees(),
                value -> renderer.getSun().setAzimuthDegrees((float) value),
                SunLight.MIN_AZIMUTH_DEGREES, SunLight.MAX_AZIMUTH_DEGREES, 1.0);
        debugPanel.addDial("Sun elevation",
                () -> renderer.getSun().elevationDegrees(),
                value -> renderer.getSun().setElevationDegrees((float) value),
                SunLight.MIN_ELEVATION_DEGREES, SunLight.MAX_ELEVATION_DEGREES, 1.0);
        debugPanel.addAction("Force reinforcement", this::forceDefenderReinforcement);
        debugPanel.addToggle("Capture commander trace",
                () -> getSim() != null && getSim().isCommandTraceEnabled(),
                this::toggleCommanderTrace);
        debugPanel.addAction("Dump commander trace",
                () -> CommanderTraceDumper.dump(getSim()));
        debugPanel.addAction("Spawn mech family", () -> MechFamilyDebugSpawner.spawn(getSim()));
        turretAuthor = new TurretAuthorPanel(this);
        debugPanel.addToggle("Turret author",
                () -> turretAuthor.active,
                () -> {
                    exitDirectControl();
                    turretAuthor.active = !turretAuthor.active;
                    if (turretAuthor.active) {
                        cancelOtherShipInput();
                        if (shipOrderTargeting != null) shipOrderTargeting.cancel();
                    }
                    syncAuthoringChrome();
                });
        hud.addPanel(debugPanel);
        hud.addPanel(turretAuthor);
        // Player-facing battle dispatch surface. Added last so urgent comms
        // plates and the counterattack signpost paint above ordinary HUD
        // chrome; its opaque notice owns pointer input without exposing controls.
        battleComms = new BattleCommsPanel(this);
        hud.addPanel(battleComms);
    }

    /** Installs or relayouts the retained overlay without rebuilding its reactive tree. */
    private void ensureRetainedOverlay(BattleSimulation sim) {
        if (retainedOverlay == null) {
            retainedOverlay = new BattleHudOverlay(this::setBattleSpeed, camera);
        }
        retainedOverlay.attach(position, sim, speedMultiplier);
        if (retainedAirOverlay == null) retainedAirOverlay = new BattleAirOverlay();
        retainedAirOverlay.attach(position, sim, retainedOverlay.rightRailBottom());
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
                    this::toggleCommandPowerTargeting,
                    commandPowerTargeting::targetingPowerId,
                    commandPowerTargeting::pickerRows,
                    commandPowerTargeting::togglePick);
        }
        retainedPowerOverlay.attach(position, sim);
        if (retainedOrderOverlay == null) {
            retainedOrderOverlay = new BattleOrderOverlay(selection,
                    () -> squadOrderTargeting.mode(), this::toggleSquadOrderTargeting, this::toggleGarrisonStance);
        }
        retainedOrderOverlay.attach(position, sim, retainedPowerOverlay.occupiedHeight());
        if (shipCommandController == null) {
            shipCommandController = new BattleShipCommandController(this::getSim, selection,
                    shipOrderTargeting, this::toggleCommandPowerTargeting,
                    commandPowerTargeting::targetingPowerId, this::cancelOtherShipInput);
            shipCommandOverlay = new BattleShipCommandOverlay(shipCommandController::state);
        }
        shipCommandOverlay.heroBottomInset(shipHeroBottomInset());
        shipCommandOverlay.attach(position);
        if (retainedRetreatOverlay == null) {
            retainedRetreatOverlay = new BattleRetreatOverlay(new BattleRetreatOverlayModel.Actions() {
                @Override public void withdraw() { retreatFromBattle(); }
                @Override public void proceed() { continueFromBattle(); }
                @Override public void retry() { retryOperation(); }
            });
        }
        // The end panel hangs under the banner, which renderBanner centres on the play area.
        float bannerX = layout != null ? layout.gridX + layout.gridW / 2f : position.getX() + position.getWidth() / 2f;
        float bannerY = layout != null ? layout.gridY + layout.gridH / 2f : position.getY() + position.getHeight() / 2f;
        retainedRetreatOverlay.attach(position, bannerX, bannerY, exitPhase(sim), retryOffer());
        if (directControlOverlay == null) {
            directControlOverlay = new BattleDirectControlOverlay(selection, this::toggleDirectControl,
                    this::togglePause, new BattleDirectControlHudModel.WeaponActions() {
                        @Override public void select(int key) { selectDirectWeapon(key); }
                        @Override public void cycle(FireTrigger trigger) { cycleDirectTrigger(trigger); }
                        @Override public void pick(FireTrigger trigger, int member) { pickDirectTrigger(trigger, member); }
                    });
        }
        directControlOverlay.attach(position, sim, speedMultiplier);
        if (rangeTrial != null) directControlOverlay.setExitLabelKey("armoryRangeExit");
        if (manualObjectives == null) manualObjectives = new BattleManualObjectiveOverlay();
        manualObjectives.attach(position, sim);
    }

    private void toggleSquadOrderTargeting(SquadOrderTargetingPanel.Mode mode) {
        exitDirectControl();
        if (squadOrderTargeting == null) return;
        if (commandPowerTargeting != null) commandPowerTargeting.cancel();
        if (shipOrderTargeting != null) shipOrderTargeting.cancel();
        cameraControls.release();
        if (worldPicker != null) worldPicker.cancel();
        squadOrderTargeting.toggle(selection.getSelectedSquadId(), mode);
    }

    /**
     * The order bar's GARRISON card and the G key: the selected infantry squads into the stance, or all of them out of
     * it when all are already in. Mechs never man a place, so a lance in the selection takes no part.
     */
    private void toggleGarrisonStance() {
        BattleSimulation sim = getSim();
        if (sim == null || sim.isComplete()) return;
        List<Integer> infantry = new ArrayList<>();
        for (int id : selection.getSelectedSquadIds()) {
            Squad squad = sim.getSquad(id);
            if (squad != null && !squad.isMechSquad() && !squad.isDroneSquad()
                    && SquadOrderTargetingPanel.canOrderSquad(sim, id)) {
                infantry.add(id);
            }
        }
        if (!infantry.isEmpty()) sim.getReactiveManning().toggle(infantry);
    }

    private void toggleCommandPowerTargeting(String powerId) {
        exitDirectControl();
        if (commandPowerTargeting == null) return;
        if (squadOrderTargeting != null) squadOrderTargeting.cancel();
        if (shipOrderTargeting != null) shipOrderTargeting.cancel();
        commandPowerTargeting.toggle(powerId);
    }

    private void cancelOtherShipInput() {
        if (commandPowerTargeting != null) commandPowerTargeting.cancel();
        if (squadOrderTargeting != null) squadOrderTargeting.cancel();
        if (worldPicker != null) worldPicker.cancel();
        cameraControls.release();
    }

    /** The ship selector clears the exact visible selection plate and takeover shortcut. */
    private float shipHeroBottomInset() {
        BattleSimulation sim = getSim();
        long selected = selection.hasVehicleSelection() ? selection.getSelectedVehicleId()
                : selection.getSelectedUnitEntityId();
        return BattleShipCommandOverlay.heroBottomInset(position, selection,
                sim != null && sim.world().hasMechLoadout(selected));
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

    @Override public float topCenterContentBottom() {
        BattleHudSurface subtitle = storySubtitles.surface();
        return subtitle != null ? subtitle.viewport().screenY()
                : position == null ? 0f : position.getY() + position.getHeight() - BattleLayout.PAD;
    }

    @Override public float topCenterStackBottom() {
        return battleComms == null || directControlInput.active() ? topCenterContentBottom()
                : Math.min(topCenterContentBottom(), battleComms.noticeBottom());
    }

    @Override public boolean debugHudVisible() { return DevConfig.DEV_MODE && debugHudVisible; }

    @Override public void setDebugHudVisible(boolean visible) { debugHudVisible = visible; }

    @Override public float developerHudBottom() {
        return DebugHudLayout.clusterBottom(this, tickProfilePanel, debugPanel);
    }

    @Override public float rightHudBottom() {
        float rail = retainedOverlay == null ? topCenterContentBottom() : retainedOverlay.rightRailBottom();
        BattleHudSurface air = retainedAirOverlay == null ? null : retainedAirOverlay.surface();
        return air == null ? rail : Math.min(rail, air.viewport().screenY());
    }

    @Override public float bottomHudTop() {
        return bottomHudTop(position == null ? 0 : position.getX(), position == null ? 0 : position.getWidth());
    }

    @Override public float bottomHudTop(float x, float width) {
        float top = position == null ? 0f : position.getY() + BattleLayout.PAD;
        if (layout != null && x < layout.backX + BattleLayout.BACK_W && x + width > layout.backX
                && !selection.hasSquadSelection()) {
            top = layout.backY + BattleLayout.BACK_H + BattleLayout.CONTROLS_GAP;
        }
        if (retainedSquadOverlay != null) top = occupiedTop(top, x, width, retainedSquadOverlay.surface());
        if (retainedMechOverlay != null) top = occupiedTop(top, x, width, retainedMechOverlay.surface());
        if (retainedMechOverlay != null) top = occupiedTop(top, x, width, retainedMechOverlay.sectionSurface());
        if (retainedPowerOverlay != null) top = occupiedTop(top, x, width, retainedPowerOverlay.surface());
        if (retainedOrderOverlay != null) top = occupiedTop(top, x, width, retainedOrderOverlay.surface());
        if (retainedRetreatOverlay != null) top = occupiedTop(top, x, width, retainedRetreatOverlay.surface());
        if (shipCommandOverlay != null) {
            for (BattleHudSurface surface : shipCommandOverlay.surfaces()) top = occupiedTop(top, x, width, surface);
        }
        if (directControlOverlay != null && !directControlInput.active()) {
            for (BattleHudSurface surface : directControlOverlay.surfaces()) top = occupiedTop(top, x, width, surface);
        }
        return top;
    }

    private static float occupiedTop(float top, float x, float width, BattleHudSurface surface) {
        if (surface == null) return top;
        var bounds = surface.viewport();
        return x < bounds.screenX() + bounds.width() && x + width > bounds.screenX()
                ? Math.max(top, bounds.screenY() + bounds.height()) : top;
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
            // On the map, not just airborne: an aircraft idling on its
            // hardstand or taxiing to the strip has its engines running, and
            // the intensity curve already carries that as its floor.
            if (mission == null || !mission.isOnMap()) continue;
            float intensity = AirAppearance.engineIntensity(true, world.altitudeT(id));
            if (intensity <= 0f) continue;
            AirBody body = world.kinematics(id);
            EngineVoice voice = EngineVoiceResolver.resolve(world.airframe(id).renderHullId());
            // Deterministic ±jitter from the entity id so the offset doesn't change frame-to-frame.
            float pitchOffset = (((id >> 8) & 0xffL) / 255f * 2f - 1f) * SHUTTLE_ENGINE_PITCH_JITTER;
            float pitch = ENGINE_PITCH_IDLE + (ENGINE_PITCH_CRUISE - ENGINE_PITCH_IDLE) * intensity + pitchOffset;
            Vector2f loc = new Vector2f(body.x * AUDIO_WORLD_UNITS_PER_CELL,
                                        body.y * AUDIO_WORLD_UNITS_PER_CELL);
            Vector2f vel = shuttleVelocity(body);
            // The AirBody instance is the stable per-entity loop-voice key (same
            // instance every frame and across sorties), so concurrent craft on the
            // same clip stay on distinct voices.
            Global.getSoundPlayer().playLoop(voice.loopSoundId, body, pitch,
                    SHUTTLE_ENGINE_VOLUME * intensity, loc, vel);
        }
    }

    /** Ground engines and flux vents ({@link MachineAudio}): loops held this frame, cues fired once. */
    private void playMachinery(BattleSimulation sim) {
        MachineAudio.Frame frame = machineAudio.advance(sim, camera.panCellX(), camera.panCellY());
        for (MachineAudio.Loop loop : frame.loops()) {
            Global.getSoundPlayer().playLoop(loop.soundId(), loop.key(), loop.pitch(), loop.volume(),
                    new Vector2f(loop.cellX() * AUDIO_WORLD_UNITS_PER_CELL, loop.cellY() * AUDIO_WORLD_UNITS_PER_CELL),
                    new Vector2f(loop.vxCells() * AUDIO_WORLD_UNITS_PER_CELL, loop.vyCells() * AUDIO_WORLD_UNITS_PER_CELL));
        }
        for (MachineAudio.Cue cue : frame.cues()) {
            Global.getSoundPlayer().playSound(cue.soundId(), cue.pitch(), cue.volume(),
                    new Vector2f(cue.cellX() * AUDIO_WORLD_UNITS_PER_CELL, cue.cellY() * AUDIO_WORLD_UNITS_PER_CELL),
                    new Vector2f());
        }
    }

    /**
     * Per-frame velocity for {@link #driveShuttleEngineLoops} Doppler.
     *
     * <p>Read off the body and nothing else. This used to be gated on a
     * hand-written pair of phases — INCOMING and DEPARTING, the only two that
     * existed when it was written — so a craft attacking, repositioning,
     * coming home, settling onto a pad or climbing off one all read as parked
     * and the Doppler on a machine crossing the camera at speed simply did not
     * happen. The same fault {@code ShuttleMission.isOverTheBattle} was
     * converted away from, and here there is nothing to place a new phase on:
     * the body is the authority for motion, a parked craft's velocity is
     * already zero because it is not moving, and every locomotion model in the
     * feature composes {@code vx}/{@code vy} as it goes.
     */
    private static Vector2f shuttleVelocity(AirBody body) {
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
    /**
     * Rounds an aircraft put on the ground during the last tick: the flash and
     * the weapon at the muzzle now, the round drawn on its way down, and the
     * crater and the crump when it actually gets there.
     *
     * <p>Release and arrival are separate moments on purpose. The simulation
     * resolved the delivery the instant the round left, but a bomb falls for a
     * third of a second, and an explosion that precedes its own bomb is worse
     * than no explosion at all. {@link OrdnanceTraceFxService} holds the round
     * in between and says when it landed.
     */
    private void driveOrdnanceFx(BattleSimulation sim, float simDt) {
        OrdnanceTraceFxService traces = renderer.getOrdnanceTraceFx();
        List<OrdnanceRelease> releases = sim.getOrdnanceReleasesThisFrame();
        for (int i = 0, n = releases.size(); i < n; i++) {
            OrdnanceRelease release = releases.get(i);
            traces.spawn(release);
            OrdnanceFxRuntime.spawnRelease(
                    renderer.getImpactFx(), renderer.getGroundLights(), release);
        }
        traces.advance(simDt);
        List<OrdnanceRelease> arrivals = traces.arrivalsThisFrame();
        for (int i = 0, n = arrivals.size(); i < n; i++) {
            OrdnanceFxRuntime.spawnArrival(
                    renderer.getImpactFx(), renderer.getGroundLights(), arrivals.get(i));
        }
        ordnanceAudio.update(releases, arrivals, simDt);
        WarshipAudio.play(sim.warships().cuesThisFrame());
        WarshipAudio.play(sim.shieldDomes().cuesThisFrame());
        TenderAudio.play(sim);
        BoreAudio.play(sim);
        SwarmAudio.play(sim, camera.panCellX(), camera.panCellY());
        CreepAudio.play(sim, camera.panCellX(), camera.panCellY());
    }

    private void spawnImpactFx(BattleSimulation sim) {
        java.util.Random rng = java.util.concurrent.ThreadLocalRandom.current();
        Vector2f zeroVel = new Vector2f(0f, 0f);
        NavigationGrid grid = sim.getGrid();
        // Line-tracer shots spawn their impact at fire — the line covers the
        // whole travel instantly. Traveling bodies (sprites and bolts) wait
        // for arrival in the second pass.
        for (ShotEvent s : sim.getShotsThisFrame()) {
            renderer.getBeamFx().spawn(s);
            renderer.getGroundLights().spawnMuzzle(s);
            // Every shooting marine / militia / alien ejects a casing where
            // they're standing (skip rockets — tube-launched, no brass — and
            // a Spitter's acid, which is spat, not fired).
            if (s.specialEquipmentDef == null && s.turretStructureDef == null && !firedBySwarm(sim, s)) {
                ImpactDecals.spawnShellCasing(sim, rng, s.fromX, s.fromY);
            }
            WeaponFxRuntime.spawnMuzzle(renderer.getImpactFx(), s);
            renderer.getTurretFx().onShot(s, sim, renderer.getImpactFx());
            // Line tracers (no projectile sprite) land their impact instantly;
            // projectile-sprite shots defer it to arrival (handled below).
            if (ShotFx.of(s).travels()) continue;
            if (!s.impacts()) continue;
            boolean isWall = isWallAt(grid, s.toX, s.toY);
            WeaponFxDef fx = WeaponFxRuntime.definition(s);
            WeaponFxRuntime.spawnImpactAndAftermath(renderer.getImpactFx(), s, isWall);
            renderer.getGroundLights().spawnImpact(fx, s.toX, s.visualToY());
            // A burst in the air (an anti-air round at a craft's drawn height) marks no ground.
            if (s.toZ <= 0f) ImpactDecals.spawnWeaponImpact(sim, rng, s.weaponDef(), fx, s.toX, s.toY, isWall);
            playImpactCue(s, rng, zeroVel);
        }
        for (ShotEvent s : sim.getShotsExpiredThisFrame()) {
            if (!ShotFx.of(s).travels()) continue;
            if (!s.impacts()) continue;
            boolean isWall = isWallAt(grid, s.toX, s.toY);
            WeaponFxDef fx = WeaponFxRuntime.definition(s);
            WeaponFxRuntime.spawnImpactAndAftermath(renderer.getImpactFx(), s, isWall);
            playImpactCue(s, rng, zeroVel);
            // A burst in the air (an anti-air round at a craft's drawn height) marks no ground.
            if (s.toZ <= 0f) ImpactDecals.spawnWeaponImpact(sim, rng, s.weaponDef(), fx, s.toX, s.toY, isWall);
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

    private void playImpactCue(ShotEvent shot, java.util.Random rng, Vector2f zeroVel) {
        ShotImpactAudio.Cue cue = ShotImpactAudio.resolve(shot, SFX_NEAR_EXPLOSION);
        if (cue == null) return;
        Vector2f loc = new Vector2f(
                shot.toX * AUDIO_WORLD_UNITS_PER_CELL,
                shot.toY * AUDIO_WORLD_UNITS_PER_CELL);
        Global.getSoundPlayer().playSound(
                cue.soundId(), 0.9f + rng.nextFloat() * 0.2f, cue.volume(), loc, zeroVel);
    }

    /** True when the endpoint cell is non-walkable (wall / vehicle / turret mount) and the impact should read as a chip on solid material rather than a kick of floor dust. */
    /** Whether a swarm body fired the shot: a Spitter's lob, which leaves no casing. */
    private static boolean firedBySwarm(BattleSimulation sim, ShotEvent s) {
        return s.shooterId != 0L && sim.world().isAlive(s.shooterId) && sim.identity().type(s.shooterId).isSwarm();
    }

    private static boolean isWallAt(NavigationGrid grid, float x, float y) {
        int cx = (int) Math.floor(x);
        int cy = (int) Math.floor(y);
        if (!grid.inBounds(cx, cy)) return false;
        return !grid.isWalkable(cx, cy);
    }

    private void playCombatEventSounds(BattleSimulation sim, float dt) {
        BattleShotAudio.playPositional(sim.getShotsThisFrame(), heldFireLoops, dt);
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

    /** Mission dialogue owns the channel ahead of squad chatter; presentation uses real time. */
    private void advanceVoices(BattleSimulation sim, float dt) {
        operationRadio.observe(sim);
        if (storyNarration != null) {
            storyNarration.advance(dt, sim.getPlayerStoryBeatsAfter(storyNarration.cursor()),
                    speedMultiplier <= 0f, sim.isComplete());
        }
        boolean channelBusy = storyNarration != null && storyNarration.busy();
        boolean running = speedMultiplier > 0f && !sim.isComplete();
        if (sim.isComplete()) companyOfficer.discardPending();
        CompanyOfficer.Line response = companyOfficer.advance(dt, running, channelBusy || operationRadio.busy());
        if (response != null) radioChatter.reserveVoice(response.durationSeconds());
        OperationRadio.Transmission turn = operationRadio.advance(dt, running, sim.isComplete(),
                channelBusy || companyOfficer.busy());
        if (turn != null) radioChatter.reserveVoice(turn.line().durationSeconds());

        // Confirmed tactical reports get first use of an open channel; assignment exchanges are ambient.
        BattleRadioChatter.Emission emission = radioChatter.advance(running ? dt : 0f, sim,
                running && !channelBusy && !companyOfficer.busy() && !operationRadio.busy());
        if (emission != null) {
            OperationRadio.Transmission report = operationRadio.report(emission.cue());
            // Without the cast's catalog a report goes unspoken: there is no other radio bank.
            if (report != null) {
                radioChatter.reserveVoice(report.line().durationSeconds());
                // Company command answers once the report clears the channel.
                companyOfficer.heard(emission.cue());
            }
        } else if (running && !channelBusy && !companyOfficer.busy() && radioChatter.voiceAvailable()) {
            OperationRadio.Transmission opening = operationRadio.beginExchange();
            if (opening != null) radioChatter.reserveVoice(opening.line().durationSeconds());
        }
        storySubtitles.update(storyNarration != null ? storyNarration.current() : null,
                companyOfficer.current(), operationRadio.current(), dt);
    }

    private void retreatFromBattle() {
        if (rangeTrial != null) { exitDirectControl(); return; }
        if (ctx == null) return;
        BattleSimulation sim = ctx.getBattleSimulation();
        if (sim == null || !sim.retreat()) return;
        exitDirectControl();
        continueFromBattle();
    }

    private static BattleRetreatOverlayModel.Phase exitPhase(BattleSimulation sim) {
        if (sim == null || !sim.isComplete()) return BattleRetreatOverlayModel.Phase.ACTIVE;
        return sim.getWinner() == Faction.MARINE
                ? BattleRetreatOverlayModel.Phase.VICTORY : BattleRetreatOverlayModel.Phase.DEFEAT;
    }

    /** A story operation not yet won can be tried again: free once per mission-select visit, then a story point. */
    private BattleRetreatOverlayModel.RetryOffer retryOffer() {
        if (!retryable()) return BattleRetreatOverlayModel.RetryOffer.NONE;
        String cost = switch (ctx.storyRetry().cost()) {
            case FREE -> "Free";
            case STORY_POINT -> "StoryPoint";
            case UNAVAILABLE -> "Unavailable";
        };
        return new BattleRetreatOverlayModel.RetryOffer(true,
                ctx.storyRetry().cost() != StoryRetryAllowance.Cost.UNAVAILABLE,
                Strings.get("battleRestart" + cost), Strings.get("battleRetry" + cost));
    }

    private boolean retryable() {
        if (rangeTrial != null || ctx == null) return false;
        Mission mission = ctx.getSelectedMission();
        BattleSimulation sim = ctx.getBattleSimulation();
        return mission != null && mission.source == MissionSource.STORY && sim != null
                && !(sim.isComplete() && sim.getWinner() == Faction.MARINE);
    }

    /**
     * Erases this attempt and returns to the briefing on the same map. Nothing
     * settles: no casualties, losses or results, the arc's launch claim is
     * withdrawn and supplies spent on powers come back. Only the retry allowance
     * is paid.
     */
    private void retryOperation() {
        if (!retryable() || ctx.battleSettlementStarted()) return;
        Mission mission = ctx.getSelectedMission();
        if (!ctx.storyRetry().charge(Strings.format("battleRetryLog", mission.name))) return;
        BattleSimulation sim = ctx.getBattleSimulation();
        exitDirectControl();
        MissionLaunch.abandonAttempt(mission);
        if (sim.getCommandPowerResources() instanceof CampaignCommandPowerResources spent) spent.refundSpent();
        LOG.info("MarineOps: retrying story mission id=" + mission.id + "; attempt erased");
        ctx.retainLaunchedSurveySeed();
        ctx.setBattle(null, null, null);
        ctx.goTo(ScreenId.BRIEFING);
    }

    private void continueFromBattle() {
        if (rangeTrial != null) { exitDirectControl(); return; }
        if (ctx == null) return;
        BattleSimulation sim = ctx.getBattleSimulation();
        if (sim != null && sim.isComplete() && ctx.beginBattleSettlement()) {
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
        if (loading()) return;
        syncDirectControl();
        if (rangeFinished) return;
        syncAuthoringChrome();
        refreshSelectionChrome();
        if (events == null) return;
        if (turretAuthor != null && turretAuthor.active) {
            cameraControls.release();
            for (InputEventAPI event : events) {
                if (!event.isConsumed() && event.isKeyDownEvent() && event.getEventValue() == Keyboard.KEY_ESCAPE) {
                    turretAuthor.active = false;
                    event.consume();
                }
            }
            turretAuthor.handleInput(events);
            for (InputEventAPI event : events) if (!event.isConsumed()) event.consume();
            syncAuthoringChrome();
            refreshSelectionChrome();
            return;
        }
        // Space pauses everywhere on the battle screen. No battle surface takes typed text, so the
        // key is claimed before any panel can read it as "activate the focused button" and toggle twice.
        for (InputEventAPI event : events) {
            if (!event.isConsumed() && event.isKeyDownEvent() && !event.isRepeat()
                    && event.getEventValue() == Keyboard.KEY_SPACE) {
                togglePause();
                event.consume();
            }
        }
        // The chrome is see-through, so a press on it may still be a box select: the picker holds it
        // until it is a drag or a click, and a box under way keeps its release over any chrome.
        if (!directControlInput.active() && worldPicker != null) {
            events = worldPicker.handleInputBeforeChrome(events, this::chromeMayStartBox);
        }
        boolean controlledAtInputStart = directControlInput.active();
        var manualSamples = BattleDirectControlInput.capture(events);
        // Hidden strategic surfaces neither claim input nor retain keyboard focus.
        if (!directControlInput.active()) {
            if (retainedOverlay != null) retainedOverlay.processInput(events);
            if (retainedSquadOverlay != null) retainedSquadOverlay.processInput(events);
            refreshSelectionChrome();
            if (retainedMechOverlay != null) retainedMechOverlay.processInput(events);
            refreshSelectionChrome();
            if (retainedPowerOverlay != null && !selection.hasWarshipSelection()) retainedPowerOverlay.processInput(events);
            if (shipCommandOverlay != null) shipCommandOverlay.processInput(events);
            refreshSelectionChrome();
            if (retainedOrderOverlay != null) retainedOrderOverlay.processInput(events);
            if (retainedRetreatOverlay != null) retainedRetreatOverlay.processInput(events);
        }
        if (manualObjectives != null) manualObjectives.processInput(events);
        if (directControlOverlay != null) directControlOverlay.processInput(events);
        if (rangeFinished) return;
        // Opaque panel padding owns a new primary gesture even when no child handled it.
        // RMB and Escape reach armed targeters so their cancellation bindings remain intact.
        for (InputEventAPI event : events) {
            if (!event.isConsumed() && event.isLMBDownEvent()
                    && retainedChromeBlocksWorldPointer(event.getX(), event.getY())) event.consume();
        }
        // HUD gets first crack after retained chrome so a click on a squad row doesn't
        // also pan the camera or hit a future world-picker on the cells the
        // panel overlays. Panels self-consume claimed events.
        if (hud != null) hud.processInput(events);
        refreshSelectionChrome();
        if (turretAuthor != null && turretAuthor.active) {
            cameraControls.release();
            for (InputEventAPI event : events) if (!event.isConsumed()) event.consume();
            return;
        }
        // Debug damage runs BEFORE pan-drag so shift+RMB consumes the event
        // and the plain-RMB pan handler never sees it. Plain RMB (no shift)
        // is unclaimed and falls through to pan.
        if (Display.isCreated() && Display.isActive() && Keyboard.isCreated()) {
            directControlInput.reconcileToggleKeyRelease(Keyboard.isKeyDown(Keyboard.KEY_TAB));
            directControlInput.reconcileAbilityKeyReleases(
                    Keyboard.isKeyDown(Keyboard.KEY_C), Keyboard.isKeyDown(Keyboard.KEY_G),
                    Keyboard.isKeyDown(Keyboard.KEY_X), Keyboard.isKeyDown(Keyboard.KEY_F),
                    Keyboard.isKeyDown(Keyboard.KEY_R));
            directControlInput.reconcileShiftKeyReleases(
                    Keyboard.isKeyDown(Keyboard.KEY_LSHIFT), Keyboard.isKeyDown(Keyboard.KEY_RSHIFT));
        }
        directControlInput.process(manualSamples, this::toggleDirectControl,
                this::exitDirectControl, this::selectDirectWeapon, this::requestDirectAbility,
                this::cycleDirectTrigger,
                this::battleChromeBlocksWorldPointer);
        if (rangeFinished) return;
        if (shipOrderTargeting != null) shipOrderTargeting.pointer(directControlInput.pointerX(),
                directControlInput.pointerY(), directControlInput.pointerKnown());
        if (retainedPowerOverlay != null) retainedPowerOverlay.suppressed(selection.hasWarshipSelection());
        if (directControlInput.active() && !directControlInput.physicalShiftHeld()
                && directControlSimulation != null && directControlSimulation == getSim()
                && directControlSimulation.directControl().active()) {
            directControlSimulation.directControl().observeSprintRelease();
        }
        if (DevConfig.DEV_MODE && !controlledAtInputStart && !directControlInput.active()) handleDebugDamageInput(events);
        if (!controlledAtInputStart && !directControlInput.active()) handleSallyDoorOrder(events);
        handleCameraInput(events);
        // Apply the current lead at the new zoom before aim is projected.
        followControlledUnit(0f);
        submitDirectControlIntent();
        if (DevConfig.DEV_MODE && !directControlInput.active()) handleDebugZoneToggle(events);
    }

    private void toggleDirectControl() {
        if (rangeFinished) return;
        if (directControlInput.active()) {
            exitDirectControl();
            return;
        }
        BattleSimulation sim = getSim();
        if (sim == null || sim.isComplete()) return;
        boolean entered = selection.hasWarshipSelection()
                ? sim.directControl().enterWarship(selection.getSelectedWarship())
                : sim.directControl().enter(selection.hasVehicleSelection()
                        ? selection.getSelectedVehicleId() : selection.getSelectedUnitEntityId());
        if (!entered) return;
        directControlSimulation = sim;
        speedMultiplier = directControlInput.enter(speedMultiplier);
        cameraControls.release();
        if (worldPicker != null) worldPicker.cancel();
        if (commandPowerTargeting != null) commandPowerTargeting.cancel();
        if (squadOrderTargeting != null) squadOrderTargeting.cancel();
        if (shipOrderTargeting != null) shipOrderTargeting.cancel();
        actionCamera.reset();
        followControlledUnit(0f);
        if (turretAuthor != null) turretAuthor.active = false;
        syncDirectControlHud();
    }

    private boolean selectDirectWeapon(int selection) {
        BattleSimulation sim = getSim();
        if (sim == null || !sim.directControl().selectWeapon(selection)) return false;
        directControlInput.syncSelectedWeapon(sim.directControl().selectionKey());
        directControlInput.releaseFire();
        return true;
    }

    private boolean cycleDirectTrigger(FireTrigger trigger) {
        BattleSimulation sim = getSim();
        if (sim == null || !sim.directControl().cycle(trigger)) return false;
        directControlInput.syncSelectedWeapon(sim.directControl().selectionKey());
        directControlInput.releaseFire();
        return true;
    }

    private boolean pickDirectTrigger(FireTrigger trigger, int member) {
        BattleSimulation sim = getSim();
        if (sim == null || !sim.directControl().pick(trigger, member)) return false;
        directControlInput.syncSelectedWeapon(sim.directControl().selectionKey());
        directControlInput.releaseFire();
        return true;
    }

    private boolean requestDirectAbility(DirectControlAbility ability) {
        BattleSimulation sim = getSim();
        if (sim == null || sim != directControlSimulation || sim.isComplete()
                || !directControlInput.active() || !sim.directControl().active()
                || Display.isCreated() && !Display.isActive()) return false;
        // A reload has no aim, so a pointer resting on the HUD does not refuse it.
        if (ability == DirectControlAbility.RELOAD) {
            return speedMultiplier != 0f && sim.directControl().requestAbility(ability, null);
        }
        if (directControlInput.blocked(camera, speedMultiplier == 0f,
                        this::battleChromeBlocksWorldPointer)) return false;
        float aimX = camera.screenToCellX(directControlInput.pointerX());
        float aimY = camera.screenToCellY(directControlInput.pointerY());
        if (!Float.isFinite(aimX) || !Float.isFinite(aimY)
                || !sim.getGrid().inBounds((int) Math.floor(aimX), (int) Math.floor(aimY))) return false;
        return sim.directControl().requestAbility(ability, new PointFireAim(aimX, aimY));
    }

    private void exitDirectControl() {
        if (rangeTrial != null && directControlInput.active()) rangeFinished = true;
        if (directControlSimulation != null) directControlSimulation.directControl().exit();
        directControlSimulation = null;
        actionCamera.reset();
        speedMultiplier = directControlInput.exit(speedMultiplier);
        cameraControls.release();
        syncDirectControlHud();
    }

    /** Mode changes release old captures and restore the strategic documents on handback. */
    private void syncDirectControlHud() {
        boolean active = directControlInput.active();
        if (hud != null) hud.setDirectControl(active);
        if (active == directControlHudActive) return;
        directControlHudActive = active;
        if (active) {
            if (retainedOverlay != null) retainedOverlay.detach();
            if (retainedSquadOverlay != null) retainedSquadOverlay.detach();
            if (retainedMechOverlay != null) retainedMechOverlay.detach();
            if (retainedPowerOverlay != null) retainedPowerOverlay.detach();
            if (retainedOrderOverlay != null) retainedOrderOverlay.detach();
            if (shipCommandOverlay != null) shipCommandOverlay.detach();
            if (retainedRetreatOverlay != null) {
                retainedRetreatOverlay.cancelConfirmation();
                retainedRetreatOverlay.detach();
            }
        } else if (rangeTrial == null && position != null && ctx != null) {
            ensureRetainedOverlay(getSim());
        }
        if (directControlOverlay != null) directControlOverlay.update(0f, getSim(), speedMultiplier);
        if (manualObjectives != null) manualObjectives.update(0f, getSim());
    }

    private void syncDirectControl() {
        BattleSimulation sim = getSim();
        if (Display.isCreated() && !Display.isActive() && shipOrderTargeting != null) shipOrderTargeting.cancel();
        if (directControlInput.active() && (sim != directControlSimulation
                || sim == null || sim.isComplete() || !sim.directControl().active()
                || Display.isCreated() && !Display.isActive())) {
            exitDirectControl();
        }
        if (directControlOverlay != null) directControlOverlay.update(0f, sim, speedMultiplier);
        if (manualObjectives != null) manualObjectives.update(0f, sim);
    }

    /** Space and the takeover Pause control: stop the battle, or resume it at the speed it had. */
    private void togglePause() {
        setBattleSpeed(speedMultiplier > 0f ? 0f : resumeSpeed);
    }

    private void setBattleSpeed(float requested) {
        if (speedMultiplier > 0f) resumeSpeed = speedMultiplier;
        speedMultiplier = directControlInput.active()
                ? directControlInput.speedChanged(requested) : requested;
        if (directControlInput.active()) {
            directControlSimulation.directControl().suspendInput();
        }
    }

    @Override public boolean directControlAimVisible() {
        return directControlInput.active() && directControlInput.pointerKnown()
                && camera != null && camera.containsScreen(directControlInput.pointerX(), directControlInput.pointerY())
                && !battleChromeBlocksWorldPointer(directControlInput.pointerX(), directControlInput.pointerY());
    }

    private void submitDirectControlIntent() {
        if (directControlSimulation == null || !directControlInput.active()) return;
        directControlInput.syncSelectedWeapon(directControlSimulation.directControl().selectionKey());
        if (directControlInput.blocked(camera, speedMultiplier == 0f,
                this::battleChromeBlocksWorldPointer)) {
            directControlSimulation.directControl().suspendInput();
        }
        directControlSimulation.directControl().submit(directControlInput.intent(
                camera, speedMultiplier == 0f, this::battleChromeBlocksWorldPointer));
    }

    private void followControlledUnit(float dt) {
        if (directControlSimulation == null || !directControlInput.active() || camera == null) return;
        long unit = directControlSimulation.directControl().activeUnitId();
        Warship ship = directControlSimulation.directControl().controlledWarship();
        if (unit == 0L && ship == null) return;
        float pointerX = directControlInput.pointerX();
        float pointerY = directControlInput.pointerY();
        if (ship != null) {
            // The hull is framed by its beam rather than its length, so the cursor can lead past the bow.
            float shipRadius = .5f * Math.min(ship.widthCells, ship.lengthCells);
            actionCamera.follow(camera, ship.x, ship.y, shipRadius, pointerX, pointerY,
                    directControlInput.pointerKnown()
                            && !battleChromeBlocksWorldPointer(pointerX, pointerY), dt,
                    0f);
            if (directControlOverlay != null) {
                directControlOverlay.avoidControlledBody(camera.cellToScreenX(ship.x),
                        camera.cellToScreenY(ship.y), shipRadius * camera.cellPxSize());
            }
            return;
        }
        float radius = directControlSimulation.physicalRadius(unit);
        if (directControlSimulation.directControl().controlledVehicleId() != 0L) {
            var type = directControlSimulation.convoy().vehicleType(unit);
            if (type != null) radius = .5f * (float) Math.hypot(
                    type.visualLengthCells, type.visualWidthCells);
        }
        actionCamera.follow(camera, directControlSimulation.world().renderX(unit),
                directControlSimulation.world().renderY(unit), radius,
                pointerX, pointerY,
                directControlInput.pointerKnown()
                        && !battleChromeBlocksWorldPointer(pointerX, pointerY), dt,
                0f);
        if (directControlOverlay != null) {
            directControlOverlay.avoidControlledBody(
                    camera.cellToScreenX(directControlSimulation.world().renderX(unit)),
                    camera.cellToScreenY(directControlSimulation.world().renderY(unit)),
                    radius * camera.cellPxSize());
        }
    }

    /**
     * Camera input: wheel-to-zoom (zoom-to-cursor — the world point under the
     * mouse stays under the mouse), middle-drag-to-pan, and WASD/arrow-key pan
     * (the key flags are flipped here on down/up events and the actual pan
     * integration happens in {@link #advance} so a held key keeps panning
     * between events). The right button is the order button: a click is a
     * contextual order and a drag is a line to hold (shift+RMB is the debug
     * wall-damage gesture and gets consumed earlier).
     */
    private void handleCameraInput(List<InputEventAPI> events) {
        cameraControls.process(events, camera, CameraControls.PointerSpace.SCREEN,
                this::requestSelectedTacticalOrder, this::dragSelectedLine,
                this::battleChromeBlocksWorldPointer);
    }

    /**
     * A right drag: previews the line the selection would be given while the button is held,
     * and gives it on release. For a vehicle or mech selection the drag is a heading instead: the
     * unit goes where the button went down and finishes facing the way the drag went.
     */
    private void dragSelectedLine(float startX, float startY, float x, float y, boolean released) {
        BattleSimulation sim = getSim();
        facingPreview = null;
        if (sim == null || camera == null || sim.directControl().active()) {
            linePreview = null;
            return;
        }
        float cellX0 = camera.screenToCellX(startX);
        float cellY0 = camera.screenToCellY(startY);
        float cellX1 = camera.screenToCellX(x);
        float cellY1 = camera.screenToCellY(y);
        int x0 = (int) Math.floor(cellX0);
        int y0 = (int) Math.floor(cellY0);
        int x1 = (int) Math.floor(cellX1);
        int y1 = (int) Math.floor(cellY1);
        if (SelectionOrders.takesFacing(sim, selection)) {
            linePreview = null;
            linePreviewKey = Long.MIN_VALUE;
            float length = (float) Math.hypot(cellX1 - cellX0, cellY1 - cellY0);
            if (length < SelectionOrders.MIN_FACING_DRAG_CELLS) {
                if (released) SelectionOrders.requestMove(sim, selection, x1, y1);
                return;
            }
            float facing = SelectionOrders.facingOf(cellX1 - cellX0, cellY1 - cellY0);
            if (released) {
                SelectionOrders.requestMove(sim, selection, x0, y0, facing);
            } else {
                facingPreview = new HighlightOverlay.FacingMark(x0 + .5f, y0 + .5f, facing, length);
            }
            return;
        }
        if (released) {
            linePreview = null;
            linePreviewKey = Long.MIN_VALUE;
            SelectionOrders.requestLine(sim, selection, x0, y0, x1, y1);
            return;
        }
        long key = ((long) (x0 & 0xffff) << 48) | ((long) (y0 & 0xffff) << 32)
                | ((long) (x1 & 0xffff) << 16) | (y1 & 0xffff);
        if (key == linePreviewKey) return;
        linePreviewKey = key;
        linePreview = Math.hypot(x1 - x0, y1 - y0) < FrontageLine.MIN_LENGTH ? null
                : SelectionOrders.previewMarks(sim, selection, x0, y0, x1, y1);
    }

    /** Selection changes during input must publish the same plates and bounds before the next handler/render. */
    private void refreshSelectionChrome() {
        if (turretAuthor != null && turretAuthor.active) return;
        BattleSimulation sim = getSim();
        if (directControlInput.active()) {
            if (directControlOverlay != null) directControlOverlay.update(0f, sim, speedMultiplier);
            return;
        }
        if (retainedSquadOverlay != null) retainedSquadOverlay.update(0f, sim);
        if (retainedMechOverlay != null) retainedMechOverlay.update(0f, sim);
        if (retainedPowerOverlay != null) {
            retainedPowerOverlay.suppressed(selection.hasWarshipSelection());
            retainedPowerOverlay.update(0f, sim);
        }
        if (retainedOrderOverlay != null) retainedOrderOverlay.update(0f, sim,
                retainedPowerOverlay == null ? 0f : retainedPowerOverlay.occupiedHeight());
        if (shipCommandOverlay != null) {
            shipCommandOverlay.heroBottomInset(shipHeroBottomInset());
            shipCommandOverlay.update(0f);
        }
        if (directControlOverlay != null) directControlOverlay.update(0f, sim, speedMultiplier);
    }

    /** Authoring is a modal owner; hiding chrome also releases its retained pointer/key captures. */
    private void syncAuthoringChrome() {
        boolean active = turretAuthor != null && turretAuthor.active;
        if (active == authoringChromeActive) return;
        authoringChromeActive = active;
        cameraControls.release();
        if (worldPicker != null) worldPicker.cancel();
        if (active) {
            cancelOtherShipInput();
            if (shipOrderTargeting != null) shipOrderTargeting.cancel();
            if (retainedOverlay != null) retainedOverlay.detach();
            if (retainedSquadOverlay != null) retainedSquadOverlay.detach();
            if (retainedMechOverlay != null) retainedMechOverlay.detach();
            if (retainedPowerOverlay != null) retainedPowerOverlay.detach();
            if (retainedOrderOverlay != null) retainedOrderOverlay.detach();
            if (shipCommandOverlay != null) shipCommandOverlay.detach();
            if (retainedRetreatOverlay != null) {
                retainedRetreatOverlay.cancelConfirmation();
                retainedRetreatOverlay.detach();
            }
            if (directControlOverlay != null) directControlOverlay.detach();
            if (manualObjectives != null) manualObjectives.detach();
        } else if (position != null && ctx != null) {
            ensureRetainedOverlay(getSim());
        }
    }

    /** Queues an exact-mech move or context-resolved infantry squad order. */
    private void requestSelectedTacticalOrder(float screenX, float screenY) {
        BattleSimulation sim = getSim();
        if (sim == null || camera == null || sim.directControl().active()) return;
        if (battleChromeBlocksWorldPointer(screenX, screenY)) return;
        int cellX = (int) Math.floor(camera.screenToCellX(screenX));
        int cellY = (int) Math.floor(camera.screenToCellY(screenY));
        if (!sim.getGrid().inBounds(cellX, cellY)) return;
        SelectionOrders.requestMove(sim, selection, cellX, cellY);
    }

    private boolean battleChromeBlocksWorldPointer(float screenX, float screenY) {
        return retainedChromeBlocksWorldPointer(screenX, screenY)
                || !directControlInput.active() && hud != null && hud.blocksWorldPointer(screenX, screenY);
    }

    /** Retained chrome a box select may start on, unless an armed targeter or a confirmation wants the click. */
    private boolean chromeMayStartBox(float screenX, float screenY) {
        if (commandPowerTargeting != null && commandPowerTargeting.isVisible()
                || squadOrderTargeting != null && squadOrderTargeting.isVisible()
                || shipOrderTargeting != null && shipOrderTargeting.active()
                || retainedRetreatOverlay != null && retainedRetreatOverlay.confirmingRetreat()) return false;
        return retainedChromeBlocksWorldPointer(screenX, screenY);
    }

    private boolean retainedChromeBlocksWorldPointer(float screenX, float screenY) {
        if (turretAuthor != null && turretAuthor.active) return true;
        if (storySubtitles.blocksWorldPointer(screenX, screenY)) return true;
        if (directControlOverlay != null
                && directControlOverlay.blocksWorldPointer(screenX, screenY)) return true;
        if (manualObjectives != null && manualObjectives.blocksWorldPointer(screenX, screenY)) return true;
        if (directControlInput.active()) return false;
        return retainedOverlay != null
                && retainedOverlay.blocksWorldPointer(screenX, screenY)
                || retainedSquadOverlay != null
                && retainedSquadOverlay.blocksWorldPointer(screenX, screenY)
                || retainedMechOverlay != null
                && retainedMechOverlay.blocksWorldPointer(screenX, screenY)
                || retainedPowerOverlay != null
                && !selection.hasWarshipSelection() && retainedPowerOverlay.blocksWorldPointer(screenX, screenY)
                || shipCommandOverlay != null
                && shipCommandOverlay.blocksWorldPointer(screenX, screenY)
                || retainedOrderOverlay != null
                && retainedOrderOverlay.blocksWorldPointer(screenX, screenY)
                || retainedRetreatOverlay != null
                && retainedRetreatOverlay.blocksWorldPointer(screenX, screenY);
    }

    /**
     * B over a sally door of a place the player's side holds whole bars it (shut now, or as soon as nobody stands in it,
     * and kept shut) or unbars it (opened now and left open): the player's order on a door ({@code garrisonables.md},
     * sally doors). A door of a place the side does not hold, or one breached for good, ignores the key.
     */
    private void handleSallyDoorOrder(List<InputEventAPI> events) {
        if (events == null || layout == null || camera == null || ctx == null) return;
        BattleSimulation sim = ctx.getBattleSimulation();
        if (sim == null) return;
        for (InputEventAPI e : events) {
            if (e.isConsumed() || !e.isKeyDownEvent() || e.isRepeat() || e.getEventValue() != Keyboard.KEY_B) continue;
            if (e.isCtrlDown() || e.isAltDown() || e.isShiftDown()) continue;
            float px = e.getX();
            float py = e.getY();
            if (!camera.containsScreen(px, py)) continue;
            int door = sim.sallyDoors().doorAt(sim, (int) Math.floor(camera.screenToCellX(px)),
                    (int) Math.floor(camera.screenToCellY(py)));
            if (door < 0) continue;
            sim.sallyDoors().requestOrder(door, Faction.MARINE);
            e.consume();
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
        if (loading()) {
            if (loadingOverlay != null) loadingOverlay.render(alphaMult);
            return;
        }
        syncAuthoringChrome();
        refreshSelectionChrome();
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
            boolean strategic = !directControlInput.active();
            RenderContext rc = new RenderContext(
                    sim, camera, layout, alphaMult, lastAdvanceDt,
                    strategic && debugZonesVisible, strategic ? highlights : null,
                    strategic ? selection : null, rangeTrial == null
                    ? BattleRenderHostProfile.STANDALONE_BATTLE : BattleRenderHostProfile.EMBEDDED_SCENE);
            if (rangeTrial == null) renderer.renderWorld(rc);
            else renderer.renderWorld(rc, EnumSet.complementOf(EnumSet.of(RenderLayer.ROOFS, RenderLayer.FOG)));
            if (strategic && retainedRetreatOverlay != null && retainedRetreatOverlay.confirmingRetreat()) {
                renderExtractionZones(sim, alphaMult);
            }
            glPopAttrib();
        }

        // HUD sits above the world layer but below the victory/defeat banner — a
        // mid-battle squad-select shouldn't be visually competing with the
        // end-of-battle takeover.
        boolean authoring = turretAuthor != null && turretAuthor.active;
        if (authoring) turretAuthor.render(alphaMult);
        else if (hud != null) hud.render(alphaMult);

        // Player-facing MLX chrome paints above the debug HUD. Its root is
        // transparent, so only the compact command surfaces touch the canvas.
        if (!directControlInput.active() && !authoring) {
            if (retainedOverlay != null) retainedOverlay.render(alphaMult);
            if (retainedAirOverlay != null) retainedAirOverlay.render(alphaMult);
            if (retainedOrderOverlay != null) retainedOrderOverlay.render(alphaMult);
            if (retainedSquadOverlay != null) retainedSquadOverlay.render(alphaMult);
            if (retainedMechOverlay != null) retainedMechOverlay.render(alphaMult);
            if (retainedPowerOverlay != null && !selection.hasWarshipSelection()) retainedPowerOverlay.render(alphaMult);
            if (shipCommandOverlay != null) shipCommandOverlay.render(alphaMult);
            if (retainedRetreatOverlay != null) retainedRetreatOverlay.render(alphaMult);
        }
        if (!authoring) {
            if (manualObjectives != null) manualObjectives.render(alphaMult);
            if (directControlOverlay != null) directControlOverlay.render(alphaMult);
            storySubtitles.render(alphaMult);
        }

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

    /**
     * While Retreat is being confirmed, the circles a withdrawal extracts from
     * unhurt: {@link WithdrawalExtraction} tests exactly these.
     */
    private void renderExtractionZones(BattleSimulation sim, float alphaMult) {
        if (sim == null || camera == null) return;
        String label = Strings.upper(Strings.get("battleExtractionZone"));
        for (LandingPad pad : sim.getMarineLandingPads()) {
            float cx = camera.cellToScreenX(WithdrawalExtraction.centreX(pad));
            float cy = camera.cellToScreenY(WithdrawalExtraction.centreY(pad));
            float r = WithdrawalExtraction.radius(pad) * camera.cellPxSize();
            int segments = Math.max(32, Math.min(128, Math.round(r / 4f)));
            glDisable(GL_TEXTURE_2D);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glColor4f(EXTRACTION_COLOR.getRed() / 255f, EXTRACTION_COLOR.getGreen() / 255f,
                    EXTRACTION_COLOR.getBlue() / 255f, 0.14f * alphaMult);
            glBegin(GL_TRIANGLE_FAN);
            glVertex2f(cx, cy);
            for (int i = 0; i <= segments; i++) {
                double a = Math.PI * 2.0 * i / segments;
                glVertex2f(cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r);
            }
            glEnd();
            glColor4f(EXTRACTION_COLOR.getRed() / 255f, EXTRACTION_COLOR.getGreen() / 255f,
                    EXTRACTION_COLOR.getBlue() / 255f, 0.85f * alphaMult);
            glLineWidth(2f);
            glBegin(GL_LINE_LOOP);
            for (int i = 0; i < segments; i++) {
                double a = Math.PI * 2.0 * i / segments;
                glVertex2f(cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r);
            }
            glEnd();
            float labelW = Fonts.ORBITRON_12_BOLD.measureWidth(label);
            Fonts.ORBITRON_12_BOLD.drawString(label, cx - labelW / 2f,
                    cy + r + 6f + Fonts.ORBITRON_12_BOLD.getLineHeight(), EXTRACTION_COLOR, alphaMult);
        }
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
