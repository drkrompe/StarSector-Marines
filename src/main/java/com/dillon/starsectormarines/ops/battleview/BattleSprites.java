package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.battle.turret.TurretKind;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.VehicleKind;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.battle.world.tiles.NatureTileset;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetFrames;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetSlicer;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.battle.world.tiles.UrbanTile3Tileset;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.apache.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

/**
 * Asset/sprite-cache registry for the battle screen. Owns all loaded
 * {@link SpriteAPI} sheets, their {@link SpriteSheetFrames}, the content
 * px-dimensions, per-type {@link java.util.EnumMap} caches, and every
 * {@code ensure*}/{@code load*} method. The per-sheet {@code QuadBatch}es
 * stay in {@code BattleScreen} (Story B will move them to BattleRenderer).
 */
public class BattleSprites {

    private static final Logger LOG = Global.getLogger(BattleSprites.class);

    // ---- asset-path constants -----------------------------------------------

    private static final String SPRITE_DECAL_SHEET  = "graphics/decals/decals.png";
    private static final String ENGINE_FLAME_SPRITE = "graphics/fx/engineflame32.png";
    private static final String ENGINE_GLOW_SPRITE  = "graphics/fx/engineglow32.png";
    private static final String ICON_ALARM          = "graphics/icons/Alarm 512 px.png";
    private static final String ICON_DANGER         = "graphics/icons/Danger sign 1 512 px.png";
    private static final String ICON_STAR           = "graphics/icons/Star 512 px.png";

    // ---- unit sheets --------------------------------------------------------

    private final java.util.EnumMap<UnitType, UnitSpriteCache> unitSprites =
            new java.util.EnumMap<>(UnitType.class);
    private final java.util.EnumMap<UnitType, UnitSpriteCache> unitDeadSprites =
            new java.util.EnumMap<>(UnitType.class);
    private boolean unitSpritesLoadAttempted;

    // ---- vehicle sheets -----------------------------------------------------

    private final java.util.EnumMap<VehicleKind.VehicleSheet, UnitSpriteCache> vehicleSheets =
            new java.util.EnumMap<>(VehicleKind.VehicleSheet.class);
    private boolean vehicleSheetsLoadAttempted;

    // ---- turret sprites -----------------------------------------------------

    private final java.util.EnumMap<TurretKind, ShuttleSpriteCache> turretSprites =
            new java.util.EnumMap<>(TurretKind.class);
    private final java.util.EnumMap<TurretKind, ShuttleSpriteCache> turretRecoilSprites =
            new java.util.EnumMap<>(TurretKind.class);
    private boolean turretSpritesLoadAttempted;

    // ---- marine secondary sprites -------------------------------------------

    /**
     * Projectile sprites keyed by texture path — the carrier-agnostic view {@code ShotFx}
     * resolves against (any weapon declaring the same path shares the one loaded sprite).
     * The single projectile-sprite store; the old per-source maps were write-only once
     * the shot pass went path-keyed (F3/F5) and are gone.
     */
    private final java.util.Map<String, ShuttleSpriteCache> projectileSpriteByPath =
            new java.util.HashMap<>();
    private final java.util.EnumMap<MarineSecondary, UnitSpriteCache> marineSecondaryAimSheets =
            new java.util.EnumMap<>(MarineSecondary.class);
    private boolean marineSecondarySpritesLoadAttempted;

    // ---- decal sheet --------------------------------------------------------

    private SpriteAPI decalSheet;
    private SpriteSheetFrames decalFrames;
    private boolean decalSheetLoadAttempted;

    // ---- drone sprites ------------------------------------------------------

    private ShuttleSpriteCache droneHubSprite;
    private boolean droneHubSpriteLoadAttempted;
    private ShuttleSpriteCache droneSprite;
    private boolean droneSpriteLoadAttempted;

    // ---- tile sheets --------------------------------------------------------

    private SpriteAPI tileSheet;
    private int tileSheetPxW;
    private int tileSheetPxH;
    private boolean tileSheetLoadAttempted;

    private SpriteAPI roadSheet;
    private int roadSheetPxW;
    private int roadSheetPxH;
    private boolean roadSheetLoadAttempted;

    private SpriteAPI floorsSheet;
    private int floorsSheetPxW;
    private int floorsSheetPxH;
    private boolean floorsSheetLoadAttempted;

