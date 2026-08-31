package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.air.Airframe;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
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
import java.util.LinkedHashMap;
import java.util.List;
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
            // BattleScreen.attach loads these before it builds batches; nothing
            // does it here, so a headless scene collected every unit and drew
            // none of them -- UnitRenderService skips a row whose sheet is
            // null, silently, which reads as a render system that does not
            // work rather than as art that was never loaded.
            sprites.ensureUnitSheets();
            sprites.ensureEngineFxSprites();
            renderer = new BattleRenderer(sprites);
            this.skipUnsupportedCommands = skipUnsupportedCommands;
        } catch (Exception failure) {
            throw new IllegalStateException("Could not prepare headless battle assets", failure);
        }
    }

    /**
     * Where the installed game keeps its own art, from the
     * {@code starsectorDir} property every Gradle task that renders anything
     * already forwards.
     *
     * <p>Not a new dependency: the install is where the compile-only game jars
     * come from, so the build cannot run at all without it. Null when the
     * property is absent or points nowhere, and everything sourced from it is
     * then simply not drawn — the behaviour before any of this existed.
     */
    public static Path installedGameResources() {
        String installed = System.getProperty("starsectorDir");
        if (installed == null || installed.isBlank()) return null;
        Path core = Path.of(installed).resolve("starsector-core");
        return Files.isDirectory(core) ? core.toAbsolutePath().normalize() : null;
    }

    /**
     * The roots a canvas drawing this scene must be able to read, in the order
     * the game itself reads them: the mod first, the install second.
     *
     * <p>A scene renderer and the canvas it draws into have to agree on where
     * files come from. They are separate objects with separate lookups, so a
     * scene that loaded a hull out of the install and a canvas that could only
     * see the mod folder would collect the command and then fail to paint it.
     */
    public static List<Path> resourceRoots(Path modRoot) {
        Path installed = installedGameResources();
        return installed == null ? List.of(modRoot) : List.of(modRoot, installed);
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

    /** Set {@code -Dbattle.diag.commandCounts} to have every collected frame print its per-layer command count. */
    private static final String COMMAND_COUNT_PROPERTY = "battle.diag.commandCounts";

    @Override
    public boolean draw(CanvasHostPass pass, CanvasContext context,
                        CanvasHostViewport viewport, float alphaMult) {
        if (!(pass instanceof BattleSceneHostPass scenePass)) return false;
        // RasterCanvasContext applies the document alpha while painting each
        // replayed primitive. Collect at full opacity so the host fade is not
        // folded into the battle commands and then multiplied a second time.
        BattleSceneFrame frame = scenePass.prepare(viewport, 1f);
        DrawList commands = renderer.collectWorld(frame.context(), frame.layers());
        // Opt-in per-layer census. "Nothing appeared" has two very different
        // causes -- a collector that emitted nothing, and a drain that dropped
        // what it was given -- and they look identical from the picture. This
        // separates them in one run; guessing between them cost several.
        if (System.getProperty(COMMAND_COUNT_PROPERTY) != null) {
            StringBuilder diag = new StringBuilder("[cmd-counts]");
            for (RenderLayer probe : RenderLayer.values()) {
                if (commands.count(probe) > 0) {
                    diag.append(' ').append(probe).append('=').append(commands.count(probe));
                }
            }
            diag.append(" | selected=").append(frame.layers());
            System.out.println(diag);
        }
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
                        command.angleDegrees(), tint, CanvasSpriteRegion.FULL,
                        command.additive() ? CanvasBlend.ADDITIVE : CanvasBlend.NORMAL);
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
        private SpriteAPI engineFlame;
        private SpriteAPI engineGlow;
        private SpriteAPI shadowBlob;

        private HeadlessBattleSprites(Path modRoot) throws Exception {
            this.modRoot = modRoot.toAbsolutePath().normalize();
            this.vanillaRoot = installedGameResources();
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
        private void loadHulls() {
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
        @Override public SpriteAPI engineFlameSprite() { return engineFlame; }
        @Override public SpriteAPI engineGlowSprite() { return engineGlow; }
        @Override public SpriteAPI shadowBlobSprite() { return shadowBlob; }
        /** Already loaded in the constructor, off the resource roots rather than through the game. */
        @Override public void ensureEngineFxSprites() { }
        @Override public void ensureShadowSprite() { }
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
                    layer(MECH_ROOT + "shoulder-laser-cannon.png"),
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
