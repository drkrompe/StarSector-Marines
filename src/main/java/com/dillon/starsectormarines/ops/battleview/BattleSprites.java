package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.air.Airframe;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.appearance.MechLivery;
import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts;
import com.dillon.starsectormarines.battle.drone.DroneHub;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.MountClass;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.battle.world.tiles.SheetTexture;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetFrames;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetSlicer;
import com.dillon.starsectormarines.marine.EquipmentLayerDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.apache.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Asset/sprite-cache registry for the battle screen. Owns all loaded
 * {@link SpriteAPI} sheets, their {@link SpriteSheetFrames}, the content
 * px-dimensions, per-type {@link java.util.EnumMap} caches, and every
 * {@code ensure*}/{@code load*} method. {@link BattleRenderer} owns the
 * per-sheet batches that consume these assets.
 */
public class BattleSprites {

    private static final Logger LOG = Global.getLogger(BattleSprites.class);

    // ---- asset-path constants -----------------------------------------------

    private static final String SPRITE_DECAL_SHEET  = "graphics/decals/decals.png";
    /** Vanilla, so the headless sprite set resolves these off the install root rather than through the game. */
    static final String ENGINE_FLAME_SPRITE = "graphics/fx/engineflame32.png";
    static final String ENGINE_GLOW_SPRITE  = "graphics/fx/engineglow32.png";
    /**
     * The soft disc a body's shadow is drawn with. Ours rather than the
     * vanilla engine glow the shadow layer used to borrow, which is a
     * four-lobed flare and not a falloff at all — see
     * {@code art-source/fx/build_shadow_blob.py}.
     */
    static final String SHADOW_BLOB_SPRITE  = "graphics/battle/fx/shadow-blob.png";
    private static final String ICON_ALARM          = "graphics/icons/Alarm 512 px.png";
    private static final String ICON_DANGER         = "graphics/icons/Danger sign 1 512 px.png";
    private static final String ICON_STAR           = "graphics/icons/Star 512 px.png";

    // ---- unit sheets --------------------------------------------------------

    private final java.util.EnumMap<UnitType, UnitSpriteCache> unitSprites =
            new java.util.EnumMap<>(UnitType.class);
    private final java.util.EnumMap<UnitType, UnitSpriteCache> unitDeadSprites =
            new java.util.EnumMap<>(UnitType.class);
    private boolean unitSpritesLoadAttempted;

    // ---- modular layered infantry -----------------------------------------

    private static final String MODULAR_ROOT =
            "graphics/battle/marine-modular-topdown/variants/";
    private final java.util.EnumMap<LayeredArmorFamily, LayeredUnitAssets> layeredUnitSprites =
            new java.util.EnumMap<>(LayeredArmorFamily.class);
    private final Map<String, LayeredSpriteCache> specialEquipmentLayers = new LinkedHashMap<>();
    private boolean layeredUnitSpritesLoadAttempted;

    // ---- modular layered heavy mech ---------------------------------------

    private LayeredMechAssets layeredMechSprites;
    private final java.util.EnumMap<MechLivery, LayeredMechAssets> layeredMechLiveries =
            new java.util.EnumMap<>(MechLivery.class);
    private final java.util.EnumMap<Faction, MechLivery> mechLiveryBySide =
            new java.util.EnumMap<>(Faction.class);
    private boolean layeredMechSpritesLoadAttempted;

    // ---- vehicle sheets -----------------------------------------------------


    // ---- turret sprites -----------------------------------------------------

    private final Map<String, ShuttleSpriteCache> turretSprites = new LinkedHashMap<>();
    private final Map<String, ShuttleSpriteCache> turretRecoilSprites = new LinkedHashMap<>();
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
    private final Map<String, UnitSpriteCache> specialEquipmentAimSheets = new LinkedHashMap<>();
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
    // One generic SheetTexture handle per PNG (grid = no slicing; sliced =
    // SpriteSheetSlicer + TileRegistry count-check with legacy fallback).

    private final SheetTexture tileTex       = SheetTexture.grid(TileManifest.SHEET);
    private final SheetTexture roadTex       = SheetTexture.grid(TileManifest.ROAD_SHEET);
    private final SheetTexture floorsTex     = SheetTexture.grid(TileManifest.FLOORS_SHEET);
    private final SheetTexture waterTex       = SheetTexture.grid(TileManifest.WATER_SHEET);
    private final SheetTexture urbanTile3Tex = SheetTexture.sliced(TileManifest.STREET3_SHEET);
    private final SheetTexture natureTex     = SheetTexture.sliced(TileManifest.NATURE_SHEET);
    private final SheetTexture doodadTex     = SheetTexture.grid(TileManifest.DOODAD_SHEET);
    private final SheetTexture parkedVehicleTex = SheetTexture.grid(TileManifest.PARKED_VEHICLE_SHEET);

    // ---- airframe sprites ---------------------------------------------------

    /**
     * One entry per {@link Airframe} the battle can draw — every transport and
     * every fighter. A plain map rather than an {@code EnumMap} because the
     * airframes are spread across two enums on purpose: transports and
     * fighters are different kinds of aircraft and neither list wants the
     * other's fields.
     */
    private final java.util.Map<Airframe, ShuttleSpriteCache> airframeSprites =
            new java.util.LinkedHashMap<>();
    private boolean airframeSpritesLoadAttempted;

    // ---- convoy sprites -----------------------------------------------------

    private final java.util.EnumMap<com.dillon.starsectormarines.battle.vehicle.VehicleType, UnitSpriteCache> convoySprites =
            new java.util.EnumMap<>(com.dillon.starsectormarines.battle.vehicle.VehicleType.class);
    private boolean convoySpritesLoadAttempted;