    private SpriteAPI waterSheet;
    private int waterSheetPxW;
    private int waterSheetPxH;
    private boolean waterSheetLoadAttempted;

    private SpriteAPI urbanTile3Sheet;
    private int urbanTile3SheetPxW;
    private int urbanTile3SheetPxH;
    private boolean urbanTile3SheetLoadAttempted;
    private SpriteSheetFrames urbanTile3Frames;

    private SpriteAPI natureSheet;
    private int natureSheetPxW;
    private int natureSheetPxH;
    private boolean natureSheetLoadAttempted;
    private SpriteSheetFrames natureFrames;

    // ---- shuttle sprites ----------------------------------------------------

    private final java.util.EnumMap<ShuttleType, ShuttleSpriteCache> shuttleSprites =
            new java.util.EnumMap<>(ShuttleType.class);
    private boolean shuttleSpritesLoadAttempted;

    // ---- convoy sprites -----------------------------------------------------

    private final java.util.EnumMap<com.dillon.starsectormarines.battle.vehicle.VehicleType, UnitSpriteCache> convoySprites =
            new java.util.EnumMap<>(com.dillon.starsectormarines.battle.vehicle.VehicleType.class);
    private boolean convoySpritesLoadAttempted;

    // ---- engine FX sprites --------------------------------------------------

    private SpriteAPI engineFlameSprite;
    private SpriteAPI engineGlowSprite;
    private boolean engineFxSpritesLoadAttempted;

    // ---- objective icons ----------------------------------------------------

    private SpriteAPI iconAlarm;
    private SpriteAPI iconDanger;
    private SpriteAPI iconStar;
    private boolean iconsLoadAttempted;

    // =========================================================================
    // Accessors
    // =========================================================================

    public java.util.EnumMap<UnitType, UnitSpriteCache> unitSprites()          { return unitSprites; }
    public java.util.EnumMap<UnitType, UnitSpriteCache> unitDeadSprites()      { return unitDeadSprites; }
    public java.util.EnumMap<VehicleKind.VehicleSheet, UnitSpriteCache> vehicleSheets() { return vehicleSheets; }
    public java.util.EnumMap<TurretKind, ShuttleSpriteCache> turretSprites()   { return turretSprites; }
    public java.util.EnumMap<TurretKind, ShuttleSpriteCache> turretRecoilSprites() { return turretRecoilSprites; }
    /** Carrier-agnostic projectile-sprite lookup by texture path (what {@code ShotFx.Sprite} resolves against). Null if not loaded / no such path. */
    public ShuttleSpriteCache projectileSprite(String path) { return path == null ? null : projectileSpriteByPath.get(path); }
    public java.util.EnumMap<MarineSecondary, UnitSpriteCache> marineSecondaryAimSheets() { return marineSecondaryAimSheets; }
    public SpriteAPI decalSheet()                  { return decalSheet; }
    public SpriteSheetFrames decalFrames()         { return decalFrames; }
    public ShuttleSpriteCache droneHubSprite()     { return droneHubSprite; }
    public ShuttleSpriteCache droneSprite()        { return droneSprite; }
    public SpriteAPI tileSheet()                   { return tileSheet; }
    public int tileSheetPxW()                      { return tileSheetPxW; }
    public int tileSheetPxH()                      { return tileSheetPxH; }
    public SpriteAPI roadSheet()                   { return roadSheet; }
    public int roadSheetPxW()                      { return roadSheetPxW; }
    public int roadSheetPxH()                      { return roadSheetPxH; }
    public SpriteAPI floorsSheet()                 { return floorsSheet; }
    public int floorsSheetPxW()                    { return floorsSheetPxW; }
    public int floorsSheetPxH()                    { return floorsSheetPxH; }
    public SpriteAPI waterSheet()                  { return waterSheet; }
    public int waterSheetPxW()                     { return waterSheetPxW; }
    public int waterSheetPxH()                     { return waterSheetPxH; }
    public SpriteAPI urbanTile3Sheet()             { return urbanTile3Sheet; }
    public int urbanTile3SheetPxW()                { return urbanTile3SheetPxW; }
    public int urbanTile3SheetPxH()                { return urbanTile3SheetPxH; }
    public SpriteSheetFrames urbanTile3Frames()    { return urbanTile3Frames; }
    public SpriteAPI natureSheet()                 { return natureSheet; }
    public int natureSheetPxW()                    { return natureSheetPxW; }
    public int natureSheetPxH()                    { return natureSheetPxH; }
    public SpriteSheetFrames natureFrames()        { return natureFrames; }
    public java.util.EnumMap<ShuttleType, ShuttleSpriteCache> shuttleSprites() { return shuttleSprites; }
    public java.util.EnumMap<com.dillon.starsectormarines.battle.vehicle.VehicleType, UnitSpriteCache> convoySprites() { return convoySprites; }
    public SpriteAPI engineFlameSprite()           { return engineFlameSprite; }
    public SpriteAPI engineGlowSprite()            { return engineGlowSprite; }
    public SpriteAPI iconAlarm()                   { return iconAlarm; }
    public SpriteAPI iconDanger()                  { return iconDanger; }
    public SpriteAPI iconStar()                    { return iconStar; }

