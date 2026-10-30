package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.vehicle.TurretMountStyle;
import com.dillon.starsectormarines.render2d.texture.ModTextures;
import com.dillon.starsectormarines.render2d.texture.TextureLease;
import com.dillon.starsectormarines.render2d.texture.TextureLedger;
import com.dillon.starsectormarines.render2d.texture.TextureUsage;
import com.dillon.starsectormarines.DevConfig;
import com.dillon.starsectormarines.battle.air.Airframe;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.appearance.MechLivery;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts;
import com.dillon.starsectormarines.battle.drone.DroneHub;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.weapon.MountClass;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.battle.world.tiles.SheetTexture;
import com.dillon.starsectormarines.battle.world.tiles.GridBlockDef;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetFrames;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetSlicer;
import com.dillon.starsectormarines.marine.EquipmentLayerDef;
import com.dillon.starsectormarines.marine.IntegralSystemDef;
import com.dillon.starsectormarines.marine.MarineArmorCatalogDef;
import com.dillon.starsectormarines.marine.MarineArmorCatalogRegistry;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentPresentationDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentPresentationDef.HandsetLayer;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import java.util.EnumMap;
import org.apache.log4j.Logger;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Asset/sprite-cache registry for the battle screen. Owns all loaded
 * {@link SpriteAPI} sheets, their {@link SpriteSheetFrames}, the content
 * px-dimensions, per-type {@link java.util.EnumMap} caches, and every
 * {@code ensure*}/{@code load*} method. {@link BattleRenderer} owns the
 * per-sheet batches that consume these assets.
 */
public class BattleSprites {

    private static final Logger LOG = Global.getLogger(BattleSprites.class);

    /**
     * Everything this instance loads is held under this lease ({@code battle-textures.md}). The
     * battle screen gives each battle its own and releases it when the battle ends, after which
     * this instance's sprites are dead and the instance is dropped; every other owner loads on
     * the session lease and keeps its textures as it always did.
     */
    private final TextureLease lease;

    /** Loads for the whole session: the company deck, the Armory, the combat bridge. */
    public BattleSprites() {
        this(TextureLedger.shared().session());
    }

    /** Loads under {@code lease}, which keeps the textures until it is released. */
    public BattleSprites(TextureLease lease) {
        this.lease = lease;
    }

    /** The lease this instance's textures are held under. */
    public TextureLease lease() {
        return lease;
    }

    // ---- asset-path constants -----------------------------------------------

    private static final String SPRITE_DECAL_SHEET  = "graphics/decals/decals.png";
    /** Vanilla, so the headless sprite set resolves these off the install root rather than through the game. */
    static final String WARSHIP_ENGINE_FLAME_SPRITE = "graphics/fx/engineflame32.png";
    static final String WARSHIP_ENGINE_GLOW_SPRITE = "graphics/fx/engineglow32.png";
    static final String ENGINE_FLAME_SPRITE = "graphics/battle/fx/aircraft-engine-flame.png";
    static final String ENGINE_GLOW_SPRITE  = "graphics/battle/fx/aircraft-engine-glow.png";
    /**
     * The soft disc a body's shadow is drawn with. Ours rather than the
     * vanilla engine glow the shadow layer used to borrow, which is a
     * four-lobed flare and not a falloff at all — see
     * {@code art-source/fx/build_shadow_blob.py}.
     */
    static final String SHADOW_BLOB_SPRITE  = "graphics/battle/fx/shadow-blob.png";
    private static final String ICON_DANGER         = "graphics/icons/Danger sign 1 512 px.png";
    private static final String ICON_STAR           = "graphics/icons/Star 512 px.png";

    // ---- unit corpse sheets -------------------------------------------------

    private final java.util.EnumMap<UnitType, UnitSpriteCache> unitDeadSprites =
            new java.util.EnumMap<>(UnitType.class);
    private boolean unitSpritesLoadAttempted;

    // ---- modular layered infantry -----------------------------------------

    private static final String MODULAR_ROOT =
            "graphics/battle/marine-modular-topdown/variants/";
    private RavRunnerAssets ravRunner;
    private RavBruteAssets ravBrute;
    private RavMatriarchAssets ravMatriarch;
    private RavSpitterAssets ravSpitter;
    private RavBombardAssets ravBombard;
    /** Loaded families; asking for one not loaded loads it, once the shared layers have been. */
    private final java.util.EnumMap<LayeredArmorFamily, LayeredUnitAssets> layeredUnitSprites =
            new java.util.EnumMap<>(LayeredArmorFamily.class) {
                @Override
                public LayeredUnitAssets get(Object key) {
                    LayeredUnitAssets assets = super.get(key);
                    if (assets == null && key instanceof LayeredArmorFamily family
                            && layeredUnitSpritesLoadAttempted && !familiesAttempted.contains(family)) {
                        unplanned("infantry family " + family);
                        ensureLayeredFamily(family);
                        assets = super.get(key);
                    }
                    return assets;
                }
            };
    private final Map<String, LayeredSpriteCache> specialEquipmentLayers = new LinkedHashMap<>();
    private final Map<String, LayeredSpriteCache> integralSystemLayers = new LinkedHashMap<>();
    private boolean layeredUnitSpritesLoadAttempted;
    /** Pools drawn under a fallen layered body, picked by its death-pose roll. */
    private final List<LayeredSpriteCache> bloodPools = new ArrayList<>();
    static final String BLOOD_POOL_ROOT = "graphics/battle/downed/blood-pool-";
    static final int BLOOD_POOLS = 4;

    // ---- modular layered heavy mech ---------------------------------------

    private LayeredMechAssets layeredMechSprites;
    private final java.util.EnumMap<MechLivery, LayeredMechAssets> layeredMechLiveries =
            new java.util.EnumMap<>(MechLivery.class);
    private final java.util.EnumMap<Faction, MechLivery> mechLiveryBySide =
            new java.util.EnumMap<>(Faction.class);
    private boolean layeredMechSpritesLoadAttempted;
    private final EnumMap<TurretMountStyle, LayeredSpriteCache> vehicleGunMounts =
            new EnumMap<>(TurretMountStyle.class);
    private LayeredSpriteCache vehicleTurretCollar;
    private LayeredSpriteCache vehicleTurretBase;
    private LayeredSpriteCache vehicleTurretBaseLight;
    private LayeredSpriteCache vehicleGunPlate;
    private LayeredSpriteCache vehicleKitActiveProtection;
    private LayeredSpriteCache vehicleKitActiveProtectionTurret;
    private final EnumMap<MechWeaponComponent, LayeredSpriteCache> vehicleGunDrawings =
            new EnumMap<>(MechWeaponComponent.class);

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
    private final SheetTexture roofTex       = SheetTexture.grid(RoofTiles.SHEET);
    private final SheetTexture waterTex       = SheetTexture.grid(TileManifest.WATER_SHEET);
    private final SheetTexture urbanTile3Tex = SheetTexture.sliced(TileManifest.STREET3_SHEET);
    private final SheetTexture natureTex     = SheetTexture.sliced(TileManifest.NATURE_SHEET);
    private final SheetTexture doodadTex     = SheetTexture.grid(TileManifest.DOODAD_SHEET);
    private final SheetTexture parkedVehicleTex = SheetTexture.grid(TileManifest.PARKED_VEHICLE_SHEET);
    private final Map<String, SheetTexture> extraTerrainSheets = new LinkedHashMap<>();

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

    /** Loaded vehicle sheets; asking for a line not loaded loads it, once a load has been asked for. */
    private final java.util.EnumMap<VehicleType, UnitSpriteCache> convoySprites =
            new java.util.EnumMap<>(VehicleType.class) {
                @Override
                public UnitSpriteCache get(Object key) {
                    UnitSpriteCache cache = super.get(key);
                    if (cache == null && key instanceof VehicleType type
                            && convoySpritesLoadAttempted && !vehiclesAttempted.contains(type)) {
                        unplanned("vehicle line " + type);
                        loadConvoySprite(type);
                        cache = super.get(key);
                    }
                    return cache;
                }
            };
    private final EnumSet<VehicleType> vehiclesAttempted = EnumSet.noneOf(VehicleType.class);
    private boolean convoySpritesLoadAttempted;

