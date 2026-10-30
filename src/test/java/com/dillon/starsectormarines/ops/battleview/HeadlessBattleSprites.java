package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.drone.Drone;
import com.dillon.starsectormarines.battle.vehicle.TurretMountStyle;
import com.dillon.starsectormarines.render2d.texture.ModTextures;
import com.dillon.starsectormarines.battle.air.Airframe;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetFrames;
import com.dillon.starsectormarines.battle.world.tiles.GridBlockDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetSlicer;
import com.dillon.starsectormarines.marine.EquipmentLayerDef;
import com.dillon.starsectormarines.marine.IntegralSystemDef;
import com.dillon.starsectormarines.marine.MarineArmorCatalogDef;
import com.dillon.starsectormarines.marine.MarineArmorCatalogRegistry;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentPresentationDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentPresentationDef.HandsetLayer;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.render2d.texture.FittedTextureOverlay;
import com.dillon.starsectormarines.render2d.texture.TextureUsage;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.json.JSONObject;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Disk-backed sprite catalog that presents harmless {@link SpriteAPI} identity
 * tokens to collectors.
 *
 * <p>A token is an identity and a size, not a texture: the ordinary headless
 * drain paints through Java2D off the recorded {@link Asset} path, so nothing
 * here needs a graphics context. A caller that <em>does</em> have one — the
 * render-budget evidence, which drains through a real driver — supplies its own
 * {@link SpriteTokens} instead, and the loading, slicing and family assembly
 * below are shared rather than written twice.
 */
final class HeadlessBattleSprites extends BattleSprites {

    /** What a loaded image is to the drain: where it came from and how big it is. */
    record Asset(String path, int width, int height) { }

    /**
     * Makes the {@link SpriteAPI} token that stands for one loaded image.
     *
     * <p>The seam between a sprite catalog and a graphics backend. The default
     * token can answer its own size and nothing else, which is all a Java2D
     * drain reads off it; a GL drain needs one that can bind a texture and draw
     * itself, and gets it by handing in its own factory rather than by loading
     * every sheet a second time.
     */
    interface SpriteTokens {
        /**
         * @param path the resource path, as the game would name it
         * @param file where that path actually resolved on disk
         * @param width the image's logical width in pixels
         * @param height its logical height
         */
        SpriteAPI token(String path, Path file, int width, int height);
    }

    private static final String INFANTRY_ROOT =
            "graphics/battle/marine-modular-topdown/variants/";
    private static final String MECH_ROOT =
            "graphics/battle/mech-modular-topdown/";
    /** Same path the base class loads through the game; see {@link #decalSheet()}. */
    private static final String DECAL_SHEET = "graphics/decals/decals.png";

    private final SpriteTokens tokens;
    private final Path modRoot;
    /**
     * The game's own resource root, or null when this machine has not said
     * where the install is.
     *
     * <p>An asset is looked for under the mod first and here second, which
     * is the load order the game itself uses and the reason a mod can
     * override a vanilla file by shipping one at the same path.
     */
    private final Path vanillaRoot;
    private final IdentityHashMap<SpriteAPI, Asset> assets = new IdentityHashMap<>();
    private final EnumMap<LayeredArmorFamily, LayeredUnitAssets> infantry =
            new EnumMap<>(LayeredArmorFamily.class);
    private final Map<Airframe, ShuttleSpriteCache> airframes =
            new LinkedHashMap<>();
    private final EnumMap<VehicleType, UnitSpriteCache> vehicles = new EnumMap<>(VehicleType.class);
    private final Map<String, ShuttleSpriteCache> projectiles = new LinkedHashMap<>();
    private final Map<String, ShuttleSpriteCache> turretBases = new LinkedHashMap<>();
    private final Map<String, ShuttleSpriteCache> turretBarrels = new LinkedHashMap<>();
    private final Map<String, SpriteAPI> extraTerrainSheets = new LinkedHashMap<>();
    private final UnitLayerLayouts layouts;

    private final SpriteAPI tile;
    private final SpriteAPI road;
    private final SpriteAPI floors;
    private final SpriteAPI roofs;
    private final SpriteAPI water;
    private final SpriteAPI urban3;
    private final SpriteAPI nature;
    private final SpriteAPI doodads;
    private final SpriteAPI parkedVehicles;
    private final SpriteSheetFrames urban3Frames;
    private final SpriteSheetFrames natureFrames;
    private final LayeredMechAssets mech;
    private final EnumMap<TurretMountStyle, LayeredSpriteCache> gunMounts =
            new EnumMap<>(TurretMountStyle.class);
    private final LayeredSpriteCache turretCollar;
    private final LayeredSpriteCache turretBase;
    private final LayeredSpriteCache turretBaseLight;
    private final LayeredSpriteCache gunPlate;
    private final LayeredSpriteCache kitActiveProtection;
    private final LayeredSpriteCache kitActiveProtectionTurret;
    private final EnumMap<MechWeaponComponent, LayeredSpriteCache> gunDrawings = new EnumMap<>(MechWeaponComponent.class);
    private SpriteAPI decals;
    private SpriteSheetFrames decalFrames;
    private SpriteAPI warshipEngineFlame;
    private SpriteAPI warshipEngineGlow;
    private SpriteAPI engineFlame;
    private SpriteAPI engineGlow;
    private SpriteAPI shadowBlob;
    private int shadowBlobPxW;
    private int shadowBlobPxH;