    // =========================================================================
    // Ensure methods (moved verbatim from BattleScreen; batch lines deleted)
    // =========================================================================

    /**
     * Lazy-loads the battle tileset on first attach. Reads the raw PNG to
     * capture content dimensions — {@code SpriteAPI.getTextureWidth()} reports
     * the POT-padded texture size, but per-tile UV math needs the content
     * width to compute texture-fraction coords correctly. Cached across attach
     * calls; survives screen re-entry without re-decoding the PNG.
     */
    public void ensureTileSheet() {
        if (tileSheetLoadAttempted) return;
        tileSheetLoadAttempted = true;
        try {
            Global.getSettings().loadTexture(TileManifest.SHEET);
            tileSheet = Global.getSettings().getSprite(TileManifest.SHEET);
            if (tileSheet == null) {
                LOG.warn("BattleSprites: getSprite returned null for " + TileManifest.SHEET);
                return;
            }
            try (InputStream stream = Global.getSettings().openStream(TileManifest.SHEET)) {
                BufferedImage img = ImageIO.read(stream);
                if (img == null) {
                    LOG.warn("BattleSprites: ImageIO.read returned null for " + TileManifest.SHEET);
                    tileSheet = null;
                    return;
                }
                tileSheetPxW = img.getWidth();
                tileSheetPxH = img.getHeight();
                LOG.info("BattleSprites: loaded tileset " + TileManifest.SHEET
                        + " (" + tileSheetPxW + "x" + tileSheetPxH + ")");
            }
        } catch (Exception e) {
            LOG.error("BattleSprites: failed to load tileset " + TileManifest.SHEET, e);
            tileSheet = null;
        }
    }

    /** Same lazy-load pattern as {@link #ensureTileSheet} — the road sheet is its own PNG so updates don't churn the indoor floor cache. */
    public void ensureRoadSheet() {
        if (roadSheetLoadAttempted) return;
        roadSheetLoadAttempted = true;
        try {
            Global.getSettings().loadTexture(TileManifest.ROAD_SHEET);
            roadSheet = Global.getSettings().getSprite(TileManifest.ROAD_SHEET);
            if (roadSheet == null) {
                LOG.warn("BattleSprites: getSprite returned null for " + TileManifest.ROAD_SHEET);
                return;
            }
            try (InputStream stream = Global.getSettings().openStream(TileManifest.ROAD_SHEET)) {
                BufferedImage img = ImageIO.read(stream);
                if (img == null) {
                    LOG.warn("BattleSprites: ImageIO.read returned null for " + TileManifest.ROAD_SHEET);
                    roadSheet = null;
                    return;
                }
                roadSheetPxW = img.getWidth();
                roadSheetPxH = img.getHeight();
                LOG.info("BattleSprites: loaded road tileset " + TileManifest.ROAD_SHEET
                        + " (" + roadSheetPxW + "x" + roadSheetPxH + ")");
            }
        } catch (Exception e) {
            LOG.error("BattleSprites: failed to load road tileset " + TileManifest.ROAD_SHEET, e);
            roadSheet = null;
        }
    }

