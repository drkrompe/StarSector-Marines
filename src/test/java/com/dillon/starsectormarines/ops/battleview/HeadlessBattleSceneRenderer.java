package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.air.Airframe;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts;
import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.gen.road.RoadCatalog;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainCatalog;
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
                HeadlessBattleSprites.Asset asset = sprites.asset(command.sprite());
                CanvasSpriteRegion region = new CanvasSpriteRegion(
                        command.sourceX() / (float) asset.width(),
                        command.sourceY() / (float) asset.height(),
                        command.sourceWidth() / (float) asset.width(),
                        command.sourceHeight() / (float) asset.height());
                if (command.flippedVertically()) region = region.flippedVertically();
                if(command.patch()!=null) context.spritePatch(asset.path(),command.centerX(),
                        surfaceHeight-command.centerY(),command.width(),command.height(),tint,region,command.patch());
                else if(command.angleDegrees()==0) {
                    // Shared pixel edges prevent Java2D seams at fractional overview scales.
                    float left=pixelEdge(command.centerX()-command.width()/2);
                    float right=pixelEdge(command.centerX()+command.width()/2);
                    float top=pixelEdge(surfaceHeight-command.centerY()-command.height()/2);
                    float bottom=pixelEdge(surfaceHeight-command.centerY()+command.height()/2);
                    context.sprite(asset.path(),null,(left+right)/2,(top+bottom)/2,right-left,bottom-top,
                            0,tint,region,CanvasBlend.NORMAL);
                } else context.sprite(asset.path(), null, command.centerX(),
                        surfaceHeight - command.centerY(), command.width(), command.height(),
                        command.angleDegrees(), tint, region, CanvasBlend.NORMAL);
            }
            case SPRITE -> {
                HeadlessBattleSprites.Asset asset = sprites.asset(command.sprite());
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
            case CUSTOM -> {
                if (command.customDraw() instanceof WorldLabel label) drawWorldLabel(context, surfaceHeight, label);
                else if (command.customDraw() instanceof ShieldDomePass domes) drawDomes(context, viewport, domes);
                else if (!skipUnsupportedCommands) throw new IllegalStateException("Unsupported custom draw");
            }
            case RIBBON -> {
                if (!skipUnsupportedCommands) {
                    throw new IllegalStateException(
                            "Embedded headless scene emitted unsupported command "
                                    + command.kind());
                }
            }
        }
    }

    /**
     * Shield domes through {@link ShieldDomeShading}, the CPU model of their
     * shader, sampled in blocks and drawn as quads. Coarser than a full-resolution
     * raster (a block is several pixels), which is what a scene frame can afford;
     * the dome look suite renders the model per pixel.
     */
    private static void drawDomes(CanvasContext context, CanvasHostViewport viewport, ShieldDomePass pass) {
        float surfaceHeight = viewport.height();
        float[] rgba = new float[4];
        for (ShieldDomeDraw dome : pass.draws()) {
            float extent = ShieldDomeShading.EXTENT * dome.radiusPx;
            float x0 = Math.max(0f, dome.sx - extent);
            float x1 = Math.min(viewport.width(), dome.sx + extent);
            float y0 = Math.max(0f, dome.sy - extent);
            float y1 = Math.min(surfaceHeight, dome.sy + extent);
            if (x1 <= x0 || y1 <= y0) continue;
            float block = Math.max(3f, (float) Math.ceil(2f * extent / 150f));
            for (float y = y0; y < y1; y += block) {
                for (float x = x0; x < x1; x += block) {
                    float lx = (x + block / 2f - dome.sx) / dome.radiusPx;
                    float ly = (y + block / 2f - dome.sy) / dome.radiusPx;
                    if (lx * lx + ly * ly > ShieldDomeShading.EXTENT * ShieldDomeShading.EXTENT) continue;
                    ShieldDomeShading.shade(dome, lx, ly, pass.timeSeconds(), rgba);
                    float a = rgba[3];
                    if (a < 0.004f) continue;
                    Color color = new Color(clamp(rgba[0] / a), clamp(rgba[1] / a), clamp(rgba[2] / a), clamp(a));
                    float cy0 = surfaceHeight - (y + block);
                    context.fillRect(x, cy0, block, block, color);
                }
            }
        }
    }

    private static void drawWorldLabel(CanvasContext context, float height, WorldLabel label) {
        var font = label.font();
        float x = label.centerX() - font.measureWidth(label.text()) / 2;
        Color tint = new Color(label.color().getRed(), label.color().getGreen(), label.color().getBlue(),
                Math.round(label.alpha() * 255));
        for (int code : label.text().codePoints().toArray()) {
            var glyph = font.glyph(code);
            if (glyph == null) continue;
            if (glyph.w > 0 && glyph.h > 0) context.sprite(font.pagePath(), null,
                    x + glyph.xoffset + glyph.w / 2f, height - label.topY() + glyph.yoffset + glyph.h / 2f,
                    glyph.w, glyph.h, 0, tint,
                    new CanvasSpriteRegion(glyph.x / (float) font.atlasWidth(), glyph.y / (float) font.atlasHeight(),
                            glyph.w / (float) font.atlasWidth(), glyph.h / (float) font.atlasHeight()), CanvasBlend.NORMAL);
            x += glyph.xadvance;
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
        if (!RoadCatalog.installed()) RoadCatalog.loadBuiltins(path -> new JSONObject(Files.readString(modRoot.resolve(path))));
        if (!TerrainCatalog.installed()) TerrainCatalog.loadBuiltins(path -> new JSONObject(Files.readString(modRoot.resolve(path))));
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
    static float pixelEdge(float value) { return (float)Math.floor(value+.5001d); }
}
