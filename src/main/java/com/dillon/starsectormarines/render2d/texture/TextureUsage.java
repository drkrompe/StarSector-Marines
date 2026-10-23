package com.dillon.starsectormarines.render2d.texture;

import com.fs.starfarer.api.graphics.SpriteAPI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * How large every texture is actually drawn: the measurement the release build fits shipped
 * images to (see {@code texture-fit.md}).
 *
 * <p>For each image path it keeps the largest size the <em>whole image</em> reaches on screen, in
 * screen pixels, as if the frame were shown at the reference conditions: a 1920x1080 screen, the
 * closest battle zoom and the largest UI scale the game offers. A draw is measured where it is
 * made, in whatever framing it happened to be made, and scaled to those conditions here, so a
 * whole-map review frame and a close-up in the live game measure the same art the same way. The
 * fit then needs only {@code display / sourcePixels} to know how many source pixels earn their
 * place.
 *
 * <p>Two domains, because they scale differently:
 * <ul>
 *   <li><b>World</b> draws, collected by {@code BattleRenderer} into the {@code DrawList}. A world
 *   draw's size follows the camera, and the closest the camera gets is
 *   {@code BattleCamera.MAX_ZOOM} times the cover-fitted cell of that map on a 1920x1080 screen
 *   (independent of the UI scale, because the battle canvas is fitted to the physical screen).
 *   {@link #beginWorld} records the frame's cell size and map, and each draw is scaled by
 *   {@code referenceCell / frameCell}.</li>
 *   <li><b>UI</b> draws, every image and sprite a retained canvas paints. Their size is in
 *   document units, which are UI units on a 1920x1080 screen at any UI scale, so the reference
 *   is the document size times {@link #MAX_UI_SCALE}.</li>
 * </ul>
 *
 * <p>Off unless {@link #enable} is called: in the game only under {@code DevConfig.DEV_MODE}, in
 * the headless evidence only when the run asks for it. Disabled, every hook is one volatile read.
 * Each recording session labels itself ({@link #source}); the record keeps every label's maximum
 * separately, so a context that draws art bigger than play ever does (a scene on a toy grid) can
 * be seen, and left out of the fit, rather than silently deciding it.
 */
public final class TextureUsage {

    public static final float REFERENCE_WIDTH = 1920f;
    public static final float REFERENCE_HEIGHT = 1080f;
    /** {@code BattleCamera.MAX_ZOOM}, repeated so this class reads no camera. */
    public static final float MAX_BATTLE_ZOOM = 16f;
    /**
     * The largest UI scale assumed. Starsector's screen-scaling choices top out at 200%; if the
     * game offers less at 1920x1080 this over-measures UI art, which keeps it larger, never smaller.
     */
    public static final float MAX_UI_SCALE = 2f;
    /**
     * The smallest real battle map, a ship deck. A grid smaller than this (a behaviour scene's toy
     * ground) is measured as if it were this size, or its closest zoom would ask for more than
     * any played map can show.
     */
    public static final int MIN_PLAY_MAP_WIDTH = 112;
    public static final int MIN_PLAY_MAP_HEIGHT = 64;

    private static volatile boolean enabled;
    private static volatile Consumer<String> sink;
    private static volatile String defaultSource = "unlabeled";

    /** What a loaded sprite is: the path it was loaded from and its logical (source) size. */
    private record Named(String path, float width, float height) { }

    private static final Map<SpriteAPI, Named> NAMES =
            Collections.synchronizedMap(new IdentityHashMap<>());
    /** path → source label → largest display. */
    private static final Map<String, Map<String, Usage>> USAGE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> SLICED = new ConcurrentHashMap<>();
    private static volatile long dirtySince;
    /** Open {@link #suppressUi} scopes, process-wide: a headless drain paints on worker threads. */
    private static final AtomicInteger UI_SUPPRESSED = new AtomicInteger();

    private static final class Frame {
        float worldFactor = Float.NaN;
        int mapWidth;
        int mapHeight;
        String source;
        SpriteAPI lastSprite;
        Named lastNamed;
    }

    private static final ThreadLocal<Frame> FRAME = ThreadLocal.withInitial(Frame::new);

    /** One source's largest display of one image. */
    static final class Usage {
        float displayWidth;
        float displayHeight;
        String domain;
        int mapWidth;
        int mapHeight;

        synchronized boolean offer(float w, float h, String domain, int mapW, int mapH) {
            boolean grew = false;
            if (w > displayWidth) {
                displayWidth = w;
                this.domain = domain;
                mapWidth = mapW;
                mapHeight = mapH;
                grew = true;
            }
            if (h > displayHeight) {
                displayHeight = h;
                grew = true;
            }
            return grew;
        }
    }

    private TextureUsage() { }

    /** A scope closed by try-with-resources without a checked exception. */
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /** Starts recording; {@code sink} receives the record as JSON whenever {@link #flush} writes. */
    public static void enable(String source, Consumer<String> sink) {
        defaultSource = source == null ? "unlabeled" : source;
        TextureUsage.sink = sink;
        enabled = true;
    }

    /** Switches recording on or off without touching the session's sink; returns what it was. */
    static boolean setEnabled(boolean on) {
        boolean was = enabled;
        enabled = on;
        return was;
    }

    /** Labels this thread's draws, until relabelled; null returns to the session's label. */
    public static void source(String label) {
        if (!enabled) return;
        FRAME.get().source = label;
    }

    /** Remembers which path a sprite was loaded from, and its logical size in source pixels. */
    public static void name(SpriteAPI sprite, String path, float width, float height) {
        if (!enabled || sprite == null || path == null || !(width > 0f) || !(height > 0f)) return;
        NAMES.put(sprite, new Named(normalize(path), width, height));
    }

    /** Notes that a path's frames are found by slicing its pixels at load time. */
    public static void sliced(String path) {
        if (!enabled || path == null) return;
        SLICED.put(normalize(path), Boolean.TRUE);
    }

    /**
     * Opens a world frame on this thread: draws until the next call are scaled from a cell of
     * {@code cellPx} to the closest zoom a {@code mapWidth x mapHeight} battle reaches.
     */
    public static void beginWorld(float cellPx, int mapWidth, int mapHeight) {
        if (!enabled) return;
        Frame frame = FRAME.get();
        if (!(cellPx > 0f) || mapWidth <= 0 || mapHeight <= 0) {
            frame.worldFactor = Float.NaN;
            return;
        }
        frame.worldFactor = referenceCellPx(mapWidth, mapHeight) / cellPx;
        frame.mapWidth = mapWidth;
        frame.mapHeight = mapHeight;
        maybeFlush();
    }

    /** The closest zoom's cell, in screen pixels, of a battle on a {@code w x h} map at 1920x1080. */
    public static float referenceCellPx(int mapWidth, int mapHeight) {
        int w = Math.max(mapWidth, MIN_PLAY_MAP_WIDTH);
        int h = Math.max(mapHeight, MIN_PLAY_MAP_HEIGHT);
        return MAX_BATTLE_ZOOM * Math.max(REFERENCE_WIDTH / w, REFERENCE_HEIGHT / h);
    }

    /**
     * While open, UI draws are not recorded: a headless drain paints world draws through the
     * canvas, and those were already measured, as world draws, when collected. Process-wide,
     * because the drain paints in bands on worker threads; no evidence run paints UI and a battle
     * at the same moment.
     */
    public static Scope suppressUi() {
        if (!enabled) return () -> { };
        UI_SUPPRESSED.incrementAndGet();
        return UI_SUPPRESSED::decrementAndGet;
    }

    /** A world draw of the {@code srcW x srcH} cut of {@code sheet} at {@code dstW x dstH}. */
    public static void sheet(SpriteAPI sheet, int srcW, int srcH, float dstW, float dstH) {
        if (!enabled || srcW <= 0 || srcH <= 0) return;
        Frame frame = FRAME.get();
        if (Float.isNaN(frame.worldFactor)) return;
        Named named = named(frame, sheet);
        if (named == null) return;
        offer(frame, named.path, "world",
                Math.abs(dstW) * named.width / srcW * frame.worldFactor,
                Math.abs(dstH) * named.height / srcH * frame.worldFactor);
    }

    /** A world draw of the whole of {@code sprite} at {@code dstW x dstH}. */
    public static void sprite(SpriteAPI sprite, float dstW, float dstH) {
        if (!enabled) return;
        Frame frame = FRAME.get();
        if (Float.isNaN(frame.worldFactor)) return;
        Named named = named(frame, sprite);
        if (named == null) return;
        offer(frame, named.path, "world",
                Math.abs(dstW) * frame.worldFactor, Math.abs(dstH) * frame.worldFactor);
    }

    /**
     * A UI draw: the normalized {@code regionW x regionH} cut of an image at
     * {@code docW x docH} document units. The image is named by path, or by its live sprite.
     */
    public static void ui(String path, SpriteAPI live, float docW, float docH,
                          float regionW, float regionH) {
        if (!enabled || !(regionW > 0f) || !(regionH > 0f)) return;
        if (UI_SUPPRESSED.get() > 0) return;
        Frame frame = FRAME.get();
        String named = path;
        if (named == null) {
            Named byLive = named(frame, live);
            if (byLive == null) return;
            named = byLive.path;
        }
        offer(frame, normalize(named), "ui",
                Math.abs(docW) * MAX_UI_SCALE / regionW, Math.abs(docH) * MAX_UI_SCALE / regionH);
        maybeFlush();
    }

    private static Named named(Frame frame, SpriteAPI sprite) {
        if (sprite == null) return null;
        if (sprite == frame.lastSprite) return frame.lastNamed;
        Named named = NAMES.get(sprite);
        frame.lastSprite = sprite;
        frame.lastNamed = named;
        return named;
    }

    private static void offer(Frame frame, String path, String domain, float w, float h) {
        if (!(w > 0f) && !(h > 0f)) return;
        if (!Float.isFinite(w) || !Float.isFinite(h)) return;
        String source = frame.source != null ? frame.source : defaultSource;
        Usage usage = USAGE.computeIfAbsent(path, p -> new ConcurrentHashMap<>())
                .computeIfAbsent(source, s -> new Usage());
        boolean world = domain.equals("world");
        if (usage.offer(w, h, domain, world ? frame.mapWidth : 0, world ? frame.mapHeight : 0)
                && dirtySince == 0L) {
            dirtySince = System.currentTimeMillis();
        }
    }

    private static void maybeFlush() {
        long since = dirtySince;
        if (since != 0L && System.currentTimeMillis() - since > 30_000L) flush();
    }

    /** Hands the record to the sink, if there is one and anything changed. */
    public static synchronized void flush() {
        Consumer<String> target = sink;
        if (target == null || dirtySince == 0L) return;
        dirtySince = 0L;
        target.accept(toJson());
    }

    /** Forgets everything recorded; the names of loaded sprites are kept. */
    public static void reset() {
        USAGE.clear();
        SLICED.clear();
        dirtySince = 0L;
    }

    /** The largest display width recorded for {@code path} under any label, or 0. */
    public static float displayWidth(String path) {
        Map<String, Usage> bySource = USAGE.get(normalize(path));
        if (bySource == null) return 0f;
        float max = 0f;
        for (Usage usage : bySource.values()) max = Math.max(max, usage.displayWidth);
        return max;
    }

    /**
     * The record, one entry per path with one entry per source label beneath it:
     * {@code {"textures": {path: {"sliced": bool, "sources": {label: {displayWidth,
     * displayHeight, domain, mapWidth, mapHeight}}}}}}.
     */
    public static String toJson() {
        Map<String, Map<String, Usage>> sorted = new TreeMap<>(USAGE);
        StringBuilder out = new StringBuilder(64 + sorted.size() * 160);
        out.append("{\n  \"reference\": {\"screen\": \"1920x1080\", \"battleZoom\": ")
                .append(MAX_BATTLE_ZOOM).append(", \"uiScale\": ").append(MAX_UI_SCALE)
                .append("},\n  \"textures\": {");
        boolean firstPath = true;
        for (Map.Entry<String, Map<String, Usage>> entry : sorted.entrySet()) {
            out.append(firstPath ? "\n" : ",\n");
            firstPath = false;
            out.append("    ").append(quote(entry.getKey())).append(": {\"sliced\": ")
                    .append(SLICED.containsKey(entry.getKey())).append(", \"sources\": {");
            List<String> labels = new ArrayList<>(entry.getValue().keySet());
            Collections.sort(labels);
            boolean firstSource = true;
            for (String label : labels) {
                Usage usage = entry.getValue().get(label);
                if (!firstSource) out.append(", ");
                firstSource = false;
                synchronized (usage) {
                    out.append(quote(label)).append(": {\"displayWidth\": ")
                            .append(round(usage.displayWidth))
                            .append(", \"displayHeight\": ").append(round(usage.displayHeight))
                            .append(", \"domain\": ").append(quote(usage.domain));
                    if (usage.mapWidth > 0) {
                        out.append(", \"mapWidth\": ").append(usage.mapWidth)
                                .append(", \"mapHeight\": ").append(usage.mapHeight);
                    }
                    out.append('}');
                }
            }
            out.append("}}");
        }
        out.append("\n  }\n}\n");
        return out.toString();
    }

    static String normalize(String path) {
        String p = path.replace('\\', '/');
        while (p.startsWith("./")) p = p.substring(2);
        return p;
    }

    private static String round(float value) {
        return String.valueOf(Math.round(value * 10f) / 10f);
    }

    private static String quote(String text) {
        if (text == null) return "null";
        StringBuilder out = new StringBuilder(text.length() + 2).append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\\') out.append('\\');
            out.append(c);
        }
        return out.append('"').toString();
    }
}