    // ---- engine FX sprites --------------------------------------------------

    private SpriteAPI engineFlameSprite;
    private SpriteAPI engineGlowSprite;
    private SpriteAPI shadowBlobSprite;
    private boolean shadowBlobLoadAttempted;
    private boolean engineFxSpritesLoadAttempted;

    // ---- objective icons ----------------------------------------------------

    private SpriteAPI iconAlarm;
    private SpriteAPI iconDanger;
    private SpriteAPI iconStar;
    private boolean iconsLoadAttempted;
    private SpriteAPI smokeGrenadeSprite;
    private SpriteAPI smokeFieldSheet;
    private boolean smokeSpritesLoadAttempted;
    private SpriteAPI satchelChargeSprite;
    private boolean satchelSpriteLoadAttempted;


    // ---- embedded Mech Lab workshop FX ------------------------------------

    private SpriteAPI mechLabWeldingTorch;
    private SpriteAPI mechLabWeldingSparks;
    private boolean mechLabFxLoadAttempted;

    // =========================================================================
    // Accessors
    // =========================================================================

    public java.util.EnumMap<UnitType, UnitSpriteCache> unitSprites()          { return unitSprites; }
    public java.util.EnumMap<UnitType, UnitSpriteCache> unitDeadSprites()      { return unitDeadSprites; }
    public java.util.EnumMap<LayeredArmorFamily, LayeredUnitAssets> layeredUnitSprites() { return layeredUnitSprites; }
    public LayeredMechAssets layeredMechSprites() { return layeredMechSprites; }
    /** Complete livery selected for one tactical side, with the base set as fail-safe. */
    public LayeredMechAssets layeredMechSprites(Faction side) {
        MechLivery livery = mechLiveryBySide.getOrDefault(side, MechLivery.BASE);
        LayeredMechAssets selected = layeredMechLiveries.get(livery);
        return selected != null ? selected : layeredMechSprites();
    }

    /**
     * Binds campaign identities to the two tactical sides. This is presentation
     * state only and may be refreshed on every screen attachment, before or
     * after the immutable sprite families have loaded.
     */
    public void configureMechLiveries(String marineFactionId, String defenderFactionId) {
        mechLiveryBySide.put(Faction.MARINE, MechLivery.forFactionId(marineFactionId));
        mechLiveryBySide.put(Faction.DEFENDER, MechLivery.forFactionId(defenderFactionId));
        mechLiveryBySide.put(Faction.CIVILIAN, MechLivery.BASE);
    }
    public Map<String, ShuttleSpriteCache> turretSprites()   { return turretSprites; }
    public Map<String, ShuttleSpriteCache> turretRecoilSprites() { return turretRecoilSprites; }
    /** Carrier-agnostic projectile-sprite lookup by texture path (what {@code ShotFx.Sprite} resolves against). Null if not loaded / no such path. */
    public ShuttleSpriteCache projectileSprite(String path) { return path == null ? null : projectileSpriteByPath.get(path); }
    public Map<String, UnitSpriteCache> specialEquipmentAimSheets() {
        return specialEquipmentAimSheets;
    }
    public SpriteAPI smokeGrenadeSprite() { return smokeGrenadeSprite; }
    /** The deployed smoke field's flipbook sheet; frames are addressed by {@code SpecialEquipmentPresentationDef.Field}. */
    public SpriteAPI smokeFieldSheet() { return smokeFieldSheet; }
    public SpriteAPI satchelChargeSprite() { return satchelChargeSprite; }
    public SpriteAPI mechLabWeldingTorch() { return mechLabWeldingTorch; }
    public SpriteAPI mechLabWeldingSparks() { return mechLabWeldingSparks; }
    public SpriteAPI decalSheet()                  { return decalSheet; }
    public SpriteSheetFrames decalFrames()         { return decalFrames; }
    public ShuttleSpriteCache droneHubSprite()     { return droneHubSprite; }
    public ShuttleSpriteCache droneSprite()        { return droneSprite; }
    public SpriteAPI tileSheet()                   { return tileTex.sprite(); }
    public int tileSheetPxW()                      { return tileTex.pxW(); }
    public int tileSheetPxH()                      { return tileTex.pxH(); }
    public SpriteAPI roadSheet()                   { return roadTex.sprite(); }
    public int roadSheetPxW()                      { return roadTex.pxW(); }
    public int roadSheetPxH()                      { return roadTex.pxH(); }
    public SpriteAPI floorsSheet()                 { return floorsTex.sprite(); }
    public int floorsSheetPxW()                    { return floorsTex.pxW(); }
    public int floorsSheetPxH()                    { return floorsTex.pxH(); }
    public SpriteAPI waterSheet()                  { return waterTex.sprite(); }
    public int waterSheetPxW()                     { return waterTex.pxW(); }
    public int waterSheetPxH()                     { return waterTex.pxH(); }
    public SpriteAPI urbanTile3Sheet()             { return urbanTile3Tex.sprite(); }
    public int urbanTile3SheetPxW()                { return urbanTile3Tex.pxW(); }
    public int urbanTile3SheetPxH()                { return urbanTile3Tex.pxH(); }
    public SpriteSheetFrames urbanTile3Frames()    { return urbanTile3Tex.frames(); }
    public SpriteAPI natureSheet()                 { return natureTex.sprite(); }
    public int natureSheetPxW()                    { return natureTex.pxW(); }
    public int natureSheetPxH()                    { return natureTex.pxH(); }
    public SpriteSheetFrames natureFrames()        { return natureTex.frames(); }
    public SpriteAPI parkedVehicleSheet()           { return parkedVehicleTex.sprite(); }
    public int parkedVehicleSheetPxW()              { return parkedVehicleTex.pxW(); }
    public int parkedVehicleSheetPxH()              { return parkedVehicleTex.pxH(); }
    public SpriteAPI doodadSheet()                  { return doodadTex.sprite(); }
    public int doodadSheetPxW()                     { return doodadTex.pxW(); }
    public int doodadSheetPxH()                     { return doodadTex.pxH(); }
    public java.util.Map<Airframe, ShuttleSpriteCache> airframeSprites() { return airframeSprites; }
    public java.util.EnumMap<com.dillon.starsectormarines.battle.vehicle.VehicleType, UnitSpriteCache> convoySprites() { return convoySprites; }
    public SpriteAPI engineFlameSprite()           { return engineFlameSprite; }
    public SpriteAPI engineGlowSprite()            { return engineGlowSprite; }
    public SpriteAPI shadowBlobSprite()            { return shadowBlobSprite; }
    public SpriteAPI iconAlarm()                   { return iconAlarm; }
    public SpriteAPI iconDanger()                  { return iconDanger; }
    public SpriteAPI iconStar()                    { return iconStar; }
    /** Authored pose authority; overridable by tooling that loads assets off disk. */
    public UnitLayerLayouts unitLayerLayouts()     { return UnitLayerLayouts.get(); }

