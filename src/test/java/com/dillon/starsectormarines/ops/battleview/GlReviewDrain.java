package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.testsupport.GlSurface;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import org.lwjgl.BufferUtils;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.lang.reflect.Proxy;
import java.nio.IntBuffer;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_MODELVIEW;
import static org.lwjgl.opengl.GL11.GL_PACK_ALIGNMENT;
import static org.lwjgl.opengl.GL11.GL_PROJECTION;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glLoadIdentity;
import static org.lwjgl.opengl.GL11.glMatrixMode;
import static org.lwjgl.opengl.GL11.glOrtho;
import static org.lwjgl.opengl.GL11.glPixelStorei;
import static org.lwjgl.opengl.GL11.glReadPixels;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL12.GL_BGRA;
import static org.lwjgl.opengl.GL12.GL_UNSIGNED_INT_8_8_8_8_REV;

/**
 * The review frame's scene drawn by the GPU: the shipping {@link BattleRenderer} collect and drain into a
 * {@link GlSurface} (an LWJGL 2 pbuffer, or LWJGL 3 on the Mac), read back as a {@link BufferedImage} for the CPU to caption and annotate.
 *
 * <p>Opt-in with {@code -Dsnapshot.backend=gl} ({@link ReviewSceneBackend}). Where no context can be made it says so once and every frame falls
 * back to the Java2D drain, which stays the deterministic reference: a GL frame is a function of the driver as
 * well as the battle, so two machines may differ in the last bit and a raw-frame comparison is a one-machine A/B.
 *
 * <p><b>One thread owns the context.</b> A GL context is current on exactly one thread, and a suite may render
 * from whichever thread it plays on, so every frame is handed to a single daemon thread and the caller waits for
 * the picture. The simulation is not advanced while it waits, so the collect reads a still world.
 */
final class GlReviewDrain implements ReviewSceneBackend {

    /**
     * The surface-relief composite (sun shadows, relief, event lights) is on unless {@code -Dsnapshot.gl.relief=false},
     * which draws the plain observer scene: the control for what the composite costs and changes.
     */
    private static final BattleRenderHostProfile PROFILE =
            Boolean.parseBoolean(System.getProperty("snapshot.gl.relief", "true"))
                    ? BattleRenderHostProfile.OBSERVER_GL : BattleRenderHostProfile.OBSERVER_SCENE;

    private static final Object LOCK = new Object();
    private static GlReviewDrain shared;
    private static boolean tried;

    private final ExecutorService thread = Executors.newSingleThreadExecutor(runnable -> {
        Thread owner = new Thread(runnable, "gl-review-drain");
        owner.setDaemon(true);
        return owner;
    });
    private final Path modRoot;
    private GlSurface gl;
    private int surfaceW;
    private int surfaceH;
    private GlSpriteTokens tokens;
    private HeadlessBattleSprites sprites;
    /**
     * One renderer per battle, as the live screen has: a renderer bakes resident ground, roofs, fog and a decoration
     * atlas for the map it last drew, so suites that take turns between battles would rebuild them every frame.
     * Least recently drawn first; the oldest is released the way {@code BattleScreen.detach} releases its own.
     */
    private final Map<BattleSimulation, BattleRenderer> renderers = new LinkedHashMap<>(8, 0.75f, true);
    /**
     * Above the most battles any suite draws in turn: `aircraft-crash` draws six side by side every frame, and at a
     * cap of four every frame evicted one and rebuilt its atlases, which cost a hundred milliseconds a view.
     */
    private static final int MAX_RENDERERS = 16;
    private int frameW;
    private int frameH;
    private IntBuffer readBuffer;
    private final SettingsAPI settings = screenSettings();

    private GlReviewDrain(Path modRoot) {
        this.modRoot = modRoot;
    }

    /** The drain for this process, or null when this machine cannot give a context. */
    static GlReviewDrain shared(Path modRoot) {
        synchronized (LOCK) {
            if (tried) return shared;
            tried = true;
            GlReviewDrain drain = new GlReviewDrain(modRoot.toAbsolutePath().normalize());
            try {
                String renderer = drain.call(() -> drain.start(1920, 1080));
                if (renderer == null) {
                    System.out.println("[gl-review] falling back to the Java2D drain: " + GlSurface.unavailableReason());
                    drain.thread.shutdown();
                    return null;
                }
                System.out.println("[gl-review] drawing review frames on " + renderer);
                shared = drain;
            } catch (RuntimeException failure) {
                System.out.println("[gl-review] falling back to the Java2D drain: " + failure);
                drain.thread.shutdown();
            }
            return shared;
        }
    }

    /** The sprite set whose tokens bind in this context, for a feed whose commands this drain will draw. */
    HeadlessBattleSprites sprites() {
        return sprites;
    }

