package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts;
import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetFrames;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetSlicer;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.marine.EquipmentLayerDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.render2d.DrawCommand;
import com.dillon.starsectormarines.render2d.PolyMesh;
import com.dillon.starsectormarines.ui.retained.CanvasBlend;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasHostPass;
import com.dillon.starsectormarines.ui.retained.CanvasHostViewport;
import com.dillon.starsectormarines.ui.retained.CanvasSpriteRegion;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessHostPassRenderer;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/** Java2D drain for the ordinary battle renderer's collected embedded-scene frame. */
public final class HeadlessBattleSceneRenderer implements HeadlessHostPassRenderer {

    /**
     * Loaded sprite sets, kept only while there is room for them.
     *
     * <p>Softly rather than strongly held. A sprite set is tens of megabytes of
     * decoded image and this map is keyed by resource root, so a caller that
     * renders against a fresh root each time — an authoring preview pointed at a
     * temporary copy of the mod folder — adds an entry per call and never
     * removes one. That exhausted the heap partway through a full suite run, and
     * it surfaced as an unrelated sheet failing to read, because ImageIO wraps
     * an OutOfMemoryError as an ordinary IIOException.
     *
     * <p>Soft references keep the fast path — a suite rendering repeatedly
     * against the shipped root still loads once — while guaranteeing the cache
     * is cleared before the heap runs out.
     */
    private static final Map<Path, SoftReference<HeadlessBattleSprites>> SHARED_SPRITES =
            new HashMap<>();

    private final HeadlessBattleSprites sprites;
    private final BattleRenderer renderer;
    private final boolean skipUnsupportedCommands;

    public HeadlessBattleSceneRenderer(Path modRoot) {
        this(modRoot, false);
    }

    /**
     * @param skipUnsupportedCommands omit GL-owned decorative commands while
     *        retaining the command-driven world; ordinary snapshot tests keep
     *        fail-loud behavior by using the one-argument constructor
     */
    public HeadlessBattleSceneRenderer(Path modRoot,
                                       boolean skipUnsupportedCommands) {
        try {
            HeadlessArmoryPreviewRenderer.installCatalogs(modRoot);
            installTileCatalogs(modRoot);
            sprites = sharedSprites(modRoot);
            renderer = new BattleRenderer(sprites);
            this.skipUnsupportedCommands = skipUnsupportedCommands;
        } catch (Exception failure) {
            throw new IllegalStateException("Could not prepare headless battle assets", failure);
        }
    }

    private static HeadlessBattleSprites sharedSprites(Path modRoot) throws Exception {
        Path normalized = modRoot.toAbsolutePath().normalize();
        synchronized (SHARED_SPRITES) {
            SoftReference<HeadlessBattleSprites> cached = SHARED_SPRITES.get(normalized);
            HeadlessBattleSprites existing = cached == null ? null : cached.get();
            if (existing != null) return existing;
            // Entries whose sprites have been collected are dead weight, and the
            // roots that produce them are exactly the ones never asked for again.
            SHARED_SPRITES.entrySet().removeIf(entry -> entry.getValue().get() == null);
            HeadlessBattleSprites loaded = new HeadlessBattleSprites(normalized);
            SHARED_SPRITES.put(normalized, new SoftReference<>(loaded));
            return loaded;
        }
    }

    @Override
    public boolean draw(CanvasHostPass pass, CanvasContext context,
                        CanvasHostViewport viewport, float alphaMult) {
        if (!(pass instanceof BattleSceneHostPass scenePass)) return false;
        // RasterCanvasContext applies the document alpha while painting each
        // replayed primitive. Collect at full opacity so the host fade is not
        // folded into the battle commands and then multiplied a second time.
        BattleSceneFrame frame = scenePass.prepare(viewport, 1f);
        DrawList commands = renderer.collectWorld(frame.context(), frame.layers());
        for (RenderLayer layer : RenderLayer.values()) {
            if (!frame.layers().contains(layer)) continue;
            for (int index = 0; index < commands.count(layer); index++) {
                drawCommand(context, viewport, commands.buffer(layer)[index]);
            }
        }
        return true;
    }

    private void drawCommand(CanvasContext context, CanvasHostViewport viewport,
                             DrawCommand command) {
        float surfaceHeight = viewport.height();
        Color tint = color(command);
        switch (command.kind()) {
            case SHEET_QUAD -> {
                Asset asset = sprites.asset(command.sprite());
                CanvasSpriteRegion region = new CanvasSpriteRegion(
                        command.sourceX() / (float) asset.width(),
                        command.sourceY() / (float) asset.height(),
                        command.sourceWidth() / (float) asset.width(),
                        command.sourceHeight() / (float) asset.height());
                if (command.flippedVertically()) region = region.flippedVertically();
                context.sprite(asset.path(), null, command.centerX(),
                        surfaceHeight - command.centerY(), command.width(), command.height(),
                        command.angleDegrees(), tint, region, CanvasBlend.NORMAL);
            }
            case SPRITE -> {
                Asset asset = sprites.asset(command.sprite());
                context.sprite(asset.path(), null, command.centerX(),
                        surfaceHeight - command.centerY(), command.width(), command.height(),
                        command.angleDegrees(), tint);
            }
            case SOLID_RECT -> {
                float left = Math.min(command.centerX(), command.width());
                float right = Math.max(command.centerX(), command.width());
                float bottom = Math.min(command.centerY(), command.height());
                float top = Math.max(command.centerY(), command.height());
                context.fillRect(left, surfaceHeight - top,
                        right - left, top - bottom, tint);
            }
            case LINE -> context.line(command.centerX(),
                    surfaceHeight - command.centerY(), command.width(),
                    surfaceHeight - command.height(), tint, command.angleDegrees());
            case POLY -> drawPolygon(context, surfaceHeight,
                    command.polygon());
            case RIBBON, CUSTOM -> {
                if (!skipUnsupportedCommands) {
                    throw new IllegalStateException(
                            "Embedded headless scene emitted unsupported command "
                                    + command.kind());
                }
            }
        }
    }