    /** Tokens that answer their own size and draw nothing — the Java2D drain's. */
    HeadlessBattleSprites(Path modRoot) throws Exception {
        this(modRoot, HeadlessBattleSprites::identityToken);
    }

    HeadlessBattleSprites(Path modRoot, SpriteTokens tokens) throws Exception {
        this.tokens = tokens;
        this.modRoot = modRoot.toAbsolutePath().normalize();
        this.vanillaRoot = HeadlessBattleSceneRenderer.installedGameResources();
        layouts = UnitLayerLayouts.parse(new JSONObject(Files.readString(
                this.modRoot.resolve(UnitLayerLayouts.CONTENT_PATH))));
        tile = sprite(TileManifest.SHEET);
        road = sprite(TileManifest.ROAD_SHEET);
        floors = sprite(TileManifest.FLOORS_SHEET);
        roofs = sprite(RoofTiles.SHEET);
        water = sprite(TileManifest.WATER_SHEET);
        urban3 = sprite(TileManifest.STREET3_SHEET);
        nature = sprite(TileManifest.NATURE_SHEET);
        doodads = sprite(TileManifest.DOODAD_SHEET);
        parkedVehicles = sprite(TileManifest.PARKED_VEHICLE_SHEET);
        for (GridBlockDef block : TileRegistry.installed().blocks()) {
            String path = block.sheetPath;
            if (path.equals(TileManifest.SHEET) || path.equals(TileManifest.ROAD_SHEET)
                    || path.equals(TileManifest.FLOORS_SHEET) || path.equals(TileManifest.WATER_SHEET)) continue;
            if (!extraTerrainSheets.containsKey(path)) extraTerrainSheets.put(path, sprite(path));
        }
        for (var doodad : TileRegistry.installed().doodads()) {
            String path = doodad.sheetPath;
            if (path.equals(TileManifest.SHEET) || path.equals(TileManifest.ROAD_SHEET)
                    || path.equals(TileManifest.DOODAD_SHEET) || path.equals(TileManifest.PARKED_VEHICLE_SHEET)) continue;
            if (!extraTerrainSheets.containsKey(path)) extraTerrainSheets.put(path, sprite(path));
        }
        urban3Frames = slice(TileManifest.STREET3_SHEET);
        natureFrames = slice(TileManifest.NATURE_SHEET);
        loadInfantry();
        loadHulls();
        for (VehicleType type : VehicleType.values()) {
            // As the game loads them: equal slots from the sheet's size, no slicing.
            SpriteAPI sheet = sprite(type.spritePath);
            Asset size = assets.get(sheet);
            vehicles.put(type, new UnitSpriteCache(sheet, type.registeredSpriteFrames(new SpriteSheetFrames(
                    size.width(), size.height(), new SpriteSheetFrames.Frame[0]))));
        }
        for (var structure : TurretCatalogRegistry.installed().structures()) {
            loadWholeSprite(turretBases, structure.id, structure.mount.spritePath);
            loadWholeSprite(turretBarrels, structure.id, structure.mount.recoilSpritePath);
        }
        loadEngineFx();
        ensureObjectiveIcons();
        loadDecals();
        mech = loadMech();
        for (TurretMountStyle style : TurretMountStyle.values()) {
            LayeredSpriteCache ring = optionalLayer("graphics/battle/vehicles/" + style.ringTexture);
            if (ring != null) gunMounts.put(style, ring);
        }
        turretCollar = optionalLayer("graphics/battle/vehicles/turret-collar.png");
        turretBase = optionalLayer("graphics/battle/vehicles/turret-base-medium.png");
        turretBaseLight = optionalLayer("graphics/battle/vehicles/turret-base-light.png");
        gunPlate = optionalLayer("graphics/battle/vehicles/gun-plate.png");
        kitActiveProtection = optionalLayer("graphics/battle/vehicles/kit-active-protection.png");
        kitActiveProtectionTurret = optionalLayer("graphics/battle/vehicles/kit-active-protection-turret.png");
        for (MechWeaponComponent weapon : MechWeaponComponent.values()) {
            String path = VehicleGunArt.vehicleDrawingPath(weapon);
            LayeredSpriteCache drawing = path == null ? null : optionalLayer(path);
            if (drawing != null) gunDrawings.put(weapon, drawing);
        }
    }

    /**
     * Loads the aircraft hulls.
     *
     * <p>These are vanilla ship sprites, so this is the one sprite family
     * the mod folder does not hold and the reason the vanilla root exists
     * at all. Without them a headless frame of an airfield showed bare
     * concrete where an aircraft was standing, and the wreck of one was
     * invisible — which made the whole of the air arm unphotographable in
     * evidence that already renders everything around it.
     *
     * <p>Best effort per hull: a type whose sprite cannot be read is left
     * out rather than failing the render, because a missing aircraft is
     * worth far less than the frame it appears in.
     */
    void loadHulls() {
        for (ShuttleType type : ShuttleType.values()) loadHull(type);
        for (FighterProfile fighter : FighterProfile.values()) loadHull(fighter);
    }