    @Override
    public BufferedImage draw(Scene scene, int width, int height) {
        // Collected on the caller's thread: the feed's services are not the drain's to touch.
        DrawList leading = scene.impacts() == null ? null : scene.impacts().overlay(scene.camera());
        long queued = System.nanoTime();
        return call(() -> {
            if (ReviewRenderProfile.ENABLED) ReviewRenderProfile.phase("gl handoff wait", System.nanoTime() - queued);
            ensureSurface(width, height);
            long start = System.nanoTime();
            frameW = width;
            frameH = height;
            glViewport(0, 0, width, height);
            glMatrixMode(GL_PROJECTION);
            glLoadIdentity();
            glOrtho(0, width, 0, height, -1, 1);
            glMatrixMode(GL_MODELVIEW);
            glLoadIdentity();
            glClearColor(0f, 0f, 0f, 1f);
            glClear(GL_COLOR_BUFFER_BIT);
            RenderContext context = new RenderContext(scene.simulation(), scene.camera(), null, 1f, 0f, false,
                    null, null, PROFILE, scene.clockSeconds());
            // The suite's own code may install settings of its own between frames, and the composite reads the
            // screen off whatever is installed, so the drain's are in place only while it draws.
            SettingsAPI previous = Global.getSettings();
            Global.setSettings(settings);
            try {
                BattleRenderer renderer = rendererFor(scene.simulation());
                renderer.useGroundLights(scene.impacts() == null ? null : scene.impacts().lights());
                renderer.renderWorld(context, scene.layers(), null, leading);
            } finally {
                Global.setSettings(previous);
            }
            long drawn = System.nanoTime();
            BufferedImage image = readBack(width, height);
            if (ReviewRenderProfile.ENABLED) {
                ReviewRenderProfile.phase("gl collect+drain", drawn - start);
                ReviewRenderProfile.phase("gl readback", System.nanoTime() - drawn);
            }
            return image;
        });
    }

    private String start(int width, int height) throws Exception {
        gl = GlSurface.createOrNull(width, height);
        if (gl == null) return null;
        surfaceW = width;
        surfaceH = height;
        tokens = new GlSpriteTokens();
        long started = System.nanoTime();
        HeadlessBattleSceneRenderer.installCatalogs(modRoot);
        sprites = new HeadlessBattleSprites(modRoot, tokens);
        sprites.ensureUnitSheets();
        sprites.ensureEngineFxSprites();
        if (ReviewRenderProfile.ENABLED) ReviewRenderProfile.phase("gl context and sprite set (once)", System.nanoTime() - started);
        return gl.rendererDescription();
    }

    /** Called with the drain's settings installed. */
    private BattleRenderer rendererFor(BattleSimulation simulation) {
        BattleRenderer existing = renderers.get(simulation);
        if (existing != null) return existing;
        if (renderers.size() >= MAX_RENDERERS) {
            Iterator<Map.Entry<BattleSimulation, BattleRenderer>> oldest = renderers.entrySet().iterator();
            release(oldest.next().getValue());
            oldest.remove();
        }
        long started = System.nanoTime();
        BattleRenderer renderer = new BattleRenderer(sprites);
        renderer.onAttach();
        renderer.buildTileBatches();
        renderers.put(simulation, renderer);
        if (ReviewRenderProfile.ENABLED) ReviewRenderProfile.phase("gl renderer setup (per battle)", System.nanoTime() - started);
        return renderer;
    }

    private static void release(BattleRenderer renderer) {
        renderer.getDecalAccumulator().dispose();
        renderer.getGroundParallax().dispose();
        renderer.getGroundMesh().dispose();
        renderer.getRoofMesh().dispose();
        renderer.getFogField().dispose();
        renderer.disposeLighting();
        renderer.getGroundAtlas().dispose();
        renderer.getUnitAtlas().dispose();
        renderer.disposeDecorationAtlas();
    }

    /**
     * A pbuffer is fixed at the size it was made, so a frame larger than the surface makes a bigger one and starts
     * again: textures belong to the context, and so does everything the renderer uploaded.
     */
    private void ensureSurface(int width, int height) throws Exception {
        if (width <= surfaceW && height <= surfaceH) return;
        renderers.clear();   // their GL names die with the context
        tokens.close();
        gl.close();
        if (start(Math.max(width, surfaceW), Math.max(height, surfaceH)) == null) {
            throw new IllegalStateException("could not make a " + width + "x" + height + " GL surface");
        }
    }

    /** GL rows run bottom up; the packed BGRA read lands straight in the image's own ints. */
    private BufferedImage readBack(int width, int height) {
        int pixels = width * height;
        if (readBuffer == null || readBuffer.capacity() < pixels) readBuffer = BufferUtils.createIntBuffer(pixels);
        readBuffer.clear();
        glPixelStorei(GL_PACK_ALIGNMENT, 4);
        glReadPixels(0, 0, width, height, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, readBuffer);
        // ARGB, as the Java2D drain returns: the particle painter and the frame store take their fast paths on it.
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] target = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        for (int y = 0; y < height; y++) {
            readBuffer.position((height - 1 - y) * width);
            readBuffer.get(target, y * width, width);
        }
        // The framebuffer's alpha is whatever the blends left in it; the frame is a picture, so it is opaque.
        for (int i = 0; i < target.length; i++) target[i] |= 0xFF000000;
        return image;
    }

    /**
     * The screen the ground composite sizes its targets from: the frame being drawn, at a scale of one. Everything
     * else answers as it does with no game at all.
     */
    private SettingsAPI screenSettings() {
        return (SettingsAPI) Proxy.newProxyInstance(
                SettingsAPI.class.getClassLoader(), new Class<?>[]{SettingsAPI.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getScreenWidth" -> (float) (frameW == 0 ? surfaceW : frameW);
                    case "getScreenHeight" -> (float) (frameH == 0 ? surfaceH : frameH);
                    case "getScreenScaleMult" -> 1f;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "GlReviewSettings";
                    default -> HeadlessBattleSprites.primitiveDefault(method.getReturnType());
                });
    }

    private <T> T call(Callable<T> work) {
        Future<T> result = thread.submit(work);
        try {
            return result.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted waiting for the GL review drain", interrupted);
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("GL review drain failed", cause);
        }
    }
}