    // ---- engine FX sprites --------------------------------------------------

    private SpriteAPI warshipEngineFlameSprite;
    private SpriteAPI warshipEngineGlowSprite;
    private SpriteAPI engineFlameSprite;
    private SpriteAPI engineGlowSprite;
    private SpriteAPI shadowBlobSprite;
    /** The disc's size as loaded, before any draw resizes the sprite; see {@link UnitAtlas#offer}. */
    private int shadowBlobPxW;
    private int shadowBlobPxH;
    private boolean shadowBlobLoadAttempted;
    private boolean engineFxSpritesLoadAttempted;

    // ---- objective icons ----------------------------------------------------

    private SpriteAPI iconDanger;
    private SpriteAPI iconStar;
    private boolean iconsLoadAttempted;
    private SpriteAPI smokeGrenadeSprite;
    private SpriteAPI smokeFieldSheet;
    /** The smoke-and-fire flipbook the ground fire draws its flames from (`fire-nouns.md`, Look). */
    private SpriteAPI fireSheet;
    private SpriteAPI flameSheet;
    public static final String FIRE_SHEET_PATH = "graphics/particle/smokeAndFire.png";
    private boolean smokeSpritesLoadAttempted;
    private SpriteAPI satchelChargeSprite;
    private boolean satchelSpriteLoadAttempted;

    // =========================================================================
    // Accessors
    // =========================================================================

    public java.util.EnumMap<UnitType, UnitSpriteCache> unitDeadSprites()      { return unitDeadSprites; }
    public java.util.EnumMap<LayeredArmorFamily, LayeredUnitAssets> layeredUnitSprites() { return layeredUnitSprites; }
    /** The Runner's skeleton bones, or null when they did not all load. */
    public RavRunnerAssets ravRunnerSprites() { return ravRunner; }

    /** The Rav Bloom Brute's rig sprites, or null when incomplete (the Brute then keeps its layered art). */
    public RavBruteAssets ravBruteSprites() { return ravBrute; }
    /** The Matriarch's rig sprites, or null when incomplete (it then wears the Brute's layered art). */
    public RavMatriarchAssets ravMatriarchSprites() { return ravMatriarch; }
    /** The Spitter's rig sprites, or null when incomplete (the Spitter then keeps its layered art). */
    public RavSpitterAssets ravSpitterSprites() { return ravSpitter; }
    /** The Bombard's rig sprites, or null when incomplete (the Bombard then keeps its stand-in art). */
    public RavBombardAssets ravBombardSprites() { return ravBombard; }
    public LayeredMechAssets layeredMechSprites() { return layeredMechSprites; }
    /** The turret mount ring of this finish that a vehicle's gun stands on; null when the art did not load. */
    public LayeredSpriteCache vehicleGunMount(TurretMountStyle style) { return vehicleGunMounts.get(style); }
    /** The collar that turns with a modular turret inside its ring; null when the art did not load. */
    public LayeredSpriteCache vehicleTurretCollar() { return vehicleTurretCollar; }
    /** The round base a laser stands in on a modular turret: two rows or more, one row. Null when the art did not load. */
    public LayeredSpriteCache vehicleTurretBase(boolean light) { return light ? vehicleTurretBaseLight : vehicleTurretBase; }
    /**
     * The armoured plate that turns with a fitted weapon on a fixed hull's mount ring, gun slot to the top
     * ({@link com.dillon.starsectormarines.battle.vehicle.VehicleGun#plateCells}). Null when the art did not load.
     */
    public LayeredSpriteCache vehicleGunPlate() { return vehicleGunPlate; }
    /**
     * The active-protection kit's launchers and sensor panels, drawn over a gun tank's live hull in its frame's
     * registration ({@link com.dillon.starsectormarines.battle.vehicle.VehicleKit#ACTIVE_PROTECTION}). Null when the
     * art did not load.
     */
    public LayeredSpriteCache vehicleKitActiveProtection() { return vehicleKitActiveProtection; }
    /** The same fit in the Onager's turret frame registration, turning with the turret housing. Null when not loaded. */
    public LayeredSpriteCache vehicleKitActiveProtectionTurret() { return vehicleKitActiveProtectionTurret; }
    /** The drawing a hull carries {@code weapon} in instead of the mech's ({@link VehicleGunArt#vehicleDrawingPath}); null for none. */
    public LayeredSpriteCache vehicleGunDrawing(MechWeaponComponent weapon) { return vehicleGunDrawings.get(weapon); }
    /** Pools under fallen layered bodies; empty when the art did not load. */
    public List<LayeredSpriteCache> bloodPools() { return bloodPools; }

    /**
     * Every faction livery that loaded completely.
     *
     * <p>Exposed for a consumer that needs the whole set rather than the one a
     * side is wearing — {@link UnitAtlas}'s layout, which is settled once and
     * must hold whichever livery a later screen attachment selects.
     */
    public Map<MechLivery, LayeredMechAssets> layeredMechLiveries() {
        return layeredMechLiveries;
    }

    /** Complete livery selected for one tactical side, with the base set as fail-safe. */
    public LayeredMechAssets layeredMechSprites(Faction side) {
        MechLivery livery = mechLiveryBySide.getOrDefault(side, MechLivery.BASE);
        LayeredMechAssets selected = layeredMechLiveries.get(livery);
        if (selected == null && layeredMechSpritesLoadAttempted && livery != MechLivery.BASE
                && !liveriesAttempted.contains(livery)) {
            unplanned("mech livery " + livery);
            ensureMechLivery(livery);
            selected = layeredMechLiveries.get(livery);
        }
        return selected != null ? selected : layeredMechSprites();
    }

    /**
     * Binds campaign identities to the two tactical sides. This is presentation
     * state only and may be refreshed on every screen attachment, before or
     * after the immutable sprite families have loaded.
     */
    /** The liveries {@code sides} wear, as {@link #configureMechLiveries} bound them. */
    public Set<MechLivery> liveriesWornBy(Set<Faction> sides) {
        EnumSet<MechLivery> worn = EnumSet.noneOf(MechLivery.class);
        for (Faction side : sides) worn.add(mechLiveryBySide.getOrDefault(side, MechLivery.BASE));
        return worn;
    }

    public void configureMechLiveries(String marineFactionId, String defenderFactionId) {
        mechLiveryBySide.put(Faction.MARINE, MechLivery.forFactionId(marineFactionId));
        mechLiveryBySide.put(Faction.DEFENDER, MechLivery.forFactionId(defenderFactionId));
        mechLiveryBySide.put(Faction.CIVILIAN, MechLivery.BASE);
    }
    public Map<String, ShuttleSpriteCache> turretSprites()   { return turretSprites; }
    public Map<String, ShuttleSpriteCache> turretRecoilSprites() { return turretRecoilSprites; }
    /** Carrier-agnostic projectile-sprite lookup by texture path (what {@code ShotFx.Sprite} resolves against). Null if not loaded / no such path. */
    public ShuttleSpriteCache projectileSprite(String path) { return path == null ? null : projectileSpriteByPath.get(path); }
    public SpriteAPI smokeGrenadeSprite() { return smokeGrenadeSprite; }
    /** The deployed smoke field's flipbook sheet; frames are addressed by {@code SpecialEquipmentPresentationDef.Field}. */
    public SpriteAPI smokeFieldSheet() { return smokeFieldSheet; }
    /** The ground fire's flame sheet, loaded with the smoke sprites. */
    public SpriteAPI fireSheet() { return fireSheet; }
    /** The top-down flame sheet ({@link FireRenderSystem#FLAME_SHEET_PATH}), or null where it failed to load. */
    public SpriteAPI flameSheet() { return flameSheet; }
    public SpriteAPI satchelChargeSprite() { return satchelChargeSprite; }
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
    public SpriteAPI roofSheet()                   { return roofTex.sprite(); }
    public int roofSheetPxW()                      { return roofTex.pxW(); }
    public int roofSheetPxH()                      { return roofTex.pxH(); }
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