    /**
     * Loads the vanilla effect sprites.
     *
     * <p>Same reason as the hulls, and reached the same way: these live in
     * the install rather than the mod folder, and the vanilla root is
     * already in {@link #resourceRoots}. The base class loads them through
     * {@code Global.getSettings().loadTexture}, which is a stub here and
     * hands back null, so every consumer of an effect sprite silently drew
     * nothing in headless evidence — an engine plume, and any later system
     * that borrows a radial falloff for something of its own.
     *
     * <p>Best effort per sprite, as with a hull: a missing effect is worth
     * far less than the frame it would have appeared in.
     */
    /** Best effort, as with the hulls: a missing decal sheet leaves the pass a no-op. */
    private void loadDecals() {
        try {
            decals = sprite(DECAL_SHEET);
            decalFrames = slice(DECAL_SHEET);
        } catch (IOException | RuntimeException missing) {
            decals = null;
            decalFrames = null;
        }
    }

    private void loadEngineFx() {
        warshipEngineFlame = spriteOrNull(WARSHIP_ENGINE_FLAME_SPRITE);
        warshipEngineGlow = spriteOrNull(WARSHIP_ENGINE_GLOW_SPRITE);
        engineFlame = spriteOrNull(ENGINE_FLAME_SPRITE);
        engineGlow = spriteOrNull(ENGINE_GLOW_SPRITE);
        shadowBlob = spriteOrNull(SHADOW_BLOB_SPRITE);
        shadowBlobPxW = shadowBlob == null ? 0 : Math.round(shadowBlob.getWidth());
        shadowBlobPxH = shadowBlob == null ? 0 : Math.round(shadowBlob.getHeight());
    }

    private SpriteAPI spriteOrNull(String path) {
        try {
            return sprite(path);
        } catch (IOException | RuntimeException missing) {
            return null;
        }
    }

    /**
     * Marker icons off the resource roots. The base class loads them through {@code Global.getSettings()}, a
     * stub here, so a headless frame drew every danger, alarm and star marker as its fallback solid square.
     */
    @Override public SpriteAPI loadIconOrNull(String path) {
        return spriteOrNull(path);
    }

    private void loadWholeSprite(Map<String, ShuttleSpriteCache> target, String id, String path) {
        if (path == null) return;
        SpriteAPI token = spriteOrNull(path);
        if (token == null) return;
        Asset asset = asset(token);
        target.put(id, new ShuttleSpriteCache(token, asset.width() / (float) asset.height(),
                asset.width(), asset.height()));
    }

    @Override public Map<String, ShuttleSpriteCache> turretSprites() { return turretBases; }
    @Override public Map<String, ShuttleSpriteCache> turretRecoilSprites() { return turretBarrels; }

    private void loadHull(Airframe airframe) {
        if (airframes.containsKey(airframe)) return;
        try {
            SpriteAPI token = sprite(airframe.spritePath());
            Asset asset = asset(token);
            airframes.put(airframe, new ShuttleSpriteCache(token,
                    asset.height() == 0 ? 1f : asset.width() / (float) asset.height(),
                    asset.width(), asset.height()));
        } catch (IOException | RuntimeException missing) {
            // Left out; see the method note.
        }
    }

    private final Map<String, ShuttleSpriteCache> byPath = new java.util.HashMap<>();

    /** A sprite by path off the same disk roots as every other headless sprite; a path that will not read is absent. */
    @Override public ShuttleSpriteCache spriteByPath(String path) {
        if (path == null || path.isEmpty()) return null;
        if (byPath.containsKey(path)) return byPath.get(path);
        ShuttleSpriteCache cache = null;
        try {
            SpriteAPI token = sprite(path);
            Asset asset = asset(token);
            cache = new ShuttleSpriteCache(token,
                    asset.height() == 0 ? 1f : asset.width() / (float) asset.height(),
                    asset.width(), asset.height());
        } catch (IOException | RuntimeException missing) {
            // Left out, as with a hull.
        }
        byPath.put(path, cache);
        return cache;
    }

