package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.air.Airframe;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetFrames;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetSlicer;
import com.dillon.starsectormarines.marine.EquipmentLayerDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
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
         * @param image the decoded pixels, valid only for the duration of the call
         */
        SpriteAPI token(String path, Path file, BufferedImage image);
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
    private final UnitLayerLayouts layouts;

    private final SpriteAPI tile;
    private final SpriteAPI road;
    private final SpriteAPI floors;
    private final SpriteAPI water;
    private final SpriteAPI urban3;
    private final SpriteAPI nature;
    private final SpriteAPI doodads;
    private final SpriteAPI parkedVehicles;
    private final SpriteSheetFrames urban3Frames;
    private final SpriteSheetFrames natureFrames;
    private final LayeredMechAssets mech;
    private SpriteAPI decals;
    private SpriteSheetFrames decalFrames;
    private SpriteAPI engineFlame;
    private SpriteAPI engineGlow;
    private SpriteAPI shadowBlob;

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
        water = sprite(TileManifest.WATER_SHEET);
        urban3 = sprite(TileManifest.STREET3_SHEET);
        nature = sprite(TileManifest.NATURE_SHEET);
        doodads = sprite(TileManifest.DOODAD_SHEET);
        parkedVehicles = sprite(TileManifest.PARKED_VEHICLE_SHEET);
        urban3Frames = slice(TileManifest.STREET3_SHEET);
        natureFrames = slice(TileManifest.NATURE_SHEET);
        loadInfantry();
        loadHulls();
        loadEngineFx();
        loadDecals();
        mech = loadMech();
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
        engineFlame = spriteOrNull(ENGINE_FLAME_SPRITE);
        engineGlow = spriteOrNull(ENGINE_GLOW_SPRITE);
        shadowBlob = spriteOrNull(SHADOW_BLOB_SPRITE);
    }

    private SpriteAPI spriteOrNull(String path) {
        try {
            return sprite(path);
        } catch (IOException | RuntimeException missing) {
            return null;
        }
    }

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

    @Override public SpriteAPI tileSheet() { return tile; }
    @Override public int tileSheetPxW() { return asset(tile).width(); }
    @Override public int tileSheetPxH() { return asset(tile).height(); }
    @Override public SpriteAPI roadSheet() { return road; }
    @Override public int roadSheetPxW() { return asset(road).width(); }
    @Override public int roadSheetPxH() { return asset(road).height(); }
    @Override public SpriteAPI floorsSheet() { return floors; }
    @Override public int floorsSheetPxW() { return asset(floors).width(); }
    @Override public int floorsSheetPxH() { return asset(floors).height(); }
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
    @Override public LayeredMechAssets layeredMechSprites() { return mech; }
    @Override public Map<Airframe, ShuttleSpriteCache> airframeSprites() { return airframes; }
    /**
     * The decal sheet, off the resource roots.
     *
     * <p>Loaded here because the base class reaches it through
     * {@code Global.getSettings().openStream}, which is a stub headless, so a
     * headless decal pass had nothing to stamp and quietly cost nothing. The
     * collected command stream is unchanged either way — the accumulator is a
     * {@code CUSTOM} whether or not it has a sheet — so this only matters to a
     * drain that actually runs it.
     */
    @Override public SpriteAPI decalSheet() { return decals; }
    @Override public SpriteSheetFrames decalFrames() { return decalFrames; }
    @Override public void ensureDecalSheet() { }

    @Override public SpriteAPI engineFlameSprite() { return engineFlame; }
    @Override public SpriteAPI engineGlowSprite() { return engineGlow; }
    @Override public SpriteAPI shadowBlobSprite() { return shadowBlob; }
    /** Already loaded in the constructor, off the resource roots rather than through the game. */
    @Override public void ensureEngineFxSprites() { }
    @Override public void ensureShadowSprite() { }
    @Override public UnitLayerLayouts unitLayerLayouts() { return layouts; }

    Asset asset(SpriteAPI sprite) {
        Asset asset = assets.get(sprite);
        if (asset == null) throw new IllegalStateException("Unknown headless sprite token");
        return asset;
    }

    private void loadInfantry() throws IOException {
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
                foot, null, rifle, laser, smg, dmr, rocket, amr, flash, surplus, masterwork);
        loadFamily(LayeredArmorFamily.BULWARK_HEAVY, INFANTRY_ROOT + "armor/bulwark-heavy/",
                foot, null, rifle, laser, smg, dmr, rocket, amr, flash, surplus, masterwork);
        loadFamily(LayeredArmorFamily.RELIQUARY_HEAVY, INFANTRY_ROOT + "armor/reliquary-heavy/",
                foot, null, rifle, laser, smg, dmr, rocket, amr, flash, surplus, masterwork);
        loadFamily(LayeredArmorFamily.LIONS_MANTLE, INFANTRY_ROOT + "armor/lions-mantle/",
                foot, null, rifle, laser, smg, dmr, rocket, amr, flash, surplus, masterwork);
        loadFamily(LayeredArmorFamily.FOUNDRY_BREAKER, INFANTRY_ROOT + "armor/foundry-breaker/",
                foot, null, rifle, laser, smg, dmr, rocket, amr, flash, surplus, masterwork);
        loadFamily(LayeredArmorFamily.ENGINEER,
                "graphics/battle/colonist-modular-topdown/engineer/",
                foot, null, rifle, laser, smg, dmr, rocket, amr, flash, surplus, masterwork);
    }

    private void loadFamily(LayeredArmorFamily family, String root,
                            LayeredSpriteCache foot, LayeredSpriteCache claw,
                            LayeredSpriteCache rifle, LayeredSpriteCache laser,
                            LayeredSpriteCache smg, LayeredSpriteCache dmr,
                            LayeredSpriteCache rocket, LayeredSpriteCache amr,
                            LayeredSpriteCache flash, LayeredSpriteCache surplus,
                            LayeredSpriteCache masterwork) throws IOException {
        LayeredUnitAssets loaded = new LayeredUnitAssets(layer(root + "body.png"),
                layer(root + "head.png"), foot, claw, rifle, laser, smg, dmr,
                rocket, amr, flash, surplus, masterwork);
        SpecialEquipmentRegistry equipment = SpecialEquipmentRegistry.installed();
        if (equipment != null) {
            for (SpecialEquipmentDef def : equipment.all()) {
                EquipmentLayerDef carrier = def.presentation().carrierLayer();
                if (carrier != null) {
                    loaded.registerSpecialEquipment(def.id(), layer(carrier.spritePath()));
                }
            }
        }
        infantry.put(family, loaded);
    }

    private LayeredMechAssets loadMech() throws IOException {
        return new LayeredMechAssets(layer(MECH_ROOT + "chassis.png"),
                layer(MECH_ROOT + "chassis-socketed-variant.png"),
                layer(MECH_ROOT + "chassis-hound.png"),
                layer(MECH_ROOT + "chassis-sirocco.png"),
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
                  layer(MECH_ROOT + "pioneer-rocket-cradle.png"),
                  layer("graphics/battle/marine-modular-topdown/marine-muzzle-flash.png"));
    }

    private LayeredSpriteCache layer(String path) throws IOException {
        SpriteAPI token = sprite(path);
        Asset asset = asset(token);
        return new LayeredSpriteCache(token, path, asset.width(), asset.height());
    }

    private SpriteSheetFrames slice(String path) throws IOException {
        BufferedImage image = read(path);
        return SpriteSheetSlicer.slice(image);
    }

    private SpriteAPI sprite(String path) throws IOException {
        Path file = resolve(path);
        BufferedImage image = read(path);
        SpriteAPI token = tokens.token(path, file, image);
        assets.put(token, new Asset(path, image.getWidth(), image.getHeight()));
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
     */
    private static SpriteAPI identityToken(String path, Path file, BufferedImage image) {
        return (SpriteAPI) Proxy.newProxyInstance(
                SpriteAPI.class.getClassLoader(), new Class<?>[]{SpriteAPI.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getWidth", "getTextureWidth" -> (float) image.getWidth();
                    case "getHeight", "getTextureHeight" -> (float) image.getHeight();
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
            image = ImageIO.read(file.toFile());
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