    /** Hull art belongs to the pattern; a boat's identity and fittings are not asset keys. */
    public ShuttleSpriteCache airframeSprite(Airframe frame) {
        ShuttleType pattern = ShuttleType.fromAirframe(frame);
        return airframeSprites().get(pattern != null ? pattern : frame);
    }

    public java.util.EnumMap<VehicleType, UnitSpriteCache> convoySprites() { return convoySprites; }
    public SpriteAPI warshipEngineFlameSprite() { return warshipEngineFlameSprite; }
    public SpriteAPI warshipEngineGlowSprite() { return warshipEngineGlowSprite; }
    public SpriteAPI engineFlameSprite()           { return engineFlameSprite; }
    public SpriteAPI engineGlowSprite()            { return engineGlowSprite; }
    public SpriteAPI shadowBlobSprite()            { return shadowBlobSprite; }
    public int shadowBlobPxW()                     { return shadowBlobPxW; }
    public int shadowBlobPxH()                     { return shadowBlobPxH; }
    public SpriteAPI iconDanger()                  { return iconDanger; }
    public SpriteAPI iconStar()                    { return iconStar; }
    /** Authored pose authority; overridable by tooling that loads assets off disk. */
    public UnitLayerLayouts unitLayerLayouts()     { return UnitLayerLayouts.get(); }


    /**
     * A battle's texture loads as load-queue steps ({@code battle-textures.md}): its terrain, then
     * only the families, liveries and vehicle lines {@code set} names — every one shipped when it
     * is null — a step per family, livery and line so no one step holds the GL thread long enough
     * to stop the loading screen's spinner. {@code stage} turns a stage's string key into the line
     * the loading screen shows. The battle screen appends its atlas step after these.
     */
    public List<TextureLoadQueue.Step> battleLoadSteps(BattleTextureSet set, Function<String, String> stage) {
        return battleLoadSteps(set, null, stage);
    }

    /**
     * {@link #battleLoadSteps(BattleTextureSet, Function)}, loading of the catalog's terrain sheets
     * only {@code terrainSheets} ({@link BattleTerrainSheets#of}), a step each, in catalog order.
     * Null loads every sheet the catalog names in the first step, as a battle did before its
     * terrain was filtered. Either way a catalog sheet asked for and not loaded is loaded on first
     * ask ({@link #extraTerrainSheet}).
     */
    public List<TextureLoadQueue.Step> battleLoadSteps(BattleTextureSet set, Set<String> terrainSheets,
                                                       Function<String, String> stage) {
        List<TextureLoadQueue.Step> steps = new ArrayList<>();
        String loadingTerrain = stage.apply("battleLoadingTerrain");
        steps.add(new TextureLoadQueue.Step(loadingTerrain, () -> {
            if (terrainSheets == null) ensureTileSheet();
            else ensureUrbanSheet();
            ensureRoadSheet();
            ensureFloorsSheet();
            ensureRoofSheet();
            ensureNatureSheet();
        }));
        steps.add(new TextureLoadQueue.Step(loadingTerrain, () -> {
            ensureWaterSheet();
            ensureUrbanTile3Sheet();
            ensureDoodadSheet();
            ensureDecalSheet();
        }));
        if (terrainSheets != null) {
            for (String path : terrainSheetPaths()) {
                if (terrainSheets.contains(path)) {
                    steps.add(new TextureLoadQueue.Step(loadingTerrain, () -> ensureTerrainSheet(path)));
                }
            }
        }
        // Only what this battle places, spawns and can bring in; a step per family, livery and
        // vehicle line, so no one step holds the GL thread long enough to stop the spinner.
        String loadingSprites = stage.apply("battleLoadingSprites");
        steps.add(new TextureLoadQueue.Step(loadingSprites, () -> {
            ensureUnitSheets();
            ensureInfantryShared();
        }));
        for (LayeredArmorFamily family : set != null ? set.families() : EnumSet.allOf(LayeredArmorFamily.class)) {
            steps.add(new TextureLoadQueue.Step(loadingSprites, () -> ensureLayeredFamily(family)));
        }
        if (set == null || set.ravRigs()) steps.add(new TextureLoadQueue.Step(loadingSprites, this::ensureRavRigs));
        Set<MechLivery> liveries = set != null ? liveriesWornBy(set.mechSides()) : EnumSet.allOf(MechLivery.class);
        steps.add(new TextureLoadQueue.Step(loadingSprites, () -> ensureLayeredMechSprites(Set.of())));
        for (MechLivery livery : liveries) {
            steps.add(new TextureLoadQueue.Step(loadingSprites, () -> ensureMechLivery(livery)));
        }
        steps.add(new TextureLoadQueue.Step(loadingSprites, this::ensureTurretSprites));
        steps.add(new TextureLoadQueue.Step(loadingSprites, this::ensureMarineSecondarySprites));
        steps.add(new TextureLoadQueue.Step(loadingSprites, () -> {
            ensureAirframeSprites();
            ensureDroneSprite();
            ensureDroneHubSprite();
            ensureEngineFxSprites();
        }));
        steps.add(new TextureLoadQueue.Step(loadingSprites, () -> ensureConvoySprites(Set.of())));
        for (VehicleType type : set != null ? set.vehicles() : EnumSet.allOf(VehicleType.class)) {
            steps.add(new TextureLoadQueue.Step(loadingSprites, () -> loadConvoySprite(type)));
        }
        steps.add(new TextureLoadQueue.Step(stage.apply("battleLoadingEffects"), this::ensureObjectiveIcons));
        return steps;
    }

    // =========================================================================
    // Ensure methods (moved verbatim from BattleScreen; batch lines deleted)
    // =========================================================================

    /**
     * Lazy-loads the indoor tileset (urban-tileset.png). The handle captures
     * content dimensions for the per-tile UV math and caches across attach
     * calls; see {@link SheetTexture} for the load/fallback contract shared by
     * all six tile sheets.
     */
    public void ensureTileSheet() {
        ensureUrbanSheet();
        for (String path : terrainSheetPaths()) ensureTerrainSheet(path);
    }

    /**
     * The indoor tileset alone, without the catalog's other sheets. From here on a catalog sheet
     * nobody loaded is loaded the first time it is asked for ({@link #extraTerrainSheet}).
     */
    public void ensureUrbanSheet() {
        tileTex.ensureLoaded(lease);
        terrainOnDemand = true;
    }

    /** Whether a catalog sheet asked for and not loaded is loaded then; see {@link #ensureUrbanSheet()}. */
    private boolean terrainOnDemand;
    /** {@link #terrainSheetPaths()}, as a set, once asked. */
    private Set<String> terrainCatalog;

    /**
     * Every catalog-declared grid sheet {@link #ensureTileSheet()} loads besides the indoor
     * tileset, in its order, so a caller can load them one at a time.
     */
    public List<String> terrainSheetPaths() {
        TileRegistry registry = TileRegistry.installed();
        if (registry == null) return List.of();
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        for (GridBlockDef block : registry.blocks()) {
            String path = block.sheetPath;
            if (path.equals(TileManifest.SHEET) || path.equals(TileManifest.ROAD_SHEET)
                    || path.equals(TileManifest.FLOORS_SHEET) || path.equals(TileManifest.WATER_SHEET)) continue;
            paths.add(path);
        }
        for (DoodadDef doodad : registry.doodads()) {
            String path = doodad.sheetPath;
            if (path.equals(TileManifest.SHEET) || path.equals(TileManifest.ROAD_SHEET)
                    || path.equals(TileManifest.DOODAD_SHEET) || path.equals(TileManifest.PARKED_VEHICLE_SHEET)) continue;
            paths.add(path);
        }
        return List.copyOf(paths);
    }

    /** One catalog sheet from {@link #terrainSheetPaths()}. */
    public void ensureTerrainSheet(String path) {
        extraTerrainSheets.computeIfAbsent(path, SheetTexture::grid).ensureLoaded(lease);
    }

