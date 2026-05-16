package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.BattleSetup;
import com.dillon.starsectormarines.battle.Doodad;
import com.dillon.starsectormarines.battle.EquipmentDrop;
import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.ShotEvent;
import com.dillon.starsectormarines.battle.Shuttle;
import com.dillon.starsectormarines.battle.ShuttleType;
import com.dillon.starsectormarines.battle.SpriteSheetFrames;
import com.dillon.starsectormarines.battle.SpriteSheetSlicer;
import com.dillon.starsectormarines.battle.TileManifest;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.flyby.FlybyOverlay;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.objective.ChargeSiteObjective;
import com.dillon.starsectormarines.battle.objective.Objective;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ui.ButtonWidget;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.LabelWidget;
import com.dillon.starsectormarines.ui.WidgetRoot;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import org.apache.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glColor4f;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glEnd;
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
public class BattleScreen implements Screen {

    private static final Logger LOG = Global.getLogger(BattleScreen.class);

    private static final Color FLOOR_COLOR    = new Color(0x18, 0x22, 0x30);
    private static final Color WALL_COLOR     = new Color(0x06, 0x0A, 0x10);
    private static final Color GRID_LINE      = new Color(0x25, 0x32, 0x44);
    private static final Color MARINE_COLOR   = new Color(0x5A, 0xA0, 0xE0);
    private static final Color DEFENDER_COLOR = new Color(0xE0, 0x6A, 0x6A);
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

    /** Marine sprite sheet path (Starsector resource lookup). */
    private static final String MARINE_SHEET  = "graphics/battle/marine.png";

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

    /**
     * Pool of ambient loop sound-ids the battle picks 1-2 from at attach time for environmental
     * background. Subway-train and wind-up-long are intentionally excluded — they feel more like
     * situational cues than ambient bed; trivial to flip in if a battle wants the urban-rail or
     * winding-mechanism vibe.
     */
    private static final String[] AMBIENT_LOOP_POOL = {
            "marines_ambient_fan_noise",
            "marines_ambient_fan_reso_1",
            "marines_ambient_fan_reso_2",
            "marines_ambient_fan_reso_3",
            "marines_ambient_motor_1",
            "marines_ambient_motor_2",
            "marines_ambient_loudmotor_3",
            "marines_ambient_radiator_1",
            "marines_ambient_helicopter_2",
    };
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
    /** Multiplicative tint applied to defender sprites (marines are untinted). */
    private static final Color DEFENDER_TINT  = new Color(0xE0, 0x90, 0x90);

    private static final float[] SPEED_OPTIONS = {0f, 1f, 2f, 4f};
    private static final String[] SPEED_KEYS   = {
            "battleSpeedPause", "battleSpeed1x", "battleSpeed2x", "battleSpeed4x"
    };

    private final WidgetRoot widgets = new WidgetRoot();

    private PositionAPI position;
    private MarineOpsContext ctx;
    private BattleLayout layout;
    private float speedMultiplier = 1f;
    /** Pixel x-center of each speed button, captured at layout time for the active-marker dot. */
    private final float[] speedBtnCenterX = new float[SPEED_OPTIONS.length];
    private float speedBtnBottomY;
    /** Tracks the last-seen sim completion flag so we can rebuild widgets when it flips. */
    private boolean lastSimComplete;
    /** Cached marine sprite sheet — lazy-loaded once per Screen lifetime. Null if load failed. */
    private SpriteAPI marineSheet;
    /** Detected sprite bounding boxes on {@link #marineSheet}. Null until sheet is loaded. */
    private SpriteSheetFrames marineFrames;
    private boolean marineSheetLoadAttempted;
    /** Cached tileset sheet — lazy-loaded once per Screen lifetime. Null if load failed. */
    private SpriteAPI tileSheet;
    /** Pixel dimensions of the tileset PNG content (pre-POT-padding). */
    private int tileSheetPxW;
    private int tileSheetPxH;
    private boolean tileSheetLoadAttempted;

