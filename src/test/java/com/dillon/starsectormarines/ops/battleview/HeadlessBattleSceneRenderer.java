package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.render2d.texture.TextureUsage;
import com.dillon.starsectormarines.render2d.texture.TextureUsageCapture;
import com.dillon.starsectormarines.battle.air.Airframe;
import com.dillon.starsectormarines.battle.air.engine.EngineHullLighting;
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
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.DrawCommand;
import com.dillon.starsectormarines.render2d.PolyMesh;
import com.dillon.starsectormarines.render2d.QuadMesh;
import com.dillon.starsectormarines.render2d.RibbonBatch;
import com.dillon.starsectormarines.ui.retained.CanvasBlend;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasHostPass;
import com.dillon.starsectormarines.ui.retained.CanvasHostViewport;
import com.dillon.starsectormarines.ui.retained.CanvasSpriteRegion;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessHostPassRenderer;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
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
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final HeadlessUiRenderer nightRasterizer;

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
            // No line up front: each hull's sheet loads on its first draw, as the deck's do.
            sprites.ensureConvoySprites(Set.of());
            renderer = new BattleRenderer(sprites);
            this.skipUnsupportedCommands = skipUnsupportedCommands;
            nightRasterizer = new HeadlessUiRenderer(resourceRoots(modRoot), (pass, context, viewport, alpha) -> {
                if (!(pass instanceof NightRasterLayer layer)) return false;
                if (layer.mesh() != null) drawPolygon(context, viewport.height(), layer.mesh());
                else for (int i = 0; i < layer.surfaces().count(RenderLayer.FOG); i++)
                    drawCommand(context, viewport, layer.surfaces().buffer(RenderLayer.FOG)[i]);
                return true;
            });
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

    /** The shared sprite set for a mod root, with the catalogs it reads installed first, for a feed built before any frame renderer. */
    static HeadlessBattleSprites spritesFor(Path modRoot) throws Exception {
        installCatalogs(modRoot);
        return sharedSprites(modRoot);
    }

    /** The catalogs a sprite set reads, for a drain that builds a sprite set of its own. */
    static void installCatalogs(Path modRoot) throws Exception {
        HeadlessArmoryPreviewRenderer.installCatalogs(modRoot);
        installTileCatalogs(modRoot);
    }

    static HeadlessBattleSprites sharedSprites(Path modRoot) throws Exception {
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
        // The canvas sprites this drain paints are world draws, measured as such when collected.
        try (TextureUsage.Scope ignored = TextureUsage.suppressUi()) {
            return drawScene(scenePass, context, viewport, alphaMult);
        }
    }

    private boolean drawScene(BattleSceneHostPass scenePass, CanvasContext context,
                              CanvasHostViewport viewport, float alphaMult) {
        // RasterCanvasContext applies the document alpha while painting each
        // replayed primitive. Collect at full opacity so the host fade is not
        // folded into the battle commands and then multiplied a second time.
        long collectStart = System.nanoTime();
        BattleSceneFrame frame = scenePass.prepare(viewport, 1f);
        DrawList commands = renderer.collectWorld(frame.context(), frame.layers());
        if (ReviewRenderProfile.ENABLED) ReviewRenderProfile.phase("collect", System.nanoTime() - collectStart);
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
        // Presentation state the production screen feeds its services every tick (contrail samples, lingering beams,
        // delivered rounds) is not in the simulation, so a review feed owns it and hands the collected commands in.
        DrawList overlay = scenePass instanceof ReviewOverlayPass review ? review.overlay(frame.context().camera) : null;
        camera = frame.context().camera;
        // Measuring texture usage needs the collected commands, not their pixels: every draw was recorded as it
        // was collected, so the frame is left blank and the CPU raster, the slow part of a review, is skipped.
        if (TextureUsageCapture.measureOnly()) return true;
        List<DrawCommand> ordered = new ArrayList<>();
        for (RenderLayer layer : RenderLayer.values()) {
            if (!frame.layers().contains(layer)) continue;
            // Ahead of the layer's own commands: the production SHOTS order is contrails, delivered rounds and beams,
            // and only then the bodies, so a missile is drawn over its own trail.
            if (overlay != null) {
                for (int index = 0; index < overlay.count(layer); index++) ordered.add(overlay.buffer(layer)[index]);
            }
            for (int index = 0; index < commands.count(layer); index++) ordered.add(commands.buffer(layer)[index]);
        }
        // Whatever a command needs that is not a pure read is worked out here, once, on the caller's thread: the
        // drain below may run as several bands at once, and each band would otherwise repeat it.
        long prepareStart = System.nanoTime();
        Prepared prepared = prepare(ordered, context, viewport);
        long drainStart = System.nanoTime();
        if (ReviewRenderProfile.ENABLED) ReviewRenderProfile.phase("prepare", drainStart - prepareStart);
        // Always in the same bands, however many workers there are: where the bands fall is part of the picture
        // (see HeadlessUiRenderer.drawInBands), so it must not depend on the machine.
        if (!HeadlessUiRenderer.drawInBands(context, BAND_ROWS,
                (band, top, bottom) -> drain(band, viewport, ordered, prepared, top, bottom))) {
            drain(context, viewport, ordered, prepared, Integer.MIN_VALUE, Integer.MAX_VALUE);
        }
        if (ReviewRenderProfile.ENABLED) ReviewRenderProfile.phase("drain", System.nanoTime() - drainStart);
        return true;
    }

    /**
     * The custom passes' own rasters and recordings for one frame. A night shade is a whole-frame composite of three
     * renders, a hull light a whole-sprite relight, a plume list a replay of production code — none of which a band
     * should do again, and the last of which should not run twice at all.
     */
    private record Prepared(Map<Object, BufferedImage> nightShades, Map<Object, List<PlumeDraw>> plumes,
                            Map<Object, List<BufferedImage>> hullLights) { }

    private Prepared prepare(List<DrawCommand> ordered, CanvasContext context, CanvasHostViewport viewport) {
        Map<Object, BufferedImage> night = new IdentityHashMap<>();
        Map<Object, List<PlumeDraw>> plumes = new IdentityHashMap<>();
        Map<Object, List<BufferedImage>> hullLights = new IdentityHashMap<>();
        for (DrawCommand command : ordered) {
            if (command.kind() != DrawCommand.Kind.CUSTOM) continue;
            Object custom = command.customDraw();
            if (custom instanceof NightLightingPass pass && !night.containsKey(pass)) {
                night.put(pass, renderNightLighting(pass, viewport));
            } else if (custom instanceof EngineFxPass pass && !plumes.containsKey(pass)) {
                plumes.put(pass, recordPlumes(pass));
            } else if (custom instanceof EngineHullLightPass pass && !hullLights.containsKey(pass)) {
                BufferedImage source = HeadlessUiRenderer.sourceImage(context, sprites.asset(pass.hullSprite()).path());
                List<BufferedImage> overlays = new ArrayList<>(pass.lights().length);
                for (EngineHullLighting.Light light : pass.lights()) {
                    overlays.add(engineHullOverlay(source, pass.hullWidthCells(), pass.hullLengthCells(),
                            pass.pivotX(), pass.pivotY(), new EngineHullLighting.Light[]{light}));
                }
                hullLights.put(pass, overlays);
            }
        }
        return new Prepared(night, plumes, hullLights);
    }

    /**
     * Draws {@code ordered} in order, skipping a sprite whose drawn box lies wholly outside rows
     * {@code top}..{@code bottom}: it could not have written a pixel of them. The margin is wider than any edge a
     * rasteriser touches, so a skipped draw is one that would have been clipped to nothing.
     */
    private void drain(CanvasContext context, CanvasHostViewport viewport, List<DrawCommand> ordered,
                       Prepared prepared, int top, int bottom) {
        boolean banded = top != Integer.MIN_VALUE || bottom != Integer.MAX_VALUE;
        float surfaceHeight = viewport.height();
        for (DrawCommand command : ordered) {
            if (banded && (command.kind() == DrawCommand.Kind.SHEET_QUAD || command.kind() == DrawCommand.Kind.SPRITE)) {
                float centerY = surfaceHeight - command.centerY();
                float reach = command.angleDegrees() == 0f ? Math.abs(command.height()) / 2f
                        : (float) Math.hypot(command.width(), command.height()) / 2f;
                if (centerY + reach + CULL_MARGIN < top || centerY - reach - CULL_MARGIN > bottom) continue;
            }
            if (!ReviewRenderProfile.ENABLED) {
                drawCommand(context, viewport, command, prepared, top, bottom);
                continue;
            }
            long start = System.nanoTime();
            drawCommand(context, viewport, command, prepared, top, bottom);
            ReviewRenderProfile.draw(profileKey(command), System.nanoTime() - start);
        }
    }

    /**
     * Rows per band of a drained frame: 34 bands on a 1080-row frame, enough for a busy band (the rows a hull
     * crosses) not to leave the other workers idle.
     * {@code -Dsnapshot.bandRows=0} drains every frame whole, the control for what the bands change.
     */
    private static final int BAND_ROWS = Integer.getInteger("snapshot.bandRows", 32);

    private String profileKey(DrawCommand command) {
        String what = switch (command.kind()) {
            case SHEET_QUAD, SPRITE -> sprites.asset(command.sprite()).path()
                    + (command.angleDegrees() == 0f ? " axis" : " turned") + (command.additive() ? " additive" : "");
            case CUSTOM -> command.customDraw() == null ? "null" : command.customDraw().getClass().getSimpleName();
            default -> "";
        };
        return command.kind() + " " + what;
    }

    /** Pixels of slack around a culled draw's box: pixel-edge snapping moves an edge by at most one. */
    private static final float CULL_MARGIN = 3f;

    /**
     * A review pass that brings commands of its own into the frame: the part of the production presentation that
     * lives in services the screen ticks rather than in the simulation.
     */
    interface ReviewOverlayPass {
        /** The commands to draw alongside the production collection, projected through {@code camera}. */
        DrawList overlay(BattleCamera camera);
    }

    /** The camera of the frame being drained, which a ribbon needs to expand its samples into screen space. */
    private BattleCamera camera;

    private void drawCommand(CanvasContext context, CanvasHostViewport viewport,
                             DrawCommand command) {
        drawCommand(context, viewport, command, null, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    private void drawCommand(CanvasContext context, CanvasHostViewport viewport,
                             DrawCommand command, Prepared prepared, int bandTop, int bandBottom) {
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
                else if (command.customDraw() instanceof ShieldDomePass domes) drawDomes(context, viewport, domes, bandTop, bandBottom);
                else if (command.customDraw() instanceof CliffArtPass cliffs) drawCliffArt(context, viewport, cliffs, bandTop, bandBottom);
                else if (command.customDraw() instanceof CreepPass creep) drawCreep(context, viewport, creep, bandTop, bandBottom);
                else if (command.customDraw() instanceof ScorchPass scorch) drawScorch(context, viewport, scorch, bandTop, bandBottom);
                else if (command.customDraw() instanceof NightLightingPass night) drawNightLighting(context, viewport, night, prepared, bandTop, bandBottom);
                else if (command.customDraw() instanceof EngineFxPass engine) drawEngineFx(context, viewport, engine, prepared);
                else if (command.customDraw() instanceof EngineHullLightPass light) drawEngineHullLight(context, viewport, light, prepared);
                // A bare lambda is GL housekeeping (the doodad atlas building or releasing its texture): it draws nothing,
                // and leaving it unrun keeps the atlas unserving, so doodads keep drawing from their sheets here.
                else if (command.customDraw().getClass().isHidden()) { }
                else if (!skipUnsupportedCommands) throw new IllegalStateException("Unsupported custom draw: " + command.customDraw().getClass().getName());
            }
            case RIBBON -> drawRibbon(context, viewport.height(), command);
            case MESH -> drawMesh(context, surfaceHeight, command, bandTop, bandBottom);
        }
    }

    /**
     * A recorded mesh, quad by quad, each the whole-image draw it was recorded from, with the band cull a sprite
     * gets. A mesh addressed into an atlas never reaches a headless drain, which has none.
     */
    private void drawMesh(CanvasContext context, float surfaceHeight, DrawCommand command, int top, int bottom) {
        QuadMesh mesh = command.mesh();
        if (mesh == null) return;
        boolean banded = top != Integer.MIN_VALUE || bottom != Integer.MAX_VALUE;
        float scale = command.meshScale();
        for (int q = 0, n = mesh.size(); q < n; q++) {
            float cx = command.centerX() + mesh.centerX(q) * scale;
            float cy = surfaceHeight - (command.centerY() + mesh.centerY(q) * scale);
            float w = mesh.width(q) * scale, h = mesh.height(q) * scale;
            float angle = mesh.angleDegrees(q);
            if (banded) {
                float reach = angle == 0f ? Math.abs(h) / 2f : (float) Math.hypot(w, h) / 2f;
                if (cy + reach + CULL_MARGIN < top || cy - reach - CULL_MARGIN > bottom) continue;
            }
            Color tint = new Color(clamp(mesh.red(q)), clamp(mesh.green(q)), clamp(mesh.blue(q)),
                    clamp(mesh.alpha(q) * command.alpha()));
            context.sprite(sprites.asset(mesh.image(q)).path(), null, cx, cy, w, h, angle, tint,
                    CanvasSpriteRegion.FULL, CanvasBlend.NORMAL);
        }
    }

    /**
     * A contrail through the production {@link RibbonBatch} geometry, drawn a segment at a time in the colour its
     * corners average to. A segment is a few pixels long at any framing this review uses, so the flat fill costs
     * the age gradient nothing a reader can see. Additive plumes are drawn as ordinary blends.
     */
    private void drawRibbon(CanvasContext context, float surfaceHeight, DrawCommand command) {
        if (camera == null || command.trail() == null) return;
        RibbonBatch batch = new RibbonBatch(Math.max(1, command.trail().size()));
        batch.append(command.trail(), camera, command.alpha());
        for (int quad = 0; quad < batch.quadCount(); quad++) {
            float red = 0f, green = 0f, blue = 0f, alpha = 0f;
            for (int vertex = 0; vertex < 4; vertex++) {
                red += batch.vertexChannel(quad, vertex, 0) / 4f;
                green += batch.vertexChannel(quad, vertex, 1) / 4f;
                blue += batch.vertexChannel(quad, vertex, 2) / 4f;
                alpha += batch.vertexChannel(quad, vertex, 3) / 4f;
            }
            if (alpha <= 0.004f) continue;
            context.fillQuad(
                    batch.vertexX(quad, 0), surfaceHeight - batch.vertexY(quad, 0),
                    batch.vertexX(quad, 1), surfaceHeight - batch.vertexY(quad, 1),
                    batch.vertexX(quad, 2), surfaceHeight - batch.vertexY(quad, 2),
                    batch.vertexX(quad, 3), surfaceHeight - batch.vertexY(quad, 3),
                    new Color(clamp(red), clamp(green), clamp(blue), clamp(alpha)));
        }
    }

    /** Material-clipped reflected engine light, added one nozzle at a time just like the live shader. */
    private void drawEngineHullLight(CanvasContext context, CanvasHostViewport viewport, EngineHullLightPass pass,
                                     Prepared prepared) {
        List<BufferedImage> overlays = prepared == null ? null : prepared.hullLights().get(pass);
        if (overlays != null) {
            for (BufferedImage overlay : overlays) {
                HeadlessUiRenderer.additiveImage(context, overlay, pass.centerX(), viewport.height() - pass.centerY(),
                        pass.widthPx(), pass.heightPx(), pass.angleDegrees(), pass.alphaMult());
            }
            return;
        }
        String path = sprites.asset(pass.hullSprite()).path();
        BufferedImage source = HeadlessUiRenderer.sourceImage(context, path);
        drawEngineHullLights(context, source, pass.centerX(), viewport.height()-pass.centerY(),
                pass.widthPx(), pass.heightPx(), pass.angleDegrees(), pass.hullWidthCells(),
                pass.hullLengthCells(), pass.pivotX(), pass.pivotY(), pass.alphaMult(), pass.lights());
    }

    static void drawEngineHullLights(CanvasContext context, BufferedImage source, float centerX, float centerY,
                                     float widthPx, float heightPx, float angle, float widthCells, float lengthCells,
                                     float pivotX, float pivotY, float alpha, EngineHullLighting.Light[] lights) {
        for (EngineHullLighting.Light light : lights) {
            BufferedImage overlay = engineHullOverlay(source, widthCells, lengthCells, pivotX, pivotY,
                    new EngineHullLighting.Light[]{light});
            HeadlessUiRenderer.additiveImage(context, overlay, centerX, centerY, widthPx, heightPx, angle, alpha);
        }
    }

    /** Logical UVs are bottom-up; source rasters are top-down. Hull coverage remains unmodified. */
    static BufferedImage engineHullOverlay(BufferedImage source, float widthCells, float lengthCells,
                                           float pivotX, float pivotY, EngineHullLighting.Light[] lights) {
        int width=source.getWidth(), height=source.getHeight();
        BufferedImage result=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        float[] rgb=new float[3];
        for(int y=0;y<height;y++)for(int x=0;x<width;x++) {
            int pixel=source.getRGB(x,y);
            if((pixel>>>24)==0) continue;
            float localX=((x+.5f)/width-.5f)*widthCells+pivotX;
            float localY=(.5f-(y+.5f)/height)*lengthCells+pivotY;
            EngineHullLighting.sample(lights,localX,localY,rgb);
            int red=Math.min(255,Math.max(0,Math.round((pixel>>>16&255)*rgb[0])));
            int green=Math.min(255,Math.max(0,Math.round((pixel>>>8&255)*rgb[1])));
            int blue=Math.min(255,Math.max(0,Math.round((pixel&255)*rgb[2])));
            result.setRGB(x,y,(pixel&0xff000000)|(red<<16)|(green<<8)|blue);
        }
        return result;
    }

    /**
     * Aircraft engine plumes: the production {@link EngineFxPass} run against sprites that remember each additive
     * glow and flame it is asked to draw, replayed here through the canvas.
     */
    private void drawEngineFx(CanvasContext context, CanvasHostViewport viewport, EngineFxPass pass,
                              Prepared prepared) {
        List<PlumeDraw> draws = prepared == null ? null : prepared.plumes().get(pass);
        if (draws == null) draws = recordPlumes(pass);
        for (PlumeDraw draw : draws) {
            context.sprite(draw.path(), null, draw.x(), viewport.height() - draw.y(), draw.width(), draw.height(),
                    draw.angle(), draw.tint(), CanvasSpriteRegion.FULL, CanvasBlend.ADDITIVE);
        }
    }

    /** The plumes {@code pass} asks for, recorded rather than drawn; empty when it has no sprites. */
    private List<PlumeDraw> recordPlumes(EngineFxPass pass) {
        List<PlumeDraw> draws = new ArrayList<>();
        if (pass.glowSprite() == null && pass.flameSprite() == null) return draws;
        pass.draw(recordingSprite(pass.glowSprite(), draws), recordingSprite(pass.flameSprite(), draws));
        return draws;
    }

    private record PlumeDraw(String path, float x, float y, float width, float height, float angle, Color tint) { }

    private SpriteAPI recordingSprite(SpriteAPI token, List<PlumeDraw> into) {
        if (token == null) return null;
        String path = sprites.asset(token).path();
        float[] size = new float[2];
        float[] angle = new float[1];
        float[] alpha = {1f};
        Color[] color = {Color.WHITE};
        return (SpriteAPI) Proxy.newProxyInstance(SpriteAPI.class.getClassLoader(), new Class<?>[]{SpriteAPI.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "setSize" -> { size[0] = (Float) args[0]; size[1] = (Float) args[1]; }
                        case "setAngle" -> angle[0] = (Float) args[0];
                        case "setAlphaMult" -> alpha[0] = (Float) args[0];
                        case "setColor" -> color[0] = (Color) args[0];
                        case "renderAtCenter" -> into.add(new PlumeDraw(path, (Float) args[0], (Float) args[1],
                                size[0], size[1], angle[0], new Color(color[0].getRed() / 255f,
                                color[0].getGreen() / 255f, color[0].getBlue() / 255f, clamp(alpha[0]))));
                        default -> { }
                    }
                    return HeadlessBattleSprites.primitiveDefault(method.getReturnType());
                });
    }

    /** Exact sprite-alpha coverage and premultiplied shade mixing, matching the night GPU pass. */
    BufferedImage renderNightLighting(NightLightingPass pass, CanvasHostViewport viewport) {
        int width = Math.max(1, Math.round(viewport.width()));
        int height = Math.max(1, Math.round(viewport.height()));
        BufferedImage fog = nightRasterizer.renderHostPass(new NightRasterLayer(pass.fogShade(), null), width, height);
        BufferedImage lit = nightRasterizer.renderHostPass(new NightRasterLayer(pass.litShade(), null), width, height);
        BufferedImage coverage = nightRasterizer.renderHostPass(new NightRasterLayer(null, pass.surfaces()), width, height);
        return compositeNightLighting(fog, lit, coverage);
    }

    static BufferedImage compositeNightLighting(BufferedImage fog, BufferedImage lit, BufferedImage coverage) {
        int width = fog.getWidth(), height = fog.getHeight();
        if (lit.getWidth() != width || lit.getHeight() != height
                || coverage.getWidth() != width || coverage.getHeight() != height)
            throw new IllegalArgumentException("Night shade and coverage dimensions must agree");
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int dark = fog.getRGB(x, y), light = lit.getRGB(x, y);
            int mask = coverage.getRGB(x, y) >>> 24;
            if (mask == 0) { result.setRGB(x, y, dark); continue; }
            if (mask == 255) { result.setRGB(x, y, light); continue; }
            float weight = mask / 255f;
            float darkWeight = (dark >>> 24) * (1 - weight);
            float lightWeight = (light >>> 24) * weight;
            float alpha = darkWeight + lightWeight;
            if (alpha <= 0f) continue;
            int mixed = Math.round(alpha) << 24;
            for (int shift = 0; shift <= 16; shift += 8) {
                int channel = Math.round((((dark >>> shift) & 255) * darkWeight
                        + ((light >>> shift) & 255) * lightWeight) / alpha);
                mixed |= channel << shift;
            }
            result.setRGB(x, y, mixed);
        }
        return result;
    }

    private void drawNightLighting(CanvasContext context, CanvasHostViewport viewport, NightLightingPass pass,
                                   Prepared prepared, int top, int bottom) {
        BufferedImage shade = prepared == null ? null : prepared.nightShades().get(pass);
        if (shade == null) shade = renderNightLighting(pass, viewport);
        // Equal adjacent pixels share a fill; transparent gaps never get a lighting rectangle. A band fills only its
        // own rows, which are the only ones its clip would let through.
        for (int y = Math.max(0, top); y < Math.min(shade.getHeight(), bottom); y++) {
            int start = 0, color = shade.getRGB(0, y);
            for (int x = 1; x <= shade.getWidth(); x++) {
                int next = x == shade.getWidth() ? 0 : shade.getRGB(x, y);
                if (x < shade.getWidth() && next == color) continue;
                if ((color >>> 24) != 0) context.fillRect(start, y, x - start, 1, new Color(color, true));
                start = x;
                color = next;
            }
        }
    }

    private record NightRasterLayer(PolyMesh mesh, DrawList surfaces) implements CanvasHostPass {
        public void draw(CanvasHostViewport viewport, float alpha) {
            throw new UnsupportedOperationException("Headless night intermediate only");
        }
    }

    /**
     * Creep through {@link CreepShading}, the CPU model of its shader, over the layer's mirror of the field: one
     * sample per pixel, because the edge is hard and a coarser block would step it. Equal neighbours in a row share
     * one fill.
     */
    /**
     * Baked cliffs, one bilinear sample of the bake per pixel, laid over the
     * ground as the live pass's premultiplied blend does.
     */
    private static void drawCliffArt(CanvasContext context, CanvasHostViewport viewport, CliffArtPass pass, int top, int bottom) {
        float surfaceHeight = viewport.height();
        float sx0 = pass.cellToScreenX(pass.minChunkX * CliffArtLayer.CHUNK);
        float sx1 = pass.cellToScreenX((pass.maxChunkX + 1) * CliffArtLayer.CHUNK);
        float gy0 = pass.cellToScreenY(pass.minChunkY * CliffArtLayer.CHUNK);
        float gy1 = pass.cellToScreenY((pass.maxChunkY + 1) * CliffArtLayer.CHUNK);
        int px0 = Math.max(0, (int) Math.floor(Math.min(sx0, sx1)));
        int px1 = Math.min((int) Math.ceil(viewport.width()), (int) Math.ceil(Math.max(sx0, sx1)));
        int row0 = Math.max(Math.max(0, top), (int) Math.floor(surfaceHeight - Math.max(gy0, gy1)));
        int row1 = Math.min(Math.min((int) Math.ceil(surfaceHeight), bottom), (int) Math.ceil(surfaceHeight - Math.min(gy0, gy1)));
        if (px1 <= px0 || row1 <= row0) return;
        BufferedImage overlay = new BufferedImage(px1 - px0, row1 - row0, BufferedImage.TYPE_INT_ARGB);
        float[] rgba = new float[4];
        boolean any = false;
        for (int y = row0; y < row1; y++) {
            float cellY = pass.screenToCellY(surfaceHeight - (y + 0.5f));
            for (int x = px0; x < px1; x++) {
                if (!pass.layer().sample(pass.screenToCellX(x + 0.5f), cellY, rgba)) continue;
                float a = rgba[3] * pass.alpha;
                if (a <= 0f) continue;
                any = true;
                // Straight alpha for an ordinary over: the bake is premultiplied.
                float inv = 1f / rgba[3];
                overlay.setRGB(x - px0, y - row0, Math.round(clamp(a) * 255f) << 24 | channel(rgba[0] * inv) << 16
                        | channel(rgba[1] * inv) << 8 | channel(rgba[2] * inv));
            }
        }
        if (any) HeadlessUiRenderer.blendImage(context, overlay, px0, row0, false);
    }

    private static void drawCreep(CanvasContext context, CanvasHostViewport viewport, CreepPass pass, int top, int bottom) {
        float surfaceHeight = viewport.height();
        float sx0 = pass.cellToScreenX(pass.minX), sx1 = pass.cellToScreenX(pass.maxX + 1);
        float gy0 = pass.cellToScreenY(pass.minY), gy1 = pass.cellToScreenY(pass.maxY + 1);
        int px0 = Math.max(0, (int) Math.floor(Math.min(sx0, sx1)));
        int px1 = Math.min((int) Math.ceil(viewport.width()), (int) Math.ceil(Math.max(sx0, sx1)));
        int row0 = Math.max(Math.max(0, top), (int) Math.floor(surfaceHeight - Math.max(gy0, gy1)));
        int row1 = Math.min(Math.min((int) Math.ceil(surfaceHeight), bottom), (int) Math.ceil(surfaceHeight - Math.min(gy0, gy1)));
        CreepLayer layer = pass.layer();
        CreepShading.Scratch scratch = new CreepShading.Scratch();
        float[] rgba = new float[4];
        for (int y = row0; y < row1; y++) {
            float cellY = pass.screenToCellY(surfaceHeight - (y + 0.5f));
            int runStart = px0, runColor = 0;
            for (int x = px0; x < px1; x++) {
                float cellX = pass.screenToCellX(x + 0.5f);
                int color = 0;
                if (layer.coverageNear(cellX, cellY)) {
                    CreepShading.shade(layer, cellX, cellY, pass.cellPx, pass.timeSeconds, pass.palette, pass.sun,
                            scratch, rgba);
                    color = argb(rgba, pass.alpha);
                }
                if (color == runColor) continue;
                if ((runColor >>> 24) != 0) context.fillRect(runStart, y, x - runStart, 1, new Color(runColor, true));
                runStart = x;
                runColor = color;
            }
            if ((runColor >>> 24) != 0) context.fillRect(runStart, y, px1 - runStart, 1, new Color(runColor, true));
        }
    }

    /**
     * Burnt ground through {@link ScorchShading}, one sample per pixel: the multiplier darkens the ground already
     * drawn (a multiply blend, as the live pass does) and the overlay lays ash and crust over it.
     */
    private static void drawScorch(CanvasContext context, CanvasHostViewport viewport, ScorchPass pass, int top, int bottom) {
        float surfaceHeight = viewport.height();
        float sx0 = pass.cellToScreenX(pass.minX), sx1 = pass.cellToScreenX(pass.maxX + 1);
        float gy0 = pass.cellToScreenY(pass.minY), gy1 = pass.cellToScreenY(pass.maxY + 1);
        int px0 = Math.max(0, (int) Math.floor(Math.min(sx0, sx1)));
        int px1 = Math.min((int) Math.ceil(viewport.width()), (int) Math.ceil(Math.max(sx0, sx1)));
        int row0 = Math.max(Math.max(0, top), (int) Math.floor(surfaceHeight - Math.max(gy0, gy1)));
        int row1 = Math.min(Math.min((int) Math.ceil(surfaceHeight), bottom), (int) Math.ceil(surfaceHeight - Math.min(gy0, gy1)));
        if (px1 <= px0 || row1 <= row0) return;
        int w = px1 - px0, h = row1 - row0;
        BufferedImage multiply = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        BufferedImage overlay = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        ScorchShading.Scratch scratch = new ScorchShading.Scratch();
        float[] out = new float[7];
        boolean any = false;
        for (int y = row0; y < row1; y++) {
            float cellY = pass.screenToCellY(surfaceHeight - (y + 0.5f));
            for (int x = px0; x < px1; x++) {
                float cellX = pass.screenToCellX(x + 0.5f);
                if (!ScorchShading.shade(pass.layer(), cellX, cellY, scratch, out)) continue;
                any = true;
                multiply.setRGB(x - px0, y - row0, 0xFF000000 | channel(out[0]) << 16 | channel(out[1]) << 8 | channel(out[2]));
                int oa = Math.round(clamp(out[6] * pass.alpha) * 255f);
                if (oa > 0) overlay.setRGB(x - px0, y - row0, oa << 24 | channel(out[3]) << 16 | channel(out[4]) << 8 | channel(out[5]));
            }
        }
        if (!any) return;
        HeadlessUiRenderer.blendImage(context, multiply, px0, row0, true);
        HeadlessUiRenderer.blendImage(context, overlay, px0, row0, false);
    }

    private static int channel(float v) { return Math.round(clamp(v) * 255f); }

    private static int argb(float[] rgba, float alphaMult) {
        int a = Math.round(clamp(rgba[3] * alphaMult) * 255f) & ~3;
        if (a == 0) return 0;
        int r = Math.round(clamp(rgba[0]) * 255f) & ~3, g = Math.round(clamp(rgba[1]) * 255f) & ~3, b = Math.round(clamp(rgba[2]) * 255f) & ~3;
        return a << 24 | r << 16 | g << 8 | b;
    }

    /**
     * Shield domes through {@link ShieldDomeShading}, the CPU model of their
     * shader, sampled in blocks and drawn as quads. Coarser than a full-resolution
     * raster (a block is several pixels), which is what a scene frame can afford;
     * the dome look suite renders the model per pixel.
     */
    private static void drawDomes(CanvasContext context, CanvasHostViewport viewport, ShieldDomePass pass,
                                  int top, int bottom) {
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
                float rowTop = surfaceHeight - (y + block);
                if (rowTop + block < (float) top - 1f || rowTop > (float) bottom + 1f) continue;
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
        float left = label.centerX() - label.font().measureWidth(label.text()) / 2;
        if (label.halo() != null) {
            for (float[] offset : WorldLabel.HALO_OFFSETS)
                drawWorldLabelGlyphs(context, height, label, left + offset[0], label.topY() + offset[1], label.halo());
        }
        drawWorldLabelGlyphs(context, height, label, left, label.topY(), label.color());
    }

    private static void drawWorldLabelGlyphs(CanvasContext context, float height, WorldLabel label,
                                             float x, float topY, Color color) {
        var font = label.font();
        Color tint = new Color(color.getRed(), color.getGreen(), color.getBlue(),
                Math.round(label.alpha() * color.getAlpha()));
        for (int code : label.text().codePoints().toArray()) {
            var glyph = font.glyph(code);
            if (glyph == null) continue;
            if (glyph.w > 0 && glyph.h > 0) context.sprite(font.pagePath(), null,
                    x + glyph.xoffset + glyph.w / 2f, height - topY + glyph.yoffset + glyph.h / 2f,
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