    @Override public SpriteAPI tileSheet() { return tile; }
    @Override public int tileSheetPxW() { return asset(tile).width(); }
    @Override public int tileSheetPxH() { return asset(tile).height(); }
    @Override public SpriteAPI roadSheet() { return road; }
    @Override public int roadSheetPxW() { return asset(road).width(); }
    @Override public int roadSheetPxH() { return asset(road).height(); }
    @Override public SpriteAPI floorsSheet() { return floors; }
    @Override public int floorsSheetPxW() { return asset(floors).width(); }
    @Override public int floorsSheetPxH() { return asset(floors).height(); }
    @Override public SpriteAPI roofSheet() { return roofs; }
    @Override public int roofSheetPxW() { return asset(roofs).width(); }
    @Override public int roofSheetPxH() { return asset(roofs).height(); }
    @Override public SpriteAPI waterSheet() { return water; }
    @Override public int waterSheetPxW() { return asset(water).width(); }
    @Override public int waterSheetPxH() { return asset(water).height(); }
    @Override public SpriteAPI urbanTile3Sheet() { return urban3; }
    @Override public int urbanTile3SheetPxW() { return asset(urban3).width(); }
    @Override public int urbanTile3SheetPxH() { return asset(urban3).height(); }
    @Override public SpriteSheetFrames urbanTile3Frames() { return urban3Frames; }
    @Override public SpriteAPI natureSheet() { return nature; }
    @Override public int natureSheetPxW() { return asset(nature).width(); }
    @Override public int natureSheetPxH() { return asset(nature).height(); }
    @Override public SpriteSheetFrames natureFrames() { return natureFrames; }
    @Override public SpriteAPI doodadSheet() { return doodads; }
    @Override public int doodadSheetPxW() { return asset(doodads).width(); }
    @Override public int doodadSheetPxH() { return asset(doodads).height(); }
    @Override public SpriteAPI parkedVehicleSheet() { return parkedVehicles; }
    @Override public int parkedVehicleSheetPxW() { return asset(parkedVehicles).width(); }
    @Override public int parkedVehicleSheetPxH() { return asset(parkedVehicles).height(); }
    @Override public EnumMap<LayeredArmorFamily, LayeredUnitAssets> layeredUnitSprites() {
        return infantry;
    }
    @Override public RavRunnerAssets ravRunnerSprites() { return ravRunner; }
    @Override public RavBruteAssets ravBruteSprites() { return ravBrute; }
    @Override public RavMatriarchAssets ravMatriarchSprites() { return ravMatriarch; }
    @Override public RavSpitterAssets ravSpitterSprites() { return ravSpitter; }
    @Override public RavBombardAssets ravBombardSprites() { return ravBombard; }
    @Override public LayeredMechAssets layeredMechSprites() { return mech; }
    @Override public LayeredSpriteCache vehicleGunMount(TurretMountStyle style) { return gunMounts.get(style); }
    @Override public LayeredSpriteCache vehicleTurretCollar() { return turretCollar; }
    @Override public LayeredSpriteCache vehicleTurretBase(boolean light) { return light ? turretBaseLight : turretBase; }
    @Override public LayeredSpriteCache vehicleGunPlate() { return gunPlate; }
    @Override public LayeredSpriteCache vehicleKitActiveProtection() { return kitActiveProtection; }
    @Override public LayeredSpriteCache vehicleKitActiveProtectionTurret() { return kitActiveProtectionTurret; }
    @Override public LayeredSpriteCache vehicleGunDrawing(MechWeaponComponent weapon) { return gunDrawings.get(weapon); }
    @Override public EnumMap<VehicleType, UnitSpriteCache> convoySprites() { return vehicles; }
    @Override public Map<Airframe, ShuttleSpriteCache> airframeSprites() { return airframes; }
    /** Off the resource roots: the base class reads through {@code Global.getSettings().openStream}, a stub here. */
    @Override public BufferedImage readPixels(String path) throws IOException { return read(path); }
    /**
     * The decal sheet, off the resource roots.
     *
     * <p>Loaded here because the base class reaches it through
     * {@code Global.getSettings().openStream}, which is a stub headless, so a
     * headless decal pass had nothing to stamp and quietly cost nothing. The
     * standalone accumulator is GL-owned, while embedded hosts submit these
     * same frames through the ordinary decal command path.
     */
    @Override public SpriteAPI decalSheet() { return decals; }
    @Override public SpriteSheetFrames decalFrames() { return decalFrames; }
    @Override public void ensureDecalSheet() { }

    @Override public SpriteAPI warshipEngineFlameSprite() { return warshipEngineFlame; }
    @Override public SpriteAPI warshipEngineGlowSprite() { return warshipEngineGlow; }
    /**
     * The smoke utility sprites, off the resource roots. The base class loads them through {@code Global.getSettings().loadTexture}, a
     * stub here, so a headless frame of a screen showed the soldiers either side of it and no smoke between them. Loaded on first
     * use rather than in the constructor: the catalog that names the sheet is installed after the sprites are made.
     */
    private SpriteAPI smokeSheet;
    private SpriteAPI smokeGrenade;
    private boolean smokeLoaded;