    /**
     * A catalog-declared grid sheet. One a host loads only some of ({@code CompanyDeck} loads what
     * her deck places) is loaded the first time it is asked for, under this lease: a hitch rather
     * than a blank, logged in the dev build as unplanned. The renderer registers its batch before
     * the next drain.
     */
    public SpriteAPI extraTerrainSheet(String path) {
        SheetTexture texture = extraTerrainSheets.get(path);
        if (texture == null && terrainOnDemand && path != null) {
            if (terrainCatalog == null) terrainCatalog = new HashSet<>(terrainSheetPaths());
            if (terrainCatalog.contains(path)) {
                if (terrainSurvey != null) {
                    terrainSurvey.add(path);
                    return null;
                }
                unplanned("terrain sheet " + path);
                ensureTerrainSheet(path);
                texture = extraTerrainSheets.get(path);
            }
        }
        return texture == null ? null : texture.sprite();
    }

    /** While a survey runs, the catalog sheets asked for and not loaded, instead of loading them. */
    private Set<String> terrainSurvey;

    /**
     * From here until {@link #endTerrainSurvey()}, a catalog sheet asked for and not loaded is
     * recorded rather than loaded, so a host can learn what a frame would load and load it a sheet
     * at a time. Whatever drew during the survey drew without those sheets and is thrown away.
     */
    public void beginTerrainSurvey() {
        terrainSurvey = new LinkedHashSet<>();
    }

    /** The sheets asked for since {@link #beginTerrainSurvey()}; loading on demand resumes. */
    public List<String> endTerrainSurvey() {
        List<String> asked = terrainSurvey == null ? List.of() : List.copyOf(terrainSurvey);
        terrainSurvey = null;
        return asked;
    }

    /**
     * {@link #extraTerrainSheet} without loading it: null when nobody has. Where nothing loads on
     * demand the two are the same question, so a set that answers {@link #extraTerrainSheet} its
     * own way (the headless review set) is asked that way.
     */
    public SpriteAPI loadedTerrainSheet(String path) {
        if (!terrainOnDemand) return extraTerrainSheet(path);
        SheetTexture texture = extraTerrainSheets.get(path);
        return texture == null ? null : texture.sprite();
    }

    /** How many catalog sheets have been loaded or tried; a renderer re-registers batches when it changes. */
    public int terrainSheetCount() {
        return extraTerrainSheets.size();
    }

    /** Loaded catalog sheets for batch registration at attach time. */
    public java.util.Collection<SpriteAPI> extraTerrainSheets() {
        java.util.List<SpriteAPI> loaded = new java.util.ArrayList<>();
        for (SheetTexture texture : extraTerrainSheets.values()) {
            if (texture.sprite() != null) loaded.add(texture.sprite());
        }
        return loaded;
    }

    /** Lazy-loads the road sheet (urban-tileset-2.png) — its own PNG so road art iterates independently of the indoor floors. */
    public void ensureRoadSheet()       { roadTex.ensureLoaded(lease); }

    /** Lazy-loads the high-resolution outdoor surfaces sheet (Floors_Tiles.png), with 56px source cells drawn into the 32px nav grid. */
    public void ensureFloorsSheet()     { floorsTex.ensureLoaded(lease); }

    /** Lazy-loads the roof sheet ({@link RoofTiles}): a row of field, eave trims and damage frames per roof style. */
    public void ensureRoofSheet()       { roofTex.ensureLoaded(lease); }

    /** Lazy-loads the Water_tiles sheet (legacy 16px cells, upscaled to the 32px nav grid). */
    public void ensureWaterSheet()      { waterTex.ensureLoaded(lease); }

    /**
     * Lazy-loads the nature-tiles sheet — a sliced strip (grass / dirt / sand /
     * water grounds plus plant/rock overlays). On a slicer-vs-registry count
     * mismatch the handle nulls itself so the renderer falls back to the legacy
     * Floors_Tiles grass/dirt; {@link #natureFrames()} is null in that case.
     */
    public void ensureNatureSheet()     { natureTex.ensureLoaded(lease); }

    /**
     * Lazy-loads the urban-tileset-3 sheet — a sliced strip (modern road +
     * sidewalk look). On a slicer-vs-registry count mismatch the handle nulls
     * itself so STREET cells fall back to the legacy road autotile;
     * {@link #urbanTile3Frames()} is null in that case.
     */
    public void ensureUrbanTile3Sheet() { urbanTile3Tex.ensureLoaded(lease); }

    /** Lazy-loads the dedicated 32px generated-doodad atlas. */
    /** Loads both prop atlases — the generated doodad sheet and the parked-vehicle
     *  sheet. One call because they are one layer's art: a caller that wants props
     *  drawn wants trucks drawn, and splitting the two only invites a host that
     *  loads one and silently drops the other. */
    public void ensureDoodadSheet()     { doodadTex.ensureLoaded(lease); parkedVehicleTex.ensureLoaded(lease); }

    /**
     * Lazy-loads aircraft textures and the separate vanilla warship textures. Same one-shot
     * pattern as {@link #ensureAirframeSprites()}: try to load each path
     * once, log + degrade gracefully if either fails. A missing engine
     * sprite just means the engine pass renders nothing — no crash.
     */
    public void ensureEngineFxSprites() {
        if (engineFxSpritesLoadAttempted) return;
        engineFxSpritesLoadAttempted = true;
        warshipEngineFlameSprite = loadEngineFxSpriteOrNull(WARSHIP_ENGINE_FLAME_SPRITE);
        warshipEngineGlowSprite = loadEngineFxSpriteOrNull(WARSHIP_ENGINE_GLOW_SPRITE);
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
        if (shadowBlobSprite != null) {
            shadowBlobPxW = Math.round(shadowBlobSprite.getWidth());
            shadowBlobPxH = Math.round(shadowBlobSprite.getHeight());
        }
    }