    // =========================================================================
    // Ensure methods (moved verbatim from BattleScreen; batch lines deleted)
    // =========================================================================

    /**
     * Lazy-loads the indoor tileset (urban-tileset.png). The handle captures
     * content dimensions for the per-tile UV math and caches across attach
     * calls; see {@link SheetTexture} for the load/fallback contract shared by
     * all six tile sheets.
     */
    public void ensureTileSheet()       { tileTex.ensureLoaded(); }

    /** Lazy-loads the road sheet (urban-tileset-2.png) — its own PNG so road art iterates independently of the indoor floors. */
    public void ensureRoadSheet()       { roadTex.ensureLoaded(); }

    public void ensureMechLabFxSprites() {
        if (mechLabFxLoadAttempted) return;
        mechLabFxLoadAttempted = true;
        mechLabWeldingTorch = loadMechLabFxSpriteOrNull(
                MechLabDollCanvas.WELDING_TORCH_PATH);
        mechLabWeldingSparks = loadMechLabFxSpriteOrNull(
                MechLabDollCanvas.WELDING_SPARKS_PATH);
    }

    private SpriteAPI loadMechLabFxSpriteOrNull(String path) {
        try {
            Global.getSettings().loadTexture(path);
            SpriteAPI sprite = Global.getSettings().getSprite(path);
            if (sprite == null) {
                LOG.warn("BattleSprites: getSprite returned null for " + path);
            }
            return sprite;
        } catch (Exception failure) {
            LOG.warn("BattleSprites: could not load Mech Lab FX sprite " + path, failure);
            return null;
        }
    }

    /** Lazy-loads the high-resolution outdoor surfaces sheet (Floors_Tiles.png), with 56px source cells drawn into the 32px nav grid. */
    public void ensureFloorsSheet()     { floorsTex.ensureLoaded(); }

    /** Lazy-loads the Water_tiles sheet (legacy 16px cells, upscaled to the 32px nav grid). */
    public void ensureWaterSheet()      { waterTex.ensureLoaded(); }

    /**
     * Lazy-loads the nature-tiles sheet — a sliced strip (grass / dirt / sand /
     * water grounds plus plant/rock overlays). On a slicer-vs-registry count
     * mismatch the handle nulls itself so the renderer falls back to the legacy
     * Floors_Tiles grass/dirt; {@link #natureFrames()} is null in that case.
     */
    public void ensureNatureSheet()     { natureTex.ensureLoaded(); }

    /**
     * Lazy-loads the urban-tileset-3 sheet — a sliced strip (modern road +
     * sidewalk look). On a slicer-vs-registry count mismatch the handle nulls
     * itself so STREET cells fall back to the legacy road autotile;
     * {@link #urbanTile3Frames()} is null in that case.
     */
    public void ensureUrbanTile3Sheet() { urbanTile3Tex.ensureLoaded(); }

    /** Lazy-loads the dedicated 32px generated-doodad atlas. */
    /** Loads both prop atlases — the generated doodad sheet and the parked-vehicle
     *  sheet. One call because they are one layer's art: a caller that wants props
     *  drawn wants trucks drawn, and splitting the two only invites a host that
     *  loads one and silently drops the other. */
    public void ensureDoodadSheet()     { doodadTex.ensureLoaded(); parkedVehicleTex.ensureLoaded(); }

    /**
     * Lazy-loads the vanilla engine flame + glow textures. Same one-shot
     * pattern as {@link #ensureAirframeSprites()}: try to load each path
     * once, log + degrade gracefully if either fails. A missing engine
     * sprite just means the engine pass renders nothing — no crash.
     */
    public void ensureEngineFxSprites() {
        if (engineFxSpritesLoadAttempted) return;
        engineFxSpritesLoadAttempted = true;
        engineFlameSprite = loadEngineFxSpriteOrNull(ENGINE_FLAME_SPRITE);
        engineGlowSprite  = loadEngineFxSpriteOrNull(ENGINE_GLOW_SPRITE);
    }