    private synchronized void loadSmoke() {
        if (smokeLoaded) return;
        smokeLoaded = true;
        try {
            SpecialEquipmentDef smoke = SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SMOKE_GRENADE_ID);
            if (smoke.presentation().thrown() != null) smokeGrenade = spriteOrNull(smoke.presentation().thrown().spritePath());
            if (smoke.presentation().field() != null) smokeSheet = spriteOrNull(smoke.presentation().field().sheetPath());
        } catch (RuntimeException notInstalled) {
            smokeSheet = null;
            smokeGrenade = null;
        }
    }

    @Override public SpriteAPI smokeFieldSheet() {
        loadSmoke();
        return smokeSheet;
    }

    @Override public SpriteAPI smokeGrenadeSprite() {
        loadSmoke();
        return smokeGrenade;
    }

    @Override public void ensureSmokeSprites() { }

    private SpriteAPI fireSheet;
    private boolean fireLoaded;

    @Override public synchronized SpriteAPI fireSheet() {
        if (!fireLoaded) {
            fireLoaded = true;
            fireSheet = spriteOrNull(FIRE_SHEET_PATH);
        }
        return fireSheet;
    }

    private SpriteAPI flameSheet;
    private boolean flameLoaded;

    @Override public synchronized SpriteAPI flameSheet() {
        if (!flameLoaded) {
            flameLoaded = true;
            flameSheet = spriteOrNull(FireRenderSystem.FLAME_SHEET_PATH);
        }
        return flameSheet;
    }

    @Override public SpriteAPI engineFlameSprite() { return engineFlame; }
    @Override public SpriteAPI engineGlowSprite() { return engineGlow; }
    @Override public SpriteAPI shadowBlobSprite() { return shadowBlob; }
    @Override public int shadowBlobPxW() { return shadowBlobPxW; }
    @Override public int shadowBlobPxH() { return shadowBlobPxH; }
    /** Already loaded in the constructor, off the resource roots rather than through the game. */
    @Override public void ensureEngineFxSprites() { }
    @Override public void ensureShadowSprite() { }
    /**
     * The shipped drone's vanilla sprite, off the same disk roots as every other headless sprite. The base class loads
     * it through the game, which is a stub here, so every drone in headless evidence drew as its bare marker.
     */
    @Override public ShuttleSpriteCache droneSprite() { return spriteByPath(Drone.SPRITE_PATH); }
    @Override public void ensureDroneSprite() { }
    @Override public UnitLayerLayouts unitLayerLayouts() { return layouts; }

    /** Same resource lookup as the live catalog; evidence must not silently omit shells. */
    @Override public synchronized ShuttleSpriteCache projectileSprite(String path) {
        if (!projectiles.containsKey(path)) {
            SpriteAPI token = spriteOrNull(path);
            Asset asset = token == null ? null : asset(token);
            projectiles.put(path, asset == null ? null : new ShuttleSpriteCache(token,
                    asset.width() / (float) asset.height(), asset.width(), asset.height()));
        }
        return projectiles.get(path);
    }

    @Override public ModTextures.Loaded loadedImage(SpriteAPI sprite) {
        Asset asset = sprite == null ? null : assets.get(sprite);
        return asset == null ? null : new ModTextures.Loaded(asset.path(), asset.width(), asset.height());
    }

    Asset asset(SpriteAPI sprite) {
        Asset asset = assets.get(sprite);
        if (asset == null) throw new IllegalStateException("Unknown headless sprite token");
        return asset;
    }

    @Override public List<LayeredSpriteCache> bloodPools() { return bloodPools; }

    private final List<LayeredSpriteCache> bloodPools = new ArrayList<>();

    private void loadInfantry() throws IOException {
        for (int pool = 0; pool < BLOOD_POOLS; pool++) bloodPools.add(layer(BLOOD_POOL_ROOT + pool + ".png"));
        LayeredSpriteCache foot = layer(
                "graphics/battle/marine-modular-topdown/marine-foot.png");
        LayeredSpriteCache rifle = layer(INFANTRY_ROOT + "weapons/rifle.png");
        LayeredSpriteCache laser = layer(INFANTRY_ROOT + "weapons/laser-gun.png");
        LayeredSpriteCache smg = layer(INFANTRY_ROOT + "weapons/smg.png");
        LayeredSpriteCache dmr = layer(INFANTRY_ROOT + "weapons/dmr.png");
        LayeredSpriteCache rocket = layer(INFANTRY_ROOT + "weapons/rocket-launcher.png");
        LayeredSpriteCache amr = layer(INFANTRY_ROOT + "weapons/anti-materiel-rifle.png");
        LayeredSpriteCache flash = layer(
                "graphics/battle/marine-modular-topdown/marine-muzzle-flash.png");
        LayeredSpriteCache surplus = layer(
                INFANTRY_ROOT + "weapons/grades/surplus/rifle.png");
        LayeredSpriteCache masterwork = layer(
                INFANTRY_ROOT + "weapons/grades/masterwork/dmr.png");
        loadFamily(LayeredArmorFamily.ARMORLESS, INFANTRY_ROOT + "armor/armorless/",
                foot, null, rifle, laser, smg, dmr, rocket, amr, flash, surplus, masterwork);
        loadFamily(LayeredArmorFamily.CHARCOAL, INFANTRY_ROOT + "armor/charcoal/",
                foot, null, rifle, laser, smg, dmr, rocket, amr, flash, surplus, masterwork);
        loadFamily(LayeredArmorFamily.BLUE_SCOUT, INFANTRY_ROOT + "armor/blue-scout/",
                foot, null, rifle, laser, smg, dmr, rocket, amr, flash, surplus, masterwork);
        loadFamily(LayeredArmorFamily.RED_ELITE, INFANTRY_ROOT + "armor/red-heavy/",
                foot, null, rifle, laser, smg, dmr, rocket, amr, flash, surplus, masterwork);
        loadFamily(LayeredArmorFamily.OUTLAW, INFANTRY_ROOT + "armor/outlaw/",
                foot, null, rifle, laser, smg, dmr, rocket, amr, flash, surplus, masterwork);
        loadFamily(LayeredArmorFamily.ARMY_GREEN, INFANTRY_ROOT + "armor/army-green/",
                foot, null, rifle, laser, smg, dmr, rocket, amr, flash, surplus, masterwork);
        loadFamily(LayeredArmorFamily.MILITIA, INFANTRY_ROOT + "armor/militia/",
                foot, null, rifle, laser, smg, dmr, rocket, amr, flash, surplus, masterwork);
        loadFamily(LayeredArmorFamily.AEGIS_COMPOSITE, INFANTRY_ROOT + "armor/aegis/",
                foot, null, rifle, laser, smg, dmr, rocket, amr, flash, surplus, masterwork);
        loadFamily(LayeredArmorFamily.PALATINE, INFANTRY_ROOT + "armor/palatine/",
                foot, null, rifle, laser, smg, dmr, rocket, amr, flash, surplus, masterwork);
        loadFamily(LayeredArmorFamily.FURNACE_LINE, INFANTRY_ROOT + "armor/furnace-line/",
                foot, null, rifle, laser, smg, dmr, rocket, amr, flash, surplus, masterwork);
        loadFamily(LayeredArmorFamily.REAVER, INFANTRY_ROOT + "armor/reaver/",
                foot, null, rifle, laser, smg, dmr, rocket, amr, flash, surplus, masterwork);
        loadFamily(LayeredArmorFamily.SPECTER_HEAVY, INFANTRY_ROOT + "armor/specter-heavy/",
                layer(INFANTRY_ROOT + "armor/specter-heavy/foot.png"), null,
                rifle, laser, smg, dmr, rocket, amr, flash, surplus, masterwork);
        loadFamily(LayeredArmorFamily.ARBALEST_HEAVY, INFANTRY_ROOT + "armor/specter-heavy/paints/olive/",
                layer(INFANTRY_ROOT + "armor/specter-heavy/paints/olive/foot.png"), null,
                rifle, laser, smg, dmr, rocket, amr, flash, surplus, masterwork);
        loadFamily(LayeredArmorFamily.BOARDING_JACK, INFANTRY_ROOT + "armor/boarding-jack/",
                layer(INFANTRY_ROOT + "armor/boarding-jack/foot.png"), null,
                rifle, laser, smg, dmr, rocket, amr, flash, surplus, masterwork);
        loadFamily(LayeredArmorFamily.BOARDING_JACK_PIRATE, INFANTRY_ROOT + "armor/boarding-jack/paints/pirate/",
                layer(INFANTRY_ROOT + "armor/boarding-jack/paints/pirate/foot.png"), null,
                rifle, laser, smg, dmr, rocket, amr, flash, surplus, masterwork);
        loadFamily(LayeredArmorFamily.BULWARK_HEAVY, INFANTRY_ROOT + "armor/bulwark-heavy/",
                foot, null, rifle, laser, smg, dmr, rocket, amr, flash, surplus, masterwork);
        loadFamily(LayeredArmorFamily.RELIQUARY_HEAVY, INFANTRY_ROOT + "armor/reliquary-heavy/",
                foot, null, rifle, laser, smg, dmr, rocket, amr, flash, surplus, masterwork);
        loadFamily(LayeredArmorFamily.LIONS_MANTLE, INFANTRY_ROOT + "armor/lions-mantle/",
                foot, null, rifle, laser, smg, dmr, rocket, amr, flash, surplus, masterwork);
        loadFamily(LayeredArmorFamily.FOUNDRY_BREAKER, INFANTRY_ROOT + "armor/foundry-breaker/",
                foot, null, rifle, laser, smg, dmr, rocket, amr, flash, surplus, masterwork);
        loadFamily(LayeredArmorFamily.SHIP_COMMAND,
                "graphics/battle/ship-crew-modular-topdown/command/", layer("graphics/battle/ship-crew-modular-topdown/command/foot.png"),
                null, rifle, laser, smg, dmr, rocket, amr, flash, surplus, masterwork);
        loadFamily(LayeredArmorFamily.SHIP_ENGINEERING,
                "graphics/battle/ship-crew-modular-topdown/engineering/", layer("graphics/battle/ship-crew-modular-topdown/engineering/foot.png"),
                null, rifle, laser, smg, dmr, rocket, amr, flash, surplus, masterwork);
        loadFamily(LayeredArmorFamily.SHIP_MEDICAL,
                "graphics/battle/ship-crew-modular-topdown/medical/", layer("graphics/battle/ship-crew-modular-topdown/medical/foot.png"),
                null, rifle, laser, smg, dmr, rocket, amr, flash, surplus, masterwork);
        loadFamily(LayeredArmorFamily.SHIP_SERVICE,
                "graphics/battle/ship-crew-modular-topdown/service/", layer("graphics/battle/ship-crew-modular-topdown/service/foot.png"),
                null, rifle, laser, smg, dmr, rocket, amr, flash, surplus, masterwork);
        loadFamily(LayeredArmorFamily.CIVILIAN_COLONIST,
                "graphics/battle/colonist-modular-topdown/civilian/", layer("graphics/battle/colonist-modular-topdown/civilian/foot.png"),
                null, rifle, laser, smg, dmr, rocket, amr, flash, surplus, masterwork);
        loadFamily(LayeredArmorFamily.ENGINEER,
                "graphics/battle/colonist-modular-topdown/engineer/", layer("graphics/battle/colonist-modular-topdown/engineer/foot.png"),
                null, rifle, laser, smg, dmr, rocket, amr, flash, surplus, masterwork);
        loadFamily(LayeredArmorFamily.SCIENTIST,
                "graphics/battle/colonist-modular-topdown/scientist/", layer("graphics/battle/colonist-modular-topdown/scientist/foot.png"),
                null, rifle, laser, smg, dmr, rocket, amr, flash, surplus, masterwork);
        // The swarm, as BattleSprites loads it: the Spitter walks and strikes on the Runner's feet and claws.
        String alien = "graphics/battle/alien-modular-topdown/";
        LayeredSpriteCache alienFoot = layer(alien + "foot.png");
        LayeredSpriteCache alienClaw = layer(alien + "fore-claw.png");
        loadFamily(LayeredArmorFamily.XENO, alien, alienFoot, alienClaw,
                rifle, laser, smg, dmr, rocket, amr, flash, surplus, masterwork);
        String brute = "graphics/battle/swarm-brute-modular-topdown/";
        loadFamily(LayeredArmorFamily.XENO_BRUTE, brute, layer(brute + "foot.png"),
                layer(brute + "fore-claw.png"), rifle, laser, smg, dmr, rocket, amr, flash, surplus, masterwork);
        loadFamily(LayeredArmorFamily.XENO_SPITTER, "graphics/battle/swarm-spitter-modular-topdown/",
                alienFoot, alienClaw, rifle, laser, smg, dmr, rocket, amr, flash, surplus, masterwork);
        ravRunner = RavRunnerAssets.load(path -> {
            try {
                return layer(path);
            } catch (IOException e) {
                return null;
            }
        });
        ravBrute = RavBruteAssets.load(path -> {
            try {
                return layer(path);
            } catch (IOException e) {
                return null;
            }
        });
        ravMatriarch = RavMatriarchAssets.load(path -> {
            try {
                return layer(path);
            } catch (IOException e) {
                return null;
            }
        });
        ravSpitter = RavSpitterAssets.load(path -> {
            try {
                return layer(path);
            } catch (IOException e) {
                return null;
            }
        });
        ravBombard = RavBombardAssets.load(path -> {
            try {
                return layer(path);
            } catch (IOException e) {
                return null;
            }
        });
        // Families without lying poses fall through their unit type's corpse sheet, which the
        // base class would load through the game.
        for (UnitType type : UnitType.values()) {
            if (type.deadSpritePath == null || !type.drawnAsLayers()
                    || LayeredArmorFamily.spawnDefault(type).hasDownedArt()) continue;
            deadSheets.put(type, new UnitSpriteCache(sprite(type.deadSpritePath),
                    slice(type.deadSpritePath)));
        }
    }

    private RavRunnerAssets ravRunner;
    private RavBruteAssets ravBrute;
    private RavMatriarchAssets ravMatriarch;
    private RavSpitterAssets ravSpitter;
    private RavBombardAssets ravBombard;
    private final EnumMap<UnitType, UnitSpriteCache> deadSheets = new EnumMap<>(UnitType.class);

    @Override public EnumMap<UnitType, UnitSpriteCache> unitDeadSprites() { return deadSheets; }

    private void loadFamily(LayeredArmorFamily family, String root,
                            LayeredSpriteCache foot, LayeredSpriteCache claw,
                            LayeredSpriteCache rifle, LayeredSpriteCache laser,
                            LayeredSpriteCache smg, LayeredSpriteCache dmr,
                            LayeredSpriteCache rocket, LayeredSpriteCache amr,
                            LayeredSpriteCache flash, LayeredSpriteCache surplus,
                            LayeredSpriteCache masterwork) throws IOException {
        boolean articulatedShoulders = family.articulatedShoulders();
        LayeredUnitAssets loaded = new LayeredUnitAssets(layer(root + "body.png"),
                layer(root + "head.png"), foot, claw, rifle, laser, smg, dmr,
                rocket, amr, flash, surplus, masterwork,
                articulatedShoulders
                        ? optionalLayer(root + "shoulder-left.png") : null,
                articulatedShoulders
                        ? optionalLayer(root + "shoulder-right.png") : null);
        SpecialEquipmentRegistry equipment = SpecialEquipmentRegistry.installed();
        if (equipment != null) {
            for (SpecialEquipmentDef def : equipment.all()) {
                EquipmentLayerDef carrier = def.presentation().carrierLayer();
                if (carrier != null) {
                    loaded.registerSpecialEquipment(def.id(), layer(carrier.spritePath()));
                }
                EquipmentLayerDef packed = def.presentation().packedLayer();
                if (packed != null) {
                    loaded.registerSpecialEquipment(
                            SpecialEquipmentPresentationDef.packedAssetKey(def.id()), layer(packed.spritePath()));
                }
                HandsetLayer handset = def.presentation().handsetLayer();
                if (handset != null) {
                    loaded.registerHandset(def.id(), layer(handset.spritePath()),
                            layer(handset.cordSpritePath()));
                }
            }
        }
        loaded.registerSpecialEquipment(TreatmentKitLayer.ASSET_KEY, layer(TreatmentKitLayer.SPRITE));
        for (var prop : WorkPropLayer.SPRITES.entrySet()) {
            loaded.registerSpecialEquipment(prop.getKey(), layer(prop.getValue()));
        }
        MarineArmorCatalogRegistry armorRegistry = MarineArmorCatalogRegistry.installed();
        if (armorRegistry != null) {
            for (MarineArmorCatalogDef armor : armorRegistry.all()) {
                IntegralSystemDef system = armor.integralSystem();
                if (system != null && system.carrierLayer() != null) {
                    loaded.registerIntegralSystem(system.id(),
                            layer(system.carrierLayer().spritePath()));
                }
            }
        }
        if (family.hasDownedArt()) {
            for (int pose = 0; pose < LayeredUnitAssets.DOWNED_POSES; pose++) {
                loaded.registerDowned(layer(root + "downed-" + pose + ".png"));
            }
        }
        if (family.hasStationArms()) {
            loaded.registerStationArms(layer(root + "station-arm-left.png"),
                    layer(root + "station-arm-right.png"));
        }
        infantry.put(family, loaded);
    }

    private LayeredMechAssets loadMech() throws IOException {
        return new LayeredMechAssets(layer(MECH_ROOT + "chassis.png"),
                layer(MECH_ROOT + "chassis-socketed-variant.png"),
                layer(MECH_ROOT + "chassis-hound.png"),
                layer(MECH_ROOT + "chassis-warden.png"),
                layer(MECH_ROOT + "foot.png"), layer(MECH_ROOT + "thigh-bone.png"),
                layer(MECH_ROOT + "chaingun-arm.png"),
                layer(MECH_ROOT + "linear-cannon-variant.png"),
                layer(MECH_ROOT + "heavy-cannon.png"), layer(MECH_ROOT + "srm-pod.png"),
                layer(MECH_ROOT + "lrm-pod.png"),
                layer(MECH_ROOT + "shoulder-laser-cannon.png"),
                layer(MECH_ROOT + "pulse-laser-arm.png"),
                layer(MECH_ROOT + "hegemony-bastion-autocannon.png"),
                  layer(MECH_ROOT + "pather-demolition-cannon.png"),
                  layer(MECH_ROOT + "lions-guard-thermal-lance.png"),
                  layer(MECH_ROOT + "muster-autogun.png"),
                  layer(MECH_ROOT + "quarry-breaker-cannon.png"),
                  layer(MECH_ROOT + "srm-3-qs-pod.png"),
                  layer(MECH_ROOT + "light-machine-gun.png"),
                  layer(MECH_ROOT + "light-pulse-laser.png"),
                  layer(MECH_ROOT + "burst-laser.png"),
                  layer(MECH_ROOT + "pd-laser.png"),
                  layer(MECH_ROOT + "heavy-pd-laser.png"),
                  layer(MECH_ROOT + "medium-burst-laser.png"),
                  layer(MECH_ROOT + "light-burst-laser.png"),
                  layer("graphics/battle/marine-modular-topdown/marine-muzzle-flash.png"));
    }

    private LayeredSpriteCache optionalLayer(String path) throws IOException {
        return Files.exists(modRoot.resolve(path)) ? layer(path) : null;
    }

    private LayeredSpriteCache layer(String path) throws IOException {
        SpriteAPI token = sprite(path);
        Asset asset = asset(token);
        return new LayeredSpriteCache(token, path, asset.width(), asset.height());
    }

    private SpriteSheetFrames slice(String path) throws IOException {
        BufferedImage image = read(path);
        TextureUsage.sliced(path);
        return SpriteSheetSlicer.slice(image);
    }

    @Override
    public SpriteAPI extraTerrainSheet(String path) {
        return extraTerrainSheets.get(path);
    }

    @Override public java.util.Collection<SpriteAPI> extraTerrainSheets() {
        return extraTerrainSheets.values();
    }

    /** A token for {@code path}, sized from the file's header: neither drain needs its pixels here. */
    private SpriteAPI sprite(String path) throws IOException {
        Path file = resolve(path);
        int[] size;
        try {
            size = FittedTextureOverlay.size(path, file);
        } catch (IOException failure) {
            throw new IOException("Could not read " + path, failure);
        }
        SpriteAPI token = tokens.token(path, file, size[0], size[1]);
        assets.put(token, new Asset(path, size[0], size[1]));
        TextureUsage.name(token, path, size[0], size[1]);
        return token;
    }

    /**
     * The default token: its own size, its own identity, and nothing else.
     *
     * <p>{@code getTextureWidth} answers in pixels rather than as a normalised
     * max-U because no texture exists to normalise against; the Java2D drain
     * divides by the recorded {@link Asset} size and never reads it. A GL token
     * has to answer differently, which is the other half of why the factory
     * exists.
     *
     * <p>It keeps the size and lets the pixels go. The handler used to close
     * over the decoded image to answer two numbers, so every sprite the
     * bootstrap read stayed resident for the life of the cache — 1.3 GB of a
     * 1.7 GB heap on The Burn's framed run, which is what ran it out of a 2g
     * cap. The drain paints from {@code HeadlessUiRenderer}'s own store,
     * never from here.
     */
    private static SpriteAPI identityToken(String path, Path file, int pixelWidth, int pixelHeight) {
        float width = pixelWidth;
        float height = pixelHeight;
        return (SpriteAPI) Proxy.newProxyInstance(
                SpriteAPI.class.getClassLoader(), new Class<?>[]{SpriteAPI.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getWidth", "getTextureWidth" -> width;
                    case "getHeight", "getTextureHeight" -> height;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "HeadlessSprite[" + path + "]";
                    default -> primitiveDefault(method.getReturnType());
                });
    }

    /**
     * Read one asset, naming it if the read fails.
     *
     * <p>ImageIO reports a failed decode as "Caught exception during read"
     * with no indication of what it was reading, which turns a rare failure
     * in a pack of several hundred files into an unactionable one. The
     * bootstrap loads them all, so the name is the whole diagnosis.
     */
    private BufferedImage read(String path) throws IOException {
        Path file = resolve(path);
        BufferedImage image;
        try {
            image = FittedTextureOverlay.read(path, file);
        } catch (IOException failure) {
            throw new IOException("Could not read " + path, failure);
        }
        if (image == null) throw new IOException("Unsupported image " + path);
        return image;
    }

    /**
     * The file behind a resource path: the mod's copy if it ships one, the
     * installed game's otherwise.
     *
     * <p>The game's own order, so a mod file at the same path wins here for
     * the same reason it wins in the game. Falls back to the mod path when
     * neither exists, so the failure names the file somebody expected to
     * ship rather than one in an install they were not thinking about.
     */
    private Path resolve(String path) {
        Path shipped = modRoot.resolve(path);
        if (Files.isRegularFile(shipped) || vanillaRoot == null) return shipped;
        Path installed = vanillaRoot.resolve(path);
        return Files.isRegularFile(installed) ? installed : shipped;
    }

    /** Whatever a {@link SpriteAPI} method with no headless meaning must still return. */
    static Object primitiveDefault(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == char.class) return '\0';
        return null;
    }
}