    public SpriteAPI loadEngineFxSpriteOrNull(String path) {
        try {
            ModTextures.loadTexture(path, lease);
            SpriteAPI s = ModTextures.sprite(path);
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

    private final java.util.Map<String, ShuttleSpriteCache> pathSprites = new java.util.HashMap<>();

    /**
     * A sprite loaded by its path, for art that belongs to no enum — a capital
     * ship hull or one of its turrets, named by the data that fits it. Loaded on
     * first ask; a path that will not read is remembered as absent so it is not
     * retried every frame.
     */
    /**
     * Where {@code sprite} came from and its logical size, or null for a sprite this host did not load. A sprite's
     * own width and height are display sizes the last whole-sprite draw set, so code holding only the sprite (an
     * atlas taking in an image it was not told about) asks the host that loaded it instead.
     */
    public ModTextures.Loaded loadedImage(SpriteAPI sprite) {
        return ModTextures.loaded(sprite);
    }

    public ShuttleSpriteCache spriteByPath(String path) {
        if (path == null || path.isEmpty()) return null;
        if (pathSprites.containsKey(path)) return pathSprites.get(path);
        ShuttleSpriteCache cache = null;
        try {
            ModTextures.loadTexture(path, lease);
            SpriteAPI sprite = ModTextures.sprite(path);
            if (sprite != null) {
                float w = sprite.getWidth();
                float h = sprite.getHeight();
                cache = new ShuttleSpriteCache(sprite, h > 0f ? w / h : 1f, (int) w, (int) h);
            }
        } catch (Exception e) {
            LOG.warn("BattleSprites: could not load " + path + " — " + e.getMessage());
        }
        pathSprites.put(path, cache);
        return cache;
    }

    /** Best effort for one airframe: a hull whose sprite will not read is left out. */
    private void loadAirframe(Airframe airframe) {
        String path = airframe.spritePath();
        try {
            ModTextures.loadTexture(path, lease);
            SpriteAPI sprite = ModTextures.sprite(path);
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
     * legacy per-frame bounds; registered types restore authored full slots so
     * transparent margins cannot move their bearings. Failure on a single type leaves it absent from the cache;
     * the convoy render pass silently skips vehicles whose cache entry is null.
     */
    public void ensureConvoySprites() {
        ensureConvoySprites(EnumSet.allOf(VehicleType.class));
    }

    /** {@link #ensureConvoySprites()} for only the lines a battle fields ({@code battle-textures.md}). */
    public void ensureConvoySprites(Set<VehicleType> types) {
        convoySpritesLoadAttempted = true;
        for (VehicleType type : types) loadConvoySprite(type);
    }

    /**
     * {@code path}'s pixels at its logical size, for art read as data rather than drawn (a hull's opaque footprint);
     * null when it cannot be read. A headless host reads it off its own resource roots.
     */
    public BufferedImage readPixels(String path) throws IOException {
        return ModTextures.readImage(path);
    }

    /** Each path's {@link #alphaBounds}, or {@link #NO_BOUNDS} where none could be read. */
    private final Map<String, int[]> alphaBounds = new HashMap<>();
    private static final int[] NO_BOUNDS = new int[0];

    /**
     * The smallest rectangle holding every texel of {@code sprite}'s image that is not fully transparent, in its
     * logical pixels with {@code y} down from the top, as {@code {x0, y0, x1, y1}} (ends exclusive); null for an
     * image this host did not load, one it cannot read, or one with nothing in it.
     *
     * <p>Read from the image's file the first time it is asked, because what asks it plans a texture before any GL
     * runs ({@link UnitAtlas}), and kept for the host's life: a file's art does not move.
     */
    public synchronized int[] alphaBounds(SpriteAPI sprite) {
        ModTextures.Loaded loaded = loadedImage(sprite);
        if (loaded == null || loaded.path() == null) return null;
        int[] bounds = alphaBounds.get(loaded.path());
        if (bounds == null) {
            bounds = NO_BOUNDS;
            try {
                BufferedImage image = readPixels(loaded.path());
                if (image != null) bounds = opaqueBounds(image);
            } catch (IOException | RuntimeException unreadable) {
                LOG.warn("No alpha bounds for " + loaded.path() + ": " + unreadable);
            }
            alphaBounds.put(loaded.path(), bounds);
        }
        return bounds.length == 4 ? bounds : null;
    }

    /** {@link #alphaBounds} of decoded pixels; {@link #NO_BOUNDS} when every texel is transparent. */
    static int[] opaqueBounds(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight();
        int x0 = w, y0 = h, x1 = 0, y1 = 0;
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            image.getRGB(0, y, w, 1, row, 0, w);
            for (int x = 0; x < w; x++) {
                if ((row[x] >>> 24) == 0) continue;
                if (x < x0) x0 = x;
                if (x >= x1) x1 = x + 1;
                if (y < y0) y0 = y;
                y1 = y + 1;
            }
        }
        return x1 > x0 ? new int[]{x0, y0, x1, y1} : NO_BOUNDS;
    }

    /** One vehicle line's sheet, once. */
    public void loadConvoySprite(VehicleType type) {
        if (!vehiclesAttempted.add(type)) return;
        SpriteAPI sheet = loadIconOrNull(type.spritePath);
        if (sheet != null) {
            // Registered sheets are equal horizontal slots: the frames follow from the
            // sheet's logical size alone, so its pixels are never decoded or sliced.
            SpriteSheetFrames size = new SpriteSheetFrames(Math.round(sheet.getWidth()),
                    Math.round(sheet.getHeight()), new SpriteSheetFrames.Frame[0]);
            UnitSpriteCache cache = new UnitSpriteCache(sheet, type.registeredSpriteFrames(size));
            convoySprites.put(type, cache);
            // Where worn plate may be drawn, read off the hull's pixels now rather than on the frame of its first hit.
            VehicleScuffs.masks(this, type, cache.frames);
        } else {
            LOG.warn("convoy: failed to load sprite for " + type
                    + " (" + type.spritePath + ")");
        }
    }

    /**
     * Art a battle's texture set did not name, loaded the first time it is drawn: a hitch, not
     * a blank. The dev build says so, since it means {@link BattleTextureSet} missed a way the
     * battle can bring it on.
     */
    /** What was loaded on first draw because its owner's set did not name it, in order. */
    private final List<String> unplanned = new ArrayList<>();

    /** Everything loaded on first draw because the set did not name it; empty when the set was right. */
    public List<String> unplannedLoads() {
        return List.copyOf(unplanned);
    }

    private void unplanned(String what) {
        unplanned.add(what);
        if (DevConfig.DEV_MODE) {
            LOG.info("battle textures: loading " + what + " on first draw; the battle's texture set missed it");
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
        iconDanger = loadIconOrNull(ICON_DANGER);
        iconStar   = loadIconOrNull(ICON_STAR);
    }

    public SpriteAPI loadIconOrNull(String path) {
        try {
            ModTextures.loadTexture(path, lease);
            SpriteAPI sprite = ModTextures.sprite(path);
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
     * Loads every {@link UnitType} corpse sheet on first call and auto-slices
     * each into per-frame bounding boxes. Live bodies have no sheet: they are
     * drawn from modular art. A corpse sheet is declared only by a type whose
     * fallen no layered downed pose or Rav rig lays down; a sheet that fails to
     * load leaves its bodies undrawn.
     */
    public void ensureUnitSheets() {
        if (unitSpritesLoadAttempted) return;
        unitSpritesLoadAttempted = true;
        for (UnitType type : UnitType.values()) {
            if (!type.carriesSprite()) continue;
            if (type.deadSpritePath != null) {
                UnitSpriteCache dead = loadUnitSheet(type.deadSpritePath);
                if (dead != null) unitDeadSprites.put(type, dead);
            }
        }
    }

    /** Lazy-loads each marine secondary's projectile sprite, a single sprite rotated at draw time, and the primary weapons' projectile sprites. */
    public void ensureMarineSecondarySprites() {
        if (marineSecondarySpritesLoadAttempted) return;
        marineSecondarySpritesLoadAttempted = true;
        SpecialEquipmentRegistry registry = SpecialEquipmentRegistry.installed();
        if (registry == null) return;
        for (SpecialEquipmentDef sec : registry.all()) {
            if (sec.projectileSpritePath() != null) {
                try {
                    ModTextures.loadTexture(sec.projectileSpritePath(), lease);
                    SpriteAPI sprite = ModTextures.sprite(sec.projectileSpritePath());
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
        }
        // Primary projectile sprites (field-rifle / SMG shells today). Skip
        // weapons whose projectile path is null — those share the tinted bolt.
        for (WeaponDef w : WeaponRegistry.installed().all()) {
            if (w.mount != MountClass.MARINE_PRIMARY) continue;
            if (w.projectileSpritePath == null) continue;
            try {
                ModTextures.loadTexture(w.projectileSpritePath, lease);
                SpriteAPI sprite = ModTextures.sprite(w.projectileSpritePath);
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
                ModTextures.loadTexture(projectileSpritePath, lease);
                SpriteAPI sprite = ModTextures.sprite(projectileSpritePath);
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
            ModTextures.loadTexture(grenadePath, lease);
            smokeGrenadeSprite = ModTextures.sprite(grenadePath);
            ModTextures.loadTexture(fieldPath, lease);
            smokeFieldSheet = ModTextures.sprite(fieldPath);
            ModTextures.loadTexture(FIRE_SHEET_PATH, lease);
            fireSheet = ModTextures.sprite(FIRE_SHEET_PATH);
            ModTextures.loadTexture(FireRenderSystem.FLAME_SHEET_PATH, lease);
            flameSheet = ModTextures.sprite(FireRenderSystem.FLAME_SHEET_PATH);
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
            ModTextures.loadTexture(path, lease);
            satchelChargeSprite = ModTextures.sprite(path);
        } catch (Exception e) {
            LOG.error("BattleSprites: failed to load satchel charge sprite", e);
        }
    }

    /**
     * Loads independent body/head/feet/weapon textures for converted infantry
     * archetypes: every family. A family is published only when every required layer
     * loaded; otherwise {@code UnitRenderService} draws its actors as plain markers.
     */
    public void ensureLayeredUnitSprites() {
        ensureLayeredUnitSprites(EnumSet.allOf(LayeredArmorFamily.class), true);
    }

    /**
     * {@link #ensureLayeredUnitSprites()} for only the families a battle draws, and the Rav
     * Bloom's rigs only when they are on its field ({@code battle-textures.md}). A family the
     * set missed loads the first time {@link #layeredUnitSprites()} is asked for it.
     */
    public void ensureLayeredUnitSprites(Set<LayeredArmorFamily> families, boolean ravRigs) {
        ensureInfantryShared();
        for (LayeredArmorFamily family : families) ensureLayeredFamily(family);
        if (ravRigs) ensureRavRigs();
    }

    /**
     * The layers every infantry family shares — feet, weapons, muzzle flash, carried equipment,
     * a medic's kit, crew work props — and the blood pools under a fallen body. Families are
     * loaded after it, since each registers the equipment layers loaded here.
     */
    public void ensureInfantryShared() {
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
        integralSystemLayers.clear();
        MarineArmorCatalogRegistry armorRegistry = MarineArmorCatalogRegistry.installed();
        if (armorRegistry != null) {
            for (MarineArmorCatalogDef armor : armorRegistry.all()) {
                IntegralSystemDef system = armor.integralSystem();
                EquipmentLayerDef layer = system != null ? system.carrierLayer() : null;
                if (layer == null) continue;
                LayeredSpriteCache sprite = loadLayeredSprite(layer.spritePath());
                if (sprite != null) integralSystemLayers.put(system.id(), sprite);
            }
        }
        SpecialEquipmentRegistry equipmentRegistry = SpecialEquipmentRegistry.installed();
        if (equipmentRegistry != null) {
            for (SpecialEquipmentDef def : equipmentRegistry.all()) {
                EquipmentLayerDef layer = def.presentation().carrierLayer();
                if (layer == null) continue;
                LayeredSpriteCache sprite = def.id().equals(SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID)
                        ? rocket : def.id().equals(SpecialEquipmentRegistry.ANTI_MATERIEL_RIFLE_ID)
                                ? amr : loadLayeredSprite(layer.spritePath());
                if (sprite != null) specialEquipmentLayers.put(def.id(), sprite);
                EquipmentLayerDef packed = def.presentation().packedLayer();
                LayeredSpriteCache packedSprite = packed != null
                        ? loadLayeredSprite(packed.spritePath()) : null;
                if (packedSprite != null) {
                    specialEquipmentLayers.put(
                            SpecialEquipmentPresentationDef.packedAssetKey(def.id()), packedSprite);
                }
                HandsetLayer handset = def.presentation().handsetLayer();
                if (handset != null) {
                    LayeredSpriteCache handsetSprite = loadLayeredSprite(handset.spritePath());
                    LayeredSpriteCache cordSprite = loadLayeredSprite(handset.cordSpritePath());
                    if (handsetSprite != null && cordSprite != null) {
                        specialEquipmentLayers.put(HandsetLayer.assetKey(def.id()), handsetSprite);
                        specialEquipmentLayers.put(HandsetLayer.cordAssetKey(def.id()), cordSprite);
                    }
                }
            }
        }
        // Not a special, but drawn the way one is: a medic's kit while treating.
        LayeredSpriteCache treatmentKit = loadLayeredSprite(TreatmentKitLayer.SPRITE);
        if (treatmentKit != null) specialEquipmentLayers.put(TreatmentKitLayer.ASSET_KEY, treatmentKit);
        // Nor is what a crew member holds at work, and it is registered the same way.
        for (Map.Entry<String, String> prop : WorkPropLayer.SPRITES.entrySet()) {
            LayeredSpriteCache sprite = loadLayeredSprite(prop.getValue());
            if (sprite != null) specialEquipmentLayers.put(prop.getKey(), sprite);
        }
        for (int pool = 0; pool < BLOOD_POOLS; pool++) {
            LayeredSpriteCache sprite = loadLayeredSprite(BLOOD_POOL_ROOT + pool + ".png");
            if (sprite != null) bloodPools.add(sprite);
        }
        if (foot == null || rifle == null || laser == null || smg == null || dmr == null
                || rocket == null || amr == null || flash == null || surplusRifle == null
                || masterworkDmr == null) {
            LOG.warn("BattleSprites: modular shared layers incomplete; infantry fall back to plain markers");
            return;
        }
        infantryShared = new InfantryShared(foot, rifle, laser, smg, dmr, rocket, amr, flash,
                surplusRifle, masterworkDmr);
    }

    /** The layers {@link #ensureInfantryShared()} loaded, or null when one of them would not. */
    private InfantryShared infantryShared;
    /** Families already tried, so one that will not load is not retried on every draw. */
    private final EnumSet<LayeredArmorFamily> familiesAttempted = EnumSet.noneOf(LayeredArmorFamily.class);
    private boolean ravRigsAttempted;

    private record InfantryShared(LayeredSpriteCache foot, LayeredSpriteCache rifle,
                                  LayeredSpriteCache laser, LayeredSpriteCache smg,
                                  LayeredSpriteCache dmr, LayeredSpriteCache rocket,
                                  LayeredSpriteCache amr, LayeredSpriteCache flash,
                                  LayeredSpriteCache surplusRifle, LayeredSpriteCache masterworkDmr) { }

    /** Where a family's art lives: its folder, and its own feet and fore claws where it has them. */
    private record FamilyArt(String root, String footPath, String clawPath) { }

    private static final String ALIEN_ROOT = "graphics/battle/alien-modular-topdown/";
    private static final String SWARM_BRUTE_ROOT = "graphics/battle/swarm-brute-modular-topdown/";

    private static FamilyArt familyArt(LayeredArmorFamily family) {
        return switch (family) {
            case ARMORLESS -> marine("armorless", false);
            case CHARCOAL -> marine("charcoal", false);
            case BLUE_SCOUT -> marine("blue-scout", false);
            case RED_ELITE -> marine("red-heavy", false);
            case OUTLAW -> marine("outlaw", false);
            case ARMY_GREEN -> marine("army-green", false);
            case MILITIA -> marine("militia", false);
            case AEGIS_COMPOSITE -> marine("aegis", false);
            case PALATINE -> marine("palatine", false);
            case FURNACE_LINE -> marine("furnace-line", false);
            case REAVER -> marine("reaver", false);
            case SPECTER_HEAVY -> marine("specter-heavy", true);
            case ARBALEST_HEAVY -> marine("specter-heavy/paints/olive", true);
            case BOARDING_JACK -> marine("boarding-jack", true);
            case BOARDING_JACK_PIRATE -> marine("boarding-jack/paints/pirate", true);
            case BULWARK_HEAVY -> marine("bulwark-heavy", false);
            case RELIQUARY_HEAVY -> marine("reliquary-heavy", false);
            case LIONS_MANTLE -> marine("lions-mantle", false);
            case FOUNDRY_BREAKER -> marine("foundry-breaker", false);
            case CIVILIAN_COLONIST -> own("graphics/battle/colonist-modular-topdown/civilian/");
            case ENGINEER -> own("graphics/battle/colonist-modular-topdown/engineer/");
            case SCIENTIST -> own("graphics/battle/colonist-modular-topdown/scientist/");
            case SHIP_COMMAND -> own("graphics/battle/ship-crew-modular-topdown/command/");
            case SHIP_ENGINEERING -> own("graphics/battle/ship-crew-modular-topdown/engineering/");
            case SHIP_MEDICAL -> own("graphics/battle/ship-crew-modular-topdown/medical/");
            case SHIP_SERVICE -> own("graphics/battle/ship-crew-modular-topdown/service/");
            // The Brute walks and strikes on its own heavier foot and claw; the Spitter is the
            // Runner's lineage below the shoulders and keeps its feet and claws.
            case XENO -> new FamilyArt(ALIEN_ROOT, ALIEN_ROOT + "foot.png", ALIEN_ROOT + "fore-claw.png");
            case XENO_BRUTE -> new FamilyArt(SWARM_BRUTE_ROOT, SWARM_BRUTE_ROOT + "foot.png",
                    SWARM_BRUTE_ROOT + "fore-claw.png");
            case XENO_SPITTER -> new FamilyArt("graphics/battle/swarm-spitter-modular-topdown/",
                    ALIEN_ROOT + "foot.png", ALIEN_ROOT + "fore-claw.png");
        };
    }

    /** A marine armour under the variants folder, on the shared foot or on its own. */
    private static FamilyArt marine(String folder, boolean ownFoot) {
        String root = MODULAR_ROOT + "armor/" + folder + "/";
        return new FamilyArt(root, ownFoot ? root + "foot.png" : null, null);
    }

    /** A family in its own folder with its own feet. */
    private static FamilyArt own(String root) {
        return new FamilyArt(root, root + "foot.png", null);
    }

    /** Loads one family once; the shared layers first if nobody has yet. */
    public void ensureLayeredFamily(LayeredArmorFamily family) {
        ensureInfantryShared();
        if (family == null || !familiesAttempted.add(family)) return;
        InfantryShared shared = infantryShared;
        if (shared == null) return;
        FamilyArt art = familyArt(family);
        LayeredSpriteCache foot = art.footPath() == null ? shared.foot() : loadLayeredSprite(art.footPath());
        LayeredSpriteCache claw = art.clawPath() == null ? null : loadLayeredSprite(art.clawPath());
        loadLayeredFamilyAt(family, art.root(), foot, claw, shared.rifle(), shared.laser(), shared.smg(),
                shared.dmr(), shared.rocket(), shared.amr(), shared.flash(), shared.surplusRifle(),
                shared.masterworkDmr());
    }

    /** The Rav Bloom's Runner, Brute, Spitter and Matriarch rigs. */
    public void ensureRavRigs() {
        if (ravRigsAttempted) return;
        ravRigsAttempted = true;
        ravRunner = RavRunnerAssets.load(this::loadLayeredSprite);
        if (ravRunner == null) {
            LOG.warn("BattleSprites: Rav Runner bones incomplete; the Runner keeps its layered art");
        }
        ravBrute = RavBruteAssets.load(this::loadLayeredSprite);
        if (ravBrute == null) {
            LOG.warn("BattleSprites: Rav Brute rig sprites incomplete; the Brute keeps its layered art");
        }
        ravSpitter = RavSpitterAssets.load(this::loadLayeredSprite);
        if (ravSpitter == null) {
            LOG.warn("BattleSprites: Rav Spitter rig sprites incomplete; the Spitter keeps its layered art");
        }
        ravBombard = RavBombardAssets.load(this::loadLayeredSprite);
        if (ravBombard == null) {
            LOG.warn("BattleSprites: Rav Bombard rig sprites incomplete; the Bombard keeps its stand-in art");
        }
        ravMatriarch = RavMatriarchAssets.load(this::loadLayeredSprite);
        if (ravMatriarch == null) {
            LOG.warn("BattleSprites: Rav Matriarch rig sprites incomplete");
        }
    }

    /** Loads the all-or-nothing modular heavy-mech set; without it mechs draw as plain markers. */
    public void ensureLayeredMechSprites() {
        ensureLayeredMechSprites(EnumSet.allOf(MechLivery.class));
    }

    /**
     * The base mech set and only the {@code liveries} named, which for a battle are the ones
     * its sides wear ({@code battle-textures.md}); another loads when a side asks for it.
     */
    public void ensureLayeredMechSprites(Set<MechLivery> liveries) {
        ensureMechBase();
        for (MechLivery livery : liveries) ensureMechLivery(livery);
    }

    /** One painted livery, once; the base set first. A livery that will not load falls back to the base. */
    public void ensureMechLivery(MechLivery livery) {
        ensureMechBase();
        if (livery == null || livery == MechLivery.BASE || mechBase == null || !liveriesAttempted.add(livery)) return;
        loadMechLivery(MECH_ROOT, livery, mechBase.socketedChassis(), mechBase.foot(), mechBase.thighBone(),
                mechBase.flash());
    }

    private static final String MECH_ROOT = "graphics/battle/mech-modular-topdown/";
    /** The unpainted layers every livery shares, or null when the base set would not load. */
    private MechBase mechBase;
    private final EnumSet<MechLivery> liveriesAttempted = EnumSet.noneOf(MechLivery.class);

    private record MechBase(LayeredSpriteCache socketedChassis, LayeredSpriteCache foot,
                            LayeredSpriteCache thighBone, LayeredSpriteCache flash) { }

    private void ensureMechBase() {
        if (layeredMechSpritesLoadAttempted) return;
        layeredMechSpritesLoadAttempted = true;
        String root = MECH_ROOT;
        LayeredSpriteCache chassis = loadLayeredSprite(root + "chassis.png");
        LayeredSpriteCache socketedChassis = loadLayeredSprite(root + "chassis-socketed-variant.png");
        LayeredSpriteCache houndChassis = loadLayeredSprite(root + "chassis-hound.png");
        LayeredSpriteCache wardenChassis = loadLayeredSprite(root + "chassis-warden.png");
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
        LayeredSpriteCache lightMachineGun = loadLayeredSprite(root + "light-machine-gun.png");
        LayeredSpriteCache lightPulseLaser = loadLayeredSprite(root + "light-pulse-laser.png");
        LayeredSpriteCache lightRocketPod = loadLayeredSprite(root + "light-rocket-pod.png");
        LayeredSpriteCache burstLaser = loadLayeredSprite(root + "burst-laser.png");
        LayeredSpriteCache pdLaser = loadLayeredSprite(root + "pd-laser.png");
        LayeredSpriteCache heavyPdLaser = loadLayeredSprite(root + "heavy-pd-laser.png");
        LayeredSpriteCache mediumBurstLaser = loadLayeredSprite(root + "medium-burst-laser.png");
        LayeredSpriteCache lightBurstLaser = loadLayeredSprite(root + "light-burst-laser.png");
        LayeredSpriteCache flash = loadLayeredSprite(
                "graphics/battle/marine-modular-topdown/marine-muzzle-flash.png");
        if (chassis == null || socketedChassis == null || houndChassis == null
                || wardenChassis == null || foot == null || thighBone == null || arm == null
                || linearCannon == null || heavyCannon == null
                || srm == null || lrm == null || shoulderLaser == null
                || pulseLaserArm == null || bastionAutocannon == null
                || demolitionCannon == null || thermalLance == null
                || musterAutogun == null || quarryBreakerCannon == null
                || pioneerRocketCradle == null || lightMachineGun == null
                || lightPulseLaser == null || lightRocketPod == null || burstLaser == null
                || pdLaser == null || heavyPdLaser == null || mediumBurstLaser == null || lightBurstLaser == null
                || flash == null) {
            LOG.warn("BattleSprites: modular mech layers incomplete; mechs fall back to plain markers");
            return;
        }
        layeredMechSprites = new LayeredMechAssets(chassis, socketedChassis,
                houndChassis, wardenChassis, foot, thighBone, arm, linearCannon, heavyCannon,
                srm, lrm, shoulderLaser, pulseLaserArm, bastionAutocannon,
                demolitionCannon, thermalLance, musterAutogun,
                quarryBreakerCannon, pioneerRocketCradle, lightMachineGun, lightPulseLaser,
                lightRocketPod, burstLaser, pdLaser, heavyPdLaser,
                mediumBurstLaser, lightBurstLaser, flash);
        layeredMechLiveries.put(MechLivery.BASE, layeredMechSprites);
        for (TurretMountStyle style : TurretMountStyle.values()) {
            LayeredSpriteCache ring = loadLayeredSprite("graphics/battle/vehicles/" + style.ringTexture);
            if (ring != null) vehicleGunMounts.put(style, ring);
        }
        vehicleTurretCollar = loadLayeredSprite("graphics/battle/vehicles/turret-collar.png");
        vehicleTurretBase = loadLayeredSprite("graphics/battle/vehicles/turret-base-medium.png");
        vehicleTurretBaseLight = loadLayeredSprite("graphics/battle/vehicles/turret-base-light.png");
        vehicleGunPlate = loadLayeredSprite("graphics/battle/vehicles/gun-plate.png");
        vehicleKitActiveProtection = loadLayeredSprite("graphics/battle/vehicles/kit-active-protection.png");
        vehicleKitActiveProtectionTurret = loadLayeredSprite("graphics/battle/vehicles/kit-active-protection-turret.png");
        for (MechWeaponComponent weapon : MechWeaponComponent.values()) {
            String path = VehicleGunArt.vehicleDrawingPath(weapon);
            LayeredSpriteCache drawing = path == null ? null : loadLayeredSprite(path);
            if (drawing != null) vehicleGunDrawings.put(weapon, drawing);
        }
        mechBase = new MechBase(socketedChassis, foot, thighBone, flash);
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
        LayeredSpriteCache wardenChassis = loadLayeredSprite(root + "chassis-warden.png");
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
        LayeredSpriteCache lightMachineGun = loadLayeredSprite(root + "light-machine-gun.png");
        LayeredSpriteCache lightPulseLaser = loadLayeredSprite(root + "light-pulse-laser.png");
        LayeredSpriteCache lightRocketPod = loadLayeredSprite(root + "light-rocket-pod.png");
        LayeredSpriteCache burstLaser = loadLayeredSprite(root + "burst-laser.png");
        LayeredSpriteCache pdLaser = loadLayeredSprite(root + "pd-laser.png");
        LayeredSpriteCache heavyPdLaser = loadLayeredSprite(root + "heavy-pd-laser.png");
        LayeredSpriteCache mediumBurstLaser = loadLayeredSprite(root + "medium-burst-laser.png");
        LayeredSpriteCache lightBurstLaser = loadLayeredSprite(root + "light-burst-laser.png");
        if (chassis == null || houndChassis == null || wardenChassis == null
                || arm == null || linearCannon == null || heavyCannon == null
                || srm == null || lrm == null || shoulderLaser == null
                || pulseLaserArm == null || bastionAutocannon == null
                || demolitionCannon == null || thermalLance == null
                || musterAutogun == null || quarryBreakerCannon == null
                || pioneerRocketCradle == null || lightMachineGun == null
                || lightPulseLaser == null || lightRocketPod == null || burstLaser == null
                || pdLaser == null || heavyPdLaser == null || mediumBurstLaser == null || lightBurstLaser == null) {
            LOG.warn("BattleSprites: modular mech livery " + livery
                    + " incomplete; actors using it keep the complete base set");
            return;
        }
        layeredMechLiveries.put(livery, new LayeredMechAssets(
                chassis, socketedChassis, houndChassis, wardenChassis,
                foot, thighBone, arm, linearCannon, heavyCannon,
                srm, lrm, shoulderLaser, pulseLaserArm, bastionAutocannon,
                demolitionCannon, thermalLance, musterAutogun,
                quarryBreakerCannon, pioneerRocketCradle, lightMachineGun, lightPulseLaser,
                lightRocketPod, burstLaser, pdLaser, heavyPdLaser,
                mediumBurstLaser, lightBurstLaser, flash));
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
                || (familyId.isXeno() && foreClaw == null)) {
            LOG.warn("BattleSprites: modular family " + familyId
                    + " incomplete; actors using it draw as plain markers");
            return;
        }
        LayeredUnitAssets assets = new LayeredUnitAssets(body, head, foot, foreClaw,
                rifle, laser, smg, dmr, rocket, amr, flash, surplusRifle, masterworkDmr,
                familyId.articulatedShoulders()
                        ? loadLayeredSprite(familyRoot + "shoulder-left.png") : null,
                familyId.articulatedShoulders()
                        ? loadLayeredSprite(familyRoot + "shoulder-right.png") : null);
        for (Map.Entry<String, LayeredSpriteCache> entry : specialEquipmentLayers.entrySet()) {
            assets.registerSpecialEquipment(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, LayeredSpriteCache> entry : integralSystemLayers.entrySet()) {
            assets.registerIntegralSystem(entry.getKey(), entry.getValue());
        }
        if (familyId.hasDownedArt()) {
            for (int pose = 0; pose < LayeredUnitAssets.DOWNED_POSES; pose++) {
                assets.registerDowned(loadLayeredSprite(familyRoot + "downed-" + pose + ".png"));
            }
        }
        if (familyId.hasStationArms()) {
            assets.registerStationArms(loadLayeredSprite(familyRoot + "station-arm-left.png"),
                    loadLayeredSprite(familyRoot + "station-arm-right.png"));
        }
        layeredUnitSprites.put(familyId, assets);
    }

    /** Whole transparent PNG loader that captures image pixels before SpriteAPI mutation. */
    public LayeredSpriteCache loadLayeredSprite(String path) {
        try {
            ModTextures.loadTexture(path, lease);
            SpriteAPI sprite = ModTextures.sprite(path);
            if (sprite == null) {
                LOG.warn("BattleSprites: getSprite returned null for modular layer " + path);
                return null;
            }
            // Its size from the header: decoding every layer a second time on the frame that
            // uploads it was most of what loading the infantry cost.
            int[] size = ModTextures.readSize(path);
            if (size == null) {
                LOG.warn("BattleSprites: ImageIO could not read the size of modular layer " + path);
                return null;
            }
            LOG.debug("BattleSprites: loaded modular layer " + path + " (" + size[0] + "x" + size[1] + ")");
            return new LayeredSpriteCache(sprite, path, size[0], size[1]);
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
            ModTextures.loadTexture(path, lease);
            SpriteAPI sprite = ModTextures.sprite(path);
            if (sprite == null) {
                LOG.warn("BattleSprites: getSprite returned null for " + path);
                return;
            }
            float w = sprite.getWidth();
            float h = sprite.getHeight();
            float aspect = (h > 0f) ? w / h : 1f;
            droneHubSprite = new ShuttleSpriteCache(sprite, aspect, Math.round(w), Math.round(h));
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
            ModTextures.loadTexture(path, lease);
            SpriteAPI sprite = ModTextures.sprite(path);
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
            ModTextures.loadTexture(path, lease);
            SpriteAPI sprite = ModTextures.sprite(path);
            if (sprite == null) {
                LOG.warn("BattleSprites: getSprite returned null for " + path);
                return null;
            }
            float w = sprite.getWidth();
            float h = sprite.getHeight();
            float aspect = (h > 0f) ? w / h : 1f;
            LOG.info("BattleSprites: loaded " + path + " (" + w + "x" + h + ", aspect=" + aspect + ")");
            return new ShuttleSpriteCache(sprite, aspect, Math.round(w), Math.round(h));
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
            ModTextures.loadTexture(path, lease);
            SpriteAPI sprite = ModTextures.sprite(path);
            if (sprite == null) {
                LOG.warn("BattleSprites: getSprite returned null for " + path);
                return null;
            }
            {
                BufferedImage img = ModTextures.readImage(path);
                if (img == null) {
                    LOG.warn("BattleSprites: ImageIO.read returned null for " + path);
                    return null;
                }
                SpriteSheetFrames frames = SpriteSheetSlicer.slice(img);
                TextureUsage.sliced(path);
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
            ModTextures.loadTexture(SPRITE_DECAL_SHEET, lease);
            decalSheet = ModTextures.sprite(SPRITE_DECAL_SHEET);
            if (decalSheet == null) {
                LOG.warn("BattleSprites: getSprite returned null for " + SPRITE_DECAL_SHEET);
                return;
            }
            {
                BufferedImage img = ModTextures.readImage(SPRITE_DECAL_SHEET);
                if (img == null) {
                    LOG.warn("BattleSprites: ImageIO.read returned null for " + SPRITE_DECAL_SHEET);
                    decalSheet = null;
                    return;
                }
                decalFrames = SpriteSheetSlicer.slice(img);
                TextureUsage.sliced(SPRITE_DECAL_SHEET);
                LOG.info("BattleSprites: auto-sliced " + SPRITE_DECAL_SHEET
                        + " — " + decalFrames.frames.length + " frames");
            }
        } catch (Exception e) {
            LOG.error("BattleSprites: failed to load decal sheet " + SPRITE_DECAL_SHEET, e);
            decalSheet = null;
        }
    }
}