    /**
     * Lazy-loads the shadow disc. Its own one-shot flag rather than a line in
     * {@link #ensureEngineFxSprites()}, because the two are unrelated: an
     * aircraft with no engine plume still casts, and a frame with no aircraft
     * in it still wants shadows without paying for the engine textures.
     */
    public void ensureShadowSprite() {
        if (shadowBlobLoadAttempted) return;
        shadowBlobLoadAttempted = true;
        shadowBlobSprite = loadEngineFxSpriteOrNull(SHADOW_BLOB_SPRITE);
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

    /**
     * Loads the sprite for every airframe a berth or a flight can put on the
     * map — transports and fighters both.
     *
     * <p>One pass over two enums rather than two caches, because the thing
     * doing the drawing has an {@link Airframe} and does not know or care
     * which list it came from. Two airframes that name the same image share
     * one entry; the map is keyed by the airframe so a later per-type tint or
     * frame choice has somewhere to live.
     */
    public void ensureAirframeSprites() {
        if (airframeSpritesLoadAttempted) return;
        airframeSpritesLoadAttempted = true;
        for (ShuttleType type : ShuttleType.values()) loadAirframe(type);
        for (FighterProfile fighter : FighterProfile.values()) loadAirframe(fighter);
    }

    /** Best effort for one airframe: a hull whose sprite will not read is left out. */
    private void loadAirframe(Airframe airframe) {
        String path = airframe.spritePath();
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
            airframeSprites.put(airframe, new ShuttleSpriteCache(sprite, aspect,
                    (int) w, (int) h));
            LOG.info("BattleSprites: loaded airframe " + path
                    + " (" + w + "x" + h + ", aspect=" + aspect + ")");
        } catch (Exception e) {
            LOG.error("BattleSprites: failed to load airframe sprite " + path, e);
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
            // Whole-sprite, separately rendered, and invisible types do not own
            // a unit sheet. UnitType is the authority for that distinction.
            if (!type.drawnAsSheet()) continue;
            unitSprites.put(type, loadUnitSheet(type.spritePath));
            if (type.deadSpritePath != null) {
                UnitSpriteCache dead = loadUnitSheet(type.deadSpritePath);
                if (dead != null) unitDeadSprites.put(type, dead);
            }
        }
    }