    /**
     * Lazy-loads the outdoor surfaces sheet (Floors_Tiles.png). 16px source
     * cells — the renderer upscales 2x to fit the 32px nav grid. Same pattern
     * as {@link #ensureRoadSheet}.
     */
    public void ensureFloorsSheet() {
        if (floorsSheetLoadAttempted) return;
        floorsSheetLoadAttempted = true;
        try {
            Global.getSettings().loadTexture(TileManifest.FLOORS_SHEET);
            floorsSheet = Global.getSettings().getSprite(TileManifest.FLOORS_SHEET);
            if (floorsSheet == null) {
                LOG.warn("BattleSprites: getSprite returned null for " + TileManifest.FLOORS_SHEET);
                return;
            }
            try (InputStream stream = Global.getSettings().openStream(TileManifest.FLOORS_SHEET)) {
                BufferedImage img = ImageIO.read(stream);
                if (img == null) {
                    LOG.warn("BattleSprites: ImageIO.read returned null for " + TileManifest.FLOORS_SHEET);
                    floorsSheet = null;
                    return;
                }
                floorsSheetPxW = img.getWidth();
                floorsSheetPxH = img.getHeight();
                LOG.info("BattleSprites: loaded floors tileset " + TileManifest.FLOORS_SHEET
                        + " (" + floorsSheetPxW + "x" + floorsSheetPxH + ")");
            }
        } catch (Exception e) {
            LOG.error("BattleSprites: failed to load floors tileset " + TileManifest.FLOORS_SHEET, e);
            floorsSheet = null;
        }
    }

