package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.BattleSetup;
import com.dillon.starsectormarines.battle.Decal;
import com.dillon.starsectormarines.battle.Doodad;
import com.dillon.starsectormarines.battle.EquipmentDrop;
import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.ShotEvent;
import com.dillon.starsectormarines.battle.SmokingWreck;
import com.dillon.starsectormarines.battle.TimeOfDay;
import com.dillon.starsectormarines.battle.air.Shuttle;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.sprites.SpriteSheetFrames;
import com.dillon.starsectormarines.battle.sprites.SpriteSheetSlicer;
import com.dillon.starsectormarines.battle.sprites.UrbanTile3;
import com.dillon.starsectormarines.battle.MapTurret;
import com.dillon.starsectormarines.battle.MapVehicle;
import com.dillon.starsectormarines.battle.MarineSecondary;
import com.dillon.starsectormarines.battle.MarineWeapon;
import com.dillon.starsectormarines.battle.TileManifest;
import com.dillon.starsectormarines.battle.TurretKind;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.UnitType;
import com.dillon.starsectormarines.battle.VehicleKind;
import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.map.WallMasks;
import com.dillon.starsectormarines.battle.flyby.FlybyOverlay;
import com.dillon.starsectormarines.battle.ui.BattleHud;
import com.dillon.starsectormarines.battle.ui.BattleUiContext;
import com.dillon.starsectormarines.battle.ui.panel.SquadDetailPanel;
import com.dillon.starsectormarines.battle.ui.panel.SquadOverviewPanel;
import com.dillon.starsectormarines.battle.ui.panel.SquadPlanDebugPanel;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.ui.picking.WorldPicker;
import com.dillon.starsectormarines.battle.fx.ImpactDecals;
import com.dillon.starsectormarines.battle.fx.ImpactFx;
import com.dillon.starsectormarines.battle.fx.ImpactProfile;
import com.dillon.starsectormarines.battle.fx.WeaponLights;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.objective.ChargeSiteObjective;
import com.dillon.starsectormarines.battle.objective.Objective;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ops.battleview.BattleCamera;
import com.dillon.starsectormarines.render2d.DecalAccumulator;
import com.dillon.starsectormarines.render2d.GlStateBracket;
import com.dillon.starsectormarines.render2d.LightAccumulator;
import com.dillon.starsectormarines.render2d.LightKernel;
import com.dillon.starsectormarines.render2d.QuadBatch;
import com.dillon.starsectormarines.render2d.SolidQuadBatch;
import com.dillon.starsectormarines.ui.ButtonWidget;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.LabelWidget;
import com.dillon.starsectormarines.ui.WidgetRoot;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
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

    private static final Color FLOOR_COLOR    = new Color(0x18, 0x22, 0x30);
    private static final Color WALL_COLOR     = new Color(0x06, 0x0A, 0x10);
    private static final Color MARINE_COLOR   = new Color(0x5A, 0xA0, 0xE0);
    private static final Color DEFENDER_COLOR = new Color(0xE0, 0x6A, 0x6A);
    private static final Color CIVILIAN_COLOR = new Color(0xC8, 0xC8, 0x80);
    private static final Color HP_BG          = new Color(0x60, 0x20, 0x20);
    private static final Color HP_FG          = new Color(0x40, 0xC0, 0x40);
    private static final Color HEADER_COLOR   = new Color(0xC8, 0xE0, 0xFF);
    private static final Color ACTIVE_SPEED   = new Color(0xFF, 0xB8, 0x00);
    private static final Color BANNER_BG      = new Color(0x10, 0x14, 0x1E);
    private static final Color VICTORY_COLOR  = new Color(0x80, 0xE0, 0x80);
    private static final Color DEFEAT_COLOR   = new Color(0xE0, 0x60, 0x60);
    private static final Color MARINE_TRACER  = new Color(0xFF, 0xE0, 0x70);
    private static final Color DEFENDER_TRACER = new Color(0xFF, 0x70, 0x40);

    /** Sim-seconds shots live for — must match {@code BattleSimulation.SHOT_LIFETIME}. Used to fade tracer alpha. */
    private static final float SHOT_LIFETIME_REF = 0.15f;

    private static final float UNIT_FRAC      = 1.00f; // sprite fills the cell
    private static final float HP_BAR_H       = 3f;
    private static final float HP_BAR_GAP     = 2f;
    private static final float SPEED_BTN_W    = 60f;
    private static final float SPEED_BTN_H    = 32f;
    private static final float SPEED_BTN_GAP  = 6f;
    private static final float SPEED_MARK_H   = 3f;

    /** Objective marker icons — white-on-transparent shapes tinted at render time. */
    private static final String ICON_ALARM   = "graphics/icons/Alarm 512 px.png";
    private static final String ICON_DANGER  = "graphics/icons/Danger sign 1 512 px.png";
    private static final String ICON_STAR    = "graphics/icons/Star 512 px.png";

    /** Icon tints + sizes. Sizes are fractions of {@code layout.cellSize}. */
    private static final Color  CHARGE_TINT_ACTIVE   = new Color(0xFF, 0x9A, 0x40);
    private static final Color  CHARGE_TINT_COMPLETE = new Color(0xE0, 0x40, 0x40);
    private static final Color  CHARGE_TINT_ARC      = new Color(0xFF, 0xC8, 0x70);
    private static final Color  KIT_DROP_TINT        = new Color(0x80, 0xE8, 0xFF);
    private static final float  CHARGE_ICON_SIZE     = 1.5f;
    private static final float  KIT_DROP_SIZE        = 1.0f;
    private static final float  CHARGE_PULSE_AMP     = 0.10f;
    private static final float  CHARGE_PULSE_HZ      = 1.5f;
    private static final float  KIT_DROP_PULSE_AMP   = 0.10f;
    private static final float  KIT_DROP_PULSE_HZ    = 0.6f;
    private static final int    PROGRESS_ARC_SEGMENTS = 32;

    /** Sound IDs declared in mod/data/config/sounds.json. */
    private static final String MUSIC_BATTLE  = "marines_battle_music";
    private static final String LOOP_TICKING  = "marines_ticking_clock";
    private static final String LOOP_ENGINE   = "marines_shuttle_engine";
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
     * winding-mechanism vibe. The fan_reso_1/2/3 clips also live in {@link #VEHICLE_ENGINE_LOOPS}
     * and follow shuttles + flyby fighters as positional engine resonance — they read as airy
     * overhead-mechanical noise that's distinctive enough that hearing it in the static ambient
     * bed reads as "another shuttle nearby," so we keep them out of the bed.
     */
    private static final String[] AMBIENT_LOOP_POOL = {
            "marines_ambient_fan_noise",
            "marines_ambient_motor_1",
            "marines_ambient_motor_2",
            "marines_ambient_loudmotor_3",
            "marines_ambient_radiator_1",
            "marines_ambient_helicopter_2",
    };

    /**
     * Per-vehicle positional engine resonance loops. Each visible shuttle (and each flyby fighter,
     * driven from {@link FlybyOverlay}) picks one of these three by stable hash and plays it at
     * its world position every frame — OpenAL spatialization gives each vehicle a distinct
     * doppler-shifted whoosh as it banks overhead. Cycling across three clips means three shuttles
     * landing simultaneously each contribute a unique texture instead of folding into one voice.
     */
    private static final String[] VEHICLE_ENGINE_LOOPS = {
            "marines_ambient_fan_reso_1",
            "marines_ambient_fan_reso_2",
            "marines_ambient_fan_reso_3",
    };
    /** Volume scalar on the shuttle resonance loop. Layered on top of the existing UI engine hum, so kept moderate — the hum is the bass, this is the airy top layer. */
    private static final float SHUTTLE_RESONANCE_VOLUME = 0.5f;
    /** ±range of the per-shuttle pitch offset, so simultaneous shuttles playing the same reso clip don't beat against each other in lockstep. */
    private static final float SHUTTLE_RESONANCE_PITCH_JITTER = 0.08f;
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
    /** Window (s) after a unit fires during which we show the weapon-up pose. */
    private static final float WEAPON_UP_TIME = 0.25f;

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
    /** Per-{@link UnitType} sprite sheet cache. Each entry's sheet + frames pair is null if that type's load failed; the renderer falls through to its color-quad fallback for that unit. */
    private final java.util.EnumMap<UnitType, UnitSpriteCache> unitSprites = new java.util.EnumMap<>(UnitType.class);
    /** Per-{@link UnitType} corpse-pose sheet — 4 frames per type, picked randomly per unit at death. Missing entries (civilians, turrets) skip the dead-render pass entirely. */
    private final java.util.EnumMap<UnitType, UnitSpriteCache> unitDeadSprites = new java.util.EnumMap<>(UnitType.class);
    private boolean unitSpritesLoadAttempted;

    private static final class UnitSpriteCache {
        final SpriteAPI sheet;
        final SpriteSheetFrames frames;
        UnitSpriteCache(SpriteAPI sheet, SpriteSheetFrames frames) {
            this.sheet = sheet;
            this.frames = frames;
        }
    }

    /** Per-{@link VehicleKind.VehicleSheet} sprite cache for parked truck sprites. Same lazy-load + auto-slice pattern as the unit sheets. */
    private final java.util.EnumMap<VehicleKind.VehicleSheet, UnitSpriteCache> vehicleSheets =
            new java.util.EnumMap<>(VehicleKind.VehicleSheet.class);
    private boolean vehicleSheetsLoadAttempted;
    /** Per-{@link TurretKind} sprite + native aspect cache. Same shape as {@link ShuttleSpriteCache}: the sprite is single-frame and rotated at draw time, so no auto-slicing. */
    private final java.util.EnumMap<TurretKind, ShuttleSpriteCache> turretSprites =
            new java.util.EnumMap<>(TurretKind.class);
    /** Per-{@link TurretKind} barrel sprite cache. Vanilla {@code _recoil} naming is misleading — it's the gun/barrel piece (the part that visibly recoils), not a muzzle-flash overlay. Drawn below the body and shifted backward briefly after firing. */
    private final java.util.EnumMap<TurretKind, ShuttleSpriteCache> turretRecoilSprites =
            new java.util.EnumMap<>(TurretKind.class);
    /** Per-{@link TurretKind} projectile sprite (vanilla {@code bulletSprite}). Rendered rotated along the travel vector for the shot's {@link #SHOT_LIFETIME_REF} lifetime. */
    private final java.util.EnumMap<TurretKind, ShuttleSpriteCache> turretProjectileSprites =
            new java.util.EnumMap<>(TurretKind.class);
    /** Per-{@link MarineSecondary} projectile sprite (rocket etc). Same rendering path as turret projectiles. */
    private final java.util.EnumMap<MarineSecondary, ShuttleSpriteCache> marineSecondarySprites =
            new java.util.EnumMap<>(MarineSecondary.class);
    /** Per-{@link MarineWeapon} projectile sprite — populated only for weapons whose {@code projectileSpritePath} is non-null (SMG today). Marines without a sprite path keep the line-tracer render. */
    private final java.util.EnumMap<MarineWeapon, ShuttleSpriteCache> marineWeaponProjectileSprites =
            new java.util.EnumMap<>(MarineWeapon.class);
    /** Per-{@link com.dillon.starsectormarines.battle.MechWeapon} projectile sprite — every mech weapon ships with one (vanilla shell / SRM / LRM art), so every entry gets loaded eagerly. */
    private final java.util.EnumMap<com.dillon.starsectormarines.battle.MechWeapon, ShuttleSpriteCache> mechWeaponProjectileSprites =
            new java.util.EnumMap<>(com.dillon.starsectormarines.battle.MechWeapon.class);
    /** Shared decal sheet — 13 frames horizontally, auto-sliced into a SpriteSheetFrames so individual {@link Decal#decalIndex} values map to a frame box. */
    private SpriteAPI decalSheet;
    private SpriteSheetFrames decalFrames;
    private boolean decalSheetLoadAttempted;
    /**
     * Persistent decal-accumulator FBO. Each spawned decal is stamped into
     * the FBO once; the per-frame render cost is a single textured-quad
     * blit regardless of decal count. See {@link DecalAccumulator} javadoc.
     * Resolution comes from {@link com.dillon.starsectormarines.DevConfig#DECAL_FBO_PX_PER_CELL}.
     */
    private final DecalAccumulator decalAccumulator =
            new DecalAccumulator(com.dillon.starsectormarines.DevConfig.DECAL_FBO_PX_PER_CELL);
    /**
     * Lightmap accumulator driving the pseudo time-of-day pass. Bakes a
     * lightmap each frame (ambient clear + additive radial kernels for
     * muzzle flashes, HE bursts, wreck fires) and multiply-blends it over
     * the world layer. Bypassed when {@link #timeOfDay} is {@link
     * TimeOfDay#DAY}.
     */
    private final LightAccumulator lightAccumulator =
            new LightAccumulator(com.dillon.starsectormarines.DevConfig.DECAL_FBO_PX_PER_CELL);
    /**
     * Current ambient lighting preset. Hard-coded to NIGHT for v1; will be
     * driven by mission data and (eventually) an animated cycle that ties
     * battle elapsed time to a dawn arrival of enemy reinforcements.
     */
//    private TimeOfDay timeOfDay = TimeOfDay.NIGHT;
    private TimeOfDay timeOfDay = TimeOfDay.DAY;
    private static final String SPRITE_DECAL_SHEET = "graphics/decals/decals.png";
    /** Per-{@link MarineSecondary} marine aim sheet — drawn instead of the regular type sheet while the marine is mid-aim animation. Same 7-frame WNES + weapon-up convention as the regular marine sheets, auto-sliced via {@link SpriteSheetSlicer}. */
    private final java.util.EnumMap<MarineSecondary, UnitSpriteCache> marineSecondaryAimSheets =
            new java.util.EnumMap<>(MarineSecondary.class);
    private boolean marineSecondarySpritesLoadAttempted;
    private boolean turretSpritesLoadAttempted;
    /** Sim-seconds the barrel sprite eases forward to its at-rest position after a shot. Quick snap-back-then-return reads as a real recoil pulse. */
    private static final float RECOIL_DURATION = 0.12f;
    /** Peak backward displacement of the barrel sprite, as a fraction of the turret's visual long-axis (cells). Vanilla uses absolute pixel offsets per-weapon; a relative figure scales cleanly across our 5 kinds. */
    private static final float RECOIL_DISTANCE_FRAC = 0.10f;
    /** Cached tileset sheet — lazy-loaded once per Screen lifetime. Null if load failed. */
    private SpriteAPI tileSheet;
    /** Pixel dimensions of the tileset PNG content (pre-POT-padding). */
    private int tileSheetPxW;
    private int tileSheetPxH;
    private boolean tileSheetLoadAttempted;
    /** Cached road tileset (second sheet, outdoor street autotile). Same lazy-load pattern as {@link #tileSheet}. */
    private SpriteAPI roadSheet;
    private int roadSheetPxW;
    private int roadSheetPxH;
    private boolean roadSheetLoadAttempted;
    /** Cached Floors_Tiles sheet (outdoor surfaces: grass/dirt/stone/sand/snow + fl-tile cluster). 16px source cells, drawn at 2x to fit the 32px nav grid. */
    private SpriteAPI floorsSheet;
    private int floorsSheetPxW;
    private int floorsSheetPxH;
    private boolean floorsSheetLoadAttempted;
    /** Cached Water_tiles sheet. Same 16px-at-2x setup as {@link #floorsSheet}. */
    private SpriteAPI waterSheet;
    private int waterSheetPxW;
    private int waterSheetPxH;
    private boolean waterSheetLoadAttempted;
    /**
     * Cached urban-tileset-3.png sheet — sliced strip carrying modern road,
     * sidewalk, sidewalk-corner, and three doodad frames. Variable-width
     * cells: the per-frame bbox lives in {@link #urbanTile3Frames}, populated
     * by {@link SpriteSheetSlicer} on load. STREET cells dispatch through
     * this sheet when loaded; the legacy {@link TileManifest#ROAD_SHEET}
     * autotile path stays in place as a fallback for the load-failed case.
     */
    private SpriteAPI urbanTile3Sheet;
    private int urbanTile3SheetPxW;
    private int urbanTile3SheetPxH;
    private boolean urbanTile3SheetLoadAttempted;
    private SpriteSheetFrames urbanTile3Frames;
    /**
     * Cached nature-tiles.png sheet — sliced strip carrying grass / dirt /
     * sand / water ground variants plus plant + rock overlays. Loaded the
     * same way as {@link #urbanTile3Sheet} (slicer-detected variable bbox
     * per frame, cached in {@link #natureFrames}). GRASS and DIRT
     * {@link CellTopology.GroundKind} cells dispatch through this sheet —
     * the Floors_Tiles autotile path is retired for those two kinds.
     */
    private SpriteAPI natureSheet;
    private int natureSheetPxW;
    private int natureSheetPxH;
    private boolean natureSheetLoadAttempted;
    private SpriteSheetFrames natureFrames;
    /**
     * Per-sheet quad batchers. Lazily constructed when each sheet finishes
     * loading. Reused across passes — the urban batch is appended to by
     * both the floor pass (rubble + INDOOR + DOOR_OPEN overlays) and the
     * wall pass, with a flush in between so decals / vehicles slot in at
     * the correct painter depth.
     */
    private QuadBatch urbanBatch;
    private QuadBatch roadBatch;
    private QuadBatch floorsBatch;
    private QuadBatch waterBatch;
    private QuadBatch urbanTile3Batch;
    private QuadBatch natureBatch;
    /**
     * Solid-color batch for in-loop fills that need to share painter
     * ordering with the textured batches — crosswalk stripes (drawn over
     * road tiles inside the floor pass) and the interior-wall fallback
     * (no autotile pick).
     */
    private final SolidQuadBatch solidBatch = new SolidQuadBatch(256);
    private static final Color ROAD_FILL = new Color(TileManifest.ROAD_FILL_RGB);
    /** Solid fill for the open-courtyard case (no wall-neighbor autotile lookup hits a frame variant). */
    private static final Color COURTYARD_FILL = new Color(TileManifest.COURTYARD_FILL_RGB);
    /** Painted crosswalk stripe color. Slightly off-white so it doesn't compete with HP-bar greens. */
    private static final Color CROSSWALK_STRIPE = new Color(0xE8, 0xE8, 0xD0);
    /** Stripes per crosswalk cell. 5 is the classic zebra-crossing count and fits cleanly inside one cell. */
    private static final int CROSSWALK_STRIPE_COUNT = 5;
    /** Crosswalk stripe + gap widths as fractions of the cell — chosen so 5 stripes + 4 gaps + 2 margins fill the cell width. */
    private static final float CROSSWALK_STRIPE_FRAC = 0.10f;
    private static final float CROSSWALK_GAP_FRAC    = 0.10f;
    private static final float CROSSWALK_ALPHA       = 0.85f;
    /** Inset along the perpendicular axis — stripes don't quite reach the cell edge, leaving the road surface visible at the curb. */
    private static final float CROSSWALK_INSET_FRAC  = 0.08f;

    /**
     * Pixels to crop from each side of a ground-tile's source rect before
     * sampling. Values live in {@link com.dillon.starsectormarines.battle.sprites.FixedGridTileDrawer}
     * so the in-game renderer and the preview tests share one source of
     * truth — bumping the inset in one place reflects in both. See that
     * class's javadoc for the rationale (AA-halo + atlas-bleed seams).
     *
     * <p>Doodads and overhead-door overlays explicitly pass
     * {@link com.dillon.starsectormarines.battle.sprites.FixedGridTileDrawer#OVERLAY_INSET_PX}
     * to {@link #drawTile} — their edge pixels are visible content, not
     * part of a tiling field, so cropping would chop the sprite visibly.
     */
    private static final int GROUND_TILE_EDGE_INSET_PX        = com.dillon.starsectormarines.battle.sprites.FixedGridTileDrawer.GROUND_INSET_PX_LARGE;
    private static final int GROUND_SMALL_TILE_EDGE_INSET_PX  = com.dillon.starsectormarines.battle.sprites.FixedGridTileDrawer.GROUND_INSET_PX_SMALL;

    /**
     * Cached shuttle sprite + natural aspect ratio (width/height) per ShuttleType.
     * Captured at load time before any setSize call mutates getWidth/getHeight.
     * EnumMap is overkill for one variant today; map keeps the door open for
     * Valkyrie + heavier dropships in Phase 2 without restructuring.
     */
    private final java.util.EnumMap<ShuttleType, ShuttleSpriteCache> shuttleSprites = new java.util.EnumMap<>(ShuttleType.class);
    private boolean shuttleSpritesLoadAttempted;

    /**
     * Vanilla engine-FX sprites — drawn under each shuttle's hull to sell
     * the "thrusters firing" read. The flame is a column-shaped plume that
     * elongates with throttle; the glow is a soft halo behind it. Both are
     * tinted at draw time by {@link EngineStylePalette} based on the
     * per-slot style read out of the .ship spec.
     */
    private static final String ENGINE_FLAME_SPRITE = "graphics/fx/engineflame32.png";
    private static final String ENGINE_GLOW_SPRITE  = "graphics/fx/engineglow32.png";
    private SpriteAPI engineFlameSprite;
    private SpriteAPI engineGlowSprite;
    private boolean engineFxSpritesLoadAttempted;

    /** Lazy-loaded objective marker icons. Null entries fall back to colored rectangles. */
    private SpriteAPI iconAlarm;
    private SpriteAPI iconDanger;
    private SpriteAPI iconStar;
    private boolean iconsLoadAttempted;
    /** Atmosphere layer — vanilla fighters flying overhead, strafing targets of opportunity. Survives across attach() calls; lazy-loads sprites on first render. */
    private final FlybyOverlay flybyOverlay = new FlybyOverlay();
    /** Debug toggle (Z) — tints each navigation zone with a stable per-id color so the partitioning + new portals from wall breaches are eyeball-verifiable. */
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
    /** Ground-combat impact FX engine — sparks, dust, smoke, and HE fire bursts at shot endpoints. Separate from the flyby overlay's particle system so the two evolve independently. */
    private final ImpactFx impactFx = new ImpactFx();

    private static final class ShuttleSpriteCache {
        final SpriteAPI sprite;
        final float aspect;
        ShuttleSpriteCache(SpriteAPI sprite, float aspect) {
            this.sprite = sprite;
            this.aspect = aspect;
        }
    }

    @Override
    public void attach(PositionAPI position, MarineOpsContext ctx, Runnable dismissDialog) {
        this.position = position;
        this.ctx = ctx;
        this.speedMultiplier = 1f;
        ensureUnitSheets();
        ensureVehicleSheets();
        ensureTurretSprites();
        ensureMarineSecondarySprites();
        ensureDecalSheet();
        ensureTileSheet();
        ensureRoadSheet();
        ensureFloorsSheet();
        ensureNatureSheet();
        ensureWaterSheet();
        ensureUrbanTile3Sheet();
        ensureShuttleSprites();
        ensureEngineFxSprites();
        ensureObjectiveIcons();
        impactFx.ensureSprites();
        // Wire the lightmap sink into the flyby overlay so fighter muzzle
        // flashes + engine glows route to the same accumulator the
        // BattleScreen drives. Idempotent — safe across attach/detach
        // cycles.
        flybyOverlay.setLightAccumulator(lightAccumulator);
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
        Global.getSoundPlayer().playCustomMusic(MUSIC_FADE_SECS, MUSIC_FADE_SECS, MUSIC_BATTLE, true);
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

    /**
     * Lazy-loads the battle tileset on first attach. Reads the raw PNG to
     * capture content dimensions — {@code SpriteAPI.getTextureWidth()} reports
     * the POT-padded texture size, but per-tile UV math needs the content
     * width to compute texture-fraction coords correctly. Cached across attach
     * calls; survives screen re-entry without re-decoding the PNG.
     */
    private void ensureTileSheet() {
        if (tileSheetLoadAttempted) return;
        tileSheetLoadAttempted = true;
        try {
            Global.getSettings().loadTexture(TileManifest.SHEET);
            tileSheet = Global.getSettings().getSprite(TileManifest.SHEET);
            if (tileSheet == null) {
                LOG.warn("BattleScreen: getSprite returned null for " + TileManifest.SHEET);
                return;
            }
            try (java.io.InputStream stream = Global.getSettings().openStream(TileManifest.SHEET)) {
                BufferedImage img = ImageIO.read(stream);
                if (img == null) {
                    LOG.warn("BattleScreen: ImageIO.read returned null for " + TileManifest.SHEET);
                    tileSheet = null;
                    return;
                }
                tileSheetPxW = img.getWidth();
                tileSheetPxH = img.getHeight();
                // Tile sheet covers floors + walls + DOOR_OPEN overlays + doodads —
                // worst case is ~map cells, so size for a generous 128×128 grid.
                urbanBatch = new QuadBatch(tileSheet, tileSheetPxW, tileSheetPxH, 16384);
                LOG.info("BattleScreen: loaded tileset " + TileManifest.SHEET
                        + " (" + tileSheetPxW + "x" + tileSheetPxH + ")");
            }
        } catch (Exception e) {
            LOG.error("BattleScreen: failed to load tileset " + TileManifest.SHEET, e);
            tileSheet = null;
        }
    }

    /** Same lazy-load pattern as {@link #ensureTileSheet} — the road sheet is its own PNG so updates don't churn the indoor floor cache. */
    private void ensureRoadSheet() {
        if (roadSheetLoadAttempted) return;
        roadSheetLoadAttempted = true;
        try {
            Global.getSettings().loadTexture(TileManifest.ROAD_SHEET);
            roadSheet = Global.getSettings().getSprite(TileManifest.ROAD_SHEET);
            if (roadSheet == null) {
                LOG.warn("BattleScreen: getSprite returned null for " + TileManifest.ROAD_SHEET);
                return;
            }
            try (java.io.InputStream stream = Global.getSettings().openStream(TileManifest.ROAD_SHEET)) {
                BufferedImage img = ImageIO.read(stream);
                if (img == null) {
                    LOG.warn("BattleScreen: ImageIO.read returned null for " + TileManifest.ROAD_SHEET);
                    roadSheet = null;
                    return;
                }
                roadSheetPxW = img.getWidth();
                roadSheetPxH = img.getHeight();
                // Road sheet covers street/sidewalk/courtyard/striped + road-sheet doodads.
                // Less coverage than the urban sheet but still cell-count-bounded; 4096 fits ~64×64.
                roadBatch = new QuadBatch(roadSheet, roadSheetPxW, roadSheetPxH, 4096);
                LOG.info("BattleScreen: loaded road tileset " + TileManifest.ROAD_SHEET
                        + " (" + roadSheetPxW + "x" + roadSheetPxH + ")");
            }
        } catch (Exception e) {
            LOG.error("BattleScreen: failed to load road tileset " + TileManifest.ROAD_SHEET, e);
            roadSheet = null;
        }
    }

    /**
     * Lazy-loads the outdoor surfaces sheet (Floors_Tiles.png). 16px source
     * cells — the renderer upscales 2x to fit the 32px nav grid. Same pattern
     * as {@link #ensureRoadSheet}.
     */
    private void ensureFloorsSheet() {
        if (floorsSheetLoadAttempted) return;
        floorsSheetLoadAttempted = true;
        try {
            Global.getSettings().loadTexture(TileManifest.FLOORS_SHEET);
            floorsSheet = Global.getSettings().getSprite(TileManifest.FLOORS_SHEET);
            if (floorsSheet == null) {
                LOG.warn("BattleScreen: getSprite returned null for " + TileManifest.FLOORS_SHEET);
                return;
            }
            try (java.io.InputStream stream = Global.getSettings().openStream(TileManifest.FLOORS_SHEET)) {
                BufferedImage img = ImageIO.read(stream);
                if (img == null) {
                    LOG.warn("BattleScreen: ImageIO.read returned null for " + TileManifest.FLOORS_SHEET);
                    floorsSheet = null;
                    return;
                }
                floorsSheetPxW = img.getWidth();
                floorsSheetPxH = img.getHeight();
                floorsBatch = new QuadBatch(floorsSheet, floorsSheetPxW, floorsSheetPxH, 4096);
                LOG.info("BattleScreen: loaded floors tileset " + TileManifest.FLOORS_SHEET
                        + " (" + floorsSheetPxW + "x" + floorsSheetPxH + ")");
            }
        } catch (Exception e) {
            LOG.error("BattleScreen: failed to load floors tileset " + TileManifest.FLOORS_SHEET, e);
            floorsSheet = null;
        }
    }

    /** Lazy-loads the Water_tiles sheet. Same setup as {@link #ensureFloorsSheet}. */
    private void ensureWaterSheet() {
        if (waterSheetLoadAttempted) return;
        waterSheetLoadAttempted = true;
        try {
            Global.getSettings().loadTexture(TileManifest.WATER_SHEET);
            waterSheet = Global.getSettings().getSprite(TileManifest.WATER_SHEET);
            if (waterSheet == null) {
                LOG.warn("BattleScreen: getSprite returned null for " + TileManifest.WATER_SHEET);
                return;
            }
            try (java.io.InputStream stream = Global.getSettings().openStream(TileManifest.WATER_SHEET)) {
                BufferedImage img = ImageIO.read(stream);
                if (img == null) {
                    LOG.warn("BattleScreen: ImageIO.read returned null for " + TileManifest.WATER_SHEET);
                    waterSheet = null;
                    return;
                }
                waterSheetPxW = img.getWidth();
                waterSheetPxH = img.getHeight();
                waterBatch = new QuadBatch(waterSheet, waterSheetPxW, waterSheetPxH, 2048);
                LOG.info("BattleScreen: loaded water tileset " + TileManifest.WATER_SHEET
                        + " (" + waterSheetPxW + "x" + waterSheetPxH + ")");
            }
        } catch (Exception e) {
            LOG.error("BattleScreen: failed to load water tileset " + TileManifest.WATER_SHEET, e);
            waterSheet = null;
        }
    }

    /**
     * Lazy-loads the nature-tiles sheet — sliced strip with grass / dirt /
     * sand / water ground variants plus overlay frames. Same shape as
     * {@link #ensureUrbanTile3Sheet}. On success the slicer frames live in
     * {@link #natureFrames} and the per-sheet batch is built so GRASS and
     * DIRT cells can dispatch through it.
     */
    private void ensureNatureSheet() {
        if (natureSheetLoadAttempted) return;
        natureSheetLoadAttempted = true;
        try {
            Global.getSettings().loadTexture(com.dillon.starsectormarines.battle.sprites.NatureTileset.SHEET_PATH);
            natureSheet = Global.getSettings().getSprite(com.dillon.starsectormarines.battle.sprites.NatureTileset.SHEET_PATH);
            if (natureSheet == null) {
                LOG.warn("BattleScreen: getSprite returned null for "
                        + com.dillon.starsectormarines.battle.sprites.NatureTileset.SHEET_PATH);
                return;
            }
            try (java.io.InputStream stream = Global.getSettings().openStream(
                    com.dillon.starsectormarines.battle.sprites.NatureTileset.SHEET_PATH)) {
                BufferedImage img = ImageIO.read(stream);
                if (img == null) {
                    LOG.warn("BattleScreen: ImageIO.read returned null for "
                            + com.dillon.starsectormarines.battle.sprites.NatureTileset.SHEET_PATH);
                    natureSheet = null;
                    return;
                }
                natureSheetPxW = img.getWidth();
                natureSheetPxH = img.getHeight();
                natureFrames = SpriteSheetSlicer.slice(img);
                int expected = com.dillon.starsectormarines.battle.sprites.NatureTile.values().length;
                if (natureFrames.frames.length != expected) {
                    LOG.warn("BattleScreen: nature-tiles slicer returned "
                            + natureFrames.frames.length + " frames but NatureTile expects "
                            + expected + " — falling back to legacy Floors_Tiles grass/dirt");
                    natureSheet = null;
                    natureFrames = null;
                    return;
                }
                natureBatch = new QuadBatch(natureSheet, natureSheetPxW, natureSheetPxH, 4096);
                LOG.info("BattleScreen: loaded nature-tiles "
                        + com.dillon.starsectormarines.battle.sprites.NatureTileset.SHEET_PATH
                        + " (" + natureSheetPxW + "x" + natureSheetPxH + "), "
                        + natureFrames.frames.length + " frames sliced");
            }
        } catch (Exception e) {
            LOG.error("BattleScreen: failed to load nature-tiles "
                    + com.dillon.starsectormarines.battle.sprites.NatureTileset.SHEET_PATH, e);
            natureSheet = null;
            natureFrames = null;
        }
    }

    /**
     * Lazy-loads the urban-tileset-3 sheet — sliced strip with variable-width
     * cells separated by alpha gutters, so the frame bboxes are computed by
     * {@link SpriteSheetSlicer} rather than read off a fixed grid. Same
     * lazy-load shape as {@link #ensureRoadSheet}; on success the slicer
     * frames live in {@link #urbanTile3Frames} and the per-sheet batch is
     * built so STREET cells can dispatch through it.
     */
    private void ensureUrbanTile3Sheet() {
        if (urbanTile3SheetLoadAttempted) return;
        urbanTile3SheetLoadAttempted = true;
        try {
            Global.getSettings().loadTexture(TileManifest.STREET3_SHEET);
            urbanTile3Sheet = Global.getSettings().getSprite(TileManifest.STREET3_SHEET);
            if (urbanTile3Sheet == null) {
                LOG.warn("BattleScreen: getSprite returned null for " + TileManifest.STREET3_SHEET);
                return;
            }
            try (java.io.InputStream stream = Global.getSettings().openStream(TileManifest.STREET3_SHEET)) {
                BufferedImage img = ImageIO.read(stream);
                if (img == null) {
                    LOG.warn("BattleScreen: ImageIO.read returned null for " + TileManifest.STREET3_SHEET);
                    urbanTile3Sheet = null;
                    return;
                }
                urbanTile3SheetPxW = img.getWidth();
                urbanTile3SheetPxH = img.getHeight();
                urbanTile3Frames = SpriteSheetSlicer.slice(img);
                int expected = UrbanTile3.values().length;
                if (urbanTile3Frames.frames.length != expected) {
                    LOG.warn("BattleScreen: urban-tileset-3 slicer returned "
                            + urbanTile3Frames.frames.length + " frames but UrbanTile3 expects "
                            + expected + " — falling back to legacy road autotile");
                    urbanTile3Sheet = null;
                    urbanTile3Frames = null;
                    return;
                }
                urbanTile3Batch = new QuadBatch(urbanTile3Sheet, urbanTile3SheetPxW, urbanTile3SheetPxH, 4096);
                LOG.info("BattleScreen: loaded urban-tileset-3 " + TileManifest.STREET3_SHEET
                        + " (" + urbanTile3SheetPxW + "x" + urbanTile3SheetPxH + "), "
                        + urbanTile3Frames.frames.length + " frames sliced");
            }
        } catch (Exception e) {
            LOG.error("BattleScreen: failed to load urban-tileset-3 " + TileManifest.STREET3_SHEET, e);
            urbanTile3Sheet = null;
            urbanTile3Frames = null;
        }
    }

    /**
     * Lazy-loads each {@link ShuttleType}'s sprite via the vanilla path lookup.
     * Captures the natural aspect ratio before any setSize call mutates it, so
     * render math can preserve the ship's drawn proportions across cellSize
     * changes. Same lazy-load pattern as the marine sheet — getSprite returns
     * a wrapper whose backing texture is null until loadTexture is called.
     */
    /**
     * Lazy-loads the vanilla engine flame + glow textures. Same one-shot
     * pattern as {@link #ensureShuttleSprites()}: try to load each path
     * once, log + degrade gracefully if either fails. A missing engine
     * sprite just means the engine pass renders nothing — no crash.
     */
    private void ensureEngineFxSprites() {
        if (engineFxSpritesLoadAttempted) return;
        engineFxSpritesLoadAttempted = true;
        engineFlameSprite = loadEngineFxSpriteOrNull(ENGINE_FLAME_SPRITE);
        engineGlowSprite  = loadEngineFxSpriteOrNull(ENGINE_GLOW_SPRITE);
    }

    private SpriteAPI loadEngineFxSpriteOrNull(String path) {
        try {
            Global.getSettings().loadTexture(path);
            SpriteAPI s = Global.getSettings().getSprite(path);
            if (s == null) {
                LOG.warn("BattleScreen: getSprite returned null for " + path);
            }
            return s;
        } catch (Exception e) {
            LOG.error("BattleScreen: failed to load engine FX sprite " + path, e);
            return null;
        }
    }

    private void ensureShuttleSprites() {
        if (shuttleSpritesLoadAttempted) return;
        shuttleSpritesLoadAttempted = true;
        for (ShuttleType type : ShuttleType.values()) {
            try {
                Global.getSettings().loadTexture(type.spritePath);
                SpriteAPI sprite = Global.getSettings().getSprite(type.spritePath);
                if (sprite == null) {
                    LOG.warn("BattleScreen: getSprite returned null for " + type.spritePath);
                    continue;
                }
                float w = sprite.getWidth();
                float h = sprite.getHeight();
                float aspect = (h > 0f) ? w / h : 1f;
                shuttleSprites.put(type, new ShuttleSpriteCache(sprite, aspect));
                LOG.info("BattleScreen: loaded shuttle " + type.spritePath
                        + " (" + w + "x" + h + ", aspect=" + aspect + ")");
            } catch (Exception e) {
                LOG.error("BattleScreen: failed to load shuttle sprite " + type.spritePath, e);
            }
        }
    }

    /**
     * Lazy-loads the marine sprite sheet on first attach and auto-slices it
     * into per-frame bounding boxes. {@code getSprite} alone returns a wrapper
     * whose backing texture is null until {@code loadTexture} is called — same
     * pattern BitmapFont uses for font pages. Survives across multiple attach
     * calls via the cached fields.
     *
     * <p>{@link SpriteSheetSlicer} runs on the raw PNG bytes via
     * {@code openStream + ImageIO} — it handles both clean uniform-grid sheets
     * (the reference marine sprite) and AI-generated sheets with irregular
     * sprite spacing, so we don't have to keep the source art on a strict grid.
     */
    /**
     * Lazy-loads the SABOTAGE objective marker icons. Each PNG is a 512px white
     * shape on transparent — tinted at draw time via {@code setColor}. Same
     * sprite-lazy-load gotcha as the tileset: {@code getSprite} returns a wrapper
     * whose backing texture is null until {@code loadTexture} is called.
     */
    private void ensureObjectiveIcons() {
        if (iconsLoadAttempted) return;
        iconsLoadAttempted = true;
        iconAlarm  = loadIconOrNull(ICON_ALARM);
        iconDanger = loadIconOrNull(ICON_DANGER);
        iconStar   = loadIconOrNull(ICON_STAR);
    }

    private SpriteAPI loadIconOrNull(String path) {
        try {
            Global.getSettings().loadTexture(path);
            SpriteAPI sprite = Global.getSettings().getSprite(path);
            if (sprite == null) {
                LOG.warn("BattleScreen: getSprite returned null for " + path);
                return null;
            }
            LOG.info("BattleScreen: loaded icon " + path);
            return sprite;
        } catch (Exception e) {
            LOG.error("BattleScreen: failed to load icon " + path, e);
            return null;
        }
    }

    /**
     * Loads every {@link UnitType} sprite sheet on first call and auto-slices
     * each into per-frame bounding boxes. A type whose load fails is recorded
     * with a null entry so its units fall back to the color-quad path without
     * retrying every frame.
     */
    private void ensureUnitSheets() {
        if (unitSpritesLoadAttempted) return;
        unitSpritesLoadAttempted = true;
        for (UnitType type : UnitType.values()) {
            // TURRET sprite is per-instance via MapTurret.kind, not per-type —
            // its spritePath is intentionally empty so we skip the load here.
            if (type == UnitType.TURRET) continue;
            unitSprites.put(type, loadUnitSheet(type.spritePath));
            if (type.deadSpritePath != null) {
                UnitSpriteCache dead = loadUnitSheet(type.deadSpritePath);
                if (dead != null) unitDeadSprites.put(type, dead);
            }
        }
    }

    private void ensureVehicleSheets() {
        if (vehicleSheetsLoadAttempted) return;
        vehicleSheetsLoadAttempted = true;
        for (VehicleKind.VehicleSheet sheet : VehicleKind.VehicleSheet.values()) {
            vehicleSheets.put(sheet, loadUnitSheet(sheet.path));
        }
    }

    /**
     * Lazy-loads each {@link TurretKind}'s vanilla weapon sprite. Same wrapper-
     * after-loadTexture gotcha as every other sheet, plus aspect capture before
     * {@code setSize} clobbers {@code getWidth/getHeight}. Sprites live under
     * {@code graphics/weapons/} in the Starsector install; Starsector's
     * resource loader resolves them transparently — we don't ship copies in
     * the mod folder.
     */
    /**
     * Renders every persistent decal (bullet holes, craters, rubble) as a
     * rotated quad slice of the shared sheet. Skipped entirely when the sheet
     * failed to load. Normal-alpha blend — decals occlude rather than glow.
     */
    private void renderDecals(BattleSimulation sim, float alphaMult) {
        if (decalSheet == null || decalFrames == null) return;
        decalAccumulator.render(
                camera,
                sim.getGrid().getWidth(), sim.getGrid().getHeight(),
                sim.getDecals(),
                decalSheet, decalFrames,
                alphaMult);
    }

    /** Lazy-loads the decal sheet and auto-slices it into per-frame bounding boxes. Failure leaves both fields null and the decal pass becomes a no-op. */
    private void ensureDecalSheet() {
        if (decalSheetLoadAttempted) return;
        decalSheetLoadAttempted = true;
        try {
            Global.getSettings().loadTexture(SPRITE_DECAL_SHEET);
            decalSheet = Global.getSettings().getSprite(SPRITE_DECAL_SHEET);
            if (decalSheet == null) {
                LOG.warn("BattleScreen: getSprite returned null for " + SPRITE_DECAL_SHEET);
                return;
            }
            try (java.io.InputStream stream = Global.getSettings().openStream(SPRITE_DECAL_SHEET)) {
                BufferedImage img = ImageIO.read(stream);
                if (img == null) {
                    LOG.warn("BattleScreen: ImageIO.read returned null for " + SPRITE_DECAL_SHEET);
                    decalSheet = null;
                    return;
                }
                decalFrames = SpriteSheetSlicer.slice(img);
                LOG.info("BattleScreen: auto-sliced " + SPRITE_DECAL_SHEET
                        + " — " + decalFrames.frames.length + " frames");
            }
        } catch (Exception e) {
            LOG.error("BattleScreen: failed to load decal sheet " + SPRITE_DECAL_SHEET, e);
            decalSheet = null;
        }
    }

    /** Lazy-loads each marine secondary's projectile sprite AND the marine-pose sheet shown during the weapon's aim cycle. The projectile is a single sprite rotated at draw time; the aim sheet is auto-sliced into 7 frames matching the regular marine convention. */
    private void ensureMarineSecondarySprites() {
        if (marineSecondarySpritesLoadAttempted) return;
        marineSecondarySpritesLoadAttempted = true;
        for (MarineSecondary sec : MarineSecondary.values()) {
            try {
                Global.getSettings().loadTexture(sec.projectileSpritePath);
                SpriteAPI sprite = Global.getSettings().getSprite(sec.projectileSpritePath);
                if (sprite == null) {
                    LOG.warn("BattleScreen: getSprite returned null for " + sec.projectileSpritePath);
                } else {
                    float w = sprite.getWidth();
                    float h = sprite.getHeight();
                    float aspect = (h > 0f) ? w / h : 1f;
                    marineSecondarySprites.put(sec, new ShuttleSpriteCache(sprite, aspect));
                    LOG.info("BattleScreen: loaded " + sec.projectileSpritePath
                            + " (" + w + "x" + h + ", aspect=" + aspect + ")");
                }
            } catch (Exception e) {
                LOG.error("BattleScreen: failed to load secondary projectile " + sec.projectileSpritePath, e);
            }
            if (sec.aimSpritePath != null) {
                UnitSpriteCache aim = loadUnitSheet(sec.aimSpritePath);
                if (aim != null) marineSecondaryAimSheets.put(sec, aim);
            }
        }
        // Primary projectile sprites (SMG bullet today). Skip weapons whose
        // projectile path is null — those fire as line tracers.
        for (MarineWeapon w : MarineWeapon.values()) {
            if (w.projectileSpritePath == null) continue;
            try {
                Global.getSettings().loadTexture(w.projectileSpritePath);
                SpriteAPI sprite = Global.getSettings().getSprite(w.projectileSpritePath);
                if (sprite == null) {
                    LOG.warn("BattleScreen: getSprite returned null for " + w.projectileSpritePath);
                    continue;
                }
                float pw = sprite.getWidth();
                float ph = sprite.getHeight();
                float aspect = (ph > 0f) ? pw / ph : 1f;
                marineWeaponProjectileSprites.put(w, new ShuttleSpriteCache(sprite, aspect));
                LOG.info("BattleScreen: loaded " + w.projectileSpritePath
                        + " (" + pw + "x" + ph + ", aspect=" + aspect + ")");
            } catch (Exception e) {
                LOG.error("BattleScreen: failed to load primary projectile " + w.projectileSpritePath, e);
            }
        }
        // Mech chassis projectile sprites — every entry has one (chaingun
        // shell / SRM / LRM). Same load + aspect-capture pattern as the marine
        // primaries above.
        for (com.dillon.starsectormarines.battle.MechWeapon w
                : com.dillon.starsectormarines.battle.MechWeapon.values()) {
            if (w.projectileSpritePath == null) continue;
            try {
                Global.getSettings().loadTexture(w.projectileSpritePath);
                SpriteAPI sprite = Global.getSettings().getSprite(w.projectileSpritePath);
                if (sprite == null) {
                    LOG.warn("BattleScreen: getSprite returned null for " + w.projectileSpritePath);
                    continue;
                }
                float pw = sprite.getWidth();
                float ph = sprite.getHeight();
                float aspect = (ph > 0f) ? pw / ph : 1f;
                mechWeaponProjectileSprites.put(w, new ShuttleSpriteCache(sprite, aspect));
                LOG.info("BattleScreen: loaded mech projectile " + w.projectileSpritePath
                        + " (" + pw + "x" + ph + ", aspect=" + aspect + ")");
            } catch (Exception e) {
                LOG.error("BattleScreen: failed to load mech projectile " + w.projectileSpritePath, e);
            }
        }
    }

    private void ensureTurretSprites() {
        if (turretSpritesLoadAttempted) return;
        turretSpritesLoadAttempted = true;
        for (TurretKind kind : TurretKind.values()) {
            loadTurretSpriteInto(turretSprites,           kind, kind.spritePath);
            loadTurretSpriteInto(turretRecoilSprites,     kind, kind.recoilSpritePath);
            loadTurretSpriteInto(turretProjectileSprites, kind, kind.projectileSpritePath);
        }
    }

    /** Shared loader for the three turret-related sprite caches. Captures the native aspect before any {@code setSize} call clobbers {@code getWidth/getHeight}. */
    private void loadTurretSpriteInto(java.util.EnumMap<TurretKind, ShuttleSpriteCache> cache,
                                      TurretKind kind, String path) {
        try {
            Global.getSettings().loadTexture(path);
            SpriteAPI sprite = Global.getSettings().getSprite(path);
            if (sprite == null) {
                LOG.warn("BattleScreen: getSprite returned null for " + path);
                return;
            }
            float w = sprite.getWidth();
            float h = sprite.getHeight();
            float aspect = (h > 0f) ? w / h : 1f;
            cache.put(kind, new ShuttleSpriteCache(sprite, aspect));
            LOG.info("BattleScreen: loaded " + path + " (" + w + "x" + h + ", aspect=" + aspect + ")");
        } catch (Exception e) {
            LOG.error("BattleScreen: failed to load " + path, e);
        }
    }

    private UnitSpriteCache loadUnitSheet(String path) {
        try {
            Global.getSettings().loadTexture(path);
            SpriteAPI sprite = Global.getSettings().getSprite(path);
            if (sprite == null) {
                LOG.warn("BattleScreen: getSprite returned null for " + path);
                return null;
            }
            try (java.io.InputStream stream = Global.getSettings().openStream(path)) {
                BufferedImage img = ImageIO.read(stream);
                if (img == null) {
                    LOG.warn("BattleScreen: ImageIO.read returned null for " + path);
                    return null;
                }
                SpriteSheetFrames frames = SpriteSheetSlicer.slice(img);
                LOG.info("BattleScreen: auto-sliced " + path + " — " + frames.frames.length + " frames detected");
                return new UnitSpriteCache(sprite, frames);
            }
        } catch (Exception e) {
            LOG.error("BattleScreen: failed to load unit sheet " + path, e);
            return null;
        }
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
        // Always tick — dt=0 makes the sim a no-op but still clears the per-frame event lists,
        // so a paused caller doesn't keep replaying the previous frame's shot/death sounds.
        sim.advance(dt * speedMultiplier);
        // Flyby fighters run on the same scaled clock as the sim so pause / 1x / 2x / 4x
        // applies uniformly — spawning, strafing, and dogfighting all freeze on pause.
        flybyOverlay.advance(dt * speedMultiplier, sim, camera);
        // Impact FX: spawn at the moment the shot's visual reaches its endpoint
        // (instant for marine line tracers, on lifetime expiry for projectile
        // sprites), then advance particles on the same scaled clock.
        spawnImpactFx(sim);
        // Drain wreck smoke puffs the sim queued this tick — each entry is
        // {x, y, radiusCells}. Same scaled clock means wrecks stop smoking
        // when the sim pauses.
        for (float[] puff : sim.getSmokePuffsThisFrame()) {
            impactFx.spawnAmbientSmoke(puff[0], puff[1], puff[2]);
        }
        for (float[] burst : sim.getFireBurstsThisFrame()) {
            impactFx.spawnAmbientFire(burst[0], burst[1], burst[2]);
        }
        impactFx.advance(dt * speedMultiplier);
        // Light pass — tick transient lights and re-assert persistent lights
        // for every emitter that lives across frames (burning wrecks, air
        // vehicle engines). All persistent ids accumulate into seenLightIds;
        // retainPersistent at the end evicts anything that disappeared this
        // tick.
        lightAccumulator.advance(dt * speedMultiplier);
        java.util.HashSet<Long> seenLightIds = new java.util.HashSet<>();
        for (SmokingWreck w : sim.getSmokingWrecks()) {
            float age = w.totalLifetime - w.remainingLifetime;
            if (age >= BattleSimulation.WRECK_BURN_DURATION) continue;
            float burnRemaining = BattleSimulation.WRECK_BURN_DURATION - age;
            float intensity = (burnRemaining < BattleSimulation.WRECK_FIRE_FADE_DURATION)
                    ? burnRemaining / BattleSimulation.WRECK_FIRE_FADE_DURATION
                    : 1f;
            long id = ((long) w.cellX << 32) | (w.cellY & 0xffffffffL);
            seenLightIds.add(id);
            lightAccumulator.putPersistent(id, w.cellX + 0.5f, w.cellY + 0.5f,
                    2.8f, LightKernel.WRECK_FIRE,
                    1.0f, 0.55f, 0.20f, intensity);
        }
        // Engine glow halos per live shuttle/valk. Lights track the world
        // position (no altitude offset), so cruise altitude doesn't lift the
        // halo off the ground — the shuttle stays a flying spotlight.
        for (com.dillon.starsectormarines.battle.air.Shuttle s : sim.getShuttles()) {
            if (s.state == com.dillon.starsectormarines.battle.air.Shuttle.State.PENDING
                    || s.state == com.dillon.starsectormarines.battle.air.Shuttle.State.GONE) continue;
            com.dillon.starsectormarines.battle.air.engine.EngineFxRenderer.emitLights(
                    com.dillon.starsectormarines.battle.air.engine.EngineSlotResolver.resolve(s.type),
                    s.body.x, s.body.y,
                    s.body.facingDegrees,
                    s.scaleMult,
                    s.visualAltitudeOffsetCells(),
                    s.engineFxIntensity(),
                    lightAccumulator,
                    ((long) System.identityHashCode(s)) << 16,
                    seenLightIds);
        }
        // Fighter engine halos — FlybyOverlay owns the fighter list, so it
        // pumps directly into our seen-id set.
        flybyOverlay.pumpEngineLights(seenLightIds);
        lightAccumulator.retainPersistent(seenLightIds);
        // Roof alpha lerp runs on real dt (not sim-scaled) so the fog-of-war
        // fade keeps animating even when the sim is paused — matches how the
        // HUD ticks on real dt for the same reason.
        advanceRoofAlphaLerp(sim, dt);
        driveShuttleEngineLoop(sim);
        driveShuttleResonanceLoops(sim);
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
        decalAccumulator.dispose();
        lightAccumulator.dispose();

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
        hud.addPanel(new SquadOverviewPanel(this));
        hud.addPanel(new SquadDetailPanel(this));
        // Per-squad GOAP plan readout. Compact when nothing is selected; full
        // plan + predicate grid when WorldPicker (or the Overview rows) put a
        // squad id into Selection.
        hud.addPanel(new SquadPlanDebugPanel(this));
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
     * Drives the shared shuttle engine loop from the loudest visible shuttle each frame.
     * playUILoop has no per-entity identifier (unlike the positional playLoop), so three
     * shuttles cruising in at once share a single loop voice — taking the max means N
     * landing shuttles don't N-times the volume. When no shuttles are visible, we skip
     * the call entirely and the loop self-fades over Starsector's default UI-loop hold.
     *
     * <p>Pitch sweeps {@link #ENGINE_PITCH_IDLE} → {@link #ENGINE_PITCH_CRUISE} across the
     * intensity range; volume is the intensity itself (multiplied by the per-clip base in
     * sounds.json, so on-ground idle still reads as audible but not pushy).
     */
    private void driveShuttleEngineLoop(BattleSimulation sim) {
        float maxIntensity = 0f;
        for (Shuttle s : sim.getShuttles()) {
            if (!s.isVisible()) continue;
            float i = s.engineIntensity();
            if (i > maxIntensity) maxIntensity = i;
        }
        if (maxIntensity <= 0f) return;
        float pitch = ENGINE_PITCH_IDLE + (ENGINE_PITCH_CRUISE - ENGINE_PITCH_IDLE) * maxIntensity;
        Global.getSoundPlayer().playUILoop(LOOP_ENGINE, pitch, maxIntensity);
    }

    /**
     * Per-shuttle positional resonance loops — every visible shuttle emits one of three
     * {@code marines_ambient_fan_reso_*} clips at its world position, so the airy fan/reso
     * texture follows the actual vehicle rather than sitting in a fixed ambient bed anchor.
     *
     * <p>Assignment is by {@link System#identityHashCode(Object)} mod 3 — stable for the
     * shuttle's lifetime, and we don't care about save/load consistency (audio is not
     * persisted state). The {@code playingEntity} key is the shuttle itself, which keeps
     * three simultaneous shuttles on three voices even when they share the same clip id.
     *
     * <p>Pitch carries a small per-shuttle deterministic offset so two shuttles drawing the
     * same reso clip don't run in phase lock; volume scales by {@link Shuttle#engineIntensity()}
     * so off-screen / on-ground reads quieter. Velocity is fed through so OpenAL applies
     * Doppler as a shuttle banks over the camera.
     */
    private void driveShuttleResonanceLoops(BattleSimulation sim) {
        for (Shuttle s : sim.getShuttles()) {
            if (!s.isVisible()) continue;
            float intensity = s.engineIntensity();
            if (intensity <= 0f) continue;
            int idHash = System.identityHashCode(s);
            int resoIdx = (idHash & 0x7fffffff) % VEHICLE_ENGINE_LOOPS.length;
            String loopId = VEHICLE_ENGINE_LOOPS[resoIdx];
            // Deterministic ±jitter from the hash so the offset doesn't change frame-to-frame.
            float pitchOffset = (((idHash >> 8) & 0xff) / 255f * 2f - 1f) * SHUTTLE_RESONANCE_PITCH_JITTER;
            Vector2f loc = new Vector2f(s.body.x * AUDIO_WORLD_UNITS_PER_CELL,
                                        s.body.y * AUDIO_WORLD_UNITS_PER_CELL);
            Vector2f vel = shuttleVelocity(s);
            Global.getSoundPlayer().playLoop(loopId, s, 1f + pitchOffset,
                    SHUTTLE_RESONANCE_VOLUME * intensity, loc, vel);
        }
    }

    /** Per-frame velocity for {@link #driveShuttleResonanceLoops} Doppler — reads the AirBody directly. Returns zero on the ground / off-screen so audio stays parked. */
    private static Vector2f shuttleVelocity(Shuttle s) {
        if (s.state != Shuttle.State.INCOMING && s.state != Shuttle.State.DEPARTING) {
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
            if (s.mechWeapon == com.dillon.starsectormarines.battle.MechWeapon.CHAINGUN) {
                impactFx.spawnMuzzleFlash(s.fromX, s.fromY, 0.55f, 0.08f);
            }
            // Light emission — turret / mech chaingun / marine small arms /
            // generic infantry line tracer, all dispatched in one place.
            boolean projectile = hasProjectileSprite(s);
            WeaponLights.shotMuzzleFlash(lightAccumulator, s, projectile);
            if (projectile) continue;
            boolean isWall = isWallAt(grid, s.toX, s.toY);
            ImpactProfile profile = (s.marineWeapon != null)
                    ? s.marineWeapon.impactProfile : ImpactProfile.RIFLE;
            impactFx.spawnImpact(profile, s.toX, s.toY, isWall);
            WeaponLights.impactBurst(lightAccumulator, profile, s.toX, s.toY);
            ImpactDecals.spawnImpact(sim, rng, profile, s.toX, s.toY, isWall);
            // Line-tracer beam path — stamp the tracer color along the
            // shot line so the multiply pass doesn't darken pulse-rifle /
            // railgun / militia tracer beams. Marines use their per-weapon
            // tracerColor; other factions fall back to the same palette
            // renderShots reads.
            Color tracerColor = (s.marineWeapon != null)
                    ? s.marineWeapon.tracerColor
                    : (s.shooterFaction == Faction.MARINE ? MARINE_TRACER : DEFENDER_TRACER);
            WeaponLights.laserPath(lightAccumulator, s.fromX, s.fromY, s.toX, s.toY, tracerColor);
        }
        for (ShotEvent s : sim.getShotsExpiredThisFrame()) {
            if (!hasProjectileSprite(s)) continue;
            boolean isWall = isWallAt(grid, s.toX, s.toY);
            ImpactProfile profile;
            if (s.turretKind != null) {
                profile = s.turretKind.impactProfile();
                impactFx.spawnImpact(profile, s.toX, s.toY, isWall);
                WeaponLights.impactBurst(lightAccumulator, profile, s.toX, s.toY);
                if (s.turretKind == com.dillon.starsectormarines.battle.TurretKind.HEAVY_MORTAR) {
                    float pitch = 0.9f + rng.nextFloat() * 0.2f;
                    Vector2f loc = new Vector2f(
                            s.toX * AUDIO_WORLD_UNITS_PER_CELL,
                            s.toY * AUDIO_WORLD_UNITS_PER_CELL);
                    Global.getSoundPlayer().playSound(SFX_NEAR_EXPLOSION, pitch, 0.55f, loc, zeroVel);
                }
            } else if (s.marineSecondary != null) {
                profile = s.marineSecondary.impactProfile();
                impactFx.spawnImpact(profile, s.toX, s.toY, isWall);
                WeaponLights.impactBurst(lightAccumulator, profile, s.toX, s.toY);
                float pitch = 0.9f + rng.nextFloat() * 0.2f;
                Vector2f loc = new Vector2f(
                        s.toX * AUDIO_WORLD_UNITS_PER_CELL,
                        s.toY * AUDIO_WORLD_UNITS_PER_CELL);
                Global.getSoundPlayer().playSound(s.marineSecondary.impactSoundId, pitch, 0.70f, loc, zeroVel);
            } else if (s.marineWeapon != null) {
                profile = s.marineWeapon.impactProfile;
                impactFx.spawnImpact(profile, s.toX, s.toY, isWall);
                WeaponLights.impactBurst(lightAccumulator, profile, s.toX, s.toY);
            } else if (s.mechWeapon != null) {
                // Mech rounds — HE entries (SRM, LRM) also play the explosion
                // clip on arrival; chainguns are kinetic, no extra audio (the
                // burst itself is loud enough at fire time).
                profile = s.mechWeapon.impactProfile;
                impactFx.spawnImpact(profile, s.toX, s.toY, isWall);
                WeaponLights.impactBurst(lightAccumulator, profile, s.toX, s.toY);
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
        for (Unit u : sim.getDeathsThisFrame()) {
            if (u.faction == Faction.MARINE) {
                Vector2f loc = new Vector2f(
                        u.renderX * AUDIO_WORLD_UNITS_PER_CELL,
                        u.renderY * AUDIO_WORLD_UNITS_PER_CELL);
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
    private void handleDebugZoneToggle(List<InputEventAPI> events) {
        if (events == null) return;
        for (InputEventAPI e : events) {
            if (e.isConsumed()) continue;
            if (e.isKeyDownEvent() && e.getEventValue() == org.lwjgl.input.Keyboard.KEY_Z) {
                debugZonesVisible = !debugZonesVisible;
                e.consume();
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
            java.nio.IntBuffer scissorBuf = org.lwjgl.BufferUtils.createIntBuffer(16);
            org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_SCISSOR_BOX, scissorBuf);
            int dialogFbX = scissorBuf.get(0);
            int dialogFbY = scissorBuf.get(1);
            int dialogFbW = scissorBuf.get(2);
            int dialogFbH = scissorBuf.get(3);
            float uiDialogW = Math.max(0.001f, position.getWidth());
            float uiDialogH = Math.max(0.001f, position.getHeight());
            float sx = dialogFbW / uiDialogW;
            float sy = dialogFbH / uiDialogH;
            int gridFbX = dialogFbX + Math.round((layout.gridX - position.getX()) * sx);
            int gridFbY = dialogFbY + Math.round((layout.gridY - position.getY()) * sy);
            int gridFbW = Math.round(layout.gridW * sx);
            int gridFbH = Math.round(layout.gridH * sy);
            glEnable(GL_SCISSOR_TEST);
            glScissor(gridFbX, gridFbY, gridFbW, gridFbH);
            renderGrid(sim.getGrid(), sim.getTopology(), alphaMult);
            if (debugZonesVisible) renderZoneOverlay(sim, alphaMult);
            // Decals sit between the floor pass and vehicles so parked trucks
            // (and later units) draw on top of bullet holes and craters.
            renderDecals(sim, alphaMult);
            renderVehicles(sim, alphaMult);
            renderDoodads(sim, alphaMult);
            // Debug cell highlights — published by HUD panels (plan-step cells,
            // selected squad members, captain). Paints above ground decals/
            // doodads, below units so unit sprites stay legible over the tint.
            highlights.render(camera, alphaMult);
            renderUnits(sim.getUnits(), alphaMult);
            // Fog-of-war roof pass — paints opaque BRICK tiles over the
            // interiors of buildings the player can't see, hiding any units
            // (and decals / doodads) inside. Sits above units but below
            // objective markers, shuttles (aircraft), projectiles, and flyby
            // — all of which should pierce the roof.
            renderRoofs(sim, alphaMult);
            // Charge sites + equipment drops sit above units so the player can
            // always see where the objectives are — even while a marine stands
            // on top of one. Shuttles still draw on top of the markers when
            // landing on a LZ.
            renderObjectiveMarkers(sim, alphaMult);
            renderShuttles(sim.getShuttles(), alphaMult);
            renderShots(sim.getActiveShots(), alphaMult);
            // Impact FX: sparks, dust, smoke at shot endpoints. Sits above the
            // shot tracer pass (so the impact reads as the punctuation at the
            // end of the line) but below the flyby layer (aerial FX still owns
            // the top of the stack).
            impactFx.render(camera, alphaMult);
            // Flyby layer lives above everything ground-side so strafing tracers and
            // engine glows punch over units / shots / shuttles without being occluded.
            flybyOverlay.render(camera, alphaMult);
            // Lightmap multiply — last world-layer pass so darkness covers
            // everything ground-side. Sits inside the scissor so the HUD and
            // speed marker stay full-bright. Bypassed entirely on the DAY
            // preset (the multiply would be a no-op against ambient white).
            TimeOfDay tod = timeOfDay.evaluateAt(0f);
            if (!tod.bypass) {
                lightAccumulator.render(camera, tod,
                        sim.getGrid().getWidth(), sim.getGrid().getHeight(),
                        alphaMult);
            }
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

    // ---- rendering ---------------------------------------------------------

    /**
     * Renders walkable cells as directional floor (or rubble) tiles and
     * non-walkable cells as wall autotiles, both picked from 4-neighbor
     * exposure. Floors connect their frame edges to adjacent walls; walls pick
     * the matching corner/edge piece.
     *
     * <p>For floor picking, out-of-bounds counts as <em>not</em> a wall — a
     * street at the map edge should look open, not framed. For wall picking,
     * out-of-bounds counts as a wall so a building flush against the edge
     * gets a real edge tile.
     *
     * <p>Doorway cells (perimeter cuts that were never walls) get the
     * open-door overhead stamped on top of the floor; rubble doorways
     * (breached walls) skip the overhead — those are holes blasted through,
     * not real doors.
     *
     * <p>Fully-interior wall cells (all four cardinal neighbors are walls)
     * fall back to a solid {@link #WALL_COLOR} fill — the source sheet's
     * center wall cell is transparent on purpose and there's no roof art.
     */
    private void renderTiledFloorsAndWalls(NavigationGrid grid, CellTopology topology, float alphaMult) {
        // Floor pass — five subtypes of walkable cell:
        //   1. Rubble: damaged-floor autotile from the main sheet.
        //   2. Sidewalk: street cell adjacent to a wall — clean panel from the road sheet.
        //   3. Road: street cell not adjacent to a wall — road autotile, with sidewalk
        //      neighbors treated as boundary so the dashed/red edge lights up against
        //      the sidewalk ring instead of pressing straight into the building.
        //   4. Courtyard: private interior pavement inside a super-block — dark steel
        //      autotile from the road sheet, framed against the surrounding buildings.
        //   5. Interior floor: anything else walkable (inside a building, or doorway).
        //
        // Exclusion is keyed on WALL rather than !walkable so the ground layer
        // is "everywhere except walls". Vehicles, future emplacements,
        // sandbags, etc. stamp non-walkable + their own topology tag without
        // having to opt back into the floor pass — the underlying surface
        // (street / courtyard / floor) renders here as before and the obstacle
        // sprite is drawn on top in its own pass.
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                if (topology.isWall(x, y)) continue;
                boolean nWall = isInBoundsWall(topology, x, y + 1);
                boolean sWall = isInBoundsWall(topology, x, y - 1);
                boolean eWall = isInBoundsWall(topology, x + 1, y);
                boolean wWall = isInBoundsWall(topology, x - 1, y);

                CellTopology.GroundKind kind = topology.getGroundKind(x, y);
                switch (kind) {
                    case RUBBLE: {
                        TileManifest.TileFrame f = TileManifest.pickRubbleTile(nWall, sWall, eWall, wWall);
                        drawTile(f, x, y, alphaMult, GROUND_TILE_EDGE_INSET_PX);
                        break;
                    }
                    case STREET:
                        if (urbanTile3Batch != null) {
                            // New path: urban-tileset-3 is loaded — every STREET
                            // cell renders through it. Sidewalk cells (those
                            // adjacent to a wall) pick the corner variant when
                            // two perpendicular neighbors aren't also sidewalk;
                            // straight runs get the plain slab. Road cells get
                            // a uniform paver — no autotile, no fallback fill.
                            if (isSidewalkCell(grid, topology, x, y)) {
                                // Use isSidewalkLikeCell on neighbors so a wall-
                                // adjacent STREET sidewalk that butts up against
                                // an explicit GroundKind.SIDEWALK (the trunk's
                                // 2-thick flank, e.g.) reads as one continuous
                                // strip rather than picking a corner at the seam.
                                boolean nNotSw = !isSidewalkLikeCell(grid, topology, x, y + 1);
                                boolean sNotSw = !isSidewalkLikeCell(grid, topology, x, y - 1);
                                boolean eNotSw = !isSidewalkLikeCell(grid, topology, x + 1, y);
                                boolean wNotSw = !isSidewalkLikeCell(grid, topology, x - 1, y);
                                UrbanTile3 frame = TileManifest.pickStreet3SidewalkFrame(
                                        nNotSw, sNotSw, eNotSw, wNotSw);
                                drawUrbanTile3Frame(frame, x, y, alphaMult);
                            } else {
                                drawUrbanTile3Frame(UrbanTile3.STREET_SQUARE, x, y, alphaMult);
                                if (topology.isCrosswalk(x, y)) {
                                    drawCrosswalkStripes(x, y, topology.isCrosswalkStripesHorizontal(x, y), alphaMult);
                                }
                            }
                        } else if (roadSheet != null) {
                            // Fallback path: urban-tileset-3 didn't load —
                            // use the legacy dashed-road autotile from the
                            // urban-tileset-2 sheet so STREET cells still
                            // render rather than going blank.
                            if (isSidewalkCell(grid, topology, x, y)) {
                                drawRoadTile(TileManifest.SIDEWALK, x, y, alphaMult, GROUND_TILE_EDGE_INSET_PX);
                            } else {
                                boolean nB = isRoadBoundary(grid, topology, x, y + 1);
                                boolean sB = isRoadBoundary(grid, topology, x, y - 1);
                                boolean eB = isRoadBoundary(grid, topology, x + 1, y);
                                boolean wB = isRoadBoundary(grid, topology, x - 1, y);
                                TileManifest.TileFrame f = TileManifest.pickRoadTile(nB, sB, eB, wB);
                                if (f == null) {
                                    fillCell(x, y, ROAD_FILL, alphaMult);
                                } else {
                                    drawRoadTile(f, x, y, alphaMult, GROUND_TILE_EDGE_INSET_PX);
                                }
                                if (topology.isCrosswalk(x, y)) {
                                    drawCrosswalkStripes(x, y, topology.isCrosswalkStripesHorizontal(x, y), alphaMult);
                                }
                            }
                        }
                        break;
                    case COURTYARD:
                        if (roadSheet != null) {
                            TileManifest.TileFrame f = TileManifest.pickCourtyardTile(nWall, sWall, eWall, wWall);
                            if (f == null) {
                                fillCell(x, y, COURTYARD_FILL, alphaMult);
                            } else {
                                drawRoadTile(f, x, y, alphaMult, GROUND_TILE_EDGE_INSET_PX);
                            }
                        }
                        break;
                    case GRASS:
                        drawSameKindAutotile(topology, kind, x, y, alphaMult);
                        break;
                    case DIRT:
                        drawSameKindAutotile(topology, kind, x, y, alphaMult);
                        break;
                    case STONE:
                        drawSameKindAutotile(topology, kind, x, y, alphaMult);
                        break;
                    case SAND:
                        drawSameKindAutotile(topology, kind, x, y, alphaMult);
                        break;
                    case SNOW:
                        drawSameKindAutotile(topology, kind, x, y, alphaMult);
                        break;
                    case WATER:
                        drawSameKindAutotile(topology, kind, x, y, alphaMult);
                        break;
                    case TILE: {
                        // Commercial polished-floor primary — fl-2 on the road
                        // sheet, uniform across every TILE cell (no per-cell
                        // variant pool). Whole-building floor, same model as
                        // INDOOR's `fl` blanket fill.
                        TileManifest.TileFrame f = TileManifest.pickTileGroundTile(x, y);
                        if (roadSheet != null) drawRoadTile(f, x, y, alphaMult, GROUND_TILE_EDGE_INSET_PX);
                        break;
                    }
                    case BRICK: {
                        // Brick paving (fl-tile-1..5 on the floors sheet).
                        // Plaza centers, building roofs (planned), large
                        // uniform paved areas. Per-cell variant hash gives
                        // noise rather than a uniform stamp.
                        TileManifest.TileFrame f = TileManifest.pickBrickTile(x, y);
                        drawFloorsTile(f, x, y, alphaMult);
                        break;
                    }
                    case SIDEWALK: {
                        // Curb-side sidewalk strip — same urban-tileset-3
                        // picker the STREET-wall-adjacent path uses, so an
                        // explicit SIDEWALK cell flanking a wall-adjacent
                        // STREET sidewalk reads as one continuous strip.
                        // "Not sidewalk" = neither tagged SIDEWALK nor a
                        // STREET-wall-adjacent cell that the renderer would
                        // also stamp with urban-3 sidewalk art.
                        boolean nNotSw = !isSidewalkLikeCell(grid, topology, x, y + 1);
                        boolean sNotSw = !isSidewalkLikeCell(grid, topology, x, y - 1);
                        boolean eNotSw = !isSidewalkLikeCell(grid, topology, x + 1, y);
                        boolean wNotSw = !isSidewalkLikeCell(grid, topology, x - 1, y);
                        UrbanTile3 frame = TileManifest.pickStreet3SidewalkFrame(
                                nNotSw, sNotSw, eNotSw, wNotSw);
                        drawUrbanTile3Frame(frame, x, y, alphaMult);
                        break;
                    }
                    case STRIPED: {
                        // STRIPED autotile frames itself against any non-walkable
                        // neighbor (e.g. fortified post perimeter) — same neighbor
                        // semantics as the courtyard autotile.
                        TileManifest.TileFrame f = TileManifest.pickStripedTile(nWall, sWall, eWall, wWall);
                        if (roadSheet != null) drawRoadTile(f, x, y, alphaMult, GROUND_TILE_EDGE_INSET_PX);
                        break;
                    }
                    case LZ_MARKER: {
                        TileManifest.TileFrame f = TileManifest.pickLzMarkerTile();
                        if (roadSheet != null) drawRoadTile(f, x, y, alphaMult, GROUND_TILE_EDGE_INSET_PX);
                        break;
                    }
                    case INDOOR:
                    default: {
                        TileManifest.TileFrame f = TileManifest.pickFloorTile(nWall, sWall, eWall, wWall);
                        drawTile(f, x, y, alphaMult, GROUND_TILE_EDGE_INSET_PX);
                        break;
                    }
                }

                // Original doorway (punched through a wall at gen time) gets
                // the overhead door overlay. Breached cells are flagged rubble
                // AND doorway — the !isRubble guard keeps the overhead off
                // those, since they're holes blasted through, not real doors.
                // DOOR_OPEN is an overlay, not a ground tile — inset=0 so the
                // overhead bar's edge pixels aren't cropped.
                if (grid.isDoorway(x, y) && !topology.isRubble(x, y)) {
                    drawTile(TileManifest.DOOR_OPEN, x, y, alphaMult, 0);
                }
            }
        }

        // Floor-pass flush — painter order: road/floors/water before urban so
        // DOOR_OPEN overlays (queued onto urbanBatch on doorway cells in this
        // loop) draw on top of any adjacent road tile. Solid last so crosswalk
        // stripes — appended after their road tile in the same cell — go on
        // top of the road. Fallback fills (road/courtyard/null pick) also
        // live in solidBatch and only occupy cells with no textured floor draw,
        // so the relative ordering against road/floors is moot for them.
        try (GlStateBracket gl = GlStateBracket.textured2D()) {
            if (roadBatch       != null) roadBatch.flush();
            if (urbanTile3Batch != null) urbanTile3Batch.flush();
            if (natureBatch     != null) natureBatch.flush();
            if (floorsBatch     != null) floorsBatch.flush();
            if (waterBatch      != null) waterBatch.flush();
            if (urbanBatch      != null) urbanBatch.flush();
            solidBatch.flush();
        }

        // Wall pass — autotile pick from neighbor exposure. Interior walls
        // (all four neighbors are walls) draw a solid fill since the source's
        // interior cell is transparent. Iteration keys on grid.isWall directly
        // so vehicle cells (non-walkable but not walls) are skipped without
        // any extra guard.
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                if (!topology.isWall(x, y)) continue;
                // Wall direction is intrinsic — set once at gen time by the
                // building stamper, immutable under runtime mutations (rubble
                // appearing next to a wall doesn't reorient it). Mask bits
                // say "the building's exterior is on my N/S/E/W side."
                int mask = topology.getWallDirMask(x, y);
                TileManifest.TileFrame tile = WallMasks.pickTileFromMask(mask);
                if (tile == null) {
                    fillCell(x, y, WALL_COLOR, alphaMult);
                } else {
                    // Walls render with NO source inset — the directional cap
                    // strokes on the 3×3 wall block live at the cell edge
                    // (top row of (4,0) draws the wall's north cap, etc.), so
                    // any inset crops them and makes opposite-edge walls read
                    // identically. Bilinear bleed isn't a concern here because
                    // a wall cell's neighbors are either more wall (same art)
                    // or a known interior/exterior fill that doesn't bleed in
                    // a visible way.
                    drawTile(tile, x, y, alphaMult, 0);
                }
            }
        }

        // Wall-pass flush — walls draw on top of the floor pass that already
        // flushed above. urbanBatch and solidBatch are disjoint cell sets
        // within the wall pass (textured wall vs. interior-wall fallback fill)
        // so their relative flush order is incidental.
        try (GlStateBracket gl = GlStateBracket.textured2D()) {
            if (urbanBatch != null) urbanBatch.flush();
            solidBatch.flush();
        }
    }

    /**
     * Queues a solid quad covering one nav-grid cell into {@link #solidBatch}
     * — used as the interior-wall fallback and as the road/courtyard fallback
     * when an autotile pick returns null. Deferred so painter ordering is
     * preserved against the textured batch flushes around it.
     */
    private void fillCell(int gridX, int gridY, Color color, float alphaMult) {
        float x0 = camera.cellToScreenX(gridX);
        float y0 = camera.cellToScreenY(gridY);
        float c = camera.cellPxSize();
        solidBatch.appendRect(
                x0, y0, x0 + c, y0 + c,
                color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f,
                alphaMult);
    }

    /**
     * Debug overlay — tints each cell by its zone id so the partitioning is
     * visible at a glance. Per-id color comes from a stable hash so toggling
     * the overlay never reshuffles which zone reads as which color, and a
     * wall breach that materializes a new portal zone gets its own fresh
     * color rather than reusing an existing one.
     *
     * <p>Drawn between the tile pass and the unit pass so units stay readable
     * on top. Low alpha (~0.30) so floor / rubble texture still shows through.
     */
    private void renderZoneOverlay(BattleSimulation sim, float alphaMult) {
        com.dillon.starsectormarines.battle.nav.zone.ZoneGraph zones = sim.getZoneGraph();
        NavigationGrid grid = sim.getGrid();
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glBegin(GL_QUADS);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                int zoneId = zones.zoneIdAt(x, y);
                if (zoneId < 0) continue;
                int h = zoneId * 0x9E3779B1; // Knuth multiplicative hash for stable color spread
                float r = ((h       ) & 0xFF) / 255f;
                float g = ((h >>> 8 ) & 0xFF) / 255f;
                float b = ((h >>> 16) & 0xFF) / 255f;
                glColor4f(r, g, b, 0.30f * alphaMult);
                float x0 = camera.cellToScreenX(x);
                float y0 = camera.cellToScreenY(y);
                float c = camera.cellPxSize();
                float x1 = x0 + c;
                float y1 = y0 + c;
                glVertex2f(x0, y0);
                glVertex2f(x1, y0);
                glVertex2f(x1, y1);
                glVertex2f(x0, y1);
            }
        }
        glEnd();
    }

    /** True for in-bounds cells tagged WALL. Used by sidewalk-edge detection where the renderer cares about cell type only, not exterior-side semantics. */
    private static boolean isInBoundsWall(CellTopology topology, int x, int y) {
        return topology.inBounds(x, y) && topology.isWall(x, y);
    }

    /** Street cell directly adjacent to an in-bounds wall — gets the sidewalk panel instead of the road autotile. */
    private static boolean isSidewalkCell(NavigationGrid grid, CellTopology topology, int x, int y) {
        if (!grid.inBounds(x, y) || !grid.isWalkable(x, y) || !topology.isStreet(x, y)) return false;
        return isInBoundsWall(topology, x + 1, y)
                || isInBoundsWall(topology, x - 1, y)
                || isInBoundsWall(topology, x, y + 1)
                || isInBoundsWall(topology, x, y - 1);
    }

    /**
     * Either an explicitly-tagged {@link CellTopology.GroundKind#SIDEWALK} cell
     * (the gen-time route — wide trunk flanks, etc.) or an implicit
     * STREET-wall-adjacent sidewalk cell. Used as the "is this neighbor part
     * of the same sidewalk strip?" predicate by the urban-tileset-3 corner
     * picker, so the two paths join at their shared edges without picking a
     * corner artifact at the boundary.
     */
    private static boolean isSidewalkLikeCell(NavigationGrid grid, CellTopology topology, int x, int y) {
        if (!topology.inBounds(x, y)) return false;
        if (topology.getGroundKind(x, y) == CellTopology.GroundKind.SIDEWALK) return true;
        return isSidewalkCell(grid, topology, x, y);
    }

    /**
     * What the road autotile considers a boundary: in-bounds wall cells and
     * sidewalk cells. OOB stays open so map-edge roads don't pick up an edge
     * marking against nothing. Vehicle cells aren't walls (just non-walkable),
     * so the road tiles right up to them without an edge marking.
     */
    private static boolean isRoadBoundary(NavigationGrid grid, CellTopology topology, int x, int y) {
        if (!grid.inBounds(x, y)) return false;
        if (topology.isWall(x, y)) return true;
        return isSidewalkCell(grid, topology, x, y);
    }

    /**
     * Paints zebra-crossing stripes over a crosswalk cell. {@code stripesHorizontal}
     * runs the stripes east-west (pedestrian walks north-south); otherwise they
     * run north-south. The cell is divided into N stripe + (N-1) gap bands plus
     * two margins, then each band is filled as a quad.
     */
    private void drawCrosswalkStripes(int gridX, int gridY, boolean stripesHorizontal, float alphaMult) {
        float cell = camera.cellPxSize();
        float x0 = camera.cellToScreenX(gridX);
        float y0 = camera.cellToScreenY(gridY);
        float stripeW = cell * CROSSWALK_STRIPE_FRAC;
        float gapW    = cell * CROSSWALK_GAP_FRAC;
        float bandSpan = CROSSWALK_STRIPE_COUNT * stripeW + (CROSSWALK_STRIPE_COUNT - 1) * gapW;
        float marginAlong = (cell - bandSpan) / 2f;
        float perpInset = cell * CROSSWALK_INSET_FRAC;
        float alpha = CROSSWALK_ALPHA * alphaMult;
        float sr = CROSSWALK_STRIPE.getRed()   / 255f;
        float sg = CROSSWALK_STRIPE.getGreen() / 255f;
        float sb = CROSSWALK_STRIPE.getBlue()  / 255f;
        for (int i = 0; i < CROSSWALK_STRIPE_COUNT; i++) {
            float bandStart = marginAlong + i * (stripeW + gapW);
            float rx, ry, rw, rh;
            if (stripesHorizontal) {
                // Bands stack vertically; each stripe spans the cell horizontally.
                rx = x0 + perpInset; ry = y0 + bandStart;
                rw = cell - 2 * perpInset; rh = stripeW;
            } else {
                // Bands stack horizontally; each stripe spans the cell vertically.
                rx = x0 + bandStart; ry = y0 + perpInset;
                rw = stripeW; rh = cell - 2 * perpInset;
            }
            solidBatch.appendRect(rx, ry, rx + rw, ry + rh, sr, sg, sb, alpha);
        }
    }

    /** Renders the doodad layer (chairs, crates, chest, LZ pads, etc.) on top of floors and below units. Branches on which sheet each doodad indexes into. */
    private void renderDoodads(BattleSimulation sim, float alphaMult) {
        if (tileSheet == null) return;
        for (Doodad d : sim.getDoodads()) {
            if (d.fromRoadSheet) {
                if (roadSheet != null) drawRoadTile(d.tile, d.cellX, d.cellY, alphaMult, 0);
            } else {
                // Doodads are standalone props (chairs, crates, LZ pads) — their
                // edge pixels are real visible content, so inset=0 to avoid
                // chopping the sprite. Same reason DOOR_OPEN passes 0 above.
                drawTile(d.tile, d.cellX, d.cellY, alphaMult, 0);
            }
        }
        try (GlStateBracket gl = GlStateBracket.textured2D()) {
            if (roadBatch       != null) roadBatch.flush();
            if (urbanTile3Batch != null) urbanTile3Batch.flush();
            if (urbanBatch      != null) urbanBatch.flush();
        }
    }

    /**
     * Counterpart to {@link #drawTile} for the road sheet. Appends to
     * {@link #roadBatch}. Pass {@link #GROUND_TILE_EDGE_INSET_PX} for
     * road/courtyard/striped/sidewalk autotiles (continuous field) and
     * {@code 0} for road-sheet doodads (LZ pad as a prop) so their edges
     * aren't cropped.
     */
    private void drawRoadTile(TileManifest.TileFrame f, int gridX, int gridY, float alphaMult,
                              int srcEdgeInsetPx) {
        if (roadBatch == null) return;
        int srcPxX = f.col * TileManifest.TILE_SIZE + srcEdgeInsetPx;
        int srcTopPxY = f.row * TileManifest.TILE_SIZE + srcEdgeInsetPx;
        int srcPxW = TileManifest.TILE_SIZE - 2 * srcEdgeInsetPx;
        int srcPxH = TileManifest.TILE_SIZE - 2 * srcEdgeInsetPx;

        float cellPx = camera.cellPxSize();
        float cx = camera.cellToScreenX(gridX + 0.5f);
        float cy = camera.cellToScreenY(gridY + 0.5f);
        roadBatch.append(srcPxX, srcTopPxY, srcPxW, srcPxH,
                cx, cy, cellPx, cellPx,
                1f, 1f, 1f, alphaMult);
    }

    /**
     * Stamps one {@link UrbanTile3} frame at {@code (gridX, gridY)} via the
     * sliced-sheet bbox cached in {@link #urbanTile3Frames}. Source rect is
     * the slicer-detected per-frame bounding box minus the standard ground
     * inset for {@link UrbanTile3.Kind#GROUND} tiles (street, sidewalk),
     * zero inset for overlays (doodads). Destination is always one nav cell
     * — the variable-width source frame stretches with bilinear filtering
     * to fill it, same convention as the {@link com.dillon.starsectormarines.battle.sprites.NatureTile}
     * render path.
     */
    private void drawUrbanTile3Frame(UrbanTile3 frame, int gridX, int gridY, float alphaMult) {
        if (urbanTile3Batch == null || urbanTile3Frames == null || frame == null) return;
        int idx = frame.frameIndex();
        if (idx < 0 || idx >= urbanTile3Frames.frames.length) return;
        SpriteSheetFrames.Frame f = urbanTile3Frames.frames[idx];
        int inset = frame.isGround() ? GROUND_TILE_EDGE_INSET_PX : 0;
        int srcPxX = f.x + inset;
        int srcPxY = f.y + inset;
        int srcPxW = Math.max(1, f.w - 2 * inset);
        int srcPxH = Math.max(1, f.h - 2 * inset);

        float cellPx = camera.cellPxSize();
        float cx = camera.cellToScreenX(gridX + 0.5f);
        float cy = camera.cellToScreenY(gridY + 0.5f);
        urbanTile3Batch.append(srcPxX, srcPxY, srcPxW, srcPxH,
                cx, cy, cellPx, cellPx,
                1f, 1f, 1f, alphaMult);
    }

    /**
     * Counterpart to {@link #drawUrbanTile3Frame} for the nature-tiles sheet.
     * Stamps one {@link com.dillon.starsectormarines.battle.sprites.NatureTile}
     * frame at {@code (gridX, gridY)} via the slicer-detected per-frame bbox
     * cached in {@link #natureFrames}. Ground tiles get the standard inset,
     * overlay tiles get inset=0 — same convention as urban-tileset-3.
     */
    private void drawNatureTile(com.dillon.starsectormarines.battle.sprites.NatureTile tile,
                                int gridX, int gridY, float alphaMult) {
        if (natureBatch == null || natureFrames == null || tile == null) return;
        int idx = tile.frameIndex();
        if (idx < 0 || idx >= natureFrames.frames.length) return;
        SpriteSheetFrames.Frame f = natureFrames.frames[idx];
        int inset = tile.isGround() ? GROUND_TILE_EDGE_INSET_PX : 0;
        int srcPxX = f.x + inset;
        int srcPxY = f.y + inset;
        int srcPxW = Math.max(1, f.w - 2 * inset);
        int srcPxH = Math.max(1, f.h - 2 * inset);

        float cellPx = camera.cellPxSize();
        float cx = camera.cellToScreenX(gridX + 0.5f);
        float cy = camera.cellToScreenY(gridY + 0.5f);
        natureBatch.append(srcPxX, srcPxY, srcPxW, srcPxH,
                cx, cy, cellPx, cellPx,
                1f, 1f, 1f, alphaMult);
    }

    /**
     * Dispatch for the "same-kind autotile" ground kinds (grass, dirt, stone,
     * sand, snow, water) — picks the right picker and the right sheet, then
     * draws. Neighbor predicate is "is this neighbor the SAME ground kind",
     * inverted to "is not same kind" since the autotile pickers want the
     * edge drawn where the kind ends.
     *
     * <p>OOB cells count as "not same kind" (i.e., the edge faces outward at
     * the map boundary) so a grass strip flush against the edge still gets a
     * proper edge tile rather than tiling open.
     */
    private void drawSameKindAutotile(CellTopology topology, CellTopology.GroundKind kind,
                                      int x, int y, float alphaMult) {
        // Outdoor ground kinds always pick a center variant — see memory note
        // "flat-edges-between-kinds". The per-kind edge tiles look bad in
        // practice (doubled dark trim at kind transitions, water-sheet
        // "shore" art assumes grass adjacency it can't guarantee). Hard
        // cell-boundary transitions are the accepted look.
        //
        // GRASS and DIRT dispatch through the sliced nature-tiles sheet —
        // hash-picked from a two-variant pool per kind so parks and yards
        // don't read as a uniform single-frame stamp. Falls back to the
        // legacy Floors_Tiles autotile only if the nature sheet failed to
        // load, since the Floors_Tiles grass / dirt art is the second-best
        // option for these surfaces.
        if (natureBatch != null) {
            if (kind == CellTopology.GroundKind.GRASS) {
                drawNatureTile(TileManifest.pickNatureGrassTile(x, y), x, y, alphaMult);
                return;
            }
            if (kind == CellTopology.GroundKind.DIRT) {
                drawNatureTile(TileManifest.pickNatureDirtTile(x, y), x, y, alphaMult);
                return;
            }
        }
        TileManifest.TileFrame f;
        switch (kind) {
            case GRASS: f = TileManifest.pickGrassTile(false, false, false, false, x, y); break;
            case DIRT:  f = TileManifest.pickDirtTile (false, false, false, false, x, y); break;
            case STONE: f = TileManifest.pickStoneTile(false, false, false, false, x, y); break;
            case SAND:  f = TileManifest.pickSandTile (false, false, false, false, x, y); break;
            case SNOW:  f = TileManifest.pickSnowTile (false, false, false, false, x, y); break;
            case WATER: f = TileManifest.pickWaterTile(false, false, false, false, x, y); break;
            default: return;
        }
        if (kind == CellTopology.GroundKind.WATER) {
            if (waterSheet != null) drawWaterTile(f, x, y, alphaMult);
        } else {
            if (floorsSheet != null) drawFloorsTile(f, x, y, alphaMult);
        }
    }

    /**
     * Stamps a 1×1 tile from the floors sheet at the 16px source cell size,
     * drawn at the full nav cell size (2x upscale). Same UV math as the
     * other sheets but with {@link TileManifest#FLOORS_TILE_SIZE} instead of
     * {@link TileManifest#TILE_SIZE}.
     */
    private void drawFloorsTile(TileManifest.TileFrame f, int gridX, int gridY, float alphaMult) {
        appendSmallTile(floorsBatch, f, gridX, gridY, alphaMult);
    }

    /** Counterpart to {@link #drawFloorsTile} for the water sheet — same 16px source cell, 2x upscale. */
    private void drawWaterTile(TileManifest.TileFrame f, int gridX, int gridY, float alphaMult) {
        appendSmallTile(waterBatch, f, gridX, gridY, alphaMult);
    }

    /**
     * Shared 16px-source tile append — used by both the floors and water sheets.
     * The 2x upscale comes from sizing the destination to {@code cellPx} regardless
     * of source size. Always applies {@link #GROUND_SMALL_TILE_EDGE_INSET_PX} since
     * both callers are pure ground autotiles.
     */
    private void appendSmallTile(QuadBatch batch, TileManifest.TileFrame f,
                                 int gridX, int gridY, float alphaMult) {
        if (batch == null) return;
        int inset = GROUND_SMALL_TILE_EDGE_INSET_PX;
        int srcPxX = f.col * TileManifest.FLOORS_TILE_SIZE + inset;
        int srcTopPxY = f.row * TileManifest.FLOORS_TILE_SIZE + inset;
        int srcPxW = TileManifest.FLOORS_TILE_SIZE - 2 * inset;
        int srcPxH = TileManifest.FLOORS_TILE_SIZE - 2 * inset;

        float cellPx = camera.cellPxSize();
        float cx = camera.cellToScreenX(gridX + 0.5f);
        float cy = camera.cellToScreenY(gridY + 0.5f);
        batch.append(srcPxX, srcTopPxY, srcPxW, srcPxH,
                cx, cy, cellPx, cellPx,
                1f, 1f, 1f, alphaMult);
    }

    /**
     * Queues a single 1×1 {@link TileManifest.TileFrame} into {@link #urbanBatch}
     * for one grid cell. Pass {@link #GROUND_TILE_EDGE_INSET_PX} for ground
     * autotiles (floor, wall, rubble) and {@code 0} for overlays / standalone
     * props (doodads, DOOR_OPEN) — the inset would visibly chop a standalone
     * sprite's edge. Doesn't draw; the surrounding pass flushes {@link #urbanBatch}.
     */
    private void drawTile(TileManifest.TileFrame f, int gridX, int gridY,
                          float alphaMult, int srcEdgeInsetPx) {
        if (urbanBatch == null) return;
        int srcPxX = f.col * TileManifest.TILE_SIZE + srcEdgeInsetPx;
        int srcTopPxY = f.row * TileManifest.TILE_SIZE + srcEdgeInsetPx;
        int srcPxW = TileManifest.TILE_SIZE - 2 * srcEdgeInsetPx;
        int srcPxH = TileManifest.TILE_SIZE - 2 * srcEdgeInsetPx;

        float cellPx = camera.cellPxSize();
        float cx = camera.cellToScreenX(gridX + 0.5f);
        float cy = camera.cellToScreenY(gridY + 0.5f);
        urbanBatch.append(srcPxX, srcTopPxY, srcPxW, srcPxH,
                cx, cy, cellPx, cellPx,
                1f, 1f, 1f, alphaMult);
    }

    /** Stable per-cell hash for picking from tile pools — same cell always picks the same tile. */
    private static int cellHash(int x, int y) {
        int h = x * 73856093 ^ y * 19349663;
        return h & 0x7FFFFFFF;
    }

    private void renderGrid(NavigationGrid grid, CellTopology topology, float alphaMult) {
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // One floor quad covering the whole world rect (in screen-space, after
        // camera projection). Acts as the floor color when the tileset hasn't
        // loaded, and as a safety underlay if any tile sprite happens to be
        // transparent. The quad is the world rect — pan/zoom shrink or shift
        // it inside the viewport, and the scissor cap from render() keeps it
        // from bleeding outside the grid area.
        float worldX0 = camera.cellToScreenX(0);
        float worldY0 = camera.cellToScreenY(0);
        float worldX1 = camera.cellToScreenX(grid.getWidth());
        float worldY1 = camera.cellToScreenY(grid.getHeight());
        glColor4f(FLOOR_COLOR.getRed() / 255f, FLOOR_COLOR.getGreen() / 255f,
                FLOOR_COLOR.getBlue() / 255f, alphaMult);
        glBegin(GL_QUADS);
        glVertex2f(worldX0, worldY0);
        glVertex2f(worldX1, worldY0);
        glVertex2f(worldX1, worldY1);
        glVertex2f(worldX0, worldY1);
        glEnd();

        if (tileSheet != null) {
            renderTiledFloorsAndWalls(grid, topology, alphaMult);
        } else {
            // Fallback — solid-color walls. Same path the renderer used before
            // the tileset landed; kept so the sim still reads if texture load fails.
            float cellPx = camera.cellPxSize();
            glColor4f(WALL_COLOR.getRed() / 255f, WALL_COLOR.getGreen() / 255f,
                    WALL_COLOR.getBlue() / 255f, alphaMult);
            glBegin(GL_QUADS);
            for (int y = 0; y < grid.getHeight(); y++) {
                for (int x = 0; x < grid.getWidth(); x++) {
                    if (grid.isWalkable(x, y)) continue;
                    float x0 = camera.cellToScreenX(x);
                    float y0 = camera.cellToScreenY(y);
                    float x1 = x0 + cellPx;
                    float y1 = y0 + cellPx;
                    glVertex2f(x0, y0);
                    glVertex2f(x1, y0);
                    glVertex2f(x1, y1);
                    glVertex2f(x0, y1);
                }
            }
            glEnd();
        }
    }

    /**
     * Fog-of-war roof pass. For each building, paints a BRICK tile across its
     * interior cells, modulated by the building's tint and current alpha. The
     * sim's visibility pass writes {@code targetAlpha} at ~10 Hz; this method
     * lerps {@code currentAlpha → targetAlpha} so the fade is smooth across
     * frames between visibility updates. Runs after units draw, so a roofed
     * building visibly covers the units inside; runs before objective markers,
     * shuttles, projectiles, and FX so all of those pierce the roof.
     */
    private void renderRoofs(BattleSimulation sim, float alphaMult) {
        com.dillon.starsectormarines.battle.map.Buildings buildings = sim.getBuildings();
        if (buildings == null || buildings.isEmpty()) return;
        ensureFloorsSheet();
        if (floorsSheet == null || floorsBatch == null) return;

        CellTopology topology = sim.getTopology();
        for (com.dillon.starsectormarines.battle.map.Building b : buildings.all()) {
            float roofAlpha = b.currentAlpha;
            if (roofAlpha <= 0.01f) continue;
            for (int i = 0, n = b.cellCount(); i < n; i++) {
                int cx = b.cellsX[i];
                int cy = b.cellsY[i];
                // Skip caved-in cells — the rubble decal painted earlier in
                // the frame shows through where the roof used to be.
                if (topology.isRoofDestroyed(cx, cy)) continue;
                TileManifest.TileFrame f = TileManifest.pickBrickTile(cx, cy);
                appendSmallTileTinted(floorsBatch, f, cx, cy,
                        b.tintR, b.tintG, b.tintB, roofAlpha * alphaMult);
            }
        }

        try (GlStateBracket gl = GlStateBracket.textured2D()) {
            floorsBatch.flush();
        }
    }

    /**
     * Lerps each building's {@code currentAlpha → targetAlpha} on real dt so
     * the fade is decoupled from sim tick rate (still smooth under pause,
     * speed-up, or low FPS). Driven from {@link #advance} so the lerp keeps
     * advancing even when the sim doesn't tick this frame.
     */
    private void advanceRoofAlphaLerp(BattleSimulation sim, float dt) {
        if (sim == null) return;
        com.dillon.starsectormarines.battle.map.Buildings buildings = sim.getBuildings();
        if (buildings == null || buildings.isEmpty()) return;
        // ~3 alpha-units / sec → reveal in ~0.33 s, reads as a quick wipe.
        float lerpAmount = Math.min(1f, dt * 3f);
        for (com.dillon.starsectormarines.battle.map.Building b : buildings.all()) {
            b.currentAlpha += (b.targetAlpha - b.currentAlpha) * lerpAmount;
        }
    }

    /**
     * Tinted variant of {@link #appendSmallTile} — same source-cell + 2x dst
     * shape, but the caller supplies an RGB multiply for per-building roof
     * flavor. Alpha is the standard alpha-mult.
     */
    private void appendSmallTileTinted(QuadBatch batch, TileManifest.TileFrame f,
                                       int gridX, int gridY,
                                       float r, float g, float b, float alphaMult) {
        if (batch == null) return;
        int inset = GROUND_SMALL_TILE_EDGE_INSET_PX;
        int srcPxX = f.col * TileManifest.FLOORS_TILE_SIZE + inset;
        int srcTopPxY = f.row * TileManifest.FLOORS_TILE_SIZE + inset;
        int srcPxW = TileManifest.FLOORS_TILE_SIZE - 2 * inset;
        int srcPxH = TileManifest.FLOORS_TILE_SIZE - 2 * inset;

        float cellPx = camera.cellPxSize();
        float cx = camera.cellToScreenX(gridX + 0.5f);
        float cy = camera.cellToScreenY(gridY + 0.5f);
        batch.append(srcPxX, srcTopPxY, srcPxW, srcPxH,
                cx, cy, cellPx, cellPx,
                r, g, b, alphaMult);
    }

    private void renderUnits(List<Unit> units, float alphaMult) {
        float unitSize = camera.cellPxSize() * UNIT_FRAC;
        float half = unitSize / 2f;

        // Turrets render in their own pass first — pavement plate + rotated
        // weapon sprite — so marines walking adjacent to a turret draw over
        // the barrel rather than under it. (Z-order is iteration order; this
        // pass runs before the marine pass.)
        renderTurrets(units, alphaMult);

        // Dead-body pre-pass — corpses always sit beneath living units so a
        // marine standing over a fallen squadmate fully occludes them.
        renderDeadUnits(units, unitSize, alphaMult);

        // Per-unit sheet pick — every UnitType has its own sheet cache. The
        // singleton SpriteAPI is shared across all units of the same type;
        // we reset tint at the end so a tinted civilian doesn't bleed into
        // the next render pass.
        java.util.Set<UnitSpriteCache> tintedThisFrame = new java.util.HashSet<>();
        for (Unit u : units) {
            if (!u.isAlive()) continue;
            if (u instanceof MapTurret) continue; // handled by renderTurrets above
            UnitSpriteCache cache = unitSprites.get(u.type);
            // Mid-aim rocket marine swaps to the per-secondary aim sheet so
            // the launcher pose reads. Falls back to the regular sheet if the
            // aim sheet failed to load.
            if (u.secondaryActionTimer > 0f && u.secondaryWeapon != null) {
                UnitSpriteCache aim = marineSecondaryAimSheets.get(u.secondaryWeapon);
                if (aim != null && aim.sheet != null && aim.frames != null
                        && aim.frames.frames.length > 0) {
                    cache = aim;
                }
            }
            if (cache == null || cache.sheet == null || cache.frames == null
                    || cache.frames.frames.length == 0) {
                renderUnitQuadFallback(u, unitSize, half, alphaMult);
                continue;
            }
            renderUnitSprite(u, cache, unitSize, alphaMult);
            tintedThisFrame.add(cache);
        }
        // Reset every sheet we touched so leftover tint/alpha doesn't bleed
        // into other passes that share these SpriteAPI singletons.
        for (UnitSpriteCache cache : tintedThisFrame) {
            cache.sheet.setColor(Color.WHITE);
        }

        // HP bars above each unit (always, regardless of sprite fallback).
        // Skip non-combatants — civilians/scientists with HP bars looks weird
        // and they don't really "fight" in a sense the bar communicates.
        // Turrets get their bar pushed above their visual envelope, since
        // visualCells > 1 means a cell-top bar would draw inside the sprite.
        for (Unit u : units) {
            if (!u.isAlive()) continue;
            if (!u.type.combatant) continue;
            float cx = camera.cellToScreenX(u.renderX + 0.5f);
            float cy = camera.cellToScreenY(u.renderY + 0.5f);
            float barW = unitSize;
            float barX = cx - barW / 2f;
            float barY;
            if (u instanceof MapTurret) {
                float visual = ((MapTurret) u).kind.visualCells;
                barY = cy + visual * camera.cellPxSize() / 2f + HP_BAR_GAP;
            } else {
                barY = cy + half + HP_BAR_GAP;
            }
            fillRect(barX, barY, barW, HP_BAR_H, HP_BG, alphaMult);
            float frac = Math.max(0f, Math.min(1f, u.hp / u.maxHp));
            fillRect(barX, barY, barW * frac, HP_BAR_H, HP_FG, alphaMult);
        }
    }

    /**
     * Draws one unit using its type-specific sheet. Non-combatants never enter
     * weapon-up state — their weapon-up slots are interaction poses
     * (clipboard, coffee) that don't track cooldowns. The DEFENDER tint is
     * gone now that each side has a distinct sprite; sheets render at white.
     */
    private void renderUnitSprite(Unit u, UnitSpriteCache cache, float unitSize, float alphaMult) {
        SpriteAPI sheet = cache.sheet;
        SpriteSheetFrames frames = cache.frames;
        float texW = sheet.getTextureWidth();
        float texH = sheet.getTextureHeight();
        int sheetW = frames.sheetWidth;
        int sheetH = frames.sheetHeight;

        // Aim cycle holds the weapon-up frame for its full duration so the
        // launcher reads as raised/braced through the whole animation.
        boolean inAim = u.secondaryActionTimer > 0f && u.secondaryWeapon != null;
        boolean weaponUp = inAim || (u.type.combatant
                && u.cooldownTimer > (u.attackCooldown - WEAPON_UP_TIME)
                && u.cooldownTimer > 0f);

        int frameIdx;
        boolean flipY;
        if (u.type.frameLayout == UnitType.FrameLayout.EIGHT_WAY_NO_WEAPON_UP) {
            EightWayFacing ef = computeEightWayFacing(u);
            frameIdx = pickFrameEightWay(ef);
            flipY = false;
        } else {
            Facing facing = computeFacing(u);
            frameIdx = pickFrame(facing, weaponUp);
            flipY = weaponUp && facing == Facing.SOUTH;
        }
        if (frameIdx >= frames.frames.length) frameIdx = 0;
        SpriteSheetFrames.Frame f = frames.frames[frameIdx];

        sheet.setTexX((float) f.x * texW / sheetW);
        sheet.setTexWidth((float) f.w * texW / sheetW);
        if (flipY) {
            sheet.setTexY((float) (sheetH - f.y) * texH / sheetH);
            sheet.setTexHeight(-(float) f.h * texH / sheetH);
        } else {
            sheet.setTexY((float) (sheetH - f.y - f.h) * texH / sheetH);
            sheet.setTexHeight((float) f.h * texH / sheetH);
        }
        float targetH = unitSize * u.type.renderScale;
        float targetW = targetH * f.w / (float) f.h;
        sheet.setSize(targetW, targetH);
        sheet.setAlphaMult(alphaMult);
        sheet.setNormalBlend();
        sheet.setColor(Color.WHITE);
        float cx = camera.cellToScreenX(u.renderX + 0.5f);
        float cy = camera.cellToScreenY(u.renderY + 0.5f);
        sheet.renderAtCenter(cx, cy);
    }

    /**
     * Renders parked vehicle sprites on top of the floor pass and below units.
     * Each vehicle's footprint cells are first painted with the road fill so
     * any transparent margins inside the truck sprite blend with the
     * surrounding street rather than showing the GL clear color. Sprite
     * aspect is preserved and centered inside the footprint.
     */
    private void renderVehicles(BattleSimulation sim, float alphaMult) {
        java.util.List<MapVehicle> vehicles = sim.getVehicles();
        if (vehicles.isEmpty()) return;

        // The floor pass in renderTiledFloorsAndWalls already painted the
        // underlying tile (street / courtyard / floor) for every vehicle cell,
        // so transparent margins inside the truck sprite blend with the real
        // pavement — no separate fill quad needed here.

        // Slice the right frame off each sheet, scale it to fit the footprint
        // (preserve aspect), and draw centered. The set of sheets we touch
        // this frame is reset to white afterward so leftover state doesn't
        // bleed into other passes that share the same SpriteAPI.
        float cellPx = camera.cellPxSize();
        java.util.Set<VehicleKind.VehicleSheet> touched = new java.util.HashSet<>();
        for (MapVehicle v : vehicles) {
            UnitSpriteCache cache = vehicleSheets.get(v.kind.sheet);
            if (cache == null || cache.sheet == null || cache.frames == null) continue;
            if (v.kind.frameIndex >= cache.frames.frames.length) continue;
            SpriteAPI sheet = cache.sheet;
            SpriteSheetFrames frames = cache.frames;
            SpriteSheetFrames.Frame f = frames.frames[v.kind.frameIndex];

            float texW = sheet.getTextureWidth();
            float texH = sheet.getTextureHeight();
            int sheetW = frames.sheetWidth;
            int sheetH = frames.sheetHeight;
            sheet.setTexX((float) f.x * texW / sheetW);
            sheet.setTexY((float) (sheetH - f.y - f.h) * texH / sheetH);
            sheet.setTexWidth((float) f.w * texW / sheetW);
            sheet.setTexHeight((float) f.h * texH / sheetH);

            float footW = v.kind.footprintCellsX * cellPx;
            float footH = v.kind.footprintCellsY * cellPx;
            // Preserve frame aspect inside the footprint. The shorter axis
            // fills the footprint and the longer axis is letterboxed against
            // the underlying floor tile painted by the floor pass — that way a
            // tall sprite doesn't squish into a wide footprint or vice versa.
            float frameAspect = (float) f.w / (float) f.h;
            float footAspect  = footW / footH;
            float drawW, drawH;
            if (frameAspect > footAspect) {
                drawW = footW;
                drawH = footW / frameAspect;
            } else {
                drawH = footH;
                drawW = footH * frameAspect;
            }
            sheet.setSize(drawW, drawH);
            sheet.setAlphaMult(alphaMult);
            sheet.setNormalBlend();
            sheet.setColor(Color.WHITE);
            float cx = camera.cellToScreenX(v.cellX + v.kind.footprintCellsX / 2f);
            float cy = camera.cellToScreenY(v.cellY + v.kind.footprintCellsY / 2f);
            sheet.renderAtCenter(cx, cy);
            touched.add(v.kind.sheet);
        }
        // Reset state on each touched sheet so the singleton SpriteAPI carries
        // no leftover UVs into the next pass.
        for (VehicleKind.VehicleSheet s : touched) {
            UnitSpriteCache cache = vehicleSheets.get(s);
            if (cache != null && cache.sheet != null) cache.sheet.setColor(Color.WHITE);
        }
    }

    /**
     * Renders MapTurret entries: a pavement plate under each mount (so the
     * non-walkable cell isn't dark void) plus a rotated vanilla weapon sprite
     * sized to {@link TurretKind#visualCells}. Skips destroyed turrets — the
     * sim has already flipped those cells to rubble, so the floor pass renders
     * them as damaged terrain and we don't redraw the weapon wreck (v1; a
     * destroyed-base sprite can come later).
     *
     * <p>Sprite barrel points UP in the source PNG, so {@code setAngle(facing)}
     * works directly without an offset — facing is degrees clockwise from north
     * (Starsector sprite-angle convention).
     */
    private void renderTurrets(List<Unit> units, float alphaMult) {
        boolean any = false;
        for (Unit u : units) {
            if (u instanceof MapTurret && u.isAlive()) { any = true; break; }
        }
        if (!any) return;

        float cellPx = camera.cellPxSize();

        // Pass 1 — pavement plate fill under each turret mount cell. Mirrors
        // the road-fill underlay renderVehicles draws so transparent margins
        // in the sprite blend with the surrounding street.
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glBegin(GL_QUADS);
        glColor4f(ROAD_FILL.getRed() / 255f, ROAD_FILL.getGreen() / 255f,
                ROAD_FILL.getBlue() / 255f, alphaMult);
        for (Unit u : units) {
            if (!(u instanceof MapTurret) || !u.isAlive()) continue;
            float x0 = camera.cellToScreenX(u.cellX);
            float y0 = camera.cellToScreenY(u.cellY);
            glVertex2f(x0,          y0);
            glVertex2f(x0 + cellPx, y0);
            glVertex2f(x0 + cellPx, y0 + cellPx);
            glVertex2f(x0,          y0 + cellPx);
        }
        glEnd();

        // Pass 2 — two layers per turret. The vanilla _recoil sprite is
        // actually the GUN/BARREL piece (the part that visibly recoils),
        // not a muzzle-flash overlay. _base is the body/mount. Default
        // render order (matching vanilla's RENDER_BARREL_BELOW hint) is
        // barrel first, body on top — the body covers the chamber end of
        // the barrel, so only the protruding length is visible.
        //
        // Firing snaps the barrel backward along the facing axis and eases
        // it forward over RECOIL_DURATION. The body stays put, so the
        // protruding length visibly shortens then slides back out — that's
        // the recoil read.
        java.util.Set<TurretKind> touched = new java.util.HashSet<>();
        java.util.Set<TurretKind> touchedRecoil = new java.util.HashSet<>();
        for (Unit u : units) {
            if (!(u instanceof MapTurret) || !u.isAlive()) continue;
            MapTurret t = (MapTurret) u;
            ShuttleSpriteCache base = turretSprites.get(t.kind);
            if (base == null) {
                renderTurretQuadFallback(t, cellPx * UNIT_FRAC, alphaMult);
                continue;
            }
            float cx = camera.cellToScreenX(t.cellX + 0.5f);
            float cy = camera.cellToScreenY(t.cellY + 0.5f);

            // Barrel layer (below body). Shifted backward when in recoil window.
            // Driven by recoilTimer (sim-seconds since last fired round) rather
            // than cooldownTimer, so burst weapons cycle the slide per round
            // instead of only on the trigger pull.
            ShuttleSpriteCache barrel = turretRecoilSprites.get(t.kind);
            if (barrel != null) {
                float recoilT = 0f;
                if (t.recoilTimer < RECOIL_DURATION) {
                    recoilT = 1f - t.recoilTimer / RECOIL_DURATION;
                }
                float pushPx = recoilT * RECOIL_DISTANCE_FRAC * t.kind.visualCells * cellPx;
                // Backward = opposite of forward facing. At facing 0 (north),
                // forward = +Y, so backward = -Y. General: backward direction
                // in our screen-Y-up convention is (sin F, -cos F).
                double rad = Math.toRadians(t.facingDegrees);
                float bx =  (float) Math.sin(rad)  * pushPx;
                float by = -(float) Math.cos(rad)  * pushPx;
                drawTurretLayer(barrel, t.facingDegrees, t.kind.visualCells, cellPx, cx + bx, cy + by, alphaMult);
                touchedRecoil.add(t.kind);
            }
            // Body layer (on top of the barrel).
            drawTurretLayer(base, t.facingDegrees, t.kind.visualCells, cellPx, cx, cy, alphaMult);
            touched.add(t.kind);
        }
        for (TurretKind k : touched) {
            ShuttleSpriteCache c = turretSprites.get(k);
            if (c != null) c.sprite.setAngle(0f);
        }
        for (TurretKind k : touchedRecoil) {
            ShuttleSpriteCache c = turretRecoilSprites.get(k);
            if (c != null) c.sprite.setAngle(0f);
        }
    }

    /** Renders one turret layer (body or barrel) at the given facing + center, sized to fit the visual envelope. Hoisted out so both layers share the setSize/setAngle/setColor boilerplate. */
    private static void drawTurretLayer(ShuttleSpriteCache cache, float facingDegrees, float visualCells,
                                        float cellPx, float cx, float cy, float alphaMult) {
        SpriteAPI sprite = cache.sprite;
        float pxH = visualCells * cellPx;
        float pxW = pxH * cache.aspect;
        sprite.setSize(pxW, pxH);
        sprite.setAngle(facingDegrees);
        sprite.setAlphaMult(alphaMult);
        sprite.setNormalBlend();
        sprite.setColor(Color.WHITE);
        sprite.renderAtCenter(cx, cy);
    }

    /** Fallback for turrets whose sprite failed to load — small red square so the unit is at least visible at the mount cell. */
    private void renderTurretQuadFallback(MapTurret t, float size, float alphaMult) {
        float half = size / 2f;
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glBegin(GL_QUADS);
        glColor4f(DEFENDER_COLOR.getRed() / 255f, DEFENDER_COLOR.getGreen() / 255f,
                DEFENDER_COLOR.getBlue() / 255f, alphaMult);
        float cx = camera.cellToScreenX(t.cellX + 0.5f);
        float cy = camera.cellToScreenY(t.cellY + 0.5f);
        glVertex2f(cx - half, cy - half);
        glVertex2f(cx + half, cy - half);
        glVertex2f(cx + half, cy + half);
        glVertex2f(cx - half, cy + half);
        glEnd();
    }

    /**
     * Renders fallen combatants as static corpse sprites pulled from
     * {@link UnitType#deadSpritePath}. The pose is rolled at death time and
     * stored in {@link Unit#deathPoseIdx} so the corpse never flickers between
     * frames. No rotation — the dead sheets are hand-drawn for a single
     * top-down orientation; spinning them reads as wrong.
     *
     * <p>Units whose type has no dead sheet (civilians, scientists, turrets)
     * just vanish on death. Same for units that died before the death-pose
     * roll landed (sentinel {@code deathPoseIdx == -1}, which shouldn't
     * normally happen but skip defensively).
     */
    private void renderDeadUnits(List<Unit> units, float unitSize, float alphaMult) {
        java.util.Set<UnitSpriteCache> touched = new java.util.HashSet<>();
        for (Unit u : units) {
            if (u.isAlive()) continue;
            if (u.deathPoseIdx < 0) continue;
            UnitSpriteCache cache = unitDeadSprites.get(u.type);
            if (cache == null || cache.sheet == null || cache.frames == null
                    || cache.frames.frames.length == 0) continue;
            SpriteSheetFrames frames = cache.frames;
            int frameIdx = ((u.deathPoseIdx % frames.frames.length) + frames.frames.length) % frames.frames.length;
            SpriteSheetFrames.Frame f = frames.frames[frameIdx];
            SpriteAPI sheet = cache.sheet;
            float texW = sheet.getTextureWidth();
            float texH = sheet.getTextureHeight();
            int sheetW = frames.sheetWidth;
            int sheetH = frames.sheetHeight;
            sheet.setTexX((float) f.x * texW / sheetW);
            sheet.setTexY((float) (sheetH - f.y - f.h) * texH / sheetH);
            sheet.setTexWidth((float) f.w * texW / sheetW);
            sheet.setTexHeight((float) f.h * texH / sheetH);
            // Fit the longer pose axis into one cell so wide prone poses
            // don't spill out into neighboring cells. Aspect of the loaded
            // frame drives the perpendicular dimension. Mech wrecks inherit
            // the live mech's renderScale so the corpse reads at the same
            // visual footprint as the walker did before it fell.
            float scaledSize = unitSize * u.type.renderScale;
            float targetW, targetH;
            if (f.w >= f.h) {
                targetW = scaledSize;
                targetH = scaledSize * f.h / (float) f.w;
            } else {
                targetH = scaledSize;
                targetW = scaledSize * f.w / (float) f.h;
            }
            sheet.setSize(targetW, targetH);
            sheet.setAngle(0f);
            sheet.setAlphaMult(alphaMult);
            sheet.setNormalBlend();
            sheet.setColor(Color.WHITE);
            float cx = camera.cellToScreenX(u.renderX + 0.5f);
            float cy = camera.cellToScreenY(u.renderY + 0.5f);
            sheet.renderAtCenter(cx, cy);
            touched.add(cache);
        }
        for (UnitSpriteCache c : touched) {
            c.sheet.setColor(Color.WHITE);
        }
    }

    /** Fallback colored-quad path when a unit's sprite sheet failed to load. */
    private void renderUnitQuadFallback(Unit u, float unitSize, float half, float alphaMult) {
        Color c;
        if (u.faction == Faction.MARINE)       c = MARINE_COLOR;
        else if (u.faction == Faction.DEFENDER) c = DEFENDER_COLOR;
        else                                    c = CIVILIAN_COLOR;
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glBegin(GL_QUADS);
        glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, alphaMult);
        float cx = camera.cellToScreenX(u.renderX + 0.5f);
        float cy = camera.cellToScreenY(u.renderY + 0.5f);
        glVertex2f(cx - half, cy - half);
        glVertex2f(cx + half, cy - half);
        glVertex2f(cx + half, cy + half);
        glVertex2f(cx - half, cy + half);
        glEnd();
    }

    /**
     * Renders each visible shuttle as a rotated sprite at its world position.
     * Length is {@link ShuttleType#visualLengthCells} cell-widths in the
     * sprite's forward (height) axis; width is derived from the cached natural
     * aspect ratio so taller-than-wide ship sprites render with their drawn
     * proportions intact.
     */
    private void renderShuttles(List<Shuttle> shuttles, float alphaMult) {
        if (shuttles.isEmpty()) return;
        for (Shuttle s : shuttles) {
            if (!s.isVisible()) continue;
            ShuttleSpriteCache cache = shuttleSprites.get(s.type);
            if (cache == null) continue;
            SpriteAPI sprite = cache.sprite;
            // scaleMult drives the altitude lerp (cruise → 1.0 across the leg)
            // plus the in-flight wobble. Multiplied through both axes so the
            // sprite keeps its native aspect.
            float pxLen = s.type.visualLengthCells * camera.cellPxSize() * s.scaleMult;
            float pxH = pxLen;
            float pxW = pxLen * cache.aspect;
            sprite.setSize(pxW, pxH);
            sprite.setAngle(s.body.facingDegrees);
            sprite.setAlphaMult(alphaMult);
            sprite.setNormalBlend();
            sprite.setColor(Color.WHITE);
            // Y-axis lift: sim position is the ground projection, the
            // rendered sprite floats above it by altitudeT * peak cells.
            // Combined with the scale lerp, this reads as actual upward
            // climb during takeoff / descent during landing.
            float altOffset = s.visualAltitudeOffsetCells();
            float cx = camera.cellToScreenX(s.body.x);
            float cy = camera.cellToScreenY(s.body.y + altOffset);
            // Engines first, so the hull's opaque alpha-over render naturally
            // covers the chamber end of each plume — only the protruding
            // length pokes out aft. Matches vanilla's "render engines under"
            // convention; turrets layer on top of the hull afterwards.
            renderShuttleEngines(s, alphaMult, altOffset);
            sprite.renderAtCenter(cx, cy);
            renderShuttleTurrets(s, alphaMult, altOffset);
        }
        // Reset angle so the singleton sprite doesn't carry our rotation
        // into whatever else might draw it.
        for (ShuttleSpriteCache cache : shuttleSprites.values()) {
            cache.sprite.setAngle(0f);
        }
    }

    /**
     * Thin call site over the shared {@link com.dillon.starsectormarines.battle.air.engine.EngineFxRenderer}.
     * Slots are scraped from the matching vanilla {@code .ship} spec at
     * runtime (mod load order aware, so modded hulls work without code
     * changes); the renderer does the world transform + glow/flame draw.
     * The hull's own facing already follows our {@code 0°=+Y} convention,
     * so we pass it through unchanged.
     */
    private void renderShuttleEngines(com.dillon.starsectormarines.battle.air.Shuttle s, float alphaMult,
                                      float altOffsetCells) {
        com.dillon.starsectormarines.battle.air.engine.EngineFxRenderer.draw(
                com.dillon.starsectormarines.battle.air.engine.EngineSlotResolver.resolve(s.type),
                s.body.x, s.body.y,
                s.body.facingDegrees,
                s.scaleMult,
                altOffsetCells,
                // FX-specific intensity: full plume at cruise speed, dialed
                // back at hover so a stationary Valkyrie doesn't look like
                // it's at full burn. Audio still tracks engineIntensity().
                s.engineFxIntensity(),
                alphaMult,
                camera,
                engineGlowSprite, engineFlameSprite);
    }

    /**
     * Draws every {@link com.dillon.starsectormarines.battle.air.MountedTurret}
     * on a shuttle on top of the shuttle's hull sprite. Mount offsets are
     * rotated into world frame using the parent's facing, and each turret's
     * own facing (independent of the hull's) drives the sprite rotation —
     * so an Arbalest tracking a target rotates against the hovering Valkyrie.
     *
     * <p>Mount sprite size scales with the shuttle's {@code scaleMult} so a
     * hovering shuttle's turrets read at the same "this is up high" size as
     * the hull they're attached to.
     */
    private void renderShuttleTurrets(com.dillon.starsectormarines.battle.air.Shuttle s, float alphaMult,
                                      float altOffsetCells) {
        if (s.turrets.length == 0) return;
        float rad = (float) Math.toRadians(s.body.facingDegrees);
        float c = (float) Math.cos(rad);
        float si = (float) Math.sin(rad);
        float cellPx = camera.cellPxSize();
        for (com.dillon.starsectormarines.battle.air.MountedTurret mt : s.turrets) {
            ShuttleSpriteCache base = turretSprites.get(mt.mount.kind);
            if (base == null) continue;
            // Mount offsets are defined in the shuttle's full-size frame. The
            // hull sprite renders at scaleMult × visualLengthCells (line ~2633),
            // so the mount positions have to shrink by the same factor or the
            // turrets visibly detach from the hull during the landing lerp.
            float lx = mt.mount.localOffsetX * s.scaleMult;
            float ly = mt.mount.localOffsetY * s.scaleMult;
            float worldOffsetX = lx * c - ly * si;
            float worldOffsetY = lx * si + ly * c;
            float wx = s.body.x + worldOffsetX;
            float wy = s.body.y + worldOffsetY + altOffsetCells;
            float screenX = camera.cellToScreenX(wx);
            float screenY = camera.cellToScreenY(wy);
            // Shared scale: per-shuttle scaleMult (altitude lerp) × per-hull
            // turretVisualScale. Both layers must share it so barrel and body
            // stay aligned through landing/takeoff.
            float layerVisualCells = mt.mount.kind.visualCells * s.scaleMult * s.type.turretVisualScale;

            // Barrel layer (drawn under the body — mirrors the static-turret
            // pass in renderTurrets). Slides backward along the barrel-facing
            // axis during the recoil window after firing, then eases forward
            // over RECOIL_DURATION. Body covers the chamber end so only the
            // protruding length visibly shortens then slides back out.
            ShuttleSpriteCache barrel = turretRecoilSprites.get(mt.mount.kind);
            if (barrel != null) {
                float recoilT = 0f;
                if (mt.recoilTimer < RECOIL_DURATION) {
                    recoilT = 1f - mt.recoilTimer / RECOIL_DURATION;
                }
                float pushPx = recoilT * RECOIL_DISTANCE_FRAC * layerVisualCells * cellPx;
                double brad = Math.toRadians(mt.facingDegrees);
                float bx =  (float) Math.sin(brad)  * pushPx;
                float by = -(float) Math.cos(brad)  * pushPx;
                drawTurretLayer(barrel, mt.facingDegrees, layerVisualCells, cellPx,
                        screenX + bx, screenY + by, alphaMult);
            }
            // Body layer on top of the barrel.
            drawTurretLayer(base, mt.facingDegrees, layerVisualCells, cellPx,
                    screenX, screenY, alphaMult);
        }
    }

    /**
     * Renders SABOTAGE charge-site markers and dropped equipment kits on top
     * of the unit layer. Charge sites pulse and grow a progress arc while a
     * planter channels; on completion the alarm icon swaps to the danger sign
     * and the arc disappears. Kit drops gently bob to catch the eye.
     *
     * <p>Time source for animation is wall-clock — pulses keep playing while
     * the sim is paused, which is desirable: a paused player still needs the
     * progress visualization to read clearly.
     */
    private void renderObjectiveMarkers(BattleSimulation sim, float alphaMult) {
        float now = (float) (System.currentTimeMillis() / 1000.0);

        float cellPx = camera.cellPxSize();
        for (Objective o : sim.getObjectives()) {
            if (!(o instanceof ChargeSiteObjective)) continue;
            ChargeSiteObjective site = (ChargeSiteObjective) o;
            float cx = camera.cellToScreenX(site.cellX() + 0.5f);
            float cy = camera.cellToScreenY(site.cellY() + 0.5f);
            if (site.isComplete()) {
                drawTintedIcon(iconDanger, cx, cy,
                        cellPx * CHARGE_ICON_SIZE,
                        CHARGE_TINT_COMPLETE, alphaMult);
            } else {
                float pulse = site.planterOnSite()
                        ? 1f + CHARGE_PULSE_AMP * (float) Math.sin(now * 2.0 * Math.PI * CHARGE_PULSE_HZ)
                        : 1f;
                drawTintedIcon(iconAlarm, cx, cy,
                        cellPx * CHARGE_ICON_SIZE * pulse,
                        CHARGE_TINT_ACTIVE, alphaMult);
                float progress = site.progress() / Math.max(0.001f, site.plantDuration());
                float innerR = cellPx * 0.55f;
                float outerR = innerR + Math.max(3f, cellPx * 0.12f);
                drawProgressArc(cx, cy, innerR, outerR, progress, CHARGE_TINT_ARC, alphaMult);
            }
        }

        for (EquipmentDrop drop : sim.getEquipmentDrops()) {
            if (drop.consumed) continue;
            float cx = camera.cellToScreenX(drop.cellX + 0.5f);
            float cy = camera.cellToScreenY(drop.cellY + 0.5f);
            float pulse = 1f + KIT_DROP_PULSE_AMP * (float) Math.sin(now * 2.0 * Math.PI * KIT_DROP_PULSE_HZ);
            drawTintedIcon(iconStar, cx, cy,
                    cellPx * KIT_DROP_SIZE * pulse,
                    KIT_DROP_TINT, alphaMult);
        }
    }

    /**
     * Draws a sprite tinted by {@code tint}, centered at ({@code cx}, {@code cy}),
     * sized to a {@code size}x{@code size} square. Falls through to a colored
     * square if the sprite failed to load — fallback keeps the map readable.
     * Resets the sprite's color to white afterward so the singleton doesn't
     * carry our tint into the next caller.
     */
    private void drawTintedIcon(SpriteAPI sprite, float cx, float cy, float size, Color tint, float alphaMult) {
        if (sprite == null) {
            fillRect(cx - size / 2f, cy - size / 2f, size, size, tint, alphaMult);
            return;
        }
        sprite.setSize(size, size);
        sprite.setColor(tint);
        sprite.setAlphaMult(alphaMult);
        sprite.setNormalBlend();
        sprite.renderAtCenter(cx, cy);
        sprite.setColor(Color.WHITE);
    }

    /**
     * Draws a clockwise-filling ring from the 12 o'clock position. Built as a
     * fan of quads — each segment is one slice of the ring annulus between
     * {@code innerR} and {@code outerR}. The final segment is capped at the
     * exact progress angle so the leading edge doesn't snap between steps.
     */
    private void drawProgressArc(float cx, float cy, float innerR, float outerR,
                                 float progress, Color color, float alphaMult) {
        progress = Math.max(0f, Math.min(1f, progress));
        if (progress <= 0f) return;
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, alphaMult);

        int filled = (int) Math.ceil(PROGRESS_ARC_SEGMENTS * progress);
        glBegin(GL_QUADS);
        for (int i = 0; i < filled; i++) {
            float t1 = (float) i / PROGRESS_ARC_SEGMENTS;
            float t2 = Math.min(progress, (float) (i + 1) / PROGRESS_ARC_SEGMENTS);
            // Start at 12 o'clock (math angle = +π/2), sweep clockwise (decreasing angle).
            float a1 = (float) (Math.PI / 2.0) - t1 * (float) (Math.PI * 2.0);
            float a2 = (float) (Math.PI / 2.0) - t2 * (float) (Math.PI * 2.0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float c2 = (float) Math.cos(a2), s2 = (float) Math.sin(a2);
            glVertex2f(cx + c1 * innerR, cy + s1 * innerR);
            glVertex2f(cx + c1 * outerR, cy + s1 * outerR);
            glVertex2f(cx + c2 * outerR, cy + s2 * outerR);
            glVertex2f(cx + c2 * innerR, cy + s2 * innerR);
        }
        glEnd();
    }

    /**
     * Renders active tracers as faction-colored lines, fading alpha over
     * remaining lifetime. Hits draw a clean shooter-to-target line; misses
     * draw to the randomized near-miss endpoint baked into the {@link ShotEvent}
     * on emit, so misses read as stray rounds whizzing past the target.
     */
    private void renderShots(List<ShotEvent> shots, float alphaMult) {
        if (shots.isEmpty()) return;

        // Line tracers for marine primary / militia / alien rifle fire. Turret
        // shots and marine secondary (rocket) shots are filtered out — both
        // get sprite-projectile treatment in the second pass.
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        org.lwjgl.opengl.GL11.glLineWidth(2f);
        glBegin(org.lwjgl.opengl.GL11.GL_LINES);
        for (ShotEvent s : shots) {
            // Skip everything that has its own projectile sprite.
            if (s.turretKind != null) continue;
            if (s.marineSecondary != null) continue;
            if (s.marineWeapon != null && s.marineWeapon.projectileSpritePath != null) continue;
            if (s.mechWeapon != null && s.mechWeapon.projectileSpritePath != null) continue;
            float t = Math.max(0f, Math.min(1f, s.lifetime / Math.max(0.001f, s.lifetimeMax)));
            Color c;
            if (s.marineWeapon != null) {
                c = s.marineWeapon.tracerColor;
            } else {
                c = s.shooterFaction == Faction.MARINE ? MARINE_TRACER : DEFENDER_TRACER;
            }
            glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, t * alphaMult);
            float x0 = camera.cellToScreenX(s.fromX);
            float y0 = camera.cellToScreenY(s.fromY);
            float x1 = camera.cellToScreenX(s.toX);
            float y1 = camera.cellToScreenY(s.toY);
            glVertex2f(x0, y0);
            glVertex2f(x1, y1);
        }
        glEnd();
        org.lwjgl.opengl.GL11.glLineWidth(1f);

        // Projectile sprites for turret shots AND marine secondary (rocket)
        // shots — lerp along the from→to vector over SHOT_LIFETIME_REF, rotate
        // to align with travel direction. Source sprites are nose-up (+Y), so
        // setAngle(bearing) directly aligns the nose with the travel vector.
        java.util.Set<TurretKind> touchedTurret = new java.util.HashSet<>();
        java.util.Set<MarineSecondary> touchedSecondary = new java.util.HashSet<>();
        java.util.Set<MarineWeapon> touchedPrimary = new java.util.HashSet<>();
        java.util.Set<com.dillon.starsectormarines.battle.MechWeapon> touchedMech = new java.util.HashSet<>();
        for (ShotEvent s : shots) {
            ShuttleSpriteCache cache;
            float visualCells;
            if (s.turretKind != null) {
                cache = turretProjectileSprites.get(s.turretKind);
                visualCells = s.turretKind.projectileVisualCells;
            } else if (s.marineSecondary != null) {
                cache = marineSecondarySprites.get(s.marineSecondary);
                visualCells = s.marineSecondary.projectileVisualCells;
            } else if (s.marineWeapon != null && s.marineWeapon.projectileSpritePath != null) {
                cache = marineWeaponProjectileSprites.get(s.marineWeapon);
                visualCells = s.marineWeapon.projectileVisualCells;
            } else if (s.mechWeapon != null && s.mechWeapon.projectileSpritePath != null) {
                cache = mechWeaponProjectileSprites.get(s.mechWeapon);
                visualCells = s.mechWeapon.projectileVisualCells;
            } else {
                continue;
            }
            if (cache == null) continue;
            float progress = 1f - Math.max(0f, Math.min(1f, s.lifetime / Math.max(0.001f, s.lifetimeMax)));
            float px = s.fromX + (s.toX - s.fromX) * progress;
            float py = s.fromY + (s.toY - s.fromY) * progress;
            // Parabolic arc — peaks at progress=0.5 with the kind's arcHeight.
            // Visual only; the sim's endpoint is unchanged. Bearing is computed
            // from the analytical tangent so the round noses up at launch and
            // tips down on descent, instead of always pointing at the endpoint.
            float bearing;
            float arcH = 0f;
            if (s.mechWeapon != null) arcH = s.mechWeapon.arcHeight;
            else if (s.turretKind != null) arcH = s.turretKind.arcHeight;
            if (arcH > 0f) {
                py += arcH * 4f * progress * (1f - progress);
                float tangentDy = (s.toY - s.fromY) + arcH * 4f * (1f - 2f * progress);
                bearing = bearingDeg(0f, 0f, s.toX - s.fromX, tangentDy);
            } else {
                bearing = bearingDeg(s.fromX, s.fromY, s.toX, s.toY);
            }
            SpriteAPI sprite = cache.sprite;
            float cellPxLocal = camera.cellPxSize();
            float pxH = visualCells * cellPxLocal;
            float pxW = pxH * cache.aspect;
            sprite.setSize(pxW, pxH);
            sprite.setAngle(bearing);
            sprite.setAlphaMult(alphaMult);
            sprite.setNormalBlend();
            sprite.setColor(Color.WHITE);
            sprite.renderAtCenter(camera.cellToScreenX(px), camera.cellToScreenY(py));
            // Trail — emit one particle per render frame at the round's tail
            // (slight offset opposite the travel direction). Short lifetimes
            // mean successive frames overlap into a fading streak. Mech rockets
            // get the hot engine glow; grenade-launcher shells get a gunpowder
            // smoke trail (gray, non-additive).
            boolean engineTrail = s.mechWeapon != null && s.mechWeapon.engineTrail;
            boolean smokeTrail  = s.turretKind != null && s.turretKind.smokeTrail;
            if ((engineTrail || smokeTrail) && progress > 0.02f && progress < 0.98f) {
                // Approximate the tail position as the round's current cell
                // position minus a small step along its current heading. Cheap
                // and the visual difference vs. exact-back-of-sprite is nil.
                float headingRad = (float) Math.toRadians(bearing);
                float tailDx = -(float) Math.sin(headingRad) * 0.15f;
                float tailDy = -(float) Math.cos(headingRad) * 0.15f;
                if (engineTrail) impactFx.spawnEngineTrail(px + tailDx, py + tailDy, 0.18f);
                else             impactFx.spawnSmokeTrail (px + tailDx, py + tailDy, 0.20f);
            }
            if (s.turretKind != null) touchedTurret.add(s.turretKind);
            else if (s.marineSecondary != null) touchedSecondary.add(s.marineSecondary);
            else if (s.marineWeapon != null) touchedPrimary.add(s.marineWeapon);
            else if (s.mechWeapon != null) touchedMech.add(s.mechWeapon);
        }
        for (TurretKind k : touchedTurret) {
            ShuttleSpriteCache c = turretProjectileSprites.get(k);
            if (c != null) c.sprite.setAngle(0f);
        }
        for (MarineSecondary k : touchedSecondary) {
            ShuttleSpriteCache c = marineSecondarySprites.get(k);
            if (c != null) c.sprite.setAngle(0f);
        }
        for (MarineWeapon k : touchedPrimary) {
            ShuttleSpriteCache c = marineWeaponProjectileSprites.get(k);
            if (c != null) c.sprite.setAngle(0f);
        }
        for (com.dillon.starsectormarines.battle.MechWeapon k : touchedMech) {
            ShuttleSpriteCache c = mechWeaponProjectileSprites.get(k);
            if (c != null) c.sprite.setAngle(0f);
        }
    }

    /** Starsector sprite-angle convention: 0° = +Y (north), positive clockwise. */
    private static float bearingDeg(float fromX, float fromY, float toX, float toY) {
        float dx = toX - fromX;
        float dy = toY - fromY;
        if (dx == 0f && dy == 0f) return 0f;
        return (float) Math.toDegrees(Math.atan2(dy, dx)) - 90f;
    }

    private enum Facing { WEST, NORTH, EAST, SOUTH }

    /** Prefer target direction (units face their target while attacking); fall back to movement; default south. */
    private static Facing computeFacing(Unit u) {
        if (u.target != null && u.target.isAlive()) {
            int dx = u.target.cellX - u.cellX;
            int dy = u.target.cellY - u.cellY;
            if (dx != 0 || dy != 0) return facingFromDelta(dx, dy);
        }
        if (u.pathIdx < u.pathCellCount()) {
            int dx = u.pathCellX(u.pathIdx) - u.cellX;
            int dy = u.pathCellY(u.pathIdx) - u.cellY;
            if (dx != 0 || dy != 0) return facingFromDelta(dx, dy);
        }
        return Facing.SOUTH;
    }

    private static Facing facingFromDelta(int dx, int dy) {
        if (Math.abs(dx) > Math.abs(dy)) return dx > 0 ? Facing.EAST : Facing.WEST;
        return dy > 0 ? Facing.NORTH : Facing.SOUTH;
    }

    /** Maps (facing, weaponUp) to a slot in the sheet. South+weaponUp uses slot 6 + a vertical flip. */
    private static int pickFrame(Facing facing, boolean weaponUp) {
        if (weaponUp) {
            switch (facing) {
                case WEST:  return 4;
                case EAST:  return 5;
                case NORTH: return 6;
                case SOUTH: return 6; // vertical mirror applied at draw time
            }
        } else {
            switch (facing) {
                case WEST:  return 0;
                case NORTH: return 1;
                case EAST:  return 2;
                case SOUTH: return 3;
            }
        }
        return 3;
    }

    /**
     * 8-way facing used by {@link UnitType.FrameLayout#EIGHT_WAY_NO_WEAPON_UP}.
     * Computed the same way as {@link #computeFacing}: prefer target direction,
     * fall back to current movement, default S. The 8-way variant exists so the
     * mech sheet's diagonal poses (NW/NE/SW/SE) get used instead of being
     * snapped to a cardinal.
     */
    private enum EightWayFacing { W, NW, N, NE, E, SE, S, SW }

    private static EightWayFacing computeEightWayFacing(Unit u) {
        if (u.target != null && u.target.isAlive()) {
            int dx = u.target.cellX - u.cellX;
            int dy = u.target.cellY - u.cellY;
            if (dx != 0 || dy != 0) return eightWayFromDelta(dx, dy);
        }
        if (u.pathIdx < u.pathCellCount()) {
            int dx = u.pathCellX(u.pathIdx) - u.cellX;
            int dy = u.pathCellY(u.pathIdx) - u.cellY;
            if (dx != 0 || dy != 0) return eightWayFromDelta(dx, dy);
        }
        return EightWayFacing.S;
    }

    /**
     * Bucket a (dx, dy) delta into the nearest octant. The split is by the
     * abs-ratio: when the smaller axis is at least ~41% of the larger we read
     * the delta as a diagonal, otherwise it snaps to the closer cardinal. The
     * 0.414 constant is {@code tan(22.5°)} — the midpoint between a cardinal
     * and the adjacent diagonal in polar angle, so each octant covers a 45°
     * wedge.
     */
    private static EightWayFacing eightWayFromDelta(int dx, int dy) {
        int adx = Math.abs(dx);
        int ady = Math.abs(dy);
        boolean diag = Math.min(adx, ady) * 1000 >= Math.max(adx, ady) * 414;
        if (diag) {
            if (dx > 0 && dy > 0) return EightWayFacing.NE;
            if (dx > 0 && dy < 0) return EightWayFacing.SE;
            if (dx < 0 && dy > 0) return EightWayFacing.NW;
            return EightWayFacing.SW;
        }
        if (adx > ady) return dx > 0 ? EightWayFacing.E : EightWayFacing.W;
        return dy > 0 ? EightWayFacing.N : EightWayFacing.S;
    }

    /**
     * Maps 8-way facing to the heavy-mech sheet's frame index. The mech sheet
     * has no dedicated N frame — when the mech happens to face N we fall back
     * to NW (an arbitrary but visually plausible diagonal) rather than reuse
     * a flipped frame, which would look wrong since the mech is asymmetric
     * (chaingun arm vs. rocket-pod arm).
     *
     * <p>Layout (from the source PNG, left to right):
     * <pre>
     *   0 W   1 NW   2 SE   3 S   4 SW   5 NE   6 E
     * </pre>
     */
    private static int pickFrameEightWay(EightWayFacing f) {
        switch (f) {
            case W:  return 0;
            case NW: return 1;
            case SE: return 2;
            case S:  return 3;
            case SW: return 4;
            case NE: return 5;
            case E:  return 6;
            case N:  return 1; // no dedicated N — borrow NW
        }
        return 3;
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