    private static void drawPolygon(CanvasContext context, float surfaceHeight,
                                    PolyMesh mesh) {
        if (mesh == null) return;
        for (int quad = 0; quad < mesh.quadCount(); quad++) {
            Color color = new Color(clamp(mesh.red(quad)),
                    clamp(mesh.green(quad)), clamp(mesh.blue(quad)),
                    clamp(mesh.alpha(quad)));
            context.fillQuad(
                    mesh.vertexX(quad, 0), surfaceHeight - mesh.vertexY(quad, 0),
                    mesh.vertexX(quad, 1), surfaceHeight - mesh.vertexY(quad, 1),
                    mesh.vertexX(quad, 2), surfaceHeight - mesh.vertexY(quad, 2),
                    mesh.vertexX(quad, 3), surfaceHeight - mesh.vertexY(quad, 3),
                    color);
        }
    }

    private static Color color(DrawCommand command) {
        return new Color(clamp(command.red()), clamp(command.green()),
                clamp(command.blue()), clamp(command.alpha()));
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static void installTileCatalogs(Path modRoot) throws Exception {
        if (TileRegistry.installed() == null) {
            TileRegistry tiles = new TileRegistry();
            for (String path : TileRegistry.BUILTIN_TILESETS) {
                tiles.ingestSheet(new JSONObject(Files.readString(modRoot.resolve(path))));
            }
            tiles.validateReferences();
            TileRegistry.install(tiles);
        }
        if (GenMappingRegistry.installed() == null) {
            GenMappingRegistry mappings = new GenMappingRegistry();
            for (String path : GenMappingRegistry.BUILTIN_MAPPINGS) {
                mappings.ingest(new JSONObject(Files.readString(modRoot.resolve(path))));
            }
            GenMappingRegistry.install(mappings);
        }
    }

    private record Asset(String path, int width, int height) { }

    /** Disk-backed sprite catalog that presents harmless SpriteAPI identity tokens to collectors. */
    private static final class HeadlessBattleSprites extends BattleSprites {
        private static final String INFANTRY_ROOT =
                "graphics/battle/marine-modular-topdown/variants/";
        private static final String MECH_ROOT =
                "graphics/battle/mech-modular-topdown/";

        private final Path modRoot;
        private final IdentityHashMap<SpriteAPI, Asset> assets = new IdentityHashMap<>();
        private final EnumMap<LayeredArmorFamily, LayeredUnitAssets> infantry =
                new EnumMap<>(LayeredArmorFamily.class);
        private final UnitLayerLayouts layouts;

        private final SpriteAPI systemScreenFacet;
        private final SpriteAPI systemEmitter;
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

        private HeadlessBattleSprites(Path modRoot) throws Exception {
            this.modRoot = modRoot.toAbsolutePath().normalize();
            layouts = UnitLayerLayouts.parse(new JSONObject(Files.readString(
                    this.modRoot.resolve(UnitLayerLayouts.CONTENT_PATH))));
            // The live loader reaches these through Global.getSettings(), which
            // does not exist here — so a headless review would silently draw a
            // running integral system without its art unless they are bound.
            systemScreenFacet = sprite(BattleSprites.SYSTEM_SCREEN_FACET_SPRITE);
            systemEmitter = sprite(BattleSprites.SYSTEM_EMITTER_SPRITE);
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
            mech = loadMech();
        }

        @Override public SpriteAPI systemScreenFacetSprite() { return systemScreenFacet; }
        @Override public SpriteAPI systemEmitterSprite() { return systemEmitter; }
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
        @Override public UnitLayerLayouts unitLayerLayouts() { return layouts; }

        private Asset asset(SpriteAPI sprite) {
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
            BufferedImage image = read(path);
            SpriteAPI token = (SpriteAPI) Proxy.newProxyInstance(
                    SpriteAPI.class.getClassLoader(), new Class<?>[]{SpriteAPI.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getWidth", "getTextureWidth" -> (float) image.getWidth();
                        case "getHeight", "getTextureHeight" -> (float) image.getHeight();
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        case "toString" -> "HeadlessSprite[" + path + "]";
                        default -> primitiveDefault(method.getReturnType());
                    });
            assets.put(token, new Asset(path, image.getWidth(), image.getHeight()));
            return token;
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
            BufferedImage image;
            try {
                image = ImageIO.read(modRoot.resolve(path).toFile());
            } catch (IOException failure) {
                throw new IOException("Could not read " + path, failure);
            }
            if (image == null) throw new IOException("Unsupported image " + path);
            return image;
        }

        private static Object primitiveDefault(Class<?> type) {
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
}