    /** Lazy-loads the Water_tiles sheet. Same setup as {@link #ensureFloorsSheet}. */
    public void ensureWaterSheet() {
        if (waterSheetLoadAttempted) return;
        waterSheetLoadAttempted = true;
        try {
            Global.getSettings().loadTexture(TileManifest.WATER_SHEET);
            waterSheet = Global.getSettings().getSprite(TileManifest.WATER_SHEET);
            if (waterSheet == null) {
                LOG.warn("BattleSprites: getSprite returned null for " + TileManifest.WATER_SHEET);
                return;
            }
            try (InputStream stream = Global.getSettings().openStream(TileManifest.WATER_SHEET)) {
                BufferedImage img = ImageIO.read(stream);
                if (img == null) {
                    LOG.warn("BattleSprites: ImageIO.read returned null for " + TileManifest.WATER_SHEET);
                    waterSheet = null;
                    return;
                }
                waterSheetPxW = img.getWidth();
                waterSheetPxH = img.getHeight();
                LOG.info("BattleSprites: loaded water tileset " + TileManifest.WATER_SHEET
                        + " (" + waterSheetPxW + "x" + waterSheetPxH + ")");
            }
        } catch (Exception e) {
            LOG.error("BattleSprites: failed to load water tileset " + TileManifest.WATER_SHEET, e);
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
    public void ensureNatureSheet() {
        if (natureSheetLoadAttempted) return;
        natureSheetLoadAttempted = true;
        try {
            Global.getSettings().loadTexture(NatureTileset.SHEET_PATH);
            natureSheet = Global.getSettings().getSprite(NatureTileset.SHEET_PATH);
            if (natureSheet == null) {
                LOG.warn("BattleSprites: getSprite returned null for " + NatureTileset.SHEET_PATH);
                return;
            }
            try (InputStream stream = Global.getSettings().openStream(NatureTileset.SHEET_PATH)) {
                BufferedImage img = ImageIO.read(stream);
                if (img == null) {
                    LOG.warn("BattleSprites: ImageIO.read returned null for " + NatureTileset.SHEET_PATH);
                    natureSheet = null;
                    return;
                }
                natureSheetPxW = img.getWidth();
                natureSheetPxH = img.getHeight();
                natureFrames = SpriteSheetSlicer.slice(img);
                TileRegistry reg = TileRegistry.installed();
                int expected = (reg == null) ? natureFrames.frames.length
                        : (int) reg.all().stream()
                                .filter(d -> NatureTileset.SHEET_PATH.equals(d.sheetPath))
                                .count();
                if (reg != null && natureFrames.frames.length != expected) {
                    LOG.warn("BattleSprites: nature-tiles slicer returned "
                            + natureFrames.frames.length + " frames but TileRegistry expects "
                            + expected + " — falling back to legacy Floors_Tiles grass/dirt");
                    natureSheet = null;
                    natureFrames = null;
                    return;
                }
                LOG.info("BattleSprites: loaded nature-tiles " + NatureTileset.SHEET_PATH
                        + " (" + natureSheetPxW + "x" + natureSheetPxH + "), "
                        + natureFrames.frames.length + " frames sliced");
            }
        } catch (Exception e) {
            LOG.error("BattleSprites: failed to load nature-tiles "
                    + com.dillon.starsectormarines.battle.world.tiles.NatureTileset.SHEET_PATH, e);
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
    public void ensureUrbanTile3Sheet() {
        if (urbanTile3SheetLoadAttempted) return;
        urbanTile3SheetLoadAttempted = true;
        try {
            Global.getSettings().loadTexture(TileManifest.STREET3_SHEET);
            urbanTile3Sheet = Global.getSettings().getSprite(TileManifest.STREET3_SHEET);
            if (urbanTile3Sheet == null) {
                LOG.warn("BattleSprites: getSprite returned null for " + TileManifest.STREET3_SHEET);
                return;
            }
            try (InputStream stream = Global.getSettings().openStream(TileManifest.STREET3_SHEET)) {
                BufferedImage img = ImageIO.read(stream);
                if (img == null) {
                    LOG.warn("BattleSprites: ImageIO.read returned null for " + TileManifest.STREET3_SHEET);
                    urbanTile3Sheet = null;
                    return;
                }
                urbanTile3SheetPxW = img.getWidth();
                urbanTile3SheetPxH = img.getHeight();
                urbanTile3Frames = SpriteSheetSlicer.slice(img);
                TileRegistry reg = TileRegistry.installed();
                int expected = (reg == null) ? urbanTile3Frames.frames.length
                        : (int) reg.all().stream()
                                .filter(d -> UrbanTile3Tileset.SHEET_PATH.equals(d.sheetPath))
                                .count();
                if (reg != null && urbanTile3Frames.frames.length != expected) {
                    LOG.warn("BattleSprites: urban-tileset-3 slicer returned "
                            + urbanTile3Frames.frames.length + " frames but TileRegistry expects "
                            + expected + " — falling back to legacy road autotile");
                    urbanTile3Sheet = null;
                    urbanTile3Frames = null;
                    return;
                }
                LOG.info("BattleSprites: loaded urban-tileset-3 " + TileManifest.STREET3_SHEET
                        + " (" + urbanTile3SheetPxW + "x" + urbanTile3SheetPxH + "), "
                        + urbanTile3Frames.frames.length + " frames sliced");
            }
        } catch (Exception e) {
            LOG.error("BattleSprites: failed to load urban-tileset-3 " + TileManifest.STREET3_SHEET, e);
            urbanTile3Sheet = null;
            urbanTile3Frames = null;
        }
    }

    /**
     * Lazy-loads the vanilla engine flame + glow textures. Same one-shot
     * pattern as {@link #ensureShuttleSprites()}: try to load each path
     * once, log + degrade gracefully if either fails. A missing engine
     * sprite just means the engine pass renders nothing — no crash.
     */
    public void ensureEngineFxSprites() {
        if (engineFxSpritesLoadAttempted) return;
        engineFxSpritesLoadAttempted = true;
        engineFlameSprite = loadEngineFxSpriteOrNull(ENGINE_FLAME_SPRITE);
        engineGlowSprite  = loadEngineFxSpriteOrNull(ENGINE_GLOW_SPRITE);
    }

    public SpriteAPI loadEngineFxSpriteOrNull(String path) {
        try {
            Global.getSettings().loadTexture(path);
            SpriteAPI s = Global.getSettings().getSprite(path);
            if (s == null) {
                LOG.warn("BattleSprites: getSprite returned null for " + path);
            }
            return s;
        } catch (Exception e) {
            LOG.error("BattleSprites: failed to load engine FX sprite " + path, e);
            return null;
        }
    }

    public void ensureShuttleSprites() {
        if (shuttleSpritesLoadAttempted) return;
        shuttleSpritesLoadAttempted = true;
        for (ShuttleType type : ShuttleType.values()) {
            try {
                Global.getSettings().loadTexture(type.spritePath);
                SpriteAPI sprite = Global.getSettings().getSprite(type.spritePath);
                if (sprite == null) {
                    LOG.warn("BattleSprites: getSprite returned null for " + type.spritePath);
                    continue;
                }
                float w = sprite.getWidth();
                float h = sprite.getHeight();
                float aspect = (h > 0f) ? w / h : 1f;
                shuttleSprites.put(type, new ShuttleSpriteCache(sprite, aspect));
                LOG.info("BattleSprites: loaded shuttle " + type.spritePath
                        + " (" + w + "x" + h + ", aspect=" + aspect + ")");
            } catch (Exception e) {
                LOG.error("BattleSprites: failed to load shuttle sprite " + type.spritePath, e);
            }
        }
    }

    /**
     * Lazy-loads each {@link com.dillon.starsectormarines.battle.vehicle.VehicleType}'s
     * sheet via the shared {@link #loadUnitSheet} helper. Auto-slicing produces
     * the per-frame bounds so {@code VehicleType.spriteFrame} can index into
     * the sliced list. Failure on a single type leaves it absent from the cache;
     * the convoy render pass silently skips vehicles whose cache entry is null.
     */
    public void ensureConvoySprites() {
        if (convoySpritesLoadAttempted) return;
        convoySpritesLoadAttempted = true;
        for (com.dillon.starsectormarines.battle.vehicle.VehicleType type :
                com.dillon.starsectormarines.battle.vehicle.VehicleType.values()) {
            UnitSpriteCache cache = loadUnitSheet(type.spritePath);
            if (cache != null) {
                convoySprites.put(type, cache);
            } else {
                LOG.warn("convoy: failed to load sprite for " + type
                        + " (" + type.spritePath + ")");
            }
        }
    }

    /**
     * Lazy-loads the SABOTAGE objective marker icons. Each PNG is a 512px white
     * shape on transparent — tinted at draw time via {@code setColor}. Same
     * sprite-lazy-load gotcha as the tileset: {@code getSprite} returns a wrapper
     * whose backing texture is null until {@code loadTexture} is called.
     */
    public void ensureObjectiveIcons() {
        if (iconsLoadAttempted) return;
        iconsLoadAttempted = true;
        iconAlarm  = loadIconOrNull(ICON_ALARM);
        iconDanger = loadIconOrNull(ICON_DANGER);
        iconStar   = loadIconOrNull(ICON_STAR);
    }

    public SpriteAPI loadIconOrNull(String path) {
        try {
            Global.getSettings().loadTexture(path);
            SpriteAPI sprite = Global.getSettings().getSprite(path);
            if (sprite == null) {
                LOG.warn("BattleSprites: getSprite returned null for " + path);
                return null;
            }
            LOG.info("BattleSprites: loaded icon " + path);
            return sprite;
        } catch (Exception e) {
            LOG.error("BattleSprites: failed to load icon " + path, e);
            return null;
        }
    }

    /**
     * Loads every {@link UnitType} sprite sheet on first call and auto-slices
     * each into per-frame bounding boxes. A type whose load fails is recorded
     * with a null entry so its units fall back to the color-quad path without
     * retrying every frame.
     */
    public void ensureUnitSheets() {
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

    public void ensureVehicleSheets() {
        if (vehicleSheetsLoadAttempted) return;
        vehicleSheetsLoadAttempted = true;
        for (VehicleKind.VehicleSheet sheet : VehicleKind.VehicleSheet.values()) {
            vehicleSheets.put(sheet, loadUnitSheet(sheet.path));
        }
    }

    /** Lazy-loads each marine secondary's projectile sprite AND the marine-pose sheet shown during the weapon's aim cycle. The projectile is a single sprite rotated at draw time; the aim sheet is auto-sliced into 7 frames matching the regular marine convention. */
    public void ensureMarineSecondarySprites() {
        if (marineSecondarySpritesLoadAttempted) return;
        marineSecondarySpritesLoadAttempted = true;
        for (MarineSecondary sec : MarineSecondary.values()) {
            try {
                Global.getSettings().loadTexture(sec.projectileSpritePath);
                SpriteAPI sprite = Global.getSettings().getSprite(sec.projectileSpritePath);
                if (sprite == null) {
                    LOG.warn("BattleSprites: getSprite returned null for " + sec.projectileSpritePath);
                } else {
                    float w = sprite.getWidth();
                    float h = sprite.getHeight();
                    float aspect = (h > 0f) ? w / h : 1f;
                    ShuttleSpriteCache cache = new ShuttleSpriteCache(sprite, aspect);
                    projectileSpriteByPath.put(sec.projectileSpritePath, cache);
                    LOG.info("BattleSprites: loaded " + sec.projectileSpritePath
                            + " (" + w + "x" + h + ", aspect=" + aspect + ")");
                }
            } catch (Exception e) {
                LOG.error("BattleSprites: failed to load secondary projectile " + sec.projectileSpritePath, e);
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
                    LOG.warn("BattleSprites: getSprite returned null for " + w.projectileSpritePath);
                    continue;
                }
                float pw = sprite.getWidth();
                float ph = sprite.getHeight();
                float aspect = (ph > 0f) ? pw / ph : 1f;
                ShuttleSpriteCache cache = new ShuttleSpriteCache(sprite, aspect);
                projectileSpriteByPath.put(w.projectileSpritePath, cache);
                LOG.info("BattleSprites: loaded " + w.projectileSpritePath
                        + " (" + pw + "x" + ph + ", aspect=" + aspect + ")");
            } catch (Exception e) {
                LOG.error("BattleSprites: failed to load primary projectile " + w.projectileSpritePath, e);
            }
        }
        // Mech chassis projectile sprites — every entry has one (chaingun
        // shell / SRM / LRM). Same load + aspect-capture pattern as the marine
        // primaries above.
        for (com.dillon.starsectormarines.battle.mech.MechWeapon w
                : com.dillon.starsectormarines.battle.mech.MechWeapon.values()) {
            if (w.projectileSpritePath == null) continue;
            try {
                Global.getSettings().loadTexture(w.projectileSpritePath);
                SpriteAPI sprite = Global.getSettings().getSprite(w.projectileSpritePath);
                if (sprite == null) {
                    LOG.warn("BattleSprites: getSprite returned null for " + w.projectileSpritePath);
                    continue;
                }
                float pw = sprite.getWidth();
                float ph = sprite.getHeight();
                float aspect = (ph > 0f) ? pw / ph : 1f;
                ShuttleSpriteCache cache = new ShuttleSpriteCache(sprite, aspect);
                projectileSpriteByPath.put(w.projectileSpritePath, cache);
                LOG.info("BattleSprites: loaded mech projectile " + w.projectileSpritePath
                        + " (" + pw + "x" + ph + ", aspect=" + aspect + ")");
            } catch (Exception e) {
                LOG.error("BattleSprites: failed to load mech projectile " + w.projectileSpritePath, e);
            }
        }
    }

    public void ensureTurretSprites() {
        if (turretSpritesLoadAttempted) return;
        turretSpritesLoadAttempted = true;
        for (TurretKind kind : TurretKind.values()) {
            loadTurretSpriteInto(turretSprites,       kind, kind.spritePath);
            loadTurretSpriteInto(turretRecoilSprites, kind, kind.recoilSpritePath);
            // Projectile sprite is path-keyed only (carrier-agnostic) — no per-kind map.
            ShuttleSpriteCache proj = loadTurretSprite(kind.projectileSpritePath);
            if (proj != null) projectileSpriteByPath.put(kind.projectileSpritePath, proj);
        }
    }

    public void ensureDroneHubSprite() {
        if (droneHubSpriteLoadAttempted) return;
        droneHubSpriteLoadAttempted = true;
        String path = com.dillon.starsectormarines.battle.drone.DroneHubUnit.SPRITE_PATH;
        try {
            Global.getSettings().loadTexture(path);
            SpriteAPI sprite = Global.getSettings().getSprite(path);
            if (sprite == null) {
                LOG.warn("BattleSprites: getSprite returned null for " + path);
                return;
            }
            float w = sprite.getWidth();
            float h = sprite.getHeight();
            float aspect = (h > 0f) ? w / h : 1f;
            droneHubSprite = new ShuttleSpriteCache(sprite, aspect);
            LOG.info("BattleSprites: loaded " + path + " (" + w + "x" + h + ", aspect=" + aspect + ")");
        } catch (Exception e) {
            LOG.error("BattleSprites: failed to load " + path, e);
        }
    }

    public void ensureDroneSprite() {
        if (droneSpriteLoadAttempted) return;
        droneSpriteLoadAttempted = true;
        String path = com.dillon.starsectormarines.battle.drone.Drone.SPRITE_PATH;
        try {
            Global.getSettings().loadTexture(path);
            SpriteAPI sprite = Global.getSettings().getSprite(path);
            if (sprite == null) {
                LOG.warn("BattleSprites: getSprite returned null for " + path);
                return;
            }
            float w = sprite.getWidth();
            float h = sprite.getHeight();
            float aspect = (h > 0f) ? w / h : 1f;
            droneSprite = new ShuttleSpriteCache(sprite, aspect);
            LOG.info("BattleSprites: loaded " + path + " (" + w + "x" + h + ", aspect=" + aspect + ")");
        } catch (Exception e) {
            LOG.error("BattleSprites: failed to load " + path, e);
        }
    }

    /** Loads one turret-related sprite + its native aspect (captured before any {@code setSize} clobbers {@code getWidth/getHeight}); null on failure. */
    public ShuttleSpriteCache loadTurretSprite(String path) {
        try {
            Global.getSettings().loadTexture(path);
            SpriteAPI sprite = Global.getSettings().getSprite(path);
            if (sprite == null) {
                LOG.warn("BattleSprites: getSprite returned null for " + path);
                return null;
            }
            float w = sprite.getWidth();
            float h = sprite.getHeight();
            float aspect = (h > 0f) ? w / h : 1f;
            LOG.info("BattleSprites: loaded " + path + " (" + w + "x" + h + ", aspect=" + aspect + ")");
            return new ShuttleSpriteCache(sprite, aspect);
        } catch (Exception e) {
            LOG.error("BattleSprites: failed to load " + path, e);
            return null;
        }
    }

    /** {@link #loadTurretSprite} into a per-kind map (turret body + recoil sprites, read by the turret renderer). */
    public void loadTurretSpriteInto(java.util.EnumMap<TurretKind, ShuttleSpriteCache> cache,
                                     TurretKind kind, String path) {
        ShuttleSpriteCache c = loadTurretSprite(path);
        if (c != null) cache.put(kind, c);
    }

    public UnitSpriteCache loadUnitSheet(String path) {
        try {
            Global.getSettings().loadTexture(path);
            SpriteAPI sprite = Global.getSettings().getSprite(path);
            if (sprite == null) {
                LOG.warn("BattleSprites: getSprite returned null for " + path);
                return null;
            }
            try (InputStream stream = Global.getSettings().openStream(path)) {
                BufferedImage img = ImageIO.read(stream);
                if (img == null) {
                    LOG.warn("BattleSprites: ImageIO.read returned null for " + path);
                    return null;
                }
                SpriteSheetFrames frames = SpriteSheetSlicer.slice(img);
                LOG.info("BattleSprites: auto-sliced " + path + " — " + frames.frames.length + " frames detected");
                return new UnitSpriteCache(sprite, frames);
            }
        } catch (Exception e) {
            LOG.error("BattleSprites: failed to load unit sheet " + path, e);
            return null;
        }
    }

    /** Lazy-loads the decal sheet and auto-slices it into per-frame bounding boxes. Failure leaves both fields null and the decal pass becomes a no-op. */
    public void ensureDecalSheet() {
        if (decalSheetLoadAttempted) return;
        decalSheetLoadAttempted = true;
        try {
            Global.getSettings().loadTexture(SPRITE_DECAL_SHEET);
            decalSheet = Global.getSettings().getSprite(SPRITE_DECAL_SHEET);
            if (decalSheet == null) {
                LOG.warn("BattleSprites: getSprite returned null for " + SPRITE_DECAL_SHEET);
                return;
            }
            try (InputStream stream = Global.getSettings().openStream(SPRITE_DECAL_SHEET)) {
                BufferedImage img = ImageIO.read(stream);
                if (img == null) {
                    LOG.warn("BattleSprites: ImageIO.read returned null for " + SPRITE_DECAL_SHEET);
                    decalSheet = null;
                    return;
                }
                decalFrames = SpriteSheetSlicer.slice(img);
                LOG.info("BattleSprites: auto-sliced " + SPRITE_DECAL_SHEET
                        + " — " + decalFrames.frames.length + " frames");
            }
        } catch (Exception e) {
            LOG.error("BattleSprites: failed to load decal sheet " + SPRITE_DECAL_SHEET, e);
            decalSheet = null;
        }
    }
}