    /**
     * Cached shuttle sprite + natural aspect ratio (width/height) per ShuttleType.
     * Captured at load time before any setSize call mutates getWidth/getHeight.
     * EnumMap is overkill for one variant today; map keeps the door open for
     * Valkyrie + heavier dropships in Phase 2 without restructuring.
     */
    private final java.util.EnumMap<ShuttleType, ShuttleSpriteCache> shuttleSprites = new java.util.EnumMap<>(ShuttleType.class);
    private boolean shuttleSpritesLoadAttempted;

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
    /** Real-time countdown (seconds) until the next sporadic distant explosion. */
    private float distantBoomTimer;
    /** RNG for audio variety — separate from sim.rng so audio randomness doesn't perturb sim determinism. */
    private final java.util.Random audioRng = new java.util.Random();

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
        ensureMarineSheet();
        ensureTileSheet();
        ensureShuttleSprites();
        ensureObjectiveIcons();
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
        activeAmbientLoops = pickAmbientLoops();
        distantBoomTimer = nextDistantBoomGap();
    }

    /** Picks 1-2 random ambient loop ids from {@link #AMBIENT_LOOP_POOL} for this battle. */
    private String[] pickAmbientLoops() {
        int count = 1 + audioRng.nextInt(2); // 1 or 2
        java.util.List<String> shuffled = new java.util.ArrayList<>(java.util.Arrays.asList(AMBIENT_LOOP_POOL));
        java.util.Collections.shuffle(shuffled, audioRng);
        return shuffled.subList(0, Math.min(count, shuffled.size())).toArray(new String[0]);
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
                LOG.info("BattleScreen: loaded tileset " + TileManifest.SHEET
                        + " (" + tileSheetPxW + "x" + tileSheetPxH + ")");
            }
        } catch (Exception e) {
            LOG.error("BattleScreen: failed to load tileset " + TileManifest.SHEET, e);
            tileSheet = null;
        }
    }

    /**
     * Lazy-loads each {@link ShuttleType}'s sprite via the vanilla path lookup.
     * Captures the natural aspect ratio before any setSize call mutates it, so
     * render math can preserve the ship's drawn proportions across cellSize
     * changes. Same lazy-load pattern as the marine sheet — getSprite returns
     * a wrapper whose backing texture is null until loadTexture is called.
     */
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

    private void ensureMarineSheet() {
        if (marineSheetLoadAttempted) return;
        marineSheetLoadAttempted = true;
        try {
            Global.getSettings().loadTexture(MARINE_SHEET);
            marineSheet = Global.getSettings().getSprite(MARINE_SHEET);
            if (marineSheet == null) {
                LOG.warn("BattleScreen: getSprite returned null for " + MARINE_SHEET);
                return;
            }
            try (java.io.InputStream stream = Global.getSettings().openStream(MARINE_SHEET)) {
                BufferedImage img = ImageIO.read(stream);
                if (img == null) {
                    LOG.warn("BattleScreen: ImageIO.read returned null for " + MARINE_SHEET);
                    return;
                }
                marineFrames = SpriteSheetSlicer.slice(img);
                LOG.info("BattleScreen: auto-sliced " + MARINE_SHEET + " — "
                        + marineFrames.frames.length + " frames detected");
            }
        } catch (Exception e) {
            LOG.error("BattleScreen: failed to load marine sheet " + MARINE_SHEET, e);
            marineSheet = null;
            marineFrames = null;
        }
    }

    private void rebuild() {
        widgets.clear();
        if (position == null || ctx == null) return;

        BattleSimulation sim = ctx.getBattleSimulation();
        int gridW = sim != null ? sim.getGrid().getWidth()  : BattleSetup.GRID_W;
        int gridH = sim != null ? sim.getGrid().getHeight() : BattleSetup.GRID_H;
        layout = new BattleLayout(position, gridW, gridH);
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
        flybyOverlay.advance(dt * speedMultiplier, sim, layout);
        driveShuttleEngineLoop(sim);
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
        if (!audioActive) return;
        audioActive = false;
        // Fade out our track without queuing a replacement, then let the campaign music resume.
        Global.getSoundPlayer().playCustomMusic(MUSIC_FADE_SECS, 0, null);
        Global.getSoundPlayer().setSuspendDefaultMusicPlayback(false);
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
        for (String id : activeAmbientLoops) {
            Global.getSoundPlayer().playUILoop(id, 1f, AMBIENT_VOLUME);
        }
        distantBoomTimer -= dt;
        if (distantBoomTimer <= 0f) {
            playDistantBoom();
            distantBoomTimer = nextDistantBoomGap();
        }
    }

    private void playDistantBoom() {
        float jitter = 1f + (audioRng.nextFloat() * 2f - 1f) * DISTANT_BOOM_PITCH_JITTER;
        if (audioRng.nextFloat() < DISTANT_BOOM_MUFFLED_CHANCE) {
            Global.getSoundPlayer().playUISound(SFX_DISTANT_BOOM, jitter, DISTANT_BOOM_VOLUME);
        } else {
            // Pool explosion + sub-1 pitch + low volume reads as a far-off blast (the muffled clip
            // is great but having only one source for distant booms gets repetitive).
            Global.getSoundPlayer().playUISound(SFX_NEAR_EXPLOSION,
                    NEAR_AS_DISTANT_PITCH * jitter, NEAR_AS_DISTANT_VOLUME);
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
    private void playCombatEventSounds(BattleSimulation sim) {
        java.util.Random rng = java.util.concurrent.ThreadLocalRandom.current();
        for (ShotEvent ignored : sim.getShotsThisFrame()) {
            float pitch = 1f + (rng.nextFloat() * 2f - 1f) * RIFLE_PITCH_JITTER;
            Global.getSoundPlayer().playUISound(SFX_RIFLE, pitch, RIFLE_VOLUME);
        }
        for (Unit u : sim.getDeathsThisFrame()) {
            if (u.faction == Faction.MARINE) {
                Global.getSoundPlayer().playUISound(SFX_VOICE_DEAD, 1f, 1f);
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
        handleDebugDamageInput(events);
        handleDebugZoneToggle(events);
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
        if (events == null || layout == null || ctx == null) return;
        BattleSimulation sim = ctx.getBattleSimulation();
        if (sim == null) return;
        NavigationGrid grid = sim.getGrid();
        for (InputEventAPI e : events) {
            if (e.isConsumed()) continue;
            if (!e.isRMBDownEvent() || !e.isShiftDown()) continue;
            float px = e.getX();
            float py = e.getY();
            if (px < layout.gridX || px >= layout.gridX + layout.gridW
                    || py < layout.gridY || py >= layout.gridY + layout.gridH) continue;
            int cx = (int) Math.floor((px - layout.gridX) / layout.cellSize);
            int cy = (int) Math.floor((py - layout.gridY) / layout.cellSize);
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
            renderGrid(sim.getGrid(), alphaMult);
            if (debugZonesVisible) renderZoneOverlay(sim, alphaMult);
            renderDoodads(sim, alphaMult);
            renderUnits(sim.getUnits(), alphaMult);
            // Charge sites + equipment drops sit above units so the player can
            // always see where the objectives are — even while a marine stands
            // on top of one. Shuttles still draw on top of the markers when
            // landing on a LZ.
            renderObjectiveMarkers(sim, alphaMult);
            renderShuttles(sim.getShuttles(), alphaMult);
            renderShots(sim.getActiveShots(), alphaMult);
            // Flyby layer lives above everything ground-side so strafing tracers and
            // engine glows punch over units / shots / shuttles without being occluded.
            flybyOverlay.render(layout, alphaMult);
        }

        renderSpeedMarker(alphaMult);

        if (sim != null && sim.isComplete()) {
            renderBanner(sim.getWinner(), alphaMult);
        }

        widgets.render(alphaMult);
    }

    // ---- rendering ---------------------------------------------------------

    /** Hide grid lines below this cell size — at small scales they read as visual noise rather than structure. */
    private static final float GRID_LINE_MIN_CELL = 16f;

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
    private void renderTiledFloorsAndWalls(NavigationGrid grid, float alphaMult) {
        float texW = tileSheet.getTextureWidth();
        float texH = tileSheet.getTextureHeight();
        float texXScale = texW / tileSheetPxW;
        float texYScale = texH / tileSheetPxH;
        int sheetPxH = tileSheetPxH;

        // Floor pass — directional autotile pick. OOB reads as open, so map-edge streets stay center-tiled.
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                if (!grid.isWalkable(x, y)) continue;
                boolean nWall = isInBoundsWall(grid, x, y + 1);
                boolean sWall = isInBoundsWall(grid, x, y - 1);
                boolean eWall = isInBoundsWall(grid, x + 1, y);
                boolean wWall = isInBoundsWall(grid, x - 1, y);
                TileManifest.TileFrame floor = grid.isRubble(x, y)
                        ? TileManifest.pickRubbleTile(nWall, sWall, eWall, wWall)
                        : TileManifest.pickFloorTile(nWall, sWall, eWall, wWall);
                drawTile(floor, x, y, texXScale, texYScale, sheetPxH, alphaMult);

                // Original doorway (punched through a wall at gen time) gets
                // the overhead door overlay. Breached cells are flagged rubble
                // AND doorway — the !isRubble guard keeps the overhead off
                // those, since they're holes blasted through, not real doors.
                if (grid.isDoorway(x, y) && !grid.isRubble(x, y)) {
                    drawTile(TileManifest.DOOR_OPEN, x, y, texXScale, texYScale, sheetPxH, alphaMult);
                }
            }
        }

        // Wall pass — autotile pick from neighbor exposure. Interior walls
        // (all four neighbors are walls) draw a solid fill since the source's
        // interior cell is transparent.
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                if (grid.isWalkable(x, y)) continue;
                boolean nWall = isWallOrOob(grid, x, y + 1);
                boolean sWall = isWallOrOob(grid, x, y - 1);
                boolean eWall = isWallOrOob(grid, x + 1, y);
                boolean wWall = isWallOrOob(grid, x - 1, y);
                TileManifest.TileFrame tile = TileManifest.pickWallTile(nWall, sWall, eWall, wWall);
                if (tile == null) {
                    fillCell(x, y, WALL_COLOR, alphaMult);
                } else {
                    drawTile(tile, x, y, texXScale, texYScale, sheetPxH, alphaMult);
                }
            }
        }
    }

    /** Solid quad covering one nav-grid cell — used as the interior-wall fallback. */
    private void fillCell(int gridX, int gridY, Color color, float alphaMult) {
        float x0 = layout.gridX + gridX * layout.cellSize;
        float y0 = layout.gridY + gridY * layout.cellSize;
        fillRect(x0, y0, layout.cellSize, layout.cellSize, color, alphaMult);
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
                float x0 = layout.gridX + x * layout.cellSize;
                float y0 = layout.gridY + y * layout.cellSize;
                float x1 = x0 + layout.cellSize;
                float y1 = y0 + layout.cellSize;
                glVertex2f(x0, y0);
                glVertex2f(x1, y0);
                glVertex2f(x1, y1);
                glVertex2f(x0, y1);
            }
        }
        glEnd();
    }

    /** Out-of-bounds cells act as walls for autotile purposes — a building flush against the map edge gets a real edge tile, not an open-air tile. */
    private static boolean isWallOrOob(NavigationGrid grid, int x, int y) {
        if (!grid.inBounds(x, y)) return true;
        return !grid.isWalkable(x, y);
    }

    /** True for in-bounds non-walkable cells. Out-of-bounds reads as open ground for floor picking — streets at the map edge stay center-tiled instead of getting framed against nothing. */
    private static boolean isInBoundsWall(NavigationGrid grid, int x, int y) {
        if (!grid.inBounds(x, y)) return false;
        return !grid.isWalkable(x, y);
    }

    /** Renders the doodad layer (chairs, crates, chest, etc.) on top of floors and below units. */
    private void renderDoodads(BattleSimulation sim, float alphaMult) {
        if (tileSheet == null) return;
        float texW = tileSheet.getTextureWidth();
        float texH = tileSheet.getTextureHeight();
        float texXScale = texW / tileSheetPxW;
        float texYScale = texH / tileSheetPxH;
        int sheetPxH = tileSheetPxH;
        for (Doodad d : sim.getDoodads()) {
            drawTile(d.tile, d.cellX, d.cellY, texXScale, texYScale, sheetPxH, alphaMult);
        }
    }

    /** Draws a single 1×1 {@link TileManifest.TileFrame} into one grid cell. */
    private void drawTile(TileManifest.TileFrame f, int gridX, int gridY,
                          float texXScale, float texYScale, int sheetPxH, float alphaMult) {
        int srcPxX = f.col * TileManifest.TILE_SIZE;
        int srcTopPxY = f.row * TileManifest.TILE_SIZE;
        int srcPxW = TileManifest.TILE_SIZE;
        int srcPxH = TileManifest.TILE_SIZE;

        tileSheet.setTexX(srcPxX * texXScale);
        tileSheet.setTexY((sheetPxH - (srcTopPxY + srcPxH)) * texYScale);
        tileSheet.setTexWidth(srcPxW * texXScale);
        tileSheet.setTexHeight(srcPxH * texYScale);
        tileSheet.setSize(layout.cellSize, layout.cellSize);
        tileSheet.setAlphaMult(alphaMult);
        tileSheet.setColor(Color.WHITE);
        tileSheet.setNormalBlend();

        float cx = layout.gridX + (gridX + 0.5f) * layout.cellSize;
        float cy = layout.gridY + (gridY + 0.5f) * layout.cellSize;
        tileSheet.renderAtCenter(cx, cy);
    }

    /** Stable per-cell hash for picking from tile pools — same cell always picks the same tile. */
    private static int cellHash(int x, int y) {
        int h = x * 73856093 ^ y * 19349663;
        return h & 0x7FFFFFFF;
    }

    private void renderGrid(NavigationGrid grid, float alphaMult) {
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // One floor quad covering the whole arena. Acts as the floor color when
        // the tileset hasn't loaded, and as a safety underlay if any tile sprite
        // happens to be transparent.
        glColor4f(FLOOR_COLOR.getRed() / 255f, FLOOR_COLOR.getGreen() / 255f,
                FLOOR_COLOR.getBlue() / 255f, alphaMult);
        glBegin(GL_QUADS);
        glVertex2f(layout.gridX,                  layout.gridY);
        glVertex2f(layout.gridX + layout.gridW,   layout.gridY);
        glVertex2f(layout.gridX + layout.gridW,   layout.gridY + layout.gridH);
        glVertex2f(layout.gridX,                  layout.gridY + layout.gridH);
        glEnd();

        if (tileSheet != null) {
            renderTiledFloorsAndWalls(grid, alphaMult);
        } else {
            // Fallback — solid-color walls. Same path the renderer used before
            // the tileset landed; kept so the sim still reads if texture load fails.
            glColor4f(WALL_COLOR.getRed() / 255f, WALL_COLOR.getGreen() / 255f,
                    WALL_COLOR.getBlue() / 255f, alphaMult);
            glBegin(GL_QUADS);
            for (int y = 0; y < grid.getHeight(); y++) {
                for (int x = 0; x < grid.getWidth(); x++) {
                    if (grid.isWalkable(x, y)) continue;
                    float x0 = layout.gridX + x * layout.cellSize;
                    float y0 = layout.gridY + y * layout.cellSize;
                    float x1 = x0 + layout.cellSize;
                    float y1 = y0 + layout.cellSize;
                    glVertex2f(x0, y0);
                    glVertex2f(x1, y0);
                    glVertex2f(x1, y1);
                    glVertex2f(x0, y1);
                }
            }
            glEnd();
        }

        // Grid lines — only when cells are big enough to read.
        if (layout.cellSize >= GRID_LINE_MIN_CELL) {
            glColor4f(GRID_LINE.getRed() / 255f, GRID_LINE.getGreen() / 255f,
                    GRID_LINE.getBlue() / 255f, 0.4f * alphaMult);
            glBegin(org.lwjgl.opengl.GL11.GL_LINES);
            for (int x = 0; x <= grid.getWidth(); x++) {
                float px = layout.gridX + x * layout.cellSize;
                glVertex2f(px, layout.gridY);
                glVertex2f(px, layout.gridY + layout.gridH);
            }
            for (int y = 0; y <= grid.getHeight(); y++) {
                float py = layout.gridY + y * layout.cellSize;
                glVertex2f(layout.gridX,                py);
                glVertex2f(layout.gridX + layout.gridW, py);
            }
            glEnd();
        }
    }

    private void renderUnits(List<Unit> units, float alphaMult) {
        float unitSize = layout.cellSize * UNIT_FRAC;
        float half = unitSize / 2f;

        SpriteAPI sheet = marineSheet;
        SpriteSheetFrames frames = marineFrames;
        if (sheet != null && frames != null && frames.frames.length > 0) {
            float texW = sheet.getTextureWidth();
            float texH = sheet.getTextureHeight();
            int sheetW = frames.sheetWidth;
            int sheetH = frames.sheetHeight;

            for (Unit u : units) {
                if (!u.isAlive()) continue;

                Facing facing = computeFacing(u);
                boolean weaponUp = u.cooldownTimer > (u.attackCooldown - WEAPON_UP_TIME)
                        && u.cooldownTimer > 0f;
                int frameIdx = pickFrame(facing, weaponUp);
                if (frameIdx >= frames.frames.length) frameIdx = 0; // safety
                boolean flipY = weaponUp && facing == Facing.SOUTH;
                SpriteSheetFrames.Frame f = frames.frames[frameIdx];

                // Convert pixel bbox into the texture-fraction coords setTex* expects.
                // V is measured from the bottom of the texture in Starsector's GL convention.
                sheet.setTexX((float) f.x * texW / sheetW);
                sheet.setTexWidth((float) f.w * texW / sheetW);
                if (flipY) {
                    // Vertical mirror — negative texHeight, anchor at the top of the frame.
                    sheet.setTexY((float) (sheetH - f.y) * texH / sheetH);
                    sheet.setTexHeight(-(float) f.h * texH / sheetH);
                } else {
                    sheet.setTexY((float) (sheetH - f.y - f.h) * texH / sheetH);
                    sheet.setTexHeight((float) f.h * texH / sheetH);
                }

                // Preserve the frame's aspect ratio so weapon-up poses with extended
                // guns can be wider than idle poses without squishing.
                float targetH = unitSize;
                float targetW = targetH * f.w / (float) f.h;
                sheet.setSize(targetW, targetH);
                sheet.setAlphaMult(alphaMult);
                sheet.setNormalBlend();
                sheet.setColor(u.faction == Faction.MARINE ? Color.WHITE : DEFENDER_TINT);

                float cx = layout.gridX + (u.renderX + 0.5f) * layout.cellSize;
                float cy = layout.gridY + (u.renderY + 0.5f) * layout.cellSize;
                sheet.renderAtCenter(cx, cy);
            }
            // Reset tint so the singleton sprite doesn't carry our red into
            // anything that draws it next session.
            sheet.setColor(Color.WHITE);
        } else {
            // Sprite missing — fall back to colored quads so the sim is still readable.
            glDisable(GL_TEXTURE_2D);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glBegin(GL_QUADS);
            for (Unit u : units) {
                if (!u.isAlive()) continue;
                Color c = (u.faction == Faction.MARINE) ? MARINE_COLOR : DEFENDER_COLOR;
                glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, alphaMult);
                float cx = layout.gridX + (u.renderX + 0.5f) * layout.cellSize;
                float cy = layout.gridY + (u.renderY + 0.5f) * layout.cellSize;
                glVertex2f(cx - half, cy - half);
                glVertex2f(cx + half, cy - half);
                glVertex2f(cx + half, cy + half);
                glVertex2f(cx - half, cy + half);
            }
            glEnd();
        }

        // HP bars above each unit (always, regardless of sprite fallback)
        for (Unit u : units) {
            if (!u.isAlive()) continue;
            float cx = layout.gridX + (u.renderX + 0.5f) * layout.cellSize;
            float cy = layout.gridY + (u.renderY + 0.5f) * layout.cellSize;
            float barW = unitSize;
            float barX = cx - barW / 2f;
            float barY = cy + half + HP_BAR_GAP;
            fillRect(barX, barY, barW, HP_BAR_H, HP_BG, alphaMult);
            float frac = Math.max(0f, Math.min(1f, u.hp / u.maxHp));
            fillRect(barX, barY, barW * frac, HP_BAR_H, HP_FG, alphaMult);
        }
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
            float pxLen = s.type.visualLengthCells * layout.cellSize * s.scaleMult;
            float pxH = pxLen;
            float pxW = pxLen * cache.aspect;
            sprite.setSize(pxW, pxH);
            sprite.setAngle(s.facingDegrees);
            sprite.setAlphaMult(alphaMult);
            sprite.setNormalBlend();
            sprite.setColor(Color.WHITE);
            float cx = layout.gridX + s.worldX * layout.cellSize;
            float cy = layout.gridY + s.worldY * layout.cellSize;
            sprite.renderAtCenter(cx, cy);
        }
        // Reset angle so the singleton sprite doesn't carry our rotation
        // into whatever else might draw it.
        for (ShuttleSpriteCache cache : shuttleSprites.values()) {
            cache.sprite.setAngle(0f);
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

        for (Objective o : sim.getObjectives()) {
            if (!(o instanceof ChargeSiteObjective)) continue;
            ChargeSiteObjective site = (ChargeSiteObjective) o;
            float cx = layout.gridX + (site.cellX() + 0.5f) * layout.cellSize;
            float cy = layout.gridY + (site.cellY() + 0.5f) * layout.cellSize;
            if (site.isComplete()) {
                drawTintedIcon(iconDanger, cx, cy,
                        layout.cellSize * CHARGE_ICON_SIZE,
                        CHARGE_TINT_COMPLETE, alphaMult);
            } else {
                float pulse = site.planterOnSite()
                        ? 1f + CHARGE_PULSE_AMP * (float) Math.sin(now * 2.0 * Math.PI * CHARGE_PULSE_HZ)
                        : 1f;
                drawTintedIcon(iconAlarm, cx, cy,
                        layout.cellSize * CHARGE_ICON_SIZE * pulse,
                        CHARGE_TINT_ACTIVE, alphaMult);
                float progress = site.progress() / Math.max(0.001f, site.plantDuration());
                float innerR = layout.cellSize * 0.55f;
                float outerR = innerR + Math.max(3f, layout.cellSize * 0.12f);
                drawProgressArc(cx, cy, innerR, outerR, progress, CHARGE_TINT_ARC, alphaMult);
            }
        }

        for (EquipmentDrop drop : sim.getEquipmentDrops()) {
            if (drop.consumed) continue;
            float cx = layout.gridX + (drop.cellX + 0.5f) * layout.cellSize;
            float cy = layout.gridY + (drop.cellY + 0.5f) * layout.cellSize;
            float pulse = 1f + KIT_DROP_PULSE_AMP * (float) Math.sin(now * 2.0 * Math.PI * KIT_DROP_PULSE_HZ);
            drawTintedIcon(iconStar, cx, cy,
                    layout.cellSize * KIT_DROP_SIZE * pulse,
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
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        org.lwjgl.opengl.GL11.glLineWidth(2f);
        glBegin(org.lwjgl.opengl.GL11.GL_LINES);
        for (ShotEvent s : shots) {
            float t = Math.max(0f, Math.min(1f, s.lifetime / SHOT_LIFETIME_REF));
            Color c = s.shooterFaction == Faction.MARINE ? MARINE_TRACER : DEFENDER_TRACER;
            glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, t * alphaMult);
            float x0 = layout.gridX + s.fromX * layout.cellSize;
            float y0 = layout.gridY + s.fromY * layout.cellSize;
            float x1 = layout.gridX + s.toX   * layout.cellSize;
            float y1 = layout.gridY + s.toY   * layout.cellSize;
            glVertex2f(x0, y0);
            glVertex2f(x1, y1);
        }
        glEnd();
        org.lwjgl.opengl.GL11.glLineWidth(1f);
    }

    private enum Facing { WEST, NORTH, EAST, SOUTH }

    /** Prefer target direction (units face their target while attacking); fall back to movement; default south. */
    private static Facing computeFacing(Unit u) {
        if (u.target != null && u.target.isAlive()) {
            int dx = u.target.cellX - u.cellX;
            int dy = u.target.cellY - u.cellY;
            if (dx != 0 || dy != 0) return facingFromDelta(dx, dy);
        }
        if (!u.path.isEmpty() && u.pathIdx < u.path.size()) {
            int[] next = u.path.get(u.pathIdx);
            int dx = next[0] - u.cellX;
            int dy = next[1] - u.cellY;
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