    /** Lazy-loads each marine secondary's projectile sprite AND the marine-pose sheet shown during the weapon's aim cycle. The projectile is a single sprite rotated at draw time; the aim sheet is auto-sliced into 7 frames matching the regular marine convention. */
    public void ensureMarineSecondarySprites() {
        if (marineSecondarySpritesLoadAttempted) return;
        marineSecondarySpritesLoadAttempted = true;
        SpecialEquipmentRegistry registry = SpecialEquipmentRegistry.installed();
        if (registry == null) return;
        for (SpecialEquipmentDef sec : registry.all()) {
            if (sec.projectileSpritePath() != null) {
                try {
                    Global.getSettings().loadTexture(sec.projectileSpritePath());
                    SpriteAPI sprite = Global.getSettings().getSprite(sec.projectileSpritePath());
                    if (sprite == null) {
                        LOG.warn("BattleSprites: getSprite returned null for " + sec.projectileSpritePath());
                    } else {
                        float w = sprite.getWidth();
                        float h = sprite.getHeight();
                        float aspect = (h > 0f) ? w / h : 1f;
                        ShuttleSpriteCache cache = new ShuttleSpriteCache(sprite, aspect);
                        projectileSpriteByPath.put(sec.projectileSpritePath(), cache);
                        LOG.info("BattleSprites: loaded " + sec.projectileSpritePath()
                                + " (" + w + "x" + h + ", aspect=" + aspect + ")");
                    }
                } catch (Exception e) {
                    LOG.error("BattleSprites: failed to load secondary projectile " + sec.projectileSpritePath(), e);
                }
            }
            if (sec.aimSpritePath() != null) {
                UnitSpriteCache aim = loadUnitSheet(sec.aimSpritePath());
                if (aim != null) {
                    specialEquipmentAimSheets.put(sec.id(), aim);
                }
            }
        }
        // Primary projectile sprites (field-rifle / SMG shells today). Skip
        // weapons whose projectile path is null — those share the tinted bolt.
        for (WeaponDef w : WeaponRegistry.installed().all()) {
            if (w.mount != MountClass.MARINE_PRIMARY) continue;
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
        // Bolt families may use mod or vanilla textures. The derived path set
        // keeps cache loading effect-driven and deduplicates shared styles.
        for (String path : ShotFx.boltSpritePaths()) {
            ShuttleSpriteCache cache = loadTurretSprite(path);
            if (cache != null) projectileSpriteByPath.put(path, cache);
        }
        // Bodies for delivered ordnance (today: the bomb an aircraft drops).
        // Same effect-derived path set, same shared path-keyed cache — nothing
        // here knows what carried the round.
        for (String path : OrdnanceFx.spritePaths()) {
            ShuttleSpriteCache cache = loadTurretSprite(path);
            if (cache != null) projectileSpriteByPath.put(path, cache);
        }
        // Mech chassis projectile sprites — every entry has one (chaingun
        // shell / SRM / LRM). Same load + aspect-capture pattern as the marine
        // primaries above.
        for (WeaponDef weapon : WeaponRegistry.installed().all()) {
            if (weapon.mount != MountClass.MECH_MOUNT) continue;
            String projectileSpritePath = weapon.projectileSpritePath;
            if (projectileSpritePath == null) continue;
            try {
                Global.getSettings().loadTexture(projectileSpritePath);
                SpriteAPI sprite = Global.getSettings().getSprite(projectileSpritePath);
                if (sprite == null) {
                    LOG.warn("BattleSprites: getSprite returned null for " + projectileSpritePath);
                    continue;
                }
                float pw = sprite.getWidth();
                float ph = sprite.getHeight();
                float aspect = (ph > 0f) ? pw / ph : 1f;
                ShuttleSpriteCache cache = new ShuttleSpriteCache(sprite, aspect);
                projectileSpriteByPath.put(projectileSpritePath, cache);
                LOG.info("BattleSprites: loaded mech projectile " + projectileSpritePath
                        + " (" + pw + "x" + ph + ", aspect=" + aspect + ")");
            } catch (Exception e) {
                LOG.error("BattleSprites: failed to load mech projectile " + projectileSpritePath, e);
            }
        }
    }

    public void ensureSmokeSprites() {
        if (smokeSpritesLoadAttempted) return;
        smokeSpritesLoadAttempted = true;
        try {
            SpecialEquipmentDef smoke = SpecialEquipmentRegistry.require(
                    SpecialEquipmentRegistry.SMOKE_GRENADE_ID);
            if (smoke.presentation().thrown() == null
                    || smoke.presentation().field() == null) {
                throw new IllegalStateException("Smoke equipment has incomplete usage presentation");
            }
            String grenadePath = smoke.presentation().thrown().spritePath();
            String fieldPath = smoke.presentation().field().sheetPath();
            Global.getSettings().loadTexture(grenadePath);
            smokeGrenadeSprite = Global.getSettings().getSprite(grenadePath);
            Global.getSettings().loadTexture(fieldPath);
            smokeFieldSheet = Global.getSettings().getSprite(fieldPath);
        } catch (Exception e) {
            LOG.error("BattleSprites: failed to load smoke utility sprites", e);
        }
    }

    public void ensureSatchelSprite() {
        if (satchelSpriteLoadAttempted) return;
        satchelSpriteLoadAttempted = true;
        try {
            SpecialEquipmentDef satchel = SpecialEquipmentRegistry.require(
                    SpecialEquipmentRegistry.SATCHEL_CHARGE_ID);
            if (satchel.presentation().deployed() == null) {
                throw new IllegalStateException("Satchel equipment has no deployed presentation recipe");
            }
            String path = satchel.presentation().deployed().spritePath();
            Global.getSettings().loadTexture(path);
            satchelChargeSprite = Global.getSettings().getSprite(path);
        } catch (Exception e) {
            LOG.error("BattleSprites: failed to load satchel charge sprite", e);
        }
    }

    /**
     * Loads independent body/head/feet/weapon textures for converted infantry
     * archetypes. A family is published only when every required layer
     * loaded; otherwise {@code UnitRenderService} keeps using its legacy sheet.
     */
    public void ensureLayeredUnitSprites() {
        if (layeredUnitSpritesLoadAttempted) return;
        layeredUnitSpritesLoadAttempted = true;

        // Shared prototype layers live one directory above variants.
        LayeredSpriteCache foot = loadLayeredSprite(
                "graphics/battle/marine-modular-topdown/marine-foot.png");
        LayeredSpriteCache rifle = loadLayeredSprite(MODULAR_ROOT + "weapons/rifle.png");
        LayeredSpriteCache laser = loadLayeredSprite(MODULAR_ROOT + "weapons/laser-gun.png");
        LayeredSpriteCache smg = loadLayeredSprite(MODULAR_ROOT + "weapons/smg.png");
        LayeredSpriteCache dmr = loadLayeredSprite(MODULAR_ROOT + "weapons/dmr.png");
        LayeredSpriteCache surplusRifle = loadLayeredSprite(
                MODULAR_ROOT + "weapons/grades/surplus/rifle.png");
        LayeredSpriteCache masterworkDmr = loadLayeredSprite(
                MODULAR_ROOT + "weapons/grades/masterwork/dmr.png");
        LayeredSpriteCache rocket = loadLayeredSprite(MODULAR_ROOT + "weapons/rocket-launcher.png");
        LayeredSpriteCache amr = loadLayeredSprite(MODULAR_ROOT + "weapons/anti-materiel-rifle.png");
        LayeredSpriteCache flash = loadLayeredSprite(
                "graphics/battle/marine-modular-topdown/marine-muzzle-flash.png");
        specialEquipmentLayers.clear();
        SpecialEquipmentRegistry equipmentRegistry = SpecialEquipmentRegistry.installed();
        if (equipmentRegistry != null) {
            for (SpecialEquipmentDef def : equipmentRegistry.all()) {
                EquipmentLayerDef layer = def.presentation().carrierLayer();
                if (layer == null) continue;
                LayeredSpriteCache sprite = def.id().equals(SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID)
                        ? rocket : def.id().equals(SpecialEquipmentRegistry.ANTI_MATERIEL_RIFLE_ID)
                                ? amr : loadLayeredSprite(layer.spritePath());
                if (sprite != null) specialEquipmentLayers.put(def.id(), sprite);
            }
        }
        if (foot == null || rifle == null || laser == null || smg == null || dmr == null
                || rocket == null || amr == null || flash == null || surplusRifle == null
                || masterworkDmr == null) {
            LOG.warn("BattleSprites: modular shared layers incomplete; legacy unit sheets remain active");
            return;
        }

        loadLayeredFamily(LayeredArmorFamily.ARMORLESS, "armorless", foot, rifle, laser, smg, dmr, rocket, amr, flash, surplusRifle, masterworkDmr);
        loadLayeredFamily(LayeredArmorFamily.CHARCOAL, "charcoal", foot, rifle, laser, smg, dmr, rocket, amr, flash, surplusRifle, masterworkDmr);
        loadLayeredFamily(LayeredArmorFamily.BLUE_SCOUT, "blue-scout", foot, rifle, laser, smg, dmr, rocket, amr, flash, surplusRifle, masterworkDmr);
        loadLayeredFamily(LayeredArmorFamily.RED_ELITE, "red-heavy", foot, rifle, laser, smg, dmr, rocket, amr, flash, surplusRifle, masterworkDmr);
        loadLayeredFamily(LayeredArmorFamily.OUTLAW, "outlaw", foot, rifle, laser, smg, dmr, rocket, amr, flash, surplusRifle, masterworkDmr);
        loadLayeredFamily(LayeredArmorFamily.ARMY_GREEN, "army-green", foot, rifle, laser, smg, dmr, rocket, amr, flash, surplusRifle, masterworkDmr);
        loadLayeredFamily(LayeredArmorFamily.MILITIA, "militia", foot, rifle, laser, smg, dmr, rocket, amr, flash, surplusRifle, masterworkDmr);
        loadLayeredFamily(LayeredArmorFamily.AEGIS_COMPOSITE, "aegis", foot, rifle, laser, smg, dmr, rocket, amr, flash, surplusRifle, masterworkDmr);
        loadLayeredFamily(LayeredArmorFamily.PALATINE, "palatine", foot, rifle, laser, smg, dmr, rocket, amr, flash, surplusRifle, masterworkDmr);
        loadLayeredFamily(LayeredArmorFamily.FURNACE_LINE, "furnace-line", foot, rifle, laser, smg, dmr, rocket, amr, flash, surplusRifle, masterworkDmr);
        loadLayeredFamily(LayeredArmorFamily.REAVER, "reaver", foot, rifle, laser, smg, dmr, rocket, amr, flash, surplusRifle, masterworkDmr);
        loadLayeredFamily(LayeredArmorFamily.SPECTER_HEAVY, "specter-heavy", foot, rifle, laser, smg, dmr, rocket, amr, flash, surplusRifle, masterworkDmr);
        loadLayeredFamily(LayeredArmorFamily.BULWARK_HEAVY, "bulwark-heavy", foot, rifle, laser, smg, dmr, rocket, amr, flash, surplusRifle, masterworkDmr);
        loadLayeredFamily(LayeredArmorFamily.RELIQUARY_HEAVY, "reliquary-heavy", foot, rifle, laser, smg, dmr, rocket, amr, flash, surplusRifle, masterworkDmr);
        loadLayeredFamily(LayeredArmorFamily.LIONS_MANTLE, "lions-mantle", foot, rifle, laser, smg, dmr, rocket, amr, flash, surplusRifle, masterworkDmr);
        loadLayeredFamily(LayeredArmorFamily.FOUNDRY_BREAKER, "foundry-breaker", foot, rifle, laser, smg, dmr, rocket, amr, flash, surplusRifle, masterworkDmr);
        loadLayeredFamilyAt(LayeredArmorFamily.CIVILIAN_COLONIST,
                "graphics/battle/colonist-modular-topdown/civilian/",
                foot, rifle, laser, smg, dmr, rocket, amr, flash, surplusRifle, masterworkDmr);
        loadLayeredFamilyAt(LayeredArmorFamily.ENGINEER,
                "graphics/battle/colonist-modular-topdown/engineer/",
                foot, rifle, laser, smg, dmr, rocket, amr, flash, surplusRifle, masterworkDmr);
        loadLayeredFamilyAt(LayeredArmorFamily.SCIENTIST,
                "graphics/battle/colonist-modular-topdown/scientist/",
                foot, rifle, laser, smg, dmr, rocket, amr, flash, surplusRifle, masterworkDmr);
        LayeredSpriteCache alienFoot = loadLayeredSprite(
                "graphics/battle/alien-modular-topdown/foot.png");
        LayeredSpriteCache alienForeClaw = loadLayeredSprite(
                "graphics/battle/alien-modular-topdown/fore-claw.png");
        loadLayeredFamilyAt(LayeredArmorFamily.XENO,
                "graphics/battle/alien-modular-topdown/",
                alienFoot, alienForeClaw, rifle, laser, smg, dmr, rocket, amr, flash,
                surplusRifle, masterworkDmr);
    }

    /** Loads the all-or-nothing modular heavy-mech set; legacy sheet remains fallback. */
    public void ensureLayeredMechSprites() {
        if (layeredMechSpritesLoadAttempted) return;
        layeredMechSpritesLoadAttempted = true;
        String root = "graphics/battle/mech-modular-topdown/";
        LayeredSpriteCache chassis = loadLayeredSprite(root + "chassis.png");
        LayeredSpriteCache socketedChassis = loadLayeredSprite(root + "chassis-socketed-variant.png");
        LayeredSpriteCache houndChassis = loadLayeredSprite(root + "chassis-hound.png");
        LayeredSpriteCache siroccoChassis = loadLayeredSprite(root + "chassis-sirocco.png");
        LayeredSpriteCache foot = loadLayeredSprite(root + "foot.png");
        LayeredSpriteCache thighBone = loadLayeredSprite(root + "thigh-bone.png");
        LayeredSpriteCache arm = loadLayeredSprite(root + "chaingun-arm.png");
        LayeredSpriteCache linearCannon = loadLayeredSprite(root + "linear-cannon-variant.png");
        LayeredSpriteCache heavyCannon = loadLayeredSprite(root + "heavy-cannon.png");
        LayeredSpriteCache srm = loadLayeredSprite(root + "srm-pod.png");
        LayeredSpriteCache lrm = loadLayeredSprite(root + "lrm-pod.png");
        LayeredSpriteCache shoulderLaser = loadLayeredSprite(root + "shoulder-laser-cannon.png");
        LayeredSpriteCache pulseLaserArm = loadLayeredSprite(root + "pulse-laser-arm.png");
        LayeredSpriteCache bastionAutocannon = loadLayeredSprite(
                root + "hegemony-bastion-autocannon.png");
        LayeredSpriteCache demolitionCannon = loadLayeredSprite(
                root + "pather-demolition-cannon.png");
        LayeredSpriteCache thermalLance = loadLayeredSprite(
                root + "lions-guard-thermal-lance.png");
        LayeredSpriteCache musterAutogun = loadLayeredSprite(root + "muster-autogun.png");
        LayeredSpriteCache quarryBreakerCannon = loadLayeredSprite(
                root + "quarry-breaker-cannon.png");
        LayeredSpriteCache pioneerRocketCradle = loadLayeredSprite(
                root + "pioneer-rocket-cradle.png");
        LayeredSpriteCache flash = loadLayeredSprite(
                "graphics/battle/marine-modular-topdown/marine-muzzle-flash.png");
        if (chassis == null || socketedChassis == null || houndChassis == null
                || siroccoChassis == null || foot == null || thighBone == null || arm == null
                || linearCannon == null || heavyCannon == null
                || srm == null || lrm == null || shoulderLaser == null
                || pulseLaserArm == null || bastionAutocannon == null
                || demolitionCannon == null || thermalLance == null
                || musterAutogun == null || quarryBreakerCannon == null
                || pioneerRocketCradle == null || flash == null) {
            LOG.warn("BattleSprites: modular mech layers incomplete; legacy heavy-mech sheet remains active");
            return;
        }
        layeredMechSprites = new LayeredMechAssets(chassis, socketedChassis,
                houndChassis, siroccoChassis, foot, thighBone, arm, linearCannon, heavyCannon,
                srm, lrm, shoulderLaser, pulseLaserArm, bastionAutocannon,
                demolitionCannon, thermalLance, musterAutogun,
                quarryBreakerCannon, pioneerRocketCradle, flash);
        layeredMechLiveries.put(MechLivery.BASE, layeredMechSprites);
        for (MechLivery livery : MechLivery.values()) {
            if (livery == MechLivery.BASE) continue;
            loadMechLivery(root, livery, socketedChassis, foot, thighBone, flash);
        }
    }

    /** A skin is usable only when every faction-painted chassis and weapon layer exists. */
    private void loadMechLivery(String baseRoot, MechLivery livery,
                                LayeredSpriteCache socketedChassis,
                                LayeredSpriteCache foot,
                                LayeredSpriteCache thighBone,
                                LayeredSpriteCache flash) {
        String root = baseRoot + "factions/" + livery.assetFolder() + "/";
        LayeredSpriteCache chassis = loadLayeredSprite(root + "chassis.png");
        LayeredSpriteCache houndChassis = loadLayeredSprite(root + "chassis-hound.png");
        LayeredSpriteCache siroccoChassis = loadLayeredSprite(root + "chassis-sirocco.png");
        LayeredSpriteCache arm = loadLayeredSprite(root + "chaingun-arm.png");
        LayeredSpriteCache linearCannon = loadLayeredSprite(root + "linear-cannon-variant.png");
        LayeredSpriteCache heavyCannon = loadLayeredSprite(root + "heavy-cannon.png");
        LayeredSpriteCache srm = loadLayeredSprite(root + "srm-pod.png");
        LayeredSpriteCache lrm = loadLayeredSprite(root + "lrm-pod.png");
        LayeredSpriteCache shoulderLaser = loadLayeredSprite(root + "shoulder-laser-cannon.png");
        LayeredSpriteCache pulseLaserArm = loadLayeredSprite(root + "pulse-laser-arm.png");
        LayeredSpriteCache bastionAutocannon = loadLayeredSprite(
                root + "hegemony-bastion-autocannon.png");
        LayeredSpriteCache demolitionCannon = loadLayeredSprite(
                root + "pather-demolition-cannon.png");
        LayeredSpriteCache thermalLance = loadLayeredSprite(
                root + "lions-guard-thermal-lance.png");
        LayeredSpriteCache musterAutogun = loadLayeredSprite(root + "muster-autogun.png");
        LayeredSpriteCache quarryBreakerCannon = loadLayeredSprite(
                root + "quarry-breaker-cannon.png");
        LayeredSpriteCache pioneerRocketCradle = loadLayeredSprite(
                root + "pioneer-rocket-cradle.png");
        if (chassis == null || houndChassis == null || siroccoChassis == null
                || arm == null || linearCannon == null || heavyCannon == null
                || srm == null || lrm == null || shoulderLaser == null
                || pulseLaserArm == null || bastionAutocannon == null
                || demolitionCannon == null || thermalLance == null
                || musterAutogun == null || quarryBreakerCannon == null
                || pioneerRocketCradle == null) {
            LOG.warn("BattleSprites: modular mech livery " + livery
                    + " incomplete; actors using it keep the complete base set");
            return;
        }
        layeredMechLiveries.put(livery, new LayeredMechAssets(
                chassis, socketedChassis, houndChassis, siroccoChassis,
                foot, thighBone, arm, linearCannon, heavyCannon,
                srm, lrm, shoulderLaser, pulseLaserArm, bastionAutocannon,
                demolitionCannon, thermalLance, musterAutogun,
                quarryBreakerCannon, pioneerRocketCradle, flash));
    }

    private void loadLayeredFamily(LayeredArmorFamily familyId, String family,
                                   LayeredSpriteCache foot,
                                   LayeredSpriteCache rifle,
                                   LayeredSpriteCache laser,
                                   LayeredSpriteCache smg,
                                   LayeredSpriteCache dmr,
                                   LayeredSpriteCache rocket,
                                   LayeredSpriteCache amr,
                                   LayeredSpriteCache flash,
                                   LayeredSpriteCache surplusRifle,
                                   LayeredSpriteCache masterworkDmr) {
        loadLayeredFamilyAt(familyId, MODULAR_ROOT + "armor/" + family + "/",
                foot, null, rifle, laser, smg, dmr, rocket, amr, flash,
                surplusRifle, masterworkDmr);
    }

    private void loadLayeredFamilyAt(LayeredArmorFamily familyId, String familyRoot,
                                     LayeredSpriteCache foot,
                                     LayeredSpriteCache rifle,
                                     LayeredSpriteCache laser,
                                     LayeredSpriteCache smg,
                                     LayeredSpriteCache dmr,
                                     LayeredSpriteCache rocket,
                                     LayeredSpriteCache amr,
                                     LayeredSpriteCache flash,
                                     LayeredSpriteCache surplusRifle,
                                     LayeredSpriteCache masterworkDmr) {
        loadLayeredFamilyAt(familyId, familyRoot, foot, null, rifle, laser, smg, dmr,
                rocket, amr, flash, surplusRifle, masterworkDmr);
    }

    private void loadLayeredFamilyAt(LayeredArmorFamily familyId, String familyRoot,
                                     LayeredSpriteCache foot,
                                     LayeredSpriteCache foreClaw,
                                     LayeredSpriteCache rifle,
                                     LayeredSpriteCache laser,
                                     LayeredSpriteCache smg,
                                     LayeredSpriteCache dmr,
                                     LayeredSpriteCache rocket,
                                     LayeredSpriteCache amr,
                                     LayeredSpriteCache flash,
                                     LayeredSpriteCache surplusRifle,
                                     LayeredSpriteCache masterworkDmr) {
        LayeredSpriteCache body = loadLayeredSprite(familyRoot + "body.png");
        LayeredSpriteCache head = loadLayeredSprite(familyRoot + "head.png");
        if (body == null || head == null || foot == null
                || (familyId == LayeredArmorFamily.XENO && foreClaw == null)) {
            LOG.warn("BattleSprites: modular family " + familyId
                    + " incomplete; actors using it keep their legacy sheet");
            return;
        }
        LayeredUnitAssets assets = new LayeredUnitAssets(body, head, foot, foreClaw,
                rifle, laser, smg, dmr, rocket, amr, flash, surplusRifle, masterworkDmr);
        for (Map.Entry<String, LayeredSpriteCache> entry : specialEquipmentLayers.entrySet()) {
            assets.registerSpecialEquipment(entry.getKey(), entry.getValue());
        }
        layeredUnitSprites.put(familyId, assets);
    }

    /** Whole transparent PNG loader that captures image pixels before SpriteAPI mutation. */
    public LayeredSpriteCache loadLayeredSprite(String path) {
        try {
            Global.getSettings().loadTexture(path);
            SpriteAPI sprite = Global.getSettings().getSprite(path);
            if (sprite == null) {
                LOG.warn("BattleSprites: getSprite returned null for modular layer " + path);
                return null;
            }
            try (InputStream stream = Global.getSettings().openStream(path)) {
                BufferedImage image = ImageIO.read(stream);
                if (image == null) {
                    LOG.warn("BattleSprites: ImageIO.read returned null for modular layer " + path);
                    return null;
                }
                LOG.info("BattleSprites: loaded modular layer " + path + " ("
                        + image.getWidth() + "x" + image.getHeight() + ")");
                return new LayeredSpriteCache(sprite, path,
                        image.getWidth(), image.getHeight());
            }
        } catch (Exception e) {
            LOG.error("BattleSprites: failed to load modular layer " + path, e);
            return null;
        }
    }

    public void ensureTurretSprites() {
        if (turretSpritesLoadAttempted) return;
        turretSpritesLoadAttempted = true;
        for (StructureDef structure : TurretCatalogRegistry.installed().structures()) {
            loadTurretSpriteInto(turretSprites, structure.id, structure.mount.spritePath);
            loadTurretSpriteInto(turretRecoilSprites, structure.id,
                    structure.mount.recoilSpritePath);
            String projectilePath = structure.mount.weapon.projectileSpritePath;
            ShuttleSpriteCache proj = loadTurretSprite(projectilePath);
            if (proj != null) projectileSpriteByPath.put(projectilePath, proj);
        }
    }

    public void ensureDroneHubSprite() {
        if (droneHubSpriteLoadAttempted) return;
        droneHubSpriteLoadAttempted = true;
        String path = DroneHub.SPRITE_PATH;
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

    /**
     * Loads one turret-related sprite + its native aspect (captured before any
     * {@code setSize} clobbers {@code getWidth/getHeight}); null when the
     * optional path is absent or loading fails.
     */
    public ShuttleSpriteCache loadTurretSprite(String path) {
        if (path == null) return null;
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

    /** {@link #loadTurretSprite} into a structure-id map read by the turret renderer. */
    public void loadTurretSpriteInto(Map<String, ShuttleSpriteCache> cache,
                                     String structureId, String path) {
        ShuttleSpriteCache c = loadTurretSprite(path);
        if (c != null) cache.put(structureId, c);
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
